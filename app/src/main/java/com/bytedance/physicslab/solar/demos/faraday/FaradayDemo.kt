// 法拉第电磁感应 (Faraday, 1831).
//
// LESSON: a magnet does nothing to the coil — a CHANGING flux does. Push the N pole in: the needle
// swings one way; stop with the magnet deep inside the coil: the needle falls back to zero (the flux
// is large but no longer changing); pull it out: the needle swings the other way. Faster, or more
// turns, bigger swing: EMF = -N dPhi/dt. And Lenz: the coil end facing the magnet lights up as the
// SAME pole while it approaches (repel) and the opposite pole while it leaves (attract) — the
// induced current always opposes the change (来拒去留), so you do work, and that work is the energy.
//
// Interaction: auto plunge (in / hold / out), auto pass-through (the classic double pulse), or
// drag the position slider yourself — your hand speed IS dPhi/dt. Gaze + pinch the magnet to flip
// N/S, the coil to change the number of turns. The charge visibly circulates in the winding.
//
// Physics: core/faraday/FaradayCore (magnet = uniformly magnetised cylinder, exact coaxial-loop
// mutual inductance integrated over every turn; galvanometer = damped torsion oscillator).

package com.bytedance.physicslab.solar.demos.faraday

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bytedance.physicslab.solar.core.P3
import com.bytedance.physicslab.solar.core.faraday.BarMagnet
import com.bytedance.physicslab.solar.core.faraday.Coil
import com.bytedance.physicslab.solar.core.faraday.Galvanometer
import com.bytedance.physicslab.solar.core.faraday.Induction
import com.bytedance.physicslab.solar.demos.cyclotron.MeshKit
import com.bytedance.physicslab.solar.lab.About
import com.bytedance.physicslab.solar.lab.Bi
import com.bytedance.physicslab.solar.lab.Challenge
import com.bytedance.physicslab.solar.lab.Lesson
import com.bytedance.physicslab.solar.lab.LessonStep
import com.bytedance.physicslab.solar.lab.Q
import com.bytedance.physicslab.solar.lab.Sfx
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
import com.bytedance.physicslab.solar.lab.ui.LabSlider
import com.bytedance.physicslab.solar.lab.ui.LabSwitch
import com.bytedance.physicslab.solar.render.MeshData
import com.bytedance.physicslab.solar.render.MeshGen
import com.bytedance.physicslab.solar.render.ProcTex
import com.bytedance.physicslab.solar.render.Rgb
import com.bytedance.physicslab.solar.lab.ui.SegmentControl
import com.bytedance.physicslab.solar.lab.ui.SegmentItem
import com.pico.spatial.ui.design.Text
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sign
import kotlin.math.sin

object FaradayDemo : SpecDemo() {
    override val id = "faraday"
    override val title = "法拉第电磁感应"   // i18n-ok: Chinese title; the English one is DemoRegistry.TITLE_EN (shown via titleOf)
    override val credit = "Faraday · 1831"

    // ---------------------------------------------------------------- the bench (SI)
    private val magnet = BarMagnet(a = 0.010, len = 0.080, br = 0.40)     // ferrite rod magnet
    private const val COIL_R = 0.030; private const val COIL_LEN = 0.080
    private val TURNS = intArrayOf(100, 200, 400)
    private val VIS_TURNS = intArrayOf(9, 16, 28)                        // drawn windings (more = more)
    private const val R_G = 100.0                                        // galvanometer coil, ohm
    private const val I_FS = 1.0e-3                                      // full scale +-1 mA
    private const val X0 = 0.13                                          // rest: magnet centre 13 cm out (5 cm clear)
    private val V_PEAK = doubleArrayOf(0.08, 0.16, 0.32)                 // m/s cruise, slow / medium / fast
    private val SPEED_LABELS get() = listOf(tr("慢 8 cm/s", "8 cm/s"), tr("中 16 cm/s", "16 cm/s"), tr("快 32 cm/s", "32 cm/s"))
    private val MODE_LABELS get() = listOf(tr("插入·停·拔出", "In · hold · out"), tr("匀速穿过", "Pass through"), tr("手动拖动", "Manual"))
    private const val REST = 1.2; private const val HOLD = 1.4

    var mode = 0; var speedIdx = 1; var turnsIdx = 1; var pole = 1; var showField = true
    var manualTarget = -1f                                               // slider, -1..1 of X0

    // ---------------------------------------------------------------- layout (display metres)
    private const val SF = 3.6f
    private val AX = P3(-0.12f, 0.02f, 0f)                              // coil centre, axis along +x
    private const val PLINTH_Y = -0.262f
    private const val TOP = -0.2445f
    private const val CR = (COIL_R * SF).toFloat() - 0.006f              // winding radius 0.102
    private const val CL = (COIL_LEN * SF).toFloat()                     // 0.24
    private const val MR = 0.036f; private const val MH = 0.144f          // magnet radius, half length
    private val G = P3(0.67f, -0.07f, -0.10f)                           // galvanometer housing centre
    private const val G_YAW = -0.30f
    private const val PIV_Y = -0.115f                                   // needle pivot (housing-local)
    private const val NEEDLE = 0.185f
    private const val FS_RAD = 0.70                                     // +-40 deg full scale
    private const val TX0 = -0.84f; private const val TW = 1.06f; private const val TZ = -0.34f
    private const val LANE_PHI = 0.378f; private const val LANE_EMF = 0.240f; private const val LANE_H = 0.052f
    private const val TRACE_N = 180; private const val TRACE_HZ = 30.0
    private const val ND = 40; private const val NW = 12
    private const val EMF_LANE = 0.6e-3                               // current trace: +-0.6 mA per lane half-height

