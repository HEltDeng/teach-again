package com.teachagain.app.ai;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import com.teachagain.app.FamilyCreateActivity;
import com.teachagain.app.FloatingBallService;
import com.teachagain.app.InsetsUtil;
import com.teachagain.app.ProgressRepository;
import com.teachagain.app.R;
import com.teachagain.app.Tutorial;
import com.teachagain.app.TutorialDetailActivity;
import com.teachagain.app.TutorialRepository;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 单输入框聊天页（类 DeepSeek）：老人说一句话，助手整理成步骤。
 * - 不分字段；整句交给本地模板/模型识别。
 * - 输入法自带语音即可，不新增录音权限、不做语音模型。
 * - 支持从悬浮球"问一问"带入当前教程+步骤上下文作为反馈。
 */
public class AskActivity extends AppCompatActivity {

    public static final String EXTRA_FEEDBACK_TUTORIAL_ID = "feedback_tutorial_id";
    public static final String EXTRA_FEEDBACK_STEP = "feedback_step"; // 0-based

    private LinearLayout chatContainer;
    private ScrollView chatScroll;
    private EditText etMessage;
    private Button btnSend, btnVoice;
    private ProgressBar progress;
    private TextView tvSource;

    private AiSettings settings;
    private ChatClient client;
    private ExecutorService executor;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean running = false;

