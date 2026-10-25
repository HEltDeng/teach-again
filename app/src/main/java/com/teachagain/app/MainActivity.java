package com.teachagain.app;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

/**
 * 首页：引导悬浮窗权限、列出教程、入口到家人创建/导入。
 * 接收系统分享的 text/plain 作为教程导入。
 */
public class MainActivity extends AppCompatActivity {

    private TutorialRepository repo;
    private TextView tvStatus;
    private LinearLayout listContainer;
    private AutoFollowPrefs autoFollow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);
        InsetsUtil.applySystemBars(findViewById(R.id.root));

        repo = new TutorialRepository(this);
        tvStatus = findViewById(R.id.tv_status);
        listContainer = findViewById(R.id.tutorial_list);

        findViewById(R.id.btn_family_create).setOnClickListener(v ->
                startActivity(new Intent(this, FamilyCreateActivity.class)));

        findViewById(R.id.btn_import).setOnClickListener(v -> showImportDialog(null));

        autoFollow = new AutoFollowPrefs(this);
        findViewById(R.id.btn_auto_follow).setOnClickListener(v -> showAutoFollowDialog());

        findViewById(R.id.btn_ask).setOnClickListener(v ->
                startActivity(new Intent(this, com.teachagain.app.ai.AskActivity.class)));
        findViewById(R.id.btn_ai_settings).setOnClickListener(v ->
                startActivity(new Intent(this, com.teachagain.app.ai.AiSettingsActivity.class)));

        handleShareIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleShareIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
        buildTutorialList();
        startBreathing();
    }

    private void startBreathing() {
        android.view.View ball = findViewById(R.id.breath_ball);
        if (ball == null || ball.getAnimation() != null) return;
        android.view.animation.ScaleAnimation anim =
                new android.view.animation.ScaleAnimation(
                        1f, 1.08f, 1f, 1.08f,
                        android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f,
                        android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f);
        anim.setDuration(1600);
        anim.setRepeatMode(android.view.animation.Animation.REVERSE);
        anim.setRepeatCount(android.view.animation.Animation.INFINITE);
        anim.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        ball.startAnimation(anim);
    }

    private void handleShareIntent(Intent intent) {
        if (intent != null && Intent.ACTION_SEND.equals(intent.getAction())
                && "text/plain".equals(intent.getType())) {
            String shared = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (shared != null && !shared.isEmpty()) {
                showImportDialog(shared);
            }
        }
    }

    // ---------- 权限 ----------

    private boolean hasOverlayPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || Settings.canDrawOverlays(this);
    }

    private void refreshStatus() {
        if (hasOverlayPermission()) {
            tvStatus.setText("小帮手已就绪，随时陪您学");
        } else {
            tvStatus.setText("点教程时会请您开启悬浮窗权限");
        }
        refreshAutoFollowStatus();
    }

    private void refreshAutoFollowStatus() {
        TextView tv = findViewById(R.id.tv_auto_status);
        boolean wanted = autoFollow.isEnabled();
        boolean granted = autoFollow.isAccessibilityGranted();
        if (wanted && granted) {
            tv.setText("已开启：学习时会自动识别微信页面，帮您翻到下一步"
                    + (autoFollow.isOcrEnabled() ? "（含画面识别）" : ""));
        } else if (wanted) {
            tv.setText("已打开意愿，但还没在系统里授权。点下面去开启");
        } else {
            tv.setText("关闭中。打开后，支持的步骤能自动帮您往下走（可随时关）");
        }
    }

    private void showAutoFollowDialog() {
        boolean granted = autoFollow.isAccessibilityGranted();
        String msg = "自动跟随能在您学习时，识别微信当前页面上的文字，判断这一步是否做好，"
                + "自动帮您翻到下一步。\n\n"
                + "• 只在学习中、页面是微信时才处理\n"
                + "• 只在本机处理可见控件文字，不保存、不上传\n"
                + "• 不会自动点击任何按钮\n"
                + "• 复杂步骤仍由您点「我做到了」确认\n"
                + "• 需要您在系统设置里授权无障碍，可随时关闭\n\n"
                + (granted ? "当前：系统已授权。" : "当前：系统尚未授权。");

        new AlertDialog.Builder(this)
                .setTitle("自动跟随")
                .setMessage(msg)
                .setPositiveButton(autoFollow.isEnabled() ? "去系统设置" : "开启并去设置",
                        (d, w) -> {
                            autoFollow.setEnabled(true);
                            openAccessibilitySettings();
                        })
                .setNeutralButton(autoFollow.isOcrEnabled() ? "关闭画面识别" : "画面识别(可选)",
                        (d, w) -> toggleOcr())
                .setNegativeButton("关闭自动跟随", (d, w) -> {
                    autoFollow.setEnabled(false);
                    refreshAutoFollowStatus();
                    Toast.makeText(this, "已关闭自动跟随", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void toggleOcr() {
        if (autoFollow.isOcrEnabled()) {
            autoFollow.setOcrEnabled(false);
            Toast.makeText(this, "已关闭画面识别", Toast.LENGTH_SHORT).show();
            refreshAutoFollowStatus();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("画面识别（可选）")
                .setMessage("开启后，在学习微信时会在本机截取当前微信窗口的画面做文字识别，"
                        + "帮助更准地判断这一步。\n\n"
                        + "• 只截当前微信这一个窗口\n"
                        + "• 全部在本机识别，不保存图片、不上传\n"
                        + "• 需要无障碍的截屏能力；若提示缺少能力，请在系统里关掉再重新开启本应用的无障碍\n"
                        + "• 可随时关闭")
                .setPositiveButton("开启", (d, w) -> {
                    autoFollow.setOcrEnabled(true);
                    if (!autoFollow.isAccessibilityGranted()) {
                        Toast.makeText(this,
                                "还需在系统里授予无障碍（含截屏能力）。请在设置里重新开启本应用的无障碍。",
                                Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, "已开启画面识别", Toast.LENGTH_SHORT).show();
                    }
                    refreshAutoFollowStatus();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void openAccessibilitySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            Toast.makeText(this,
                    "请在列表里找到「再教我一次」，打开它的开关",
                    Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "打不开无障碍设置，请手动到系统设置里开启",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void requestOverlayPermission(Runnable afterGranted) {
        new AlertDialog.Builder(this)
                .setTitle("开启悬浮小帮手")
                .setMessage(getString(R.string.overlay_hint))
                .setPositiveButton("去开启", (d, w) -> {
                    try {
                        Intent i = new Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getPackageName()));
                        startActivity(i);
                    } catch (Exception e) {
                        // 某些系统无此设置页，兜底打开应用详情
                        try {
                            startActivity(new Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:" + getPackageName())));
                        } catch (Exception ignored) {
                            Toast.makeText(this, "请到系统设置里允许悬浮窗权限",
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                })
                .setNegativeButton("以后再说", null)
                .show();
    }

    // ---------- 教程列表 ----------

    private void buildTutorialList() {
        listContainer.removeAllViews();
        List<Tutorial> all = repo.getAll();
        for (Tutorial t : all) {
            listContainer.addView(createTutorialButton(t));
        }
    }

    private View createTutorialButton(Tutorial t) {
        ProgressRepository pr = new ProgressRepository(this);
        int saved = pr.getStepFor(t.id);
        boolean finished = pr.isFinished(t.id);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.card_soft);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        card.setLayoutParams(lp);

        TextView title = new TextView(this);
        title.setText(t.title);
        title.setTextSize(21);
        title.setTextColor(getColor(R.color.text_main));
        title.getPaint().setFakeBoldText(true);
        card.addView(title);

        TextView sub = new TextView(this);
        String status;
        if (finished) {
            status = "已学完 · 共 " + t.stepCount() + " 步 · 可再看一遍";
        } else if (saved > 0) {
            status = "学到第 " + (saved + 1) + " 步 · 共 " + t.stepCount() + " 步 · 点我继续";
        } else {
            status = "共 " + t.stepCount() + " 步 · 点我开始";
        }
        if (!t.builtin) status = status + " · 家人编写";
        sub.setText(status);
        sub.setTextSize(15);
        sub.setTextColor(getColor(finished ? R.color.success : R.color.primary));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = dp(4);
        sub.setLayoutParams(slp);
        card.addView(sub);

        if (t.builtin) {
            card.setOnClickListener(v -> openDetail(t));
        } else {
            card.setOnClickListener(v -> showCustomOptions(t));
        }
        return card;
    }

    private void showCustomOptions(Tutorial t) {
        String[] items = {"开始学习", "修改教程", "分享教程", "删除教程"};
        new AlertDialog.Builder(this)
                .setTitle(t.title)
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0: openDetail(t); break;
                        case 1: editTutorial(t); break;
                        case 2: shareTutorial(t); break;
                        case 3: confirmDelete(t); break;
                    }
                })
                .show();
    }

    private void editTutorial(Tutorial t) {
        Intent i = new Intent(this, FamilyCreateActivity.class);
        i.putExtra(FamilyCreateActivity.EXTRA_EDIT_ID, t.id);
        startActivity(i);
    }

    private void shareTutorial(Tutorial t) {
        String text = TutorialImporter.export(t);
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, text);
        try {
            startActivity(Intent.createChooser(send, "分享教程"));
        } catch (Exception e) {
            Toast.makeText(this, "没有可用的分享应用", Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmDelete(Tutorial t) {
        new AlertDialog.Builder(this)
                .setTitle("删除教程")
                .setMessage("要删除「" + t.title + "」吗？")
                .setPositiveButton("删除", (d, w) -> {
                    if (repo.deleteUserTutorial(t.id)) {
                        buildTutorialList();
                    } else {
                        Toast.makeText(this, "删除失败，请重试", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void openDetail(Tutorial t) {
        if (!hasOverlayPermission()) {
            requestOverlayPermission(null);
            return;
        }
        Intent i = new Intent(this, TutorialDetailActivity.class);
        i.putExtra(TutorialDetailActivity.EXTRA_TUTORIAL_ID, t.id);
        startActivity(i);
    }

    private void startTutorial(Tutorial t) {
        if (!hasOverlayPermission()) {
            requestOverlayPermission(null);
            return;
        }
        // 切到该教程，但保留它上次学到的步骤（断点续学），不无条件清零
        ProgressRepository pr = new ProgressRepository(this);
        pr.save(t.id, pr.getStepFor(t.id));
        Intent svc = new Intent(this, FloatingBallService.class);
        svc.setAction(FloatingBallService.ACTION_START);
        svc.putExtra(FloatingBallService.EXTRA_TUTORIAL_ID, t.id);
        try {
            startForegroundService(svc);
            Toast.makeText(this,
                    "小帮手出现啦，去打开微信，点屏幕上的小球看步骤",
                    Toast.LENGTH_LONG).show();
            // 回到桌面，方便去开微信
            moveTaskToBack(true);
        } catch (Exception e) {
            Toast.makeText(this, "启动失败，请确认已允许悬浮窗权限",
                    Toast.LENGTH_LONG).show();
        }
    }

    // ---------- 导入 ----------

    private void showImportDialog(String prefill) {
        final android.widget.EditText et = new android.widget.EditText(this);
        et.setTextSize(16);
        et.setMinLines(6);
        et.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        et.setHint("教程名称：XXX\n第1步：标题 | 说明\n第2步：标题 | 说明");
        if (prefill != null) et.setText(prefill);
        int pad = dp(16);
        et.setPadding(pad, pad, pad, pad);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("导入教程")
                .setView(et)
                .setPositiveButton("导入", null) // 先置空，show 后接管点击，失败不自动关闭
                .setNegativeButton("取消", null)
                .create();

        dialog.setOnShowListener(d -> {
            android.widget.Button ok = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            ok.setOnClickListener(v -> {
                TutorialImporter.Result r = TutorialImporter.parse(et.getText().toString());
                if (!r.ok) {
                    // 失败：仅提示，保留已粘贴文本，对话框不关闭，可直接修改
                    et.setError(r.error);
                    Toast.makeText(this, r.error, Toast.LENGTH_LONG).show();
                    return;
                }
                if (repo.saveUserTutorial(r.tutorial)) {
                    Toast.makeText(this, "导入成功：" + r.tutorial.title,
                            Toast.LENGTH_SHORT).show();
                    buildTutorialList();
                    dialog.dismiss();
                } else {
                    Toast.makeText(this, "保存失败，存储空间可能不足，请重试",
                            Toast.LENGTH_LONG).show();
                }
            });
        });
        dialog.show();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