    // ---------------------------------------------------------------- state
    private lateinit var ind: Induction
    private val galvo = Galvanometer(I_FS, FS_RAD, period = 0.5, zeta = 0.5)
    private var t = 0.0; private var x = -X0; private var v = 0.0
    private var emf = 0.0; private var cur = 0.0
    private var dotPhase = 0.0; private var wirePhase = 0.0
    private var sampleAcc = 0.0
    private val histPhi = FloatArray(TRACE_N); private val histEmf = FloatArray(TRACE_N); private var head = 0
    // lesson bookkeeping: signed current extremes since resetPeaks(), and the peak |I| in the auto programme
    private var iMax = 0.0; private var iMin = 0.0; private var autoPeak = 0.0; private var pushSign = 1
    private fun resetPeaks() { iMax = 0.0; iMin = 0.0; autoPeak = 0.0 }

    private lateinit var magnetNode: Node; private lateinit var sled: Node
    private lateinit var fieldNode: Node
    private val windings = arrayOfNulls<Node>(3)
    private val coilDots = arrayOfNulls<Node>(ND)
    private val wireDots = arrayOfNulls<Node>(2 * NW)
    private lateinit var wireA: FloatArray; private lateinit var wireB: FloatArray
    private val poleRing = arrayOfNulls<Node>(2)
    private lateinit var needle: Node
    private lateinit var phiMesh: MeshData; private lateinit var emfMesh: MeshData

    private val turns get() = TURNS[turnsIdx]
    private val coil get() = Coil(COIL_R, COIL_LEN, turns)
    private val rTot get() = coil.resistance + R_G
    private val phiMax by lazy { abs(ind.fluxPerTurn(0.0)) }

    // ---------------------------------------------------------------- materials (sRGB)
    private val N_RED = Rgb(0.90f, 0.20f, 0.17f)
    private val S_BLUE = Rgb(0.18f, 0.36f, 0.85f)
    private val copper = Mat.Pbr(Rgb(0.90f, 0.55f, 0.32f), metallic = 1f, roughness = 0.28f)
    private val bobbin = Mat.Pbr(Rgb(0.10f, 0.11f, 0.13f), metallic = 0f, roughness = 0.45f)
    private val acrylic = Mat.Pbr(Rgb(0.70f, 0.80f, 0.90f), metallic = 0f, roughness = 0.10f, alpha = 0.22f)   // clear former: the magnet stays visible inside
    private val alu = Mat.Pbr(Rgb(0.80f, 0.82f, 0.85f), metallic = 1f, roughness = 0.32f)
    private val bakelite = Mat.Pbr(Rgb(0.07f, 0.07f, 0.08f), metallic = 0f, roughness = 0.35f)
    private val cream = Mat.Pbr(Rgb(0.93f, 0.90f, 0.82f), metallic = 0f, roughness = 0.65f)
    private val ink = Mat.Unlit(Rgb(0.10f, 0.10f, 0.12f))
    private val glass = Mat.Pbr(Rgb(0.05f, 0.06f, 0.08f), metallic = 0f, roughness = 0.08f, alpha = 0.82f)
    private val gridLine = Mat.Unlit(Rgb(0.55f, 0.70f, 0.85f), alpha = 0.22f, blend = Blend.TRANSPARENT)
    private val fieldMat = Mat.Unlit(Rgb(0.45f, 0.70f, 1.0f), alpha = 0.55f, blend = Blend.ADD)
    private val chargeMat = Mat.Unlit(Rgb(1.0f, 0.86f, 0.45f), alpha = 1f, blend = Blend.ADD)
    private val PHI_C = Rgb(0.45f, 0.70f, 1.0f)
    private val EMF_C = Rgb(1.0f, 0.70f, 0.30f)
    private val slabTex by lazy {
        ProcTex.bakePlanet(256, 128, Rgb(0.07f, 0.075f, 0.085f), Rgb(0.12f, 0.125f, 0.14f), Rgb(0.20f, 0.205f, 0.22f), 0f, 77)
    }

    // ---------------------------------------------------------------- build

