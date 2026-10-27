// 伽利略单摆 · 等时性 (Galileo, 1602) — the exemplar SpecDemo.
//
// LESSON: the period depends on length and gravity, not on mass — and (Galileo's famous claim)
// not on amplitude, which is only true for SMALL swings. Two pendulums of equal length but
// different mass and amplitude share one trace lane, so "do the curves overlap?" is the question
// the student answers by looking. Small-angle mode: they stay together for hundreds of swings.
// Large-angle mode: the 45 deg swing visibly lags within ~12 swings (T is 4% longer).
// The third pendulum is longer and drifts as T = 2 pi sqrt(L/g) predicts. On the right, the classic
// 15-bob pendulum wave (Harvard demo) — same equation, graded lengths, realigning every 60 s.
//
// Physics: core/pendulum/PendulumCore (exact elliptic solution, no integration drift).

package com.bytedance.physicslab.solar.demos.pendulum

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bytedance.physicslab.solar.core.P3
import com.bytedance.physicslab.solar.core.pendulum.Pendulum
import com.bytedance.physicslab.solar.lab.Q
import com.bytedance.physicslab.solar.lab.SpecDemo
import com.bytedance.physicslab.solar.lab.TapTarget
import com.bytedance.physicslab.solar.lab.tr
import com.bytedance.physicslab.solar.lab.spec.Blend
import com.bytedance.physicslab.solar.lab.spec.LabelSpec
import com.bytedance.physicslab.solar.lab.spec.Mat
import com.bytedance.physicslab.solar.lab.spec.Node
import com.bytedance.physicslab.solar.lab.ui.FieldRef
import com.bytedance.physicslab.solar.lab.ui.LabPanel
import com.bytedance.physicslab.solar.lab.ui.LabRow
import com.bytedance.physicslab.solar.lab.ui.LabSwitch
import com.bytedance.physicslab.solar.render.MeshData
import com.bytedance.physicslab.solar.render.MeshGen
import com.bytedance.physicslab.solar.render.ProcTex
import com.bytedance.physicslab.solar.render.Rgb
import com.bytedance.physicslab.solar.lab.ui.Button
import com.bytedance.physicslab.solar.lab.ui.SegmentControl
import com.bytedance.physicslab.solar.lab.ui.SegmentItem
import com.pico.spatial.ui.design.Text
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

object PendulumDemo : SpecDemo() {
    override val id = "pendulum"
    override val title = "伽利略单摆"  // i18n-ok (zh half of DemoRegistry.titleOf; English in TITLE_EN)
    override val credit = "Galileo · 1602"

    // ---------------------------------------------------------------- parameters
    private val SMALL = doubleArrayOf(6.0, 12.0)          // A, B release angles (deg), small mode
    private val LARGE = doubleArrayOf(10.0, 45.0)         // large mode: Galileo's claim breaks
    private val AMP_CYCLE = doubleArrayOf(6.0, 12.0, 20.0, 30.0, 45.0)
    private val SPEEDS = floatArrayOf(0.25f, 0.5f, 1f)
    private val SPEED_LABELS get() = listOf(tr("慢 ×0.25", "×0.25"), tr("中 ×0.5", "×0.5"), tr("实时 ×1", "×1"))

    var gIdx = 2; var speedIdx = 1; var largeMode = false; var showWave = true
    private val amp = doubleArrayOf(SMALL[0], SMALL[1], 8.0)
    private val len = doubleArrayOf(0.40, 0.40, 0.62)
    private val bobR = floatArrayOf(0.024f, 0.034f, 0.028f)   // visibly different masses
    private val pivotX = floatArrayOf(-0.62f, -0.40f, -0.16f)
    private const val PIVOT_Y = 0.43f
    private const val PLINTH_Y = -0.262f

    // identity colours (sRGB): A blue, B green, C orange — same as the Unity build
    private val HUE = listOf(Rgb(0.30f, 0.65f, 1.00f), Rgb(0.35f, 0.95f, 0.50f), Rgb(1.00f, 0.58f, 0.25f))

    // wave row
    private const val WAVE_N = 15
    private const val WAVE_WINDOW = 60.0
    private const val WAVE_N0 = 51
    private const val WAVE_AMP = 14.0
    private const val WAVE_X = 0.50f

