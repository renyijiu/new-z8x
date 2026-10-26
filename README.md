# new-z8x · 极米 NEW Z8X 简洁桌面与默认桌面切换

[安装指南](docs/03-install-and-use.md) · [完整配置](docs/13-adb-and-final-setup.md) · [恢复指南](docs/05-recovery.md) · [更新记录](CHANGELOG.md) · [参与贡献](CONTRIBUTING.md)

这是一份在一台 **极米 NEW Z8X、Android 8.0.0、固件 5.0.0.30_375 (OY122V12.15.214)** 上完成的改造记录与安装指南。最终保留两个应用：简洁桌面、极米桌面切换。无需把前期诊断 APK 全部安装一遍。

简洁桌面基于 [Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv) 修改；默认桌面切换通过本机已验证的厂商维护通道临时取得命令执行权限。**这是特定固件的兼容方案，不是所有极米设备通用的 root 工具。**

## 从这里开始

1. 对照 [设备信息与验证范围](docs/01-device-and-results.md)。仅 Android 8.0 相同不足以证明兼容。
2. 按 [Samba 无线传输](docs/02-samba.md) 开启临时、只读、带密码的安装包共享。
3. 按 [安装与日常使用](docs/03-install-and-use.md) 安装下列两个 APK，先验证新桌面可打开，再切换默认桌面。
4. 需要了解原理、历史问题及清理机制，阅读 [临时 root 与默认桌面处理](docs/04-temporary-root.md)。
5. 出现异常或准备卸载，先看 [恢复与故障排查](docs/05-recovery.md)。
6. 想自行修改或构建，阅读 [源码与构建](docs/06-build.md)。临时ADB和最终适配见 [完整配置](docs/13-adb-and-final-setup.md)。完整过程见 [过程记录](docs/07-history.md)。

## 最终安装包

| 应用 | 文件 | 版本 / code | 用途 |
|---|---|---|---|
| 简洁桌面，应用名可能显示“主屏幕” | [02-clean-home-z8x13.apk](packages/02-clean-home-z8x13.apk) | 2.6-z8x13 / 20 | 日常桌面、每日壁纸、极米设置侧边栏、临时返回极米桌面 |
| 极米桌面切换 | [15-z8x-home-switch-v1.apk](packages/15-z8x-home-switch-v1.apk) | 1.0 / 1 | 设置默认简洁/极米桌面、临时打开极米桌面、恢复检查 |

运行 `python3 tools/verify.py` 检查文件哈希。也可在 `packages` 目录执行 `shasum -a 256 -c SHA256SUMS`。两个包签名证书 SHA-256：

```text
51b01b97f95948d308eee6e8aaa187430e677cfff2a1153f55f4a88b18e80817
```

签名私钥不随仓库分发。自行编译的不同签名包不能直接覆盖这两个包。

## 最终效果与边界

- 原机已验证临时 UID 0 命令执行、系统默认 HOME 设置及物理 Home 键进入简洁桌面。
- 极米设置侧边栏与返回行为已获用户确认；z8x6 的“返回极米桌面”入口重启后也已确认正常。
- 新桌面提供每日联网壁纸、离线渐变、系统字体、较缓和滚动、半透明卡片与 Dock。
- 原极米桌面保持安装和启用。临时打开原桌面不会改变默认 HOME。
- **没有实施系统应用批量卸载、系统分区修改或永久 root；没有验证去除开机广告，也没有测得性能提升的量化数据。**
- 独立切换 APK 复用已验证流程并完成模拟检查，但其反复双向切换、被强制停止后的恢复仍缺少明确的独立实机验收记录。读者安装后应按文档自行验收。

## 仓库内容与分享

`src/launcher`、`src/home-switch` 是对应最终版本源码；`tools/samba` 是临时 Samba 容器配置；`verification/release.json` 记录校验值和验证边界。日常推荐为这两个 APK；历史包放在 testing。公开内容不含实际会话维护 APK、厂商 APK、个人 IP、密码、接收回执 token 或签名私钥。

项目地址：[ymbian/new-z8x](https://github.com/ymbian/new-z8x)。分享仓库或使用 `git archive` 导出已提交内容；不要直接压缩带 `.local` 的工作目录。代码及文档采用 [MIT](LICENSE)，第三方字体、依赖和图片另按其各自许可，见 [来源与许可](THIRD_PARTY_NOTICES.md)。本项目由社区维护，与极米官方及上游官方发行版无关联。

目录分工：

| 目录 | 内容 |
|---|---|
| `docs` | 设备信息、Samba、安装、恢复、诊断历史和最终配置 |
| `src/launcher` | 简洁桌面源码 |
| `src/home-switch` | 两个固定 HOME 目标的切换器源码 |
| `src/maintenance` | 按本地会话生成的临时维护工具模板 |
| `tools` | APK 校验、临时共享、会话生成及接收器 |
| `packages` | 两个日常 APK；`testing` 仅供历史研究 |
| `verification` | 版本、哈希、验证范围及发布资料检查 |

## 当前验证状态（2026-10-24）

- 单文件后台白名单只读保护后，用户确认应用退出回到简洁桌面。
- z8x13 增加开机通知与兼容服务恢复，用户确认完整开机表现“看起来可以了”；长期联网、固件更新和不同机型仍待验证。
- **两个日常 APK 不包含首次适配所需的后台白名单和权限设置**。完整流程、临时 ADB、关闭授权及回滚见 [最终配置与 ADB 指南](docs/13-adb-and-final-setup.md)。
- 白名单只读保护会暂停该文件的厂商应用管理规则自动更新，已获原机用户明确同意；详情见 [后台策略调查与恢复](docs/10-background-whitelist-trial.md)。
- `packages/testing` 为历史试验，不能按编号全部安装，也不要把开机试验失败记录当作最终结果。

## 可复现的维护工具

`src/maintenance` 是去除专用连接凭据后的维护工具模板；`tools/prepare_adb_session.py` 在 `.local` 生成自己的密钥、接收配置及构建工程。只有生成的专用工具能够开启对应电脑的 ADB。不公开预编译维护 APK，防止复用作者的会话凭据。详见上述 ADB 指南。

## 后续计划与反馈

优先补充独立切换器反复双向切换、异常中断恢复，以及长期联网和完整断电启动的实机记录。其他机型须单独诊断和适配，不承诺直接支持。问题反馈请附完整固件版本、APK 版本和最小复现步骤，按 [贡献指南](CONTRIBUTING.md) 去除个人信息。敏感问题见 [安全说明](SECURITY.md)。