    override fun buildSpec(cacheDir: File): Node {
        ind = Induction(magnet, Coil(COIL_R, COIL_LEN, 1))
        val root = Node("FaradayRoot")
        val unitCyl = MeshGen.cylinder(1f, 1f, 16)
        val sphere = MeshGen.sphere(1f, 12, 24, MeshGen.SphereUv.EQUIRECT)

        root.add("Plinth", MeshGen.box(1.74f, 0.035f, 0.92f), Mat.Pbr(Rgb(1f, 1f, 1f), roughness = 0.55f, tex = slabTex), P3(0f, PLINTH_Y, -0.04f))
        // trim sits INSIDE the slab: only its 5 mm overhang shows, as an edge (it used to cover the top)
        root.add("PlinthBand", MeshGen.box(1.75f, 0.008f, 0.93f), alu, P3(0f, PLINTH_Y, -0.04f))

        // ---- the coil: bobbin, flanges, copper winding (drawn turn count follows N)
        val coilG = root.add(Node("Coil")).also { it.pos = AX; it.rot = Q.axisAngle(0f, 1f, 0f, (PI / 2).toFloat()) } // local z = world x
        // gaze target: the bobbin, collider r = 0.13 m about the coil centre (unscaled node, metres)
        coilG.add("BobbinMesh", MeshKit.sectorPrism(CR - 0.012f, CR - 0.005f, 0f, 2 * PI.toFloat(), -CL / 2 - 0.004f, CL / 2 + 0.004f, 48), acrylic)
        // gaze target for the coil sits ON TOP of the winding: a sphere centred on the axis would
        // enclose the magnet whenever it is inside the coil and steal every pinch meant for it
        coilG.add(Node("Bobbin")).also { it.pos = P3(0f, CR + 0.035f, 0f); it.tapRadius = 0.05f }
        val flange = MeshKit.sectorPrism(CR - 0.012f, CR + 0.036f, 0f, 2 * PI.toFloat(), -0.006f, 0.006f, 48)
        for (s in floatArrayOf(-1f, 1f)) coilG.add("Flange", flange, bobbin, P3(0f, 0f, s * (CL / 2 + 0.010f)))
        for (k in 0..2) windings[k] = root.add("Winding$k", MeshGen.tube(helix(VIS_TURNS[k], 64), 0.0042f, 8, false), copper)
        for (s in floatArrayOf(-1f, 1f)) {
            root.add("CoilPost", unitCyl, alu, P3(AX.x + s * (CL / 2 + 0.010f), (TOP + AX.y - CR - 0.03f) / 2, 0f)).also {
                it.scale = P3(0.008f, AX.y - CR - 0.03f - TOP, 0.008f)
            }
            root.add("CoilFoot", MeshGen.cylinder(0.03f, 0.010f, 24), alu, P3(AX.x + s * (CL / 2 + 0.010f), TOP + 0.005f, 0f))
        }
        // induced-pole rings on the two flanges (glow N red / S blue with the current)
        val ringMesh = MeshGen.tube(MeshKit.circle(CR + 0.030f, 64, "yz"), 0.0055f, 8, true)
        for (k in 0..1) poleRing[k] = root.add("PoleRing$k", ringMesh, Mat.Unlit(N_RED, 0f, blend = Blend.ADD),
            P3(AX.x + (if (k == 0) -1 else 1) * (CL / 2 + 0.018f), AX.y, 0f))

        // ---- rail through the bore, the magnet on its sled
        val railY = AX.y - MR - 0.010f
        root.add("Rail", unitCyl, alu, P3(-0.28f, railY, 0f)).also { it.scale = P3(0.0055f, 1.18f, 0.0055f); it.rot = Q.axisAngle(0f, 0f, 1f, (PI / 2).toFloat()) }
        for (px in floatArrayOf(-0.86f, 0.30f)) {
            root.add("RailPost", unitCyl, alu, P3(px, (railY + TOP) / 2, 0f)).also { it.scale = P3(0.007f, railY - TOP, 0.007f) }
            root.add("RailFoot", MeshGen.cylinder(0.026f, 0.010f, 24), alu, P3(px, TOP + 0.005f, 0f))
        }
        sled = root.add("Sled", MeshGen.box(0.10f, 0.008f, 0.034f), alu)
        magnetNode = root.add(Node("Magnet"))
        val half = MeshGen.cylinder(MR, MH, 32)
        val zRot = Q.axisAngle(0f, 0f, 1f, (PI / 2).toFloat())
        magnetNode.add("MagnetN", half, Mat.Pbr(N_RED, metallic = 0.1f, roughness = 0.38f), P3(MH / 2, 0f, 0f)).also { it.rot = zRot; it.tapRadius = 0.065f }
        magnetNode.add("MagnetS", half, Mat.Pbr(S_BLUE, metallic = 0.1f, roughness = 0.38f), P3(-MH / 2, 0f, 0f)).also { it.rot = zRot; it.tapRadius = 0.065f }
        magnetNode.add("MagnetBand", MeshGen.cylinder(MR + 0.0015f, 0.006f, 32), Mat.Pbr(Rgb(0.85f, 0.86f, 0.88f), metallic = 1f, roughness = 0.3f)).rot = zRot
        fieldNode = magnetNode.add(Node("Field"))
        for (line in fieldLines()) fieldNode.add("FieldLine", MeshGen.tube(line, 0.0022f, 5, false), fieldMat)

        // ---- the current, made visible: charges circulating in the winding and along the leads
        for (k in 0 until ND) coilDots[k] = root.add("Charge$k", sphere, chargeMat).also { it.uniformScale(0.0075f) }
        buildGalvanometer(root, unitCyl, sphere)
        val t1 = galvoWorld(-0.09f, -0.145f, 0.073f); val t2 = galvoWorld(0.09f, -0.145f, 0.073f)
        val right = P3(AX.x + CL / 2 - 0.002f, AX.y + CR, 0f); val left = P3(AX.x - CL / 2 + 0.002f, AX.y + CR, 0f)
        // + terminal (left post on the meter, red) takes the current that leaves the coil's right end
        wireA = spline(listOf(right, P3(right.x + 0.06f, right.y + 0.07f, 0.02f), P3(0.33f, 0.02f, 0.05f), P3(t1.x - 0.03f, t1.y + 0.02f, t1.z + 0.05f), t1), 80)
        wireB = spline(listOf(t2, P3(t2.x + 0.02f, TOP + 0.012f, t2.z + 0.06f), P3(0.25f, TOP + 0.012f, -0.17f),
            P3(AX.x - 0.10f, TOP + 0.012f, -0.17f), P3(left.x - 0.06f, AX.y + 0.06f, -0.10f), left), 120)
        val leadMat = Mat.Pbr(Rgb(0.75f, 0.16f, 0.14f), roughness = 0.5f)
        root.add("LeadA", MeshGen.tube(wireA, 0.0035f, 6, false), leadMat)
        root.add("LeadB", MeshGen.tube(wireB, 0.0035f, 6, false), Mat.Pbr(Rgb(0.12f, 0.12f, 0.13f), roughness = 0.5f))
        for (k in 0 until 2 * NW) wireDots[k] = root.add("WireCharge$k", sphere, chargeMat).also { it.uniformScale(0.0065f) }

        buildTraces(root)
        // warm start: run one whole auto cycle so the traces already hold a full in/out story
        prime()
        return root
    }

