package io.github.hatake716.pixelcity.ui

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.RippleDrawable
import android.text.InputType
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.WindowManager
import android.widget.*
import io.github.hatake716.pixelcity.game.*
import io.github.hatake716.pixelcity.R

/** A tool tray over the same city surface. No second map, renderer, or simulation. */
class GlobeTerrainEditor(private val context: Context,private val planet: Planet,
                         private val surface: PlanetSurface,private val changed: ()->Unit) {
    private val editorContext=ContextThemeWrapper(context,R.style.Theme_PixelCity_Terrain)
    private var dialog: Dialog?=null
    val isEditing get()=dialog?.isShowing==true
    var terrain=PlanetTerrain.PLAIN
        private set
    private var coordinate: Button?=null
    private var terrainButton: Button?=null
    private var brushButton: Button?=null
    private fun dp(v: Int)=(v*context.resources.displayMetrics.density).toInt()
    private fun button(text: String,primary: Boolean=false,action: ()->Unit)=Button(editorContext).apply {
        this.text=text;textSize=16f;isAllCaps=false;minHeight=dp(52)
        typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
        setPadding(dp(10),dp(12),dp(10),dp(12))
        setTextColor(if(primary) 0xfffff8eb.toInt() else Color.rgb(25,48,52))
        backgroundTintList=null
        background=RippleDrawable(ColorStateList.valueOf(if(primary) 0x28ffffff else 0x20193034),
            ModernMenu.shape(editorContext,if(primary) 0xff245e50.toInt() else 0xfffff8eb.toInt(),0xffb5a58d.toInt()),null)
        setOnClickListener { action() }
    }
    fun show() {
        if(isEditing) return
        val d=Dialog(editorContext);dialog=d
        val column=LinearLayout(editorContext).apply { orientation=LinearLayout.VERTICAL;setPadding(dp(10),dp(8),dp(10),dp(12));setBackgroundColor(0xfff7ecd7.toInt()) }
        fun row(vararg buttons: Button) {
            column.addView(LinearLayout(editorContext).apply {
                for(b in buttons) addView(b,LinearLayout.LayoutParams(0,-2,1f).apply { setMargins(dp(3),dp(3),dp(3),dp(3)) })
            })
        }
        row(button("地形編集・無料") { AlertDialog.Builder(editorContext).setTitle("同じ惑星に、街を育てる")
            .setMessage("上の球面を押して土地を選び、地形と筆を指定して適用します。指で回転、二本指で拡大できます。\n\n建設は平野のみ。建築のある土地を変えると建築を保管し、平野へ戻すと復元します。市政の時間は停止し、地形変更には資金を使いません。\n\n完了後は同じ球面で都市の建設を続けられます。")
            .setPositiveButton("閉じる",null).show() },button("完了") { d.dismiss() })
        coordinate=button("") { chooseCoordinate() };row(coordinate!!)
        terrainButton=button("") {
            AlertDialog.Builder(editorContext).setTitle("土地属性").setItems(PlanetTerrain.entries.map { it.label }.toTypedArray()) { _,i -> terrain=PlanetTerrain.entries[i];updateSelection() }.show()
        }
        brushButton=button("") {
            AlertDialog.Builder(editorContext).setTitle("筆の大きさ").setItems(arrayOf("1マス","4×4","16×16","32×32")) { _,i -> planet.brushSize=intArrayOf(1,4,16,32)[i];surface.updateBrush(planet.brushSize);updateSelection() }.show()
        }
        row(terrainButton!!,brushButton!!)
        row(button("地形を適用 $0",primary=true) { apply() },button("変更を戻す") { undo() })
        row(button("海面・海底") { surface.setSeabed(!planet.seabedVisible);changed() },button("惑星全体") { surface.setZoom(1f) })
        val scroll=object: ScrollView(editorContext) {
            override fun onMeasure(widthMeasureSpec: Int,heightMeasureSpec: Int) {
                // Large type and landscape stay scrollable without covering the whole globe.
                val maxHeight=(resources.displayMetrics.heightPixels*.55f).toInt()
                super.onMeasure(widthMeasureSpec,MeasureSpec.makeMeasureSpec(minOf(MeasureSpec.getSize(heightMeasureSpec),maxHeight),MeasureSpec.AT_MOST))
            }
        }.apply { addView(column);setBackgroundColor(0xfff7ecd7.toInt()) }
        d.setContentView(scroll)
        d.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setGravity(Gravity.BOTTOM)
        }
        d.setCanceledOnTouchOutside(false)
        d.setOnDismissListener { dialog=null;surface.updateBrush(1);surface.setHighlights(IntArray(0));changed() }
        d.show();d.window?.setLayout(-1,-2)
        surface.updateBrush(planet.brushSize);updateSelection()
    }
    fun updateSelection() {
        coordinate?.text=context.getString(R.string.globe_coordinate,planet.selectedX,planet.selectedY,planet.terrainAt(planet.selectedX,planet.selectedY).label)
        terrainButton?.text=context.getString(R.string.globe_terrain,terrain.label);brushButton?.text=if(planet.brushSize==1) "1マス" else "${planet.brushSize}×${planet.brushSize}"
        surface.select(planet.selectedIndex,false)
    }
    fun dispose() {
        dialog?.setOnDismissListener(null)
        dialog?.dismiss();dialog=null
        coordinate=null;terrainButton=null;brushButton=null
    }
    fun apply() {
        if(planet.terraform(planet.selectedX,planet.selectedY,terrain)>0) {
            surface.refresh(planet.selectedIndex);changed();updateSelection()
        }
    }
    fun undo() { if(planet.undo()) { surface.refresh(planet.selectedIndex);changed();updateSelection() } }
    fun chooseCoordinate() {
        val column=LinearLayout(editorContext).apply { orientation=LinearLayout.VERTICAL;setPadding(dp(18),0,dp(18),0) }
        fun input(value: Int,hint: String)=EditText(editorContext).apply {
            inputType=InputType.TYPE_CLASS_NUMBER;this.hint=hint;setText(String.format(java.util.Locale.ROOT,"%d",value));selectAll();column.addView(this)
        }
        val x=input(planet.selectedX,"横 X（0〜511）");val y=input(planet.selectedY,"縦 Y（0〜511）")
        val d=AlertDialog.Builder(editorContext).setTitle("球面の地点へ移動").setView(column).setPositiveButton("移動",null).setNegativeButton("取消",null).create()
        d.setOnShowListener {
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val xx=x.text.toString().toIntOrNull();val yy=y.text.toString().toIntOrNull()
                if(xx==null || xx !in 0..511) x.error="0〜511を入力"
                else if(yy==null || yy !in 0..511) y.error="0〜511を入力"
                else { planet.select(xx,yy);surface.select(planet.selectedIndex,true,true);updateSelection();changed();d.dismiss() }
            }
        };d.show()
    }
}
