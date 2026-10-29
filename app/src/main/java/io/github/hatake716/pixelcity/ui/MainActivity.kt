package io.github.hatake716.pixelcity.ui

import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.os.Build
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.hatake716.pixelcity.data.SaveGame
import io.github.hatake716.pixelcity.game.Planet
import io.github.hatake716.pixelcity.game.PlanetTerrain
import io.github.hatake716.pixelcity.game.City
import io.github.hatake716.pixelcity.game.Tutorial
import kotlin.random.Random

class MainActivity : ComponentActivity() {

    private lateinit var root: FrameLayout
    private var gameView: GameView? = null
    private var planet: Planet? = null
    private var planetIntroductionShowing = false
    private var city: City = City()
    private var tutorial: Tutorial = Tutorial()
    private var seed: Long = 0L
    /** いま遊んでいるスロット。ここへ自動で保存し続ける。 */
    private var slot: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        drawBehindSystemBars()
        root = FrameLayout(this)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val safe = SystemBars.safeInsets(insets, ModernMenu.dp(this, 8))
            val changed = SystemBars.rawTop != safe.top || SystemBars.rawBottom != safe.bottom || SystemBars.rawLeft != safe.left || SystemBars.rawRight != safe.right
            SystemBars.set(safe.top, safe.bottom, safe.left, safe.right)
            if (changed) {
                // HUDと球面の表示範囲・タッチ位置を、同じ余白で配置し直す。
                (view as FrameLayout).getChildAt(0)?.let { it.requestLayout(); it.invalidate() }
            }
            insets
        }
        setContentView(root)
        // 旧版（1スロットだけ）で遊んでいた街を、最初の枠へ引き継ぐ。
        SaveGame.migrateLegacySave(this)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = showMayorMenu()
        })
        showTitle()
    }

    private fun showTitle() {
        planet = null
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
        picker.onWorldChosen = { chooseSlotForShowcase(null) }
        setRoot(picker)
    }

    /** お手本をどの枠に入れるか選ぶ。 */
    private fun chooseSlotForShowcase(kind: ShowcaseCity.Kind?) {
        val empty = SaveGame.firstEmptySlot(this)
        if (empty != null && !SaveGame.hasAnySave(this)) {
            startShowcase(empty, kind)
            return
        }
        val view = SlotView(this, allowEmpty = true, title = "保存先を選択")
        view.onSlotChosen = { chosen, hasCity ->
            if (hasCity) {
                AlertDialog.Builder(this)
                    .setTitle("保存枠${chosen + 1}の都市を削除しますか？")
                    .setMessage("${kind?.label ?: RegionalShowcase.WORLD_LABEL}で置き換えます。現在の都市は削除されます。")
                    .setPositiveButton("置換") { _, _ -> startShowcase(chosen, kind) }
                    .setNegativeButton("取消", null)
                    .show()
            } else {
                startShowcase(chosen, kind)
            }
        }
        view.onBack = { showShowcasePicker() }
        view.onDeleteRequested = { target -> confirmDelete(target) { view.refresh() } }
        setRoot(view)
    }

    private fun startShowcase(targetSlot: Int, kind: ShowcaseCity.Kind?) {
        slot = targetSlot
        seed = kind?.seed ?: RegionalShowcase.WORLD_SEED
        val located=if(kind==null) RegionalShowcase.buildWorld() else RegionalShowcase.build(kind).expand()
        city = located.city
        tutorial = located.tutorial
        planet = located.planet
        SaveGame.clear(this, slot)
        startGame()
        gameView?.setSpeed(0)
        AlertDialog.Builder(this)
            .setTitle(kind?.label ?: RegionalShowcase.WORLD_LABEL)
            .setMessage(if(kind==null) RegionalShowcase.WORLD_SUMMARY else "${RegionalShowcase.region(kind).label}\n${kind.summary}\n\nここから自由に都市を育ててください。")
            .setPositiveButton("開始") { _, _ -> gameView?.setSpeed(1) }
            .show()
    }

    /** 保存した街から選んで再開する。 */
    private fun showContinue() {
        val view = SlotView(this, allowEmpty = false, title = "都市再開")
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
        val view = SlotView(this, allowEmpty = true, title = "新しい都市の保存先")
        view.onSlotChosen = { chosen, hasCity ->
            if (hasCity) {
                AlertDialog.Builder(this)
                    .setTitle("保存枠${chosen + 1}の都市を削除しますか？")
                    .setMessage("新しい都市を開始します。現在の都市は削除されます。")
                    .setPositiveButton("開始") { _, _ -> newGame(chosen, withTutorial) }
                    .setNegativeButton("取消", null)
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
            .setTitle("保存枠${target + 1}の都市を削除しますか？")
            .setMessage("削除した都市は復元できません。")
            .setPositiveButton("削除") { _, _ ->
                SaveGame.clear(this, target)
                after()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun setRoot(view: View) {
        if (view !is GameView) {
            gameView?.pauseRenderer()
            gameView?.releaseAudio()
            gameView = null
        }
        root.removeAllViews()
        root.addView(
            view,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        // 画面切替後に加わったメニューにも、現在のカメラ穴・システム欄を配信する。
        ViewCompat.requestApplyInsets(root)
    }

    private fun newGame(targetSlot: Int, withTutorial: Boolean) {
        slot = targetSlot
        seed = Random.nextLong()
        // The starting location is selected from natural Earth lowlands.
        city = City()
        tutorial = Tutorial()
        if (withTutorial) tutorial.start() else tutorial.skip()
        planet = null
        SaveGame.clear(this, slot)
        startGame()
    }

    private fun continueGame(targetSlot: Int) {
        val loaded = SaveGame.load(this, targetSlot)
        if (loaded == null) {
            AlertDialog.Builder(this)
                .setTitle("都市を読み込めませんでした")
                .setMessage("保存データは保持しています。新しいバージョンのデータか、破損している可能性があります。")
                .setPositiveButton("閉じる", null).show()
            return
        }
        slot = targetSlot
        city = loaded.city
        tutorial = loaded.tutorial
        seed = loaded.seed
        planet = loaded.planet
        startGame()
    }

    private fun startGame(paused: Boolean = false, focusX: Int? = null, focusY: Int? = null) {
        if (planet == null || planet?.earthVersion != io.github.hatake716.pixelcity.game.EarthTerrain.VERSION) {
            val expanded = SaveGame.expandToPlanet(SaveGame.Loaded(city, tutorial, seed, planet))
            city = expanded.city; tutorial = expanded.tutorial; planet = expanded.planet
            planet?.zoom=24f
        }
        val view = GameView(this, city, tutorial,planet)
        view.centerCamera()
        if (focusX != null && focusY != null) view.focusOnTile(focusX, focusY)
        if (paused) view.setSpeed(0)
        view.onStateChanged = { save() }
        view.onPlanetRequested = { view.openTerrainEditor() }
        view.onTutorialFinished = { view.post { showPlanetIntroduction() } }
        view.onRestartRequested = {
            SaveGame.clear(this, slot)
            showTitle()
        }

        gameView = view
        setRoot(view)
        save()
        if (tutorial.finished) view.post { showPlanetIntroduction() }
    }

    private fun showPlanet() {
        gameView?.openTerrainEditor()
    }

    private fun showPlanetIntroduction() {
        val world = planet ?: return
        if (!tutorial.finished || world.introductionSeen || planetIntroductionShowing || gameView == null) return
        planetIntroductionShowing = true
        gameView?.pause()
        AlertDialog.Builder(this).setTitle("ひよりから、惑星の地形編集の御案内")
            .setMessage("""市長、お疲れさまでした！

市長が育てている街は、512×512マスの惑星の一部です。画面を縮小すると、同じ街から星全体を見渡せます。「地形編集」では1マスずつ11種類の地形を無料で配置できます。

この星は本物の地球の地形をもとにしています。最初の街は自然の平野から始まります。ほかの場所も平野に変えれば、同じ街を広げられます。建設できるのは平野だけ。市政の時間は編集中止まるので、息抜きに御自分だけの星を作ってみてくださいね。""")
            .setPositiveButton("市政を続ける") { _, _ -> }
            .setNeutralButton("地形を編集") { _, _ -> showPlanet() }
            .setOnDismissListener {
                world.introductionSeen = true
                planetIntroductionShowing = false
                save()
                gameView?.resume()
            }.show()
    }

    private fun showLicenses() {
        val body = listOf("Earth-assets.txt", "Realistic-assets.txt", "Outfit-OFL.txt", "DotGothic16-OFL.txt").joinToString("\n\n") { file ->
            assets.open("licenses/$file").bufferedReader().use { it.readText() }
        }
        val text = TextView(this).apply {
            setPadding(32,32,32,32);textSize=16f
            setText("PIXELCITY のコード・独自制作の3Dモデルは MIT ライセンスです。写真・マテリアル・フォントは各記載の条件で収録しています。\n\n"+body)
        }
        AlertDialog.Builder(this)
            .setTitle("ライセンス")
            .setView(ScrollView(this).apply { addView(text) })
            .setPositiveButton("閉じる", null)
            .show()
    }

    private fun save() {
        if (planet != null && gameView != null) SaveGame.save(this, slot, city, tutorial, seed, planet)
    }

    override fun onPause() {
        super.onPause()
        gameView?.pause()
        gameView?.pauseRenderer()
        save()
    }

    override fun onStop() {
        super.onStop()
        RealisticCityRenderer.trimMemory()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN ||
            level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
            level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            RealisticCityRenderer.trimMemory()
            root.invalidate()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!planetIntroductionShowing) gameView?.resume()
        gameView?.resumeRenderer()
    }

    /**
     * 画面の端まで描く。
     *
     * 通知の欄と操作の欄は**隠さない**。隠すと、遊んでいる最中に
     * うっかり引き出してしまったり、戻る操作ができずに困る。
     * 欄の裏まで絵を伸ばしたうえで、その高さぶんだけ中身を寄せる。
     * こうすると余白は消えるが、欄には重ならない。
     */
    private fun drawBehindSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        // 欄は出したまま。文字が読めるよう、背景に合わせて暗くする。
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }

    private fun showMayorMenu() {
        if (gameView == null) {
            if (root.getChildAt(0) is TitleView) finish() else showTitle()
            return
        }
        gameView?.pause()
        AlertDialog.Builder(this)
            .setTitle("市長メニュー")
            .setItems(arrayOf("市政を続ける", "地形編集", "保存してタイトルへ")) { _, which ->
                when (which) { 1 -> showPlanet(); 2 -> { save(); showTitle() }; else -> gameView?.resume() }
            }
            .setOnCancelListener { gameView?.resume() }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        planet = null
        gameView?.releaseAudio()
        gameView?.pauseRenderer()
    }
}
