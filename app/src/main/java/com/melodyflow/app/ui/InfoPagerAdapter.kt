package com.melodyflow.app.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.melodyflow.app.R

class InfoPagerAdapter(
    private val context: Context,
    private val pages: List<String>
) : RecyclerView.Adapter<InfoPagerAdapter.InfoViewHolder>() {

    inner class InfoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvContent: TextView = itemView.findViewById(R.id.tvContent)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InfoViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_info_page, parent, false)
        return InfoViewHolder(view)
    }

    override fun onBindViewHolder(holder: InfoViewHolder, position: Int) {
        val page = pages[position]
        holder.tvContent.text = when (page) {
            "user_agreement" -> getUserAgreement()
            "privacy_policy" -> getPrivacyPolicy()
            "changelog" -> getChangelog()
            else -> ""
        }
    }

    override fun getItemCount(): Int = pages.size

    private fun getUserAgreement(): String {
        return """用户协议

欢迎使用 MelodyFlow！

在使用本应用之前，请您仔细阅读以下用户协议。如您不同意以下条款，请勿使用本应用。

一、服务条款

1. MelodyFlow 是一款音乐播放器应用，为用户提供在线音乐播放服务。
2. 本应用仅提供音乐播放功能，不存储或传播音乐文件。
3. 本应用使用公开的音乐 API 获取音乐信息。

二、用户使用规范

1. 用户在使用本应用时，应遵守相关法律法规。
2. 用户不得利用本应用从事任何违法活动。
3. 用户应尊重版权，不得将本应用用于商业用途。

三、免责声明

1. 本应用不对音乐来源于网络，本应用不保证所有音乐的准确性和可用性。
2. 如因音乐版权问题产生纠纷，本应用不承担责任。
3. 用户使用本应用产生的一切后果由用户自行承担。

四、隐私保护

1. 本应用重视用户隐私，我们不会收集、存储用户的个人信息。
2. 详细请查看隐私政策。

五、协议修改

1. 我们保留随时修改本协议的权利。
2. 修改后的协议一经公布即生效。

六、其他

1. 如您有任何问题，请联系我们。

协议发起人：飞翔的死猪
最后更新日期：2026年6月"""
    }

    private fun getPrivacyPolicy(): String {
        return """隐私政策

我们非常重视您的隐私保护。

一、信息收集

1. 本应用不主动收集任何个人信息。
2. 我们不会收集您的姓名、联系方式等个人信息。
3. 我们不会收集您的位置信息。

二、信息使用

1. 本应用仅在本地存储您的收藏和播放历史。
2. 这些数据不会上传到任何服务器。
3. 清除应用缓存仅用于提高播放体验。

三、权限使用

1. 网络权限：用于获取音乐信息和播放音乐。
2. 存储权限：用于缓存音乐文件。
3. 我们不会滥用任何权限用于其他用途。

四、数据安全

1. 您的数据仅存储在您的设备上。
2. 您可以随时清除应用数据。
3. 我们无法访问您的个人数据。

五、其他

1. 本政策可能会更新，请关注最新版本。
2. 如您有任何疑问，请联系我们。

协议发起人：飞翔的死猪
最后更新日期：2026年6月"""
    }

    private fun getChangelog(): String {
        return """更新日志

v2.1.0 (2026-06-21)

🎨 界面与体验优化
- 🎛️ 均衡器界面视觉重设计，卡片式分区布局，交互更流畅
- ✨ 均衡器频段切换平滑动画效果
- 🔄 新增检查更新功能（设置页）

🔧 问题修复
- 🖼️ 修复背景设置不起作用的问题
- 📱 修复版本号显示不正确的问题

v2.0.0 (2026-06-14)

✨ 重磅新功能
- 🤖 新增AI音乐推荐功能，智能分析你的喜好
- 🎨 AI支持多场景推荐（工作、运动、睡眠等）
- 📝 AI对话式推荐，自然语言描述需求
- 🎵 支持国内外主流AI API（OpenAI、AstrBot等）
- 🚀 启动页面可自定义（主页或AI推荐）

🎨 界面与体验优化
- 新增多种强调色，界面更加丰富多彩
- AI推荐页面采用全新设计
- 优化背景自定义功能稳定性

🔧 问题修复
- 修复歌曲缓存逻辑，只缓存喜欢的歌曲
- 修复AI推荐页面闪退问题
- 优化AI推荐歌曲加载速度（并行搜索）

v1.3.0 (2026-06-07)

✨ 重磅新功能
- 🎛️ 新增均衡器功能，自定义音效体验
- ⏱️ 新增定时关闭功能，安心入眠
- 📱 优化播放器更多菜单布局

🎨 界面与体验优化
- 优化歌单导入性能，加载更流畅
- 缓存改为静默模式，不弹窗干扰

🔧 问题修复
- 修复更新日志弹窗不显示问题

v1.2.0 (2026-06-06)

✨ 重磅新功能
- 🎵 新增多音乐源支持，随心切换
- 🔄 智能源切换：播放失败自动回退到另一音乐源
- ⚙️ 设置页新增音乐源切换选项
- 📜 新增用户协议和隐私政策
- 📝 新增更新日志功能
- 💾 新增已缓存歌曲专属歌单，离线也能听
- 📦 新增数据备份与恢复功能
- 🔄 缓存歌曲信息自动补全（封面、歌词、歌曲信息）
- 🏷️ 缓存补全标记，避免重复下载

🎨 界面与体验优化
- 修复横屏模式下导航栏点击无响应问题
- 优化迷你播放器显示逻辑
- 正在播放的歌词高亮绿色显示
- 优化多源搜索和播放体验
- 更流畅的页面切换动画

🔧 问题修复
- 修复收藏歌曲播放初始化失败问题
- 修复歌曲莫名跳过问题
- 修复缓存歌曲信息丢失问题
- 修复横屏模式布局适配问题
- 修复播放状态实时更新问题

v1.0.0 (2026-06-06)

🎉 首次发布
- 基础音乐播放功能
- 音乐源支持
- 收藏和历史记录
- 音乐缓存功能
- 歌词滚动显示
- 自定义背景主题
- 迷你播放器
- 横屏模式支持"""
    }
}
