# 技术指引 — 再教我一次

## 构建环境
- JDK 17（Temurin）
- Android SDK: platforms/android-36.1, build-tools/36.0.0
- AGP 9.1.1 + Gradle 9.4.1（已缓存分发，最低要求 Gradle 9.3.1）
- 纯 Java + 原生 Android Views（无 Compose，减少依赖下载量）
- compileSdk "android-36.1"（minorApiLevel），minSdk 26（Android 8），targetSdk 36
- 前台服务类型 specialUse，从可见 Activity 启动

## 项目布局
```
app/
  src/main/java/com/teachagain/
  src/main/res/
  AndroidManifest.xml
build.gradle (app)
build.gradle (root)
settings.gradle
local.properties  ← 本机路径，不提交
```

## 悬浮窗规范
- TYPE_APPLICATION_OVERLAY，WRAP_CONTENT，非全屏
- FLAG_NOT_FOCUSABLE（球态），展开时去掉该 flag 允许文字可点
- 只在球/卡实际区域接收触摸

## 约定
- 路径写 local.properties，不硬编码
- 所有教程/进度本地 SharedPreferences + JSON 文件
- 前台服务 + 持久通知，保活
