package com.segovia.tv.detalles

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.segovia.tv.model.PeliculaDrive
import com.segovia.tv.playback.ExternalPlayerLauncher
import com.segovia.tv.ui.ImageLoader
import com.segovia.tv.ui.NavigationUi
import com.segovia.tv.ui.UiUtils

class MovieDetailsScreen(private val activity:AppCompatActivity,private val nav:NavigationUi,private val token:()->String?,private val name:(String)->String,private val player:ExternalPlayerLauncher,private val onPlay:(PeliculaDrive)->Unit={}){
    private val d get()=activity.resources.displayMetrics.density;private fun dp(v:Int)=UiUtils.dp(v,d)
    fun show(p:PeliculaDrive){
        val root=FrameLayout(activity).apply{setBackgroundColor(Color.parseColor(UiUtils.BG))};val hero=ImageView(activity).apply{layoutParams=FrameLayout.LayoutParams(-1,-1);scaleType=ImageView.ScaleType.CENTER_CROP};ImageLoader.load(activity,p.bannerUrl,hero,token());root.addView(hero)
        root.addView(View(activity).apply{layoutParams=FrameLayout.LayoutParams(-1,-1);background=GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,intArrayOf(Color.parseColor("#FF080C14"),Color.parseColor("#EE080C14"),Color.parseColor("#AA080C14"),Color.TRANSPARENT))});root.addView(View(activity).apply{layoutParams=FrameLayout.LayoutParams(-1,-1);background=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(Color.TRANSPARENT,Color.parseColor("#CC080C14"),Color.parseColor("#FF080C14")))})
        val bar=nav.topBar("DetallesPeli");root.addView(bar,FrameLayout.LayoutParams(-1,dp(76)))
        val scroll=ScrollView(activity).apply{layoutParams=FrameLayout.LayoutParams(-1,-1).apply{topMargin=dp(76);leftMargin=dp(50)}};val c=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;setPadding(0,dp(35),0,dp(40))}
        c.addView(TextView(activity).apply{text=name(p.titulo);textSize=34f;setTextColor(Color.WHITE);setTypeface(null,Typeface.BOLD);setPadding(0,0,0,dp(15))});c.addView(TextView(activity).apply{text="2024  •  2h 46min  •  Aventura, Ciencia ficción  •  ★ 8.8";textSize=13f;setTextColor(Color.parseColor("#CBD5E1"));setPadding(0,0,0,dp(20))})
        val row=LinearLayout(activity).apply{orientation=LinearLayout.HORIZONTAL;setPadding(0,0,0,dp(25))};val play=TextView(activity).apply{text="▶   REPRODUCIR";textSize=13f;gravity=Gravity.CENTER;setTextColor(Color.BLACK);setTypeface(null,Typeface.BOLD);isFocusable=true;isFocusableInTouchMode=true;background=GradientDrawable().apply{shape=GradientDrawable.RECTANGLE;setColor(Color.parseColor(UiUtils.YELLOW));cornerRadius=dp(8).toFloat()};setPadding(dp(35),dp(14),dp(35),dp(14));layoutParams=LinearLayout.LayoutParams(-2,dp(52)).apply{rightMargin=dp(15)};setOnFocusChangeListener{v,has->v.animate().scaleX(if(has)1.05f else 1f).scaleY(if(has)1.05f else 1f).setDuration(100).start()};setOnClickListener{onPlay(p);player.play(p.streamUrl,name(p.titulo))}};row.addView(play)
        row.addView(TextView(activity).apply{text="+ MI LISTA";textSize=13f;gravity=Gravity.CENTER;setTextColor(Color.WHITE);setTypeface(null,Typeface.BOLD);isFocusable=true;isFocusableInTouchMode=true;background=UiUtils.rounded(Color.parseColor("#27272A"),dp(8).toFloat());setPadding(dp(35),dp(14),dp(35),dp(14));layoutParams=LinearLayout.LayoutParams(-2,dp(52));setOnClickListener{Toast.makeText(activity,"Añadido a mi lista",Toast.LENGTH_SHORT).show()}});c.addView(row)
        c.addView(TextView(activity).apply{text=p.sinopsis;textSize=14f;setTextColor(Color.parseColor("#94A3B8"));setLineSpacing(dp(4).toFloat(),1.1f);layoutParams=LinearLayout.LayoutParams(dp(700),-2)});scroll.addView(c);root.addView(scroll);activity.setContentView(root);bar.bringToFront();play.requestFocus()
    }
}