    private fun buildGalvanometer(root: Node, unitCyl: MeshData, sphere: MeshData) {
        val g = root.add(Node("Galvo")).also { it.pos = G; it.rot = Q.axisAngle(0f, 1f, 0f, G_YAW) }
        g.add("Housing", MeshGen.box(0.38f, 0.35f, 0.11f), bakelite)
        g.add("Bezel", MeshGen.box(0.35f, 0.27f, 0.006f), alu, P3(0f, 0.020f, 0.056f))
        g.add("Dial", MeshGen.box(0.33f, 0.25f, 0.006f), cream, P3(0f, 0.020f, 0.059f))
        val z = 0.0635f
        // scale: arc + ticks, -40..+40 deg, zero in the middle (centre-zero galvanometer)
        val arcR = 0.158f
        val arc = FloatArray(41 * 3) { k ->
            val a = (PI / 2 + FS_RAD * (1 - 2.0 * (k / 3) / 40)).toFloat()
            when (k % 3) { 0 -> arcR * cos(a); 1 -> PIV_Y + arcR * sin(a); else -> z }
        }
        g.add("ScaleArc", MeshGen.tube(arc, 0.0016f, 6, false), ink)
        for (i in -10..10) {
            val a = (PI / 2 - FS_RAD * i / 10).toFloat()
            val major = i % 5 == 0
            val r0 = arcR + (if (major) 0.020f else 0.010f)
            g.add("Tick", MeshGen.tube(floatArrayOf(arcR * cos(a), PIV_Y + arcR * sin(a), z, r0 * cos(a), PIV_Y + r0 * sin(a), z),
                if (major) 0.0018f else 0.0011f, 6, false), if (i == 0) Mat.Unlit(Rgb(0.10f, 0.10f, 0.12f)) else ink)
        }
        // coloured ends: + side red, - side blue (which way the current goes)
        for (sgn in intArrayOf(-1, 1)) {
            val path = FloatArray(12 * 3) { k ->
                val a = (PI / 2 - sgn * FS_RAD * (0.55 + 0.45 * (k / 3) / 11.0)).toFloat()
                val rr = arcR + 0.030f
                when (k % 3) { 0 -> rr * cos(a); 1 -> PIV_Y + rr * sin(a); else -> z }
            }
            g.add("Zone", MeshGen.tube(path, 0.0035f, 6, false), Mat.Unlit(if (sgn > 0) N_RED else S_BLUE))
        }
        needle = g.add(Node("Needle")).also { it.pos = P3(0f, PIV_Y, z + 0.004f) }
        needle.add("NeedleBody", MeshGen.box(0.0042f, NEEDLE, 0.0025f), Mat.Unlit(Rgb(0.85f, 0.10f, 0.08f)), P3(0f, NEEDLE / 2 - 0.012f, 0f))
        g.add("Pivot", unitCyl, Mat.Pbr(Rgb(0.15f, 0.15f, 0.16f), metallic = 1f, roughness = 0.3f), P3(0f, PIV_Y, z + 0.006f)).also {
            it.scale = P3(0.012f, 0.008f, 0.012f); it.rot = Q.axisAngle(1f, 0f, 0f, (PI / 2).toFloat())
        }
        // terminals: red + (left), black - (right)
        g.add("TermPlus", unitCyl, Mat.Pbr(Rgb(0.80f, 0.12f, 0.10f), roughness = 0.35f), P3(-0.09f, -0.145f, 0.062f)).also {
            it.scale = P3(0.013f, 0.022f, 0.013f); it.rot = Q.axisAngle(1f, 0f, 0f, (PI / 2).toFloat())
        }
        g.add("TermMinus", unitCyl, Mat.Pbr(Rgb(0.10f, 0.10f, 0.11f), roughness = 0.35f), P3(0.09f, -0.145f, 0.062f)).also {
            it.scale = P3(0.013f, 0.022f, 0.013f); it.rot = Q.axisAngle(1f, 0f, 0f, (PI / 2).toFloat())
        }
    }

    private fun buildTraces(root: Node) {
        root.add("TracePanel", MeshGen.box(TW + 0.06f, 0.275f, 0.012f), glass, P3(TX0 + TW / 2, 0.309f, TZ - 0.012f))
        for (ly in floatArrayOf(LANE_PHI, LANE_EMF)) {
            root.add("Zero", seg(TX0, ly, TX0 + TW, ly, TZ - 0.004f, 0.0012f), gridLine)
        }
        for (k in listOf(-1f, 1f)) root.add("Grid", seg(TX0, LANE_EMF + k * LANE_H, TX0 + TW, LANE_EMF + k * LANE_H, TZ - 0.004f, 0.0007f), gridLine)
        phiMesh = MeshGen.tube(tracePath(histPhi, LANE_PHI), 0.0028f, 6, false)
        emfMesh = MeshGen.tube(tracePath(histEmf, LANE_EMF), 0.0028f, 6, false)
        root.add("PhiTrace", phiMesh, Mat.Unlit(PHI_C, 0.95f, blend = Blend.ADD))
        root.add("EmfTrace", emfMesh, Mat.Unlit(EMF_C, 0.95f, blend = Blend.ADD))
    }

    // ---------------------------------------------------------------- geometry helpers

    /** Winding: [n] turns over the coil length, world coordinates. Parameter s in 0..1. */
    private fun helixAt(n: Int, s: Double): P3 {
        val a = 2 * PI * n * s
        return P3(AX.x - CL / 2 + CL * s.toFloat(), AX.y + CR * cos(a).toFloat(), CR * sin(a).toFloat())
    }

    private fun helix(n: Int, perTurn: Int): FloatArray {
        val m = n * perTurn + 1
        val out = FloatArray(m * 3)
        for (i in 0 until m) { val p = helixAt(n, i.toDouble() / (m - 1)); out[i * 3] = p.x; out[i * 3 + 1] = p.y; out[i * 3 + 2] = p.z }
        return out
    }

    /** Catmull-Rom through [pts], [n] samples. */
    private fun spline(pts: List<P3>, n: Int): FloatArray {
        val out = FloatArray(n * 3)
        val segs = pts.size - 1
        for (i in 0 until n) {
            val u = i.toFloat() / (n - 1) * segs
            val k = u.toInt().coerceAtMost(segs - 1); val f = u - k
            val p0 = pts[maxOf(0, k - 1)]; val p1 = pts[k]; val p2 = pts[k + 1]; val p3 = pts[minOf(segs, k + 2)]
            fun cr(a: Float, b: Float, c: Float, d: Float) = 0.5f * (2 * b + (-a + c) * f + (2 * a - 5 * b + 4 * c - d) * f * f + (-a + 3 * b - 3 * c + d) * f * f * f)
            out[i * 3] = cr(p0.x, p1.x, p2.x, p3.x); out[i * 3 + 1] = cr(p0.y, p1.y, p2.y, p3.y); out[i * 3 + 2] = cr(p0.z, p1.z, p2.z, p3.z)
        }
        return out
    }

