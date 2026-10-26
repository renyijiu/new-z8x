# 来源与许可

- 简洁桌面源自 Gathin23/cleanyourtv，提交 3b3f688a3737914482145b0895480ca771ff4c9c，MIT。保留根目录 LICENSE 和 LICENSES/cleanyourtv-MIT.txt。
- 仓库沿用 MIT 许可；修改版非极米官方软件，也非上游官方发布包。
- 源码保留上游 Outfit 字体资源，见 LICENSES/Outfit-OFL.txt 与 src/launcher/FONT-LICENSE-OFL.txt；当前默认显示改用系统字体，不因此删除原字体许可。
- Gradle wrapper / Gradle 与 AndroidX（RecyclerView 1.3.2、DynamicAnimation 1.0.0 及其传递依赖）采用 Apache 2.0；许可副本见 [LICENSES/Apache-2.0.txt](LICENSES/Apache-2.0.txt)。各组件版权归各自作者，源码文件中的声明保留。依赖声明在 `src/launcher/app/build.gradle`，完整解析列表可用 `./gradlew :app:dependencies --configuration releaseRuntimeClasspath` 查看。
- JUnit 4.13.2 为仅测试依赖（EPL 1.0），不随 APK 打包；许可见 https://github.com/junit-team/junit4/blob/r4.13.2/LICENSE-junit.txt 。Android SDK、构建插件等构建环境工具不作为仓库分发内容。
- Samba 容器通过 Alpine 包仓库安装 Samba，镜像不作为文件放入本仓库；各软件按各自许可使用。
- Bing 壁纸由服务提供方及图片权利人拥有；仓库不打包下载后的壁纸。桌面显示图片来源署名，联网服务可用性和图片内容不由本仓库保证。
- 不包含极米厂商 APK、主机私钥、签名私钥或第三方 ADB 开启器二进制。厂商服务名称、组件与必要配置片段用于兼容性说明。

研究线索还参考 [HG0539xDC860539/xgimi-clean](https://github.com/HG0539xDC860539/xgimi-clean) 的维护服务记录；本项目对 NEW Z8X 的实际固件另做校验。`LICENSES/cleanyourtv-MIT.txt` 保留上游原文，其中的 `launcher/` 是上游目录；本仓库对应 `src/launcher/`。MIT 不授予极米商标或 Bing 图片的额外使用权。
