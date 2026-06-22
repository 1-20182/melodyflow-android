package com.melodyflow.app.widget

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.SearchView
import com.melodyflow.app.R

class MusicPickerDialog(
    private val context: Context,
    private val onSongSelected: (songId: String, songName: String, artist: String) -> Unit
) {
    fun show() {
        val builder = AlertDialog.Builder(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_song_picker, null)
        builder.setView(view)
            .setTitle("选择音乐")
            .setPositiveButton("确认", null)
            .setNegativeButton("取消", null)
            .show()
    }
}