package com.segovia.tv.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Lee sinopsis TXT/JSON directamente desde Google Drive API v3.
 *
 * Seguridad: accessTokenProvider debe devolver un access token OAuth DE USUARIO
 * obtenido mediante el flujo oficial de Google en Android. Nunca pasar aquí una
 * clave privada de cuenta de servicio ni guardarla en el APK.
 *
 * Esta clase no modifica ContentRepository ni la reproducción VLC. Es la primera
 * pieza aislada para probar la lectura directa; debe conectarse después al flujo
 * OAuth real y al punto de carga de sinopsis.
 */
class GoogleDriveSynopsisRepository(
    private val accessTokenProvider: () -> String,
    private val synopsisFolderId: String = DEFAULT_SYNOPSIS_FOLDER_ID,
    private val postersFolderId: String = DEFAULT_POSTERS_FOLDER_ID
) {
    companion object {
        const val DEFAULT_SYNOPSIS_FOLDER_ID = "11DWfVFAVZZEcVKXJnIpjkX88ajDcJ47R"
        const val DEFAULT_POSTERS_FOLDER_ID = "16xWepJI0aA0AhyumL4-11rHXf2CPqZNW"
        private const val FOLDER_MIME = "application/vnd.google-apps.folder"
        private const val DRIVE_FILES = "https://www.googleapis.com/drive/v3/files"
    }

    data class DriveFile(
        val id: String,
        val name: String,
        val mimeType: String,
        val parents: List<String>
    )

    /** Ejecutar en un hilo de fondo. Devuelve un catálogo compatible en forma general con el Worker. */
    fun buildCatalog(): JSONObject {
        val token = accessTokenProvider().trim()
        require(token.isNotEmpty()) { "No hay token OAuth de Google. Iniciá sesión y autorizá Drive." }

        val files = listRecursively(token, listOf(synopsisFolderId, postersFolderId))
        val children = files.flatMap { file -> file.parents.map { it to file } }
            .groupBy({ it.first }, { it.second })

        val result = JSONObject()
        val synopsisFiles = children[synopsisFolderId].orEmpty()

        for (file in synopsisFiles) {
            val lower = file.name.lowercaseCompat()
            val key = clean(file.name)
            when {
                lower.endsWith(".json") -> {
                    val parsed = readJson(token, file.id) ?: continue
                    result.put(key, JSONObject().put("sinopsis", parsed))
                }
                lower.endsWith(".txt") -> {
                    val text = downloadText(token, file.id).trim()
                    if (text.isNotEmpty()) {
                        // Representa el TXT como sinopsis textual; ContentRepository
                        // puede normalizarlo al formato de sus modelos en la integración.
                        result.put(key, JSONObject().put("sinopsis", JSONObject().put("sinopsis", text)))
                    }
                }
            }
        }

        // Carpetas especiales: #Nombre/S01/temporada.json
        for (folder in synopsisFiles.filter { it.mimeType == FOLDER_MIME && it.name.startsWith("#") }) {
            val displayName = folder.name.removePrefix("#").trim()
            val seasonObjects = JSONArray()
            val seasonFolders = children[folder.id].orEmpty().filter { it.mimeType == FOLDER_MIME }

            val parsedSeasons = seasonFolders.mapNotNull { seasonFolder ->
                val match = Regex("S(\\d+)", RegexOption.IGNORE_CASE).find(seasonFolder.name)
                    ?: return@mapNotNull null
                val number = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                val seasonFiles = children[seasonFolder.id].orEmpty()
                val jsonFile = seasonFiles.firstOrNull { it.name.equals("temporada.json", ignoreCase = true) }
                    ?: return@mapNotNull null
                val data = readJson(token, jsonFile.id) ?: return@mapNotNull null
                JSONObject().put("numero", number).put("episodios", data)
            }.sortedBy { it.optInt("numero") }

            parsedSeasons.forEach { seasonObjects.put(it) }
            if (seasonObjects.length() > 0) {
                result.put(
                    clean(displayName),
                    JSONObject()
                        .put("nombre", displayName)
                        .put("modo", "especial")
                        .put("temporadas", seasonObjects)
                )
            }
        }
        return result
    }

    private fun listRecursively(token: String, rootIds: List<String>): List<DriveFile> {
        val output = mutableListOf<DriveFile>()
        val visited = mutableSetOf<String>()
        var pending = rootIds.distinct()

        while (pending.isNotEmpty()) {
            val query = pending.joinToString(" or ") { "'${it.replace("'", "\\'")}' in parents" }
            val found = mutableListOf<DriveFile>()
            var pageToken: String? = null
            do {
                val params = linkedMapOf(
                    "q" to "($query) and trashed = false",
                    "pageSize" to "1000",
                    "fields" to "nextPageToken,files(id,name,mimeType,parents)",
                    "supportsAllDrives" to "true",
                    "includeItemsFromAllDrives" to "true"
                )
                if (!pageToken.isNullOrBlank()) params["pageToken"] = pageToken
                val url = DRIVE_FILES + "?" + params.entries.joinToString("&") {
                    enc(it.key) + "=" + enc(it.value)
                }
                val json = getJson(url, token)
                val array = json.optJSONArray("files") ?: JSONArray()
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val parentsJson = item.optJSONArray("parents") ?: JSONArray()
                    val parents = (0 until parentsJson.length()).mapNotNull { parentsJson.optString(it).takeIf(String::isNotBlank) }
                    found += DriveFile(
                        id = item.optString("id"),
                        name = item.optString("name"),
                        mimeType = item.optString("mimeType"),
                        parents = parents
                    )
                }
                pageToken = json.optString("nextPageToken").takeIf { it.isNotBlank() && it != "null" }
            } while (pageToken != null)

            output += found
            pending = found.filter { it.mimeType == FOLDER_MIME && visited.add(it.id) }.map { it.id }
        }
        return output
    }

    private fun readJson(token: String, id: String): JSONObject? = try {
        JSONObject(downloadText(token, id).removePrefix("\uFEFF"))
    } catch (_: Exception) {
        try { JSONObject().put("items", JSONArray(downloadText(token, id).removePrefix("\uFEFF"))) }
        catch (_: Exception) { null }
    }

    private fun downloadText(token: String, id: String): String {
        val url = "$DRIVE_FILES/${enc(id)}?alt=media"
        return getText(url, token)
    }

    private fun getJson(url: String, token: String): JSONObject = JSONObject(getText(url, token))

    private fun getText(url: String, token: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 30000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json, text/plain, */*")
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { it.readText() }
            if (status !in 200..299) throw IllegalStateException("Google Drive HTTP $status: ${body.take(500)}")
            return body
        } finally {
            connection.disconnect()
        }
    }

    private fun clean(value: String): String = value
        .replace(Regex("\\.[^.]+$"), "")
        .normalizeAccents()
        .trim()
        .lowercaseCompat()

    private fun String.normalizeAccents(): String {
        val normalized = java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFD)
        return normalized.replace(Regex("[\\u0300-\\u036f]"), "")
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
    private fun String.lowercaseCompat(): String = toLowerCase(java.util.Locale.ROOT)
}