    // traces
    // 120 samples x 4-sided tube = 600 verts per trace (was 160 x 6 = 1120): every frame the traces are
    // re-uploaded through the SDK's boxed-List mesh API (~20 us/vertex on the Swan), same 5.3 s window
    private const val TRACE_N = 120
    private const val TRACE_HZ = 22.5
    private const val TRACE_X0 = -0.78f
    private const val TRACE_W = 0.74f
    private const val TRACE_Z = -0.30f
    private val LANE_Y = floatArrayOf(0.13f, 0.13f, -0.08f)  // A and B SHARE a lane on purpose

    // ---------------------------------------------------------------- state
    private var t = 0.0
    private var sampleAcc = 0.0
    private val hist = Array(3) { FloatArray(TRACE_N) }
    private var histHead = 0
    private lateinit var swings: Array<Node>
    private val beads = arrayOfNulls<Node>(3)
    private lateinit var traces: Array<Node>
    private lateinit var traceMesh: Array<MeshData>
    private lateinit var waveSwings: Array<Node>
    private lateinit var waveRoot: Node
    private val waveLen = Pendulum.waveLengths(WAVE_N, WAVE_WINDOW, WAVE_N0)

    private val g get() = Pendulum.G[gIdx]
    private fun gName(k: Int) = tr(Pendulum.G_NAME[k], Pendulum.G_NAME_EN[k])

    // ---------------------------------------------------------------- materials (sRGB)
    private val alu = Mat.Pbr(Rgb(0.80f, 0.82f, 0.85f), metallic = 1f, roughness = 0.32f)
    private val darkSteel = Mat.Pbr(Rgb(0.30f, 0.31f, 0.33f), metallic = 1f, roughness = 0.40f)
    private val rodSteel = Mat.Pbr(Rgb(0.88f, 0.89f, 0.91f), metallic = 1f, roughness = 0.18f)
    private val brass = Mat.Pbr(Rgb(0.96f, 0.78f, 0.45f), metallic = 1f, roughness = 0.18f)
    private val slabTex by lazy {   // honed black granite: faint mottle so the slab is a material, not a colour
        ProcTex.bakePlanet(256, 128, Rgb(0.07f, 0.075f, 0.085f), Rgb(0.12f, 0.125f, 0.14f), Rgb(0.20f, 0.205f, 0.22f), 0f, 77)
    }
    private val slab get() = Mat.Pbr(Rgb(1f, 1f, 1f), metallic = 0f, roughness = 0.55f, tex = slabTex)
    private val glass = Mat.Pbr(Rgb(0.05f, 0.06f, 0.08f), metallic = 0f, roughness = 0.08f, alpha = 0.82f)
    private val gridLine = Mat.Unlit(Rgb(0.55f, 0.70f, 0.85f), alpha = 0.22f, blend = Blend.TRANSPARENT)
    private val tickMat = Mat.Unlit(Rgb(0.75f, 0.85f, 0.95f), alpha = 0.55f, blend = Blend.TRANSPARENT)

    // ---------------------------------------------------------------- build

