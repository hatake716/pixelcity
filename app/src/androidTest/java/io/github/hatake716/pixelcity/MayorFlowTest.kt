package io.github.hatake716.pixelcity

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import io.github.hatake716.pixelcity.data.SaveGame
import io.github.hatake716.pixelcity.game.City
import io.github.hatake716.pixelcity.game.Tutorial
import io.github.hatake716.pixelcity.ui.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real Activity, rendered controls and OS lifecycle; run only on a disposable emulator. */
@RunWith(AndroidJUnit4::class)
class MayorFlowTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private fun content(activity: MainActivity): View =
        ((activity.findViewById<ViewGroup>(android.R.id.content)).getChildAt(0) as ViewGroup).getChildAt(0)
    @Before fun prepare() {
        context.getSharedPreferences("pixelcity",Context.MODE_PRIVATE).edit().clear().commit()
        SaveGame.save(context,0,ShowcaseCity.build(ShowcaseCity.Kind.HARBOUR),Tutorial().apply { skip() },23L)
    }
    private fun openCity(scenario: ActivityScenario<MainActivity>) {
        scenario.onActivity { (content(it) as TitleView).onContinue!!.invoke() }
        scenario.onActivity { (content(it) as SlotView).onSlotChosen!!.invoke(0,true) }
        scenario.onActivity { (content(it) as GameView).pause() }
        awaitRendered(scenario,::content)
    }
    @Test fun theRenderedTaxButtonChangesTaxWithoutChargingOrAdvancingTime() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openCity(scenario)
            var bx=0;var by=0
            scenario.onActivity {
                val view=content(it) as GameView
                val scale=GameView.layoutFor(view.width,view.height).first
                val button=GameView::class.java.getDeclaredField("budgetButtonX").apply { isAccessible=true }.get(view) as Pair<*, *>
                bx=((button.first as Int)+(button.second as Int))/2*scale
                by=(GameView::class.java.getDeclaredField("bottomRowTop").apply { isAccessible=true }.getInt(view)+15)*scale
            }
            device.click(bx,by)
            awaitRendered(scenario,::content)
            val before=SaveGame.load(context,0)!!.city
            var x=0;var y=0
            scenario.onActivity {
                val view=content(it) as GameView
                assertEquals("Budget button must open the rendered budget",GameView.Screen.BUDGET,view.screen)
                val field=GameView::class.java.getDeclaredField("taxButtonY").apply { isAccessible=true }
                val scale=GameView.layoutFor(view.width,view.height).first
                x=422*scale;y=(field.getInt(view)+32)*scale
            }
            assertTrue(device.click(x,y));device.waitForIdle(500)
            val after=SaveGame.load(context,0)!!.city
            assertEquals(before.taxRate+1,after.taxRate)
            assertEquals(before.month,after.month);assertEquals(before.funds,after.funds)
        }
    }
    @Test fun restartingDoesNotResurrectTheDeletedCityWhenTheActivityPauses() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openCity(scenario)
            scenario.onActivity { (content(it) as GameView).onRestartRequested!!.invoke() }
            assertFalse(SaveGame.hasSave(context,0))
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            assertFalse(SaveGame.hasSave(context,0))
            scenario.onActivity { assertTrue(content(it) is TitleView) }
        }
    }
    @Test fun inspectionDoesNotBuildOrSpendAndBackOpensTheMayorMenu() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openCity(scenario)
            val before=SaveGame.load(context,0)!!.city
            var x=0;var y=0
            scenario.onActivity {
                val view=content(it) as GameView
                val scale=GameView.layoutFor(view.width,view.height).first
                x=view.width-56*scale;y=SystemBars.top(scale)*scale+105*scale
            }
            device.click(x,y);awaitRendered(scenario,::content)
            device.click(device.displayWidth/2,device.displayHeight/2);device.waitForIdle(500)
            scenario.onActivity { assertEquals(GameView.Screen.MESSAGE,(content(it) as GameView).screen) }
            assertEquals(before.funds,SaveGame.load(context,0)!!.city.funds)
            device.pressBack();device.waitForIdle(500)
            assertTrue(device.findObject(androidx.test.uiautomator.UiSelector().text("市長メニュー")).exists())
        }
    }
}
