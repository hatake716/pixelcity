package io.github.hatake716.pixelcity

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.core.view.doOnPreDraw
import androidx.test.core.app.ActivityScenario
import io.github.hatake716.pixelcity.ui.MainActivity
import org.junit.Assert.assertTrue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import io.github.hatake716.pixelcity.ui.GameView

internal fun awaitGlobe(scenario: ActivityScenario<MainActivity>,root: (MainActivity)->View) {
    val end=android.os.SystemClock.uptimeMillis()+30000
    var ready=false
    while(!ready && android.os.SystemClock.uptimeMillis()<end) {
        scenario.onActivity { ready=(root(it) as? GameView)?.globe?.renderedFrames?.let { it>0 } ?: true }
        if(!ready) android.os.SystemClock.sleep(100)
    }
    assertTrue("Unified city globe has not rendered",ready)
}

/** RESUMED / a quiet event queue does not mean the next view has been laid out. */
internal fun awaitRendered(scenario: ActivityScenario<MainActivity>, root: (MainActivity)->View) {
    val ready=CountDownLatch(1)
    scenario.onActivity { a -> root(a).apply { doOnPreDraw { ready.countDown() };invalidate() } }
    assertTrue("Activity did not lay out the requested screen",ready.await(10,TimeUnit.SECONDS))
    scenario.onActivity { a ->
        val v=root(a)
        assertTrue("Screen has no bounds",v.width>0 && v.height>0)
        val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888)
        v.draw(Canvas(b));b.recycle()
    }
}
