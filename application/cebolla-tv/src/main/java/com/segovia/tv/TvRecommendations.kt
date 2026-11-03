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

/** Publica el canal propio de recomendaciones de Segovia TV en Android TV. */
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Log.i(TAG, "Canales de recomendaciones requieren Android 8.0/API 26 o superior")
            return
        }
        Log.i(TAG, "Inicio publicación: package=${context.packageName}, recibidos=${items.size}")
        if (items.isEmpty()) {
            Log.w(TAG, "No hay elementos para publicar; el canal no se crea todavía")
            return
        }

        try {
            val resolver = context.contentResolver
            val channelId = findChannelId(context) ?: createChannel(context)
            if (channelId == null) {
                Log.e(TAG, "No se pudo crear ni localizar el canal de Segovia TV")
                return
            }
            Log.i(TAG, "Canal localizado/creado: id=$channelId")
            writeChannelLogo(context, channelId)

            var insertados = 0
            var actualizados = 0
            var omitidos = 0
            items.asSequence()
                .filter { it.streamUrl.isNotBlank() }
                .take(12)
                .forEachIndexed { index, item ->
                    val providerId = "segovia-program:${item.tipo}:${item.streamUrl.hashCode()}"
                    try {
                        val launch = launchIntent(context, item)
                        val values = ContentValues().apply {
                            put(TvContract.PreviewPrograms.COLUMN_CHANNEL_ID, channelId)
                            put(TvContract.PreviewPrograms.COLUMN_TITLE, item.titulo)
                            put(TvContract.PreviewPrograms.COLUMN_SHORT_DESCRIPTION, item.subtitulo.ifBlank { item.sinopsis })
                            put(TvContract.PreviewPrograms.COLUMN_INTENT_URI, launch.toUri(Intent.URI_INTENT_SCHEME))
                            put(TvContract.PreviewPrograms.COLUMN_INTERNAL_PROVIDER_ID, providerId)
                            put(TvContract.PreviewPrograms.COLUMN_TYPE,
                                if (item.tipo.equals("serie", true)) TvContract.PreviewPrograms.TYPE_TV_EPISODE
                                else TvContract.PreviewPrograms.TYPE_MOVIE)
                            put(TvContract.PreviewPrograms.COLUMN_WEIGHT, 100 - index)
                            val art = item.bannerUrl.ifBlank { item.posterUrl }
                            if (art.isNotBlank()) put(TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI, art)
                            if (item.posterUrl.isNotBlank()) put(TvContract.PreviewPrograms.COLUMN_THUMBNAIL_URI, item.posterUrl)
                        }

                        val existingUri = findProgramUri(context, channelId, providerId)
                        val result = if (existingUri != null) {
                            resolver.update(existingUri, values, null, null)
                            actualizados++
                            existingUri
                        } else {
                            resolver.insert(TvContract.PreviewPrograms.CONTENT_URI, values).also {
                                if (it != null) insertados++
                            }
                        }
                        if (result == null) Log.e(TAG, "El proveedor rechazó '${item.titulo}': insert/update devolvió null")
                        else Log.i(TAG, "Programa publicado: '${item.titulo}' -> $result")
                    } catch (e: Exception) {
                        omitidos++
                        Log.e(TAG, "Error publicando '${item.titulo}': ${e.javaClass.simpleName}: ${e.message}", e)
                    }
                }

            try {
                TvContract.requestChannelBrowsable(context, channelId)
                Log.i(TAG, "Solicitada visibilidad del canal id=$channelId")
            } catch (e: Exception) {
                Log.e(TAG, "Falló requestChannelBrowsable para id=$channelId: ${e.javaClass.simpleName}: ${e.message}", e)
            }
            Log.i(TAG, "Resumen canal: insertados=$insertados, actualizados=$actualizados, errores=$omitidos")
            logChannelState(context, channelId)
        } catch (e: SecurityException) {
            Log.e(TAG, "PERMISO DENEGADO al escribir en TvProvider. Revisar WRITE_EPG_DATA en el manifiesto combinado: ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error general publicando recomendaciones: ${e.javaClass.simpleName}: ${e.message}", e)
        }
    }

    private fun findChannelId(context: Context): Long? {
        context.contentResolver.query(
            TvContract.Channels.CONTENT_URI,
            arrayOf(TvContract.Channels._ID),
            "${TvContract.Channels.COLUMN_INTERNAL_PROVIDER_ID}=? AND ${TvContract.Channels.COLUMN_PACKAGE_NAME}=?",
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
        if (uri == null) {
            Log.e(TAG, "TvProvider devolvió null al crear el canal. Verificar permiso y compatibilidad del launcher")
            return null
        }
        val id = ContentUris.parseId(uri)
        Log.i(TAG, "Canal creado correctamente: $uri")
        writeChannelLogo(context, id)
        return id
    }

    private fun writeChannelLogo(context: Context, channelId: Long) {
        var bitmap: Bitmap? = null
        try {
            val drawable = context.packageManager.getApplicationIcon(context.packageName)
            val logoBitmap = Bitmap.createBitmap(512, 288, Bitmap.Config.ARGB_8888)
            bitmap = logoBitmap
            val canvas = Canvas(logoBitmap)
            drawable.setBounds(0, 0, 512, 288)
            drawable.draw(canvas)
            val logoUri = TvContract.buildChannelLogoUri(channelId)
            val output = context.contentResolver.openOutputStream(logoUri, "w")
            if (output == null) {
                Log.e(TAG, "No se pudo abrir el destino del logotipo: $logoUri")
            } else {
                output.use { logoBitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                Log.i(TAG, "Logotipo guardado: $logoUri")
            }
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo guardar el logotipo: ${e.javaClass.simpleName}: ${e.message}", e)
        } finally {
            bitmap?.recycle()
        }
    }

    private fun findProgramUri(context: Context, channelId: Long, providerId: String): Uri? {
        context.contentResolver.query(
            TvContract.PreviewPrograms.CONTENT_URI,
            arrayOf(TvContract.PreviewPrograms._ID),
            "${TvContract.PreviewPrograms.COLUMN_CHANNEL_ID}=? AND ${TvContract.PreviewPrograms.COLUMN_INTERNAL_PROVIDER_ID}=?",
            arrayOf(channelId.toString(), providerId),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return ContentUris.withAppendedId(TvContract.PreviewPrograms.CONTENT_URI, cursor.getLong(0))
            }
        }
        return null
    }

    private fun logChannelState(context: Context, channelId: Long) {
        try {
            context.contentResolver.query(
                ContentUris.withAppendedId(TvContract.Channels.CONTENT_URI, channelId),
                arrayOf(TvContract.Channels.COLUMN_DISPLAY_NAME, TvContract.Channels.COLUMN_BROWSABLE),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val name = cursor.getString(0)
                    val browsable = cursor.getInt(1)
                    Log.i(TAG, "Estado canal: nombre=$name, browsable=$browsable")
                } else Log.w(TAG, "El canal no aparece al volver a consultarlo")
            }
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo consultar estado del canal: ${e.message}", e)
        }
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
