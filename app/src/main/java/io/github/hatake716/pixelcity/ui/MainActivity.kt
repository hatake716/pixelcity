package io.github.hatake716.pixelcity.ui

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.view.WindowManager
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        root = FrameLayout(this)
        setContentView(root)
        showTitle()
    }

    private fun showTitle() {
        val title = TitleView(this, SaveGame.hasSave(this))
        title.onStartTutorial = { newGame(withTutorial = true) }
        title.onSkipTutorial = { newGame(withTutorial = false) }
        title.onContinue = { continueGame() }
        title.onLicenses = { showLicenses() }
        setRoot(title)
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

    private fun newGame(withTutorial: Boolean) {
        val start = {
            seed = Random.nextLong()
            city = City().apply {
                generateTerrain(seed)
                // 最初の一歩で詰まないよう、中心は必ず平らにしておく。
                clearStartingArea()
            }
            tutorial = Tutorial()
            if (withTutorial) tutorial.start() else tutorial.skip()
            SaveGame.clear(this)
            startGame()
        }
        if (SaveGame.hasSave(this)) {
            AlertDialog.Builder(this)
                .setTitle("あたらしく はじめますか？")
                .setMessage("いまの まちは きえます。")
                .setPositiveButton("はじめる") { _, _ -> start() }
                .setNegativeButton("やめる", null)
                .show()
        } else {
            start()
        }
    }

    private fun continueGame() {
        val loaded = SaveGame.load(this)
        if (loaded == null) {
            newGame(withTutorial = true)
            return
        }
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
            SaveGame.clear(this)
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
                    "どうこうフォント DotGothic16 は Fontworks Inc. による\n" +
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
        if (gameView != null) SaveGame.save(this, city, tutorial, seed)
    }

    override fun onPause() {
        super.onPause()
        gameView?.pause()
        save()
    }
}
