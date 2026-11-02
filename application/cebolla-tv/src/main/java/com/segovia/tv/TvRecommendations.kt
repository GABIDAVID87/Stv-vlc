package com.segovia.tv

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
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

            items.asSequence()
                .filter { it.streamUrl.isNotBlank() }
                .take(12)
                .forEachIndexed { index, item ->
                    val providerId = "segovia-program:${item.tipo}:${item.streamUrl.hashCode()}"
                    val launch = launchIntent(context, item)
                    val values = ContentValues().apply {
                        put("channel_id", channelId)
                        put("title", item.titulo)
                        put("description", item.subtitulo.ifBlank { item.sinopsis })
                        put("intent_uri", launch.toUri(Intent.URI_INTENT_SCHEME))
                        put("internal_provider_id", providerId)
                        put("type", if (item.tipo.equals("serie", true)) "TYPE_TV_EPISODE" else "TYPE_MOVIE")
                        put("weight", 100 - index)
                        val art = item.bannerUrl.ifBlank { item.posterUrl }
                        if (art.isNotBlank()) put("poster_art_uri", art)
                        if (item.posterUrl.isNotBlank()) put("thumbnail_uri", item.posterUrl)
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
            TvContract.PreviewChannels.CONTENT_URI,
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
            put("package_name", context.packageName)
            put("display_name", "Segovia TV")
            put("description", "Películas y series de Segovia TV")
            put("internal_provider_id", CHANNEL_PROVIDER_ID)
            put("type", "TYPE_PREVIEW")
            put("app_link_intent_uri", appIntent.toUri(Intent.URI_INTENT_SCHEME))
        }
        val uri = context.contentResolver.insert(TvContract.PreviewChannels.CONTENT_URI, values)
        return uri?.let { ContentUris.parseId(it) }
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
