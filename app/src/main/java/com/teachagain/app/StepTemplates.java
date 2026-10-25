package com.teachagain.app;

import java.util.ArrayList;
import java.util.List;

/**
 * 本地步骤模板库。"家人教一次"根据所选任务生成分步草稿，供家人编辑。
 * 纯本地模板，不调用任何在线 AI。界面上称为"生成步骤"。
 */
public class StepTemplates {

    public static class TaskOption {
        public final String key;
        public final String label;

        public TaskOption(String key, String label) {
            this.key = key;
            this.label = label;
        }
    }

    public static List<TaskOption> taskOptions() {
        List<TaskOption> list = new ArrayList<>();
        list.add(new TaskOption("wechat_voice", "教发微信语音消息"));
        list.add(new TaskOption("wechat_group", "教在群里发消息"));
        list.add(new TaskOption("phone_call", "教打电话给家人"));
        list.add(new TaskOption("photo_take", "教用相机拍照"));
        list.add(new TaskOption("custom", "自己写（空白开始）"));
        return list;
    }

    /**
     * 根据任务 key 和称呼生成步骤草稿。
     * @param addressee 称呼，如"女儿""儿子"，可为空
     */
    public static List<Tutorial.Step> generate(String taskKey, String addressee) {
        String who = (addressee == null || addressee.trim().isEmpty())
                ? "家人" : addressee.trim();
        List<Tutorial.Step> steps = new ArrayList<>();

        switch (taskKey) {
            case "wechat_voice":
                steps.add(new Tutorial.Step("打开微信进入聊天",
                        "点绿色微信图标，点开要发语音的人的聊天。"));
                steps.add(new Tutorial.Step("点输入框左边的话筒",
                        "在最下面输入框的左边，有个话筒小图标，点一下。"));
                steps.add(new Tutorial.Step("按住「按住说话」",
                        "出现「按住说话」的长条，用手指一直按住它，对着手机说话。"));
                steps.add(new Tutorial.Step("松手就发出去了",
                        "说完把手指松开，语音就发给对方了。"));
                break;
            case "wechat_group":
                steps.add(new Tutorial.Step("打开微信",
                        "点绿色微信图标。"));
                steps.add(new Tutorial.Step("点开群聊",
                        "在聊天列表里找到那个群，点进去。"));
                steps.add(new Tutorial.Step("在输入框打字",
                        "点最下面的输入框，用键盘打出想说的话。"));
                steps.add(new Tutorial.Step("点「发送」",
                        "打完点右边的「发送」，群里大家就能看到了。"));
                break;
            case "phone_call":
                steps.add(new Tutorial.Step("打开电话",
                        "在桌面找到「电话」图标，点一下。"));
                steps.add(new Tutorial.Step("点「联系人」",
                        "点下面的「联系人」，找到要打给谁。"));
                steps.add(new Tutorial.Step("点名字再点绿色电话",
                        "点一下" + who + "的名字，再点绿色的电话图标，就拨出去了。"));
                steps.add(new Tutorial.Step("打完点红色挂断",
                        "说完话，点红色的电话图标挂断。"));
                break;
            case "photo_take":
                steps.add(new Tutorial.Step("打开相机",
                        "在桌面找到「相机」图标，点一下。"));
                steps.add(new Tutorial.Step("对准要拍的东西",
                        "把手机举起来，屏幕里看到想拍的画面。"));
                steps.add(new Tutorial.Step("点中间的大圆按钮",
                        "点屏幕下方中间那个大圆圈，就拍好一张照片了。"));
                break;
            default:
                // custom：给一个起始模板
                steps.add(new Tutorial.Step("第一步", "在这里写第一步要做什么。"));
                steps.add(new Tutorial.Step("第二步", "在这里写第二步要做什么。"));
                break;
        }
        return steps;
    }
}
