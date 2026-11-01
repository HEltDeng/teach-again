package com.teachagain.app.ai;

import com.teachagain.app.Tutorial;
import com.teachagain.app.TutorialImporter;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 根据"用哪个应用、想做什么"生成分步教程。
 *
 * 两种来源，界面必须如实标注：
 * - 本地体验（local）：未配置模型 Key 时，用本机模板/通用骨架生成，不联网、不冒充模型。
 * - 模型生成（remote）：已配置 Key 时调用 OpenAI 兼容接口，解析并严格校验 JSON。
 */
public class StepGenerator {

    public enum Source { LOCAL, REMOTE }

    public static class GenResult {
        public final boolean ok;
        public final String error;
        public final Source source;
        public final Tutorial tutorial;

        private GenResult(boolean ok, String error, Source source, Tutorial t) {
            this.ok = ok; this.error = error; this.source = source; this.tutorial = t;
        }
        static GenResult fail(String e, Source s) { return new GenResult(false, e, s, null); }
        static GenResult ok(Source s, Tutorial t) { return new GenResult(true, null, s, t); }
    }

    private static final String SYSTEM_PROMPT =
            "你是帮助中老年人学用手机的耐心助手。用户会说要用哪个应用、想做什么。"
            + "请输出严格的 JSON，不要任何多余文字，格式："
            + "{\"title\":\"教程名\",\"steps\":[{\"title\":\"这一步做什么\",\"desc\":\"在哪个应用、点哪里、看到什么\"}]}。"
            + "要求：步骤 2 到 20 步；每步只做一个动作，并说明做完后屏幕上会看到什么反馈；"
            + "语气温暖具体，每步说明不超过 200 字。"
            + "不要凭空猜测不同系统或版本的菜单路径；如果某一步在不同手机上位置可能不同，"
            + "要在说明里提醒用户留意、必要时让家人确认，不要假装你已在用户手机上验证过。"
            + "只描述让用户自己操作的引导，绝不替用户发送消息或拨打电话。";

