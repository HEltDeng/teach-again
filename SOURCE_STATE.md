# 源码接手说明 · 2026-11-01

本仓库同步截至本次整理的本机现有源码。源码同步不等于重新发布生产服务、App Store 或 APK。

## 获取完整源码

```sh
git clone --recurse-submodules https://github.com/HEltDeng/teach-again.git
cd teach-again
git submodule update --init --recursive
```

JDK 17、Android SDK、Gradle wrapper：./gradlew :app:assembleDebug。sites/teach-again-demo 为独立展示网页子模块。

## 保留与排除

保留源码、构建脚本、工程配置、依赖锁定文件、必要应用资源和合成测试。运行数据库、用户上传文件、登录会话、密钥、签名私钥、个人资料、缓存与可重新生成的输出不随仓库分发。

正式签名、地图/短信/外部服务密钥必须在本机配置。源码、依赖和资源可恢复，生产数据和签名身份需用用户自己的独立备份恢复；不能把缺少真实账号或服务配置当成代码缺失。二进制字节还会受工具链、签名和构建时间影响。
