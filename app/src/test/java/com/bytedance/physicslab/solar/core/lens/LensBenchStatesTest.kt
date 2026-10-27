package com.bytedance.physicslab.solar.core.lens

import com.bytedance.physicslab.solar.core.P3
import com.bytedance.physicslab.solar.core.lens.ThinLens.Case
import com.bytedance.physicslab.solar.demos.lens.LensArt
import com.bytedance.physicslab.solar.demos.lens.LensDemo
import com.bytedance.physicslab.solar.lab.spec.Cam
import com.bytedance.physicslab.solar.lab.spec.SoftRender
import com.bytedance.physicslab.solar.render.Image
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

/**
 * The convex-lens bench driven like a user would (pinch taps, pinch-drags, panel presets) — checks
 * that what the demo SHOWS follows core/lens, and writes close-ups to build/review/variants/lens_*.png
 * for the visual review (the shared review renders only the 2.5 m view).
 */
class LensBenchStatesTest {
    private val out = File("build/review/variants").apply { mkdirs() }
    private val cache = File("build/review-cache").apply { mkdirs() }
    private val yaw = Math.toRadians(25.0).toFloat()

    /** Bench-local point -> volume (the bench is yawed 25° about +Y, lens at the origin). */
    private fun w(x: Float, y: Float, z: Float) = P3(x * cos(yaw) + z * sin(yaw), y, -x * sin(yaw) + z * cos(yaw))

    private fun shot(name: String, eye: P3, target: P3, fov: Float = 34f) {
        val root = LensDemo.root ?: return
        SoftRender.writePng(SoftRender.render(root, Cam(eye, target, fov, name), 960, 720, 2), File(out, "lens_$name.png"))
    }

    /** Settle the easing, then check the whole scene stays inside the volume (|x| < 0.9, |z| < 0.6, y < 0.45). */
    private fun settle() {
        repeat(90) { LensDemo.update(1f / 60f) }
        val (lo, hi) = SoftRender.bounds(LensDemo.root!!)
        assertTrue("bounds $lo .. $hi", lo.x > -0.9f && hi.x < 0.9f && lo.z > -0.6f && hi.z < 0.6f && hi.y < 0.45f && lo.y > -0.27f)
    }

    @Test
    fun benchFollowsTheThinLensAndRendersEveryCase() {
        val d = LensDemo
        d.runHeadless(cache, 0.5f)
        // default: f = 10, u = 15 -> v = 30, m = -2, screen in focus
        assertEquals(30.0, d.vCm, 1e-9)
        assertTrue(d.sharp)
        assertTrue(d.readoutText, d.readoutText.contains("v = 30.0 cm") && d.readoutText.contains("m = −v/u = −2.00"))
        val scr = w(0.48f, 0.04f, 0f)
        shot("screen_projector", P3(scr.x - 0.60f, 0.16f, scr.z + 0.95f), scr, 32f)
        shot("bench_projector", P3(-0.10f, 0.35f, 1.65f), P3(0.05f, 0.05f, 0f), 44f)

        // pinch the candle: the five textbook cases in order, the screen auto-focuses on each real image
        val seen = ArrayList<Case>()
        repeat(5) {
            d.onTap("Candle"); settle()
            val c = ThinLens.classify(d.uT, d.f); seen += c
            val v = ThinLens.imageDistance(d.uT, d.f)
            println("tap candle -> %-9s u = %5.2f  v = %s  screen %5.2f  %s".format(c, d.uT, if (v.isInfinite()) "∞" else "%.2f".format(v), d.sT,
                d.readoutText.lines()[1]))
            if (c == Case.BEYOND_2F || c == Case.AT_2F || c == Case.BETWEEN) { assertEquals(v, d.sT, 1e-9); assertTrue(d.sharp) }
            if (c == Case.BEYOND_2F) shot("screen_camera", P3(scr.x - 0.55f, 0.16f, scr.z + 0.90f), w(0.24f, 0.06f, 0f), 34f)
            if (c == Case.INSIDE_F) {
                assertTrue(d.vCm < 0); assertFalse(d.sharp)
                shot("virtual", P3(-0.35f, 0.28f, 1.45f), w(-0.12f, 0.14f, 0f), 44f)
            }
            if (c == Case.AT_F) { assertTrue(d.vCm.isInfinite()); shot("parallel", P3(-0.10f, 0.35f, 1.65f), P3(0.05f, 0.05f, 0f), 44f) }
        }
        assertEquals(listOf(Case.AT_F, Case.INSIDE_F, Case.BEYOND_2F, Case.AT_2F, Case.BETWEEN), seen)

        // the u = 2f case: equal size, v = 2f
        d.setCase(Case.AT_2F); settle()
        assertEquals(20.0, d.vCm, 1e-9); assertTrue(d.readoutText.contains("m = −v/u = −1.00"))

        // drag the screen 6 cm too far: blurred, c = D·|s − v|/v; release near focus -> snaps sharp
        d.setCase(Case.BETWEEN); settle()
        d.onDragStart("Screen")
        val c35 = cos(yaw)
        repeat(10) { d.onDrag("Screen", 0.6f * 0.016f / 1.2f / c35, 0f, 0f); d.update(1f / 60f) }   // +6 cm along the rail
        d.onDragEnd("Screen")
        settle()
        assertEquals(36.0, d.sT, 1e-3)
        assertEquals(7.0 * 6.0 / 30.0 * 10, d.blurMm, 1e-6)          // D = 7 cm: c = 14 mm
        assertTrue(d.readoutText, d.readoutText.contains("14 mm"))
        shot("screen_blurred", P3(scr.x - 0.55f, 0.16f, scr.z + 0.95f), w(0.52f, 0.04f, 0f), 34f)
        d.onDragStart("Screen")
        repeat(10) { d.onDrag("Screen", -0.55f * 0.016f / 1.2f / c35, 0f, 0f) }     // back 5.5 cm: c = 1.2 mm
        d.onDragEnd("Screen"); settle()
        assertEquals(30.0, d.sT, 1e-9); assertTrue(d.sharp)                // snapped into focus

        // the lens cycles 10 -> 15 -> 20 -> 10 cm; the screen tap focuses or explains
        d.onTap("Lens"); assertEquals(15.0, d.f, 0.0)
        settle(); println("f = 15 at u = 15 (= f): " + d.readoutText.lines()[1]); assertTrue(d.vCm.isInfinite())
        d.onTap("Lens"); settle(); assertTrue(d.vCm < 0)                   // u = 15 < f = 20: virtual
        println("tap screen with a virtual image -> " + d.tapMessage("Screen"))
        d.onTap("Lens"); settle(); assertEquals(10.0, d.f, 0.0)

        // gaze targets >= 45 mm (no scaled parents), <= 6 labels
        d.root!!.walk { n -> if (n.tapRadius > 0f) assertTrue("${n.name} ${n.tapRadius}", n.tapRadius >= 0.045f) }
        assertTrue(d.labels.size <= 6)

        // the baked blur levels, side by side (sanity view of the textures)
        val lv = LensArt.blurLevels()
        val strip = Image(LensArt.TW * lv.size, LensArt.TH)
        for ((k, img) in lv.withIndex()) for (y in 0 until LensArt.TH) for (x in 0 until LensArt.TW) strip.px[y * strip.w + k * LensArt.TW + x] = img.px[y * LensArt.TW + x]
        SoftRender.writePng(strip, File(out, "lens_blur_levels.png"))
    }
}
