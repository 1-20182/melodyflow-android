package com.melodyflow.app.widget

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.melodyflow.app.R
import com.melodyflow.app.db.MusicDatabase
import com.melodyflow.app.db.MusicDiaryDao
import com.melodyflow.app.repository.WidgetRepository
import kotlinx.coroutines.launch

class MusicDiaryActivity : AppCompatActivity() {

    private lateinit var diaryDao: MusicDiaryDao
    private lateinit var repository: WidgetRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_music_diary)

        val db = MusicDatabase.getInstance(this)
        diaryDao = db.musicDiaryDao()
        repository = WidgetRepository(this)

        // Set up FAB to add diary
        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fab_add_diary)
            .setOnClickListener {
                showDiaryEditor()
            }

        // Load diary list
        lifecycleScope.launch {
            loadDiaries()
        }
    }

    private suspend fun loadDiaries() {
        val diaries = diaryDao.getAllDiaries()
        // TODO: Update RecyclerView adapter
    }

    private fun showDiaryEditor() {
        // Simplified - in production use a DialogFragment
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("写音乐日记")
            .setView(layoutInflater.inflate(R.layout.dialog_diary_editor, null))
            .setPositiveButton("保存") { _, _ ->
                Toast.makeText(this, "日记已保存", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .create()
        dialog.show()
    }
}