    override fun buildSpec(cacheDir: File): Node {
        histHead = 0; restart()
        val root = Node("PendulumRoot")
        val unitCyl = MeshGen.cylinder(1f, 1f, 20)
        val sphere = MeshGen.sphere(1f, 24, 48, MeshGen.SphereUv.EQUIRECT)

        // plinth: a charcoal slab with an aluminium edge band, grounding the whole bench
        root.add("Plinth", MeshGen.box(1.74f, 0.035f, 0.92f), slab, P3(-0.05f, PLINTH_Y, -0.04f))
        // trim sits INSIDE the slab: only its 5 mm overhang shows, as an edge (it used to cover the top)
        root.add("PlinthBand", MeshGen.box(1.75f, 0.008f, 0.93f), alu, P3(-0.05f, PLINTH_Y, -0.04f))

        // ---- hero bench (Galileo): two uprights + top beam ----
        for (x in floatArrayOf(-0.80f, -0.02f)) {
            root.add("Upright", unitCyl, alu, P3(x, (PLINTH_Y + 0.465f) / 2, 0f)).also { it.scale = P3(0.012f, 0.465f - PLINTH_Y, 0.012f) }
            root.add("Foot", MeshGen.cylinder(0.035f, 0.012f, 24), darkSteel, P3(x, PLINTH_Y + 0.024f, 0f))
        }
        root.add("Beam", MeshGen.box(0.82f, 0.028f, 0.045f), alu, P3(-0.41f, PIVOT_Y + 0.026f, 0f))

        swings = Array(3) { i ->
            val px = pivotX[i]
            root.add("Axle$i", unitCyl, darkSteel, P3(px, PIVOT_Y, 0f)).also {
                it.scale = P3(0.007f, 0.05f, 0.007f); it.rot = Q.axisAngle(1f, 0f, 0f, (PI / 2).toFloat())
            }
            buildProtractor(root, px, i)
            val sw = root.add(Node("Swing$i")).also { it.pos = P3(px, PIVOT_Y, 0f) }
            val L = len[i].toFloat(); val r = bobR[i]
            val rodLen = L - r
            sw.add("Rod$i", unitCyl, rodSteel, P3(0f, -rodLen / 2, 0f)).also { it.scale = P3(0.0022f, rodLen, 0.0022f) }
            sw.add("Collar$i", unitCyl, Mat.Pbr(HUE[i], metallic = 0.2f, roughness = 0.35f, emissive = dim(HUE[i], 0.25f)),
                P3(0f, -L + r + 0.006f, 0f)).also { it.scale = P3(0.0075f, 0.012f, 0.0075f) }
            val bob = sw.add("Bob$i", sphere, brass, P3(0f, -L, 0f))
            bob.uniformScale(r)
            bob.tapRadius = maxOf(1.7f, 0.045f / r)            // >= 45 mm world radius for gaze
            sw
        }

        // ---- trace panel behind the bench: A and B share the top lane, C below ----
        root.add("TracePanel", MeshGen.box(0.80f, 0.42f, 0.012f), glass, P3(-0.40f, 0.025f, TRACE_Z - 0.012f))
        for (ly in floatArrayOf(LANE_Y[0], LANE_Y[2])) {
            root.add("Zero", seg(TRACE_X0, ly, TRACE_X0 + TRACE_W, ly, TRACE_Z - 0.004f, 0.0012f), gridLine)
            for (k in listOf(-1f, 1f)) root.add("Grid", seg(TRACE_X0, ly + k * 0.07f, TRACE_X0 + TRACE_W, ly + k * 0.07f, TRACE_Z - 0.004f, 0.0007f), gridLine)
        }
        traceMesh = Array(3) { MeshGen.tube(tracePath(it), 0.0028f, 4, false) }
        traces = Array(3) { i -> root.add("Trace$i", traceMesh[i], Mat.Unlit(HUE[i], alpha = 0.95f, blend = Blend.ADD)) }

        // ---- pendulum wave (right): row runs front-to-back, bobs swing left-right ----
        // rig yawed 30 deg: from the default (front) viewpoint a row running straight away from the
        // eye stacks its 15 bobs on one sight line; turned, both the row and the swing read
        waveRoot = root.add(Node("Wave")).also {
            it.pos = P3(WAVE_X, 0f, -0.02f); it.rot = Q.axisAngle(0f, 1f, 0f, Math.toRadians(-30.0).toFloat())
        }
        val zs = FloatArray(WAVE_N) { -0.38f + 0.76f * it / (WAVE_N - 1) }
        for (z in floatArrayOf(-0.43f, 0.43f)) {
            waveRoot.add("WUpright", unitCyl, alu, P3(0f, (PLINTH_Y + 0.465f) / 2, z)).also { it.scale = P3(0.010f, 0.465f - PLINTH_Y, 0.010f) }
            waveRoot.add("WFoot", MeshGen.cylinder(0.03f, 0.012f, 24), darkSteel, P3(0f, PLINTH_Y + 0.024f, z))
        }
        waveRoot.add("WBeam", MeshGen.box(0.04f, 0.026f, 0.90f), alu, P3(0f, PIVOT_Y + 0.024f, 0f))
        waveSwings = Array(WAVE_N) { n ->
            val sw = waveRoot.add(Node("WSwing$n")).also { it.pos = P3(0f, PIVOT_Y, zs[n]) }
            val L = waveLen[n].toFloat(); val r = 0.017f
            sw.add("WRod$n", unitCyl, rodSteel, P3(0f, -(L - r) / 2, 0f)).also { it.scale = P3(0.0013f, L - r, 0.0013f) }
            // OKLab rainbow along the row so the travelling wave reads at a glance
            val f = n / (WAVE_N - 1f)
            val c = if (f < 0.5f) ProcTex.okLerp(Rgb(0.20f, 0.85f, 0.95f), Rgb(0.55f, 0.45f, 1.0f), f * 2)
                    else ProcTex.okLerp(Rgb(0.55f, 0.45f, 1.0f), Rgb(1.0f, 0.40f, 0.70f), (f - 0.5f) * 2)
            sw.add("WBob$n", sphere, Mat.Pbr(c, metallic = 0.0f, roughness = 0.22f, emissive = dim(c, 0.12f)), P3(0f, -L, 0f)).uniformScale(r)
            sw
        }
        updateLabels()
        return root
    }

