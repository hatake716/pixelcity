package io.github.hatake716.pixelcity.ui

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.View
import android.widget.*
import kotlin.math.sin

/** A living, playable city behind a native, comfortably sized title menu. */
class TitleView(context: Context, private val hasSave: Boolean) : FrameLayout(context) {
    var onStartTutorial: (() -> Unit)?=null
    var onSkipTutorial: (() -> Unit)?=null
    var onContinue: (() -> Unit)?=null
    var onShowcase: (() -> Unit)?=null
    var onLicenses: (() -> Unit)?=null
    private val cityBackdrop=CityBackdrop(context)
    private var menuWide: Boolean?=null
    init { addView(cityBackdrop,LayoutParams(-1,-1));buildMenu(false) }
    override fun onSizeChanged(w: Int,h: Int,oldw: Int,oldh: Int) {
        super.onSizeChanged(w,h,oldw,oldh)
        // Replacing children inside layout leaves the new tree unmeasured until another
        // traversal. Rebuild after this traversal so every button receives real bounds.
        if(menuWide!=(w>h)) post { if(isAttachedToWindow && menuWide!=(width>height)) buildMenu(width>height) }
    }
    private fun buildMenu(wide: Boolean) {
        menuWide=wide
        while(childCount>1) removeViewAt(1)
        fun column()=LinearLayout(context).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(ModernMenu.dp(context,26),ModernMenu.dp(context,24),ModernMenu.dp(context,26),ModernMenu.dp(context,20))
        }
        val intro=column();val actions=column()
        intro.addView(ModernMenu.label(context,"あなたの決断が、街の明日になる。",14f).apply { letterSpacing=.08f })
        intro.addView(ModernMenu.label(context,"PIXEL\nCITY",66f,true).apply {
            typeface=Typeface.Builder(context.assets,"fonts/Outfit.ttf").setFontVariationSettings("'wght' 750").build()
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP,66f)
            setLineSpacing(0f,.83f);letterSpacing=-.055f;setTextColor(ModernMenu.ink)
            setShadowLayer(14f,0f,4f,0x80303c3a.toInt());includeFontPadding=false
            setPadding(0,ModernMenu.dp(context,16),0,0);contentDescription="ピクセルシティ"
        })
        intro.addView(ModernMenu.label(context,"街を作る。暮らしを育てる。",17f).apply { setPadding(0,ModernMenu.dp(context,20),0,0) })
        if(hasSave) ModernMenu.add(actions,ModernMenu.action(context,"都市再開","あなたの街へ戻る",true) { onContinue?.invoke() })
        ModernMenu.add(actions,ModernMenu.action(context,"新規開始","新米市長と秘書ひよりの物語",!hasSave) { onStartTutorial?.invoke() }.apply { tag="startTutorial" })
        ModernMenu.add(actions,ModernMenu.action(context,"自由建設","チュートリアルをスキップ") { onSkipTutorial?.invoke() })
        ModernMenu.add(actions,ModernMenu.action(context,"模範都市","発展した世界・4つの個性ある都市") { onShowcase?.invoke() })
        ModernMenu.add(actions,ModernMenu.action(context,"クレジット・ライセンス") { onLicenses?.invoke() })
        if(wide) {
            val split=LinearLayout(context).apply { orientation=LinearLayout.HORIZONTAL }
            split.addView(ScrollView(context).apply { addView(intro) },LinearLayout.LayoutParams(0,-1,1f))
            split.addView(ScrollView(context).apply { addView(actions) },LinearLayout.LayoutParams(0,-1,1.15f))
            addView(split,LayoutParams(-1,-1));ModernMenu.safeInsets(split)
        } else {
            val content=LinearLayout(context).apply { orientation=LinearLayout.VERTICAL;addView(intro)
                addView(View(context),LinearLayout.LayoutParams(1,ModernMenu.dp(context,28),1f));addView(actions) }
            val scroll=ScrollView(context).apply { isFillViewport=true;addView(content) }
            addView(scroll,LayoutParams(-1,-1));ModernMenu.safeInsets(scroll)
        }
    }

    private class CityBackdrop(context: Context): View(context) {
        private val city by lazy { ShowcaseCity.build(ShowcaseCity.Kind.HARBOUR) }
        private val renderer=RealisticCityRenderer(context)
        private val traffic=TrafficAnimation()
        private val p=Paint(Paint.ANTI_ALIAS_FLAG)
        private var started=0L
        private var last=0L
        private var refilled=0L
        override fun onDraw(c: Canvas) {
            val now=SystemClock.uptimeMillis()
            if(started==0L) started=now
            val t=(now-started)/1000f
            if(now-refilled>3500) { traffic.refill(city,24,28,46,45);refilled=now }
            if(last!=0L) traffic.advance(city,((now-last)/1000f).coerceAtMost(.05f))
            last=now
            // Slow bounded orbit; the city and vehicles are the game's own simulation scenery.
            val z=width/1080f*.28f
            renderer.draw(c,city,34f+sin(t*.045f)*1.3f,39f+sin(t*.035f)*1.1f,z,width,0,height,traffic.all,time=t)
            p.shader=LinearGradient(0f,0f,0f,height.toFloat(),intArrayOf(0x80352e29.toInt(),0x10352e29,0x98352e29.toInt(),0xfa352e29.toInt()),floatArrayOf(0f,.33f,.61f,1f),Shader.TileMode.CLAMP)
            c.drawRect(0f,0f,width.toFloat(),height.toFloat(),p);p.shader=null
            if(isShown && hasWindowFocus()) postInvalidateDelayed(32L)
        }
        override fun onDetachedFromWindow() { super.onDetachedFromWindow(); renderer.releaseCaches() }
        override fun onWindowFocusChanged(hasWindowFocus: Boolean) { super.onWindowFocusChanged(hasWindowFocus);last=0; if(hasWindowFocus) invalidate() }
    }
}
