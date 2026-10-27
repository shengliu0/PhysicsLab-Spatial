// ToScaleView — 真实比例: the solar system with sizes AND distances on ONE ruler. Neptune's orbit spans the
// volume (1 AU = 5.39 cm), so the Sun is 0.5 mm across and Earth 4.6 micrometres: the true picture is
// empty space. Planets sit on one line (an alignment, for comparison only), each orbit drawn as the arc
// that fits the volume. Magnifier bubbles show each planet at its true spot, enlarged x N (stated).
// Fully static: built once, nothing changes per frame except the selection highlight.

package com.bytedance.physicslab.solar.demos.solar

import com.bytedance.physicslab.solar.core.P3
import com.bytedance.physicslab.solar.core.ScaleFacts
import com.bytedance.physicslab.solar.core.Sky
import com.bytedance.physicslab.solar.core.Solar
import com.bytedance.physicslab.solar.lab.Q
import com.bytedance.physicslab.solar.lab.spec.Blend
import com.bytedance.physicslab.solar.lab.spec.Mat
import com.bytedance.physicslab.solar.lab.spec.Node
import com.bytedance.physicslab.solar.render.MeshGen
import com.bytedance.physicslab.solar.render.Palette
import com.bytedance.physicslab.solar.render.ProcTex
import com.bytedance.physicslab.solar.render.Rgb
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

internal class ToScaleView(private val tex: SolarTex) {
    companion object {
        const val SUN_X = -0.80f
        const val LINE_Y = 0.10f
        /** metres per AU in the volume: Neptune's mean distance 30.07 AU -> 1.62 m */
        val M_PER_AU = 1.62f / Solar.planets[7].a.toFloat()
        /** magnifier strengths: rocky planets and giants (one per row, stated on the row) */
        const val N_ROCKY = 6000f
        const val N_GIANT = 1500f
        const val TILT_DEG = 20f
        fun mag(i: Int) = if (i < 4) N_ROCKY else N_GIANT
        fun planetX(i: Int) = SUN_X + Solar.planets[i].a.toFloat() * M_PER_AU
        /** true radius in the volume (m) */
        fun trueR(i: Int) = (ScaleFacts.radiusKm(i) / Sky.AU_KM).toFloat() * M_PER_AU
        val BUBBLE_POS = arrayOf(
            P3(-0.84f, -0.075f, 0.06f), P3(-0.73f, -0.075f, 0.06f), P3(-0.62f, -0.075f, 0.06f), P3(-0.51f, -0.075f, 0.06f),
            P3(-0.53f, 0.30f, 0.0f), P3(-0.26f, 0.30f, 0.0f), P3(0.23f, 0.30f, 0.0f), P3(0.80f, 0.30f, 0.0f))
    }

    var selected = -1
    lateinit var root: Node; private set
    val bubbles = ArrayList<Node>()
    private val rims = ArrayList<Node>()
    private var selShown = -2
    lateinit var sun: Node; private set
    val bubbleR = FloatArray(8)

