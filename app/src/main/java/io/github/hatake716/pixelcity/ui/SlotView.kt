package io.github.hatake716.pixelcity.ui

import android.content.Context
import android.widget.*
import io.github.hatake716.pixelcity.data.SaveGame

/** Scrollable save cards, with explicit deletion and the Activity's existing confirmation. */
class SlotView(context: Context,private val allowEmpty: Boolean,private val title: String): FrameLayout(context) {
    var onSlotChosen: ((Int,Boolean)->Unit)?=null
    var onBack: (()->Unit)?=null
    var onDeleteRequested: ((Int)->Unit)?=null
    init { refresh() }
    fun refresh() {
        removeAllViews()
        val list=ModernMenu.column(this,title,"都市の記録は自動的に保存されます。")
        val slots=SaveGame.listSlots(context)
        repeat(SaveGame.SLOT_COUNT) { i ->
            val info=slots.getOrNull(i)
            val title=if(info==null) "${i+1}  新しい街" else "${i+1}  人口 ${"%,d".format(info.population)} 人"
            val detail=if(info==null) { if(allowEmpty) "ここに新しい街をつくります" else "保存された街はありません" }
                else if(!info.readable) "読み込みエラー・元の記録は保持されています"
                else "${info.dateLabel}  /  資金 $${"%,d".format(info.funds)}\n"+(if(info.planetary) "512×512の惑星・" else "")+(if(info.tutorialActive) "就任日誌" else info.campaignLabel)
            val card=ModernMenu.action(context,title,detail) { if(info!=null || allowEmpty) onSlotChosen?.invoke(i,info!=null) }
            card.isEnabled=info!=null || allowEmpty;if(!card.isEnabled) card.alpha=.45f
            ModernMenu.add(list,card)
            if(info!=null) ModernMenu.add(list,ModernMenu.action(context,"記録 ${i+1} を削除") { onDeleteRequested?.invoke(i) })
        }
        ModernMenu.add(list,ModernMenu.action(context,"戻る") { onBack?.invoke() })
    }
}
