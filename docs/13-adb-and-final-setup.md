# 临时 ADB、后台保护与最终配置

## 已验证环境与结果

仅本台 NEW Z8X / Android8.0.0 / 5.0.0.30_375 (OY122V12.15.214)，device=ironman、hardware=m7221。2026-10-24 用户确认：只读白名单后应用退出返回简洁桌面；安装 z8x13 后开机表现“看起来可以了”。这是单机用户验收，不等于所有固件或长期更新均已验证。

当前推荐 APK：`packages/02-clean-home-z8x13.apk`（code20）和 `packages/15-z8x-home-switch-v1.apk`（code1）。不要按文件编号把历史诊断包全部安装一遍。

## 操作顺序

1. 依照02文档建立带密码、只读的 Samba，共享仅放安装包，完成后关闭。
2. 安装并打开简洁桌面、安装切换器。使用切换器设默认简洁桌面，确认实体 Home 正常。切换器要求 ADB/SSH 原先关闭。
3. 需要做同样的设备适配时，生成下文的专用维护 APK，安装后开启临时 ADB。先备份和核对设备、当前默认桌面及原权限。
4. 授予简洁桌面 `android.permission.WRITE_SECURE_SETTINGS`，仅为保存无障碍服务开关；这不是授予桌面 root。通过 ADB 执行 `pm grant com.gathin.tvhome android.permission.WRITE_SECURE_SETTINGS`，并核对权限读回。桌面设置中打开“应用返回兼容”。
5. 在本机读取到厂商后台白名单会清理名单外应用，云端同步又会覆写本地改动。确认接受停止该文件的云端规则更新后，才按10文档做单文件只读保护；不能直接套用其他型号。脚本对基线不匹配会停止，禁止去掉比较检查。
6. 关闭 ADB 并验证端口，完整关机、待风扇停止后断电约10秒再开机。短按电源通常只是待机，不会重新加载配置。
7. z8x13 依靠默认简洁桌面、已启用兼容服务和前三分钟的开机通知/服务连接检查，精确匹配极米首页后跳转一次。可能短暂显示极米首页。验证乐播、QQ音乐退出、实体 Home、主动“返回极米桌面”及投影硬件功能。

## 生成可复现的临时 ADB 工具

不要分享别人的专用维护 APK：它含接收 token 和专用公钥。私钥留在电脑。仓库提供 `src/maintenance` 的模板源码，所有实际会话产物生成到被 Git 忽略的 `.local/adb-session`。

准备 JDK17、Android SDK34/build-tools34/platform-tools。电脑与投影仪同一局域网，在仓库根目录执行（尖括号替换为自己的值）：

```sh
python3 tools/prepare_adb_session.py --host <电脑IPv4> --projector <投影仪IPv4> --adb <adb完整路径>
cd .local/adb-session/maintenance
./gradlew --no-daemon lintRelease assembleRelease
```

Gradle 需要通过 ANDROID_HOME/ANDROID_SDK_ROOT 或本机 local.properties 找到 SDK。默认 debug 签名用于自己首次构建；升级必须沿用同一签名。仓库不包含作者私钥，自己构建的维护 APK 不能覆盖另一签名的旧工具。不要为了安装而卸载尚有待清理会话的工具。

构建得到 `app/build/outputs/apk/release/app-release.apk`，通过 Samba 安装。回仓库根目录开启接收器：

```sh
python3 tools/adb_receiver.py .local/adb-session/receiver.json
```

投影仪打开“极米返回路径诊断”，点“开启本次 ADB 连接”，保持页面开启。工具检查已知固件和维护脚本哈希，通过固定厂商维护路径临时执行 root 脚本，追加本次公钥，备份旧授权和端口；不设置开机开启 ADB，不关闭认证。随后 SSH 维护清理，ADB 暂留给电脑。

电脑使用独立服务器（5039仅电脑本地端口）：

```sh
ADB_VENDOR_KEYS="$PWD/.local/adb-session/session-key" adb -P 5039 start-server
adb -P 5039 connect <投影仪IPv4>:5555
adb -P 5039 -s <投影仪IPv4>:5555 shell id
```

本机结果是 uid2000 shell，**ADB不等于root shell**。固定维护工具才会短暂执行受限的root操作。接收器收到一份新报告后退出，下次操作前重新启动；报告仅接受指定投影仪IP和本次token。

## 核对与固定用途操作

普通 ADB 可读取 `dumpsys accessibility`、`dumpsys activity services com.gathin.tvhome`、`cat /proc/uptime`，以及 `settings --user 0 get secure enabled_accessibility_services`。明确指定 user0。不要用 uiautomator 抓页面同时测无障碍连接，它会干扰服务状态；用截图和 dumpsys。

更高权限检查使用 `.AdbCloseReceiver` 的固定动作：`local.projector.returndiag.INSPECT`、`PIN`、`UNPIN`、`CLOSE_ADB`。Receiver 要求 DUMP 权限，ADB shell/system可调用，普通应用不能。不存在任意命令参数。

```sh
adb -P 5039 -s <投影仪IPv4>:5555 shell am start -n local.projector.returndiag/.MainActivity
adb -P 5039 -s <投影仪IPv4>:5555 shell am broadcast -n local.projector.returndiag/.AdbCloseReceiver -a local.projector.returndiag.INSPECT
```

INSPECT 只读本机策略、服务和桌面记录。PIN 是**有副作用的单文件保护**，必须先读10文档、核对实际配置并决定是否接受暂停厂商规则更新。它仅支持源码中精确匹配的基线，不是通用工具。UNPIN 对内容作冲突比较后恢复备份和原权限。执行操作前启动接收器；收到回执后核对 root_verified、cleanup.verified 和 home_log 中结果，不以界面“已执行”替代验证。

PIN使用 `/data/local/tmp/codex-launcher-background-pin-v1` 保存原始/应用后文件；原始白名单试验另在 background-v1。不要清理这些备份。root:system 0444 阻止已查明的管家直接写入路径，不是不可变文件；固件更新或root进程仍可能替换。升级前解除保护，恢复后需完整重启。

## 结束与故障处理

启动接收器，再在投影仪点击“关闭 ADB／移除本次密钥”，或通过上述广播把动作改为 CLOSE_ADB。要求报告包含 `adb_close=verified`、cleanup.verified=true，再用 `python3 tools/check_ports.py <投影仪IPv4>` 核对22/5555均关闭。最后 `adb -P 5039 kill-server`，清理本地会话私钥；在同一诊断流程的待重启验证阶段可暂留电脑资料，设备授权仍须关闭。下次生成新会话前先归档旧目录。

- 提示“调试端口原本已开启”：不要绕过预检。用开启该会话的原工具/原密钥清理；新会话不接管旧会话。
- 提示“缺少 awk”：历史版本问题，当前脚本使用shell循环，不依赖awk/grep/touch。
- “服务记录已连接”是历史记录；“等待服务连接”才是当前状态。先保留失败现场读取报告，不要连续重装。
- “重启”后 uptime 仍数小时：实际可能只是待机。完整关机断电后再次核对。
- 只装两个日常APK不会自动完成白名单保护和权限授予。首次适配仍需本页的一次性设置。

临时root的目录校验、脚本哈希、固定操作和清理机制见04文档。不要提交报告、token、Samba密码、ADB私钥、厂商APK/framework或个人设备标识到Git。
