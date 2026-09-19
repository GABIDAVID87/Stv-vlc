package com.segovia.tv.playback

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast

class SegoviaVideoPlayerActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        abrirVlcIntegrado()
    }

    private fun abrirVlcIntegrado() {

        try {

            val url = intent.data
            val title = intent.getStringExtra(Intent.EXTRA_TITLE)

            if (url == null) {
                throw IllegalArgumentException(
                    "No se recibió ninguna URL de reproducción"
                )
            }

            val vlcIntent = Intent().apply {

                component = ComponentName(
                    "org.videolan.vlc",
                    "org.videolan.vlc.gui.video.VideoPlayerActivity"
                )

                action = Intent.ACTION_VIEW

                setDataAndType(
                    Uri.parse(url.toString()),
                    "video/*"
                )

                if (!title.isNullOrBlank()) {
                    putExtra(
                        Intent.EXTRA_TITLE,
                        title
                    )
                }

                putExtra(
                    "from_external",
                    true
                )

                // Conservamos todos los extras enviados por Segovia TV.
                intent.extras?.let { extras ->
                    putExtras(extras)
                }
            }

            startActivity(vlcIntent)

            finish()

        } catch (e: Exception) {

            android.util.Log.e(
                "SegoviaVLC",
                "ERROR ABRIENDO VLC INTEGRADO",
                e
            )

            Toast.makeText(
                this,
                "ERROR VLC: ${e.javaClass.simpleName}\n${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
