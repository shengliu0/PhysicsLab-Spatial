package com.bytedance.physicslab.solar.demos.prism

import com.bytedance.physicslab.solar.core.P3
import com.bytedance.physicslab.solar.core.prism.Glasses
import com.bytedance.physicslab.solar.core.prism.PrismOptics
import com.bytedance.physicslab.solar.lab.spec.Cam
import com.bytedance.physicslab.solar.lab.spec.Node
import com.bytedance.physicslab.solar.lab.spec.SoftRender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Headless checks of the prism demo's states (the shared review renders only the default):
 * drives the panel / gaze setters, checks the readout numbers against core/prism, and writes
 * close-ups to build/review/variants/prism_*.png for the visual review.
 */
class PrismDemoTest {
    private val out = File("build/review/variants")
    private val cache = File("build/review-cache").apply { mkdirs() }

    private fun settle() = repeat(120) { PrismDemo.update(1f / 60f) }

    private fun viewer(name: String) {
        val r = PrismDemo.root!!
        SoftRender.writePng(SoftRender.render(r, SoftRender.framings(r).last()), File(out, "prism_viewer_$name.png"))
    }

    /** Close-up looking straight at a screen card's face. */
    private fun faceShot(node: Node, name: String, dist: Float = 0.45f) {
        val n = node.rot.rotate(floatArrayOf(0f, 0f, 1f))
        val c = node.pos
        val eye = P3(c.x + n[0] * dist, c.y + n[1] * dist, c.z + n[2] * dist)
        SoftRender.writePng(SoftRender.render(PrismDemo.root!!, Cam(eye, c, 40f, name), 960, 720, 2), File(out, "prism_$name.png"))
    }

    @Test
    fun statesRenderAndReadoutsFollowThePhysics() {
        val d = PrismDemo
        d.runHeadless(cache, 1f)
        try {
            val root = d.root!!
            val s1 = root.find("Screen1Mount")!!
            val a = Math.toRadians(60.0)
            // default: dense flint at minimum deviation — readout matches the analytic D_min
            val dmin = Math.toDegrees(PrismOptics.minDeviation(Glasses.SF11.n(589.3), a))
            assertTrue(d.readoutText, d.readoutText.contains("最小偏向 D = %.2f°".format(dmin)))
            faceShot(s1, "screen_SF11"); withLabelDots("prism_labels_single.png")
            SoftRender.writePng(SoftRender.render(root, Cam(P3(-0.20f, 0.25f, 0.75f), P3(-0.25f, 0.22f, 0f), 40f, "p")), File(out, "prism_closeup_SF11.png"))

            // crown glass: short, impure spectrum
            d.applyGlass(0); settle()
            assertTrue(d.readoutText.contains("BK7"))
            faceShot(s1, "screen_BK7"); viewer("BK7")
            d.applyGlass(1); settle(); viewer("F2")

            // turning the prism away from minimum deviation always INCREASES D
            d.applyGlass(2); d.applyInc(12f); settle()
            assertTrue(d.readoutText, d.readoutText.contains("比最小偏向"))
            viewer("rotated")
            // too far the other way: the violet end is lost to total internal reflection first
            d.applyInc(-9f); settle()
            assertTrue(d.readoutText, d.readoutText.contains("全反射"))
            viewer("tir")
            d.applyInc(0f); settle()

            // experimentum crucis
            d.applyCrucis(true); d.applySel(3); settle()
            assertTrue(d.readoutText, d.readoutText.contains("判决性实验") && d.readoutText.contains("绿"))
            viewer("crucis_green"); withLabelDots("prism_labels_crucis.png")
            faceShot(root.find("Screen2Mount")!!, "screen2_green", 0.35f)
            faceShot(s1, "screen1_crucis")
            d.applySel(5); settle(); viewer("crucis_violet")
            faceShot(root.find("Screen2Mount")!!, "screen2_violet", 0.35f)
            d.onTap("Screen1"); assertEquals(0, d.selIdx)
            d.onTap("Prism"); assertEquals(0, d.glassIdx)

            assertTrue(d.isTarget("Prism") && d.isTarget("Screen1") && !d.isTarget("Plinth"))
            root.walk { n -> if (n.tapRadius > 0f) assertTrue("${n.name} ${n.tapRadius}", n.tapRadius >= 0.045f) }
            assertTrue(d.labels.size <= 6)
        } finally {
            d.applyCrucis(false); d.applyGlass(2); d.applyInc(0f); d.applySel(3)
        }
    }

    /** Render [name] from the viewer pose with a small dot at every floating label's anchor. */
    private fun withLabelDots(name: String) {
        val d = PrismDemo
        val r = d.root!!
        val dot = com.bytedance.physicslab.solar.render.MeshGen.sphere(0.012f, 8, 16, com.bytedance.physicslab.solar.render.MeshGen.SphereUv.EQUIRECT)
        val mat = com.bytedance.physicslab.solar.lab.spec.Mat.Unlit(com.bytedance.physicslab.solar.render.Rgb(1f, 0.2f, 0.8f))
        val added = d.labels.filter { it.visible }.map { r.add("LabelDot", dot, mat, it.pos) }
        SoftRender.writePng(SoftRender.render(r, SoftRender.framings(r).last()), File(out, name))
        r.children.removeAll(added.toSet())
    }
}