    /** A protractor arc behind pendulum i, 10-degree ticks, and a bead that rides at the live angle. */
    private fun buildProtractor(root: Node, px: Float, i: Int) {
        val r = 0.115f; val z = -0.028f; val n = 41
        val arc = FloatArray(n * 3) { k ->
            val a = Math.toRadians(-50.0 + 100.0 * (k / 3) / (n - 1)).toFloat()
            when (k % 3) { 0 -> px + r * sin(a); 1 -> PIVOT_Y - r * cos(a); else -> z }
        }
        root.add("Arc$i", MeshGen.tube(arc, 0.0016f, 6, false), tickMat)
        for (d in -50..50 step 10) {
            val a = Math.toRadians(d.toDouble()).toFloat(); val r0 = r - if (d == 0) 0.028f else 0.014f
            root.add("Tick$i", seg3(px + r0 * sin(a), PIVOT_Y - r0 * cos(a), px + r * sin(a), PIVOT_Y - r * cos(a), z, 0.0011f), tickMat)
        }
        beads[i] = root.add("Bead$i", MeshGen.sphere(0.0065f, 10, 20, MeshGen.SphereUv.EQUIRECT),
            Mat.Unlit(HUE[i], alpha = 1f, blend = Blend.OPAQUE))
    }

    // ---------------------------------------------------------------- per frame

    override fun update(dt: Float) {
        val sdt = dt * SPEEDS[speedIdx]
        t += sdt
        for (i in 0 until 3) {
            val th = Pendulum.angle(Math.toRadians(amp[i]), len[i], g, t)
            swings[i].rot = Q.axisAngle(0f, 0f, 1f, th.toFloat())
            val r = 0.115f
            beads[i]!!.pos = P3(pivotX[i] + r * sin(th).toFloat(), PIVOT_Y - r * cos(th).toFloat(), -0.026f)
        }
        // traces sample in SIM time, so they scroll at a speed tied to the physics, not the fps
        sampleAcc += sdt
        var sampled = false
        while (sampleAcc >= 1.0 / TRACE_HZ) {
            sampleAcc -= 1.0 / TRACE_HZ
            val ts = t - sampleAcc
            for (i in 0 until 3) hist[i][histHead] = Pendulum.angle(Math.toRadians(amp[i]), len[i], g, ts).toFloat()
            histHead = (histHead + 1) % TRACE_N
            sampled = true
        }
        if (sampled || dt == 0f) for (i in 0 until 3) { MeshGen.tubeInto(traceMesh[i], tracePath(i), 0.0028f, 4, false); meshEdited(traceMesh[i]) }

        waveRoot.visible = showWave
        if (showWave) {
            val a = Math.toRadians(WAVE_AMP)
            for (n in 0 until WAVE_N) waveSwings[n].rot = Q.axisAngle(0f, 0f, 1f, Pendulum.angle(a, waveLen[n], g, t).toFloat())
        }
        updateReadout()
        updateLabels()
    }

    private fun tracePath(i: Int): FloatArray {
        val out = FloatArray(TRACE_N * 3)
        val scale = 0.07f / Math.toRadians(45.0).toFloat()        // 45 deg -> lane half-height
        for (k in 0 until TRACE_N) {
            val v = hist[i][(histHead + k) % TRACE_N]
            out[k * 3] = TRACE_X0 + TRACE_W * k / (TRACE_N - 1)
            out[k * 3 + 1] = LANE_Y[i] + (v * scale).coerceIn(-0.075f, 0.075f)
            out[k * 3 + 2] = TRACE_Z + i * 0.002f                  // A/B never z-fight on the shared lane
        }
        return out
    }

    private fun periods() = DoubleArray(3) { Pendulum.period(len[it], g, Math.toRadians(amp[it])) }

    private fun updateReadout() {
        val T = periods()
        val drift = ((t / T[0] - t / T[1]) * 360.0) % 360.0
        // the row was tuned for 9.81 m/s^2; every period scales by sqrt(9.81/g), so does the window
        val window = WAVE_WINDOW * kotlin.math.sqrt(9.81 / g)
        val realign = window - t % window
        readoutText = tr("伽利略等时性：摆长相同 → 周期相同，与质量无关；与振幅无关只在小角度时成立\n",
                "Isochronism: same length → same period, any mass; amplitude-free only for small swings\n") +
            tr("A %.0f°  T=%.4f s   B %.0f°  T=%.4f s   相差 %.2f%%   C 摆长 %.2f m  T=%.3f s\n",
                "A %.0f°  T=%.4f s   B %.0f°  T=%.4f s   ΔT %.2f%%   C length %.2f m  T=%.3f s\n").format(
                amp[0], T[0], amp[1], T[1], abs(T[1] / T[0] - 1) * 100, len[2], T[2]) +
            tr("A-B 相位差 %.1f°   ·   %s g = %.2f m/s²   ·   单摆波 %.1f s 后重新对齐",
                "A-B phase difference %.1f°   ·   %s g = %.2f m/s²   ·   wave realigns in %.1f s").format(
                abs(drift), gName(gIdx), g, realign)
    }

