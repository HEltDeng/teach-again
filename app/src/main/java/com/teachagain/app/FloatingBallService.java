package com.teachagain.app;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import androidx.core.content.ContextCompat;

/** A foreground, bounded overlay. Recognition can only confirm the exact active step. */
public class FloatingBallService extends Service {
    public static final String EXTRA_TUTORIAL_ID = "tutorial_id";
    public static final String ACTION_START = "com.teachagain.START";
    public static final String ACTION_STOP = "com.teachagain.STOP";
    public static final String ACTION_SHOW = "com.teachagain.SHOW";
    public static final String ACTION_AUTO_ADVANCE = "com.teachagain.AUTO_ADVANCE";
    public static final String EXTRA_EXPECTED_STEP = "expected_step";
    private static final String CHANNEL = "floating_ball";
    private static final int NOTIFICATION = 1001;
    private static volatile boolean running;
    private static volatile boolean recognitionActive;
    public static boolean isRunning() { return running; }
    public static boolean isRecognitionActive() { return recognitionActive; }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private View ball, card;
    private WindowManager.LayoutParams ballLp, cardLp;
    private TutorialRepository tutorials;
    private ProgressRepository progress;
    private SharedPreferences positions;
    private TtsHelper voice;
    private Tutorial tutorial;
    private int step;
    private boolean finished, paused, missingTarget, receiverRegistered;
    private long speechTicket;
    private String feedback = "慢慢来，我陪着您";

