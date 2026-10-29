package io.github.hatake716.pixelcity.game

import kotlin.math.abs
import kotlin.random.Random

/** Post-inauguration stories. All construction goals inspect the player's actual city. */
class Campaign {
    enum class Route(val id: Int, val label: String, val cityName: String, val summary: String, val population: Int) {
        METROPOLIS(1, "大都市", "多文化共生の大都市", "3万人の街。住宅・治安・多文化共生を支える。", 30_000),
        GARDEN(2, "田園都市", "自然と生きる田園都市", "5千人の緑の街。環境・財政・防災を両立する。", 5_000),
        INDUSTRIAL(3, "工業都市", "住民と歩む産業都市", "1万2千人、工業雇用8千人。企業と住民の協定を結ぶ。", 12_000),
    }
    data class Policy(val label: String, val cost: Int, val monthly: Int, val perThousand: Int,
        val detail: String, val civic: Int = 0, val inclusion: Int = 0, val clean: Int = 0,
        val resilience: Int = 0, val safety: Int = 0, val business: Int = 0) {
        fun upkeep(pop: Int) = monthly + pop * perThousand / 1000
    }
    data class Chapter(val title: String, val story: String, val options: List<Policy>)
    data class Objective(val label: String, val value: Int, val target: Int, val maximum: Boolean = false) {
        val met get() = if (maximum) value <= target else value >= target
        fun text() = "${if (met) "✓" else "・"}$label $value${if (maximum) " ≤ " else " / "}$target"
    }
    companion object {
        const val STABLE_MONTHS = 6
        val STORIES = mapOf(
            Route.METROPOLIS to listOf(
                Chapter("届き始めた苦情", "市長、人口が増え、ごみ出しや夜の騒音の相談が届いています。ひよりが住民の声を集めました。窓口を集約するか、地区に出向くか。相談に使うお金も予算に残ります。", listOf(
                    Policy("市民相談窓口", 300, 8, 2, "苦情を記録・公開。市民の信頼 +8。", civic = 8),
                    Policy("地区の相談員", 500, 12, 3, "訪問と仲裁。信頼 +12・共生 +4。", civic = 12, inclusion = 4))),
                Chapter("安心して帰れる通り", "商店街で窃盗が続き、住民は安全を求めています。犯罪への対応と、若者の孤立・失業への支援を一緒に考えましょう。出身や国籍を犯罪の原因とせず、被害と地域の状況を調べます。", listOf(
                    Policy("街灯と地域巡回", 900, 20, 3, "全市の犯罪指標 -8。信頼 +3。", safety = 8, civic = 3),
                    Policy("就労支援と見守り", 1200, 18, 4, "犯罪指標 -5、信頼 +7・共生 +5。", safety = 5, civic = 7, inclusion = 5))),
                Chapter("いくつもの言葉で暮らす", "海外から来た家族と、長く暮らす住民の間で、学校の連絡や生活ルールを巡る行き違いが起きています。通訳と対話の場を整え、双方が同じ行政サービスへ届く街をつくりましょう。", listOf(
                    Policy("多言語の生活センター", 1600, 25, 4, "通訳と相談。共生 +28・信頼 +5。", inclusion = 28, civic = 5),
                    Policy("学校通訳と交流教室", 2100, 20, 5, "学校と地域をつなぐ。共生 +32・信頼 +3。", inclusion = 32, civic = 3))),
                Chapter("住まいを失わない成長", "家賃と通勤の負担が増え、古くからの住民にも新住民にも住まいが足りません。住宅を増やし、電気と水、公共交通を届けることが必要です。支援する重点を決めてください。", listOf(
                    Policy("家賃相談と住宅支援", 2500, 30, 4, "信頼 +10・共生 +8。住宅需要 +5。", civic = 10, inclusion = 8),
                    Policy("交通沿線の生活支援", 2000, 25, 3, "信頼 +7・共生 +6。住宅需要 +3。", civic = 7, inclusion = 6))),
                Chapter("三万人の市民と決算", "高いビルが増えても、取り残される地区があれば完成とは呼べません。市民が予算に関わる仕組みを残し、生活・治安・共生を保って半年間黒字を続けましょう。", listOf(
                    Policy("公開予算と市民監査", 3000, 25, 2, "支出を見える形に。信頼 +10。", civic = 10),
                    Policy("地区ごとの参加型予算", 4000, 35, 3, "新旧住民が共に決定。信頼 +14・共生 +8。", civic = 14, inclusion = 8)))
            ),
            Route.GARDEN to listOf(
                Chapter("残したい風景", "畑と緑を残して暮らせる街にしたい、と住民から手紙が届きました。緑は空き地の数ではなく、実際に整備した農地と公園で測ります。住宅や仕事も一緒に育てましょう。", listOf(
                    Policy("里山と農地の保全協定", 350, 10, 2, "環境への信頼 +10・排出量 -5%。", civic = 10, clean = 5),
                    Policy("市民の森と共同菜園", 550, 15, 2, "環境への信頼 +14・排出量 -3%。", civic = 14, clean = 3))),
                Chapter("きれいな電気の値段", "発電所の煙を減らしてほしいという声と、費用を心配する声があります。千人で風力が使えます。新電源をつないでから火力を減らし、停電と赤字を避けましょう。", listOf(
                    Policy("省エネ改修の支援", 900, 15, 3, "排出量 -15%、防災 +5。", clean = 15, resilience = 5),
                    Policy("地域電力の共同運営", 1300, 20, 3, "排出量 -10%、信頼 +8。", clean = 10, civic = 8))),
                Chapter("嵐が来る前に", "気象台から強風と雷雨への備えを求められました。竜巻・嵐・火災は、このシナリオでは災害設定にかかわらず起こり得ます。消防の届く範囲と備蓄・避難体制が被害を減らします。", listOf(
                    Policy("避難所と耐風改修", 1800, 25, 4, "防災 +50。竜巻・嵐・火災の被害を軽減。", resilience = 50),
                    Policy("防災林と地域の備蓄", 1500, 20, 3, "防災 +40、排出量 -8%、信頼 +5。", resilience = 40, clean = 8, civic = 5))),
                Chapter("自然を守る仕事", "環境対策費だけが増えると、市政は続きません。農家と商店から、地元の仕事を増やす提案が届きました。清潔な住宅地と仕事、ゴミ処理を両立し、自然への投資を支える税収を育てましょう。", listOf(
                    Policy("地産地消の取引支援", 1600, 18, 2, "住民との信頼 +12・防災 +5。", civic = 12, resilience = 5),
                    Policy("環境学習と農業体験", 2000, 22, 3, "信頼 +16・排出量 -5%。", civic = 16, clean = 5))),
                Chapter("次の世代へ渡す田園", "緑の帯が住宅と畑をつなぎました。環境保全も避難訓練も、毎年続ける仕組みにしましょう。緑地率と再生可能電力、防災力を保ち、半年間の黒字で田園都市を完成させます。", listOf(
                    Policy("自然保全の市民基金", 2200, 25, 3, "信頼 +10・防災 +10。", civic = 10, resilience = 10),
                    Policy("流域の防災協定", 2800, 30, 3, "防災 +20・排出量 -5%。", resilience = 20, clean = 5)))
            ),
            Route.INDUSTRIAL to listOf(
                Chapter("大企業からの提案", "架空企業『暁モビリティ』が工場の候補地を探しています。雇用だけでなく、電気・水・公害基準を契約にしましょう。協定負担金は実際の工業雇用と安定供給がそろって初めて入ります。", listOf(
                    Policy("基幹企業との雇用協定", 700, 12, 2, "企業信頼 +18。工業雇用に応じた協定収入。", business = 18),
                    Policy("複数企業の連携団地", 1000, 18, 2, "企業信頼 +12・住民信頼 +8。", business = 12, civic = 8))),
                Chapter("止められない生産ライン", "企業から増産の相談です。断水や停電があれば約束した生産は止まります。住宅と離した工業区画、幹線道路、給水設備を整えましょう。企業への支援にも継続費用がかかります。", listOf(
                    Policy("共同物流と職業訓練", 1500, 25, 3, "企業信頼 +14・住民信頼 +5。", business = 14, civic = 5),
                    Policy("安定供給の設備協定", 1800, 30, 3, "企業信頼 +20・防災 +10。", business = 20, resilience = 10))),
                Chapter("煙の向こうの暮らし", "環境団体『川の会』が、住宅地の煙と排水を問題にしています。測定値を公開し、緩衝緑地と下水処理を整えましょう。企業にも改修を求めます。約束だけでは信頼は戻りません。", listOf(
                    Policy("排出規制と共同改修", 2400, 30, 4, "排出量 -35%、住民信頼 +10。", clean = 35, civic = 10),
                    Policy("第三者測定と設備更新", 3000, 35, 4, "排出量 -45%、住民信頼 +6。", clean = 45, civic = 6))),
                Chapter("工場ゲートの向こう側", "反対派の一部による器物損壊が発生し、団体代表も暴力を非難しています。被害は行為ごとに公平に捜査し、平和的な抗議と分けて扱います。公害の改善と対話を続け、対立の連鎖を止めましょう。", listOf(
                    Policy("公平な捜査と公開協議", 2200, 25, 3, "犯罪指標 -8・住民信頼 +22。", safety = 8, civic = 22),
                    Policy("第三者仲裁と地域見守り", 2800, 30, 3, "犯罪指標 -5・住民信頼 +28。", safety = 5, civic = 28))),
                Chapter("産業と暮らしの共同協定", "企業と住民が同じ机に着きました。雇用八千人の産業基盤、健康な住宅地、公開された環境基準を残しましょう。両者の信頼と半年間の黒字を保てば、工業都市の完成です。", listOf(
                    Policy("雇用と環境の三者協定", 3500, 30, 3, "住民信頼 +12・企業信頼 +12。", civic = 12, business = 12),
                    Policy("地域還元と技術転換", 4200, 35, 4, "住民信頼 +16・排出量 -10%、企業信頼 +8。", civic = 16, clean = 10, business = 8)))
            )
        )
    }

