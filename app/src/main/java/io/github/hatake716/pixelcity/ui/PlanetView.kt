package io.github.hatake716.pixelcity.ui

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.widget.*
import androidx.core.view.ViewCompat
import io.github.hatake716.pixelcity.game.*
import io.github.hatake716.pixelcity.R
import java.util.Locale

/** Individual tile editing on a continuous planet, with explicit coordinates and brush sizes. */
@SuppressLint("ViewConstructor") // Planet is required; this screen is created programmatically.
class PlanetView(context: Context,val planet: Planet): LinearLayout(context) {
    var onEnterCity: ((Int,Int)->Unit)?=null
    var onReturnToCity: (()->Unit)?=null
    var onStateChanged: (()->Unit)?=null
    val surface=PlanetSurface(context,planet)
    var selectedTerrain=PlanetTerrain.PLAIN
        private set
    private val cream=0xfff7ecd7.toInt(); private val muted=0xffb8c9c3.toInt()
    private val ink=0xff162e35.toInt(); private val gold=0xffeed099.toInt()
    private val coordinate=button("",cream)
    private val details=label("",13f)
    private val terrainButton=button("地形：平野",PlanetTerrain.PLAIN.color)
    private val applyButton=button("地形を適用 $0",gold)
    private val enterButton=button("この平野へ",cream)
    private val undoButton=button("変更を戻す",0xffb9ccc3.toInt())
    private val brushButtons=ArrayList<Pair<Int,Button>>()
    private var paused=false
    init {
        orientation=VERTICAL; setBackgroundColor(ink)
        setPadding(dp(14),SystemBars.top(1)+dp(8),dp(14),SystemBars.bottom(1)+dp(8))
        ViewCompat.setOnApplyWindowInsetsListener(this) { _,insets ->
            val safe=SystemBars.safeInsets(insets,dp(8))
            setPadding(safe.left+dp(14),safe.top,safe.right+dp(14),safe.bottom+dp(8));insets
        }
        val top=LinearLayout(context).apply { gravity=Gravity.CENTER_VERTICAL }
        top.addView(label("青い惑星",25f,true),LayoutParams(0,dp(48),1f))
        top.addView(button("都市へ戻る",cream).apply { setOnClickListener { onReturnToCity?.invoke() } },LayoutParams(dp(116),dp(48)))
        addView(top)
        addView(label("512 × 512マス  ·  地形編集  ·  編集無料",12f),LayoutParams(-1,dp(28)))
        val stage=FrameLayout(context)
        stage.addView(surface,FrameLayout.LayoutParams(-1,-1))
        stage.addView(label("土地属性で描く惑星",12f).apply {
            setPadding(dp(8),0,dp(8),0); background=rounded(ink)
        },FrameLayout.LayoutParams(-2,dp(30),Gravity.TOP or Gravity.START))
        stage.addView(button("操作説明",0xffc0d8d2.toInt()).apply { textSize=12f;setOnClickListener { showHelp() } },FrameLayout.LayoutParams(dp(88),dp(48),Gravity.BOTTOM or Gravity.END))
        stage.addView(label("指で回転 / 二本指で拡大",12f).apply { setPadding(dp(8),0,0,0) },FrameLayout.LayoutParams(-2,dp(40),Gravity.BOTTOM or Gravity.START))
        addView(stage,LayoutParams(-1,0,1f))
        addRow(button("惑星全体",cream).apply { setOnClickListener { surface.select(planet.selectedIndex,true,false);onStateChanged?.invoke() } },
            button("選択地点を拡大",cream).apply { setOnClickListener { surface.select(planet.selectedIndex,true,true);onStateChanged?.invoke() } })
        val seabed=button(if(planet.seabedVisible) "海面を表示" else "海底地形を見る",cream)
        seabed.setOnClickListener {
            surface.setSeabed(!planet.seabedVisible)
            seabed.text=if(planet.seabedVisible) "海面を表示" else "海底地形を見る"
            onStateChanged?.invoke()
        }
        addRow(seabed,button("初期の街へ移動",cream).apply {
            setOnClickListener { selectTile(planet.city.originX,planet.city.originY,true) }
        })
        coordinate.setOnClickListener { chooseCoordinate() }
        addView(coordinate,LayoutParams(-1,dp(48)).apply { topMargin=dp(6) })
        addView(details,LayoutParams(-1,dp(48)))
        val brushes=LinearLayout(context)
        for(size in listOf(1,4,16,32)) {
            val b=button(if(size==1) "1マス" else "$size×$size",cream).apply {
                textSize=14f;setOnClickListener { planet.brushSize=size;surface.updateBrush(size);updateLabels();onStateChanged?.invoke() }
            }
            brushButtons.add(size to b)
            brushes.addView(b,LayoutParams(0,dp(48),1f).apply { setMargins(dp(2),0,dp(2),0) })
        }
        addView(brushes)
        addRow(terrainButton,applyButton)
        addRow(undoButton,enterButton)
        terrainButton.setOnClickListener { chooseTerrain() }
        applyButton.setOnClickListener { applyTerrain() }
        enterButton.setOnClickListener { if(planet.canBuild(planet.selectedX,planet.selectedY)) onEnterCity?.invoke(planet.selectedX,planet.selectedY) }
        undoButton.setOnClickListener {
            if(planet.undo()) { surface.refresh(planet.selectedIndex);updateLabels();onStateChanged?.invoke() }
        }
        surface.onTileSelected={ index -> selectTile(index%Planet.SIZE,index/Planet.SIZE) }
        surface.onCameraChanged={ surface.updateDetail();onStateChanged?.invoke() }
        updateLabels();surface.select(planet.selectedIndex,false);surface.updateBrush(planet.brushSize)
    }
    private fun addRow(a: Button,b: Button) {
        val row=LinearLayout(context)
        row.addView(a,LayoutParams(0,dp(50),1f).apply { marginEnd=dp(6) })
        row.addView(b,LayoutParams(0,dp(50),1f))
        addView(row,LayoutParams(-1,-2).apply { topMargin=dp(6) })
    }
    fun selectTile(x: Int,y: Int,focus: Boolean=false) {
        planet.select(x,y); surface.select(planet.selectedIndex,focus,focus)
        updateLabels();onStateChanged?.invoke()
    }
    fun chooseTerrain(terrain: PlanetTerrain) { selectedTerrain=terrain;updateLabels() }
    fun applyTerrain() {
        val changed=planet.terraform(planet.selectedX,planet.selectedY,selectedTerrain)
        if(changed>0) {
            surface.refresh(planet.selectedIndex);updateLabels();onStateChanged?.invoke()
            Toast.makeText(context,"${changed}マスを${selectedTerrain.label}に変更しました。費用 $0",Toast.LENGTH_SHORT).show()
        }
    }
    private fun chooseTerrain() {
        surface.stopMotion()
        val column=LinearLayout(context).apply { orientation=VERTICAL;setPadding(dp(12),dp(4),dp(12),dp(12)) }
        val dialog=AlertDialog.Builder(context).setTitle("配置する地形")
            .setMessage("1マスまたは選択した筆の大きさで適用します。建設できるのは平野のみです。")
            .setView(ScrollView(context).apply { addView(column) }).setNegativeButton("取消",null).create()
        for(terrain in PlanetTerrain.entries) {
            val b=button(terrain.label,terrain.color).apply {
                gravity=Gravity.CENTER_VERTICAL or Gravity.START;setPadding(dp(18),0,dp(18),0)
                contentDescription="${terrain.label}。${terrain.description}"
                setOnClickListener { chooseTerrain(terrain);dialog.dismiss() }
            }
            column.addView(b,LayoutParams(-1,dp(52)).apply { topMargin=dp(5) })
        }
        dialog.show()
    }
    private fun chooseCoordinate() {
        val column=LinearLayout(context).apply { orientation=VERTICAL;setPadding(dp(20),dp(8),dp(20),0) }
        fun input(hintText: String,value: Int)=EditText(context).apply {
            hint=hintText;inputType=InputType.TYPE_CLASS_NUMBER;setSingleLine();setText(String.format(Locale.ROOT,"%d",value));textSize=18f
            selectAll();column.addView(this,LayoutParams(-1,dp(64)))
        }
        val x=input("横 X（0〜511）",planet.selectedX);val y=input("縦 Y（0〜511）",planet.selectedY)
        val dialog=AlertDialog.Builder(context).setTitle("編集するマスの座標")
            .setMessage("上段が横 X、下段が縦 Y。どちらも0〜511です。")
            .setView(column).setPositiveButton("地点へ移動",null).setNegativeButton("取消",null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener confirm@ {
                val xx=x.text.toString().toIntOrNull();val yy=y.text.toString().toIntOrNull()
                if(xx==null || xx !in 0 until Planet.SIZE) { x.error="0〜511を入力してください";return@confirm }
                if(yy==null || yy !in 0 until Planet.SIZE) { y.error="0〜511を入力してください";return@confirm }
                selectTile(xx,yy,true);dialog.dismiss()
            }
        }
        dialog.show()
    }
    private fun updateLabels() {
        val t=planet.city.tiles[planet.selectedIndex]
        coordinate.text=context.getString(R.string.planet_coordinate,planet.selectedX,planet.selectedY,t.biome.label)
        coordinate.contentDescription="選択座標。横${planet.selectedX}、縦${planet.selectedY}。${t.biome.label}。押して座標を指定。"
        details.text=when {
            t.storedBuilding!=null -> "建築を保管中。平野に戻すと復元します。\n筆が記念建築に触れた場合は敷地全体を変更します。"
            planet.canBuild(planet.selectedX,planet.selectedY) -> "都市建設可能。資金・道路・市政は惑星全体で共通です。"
            else -> "都市建設不可。平野に変更すると建設できます。\n${t.biome.description}"
        }
        terrainButton.text=context.getString(R.string.planet_terrain,selectedTerrain.label);terrainButton.background=rounded(selectedTerrain.color)
        for((size,b) in brushButtons) { b.background=rounded(if(size==planet.brushSize) gold else cream);b.isSelected=size==planet.brushSize }
        enterButton.isEnabled=planet.canBuild(planet.selectedX,planet.selectedY)
        enterButton.alpha=if(enterButton.isEnabled) 1f else 0.45f
        enterButton.text=if(enterButton.isEnabled) "この平野へ" else "建設不可"
        undoButton.isEnabled=planet.canUndo;undoButton.alpha=if(planet.canUndo) 1f else 0.45f
    }
    fun showHelp() {
        AlertDialog.Builder(context).setTitle("惑星の小さな創造主")
            .setMessage("惑星全体は512×512マス。1マスごとに11種類の3D地形を配置できます。\n\n球体を指で回転し、二本指で拡大します。球面を押してマスを選ぶか、座標欄からX・Yを指定してください。「選択地点を拡大」で細かな地形を確認できます。\n\n地形と筆の大きさを選び「地形を適用 $0」で編集。「変更を戻す」で直前の変更を取り消せます。\n\n初期の街は地球の自然な平野から始まります。中央を四角く整地することはありません。ほかの土地も平野に変えれば、同じ都市を広げられます。地形を変えた場所の建築は保管し、平野へ戻すと復元します。\n\n表面は現在の土地属性に合わせて1マスずつ描画しています。拡大すると森林の木や砂丘、火口などの立体地形を確認できます。初期の大陸配置と高低差には地球の資料を使用しています。起伏は見やすいよう65倍に強調しています。「海底地形を見る」で海嶺や海溝も観察できます。地形区分はゲーム用の分類です。\n\n初期地形の分類資料：NASA Earth Observatory / Reto Stöckli。標高・海底：GEBCO / BODC、NASA Earth Observatory / Jesse Allen。\n\n編集中は時間停止、費用も不要です。都市へ戻ったら「時間」で市政を再開できます。火山などは観賞用です。")
            .setPositiveButton("閉じる",null).show()
    }
    fun pauseRenderer() { if(!paused) { paused=true;surface.onPause() } }
    fun resumeRenderer() { if(paused) { paused=false;surface.onResume() } }
    private fun dp(value: Int)=(value*resources.displayMetrics.density+0.5f).toInt()
    private fun rounded(color: Int)=GradientDrawable().apply { setColor(color);cornerRadius=dp(12).toFloat() }
    private fun label(value: String,size: Float,bold: Boolean=false)=TextView(context).apply {
        text=value;textSize=size;setTextColor(if(bold) cream else muted);gravity=Gravity.CENTER_VERTICAL
        if(bold) setTypeface(typeface,Typeface.BOLD)
    }
    private fun button(value: String,color: Int)=Button(context).apply {
        text=value;textSize=15f;isAllCaps=false;setTextColor(ink);setTypeface(typeface,Typeface.BOLD)
        minWidth=0;minimumWidth=0;minHeight=0;minimumHeight=0
        setPadding(dp(6),0,dp(6),0);background=rounded(color);stateListAnimator=null
    }
}
