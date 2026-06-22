package com.melodyflow.app.ui

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.melodyflow.app.R
import com.melodyflow.app.adapter.ChartAdapter
import com.melodyflow.app.model.Chart
import com.melodyflow.app.viewmodel.HomeViewModel
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private lateinit var rvCharts: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var chartAdapter: ChartAdapter

    private var tvEmpty: TextView? = null

    private val viewModel: HomeViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        android.util.Log.d("HomeFragment", "onCreateView called")
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        android.util.Log.d("HomeFragment", "onViewCreated called")

        rvCharts = view.findViewById(R.id.rvCharts)
        progressBar = view.findViewById(R.id.progressBar)

        // Try to find optional views
        try { tvEmpty = view.findViewById(R.id.tvEmpty) } catch (e: Exception) { }

        // Use GridLayoutManager in landscape, LinearLayoutManager in portrait
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        rvCharts.layoutManager = if (isLandscape) {
            GridLayoutManager(requireContext(), 3)
        } else {
            GridLayoutManager(requireContext(), 2)
        }

        chartAdapter = ChartAdapter { chart ->
            android.util.Log.d("HomeFragment", "Chart clicked: ${chart.name}")
            openChartSongs(chart)
        }
        rvCharts.adapter = chartAdapter

        observeViewModel()
        viewModel.loadCharts()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    if (state.isLoading) {
                        showLoading()
                    } else {
                        if (state.charts.isNotEmpty()) {
                            chartAdapter.submitList(state.charts)
                        }
                        hideLoading()
                    }
                    if (state.error != null && state.charts.isEmpty()) {
                        tvEmpty?.text = "加载失败: ${state.error}"
                        tvEmpty?.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun showLoading() {
        progressBar.visibility = View.VISIBLE
        tvEmpty?.visibility = View.GONE
    }

    private fun hideLoading() {
        progressBar.visibility = View.GONE
        if (chartAdapter.currentList.isEmpty()) {
            tvEmpty?.text = "暂无榜单数据"
            tvEmpty?.visibility = View.VISIBLE
        }
    }

    private fun openChartSongs(chart: Chart) {
        android.util.Log.d("HomeFragment", "Opening SongListActivity for chart: ${chart.name}")
        val intent = Intent(requireContext(), SongListActivity::class.java).apply {
            putExtra("chart_id", chart.id)
            putExtra("chart_name", chart.name)
            putExtra("chart_server", chart.server)
        }
        startActivity(intent)
    }
}
