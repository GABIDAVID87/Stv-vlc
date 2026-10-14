package com.segovia.tv.data

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.KeyFactory
import java.util.Locale

/** Acceso directo a Google Drive con cuenta de servicio. No usa Worker para las sinopsis. */
internal class GoogleDriveSynopsisRepository {
    companion object {
        // PEGÁ tus datos reales únicamente en estas dos constantes.
        // CLIENT_EMAIL: el campo "client_email" del JSON de la cuenta de servicio.
        private const val CLIENT_EMAIL = "segoviatv-bot@segoviatv.iam.gserviceaccount.com"
        // Pegá TODO el valor de private_key, incluidos BEGIN/END, como string Kotlin multilínea.
        private const val PRIVATE_KEY_PEM = """-----BEGIN PRIVATE KEY-----

MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQChCctBF3ytpJQA
Gb/nPwY081Qmc6Soe1XDvJ6O9R2DyzxFSfZvZhGTMnPeGSSBDM0QOMd8CJupVF0q
5GnhKo44ubaoZ/dfmAUUOvzi5aftcU0LI5fr89riqcTkEiOTTYnf+XcNLum1c5jM
gu/Djyb1/v3RPxy+J0PbdDuOYcYkRe5vGYJeleK2cFmTdhxWjWj9uaSsxGtRAsoR
ciEkob2htqZ7jTAc+LTrNc1V51igjCbiX/kAbsFhePkY3+L40zaxcmys7HavF8BO
X30yx2azPUo+A5pZ4515dX7zyIKUBY7xzEVeXoQBMH5nNxRYJL/7rB0Mdr6uHh0e
lBBZjeZ3AgMBAAECggEAHhAQgCnMzjPDp1MnRpm25GqqgnUjP4qqw5wyM3jh1ik6
Kqs4lKpGzKZmCAfEj0ze+VDIOMj4mj9UFz5F39GMdF2DEmQ6Q3/DLvgmHouF8mcs
3U/7PAKtkZPk3DjgqE6Y1xWgmnbwDT4zv7YP4X+XY+QC6S2xhXQaEF1eAuIQ1dNm
dHXptwAl6UZKuRoSxxK10NMhxVD/ONQQB9dAm5S1ve/g0MEKe+NXcV0TupP/DAvI
gsJLL3O7E6nVX0qz5GT1TQhzDkKEo2hRQyDslOi2zeBOScd7F2H9RNmjT1nr86Jo
FPM6w302COlobTfJrblnoeI4BdT8bBofV6YsR7Fp7QKBgQDMjkDEyJImnlGrIEgi
0qo4v7gwBAEtiXP3fp1h6WSIT31d9Qa+vrKfvRTSiF10Dv/ZtO2AVJVHnvt2Dxir
sKKY7j6X8VbDot3f2W52m5zSuKNZng1w6yPIsnVbj1av43SujKbN0aLZv6TOxA6k
OUNFoizVIPeegKRsgLItXUhoQwKBgQDJicsVbbeM/AZImjqVi66bkBqSz/rbkFpw
8TG/KWJHlcv1U8FJr8HVWI197DAv6qMrspKPZooBByfbYOftbVrWcm3M3wooDwqV
gbDorArvkuBo9aGLz8uRDF3zGVeLyT0h5ux0Mn85bc1RpxD73T00BDu1GkCGYZYz
W30jEb8PvQKBgQCbZxMjrPEDr9GBvYOwGPXDKTwIFnLCoqNcim+MnCCJQkKPKACY
1w2jN6XjEEBXVLHhNogcr6W9hLWrimqGvSm6H32kN3BgLpJ7Zpd6OlyY5jcLdXzm
pW1JIvLvvWTxOU9C8I4k3TfoT6UohGBPOUW3WC7RODuGylqgJ+G1fyHsOwKBgGsp
+fLLBG7TFARG/h/GxnGxjm/CZ4GYoH6tweYD+15VKL6qanzkGA15qMLsdDMGcP0r
VwMpYvho9aiRPsHhBAxDjF8DukLSvK4C6C7QTIBXeTxWYDAXrgbSrKI3N3X866S7
RcADmeLPn/1q+Hz0hzbBiX4jF2xdN6DXZRLgvNeBAoGADhjU4P9psHMBLk7rQXyI
Rlrodzxsnqy4jiPfoLkI5HCG9A3Dk5Ik1kx2R36KO/A1kITUugslhkPnIvKcktfK
By5mCamdkFe1fsYoXfJxg6d/KHMJvnSbMfe4pu3ivl4oDQkHEc2o6ol+SbvCjY2Z
LUtcPRjGmvOie5WuqJFgIbY=

-----END PRIVATE KEY-----"""

        private const val SYNOPSIS_FOLDER_ID = "11DWfVFAVZZEcVKXJnIpjkX88ajDcJ47R"
        private const val POSTERS_FOLDER_ID = "16xWepJI0aA0AhyumL4-11rHXf2CPqZNW"
        private const val TOKEN_URL = "https://oauth2.googleapis.com/token"
        private const val DRIVE_API = "https://www.googleapis.com/drive/v3"
    }

    @Volatile private var cachedToken: String? = null
    @Volatile private var tokenExpiryMs: Long = 0

