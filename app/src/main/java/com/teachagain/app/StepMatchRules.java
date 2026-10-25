package com.teachagain.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 只读页面匹配规则（精确版，支持新旧版微信）。
 *
 * 设计原则：
 * - 仅对"有可靠规则"的步骤给出完成证据；没把握的步骤返回 null（保持手动确认）。
 * - 每步可配置多组候选特征(candidate groups)，命中任一组即可，用于兼容新旧页面。
 *   每组内是「必需特征 allOf」，必须全部出现。
 * - 另有全局排除词 noneOf：命中任一则整体否决，避免误判。
 * - 只用页面可见控件的精确/短文案，不读聊天长句、不保存节点、不联网、不点击。
 *
 * 判定模型：教程"第 N 步"完成 = 当前页面已是"第 N+1 步才会出现的那一页"。
 */
public final class StepMatchRules {

    private StepMatchRules() {}

    /** 一组"必需同时出现"的特征。 */
    private static class Group {
        final List<String> allOf;
        Group(String... items) { this.allOf = Arrays.asList(items); }
    }

    public static class Rule {
        public final String tutorialId;
        public final int stepIndex;
        final List<Group> candidates; // 命中任一组即算
        final List<String> noneOf;    // 命中任一则否决
        public final String evidenceName;

        Rule(String tutorialId, int stepIndex, String evidenceName,
             List<Group> candidates, String[] noneOf) {
            this.tutorialId = tutorialId;
            this.stepIndex = stepIndex;
            this.evidenceName = evidenceName;
            this.candidates = candidates;
            this.noneOf = noneOf == null ? Arrays.asList() : Arrays.asList(noneOf);
        }
    }

    private static final Map<String, Map<Integer, Rule>> RULES = new HashMap<>();
    private static final String[] NONE = new String[0];

    static {
        // ========== 微信调大字体：wechat_font ==========
        // 步骤：0 打开微信 / 1 点"我" / 2 点"设置" / 3 点"界面与显示(新)/通用(旧)" /
        //       4 点"字体大小" / 5 拖滑块 / 6 点完成
        //
        // 真机(新版)：我→设置→界面与显示→字体大小→右上角完成
        //   设置页可见：设置、账号安全、个人信息与权限、通知、界面与显示、存储空间、更多
        //   界面与显示页：界面与显示、深色模式、支持横屏模式、字体大小、多语言、翻译
        //   字体大小页：字体大小、完成、预览字体大小、拖动下面的滑块，可设置字体大小、标准

        // 第0步完成=微信主框架（四个 tab 同时出现）
        add(new Rule("wechat_font", 0, "微信首页",
                groups(g("微信", "通讯录", "发现", "我")), NONE));

        // 第1步完成=已进入"我"页；排除设置页/界面页/字体页特征
        add(new Rule("wechat_font", 1, "我的页面",
                groups(g("收藏", "设置")),
                new String[]{"账号安全", "账号与安全", "字体大小", "深色模式",
                        "预览字体大小", "界面与显示"}));

        // 第2步完成=已进入"设置"页。
        // 新版候选：账号安全 + 存储空间（设置页独有，界面页没有）
        // 旧版候选：账号与安全 + 通用
        // 排除界面与显示页/字体页特征，避免把下一页误判成设置页
        add(new Rule("wechat_font", 2, "设置页",
                groups(
                        g("账号安全", "存储空间"),
                        g("账号与安全", "通用")),
                new String[]{"深色模式", "多语言", "预览字体大小", "拖动下面的滑块"}));

        // 第3步完成=已进入"界面与显示/通用"页。
        // 新版候选：深色模式 + 多语言（设置页都没有这两项）
        // 旧版候选：字体大小 + 多语言
        // 排除设置页特征(账号安全/存储空间)与字体页特征(预览字体大小)
        add(new Rule("wechat_font", 3, "界面与显示/通用页",
                groups(
                        g("深色模式", "多语言"),
                        g("字体大小", "多语言")),
                new String[]{"预览字体大小", "拖动下面的滑块",
                        "账号安全", "存储空间", "个人信息与权限"}));

        // 第4步完成=字体大小页：该页独有"预览字体大小"
        add(new Rule("wechat_font", 4, "字体大小页",
                groups(g("预览字体大小")),
                NONE));

        // 第5步拖滑块、第6步点完成无可靠纯文本证据 -> 保持手动确认

        // ========== 给家人发照片：wechat_photo ==========
        add(new Rule("wechat_photo", 0, "微信首页",
                groups(g("微信", "通讯录", "发现", "我")), NONE));
        // 第2步完成=聊天功能面板：相册+拍摄+视频通话 同时出现
        add(new Rule("wechat_photo", 2, "聊天功能面板",
                groups(g("相册", "拍摄", "视频通话")),
                new String[]{"所有图片", "最近项目"}));
        // 第3步完成=相册选择页
        add(new Rule("wechat_photo", 3, "相册选择页",
                groups(g("所有图片")), NONE));

        // ========== 发起视频通话：wechat_video ==========
        add(new Rule("wechat_video", 0, "微信首页",
                groups(g("微信", "通讯录", "发现", "我")), NONE));
        // 第2步完成=聊天功能面板（有相册/拍摄面板项），区别于通话方式弹层
        add(new Rule("wechat_video", 2, "聊天功能面板",
                groups(g("视频通话", "相册", "拍摄")), NONE));
        // 第3步完成=通话方式选择弹层：语音通话+视频通话，但没有相册/拍摄
        add(new Rule("wechat_video", 3, "通话方式选择",
                groups(g("语音通话", "视频通话")),
                new String[]{"相册", "拍摄", "位置"}));
    }

