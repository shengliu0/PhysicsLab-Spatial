package com.bytedance.physicslab.solar.demos.young

import com.bytedance.physicslab.solar.core.P3
import com.bytedance.physicslab.solar.lab.spec.Cam
import com.bytedance.physicslab.solar.lab.spec.SoftRender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Headless checks of the double-slit demo's states (the shared review renders only the default):
 * builds, drives the same setters the panel / gaze taps use, and writes close-ups to
 * build/review/variants/young_*.png for the visual review.
 */
class YoungDemoTest {
    private val out = File("build/review/variants")
    private val cache = File("build/review-cache").apply { mkdirs() }

    private fun shot(name: String, eye: P3, target: P3, fov: Float = 40f) {
        val root = YoungDemo.root ?: return
        SoftRender.writePng(SoftRender.render(root, Cam(eye, target, fov, name), 960, 720, 2), File(out, "young_$name.png"))
    }

    @Test
    fun statesRenderAndReadoutsFollowThePhysics() {
        val d = YoungDemo
        d.runHeadless(cache, 0.5f)
        try {
            val screenEye = P3(-0.33f, 0.13f, 0.20f); val screenAt = P3(0.40f, 0.13f, -0.40f)
            val insetEye = P3(-0.50f, 0.315f, 1.05f); val insetAt = P3(-0.50f, 0.315f, 0.16f)
            shot("screen_green", screenEye, screenAt)
            shot("inset_green", insetEye, insetAt); withLabelDots("young_labels.png")
            assertTrue(d.readoutText.contains("Δy = λL/d = 2.13 mm"))

            d.applyCover(0, true); repeat(30) { d.update(1f / 60f) }
            assertTrue(d.readoutText.contains("只开一条缝"))
            shot("screen_oneSlit", screenEye, screenAt); shot("inset_oneSlit", insetEye, insetAt)
            d.applyCover(0, false)

            d.applyWhite(true); repeat(30) { d.update(1f / 60f) }
            assertTrue(d.readoutText.contains("白光"))
            shot("screen_white", screenEye, screenAt)
            SoftRender.writePng(SoftRender.render(d.root!!, SoftRender.framings(d.root!!).last()), File(out, "young_viewer_white.png"))

            d.applyD(2); repeat(30) { d.update(1f / 60f) }; shot("inset_d040", insetEye, insetAt)
            d.applyLambda(650f); d.applyD(0); d.applyL(0.6f); repeat(120) { d.update(1f / 60f) }
            shot("inset_d015_red", insetEye, insetAt)
            assertTrue(d.readoutText, d.readoutText.contains("Δy = λL/d = 2.60 mm"))   // 650e-9 * 0.6 / 0.15e-3
            SoftRender.writePng(SoftRender.render(d.root!!, SoftRender.framings(d.root!!).last()), File(out, "young_viewer_red.png"))

            // gaze taps cycle the presets
            d.onTap("Filter"); assertEquals(532f, d.lambdaNm, 0.01f)
            d.onTap("Filter"); d.onTap("Filter"); assertTrue(d.white)
            d.onTap("Screen"); assertEquals(0.85f, d.lTarget, 1e-6f)
            assertTrue(d.isTarget("ShutterTop") && d.isTarget("ShutterBot") && !d.isTarget("Rail"))
            // every gaze target is >= 45 mm in world (all at scale 1)
            d.root!!.walk { n -> if (n.tapRadius > 0f) assertTrue("${n.name} ${n.tapRadius}", n.tapRadius >= 0.045f) }
            assertTrue(d.labels.size <= 6)
        } finally {
            d.applyWhite(false); d.applyLambda(532f); d.applyD(1); d.applyL(1.0f)
            d.applyCover(0, false); d.applyCover(1, false)
        }
    }

    /** Render [name] from the viewer pose with a small dot at every floating label's anchor. */
    private fun withLabelDots(name: String) {
        val d = YoungDemo
        val r = d.root!!
        val dot = com.bytedance.physicslab.solar.render.MeshGen.sphere(0.012f, 8, 16, com.bytedance.physicslab.solar.render.MeshGen.SphereUv.EQUIRECT)
        val mat = com.bytedance.physicslab.solar.lab.spec.Mat.Unlit(com.bytedance.physicslab.solar.render.Rgb(1f, 0.2f, 0.8f))
        val added = d.labels.filter { it.visible }.map { r.add("LabelDot", dot, mat, it.pos) }
        SoftRender.writePng(SoftRender.render(r, SoftRender.framings(r).last()), File(out, name))
        r.children.removeAll(added.toSet())
    }
}
