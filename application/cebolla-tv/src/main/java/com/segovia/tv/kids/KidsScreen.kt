package com.segovia.tv.kids

import com.segovia.tv.grilla.GridScreen
import androidx.appcompat.app.AppCompatActivity
import com.segovia.tv.model.KidsItem
import com.segovia.tv.ui.NavigationUi
class KidsScreen(private val activity:AppCompatActivity,private val nav:NavigationUi,private val grid:GridScreen){fun show(items:List<KidsItem>){grid.showKids(items,"Kids")}}
