package io.github.hatake716.pixelcity.ui

import android.graphics.Canvas
import android.graphics.RectF
import io.github.hatake716.pixelcity.game.Campaign
import io.github.hatake716.pixelcity.game.City

/** Paged council notebook. Buttons and portrait use the same coordinates as the rendered page. */
class CampaignPanel(private val text: GbText, private val portrait: HiyoriPortrait) {
    companion object { const val BANNER_HEIGHT = 144 }
    enum class Action { METROPOLIS, GARDEN, INDUSTRIAL, OPTION_0, OPTION_1, CLOSE, OPEN, STORY, GOALS, POLICIES, PREVIOUS, NEXT }
    data class Button(val action: Action, val x: Int, val y: Int, val w: Int, val h: Int) {
        fun contains(px: Int, py: Int) = px in x until x+w && py in y until y+h
    }
    var buttons = emptyList<Button>(); private set
    var tab = 0; private set
    var page = 0; private set
    private var lastPage = 0
    private val portraitBounds = RectF()
    private val paper = Palette.RURAL_WALL
    private val ink = Palette.UI_BG
    private val green = Palette.TREE_DARK
    fun open() { tab = 0; page = 0 }
    fun navigate(action: Action) {
        when(action) {
            Action.STORY -> { tab = 0; page = 0 }
            Action.GOALS -> { tab = 1; page = 0 }
            Action.POLICIES -> { tab = 2; page = 0 }
            Action.NEXT -> page = (page+1).coerceAtMost(lastPage)
            Action.PREVIOUS -> page = (page-1).coerceAtLeast(0)
            else -> {}
        }
    }
    fun actionAt(x: Int,y: Int) = buttons.firstOrNull { it.contains(x,y) }?.action
    fun drawPortrait(canvas: Canvas) = portrait.draw(canvas,portraitBounds)

    fun drawBanner(c: PixelCanvas, city: City, bottom: Int,width: Int=c.width) {
        val campaign = city.campaign
        val y = bottom-BANNER_HEIGHT
        c.fillRect(0,y,width,BANNER_HEIGHT,paper);c.fillRect(0,y,width,3,green)
        text.textSize=21
        text.draw(c, campaign.route?.let { "${it.label}  ${if(campaign.completed) "完成" else "第${campaign.chapter+1}章 / 5"}" } ?: "ひよりから、次の街への提案",12,y+8,green)
        val b=Button(Action.OPEN,width-172,y+8,160,64)
        c.drawRect(b.x,b.y,b.w,b.h,green);text.drawCentered(c,"市政手帳",b.x+b.w/2,b.y+20,ink)
        text.textSize=18
        val line=when {
            campaign.route==null -> "大都市・田園都市・工業都市から未来を選びます。"
            campaign.completed -> "${campaign.route!!.cityName}。この街の経営を続けられます。"
            !campaign.chosen -> "新しい政策を選びましょう。費用と効果は市政手帳へ。"
            else -> campaign.objectives(city).firstOrNull { !it.met }?.text()
                ?: if(campaign.chapter==4) "全条件を保って黒字 ${campaign.stableMonths}/${Campaign.STABLE_MONTHS}か月" else "目標達成。次の月に住民の報告が届きます。"
        }
        text.wrap(line,width-24).take(2).forEachIndexed { i,s -> text.draw(c,s,12,y+84+i*25,ink) }
        buttons=listOf(b)
    }

