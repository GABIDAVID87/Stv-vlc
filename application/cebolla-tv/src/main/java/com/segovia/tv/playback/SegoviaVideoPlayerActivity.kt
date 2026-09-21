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
         * Activity de entrada de VLC integrada dentro
         * del mismo APK de Segovia TV.
         */
        private const val VLC_START_ACTIVITY =
            "org.videolan.vlc.StartActivity"

        /*
         * VLC VideoPlayerActivity utiliza actualmente
         * esta clave para recibir el título del vídeo.
         *
         * MUY IMPORTANTE:
         *
         * Intent.EXTRA_TITLE = "android.intent.extra.TITLE"
         *
         * NO es la clave que utiliza VideoPlayerActivity
         * para el título de reproducción externa.
         *
         * VLC espera:
         *
         *     "title"
         */
        private const val VLC_TITLE_EXTRA = "title"

        /*
         * Algunas versiones anteriores de VLC utilizaron
         * "item_title".
         *
         * Lo enviamos también para mantener compatibilidad
         * sin perjudicar la versión actual.
         */
        private const val VLC_ITEM_TITLE_EXTRA = "item_title"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        abrirStartActivityVlc()
    }

    private fun abrirStartActivityVlc() {

        try {

            /*
             * Intent original recibido desde Segovia TV.
             */
            val originalIntent = intent

            /*
             * URL del capítulo/película.
             */
            val videoUri: Uri =
                originalIntent.data
                    ?: throw IllegalArgumentException(
                        "No se recibió ninguna URL de reproducción"
                    )

            /*
             * Recuperamos el título enviado por
             * ExternalPlayerLauncher.
             *
             * ExternalPlayerLauncher ya utiliza:
             *
             * Intent.EXTRA_TITLE
             *
             * Por lo tanto lo leemos desde ahí.
             */
            val title =
                originalIntent.getStringExtra(
                    Intent.EXTRA_TITLE
                )?.trim()

            /*
             * Copiamos el Intent original para conservar
             * todos los extras de Segovia TV:
             *
             * - series
             * - temporada
             * - capítulo
             * - playlist
             * - posters
             * - progreso
             * - etc.
             */
            val vlcIntent = Intent(originalIntent)

            /*
             * VLC StartActivity espera ACTION_VIEW.
             */
            vlcIntent.action = Intent.ACTION_VIEW

            /*
             * URI y MIME.
             */
            vlcIntent.setDataAndType(
                videoUri,
                originalIntent.type ?: "video/*"
            )

            /*
             * StartActivity pertenece al módulo VLC integrado.
             *
             * NO usamos:
             *
             * ComponentName(
             *     "org.videolan.vlc",
             *     VLC_START_ACTIVITY
             * )
             *
             * porque el APK final es Segovia TV.
             */
            vlcIntent.component = ComponentName(
                this,
                VLC_START_ACTIVITY
            )

            /*
             * Indicamos que la reproducción viene
             * desde una aplicación externa.
             */
            vlcIntent.putExtra(
                "from_external",
                true
            )

            /*
             * =====================================================
             * CORRECCIÓN IMPORTANTE DEL TÍTULO
             * =====================================================
             *
             * Segovia TV recibe el título mediante:
             *
             *     Intent.EXTRA_TITLE
             *
             * pero VLC VideoPlayerActivity espera:
             *
             *     "title"
             *
             * Si no mandamos "title", cuando VLC encuentra
             * nuevamente la misma URL en MediaLibrary puede
             * terminar mostrando:
             *
             *     video?id=XXXXXXXX
             *
             * en lugar del nombre del capítulo.
             */

            if (!title.isNullOrBlank()) {

                /*
                 * Conservamos el extra Android original.
                 */
                vlcIntent.putExtra(
                    Intent.EXTRA_TITLE,
                    title
                )

                /*
                 * CLAVE QUE VLC UTILIZA REALMENTE.
                 */
                vlcIntent.putExtra(
                    VLC_TITLE_EXTRA,
                    title
                )

                /*
                 * Compatibilidad con versiones anteriores
                 * de VLC que utilizaban "item_title".
                 */
                vlcIntent.putExtra(
                    VLC_ITEM_TITLE_EXTRA,
                    title
                )

                Log.d(
                    TAG,
                    "Título enviado a VLC: $title"
                )

            } else {

                Log.w(
                    TAG,
                    "La reproducción no recibió título"
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
                "Título: $title"
            )

            Log.d(
                TAG,
                "VLC title extra: " +
                    vlcIntent.getStringExtra(
                        VLC_TITLE_EXTRA
                    )
            )

            Log.d(
                TAG,
                "Component: ${vlcIntent.component}"
            )

            /*
             * Abrimos StartActivity de VLC.
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
             * Devolvemos el resultado a Segovia TV.
             */
            setResult(
                resultCode,
                data
            )

            finish()
        }
    }
}