    private static Group g(String... items) { return new Group(items); }

    private static List<Group> groups(Group... gs) { return Arrays.asList(gs); }

    private static void add(Rule r) {
        Map<Integer, Rule> m = RULES.get(r.tutorialId);
        if (m == null) {
            m = new HashMap<>();
            RULES.put(r.tutorialId, m);
        }
        m.put(r.stepIndex, r);
    }

    public static boolean hasRule(String tutorialId, int stepIndex) {
        Map<Integer, Rule> m = RULES.get(tutorialId);
        return m != null && m.containsKey(stepIndex);
    }

    /**
     * 判断该步是否有完成证据。
     * 规则：先过 noneOf（命中任一即否决），再看任一候选组的 allOf 是否全部命中。
     * @param visibleTexts 仅应包含 isVisibleToUser 的精确/短控件文案
     * @return 命中的证据页面名；无把握返回 null。
     */
    public static String evidenceForStep(String tutorialId, int stepIndex,
                                         List<String> visibleTexts) {
        Map<Integer, Rule> m = RULES.get(tutorialId);
        if (m == null) return null;
        Rule r = m.get(stepIndex);
        if (r == null || visibleTexts == null) return null;

        for (String bad : r.noneOf) {
            if (containsExact(visibleTexts, bad)) return null;
        }
        for (Group grp : r.candidates) {
            boolean all = true;
            for (String need : grp.allOf) {
                if (!containsExact(visibleTexts, need)) { all = false; break; }
            }
            if (all) return r.evidenceName;
        }
        return null;
    }

    /**
     * 精确匹配：控件文案等于关键词，或在"短文案(<=12字)"内包含关键词。
     * 聊天长句不参与包含匹配，避免把句子里的"设置/界面与显示"误当控件。
     */
    private static boolean containsExact(List<String> texts, String keyword) {
        for (String t : texts) {
            if (t == null) continue;
            if (t.equals(keyword)) return true;
            if (t.length() <= 12 && t.contains(keyword)) return true;
        }
        return false;
    }

    public static List<Integer> supportedSteps(String tutorialId) {
        List<Integer> out = new ArrayList<>();
        Map<Integer, Rule> m = RULES.get(tutorialId);
        if (m != null) out.addAll(m.keySet());
        return out;
    }
}