    private fun alongPath(p: FloatArray, s: Double): P3 {
        val n = p.size / 3
        val f = (s.coerceIn(0.0, 1.0) * (n - 1)); val i = f.toInt().coerceAtMost(n - 2); val w = (f - i).toFloat()
        return P3(p[i * 3] + (p[i * 3 + 3] - p[i * 3]) * w, p[i * 3 + 1] + (p[i * 3 + 4] - p[i * 3 + 1]) * w, p[i * 3 + 2] + (p[i * 3 + 5] - p[i * 3 + 2]) * w)
    }

    private fun galvoWorld(x: Float, y: Float, z: Float): P3 {
        val c = cos(G_YAW); val s = sin(G_YAW)
        return P3(G.x + c * x + s * z, G.y + y, G.z - s * x + c * z)
    }

    /**
     * Field lines of the magnet in its own frame (moment along +x), traced through the exact field
     * from seeds just outside the N face, in the vertical plane, mirrored below the axis.
     */
    private fun fieldLines(): List<FloatArray> {
        val a = magnet.a; val half = magnet.len / 2
        // seeds on the N end: near the rim the loops close tightly; nearer the axis they reach wider
        val seeds = listOf(doubleArrayOf(half + 0.001, 0.55 * a), doubleArrayOf(half + 0.001, 0.92 * a),
            doubleArrayOf(half - 0.006, a + 0.001), doubleArrayOf(half - 0.016, a + 0.001))
        val out = ArrayList<FloatArray>()
        for (sd in seeds) {
            val pts = ArrayList<Double>()
            var z = sd[0]; var r = sd[1]
            val h = 0.0015
            for (step in 0 until 600) {
                pts += z; pts += r
                fun dir(zz: Double, rr: Double): DoubleArray { val b = magnet.field(rr, zz); val l = kotlin.math.hypot(b[0], b[1]); return doubleArrayOf(b[1] / l, b[0] / l) }
                val d1 = dir(z, r); val d2 = dir(z + h / 2 * d1[0], r + h / 2 * d1[1])
                z += h * d2[0]; r += h * d2[1]
                if (r < 0) break
                if (abs(z) < half + 0.0005 && r < a + 0.0005 && step > 3) { pts += z; pts += r; break }
                if (abs(z) > 0.16 || r > 0.060) { pts.clear(); break }
            }
            if (pts.size < 8) continue
            for (sgn in floatArrayOf(1f, -1f)) {
                val n = pts.size / 2
                out += FloatArray(n * 3) { k -> when (k % 3) { 0 -> (pts[(k / 3) * 2] * SF).toFloat(); 1 -> sgn * (pts[(k / 3) * 2 + 1] * SF).toFloat(); else -> 0f } }
            }
        }
        return out
    }

    private fun tracePath(h: FloatArray, lane: Float): FloatArray {
        val out = FloatArray(TRACE_N * 3)
        for (k in 0 until TRACE_N) {
            out[k * 3] = TX0 + TW * k / (TRACE_N - 1)
            out[k * 3 + 1] = lane + LANE_H * h[(head + k) % TRACE_N].coerceIn(-1.08f, 1.08f)
            out[k * 3 + 2] = TZ
        }
        return out
    }

    // ---------------------------------------------------------------- motion

    /** Trapezoidal velocity: ramp over the first and last quarter of the distance, cruise between. */
    private fun moveTime(d: Double) = 1.5 * d / V_PEAK[speedIdx]
    private val tIn get() = moveTime(if (mode == 0) X0 else 2 * X0)
    private val cycle get() = REST + 2 * tIn + HOLD

    private fun trapezoid(f: Double, a: Double, b: Double, tm: Double): DoubleArray {
        // f in 0..1 of the move; distance fractions: ramp 1/4, cruise 1/2, ramp 1/4 (times 1/3 each)
        val d = b - a; val vc = 1.5 * d / tm
        val s: Double; val vel: Double
        when {
            f < 1.0 / 3 -> { val u = f * 3; s = 0.25 * u * u; vel = vc * u }
            f < 2.0 / 3 -> { s = 0.25 + 0.5 * (f * 3 - 1); vel = vc }
            else -> { val u = 3 - f * 3; s = 1 - 0.25 * u * u; vel = vc * u }
        }
        return doubleArrayOf(a + d * s, vel)
    }

    /** Auto programme: position and velocity at programme time tt. */
    private fun program(tt: Double): DoubleArray {
        val tm = tIn
        val xEnd = if (mode == 0) 0.0 else X0
        val c = tt % cycle
        return when {
            c < REST -> doubleArrayOf(-X0, 0.0)
            c < REST + tm -> trapezoid((c - REST) / tm, -X0, xEnd, tm)
            c < REST + tm + HOLD -> doubleArrayOf(xEnd, 0.0)
            else -> trapezoid(((c - REST - tm - HOLD) / tm).coerceAtMost(1.0), xEnd, -X0, tm)
        }
    }

    private fun prime() {
        t = 0.0; head = 0; histPhi.fill(0f); histEmf.fill(0f); galvo.reset()
        if (mode == 2) { x = manualTarget * X0; v = 0.0 }
        else { val n = (cycle * 60).toInt(); repeat(n) { step(1f / 60f, edit = false) } }
        refreshTraces()
    }

    // ---------------------------------------------------------------- per frame

    override fun update(dt: Float) { step(dt, edit = true) }

