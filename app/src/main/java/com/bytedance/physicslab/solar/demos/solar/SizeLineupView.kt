// SizeLineupView — 大小对比: the Sun, the planets and the Moon at TRUE relative sizes, lined up. Three
// stages are three zoom levels of the same lineup (one scale for every body at every instant):
//   1  the Sun fills the back; Jupiter and Saturn in front; the rest are specks inside the magnifier ring
//   2  zoom x3: Jupiter and Saturn vs Uranus, Neptune and the specks (Earth, Venus, Mars, Mercury, Moon)
//   3  zoom x5 more: the rocky worlds, with Neptune as the wall behind them
// Stage changes animate scale (in log space) and positions over 1.6 s; at rest nothing is edited per frame.

package com.bytedance.physicslab.solar.demos.solar

import com.bytedance.physicslab.solar.core.P3
import com.bytedance.physicslab.solar.core.ScaleFacts
import com.bytedance.physicslab.solar.lab.Q
import com.bytedance.physicslab.solar.lab.spec.Blend
import com.bytedance.physicslab.solar.lab.spec.Mat
import com.bytedance.physicslab.solar.lab.spec.Node
import com.bytedance.physicslab.solar.render.MeshGen
import com.bytedance.physicslab.solar.render.Palette
import com.bytedance.physicslab.solar.render.ProcTex
import com.bytedance.physicslab.solar.render.Rgb
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

internal class SizeLineupView(private val tex: SolarTex) {
    companion object {
        /** metres per Earth radius at each stage */
        val K = floatArrayOf(0.0066f, 0.0140f, 0.068f)
        const val SUN = 9; const val MOON = 8
        /** body order in the lineup: biggest first */
        val ORDER = intArrayOf(SUN, 4, 5, 6, 7, 2, 1, 3, 0, MOON)
        private val OFF = P3(-9f, 0f, -6f)
        /** per stage: centre of every body (index 0..9); OFF-like far positions = not on stage */
        val POS: Array<Array<P3>> = arrayOf(
            // stage 1: the Sun (r 0.72 m) rises from the back-left, only its upper right limb inside the volume
            arrayOf(P3(0.650f, 0.0f, 0.14f), P3(0.613f, 0.0f, 0.14f), P3(0.590f, 0.0f, 0.14f), P3(0.633f, 0.0f, 0.14f),
                P3(0.36f, 0.19f, 0.12f), P3(0.585f, 0.19f, 0.12f), P3(0.765f, 0.19f, 0.12f), P3(0.835f, 0.19f, 0.12f),
                P3(0.664f, 0.0f, 0.14f), P3(-0.50f, -0.42f, -0.95f)),
            arrayOf(P3(0.648f, 0.12f, 0.06f), P3(0.580f, 0.12f, 0.06f), P3(0.535f, 0.12f, 0.06f), P3(0.620f, 0.12f, 0.06f),
                P3(-0.60f, 0.12f, -0.04f), P3(-0.15f, 0.12f, -0.04f), P3(0.27f, 0.12f, 0.04f), P3(0.42f, 0.12f, 0.04f),
                P3(0.668f, 0.12f, 0.06f), P3(-2.8f, -0.05f, -1.8f)),
            arrayOf(P3(0.355f, 0.12f, 0.08f), P3(0.08f, 0.12f, 0.08f), P3(-0.10f, 0.12f, 0.08f), P3(0.24f, 0.12f, 0.08f),
                P3(-2.2f, 0.12f, -0.8f), P3(-1.5f, 0.12f, -0.7f), P3(-1.25f, 0.10f, -0.45f), P3(-0.60f, 0.10f, -0.30f),
                P3(0.45f, 0.12f, 0.08f), OFF),
        )
        /** which bodies are on stage (visible at rest) */
        val ON = arrayOf(
            booleanArrayOf(true, true, true, true, true, true, true, true, true, true),
            booleanArrayOf(true, true, true, true, true, true, true, true, true, false),
            booleanArrayOf(true, true, true, true, false, false, false, true, true, false))
        /** which bodies are pinchable per stage (big enough and not crowded) */
        val PINCH = arrayOf(intArrayOf(SUN, 4, 5), intArrayOf(4, 5, 6, 7), intArrayOf(2, 1, 3, 0, MOON, 7))
        /** the magnifier ring: centre, radius per stage (stage 3: "back to the Sun") */
        val LENS = arrayOf(P3(0.627f, 0.0f, 0.14f) to 0.062f, P3(0.600f, 0.12f, 0.06f) to 0.088f, P3(0.66f, 0.12f, 0.08f) to 0.065f)
        /** studio key light (upper-left-front): the lineup is about SIZE, so every body shows its day side */
        val KEY = floatArrayOf(-0.55f, 0.40f, 0.75f)
        const val TRANSITION_S = 1.6f
    }

