# FunnyTool

<img src="artwork/funnytool-icon.png" width="120" alt="FunnyTool 图标" />

Android 多区域随机触摸工具。包名 **`jiannlee22.funnytool`**，最低 Android 8.0（API 26）。仅用于用户主动授权、主动配置的设备自动化。

## 已实现
- 多个圆形区域，启用/禁用、圆心与半径精确输入；悬浮层拖动圆心、拖动圆边缩放。
- 每次在圆内重新抽取面积均匀随机坐标；圆形必须完整处于安全屏幕矩形内。
- 所有时间均为 **ms**：`baseDelayMs + randomInclusive(randomDelayMinMs, randomDelayMaxMs)`，包含上下限，每次重新抽取。
- 每区域独立循环，或依赖一个上游的**成功手势完成回调**，延迟点击一次；支持 A → B → C，禁止循环依赖。
- 重复事件可选忽略新事件、按最新事件重新计时、每次排队；总待执行事件最多 256 个，溢出安全停止。
- 统一串行提交手势；单套配置以 Preferences DataStore + JSON 原子保存；显示本次完成计数。
- 悬浮暂停/恢复/停止；锁屏/熄屏、屏幕边界或方向变化、服务中断、手势失败时停止，不自动恢复。
- AI 生成启动图标，含 adaptive icon、五档位图与 Android 13 单色图标。

