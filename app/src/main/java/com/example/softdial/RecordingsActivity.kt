package com.example.softdial

import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File

class RecordingsActivity : AppCompatActivity() {

    private var player: MediaPlayer? = null
    private var files: List<File> = emptyList()
    private lateinit var list: ListView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Recordings"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        list = ListView(this)
        setContentView(list)

        list.setOnItemClickListener { _, _, pos, _ ->
            val f = files[pos]
            AlertDialog.Builder(this)
                .setTitle(f.name)
                .setItems(arrayOf("Play / Stop", "Share", "Delete")) { _, which ->
                    when (which) {
                        0 -> togglePlay(f)
                        1 -> share(f)
                        2 -> { stopPlayer(); f.delete(); reload() }
                    }
                }
                .show()
        }
        reload()
    }

    private fun reload() {
        files = SipManager.recordingsDir(this)
            .listFiles { f -> f.isFile && f.length() > 0 }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()
        list.adapter = ArrayAdapter(
            this, android.R.layout.simple_list_item_1,
            files.map { "${it.name}  (${it.length() / 1024} KB)" }
        )
        if (files.isEmpty()) Toast.makeText(this, "No recordings yet", Toast.LENGTH_SHORT).show()
    }

    private fun togglePlay(f: File) {
        if (player?.isPlaying == true) { stopPlayer(); return }
        stopPlayer()
        try {
            player = MediaPlayer().apply {
                setDataSource(f.absolutePath)
                setOnCompletionListener { stopPlayer() }
                prepare()
                start()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot play: ${e.message}", Toast.LENGTH_LONG).show()
            stopPlayer()
        }
    }

    private fun share(f: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "audio/x-wav"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, "Share recording"))
    }

    private fun stopPlayer() {
        player?.release()
        player = null
    }

    override fun onDestroy() { stopPlayer(); super.onDestroy() }
    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