    var offered = false; private set
    var route: Route? = null; private set
    var chapter = 0; private set
    var needsBriefing = false; private set
    var completed = false; private set
    var completedMonth = -1; private set
    var completedPopulation = 0; private set
    var stableMonths = 0; private set
    var trust = 45; private set
    var cohesion = 40; private set
    var businessTrust = 40; private set
    var startMonth = 0; private set
    var lastEvent = ""; private set
    private var lastMonth = -1
    private val choices = IntArray(5) { -1 }
    val visible get() = offered
    val current get() = route?.let { STORIES.getValue(it)[chapter] }
    val chosen get() = choices[chapter] >= 0
    fun choiceAt(index: Int) = choices.getOrElse(index) { -1 }
    val policies: List<Policy> get() = route?.let { r -> choices.toList().mapIndexedNotNull { i, c -> STORIES.getValue(r)[i].options.getOrNull(c) } }.orEmpty()
    val industrialOrders get() = if(route == Route.INDUSTRIAL && choices[0] >= 0) intArrayOf(1100,2700,5200,7800,10400)[chapter] else 0
    fun industrialDemand(city: City) = if(industrialOrders == 0) 0 else maxOf(industrialOrders,city.population/2)
    val emissionReduction get() = policies.sumOf { it.clean }.coerceAtMost(70)
    val crimeReduction get() = policies.sumOf { it.safety }
    val housingSupport get() = if (route == Route.METROPOLIS && choices[3] >= 0) if (choices[3] == 0) 5 else 3 else 0
    fun monthlyCost(city: City) = policies.sumOf { it.upkeep(city.population) }
    fun offer() { if (!offered) { offered = true; needsBriefing = true } }
    fun select(value: Route, city: City): Boolean {
        if (!offered || route != null) return false
        route = value; startMonth = city.month; lastMonth = city.month; needsBriefing = true
        city.style = when(value) { Route.METROPOLIS -> City.Style.PRIME; Route.GARDEN -> City.Style.RURAL; Route.INDUSTRIAL -> City.Style.GRITTY }
        invalidateMetrics()
        return true
    }
    fun choose(index: Int, city: City): Boolean {
        if (completed || chosen) return false
        val policy = current?.options?.getOrNull(index) ?: return false
        if (city.gameOver || city.funds < policy.cost) return false
        city.funds -= policy.cost; choices[chapter] = index
        city.recomputeDerivedState(); city.refreshBudgetForecast()
        return true
    }
    fun acknowledge() { needsBriefing = false }

