import com.teachagain.app.Tutorial;
import com.teachagain.app.TutorialImporter;
import com.teachagain.app.StepTemplates;
import com.teachagain.app.TutorialRepository;
import com.teachagain.app.TutorialRuleResolver;
import com.teachagain.app.ai.StepGenerator;
import java.util.List;

/** Host-side checks of the production parser; no emulator or network needed. */
public final class TutorialImportChecks {
    private static int checks;
    private static void check(boolean value, String name) {
        checks++;
        if (!value) throw new AssertionError(name);
    }
    public static void main(String[] args) {
        check(!TutorialImporter.parse(null).ok, "empty input rejected");
        check(!TutorialImporter.parse("hello").ok, "unrelated text rejected");
        check(!TutorialImporter.parse("x".repeat(8001)).ok, "oversized input rejected");
        String valid = "教程名称：给妈妈的教程\n第1步：打开微信 | 点绿色图标\n第2步：找到我 | 点右下角的我";
        TutorialImporter.Result result = TutorialImporter.parse(valid);
        check(result.ok && result.tutorial.stepCount() == 2, "valid tutorial accepted");
        TutorialImporter.Result roundTrip = TutorialImporter.parse(TutorialImporter.export(result.tutorial));
        check(roundTrip.ok && roundTrip.tutorial.steps.get(1).desc.equals("点右下角的我"), "plain text round trip");
        StringBuilder tooMany = new StringBuilder("教程名称：长教程\n");
        for (int i = 1; i <= 21; i++) tooMany.append("第").append(i).append("步：做一步 | 说明\n");
        check(!TutorialImporter.parse(tooMany.toString()).ok, "too many steps rejected");
        Tutorial multiline = new Tutorial("test", "换行和标点", List.of(
            new Tutorial.Step("第一步", "点打开\n然后等画面出现"),
            new Tutorial.Step("第二步", "看见 A | B 后继续")), false);
        TutorialImporter.Result multi = TutorialImporter.parse(TutorialImporter.export(multiline));
        check(multi.ok && multi.tutorial.steps.get(0).desc.equals(multiline.steps.get(0).desc)
            && multi.tutorial.steps.get(1).desc.equals(multiline.steps.get(1).desc), "multiline descriptions survive sharing");
        check(!TutorialImporter.parse(valid.replace("给妈妈的教程", "字".repeat(31))).ok,
                "long tutorial title rejected");
        check(!TutorialImporter.parse(valid.replace("打开微信", "字".repeat(31))).ok,
                "long step title rejected");
        check(!TutorialImporter.parse(valid.replace("点绿色图标", "字".repeat(201))).ok,
                "long description rejected");
        check(TutorialImporter.parse(valid.replace("\n", "\r\n")).ok,
                "Windows line endings accepted");
        for (StepTemplates.TaskOption option : StepTemplates.taskOptions()) {
            Tutorial draft = new Tutorial("local", option.label,
                    StepTemplates.generate(option.key, "家人"), false);
            TutorialImporter.Result parsed = TutorialImporter.parse(TutorialImporter.export(draft));
            check(parsed.ok && parsed.tutorial.stepCount() == draft.stepCount(),
                    "local template can be shared and imported: " + option.key);
        }
        for (Tutorial builtin : TutorialRepository.builtinTutorials()) {
            TutorialImporter.Result parsed = TutorialImporter.parse(TutorialImporter.export(builtin));
            check(parsed.ok && !parsed.tutorial.builtin && !parsed.tutorial.id.equals(builtin.id),
                    "shared builtin imports as a separate editable tutorial: " + builtin.id);
            if (builtin.id.equals("wechat_font")) {
                check(builtin.steps.get(3).title.contains("界面与显示"),
                        "font tutorial uses the display settings entry verified on the device");
            }
        }
        check(!TutorialImporter.validateUntrusted("空步骤", null).ok,
                "generated missing steps rejected");
        check(!TutorialImporter.validateUntrusted("空标题", List.of(
                new Tutorial.Step("", "说明"), new Tutorial.Step("第二步", "说明"))).ok,
                "generated empty step title rejected");
        TutorialImporter.Result generated = TutorialImporter.validateUntrusted("新教程", List.of(
                new Tutorial.Step("第一步", "说明一"), new Tutorial.Step("第二步", "说明二")));
        check(generated.ok && !generated.tutorial.builtin && generated.tutorial.id.startsWith("user_"),
                "generated tutorial receives editable user identity");
        StepGenerator.GenResult camera = StepGenerator.generateLocal("相机", "拍照");
        check(camera.ok && camera.source == StepGenerator.Source.LOCAL && camera.tutorial.stepCount() == 3
                        && camera.tutorial.steps.get(2).desc.contains("圆圈"),
                "camera request produces concrete steps without API key");
        StepGenerator.GenResult font = StepGenerator.generateLocal("微信", "字体调大");
        check(font.ok && font.tutorial.steps.get(3).title.contains("界面与显示"),
                "generated WeChat font draft preserves verified path");
        StepGenerator.GenResult unknown = StepGenerator.generateLocal("陌生应用", "一个未知功能");
        check(unknown.ok && unknown.tutorial.steps.get(1).title.contains("待家人补全"),
                "unknown task does not pretend to be an analyzed tutorial");
        check(!StepGenerator.generateLocal("", "").ok, "empty generation request rejected");
        StepGenerator.GenResult spokenFont = StepGenerator.generateLocalFromMessage("我想把微信里的字调大一点");
        check(spokenFont.ok && spokenFont.tutorial.steps.get(3).title.contains("界面与显示"),
                "single natural message identifies WeChat font task");
        StepGenerator.GenResult spokenCamera = StepGenerator.generateLocalFromMessage("我想用相机拍照");
        check(spokenCamera.ok && spokenCamera.tutorial.stepCount() == 3
                && spokenCamera.tutorial.title.equals("用相机拍照"),
                "single natural message identifies camera task");
        check(!StepGenerator.generateLocalFromMessage("  ").ok,
                "blank chat message rejected");
        check(StepGenerator.looksLikeStuckFeedback("我这里没有这个按钮")
                && StepGenerator.looksLikeStuckFeedback("这一步找不到")
                && !StepGenerator.looksLikeStuckFeedback("我想用相机拍照"),
                "stuck feedback distinguished from concrete new task");
        StepGenerator.GenResult unknownSentence = StepGenerator.generateLocalFromMessage("我想在陌生软件里打开一个未知功能");
        check(unknownSentence.ok && unknownSentence.tutorial.steps.get(0).title.contains("待家人补全"),
                "unknown utterance is not invented as an app name");
        check(TutorialImporter.validateUntrusted(unknownSentence.tutorial.title,
                unknownSentence.tutorial.steps).ok, "unknown draft remains within import limits");
        check("wechat_font".equals(TutorialRuleResolver.resolve(spokenFont.tutorial)),
                "generated exact copy retains page recognition despite user identity");
        Tutorial renamedCopy = new Tutorial("user_copy", "给妈妈调字", spokenFont.tutorial.steps, false);
        check("wechat_font".equals(TutorialRuleResolver.resolve(renamedCopy)),
                "renaming a tutorial does not lose verified recognition");
        java.util.ArrayList<Tutorial.Step> changed = new java.util.ArrayList<>(spokenFont.tutorial.steps);
        changed.set(3, new Tutorial.Step("打开别的页面", "点不同按钮"));
        check(TutorialRuleResolver.resolve(new Tutorial("wechat_font", "假的字体教程", changed, false)) == null,
                "changed steps never inherit rules from a spoofed builtin identity");
        check(TutorialRuleResolver.resolve(camera.tutorial) == null,
                "camera draft does not receive WeChat rules");
        System.out.println("PASS: " + checks + " tutorial import checks");
    }
}
