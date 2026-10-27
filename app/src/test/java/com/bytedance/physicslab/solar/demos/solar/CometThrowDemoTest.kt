package com.bytedance.physicslab.solar.demos.solar

import com.bytedance.physicslab.solar.core.comet.CometThrow
import com.bytedance.physicslab.solar.lab.Lang
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Pinch the icon, swing the hand, release: through the real demo (synthetic hand at 90 Hz). */
class CometThrowDemoTest {
    private fun throwAt(speed: Double): String {
        Lang.set(false)
        val d = SolarDemo
        d.buildSpec(File("build/review-cache").apply { mkdirs() }); d.update(0f); repeat(10) { d.update(1f / 60f) }
        var t = 0.0
        d.comets.clock = { t }
        d.onDragStart("CometIcon")
        // 0.15 s accelerating swing toward -x (left, in volume space), then let go
        val dt = 1.0 / 90
        var v = 0.0
        while (t < 0.15) { t += dt; v = speed * (t / 0.15); d.onDrag("CometIcon", (-v * dt).toFloat(), 0f, 0f) }
        d.onDragEnd("CometIcon")
        repeat(120) { d.update(1f / 60f) }
        println("THROW %.1f m/s -> k=%.2f : %s | alive=%d".format(speed, CometThrow.launchFactor(speed), d.comets.lastInfo, d.comets.count))
        return d.comets.lastInfo
    }

    @Test
    fun gentleThrowOrbitsHardThrowEscapes() {
        val gentle = throwAt(1.0)
        assertTrue("1 m/s should give a bound orbit: $gentle", gentle.contains("周期"))
        val hard = throwAt(2.5)
        assertTrue("2.5 m/s should escape: $hard", hard.contains("逃逸"))
        assertTrue(SolarDemo.readoutText.contains("彗星"))
    }
}