    data class Metrics(val services: Int, val crime: Int, val pollution: Int, val education: Int,
        val green: Int, val renewable: Int, val fireCoverage: Int, val industryServices: Int,
        val counts: Map<TileKind, Int>, val highHomes: Int, val highCommerce: Int, val highIndustry: Int) {
        fun count(kind: TileKind) = counts[kind] ?: 0
    }
    private var cached: Metrics? = null
    fun invalidateMetrics() { cached = null }
    fun metrics(city: City): Metrics = cached ?: run {
        val homes = city.tiles.withIndex().filter { it.value.kind == TileKind.ZONE_R && it.value.stage > 0 }
        val industries = city.tiles.filter { it.kind == TileKind.ZONE_I && it.stage > 0 }
        val functioning = city.tiles.filter { it.connected && (it.kind.isTransport || it.powered) }
        val fires = city.tiles.withIndex().filter { it.value.kind == TileKind.FIRE && it.value.connected && it.value.powered }
        fun percent(part: Int, all: Int) = if (all == 0) 0 else part * 100 / all
        val green = functioning.count { it.kind == TileKind.PARK || it.kind == TileKind.FARM }
        val developed = city.tiles.count { it.kind.isBuilding }
        val generation = city.tiles.filter { it.kind.isPowerPlant && it.connected }
        Metrics(
            percent(homes.count { it.value.connected && it.value.powered && it.value.watered }, homes.size),
            if (homes.isEmpty()) 100 else homes.sumOf { it.value.crime } / homes.size,
            if (homes.isEmpty()) 100 else homes.sumOf { it.value.pollution } / homes.size,
            if (homes.isEmpty()) 0 else homes.sumOf { it.value.education } / homes.size,
            percent(green, developed),
            percent(generation.filter { it.kind != TileKind.POWER_COAL }.sumOf { BuildCost.powerOutput(it.kind) }, generation.sumOf { BuildCost.powerOutput(it.kind) }),
            percent(homes.count { home -> fires.any { abs(it.index % city.width - home.index % city.width) + abs(it.index / city.width - home.index / city.width) <= 8 } }, homes.size),
            percent(industries.count { it.connected && it.powered && it.watered }, industries.size),
            functioning.groupingBy { it.kind }.eachCount(),
            homes.count { it.value.stage == 3 && it.value.connected && it.value.powered && it.value.watered },
            functioning.count { it.kind == TileKind.ZONE_C && it.stage == 3 && it.watered },
            functioning.count { it.kind == TileKind.ZONE_I && it.stage == 3 && it.watered }
        ).also { cached = it }
    }
    fun complaints(city: City): Int {
        val m = metrics(city)
        return ((100 - m.services) / 2 + m.crime / 2 + m.pollution + city.unemployment / 3 +
            city.congestionRate / 4 + (city.taxRate - 9).coerceAtLeast(0) * 3).coerceIn(0, 100)
    }
    fun preparedness(city: City) = (policies.sumOf { it.resilience } + metrics(city).fireCoverage / 2).coerceIn(0, 100)
    fun contractIncome(city: City): Int = if (route != Route.INDUSTRIAL || choices[0] < 0 || city.industrialJobs < 800 ||
        metrics(city).industryServices < 95 || city.powerRatio < 1f || city.waterRatio < 1f) 0
        else city.industrialJobs * (if (choices[0] == 0) 3 else 2) / 100

