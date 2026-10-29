package io.github.hatake716.pixelcity.ui

import android.content.Context
import android.graphics.Canvas
import android.view.View
import android.widget.*

class ShowcasePickerView(context: Context): FrameLayout(context) {
    private val previews=mutableMapOf<ShowcaseCity.Kind,Pair<io.github.hatake716.pixelcity.game.City,RealisticCityRenderer>>()
    var onChosen: ((ShowcaseCity.Kind)->Unit)?=null
    var onWorldChosen: (()->Unit)?=null
    var onBack: (()->Unit)?=null
    init {
        val list=ModernMenu.column(this,"模範都市","気になる街を選び、あなたらしく育ててください。")
        val world=LinearLayout(context).apply {
            orientation=LinearLayout.VERTICAL;background=ModernMenu.shape(context,0xff50483f.toInt())
        }
        // Four tiny crops, never four 512 × 512 worlds, even while scrolling this preview.
        for(pair in ShowcaseCity.Kind.entries.chunked(2)) {
            val row=LinearLayout(context)
            for(kind in pair) row.addView(preview(kind),LinearLayout.LayoutParams(0,ModernMenu.dp(context,100),1f))
            world.addView(row)
        }
        world.addView(ModernMenu.action(context,RegionalShowcase.WORLD_LABEL,
            "4都市が共存する惑星\nイギリスの港町・アメリカ東海岸の大都市\nヨーロッパの田園都市・中国の工業都市",primary=true) { onWorldChosen?.invoke() })
        ModernMenu.add(list,world)
        val descriptions=listOf("水辺のカフェと、緑の広場のある街。","暮らしと仕事が集まる、活気ある街。","農業・再生可能エネルギー・鉄道の街。","産業を育て、豊かな暮らしを支える街。")
        for((i,kind) in ShowcaseCity.Kind.entries.withIndex()) {
            val card=LinearLayout(context).apply { orientation=LinearLayout.VERTICAL;background=ModernMenu.shape(context,0xff50483f.toInt()) }
            card.addView(preview(kind),LinearLayout.LayoutParams(-1,ModernMenu.dp(context,150)))
            card.addView(ModernMenu.action(context,kind.label,"${RegionalShowcase.region(kind).label} · ${descriptions[i]}") { onChosen?.invoke(kind) })
            ModernMenu.add(list,card)
        }
        ModernMenu.add(list,ModernMenu.action(context,"戻る") { onBack?.invoke() })
    }
    private fun preview(kind: ShowcaseCity.Kind)=object: View(context) {
        private val data by lazy { previews.getOrPut(kind) { RegionalShowcase.build(kind).city to RealisticCityRenderer(context) } }
        init { importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO }
        override fun onDetachedFromWindow() { super.onDetachedFromWindow();previews[kind]?.second?.releaseCaches() }
        override fun onDraw(c: Canvas) {
            val (city,renderer)=data
            val zoom=minOf(width/(Iso.mapPixelWidth(city.width,city.height)*.7f),height/(Iso.mapPixelHeight(city.width,city.height)*.7f))
            renderer.draw(c,city,city.originX.toFloat(),city.originY.toFloat(),zoom,width,0,height)
        }
    }
}
