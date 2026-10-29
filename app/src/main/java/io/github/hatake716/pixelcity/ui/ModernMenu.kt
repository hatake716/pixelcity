package io.github.hatake716.pixelcity.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.view.ViewCompat
import androidx.core.view.doOnAttach

/** Native text, focus, ripple and scrolling give every menu a real accessible touch target. */
internal object ModernMenu {
    val ink=Color.rgb(255,246,231)
    val muted=Color.rgb(212,196,175)
    val accent=Color.rgb(225,189,143)
    val background=Color.rgb(53,46,41)
    fun dp(c: Context,n: Int)=(c.resources.displayMetrics.density*n+.5f).toInt()
    fun label(c: Context,value: String,size: Float=18f,bold: Boolean=false)=TextView(c).apply {
        text=value;textSize=size;setTextColor(ink)
        typeface=Typeface.create("sans-serif",if(bold) Typeface.BOLD else Typeface.NORMAL)
        setLineSpacing(dp(c,3).toFloat(),1f)
        importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }
    fun shape(c: Context,color: Int,stroke: Int=Color.TRANSPARENT)=GradientDrawable().apply {
        setColor(color);cornerRadius=dp(c,18).toFloat();setStroke(dp(c,1),stroke)
    }
    fun action(c: Context,title: String,detail: String?=null,primary: Boolean=false,action: ()->Unit): LinearLayout {
        return LinearLayout(c).apply {
            orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_VERTICAL
            minimumHeight=dp(c,if(detail==null) 56 else 72)
            setPadding(dp(c,20),dp(c,14),dp(c,20),dp(c,14))
            background=RippleDrawable(ColorStateList.valueOf(0x30f3d7ad),shape(c,if(primary) accent else 0xed50483f.toInt(),if(primary) accent else 0xff8d7c69.toInt()),null)
            addView(label(c,title,19f,true).apply { if(primary) setTextColor(ModernMenu.background) })
            detail?.let { addView(label(c,it,13f).apply { setTextColor(if(primary) 0xff554332.toInt() else muted) }) }
            isClickable=true;isFocusable=true
            contentDescription=listOfNotNull(title,detail).joinToString("。")
            descendantFocusability=android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
            setOnClickListener { action() }
        }
    }
    fun column(root: FrameLayout,title: String,subtitle: String): LinearLayout {
        val c=root.context;root.setBackgroundColor(background)
        val scroll=ScrollView(c).apply { isFillViewport=true }
        val list=LinearLayout(c).apply { orientation=LinearLayout.VERTICAL;setPadding(dp(c,22),dp(c,20),dp(c,22),dp(c,24)) }
        scroll.addView(list);root.addView(scroll,FrameLayout.LayoutParams(-1,-1))
        safeInsets(scroll)
        list.addView(label(c,title,30f,true));list.addView(label(c,subtitle,15f).apply { setTextColor(muted);setPadding(0,dp(c,8),0,dp(c,24)) })
        return list
    }
    fun add(list: LinearLayout,view: View) {
        list.addView(view,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(list.context,12) })
    }
    fun safeInsets(view: View) {
        // スクロール中の文字やカードも、カメラ穴とシステム欄の下へ描かせない。
        (view as? ViewGroup)?.clipToPadding=true
        ViewCompat.setOnApplyWindowInsetsListener(view) { v,ins ->
            val safe=SystemBars.safeInsets(ins,dp(v.context,8))
            v.setPadding(safe.left,safe.top,safe.right,safe.bottom);ins
        }
        view.doOnAttach { v ->
            ViewCompat.getRootWindowInsets(v)?.let { ins ->
                val safe=SystemBars.safeInsets(ins,dp(v.context,8))
                v.setPadding(safe.left,safe.top,safe.right,safe.bottom)
            }
            ViewCompat.requestApplyInsets(v)
        }
    }
}
