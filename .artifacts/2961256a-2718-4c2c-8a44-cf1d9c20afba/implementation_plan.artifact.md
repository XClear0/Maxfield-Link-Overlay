# 调整布局以实现全屏及无滚动体验

此方案旨在通过优化 `MainActivity` 的布局结构、减少间距以及启用全屏显示，使应用内容在不需要滚动的情况下适配整个屏幕。

## 用户审查要求

> [!IMPORTANT]
> - **移除滚动视图**：我们将移除 `ScrollView`，如果内容仍然超出屏幕，底部内容将被裁剪。
> - **布局压缩**：减小内边距和外边距可能会使界面显得更紧凑。
> - **全屏模式**：我们将隐藏状态栏和导航栏，以获得更大的显示空间。

## 拟议更改

### Android 应用模块

#### [修改] [MainActivity.java](file:///D:/Github/app/app/src/main/java/io/github/xclear0/maxfieldoverlay/MainActivity.java)

- 移除 `ScrollView`。
- 将 `root` 的垂直内边距从 `24dp` 减小到 `16dp`（或根据窗口高度动态调整）。
- 减小各个组件之间的 `topMargin`。
- 缩小标题和说明文字的大小。
- 在 `onCreate` 中设置全屏标志（隐藏状态栏和导航栏）。
- 优化 `WindowInsets` 处理，避免过度留白。

#### [修改] [Ui.java](file:///D:/Github/app/app/src/main/java/io/github/xclear0/maxfieldoverlay/Ui.java)

- 如果需要，添加用于紧凑布局的辅助工具。

## 验证计划

### 自动测试
- 目前没有自动化 UI 测试，将依赖手动验证。

### 手动验证
- 部署应用到模拟器或设备。
- 确认进入应用后不再出现滚动条。
- 确认状态栏和导航栏被隐藏（或应用内容延伸至其下方）。
- 检查所有操作按钮（导入、开始、停止）是否在屏幕内可见且可点击。
