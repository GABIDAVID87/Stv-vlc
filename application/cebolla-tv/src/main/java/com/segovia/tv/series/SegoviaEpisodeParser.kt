package com.segovia.tv.series

/**
 * Extrae la temporada desde S## y el capítulo desde E##.
 *
 * Ejemplos:
 * S01E01 -> temporada 1, capítulo 1
 * S02E36 -> temporada 2, capítulo 36
 * S03E01 -> temporada 3, capítulo 1
 *
 * El número usado para buscar la sinopsis es SIEMPRE E##.
 * No se suma ningún offset por temporada.
 */
data class SegoviaEpisodeInfo(
    val temporada: Int,
    val episodio: Int
)

object SegoviaEpisodeParser {

    private val episodeRegex =
        Regex("""S(\d+)E(\d+)""", RegexOption.IGNORE_CASE)

    /**
     * Busca S##E## en cualquier parte del nombre del archivo.
     */
    fun parse(nombreVideo: String): SegoviaEpisodeInfo? {
        val match = episodeRegex.find(nombreVideo) ?: return null

        val temporada = match.groupValues[1].toIntOrNull() ?: return null
        val episodio = match.groupValues[2].toIntOrNull() ?: return null

        if (temporada <= 0 || episodio <= 0) return null

        return SegoviaEpisodeInfo(
            temporada = temporada,
            episodio = episodio
        )
    }

    /**
     * Devuelve la clave exacta para buscar la sinopsis.
     *
     * S02E36 -> "36"
     */
    fun synopsisKey(nombreVideo: String): String? =
        parse(nombreVideo)?.episodio?.toString()

    fun chapterNumber(nombreVideo: String): Int? =
        parse(nombreVideo)?.episodio

    fun seasonNumber(nombreVideo: String): Int? =
        parse(nombreVideo)?.temporada
}

/**
 * Modelo para las entradas del JSON de sinopsis.
 */
data class SegoviaSynopsisItem(
    val titulo: String = "",
    val sinopsis: String = "",
    val edad: String = "",
    val duracion: String = "",
    val fecha: String = ""
)

/**
 * Busca la sinopsis usando directamente el número de E##.
 *
 * S02E36 -> synopsis["36"]
 */
fun buscarSinopsisSegovia(
    nombreVideo: String,
    synopsis: Map<String, SegoviaSynopsisItem>
): SegoviaSynopsisItem? {

    val key = SegoviaEpisodeParser.synopsisKey(nombreVideo)
        ?: return null

    return synopsis[key]
}

/*
 * EJEMPLO:
 *
 * val nombre = "Dragon Ball Z - S02E36 - Mi capítulo.mp4"
 *
 * val datos = SegoviaEpisodeParser.parse(nombre)
 *
 * datos?.let {
 *     Log.d("SegoviaTV", "Temporada: ${it.temporada}")
 *     Log.d("SegoviaTV", "Capítulo: ${it.episodio}")
 * }
 *
 * val info = buscarSinopsisSegovia(nombre, synopsis)
 *
 * val titulo = info?.titulo.orEmpty()
 * val sinopsis = info?.sinopsis.orEmpty()
 *
 * Resultado:
 *
 * temporada = 2
 * episodio = 36
 * clave de sinopsis = "36"
 *
 * No convierte 36 en 71.
 */
