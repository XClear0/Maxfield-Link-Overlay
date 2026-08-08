# Maxfield Link Overlay

一个用于执行 [Maxfield](https://github.com/XClear0/maxfield) 规划的原生 Android 悬浮窗应用。它从手机本地导入
Maxfield 已生成的 Link 行动清单，并在其他应用上方显示当前 Link 的起点、终点和 Agent。

## 已实现功能

- 从系统文件选择器导入 Maxfield 规划；
- 支持 `agent_assignments.txt`、`agent_N_assignment.txt` 和
  `agent_assignments.csv`；
- 显示 Link 编号、总进度、Agent、起点 Portal 和终点 Portal；
- 在半透明悬浮窗中切换上一条/下一条 Link；
- 拖动悬浮窗并保存位置；
- 自动保存当前 Link 进度，重新打开后继续；
- 常驻通知显示当前 Link，并提供关闭入口；
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

## 权限说明

- `SYSTEM_ALERT_WINDOW`：在游戏上方显示可交互悬浮窗；
- `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE`：切到游戏后继续保留悬浮窗；
- `POST_NOTIFICATIONS`：显示悬浮窗运行状态和关闭操作。

应用没有声明网络权限。

## 验证

- 单元测试覆盖 Maxfield TXT、当前 Maxfield 宽松 CSV 和标准带引号 CSV；
- 已使用 Maxfield 真实生成的 215 条中文 `agent_assignments.txt` 验证导入；
- 已在 API 35 模拟器验证悬浮显示、下一条切换、进度持久化和通知同步；
- `compileSdk` / `targetSdk` 为 API 36，最低支持 API 26（Android 8.0）。
