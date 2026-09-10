# Maxfield Link Overlay

一个用于执行 [Maxfield](https://github.com/XClear0/maxfield) 规划的原生 Android 悬浮窗应用。它从手机本地导入
Maxfield 已生成的 Link 行动清单，并在其他应用上方显示当前 Link 的起点、终点和 Agent。

## 已实现功能

- 从系统文件选择器导入 Maxfield 规划；
- 支持 `agent_assignments.txt`、`agent_N_assignment.txt` 和
  `agent_assignments.csv`；
- 显示 Link 编号、总进度、Agent、起点 Portal 和终点 Portal；
- 当前 Link 起点与上一条不同时，浮窗整体变色提示；
- 在半透明悬浮窗中切换上一条/下一条 Link；
- 拖动悬浮窗并保存位置；
- 自动保存当前 Link 进度，重新打开后继续；
- 常驻通知显示当前 Link，并提供关闭入口；
- 根据系统语言显示英文或简体中文界面；
- 不联网、不读取游戏账号、不自动操作游戏。

## 使用方法

1. 使用 Maxfield 生成规划。Maxfield 默认会在输出目录创建
   `agent_assignments.txt` 和每位 Agent 对应的 `agent_N_assignment.txt`。
2. 把其中一个文件复制到 Android 手机：
   - 多人行动总控使用 `agent_assignments.txt`；
   - 某位 Agent 只看自己的任务时，使用对应的 `agent_N_assignment.txt`。
3. 安装并打开本应用，点击“选择 Maxfield 规划文件”。
4. 授予“显示在其他应用上层”权限；Android 13 及以上建议同时允许通知。
5. 点击“在游戏上方显示”，然后切换到游戏。
6. 使用“上一条”和“下一条”推进规划；按住悬浮窗顶部可拖动。

这里的“导入”只会把解析结果保存到应用自己的本地存储，不会上传到服务器。

## 构建

环境要求：JDK 17 或更高版本、Android SDK 36、Build Tools 36.1.0。

```powershell
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat testDebugUnitTest assembleDebug
```

Debug APK 输出到：

```text
app/build/outputs/apk/debug/app-debug.apk
```

使用 ADB 安装：

```powershell
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

没有配置发布 keystore 时，`assembleRelease` 会生成未签名 Release APK，供 F-Droid 等源码构建系统自行签名。GitHub Actions 发布流程会在构建前显式运行 `verifyReleaseSigning`，因此仍会拒绝缺少发布签名凭据的正式 GitHub Release。

## GitHub 自动发布

推送格式为 `v主版本.次版本.修订号` 的标签后，GitHub Actions 会自动执行单元测试、构建签名 APK、生成
SHA-256 校验文件并创建 GitHub Release。标签版本必须与 `app/build.gradle` 中的 `versionName` 一致。

首次使用前，需要创建发布 keystore，并在仓库的 Actions Secrets 中配置以下四项：

- `RELEASE_KEYSTORE_BASE64`
- `RELEASE_STORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

本地 `keystore.properties` 的格式如下；该文件和 `*.jks` 已被 Git 忽略：

```properties
storeFile=.signing/maxfield-overlay-release.jks
storePassword=你的-keystore-密码
keyAlias=maxfield-overlay
keyPassword=你的-key-密码
```

可以使用项目脚本生成高强度随机密码和新的发布密钥（脚本发现现有密钥时会拒绝覆盖）：

```powershell
.\scripts\create-release-keystore.ps1
```

登录 GitHub CLI 后，可用脚本将这些值安全写入当前仓库的 Actions Secrets：

```powershell
gh auth login --hostname github.com --web
.\scripts\configure-github-secrets.ps1
```

发布新版本时，先递增 `versionCode` 并修改 `versionName`，提交并推送，然后创建同版本标签。例如：

```powershell
git tag -a v1.2.2 -m "Maxfield Link Overlay v1.2.2"
git push origin v1.2.2
```

不要删除或重新生成发布 keystore；后续更新必须继续使用同一套签名密钥。

## Google Play 发布

已准备 Google Play 所需的中英文商店文案、512 × 512 图标、1024 × 500 Feature Graphic、
4 张 1080 × 1920 手机截图、隐私政策、Data safety 填写建议和前台服务声明模板。完整清单见
[`store-assets/google-play/README.md`](store-assets/google-play/README.md)。

使用已配置的发布密钥生成可上传的 Android App Bundle：

```powershell
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat verifyReleaseSigning bundleRelease
```

AAB 输出到 `app/build/outputs/bundle/release/app-release.aab`。首次上传前应确定 Play App Signing
是否沿用现有应用签名密钥；将构建包上传到内部测试轨道后，再完成 Console 中的内容声明和审核。

应用内隐私政策链接指向 `https://xclear0.github.io/Maxfield-Link-Overlay/privacy.html`。
上传 Play 之前，必须提交 `docs/` 并在 GitHub 仓库设置中启用 Pages，确保该网址可公开访问。

## 权限说明

- `SYSTEM_ALERT_WINDOW`：在游戏上方显示可交互悬浮窗；
- `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE`：切到游戏后继续保留悬浮窗；
- `POST_NOTIFICATIONS`：显示悬浮窗运行状态和关闭操作。

应用没有声明网络权限。

## 许可证

本项目的源代码及随附图像资产采用 [Apache License 2.0](LICENSE) 许可：

```text
Copyright 2026 XClear0
SPDX-License-Identifier: Apache-2.0
```

## 验证

- 单元测试覆盖 Maxfield TXT、当前 Maxfield 宽松 CSV 和标准带引号 CSV；
- 已使用 Maxfield 真实生成的 215 条中文 `agent_assignments.txt` 验证导入；
- 已在 API 35 模拟器验证悬浮显示、下一条切换、进度持久化和通知同步；
- `compileSdk` / `targetSdk` 为 API 36，最低支持 API 26（Android 8.0）。