    fun downloadSynopsisMap(): JSONObject {
        val result = JSONObject()
        val files = mutableListOf<JSONObject>()
        listRecursively(SYNOPSIS_FOLDER_ID, "", files)
        for (file in files) {
            val rawName = file.optString("name")
            val name = rawName.substringBeforeLast('.', rawName)
            val mime = file.optString("mimeType")
            val content = try { downloadFile(file.optString("id"), mime).removePrefix("\uFEFF").trim() } catch (_: Exception) { "" }
            if (content.isBlank()) continue
            val key = normalize(name)
            try {
                val parsed = JSONObject(content)
                // Cada archivo #Serie-S01.json se conserva como objeto independiente.
                // La clave conserva # para que ContentRepository detecte la serie especial.
                result.put(key, parsed)
            } catch (_: Exception) {
                result.put(key, content)
            }
        }
        // Indexar pósters de temporada por nombre normalizado. Los archivos pueden ser
        // #Dragon Ball Z-S02.jpg o Dragon Ball Z-S02.jpg; el ID se convierte en URL Drive.
        val posters = mutableListOf<JSONObject>()
        try { listRecursively(POSTERS_FOLDER_ID, "", posters) } catch (_: Exception) { }
        for (file in posters) {
            val name = file.optString("name")
            val base = name.substringBeforeLast('.', name)
            val id = file.optString("id")
            if (id.isBlank()) continue
            val key = normalize(base)
            result.put("poster:$key", "https://drive.google.com/uc?export=view&id=$id")
        }
        return result
    }

    private fun listRecursively(folderId: String, parentName: String, output: MutableList<JSONObject>) {
        var pageToken: String? = null
        do {
            val q = "'${folderId}' in parents and trashed = false"
            val url = StringBuilder("$DRIVE_API/files?q=${enc(q)}&fields=nextPageToken,files(id,name,mimeType,parents)&pageSize=1000")
            if (pageToken != null) url.append("&pageToken=${enc(pageToken!!)}")
            val json = JSONObject(request(url.toString(), "GET"))
            val files = json.optJSONArray("files") ?: JSONArray()
            for (i in 0 until files.length()) {
                val f = files.optJSONObject(i) ?: continue
                if (f.optString("mimeType") == "application/vnd.google-apps.folder") {
                    val child = f.optString("name")
                    listRecursively(f.optString("id"), if (parentName.isBlank()) child else "$parentName/$child", output)
                } else {
                    f.put("_parentName", parentName)
                    output.add(f)
                }
            }
            pageToken = json.optString("nextPageToken", "").takeIf { it.isNotBlank() }
        } while (pageToken != null)
    }

    private fun downloadFile(id: String, mime: String): String {
        // Google Docs export isn't expected for TXT/JSON files; normal Drive files use alt=media.
        val suffix = if (mime.startsWith("application/vnd.google-apps.")) "/export?mimeType=text/plain" else "?alt=media"
        return request("$DRIVE_API/files/${enc(id)}$suffix", "GET")
    }

    private fun request(url: String, method: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 20000
            conn.readTimeout = 30000
            conn.setRequestProperty("Authorization", "Bearer ${accessToken()}")
            conn.setRequestProperty("Accept", "application/json, text/plain, */*")
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
            if (code !in 200..299) throw IllegalStateException("Google Drive HTTP $code: ${body.take(300)}")
            return body
        } finally { conn.disconnect() }
    }

    @Synchronized private fun accessToken(): String {
        if (!cachedToken.isNullOrBlank() && System.currentTimeMillis() < tokenExpiryMs - 60000) return cachedToken!!
        require(CLIENT_EMAIL.contains("@")) { "Falta configurar CLIENT_EMAIL en GoogleDriveSynopsisRepository.kt" }
        require(PRIVATE_KEY_PEM.contains("MII") && !PRIVATE_KEY_PEM.contains("PEGAR_AQUI")) { "Falta pegar PRIVATE_KEY_PEM en GoogleDriveSynopsisRepository.kt" }
        val now = System.currentTimeMillis() / 1000
        val header = b64("{\"alg\":\"RS256\",\"typ\":\"JWT\"}")
        val claim = b64(JSONObject().put("iss", CLIENT_EMAIL).put("scope", "https://www.googleapis.com/auth/drive.readonly")
            .put("aud", TOKEN_URL).put("iat", now).put("exp", now + 3600).toString())
        val unsigned = "$header.$claim"
        val pem = PRIVATE_KEY_PEM
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            // Acepta tanto saltos de línea reales como los caracteres literales \\n.
            .replace("\\\\n", "")
            .replace("\\s".toRegex(), "")
        val keyBytes = Base64.decode(pem, Base64.DEFAULT)
        val privateKey = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyBytes))
        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(privateKey)
        signer.update(unsigned.toByteArray(Charsets.UTF_8))
        val jwt = "$unsigned.${Base64.encodeToString(signer.sign(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)}"
        val conn = URL(TOKEN_URL).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 20000
            conn.readTimeout = 20000
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.outputStream.use { it.write("grant_type=${enc("urn:ietf:params:oauth:grant-type:jwt-bearer")}&assertion=${enc(jwt)}".toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
            if (code !in 200..299) throw IllegalStateException("Google OAuth HTTP $code: ${body.take(300)}")
            val obj = JSONObject(body)
            cachedToken = obj.getString("access_token")
            tokenExpiryMs = System.currentTimeMillis() + obj.optLong("expires_in", 3600) * 1000
            return cachedToken!!
        } finally { conn.disconnect() }
    }

    private fun normalize(value: String): String = value.lowercase(Locale.ROOT).trim()
        .replace("_", " ").replace(Regex("\\s+"), " ").trim()
        .replace(" ", "-")

    private fun b64(value: String) = Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    private fun enc(value: String) = java.net.URLEncoder.encode(value, "UTF-8")
}
