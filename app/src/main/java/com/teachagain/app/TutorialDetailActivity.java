package com.teachagain.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

/**
 * 教程详情预览：展示每一步，可用系统中文 TTS 朗读。
 * 底部固定"开始跟我学"，缺权限时直接引导到系统授权页，返回自动继续。
 */
public class TutorialDetailActivity extends AppCompatActivity {

    public static final String EXTRA_TUTORIAL_ID = "tutorial_id";

    private Tutorial tutorial;
    private TtsHelper tts;
    private ProgressRepository progress;
    private boolean pendingStartAfterPermission = false;

    private ActivityResultLauncher<Intent> overlayLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_tutorial_detail);
        InsetsUtil.applySystemBars(findViewById(R.id.root));

        tts = new TtsHelper(this);
        progress = new ProgressRepository(this);

        overlayLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (pendingStartAfterPermission && hasOverlayPermission()) {
                        pendingStartAfterPermission = false;
                        launchService();
                    } else if (pendingStartAfterPermission) {
                        pendingStartAfterPermission = false;
                        Toast.makeText(this,
                                "还没允许悬浮窗权限，您可以先看文字，准备好再点开始",
                                Toast.LENGTH_LONG).show();
                    }
                });

        String id = getIntent().getStringExtra(EXTRA_TUTORIAL_ID);
        tutorial = new TutorialRepository(this).getById(id);
        if (tutorial == null) {
            Toast.makeText(this, "找不到这个教程", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        ((TextView) findViewById(R.id.tv_title)).setText(tutorial.title);
        buildSteps();
        setupButtons();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (tutorial == null) return; // 未成功加载时不刷新，避免空指针
        refreshResumeHint();
    }

    private void refreshResumeHint() {
        TextView hint = findViewById(R.id.tv_resume_hint);
        Button restart = findViewById(R.id.btn_restart);
        Button start = findViewById(R.id.btn_start);

        int saved = progress.getStepFor(tutorial.id);
        boolean finished = progress.isFinished(tutorial.id);

        if (finished) {
            hint.setVisibility(android.view.View.VISIBLE);
            hint.setText("您已经学完这个啦，可以从头再看一遍");
            start.setText("再看一遍");
            // 已完成时"再看一遍"=从第1步重新开始
            start.setOnClickListener(v -> onStart(true));
            restart.setVisibility(android.view.View.GONE);
            return;
        } else if (saved > 0) {
            hint.setVisibility(android.view.View.VISIBLE);
            hint.setText("上次学到第 " + (saved + 1) + " 步，可以接着学");
            start.setText("继续学（第 " + (saved + 1) + " 步）");
            start.setOnClickListener(v -> onStart(false)); // 继续
            restart.setVisibility(android.view.View.VISIBLE);
        } else {
            hint.setVisibility(android.view.View.GONE);
            start.setText("开始跟我学");
            start.setOnClickListener(v -> onStart(false));
            restart.setVisibility(android.view.View.GONE);
        }
    }

    private void setupButtons() {
        findViewById(R.id.btn_start).setOnClickListener(v -> onStart(false));
        findViewById(R.id.btn_restart).setOnClickListener(v -> onStart(true));
    }

    private void buildSteps() {
        LinearLayout container = findViewById(R.id.steps_preview);
        for (int i = 0; i < tutorial.steps.size(); i++) {
            final Tutorial.Step s = tutorial.steps.get(i);
            final int num = i + 1;

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundResource(R.drawable.card_soft);
            card.setPadding(dp(16), dp(16), dp(16), dp(16));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(10);
            card.setLayoutParams(lp);

            TextView t = new TextView(this);
            t.setText("第 " + num + " 步  " + s.title);
            t.setTextColor(getColor(R.color.text_main));
            t.setTextSize(20);
            t.getPaint().setFakeBoldText(true);
            card.addView(t);

            TextView d = new TextView(this);
            d.setText(s.desc);
            d.setTextColor(getColor(R.color.text_main));
            d.setTextSize(17);
            d.setLineSpacing(0, 1.3f);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            dlp.topMargin = dp(6);
            d.setLayoutParams(dlp);
            card.addView(d);

            Button read = new Button(this);
            read.setText("再读一次");
            read.setAllCaps(false);
            read.setTextSize(15);
            read.setBackgroundResource(R.drawable.btn_secondary);
            read.setTextColor(getColor(R.color.text_main));
            read.setOnClickListener(v -> readAloud(s.title + "。" + s.desc));
            card.addView(read);

            container.addView(card);
        }
    }

    private void readAloud(String text) {
        if (tts == null || !tts.isReady()) {
            Toast.makeText(this,
                    "系统朗读暂时用不了，您可以直接看文字。可到系统设置里安装中文语音。",
                    Toast.LENGTH_LONG).show();
            return;
        }
        tts.speak(text);
    }

    private boolean hasOverlayPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || Settings.canDrawOverlays(this);
    }

    private void onStart(boolean restart) {
        if (restart) {
            progress.setFinished(tutorial.id, false);
            progress.save(tutorial.id, 0);
        } else {
            // 继续：保留已保存步骤
            progress.save(tutorial.id, progress.getStepFor(tutorial.id));
        }

        if (!hasOverlayPermission()) {
            // 直接引导到系统授权页，返回后自动继续
            pendingStartAfterPermission = true;
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                overlayLauncher.launch(i);
            } catch (Exception e) {
                pendingStartAfterPermission = false;
                Toast.makeText(this, "请到系统设置里允许悬浮窗权限",
                        Toast.LENGTH_LONG).show();
            }
            return;
        }
        launchService();
    }

    private void launchService() {
        Intent svc = new Intent(this, FloatingBallService.class);
        svc.setAction(FloatingBallService.ACTION_START);
        svc.putExtra(FloatingBallService.EXTRA_TUTORIAL_ID, tutorial.id);
        try {
            startForegroundService(svc);
            Toast.makeText(this,
                    "小帮手出现啦，按步骤打开对应的应用，点屏幕上的小球看每一步",
                    Toast.LENGTH_LONG).show();
            moveTaskToBack(true);
        } catch (Exception e) {
            Toast.makeText(this, "启动失败，请确认悬浮窗权限", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) tts.shutdown();
        super.onDestroy();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
