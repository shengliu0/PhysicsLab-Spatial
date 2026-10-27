package com.bytedance.physicslab.solar.core.vsepr

import com.bytedance.physicslab.solar.demos.vsepr.VseprDemo
import com.bytedance.physicslab.solar.lab.spec.Node
import com.bytedance.physicslab.solar.lab.spec.SoftRender
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The molecule is the hero: big (bonds ~0.3 m) yet never outside the volume's action band
 * (-0.20 < y < 0.42 m) for any preset, lone-pair balloons included; the two pads stay clear of it.
 */
class VseprStageTest {
    private fun under(n: Node, name: String): Boolean { var p: Node? = n; while (p != null) { if (p.name == name) return true; p = p.parent }; return false }

    /** World bounds of the visible meshes under [sub] (pinch halos excluded). */
    private fun bounds(root: Node, sub: String): Pair<FloatArray, FloatArray> {
        val lo = floatArrayOf(1e9f, 1e9f, 1e9f); val hi = floatArrayOf(-1e9f, -1e9f, -1e9f)
        SoftRender.forEachMesh(root) { n, w ->
            if (n.name.startsWith("Halo_") || !under(n, sub)) return@forEachMesh
            val m = n.mesh!!
            for (k in 0 until m.vertexCount) {
                val p = w.apply(m.pos[k * 3], m.pos[k * 3 + 1], m.pos[k * 3 + 2])
                for (a in 0 until 3) { lo[a] = minOf(lo[a], p[a]); hi[a] = maxOf(hi[a], p[a]) }
            }
        }
        return lo to hi
    }

    @Test
    fun everyPresetFillsTheBandWithoutLeavingIt() {
        val root = VseprDemo.runHeadless(File("build/review-cache").apply { mkdirs() }, 1f)
        val pads = listOf(bounds(root, "PadBondG"), bounds(root, "PadLoneG"))
        val tb = root.find("Trackball")!!; val tw = SoftRender.worldOf(tb).t
        for ((k, p) in VseprData.presets.withIndex()) {
            VseprDemo.choosePreset(k); repeat(300) { VseprDemo.update(1f / 60f) }
            val (lo, hi) = bounds(root, "Mol")
            val span = maxOf(hi[0] - lo[0], hi[2] - lo[2])
            println("VSEPR stage ${p.formula}: y %.3f .. %.3f   x %.3f .. %.3f   z %.3f .. %.3f   widest %.2f m".format(lo[1], hi[1], lo[0], hi[0], lo[2], hi[2], span))
            assertTrue("${p.formula} pokes above the band: ${hi[1]}", hi[1] <= 0.42f)
            assertTrue("${p.formula} sinks below the band: ${lo[1]}", lo[1] >= -0.20f)
            assertTrue("${p.formula} too small: ${hi[1] - lo[1]} x $span", maxOf(hi[1] - lo[1], span) > 0.45f)
            for ((plo, phi) in pads) assertTrue("${p.formula} overlaps a pad", phi[0] < lo[0] || plo[0] > hi[0])
            // no atom comes within 3 cm of the trackball
            var near = 1f
            SoftRender.forEachMesh(root) { n, w ->
                if (n.name.startsWith("Halo_") || !under(n, "Mol")) return@forEachMesh
                val m = n.mesh!!
                for (k in 0 until m.vertexCount) { val q = w.apply(m.pos[k * 3], m.pos[k * 3 + 1], m.pos[k * 3 + 2])
                    near = minOf(near, kotlin.math.sqrt((q[0] - tw[0]) * (q[0] - tw[0]) + (q[1] - tw[1]) * (q[1] - tw[1]) + (q[2] - tw[2]) * (q[2] - tw[2]))) }
            }
            assertTrue("${p.formula} touches the trackball (%.3f m)".format(near), near > 0.032f + 0.03f)
        }
        // a tetrahedron is ~0.6 m across, as asked
        VseprDemo.choosePreset(2); repeat(300) { VseprDemo.update(1f / 60f) }
        val (lo, hi) = bounds(root, "Mol")
        assertTrue("CH4 span ${hi[0] - lo[0]}", maxOf(hi[0] - lo[0], hi[1] - lo[1], hi[2] - lo[2]) in 0.50f..0.75f)
    }
}
