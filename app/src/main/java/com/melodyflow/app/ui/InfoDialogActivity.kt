package com.melodyflow.app.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.melodyflow.app.R

class InfoDialogActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SHOW_ONLY_CHANGELOG = "show_only_changelog"
        const val EXTRA_FIRST_TIME = "first_time"
        const val EXTRA_SHOW_PROMOTION = "show_promotion"
    }

    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2
    private lateinit var btnOk: Button
    private lateinit var tvTitle: TextView
    private var isFirstTime = false
    private var showPromotion = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_info_dialog)

        tabLayout = findViewById(R.id.tabLayout)
        viewPager = findViewById(R.id.viewPager)
        btnOk = findViewById(R.id.btnOk)
        tvTitle = findViewById(R.id.tvTitle)

        val showOnlyChangelog = intent.getBooleanExtra(EXTRA_SHOW_ONLY_CHANGELOG, false)
        isFirstTime = intent.getBooleanExtra(EXTRA_FIRST_TIME, false)
        showPromotion = intent.getBooleanExtra(EXTRA_SHOW_PROMOTION, false)

        if (showOnlyChangelog) {
            tvTitle.text = "更新日志"
            tabLayout.visibility = View.GONE
            viewPager.adapter = InfoPagerAdapter(this, listOf("changelog"))
        } else {
            tvTitle.text = if (isFirstTime) "欢迎使用 MelodyFlow" else "用户协议"
            viewPager.adapter = InfoPagerAdapter(this, listOf("user_agreement", "privacy_policy", "changelog"))
            TabLayoutMediator(tabLayout, viewPager) { tab, position ->
                when (position) {
                    0 -> tab.text = "用户协议"
                    1 -> tab.text = "隐私政策"
                    2 -> tab.text = "更新日志"
                }
            }.attach()
        }

        btnOk.setOnClickListener {
            if (isFirstTime || showPromotion) {
                showPromotionDialog()
            } else {
                finish()
            }
        }

        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun showPromotionDialog() {
        AlertDialog.Builder(this)
            .setTitle("感谢使用 MelodyFlow！")
            .setMessage("如果您觉得这个应用好用，欢迎分享给身边的朋友，让更多人享受免费的音乐体验！\n\n您的支持是我持续更新的动力！")
            .setPositiveButton("好的") { _, _ -> finish() }
            .setNegativeButton("稍后再说") { _, _ -> finish() }
            .show()
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}