未确认需求采用的可调整首版默认值见 [AGENTS.md 第 10 节](AGENTS.md#10-首版实现范围与可调整默认值)。尚无双模式并行、多上游 AND/OR、多套方案、导入导出、次数上限、滑动或多点触控。

## 使用
1. 安装 APK，打开 FunnyTool，点击「前往系统授权」，阅读说明并在系统中启用 FunnyTool 无障碍服务。部分设备的侧载应用需要用户手动允许「受限设置」。
2. 添加区域，编辑基础延迟、随机附加上下限、短按时长（1~200 ms）；需要依赖时选择上游及重复事件策略。
3. 打开悬浮编辑，通过系统导航/最近任务切换到目标应用；拖动圆心移动、拖动圆边缩放，点击悬浮条「保存」。编辑层会暂时接管屏幕触摸，系统导航仍用于切换应用。
4. 保存后编辑层移除、控制条保留，点击控制条「恢复」即可从目标应用开始；也可先停止，再从主界面「开始运行」。首次点击先等待一次配置延迟。运行时只有可拖动的控制条；落点避开控制条。
5. 使用悬浮条暂停或停止。暂停也会清空待执行事件，恢复从新延迟开始；停止不会关闭系统无障碍授权。

**安全边界**：不读取页面树、不申请网络权限、不自动授权。Android 的手势完成回调不代表目标应用业务处理成功。停止无法保证撤回已经交给 Android 的当前单次按压（首版上限 200 ms），但不会继续传播其依赖事件。回调持续丢失时会保持停止并阻止重启手势，需要用户关闭后重新启用无障碍服务；不会冒险并发提交新手势。工具条完整遮住某区域、系统拒绝手势或回调超时会停止，而不是盲目继续点击。圆形位于安全矩形内，不在系统边缘、刘海/挖孔区域执行手势；厂商实现仍需真机验证。

## 构建

已核对的稳定组合：AGP 8.10.1、Gradle 8.11.1、Kotlin/Compose 编译器 2.2.10、Compose BOM 2025.05.01、compile/target SDK 35、JVM 17。本机已验证 JDK 17/21，CI 使用 JDK 21 与 Ubuntu 24.04 预装 Android SDK（避免重复初始化命令行工具）。

```bash
# Android Studio 打开本目录，或设置 ANDROID_HOME 并安装 SDK 35 / Build Tools 35.0.0
./gradlew :app:assembleDebug :app:lintDebug
./gradlew :app:assembleRelease
```

输出：
- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/release/app-release.apk`
- `app/build/reports/lint-results-debug.html`

默认 release 构建为非 debuggable、使用 debug 密钥签名的可安装**预览包**，不是正式生产签名。稳定覆盖升级需设置下述私有签名环境变量。不要提交密钥。

如在 NixOS 遇到 Maven aapt2 无法执行，可按本机实际 SDK 路径运行：
```bash
./gradlew :app:assembleDebug -Pandroid.aapt2FromMavenOverride="$ANDROID_HOME/build-tools/35.0.0/aapt2"
```

重建图标（不影响常规 Android 构建；需要 Python Pillow）：
```bash
python3 artwork/prepare_icons.py
# NixOS 无 Pillow 时：
nix shell --impure --expr 'let pkgs = import <nixpkgs> {}; in pkgs.python3.withPackages (ps: [ ps.pillow ])' -c python3 artwork/prepare_icons.py
```

## GitHub tag 自动 Release
仓库：<https://github.com/JIAnnLee22/AutoTouch>。工作流：[`.github/workflows/release.yml`](.github/workflows/release.yml)。

```bash
# 发布新版本时使用一个尚未存在的版本号，例如：
git tag -a v0.2.0 -m "FunnyTool 0.2.0"
git push origin v0.2.0
```

推送 `v*` tag 后自动校验版本号、lint、构建 release APK、验证签名、生成 `SHA256SUMS.txt` 并创建 GitHub Release。支持 `v0.1.0-beta.1`，会标为预发布；也支持在 Actions 手动输入已存在的 tag 重跑。版本名取自 tag，版本码为 `1000 + GITHUB_RUN_NUMBER`；勿将已发布 tag 移到其他提交。

### 正式签名（推荐在第一次长期使用前配置）
在仓库 **Settings → Secrets and variables → Actions** 设置：

| Secret | 内容 |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | 私有 `.jks` 文件的 Base64 内容 |
| `ANDROID_KEYSTORE_PASSWORD` | keystore 密码 |
| `ANDROID_KEY_ALIAS` | 密钥别名 |
| `ANDROID_KEY_PASSWORD` | 对应密钥密码 |

四项必须全部配置，或全部留空。全部留空时仍能自动发布 `*-preview.apk`；GitHub 的临时 runner 会生成不同 debug 密钥，**不同 tag 之间可能需要卸载后重装，卸载会丢失配置**。设置稳定私有签名后产物为 `*-release.apk`。请自行备份密钥，切勿上传到 Git。

本地正式签名使用 `ANDROID_KEYSTORE_PATH`（绝对路径）、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD` 环境变量；版本可用 `-PreleaseVersion=0.1.0 -PreleaseVersionCode=1001` 指定。

## 本次验证结果
- JDK 17 下 `:app:assembleDebug :app:assembleRelease :app:lintDebug` 构建通过；lint 0 错误，11 条警告（版本升级建议、旧系统忽略属性、兼容位图/adaptive 资源并存）。
- `apksigner verify --verbose` 验证 release APK 签名通过；`aapt dump badging` 确认包名、min SDK 26、target SDK 35。
- `actionlint`（含 ShellCheck）检查 tag 发布工作流通过。
- `v0.1.1` 已实际跑通 [GitHub Actions](https://github.com/JIAnnLee22/AutoTouch/actions/runs/45150085044)，自动创建 [Release](https://github.com/JIAnnLee22/AutoTouch/releases/tag/v0.1.1)；已回下载云端 APK，SHA-256 和 APK 签名验证通过，确认版本名 `0.1.1`、版本码 `1002`、包名 `jiannlee22.funnytool`。初始 `v0.1.0` 的 SDK 初始化失败，保留原 tag、不移动历史。
- 已在连接的 Android 12（API 31）设备安装 debug APK，并成功执行 Activity 启动命令、确认应用进程存在，未发现该应用崩溃日志。设备处于熄屏状态，**未验证屏幕可视效果、无障碍授权、悬浮交互和实际跨应用手势**，也未通过命令自动授权。

## 真机验收清单
本次不新增单元/仪器测试用例；以下是人工验收步骤，不能视为已通过的自动测试。

- [ ] 无权限时禁止开始；系统授权撤销后服务断开、悬浮层消失，不再发出新手势。
- [ ] 创建多个圆并拖动/缩放，保存后重启应用，坐标、时间、启用状态和依赖均保留。
- [ ] 将圆拖向屏幕四边，不能越出安全范围；旋转屏幕后停止，重新编辑确认才能继续。
- [ ] 独立区域 A 配置 `12000 + random(0,10000) ms`，反复验证间隔重新抽样；`0~0 ms` 不增加随机延迟。
- [ ] B 依赖 A、C 依赖 B；仅完成回调传播事件，观察三种重复事件策略和禁止循环的提示。
- [ ] 用系统触摸可视化或可控目标应用确认落点在圆内且变化，多个区域不同时提交手势。
- [ ] 验证暂停/停止/熄屏/锁屏/方向变化清空待执行任务，恢复时不补发旧事件。
- [ ] 控制条移到圆上时不点控制按钮；圆被完全遮挡时停止并提示。
- [ ] 验证异常手势取消、服务重启后不自动恢复；零延迟仍可停止；事件过多时安全停止。
- [ ] 检查超大时间/Long 溢出、负数、上下限倒置、NaN/Infinity 坐标均拒绝；按压限制清晰。

系统调度、节电策略、目标应用与厂商无障碍实现会影响实际时序，无法保证任意设备上精确到 1 ms；尚未评估 Google Play 无障碍使用政策，不应直接宣称可上架。

## 源码组织
`app/src/main/java/jiannlee22/funnytool/`：`MainActivity`（Compose）、`MainViewModel`（单向状态）、`ClickRegion`（模型/校验/随机）、`ClickScheduler`（事件队列）、`TapAccessibilityService`（系统接口/生命周期）、`RegionOverlay`（悬浮交互）、`SettingsStore`（DataStore/简单应用容器）。不引入 Hilt、Room、Navigation 或专用 MVI 框架。
