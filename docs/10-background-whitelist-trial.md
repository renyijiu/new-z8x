# NEW Z8X 后台白名单试验（2026-10-24，待重启验证）

适用实机：NEW Z8X，Android 8.0.0，5.0.0.30_375 (OY122V12.15.214)。这是单台实机诊断记录，不代表其他固件已验证。

## 现象与证据

默认 HOME 解析为简洁桌面，实体 Home 可返回简洁桌面，但乐播等应用退出后仍回到极米首页。ADB 观察到兼容无障碍服务没有绑定；部分采样中启用列表也缺少该服务。UI 自动化工具会影响无障碍服务状态，后续复测改用截图和 dumpsys，避免混淆。

从本机 services.jar 读取到：XgimiCloudConfiguration 初始化时合并 vendor 和 data 的 sys_admin_pkg.json。当 backgroundrunningwhite 非空时，名单以外的包会被 isBackgroundRunningBlackPackage 判为受限制。ActivityManagerService 对 UID >=10000、非当前前台、后台计时超过 10000ms 的相应进程调用 removeAllProcessInApp。这不是严格每 10 秒执行一次的定时器，而是进程优先级更新中的条件分支。

本机 data 配置存在非空白名单，com.gathin.tvhome 不在其中。因此该包符合这条清理规则的包名和 UID 条件；仍需重启后的实际测试确认本次改动能否解决退出问题。

## 已执行的最小试验

只在 `/data/local/xgimi_gmui_manager/sys_admin_pkg.json` 的已有 `backgroundrunningwhite` 中加入 `com.gathin.tvhome`。没有更改 system/vendor 分区，没有禁用原桌面，没有放开全部应用的后台限制。

通过固定用途的临时 root 工具执行：比对之前读取的配置、保存原始文件、保留 owner/group/mode、在同目录准备新文件、再次比对原文件后原子替换，并进行回读核对。收到 `whitelist=applied-reboot-required` 和维护清理成功回执。

设备备份位于 `/data/local/tmp/codex-launcher-background-v1/original.json`，同时保存 `applied.json` 用于恢复前的冲突检查。保留此目录用于回滚。若云端或其他工具已经修改配置，回滚工具应停止，不能直接覆盖。

配置只在所检查的单例初始化流程中读取，当前进程尚未采用修改，需正常重启。不要将“文件已写入”视为“退出已修复”。

## 验证步骤

1. 正常重启并联网，进入简洁桌面。
2. 设置中确认“应用返回兼容”已连接；若仍等待连接，关闭后重新开启一次并记录。
3. 从简洁桌面打开乐播，停留至少 20 秒后按返回退出，观察最终桌面及是否先显示极米再跳回。
4. 同样测试 QQ 音乐，并检查实体 Home、设置里的“返回极米桌面”仍可使用。

目前为待验证试验。固件更新或云端配置同步可能覆盖此项，不能承诺永久生效。

## ADB 与发布范围

本轮已修复诊断脚本对 Android 8 缺失 awk 命令的依赖，采用临时授权密钥连接 ADB。调试结束必须通过工具撤销本次密钥、恢复原端口并关闭 adbd，不能只删除电脑上的密钥。

z8x10 测试版增加服务连接和事件诊断，并恢复显式启动桌面的返回方式；其本身不能绕过固件后台白名单。本记录不替换公开稳定版，也不将设备私有密钥、报告接收令牌或厂商 framework 文件打包分享。

## 后续实机结果

用户此前的“重启”未重启 Android：重新连接后 uptime 约 7.7 小时，原桌面进程记录与修改前相同，白名单新增条目仍在。完整关机断电开机后，用户反馈应用退出已回到简洁桌面。白名单试验得到用户实测支持；仍需长期观察云端配置是否覆盖。

剩余问题是完整开机仍先进入极米首页，实体 Home 可进入简洁桌面。这与应用退出返回分开处理。

## 再次失效的原因（已取证）

第三轮 ADB 报告中 uptime 约1327秒，证明本次完整重启成立；data 配置与原始厂商基线内容一致，新增的 com.gathin.tvhome 已消失。极米管家 com.xgimi.manager 的 BlackListService.getCommonConfig 请求 GMUI.app.manager.config；其回调按键名写入 /data/local/xgimi_gmui_manager/<key>.json，FileUtils.write 使用 FileOutputStream 直接覆盖文件。该路径不合并本地新增条目。GETWHITELIST 分支有20分钟间隔门限（不等同于固定20分钟定时器）。因此白名单一次性改写不能保证持久有效。

已准备仅对此文件设置 root:system/0444 的可回滚方案，并做宿主机模拟测试；尚未执行，等待用户决定是否接受暂停该文件的厂商应用管理规则更新。模拟测试不代表设备权限和持久性已验证。没有禁用管家，也没有改 system/vendor 分区。

## 用户授权后的只读保护

用户明确选择“采用只读保护并继续验证”。已将文件恢复为仅新增 com.gathin.tvhome 的配置，并将该文件设为 root:system、0444。不改目录权限，不禁用整个管家，不改 system/vendor。

本次独立备份：`/data/local/tmp/codex-launcher-background-pin-v1/original.json`（原始内容、所有者和模式）及 `applied.json`。之前 background-v1 备份仍保留。恢复脚本仅在当前内容与 applied.json 一致时，用 original.json 恢复内容、所有者、模式，再恢复 SELinux 标签；存在外部改动时拒绝覆盖。

限制：只读保护会阻止已检查的管家 FileOutputStream 写入路径更新此文件，连同其他厂商应用管理规则一起暂时固定。它不是文件不可变标志，不能保证未来固件或其他 root 程序无法替换该文件。升级前建议先解除保护。系统进程需要完整重启才能读取新白名单。实机重启与持续使用结果仍待核对。

只读保护实机即时核对通过：stat 为 444:0:1000；固定用途检查进程降为 UID/GID 1000 后，仅尝试 O_WRONLY 打开（不截断、不写字节），得到 EACCES。配置内容与预期一致。此测试证明当前写权限被拒绝，不替代重启后的持续验证。