    fun build(parent: Node): Node {
        bubbles.clear(); rims.clear(); selected = -1; selShown = -2
        root = parent.add(Node("TS_Root"))
        val axis = root.add(Node("TS_Axis")).also { it.pos = P3(0f, LINE_Y, 0f); it.rot = Q.axisAngle(1f, 0f, 0f, Math.toRadians(TILT_DEG.toDouble()).toFloat()) }
        val sphere = MeshGen.sphere(1f, 32, 64, MeshGen.SphereUv.EQUIRECT)
        val tiny = MeshGen.sphere(1f, 6, 12, MeshGen.SphereUv.EQUIRECT)      // true-size bodies: microns, never resolved
        val shellMesh = MeshGen.sphere(1f, 32, 32, MeshGen.SphereUv.POLAR_U)
        val cyl = MeshGen.cylinder(1f, 1f, 8)

        // ---- the ruler: a faint line Sun -> Neptune with a tick every 5 AU ----
        axis.add("TS_Line", cyl, Mat.Unlit(Rgb(0.62f, 0.68f, 0.85f), alpha = 0.35f, blend = Blend.TRANSPARENT)).also {
            it.rot = Q.axisAngle(0f, 0f, 1f, (-PI / 2).toFloat())
            it.pos = P3((SUN_X + 0.84f) / 2, 0f, 0f); it.scale = P3(0.0010f, 0.84f - SUN_X, 0.0010f)
        }
        for (k in 1..6) axis.add("TS_AuTick_$k", cyl, Mat.Unlit(Rgb(0.62f, 0.68f, 0.85f), alpha = 0.45f, blend = Blend.TRANSPARENT)).also {
            it.pos = P3(SUN_X + 5f * k * M_PER_AU, 0f, 0f); it.scale = P3(0.0010f, 0.016f, 0.0010f)
        }

        // ---- the Sun: true size (0.25 mm radius) + a glow so it can be found ----
        sun = axis.add(Node("TS_Sun")).also { it.pos = P3(SUN_X, 0f, 0f); it.tapRadius = 0.045f }
        sun.add("TS_SunBody", tiny, Mat.Unlit(tex = tex.sunA)).uniformScale(max(trueR(9), 2.5e-4f))
        sun.add("TS_SunGlow", SolarArt.quad(1f), Mat.Unlit(Rgb(1f, 0.85f, 0.55f), alpha = 1f, tex = tex.glow, blend = Blend.ADD)).also { it.billboard = true; it.uniformScale(0.05f) }
        sun.add("TS_SunCore", SolarArt.quad(1f), Mat.Unlit(Rgb(1f, 0.97f, 0.85f), alpha = 1f, tex = tex.glow, blend = Blend.ADD)).also { it.billboard = true; it.uniformScale(0.014f) }

        // ---- orbits: the arc of each true circle that fits inside the volume ----
        for (i in 0 until 8) {
            val r = Solar.planets[i].a.toFloat() * M_PER_AU
            val tLeft = if (SUN_X - r < -0.88f) acos(((-0.88f - SUN_X) / r).coerceIn(-1f, 1f)) else PI.toFloat()
            val tZ = if (r > 0.55f) asin(0.55f / r) else PI.toFloat()
            // short arcs for the outer orbits: enough curvature to read as an orbit, without a tangle of ellipses
            val tMax = min(min(tLeft, tZ), if (r > 0.12f) 0.62f else PI.toFloat())
            val full = tMax >= PI.toFloat() - 1e-4f
            val n = if (full) 96 else 64
            val path = FloatArray(n * 3) { k ->
                val th = if (full) 2 * PI * (k / 3) / n else (-tMax + 2 * tMax * (k / 3) / (n - 1)).toDouble()
                when (k % 3) { 0 -> SUN_X + r * cos(th).toFloat(); 1 -> 0f; else -> -r * sin(th).toFloat() }
            }
            axis.add("TS_Orbit_$i", MeshGen.tube(path, if (i < 4) 0.0009f else 0.0014f, 5, full),
                Mat.Unlit(Palette.orbit(i), alpha = 0.55f, blend = Blend.TRANSPARENT))
            // the planet itself, TRUE size (micrometres: drawn, invisible — that is the point) + a tick
            axis.add("TS_Planet_$i", tiny, Mat.Unlit(tex = tex.planets[i])).also { it.pos = P3(planetX(i), 0f, 0f); it.uniformScale(max(trueR(i), 1e-6f)) }
            axis.add("TS_Tick_$i", cyl, Mat.Unlit(Palette.orbit(i), alpha = 0.95f, blend = Blend.TRANSPARENT)).also {
                it.pos = P3(planetX(i), if (i < 4) -0.010f else 0.010f, 0f); it.scale = P3(0.0016f, 0.020f, 0.0016f)
            }
        }

        // ---- magnifier bubbles (upright, volume space) + leader lines to the true spot ----
        val glass = Mat.Pbr(Rgb(0.80f, 0.88f, 1f), metallic = 0f, roughness = 0.06f, alpha = 0.10f)
        val shellMat = Mat.Unlit(tex = tex.light, blend = Blend.TRANSPARENT)
        val toSun = floatArrayOf(-1f, 0.15f, 0.25f)
        for (i in 0 until 8) {
            val p = Solar.planets[i]
            val r = trueR(i) * mag(i)
            val ringOut = if (p.hasRings) r * (if (i == 5) 2.27f else 1.95f) else r
            val br = max(0.045f, ringOut * 1.18f + 0.008f)
            bubbleR[i] = br
            val b = root.add(Node("TS_Bubble_$i")).also { it.pos = BUBBLE_POS[i]; it.tapRadius = br }
            b.add("TS_Glass_$i", sphere, glass).uniformScale(br)
            rims += b.add("TS_Rim_$i", SolarArt.ring(br * 0.97f, br * 1.03f, 64), rimMat(false)).also { it.billboard = true }
            val tilt = b.add(Node("TS_Tilt_$i")).also { it.rot = Q.axisAngle(0f, 0f, 1f, Math.toRadians(p.axialTiltDeg).toFloat()) * Q.axisAngle(1f, 0f, 0f, 0.35f) }
            tilt.add("TS_Body_$i", sphere, Mat.Unlit(tex = tex.planets[i])).uniformScale(r)
            b.add("TS_Shell_$i", shellMesh, shellMat).also { it.uniformScale(r * 1.006f); it.rot = Q.fromTo(0f, 1f, 0f, toSun[0], toSun[1], toSun[2]) }
            if (p.hasRings) {
                val saturn = i == 5
                val tint = ProcTex.okLerp(Palette.mid[i], Rgb(1f, 1f, 1f), 0.45f)
                tilt.add("TS_Rings_$i", MeshGen.annulus(r * if (saturn) 1.28f else 1.55f, r * if (saturn) 2.27f else 1.95f, 96),
                    Mat.Unlit(tint, alpha = if (saturn) 1f else 0.35f, tex = tex.ring, blend = Blend.TRANSPARENT))
            }
            // leader: from the planet's true position (on the tilted line) to the bubble's edge
            val a = P3(planetX(i), LINE_Y, 0f)
            val c = BUBBLE_POS[i]
            val dx = c.x - a.x; val dy = c.y - a.y; val dz = c.z - a.z
            val d = sqrt(dx * dx + dy * dy + dz * dz)
            val len = max(0.005f, d - br)
            root.add("TS_Leader_$i", cyl, Mat.Unlit(Palette.orbit(i), alpha = 0.55f, blend = Blend.TRANSPARENT)).also {
                it.rot = Q.fromTo(0f, 1f, 0f, dx, dy, dz)
                it.pos = P3(a.x + dx / d * len / 2, a.y + dy / d * len / 2, a.z + dz / d * len / 2)
                it.scale = P3(0.0011f, len, 0.0011f)
            }
            bubbles += b
        }
        update()
        return root
    }

    private fun rimMat(on: Boolean) = Mat.Unlit(if (on) Rgb(1f, 0.82f, 0.40f) else Rgb(0.50f, 0.74f, 1f), alpha = if (on) 0.95f else 0.50f, blend = Blend.ADD)

    /** Nothing moves; only the selection ring changes (one setMat per change). */
    fun update() {
        if (selected == selShown) return
        selShown = selected
        for (i in 0 until 8) rims[i].setMat(rimMat(i == selected))
    }
}
