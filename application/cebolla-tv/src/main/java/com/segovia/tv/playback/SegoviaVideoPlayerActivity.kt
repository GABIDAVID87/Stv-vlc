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
         * Activity de entrada de VLC.
         *
         * Esta clase está integrada dentro del mismo APK de
         * Segovia TV mediante el módulo vlc-android.
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
             * Intent original recibido por Segovia TV.
             */
            val originalIntent = intent

            /*
             * La reproducción necesita una URI.
             */
            val videoUri: Uri =
                originalIntent.data
                    ?: throw IllegalArgumentException(
                        "No se recibió ninguna URL de reproducción"
                    )

            /*
             * Copiamos el Intent original para conservar todos
             * los extras enviados por Segovia TV.
             */
            val vlcIntent = Intent(originalIntent)

            /*
             * VLC StartActivity utiliza ACTION_VIEW para
             * iniciar la reproducción externa.
             */
            vlcIntent.action = Intent.ACTION_VIEW

            /*
             * Aseguramos que VLC reciba la URI y el MIME.
             */
            vlcIntent.setDataAndType(
                videoUri,
                originalIntent.type ?: "video/*"
            )

            /*
             * IMPORTANTE:
             *
             * StartActivity pertenece al módulo VLC integrado,
             * pero el APK instalado es el APK de Segovia TV.
             *
             * Por eso NO utilizamos:
             *
             * ComponentName("org.videolan.vlc", ...)
             *
             * ya que org.videolan.vlc es el namespace de VLC,
             * no necesariamente el applicationId del APK final.
             *
             * Con este constructor Android busca la Activity
             * dentro del mismo APK que está ejecutando esta Activity.
             */
            vlcIntent.component = ComponentName(
                this,
                VLC_START_ACTIVITY
            )

            /*
             * Indicamos a VLC que la reproducción procede
             * de una aplicación externa.
             */
            vlcIntent.putExtra(
                "from_external",
                true
            )

            /*
             * Conservamos explícitamente el título si existe.
             */
            originalIntent.getStringExtra(
                Intent.EXTRA_TITLE
            )?.let { title ->

                vlcIntent.putExtra(
                    Intent.EXTRA_TITLE,
                    title
                )
            }

            Log.d(
                TAG,
                "Abriendo VLC integrado"
            )

            Log.d(
                TAG,
                "URI: $videoUri"
            )

            Log.d(
                TAG,
                "MIME: ${vlcIntent.type}"
            )

            Log.d(
                TAG,
                "Component: ${vlcIntent.component}"
            )

            /*
             * Abrimos StartActivity de VLC.
             *
             * StartActivity será quien continúe el flujo
             * interno hacia VideoPlayerActivity.
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
                "ERROR VLC:\n" +
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
             * VLC terminó.
             *
             * Devolvemos el resultado a la Activity que
             * abrió SegoviaVideoPlayerActivity.
             */
            setResult(
                resultCode,
                data
            )

            finish()
        }
    }
}
