# 任务清单 — 再教我一次

## 里程碑 1：可运行的悬浮球 APK ✅

- [x] T1.0 建立 steering 和 spec 文档
- [x] T1.1 Gradle 项目结构（AGP 9.1.1 + Gradle 9.4.1，local.properties）
- [x] T1.2 AndroidManifest（悬浮窗/前台服务 specialUse/权限/Activity）
- [x] T1.3 FloatingBallService 前台服务（由 Codex 接管维护）
- [x] T1.4 可拖动悬浮球（TYPE_APPLICATION_OVERLAY，边界限制）
- [x] T1.5 步骤卡 + 五个按钮（我做到了/上一步/再说一遍/暂停/关闭）
- [x] T1.6 ProgressRepository 进度持久化 + 断点续学 + 完成态
- [x] T1.7 TtsHelper 中文朗读（不可用时如实提示）
- [x] T1.8 MainActivity 首页 + 悬浮窗权限引导闭环
- [x] T1.9 内置 3 个教程（微信调大字体[新版路径]、发照片、视频通话）
- [x] T1.10 首次编译验证 assembleDebug 成功
- [x] T1.11 真机安装测试（Codex 负责验收）

## 里程碑 2：父母端首页 + 教程 ✅

- [x] T2.1 首页 UI（大字、大按钮、暖米白+橙/珊瑚、呼吸球）
- [x] T2.2 教程详情页（继续/重新开始、固定底部按钮、WindowInsets）
- [x] T2.3 家人教一次：模板生成 + 逐步编辑 + 保存/分享
- [x] T2.4 导入教程（格式校验、长度限制、失败保留文本）
- [x] T2.5 TTS 朗读
- [x] T2.6 自定义教程：学习/修改/分享/删除
- [x] T2.7 自动跟随入口（仅微信规则、无障碍授权引导、可关闭）

## 里程碑 3：单句聊天生成 + 泛化 ✅

- [x] T3.1 AskActivity 单输入框聊天页（用户/助手气泡）
- [x] T3.2 StepGenerator 整句识别（微信/相机/系统设置复用模板，未知→待补全）
- [x] T3.3 结果气泡摘要 + 开始跟学/修改步骤
- [x] T3.4 一键开始跟学（有权限直启服务，无权限走详情页引导）
- [x] T3.5 连续反馈上下文（"这一步找不到"），新任务清理旧上下文
- [x] T3.6 本地体验纠错：温和说明本机模板局限，不重复假装解决
- [x] T3.7 IME 语音输入入口（聚焦+提示，不录音、无麦克风权限，有 contentDescription）
- [x] T3.8 未知整句不生成假应用名，占位未补全不可开始

## 里程碑 5：画面识别 OCR（仅微信、可选）✅

- [x] T5a.1 OcrPageRecognizer：takeScreenshotOfWindow 截当前微信窗口，MLKit 中文识别
- [x] T5a.2 HardwareBuffer→ARGB_8888 拷贝后释放；单任务+≥2秒间隔；失败回空；主线程回调
- [x] T5a.3 accessibility_config 加 canTakeScreenshot，仍限 com.tencent.mm
- [x] T5a.4 AutoFollowPrefs.isOcrEnabled/setOcrEnabled，默认关闭
- [x] T5a.5 首页自动跟随对话框加 OCR 可选开关 + 确认 + 缺能力提示
- [x] T5a.6 捆绑模型依赖 text-recognition-chinese:16.0.1（非 Play 动态）
- [x] T5a.7 GuideAccessibilityService 集成 OCR（由 Codex 维护）

## 模型接入（可配置，未用真实 Key）✅

- [x] T4.1 AiSettings：DeepSeek/OpenAI/兼容接口，Base URL/Model/Key，可空可清除
- [x] T4.2 SecureKeyStore：Keystore AES-GCM 加密，按供应商+域名隔离
- [x] T4.3 ChatClient：HTTPS /chat/completions 非流式，禁重定向、超时、取消、大小上限、401/429 友好错误
- [x] T4.4 严格校验模型 JSON（标题/步骤数 2–20/长度，拒绝 builtin、不覆盖内置 ID）

## 交付收尾 ✅

- [x] T5.1 Gradle wrapper（jar + 脚本）
- [x] T5.2 轻量校验 Hook（保存教程/规则文件触发，不全量编译）
- [x] T5.3 README
- [x] T5.4 .gitignore 排除机器路径（local.properties、.vscode/settings.json）

## 可选（时间充足）

- [ ] T6.1 家人录鼓励语音（未实现）
