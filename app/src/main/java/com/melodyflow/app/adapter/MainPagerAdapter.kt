package com.melodyflow.app.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.melodyflow.app.ui.AIRecommendationFragment
import com.melodyflow.app.ui.HomeFragment
import com.melodyflow.app.ui.SearchFragment
import com.melodyflow.app.ui.LibraryFragment

class MainPagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {

    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> HomeFragment()
            1 -> SearchFragment()
            2 -> LibraryFragment()
            3 -> AIRecommendationFragment()
            else -> HomeFragment()
        }
    }
}