    var stage = 0; private set
    private var from = 0
    private var t = 1f                  // transition progress 0..1 (1 = at rest)
    var selected = -1
    lateinit var root: Node; private set
    val bodies = ArrayList<Node>()      // index 0..9
    /** the pinch node of each body: the body itself, except the Sun (a spot on its visible upper-right face) */
    private val hits = ArrayList<Node>()
    private val scaled = ArrayList<Node>()
    lateinit var lens: Node; private set
    private lateinit var lensRing: Node
    private var clock = 0.0
    private lateinit var sunBMat: Mat.Unlit
    private lateinit var sunB: Node
    private var pinchShown = -1
    private var settled = false

    fun build(parent: Node): Node {
        bodies.clear(); scaled.clear(); hits.clear(); stage = 0; from = 0; t = 1f; selected = -1; clock = 0.0; pinchShown = -1; settled = false
        root = parent.add(Node("SZ_Root"))
        val sphere = MeshGen.sphere(1f, 48, 96, MeshGen.SphereUv.EQUIRECT)
        val shellMesh = MeshGen.sphere(1f, 48, 48, MeshGen.SphereUv.POLAR_U)
        val shellMat = Mat.Unlit(tex = tex.light, blend = Blend.TRANSPARENT)
        for (i in 0..9) {
            val b = root.add(Node(if (i == SUN) "SZ_SunRoot" else "SZ_Body_$i"))
            val s = b.add(Node("SZ_Scale_$i"))
            hits += if (i == SUN) s.add(Node("SZ_Body_9")).also { it.pos = P3(0.451f, 0.551f, 0.702f) } else b
            if (i == SUN) {
                val spin = s.add(Node("SZ_SunSpin"))
                spin.add("SZ_SunA", sphere, Mat.Unlit(tex = tex.sunA))
                sunBMat = Mat.Unlit(tex = tex.sunB, alpha = 0f, blend = Blend.TRANSPARENT)
                sunB = spin.add("SZ_SunB", sphere, sunBMat).also { it.uniformScale(1.0015f) }
                val v = s.add(Node("SZ_SunView")).also { it.billboard = true }
                v.add("SZ_SunLimb", MeshGen.sphere(1f, 48, 48, MeshGen.SphereUv.POLAR_U), Mat.Unlit(tex = tex.limb, blend = Blend.TRANSPARENT)).also {
                    it.rot = Q.axisAngle(1f, 0f, 0f, (PI / 2).toFloat()); it.uniformScale(1.003f)
                }
                v.add("SZ_SunCorona", SolarArt.quad(2 * 2.3f), Mat.Unlit(Rgb(0.8f, 0.8f, 0.8f), tex = tex.corona, blend = Blend.ADD))
            } else {
                val tiltDeg = if (i < 8) com.bytedance.physicslab.solar.core.Solar.planets[i].axialTiltDeg else 6.7
                val tilt = s.add(Node("SZ_Tilt_$i")).also { it.rot = Q.axisAngle(0f, 0f, 1f, Math.toRadians(tiltDeg).toFloat()) * Q.axisAngle(1f, 0f, 0f, 0.30f) }
                tilt.add("SZ_Surf_$i", sphere, Mat.Unlit(tex = if (i == MOON) tex.moonHi else tex.planets[i]))
                s.add("SZ_Shell_$i", shellMesh, shellMat).also { it.uniformScale(1.006f); it.rot = Q.fromTo(0f, 1f, 0f, KEY[0], KEY[1], KEY[2]) }
                if (i == 5 || i == 6) {
                    val sat = i == 5
                    val tint = ProcTex.okLerp(Palette.mid[i], Rgb(1f, 1f, 1f), 0.45f)
                    tilt.add("SZ_Rings_$i", MeshGen.annulus(if (sat) 1.28f else 1.55f, if (sat) 2.27f else 1.95f, 128),
                        Mat.Unlit(tint, alpha = if (sat) 1f else 0.35f, tex = tex.ring, blend = Blend.TRANSPARENT))
                }
            }
            bodies += b; scaled += s
        }
        lens = root.add(Node("SZ_Lens")).also { it.billboard = true }
        lensRing = lens.add("SZ_LensRing", SolarArt.ring(0.94f, 1f, 72), Mat.Unlit(Rgb(1f, 0.80f, 0.38f), alpha = 0.85f, blend = Blend.ADD))
        apply(0f)
        return root
    }