    fun objectives(city: City): List<Objective> {
        val r = route ?: return emptyList()
        val m = metrics(city)
        val out = mutableListOf<Objective>()
        fun min(label: String, value: Int, target: Int) { out += Objective(label, value, target) }
        fun max(label: String, value: Int, target: Int) { out += Objective(label, value, target, true) }
        fun count(label: String, kind: TileKind, target: Int) = min(label, m.count(kind), target)
        val populations = when(r) {
            Route.METROPOLIS -> intArrayOf(1000,5000,12000,20000,30000)
            Route.GARDEN -> intArrayOf(1000,2000,3000,4000,5000)
            Route.INDUSTRIAL -> intArrayOf(1500,3500,6000,9000,12000)
        }
        min("人口", city.population, populations[chapter])
        min("住宅の道路・電気・水道 %", m.services, if (chapter == 0) 80 else 95)
        max("失業 %", city.unemployment, 15)
        when(r) {
            Route.METROPOLIS -> {
                max("住民の苦情", complaints(city), if(chapter < 3) 45 else 30)
                if(chapter >= 1) { max("住宅地の犯罪指標", m.crime, 25); count("警察",TileKind.POLICE,if(chapter == 4)8 else 2); count("学校",TileKind.SCHOOL,if(chapter == 4)10 else 2) }
                if(chapter >= 2) min("多文化共生",cohesion,70)
                if(chapter >= 3) { min("市民の信頼",trust,65); count("バス停",TileKind.BUS_STOP,if(chapter==4)12 else 8); max("渋滞 %",city.congestionRate,40) }
                if(chapter == 4) { min("高層住宅",m.highHomes,300); min("高層商業",m.highCommerce,100); count("病院",TileKind.HOSPITAL,4) }
            }
            Route.GARDEN -> {
                min("整備した緑地率 %",m.green,if(chapter == 4)30 else 20)
                max("住宅地の公害",m.pollution,if(chapter >= 3)10 else 15)
                if(chapter >= 1) min("再生可能電力 %",m.renewable,if(chapter==4)100 else 50)
                if(chapter >= 2) { min("防災力",preparedness(city),70); count("消防署",TileKind.FIRE,3) }
                if(chapter >= 3) min("環境施策への信頼",trust,70)
                if(chapter == 4) { count("農地",TileKind.FARM,80); count("公園",TileKind.PARK,80) }
            }
            Route.INDUSTRIAL -> {
                min("工業雇用",city.industrialJobs,intArrayOf(800,2000,4000,6000,8000)[chapter])
                min("工場の道路・電気・水道 %",m.industryServices,95)
                if(chapter >= 1) { count("大通り",TileKind.AVENUE,24); min("企業の信頼",businessTrust,65) }
                if(chapter >= 2) { max("住宅地の公害",m.pollution,15); count("下水処理場",TileKind.SEWAGE_PLANT,1); count("公園",TileKind.PARK,40) }
                if(chapter >= 3) { min("住民・環境団体の信頼",trust,70); max("住宅地の犯罪指標",m.crime,25) }
                if(chapter == 4) {
                    min("高成長の工場",m.highIndustry,100); count("病院",TileKind.HOSPITAL,3)
                    min("工業が占める雇用 %",if(city.jobs==0)0 else city.industrialJobs*100/city.jobs,60)
                }
            }
        }
        if(chapter == 4) {
            max("ゴミ未処理量",city.garbageBacklog,0)
            min("毎月のゴミ処理余力",city.garbageCapacity-city.garbageProduced,0)
            min("財政の余裕",city.funds,0)
            min("月の黒字",city.lastIncome-city.lastUpkeep,1)
        }
        return out
    }

