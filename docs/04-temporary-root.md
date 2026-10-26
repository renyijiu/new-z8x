# 临时 root 与默认桌面处理

## 为什么普通 APK 不够

普通 Android 应用不能随意替用户替换系统默认 HOME。正常路线是系统提供桌面选择框，或由已授权的 ADB shell 执行设置命令。本机的标准默认桌面页面提示“不支持”，物理 Home 没有弹出选择框，系统应用管理也没有可用的“清除默认设置”。

ADB 开启器曾打开 5555，但电脑认证没有通过，投影仪没有授权弹窗。端口开放不等于获得 ADB shell，更不等于 root。独立桌面切换器没有依靠 ADB，也不写入 ADB 公钥；后续后台和开机适配使用了另行生成的临时 ADB 工具，见 [最终配置](13-adb-and-final-setup.md)。

## 厂商维护通道的已验证机制

研究线索来自 [xgimi-clean 的维护服务记录](https://github.com/HG0539xDC860539/xgimi-clean/blob/main/docs/03-maintenance-service.md)。其参考设备不能直接代表本机，所以先读取本机配置与脚本，再验证最小范围命令执行。

本机 `xgimi_ssh` 由 init 以 root 启动。已核对的厂商脚本读取 `/data/ssh/params`，远程调试属性为 true 时启动 sshd，随后将参数传给 `/system/bin/ssh`。`/data/ssh` 对普通应用可写，SSH 的 ProxyCommand 参数可指向本次生成的固定脚本，由这个 root SSH 进程启动。

这利用的是特定厂商固件维护入口的权限设计，并不是 Android 公共 API。它可能在其他固件中被修复或改变。仓库不提供交互式 root shell，不接受外部命令输入，不安装 su/Magisk 或开机 root 服务。

## 最终 APK 的事务顺序

1. 核对 SDK/device/hardware/incremental、维护脚本 SHA-256、init 服务定义及两个桌面入口。
2. 确认维护属性 false、服务停止、22/5555 关闭、没有现有 sshd，以及 `/data/ssh` 基线为既有四个主机密钥文件的元数据。
3. 在应用私有目录记录待清理 journal 与目录基线；为本次生成随机 run ID。
4. 在 `/data/ssh/codex-probe-<run>` 建立本次专用目录与固定脚本，以排他创建方式写入本次 params，不覆盖已有文件。
5. 临时开启维护属性，启动厂商服务。固定脚本先写 run ID、UID 回执，再执行两个允许目标之一的 HOME 设置。
6. 读取 UID 0 回执，检查回执是 root 所有的普通文件；不会仅凭“服务启动成功”宣称提权成功。
7. root 脚本退出清理；应用 finally 再关闭属性、停止服务，必要时调用已核对厂商脚本的 false 分支清理存活 SSH 子进程。
8. 删除本次可核对的临时文件，比较目录元数据，检查服务、进程、22/5555 端口与系统实际 HOME。
9. 只有目标 HOME 正确、root 证据成立且清理验证通过才显示成功。未清理完成则保留 journal，阻止新切换，允许“恢复检查”。

前台服务让页面退出后操作仍可完成，但不保证被系统强杀或断电时继续运行。因此 journal 在首次系统写入前保存；重新打开后可恢复清理。没有开机自动重启维护服务。

## 实际改变默认桌面的命令

下列是脚本内部在临时授权上下文执行的固定命令说明，**不是让普通电脑终端直接粘贴执行**。独立切换 APK 已封装完整执行与清理流程，不需要预先授权 ADB。

查询时必须同时带 HOME 和 DEFAULT：

```sh
cmd package resolve-activity --components --user 0 \
  -a android.intent.action.MAIN \
  -c android.intent.category.HOME \
  -c android.intent.category.DEFAULT
```

设置目标仅允许以下两项之一：

```sh
cmd package set-home-activity --user 0 com.gathin.tvhome/com.gathin.tvhome.MainActivity
cmd package set-home-activity --user 0 com.xgimi.home/com.xgimi.module.cellview.home.ui.HomeActivity
```

不禁用、卸载或改写原桌面。只支持主用户 0。未知的既有 HOME 会中止；若原来是系统桌面选择框，失败时以极米桌面为回退目标。若设置或读回失败，脚本尝试恢复之前的已知桌面，不能把“尝试回退”当作“已成功回退”。

## 排查中实际遇到的问题

| 问题 | 原因/处理 |
|---|---|
| 脚本 Text file busy | Android 8 的 FileOutputStream(FileDescriptor) 借用 fd，关闭流未必关闭 fd；最终用 Os.close 显式关闭，避免脚本仍以写入状态打开 |
| 回执存在却 Permission denied | root 先按 umask 077 创建，再 chmod 644；存在与可读之间有时间窗，改为等待 canRead，并保留已验证的 UID/owner 证据 |
| 默认桌面设置后被判失败/选择失效 | HOME-only 查询额外匹配 FallbackHome；改为与 Android 默认 HOME 选择一致的 HOME + DEFAULT |
| 停服务后 22 仍开放 | oneshot 服务的 sshd 子进程可能存活；使用已核对脚本的关闭分支，最后独立检查进程、端口与目录 |
| head -c 不兼容 | 本机命令实现不支持此形式；最终流程移除大段 dumpsys/head 诊断 |
| 设置已生效但日志不完整 | 最终结果依据清理后的系统 HOME 读回，不要求必须出现 shell 的 selected 字符串 |
| 原始验证器依赖电脑回执 | 独立切换器去掉 HTTP 接收器、token、电脑审批与一次尝试锁，改为本地事务和恢复记录 |

## 源码位置

- `src/home-switch/app/src/main/java/local/projector/homeswitch/Probe.java`：预检、事务、回执、清理。
- `RootPlan.java`：固定临时脚本、UID 回执、退出清理。
- `HomePlan.java`：两个目标、读回、失败回退。
- `SwitchPolicy.java`：兼容性与成功条件。
- `SwitchService.java`：前台事务与状态。

本页的独立切换器只借用临时权限设置默认桌面；后续维护工具另做专用 ADB 授权和后台策略保护，见13文档。系统分区仍只读、原桌面仍启用。不能把此记录表述为已完成开机广告去除或全系统精简。