    /** 本地体验：从整句消息识别应用与任务（单输入框聊天用）。 */
    public static GenResult generateLocalFromMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            return GenResult.fail("请说一句您想做什么，比如「我想把微信字调大」。", Source.LOCAL);
        }
        return generateLocal(message, message);
    }

    /** 判断一句话是否像"这一步找不到/不一样"的纠错反馈（而非新任务）。 */
    public static boolean looksLikeStuckFeedback(String message) {
        if (message == null) return false;
        String m = message.toLowerCase();
        String[] kws = {"找不到", "没有这个", "没有", "不一样", "对不上", "不对",
                "看不到", "没看到", "没见", "不见", "点不了", "不是这样"};
        for (String k : kws) if (m.contains(k)) return true;
        return false;
    }

    /** 模型生成：把用户整句原话交给模型识别，不要求分字段。 */
    public static GenResult generateRemoteFromMessage(ChatClient client, String baseUrl,
                                                      String apiKey, String model,
                                                      String message) {
        return generateRemote(client, baseUrl, apiKey, model, "", message);
    }

    /**
     * 本地体验：优先复用已验证的内置教程/模板；相机、系统字体给明确示例；
     * 未知任务给"待家人补全"的占位草稿，明确不假装已分析步骤。
     */
    public static GenResult generateLocal(String app, String goal) {
        String a = app == null ? "" : app.trim();
        String g = goal == null ? "" : goal.trim();
        if (a.isEmpty() && g.isEmpty()) {
            return GenResult.fail("请告诉我用哪个应用、想做什么。", Source.LOCAL);
        }
        String combined = (a + " " + g).toLowerCase();

        // 1) 微信调大字体 -> 复用内置（已真机核对的新版路径）
        if (contains(combined, "微信") && containsAny(combined, "字体", "字大", "字小", "调大", "放大")) {
            Tutorial t = findBuiltin("wechat_font");
            if (t != null) return GenResult.ok(Source.LOCAL, copyAsDraft(t, "微信调大字体"));
        }
        // 2) 微信发照片 -> 内置
        if (contains(combined, "微信") && containsAny(combined, "照片", "图片", "发图")) {
            Tutorial t = findBuiltin("wechat_photo");
            if (t != null) return GenResult.ok(Source.LOCAL, copyAsDraft(t, "给家人发照片"));
        }
        // 3) 微信视频通话 -> 内置
        if (contains(combined, "微信") && containsAny(combined, "视频", "通话", "视频通话")) {
            Tutorial t = findBuiltin("wechat_video");
            if (t != null) return GenResult.ok(Source.LOCAL, copyAsDraft(t, "发起视频通话"));
        }
        // 4) 相机拍照 -> 复用 StepTemplates photo_take
        if (containsAny(combined, "相机", "拍照", "照相")) {
            List<Tutorial.Step> steps = com.teachagain.app.StepTemplates.generate("photo_take", "");
            return GenResult.ok(Source.LOCAL, draft("用相机拍照", steps));
        }
        // 5) 手机系统设置调字体 -> 明确可编辑示例（提示不同手机位置可能不同）
        if (containsAny(combined, "设置", "系统") && containsAny(combined, "字体", "字大", "调大", "放大")) {
            List<Tutorial.Step> steps = new ArrayList<>();
            steps.add(new Tutorial.Step("打开手机「设置」",
                    "回到桌面，找到齿轮图标的「设置」，点一下打开。"));
            steps.add(new Tutorial.Step("找到「显示」或「字体与显示」",
                    "在设置里找和「显示」「字体」有关的一项。不同手机名字可能不一样，看到「显示」「字体与大小」都可以点进去。拿不准可以让家人帮忙确认。"));
            steps.add(new Tutorial.Step("找到「字体大小」",
                    "在里面找到「字体大小」或「字体与显示大小」，点进去。"));
            steps.add(new Tutorial.Step("拖动滑块调大",
                    "按住下面的小圆点往右拖，上面的字会变大。调到您看着舒服。"));
            steps.add(new Tutorial.Step("返回保存",
                    "满意了就点返回，设置一般会自动保存。回到桌面看看字是不是变大了。"));
            return GenResult.ok(Source.LOCAL, draft("把手机字体调大", steps));
        }

        // 6) 未知任务：给"待家人补全"占位草稿，不假装已分析，也不编造假应用名
        //    从整句进入时 a==g==整句，不能把整句当应用名，用识别到的已知应用或通用措辞
        String appName = detectKnownApp(combined); // 只认已知应用，否则为 null
        String opener = appName != null
                ? "打开" + appName
                : "【待家人补全】打开哪个应用";
        String openerDesc = appName != null
                ? "在桌面找到「" + appName + "」的图标，点一下打开。"
                : "请家人把这里改成要用的应用名，并写清在哪里打开。";

        String title = makeUnknownTitle(a, g);
        List<Tutorial.Step> steps = new ArrayList<>();
        steps.add(new Tutorial.Step(opener, openerDesc));
        steps.add(new Tutorial.Step("【待家人补全】这一步怎么做",
                "这台手机还没有现成的步骤。请家人照着真实操作，把这里改成具体怎么点、点完看到什么。"));
        steps.add(new Tutorial.Step("【待家人补全】下一步",
                "继续补充下一步的具体操作，确认无误后再保存给长辈用。"));
        return GenResult.ok(Source.LOCAL, draft(title, steps));
    }

    /** 只识别已知应用名，避免把整句话当成应用名。未知返回 null。 */
    private static String detectKnownApp(String combined) {
        if (combined.contains("微信")) return "微信";
        if (combined.contains("相机") || combined.contains("照相")) return "相机";
        if (combined.contains("设置")) return "设置";
        if (combined.contains("电话")) return "电话";
        if (combined.contains("短信") || combined.contains("信息")) return "信息";
        return null;
    }

    /** 未知任务的教程名：尽量用简短措辞，校验长度，不生成"打开我想…"式假名。 */
    private static String makeUnknownTitle(String a, String g) {
        String base = (g != null && !g.isEmpty()) ? g : a;
        if (base == null) base = "";
        base = base.trim();
        if (base.isEmpty()) return "待补全的教程";
        // 去掉常见口语前缀，避免"我想把…"直接做标题
        base = base.replaceAll("^(我想|我要|帮我|请|想)+", "").trim();
        if (base.isEmpty()) base = "待补全的教程";
        if (base.length() > TutorialImporter.MAX_TITLE_LEN) {
            base = base.substring(0, TutorialImporter.MAX_TITLE_LEN);
        }
        return base;
    }

    private static Tutorial findBuiltin(String id) {
        for (Tutorial t : com.teachagain.app.TutorialRepository.builtinTutorials()) {
            if (t.id.equals(id)) return t;
        }
        return null;
    }

    /** 把内置教程复制成可编辑草稿（新 id，builtin=false，不覆盖内置）。 */
    private static Tutorial copyAsDraft(Tutorial src, String title) {
        List<Tutorial.Step> steps = new ArrayList<>(src.steps);
        return draft(title, steps);
    }

    private static Tutorial draft(String title, List<Tutorial.Step> steps) {
        return new Tutorial(Tutorial.newUserId(), title, steps, false);
    }

    private static boolean contains(String s, String kw) { return s.contains(kw); }

    private static boolean containsAny(String s, String... kws) {
        for (String k : kws) if (s.contains(k)) return true;
        return false;
    }

    /**
     * 模型生成：调用接口并解析 JSON。仅在已配置 Key 时使用。
     * 解析后用 TutorialImporter.validateUntrusted 严格校验。
     */
    public static GenResult generateRemote(ChatClient client, String baseUrl, String apiKey,
                                           String model, String app, String goal) {
        String userContent = "应用：" + (app == null ? "" : app.trim())
                + "\n想做的事：" + (goal == null ? "" : goal.trim());
        String content;
        try {
            content = client.complete(baseUrl, apiKey, model, SYSTEM_PROMPT, userContent);
        } catch (ChatClient.ChatException e) {
            return GenResult.fail(e.getMessage(), Source.REMOTE);
        }
        Tutorial parsed = parseJsonTutorial(content);
        if (parsed == null) {
            return GenResult.fail("模型返回的内容无法识别为步骤，请重试或改用本地体验。", Source.REMOTE);
        }
        TutorialImporter.Result v =
                TutorialImporter.validateUntrusted(parsed.title, parsed.steps);
        if (!v.ok) {
            return GenResult.fail("模型返回的步骤不合要求：" + v.error, Source.REMOTE);
        }
        return GenResult.ok(Source.REMOTE, v.tutorial);
    }

    /** 从模型文本里提取 JSON 并转成 Tutorial（宽松定位 JSON 块）。 */
    private static Tutorial parseJsonTutorial(String content) {
        if (content == null) return null;
        String json = content.trim();
        // 去掉可能的 ```json 包裹
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start < 0 || end <= start) return null;
        json = json.substring(start, end + 1);
        try {
            JSONObject o = new JSONObject(json);
            String title = o.optString("title", "");
            JSONArray arr = o.getJSONArray("steps");
            List<Tutorial.Step> steps = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject so = arr.getJSONObject(i);
                steps.add(new Tutorial.Step(
                        so.optString("title", ""),
                        so.optString("desc", "")));
            }
            return new Tutorial("tmp", title, steps, false);
        } catch (Exception e) {
            return null;
        }
    }
}
