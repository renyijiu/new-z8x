# 设备信息与验证范围

记录时间：2026-10-24。以下来自用户提供及本次设备读取，不是厂商通用规格表。

| 项目 | 原机信息 |
|---|---|
| 零售型号 | 极米 NEW Z8X（用户确认） |
| 系统型号字段 | XGIMI TV |
| Manufacturer | Xgimi Technology Co.,Ltd |
| Android | 8.0.0 / API 26 |
| 设置中完整固件版本 | 5.0.0.30_375 (OY122V12.15.214) |
| 内部 incremental | v12.15.214 |
| Device / Board | ironman / ironman |
| Hardware | m7221 |
| Product | Product_mpb |
| Build display | OPR5.170623.014 dev-keys |
| SELinux（检查当时） | permissive，enforce=0 |
| 系统分区（检查当时） | /system、/vendor 只读 |
| 厂商设置应用 | com.android.newsettings，5.02.89 |
| 原桌面组件 | com.xgimi.home/com.xgimi.module.cellview.home.ui.HomeActivity |
| 简洁桌面组件 | com.gathin.tvhome/com.gathin.tvhome.MainActivity |
| 最终桌面版本 | 2.6-z8x13 / code 20 |
| 独立切换器版本 | 1.0 / code 1，local.projector.homeswitch |

## 维护通道指纹

厂商脚本 `/vendor/bin/xgimi_ssh.sh`，原机读取为 1105 字节，SHA-256：

```text
b559aa0e7c54ea2b746e0e607c97e6141f68a0c277f507c4dacdc587dec153ad
```

`/vendor/etc/init/hw/init.xgimi.rc` 中相关服务定义：

```text
service xgimi_ssh /vendor/bin/xgimi_ssh.sh
    class main
    oneshot
    seclabel u:r:xgimi_ssh:s0
```

未指定 user，init 默认以 root 运行。本机 `/data/ssh` 为 root:root、0777；正常基线仅有 RSA/DSA 主机密钥及两个公钥文件。工具只检查这些文件的元数据，不复制或分发密钥内容。

切换 APK 会核对 SDK、device、hardware、incremental、脚本哈希、服务定义、两个桌面是否可用、维护会话与目录状态。只匹配固件文字还不够；任一必要条件不符就停止。固件升级后可能拒绝运行，不应为了通过检查而删掉兼容性判断。

## 已验证与未验证

| 内容 | 证据/状态 |
|---|---|
| Samba 传输两个最终 APK | 电脑按旧客户端兼容协议下载并比对 SHA-256 一致 |
| ADB 普通无线调试 | 原普通开启器无授权弹窗；后通过临时 root 追加专用公钥，成功获得 uid2000 shell，结束后撤销该公钥并关闭端口 |
| 临时 root 命令执行 | UID 0 回执与 root 所有的普通回执文件相互印证 |
| 清理 | 前序实机运行后目录恢复、维护服务关闭；22/5555 拒绝连接 |
| 设置默认简洁桌面 | 系统 HOME 解析结果与用户物理 Home 键确认一致 |
| 设置侧边栏及 Back | 用户确认修复可用 |
| z8x6 返回极米桌面 | 初次安装后首点曾跳回简洁桌面，随后正常；用户重启后确认首次点击正常 |
| 独立切换器 | 14 项 shell 模拟场景、16 项策略检查通过；独立反复双向切换与异常中断恢复待明确实机确认 |
| 默认 HOME 重启持久性 | 用户做过重启测试，但没有单独记录重启后的系统 HOME 读回值；不把返回入口测试等同于完整持久性验收 |
| 开机广告、后台服务精简、量化性能 | 未实施/未验证 |

参考项目中 NEW Z6X 的经验只作为研究线索，不能据此宣称 NEW Z6X、Z7X 或其他版本可安装本切换器。分享时应保留本页范围说明。

最终用户验收：单文件只读白名单保护后，完整重启及应用退出回简洁桌面；z8x13 开机恢复用户反馈正常。长期联网/固件更新后持久性仍未验证。历史 z8x7 的返回修正失败记录保留在08文档。
