package com.segovia.tv

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.tv.TvContract
import android.net.Uri
import android.os.Build
import android.util.Log
import com.segovia.tv.model.SeguirViendoItem

/**
 * Publica una fila/canal de recomendaciones de Segovia TV en los lanzadores
 * Android TV compatibles. La presentación final depende del fabricante y
 * de que el usuario/lanzador permita mostrar el canal.
 */
internal object TvRecommendations {
    private const val TAG = "SegoviaTvRecommendations"
    private const val CHANNEL_PROVIDER_ID = "segovia_tv_recommendations"
    private const val ACTION_CONTINUE = "com.segovia.tv.action.WATCH_NEXT_CONTINUE"
    private const val EXTRA_STREAM = "segovia_google_tv_stream"
    private const val EXTRA_TITLE = "segovia_google_tv_title"
    private const val EXTRA_TYPE = "segovia_google_tv_type"
    private const val EXTRA_SEASON = "segovia_google_tv_season"
    private const val EXTRA_EPISODE = "segovia_google_tv_episode"

    fun publish(context: Context, items: List<SeguirViendoItem>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || items.isEmpty()) return
        try {
            val resolver = context.contentResolver
            val channelId = findChannelId(context) ?: createChannel(context) ?: return
            // También repara el logotipo si el canal ya existía de una instalación anterior.
            writeChannelLogo(context, channelId)

            items.asSequence()
                .filter { it.streamUrl.isNotBlank() }
                .take(12)
                .forEachIndexed { index, item ->
                    val providerId = "segovia-program:${item.tipo}:${item.streamUrl.hashCode()}"
                    val launch = launchIntent(context, item)
                    val values = ContentValues().apply {
                        put(TvContract.PreviewPrograms.COLUMN_CHANNEL_ID, channelId)
                        put(TvContract.PreviewPrograms.COLUMN_TITLE, item.titulo)
                        put(TvContract.PreviewPrograms.COLUMN_DESCRIPTION, item.subtitulo.ifBlank { item.sinopsis })
                        put(TvContract.PreviewPrograms.COLUMN_INTENT_URI, launch.toUri(Intent.URI_INTENT_SCHEME))
                        put(TvContract.PreviewPrograms.COLUMN_INTERNAL_PROVIDER_ID, providerId)
                        put(
                            TvContract.PreviewPrograms.COLUMN_TYPE,
                            if (item.tipo.equals("serie", true)) TvContract.PreviewPrograms.TYPE_TV_EPISODE
                            else TvContract.PreviewPrograms.TYPE_MOVIE
                        )
                        put(TvContract.PreviewPrograms.COLUMN_WEIGHT, 100 - index)
                        put(TvContract.PreviewPrograms.COLUMN_BROWSABLE, 1)
                        val art = item.bannerUrl.ifBlank { item.posterUrl }
                        if (art.isNotBlank()) put(TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI, art)
                        if (item.posterUrl.isNotBlank()) put(TvContract.PreviewPrograms.COLUMN_THUMBNAIL_URI, item.posterUrl)
                    }
                    val existingUri = findProgramUri(context, channelId, providerId)
                    if (existingUri != null) resolver.update(existingUri, values, null, null)
                    else resolver.insert(TvContract.PreviewPrograms.CONTENT_URI, values)
                }

            try {
                TvContract.requestChannelBrowsable(context, channelId)
            } catch (e: Exception) {
                Log.i(TAG, "El lanzador no permitió solicitar que el canal sea visible: ${e.message}")
            }
        } catch (e: Exception) {
            // No debe impedir que Segovia TV abra ni que VLC reproduzca.
            Log.w(TAG, "No se pudieron publicar recomendaciones: ${e.message}", e)
        }
    }

    private fun findChannelId(context: Context): Long? {
        context.contentResolver.query(
            TvContract.Channels.CONTENT_URI,
            arrayOf("_id"),
            "internal_provider_id=? AND package_name=?",
            arrayOf(CHANNEL_PROVIDER_ID, context.packageName),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getLong(0)
        }
        return null
    }

    private fun createChannel(context: Context): Long? {
        val appIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val values = ContentValues().apply {
            put(TvContract.Channels.COLUMN_PACKAGE_NAME, context.packageName)
            put(TvContract.Channels.COLUMN_DISPLAY_NAME, "Segovia TV")
            put(TvContract.Channels.COLUMN_DESCRIPTION, "Películas y series de Segovia TV")
            put(TvContract.Channels.COLUMN_INTERNAL_PROVIDER_ID, CHANNEL_PROVIDER_ID)
            put(TvContract.Channels.COLUMN_TYPE, TvContract.Channels.TYPE_PREVIEW)
            put(TvContract.Channels.COLUMN_APP_LINK_INTENT_URI, appIntent.toUri(Intent.URI_INTENT_SCHEME))
        }
        val uri = context.contentResolver.insert(TvContract.Channels.CONTENT_URI, values)
        val channelId = uri?.let { ContentUris.parseId(it) } ?: return null
        writeChannelLogo(context, channelId)
        return channelId
    }

    private fun writeChannelLogo(context: Context, channelId: Long) {
        try {
            val drawable = context.packageManager.getApplicationIcon(context.packageName)
            val bitmap = Bitmap.createBitmap(256, 144, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, 256, 144)
            drawable.draw(canvas)
            val logoUri = TvContract.buildChannelLogoUri(channelId)
            context.contentResolver.openOutputStream(logoUri, "w")?.use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
            bitmap.recycle()
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo guardar el logotipo del canal: ${e.message}")
        }
    }

    private fun findProgramUri(context: Context, channelId: Long, providerId: String): Uri? {
        // El proveedor de TV conserva la fila entre sincronizaciones para evitar duplicados.
        context.contentResolver.query(
            TvContract.PreviewPrograms.CONTENT_URI,
            arrayOf("_id"),
            "channel_id=? AND internal_provider_id=?",
            arrayOf(channelId.toString(), providerId),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) return ContentUris.withAppendedId(TvContract.PreviewPrograms.CONTENT_URI, cursor.getLong(0))
        }
        return null
    }

    private fun launchIntent(context: Context, item: SeguirViendoItem): Intent =
        Intent(context, MainActivity::class.java).apply {
            action = ACTION_CONTINUE
            putExtra(EXTRA_STREAM, item.streamUrl)
            putExtra(EXTRA_TITLE, item.titulo)
            putExtra(EXTRA_TYPE, item.tipo)
            putExtra(EXTRA_SEASON, item.temporada)
            putExtra(EXTRA_EPISODE, item.capitulo)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
}