    fun onMonthPassed(city: City) {
        if (route == null || city.month <= lastMonth) return
        lastMonth = city.month
        invalidateMetrics()
        val m = metrics(city)
        fun approach(value: Int, target: Int) = (value + (target - value).coerceIn(-4, 4)).coerceIn(0,100)
        trust = approach(trust, (70 - complaints(city) + policies.sumOf { it.civic }).coerceIn(0,100))
        cohesion = approach(cohesion, (42 + policies.sumOf { it.inclusion } + m.education / 4 - (100-m.services)/3 - city.unemployment/4).coerceIn(0,100))
        businessTrust = approach(businessTrust, (50 + policies.sumOf { it.business } - (100-m.industryServices) - (city.taxRate-9).coerceAtLeast(0)*5).coerceIn(0,100))
        if (route == Route.INDUSTRIAL && trust < 35 && city.month-startMonth >= 6 && city.month % 6 == 0) {
            val loss = 200 + city.industrialJobs / 20
            emergencyExpense(city, loss)
            lastEvent = "${date(city)}：反対派の一部による設備損壊。修繕費 $$loss。捜査と公害改善・対話を継続。"
            city.lastDisaster = "設備損壊の修繕費 $$loss"
        }
        if (completed || needsBriefing || !chosen || city.gameOver) return
        if (objectives(city).all { it.met }) {
            if(chapter < 4) {
                chapter++; stableMonths = 0; needsBriefing = true
                if(route == Route.INDUSTRIAL && chapter == 3) {
                    emergencyExpense(city, 350)
                    lastEvent = "${date(city)}：測定装置の損壊、修繕費 $350。対話と公平な捜査を求める住民の声。"
                    city.lastDisaster = "測定装置の損壊。修繕費 $350"
                }
            }
            else {
                stableMonths++
                if(stableMonths >= STABLE_MONTHS) {
                    completed = true; completedMonth = city.month; completedPopulation = city.population; needsBriefing = true
                }
            }
        } else stableMonths = 0
    }

