# MelodyFlow

一款基于 Android + Kotlin 构建的移动端音乐播放器，采用 Material Design 3 设计语言。

**主要功能：** 音乐搜索、歌词同步显示、收藏管理、歌单导入、本地音乐扫描、播放队列管理、横竖屏适配。

**技术栈：** Android SDK / Kotlin / Jetpack Compose / Room / Retrofit / Glide

## 功能特性

- 🎵 **音乐搜索** - 支持多平台音乐搜索和在线播放
- 🎤 **歌词同步** - 实时歌词滚动显示，支持全屏歌词模式
- ❤️ **收藏管理** - 收藏喜欢的歌曲，快速访问
- 📁 **歌单导入** - 支持导入外部歌单
- 💾 **本地音乐** - 扫描并播放本地音乐文件
- 📱 **横竖屏适配** - 完美适配手机和平板的横竖屏切换
- 🎨 **Material Design 3** - 现代化的 UI 设计

## 已知问题

### 歌词界面相关问题

- **歌词界面关闭按钮偶发失效**：在多次进入/退出歌词界面后，关闭按钮可能无法正常响应点击事件。临时解决方法：使用返回键退出歌词界面。

## 安装

```bash
# 克隆仓库
git clone https://github.com/yourusername/melodyflow.git

# 打开项目
# 使用 Android Studio 打开项目目录

# 构建并运行
./gradlew installDebug
```

## 技术架构

- **UI 层**：Activity + Fragment + XML Layout
- **数据层**：Room 数据库 + Retrofit 网络请求
- **服务层**：MusicService 后台播放服务
- **图片加载**：Glide
- **异步处理**：Kotlin Coroutines

## 开源协议

本项目采用 [GPL-3.0](LICENSE) 开源协议。

## 贡献

欢迎提交 Issue 和 Pull Request！

## 更新日志

详见 [CHANGELOG.md](CHANGELOG.md)
