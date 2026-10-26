# z8x8 应用返回兼容（待实机验证）

z8x7 的 TASK_ON_HOME 未解决本机退出问题；缓存上限试验未改变系统实际策略，已撤销。z8x8 增加可选无障碍服务，参考上游 HomeKeyService 的 stock_home_redirect 思路，不更改全机缓存限制，也不禁用极米桌面。

## 行为与边界

- 默认关闭，仅从简洁桌面首页或应用库启动外部应用时记录待返回状态。
- 服务只订阅 com.xgimi.home 的窗口状态事件，精确匹配已验证的 HomeActivity；不读取页面树、不监听按键、不轮询、不联网。
- 只有简洁桌面仍为默认 HOME，且本次已记录应用启动，才将极米首页跳转回简洁桌面。跳转前清除待返回状态，避免重复事件形成循环。
- 回到简洁桌面、主动点击“返回极米桌面”会取消待返回状态；启动 local.projector 工具（含桌面切换器）也不触发兼容拦截。默认切换到极米时不拦截。
- 窗口出现后才触发，可能短暂闪过极米首页。固件若强制停止无障碍服务仍可能失效；尚未完成实机验证，不应称为已解决。

## 本机启用

先覆盖安装 packages/testing/02-clean-home-z8x8.apk。本机使用单独的临时维护授权工具，授予 com.gathin.tvhome 声明的 android.permission.WRITE_SECURE_SETTINGS 权限；它不会自动开启兼容服务。该权限较宽，用于通过应用自身 SettingsProvider 接口开关无障碍服务。授权工具保留权限原状态，支持撤销它新增的授权，并沿用维护预检、目录恢复与端口关闭流程。

授权工具含本机回执配置，不收录在公开分享包。其他设备需自行确认无障碍入口或适用的授权方式，不可把本机临时维护流程当作跨机型通用方案。

授权完成后，进入简洁桌面设置，点击“应用返回兼容”，阅读说明后开启；确认状态为“已连接”。从简洁桌面打开 QQ 音乐并退出，测试返回结果，再测试主动返回极米桌面、桌面切换器和关闭兼容开关。不要只凭开关打开就认定修复成功。

## 回退

先在桌面设置关闭“应用返回兼容”。这会立即停止逻辑，并仅从 enabled_accessibility_services 列表移除本服务，保留其他服务。需要撤销权限时使用原授权工具；若权限在工具运行前已存在则不会撤销原有授权。卸载授权工具不会自动撤销已授予的权限。回退桌面 APK 前先关闭服务。

## 验证

217 项 JVM 单元测试通过，lint 无错误，签名与旧版一致。新增测试覆盖：仅针对外部应用启用待返回状态、默认桌面与窗口精确匹配、主动使用原桌面/工具不触发、服务列表合并保留原服务。构建详情见 verification/z8x8-testing.json。Android 8 实机服务绑定与返回行为待确认。

## z8x8 实机反馈与 z8x9 延迟优化

用户确认 z8x8 会先退到极米首页，等待片刻后自动回简洁桌面：兼容服务路径已生效，但跳转等待明显，不能称为无缝返回。

Android 8 AOSP ActivityManagerService 的 APP_SWITCH_DELAY_TIME 为 5000 ms；ActivityStarter 可能把后台 startActivity 放入待执行队列。AccessibilityManagerService 的 GLOBAL_ACTION_HOME 则通过系统发送 HOME 按键。结合本机实体 Home 能正确回简洁桌面的已知结果，z8x9 优先执行系统 GLOBAL_ACTION_HOME，仅在接口返回拒绝或抛出权限异常时退回直接启动。此为有源码依据的优化，尚未证实本机等待就是该保护期导致。

z8x9 设置页记录“窗口事件产生到收到通知”及“发起返回到桌面 onResume”的耗时，数据仅存于本地偏好设置，不含页面内容，也不自动上传。后者不等同于首帧绘制耗时。记录验证同次开机、单调时钟与 15 秒有效窗口，避免把重启或很久以后的手动返回当成成功样本。

覆盖安装 packages/testing/02-clean-home-z8x9.apk，无需重新授权；确认兼容服务已连接后再次测试 QQ 音乐退出。若仍慢，请记录设置页的两段毫秒数和返回路径。218 项 JVM 测试及 lint/build/签名校验通过，实际延迟改善待用户确认。

源码依据：
- https://github.com/aosp-mirror/platform_frameworks_base/blob/android-8.0.0_r1/services/core/java/com/android/server/am/ActivityManagerService.java
- https://github.com/aosp-mirror/platform_frameworks_base/blob/android-8.0.0_r1/services/core/java/com/android/server/am/ActivityStarter.java
- https://github.com/aosp-mirror/platform_frameworks_base/blob/android-8.0.0_r1/services/accessibility/java/com/android/server/accessibility/AccessibilityManagerService.java
