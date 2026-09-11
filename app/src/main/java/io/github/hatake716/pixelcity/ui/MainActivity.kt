package io.github.hatake716.pixelcity.ui

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.os.Build
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.hatake716.pixelcity.data.SaveGame
import io.github.hatake716.pixelcity.game.City
import io.github.hatake716.pixelcity.game.Tutorial
import kotlin.random.Random

class MainActivity : Activity() {

    private lateinit var root: FrameLayout
    private var gameView: GameView? = null
    private var city: City = City()
    private var tutorial: Tutorial = Tutorial()
    private var seed: Long = 0L
    /** いま遊んでいるスロット。ここへ自動で保存し続ける。 */
    private var slot: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        goFullScreen()
        root = FrameLayout(this)
        setContentView(root)
        // 旧版（1スロットだけ）で遊んでいた街を、最初の枠へ引き継ぐ。
        SaveGame.migrateLegacySave(this)
        showTitle()
    }

    private fun showTitle() {
        val title = TitleView(this, SaveGame.hasAnySave(this))
        title.onStartTutorial = { chooseSlotForNewGame(withTutorial = true) }
        title.onSkipTutorial = { chooseSlotForNewGame(withTutorial = false) }
        title.onContinue = { showContinue() }
        title.onShowcase = { showShowcasePicker() }
        title.onLicenses = { showLicenses() }
        setRoot(title)
    }

    /** お手本の街を選び、その続きから遊ぶ。 */
    private fun showShowcasePicker() {
        val picker = ShowcasePickerView(this)
        picker.onBack = { showTitle() }
        picker.onChosen = { kind -> chooseSlotForShowcase(kind) }
        setRoot(picker)
    }

    /** お手本をどの枠に入れるか選ぶ。 */
    private fun chooseSlotForShowcase(kind: ShowcaseCity.Kind) {
        val empty = SaveGame.firstEmptySlot(this)
        if (empty != null && !SaveGame.hasAnySave(this)) {
            startShowcase(empty, kind)
            return
        }
        val view = SlotView(this, allowEmpty = true, title = "どの まちに いれる？")
        view.onSlotChosen = { chosen, hasCity ->
            if (hasCity) {
                AlertDialog.Builder(this)
                    .setTitle("${chosen + 1}ばんの まちを けしますか？")
                    .setMessage("${kind.label}を いれます。いまの まちは きえます。")
                    .setPositiveButton("いれる") { _, _ -> startShowcase(chosen, kind) }
                    .setNegativeButton("やめる", null)
                    .show()
            } else {
                startShowcase(chosen, kind)
            }
        }
        view.onBack = { showShowcasePicker() }
        view.onDeleteRequested = { target -> confirmDelete(target) { view.refresh() } }
        setRoot(view)
    }

    private fun startShowcase(targetSlot: Int, kind: ShowcaseCity.Kind) {
        slot = targetSlot
        seed = kind.seed
        city = ShowcaseCity.build(kind)
        tutorial = Tutorial().apply { skip() }
        SaveGame.clear(this, slot)
        startGame()
        AlertDialog.Builder(this)
            .setTitle(kind.label)
            .setMessage("${kind.summary}\n\nここから じゆうに そだててください。")
            .setPositiveButton("はじめる", null)
            .show()
    }

    /** 保存した街から選んで再開する。 */
    private fun showContinue() {
        val view = SlotView(this, allowEmpty = false, title = "つづきから")
        view.onSlotChosen = { chosen, hasCity ->
            if (hasCity) continueGame(chosen)
        }
        view.onBack = { showTitle() }
        view.onDeleteRequested = { target -> confirmDelete(target) { view.refresh() } }
        setRoot(view)
    }

    /**
     * 新しい街をどの枠で始めるか選ぶ。
     * 空きがなければ、どれかを選んで上書きすることになるので確認する。
     */
    private fun chooseSlotForNewGame(withTutorial: Boolean) {
        val empty = SaveGame.firstEmptySlot(this)
        if (empty != null && !SaveGame.hasAnySave(this)) {
            // まだ一つも街がないときは、選ばせずにそのまま始める
            newGame(empty, withTutorial)
            return
        }
        val view = SlotView(this, allowEmpty = true, title = "どの まちを つくる？")
        view.onSlotChosen = { chosen, hasCity ->
            if (hasCity) {
                AlertDialog.Builder(this)
                    .setTitle("${chosen + 1}ばんの まちを けしますか？")
                    .setMessage("あたらしい まちで はじめます。いまの まちは きえます。")
                    .setPositiveButton("はじめる") { _, _ -> newGame(chosen, withTutorial) }
                    .setNegativeButton("やめる", null)
                    .show()
            } else {
                newGame(chosen, withTutorial)
            }
        }
        view.onBack = { showTitle() }
        view.onDeleteRequested = { target -> confirmDelete(target) { view.refresh() } }
        setRoot(view)
    }

    private fun confirmDelete(target: Int, after: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("${target + 1}ばんの まちを けしますか？")
            .setMessage("もとに もどせません。")
            .setPositiveButton("けす") { _, _ ->
                SaveGame.clear(this, target)
                after()
            }
            .setNegativeButton("やめる", null)
            .show()
    }

    private fun setRoot(view: View) {
        root.removeAllViews()
        root.addView(
            view,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    private fun newGame(targetSlot: Int, withTutorial: Boolean) {
        slot = targetSlot
        seed = Random.nextLong()
        city = City().apply {
            generateTerrain(seed)
            // 最初の一歩で詰まないよう、中心は必ず平らにしておく。
            clearStartingArea()
        }
        tutorial = Tutorial()
        if (withTutorial) tutorial.start() else tutorial.skip()
        SaveGame.clear(this, slot)
        startGame()
    }

    private fun continueGame(targetSlot: Int) {
        val loaded = SaveGame.load(this, targetSlot)
        if (loaded == null) {
            // 読めなかった枠は空になっているので、選び直してもらう
            showContinue()
            return
        }
        slot = targetSlot
        city = loaded.city
        tutorial = loaded.tutorial
        seed = loaded.seed
        startGame()
    }

    private fun startGame() {
        val view = GameView(this, city, tutorial)
        view.centerCamera()
        view.onStateChanged = { save() }
        view.onRestartRequested = {
            SaveGame.clear(this, slot)
            showTitle()
        }
        view.onTutorialFinished = {
            AlertDialog.Builder(this)
                .setTitle("そつぎょう！")
                .setMessage(
                    "きほんは これで かんぺきです。\n" +
                        "じんこうを ふやして、せかいの けんちくを たてましょう。",
                )
                .setPositiveButton("はじめる", null)
                .show()
        }
        gameView = view
        setRoot(view)
    }

    private fun showLicenses() {
        val body = assets.open("licenses/DotGothic16-OFL.txt").bufferedReader().use { it.readText() }
        val text = TextView(this).apply {
            setPadding(32, 32, 32, 32)
            textSize = 11f
            setText(
                "PIXELCITY のコードとドット絵は MIT ライセンスです。\n\n" +
                    "同梱のドットフォント DotGothic16 は Fontworks Inc. による\n" +
                    "SIL Open Font License 1.1 のもとで同梱しています。\n" +
                    "改変せずそのまま収録しています。\n\n" +
                    "--- DotGothic16 OFL ---\n\n" + body,
            )
        }
        AlertDialog.Builder(this)
            .setTitle("ライセンス")
            .setView(ScrollView(this).apply { addView(text) })
            .setPositiveButton("とじる", null)
            .show()
    }

    private fun save() {
        if (gameView != null) SaveGame.save(this, slot, city, tutorial, seed)
    }

    override fun onPause() {
        super.onPause()
        gameView?.pause()
        save()
    }

    override fun onResume() {
        super.onResume()
        gameView?.resume()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // 通知を引き下ろしたあとなどに、また隠す
        if (hasFocus) goFullScreen()
    }

    /**
     * 画面いっぱいに描く。
     *
     * 上下の帯（状態表示と操作の欄）を隠して、街を広く見せる。
     * 画面の端から引き出せば、いつでも出てくる。
     */
    private fun goFullScreen() {
        // 表示領域を、切り欠きのある端末でも端まで広げる
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            // 端から引き出すと一時的に出て、しばらくすると自動で隠れる
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        gameView?.releaseAudio()
    }
}