    private fun step(dt: Float, edit: Boolean) {
        if (mode == 2) {
            // the magnet follows your slider like a critically damped hand: continuous velocity
            val w = 12.0
            val a = w * w * (manualTarget * X0 - x) - 2 * w * v
            v += a * dt; x += v * dt
        } else if (dt > 0f) {
            t += dt
            val p = program(t); x = p[0]; v = p[1]
        }
        emf = ind.emfN(turns, x, v, pole)
        val cur0 = cur; val th0 = galvo.theta; val om0 = galvo.omega
        cur = emf / rTot
        galvo.step(cur, dt.toDouble())
        if (edit && dt > 0f) {
            val pan = (G.x / 0.9f).coerceIn(-1f, 1f)
            // sound: an induced-current pulse starts (|I| rises past 30 % of full scale) — a TICK, higher for
            // + (push in) than − (pull out), so 来拒去留's two opposite kicks sound different
            if (abs(cur0) < 0.3 * I_FS && abs(cur) >= 0.3 * I_FS) Sfx.play(Sfx.TICK, 0.3f, if (cur > 0) 1.3f else 0.85f, pan)
            // sound: the needle slams into its end stop (moved too fast / too many turns) — a small clack ∝ impact speed
            if (abs(th0) < galvo.stopRad && abs(galvo.theta) >= galvo.stopRad && abs(om0) > 0.8)
                Sfx.play(Sfx.CLACK, (0.1f + 0.4f * (abs(om0) / 8.0).coerceAtMost(1.0).toFloat()), 1.8f, pan)
        }
        if (cur > iMax) iMax = cur
        if (cur < iMin) iMin = cur
        if (mode == 0 && abs(cur) > autoPeak) autoPeak = abs(cur)

        // traces (sim time, 30 Hz)
        sampleAcc += dt
        var sampled = false
        while (sampleAcc >= 1.0 / TRACE_HZ) {
            sampleAcc -= 1.0 / TRACE_HZ
            histPhi[head] = (ind.fluxPerTurn(x, pole) / phiMax).toFloat()
            histEmf[head] = (cur / EMF_LANE).toFloat()
            head = (head + 1) % TRACE_N
            sampled = true
        }
        // charges: circulation speed proportional to the current (1.2 turns/s at full scale)
        val f = (cur / I_FS).coerceIn(-1.5, 1.5)
        dotPhase += f * 1.2 / VIS_TURNS[turnsIdx] * dt
        wirePhase += f * 0.45 * dt
        if (!edit) return
        if (sampled || dt == 0f) refreshTraces()
        place()
        updateReadout(); updateLabels()
    }

    private fun refreshTraces() {
        MeshGen.tubeInto(phiMesh, tracePath(histPhi, LANE_PHI), 0.0028f, 6, false); meshEdited(phiMesh)
        MeshGen.tubeInto(emfMesh, tracePath(histEmf, LANE_EMF), 0.0028f, 6, false); meshEdited(emfMesh)
    }

    private fun place() {
        val mx = AX.x + (x * SF).toFloat()
        magnetNode.pos = P3(mx, AX.y, 0f)
        magnetNode.rot = if (pole > 0) Q.identity() else Q.axisAngle(0f, 1f, 0f, PI.toFloat())
        sled.pos = P3(mx, AX.y - MR - 0.004f, 0f)
        fieldNode.visible = showField
        for (k in 0..2) windings[k]!!.visible = k == turnsIdx
        val nT = VIS_TURNS[turnsIdx]
        for (k in 0 until ND) {
            val s = ((k.toDouble() / ND + dotPhase) % 1.0 + 1.0) % 1.0
            coilDots[k]!!.pos = helixAt(nT, s).let { P3(it.x, AX.y + (it.y - AX.y) * 1.06f, it.z * 1.06f) }
        }
        for (k in 0 until NW) {
            val s = ((k.toDouble() / NW + wirePhase) % 1.0 + 1.0) % 1.0
            wireDots[k]!!.pos = alongPath(wireA, s)
            wireDots[NW + k]!!.pos = alongPath(wireB, s)
        }
        val show = abs(cur) > 0.02 * I_FS
        for (k in 0 until ND) coilDots[k]!!.visible = show
        for (k in 0 until 2 * NW) wireDots[k]!!.visible = show
        // induced poles: I > 0 circulates right-handed about +x, so its field points +x: right end N
        val a = (abs(cur) / I_FS).coerceIn(0.0, 1.0).toFloat()
        val rightN = cur > 0
        poleRing[1]!!.setMat(Mat.Unlit(if (rightN) N_RED else S_BLUE, 0.15f + 0.85f * a, blend = Blend.ADD))
        poleRing[0]!!.setMat(Mat.Unlit(if (rightN) S_BLUE else N_RED, 0.15f + 0.85f * a, blend = Blend.ADD))
        poleRing[0]!!.visible = show; poleRing[1]!!.visible = show
        // needle: positive current deflects right (clockwise seen from the front)
        needle.rot = Q.axisAngle(0f, 0f, 1f, (-galvo.theta).toFloat())
    }

    private fun updateReadout() {
        val phi = ind.fluxPerTurn(x, pole)
        val l1 = tr("法拉第：穿过线圈的磁通量 Φ 变化时才有感应电流，ε = −N ΔΦ/Δt（越快、匝数越多，ε 越大）",
            "Faraday: only a changing flux Φ induces a current, ε = −N ΔΦ/Δt (faster, more turns → larger ε)")
        val l2 = tr("N=%d 匝  v=%+.2f m/s  Φ=%+.3f mWb  ε=%+.0f mV  I=%+.2f mA%s", "N=%d turns  v=%+.2f m/s  Φ=%+.3f mWb  ε=%+.0f mV  I=%+.2f mA%s").format(
            turns, v, phi * 1e3, emf * 1e3, cur * 1e3, if (abs(cur) > 1.12 * I_FS) tr("（超量程！）", " (off scale!)") else "")
        val dAbs = sign(phi) * ind.dFluxPerTurnDx(x, pole) * v      // d|Phi|/dt sign
        val facing = if ((x < 0) == (pole > 0)) "N" else "S"         // magnet pole facing the coil
        val opposite = if (facing == "N") "S" else "N"
        val l3 = when {
            abs(v) < 0.004 -> if (abs(x) < 0.03) tr("磁铁静止在线圈里：Φ 很大但不变 → ε = 0，指针回零", "Magnet at rest inside the coil: Φ large but constant → ε = 0, needle back to zero")
                else tr("磁铁静止：Φ 不变 → 没有感应电流", "Magnet at rest: Φ constant → no induced current")
            dAbs > 0 -> tr("Φ 增大：线圈靠近磁铁的一端感应出 $facing 极，与磁铁同极相斥，阻碍它靠近（楞次定律：来拒去留）",
                "Φ rising: the near coil end becomes an $facing pole: like poles repel, opposing the approach (Lenz's law)")
            else -> tr("Φ 减小：线圈这一端感应出 $opposite 极，异极相吸，阻碍它离开（楞次定律：来拒去留）",
                "Φ falling: this coil end becomes an $opposite pole: unlike poles attract, opposing the retreat (Lenz's law)")
        }
        readoutText = "$l1\n$l2\n$l3"
    }

