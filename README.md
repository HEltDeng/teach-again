# 再教我一次

> 项目已于 2026-10-24 完成比赛交付，现保留为最终作品归档，不再继续开发。
> 最终 Android 测试安装包与无视频版汇报 PPT 见 [比赛归档 Release](https://github.com/HEltDeng/teach-again/releases/tag/competition-2026-10-24)。演示视频不上传云端。

这是一个面向不熟悉智能手机的长辈的 Android 引导应用：把“我想做什么”拆成步骤卡，通过悬浮球和中文朗读，陪用户在真实应用中逐步操作。项目结合 Kiro 的规格、设计、任务与校验 Hook 完成，相关材料保留在 [`.kiro/`](.kiro/)。

## 获取源码与安装包

本仓库的 **Code → main** 页面包含展开的完整工程，可直接浏览、克隆和构建。Release 页面中的 `Source code (zip)` / `Source code (tar.gz)` 是 GitHub 自动生成的对应标签快照，不代表仓库只上传了压缩包。最新归档说明与许可请以 `main` 分支为准。

仓库目前为 **Private（私有）**。只有仓库所有者和获授权的协作者能够查看、克隆或下载；允许学习参考不等于向公众开放仓库。是否公开由项目权利人另行决定。

```bash
git clone https://github.com/HEltDeng/teach-again.git
cd teach-again
```

克隆时使用已有 GitHub 身份验证方式；也可在 GitHub Desktop 中登录后克隆。只需要文件时，在仓库 Code 菜单选择 Download ZIP。不要把访问令牌写进克隆地址、脚本或项目文件。

| 内容 | 位置 |
| --- | --- |
| Android 源码、布局与资源 | [`app/src/main/`](app/src/main/) |
| Kiro 规格、设计、任务和 Hook | [`.kiro/`](.kiro/) |
| Gradle 构建配置与 Wrapper | 根目录及 [`gradle/`](gradle/) |
| 逻辑校验、打包脚本 | [`scripts/`](scripts/) |
| 演示说明与验收记录 | [`docs/`](docs/) |
| 独立网页交互 Demo | [`sites/teach-again-demo/dist/index.html`](sites/teach-again-demo/dist/index.html) |
| 最终测试 APK、无视频 PPT、文件校验值 | [比赛归档 Release](https://github.com/HEltDeng/teach-again/releases/tag/competition-2026-10-24) |

## 开发状态与后续安排

2026-10-24 的比赛交付版本是本项目的最终原型，现已停止后续开发。归档中的文档完善不代表恢复功能开发；目前没有新版本计划，也不承诺持续维护或兼容性更新。

- 已完成的主要流程：本地步骤生成、悬浮球手动确认、系统中文朗读、教程编辑与导入分享。
- 页面识别与 OCR 已接入，但现场反馈自动推进未成功，比赛演示采用手动确认。
- 真实模型 API 调用未用真实 Key 验收；Kiro Hook 的自动触发也尚未单独验收。
- 若今后另行启动开发，应先验证自动识别稳定性、真实模型调用、不同 Android 设备兼容性及第三方 SDK 数据行为；这些是留存问题，不是已承诺的开发计划。

## 使用许可

原创代码与文档采用自定义的 **“非商业学习与参考许可 1.0”**，详见根目录 [`LICENSE`](LICENSE)。允许获授权访问者为非商业学习、研究、参考而下载、编译和修改；引用或借鉴代码时需保留来源与许可说明。商业产品、商业服务、收费交付及其他商业使用均须另行取得权利人的书面许可。

本项目不采用 MIT 许可，也不声称采用 OSI 意义上的开源许可。仓库私有期间，不得擅自公开、镜像或向无访问授权的人员传播源码、安装包及相关资料。第三方组件仍遵循各自原有条款，见 [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)。

## 它能做什么

- **单句聊天生成教程**：在"告诉我，你想做什么"页，像聊天一样说一句话（比如"我想把微信字调大"），应用把它整理成一步步的文字步骤卡。
- **悬浮球逐步引导**：开始跟学后，屏幕上出现可拖动的"我在"小球。打开真实应用（如微信）后小球仍在，点开看当前步骤卡，支持「我做到了 / 上一步 / 再说一遍 / 暂停 / 关闭」，完成给温暖反馈。进度本机保存，重启可续学。
- **系统中文朗读**：每一步可用系统 TTS 读给长辈听；没有中文语音时如实提示看文字。
- **家人教一次**：家人选任务或整句描述 → 生成可编辑分步草稿 → 修改成自家说法 → 保存/分享。
- **导入/分享**：通过系统分享或粘贴纯文本导入教程，带格式校验与长度限制。
- **自动跟随（仅微信、可选）**：用户在系统里显式授予无障碍后，学习微信教程时识别当前页面文字，自动翻到下一步。只读、只在学习中、只处理可见控件文字，不自动点击、不保存/上传，可随时关闭。
- **画面识别 OCR（可选、默认关闭）**：在自动跟随基础上可再开启。开启后学习微信时，在本机截取"当前微信窗口"的画面，用捆绑的 MLKit 中文模型做文字识别以更准地判断步骤。只截当前微信这一个窗口、全部本机识别、不保存图片、不上传；需要无障碍的截屏能力（Android 14+），可随时关闭。

## 明确的边界（诚实很重要）

- 应用只做**引导**。教程里的发送消息、拨打电话等都由用户本人操作，应用不替用户做。
- **自动屏幕识别目前只覆盖微信已定义规则的步骤**（如调字体路径），其余步骤及其它应用一律**手动确认**。不宣称全应用自动识别。
- **本地体验 vs 模型生成**会在界面如实标注。没有配置 API Key 时用本机模板生成草稿，不冒充联网大模型；未知任务只给「待家人补全」占位且不可直接开始跟学。
- 语音输入用**系统输入法自带的语音**（键盘上的小话筒），应用不录音、不申请麦克风权限、不接入语音模型。

## 模型设置（可选，默认不联网）

- 支持 DeepSeek / OpenAI / 任意 OpenAI 兼容接口，可填 Base URL、Model、API Key。
- API Key 用 **Android Keystore AES-GCM 在本机加密保存**，不写日志、不导出、不入源码。
- Key 按"供应商 + 域名"**隔离存储**，切换供应商/域名不会把某家的 Key 发往另一域名。
- 可留空 Key 保存设置（继续本地体验），也可一键清除当前域名下的 Key。
- 请求为 HTTPS 的 `/chat/completions` 非流式调用，**禁用自动重定向**（防凭据跨域），有超时、取消、响应大小上限与 401/429/网络错误的友好提示。
- 当前交付**未使用真实 Key 做过真实在线调用**；使用者需自行配置并验证接口、模型与账户权限。
- 本仓库不提供 API Key、服务账号凭据或签名私钥；本地配置与证书处理见 [`docs/CREDENTIALS.md`](docs/CREDENTIALS.md)。

## 构建

环境：JDK 17、Android SDK（platforms/android-36.1、build-tools/36.0.0）、AGP 9.1.1 + Gradle 9.4.1。

应用最低 Android 8.0（API 26）；可选的窗口截图 OCR 需要 Android 14+。首次构建需要联网下载 Gradle 和依赖。`assembleDebug` 使用构建机器自己的调试签名，不需要项目原作者的签名私钥。归档 APK 是比赛测试包，不是正式商店发行包。

```bash
# SDK 路径写在 local.properties（不提交）：sdk.dir=/path/to/Android/sdk
export JAVA_HOME=/path/to/jdk-17
./gradlew :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

安装到真机：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 轻量校验

纯逻辑类（规则/导入）可秒级校验，无需完整构建：

```bash
bash scripts/validate-tutorials.sh      # 屏幕匹配 + 导入往返
bash scripts/check-screen-matching.sh   # 页面匹配规则回归
bash scripts/check-tutorial-import.sh   # 导入/导出往返回归
```

保存 `StepMatchRules` / `TutorialImporter` / `StepTemplates` / `TutorialRepository` 时，
Kiro Hook `.kiro/hooks/validate-tutorials.json` 已配置为调用上述校验；脚本手动运行通过，Hook 自动触发尚未单独验收。

## 隐私

- 所有教程、学习进度、API Key 都只保存在本机。无账户、无自建服务器、可离线使用。
  （应用使用一个 Android 前台服务来维持悬浮球常驻，这是设备本机运行的系统服务，不是联网后端。）
- **未配置模型 Key 时（本地体验）**：教程生成在本机完成，不向模型厂商发送生成请求。
- **第三方 OCR SDK**：图片与识别结果在设备上处理；Google 的 ML Kit 条款同时说明 SDK 可能联系服务器获取更新并发送性能与使用指标，因此这里不承诺整个应用绝对不联网。详见 [ML Kit 官方条款与隐私说明](https://developers.google.com/ml-kit/terms)。
- **已配置模型 Key 时（模型生成）**：点击生成/发送会把您当前输入的那句话原文，以及（从悬浮球"问一问"或连续反馈进入时）当前教程标题与当前步骤文字，发送到您在设置里选择的模型厂商（如 DeepSeek/OpenAI/您填写的兼容接口）。不会发送屏幕内容、聊天记录或整个教程库。
  - 注意：配置了 Key 后若此时没有网络，会直接提示网络错误，**不会**自动退回本地模板。想用本地体验请到模型设置清除 Key。

## 提交版本验收
最终 APK 构建并安装成功。页面识别与 OCR 已接入，但现场用户反馈自动推进未成功，因此提交演示采用手动确认；网页 Demo 的自动跟随为模拟交互，不代表真机自动识别已通过。

## 当前源码同步

2026-11-01 的完整克隆、构建与外部配置说明见 [SOURCE_STATE.md](SOURCE_STATE.md)。如本文保留早期冻结版本或交付分支描述，以本次源码接手说明及实际检出的提交为准。
