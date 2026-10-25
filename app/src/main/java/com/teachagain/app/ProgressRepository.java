package com.teachagain.app;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 进度持久化：记住当前教程 id 和到了第几步。
 * 重启后能恢复。
 */
public class ProgressRepository {

    private static final String PREFS = "teach_progress";
    private static final String KEY_TUTORIAL = "current_tutorial";
    private static final String KEY_STEP = "current_step";
    // 每个教程的"是否已完成" + "学到第几步"按 id 分别保存，便于断点续学
    private static final String PREFIX_STEP = "step_";
    private static final String PREFIX_DONE = "done_";

    private final SharedPreferences prefs;

    public ProgressRepository(Context ctx) {
        prefs = ctx.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** 保存"当前正在学的教程"及其步骤，同时按 id 记录每个教程的进度。 */
    public void save(String tutorialId, int stepIndex) {
        SharedPreferences.Editor e = prefs.edit()
                .putString(KEY_TUTORIAL, tutorialId)
                .putInt(KEY_STEP, stepIndex);
        if (tutorialId != null) {
            e.putInt(PREFIX_STEP + tutorialId, stepIndex);
        }
        e.apply();
    }

    public String getCurrentTutorialId() {
        return prefs.getString(KEY_TUTORIAL, null);
    }

    public int getCurrentStep() {
        return prefs.getInt(KEY_STEP, 0);
    }

    /** 某个教程上次学到第几步（没学过返回 0）。 */
    public int getStepFor(String tutorialId) {
        if (tutorialId == null) return 0;
        return prefs.getInt(PREFIX_STEP + tutorialId, 0);
    }

    /** 标记某教程已完成 / 未完成。 */
    public void setFinished(String tutorialId, boolean finished) {
        if (tutorialId == null) return;
        prefs.edit().putBoolean(PREFIX_DONE + tutorialId, finished).apply();
    }

    public boolean isFinished(String tutorialId) {
        if (tutorialId == null) return false;
        return prefs.getBoolean(PREFIX_DONE + tutorialId, false);
    }

    public void clear() {
        prefs.edit().remove(KEY_TUTORIAL).remove(KEY_STEP).apply();
    }
}
