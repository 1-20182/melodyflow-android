package com.melodyflow.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.melodyflow.app.R
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.melodyflow.app.viewmodel.LibraryViewModel
import kotlinx.coroutines.launch

class LibraryFragment : Fragment() {

    private var localSongsFragment: LocalSongsFragment? = null

    private val viewModel: LibraryViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_library, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup ViewPager2 with TabLayout
        val viewPager = view.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.viewPager)
        val tabLayout = view.findViewById<com.google.android.material.tabs.TabLayout>(R.id.tabLayout)

        val pagerAdapter = LibraryPagerAdapter(this)
        viewPager.adapter = pagerAdapter

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "在线收藏"
                1 -> "本地歌曲"
                else -> ""
            }
        }.attach()

        // Quick access card click handlers
        view.findViewById<MaterialCardView?>(R.id.cardCached)?.setOnClickListener {
            startActivity(Intent(requireContext(), CachedSongsActivity::class.java))
        }
        view.findViewById<MaterialCardView?>(R.id.cardHistory)?.setOnClickListener {
            startActivity(Intent(requireContext(), HistoryActivity::class.java))
        }
        view.findViewById<MaterialCardView?>(R.id.cardAIRecommendation)?.setOnClickListener {
            startActivity(Intent(requireContext(), MainActivity::class.java).apply {
                putExtra("nav_to", "ai")
            })
        }
        view.findViewById<MaterialCardView?>(R.id.cardImport)?.setOnClickListener {
            startActivity(Intent(requireContext(), PlaylistImportActivity::class.java))
        }
        view.findViewById<MaterialCardView?>(R.id.cardSettings)?.setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }
        view.findViewById<MaterialCardView?>(R.id.cardDonate)?.setOnClickListener {
            startActivity(Intent(requireContext(), DonateActivity::class.java))
        }

        // Scan FAB
        val fabScan = view.findViewById<FloatingActionButton>(R.id.fabScan)
        fabScan.setOnClickListener {
            // Get the LocalSongsFragment and trigger scan
            val localFragment = getChildLocalSongsFragment()
            localFragment?.startScan() ?: run {
                Toast.makeText(requireContext(), "请先切换到本地歌曲标签页", Toast.LENGTH_SHORT).show()
            }
        }

        observeViewModel()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    // Library data is observed by child fragments through shared ViewModel
                    // isScanning state can be used here if needed for UI feedback
                }
            }
        }
    }

    private fun getChildLocalSongsFragment(): LocalSongsFragment? {
        // Find the LocalSongsFragment from the ViewPager
        val viewPager = view?.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.viewPager)
            ?: return null
        val fragment = childFragmentManager.findFragmentByTag("f${1}") // ViewPager2 uses "f{position}" as tag
        return fragment as? LocalSongsFragment
    }

    inner class LibraryPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        override fun getItemCount(): Int = 2

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> FavoritesTabFragment()
                1 -> LocalSongsFragment()
                else -> FavoritesTabFragment()
            }
        }
    }
}
