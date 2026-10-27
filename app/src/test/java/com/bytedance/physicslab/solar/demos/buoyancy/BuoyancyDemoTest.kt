package com.bytedance.physicslab.solar.demos.buoyancy

import com.bytedance.physicslab.solar.core.buoyancy.Buoy
import com.bytedance.physicslab.solar.lab.spec.Cam
import com.bytedance.physicslab.solar.lab.spec.SoftRender
import com.bytedance.physicslab.solar.core.P3
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Drives the demo object exactly as the headset does (build, tick, tap) and checks what it SHOWS. */
class BuoyancyDemoTest {
    private val cache = File("build/review-cache").apply { mkdirs() }

    /** the demo is a singleton: leave its defaults as found, so LabReviewTest renders the real first view */
    @After fun restoreDefaults() { BuoyancyDemo.objIdx = 0; BuoyancyDemo.fluidIdx = 1; BuoyancyDemo.speedIdx = 1; BuoyancyDemo.lowered = true }

    private fun run(o: Int, f: Int, seconds: Float, render: String? = null): DoubleArray {
        val d = BuoyancyDemo
        d.objIdx = o; d.fluidIdx = f; d.speedIdx = 2; d.lowered = true
        val root = d.buildSpec(cache)
        repeat((seconds * 60).toInt()) { d.update(1f / 60) }
        if (render != null) for (cam in listOf(Cam(P3(0f, 0.10f, 2.5f), P3(0f, 0.02f, 0f), 55f, "viewer"), Cam(P3(0.25f, 0.30f, 1.25f), P3(-0.2f, 0.0f, 0f), 55f, "close")))
            SoftRender.writePng(SoftRender.render(root, cam, 960, 720), File("build/review/extra/buoyancy_${render}_${cam.name}.png"))
        assertTrue(d.labels.size <= 6)
        assertEquals(3, d.readoutText.lines().size)
        return d.forces()
    }

    @Test
    fun everyPairSettlesIntoTheArchimedesBalance() {
        for (o in Buoy.SOLIDS.indices) for (f in Buoy.FLUIDS.indices) {
            val v = run(o, f, 25f)
            val (g, fs, fb, gx) = listOf(v[0], v[1], v[2], v[3])
            println("${Buoy.SOLIDS[o].en} in ${Buoy.FLUIDS[f].en}: G $g  F_scale $fs  F_b $fb  G_overflow $gx")
            assertEquals("G - F_scale = F_b", g - fs, fb, 0.01 * g)
            assertEquals("F_b = G_overflow", fb, gx, 0.01 * g)
            if (Buoy.floats(Buoy.SOLIDS[o].rho, Buoy.FLUIDS[f].rho)) assertEquals(0.0, fs, 1e-9)
            else assertEquals(Buoy.FLUIDS[f].rho * Buoy.G * 0.14 * 0.14 * 0.14, fb, 1e-6)
        }
    }

    @Test
    fun liftingOutAndPouringBack() {
        run(3, 1, 20f, render = "aluminium_water")
        val d = BuoyancyDemo
        val out = d.rig.vOut
        d.onTap("Cube")                                    // lift it out
        repeat(20 * 60) { d.update(1f / 60) }
        val v = d.forces()
        assertEquals(v[0], v[1], 0.01 * v[0])              // the scale reads the full weight again
        assertEquals(out, d.rig.vOut, 1e-9)                // water does not climb back up the spout
        assertEquals(d.rig.spoutY - out / d.rig.tankArea, d.rig.level, 1e-6)
        d.onTap("Beaker")                                  // pour it back: the can is full again
        assertEquals(0.0, d.rig.vOut, 0.0)
        assertEquals(d.rig.spoutY, d.rig.level, 1e-9)
    }

    @Test
    fun renderSettledStatesForReview() {
        run(1, 1, 20f, render = "ice_water")
        run(2, 2, 20f, render = "nylon_brine")
        run(0, 0, 20f, render = "wood_alcohol")
    }
}
