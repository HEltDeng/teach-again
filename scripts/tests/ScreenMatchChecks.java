import com.teachagain.app.StepMatchRules;
import java.util.List;

/** Real page fixtures and adversarial chat text: avoid advancing from incidental words. */
public final class ScreenMatchChecks {
    static int count;
    static void expect(boolean expected, String tutorial, int step, List<String> text, String label) {
        count++;
        boolean actual = StepMatchRules.evidenceForStep(tutorial, step, text) != null;
        if (actual != expected) throw new AssertionError(label + ": expected " + expected);
    }
    public static void main(String[] args) {
        List<String> home = List.of("微信", "通讯录", "发现", "我");
        List<String> me = List.of("服务", "收藏", "朋友圈", "设置");
        List<String> settings = List.of("设置", "账号安全", "个人信息与权限", "通知", "界面与显示", "存储空间", "更多");
        List<String> display = List.of("界面与显示", "深色模式", "支持横屏模式", "字体大小", "多语言", "翻译");
        List<String> font = List.of("字体大小", "完成", "预览字体大小", "拖动下面的滑块，可设置字体大小", "标准");
        expect(true, "wechat_font", 0, home, "home is recognized");
        expect(false, "wechat_font", 1, home, "home is not me");
        expect(true, "wechat_font", 1, me, "me is recognized");
        expect(true, "wechat_font", 2, settings, "new settings page is recognized");
        expect(false, "wechat_font", 3, settings, "an entry label is not the destination page");
        expect(true, "wechat_font", 3, display, "display page is recognized");
        expect(false, "wechat_font", 4, display, "font entry is not font preview");
        expect(true, "wechat_font", 4, font, "font preview is recognized");
        for (int step = 0; step < 5; step++) {
            expect(false, "wechat_font", step,
                List.of("妈妈记得点设置，进入界面与显示，就可以看到字体大小和预览", "按住说话"),
                "chat sentence must not trigger step " + step);
        }
        List<String> plus = List.of("相册", "拍摄", "视频通话", "位置");
        expect(false, "wechat_video", 3, plus, "plus panel is not call-mode selection");
        expect(true, "wechat_video", 3, List.of("视频通话", "语音通话", "取消"), "call-mode dialog recognized");
        expect(false, "user_custom", 0, home, "custom tutorial never gets unrelated auto-advance");
        System.out.println("PASS: " + count + " screen matching checks");
    }
}
