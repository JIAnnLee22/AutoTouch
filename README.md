# FunnyTool

<img src="artwork/funnytool-icon.png" width="120" alt="FunnyTool 图标" />

Android 多区域随机触摸工具。包名 **`jiannlee22.funnytool`**，最低 Android 8.0（API 26）。仅用于用户主动授权、主动配置的设备自动化。

## 已实现
- 多个圆形区域，启用/禁用、圆心与半径精确输入；悬浮层通过圆心连杆上的滑块缩放半径（视觉上限 80dp）、杆端「移动」抓手拖动位置（支持自动/手动翻转方向，避免手指遮挡圆圈）。
- 每次在圆内重新抽取面积均匀随机坐标；圆形必须完整处于安全屏幕矩形内。
- 所有时间均为 **ms**。每个区域批次的**首次延迟**为 `baseDelayMs + randomInclusive(randomDelayMinMs, randomDelayMaxMs)`，每次组触发重新抽取。
- 每区域可配置「单次触发点击次数」`clicksPerTrigger`（至少 1，默认 1），每一击重新随机圆内坐标和按压时长；第 2 次起只按 `clickIntervalMinMs~clickIntervalMaxMs` 独立随机等待，**不重复叠加首次基础延迟或首次随机附加**。间隔从上次手势成功完成后起算，包含上下限、每次重抽，相同上下限为固定间隔，0~0 为尽快下一击。
- 每区域短按时长也支持随机范围 `pressDurationMinMs~pressDurationMaxMs`（1~200 ms），每次提交手势前独立抽取整数，包含上下限；上下限相同为固定短按。
- 区域可分组，新建/改名/删除空组；单选开启一个组，只显示、编辑、运行选定组。可把无相关依赖的区域移到其他组；跨组依赖禁止。
- 开始时设置**本次组触发次数 N**（正整数，默认 1），不是历史累计次数，也不是每区域点击上限。每次组触发中所有启用区域按自己的首次延迟或上游整批完成依赖各执行一个连点批次，全部批次完成再开始下一次，完成 N 次后停止并弹出提示，保留悬浮窗最终计数。
- 支持 A → B → C 同组依赖，禁止循环；上游整批连点完成才激活下游，不会每一击都重复触发下游。实际单击统一串行，区域等待连点间隔时可执行其他到期区域，并非整批独占执行器。旧重复事件策略保留兼容，但当前不重叠的组触发不会产生同一遍内的重复上游事件；待执行事件最多 256 个。
- 配置以 Preferences DataStore + JSON 原子保存，各组保留独立坐标基准；本次目标次数、完成次数及执行进度不写入存储。
- 悬浮窗可展开/折叠；折叠为紧凑小胶囊，仅显示本次已完成次数和展开箭头，拖动或点击即可移动/展开；展开后可暂停/恢复/关闭/编辑，开始前可用加减调整本次次数。主界面支持直接「打开悬浮控制条」；若悬浮窗开启期间进程被划卡清理后由系统重启无障碍服务，服务重连时会自动拉起待机悬浮窗（保留位置与折叠状态，不自动恢复点击）。
- 悬浮暂停/恢复/停止；锁屏/熄屏、屏幕边界或方向变化、服务中断、手势失败时停止，不自动恢复。
- AI 生成启动图标，含 adaptive icon、五档位图与 Android 13 单色图标。

