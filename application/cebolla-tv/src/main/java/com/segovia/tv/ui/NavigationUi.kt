package com.segovia.tv.ui

import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class NavigationUi(private val activity: AppCompatActivity, private val navigate: (String)->Unit, private val exit: ()->Unit) {
    private val density get() = activity.resources.displayMetrics.density
    private fun dp(v:Int)=UiUtils.dp(v,density)

    fun tab(title:String, icon:String, active:Boolean): LinearLayout {
        val activeColor=Color.parseColor(UiUtils.GOLD);val normal=Color.parseColor("#D7DCE1")
        return LinearLayout(activity).apply{
            orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;isFocusable=true;isFocusableInTouchMode=true;isClickable=true;id=View.generateViewId();setPadding(dp(2),0,dp(2),0)
            layoutParams=LinearLayout.LayoutParams(dp(82),dp(66)).apply{marginStart=dp(1);marginEnd=dp(1)}
            addView(SegoviaIconView(activity,icon).apply{layoutParams=LinearLayout.LayoutParams(dp(18),dp(20));setIconColor(if(active)activeColor else normal)})
            addView(TextView(activity).apply{text=title;textSize=11f;gravity=Gravity.CENTER;includeFontPadding=false;setTextColor(if(active)activeColor else normal);setTypeface(null,if(active)Typeface.BOLD else Typeface.NORMAL);layoutParams=LinearLayout.LayoutParams(-1,dp(22))})
            setOnClickListener{when(title){"INICIO"->navigate("Inicio");"PELICULAS"->navigate("Peliculas");"SERIES"->navigate("Series");"KIDS"->navigate("Kids");"AJUSTES"->navigate("Ajustes");"SALIR"->exit()}}
            setOnFocusChangeListener{v,has->
                val row=v as LinearLayout;val iv=row.getChildAt(0) as SegoviaIconView;val tv=row.getChildAt(1) as TextView
                iv.setIconColor(if(has||active)activeColor else normal);tv.setTextColor(if(has||active)activeColor else normal);tv.setTypeface(null,if(has||active)Typeface.BOLD else Typeface.NORMAL);v.animate().scaleX(if(has)1.06f else 1f).scaleY(if(has)1.06f else 1f).setDuration(100).start()
            }
        }
    }

    fun topBar(active:String):FrameLayout{
        val bar=FrameLayout(activity).apply{setBackgroundColor(Color.TRANSPARENT);clipChildren=false}
        val tabs=LinearLayout(activity).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER}
        tabs.addView(tab("INICIO","home",active=="Inicio"))
        tabs.addView(tab("PELICULAS","movies",active=="Peliculas"||active=="Películas"||active=="DetallesPeli"))
        tabs.addView(tab("SERIES","series",active=="Series"||active=="DetallesSerie"))
        tabs.addView(tab("KIDS","kids",active=="Kids"))
        bar.addView(tabs,FrameLayout.LayoutParams(-2,dp(66)).apply{gravity=Gravity.TOP or Gravity.CENTER_HORIZONTAL})
        bar.addView(tab("SALIR","exit",false),FrameLayout.LayoutParams(dp(82),dp(66)).apply{gravity=Gravity.TOP or Gravity.END;rightMargin=dp(42)})
        return bar
    }

    fun homeBar():FrameLayout{
        val bar=FrameLayout(activity).apply{setBackgroundColor(Color.TRANSPARENT);clipChildren=false}
        val tabs=LinearLayout(activity).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER}
        tabs.addView(tab("INICIO","home",true));tabs.addView(tab("PELICULAS","movies",false));tabs.addView(tab("SERIES","series",false));tabs.addView(tab("KIDS","kids",false))
        bar.addView(tabs,FrameLayout.LayoutParams(-2,dp(66)).apply{gravity=Gravity.TOP or Gravity.CENTER_HORIZONTAL})
        bar.addView(tab("SALIR","exit",false),FrameLayout.LayoutParams(dp(82),dp(66)).apply{gravity=Gravity.TOP or Gravity.END;rightMargin=dp(42)})
        return bar
    }
}
