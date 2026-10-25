package com.teachagain.app;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayList;
import java.util.List;
import com.teachagain.app.ai.OcrPageRecognizer;

/** Read-only page recognition. Text first; optional local OCR of the target window. */
public class GuideAccessibilityService extends AccessibilityService {
    public static final String ACTION_AUTO_ADVANCE = "com.teachagain.AUTO_ADVANCE";
    public static final String EXTRA_TUTORIAL_ID = "tutorial_id";
    public static final String EXTRA_EXPECTED_STEP = "expected_step";
    public static final String EXTRA_EVIDENCE = "evidence";
    private static final String WECHAT_PKG = "com.tencent.mm";
    private final Handler main = new Handler(Looper.getMainLooper());
    private ProgressRepository progress;
    private AutoFollowPrefs prefs;
    private TutorialRepository tutorials;
    private OcrPageRecognizer ocr;
    private long lastSentAt, pageGeneration;
    private String lastSentKey;
    private boolean connected;
    private final Runnable periodic = new Runnable() {
        @Override public void run() {
            if (!connected) return;
            inspectCurrentPage();
            main.postDelayed(this, 1000);
        }
    };
    private final Runnable afterEvent = this::inspectCurrentPage;

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        progress = new ProgressRepository(this);
        prefs = new AutoFollowPrefs(this);
        tutorials = new TutorialRepository(this);
        if (ocr != null) ocr.close();
        ocr = new OcrPageRecognizer(this);
        connected = true;
        main.removeCallbacks(periodic);
        main.post(periodic);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null
                || !WECHAT_PKG.contentEquals(event.getPackageName())) return;
        pageGeneration++;
        main.removeCallbacks(afterEvent);
        main.postDelayed(afterEvent, 180);
    }

    private boolean mayRecognize() {
        return connected && prefs != null && prefs.isEnabled()
                && FloatingBallService.isRecognitionActive();
    }

    private void inspectCurrentPage() {
        if (!mayRecognize()) return;
        String tutorialId = progress.getCurrentTutorialId();
        if (tutorialId == null || progress.isFinished(tutorialId)) return;
        int step = progress.getStepFor(tutorialId);
        String ruleId = TutorialRuleResolver.resolve(tutorials.getById(tutorialId));
        if (ruleId == null || !StepMatchRules.hasRule(ruleId, step)) return;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        List<String> texts = new ArrayList<>();
        int windowId;
        try {
            if (root.getPackageName() == null || !WECHAT_PKG.contentEquals(root.getPackageName())) return;
            windowId = root.getWindowId();
            collectVisibleText(root, texts, 0, new int[]{0});
        } catch (RuntimeException ignored) {
            return;
        } finally {
            root.recycle();
        }
        String evidence = StepMatchRules.evidenceForStep(ruleId, step, texts);
        if (evidence != null) {
            maybeSendAdvance(tutorialId, step, evidence);
            return;
        }
        if (Build.VERSION.SDK_INT < 34 || !prefs.isOcrEnabled() || ocr == null) return;
        final long generation = pageGeneration;
        final long startedAt = SystemClock.uptimeMillis();
        ocr.recognizeWindow(windowId, words -> {
            if (!mayRecognize() || !prefs.isOcrEnabled() || pageGeneration != generation
                    || SystemClock.uptimeMillis() - startedAt > 3000
                    || !tutorialId.equals(progress.getCurrentTutorialId())
                    || step != progress.getStepFor(tutorialId)) return;
            AccessibilityNodeInfo now = getRootInActiveWindow();
            if (now == null) return;
            try {
                if (now.getWindowId() != windowId || now.getPackageName() == null
                        || !WECHAT_PKG.contentEquals(now.getPackageName())) return;
            } finally { now.recycle(); }
            List<String> normalized = new ArrayList<>();
            if (words != null) for (String word : words) {
                if (word != null && word.length() <= 60) normalized.add(word.replaceAll("\\s+", ""));
            }
            String found = StepMatchRules.evidenceForStep(ruleId, step, normalized);
            if (found != null) maybeSendAdvance(tutorialId, step, "OCR · " + found);
        });
    }

    private void maybeSendAdvance(String tutorialId, int step, String evidence) {
        if (!mayRecognize()) return;
        long now = SystemClock.uptimeMillis();
        String key = tutorialId + "#" + step;
        if (key.equals(lastSentKey) && now - lastSentAt < 1200) return;
        lastSentKey = key;
        lastSentAt = now;
        Intent intent = new Intent(ACTION_AUTO_ADVANCE).setPackage(getPackageName());
        intent.putExtra(EXTRA_TUTORIAL_ID, tutorialId);
        intent.putExtra(EXTRA_EXPECTED_STEP, step);
        intent.putExtra(EXTRA_EVIDENCE, evidence);
        sendBroadcast(intent);
    }

    private void collectVisibleText(AccessibilityNodeInfo node, List<String> out, int depth, int[] visited) {
        if (node == null || depth > 40 || ++visited[0] > 400) return;
        if (node.isVisibleToUser()) {
            CharSequence text = node.getText(), desc = node.getContentDescription();
            if (text != null && text.length() > 0 && text.length() <= 40) out.add(text.toString());
            if (desc != null && desc.length() > 0 && desc.length() <= 40) out.add(desc.toString());
        }
        for (int i = 0; i < node.getChildCount() && visited[0] < 400; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            try { collectVisibleText(child, out, depth + 1, visited); }
            finally { child.recycle(); }
        }
    }

    @Override public void onInterrupt() { main.removeCallbacks(afterEvent); }
    @Override public void onDestroy() {
        connected = false;
        main.removeCallbacksAndMessages(null);
        if (ocr != null) ocr.close();
        super.onDestroy();
    }
}
