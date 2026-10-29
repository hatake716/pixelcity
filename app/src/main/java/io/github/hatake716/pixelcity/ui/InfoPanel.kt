package io.github.hatake716.pixelcity.ui

import io.github.hatake716.pixelcity.game.City
import io.github.hatake716.pixelcity.game.MonthlyStat
import io.github.hatake716.pixelcity.game.CustomStyle
import io.github.hatake716.pixelcity.game.Ordinance

/**
 * 情報の画面。地図に重ねるのではなく、全面に出して読ませる。
 *
 * 「いま街がどうなっているか」を伝えるのが役目なので、
 * 数字を並べるだけでなく、推移のグラフと助言も添える。
 */
class InfoPanel(private val text: GbText) {

    /**
     * 通知の欄の高さ（論理ピクセル）。中身をこのぶんだけ下げる。
     * [GameView] が画面の倍率を知っているので、そこから渡してもらう。
     */
    var insetTop: Int = 0
    /** 操作の欄の高さ。閉じる釦をこのぶんだけ上げる。 */
    var insetBottom: Int = 0

    /** 情報画面の見出し。 */
    enum class Tab(val label: String) {
        SUMMARY("概要"),
        GRAPH("推移"),
        BUDGET("収支"),
        MAP("地図"),
        ORDINANCE("条例"),
        SETTINGS("設定"),
    }

    var tab: Tab = Tab.SUMMARY

    /** データマップの選択。地図へ戻したときに使う。 */
    var overlay: CityRenderer.Overlay = CityRenderer.Overlay.NONE

    /** 推移グラフで見る項目。 */
    enum class Series(val label: String, val pick: (MonthlyStat) -> Int) {
        POPULATION("人口", { it.population }),
        FUNDS("資金", { it.funds }),
        BALANCE("収支", { it.balance }),
        POLLUTION("公害", { it.pollution }),
        CRIME("犯罪", { it.crime }),
        UNEMPLOYMENT("失業", { it.unemployment }),
        TRAFFIC("渋滞", { it.traffic }),
    }

    var series: Series = Series.POPULATION

    companion object {
        const val MARGIN=18
        const val TAB_H=64
        const val ROW=36
        const val MAP_ROW_H=72
        const val STYLE_ROW_H=80
    }
    private data class Target(val key: Any,val x: Int,val y: Int,val w: Int,val h: Int)
    private val targets=ArrayList<Target>()
    var scrollOffset=0
        private set
    private var maxScroll=0
    private var lastTab=tab
    private var contentTop=0
    private var contentBottom=0
    private var y=0
    var custom: CustomStyle?=null
    var sfxOn=true
    var bgmOn=true
    private var soundRowY=0
    private var settingsDisasterY=0
    private var customEditY=-1
    private fun columns()=if(GameView.LOGICAL_W>=900) 6 else 3
    private fun tabRows()=(Tab.entries.size+columns()-1)/columns()
    fun tabX(index: Int)=MARGIN+(index%columns())*tabW()
    fun tabW()=(GameView.LOGICAL_W-MARGIN*2)/columns()
    fun tabY()=insetTop+64
    fun rowY(index: Int)=tabY()+TAB_H*tabRows()+20+index*ROW
    fun tabAt(lx: Int,ly: Int): Tab? = Tab.entries.firstOrNull {
        val x=tabX(it.ordinal);val top=tabY()+(it.ordinal/columns())*TAB_H
        lx in x until x+tabW() && ly in top until top+TAB_H
    }
    fun scrollBy(delta: Int) { scrollOffset=(scrollOffset+delta).coerceIn(0,maxScroll) }
    fun closeButtonY(logicalH: Int)=logicalH-insetBottom-80
    private fun hit(lx: Int,ly: Int): Any? {
        if(lx<MARGIN || lx>=GameView.LOGICAL_W-MARGIN || ly<contentTop || ly>=contentBottom) return null
        val sy=ly+scrollOffset
        return targets.firstOrNull { lx in it.x until it.x+it.w && sy in it.y until it.y+it.h }?.key
    }
    fun overlayAt(lx: Int,ly: Int)=hit(lx,ly) as? CityRenderer.Overlay
    fun ordinanceAt(lx: Int,ly: Int,logicalH: Int)=hit(lx,ly) as? Ordinance
    fun styleAt(lx: Int,ly: Int,logicalH: Int)=hit(lx,ly) as? City.Style
    fun seriesAt(lx: Int,ly: Int)=hit(lx,ly) as? Series
    fun disasterAt(lx: Int,ly: Int)=hit(lx,ly) as? City.DisasterLevel
    fun customEditTapped(lx: Int,ly: Int)=hit(lx,ly)=="custom"
    fun soundToggleAt(lx: Int,ly: Int): Int?=when(hit(lx,ly)) { "sfx"->0;"bgm"->1;else->null }