    private fun updateLabels() {
        labels.clear()
        val mx = AX.x + (x * SF).toFloat()
        val nx = mx + pole * (MH - 0.03f); val sx = mx - pole * (MH - 0.03f)
        labels += LabelSpec("N", P3(nx, AX.y + MR + 0.035f, 0.02f))
        labels += LabelSpec("S", P3(sx, AX.y + MR + 0.035f, 0.02f))
        labels += LabelSpec(tr("线圈 %d 匝", "Coil: %d turns").format(turns), P3(AX.x, AX.y + CR + 0.085f, 0.02f))
        labels += LabelSpec(tr("电流计 ±1 mA", "Galvanometer ±1 mA"), galvoWorld(0f, 0.22f, 0.06f))
        labels += LabelSpec(tr("磁通量 Φ", "Magnetic flux Φ"), P3(TX0 + 0.08f, LANE_PHI + 0.075f, TZ + 0.01f))
        labels += LabelSpec(tr("感应电流 I（∝ ε）", "Induced current I (∝ ε)"), P3(TX0 + 0.10f, LANE_EMF + 0.075f, TZ + 0.01f))
    }

    // ---------------------------------------------------------------- interaction

    override val tapTargets get() = listOf(
        TapTarget("MagnetN", "磁铁 → 翻转 N/S", "Magnet → flip N/S"),
        TapTarget("Bobbin", "线圈 → 换匝数", "Coil → turns"),
    )

    override fun isTarget(name: String) = name == "MagnetN" || name == "MagnetS" || name == "Bobbin"

    override fun onTap(name: String) {
        when (name) {
            "MagnetN", "MagnetS" -> pole = -pole
            "Bobbin" -> turnsIdx = (turnsIdx + 1) % TURNS.size
        }
    }

    // ---------------------------------------------------------------- guided track (导学)
    // KEY INSIGHT: a current flows only while the flux through the coil CHANGES. The magnet resting deep
    // inside the coil (largest flux) gives ZERO; pulling it out reverses the current (Lenz); faster or more
    // turns, bigger current (auto programme, 200 turns: 0.33 mA at 16 cm/s, 0.65 mA at 32 cm/s).
    // ---- background card (📖): context for a viewer who has never met this topic
    override val about = About(listOf(
        About.Page(
            Bi("这个实验在做什么？", "What is this experiment?"),
            Bi("一根条形磁铁对着一个线圈，线圈接着一台电流计。在面板选「插入·停·拔出」「匀速穿过」，或拖「磁铁位置」滑块亲手推拉，看指针怎么摆。线圈靠近磁铁的一端会亮出感应出的 N 极或 S 极，下方两条曲线记录磁通量 Φ 和感应电流。捏磁铁可翻转 N/S，捏线圈可换匝数。", "A bar magnet faces a coil wired to a galvanometer. On the panel choose “In · hold · out” or “Pass through”, or drag the “Magnet” slider yourself, and watch the needle. The coil end facing the magnet lights up as the induced N or S pole, and the traces below record the flux Φ and the induced current. Pinch the magnet to flip N/S, or the coil to change its turns."),
        ),
        About.Page(
            Bi("磁能不能生电？", "Can magnets make electricity?"),
            Bi("1820 年，奥斯特发现电流能让旁边的磁针偏转：电能生磁。那反过来，磁能不能生电？不少人把磁铁放在线圈旁，盯着电流计，却什么也没看到。1831 年，法拉第发现了其中的秘密：只有在磁场变化的那一刻，电流计才会动一下。这为人类提供了大规模发电的方法。法拉第几乎没受过正规数学训练，靠的是一个又一个实验。", "In 1820 Ørsted found that an electric current deflects a nearby compass: electricity makes magnetism. Could magnetism make electricity? Many set a magnet by a coil, watched a meter, saw nothing. In 1831 Faraday found the secret: the meter moves only at the moment the magnetic field changes. That gave the world a way to generate power on a large scale. Faraday had little formal maths; experiments got him there."),
        ),
        About.Page(
            Bi("只认“变化”", "Only change counts"),
            Bi("线圈不在乎磁场有多强，只在乎它变得有多快。就像坐电梯：只有起步和刹车时你才觉得身子一沉或一轻，匀速时什么也感觉不到。磁铁停在线圈里，磁通量最大却不变，电流为零。推得越快、匝数越多，电流越大：ε = −NΔΦ/Δt。感应电流还总在阻碍变化（楞次定律），所以推磁铁要费力，这份力气变成了电能。", "The coil does not care how strong the field is, only how fast it changes. Like a lift: you feel heavier or lighter only as it starts and stops, not while it cruises. With the magnet resting inside, the flux is largest but constant — zero current. Push faster or add turns: more current, ε = −NΔΦ/Δt. The induced current always fights the change (Lenz's law), so pushing takes effort — and that effort becomes electrical energy."),
        ),
        About.Page(
            Bi("今天它在哪里？", "Where is it today?"),
            Bi("发电站里，汽轮机或水轮机带着磁铁在线圈中飞转，世界上绝大部分电都是这样来的。变压器、电磁炉、手机无线充电，都是一个线圈里变化的电流，在另一个线圈或锅底里“感应”出电流。刷公交卡、电吉他拾音器、机场的金属探测门，用的也是同一个道理。", "In a power station a steam or water turbine spins magnets past coils — that is where most of the world's electricity comes from. Transformers, induction hobs and wireless phone chargers all use a changing current in one coil to induce a current in another coil or in the pan. Tapping a transit card, an electric-guitar pickup and an airport metal detector work the same way."),
        ),
    ))

