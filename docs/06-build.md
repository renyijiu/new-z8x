# 源码与构建

## 来源

桌面基于 Gathin23/cleanyourtv 提交 `3b3f688a3737914482145b0895480ca771ff4c9c` 的 launcher 子项目。`src/launcher` 包含最终 z8x13 的完整必要源码、Gradle wrapper 与资源；`src/home-switch` 是独立切换器源码。构建这两个日常应用不需要旧诊断接收服务器或旧 APK。首次完整适配另需 `src/maintenance` 模板及本地接收器，按 [最终配置与 ADB 指南](13-adb-and-final-setup.md) 生成，不可直接分发模板构建产物。

原项目的 ADB 安装/停用桌面脚本没有收录为本指南的操作入口；本指南通过 Samba 安装，使用固定目标的独立切换器。

## 环境

- JDK 17；原构建使用 17.0.21+9。
- Android SDK platform 34、build-tools 34.0.0。
- Gradle wrapper 8.9、Android Gradle Plugin 8.5.2。
- Python 3 用于辅助脚本与 shell 模拟测试。

设置 JAVA_HOME 指向自己的 JDK、ANDROID_HOME 指向自己的 SDK。路径按你的电脑填写，不要照抄他人目录。首次构建需要网络与 Android SDK 许可。

在仓库根目录：

```sh
cd src/launcher
./gradlew --no-daemon testReleaseUnitTest lintRelease assembleRelease
```

另一个终端从仓库根目录：

```sh
cd src/home-switch
./gradlew --no-daemon lintRelease assembleRelease
python3 tests/run_tests.py
```

生成 APK 分别在两个项目的 `app/build/outputs/apk/release/app-release.apk`。shell 模拟测试需要 POSIX 环境；macOS/Linux 可运行，Windows 可使用 WSL。Windows 构建桌面可用已有 gradlew.bat，切换器可在 WSL 构建。

## 签名与更新

仓库 APK 使用发布签名证书，私钥不分发。源码默认 release 使用本机 debug signing，因此本地重建不会复现已发布签名或完全相同的 APK 字节；“源码可构建”不等于“能在没有私钥时复现发布 APK 哈希”。

若要长期维护自己的版本，应使用你自己的密钥并备份，今后的更新沿用该密钥。首次切换到不同签名版本前，先恢复默认极米桌面并确认无需新桌面也能操作，再处理卸载/重装；保留必要的布局记录。

发布前提升 versionCode，执行测试、lint、签名验证和实机验收，重新生成 SHA256SUMS。不要把密钥、local.properties、私有诊断回执或 Samba 密码加入 Git。

## 测试范围

最终 z8x13 桌面已有 221 项 JVM 测试通过；独立切换器有 14 项 shell 场景组（包含两个非法输入）和 16 项兼容性/结果策略检查。它们覆盖命令选择、回退、异常原 HOME、非 root、解析器状态、重复方向与错误结果判断等。

这些检查不模拟完整 Android 系统服务、厂商界面或实际断电，不等于实机验证前台服务和中断恢复。见设备页列出的待验收范围。
