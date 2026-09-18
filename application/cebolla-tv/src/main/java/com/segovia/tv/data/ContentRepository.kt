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

        const val SYNOPSIS_WORKER_URL =
            "https://restless-hall-4d87.guadianesgalaxi.workers.dev"

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

            val c =
                URL(SYNOPSIS_WORKER_URL)
                    .openConnection() as HttpURLConnection

            c.requestMethod = "GET"
            c.connectTimeout = 15000
            c.readTimeout = 30000

            c.setRequestProperty(
                "Accept",
                "application/json"
            )

            if (c.responseCode in 200..299) {

                JSONObject(
                    c.inputStream
                        .bufferedReader()
                        .use {
                            it.readText()
                        }
                )

            } else {

                JSONObject()
            }

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
                o.optJSONArray("peliculas")
                    ?: JSONArray()
            )

        val nk =
            parseMovies(
                o.optJSONArray("kids")
                    ?: JSONArray()
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
        a: JSONArray
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

            val sinopsis =
                o.optString(
                    "sinopsis",
                    o.optString("s")
                ).ifBlank {
                    SYNOPSIS_DEFAULT
                }

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

            val titulo =
                o.optString(
                    "titulo",
                    o.optString("t")
                )

            val temporadas =
                parseTemporadas(
                    o.optJSONArray("temporadas")
                        ?: o.optJSONArray("temps")
                        ?: JSONArray(),
                    titulo,
                    synopsisMap
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

            val sinopsis =
                o.optString(
                    "sinopsis",
                    o.optString("s")
                ).ifBlank {
                    SYNOPSIS_DEFAULT
                }

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
        synopsisMap: JSONObject?
    ): MutableList<Temporada> {

        val list =
            mutableListOf<Temporada>()

        for (i in 0 until a.length()) {

            val o =
                a.optJSONObject(i)
                    ?: continue

            val seasonNum =
                o.optInt(
                    "numero",
                    o.optInt("n", i + 1)
                )

            val seasonTitle =
                o.optString(
                    "titulo",
                    o.optString(
                        "t",
                        "Temporada ${i + 1}"
                    )
                )

            // IMPORTANTE:
            // Para buscar sinopsis mantenemos el nombre interno
            // con espacios, igual que el Worker.

            val cleanSerieName =
                serieTitulo
                    .substringBefore("(")
                    .replace(".", " ")
                    .replace("_", " ")
                    .trim()
                    .lowercase()

            val seasonKey =
                "$cleanSerieName-s${
                    String.format(
                        "%02d",
                        seasonNum
                    )
                }"

            val seasonKeyAlt =
                "$cleanSerieName-s$seasonNum"

            var seasonDataObject:
                    JSONObject? = null

            var directPosterUrl:
                    String? = null

            if (synopsisMap != null) {

                val matchedObj =
                    synopsisMap.optJSONObject(
                        seasonKey
                    )
                        ?: synopsisMap.optJSONObject(
                            seasonKeyAlt
                        )

                if (matchedObj != null) {

                    seasonDataObject =
                        matchedObj.optJSONObject(
                            "sinopsis"
                        )

                    directPosterUrl =
                        matchedObj.optString(
                            "posterUrl",
                            ""
                        ).takeIf {
                            it.isNotBlank()
                        }
                }
            }

            val caps =
                mutableListOf<Capitulo>()

            val ca =
                o.optJSONArray("capitulos")
                    ?: o.optJSONArray("caps")
                    ?: JSONArray()

            for (j in 0 until ca.length()) {

                val c =
                    ca.optJSONObject(j)
                        ?: continue

                val epNumStr =
                    (j + 1).toString()

                val tituloCap =
                    c.optString(
                        "titulo",
                        c.optString("t")
                    )

                var resolvedSinopsis =
                    SYNOPSIS_DEFAULT

                if (seasonDataObject != null) {

                    resolvedSinopsis =
                        seasonDataObject.optString(
                            epNumStr,
                            seasonDataObject.optString(
                                tituloCap,
                                SYNOPSIS_DEFAULT
                            )
                        )
                }

                if (
                    resolvedSinopsis.isBlank() ||
                    resolvedSinopsis == SYNOPSIS_DEFAULT
                ) {

                    resolvedSinopsis =
                        c.optString(
                            "sinopsis",
                            c.optString("s")
                        ).ifBlank {
                            SYNOPSIS_DEFAULT
                        }
                }

                caps.add(
                    Capitulo(
                        tituloCap,
                        c.optString(
                            "streamUrl",
                            c.optString("u")
                        ),
                        resolvedSinopsis,
                        c.optString(
                            "duracion",
                            c.optString(
                                "d",
                                "51 min"
                            )
                        )
                    )
                )
            }

            val poster =
                directPosterUrl
                    ?: o.optString(
                        "posterUrl",
                        o.optString("p")
                    ).ifBlank {
                        POSTER_DEFAULT
                    }

            list.add(
                Temporada(
                    seasonNum,
                    seasonTitle,
                    caps,
                    poster
                )
            )
        }

        return list
    }

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
    }
}