    private final BroadcastReceiver recognition = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!ACTION_AUTO_ADVANCE.equals(intent.getAction()) || !recognitionActive
                    || tutorial == null || paused || finished) return;
            if (!tutorial.id.equals(intent.getStringExtra(EXTRA_TUTORIAL_ID))) return;
            if (intent.getIntExtra(EXTRA_EXPECTED_STEP, -1) != step) return;
            String evidence = intent.getStringExtra("evidence");
            getSharedPreferences("guide_diagnostics", MODE_PRIVATE).edit()
                    .putString("source", evidence != null && evidence.startsWith("OCR") ? "ocr" : "page_text")
                    .putInt("completed_step", step)
                    .putLong("at", System.currentTimeMillis()).apply();
            // The expected-step token rejects repeats. Do not drop a real, fast page transition.
            advance(true);
        }
    };

    private final Runnable healthCheck = new Runnable() {
        @Override public void run() {
            if (!running) return;
            if (!Settings.canDrawOverlays(FloatingBallService.this)) {
                toast("悬浮窗权限已关闭，回到首页可以重新开启");
                stopSelf();
                return;
            }
            if (ball == null || !ball.isAttachedToWindow()) {
                removeBall();
                if (!addBall()) return;
            }
            handler.postDelayed(this, 2000);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        tutorials = new TutorialRepository(this);
        progress = new ProgressRepository(this);
        positions = getSharedPreferences("overlay_position", MODE_PRIVATE);
        voice = new TtsHelper(this);
        IntentFilter filter = new IntentFilter(ACTION_AUTO_ADVANCE);
        ContextCompat.registerReceiver(this, recognition, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        receiverRegistered = true;
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!Settings.canDrawOverlays(this)) {
            toast("请先允许悬浮窗，再回来点开始跟学");
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!startForegroundSafely()) return START_NOT_STICKY;
        String id = intent == null ? null : intent.getStringExtra(EXTRA_TUTORIAL_ID);
        boolean explicit = ACTION_START.equals(action);
        if (id == null) id = progress.getCurrentTutorialId();
        Tutorial next = id == null ? null : tutorials.getById(id);
        if (next == null || next.steps.isEmpty()) {
            toast("请先在首页选择一个教程");
            stopSelf();
            return START_NOT_STICKY;
        }
        tutorial = next;
        step = Math.max(0, Math.min(progress.getStepFor(id), next.stepCount() - 1));
        finished = progress.isFinished(id);
        if (explicit) { paused = false; missingTarget = false; }
        progress.save(id, step);
        running = true;
        syncRecognition();
        if (!addBall()) return START_NOT_STICKY;
        renderBall();
        if (explicit || ACTION_SHOW.equals(action)) showCard();
        else if (card != null) renderCard();
        handler.removeCallbacks(healthCheck);
        handler.postDelayed(healthCheck, 2000);
        return START_STICKY;
    }

    private boolean startForegroundSafely() {
        try {
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(new NotificationChannel(CHANNEL,
                    "学习陪伴", NotificationManager.IMPORTANCE_LOW));
            Notification notification = makeNotification();
            if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIFICATION, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            else startForeground(NOTIFICATION, notification);
            return true;
        } catch (RuntimeException error) {
            toast("暂时无法启动陪伴，请回到首页再试一次");
            stopSelf();
            return false;
        }
    }

    private Notification makeNotification() {
        PendingIntent open = PendingIntent.getActivity(this, 10,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent show = PendingIntent.getService(this, 11,
                new Intent(this, FloatingBallService.class).setAction(ACTION_SHOW),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop = PendingIntent.getService(this, 12,
                new Intent(this, FloatingBallService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher).setContentTitle("再教我一次 · 我在")
                .setContentText(paused ? "已暂停，点开小球可以继续" : "小球会陪着您，拖到哪里都可以")
                .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
                .addAction(new Notification.Action.Builder(null, "展开步骤", show).build())
                .addAction(new Notification.Action.Builder(null, "结束陪伴", stop).build()).build();
    }

    private WindowManager.LayoutParams overlayParams(int width) {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(width,
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
        return lp;
    }

    private boolean addBall() {
        if (ball != null && ball.isAttachedToWindow()) return true;
        ball = LayoutInflater.from(this).inflate(R.layout.floating_ball, null);
        ballLp = overlayParams(dp(64));
        Rect area = availableArea();
        ballLp.x = positions.getInt("ball_x", area.right - dp(68));
        ballLp.y = positions.getInt("ball_y", area.top + dp(80));
        bound(ball, ballLp, dp(64), dp(64));
        View ballTouch = ball;
        ball.setOnClickListener(v -> { if (card == null) showCard(); else hideCard(); });
        attachDrag(ballTouch, ball, ballLp, "ball", true);
        try {
            wm.addView(ball, ballLp);
            renderBall();
            ball.post(() -> updateBounds(ball, ballLp, "ball"));
            return true;
        } catch (RuntimeException error) {
            removeBall();
            toast("小球暂时没有显示，请回到首页检查悬浮窗权限");
            stopSelf();
            return false;
        }
    }

    private void showCard() {
        if (tutorial == null) return;
        if (card != null && card.isAttachedToWindow()) { renderCard(); return; }
        View content = LayoutInflater.from(this).inflate(R.layout.step_card, null);
        ScrollView outer = new ScrollView(this);
        outer.setFillViewport(false);
        outer.setClipToPadding(false);
        outer.addView(content);
        card = outer;
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(255, 252, 246));
        background.setCornerRadius(dp(20));
        background.setStroke(dp(1), Color.rgb(231, 215, 193));
        card.setBackground(background);
        Rect area = availableArea();
        cardLp = overlayParams(Math.min(dp(292), area.width() - dp(12)));
        cardLp.x = positions.getInt("card_x", area.left + dp(6));
        cardLp.y = positions.getInt("card_y", area.top + dp(145));
        View body = card.findViewById(R.id.card_scroll);
        body.getLayoutParams().height = Math.min(dp(60), Math.max(dp(44), area.height() / 8));
        attachDrag(card.findViewById(R.id.card_drag), card, cardLp, "card", false);
        card.findViewById(R.id.btn_collapse).setOnClickListener(v -> hideCard());
        card.findViewById(R.id.btn_more).setOnClickListener(v -> {
            View extra = card.findViewById(R.id.secondary_controls);
            extra.setVisibility(extra.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            fitCardHeight();
            updateBounds(card, cardLp, "card");
        });
        card.findViewById(R.id.btn_done).setOnClickListener(v -> advance(false));
        card.findViewById(R.id.btn_prev).setOnClickListener(v -> previous());
        card.findViewById(R.id.btn_repeat).setOnClickListener(v -> speakCurrent(""));
        card.findViewById(R.id.btn_help).setOnClickListener(v -> {
            missingTarget = true;
            stopVoice();
            syncRecognition();
            renderCard();
            speakCurrent("");
        });
        card.findViewById(R.id.btn_ask_help).setOnClickListener(v -> askForHelp());
        card.findViewById(R.id.btn_pause).setOnClickListener(v -> {
            paused = !paused;
            syncRecognition();
            stopVoice();
            feedback = paused ? "歇一会儿，准备好再继续" : "我在，咱们接着来";
            renderCard();
            renderBall();
            if (!paused) speakCurrent("");
        });
        card.findViewById(R.id.btn_close).setOnClickListener(v -> stopSelf());
        renderCard();
        fitCardHeight();
        bound(card, cardLp, cardLp.width, dp(300));
        try {
            wm.addView(card, cardLp);
            card.post(() -> updateBounds(card, cardLp, "card"));
            if (!paused) speakCurrent("");
        } catch (RuntimeException error) {
            card = null;
            cardLp = null;
            toast("步骤卡暂时打不开，点小球可重新试试");
        }
    }

    private void renderCard() {
        if (card == null || tutorial == null) return;
        TextView progressText = card.findViewById(R.id.tv_progress);
        TextView title = card.findViewById(R.id.tv_step_title);
        TextView desc = card.findViewById(R.id.tv_step_desc);
        TextView status = card.findViewById(R.id.tv_feedback);
        Button done = card.findViewById(R.id.btn_done);
        Button prev = card.findViewById(R.id.btn_prev);
        Button pause = card.findViewById(R.id.btn_pause);
        progressText.setText(finished ? "学会啦" : (step + 1) + " / " + tutorial.stepCount());
        title.setText(missingTarget ? "找不到也没关系" : finished ? "您做到了，真好" : tutorial.steps.get(step).title);
        desc.setText(missingTarget ? missingTargetHelp() : finished ? "想不起来也没关系。\n点开我，随时陪您再来一次。" : tutorial.steps.get(step).desc);
        status.setText(missingTarget ? "停在这一步，不会跳过" : paused ? "已暂停 · 准备好再继续" : feedback);
        done.setText(missingTarget ? "回到这一步" : finished ? "再学一遍" : "我做到了");
        card.findViewById(R.id.btn_help).setEnabled(!finished);
        card.findViewById(R.id.btn_ask_help).setVisibility(missingTarget ? View.VISIBLE : View.GONE);
        done.setEnabled(!paused);
        prev.setEnabled(step > 0 && !paused);
        prev.setAlpha(prev.isEnabled() ? 1f : .4f);
        pause.setText(paused ? "继续" : "暂停");
        if (paused || missingTarget) card.findViewById(R.id.secondary_controls).setVisibility(View.VISIBLE);
        card.findViewById(R.id.card_scroll).scrollTo(0, 0);
        if (card.isAttachedToWindow()) {
            fitCardHeight();
            card.requestLayout();
            card.post(() -> updateBounds(card, cardLp, "card"));
        }
    }

    private void renderBall() {
        if (ball == null) return;
        TextView label = ball.findViewById(R.id.tv_ball);
        label.setText(paused ? "稍等" : "我在");
        ball.setContentDescription(paused ? "学习已暂停，点开继续" : "学习小帮手，点开看步骤，按住可拖动");
    }

    private void advance(boolean automatic) {
        if (tutorial == null || paused) return;
        if (missingTarget) {
            if (automatic) return;
            missingTarget = false;
            feedback = "咱们仍在这一步，确认看到了再继续";
            syncRecognition();
            renderCard();
            speakCurrent("");
            return;
        }
        if (finished) {
            finished = false;
            step = 0;
            progress.setFinished(tutorial.id, false);
            feedback = "没关系，咱们再来一遍";
        } else if (step + 1 < tutorial.stepCount()) {
            step++;
            feedback = automatic ? "已看到您的操作，这一步完成啦" : "做得好，慢慢来";
        } else {
            finished = true;
            progress.setFinished(tutorial.id, true);
            feedback = "下次需要，我一直都在";
        }
        progress.save(tutorial.id, step);
        syncRecognition();
        renderCard();
        renderBall();
        speakCurrent(automatic && !finished ? "做得好，这一步完成了。" : "");
    }

    private void previous() {
        if (tutorial == null || paused || step == 0) return;
        missingTarget = false;
        finished = false;
        progress.setFinished(tutorial.id, false);
        step--;
        progress.save(tutorial.id, step);
        feedback = "再看一遍也没关系";
        syncRecognition();
        renderCard();
        speakCurrent("");
    }

    private void syncRecognition() {
        recognitionActive = running && !paused && !missingTarget && !finished && tutorial != null;
    }

    private void speakCurrent(String prefix) {
        if (tutorial == null || paused) return;
        String text = missingTarget ? "找不到也没关系。" + missingTargetHelp()
                : finished ? "您做到了，真好。下次想不起来，随时点开我，再陪您一遍。"
                : prefix + tutorial.steps.get(step).title + "。" + tutorial.steps.get(step).desc;
        long ticket = ++speechTicket;
        speakWhenReady(text, ticket, 0);
    }

    private String missingTargetHelp() {
        if (tutorial != null && "wechat_font".equals(tutorial.id)) {
            if (step == 3) return "先看页面顶部是不是「设置」。新版微信点「界面与显示」；「通用」可能只是分组标题，不用点。可以慢慢上下滑动找找。旧版若有可点击的「通用」，再进入它。";
            if (step == 4) return "先确认顶部是「界面与显示」，再找「字体大小」或「字号大小」。如果页面不一样，点「上一步」重新确认入口。找不到就先停下来，不用随便试。";
        }
        return "先确认打开了教程说的应用和页面，再慢慢上下滑动找找。软件版本不同，按钮名称和位置可能变化。可以点「上一步」重新确认，或回到应用请家人修改教程。我们会停在这里等您。";
    }

    private void askForHelp() {
        if (tutorial == null) return;
        paused = true;
        stopVoice();
        syncRecognition();
        renderBall();
        Intent ask = new Intent(this, com.teachagain.app.ai.AskActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("feedback_tutorial_id", tutorial.id)
                .putExtra("feedback_step", step);
        try {
            startActivity(ask);
            hideCard();
        } catch (RuntimeException error) {
            renderCard();
            toast("已暂停这一步。您也可以回到应用，告诉我哪里找不到。");
        }
    }

    private void speakWhenReady(String text, long ticket, int attempt) {
        if (ticket != speechTicket || !running || paused) return;
        if (voice != null && voice.isReady()) voice.speak(text);
        else if (attempt < 10) handler.postDelayed(() -> speakWhenReady(text, ticket, attempt + 1), 250);
        else toast("朗读暂时没有准备好，先看文字也可以");
    }

    private void stopVoice() { speechTicket++; if (voice != null) voice.stop(); }

    private void attachDrag(View handle, View window, WindowManager.LayoutParams lp,
                            String key, boolean snap) {
        int slop = ViewConfiguration.get(this).getScaledTouchSlop();
        handle.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY;
            int startX, startY;
            boolean moved;
            @Override public boolean onTouch(View view, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX(); downY = e.getRawY();
                        startX = lp.x; startY = lp.y; moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = Math.round(e.getRawX() - downX), dy = Math.round(e.getRawY() - downY);
                        moved |= Math.hypot(dx, dy) > slop;
                        if (moved) {
                            lp.x = startX + dx; lp.y = startY + dy;
                            updateBounds(window, lp, null);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) view.performClick();
                        else {
                            if (snap) {
                                Rect area = availableArea();
                                lp.x = lp.x + window.getWidth() / 2 < area.centerX()
                                        ? area.left : area.right - window.getWidth();
                            }
                            updateBounds(window, lp, key);
                        }
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        updateBounds(window, lp, key);
                        return true;
                    default: return false;
                }
            }
        });
    }

    private Rect availableArea() {
        int width = getResources().getDisplayMetrics().widthPixels;
        int height = getResources().getDisplayMetrics().heightPixels;
        int left = dp(4), top = dp(28), right = width - dp(4), bottom = height - dp(28);
        if (Build.VERSION.SDK_INT >= 30) {
            WindowMetrics metrics = wm.getCurrentWindowMetrics();
            Insets insets = metrics.getWindowInsets().getInsetsIgnoringVisibility(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            Rect bounds = metrics.getBounds();
            left = insets.left + dp(4); top = insets.top + dp(4);
            right = bounds.width() - insets.right - dp(4);
            bottom = bounds.height() - insets.bottom - dp(4);
        }
        return new Rect(left, top, Math.max(left + dp(100), right), Math.max(top + dp(160), bottom));
    }

    private void bound(View view, WindowManager.LayoutParams lp, int fallbackWidth, int fallbackHeight) {
        if (view == null || lp == null) return;
        Rect area = availableArea();
        int width = view.getWidth() > 0 ? view.getWidth() : fallbackWidth;
        int height = view.getHeight() > 0 ? view.getHeight() : fallbackHeight;
        lp.x = Math.max(area.left, Math.min(lp.x, area.right - width));
        lp.y = Math.max(area.top, Math.min(lp.y, area.bottom - height));
    }

    private void updateBounds(View view, WindowManager.LayoutParams lp, String key) {
        if (view == null || lp == null || !view.isAttachedToWindow()) return;
        bound(view, lp, lp.width > 0 ? lp.width : dp(64), dp(64));
        try {
            wm.updateViewLayout(view, lp);
            if (key != null) positions.edit().putInt(key + "_x", lp.x).putInt(key + "_y", lp.y).apply();
        } catch (RuntimeException ignored) { /* healthCheck will restore detached ball. */ }
    }

    /** Large font / landscape: scroll the complete card instead of losing its buttons. */
    private void fitCardHeight() {
        if (!(card instanceof ScrollView) || cardLp == null) return;
        View content = ((ScrollView) card).getChildAt(0);
        content.measure(View.MeasureSpec.makeMeasureSpec(cardLp.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        cardLp.height = Math.min(content.getMeasuredHeight(), availableArea().height() - dp(8));
    }

    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateBounds(ball, ballLp, "ball");
        if (card != null && cardLp != null) {
            Rect area = availableArea();
            cardLp.width = Math.min(dp(292), area.width() - dp(12));
            card.findViewById(R.id.card_scroll).getLayoutParams().height =
                    Math.min(dp(60), Math.max(dp(44), area.height() / 8));
            fitCardHeight();
            card.requestLayout();
            card.post(() -> updateBounds(card, cardLp, "card"));
        }
    }

    private void hideCard() {
        if (card != null) {
            if (cardLp != null) positions.edit().putInt("card_x", cardLp.x).putInt("card_y", cardLp.y).apply();
            try { wm.removeViewImmediate(card); } catch (RuntimeException ignored) { }
        }
        card = null; cardLp = null;
        // Collapsing never stops the ball or automatic guidance. Pause is a separate action.
    }
    private void removeBall() {
        if (ball != null) try { wm.removeViewImmediate(ball); } catch (RuntimeException ignored) { }
        ball = null; ballLp = null;
    }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    @Override public void onDestroy() {
        running = false; recognitionActive = false;
        stopVoice();
        handler.removeCallbacksAndMessages(null);
        hideCard(); removeBall();
        if (receiverRegistered) { unregisterReceiver(recognition); receiverRegistered = false; }
        if (voice != null) voice.shutdown();
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
