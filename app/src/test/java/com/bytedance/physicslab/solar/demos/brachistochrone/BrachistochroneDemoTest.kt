package com.bytedance.physicslab.solar.demos.brachistochrone

import com.bytedance.physicslab.solar.core.P3
import com.bytedance.physicslab.solar.core.brachistochrone.Cycloid
import com.bytedance.physicslab.solar.lab.spec.Cam
import com.bytedance.physicslab.solar.lab.spec.SoftRender
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.PI
import kotlin.math.abs

/** Drives the demo object like the headset (build, tick at 60 Hz, tap) and checks what it SHOWS. */
class BrachistochroneDemoTest {
    private val cache = File("build/review-cache").apply { mkdirs() }

    /** the demo is a singleton: leave its defaults as found, so LabReviewTest renders the real first view */
    @After fun restoreDefaults() {
        val d = BrachistochroneDemo
        d.mode = 0; d.speedIdx = 1; d.drop = 0.35; d.showWheel = false; d.showDots = true; d.pick = -1
    }
    private fun render(name: String) {
        val root = BrachistochroneDemo.root ?: return
        for (cam in listOf(Cam(P3(0f, 0.10f, 2.5f), P3(0f, 0.02f, 0f), 55f, "viewer"), Cam(P3(0.9f, 0.55f, 1.6f), P3(0f, 0.08f, 0f), 55f, "hero")))
            SoftRender.writePng(SoftRender.render(root, cam, 960, 720), File("build/review/extra/brach_${name}_${cam.name}.png"))
    }

    @Test
    fun raceFinishesInTheExactOrderAndEveryBeadStopsAtTheFinish() {
        val d = BrachistochroneDemo
        d.mode = 0; d.speedIdx = 1; d.showWheel = false; d.pick = -1
        d.runHeadless(cache, 0f)
        d.applyDrop(0.35)
        // run to just after the slowest lane (real time 1.101 s / 0.25 = 4.4 s of wall clock)
        val wall = (d.exact.max() / 0.25 + 0.2).toFloat()
        repeat((wall * 60).toInt()) { d.update(1f / 60) }
        render("finish")
        val order = (0 until 4).sortedBy { d.exact[it] }
        println("finish times ${d.exact.toList()} order $order")
        assertEquals(listOf(0, 1, 2, 3), order)                       // cycloid, cliff, arc, line
        for (i in 0 until 4) {
            val p = d.beads[i].pos
            assertEquals(-0.72f + 1.40f, p.x, 1e-4f); assertEquals(0.40f - 0.35f, p.y, 1e-4f)
            assertEquals(d.exact[i], d.tracks[i]!!.time, 1e-4 * d.exact[i])   // drawn motion (240-pt polyline) vs closed form: < 0.01 %
        }
        assertTrue(d.labels.size <= 6)
        assertTrue(d.labels[0].text.startsWith("①"))
    }

    @Test
    fun aBetIsAnsweredAndTheRaceRestarts() {
        val d = BrachistochroneDemo
        d.mode = 0; d.speedIdx = 2
        d.runHeadless(cache, 0f)
        d.onTap("Bead3")                    // bet on the straight line
        assertEquals(3, d.pick); assertEquals(0.0, d.t, 0.0)
        repeat(90) { d.update(1f / 60) }   // 1.5 s at x1: everyone is home
        assertTrue(d.readoutText.contains("第 4 名"))
        d.pick = -1
    }

    @Test
    fun tautochroneBeadsAllReachTheBottomTogether() {
        val d = BrachistochroneDemo
        d.speedIdx = 1
        d.runHeadless(cache, 0f)
        d.selectMode(1)
        val T = Cycloid.tautochroneTime(0.20, 9.81)
        // step to exactly T (wall = T / 0.25)
        val frames = (T / 0.25 * 60).toInt()
        repeat(frames) { d.update(1f / 60) }
        d.update(((T - d.t) / 0.25).toFloat())
        assertEquals(T, d.t, 1e-6)
        for (i in 0 until 4) {
            assertEquals("bead $i x", 0f, d.beads[i].pos.x, 2e-4f)
            assertEquals("bead $i y", 0.38f - 0.40f, d.beads[i].pos.y, 2e-4f)
        }
        render("tauto_T")
        // and again at 3T (second meeting)
        repeat(2) { _ -> d.update((2 * T / 0.25 / 2).toFloat()) }
        for (i in 0 until 4) assertEquals(0f, d.beads[i].pos.x, 5e-4f)
        assertTrue(abs(d.t - 3 * T) < 1e-6)
        // tapping a bead changes ITS height and still lands together
        d.onTap("Bead2")
        repeat(frames) { d.update(1f / 60) }
        d.update(((T - d.t) / 0.25).toFloat())
        for (i in 0 until 4) assertEquals(0f, d.beads[i].pos.x, 2e-4f)
        repeat(4) { d.onTap("Bead2") }      // 5 presets: back to the default height
        d.selectMode(0)
        assertTrue(PI > 3)
    }
}
