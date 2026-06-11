# Debug Session: Player Cover & Landscape Bug

**Session ID**: player-cover-landscape-bug  
**Status**: [OPEN]  
**Created**: 2026-06-10  

## Symptoms

1. **横屏模式下播放器初始化失败** - 旋转到横屏时播放器界面无法正常显示
2. **竖屏模式下歌曲封面图片无法显示** - 播放歌曲时封面区域空白

## Environment

- Android App (MelodyFlow)
- PlayerActivity with landscape/portrait layouts
- Glide for image loading
- MediaBrowserServiceCompat for music playback

## Hypotheses

### H1: 封面加载逻辑在竖屏模式下未正确执行
- **Observation Point**: `updateCoverView()` 方法调用时机和参数
- **Falsifiable If**: 日志显示 `updateCoverView()` 被调用但 Glide 未执行，或方法根本未被调用

### H2: 横屏布局文件缺少必要的视图ID或结构不一致
- **Observation Point**: `activity_player.xml` vs `activity_player_land.xml` 的视图ID对比
- **Falsifiable If**: 横屏布局中关键视图（coverContainer, ivCover）ID缺失或类型不匹配

### H3: Configuration Change 时 Activity 重建导致状态丢失
- **Observation Point**: `onConfigurationChanged()` 和 `onCreate()` 的生命周期日志
- **Falsifiable If**: 旋转后 Activity 重新创建但视图未重新初始化

### H4: Glide 加载时 Context 或生命周期问题
- **Observation Point**: Glide 加载时的 Activity 状态和生命周期
- **Falsifiable If**: Glide 抛出异常或加载回调未触发

### H5: 歌曲数据对象中 pic 字段为空或格式不正确
- **Observation Point**: Song 对象的 pic 字段值
- **Falsifiable If**: pic 字段为空、本地路径不存在或 URL 格式错误

## Next Steps

1. Add instrumentation logs to PlayerActivity
2. Collect runtime evidence
3. Analyze and confirm root cause
4. Implement minimal fix
5. Verify with post-fix logs
