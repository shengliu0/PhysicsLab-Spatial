package com.bytedance.physicslab.solar.demos.gas

import com.bytedance.physicslab.solar.lab.spec.Cam
import com.bytedance.physicslab.solar.lab.spec.SoftRender
import com.bytedance.physicslab.solar.core.P3
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * The demo driven like a visitor would: tap the handle twice (100 -> 50 %), then turn the heater to
 * 600 K. Checks the gauge follows Boyle and Gay-Lussac through the DEMO's own pipeline (piston
 * motion, heater thermostat, EMA smoothing), and renders each state for the visual review.
 */
class GasDemoStatesTest {
    private fun atm(): Double = Regex("p = ([0-9.]+) atm").find(GasDemo.readoutText)!!.groupValues[1].toDouble()

    private fun shot(name: String) {
        val img = SoftRender.render(GasDemo.root!!, Cam(P3(0f, 0.10f, 2.5f), P3(0f, 0.02f, 0f), 55f, "viewer"))
        SoftRender.writePng(img, File("build/review/states/gas_$name.png"))
    }

    private fun run(secs: Float) { repeat((secs * 60).toInt()) { GasDemo.update(1f / 60f) } }

    @Test
    fun boyleAndGayLussacThroughTheDemo() {
        GasDemo.tSetK = 300f; GasDemo.vFrac = 1f; GasDemo.nIdx = 2; GasDemo.speedIdx = 2
        GasDemo.runHeadless(File("build/review-cache").apply { mkdirs() }, 2f)
        GasDemo.resetAll()
        run(25f)
        val p1 = atm()
        GasDemo.onTap("PistonHandle"); GasDemo.onTap("PistonHandle")          // 100 -> 75 -> 50 %
        assertEquals(0.5f, GasDemo.vFrac, 1e-6f)
        run(2.5f); shot("compressing")
        run(40f)
        val p2 = atm()
        shot("half_volume")
        GasDemo.tSetK = 600f
        run(40f)
        val p3 = atm()
        shot("half_volume_600K")
        GasDemo.vFrac = 1f; run(40f)
        val p4 = atm()
        shot("full_600K")
        println("demo gauge: 300 K full ${"%.3f".format(p1)} atm; half V ${"%.3f".format(p2)} (x${"%.2f".format(p2 / p1)}); 600 K half V ${"%.3f".format(p3)} (x${"%.2f".format(p3 / p2)}); 600 K full ${"%.3f".format(p4)}")
        println(GasDemo.readoutText)
        assertEquals(2.0, p2 / p1, 0.2)        // Boyle (plus a few % hard-sphere excess)
        assertEquals(2.0, p3 / p2, 0.2)        // Gay-Lussac
        assertEquals(2.0, p4 / p1, 0.2)
        GasDemo.equalSpeeds(); run(0.2f); shot("equal_speeds_0.2s")
        run(3f); shot("equal_speeds_3s")
        GasDemo.tSetK = 300f; GasDemo.vFrac = 1f; GasDemo.speedIdx = 1
    }
}