    // 反馈上下文（从悬浮球"问一问"进入）
    private String feedbackTutorialId = null;
    private int feedbackStep = -1;
    // 聊天会话内最近一次生成的草稿，作为连续反馈的上下文
    private Tutorial lastDraft = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_ask);
        InsetsUtil.applySystemBars(findViewById(R.id.root));

        settings = new AiSettings(this);
        chatContainer = findViewById(R.id.chat_container);
        chatScroll = findViewById(R.id.chat_scroll);
        etMessage = findViewById(R.id.et_message);
        btnSend = findViewById(R.id.btn_send);
        btnVoice = findViewById(R.id.btn_voice);
        progress = findViewById(R.id.progress);
        tvSource = findViewById(R.id.tv_source);

        btnSend.setOnClickListener(v -> onSend());
        btnVoice.setOnClickListener(v -> focusForVoice());

        refreshSourceLabel();
        greet();
        handleFeedbackContext(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleFeedbackContext(intent);
    }

    private void refreshSourceLabel() {
        if (settings.isOnlineReady()) {
            tvSource.setText("模型生成（" + settings.getProvider().label + "，需要联网）");
        } else {
            tvSource.setText("本地体验（未配置 Key，用本机模板，可修改）");
        }
    }

    private void greet() {
        addAssistant("您好，我来帮您。说一句您想做什么就行，"
                + "比如「我想把微信里的字调大一点」。");
    }

    /** 从悬浮球"问一问"进入：显示当前教程+步骤上下文，等用户发消息，不自动联网。 */
    private void handleFeedbackContext(Intent intent) {
        if (intent == null) return;
        String tid = intent.getStringExtra(EXTRA_FEEDBACK_TUTORIAL_ID);
        int step = intent.getIntExtra(EXTRA_FEEDBACK_STEP, -1);
        if (tid == null) return;
        feedbackTutorialId = tid;
        feedbackStep = step;

        Tutorial t = new TutorialRepository(this).getById(tid);
        if (t == null) return;
        String stepText = "";
        if (step >= 0 && step < t.steps.size()) {
            Tutorial.Step s = t.steps.get(step);
            stepText = "第 " + (step + 1) + " 步「" + s.title + "」：" + s.desc;
        }
        addAssistant("您正在学「" + t.title + "」。\n" + stepText
                + "\n\n这一步要是对不上，告诉我哪里不一样，比如「我这里没有这个按钮」，我帮您重新整理。");
        etMessage.setHint("说说哪里不对，比如：我这里没有这个按钮");
    }

    private void onSend() {
        if (running) return;
        String msg = etMessage.getText().toString().trim();
        if (msg.isEmpty()) {
            Toast.makeText(this, "请先说一句您想做什么", Toast.LENGTH_SHORT).show();
            return;
        }
        addUser(msg);
        etMessage.setText("");

        boolean stuck = StepGenerator.looksLikeStuckFeedback(msg);
        boolean online = settings.isOnlineReady();

        // 先判新任务：非纠错即视为新任务，立刻清理旧的反馈/草稿上下文，
        // 避免"相机拍照"与旧"微信字体"混合。清理后再 buildContent。
        String feedbackTidForEdit = feedbackTutorialId;
        int feedbackStepForEdit = feedbackStep;
        if (!stuck) {
            feedbackTutorialId = null;
            feedbackStep = -1;
            lastDraft = null;
        }

        // 情况 A：纠错反馈 + 没 Key -> 温和说明本机局限，不重复假装解决
        if (stuck && !online) {
            // 若来自悬浮"问一问"，提供可直接修改当前步骤的按钮
            View editAction = null;
            if (feedbackTidForEdit != null) {
                final String tid = feedbackTidForEdit;
                Button edit = new Button(this);
                edit.setText("修改当前步骤");
                edit.setAllCaps(false);
                edit.setTextColor(getColor(R.color.text_main));
                edit.setBackgroundResource(R.drawable.btn_secondary);
                edit.setOnClickListener(v -> openEditorById(tid));
                editAction = edit;
            }
            addBubble("明白，这一步和您手机上看到的不一样。"
                    + "我现在用的是本机模板，没法实时看您的屏幕。"
                    + "您可以把屏幕上真实看到的字告诉我（比如按钮上写的是什么），"
                    + (editAction != null ? "或点下面「修改当前步骤」改成您看到的样子。"
                            : "或在模型设置里填入 Key 让我帮您重整。"),
                    false, editAction);
            return;
        }

        // 组织要发送的内容（仅用户明确输入 + 已有上下文）
        String content = buildContent(msg, stuck);

        if (!online) {
            StepGenerator.GenResult r = StepGenerator.generateLocalFromMessage(content);
            handleResult(r);
            return;
        }

        setBusy(true);
        addAssistant("好的，我想想怎么一步步做…");
        if (executor == null) executor = Executors.newSingleThreadExecutor();
        client = new ChatClient();
        final String baseUrl = settings.getBaseUrl();
        final String model = settings.getModel();
        final String key = settings.readKeyForRequest();
        final String sendContent = content;
        executor.execute(() -> {
            final StepGenerator.GenResult r = StepGenerator.generateRemoteFromMessage(
                    client, baseUrl, key, model, sendContent);
            main.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                setBusy(false);
                handleResult(r);
            });
        });
    }

    /** 构造发送内容：带上当前教程/步骤（悬浮反馈）或最近草稿（连续反馈），均为用户明确发送。 */
    private String buildContent(String msg, boolean stuck) {
        // 优先悬浮球带入的教程+步骤
        if (feedbackTutorialId != null) {
            Tutorial t = new TutorialRepository(this).getById(feedbackTutorialId);
            if (t != null) {
                StringBuilder sb = new StringBuilder();
                sb.append("我正在学「").append(t.title).append("」。");
                if (feedbackStep >= 0 && feedbackStep < t.steps.size()) {
                    Tutorial.Step s = t.steps.get(feedbackStep);
                    sb.append("当前第").append(feedbackStep + 1).append("步：")
                      .append(s.title).append("——").append(s.desc).append("。");
                }
                sb.append("我的问题：").append(msg).append("。请帮我重新整理清楚的步骤。");
                return sb.toString();
            }
        }
        // 其次：聊天内最近生成的草稿，支持"这一步找不到"连续反馈
        if (stuck && lastDraft != null) {
            StringBuilder sb = new StringBuilder();
            sb.append("刚才整理的教程「").append(lastDraft.title).append("」步骤如下：");
            for (int i = 0; i < lastDraft.steps.size(); i++) {
                sb.append(i + 1).append(".").append(lastDraft.steps.get(i).title).append("；");
            }
            sb.append("用户反馈：").append(msg).append("。请据此修正步骤。");
            return sb.toString();
        }
        return msg;
    }

    private void handleResult(StepGenerator.GenResult r) {
        if (!r.ok) {
            addAssistant("没整理成功：" + r.error);
            return;
        }
        final Tutorial draft = r.tutorial;
        lastDraft = draft; // 记为上下文，支持连续"这一步找不到"反馈
        final String sourceLabel = r.source == StepGenerator.Source.REMOTE ? "模型生成" : "本地体验";
        addResultBubble(draft, sourceLabel);
    }

    // ---------- 会话气泡 ----------

    private void addUser(String text) {
        addBubble(text, true, null);
    }

    private void addAssistant(String text) {
        addBubble(text, false, null);
    }

    private void addResultBubble(Tutorial draft, String source) {
        StringBuilder sb = new StringBuilder();
        sb.append("我整理好了（").append(source).append("）：「")
          .append(draft.title).append("」，一共 ").append(draft.stepCount()).append(" 步。\n");
        for (int i = 0; i < draft.steps.size(); i++) {
            sb.append(i + 1).append(". ").append(draft.steps.get(i).title).append("\n");
        }
        sb.append("\n可以直接开始跟学，也可以先修改。");

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        Button start = new Button(this);
        start.setText("开始跟学");
        start.setAllCaps(false);
        start.setTextColor(getColor(R.color.white));
        start.setBackgroundResource(R.drawable.btn_primary);
        start.setOnClickListener(v -> saveAndStart(draft));

        Button edit = new Button(this);
        edit.setText("修改步骤");
        edit.setAllCaps(false);
        edit.setTextColor(getColor(R.color.text_main));
        edit.setBackgroundResource(R.drawable.btn_secondary);
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        elp.leftMargin = dp(10);
        edit.setLayoutParams(elp);
        edit.setOnClickListener(v -> openEditor(draft, source));

        actions.addView(start);
        actions.addView(edit);

        addBubble(sb.toString(), false, actions);
    }

    private void addBubble(String text, boolean user, View extra) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wlp.topMargin = dp(10);
        wrap.setLayoutParams(wlp);

        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(17);
        tv.setLineSpacing(0, 1.25f);
        tv.setPadding(dp(14), dp(12), dp(14), dp(12));
        if (user) {
            tv.setTextColor(getColor(R.color.white));
            tv.setBackgroundResource(R.drawable.btn_primary);
        } else {
            tv.setTextColor(getColor(R.color.text_main));
            tv.setBackgroundResource(R.drawable.card_soft);
        }
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.gravity = user ? Gravity.END : Gravity.START;
        tlp.leftMargin = user ? dp(48) : 0;
        tlp.rightMargin = user ? 0 : dp(48);
        tv.setLayoutParams(tlp);
        wrap.addView(tv);

        if (extra != null) {
            LinearLayout.LayoutParams xlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            xlp.topMargin = dp(8);
            extra.setLayoutParams(xlp);
            wrap.addView(extra);
        }

        chatContainer.addView(wrap);
        chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    // ---------- 保存/编辑 ----------

    private void saveAndStart(Tutorial draft) {
        if (hasPlaceholder(draft)) {
            addAssistant("这份里还有「待家人补全」的占位，先点「修改步骤」补成具体操作，再开始跟学会更稳。");
            openEditor(draft, "本地体验");
            return;
        }
        TutorialRepository repo = new TutorialRepository(this);
        if (!repo.saveUserTutorial(draft)) {
            Toast.makeText(this, "保存失败，请重试", Toast.LENGTH_SHORT).show();
            return;
        }
        ProgressRepository pr = new ProgressRepository(this);
        pr.save(draft.id, 0);
        pr.setFinished(draft.id, false); // 同一气泡再点开始，清除旧完成态，从头学

        boolean hasOverlay = Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || Settings.canDrawOverlays(this);
        if (hasOverlay) {
            // 已有权限：直接启动悬浮引导
            Intent svc = new Intent(this, FloatingBallService.class);
            svc.setAction(FloatingBallService.ACTION_START);
            svc.putExtra(FloatingBallService.EXTRA_TUTORIAL_ID, draft.id);
            try {
                startForegroundService(svc);
                Toast.makeText(this, "小帮手出现啦，点屏幕上的小球看步骤",
                        Toast.LENGTH_LONG).show();
                moveTaskToBack(true);
                return;
            } catch (Exception ignored) {}
        }
        // 没权限：进详情页，由它引导授权
        Intent i = new Intent(this, TutorialDetailActivity.class);
        i.putExtra(TutorialDetailActivity.EXTRA_TUTORIAL_ID, draft.id);
        startActivity(i);
    }

    /** 直接编辑已保存的教程（悬浮反馈来源）。 */
    private void openEditorById(String tutorialId) {
        Intent i = new Intent(this, FamilyCreateActivity.class);
        i.putExtra(FamilyCreateActivity.EXTRA_EDIT_ID, tutorialId);
        startActivity(i);
    }

    private void openEditor(Tutorial draft, String source) {
        try {
            Intent i = new Intent(this, FamilyCreateActivity.class);
            i.putExtra(FamilyCreateActivity.EXTRA_DRAFT_JSON, draft.toJson().toString());
            i.putExtra(FamilyCreateActivity.EXTRA_DRAFT_SOURCE, source);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "打开修改页失败", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean hasPlaceholder(Tutorial t) {
        for (Tutorial.Step s : t.steps) {
            if ((s.title != null && s.title.contains("待家人补全"))
                    || (s.desc != null && s.desc.contains("待家人补全"))) return true;
        }
        return false;
    }

    // ---------- 语音输入（仅聚焦+提示，不录音）----------

    private void focusForVoice() {
        etMessage.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(etMessage, InputMethodManager.SHOW_IMPLICIT);
        Toast.makeText(this, "点键盘上的小话筒，就能用说的代替打字",
                Toast.LENGTH_LONG).show();
    }

    private void setBusy(boolean busy) {
        running = busy;
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        btnSend.setEnabled(!busy);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (running && client != null) client.cancel();
    }

    @Override
    protected void onDestroy() {
        if (client != null) client.cancel();
        if (executor != null) executor.shutdownNow();
        super.onDestroy();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
