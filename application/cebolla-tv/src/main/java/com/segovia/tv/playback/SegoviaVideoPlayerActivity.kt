package com.segovia.tv.playback

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast

class SegoviaVideoPlayerActivity : Activity() {

    companion object {
        private const val TAG = "SegoviaVLC"
        private const val REQUEST_VLC_START = 7402

        /*
         * StartActivity real de VLC.
         *
         * Desde allí VLC hace:
         *
         * intent.setClass(
         *     this@StartActivity,
         *     VideoPlayerActivity::class.java
         * )
         */
        private const val VLC_START_ACTIVITY =
            "org.videolan.vlc.StartActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        abrirStartActivityVlc()
    }

    private fun abrirStartActivityVlc() {

        try {

            /*
             * Intent que recibió Segovia TV desde
             * ExternalPlayerLauncher.
             */
            val originalIntent = intent

            /*
             * La URL debe venir en data.
             */
            val videoUri: Uri =
                originalIntent.data
                    ?: throw IllegalArgumentException(
                        "No se recibió ninguna URL de reproducción"
                    )

            /*
             * Copiamos TODO el Intent original.
             *
             * Esto conserva:
             *
             * - URL
             * - MIME type
             * - EXTRA_TITLE
             * - series_title
             * - season
             * - episode
             * - poster
             * - banner
             * - lista de capítulos
             * - extras de Segovia TV
             * - etc.
             */
            val vlcIntent = Intent(originalIntent)

            /*
             * VLC StartActivity espera ACTION_VIEW para entrar
             * en startPlaybackFromApp().
             */
            vlcIntent.action = Intent.ACTION_VIEW

            /*
             * Aseguramos que la URL y el MIME sean correctos.
             */
            vlcIntent.setDataAndType(
                videoUri,
                originalIntent.type ?: "video/*"
            )

            /*
             * Este es el componente integrado de VLC.
             *
             * IMPORTANTE:
             *
             * No estamos llamando a una aplicación VLC externa.
             *
             * El package org.videolan.vlc corresponde al APK
             * integrado que estamos construyendo.
             */
            vlcIntent.component = ComponentName(
                "org.videolan.vlc",
                VLC_START_ACTIVITY
            )

            /*
             * Igual que el flujo externo de VLC.
             */
            vlcIntent.putExtra(
                "from_external",
                true
            )

            /*
             * Nos aseguramos de conservar el título.
             */
            originalIntent.getStringExtra(
                Intent.EXTRA_TITLE
            )?.let { title ->

                vlcIntent.putExtra(
                    Intent.EXTRA_TITLE,
                    title
                )
            }

            /*
             * Lanzamos StartActivity de VLC.
             *
             * StartActivity será quien haga internamente:
             *
             * startActivityForResult(
             *     intent.setClass(
             *         this@StartActivity,
             *         VideoPlayerActivity::class.java
             *     ),
             *     ...
             * )
             */
            startActivityForResult(
                vlcIntent,
                REQUEST_VLC_START
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "ERROR ABRIENDO STARTACTIVITY DE VLC INTEGRADO",
                e
            )

            Toast.makeText(
                this,
                "ERROR VLC: " +
                    "${e.javaClass.simpleName}\n" +
                    "${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {

        super.onActivityResult(
            requestCode,
            resultCode,
            data
        )

        if (requestCode == REQUEST_VLC_START) {

            /*
             * Cuando VLC termine, devolvemos el resultado
             * a Segovia TV.
             */
            setResult(
                resultCode,
                data
            )

            finish()
        }
    }
}
