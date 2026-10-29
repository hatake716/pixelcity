package io.github.hatake716.pixelcity.ui

import io.github.hatake716.pixelcity.game.CustomStyle

/** Four material-preserving colour grades; old save palette entries remain intact. */
class StyleEditor(private val text: GbText) {
    var slot=CustomStyle.Slot.HOUSE_WALL
    var insetTop=0
    var insetBottom=0
    var scrollOffset=0
        private set
    private var maxScroll=0
    private var bottom=0
    private val slots=listOf(CustomStyle.Slot.HOUSE_WALL,CustomStyle.Slot.OFFICE_WALL,CustomStyle.Slot.FACTORY_WALL,CustomStyle.Slot.GENERIC_WALL)
    private val labels=listOf("住宅の色調","商業の色調","工業の色調","公共施設の色調")
    private val choices=intArrayOf(Palette.WHITE,Palette.STONE_LIT,Palette.STONE,Palette.METAL,Palette.HOUSE_LEFT,Palette.HOUSE_ROOF,Palette.RED,Palette.GOLD,Palette.SAND,Palette.ANCIENT_WALL,Palette.RURAL_WALL,Palette.TREE_LIT,Palette.TREE,Palette.TREE_DARK,Palette.GLASS_LIT,Palette.GLASS,Palette.SKY,Palette.SKY_DEEP,Palette.PRIME_LEFT,Palette.WATER,Palette.WATER_DARK,Palette.EURO_WALL,Palette.JP_WALL,Palette.FUTURE_WALL,Palette.GRIT_WALL,Palette.FACTORY_LEFT,Palette.WALL_LEFT,Palette.BLACK)
    private fun top()=insetTop+100
    private fun paletteTop()=top()+4*68+40
    fun backButtonY(logicalH: Int)=logicalH-insetBottom-76
    private fun resetY(logicalH: Int)=backButtonY(logicalH)-72
    fun scrollBy(delta: Int) { scrollOffset=(scrollOffset+delta).coerceIn(0,maxScroll) }
    fun draw(p: PixelCanvas,custom: CustomStyle,logicalH: Int) {
        p.clear(Palette.UI_BG)
        text.textSize=28;text.draw(p,"建物の色調",20,insetTop+12,Palette.UI_ACCENT)
        text.textSize=18;text.draw(p,"建物を選び、好みの色をタップしてください。",20,insetTop+56,Palette.UI_DIM)
        bottom=resetY(logicalH)-12
        val c=p.nativeCanvas;c?.save();c?.clipRect(0,top(),p.width,bottom);c?.translate(0f,-scrollOffset.toFloat())
        for((i,s) in slots.withIndex()) {
            val y=top()+i*68
            p.fillRect(20,y,p.width-40,62,if(s==slot) Palette.UI_BG_LIGHT else Palette.UI_BG)
            p.drawRect(20,y,p.width-40,62,if(s==slot) Palette.UI_ACCENT else Palette.UI_LINE)
            text.textSize=22;text.draw(p,labels[i],34,y+17,Palette.UI_TEXT)
            p.fillRect(p.width-76,y+10,40,40,custom[s])
        }
        text.textSize=20;text.draw(p,"素材の陰影を保ちながら色を重ねます。",20,paletteTop()-32,Palette.UI_DIM)
        for((i,color) in choices.withIndex()) {
            val x=20+(i%7)*70;val y=paletteTop()+(i/7)*70
            p.fillRect(x,y,62,62,color)
            if(color==custom[slot]) { p.drawRect(x-2,y-2,66,66,Palette.UI_ACCENT);p.drawRect(x+3,y+3,56,56,Palette.UI_BG) }
        }
        maxScroll=(paletteTop()+4*70-bottom).coerceAtLeast(0)
        c?.restore()
        for((y,title) in listOf(resetY(logicalH) to "標準の色に戻す",backButtonY(logicalH) to "設定に戻る")) {
            p.fillRect(20,y,p.width-40,64,Palette.UI_BG_LIGHT)
            text.textSize=22;text.drawCentered(p,title,p.width/2,y+18,Palette.UI_TEXT)
        }
    }
    fun slotAt(lx: Int,ly: Int): CustomStyle.Slot? {
        if(lx<20 || lx>=GameView.LOGICAL_W-20 || ly<top() || ly>=bottom) return null
        val index=(ly+scrollOffset-top())/68
        return slots.getOrNull(index)
    }
    fun colourAt(lx: Int,ly: Int,logicalH: Int): Int? {
        if(ly<top() || ly>=bottom) return null
        val yy=ly+scrollOffset-paletteTop();val xx=lx-20
        if(xx<0 || yy<0 || xx/70>=7 || xx%70>=62 || yy%70>=62) return null
        return choices.getOrNull(yy/70*7+xx/70)
    }
    fun resetTapped(lx: Int,ly: Int,logicalH: Int)=lx in 20 until GameView.LOGICAL_W-20 && ly in resetY(logicalH) until resetY(logicalH)+64
}