    override val lesson: Lesson by lazy {
        Lesson(
            listOf(
                LessonStep(
                    say = Bi("把磁铁推进线圈，然后让它停在线圈里不动。这时电流计的指针会？", "Push the magnet into the coil, then hold it still inside. What does the meter needle do?"),
                    options = listOf(
                        Bi("偏得最大", "Swings most"),
                        Bi("回到零", "Back to zero"),
                        Bi("停在偏转处", "Stays over"),
                    ),
                    answer = 1,
                ),
                LessonStep(
                    say = Bi("拖动面板「磁铁位置」滑块到中间，把磁铁推进线圈，然后松手停住，看指针。", "Drag the “Magnet” slider to the middle to push the magnet into the coil, then stop and watch the needle."),
                    onEnter = { resetPeaks() },
                    done = { mode == 2 && abs(x) < 0.03 && abs(v) < 0.003 && maxOf(iMax, -iMin) > 0.1e-3 && abs(galvo.theta) < 0.04 },
                    autoplay = { mode = 2; manualTarget = 0f },
                    reveal = true,
                    insight = Bi("回到零！磁铁停在线圈里，磁通量 Φ 最大却不再变化，ε = −NΔΦ/Δt = 0。", "Back to zero! At rest inside the coil the flux Φ is largest but no longer changing: ε = −NΔΦ/Δt = 0."),
                ),
                LessonStep(
                    say = Bi("推进时指针偏向一边。现在把磁铁拔出来，指针会？", "Pushing in swung the needle one way. Now pull the magnet out — the needle will…"),
                    options = listOf(
                        Bi("偏向同一边", "Swing the same way"),
                        Bi("偏向另一边", "Swing the other way"),
                        Bi("不动", "Not move"),
                    ),
                    answer = 1,
                ),
                LessonStep(
                    say = Bi("把「磁铁位置」滑块拖回最左，把磁铁拔出线圈，看指针和下方电流曲线。", "Drag the “Magnet” slider back to the far left to pull it out; watch the needle and the current trace."),
                    onEnter = { pushSign = if (iMax >= -iMin) 1 else -1; resetPeaks() },
                    done = { mode == 2 && x < -0.6 * X0 && (if (pushSign > 0) iMin < -0.1e-3 else iMax > 0.1e-3) },
                    autoplay = { mode = 2; manualTarget = -1f },
                    reveal = true,
                    insight = Bi("偏向另一边：拔出时 Φ 减小，电流反向。感应电流总在阻碍磁通变化（楞次定律：来拒去留）。", "The other way: pulling out, Φ falls and the current reverses. It always opposes the change (Lenz's law)."),
                ),
            ),
            challenge = Challenge(
                say = Bi("切回「插入·停·拔出」，让电流峰值超过 0.50 mA（16 cm/s、200 匝时只有 0.33 mA）。", "Back to “In · hold · out”: get the current peak above 0.50 mA (16 cm/s with 200 turns gives 0.33 mA)."),
                progress = {
                    if (mode != 0) tr("先切回「插入·停·拔出」", "Switch back to “In · hold · out” first")
                    else tr("电流峰值 %.2f mA（目标 ≥ 0.50 mA）", "Peak current %.2f mA (goal ≥ 0.50 mA)").format(autoPeak * 1e3)
                },
                onEnter = { resetPeaks() },
                done = { mode == 0 && autoPeak >= 0.5e-3 },
                autoplay = { mode = 0; t = 0.0; speedIdx = 2 },
            ),
        )
    }

    @Composable
    override fun Panel() {
        var md by FieldRef(::mode)
        var si by FieldRef(::speedIdx)
        var ni by FieldRef(::turnsIdx)
        var pos by FieldRef(::manualTarget)
        var fl by FieldRef(::showField)
        LabPanel(readout) {
            LabRow {
                SegmentControl {
                    MODE_LABELS.forEachIndexed { k, l -> SegmentItem(selected = md == k, onClick = { md = k; mode = k; t = 0.0; if (k == 2) manualTarget = (x / X0).toFloat().also { pos = it } }, title = { Text(l) }) }
                }
                Spacer(Modifier.width(20.dp))
                SegmentControl {
                    SPEED_LABELS.forEachIndexed { k, l -> SegmentItem(selected = si == k, onClick = { si = k; speedIdx = k; t = 0.0 }, title = { Text(l) }) }
                }
            }
            LabSlider(tr("磁铁位置", "Magnet"), pos, -1f..1f, if (md == 2) tr("拖得越快，指针偏得越大", "Drag faster, bigger swing") else tr("拖动即切到手动", "Drag to go manual")) {
                pos = it; manualTarget = it
                if (md != 2) { md = 2; mode = 2 }
            }
            LabRow {
                SegmentControl {
                    TURNS.forEachIndexed { k, n -> SegmentItem(selected = ni == k, onClick = { ni = k; turnsIdx = k }, title = { Text(tr("$n 匝", "$n turns")) }) }
                }
                Spacer(Modifier.width(20.dp))
                LabSwitch(tr("磁感线", "Field lines"), fl) { fl = it; showField = it }
            }
        }
    }

    private fun seg(x0: Float, y0: Float, x1: Float, y1: Float, z: Float, r: Float) =
        MeshGen.tube(floatArrayOf(x0, y0, z, x1, y1, z), r, 6, false)
}
