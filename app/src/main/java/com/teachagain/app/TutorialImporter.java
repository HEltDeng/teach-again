package com.teachagain.app;

import java.util.ArrayList;
import java.util.List;

/**
 * 解析并严格校验导入/粘贴的教程文本。
 * 格式：
 *   教程名称：XXX
 *   第1步：标题 | 说明
 *   第2步：标题 | 说明
 *
 * 校验规则：
 *   - 必须有教程名称，长度 1–30
 *   - 步骤数量 2–20
 *   - 每步标题 1–30，说明 1–200
 *   - 整体文本长度上限 8000 字符
 */
public class TutorialImporter {

    public static final int MAX_TEXT_LEN = 8000;
    public static final int MAX_TITLE_LEN = 30;
    public static final int MIN_STEPS = 2;
    public static final int MAX_STEPS = 20;
    public static final int MAX_STEP_TITLE = 30;
    public static final int MAX_STEP_DESC = 200;

    public static class Result {
        public final boolean ok;
        public final String error;     // 失败原因（给用户看）
        public final Tutorial tutorial; // 成功时的教程

        private Result(boolean ok, String error, Tutorial t) {
            this.ok = ok;
            this.error = error;
            this.tutorial = t;
        }

        public static Result fail(String msg) {
            return new Result(false, msg, null);
        }

        public static Result success(Tutorial t) {
            return new Result(true, null, t);
        }
    }

    /**
     * 校验一份教程（来自 AI 生成/远程导入等不可信来源）。
     * 规则：标题 1–30；步骤 2–20；每步标题 1–30、说明 1–200；
     * 绝不接受 builtin=true，也不允许覆盖内置 ID。校验后重新生成安全 id。
     */
    public static Result validateUntrusted(String title, List<Tutorial.Step> rawSteps) {
        if (title == null || title.trim().isEmpty()) {
            return Result.fail("教程名称不能为空。");
        }
        title = title.trim();
        if (title.length() > MAX_TITLE_LEN) {
            return Result.fail("教程名称太长（超过 " + MAX_TITLE_LEN + " 字）。");
        }
        if (rawSteps == null || rawSteps.size() < MIN_STEPS) {
            return Result.fail("步骤太少，至少要 " + MIN_STEPS + " 步。");
        }
        if (rawSteps.size() > MAX_STEPS) {
            return Result.fail("步骤太多（超过 " + MAX_STEPS + " 步）。");
        }
        List<Tutorial.Step> steps = new ArrayList<>();
        for (Tutorial.Step s : rawSteps) {
            if (s == null) return Result.fail("有一步内容无效。");
            String st = s.title == null ? "" : s.title.trim();
            String sd = s.desc == null ? "" : s.desc.trim();
            if (st.isEmpty()) return Result.fail("有一步缺少标题。");
            if (sd.isEmpty()) sd = st;
            if (st.length() > MAX_STEP_TITLE) {
                return Result.fail("某一步标题太长（超过 " + MAX_STEP_TITLE + " 字）。");
            }
            if (sd.length() > MAX_STEP_DESC) {
                return Result.fail("某一步说明太长（超过 " + MAX_STEP_DESC + " 字）。");
            }
            steps.add(new Tutorial.Step(st, sd));
        }
        // 永远作为用户教程（builtin=false），生成全新安全 id，绝不覆盖内置
        String id = Tutorial.newUserId();
        return Result.success(new Tutorial(id, title, steps, false));
    }

    public static Result parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return Result.fail("内容是空的，请粘贴教程文字。");
        }
        if (raw.length() > MAX_TEXT_LEN) {
            return Result.fail("内容太长了（超过 " + MAX_TEXT_LEN + " 字），请精简后再导入。");
        }

        String[] lines = raw.replace("\r\n", "\n").split("\n");
        String title = null;
        List<Tutorial.Step> steps = new ArrayList<>();

        for (String lineRaw : lines) {
            String line = lineRaw.trim();
            if (line.isEmpty()) continue;

            if (line.startsWith("教程名称：") || line.startsWith("教程名称:")) {
                title = afterColon(line);
            } else if (isStepLine(line)) {
                String body = afterColon(line);
                String[] parts = body.split("\\|", 2);
                String stepTitle = parts[0].trim();
                // 说明中的续行以字面量 \n 表示，解析时还原成真实换行
                String stepDesc = parts.length > 1 ? unescape(parts[1].trim()) : "";
                if (stepTitle.isEmpty()) {
                    return Result.fail("有一步没有写标题，请检查格式：第N步：标题 | 说明");
                }
                if (stepDesc.isEmpty()) {
                    // 允许说明为空时用标题兜底，但给提示更友好——这里直接用标题
                    stepDesc = stepTitle;
                }
                if (stepTitle.length() > MAX_STEP_TITLE) {
                    return Result.fail("某一步的标题太长（超过 " + MAX_STEP_TITLE + " 字）。");
                }
                if (stepDesc.length() > MAX_STEP_DESC) {
                    return Result.fail("某一步的说明太长（超过 " + MAX_STEP_DESC + " 字）。");
                }
                steps.add(new Tutorial.Step(stepTitle, stepDesc));
            }
            // 其它行忽略
        }

        if (title == null || title.isEmpty()) {
            return Result.fail("没找到教程名称。请在第一行写：教程名称：XXX");
        }
        if (title.length() > MAX_TITLE_LEN) {
            return Result.fail("教程名称太长（超过 " + MAX_TITLE_LEN + " 字）。");
        }
        if (steps.size() < MIN_STEPS) {
            return Result.fail("步骤太少，至少要 " + MIN_STEPS + " 步。格式：第1步：标题 | 说明");
        }
        if (steps.size() > MAX_STEPS) {
            return Result.fail("步骤太多（超过 " + MAX_STEPS + " 步），请精简。");
        }

        String id = Tutorial.newUserId();
        return Result.success(new Tutorial(id, title, steps, false));
    }

    private static boolean isStepLine(String line) {
        // 匹配 "第1步：" / "第 1 步:" 等
        return line.matches("^第\\s*\\d+\\s*步\\s*[：:].*");
    }

    private static String afterColon(String line) {
        int idx = line.indexOf('：');
        if (idx < 0) idx = line.indexOf(':');
        if (idx < 0) return "";
        return line.substring(idx + 1).trim();
    }

    /**
     * 把教程导出成可分享的文本（供"家人教一次"分享用）。
     * 说明中的真实换行转义为字面量 \n，保证 export→parse 往返不丢行。
     */
    public static String export(Tutorial t) {
        StringBuilder sb = new StringBuilder();
        sb.append("教程名称：").append(t.title).append("\n");
        for (int i = 0; i < t.steps.size(); i++) {
            Tutorial.Step s = t.steps.get(i);
            sb.append("第").append(i + 1).append("步：")
              .append(escape(s.title)).append(" | ").append(escape(s.desc)).append("\n");
        }
        return sb.toString();
    }

    // 转义：反斜杠先行，再把换行转为字面量 \n，避免歧义
    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\r\n", "\\n")
                .replace("\n", "\\n");
    }

    // 还原：先处理 \n（非 \\n 前缀的），再还原反斜杠
    private static String unescape(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                if (next == 'n') {
                    out.append('\n');
                    i++;
                    continue;
                } else if (next == '\\') {
                    out.append('\\');
                    i++;
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
    }
}