未确认需求采用的可调整首版默认值见 [AGENTS.md 第 10 节](AGENTS.md#10-首版实现范围与可调整默认值)。尚无双模式并行、多上游 AND/OR、导入导出、滑动或多点触控。

## 使用
1. 安装 APK，打开 FunnyTool，点击「前往系统授权」，阅读说明并在系统中启用 FunnyTool 无障碍服务。部分设备的侧载应用需要用户手动允许「受限设置」。建议同时点击「允许后台无限制」并在「应用详情 / 自启动」中开启省电无限制、自启动与最近任务锁定，避免国产系统清理后台时强行停止应用并关闭无障碍开关。
2. 新建或单选一个组，在该组添加区域，编辑首次基础/随机附加延迟、单次触发点击次数、独立连点间隔上下限、短按上下限（1~200 ms）；需要依赖时选择同组上游。例如每批 5 次、首次 `1000 + random(0,500) ms`、连点 `40~120 ms`：第 1 次等 1000~1500 ms，后 4 次各只等 40~120 ms，不再加 1000 ms。运行或暂停期间不能切组/修改参数，先停止再操作。
3. 设置本次组触发次数 N，然后打开悬浮编辑，通过系统导航/最近任务切换到目标应用；只显示当前组的圆，点选目标圆后通过连杆滑块缩放半径、按住杆端「移动」抓手拖动位置（可点 `⇄` 切换连杆左右方向），点击「保存」。编辑层会暂时接管屏幕触摸，系统导航仍可切换应用。
4. 保存后保留控制条（也可在主界面直接点击「打开悬浮控制条」），点击「开始」；若从暂停状态进入位置编辑，保存/取消后返回暂停并保留进度，点击「恢复」。悬浮条开始前也可加减本次次数。每次组触发先等待各自首次延迟，再执行各自连点批次，所有启用区域的整批点击完成后才进入下一次；组执行 N 次、区域每批 M 次，正常结束该区域共点击 N×M 次。
5. 运行时只有可拖动控制条，落点避开它；折叠后仅显示紧凑次数胶囊，拖动移动、点击展开。若在开启悬浮窗后划掉应用卡片，系统重启无障碍服务时会自动恢复待机悬浮窗（不自动恢复点击）；点击「关闭」或「停止」后不再自动拉起。暂停保留已完成组次数、当前遍已完成区域与每个区域的成功点击数，只续剩余点击；停止后再次开始清零，不保存历史累计计数。完成 N 次会弹出完成提示，悬浮计数保留，可展开查看并关闭。

**配置兼容**：旧 v1/v2 区域归入「默认组」，坐标、时间与依赖保留；v1 固定 `pressDurationMs` 转成相等上下限。保存为 JSON v4（含分组和连点参数），仍沿用原 DataStore 与配置键；v1/v2/v3 默认每批 1 次、连点间隔 100~100 ms，保留旧行为和已有配置。旧区域级运行上限仍不使用，新的「单次触发点击次数」是每批点击数而不是停止整个运行的上限。新格式不保证能回退给旧版读取；卸载重装仍会丢失配置，从旧预览签名切换到固定签名的限制见下文。

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

从 **v0.1.3** 起，默认 release 构建使用本仓库专用的**固定共享开发签名**，不再回退到临时 debug key。密钥按用户要求公开在 [`signing/`](signing/README.md)，本地与 CI 使用同一证书；后续同证书、版本码递增的 APK 可覆盖升级。

**安全警告**：私钥公开，任何人都能签出同身份的伪造 APK，只安装可信构建；这不是安全的生产签名。不要用于其他应用。旧 v0.1.1/v0.1.2 与新证书不同，首次切换通常需卸载旧版，卸载会删除配置；这两个旧 Release 按用户要求删除，但 Git tag 和历史保留。

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

推送 `v*` tag 后自动校验版本号、lint、构建 release APK、验证签名、生成 `SHA256SUMS.txt` 并创建 GitHub Release。构建失败摘要会写入 Actions 检查注解，便于无需下载完整日志时排查；依赖使用 Google 托管的 Maven Central 公共镜像优先，保留 Google Android Maven、Central 和 Plugin Portal。支持 `v0.1.0-beta.1`，会标为预发布；也支持在 Actions 手动输入已存在的 tag 重跑。版本名取自 tag，版本码为 `1000 + GITHUB_RUN_NUMBER`；勿将已发布 tag 移到其他提交。

### 可选私有生产签名覆盖
默认无需 Secrets，直接使用固定共享密钥；证书 SHA-256：
`40d32ff8d57d87894d4198ad48b1c4da010a2e46ed7a2d3b5e96442296936d0a`

如要改用**保密的生产签名**，在仓库 **Settings → Secrets and variables → Actions** 设置：

| Secret | 内容 |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | 私有 `.jks` 文件的 Base64 内容 |
| `ANDROID_KEYSTORE_PASSWORD` | keystore 密码 |
| `ANDROID_KEY_ALIAS` | 密钥别名 |
| `ANDROID_KEY_PASSWORD` | 对应密钥密码 |

四项必须全部配置，或全部留空；配置不完整时构建失败，不能悄悄回退到另一张证书。全部留空使用仓库内固定共享签名，全部配置则使用私有签名，产物均为 `*-release.apk`，并附 `SIGNING-CERTIFICATE.txt` 和 `SHA256SUMS.txt`。私有密钥必须自行备份且不要提交入库；**更换证书会影响已安装版本的覆盖升级**。

发布工作流会校验 APK 签名，并在默认模式核对 APK 证书指纹与仓库公钥证书一致。v0.1.3 新包成功发布后，仅删除 v0.1.1/v0.1.2 两个旧 Release 及其附件，不删除对应 tag。

本地正式签名使用 `ANDROID_KEYSTORE_PATH`（绝对路径）、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD` 环境变量；版本可用 `-PreleaseVersion=0.1.0 -PreleaseVersionCode=1001` 指定。

## 首次触发与连点计时
首次触发与连点后续间隔分开：上游**整批**完成后，子区域第一次点击由 `TimingRule.sampleDelayMs()` 计算自己的 `baseDelayMs + randomInclusive(min,max)`；同区域后续点击只由 `sampleClickIntervalMs()` 抽取独立间隔范围，等到单调时钟截止时间才派发手势。

例如 B 依赖 A，B 的基础延迟为 `2000 ms`、随机附加为 `300~500 ms`，则 A 整批完成后至少等待 `2300~2500 ms` 才提交 B 的第 1 次点击；其他区域的串行手势可能使 B 更晚，但不会让它提前。`0~0 ms` 仅等基础延迟，`500~500 ms` 则固定再加 500 ms；随机短按时长属于提交后的按下持续时间，不是点击前等待。暂停恢复时，尚未开始的区域重新抽取首次等待，已部分完成批次只重抽下一击的独立连点间隔，不重放成功点击。此前首次随机延迟核查未发现遗漏；新版本只是明确区分首次触发与后续连点，不移除首次随机附加。

## 验证记录
### v0.1.9：紧凑展开悬浮控制条
- 本地 Debug/Release/lint 构建通过，lint 0 错误、19 条警告；Release 证书 SHA-256 仍为 `40d32ff8d57d87894d4198ad48b1c4da010a2e46ed7a2d3b5e96442296936d0a`。
- 在 Android 17（API 37）模拟器实测展开态悬浮控制条：最大宽度收紧至 `172dp`，自定义 `26dp` 紧凑按钮与单行状态提示显示正常，次数数字对比度清晰。
- 已在小米 MIUI 14 物理机（`M2006J10C`）覆盖安装验证包并确认无障碍服务保持连接；未新增自动化测试用例。

### v0.1.8：划卡重连自动拉起悬浮窗与紧凑折叠胶囊
- 已通过 [GitHub Actions](https://github.com/JIAnnLee22/AutoTouch/actions/runs/45321182011) 发布 [v0.1.8](https://github.com/JIAnnLee22/AutoTouch/releases/tag/v0.1.8)，版本名 `0.1.8`、版本码 `1009`（包名 `jiannlee22.funnytool`，min SDK 26、target SDK 35）；回下载云端 APK 与 `SHA256SUMS.txt`、`SIGNING-CERTIFICATE.txt` 校验通过，证书 SHA-256 与仓库公钥证书一致（`40d32ff8d57d87894d4198ad48b1c4da010a2e46ed7a2d3b5e96442296936d0a`）。
- 本地 Debug/Release/lint 构建通过，lint 0 错误、19 条警告；Release 证书 SHA-256 仍为 `40d32ff8d57d87894d4198ad48b1c4da010a2e46ed7a2d3b5e96442296936d0a`。
- 在小米 MIUI 14 物理机（`M2006J10C`，Android 12）实测：开启无障碍、忽略电池优化与自启动后，打开并折叠悬浮窗，从最近任务划掉 FunnyTool 卡片（`SwipeUpClean` 杀进程）后，系统自动重启 `TapAccessibilityService` 并自动拉起紧凑折叠悬浮窗（`0 ▸`），保持待机不自动点击。
- 在 Android 17（API 37）模拟器实测：主界面「打开悬浮控制条」、紧凑折叠胶囊拖动与点击展开、`kill -9` 后自动恢复悬浮窗均正常。

### v0.1.7：无障碍保活引导与连杆悬浮编辑
- 已通过 [GitHub Actions](https://github.com/JIAnnLee22/AutoTouch/actions/runs/45314915613) 发布 [v0.1.7](https://github.com/JIAnnLee22/AutoTouch/releases/tag/v0.1.7)，版本名 `0.1.7`、版本码 `1008`（包名 `jiannlee22.funnytool`，min SDK 26、target SDK 35）；回下载云端 APK 与 `SHA256SUMS.txt`、`SIGNING-CERTIFICATE.txt` 校验通过，证书 SHA-256 与仓库公钥证书一致（`40d32ff8d57d87894d4198ad48b1c4da010a2e46ed7a2d3b5e96442296936d0a`）。
- 本地 Debug/Release/lint 构建通过，lint 0 错误、14 条警告（含 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 对应的 `BatteryLife` 提示）；Release 证书 SHA-256 仍为 `40d32ff8d57d87894d4198ad48b1c4da010a2e46ed7a2d3b5e96442296936d0a`。
- 在 Android 17（API 37）模拟器实测：`kill -9` 杀进程后系统自动重启并恢复 `TapAccessibilityService` 绑定，`am force-stop` 会清除系统无障碍开关；新版主界面可区分「开关已开但服务未连接」与「未开启」，并展示电池优化状态与跳转入口。
- 在模拟器实测新版悬浮编辑：点选切换区域、连杆滑块缩放（视觉上限 `80dp`）、杆端「移动」抓手拖动平移及靠近边缘自动翻转方向均正常，保存后服务保持绑定。
- 尚未在小米 MIUI 14 物理机实测开启「省电无限制 + 自启动 + 最近任务锁定」后的划卡保活效果；未新增自动化测试用例。

### v0.1.6：区域连点与独立随机间隔
- 已通过 [GitHub Actions](https://github.com/JIAnnLee22/AutoTouch/actions/runs/45309419191) 发布 [v0.1.6](https://github.com/JIAnnLee22/AutoTouch/releases/tag/v0.1.6)，版本名 `0.1.6`、版本码 `1007`（包名 `jiannlee22.funnytool`，min SDK 26、target SDK 35）；回下载云端 APK 与 `SHA256SUMS.txt`、`SIGNING-CERTIFICATE.txt` 校验通过，证书 SHA-256 与仓库公钥证书一致（`40d32ff8d57d87894d4198ad48b1c4da010a2e46ed7a2d3b5e96442296936d0a`）。
- 本地 Debug/Release/lint 构建通过，lint 0 错误、11 条警告；Release 证书 SHA-256 仍为 `40d32ff8d57d87894d4198ad48b1c4da010a2e46ed7a2d3b5e96442296936d0a`，未更换固定签名。
- 每批点击数与后续随机间隔独立于首次触发延迟；暂停保留批次内进度，整批完成才激活下游，组计数规则不变。
- v0.1.4/v0.1.5 未发布成功，保留失败 tag 不移动历史。v0.1.5 的错误摘要定位为 Maven Central HTTP 429，发生于 AGP 依赖解析阶段而非应用编译；已在插件和依赖两处优先使用 Google 托管的 Maven Central 公共镜像，并仅对 429/502/503/504/超时/连接重置最多重试 3 次，编译错误不重试。
- 本次不新增自动化测试用例；构建/静态复核不能代替真机连点、间隔时序、暂停续跑和 v4 配置迁移验收。

### v0.1.3：固定签名与依赖随机等待核查
- 已通过 [GitHub Actions](https://github.com/JIAnnLee22/AutoTouch/actions/runs/45305630694) 发布 [v0.1.3](https://github.com/JIAnnLee22/AutoTouch/releases/tag/v0.1.3)，版本码 `1004`；云端 APK 与本地/仓库公钥证书指纹一致，APK 与签名说明的 SHA-256 校验通过。
- 已删除未固定签名的 v0.1.1/v0.1.2 两个 Release 及附件，当前仅保留 v0.1.3 Release；旧 Git tag 和历史仍在。
- 本地 Debug/Release/lint 构建通过，lint 0 错误、11 条警告；Release 证书 SHA-256 与仓库公钥证书一致，`actionlint`（含 ShellCheck）通过。
- 依赖随机等待经模型、入队截止时间、等待循环、配置存取四条路径静态复核；未新增测试用例，尚未完成真机时序测量。

### v0.1.2：随机短按、分组、本次组触发次数与折叠悬浮窗
- 已通过 [GitHub Actions](https://github.com/JIAnnLee22/AutoTouch/actions/runs/45158158676) 自动发布 v0.1.2（旧 Release 已删除，[源码 tag 保留](https://github.com/JIAnnLee22/AutoTouch/tree/v0.1.2)），回下载 APK 的 SHA-256 与签名校验通过；版本名 `0.1.2`、版本码 `1003`。
- JDK 21 下 `:app:assembleDebug :app:assembleRelease :app:lintDebug` 构建通过，lint 0 错误、11 条警告；release APK 签名验证通过，包名 `jiannlee22.funnytool`、版本名 `0.1.2`、min SDK 26、target SDK 35。
- 按压区间、同一次抽样用于手势/超时、分组隔离、每次组触发完成条件及暂停进度采用代码复核；未新增或运行自动化测试用例，完整真机手势、配置迁移、折叠拖动与次数行为仍需按下方清单人工验收。

### 首版历史验证
- JDK 17 下 `:app:assembleDebug :app:assembleRelease :app:lintDebug` 构建通过；lint 0 错误，11 条警告（版本升级建议、旧系统忽略属性、兼容位图/adaptive 资源并存）。
- `apksigner verify --verbose` 验证 release APK 签名通过；`aapt dump badging` 确认包名、min SDK 26、target SDK 35。
- `actionlint`（含 ShellCheck）检查 tag 发布工作流通过。
- `v0.1.1` 已实际跑通 [GitHub Actions](https://github.com/JIAnnLee22/AutoTouch/actions/runs/45150085044)，自动创建 Release（现已删除，[源码 tag 保留](https://github.com/JIAnnLee22/AutoTouch/tree/v0.1.1)）；已回下载云端 APK，SHA-256 和 APK 签名验证通过，确认版本名 `0.1.1`、版本码 `1002`、包名 `jiannlee22.funnytool`。初始 `v0.1.0` 的 SDK 初始化失败，保留原 tag、不移动历史。
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
- [ ] 检查超大时间/Long 溢出、负数、上下限倒置、NaN/Infinity 坐标均拒绝；短按上下限均限定 1~200 ms，50~50 ms 固定，40~80 ms 每次重新抽取。
- [ ] 本次组触发分别设置 1 次和 10 次，0/负数/溢出被拒绝；组内单次点击不累加组次数，整遍完成后才 +1，达到 N 后自动停止、提示并保留最终计数，不保存历史。
- [ ] 当前遍最后一次手势执行途中暂停，其成功回调仍完成当前遍；恢复不得多点。暂停期间不执行新下游手势，恢复后仅为未完成区域重新计时；暂停编辑位置后不清进度。
- [ ] 多组单选，只编辑/运行当前组；切组保留其他组坐标与配置，跨组依赖拒绝；无相关依赖的区域可移动到其他组。
- [ ] 折叠仅显示次数和展开箭头，拖动次数移动，箭头可展开；在屏幕四边展开后工具条仍完整可达，不拦截目标区域点击。
- [ ] 旧 v1 固定短按可读为相等上下限，v1/v2 区域迁入默认组；v1/v2/v3 默认每批 1 次点击，保存后的 v4 能重启读取并保留其他字段，非法或缺失字段不静默覆盖原数据。

系统调度、节电策略、目标应用与厂商无障碍实现会影响实际时序，无法保证任意设备上精确到 1 ms；尚未评估 Google Play 无障碍使用政策，不应直接宣称可上架。

## 源码组织
`app/src/main/java/jiannlee22/funnytool/`：`MainActivity`（Compose）、`MainViewModel`（单向状态）、`ClickRegion`（模型/校验/随机）、`ClickScheduler`（事件队列）、`TapAccessibilityService`（系统接口/生命周期）、`RegionOverlay`（悬浮交互）、`SettingsStore`（DataStore/简单应用容器）。不引入 Hilt、Room、Navigation 或专用 MVI 框架。
