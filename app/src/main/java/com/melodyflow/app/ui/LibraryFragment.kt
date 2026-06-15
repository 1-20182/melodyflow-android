package com.melodyflow.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import com.melodyflow.app.R

class LibraryFragment : Fragment() {

    private lateinit var cardFavorites: MaterialCardView
    private lateinit var cardCached: MaterialCardView
    private lateinit var cardHistory: MaterialCardView
    private lateinit var cardImport: MaterialCardView
    private lateinit var cardSettings: MaterialCardView
    private lateinit var cardDonate: MaterialCardView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_library, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Use safe calls to avoid crashes if views are not found
        view.findViewById<MaterialCardView?>(R.id.cardFavorites)?.setOnClickListener {
            startActivity(Intent(requireContext(), FavoritesActivity::class.java))
        }
        view.findViewById<MaterialCardView?>(R.id.cardCached)?.setOnClickListener {
            startActivity(Intent(requireContext(), CachedSongsActivity::class.java))
        }
        view.findViewById<MaterialCardView?>(R.id.cardHistory)?.setOnClickListener {
            startActivity(Intent(requireContext(), HistoryActivity::class.java))
        }
        view.findViewById<MaterialCardView?>(R.id.cardAIRecommendation)?.setOnClickListener {
            startActivity(Intent(requireContext(), AIRecommendationActivity::class.java))
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
    }
}
