package com.segovia.tv.model

data class PeliculaDrive(
    val titulo:String,
    val posterUrl:String,
    val bannerUrl:String,
    val sinopsis:String,
    val streamUrl:String,
    val progreso:Int=0
)

data class Capitulo(
    val titulo:String,
    val streamUrl:String,
    val sinopsis:String="Sinopsis no disponible.",
    val duracion:String="51 min"
)

data class Temporada(
    val numero:Int,
    val titulo:String,
    val capitulos:List<Capitulo>,
    val posterUrl:String = ""
)

data class SerieDrive(
    val titulo:String,
    val posterUrl:String,
    val bannerUrl:String,
    val sinopsis:String,
    val temporadas:List<Temporada>,
    val anio:String="2011",
    val genero:String="Drama, Histórico",
    val valoracion:String="8.1"
)

sealed class KidsItem{
    data class Movie(val data:PeliculaDrive):KidsItem()
    data class Series(val data:SerieDrive):KidsItem()
}

data class SeguirViendoItem(
    val tipo:String,
    val titulo:String,
    val subtitulo:String="",
    val posterUrl:String,
    val bannerUrl:String,
    val sinopsis:String,
    val streamUrl:String,
    val temporada:Int=0,
    val capitulo:Int=0,
    val progreso:Int=0
)
