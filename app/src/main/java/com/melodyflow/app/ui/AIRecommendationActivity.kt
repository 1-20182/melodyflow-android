package com.melodyflow.app.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.melodyflow.app.R

class AIRecommendationActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_recommendation)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, AIRecommendationFragment())
                .commit()
        }
    }
}
