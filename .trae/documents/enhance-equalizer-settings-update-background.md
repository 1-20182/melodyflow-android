# 执行计划：均衡器视觉重设计 + 设置检查更新 + 同步版本日志 + 修复背景

## 一、当前状态评估

### 1.1 已完成的修改
- ✅ **背景设置修复** (`SettingsActivity.kt`) — `applyGradientPreset()` 已添加 `bgFile.delete()`, `clearCache()`, `applyToActivity()`, 广播通知
- ✅ **均衡器 XML 布局重设计** (`activity_equalizer.xml`) — 卡片式分区布局，含启用开关、音效预设、频段均衡、音效增强四个区块
- ✅ **更新日志同步应用** (`InfoPagerAdapter.kt`) — `getChangelog()` 已包含 v1.0.0 ~ v2.0.0 完整历史

### 1.2 待完成的修改
- ❌ **均衡器 Kotlin 代码适配** (`EqualizerActivity.kt`) — 未适配新布局控件，无动画效果
- ❌ **创建 UpdateChecker 工具类** — 无在线检查更新功能
- ❌ **创建更新弹窗布局** (`dialog_update.xml`) — 不存在
- ❌ **设置页添加检查更新按钮** — 布局和逻辑均未添加
- ❌ **同步版本号** — `strings.xml` 中 `setting_version = "版本 1.0.0"` 硬编码，与实际 `BuildConfig.VERSION_NAME = "2.0.0"` 不一致

---

## 二、修改内容明细

### 2.1 均衡器界面 - Kotlin 代码适配 (动画+样式)

**涉及文件**: `d:\melodyflow\app\src\main\java\com\melodyflow\app\ui\EqualizerActivity.kt`

**需要修改**:
1. `setupEqualizerBands()` 中频段滑块创建：应用 `Widget.MelodyFlow.Slider` 样式，添加 `tvBandRange` 文本更新逻辑
2. 添加 `animatePresetChange()` 方法：预设切换时使用 `ValueAnimator` 平滑过渡频段滑块值，动画持续时间 300ms
3. 添加 `animateSliderValue(slider, targetValue)` 辅助方法
4. 滑块垂直方向改为从底部延伸布局（使用 `R.id.layoutBands` 已更新的新布局）
5. 绑定 `tvBassValue` 和 `tvVirtualizerValue` 文本更新（显示百分比）
6. 确保 `switchEnable` 切换时联动 `cardEnable` 的透明度动画

### 2.2 创建 UpdateChecker 工具类

**新建文件**: `d:\melodyflow\app\src\main\java\com\melodyflow\app\util\UpdateChecker.kt`

**实现方案**:
- 使用项目已有的  `OkHttp` 调用 GitHub Releases API
- API: `https://api.github.com/repos/1-20182/melodyflow-android/releases/latest`
- 使用 `Gson` 解析响应 JSON
- 版本号比较：去除 `v` 前缀后与 `BuildConfig.VERSION_NAME` 比较
- 数据类：`UpdateInfo(val hasUpdate: Boolean, val latestVersion: String, val changelog: String, val downloadUrl: String)`
- 异步回调：使用协程 `suspend` 函数

**关键代码结构**:
```kotlin
data class UpdateInfo(
    val hasUpdate: Boolean,
    val latestVersion: String,
    val changelog: String,
    val downloadUrl: String
)

class UpdateChecker {
    suspend fun checkForUpdate(): UpdateInfo {
        // OkHttp 请求 GitHub API
        // 解析 tag_name, body, html_url
        // 比较版本号
        // 返回 UpdateInfo
    }
}
```

### 2.3 创建更新弹窗布局

**新建文件**: `d:\melodyflow\app\src\main\res\layout\dialog_update.xml`

**布局内容**:
- 使用 `MaterialCardView` 作为容器
- `TextView` — 标题 "发现新版本"
- `TextView` — 新版本号（如 "v2.1.0"）
- `ScrollView` + `TextView` — 更新日志内容
- 两个按钮："立即更新"（打开 GitHub 下载页）、"稍后再说"

### 2.4 设置页添加检查更新

**涉及文件**:
- `d:\melodyflow\app\src\main\res\layout\activity_settings.xml`
- `d:\melodyflow\app\src\main\java\com\melodyflow\app\ui\SettingsActivity.kt`

**XML 修改**: 在"关于"卡片 (`About Card`) 中添加：
- 在 `btnViewChangelog` 下方添加 `btnCheckUpdate` 按钮
- 按钮样式为 `Widget.MelodyFlow.Button.Primary`
- 文本为 "检查更新"

**Kotlin 修改**: 在 `onCreate()` 中添加：
- 绑定 `btnCheckUpdate`
- 点击后启动协程调用 `UpdateChecker.checkForUpdate()`
- 有更新 → 弹出 `dialog_update` 弹窗
- 无更新 → Toast "当前已是最新版本"
- 网络错误 → Toast "检查更新失败，请检查网络连接"

### 2.5 同步版本号与更新日志

**涉及文件**:
1. `d:\melodyflow\app\build.gradle.kts` — 升级版本号
   - `versionCode = 8`
   - `versionName = "2.1.0"`

2. `d:\melodyflow\app\src\main\res\values\strings.xml`
   - `setting_version` 改为 `"版本 %s"` 格式，代码中通过 `getString(R.string.setting_version, BuildConfig.VERSION_NAME)` 动态格式化

3. `d:\melodyflow\app\src\main\java\com\melodyflow\app\ui\InfoPagerAdapter.kt`
   - 在 `getChangelog()` 顶部添加 v2.1.0 (2026-06-21) 条目：
     - 🎛️ 均衡器界面视觉重设计，卡片式分区布局
     - 🔄 新增检查更新功能（设置页）
     - 🖼️ 修复背景设置不起作用的问题
     - ⚡ 均衡器频段切换平滑动画

---

## 三、执行步骤（按顺序）

| 步骤 | 内容 | 文件 | 优先级 |
|------|------|------|--------|
| 1 | 均衡器 Kotlin 代码适配（动画+样式） | EqualizerActivity.kt | 高 |
| 2 | 创建 UpdateChecker 工具类 | UpdateChecker.kt (新建) | 中 |
| 3 | 创建更新弹窗布局 | dialog_update.xml (新建) | 中 |
| 4 | 设置页添加检查更新按钮 | activity_settings.xml + SettingsActivity.kt | 中 |
| 5 | 同步版本号 + 更新日志 | build.gradle.kts + strings.xml + InfoPagerAdapter.kt | 低 |

---

## 四、验证方式

1. **均衡器**：打开播放器 → 更多 → 音质均衡器，验证新 UI 显示正常，频段滑块操作流畅，预设切换有平滑动画，百分比数值实时更新
2. **检查更新**：设置 → 点击"检查更新" → 有更新弹窗显示版本号和日志 → "立即更新"跳转 GitHub
3. **版本号**：设置 → 关于 → 版本号显示 "2.1.0"
4. **背景**：设置 → 选择渐变预设 → 返回验证背景立即生效（已修复）
5. **编译通过**：`./gradlew assembleDebug` 无错误

---

## 五、风险与注意事项

- GitHub API 请求可能因网络问题失败，需做好异常处理
- 协程需在 `lifecycleScope` 中启动，避免内存泄漏
- `infoPagerAdapter.getChangelog()` 是硬编码字符串，更新日志需手动维护
- `strings.xml` 中 `setting_version` 修改为格式字符串后，需确保所有引用处同步更新