    fun draw(p: PixelCanvas,city: City,logicalH: Int) {
        if(lastTab!=tab) { scrollOffset=0;lastTab=tab }
        p.clear(Palette.UI_BG)
        text.textSize=28;text.draw(p,"都市情報",MARGIN,insetTop+14,Palette.UI_TEXT)
        text.textSize=22
        for(t in Tab.entries) {
            val x=tabX(t.ordinal);val top=tabY()+t.ordinal/columns()*TAB_H
            p.fillRect(x,top,tabW()-4,TAB_H-4,if(t==tab) Palette.UI_ACCENT else Palette.UI_BG_LIGHT)
            text.drawCentered(p,t.label,x+(tabW()-4)/2,top+18,if(t==tab) Palette.UI_BG else Palette.UI_TEXT)
        }
        contentTop=rowY(0);contentBottom=closeButtonY(logicalH)-12
        targets.clear();y=contentTop
        val c=p.nativeCanvas
        c?.save();c?.clipRect(0,contentTop,GameView.LOGICAL_W,contentBottom);c?.translate(0f,-scrollOffset.toFloat())
        when(tab) {
            Tab.SUMMARY -> {
                row(p,"住宅 / 商業 / 工業の需要","${city.demandR} / ${city.demandC} / ${city.demandI}");row(p,"人口","${city.population} 人");row(p,"雇用","${city.jobs} 人");row(p,"失業率","${city.unemployment}%")
                row(p,"平均地価","${city.averageLandValue}");row(p,"平均公害","${city.averagePollution}");row(p,"平均犯罪","${city.averageCrime}")
                row(p,"感染度","${city.infection}");row(p,"渋滞率","${city.congestionRate}%");row(p,"支持率","${city.approval}%")
                heading(p,"暮らしを支える設備")
                row(p,"電力供給 / 需要","${city.powerSupply} / ${city.powerDemand}")
                row(p,"給水供給 / 需要","${city.waterSupply} / ${city.waterDemand}")
                row(p,"ゴミ発生 / 処理能力","${city.garbageProduced} / ${city.garbageCapacity}")
                row(p,"未処理のゴミ","${city.garbageBacklog}")
                heading(p,"市長への助言")
                paragraph(p,when { city.powerSupply<city.powerDemand -> "電力が不足しています。発電施設と送電経路を確認しましょう。"
                    city.waterSupply<city.waterDemand -> "水道が不足しています。給水設備を整えて住民の健康を守りましょう。"
                    city.unemployment>20 -> "働く場所が不足しています。商業や工業の区画と交通を整えましょう。"
                    city.averagePollution>35 -> "公害が広がっています。産業と住宅の距離、緑地、排出規制を見直しましょう。"
                    else -> "暮らしは落ち着いています。収支と需要を見ながら、少しずつ街を広げましょう。" })
            }
            Tab.BUDGET -> {
                val inc=city.lastIncomeBreakdown;val sp=city.lastSpending
                heading(p,"収入")
                row(p,"住宅税",money(inc.residential));row(p,"商業税",money(inc.commercial));row(p,"工業・企業協定",money(inc.industrial));row(p,"観光",money(inc.tourism));row(p,"収入合計",money(inc.total))
                heading(p,"支出")
                listOf("交通" to sp.transport,"電力" to sp.power,"水道" to sp.water,"ゴミ処理" to sp.garbage,"警察・消防" to sp.safety,"医療" to sp.health,"教育" to sp.education,"公園・農地" to sp.parks,"条例・施策" to sp.ordinances).forEach { row(p,it.first,money(it.second)) }
                row(p,"支出合計",money(sp.total));heading(p,"月間収支 ${money(inc.total-sp.total)}")
            }
            Tab.MAP -> {
                paragraph(p,"項目を選ぶと街に色を重ねます。地図上のラベルを押すと解除できます。")
                for(o in CityRenderer.Overlay.entries) choice(p,o,o.label,if(o==overlay) "選択中" else if(o.good) "値が高いほど青・緑" else "値が高いほど橙・赤",o==overlay,MAP_ROW_H)
            }
            Tab.ORDINANCE -> {
                paragraph(p,"条例の費用は人口に応じて毎月かかります。効果と収支を見ながら選びましょう。")
                for(o in Ordinance.entries) choice(p,o,o.label,o.detail+"  /  ${money((city.population*o.costPerCitizen).toInt())} / 月",o in city.ordinances,106)
            }
            Tab.SETTINGS -> {
                heading(p,"サウンド")
                soundRowY=y
                choice(p,"sfx","効果音",if(sfxOn) "オン" else "オフ",sfxOn)
                choice(p,"bgm","音楽",if(bgmOn) "オン" else "オフ",bgmOn)
                heading(p,"街並みの色調")
                paragraph(p,"建物の素材を保ちながら、街の色調を変えます。")
                for(st in City.Style.entries) choice(p,st,st.label,st.detail,st==city.style,STYLE_ROW_H)
                customEditY=-1
                if(city.style==City.Style.CUSTOM) { customEditY=y;choice(p,"custom","色を選ぶ","建物の色調をカスタマイズ",false) }
                heading(p,"災害の多さ")
                settingsDisasterY=y
                for(lv in City.DisasterLevel.entries) choice(p,lv,lv.label,null,lv==city.disasterLevel)
            }
            Tab.GRAPH -> {
                for(s in Series.entries) choice(p,s,s.label,null,s==series,64)
                heading(p,"${series.label}の推移")
                val history=city.history
                if(history.size<2) paragraph(p,"月が進むと、この場所に街の推移が記録されます。")
                else {
                    val values=history.map(series.pick);val lo=minOf(0,values.min());val hi=maxOf(1,values.max());val span=(hi.toDouble()-lo).coerceAtLeast(1.0)
                    val left=MARGIN+48;val w=GameView.LOGICAL_W-left-MARGIN;val h=240
                    p.drawRect(left,y,w,h,Palette.UI_LINE)
                    text.textSize=17;text.draw(p,"$hi",MARGIN,y,Palette.UI_DIM);text.draw(p,"$lo",MARGIN,y+h-20,Palette.UI_DIM)
                    val path=android.graphics.Path()
                    for((i,v) in values.withIndex()) { val px=left+i*w.toFloat()/(values.size-1);val py=y+h-((v.toDouble()-lo)/span*h).toFloat();if(i==0) path.moveTo(px,py) else path.lineTo(px,py) }
                    c?.drawPath(path,android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color=Palette.of(Palette.UI_ACCENT);style=android.graphics.Paint.Style.STROKE;strokeWidth=2f })
                    y+=h+20
                }
            }
        }
        maxScroll=(y-contentBottom).coerceAtLeast(0)
        if(scrollOffset>maxScroll) scrollOffset=maxScroll
        c?.restore()
        val by=closeButtonY(logicalH)
        p.fillRect(MARGIN,by,GameView.LOGICAL_W-MARGIN*2,64,Palette.UI_BG_LIGHT)
        text.textSize=23;text.drawCentered(p,"街に戻る",GameView.LOGICAL_W/2,by+18,Palette.UI_TEXT)
        if(maxScroll>0) {
            p.fillRect(GameView.LOGICAL_W-8,contentTop,3,contentBottom-contentTop,Palette.UI_LINE)
            val h=((contentBottom-contentTop).toFloat()/(y-contentTop)*(contentBottom-contentTop)).toInt().coerceAtLeast(22)
            val pos=contentTop+(contentBottom-contentTop-h)*scrollOffset/maxScroll
            p.fillRect(GameView.LOGICAL_W-8,pos,3,h,Palette.UI_ACCENT)
        }
    }
    private fun money(v: Int)="$${"%,d".format(v)}"
    private fun row(p: PixelCanvas,label: String,value: String) {
        text.textSize=21;text.draw(p,label,MARGIN,y,Palette.UI_DIM)
        text.draw(p,value,GameView.LOGICAL_W-MARGIN-text.measure(value),y,Palette.UI_TEXT);y+=ROW
    }
    private fun heading(p: PixelCanvas,label: String) {
        y+=14;text.textSize=24;text.draw(p,label,MARGIN,y,Palette.UI_ACCENT);y+=44
    }
    private fun paragraph(p: PixelCanvas,value: String) {
        text.textSize=20
        for(line in text.wrap(value,GameView.LOGICAL_W-MARGIN*2-12)) { text.draw(p,line,MARGIN,y,Palette.UI_DIM);y+=29 }
        y+=14
    }
    private fun choice(p: PixelCanvas,key: Any,title: String,detail: String?,on: Boolean,height: Int=76) {
        val x=MARGIN;val w=GameView.LOGICAL_W-MARGIN*2
        text.textSize=18
        val lines=detail?.let { text.wrap(it,w-36) }.orEmpty()
        val h=maxOf(height,if(lines.isEmpty()) 64 else 46+lines.size*24)
        p.fillRect(x,y,w,h-6,if(on) Palette.UI_BG_LIGHT else Palette.UI_BG)
        p.drawRect(x,y,w,h-6,if(on) Palette.UI_ACCENT else Palette.UI_LINE)
        text.textSize=22;text.draw(p,if(on) "✓ $title" else title,x+12,y+10,if(on) Palette.UI_ACCENT else Palette.UI_TEXT)
        text.textSize=18
        for((i,line) in lines.withIndex()) text.draw(p,line,x+12,y+40+i*24,Palette.UI_DIM)
        targets.add(Target(key,x,y,w,h-6));y+=h+4
    }
}