    /** Rural weather remains part of the route even with the sandbox disaster switch off. */
    fun weather(city: City, random: Random) {
        if(route != Route.GARDEN || city.month-startMonth < 6) return
        val protection = preparedness(city)
        if(random.nextInt(100) >= 14 - protection / 10) return
        val zones = city.tiles.indices.filter { city.tiles[it].kind.isZone && city.tiles[it].stage > 0 }
        if(zones.isEmpty()) return
        val name = listOf("竜巻", "暴風雨", "落雷による火災")[random.nextInt(3)]
        val damage = (7 - protection / 16).coerceAtLeast(1).coerceAtMost(zones.size)
        val center = zones[random.nextInt(zones.size)]
        val affected = zones.sortedBy { abs(it%city.width-center%city.width)+abs(it/city.width-center/city.width) }.take(damage)
        for(i in affected) city.tiles[i].stage = (city.tiles[i].stage - if(protection < 40)2 else 1).coerceAtLeast(0)
        val expense = damage * (100 - protection/2)
        emergencyExpense(city, expense)
        lastEvent = "${date(city)}：$name。${damage}区画が被災、応急費 $$expense。防災力 $protection。"
        city.lastDisaster = lastEvent
        invalidateMetrics()
    }

    /** Stable route IDs and versioned state; choices and storm timing survive reloads. */
    fun saveState() = intArrayOf(1,if(offered)1 else 0,route?.id ?: 0,chapter,if(needsBriefing)1 else 0,
        if(completed)1 else 0,completedMonth,completedPopulation,stableMonths,trust,cohesion,businessTrust,startMonth,lastMonth,*choices)
    fun restore(state: IntArray, event: String = "") {
        require(state.size == 19 && state[0] == 1) { "Invalid campaign state" }
        require(state[2] in 0..3 && state[3] in 0..4 && (14..18).all { state[it] in -1..1 }) { "Invalid campaign route/choice" }
        offered=state[1]==1;route=Route.entries.firstOrNull { it.id==state[2] };chapter=state[3];needsBriefing=state[4]==1
        completed=state[5]==1;completedMonth=state[6];completedPopulation=state[7].coerceAtLeast(0);stableMonths=state[8].coerceIn(0,6)
        trust=state[9].coerceIn(0,100);cohesion=state[10].coerceIn(0,100);businessTrust=state[11].coerceIn(0,100)
        startMonth=state[12];lastMonth=state[13];for(i in choices.indices)choices[i]=state[14+i]
        require(route != null || choices.all { it == -1 }) { "Policies without a campaign" }
        require(route != null || chapter == 0 && stableMonths == 0 && !completed) { "Progress without a campaign" }
        require(offered || route == null && !needsBriefing) { "Unannounced campaign" }
        require(route == null || offered && (0 until chapter).all { choices[it] >= 0 }) { "Missing campaign decisions" }
        require((chapter+1..4).all { choices[it] == -1 }) { "Future campaign policy" }
        require(!completed || route != null && choices.all { it >= 0 }) { "Incomplete campaign certificate" }
        lastEvent=event.take(240);invalidateMetrics()
    }
    private fun date(city: City) = "${1900+city.month/12}年${city.month%12+1}月"
    private fun emergencyExpense(city: City, amount: Int) {
        city.funds -= amount
        city.lastUpkeep += amount
        city.lastSpending = city.lastSpending.copy(ordinances = city.lastSpending.ordinances + amount)
    }
}