    private fun updateLabels() {
        val T = periods()
        labels.clear()
        for (i in 0 until 3) labels += LabelSpec("%s  %.0f°  T=%.3f s".format("ABC"[i], amp[i], T[i]), P3(pivotX[i], PIVOT_Y + 0.085f, 0.02f))
        labels += LabelSpec(tr("单摆波 · 15 摆 · 60 s 重合", "Pendulum wave · 15 bobs · 60 s cycle"), P3(WAVE_X, PIVOT_Y + 0.085f, 0f), visible = showWave)
        labels += LabelSpec(tr("A 与 B：同摆长", "A and B: same length"), P3(TRACE_X0 + 0.10f, LANE_Y[0] + 0.095f, TRACE_Z + 0.01f))
    }

    // ---------------------------------------------------------------- interaction

    override val tapTargets get() = listOf(
        TapTarget("Bob0", "铜球 A → 换释放角", "Bob A → new angle"),
        TapTarget("Bob1", "铜球 B → 换释放角", "Bob B → new angle"),
        TapTarget("Bob2", "铜球 C → 换释放角", "Bob C → new angle"),
    )

    override fun isTarget(name: String) = name.startsWith("Bob")

    /** Gaze at a hero bob + pinch: cycle its release angle, restart the race. */
    override fun onTap(name: String) {
        val i = name.removePrefix("Bob").toIntOrNull() ?: return
        val k = AMP_CYCLE.indexOfFirst { it > amp[i] + 0.1 }
        amp[i] = if (k < 0) AMP_CYCLE[0] else AMP_CYCLE[k]
        restart()
    }

    /** Before release each bob was HELD at its angle — so the trace history is that angle, not 0. */
    private fun restart() {
        t = 0.0; sampleAcc = 0.0
        for (i in 0 until 3) hist[i].fill(Math.toRadians(amp[i]).toFloat())
    }

    fun applyLargeMode(on: Boolean) {
        largeMode = on
        val a = if (on) LARGE else SMALL
        amp[0] = a[0]; amp[1] = a[1]
        restart()
    }

    @Composable
    override fun Panel() {
        var large by FieldRef(::largeMode)
        var gi by FieldRef(::gIdx)
        var si by FieldRef(::speedIdx)
        var wave by FieldRef(::showWave)
        LabPanel(readout) {
            LabRow {
                SegmentControl {
                    SegmentItem(selected = !large, onClick = { large = false; applyLargeMode(false) }, title = { Text(tr("小角 6°/12°", "Small 6°/12°")) })
                    SegmentItem(selected = large, onClick = { large = true; applyLargeMode(true) }, title = { Text(tr("大角 10°/45°", "Large 10°/45°")) })
                }
                Spacer(Modifier.width(20.dp))
                SegmentControl {
                    SPEED_LABELS.forEachIndexed { k, l -> SegmentItem(selected = si == k, onClick = { si = k; speedIdx = k }, title = { Text(l) }) }
                }
            }
            LabRow {
                SegmentControl {
                    Pendulum.G_NAME.indices.forEach { k -> SegmentItem(selected = gi == k, onClick = { gi = k; gIdx = k; restart() }, title = { Text(gName(k)) }) }
                }
                Spacer(Modifier.width(20.dp))
                LabSwitch(tr("单摆波", "Pendulum wave"), wave) { wave = it; showWave = it }
                Button(onClick = { restart() }) { Text(tr("同时释放", "Release all")) }
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    private fun dim(c: Rgb, k: Float) = Rgb(c.r * k, c.g * k, c.b * k)
    private fun seg(x0: Float, y0: Float, x1: Float, y1: Float, z: Float, r: Float) = seg3(x0, y0, x1, y1, z, r)
    private fun seg3(x0: Float, y0: Float, x1: Float, y1: Float, z: Float, r: Float) =
        MeshGen.tube(floatArrayOf(x0, y0, z, x1, y1, z), r, 6, false)
}
