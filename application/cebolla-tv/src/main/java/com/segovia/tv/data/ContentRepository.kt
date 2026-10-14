package com.segovia.tv.data

import android.content.Context
import com.segovia.tv.model.Capitulo
import com.segovia.tv.model.KidsItem
import com.segovia.tv.model.PeliculaDrive
import com.segovia.tv.model.SerieDrive
import com.segovia.tv.model.Temporada
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class ContentRepository(
    private val context: Context,
    private val onUpdated: () -> Unit
) {

    companion object {
        const val CATALOG_URL =
            "https://segovia-tv-proxy.guadianesgalaxi.workers.dev/catalog"

        const val POSTER_DEFAULT =
            "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=500&q=80"

        const val BANNER_DEFAULT =
            "https://images.unsplash.com/photo-1574267432553-4b4628081c31?w=1000&q=80"

        const val SYNOPSIS_DEFAULT =
            "Sinopsis no disponible."

        private const val PREFS = "segoviatv_cache"
    }

    val peliculas = mutableListOf<PeliculaDrive>()
    val kids = mutableListOf<PeliculaDrive>()
    val kidsItems = mutableListOf<KidsItem>()
    val series = mutableListOf<SerieDrive>()

    fun loadCache(): Boolean {

        val p = context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )

        val cp = p.getString("cache_pelis", null)
        val ck = p.getString("cache_kids", null)
        val cs = p.getString("cache_series", null)
        val cks = p.getString("cache_kids_series", null)

        if (
            cp.isNullOrEmpty() &&
            ck.isNullOrEmpty() &&
            cs.isNullOrEmpty() &&
            cks.isNullOrEmpty()
        ) {
            return false
        }

        return try {

            peliculas.clear()
            kids.clear()
            kidsItems.clear()
            series.clear()

            if (!cp.isNullOrEmpty()) {
                peliculas.addAll(
                    parseMovies(JSONArray(cp))
                )
            }

            if (!ck.isNullOrEmpty()) {

                val list = parseMovies(
                    JSONArray(ck)
                )

                kids.addAll(list)

                kidsItems.addAll(
                    list.map {
                        KidsItem.Movie(it)
                    }
                )
            }

            if (!cks.isNullOrEmpty()) {

                val list = parseSeries(
                    JSONArray(cks),
                    null
                )

                kidsItems.addAll(
                    list.map {
                        KidsItem.Series(it)
                    }
                )
            }

            if (!cs.isNullOrEmpty()) {

                series.addAll(
                    parseSeries(
                        JSONArray(cs),
                        null
                    )
                )
            }

            peliculas.isNotEmpty() ||
                    kids.isNotEmpty() ||
                    series.isNotEmpty() ||
                    kidsItems.isNotEmpty()

        } catch (e: Exception) {

            e.printStackTrace()
            false
        }
    }

    fun refreshAsync() {

        thread {

            try {

                val catalogJson =
                    downloadCatalog()

                val synopsisMap =
                    downloadDirectSynopses()

                parseCatalog(
                    catalogJson,
                    synopsisMap
                )

                saveCache()

                onUpdated()

            } catch (e: Exception) {

                e.printStackTrace()
            }
        }
    }

    private fun downloadCatalog(): JSONObject {

        val c =
            URL(CATALOG_URL)
                .openConnection() as HttpURLConnection

        c.requestMethod = "GET"
        c.connectTimeout = 15000
        c.readTimeout = 30000

        c.setRequestProperty(
            "Accept",
            "application/json"
        )

        c.setRequestProperty(
            "Cache-Control",
            "no-cache"
        )

        if (c.responseCode !in 200..299) {

            throw IllegalStateException(
                "Worker Catálogo HTTP ${c.responseCode}"
            )
        }

        return JSONObject(
            c.inputStream
                .bufferedReader()
                .use {
                    it.readText()
                }
        )
    }

    private fun downloadDirectSynopses(): JSONObject {
        return try {
            GoogleDriveSynopsisRepository().downloadSynopsisMap()
        } catch (e: Exception) {
            e.printStackTrace()
            JSONObject()
        }
    }

    private fun parseCatalog(
        o: JSONObject,
        synopsisMap: JSONObject
    ) {

        val np =
            parseMovies(
                o.optJSONArray("peliculas") ?: JSONArray(),
                synopsisMap
            )

        val nk =
            parseMovies(
                o.optJSONArray("kids") ?: JSONArray(),
                synopsisMap
            )

        val nks =
            parseSeries(
                o.optJSONArray("kidsSeries")
                    ?: JSONArray(),
                synopsisMap
            )

        val ns =
            parseSeries(
                o.optJSONArray("series")
                    ?: JSONArray(),
                synopsisMap
            )

        peliculas.clear()
        peliculas.addAll(np)

        kids.clear()
        kids.addAll(nk)

        kidsItems.clear()

        kidsItems.addAll(
            nk.map {
                KidsItem.Movie(it)
            }
        )

        kidsItems.addAll(
            nks.map {
                KidsItem.Series(it)
            }
        )

        series.clear()
        series.addAll(ns)
    }

    private fun parseMovies(
        a: JSONArray,
        synopsisMap: JSONObject? = null
    ): List<PeliculaDrive> {

        val list =
            mutableListOf<PeliculaDrive>()

        for (i in 0 until a.length()) {

            val o =
                a.optJSONObject(i)
                    ?: continue

            val titulo =
                o.optString(
                    "titulo",
                    o.optString("t")
                )

            val poster =
                o.optString(
                    "posterUrl",
                    o.optString("p")
                ).ifBlank {
                    POSTER_DEFAULT
                }

            val banner =
                o.optString(
                    "bannerUrl",
                    o.optString("b")
                ).ifBlank {
                    BANNER_DEFAULT
                }

            val catalogSynopsis = o.optString("sinopsis", o.optString("s")).trim()
            val directSynopsis = findDirectSynopsis(synopsisMap, titulo)
            val sinopsis = directSynopsis
                ?: catalogSynopsis.takeIf { it.isNotBlank() && it != SYNOPSIS_DEFAULT }
                ?: SYNOPSIS_DEFAULT

            val stream =
                o.optString(
                    "streamUrl",
                    o.optString("u")
                )

            val progreso =
                o.optInt(
                    "progreso",
                    o.optInt("pr", 0)
                )

            list.add(
                PeliculaDrive(
                    titulo,
                    poster,
                    banner,
                    sinopsis,
                    stream,
                    progreso
                )
            )
        }

        return list
    }

    private fun parseSeries(
        a: JSONArray,
        synopsisMap: JSONObject?
    ): List<SerieDrive> {

        val list =
            mutableListOf<SerieDrive>()

        for (i in 0 until a.length()) {

            val o =
                a.optJSONObject(i)
                    ?: continue

            val tituloOriginal =
                o.optString(
                    "titulo",
                    o.optString("t")
                )

            // El # identifica internamente una serie especial.
            // Nunca debe mostrarse en la interfaz.
            val titulo =
                tituloOriginal
                    .trimStart()
                    .removePrefix("#")
                    .trim()

            // Resolvemos primero el póster GENERAL de la serie.
            // La temporada puede usarlo como fallback, pero no se fija
            // permanentemente a él.
            val poster =
                o.optString(
                    "posterUrl",
                    o.optString("p")
                ).ifBlank {
                    POSTER_DEFAULT
                }

            val temporadas =
                parseTemporadas(
                    o.optJSONArray("temporadas")
                        ?: o.optJSONArray("temps")
                        ?: JSONArray(),
                    titulo,
                    poster,
                    synopsisMap
                )

            val banner =
                o.optString(
                    "bannerUrl",
                    o.optString("b")
                ).ifBlank {
                    BANNER_DEFAULT
                }

            val catalogSynopsis = o.optString("sinopsis", o.optString("s")).trim()
            val directSynopsis = findDirectSynopsis(synopsisMap, titulo)
            val sinopsis = directSynopsis
                ?: catalogSynopsis.takeIf { it.isNotBlank() && it != SYNOPSIS_DEFAULT }
                ?: SYNOPSIS_DEFAULT

            val anio =
                o.optString(
                    "anio",
                    o.optString("y", "2011")
                )

            val genero =
                o.optString(
                    "genero",
                    o.optString(
                        "g",
                        "Drama, Histórico"
                    )
                )

            val valoracion =
                o.optString(
                    "valoracion",
                    o.optString("r", "8.1")
                )

            list.add(
                SerieDrive(
                    titulo,
                    poster,
                    banner,
                    sinopsis,
                    temporadas,
                    anio,
                    genero,
                    valoracion
                )
            )
        }

        return list
    }

    private fun parseTemporadas(
        a: JSONArray,
        serieTitulo: String,
        seriePoster: String,
        synopsisMap: JSONObject?
    ): MutableList<Temporada> {

        val list = mutableListOf<Temporada>()
        val cleanSerieName = normalizeSynopsisName(serieTitulo).replace(" ", "-")

        // La serie especial se identifica por el # al comienzo del nombre del
        // JSON. El título visible del catálogo puede no incluirlo, por eso
        // buscamos tanto la clave normalizada como la variante con #.
        val specialSerieObject = if (synopsisMap == null) null else {
            synopsisMap.optJSONObject(cleanSerieName)
                ?: synopsisMap.optJSONObject("#$cleanSerieName")
                ?: run {
                    var found: JSONObject? = null
                    val keys = synopsisMap.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val normalizedKey = normalizeSynopsisName(key).replace(" ", "-")
                        if (normalizedKey == cleanSerieName && key.trimStart().startsWith("#")) {
                            found = synopsisMap.optJSONObject(key)
                            if (found != null) break
                        }
                    }
                    found
                }
        }
        val isSpecialSeries =
            specialSerieObject?.optString("modo", "") == "especial" ||
            specialSerieObject?.optJSONArray("temporadas") != null ||
            (synopsisMap?.keys()?.asSequence()?.any { key ->
                key.trimStart().startsWith("#") &&
                    normalizeSynopsisName(key.substringBeforeLast("-s")).replace(" ", "-") == cleanSerieName
            } == true)

        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            val seasonNum = o.optInt("numero", o.optInt("n", i + 1))
            val seasonTitle = o.optString("titulo", o.optString("t", "Temporada ${i + 1}"))
            val seasonKey = "$cleanSerieName-s${String.format("%02d", seasonNum)}"
            val seasonKeyAlt = "$cleanSerieName-s$seasonNum"
            var seasonDataObject: JSONObject? = null
            var directPosterUrl: String? = null

            if (synopsisMap != null) {
                if (isSpecialSeries) {
                    // Los JSON especiales están separados por temporada: #Nombre-S01.json.
                    val specialSeasonKeys = listOf(
                        "#$cleanSerieName-s${String.format("%02d", seasonNum)}",
                        "#$cleanSerieName-s$seasonNum",
                        "$cleanSerieName-s${String.format("%02d", seasonNum)}",
                        "$cleanSerieName-s$seasonNum"
                    )
                    for (key in specialSeasonKeys) {
                        val obj = synopsisMap.optJSONObject(key)
                        if (obj != null) { seasonDataObject = obj; break }
                    }
                    if (seasonDataObject == null) {
                        val keys = synopsisMap.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            val normalized = normalizeSynopsisName(key).replace(" ", "-")
                            if (key.trimStart().startsWith("#") &&
                                (normalized == "#$cleanSerieName-s${String.format("%02d", seasonNum)}" || normalized == "#$cleanSerieName-s$seasonNum")) {
                                seasonDataObject = synopsisMap.optJSONObject(key)
                                if (seasonDataObject != null) break
                            }
                        }
                    }
                    // Si un JSON antiguo trae el contenedor temporadas, usarlo como fallback.
                    val specialSerie = specialSerieObject
                    if (seasonDataObject == null && specialSerie != null) {
                        val especiales = specialSerie.optJSONArray("temporadas")
                        if (especiales != null) for (k in 0 until especiales.length()) {
                            val temp = especiales.optJSONObject(k) ?: continue
                            if (temp.optInt("numero", -1) == seasonNum) {
                                seasonDataObject = temp.optJSONObject("episodios")
                                directPosterUrl = temp.optString("posterUrl", "").trim().takeIf { it.isNotBlank() }
                                break
                            }
                        }
                    }
                    directPosterUrl = findDirectPoster(synopsisMap, "$cleanSerieName-s${String.format("%02d", seasonNum)}")
                        ?: findDirectPoster(synopsisMap, "#$cleanSerieName-s${String.format("%02d", seasonNum)}")
                        ?: directPosterUrl
                } else {
                    var matchedObj = synopsisMap.optJSONObject(seasonKey)
                        ?: synopsisMap.optJSONObject(seasonKeyAlt)

                    // Si el nombre del catálogo no coincide exactamente con la
                    // clave del Worker, comparamos los nombres normalizados.
                    if (matchedObj == null) {
                        val wantedPrefix = cleanSerieName
                        val wantedSuffix = "-s${String.format("%02d", seasonNum)}"
                        val wantedSuffixAlt = "-s$seasonNum"
                        val keys = synopsisMap.keys()

                        while (keys.hasNext()) {
                            val key = keys.next()
                            val keyLower = key.lowercase()

                            if (!keyLower.endsWith(wantedSuffix) &&
                                !keyLower.endsWith(wantedSuffixAlt)
                            ) {
                                continue
                            }

                            val prefix = key.substringBeforeLast("-s")
                            if (normalizeSynopsisName(prefix).replace(" ", "-") == wantedPrefix) {
                                matchedObj = synopsisMap.optJSONObject(key)
                                if (matchedObj != null) break
                            }
                        }
                    }

                    if (matchedObj != null) {
                        // En las series normales el JSON de episodios está dentro
                        // de "sinopsis". El fallback a matchedObj permite soportar
                        // también respuestas antiguas/directas del Worker.
                        seasonDataObject = matchedObj.optJSONObject("sinopsis") ?: matchedObj
                        directPosterUrl = matchedObj.optString("posterUrl", "").takeIf { it.isNotBlank() }
                    }
                }
            }

            val caps = mutableListOf<Capitulo>()
            val ca = o.optJSONArray("capitulos") ?: o.optJSONArray("caps") ?: JSONArray()
            val episodeRegex = Regex("S(\\d+)E(\\d+)", RegexOption.IGNORE_CASE)
            val specialEpisodeKeys = mutableListOf<String>()

            if (isSpecialSeries && seasonDataObject != null) {
                val keys = seasonDataObject.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    // Dragon Ball Z usa claves numéricas en temporada.json:
                    // S02 = 36..107, S03 = 108..116.
                    // También aceptamos SxxEyy por compatibilidad.
                    if (key.toIntOrNull() != null || episodeRegex.matches(key)) {
                        specialEpisodeKeys.add(key)
                    }
                }
                specialEpisodeKeys.sortWith(compareBy {
                    it.toIntOrNull()
                        ?: episodeRegex.find(it)?.groupValues?.getOrNull(2)?.toIntOrNull()
                        ?: Int.MAX_VALUE
                })
            }

            for (j in 0 until ca.length()) {
                val c = ca.optJSONObject(j) ?: continue
                val tituloCatalogo = c.optString("titulo", c.optString("t"))
                val streamUrl = c.optString("streamUrl", c.optString("u"))
                var episodioReal = episodeRegex.find(streamUrl)?.groupValues?.getOrNull(2)?.toIntOrNull()
                    ?: episodeRegex.find(tituloCatalogo)?.groupValues?.getOrNull(2)?.toIntOrNull()

                // En series especiales la clave del JSON manda. Los videos pueden
                // llamarse E01 dentro de cada temporada, aunque el episodio real sea 36.
                if (isSpecialSeries && j < specialEpisodeKeys.size) {
                    val key = specialEpisodeKeys[j]
                    episodioReal = key.toIntOrNull()
                        ?: episodeRegex.find(key)?.groupValues?.getOrNull(2)?.toIntOrNull()
                }
                if (episodioReal == null) episodioReal = j + 1

                val seasonEpisodeKey = "S${String.format("%02d", seasonNum)}E${String.format("%02d", episodioReal)}"
                var tituloCap = tituloCatalogo
                var resolvedSinopsis = SYNOPSIS_DEFAULT

                if (isSpecialSeries && seasonDataObject != null) {
                    val numericKey = episodioReal.toString()
                    val entry =
                        seasonDataObject.optJSONObject(seasonEpisodeKey)
                            ?: seasonDataObject.optJSONObject(numericKey)

                    if (entry != null) {
                        val tituloJson = entry.optString("titulo", "").trim()
                        if (tituloJson.isNotBlank()) tituloCap = tituloJson
                        resolvedSinopsis = entry.optString("sinopsis", "").trim()
                    } else {
                        val value = seasonDataObject.opt(seasonEpisodeKey)
                            ?: seasonDataObject.opt(numericKey)
                        if (value is String) resolvedSinopsis = value.trim()
                    }

                    // SeriesScreen extrae E36 de este identificador y lo muestra
                    // como Capítulo 36. El identificador no se muestra como texto.
                    tituloCap = "$seasonEpisodeKey - $tituloCap".trim()
                } else if (seasonDataObject != null) {
                    val entry = seasonDataObject.opt(seasonEpisodeKey)
                        ?: seasonDataObject.opt(epNumStr(episodioReal))

                    if (entry is JSONObject) {
                        val tituloJson = entry.optString("titulo", "").trim()
                        if (tituloJson.isNotBlank()) tituloCap = tituloJson
                        resolvedSinopsis = entry.optString("sinopsis", "").trim()
                    } else if (entry is String) {
                        resolvedSinopsis = entry.trim()
                    }
                }

                if (resolvedSinopsis.isBlank() || resolvedSinopsis == SYNOPSIS_DEFAULT) {
                    resolvedSinopsis = c.optString("sinopsis", c.optString("s")).ifBlank { SYNOPSIS_DEFAULT }
                }

                caps.add(Capitulo(tituloCap, streamUrl, resolvedSinopsis, c.optString("duracion", c.optString("d", "51 min"))))
            }

            val catalogSeasonPoster = o.optString("posterUrl", o.optString("p")).trim()
            val poster = directPosterUrl?.trim()?.takeIf { it.isNotBlank() }
                ?: catalogSeasonPoster.takeIf { it.isNotBlank() }
                ?: seriePoster

            list.add(Temporada(seasonNum, seasonTitle, caps, poster))
        }
        return list
    }

    private fun findDirectPoster(map: JSONObject?, seasonKey: String): String? {
        if (map == null) return null
        val wanted = "poster:" + seasonKey.lowercase().replace(" ", "-")
        val keys = map.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key.equals(wanted, ignoreCase = true)) {
                return map.optString(key, "").trim().takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    private fun findDirectSynopsis(map: JSONObject?, title: String): String? {
        if (map == null || title.isBlank()) return null
        val normalized = normalizeSynopsisName(title).replace(" ", "-")
        val candidates = linkedSetOf(
            normalized,
            normalized.replace("-", " "),
            normalized.replace("-", "_")
        )
        val keys = map.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val normalizedKey = normalizeSynopsisName(key).replace(" ", "-")
            if (normalizedKey in candidates) {
                val value = map.opt(key)
                val text = when (value) {
                    is String -> value.trim()
                    is JSONObject -> value.optString("sinopsis",
                        value.optString("descripcion", value.optString("description", value.optString("texto")))).trim()
                    else -> ""
                }
                if (text.isNotBlank() && text != SYNOPSIS_DEFAULT) return text
            }
        }
        return null
    }

    private fun normalizeSynopsisName(value: String): String {
        return value
            .substringBefore("(")
            .removePrefix("#")
            .trim()
            .lowercase()
            .replace(".", " ")
            .replace("_", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun epNumStr(n: Int): String = n.toString()

    private fun saveCache() {

        val p =
            context.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )

        val e =
            p.edit()

        val cp =
            JSONArray()

        peliculas.forEach {

            cp.put(
                JSONObject().apply {

                    put("t", it.titulo)
                    put("p", it.posterUrl)
                    put("b", it.bannerUrl)
                    put("s", it.sinopsis)
                    put("u", it.streamUrl)
                    put("pr", it.progreso)
                }
            )
        }

        val ck =
            JSONArray()

        kids.forEach {

            ck.put(
                JSONObject().apply {

                    put("t", it.titulo)
                    put("p", it.posterUrl)
                    put("b", it.bannerUrl)
                    put("s", it.sinopsis)
                    put("u", it.streamUrl)
                    put("pr", it.progreso)
                }
            )
        }

        val cs =
            JSONArray()

        series.forEach {

            cs.put(
                seriesJson(it)
            )
        }

        val cks =
            JSONArray()

        kidsItems
            .filterIsInstance<KidsItem.Series>()
            .forEach {

                cks.put(
                    seriesJson(it.data)
                )
            }

        e.putString(
            "cache_pelis",
            cp.toString()
        )
            .putString(
                "cache_kids",
                ck.toString()
            )
            .putString(
                "cache_series",
                cs.toString()
            )
            .putString(
                "cache_kids_series",
                cks.toString()
            )
            .apply()
    }

    private fun seriesJson(
        s: SerieDrive
    ): JSONObject {

        val ts =
            JSONArray()

        s.temporadas.forEach { t ->

            val cs =
                JSONArray()

            t.capitulos.forEach { c ->

                cs.put(
                    JSONObject().apply {

                        put("t", c.titulo)
                        put("u", c.streamUrl)
                        put("s", c.sinopsis)
                        put("d", c.duracion)
                    }
                )
            }

            ts.put(
                JSONObject().apply {

                    put("n", t.numero)
                    put("t", t.titulo)
                    put("p", t.posterUrl)
                    put("caps", cs)
                }
            )
        }

        return JSONObject().apply {

            put("t", s.titulo)
            put("p", s.posterUrl)
            put("b", s.bannerUrl)
            put("s", s.sinopsis)
            put("temps", ts)
            put("y", s.anio)
            put("g", s.genero)
            put("r", s.valoracion)
        }
    }

    fun displayName(n: String): String {

        return n
            .substringBefore("(")
            .replace("_", " ")
            .trim()
            .removePrefix("#")
            .trim()
    }
}