    fun go(s: Int, animate: Boolean = true) {
        val n = s.coerceIn(0, 2)
        if (n == stage && t >= 1f) return
        if (t < 1f) apply(1f)                 // finish a running transition first
        from = stage
        stage = n; t = if (animate) 0f else 1f; settled = false
        selected = -1
    }

    fun next() = go((stage + 1) % 3)

    val transitioning get() = t < 1f
    /** Volume position of body i's pinch spot (labels go above it). */
    fun hitPos(i: Int): P3 { val a = com.bytedance.physicslab.solar.lab.spec.SoftRender.worldOf(hits[i]).t; return P3(a[0], a[1], a[2]) }
    fun radiusNow(i: Int): Float = scaled[i].scale.x

    /** Radius of body i (m) at stage s. */
    fun radiusAt(i: Int, s: Int) = (ScaleFacts.re(i) * K[s]).toFloat()

    fun update(dt: Float) {
        clock += dt
        // the Sun boils and turns even at rest (cheap: transform + quantised material)
        if (bodies[SUN].visible) {
            scaled[SUN].children[0].rot = Q.axisAngle(0f, 1f, 0f, Math.toRadians(clock * 2.44).toFloat())
            sunB.setMat(sunBMat.copy(alpha = ((sin(clock * 0.045 * 2 * PI) * 0.5 + 0.5) * 20).toInt() / 20f))
        }
        for (i in 0..9) if (i != SUN && bodies[i].visible) scaled[i].children[0].children[0].rot = Q.axisAngle(0f, 1f, 0f, Math.toRadians(clock * 12.0).toFloat())
        if (t < 1f) { t = min(1f, t + dt / TRANSITION_S); apply(t) }
        else if (!settled) apply(1f)
    }

    private fun apply(tt: Float) {
        val s = ProcTex.smooth01(tt)
        val k = exp(ln(K[from]) + (ln(K[stage]) - ln(K[from])) * s)
        for (i in 0..9) {
            val a = POS[from][i]; val b = POS[stage][i]
            bodies[i].pos = SolarArt.lerp(a, b, s)
            scaled[i].uniformScale((ScaleFacts.re(i) * k).toFloat())
            bodies[i].visible = if (tt >= 1f) ON[stage][i] else (ON[from][i] || ON[stage][i])
        }
        val (lc, lr) = LENS[stage]; val (fc, fr) = LENS[from]
        lens.pos = SolarArt.lerp(fc, lc, s); lens.uniformScale(fr + (lr - fr) * s)
        lens.tapRadius = if (tt >= 1f) 1f else 0f            // node-local: 1 = the ring itself
        if (tt >= 1f) {
            settled = true
            if (pinchShown != stage) {
                pinchShown = stage
                for (i in 0..9) hits[i].tapRadius = 0f
                // the Sun: a 0.15 m spot on its visible face (its node is scaled by the Sun's radius); Saturn incl. rings
                for (i in PINCH[stage]) hits[i].tapRadius = if (i == SUN) 0.15f / radiusAt(SUN, stage) else max(0.045f, radiusAt(i, stage) * (if (i == 5) 1.6f else 1.1f))
            }
        } else if (pinchShown != -1) { pinchShown = -1; for (i in 0..9) hits[i].tapRadius = 0f }
    }
}