    fun draw(c: PixelCanvas,city: City,insetTop: Int,insetBottom: Int) {
        val a=city.campaign;val route=a.route
        val wide=c.width>c.height
        val out=mutableListOf<Button>()
        c.clear(paper)
        val top=insetTop+10
        val portraitSize=if(wide) 70 else 112
        c.fillRect(12,top,portraitSize,portraitSize,Palette.WHITE);c.drawRect(12,top,portraitSize,portraitSize,Palette.HOUSE_RIGHT)
        portraitBounds.set(14f,(top+2).toFloat(),(10+portraitSize).toFloat(),(top+portraitSize-2).toFloat())
        text.textSize=22;text.draw(c,"秘書 ひより / 市政手帳",140,top+2,green)
        text.textSize=18;text.draw(c,route?.let { if(a.completed) "${it.label}、完成です！" else "${it.label} 第${a.chapter+1}章" } ?: "次は、どんな街へ？",140,top+32,ink)
        text.textSize=18
        text.wrap(if(route==null) "この街と資金を引き継いで、三つの未来から一つを選びます。" else "${route.cityName}\n政策費 月$${a.monthlyCost(city)} / 資金 $${city.funds}",if(wide) c.width/2-24 else c.width-154)
            .take(3).forEachIndexed { i,s -> text.draw(c,s,if(wide) c.width/2 else 140,top+(if(wide) 6 else 78)+i*25,green) }
        fun button(action: Action,label: String,x: Int,y: Int,w: Int,h: Int=64,selected: Boolean=false) {
            val b=Button(action,x,y,w,h);out+=b
            c.fillRect(x,y,w,h,if(selected)green else Palette.WHITE);c.drawRect(x,y,w,h,green)
            text.textSize=21;
            text.wrap(label,w-20).forEachIndexed { i,line -> text.drawCentered(c,line,x+w/2,y+14+i*26,if(selected)Palette.WHITE else ink) }
        }
        val closeY=c.height-insetBottom-80
        button(Action.CLOSE,if(route==null) "あとで選ぶ" else if(a.completed) "完成した街を経営する" else "街づくりへ戻る",12,closeY,c.width-24)
        if(route==null) {
            var y=top+if(wide) 90 else 180
            for((i,r) in Campaign.Route.entries.withIndex()) {
                val action=listOf(Action.METROPOLIS,Action.GARDEN,Action.INDUSTRIAL)[i]
                val x=if(wide) 12+i*((c.width-48)/3+12) else 12
                val w=if(wide) (c.width-48)/3 else c.width-24
                val h=if(wide) closeY-y-12 else 174
                button(action,r.label,x,y,w,h)
                text.textSize=21
                text.wrap(r.summary,w-24).forEachIndexed { n,s -> text.draw(c,s,x+12,y+54+n*29,ink) }
                text.textSize=17;if(!wide) text.draw(c,"選択後はこの保存枠の街で進行します",24,y+143,green)
                if(!wide) y+=186
            }
            buttons=out;return
        }
        val tabY=top+if(wide) 88 else 174;val tabW=(c.width-48)/3
        for((i,pair) in listOf(Action.STORY to "物語",Action.GOALS to "目標・指標",Action.POLICIES to "政策").withIndex())
            button(pair.first,pair.second,12+i*(tabW+12),tabY,tabW,64,tab==i)
        val choosing=!a.chosen && !a.completed
        val choiceWidth=(c.width-36)/2
        val policies=if(choosing) a.current!!.options else emptyList()
        val details=policies.map { p -> "導入 $${p.cost} / 月$${p.upkeep(city.population)}\n${p.detail}\n${if(city.funds<p.cost) "資金不足。街で収入を増やせます。" else "月額は人口に応じて増えます。"}" }
        val labelHeights=policies.map { p -> text.textSize=21;text.wrap(p.label,choiceWidth-20).size*26 }
        val detailLines=details.map { d -> text.textSize=18;text.wrap(d,choiceWidth-16) }
        val choiceHeight=if(wide) minOf(100,(closeY-(tabY+80)-24)/2).coerceAtLeast(40) else policies.indices.maxOfOrNull { labelHeights[it]+detailLines[it].size*24+34 } ?: 0
        val choicesY=if(wide) tabY+80 else closeY-choiceHeight-12
        if(choosing) for((i,p) in policies.withIndex()) {
            val x=if(wide) c.width/2+8 else 12+i*(choiceWidth+12)
            val y=choicesY+if(wide) i*(choiceHeight+12) else 0
            button(if(i==0)Action.OPTION_0 else Action.OPTION_1,p.label,x,y,choiceWidth,choiceHeight)
            text.textSize=18
            if(wide) {
                if(choiceHeight>=80) { text.textSize=17;text.draw(c,"導入 $${p.cost} · 詳細を確認",x+8,y+choiceHeight-28,green) }
            }
            else detailLines[i].forEachIndexed { n,s -> text.draw(c,s,x+8,choicesY+labelHeights[i]+22+n*24,ink) }
        }
        val footerY=(if(choosing && !wide)choicesY else closeY)-76
        val bodyTop=tabY+80
        val content=when(tab) {
            1 -> buildList {
                add("今の章の条件（機能する施設だけを数えます）")
                addAll(a.objectives(city).map { it.text() })
                if(a.chapter==4)add("全条件を保つ連続黒字 ${a.stableMonths}/${Campaign.STABLE_MONTHS}か月")
                add("住民信頼 ${a.trust} / 共生 ${a.cohesion} / 企業信頼 ${a.businessTrust}")
                if(route==Campaign.Route.GARDEN)add("防災力 ${a.preparedness(city)}。備えで発生確率と被害を軽減。")
                if(route==Campaign.Route.INDUSTRIAL)add("企業の工業雇用需要 ${a.industrialDemand(city)} / 協定収入 月$${a.contractIncome(city)}")
            }
            2 -> buildList {
                add("政策費と災害などの対応費は『条例・施策・対応費』に含みます。")
                if(a.policies.isEmpty())add("政策はまだ選んでいません。")
                a.policies.forEach { add("${it.label}：月$${it.upkeep(city.population)}");add(it.detail);add("") }
            }
            else -> buildList {
                if(a.completed) {
                    add("完成認定：${route.cityName}")
                    add("${1900+a.completedMonth/12}年${a.completedMonth%12+1}月、人口${a.completedPopulation}人。")
                    add("市長、就任した日の小さな通りを覚えていますか。市民と交わした約束が、いま目の前の街になりました。")
                    add(when(route) {
                        Campaign.Route.METROPOLIS -> "いくつもの言葉が行き交う通りに、安心して暮らせる住まいと仕事があります。これからも、一人ひとりの声を聞いていきましょう。"
                        Campaign.Route.GARDEN -> "農地と緑の道を、きれいな電気と防災の備えが支えています。次の嵐にも、その先の世代にも向き合える街です。"
                        Campaign.Route.INDUSTRIAL -> "工場の灯りと住民の暮らしが、同じ協定の中で守られています。雇用と環境を両立する仕事は、これからも続きます。"
                    })
                    add("選んだ政策と費用、気象リスクは継続します。完成後もこの都市を経営できます。")
                } else {
                    add(a.current!!.title);add(a.current!!.story);add("")
                    add(if(a.chosen) "方針：${a.current!!.options[a.choiceAt(a.chapter)].label}。目標を満たして月を進めると、次の章へ進みます。" else "二案から政策を選んでください。資金が足りなければ街に戻り、先に経営を整えられます。")
                    add("都市の完成には最後の章の全条件と、半年間の黒字が必要です。人口だけでは完成しません。")
                }
                if(a.lastEvent.isNotEmpty()){add("");add("最近の報告：${a.lastEvent}")}
            }
        }
        text.textSize=21
        val bodyWidth=if(wide && choosing) c.width/2-24 else c.width-32
        val lines=content.flatMap { text.wrap(it,bodyWidth) }
        val capacity=((footerY-bodyTop)/30).coerceAtLeast(1)
        lastPage=((lines.size-1)/capacity).coerceAtLeast(0);page=page.coerceIn(0,lastPage)
        lines.drop(page*capacity).take(capacity).forEachIndexed { i,s -> text.draw(c,s,16,bodyTop+i*30,ink) }
        if(lastPage>0) {
            button(Action.PREVIOUS,"前へ",12,footerY,112)
            button(Action.NEXT,"次へ",bodyWidth-100,footerY,112)
            text.textSize=18;text.drawCentered(c,"${page+1}/${lastPage+1}",bodyWidth/2,footerY+18,green)
        }
        buttons=out
    }
}
