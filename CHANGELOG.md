# 更新日志

所有项目的显著变更都将记录在此文件中。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)，
并且本项目遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [Unreleased]

## [2.3.0] - 2026-06-21

### Added
- 引入 ViewModel 层：PlayerViewModel、HomeViewModel、SearchViewModel、LibraryViewModel、FavoritesViewModel、SettingsViewModel
- MusicService 全面迁移到 StateFlow 状态管理（currentSongFlow / isPlayingFlow / playbackProgressFlow / playbackDurationFlow / playModeFlow / playlistFlow）
- 新增音频焦点管理，来电或其他音频播放时自动暂停
- 新增外部公共目录歌曲缓存 `/sdcard/MelodyFlow/cache/`，卸载应用后缓存不丢失
- 新增智能缓存容量管理：LRU 淘汰策略 + 收藏歌曲保护
- 新增缓存一致性校验：启动时自动清理幽灵条目和孤立文件
- 新增本地歌曲扫描服务 LocalScanService，支持增量扫描和自定义扫描目录
- Library 页新增"在线收藏"和"本地歌曲"双 Tab
- 新增 JSON 歌曲存档备份/导入系统（收藏、历史、日历事件、音乐日记、AI 配置）
- 新增自动备份功能：收藏变更后 5 分钟防抖自动导出
- 新增启动时恢复引导：数据库为空且检测到备份时提示恢复
- 新增 Material You MD3 动态颜色主题与深色模式完整适配
- 新增播放器封面圆形裁剪与 FAB 风格播放按钮
- 新增 MD3 Slider 进度条和 MD3 SearchBar 搜索框

### Changed
- 优化横屏播放器布局：歌曲信息移至顶部居中显示
- 优化歌曲列表布局：歌曲名称和歌手显示在卡片中间
- 优化歌词界面关闭按钮层级，避免被遮挡
- 优化播放队列高亮更新为局部刷新，不再重建 Adapter

### Fixed
- 修复 LyricsFocusAdapter 双绑定崩溃
- 修复缓存恢复 url=null 导致重新缓存失败
- 修复历史记录无限增长，新增按歌曲 ID 去重与自动清理
- 修复取消收藏同时删除缓存的问题
- 修复 SearchFragment 清除单条历史误清全部
- 修复 PlayerActivity 进度更新频率过高（50ms → 200ms）
- 修复 AnimatorSet 缩放动画两组同时播放的问题
- 修复 SleepTimer 生命周期绑定错误，移至 MusicService
- 修复横屏播放队列内容不同步与点击崩溃
- 修复背景图片/渐变被 Fragment 不透明层遮挡的问题
- 修复 PlayerActivity viewModel 未初始化导致的崩溃
- 修复 layout_mini_player.xml 约束引用非同级视图的 lint 构建错误
- 修复 PlaylistImportActivity 中 RecyclerView 高度 wrap_content 与 setHasFixedSize(true) 冲突的 lint 构建错误

### Security
- 数据库升级使用 Migration(v6 → v7)，移除 fallbackToDestructiveMigration，避免升级丢数据

## [0.1.0] - 2024-06-11

### Added
- 初始版本发布
- 基础音乐播放功能
- 音乐搜索功能
- 歌词显示功能
- 收藏管理功能
- 本地音乐扫描
- 横竖屏适配
- Material Design 3 UI
