package io.github.hatake716.pixelcity.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import io.github.hatake716.pixelcity.game.BuildCost
import io.github.hatake716.pixelcity.game.City
import io.github.hatake716.pixelcity.game.Tutorial

/** Secretary dialogue and the current assignment share the same measured layout and hit boxes. */
class TutorialPanel(private val text: GbText, context: Context, private val portrait: HiyoriPortrait = HiyoriPortrait(context)) {
    private companion object {
        const val HEADER_HEIGHT = 154
        const val PORTRAIT_SIZE = 112
        const val HEADING_X = 140
    }
    enum class Action { REPLY, PARKS, CLINIC, ADVICE, FOCUS, DIALOG }
    data class Button(val action: Action, val x: Int, val y: Int, val width: Int, val height: Int) {
        fun contains(px: Int, py: Int) = px in x until x + width && py in y until y + height
    }
    var buttons: List<Button> = emptyList(); private set
    private val paper = Palette.RURAL_WALL
    private val ink = Palette.UI_BG
    private val teal = Palette.TREE_DARK
    private val portraitBounds = RectF()

    private fun lines(city: City, tutorial: Tutorial, width: Int): List<String> {
        text.textSize = 21
        return text.wrap(if (tutorial.briefingRead) tutorial.advice(city) else tutorial.briefing(city), width - 24)
    }
    fun height(city: City, tutorial: Tutorial, width: Int, compact: Boolean=false): Int {
        if (!tutorial.active) return 0
        if(compact) return 112
        val body = lines(city, tutorial, width).size * 28
        text.textSize = 20
        val objective = if (tutorial.briefingRead) text.wrap(tutorial.objective(city), width - 24).size * 27 + 8 else 0
        return HEADER_HEIGHT + 2 + body + objective + if (tutorial.awaitingChoice()) 158 else 80
    }

    fun draw(canvas: PixelCanvas, city: City, tutorial: Tutorial, bottom: Int,width: Int=canvas.width,compact: Boolean=false) {
        val step = tutorial.step ?: return
        if(compact) {
            val top=bottom-112
            canvas.fillRect(0,top,width,112,paper);canvas.fillRect(0,top,width,3,teal)
            portraitBounds.set(12f,top+12f,76f,top+76f)
            text.textSize=18;text.draw(canvas,"ひより / 就任日誌 ${tutorial.stepIndex+1}",88,top+12,teal)
            text.textSize=18
            val line=if(tutorial.briefingRead) tutorial.objective(city) else step.title
            text.wrap(line,width-284).take(2).forEachIndexed { i,s -> text.draw(canvas,s,88,top+40+i*24,ink) }
            val b=Button(Action.DIALOG,width-184,top+14,172,72)
            canvas.fillRect(b.x,b.y,b.width,b.height,teal);text.textSize=19
            text.drawCentered(canvas,if(tutorial.briefingRead) "ひよりに相談" else "説明を読む",b.x+b.width/2,b.y+25,Palette.WHITE)
            buttons=listOf(b);return
        }
        val body = lines(city, tutorial, width)
        val h = height(city, tutorial, width)
        val top = bottom - h
        canvas.fillRect(0, top, width, h, paper)
        canvas.fillRect(0, top, width, 4, teal)
        canvas.drawRect(0, top, width, h, Palette.HOUSE_RIGHT)
        canvas.fillRect(12, top + 12, PORTRAIT_SIZE, PORTRAIT_SIZE, Palette.WHITE)
        canvas.drawRect(12, top + 12, PORTRAIT_SIZE, PORTRAIT_SIZE, Palette.HOUSE_RIGHT)
        portraitBounds.set(14f, (top + 14).toFloat(), (10 + PORTRAIT_SIZE).toFloat(), (top + 10 + PORTRAIT_SIZE).toFloat())
        text.textSize = 22
        text.draw(canvas, "秘書 ひより", HEADING_X, top + 12, teal)
        text.textSize = 17
        text.draw(canvas, "就任日誌 ${tutorial.chapterNumber()}/${tutorial.chapterCount()}", HEADING_X, top + 38, teal)
        text.textSize = 22
        text.draw(canvas, step.title, HEADING_X, top + 62, ink)
        text.textSize = 17
        text.draw(canvas, step.chapter.summary, HEADING_X, top + 96, teal)
        for (i in Tutorial.Chapter.entries.indices) {
            val x = HEADING_X + i * 21
            canvas.fillRect(x, top + 133, 16, 3, if (i < tutorial.chapterNumber()) teal else Palette.HOUSE_RIGHT)
        }
        var y = top + HEADER_HEIGHT
        if (tutorial.briefingRead) {
            text.textSize = 20
            for (line in text.wrap(tutorial.objective(city), width - 24)) {
                text.draw(canvas, line, 12, y, teal); y += 27
            }
            y += 8
        }
        text.textSize = 21
        for (line in body) { text.draw(canvas, line, 12, y, ink); y += 28 }
        val output = mutableListOf<Button>()
        fun button(action: Action, label: String, x: Int, w: Int, prominent: Boolean, detail: String? = null) {
            val bh = if (detail == null) 64 else 142
            val b = Button(action, x, bottom - bh - 8, w, bh)
            output.add(b)
            canvas.fillRect(b.x, b.y, b.width, b.height, if (prominent) teal else Palette.WHITE)
            canvas.drawRect(b.x, b.y, b.width, b.height, teal)
            text.textSize = 21
            text.drawCentered(canvas, label, x + w / 2, b.y + 18, if (prominent) Palette.WHITE else ink)
            if (detail != null) {
                text.textSize = 18
                var dy = b.y + 54
                for (line in text.wrap(detail, w - 16)) { text.draw(canvas, line, x + 8, dy, ink); dy += 25 }
            }
        }
        when {
            tutorial.awaitingChoice() -> {
                val w = (width - 36) / 2
                for ((i, priority) in Tutorial.Priority.entries.withIndex()) {
                    val cost = BuildCost.cost(priority.kind) * priority.count
                    val upkeep = BuildCost.upkeep(priority.kind) * priority.count
                    val effect = if (priority == Tutorial.Priority.PARKS) "地価・環境を整える" else "近所の健康を支える"
                    button(if (i == 0) Action.PARKS else Action.CLINIC, priority.label, 12 + i * (w + 12), w, false,
                        "$${cost} / 維持費 月$${upkeep}\n$effect")
                }
            }
            !tutorial.briefingRead -> button(Action.REPLY, step.reply, 12, width - 24, true)
            else -> {
                button(Action.ADVICE, "ひよりに相談", 12, 184, false)
                if (tutorial.neededTool != null || step.goal is Tutorial.Goal.InspectHome) {
                    button(Action.FOCUS, "候補地へ", width - 192, 180, true)
                } else if (tutorial.isRunningGoal()) {
                    button(Action.REPLY, "時間を進める", width - 208, 196, true)
                }
            }
        }
        buttons = output
    }

    fun actionAt(x: Int, y: Int) = buttons.firstOrNull { it.contains(x, y) }?.action

    /** Draw at device resolution after the pixel scene, preserving the illustration's eyes and hair. */
    fun drawPortrait(canvas: Canvas) {
        portrait.draw(canvas, portraitBounds)
    }
}
