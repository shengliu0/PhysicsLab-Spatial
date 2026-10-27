package com.bytedance.physicslab.solar.demos.expansion

import com.bytedance.physicslab.solar.core.P3
import com.bytedance.physicslab.solar.lab.spec.Cam
import com.bytedance.physicslab.solar.lab.spec.SoftRender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * One full automatic cycle, frame by frame at 60 Hz, as a visitor would watch it: the hot ball sits on
 * the rim exactly while the real clearance is negative, drops the moment it turns positive, is
 * quenched, passes cold, is heated (and glows only above 525 °C), and sits on the rim again.
 */
class ExpansionDemoStatesTest {
    private fun shot(name: String) {
        val img = SoftRender.render(ExpansionDemo.root!!, Cam(P3(0f, 0.10f, 2.5f), P3(0f, 0.02f, 0f), 55f, "viewer"))
        SoftRender.writePng(img, File("build/review/states/expansion_$name.png"))
    }

    @Test
    fun fullAutomaticCycle() {
        val d = ExpansionDemo
        d.metalIdx = 0; d.speedIdx = 1; d.auto = true; d.exaggerate = true
        d.runHeadless(File("build/review-cache").apply { mkdirs() }, 0f)
        d.restart()
        val seen = LinkedHashSet<String>()
        var restFrames = 0; var restWithPositiveGap = 0; var maxGlowWhileCold = 0f
        var droppedHot = false; var passedCold = false; var heatedTo = 0.0; var backOnRim = false
        val shots = mutableSetOf<String>()
        var tSec = 0f
        while (tSec < 150f) {
            d.update(1f / 60f); tSec += 1f / 60f
            seen += d.command
            if (d.restState > 0) { restFrames++; if (d.clearanceUm >= 0) restWithPositiveGap++ }
            if (d.ballC < 500) maxGlowWhileCold = maxOf(maxGlowWhileCold, d.glowNow)
            if (d.command == "WAIT" && d.ballC > 150 && !droppedHot) { droppedHot = true; println("dropped through at t=${"%.1f".format(tSec)} s: ball ${"%.0f".format(d.ballC)} °C, ring ${"%.0f".format(d.ringC)} °C, gap ${"%.1f".format(d.clearanceUm)} µm") }
            if (d.command == "WAIT" && d.ballC < 60 && !passedCold) { passedCold = true; println("cold ball passed at t=${"%.1f".format(tSec)} s, gap ${"%.1f".format(d.clearanceUm)} µm") }
            if (d.command == "HEAT") heatedTo = maxOf(heatedTo, d.ballC)
            if (passedCold && d.restState > 0) backOnRim = true
            fun once(k: String, c: Boolean) { if (c && shots.add(k)) shot(k) }
            once("1_hot_on_rim", tSec > 2f && d.restState > 0 && !droppedHot)
            once("2_dropped", droppedHot && d.command == "WAIT")
            once("3_quench", d.command == "QUENCH" && d.ballC > 150)
            once("4_cold_pass", d.command == "AXIS_BELOW" && d.ballC < 60 && (d.axisCoord < -0.02f))
            once("5_heating", d.command == "HEAT" && d.ballC > 600)
            once("6_back_on_rim", backOnRim)
            if (backOnRim && tSec > 10f) break
        }
        println("commands seen: $seen; rest frames $restFrames; peak heat ${"%.0f".format(heatedTo)} °C; max glow below 500 °C = $maxGlowWhileCold; cycle ${"%.1f".format(tSec)} s")
        assertTrue(restFrames > 60)
        assertEquals(0, restWithPositiveGap)             // never sits on the rim with a positive gap
        assertTrue(droppedHot && passedCold && backOnRim)
        assertTrue(heatedTo >= 699.0)
        assertEquals(0f, maxGlowWhileCold, 0f)           // no visible glow below the Draper point
        // the demo's lookup table is Blackbody.glow (emissivity applied at lookup), to table resolution
        for (tc in listOf(530.0, 600.0, 650.0, 700.0)) {
            val core = com.bytedance.physicslab.solar.core.expansion.Blackbody.glow(tc + 273.15, 0.60)
            println("glow at $tc °C: core ${"%.3f".format(core)}")
        }
        // the classic second half: the hot ball is stuck — heat the RING and it drops through
        d.restart(); repeat(30) { d.update(1f / 60f) }
        assertEquals(1, d.restState)
        d.onTap("Lamp")
        var k = 0
        while (d.restState == 1 && k < 60 * 20) { d.update(1f / 60f); k++ }
        println("lamp on the ring: hot ball dropped after ${"%.1f".format(k / 60f)} s (x10) with ring ${"%.0f".format(d.ringC)} °C, ball ${"%.0f".format(d.ballC)} °C, gap ${"%.1f".format(d.clearanceUm)} µm")
        assertTrue(d.restState == 0 && d.ringC > 250 && d.ballC > 400)
        repeat(30) { d.update(1f / 60f) }
        shot("7_ring_heated_ball_through")
        // manual: lift it back up through the hot ring
        d.tryRing(); repeat(60 * 2) { d.update(1f / 60f) }
        println("lifted back: axis ${d.axisCoord}, rest ${d.restState}")
        d.auto = true; d.restart()
    }
}
