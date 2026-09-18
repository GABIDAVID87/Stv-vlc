package com.segovia.tv.peliculas

import com.segovia.tv.grilla.GridScreen
import androidx.appcompat.app.AppCompatActivity
import com.segovia.tv.model.PeliculaDrive
import com.segovia.tv.ui.NavigationUi
class PeliculasScreen(private val activity:AppCompatActivity,private val nav:NavigationUi,private val grid:GridScreen){fun show(items:List<PeliculaDrive>){grid.showMovies(items,"Películas")}}
