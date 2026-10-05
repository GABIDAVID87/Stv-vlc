package com.segovia.tv.series

/**
 * Extrae la temporada desde S## y el capítulo desde E##.
 *
 * Ejemplos:
 * S01E01 -> temporada 1, capítulo 1
 * S02E36 -> temporada 2, capítulo 36
 * S03E01 -> temporada 3, capítulo 1
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
     * S01E01 -> "S01E01"
     * S02E36 -> "S02E36"
     * S03E01 -> "S03E01"
     */
    fun synopsisKey(nombreVideo: String): String? {
        val datos = parse(nombreVideo) ?: return null

        return "S${datos.temporada.toString().padStart(2, '0')}" +
                "E${datos.episodio.toString().padStart(2, '0')}"
    }

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
 * Busca la sinopsis usando la clave S##E##.
 *
 * S02E36 -> synopsis["S02E36"]
 */
fun buscarSinopsisSegovia(
    nombreVideo: String,
    synopsis: Map<String, SegoviaSynopsisItem>
): SegoviaSynopsisItem? {

    val key = SegoviaEpisodeParser.synopsisKey(nombreVideo)
        ?: return null

    return synopsis[key]
}
