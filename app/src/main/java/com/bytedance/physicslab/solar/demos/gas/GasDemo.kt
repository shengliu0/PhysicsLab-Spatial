// 气体分子运动论 (Boltzmann, 1872) — pressure IS molecules hitting the walls.
//
// LESSON (one idea, three readouts that agree): a glass box of hard-sphere molecules, coloured by
// speed. Push the piston in and the dial gauge climbs as 1/V (Boyle); turn the heater up and the
// molecules turn warm-coloured, the gauge climbs as T (Gay-Lussac) and the speed histogram slides
// right and flattens — always hugging the Maxwell curve drawn over it. "All equal speeds" restarts
// the gas with every molecule at the same speed (Boltzmann's 1872 thought experiment): the
// histogram is one spike, and within a few collision times it relaxes onto the Maxwell curve.
//
// Physics: core/gas/GasCore (hard-sphere collisions, moving piston, heater floor). Each drawn ball is
// both the collision sphere and what you see. Unity audit (RandN at 1/3 the temperature, no
// collisions, piston that never halves V, 6 % pressure bias) is in GasCore's header.

package com.bytedance.physicslab.solar.demos.gas

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bytedance.physicslab.solar.core.P3
import com.bytedance.physicslab.solar.core.gas.GasScale
import com.bytedance.physicslab.solar.core.gas.HardSphereGas
import com.bytedance.physicslab.solar.core.gas.Maxwell
import com.bytedance.physicslab.solar.lab.Lang
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
import com.bytedance.physicslab.solar.lab.ui.LabSlider
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
import kotlin.math.exp
import kotlin.math.roundToInt

object GasDemo : SpecDemo() {
    override val id = "gas"
    override val title = "气体分子运动论"  // i18n-ok (Chinese half of the title; English is DemoRegistry.TITLE_EN, shown via titleOf)
    override val credit = "Boltzmann · 1872"

    // ---------------------------------------------------------------- geometry (metres, volume space)
    private const val R = 0.010f                 // molecule radius = collision radius
    private const val CAP = 200
    private const val YC = 0.06f                 // gas centre height
    private const val HY = 0.17f; private const val HZ = 0.14f
    private const val XHI = 0.11f; private const val LX = 0.60f
    private const val XLO_FULL = XHI - LX
    private const val PIST_T = 0.024f
    private const val ROD_LEN = 0.34f            // >= piston travel, so the handle never enters the glass
    private const val V_MIN = 0.5f
    private const val GX0 = XLO_FULL - R - PIST_T - 0.006f      // glass extent
    private const val GX1 = XHI + R
    private const val GY0 = YC - HY - R; private const val GY1 = YC + HY + R
    private const val GZ = HZ + R
    private const val PLINTH_Y = -0.262f
    private const val GAUGE_X = -0.03f; private const val GAUGE_Y = 0.352f; private const val GAUGE_R = 0.072f
    private const val THERMO_X = 0.225f; private const val TH_Y0 = -0.165f; private const val TH_LEN = 0.44f
    private const val TH_MAX_K = 700f
    // histogram
    private const val NB = 14
    private const val HX0 = 0.345f; private const val HX1 = 0.86f; private const val HB = -0.165f; private const val HZ_ = -0.085f
    private const val V_MAX_REAL = 1400.0         // N2 m/s at the right end of the axis
    private const val V_COLOUR = 1050.0           // N2 m/s that is full red (colour scale, absolute)
    private const val H_SCALE = 1.40f             // bar height per unit fraction
    private const val CURVE_N = 64
    // tracer
    private const val TRAIL_N = 48
    private const val TRAIL_HZ = 12.0

    private val SPEEDS = floatArrayOf(0.25f, 0.5f, 1f)
    // a getter so the segment titles follow the display language
    private val SPEED_LABELS get() = listOf(tr("慢 ×0.25", "Slow ×0.25"), tr("中 ×0.5", "Medium ×0.5"), tr("快 ×1", "Fast ×1"))
    private val N_CHOICES = intArrayOf(50, 100, 150, 200)

    // ---------------------------------------------------------------- UI state (Compose snapshot state so taps update the panel)
    var tSetK by mutableFloatStateOf(300f)
    var vFrac by mutableFloatStateOf(1f)
    var nIdx by mutableIntStateOf(2)
    var speedIdx by mutableIntStateOf(1)

    // ---------------------------------------------------------------- state
    private val gas = HardSphereGas(CAP, 2.0 * R, 1872L)
    private val vMaxScreen get() = V_MAX_REAL / GasScale.speedToReal
    private val pRef get() = GasScale.pRef(150, LX.toDouble() * 2 * HY * 2 * HZ)
    private var pEma = 0.0
    private var tEma = 0.0                       // what the thermometer reads (KE smoothed over ~3 s)
    private val trailWriter = TubeWriter(5)
    private var zEma = 1.0
    private val histTmp = DoubleArray(NB)
    private val histEma = DoubleArray(NB)
    private val trail = FloatArray(TRAIL_N * 3)
    private var trailAcc = 0.0
    private var curveT = -1.0
    private var heaterAt = -1f
    private var frame = 0
    private var textEn = false                   // display language the readout/labels were last written in
    /** The stated scaling: the reference box (full V, 150 balls, 300 K) is 1 atm of N₂. */
    private val PER_BALL: String by lazy {
        val v = GasScale.realPerBall(150, LX.toDouble() * 2 * HY * 2 * HZ)
        val e = kotlin.math.floor(kotlin.math.log10(v)).toInt()
        "%.1f×10%s".format(v / Math.pow(10.0, e.toDouble()), e.toString().map { "⁰¹²³⁴⁵⁶⁷⁸⁹"[it - '0'] }.joinToString(""))
    }

    private lateinit var mols: Array<Node>
    private val band = IntArray(CAP) { -1 }
    private lateinit var bandMat: Array<Mat>
    private lateinit var bars: Array<Node>
    private lateinit var piston: Node
    private lateinit var needle: Node
    private lateinit var column: Node
    private lateinit var setMark: Node
    private lateinit var halo: Node
    private lateinit var trailMesh: MeshData
    private lateinit var curveMesh: MeshData
    private lateinit var coils: Array<Node>
    private lateinit var heaterPlate: Node

    // ---------------------------------------------------------------- materials (sRGB)
    private val alu = Mat.Pbr(Rgb(0.80f, 0.82f, 0.85f), metallic = 1f, roughness = 0.30f)
    private val darkSteel = Mat.Pbr(Rgb(0.30f, 0.31f, 0.33f), metallic = 1f, roughness = 0.40f)
    private val brass = Mat.Pbr(Rgb(0.96f, 0.78f, 0.45f), metallic = 1f, roughness = 0.22f)
    private val chrome = Mat.Pbr(Rgb(0.90f, 0.91f, 0.93f), metallic = 1f, roughness = 0.12f)
    private val rubber = Mat.Pbr(Rgb(0.10f, 0.10f, 0.11f), metallic = 0f, roughness = 0.6f)
    private val slabTex by lazy {
        ProcTex.bakePlanet(256, 128, Rgb(0.07f, 0.075f, 0.085f), Rgb(0.12f, 0.125f, 0.14f), Rgb(0.20f, 0.205f, 0.22f), 0f, 77)
    }
    private val slab get() = Mat.Pbr(Rgb(1f, 1f, 1f), metallic = 0f, roughness = 0.55f, tex = slabTex)
    private val glassBack = Mat.Pbr(Rgb(0.10f, 0.13f, 0.17f), metallic = 0f, roughness = 0.06f, alpha = 0.55f)
    private val glassClear = Mat.Pbr(Rgb(0.70f, 0.82f, 0.95f), metallic = 0f, roughness = 0.04f, alpha = 0.07f)
    private val board = Mat.Pbr(Rgb(0.05f, 0.06f, 0.08f), metallic = 0f, roughness = 0.08f, alpha = 0.85f)
    private val dialFace = Mat.Pbr(Rgb(0.92f, 0.92f, 0.90f), metallic = 0f, roughness = 0.45f)
    private val ink = Mat.Unlit(Rgb(0.10f, 0.11f, 0.13f))
    private val needleMat = Mat.Unlit(Rgb(0.95f, 0.25f, 0.15f))
    private val gridLine = Mat.Unlit(Rgb(0.55f, 0.70f, 0.85f), alpha = 0.30f, blend = Blend.TRANSPARENT)
    private val tickMat = Mat.Unlit(Rgb(0.75f, 0.85f, 0.95f), alpha = 0.70f, blend = Blend.TRANSPARENT)
    private val curveMat = Mat.Unlit(Rgb(1.0f, 0.93f, 0.78f), alpha = 1f, blend = Blend.ADD)
    private val fluid = Mat.Pbr(Rgb(0.90f, 0.12f, 0.10f), metallic = 0f, roughness = 0.25f, emissive = Rgb(0.45f, 0.04f, 0.03f))
    private val tubeGlass = Mat.Pbr(Rgb(0.80f, 0.88f, 0.95f), metallic = 0f, roughness = 0.05f, alpha = 0.22f)

    /** Speed colour: absolute (not normalised by the current T — heating must SHOW). OKLab blue -> cyan -> amber -> red. */
    private fun speedColour(f: Float): Rgb {
        val stops = arrayOf(Rgb(0.22f, 0.36f, 1.00f), Rgb(0.12f, 0.80f, 0.95f), Rgb(1.00f, 0.84f, 0.25f), Rgb(1.00f, 0.32f, 0.20f))
        val x = f.coerceIn(0f, 1f) * 3f; val k = minOf(2, x.toInt())
        return ProcTex.okLerp(stops[k], stops[k + 1], x - k)
    }

    // ---------------------------------------------------------------- build

    override fun buildSpec(cacheDir: File): Node {
        val root = Node("GasRoot")
        val unitCyl = MeshGen.cylinder(1f, 1f, 20)
        val unitBox = MeshGen.box(1f, 1f, 1f)
        frame = 0
        // measurement filters start fresh on every (re)build — stale EMAs from a previous visit
        // made the gauge open on old readings (and broke the Boyle-ratio test when run after another)
        pEma = 0.0; tEma = 0.0; zEma = 1.0; trailAcc = 0.0; curveT = -1.0; heaterAt = -1f

        // speed bands: molecules and histogram bars share them, so a colour means one speed everywhere
        bandMat = Array(NB) { k ->
            val c = speedColour(((k + 0.5) / NB * V_MAX_REAL / V_COLOUR).toFloat())
            Mat.Pbr(c, metallic = 0f, roughness = 0.32f, emissive = dim(c, 0.42f))
        }

        // bench
        root.add("Plinth", MeshGen.box(1.80f, 0.035f, 0.52f), slab, P3(0f, PLINTH_Y, -0.03f))
        // trim sits INSIDE the slab: only its 5 mm overhang shows, as an edge (it used to cover the top)
        root.add("PlinthBand", MeshGen.box(1.81f, 0.008f, 0.53f), alu, P3(0f, PLINTH_Y, -0.03f))
        val benchTop = PLINTH_Y + 0.0215f

        // ---- heater chassis under the box: its front coils glow with the heater temperature
        val chY0 = benchTop + 0.035f; val chY1 = GY0 - 0.012f
        val chCx = (GX0 + GX1) / 2; val chW = GX1 - GX0 + 0.02f
        root.add("Chassis", MeshGen.box(chW, chY1 - chY0, 2 * GZ + 0.03f), darkSteel, P3(chCx, (chY0 + chY1) / 2, 0f))
        for (sx in floatArrayOf(-1f, 1f)) for (sz in floatArrayOf(-1f, 1f))
            root.add("Foot", unitCyl, rubber, P3(chCx + sx * (chW / 2 - 0.04f), (benchTop + chY0) / 2, sz * (GZ - 0.02f)))
                .also { it.scale = P3(0.022f, chY0 - benchTop, 0.022f) }
        heaterPlate = root.add("Heater", MeshGen.box(GX1 - GX0, 0.012f, 2 * GZ), darkSteel, P3(chCx, GY0 - 0.006f, 0f))
        heaterPlate.tapRadius = 0.07f
        val coilMesh = MeshGen.box(chW - 0.08f, 0.007f, 0.004f)
        coils = Array(4) { k ->
            root.add("Coil$k", coilMesh, Mat.Unlit(Rgb(0.3f, 0.3f, 0.3f)), P3(chCx, chY0 + 0.012f + k * ((chY1 - chY0 - 0.024f) / 3f), GZ + 0.0165f))
        }

        // ---- glass box: back pane darker (contrast for the swarm), front/top nearly clear, alu edge rails
        val gcx = (GX0 + GX1) / 2; val gcy = (GY0 + GY1) / 2; val gw = GX1 - GX0; val gh = GY1 - GY0
        root.add("GlassBack", MeshGen.box(gw, gh, 0.004f), glassBack, P3(gcx, gcy, -GZ - 0.002f))
        root.add("GlassFront", MeshGen.box(gw, gh, 0.004f), glassClear, P3(gcx, gcy, GZ + 0.002f))
        root.add("GlassTop", MeshGen.box(gw, 0.004f, 2 * GZ), glassClear, P3(gcx, GY1 + 0.002f, 0f))
        root.add("GlassEnd", MeshGen.box(0.004f, gh, 2 * GZ), glassClear, P3(GX1 + 0.002f, gcy, 0f))
        root.add("GlassEndL", MeshGen.box(0.004f, gh, 2 * GZ), glassClear, P3(GX0 - 0.002f, gcy, 0f))
        val rail = 0.008f
        for (a in floatArrayOf(-1f, 1f)) for (b in floatArrayOf(-1f, 1f)) {
            root.add("RailX", unitBox, alu, P3(gcx, gcy + a * (gh / 2 + rail / 2), b * (GZ + rail / 2))).also { it.scale = P3(gw + 2 * rail, rail, rail) }
            root.add("RailY", unitBox, alu, P3(if (a < 0) GX0 - rail / 2 else GX1 + rail / 2, gcy, b * (GZ + rail / 2))).also { it.scale = P3(rail, gh, rail) }
            root.add("RailZ", unitBox, alu, P3(if (a < 0) GX0 - rail / 2 else GX1 + rail / 2, gcy + b * (gh / 2 + rail / 2), 0f)).also { it.scale = P3(rail, rail, 2 * GZ) }
        }
        // left end: a frame with a bronze guide bushing the piston rod runs through
        root.add("EndBush", unitCyl, brass, P3(GX0 - 0.012f, YC, 0f)).also { it.scale = P3(0.022f, 0.024f, 0.022f); it.rot = rotZ90() }

        // ---- piston (brass face, steel rod, T-handle = gaze target)
        piston = root.add(Node("Piston"))
        piston.add("PistonFace", MeshGen.box(PIST_T, 2 * HY + 2 * R - 0.006f, 2 * HZ + 2 * R - 0.006f), brass, P3(-R - PIST_T / 2, 0f, 0f))
        piston.add("PistonSeal", MeshGen.box(0.006f, 2 * HY + 2 * R - 0.002f, 2 * HZ + 2 * R - 0.002f), rubber, P3(-R - PIST_T + 0.004f, 0f, 0f))
        piston.add("Rod", unitCyl, chrome, P3(-R - PIST_T - ROD_LEN / 2, 0f, 0f)).also { it.scale = P3(0.009f, ROD_LEN, 0.009f); it.rot = rotZ90() }
        val hx = -R - PIST_T - ROD_LEN
        piston.add("HandleBar", unitCyl, darkSteel, P3(hx, 0f, 0f)).also { it.scale = P3(0.012f, 0.15f, 0.012f) }
        val knob = MeshGen.sphere(1f, 12, 20, MeshGen.SphereUv.EQUIRECT)
        for (s in floatArrayOf(-1f, 1f)) piston.add("HandleKnob", knob, rubber, P3(hx, s * 0.075f, 0f)).uniformScale(0.018f)
        piston.add("PistonHandle", knob, brass, P3(hx, 0f, 0f)).also { it.uniformScale(0.022f); it.tapRadius = 2.5f }   // 55 mm

        // ---- molecules: ONE shared sphere mesh, nodes parented to a gas frame at (0, YC, 0)
        val gasFrame = root.add(Node("Gas")).also { it.pos = P3(0f, YC, 0f) }
        val molMesh = MeshGen.sphere(R, 8, 14, MeshGen.SphereUv.EQUIRECT)
        mols = Array(CAP) { i -> gasFrame.add("Mol$i", molMesh, bandMat[NB / 3]) }
        halo = gasFrame.add("TracerHalo", MeshGen.sphere(2.3f * R, 10, 18, MeshGen.SphereUv.EQUIRECT),
            Mat.Unlit(Rgb(1f, 0.95f, 0.80f), alpha = 0.30f, blend = Blend.ADD))
        trailMesh = MeshGen.tube(trail, 0.0017f, 5, false)
        gasFrame.add("Trail", trailMesh, Mat.Unlit(Rgb(1f, 0.90f, 0.70f), alpha = 0.38f, blend = Blend.ADD))

        // ---- pressure gauge (Bourdon dial) on the lid, 0-4 atm over 240 deg
        root.add("GaugeStem", unitCyl, chrome, P3(GAUGE_X, (GY1 + GAUGE_Y - GAUGE_R) / 2, 0f)).also { it.scale = P3(0.008f, GAUGE_Y - GAUGE_R - GY1 + 0.01f, 0.008f) }
        root.add("GaugeCase", unitCyl, darkSteel, P3(GAUGE_X, GAUGE_Y, -0.012f)).also { it.scale = P3(GAUGE_R + 0.010f, 0.022f, GAUGE_R + 0.010f); it.rot = rotX90() }
        root.add("GaugeFace", unitCyl, dialFace, P3(GAUGE_X, GAUGE_Y, 0.0f)).also { it.scale = P3(GAUGE_R, 0.004f, GAUGE_R); it.rot = rotX90() }
        val ringPts = FloatArray(49 * 3) { k -> val a = 2 * PI * (k / 3) / 48; when (k % 3) { 0 -> GAUGE_X + (GAUGE_R + 0.004f) * kotlin.math.cos(a).toFloat(); 1 -> GAUGE_Y + (GAUGE_R + 0.004f) * kotlin.math.sin(a).toFloat(); else -> 0.003f } }
        root.add("GaugeBezel", MeshGen.tube(ringPts, 0.006f, 8, false), chrome)
        for (k in 0..12) {                         // every 0.5 atm, majors at whole atm
            val a = gaugeAngle(k * 0.5); val major = k % 2 == 0
            val r1 = GAUGE_R - 0.008f; val r0 = r1 - if (major) 0.016f else 0.008f
            root.add("GTick", seg(GAUGE_X - r0 * kotlin.math.sin(a), GAUGE_Y + r0 * kotlin.math.cos(a),
                GAUGE_X - r1 * kotlin.math.sin(a), GAUGE_Y + r1 * kotlin.math.cos(a), 0.004f, if (major) 0.0022f else 0.0013f), ink)
        }
        // 1 atm reference: a short blue arc band
        val refPts = FloatArray(9 * 3) { k -> val a = gaugeAngle(0.9 + 0.2 * (k / 3) / 8.0); val r = GAUGE_R - 0.004f
            when (k % 3) { 0 -> GAUGE_X - r * kotlin.math.sin(a); 1 -> GAUGE_Y + r * kotlin.math.cos(a); else -> 0.0045f } }
        root.add("GRef", MeshGen.tube(refPts, 0.0025f, 6, false), Mat.Unlit(Rgb(0.25f, 0.55f, 1f)))
        needle = root.add(Node("NeedlePivot")).also { it.pos = P3(GAUGE_X, GAUGE_Y, 0.008f) }
        needle.add("Needle", unitBox, needleMat, P3(0f, 0.026f, 0f)).also { it.scale = P3(0.005f, 0.066f, 0.003f) }
        needle.add("NeedleHub", unitCyl, darkSteel, P3(0f, 0f, 0.002f)).also { it.scale = P3(0.008f, 0.006f, 0.008f); it.rot = rotX90() }

        // ---- thermometer (gas temperature from the molecules' kinetic energy)
        root.add("ThermoTube", unitCyl, tubeGlass, P3(THERMO_X, TH_Y0 + TH_LEN / 2, 0f)).also { it.scale = P3(0.017f, TH_LEN + 0.02f, 0.017f) }
        root.add("ThermoBulb", MeshGen.sphere(0.028f, 14, 24, MeshGen.SphereUv.EQUIRECT), fluid, P3(THERMO_X, TH_Y0 - 0.014f, 0f))
        column = root.add("ThermoColumn", unitCyl, fluid, P3(THERMO_X, TH_Y0, 0f))
        root.add("ThermoFoot", unitCyl, darkSteel, P3(THERMO_X, benchTop + 0.006f, 0f)).also { it.scale = P3(0.04f, 0.012f, 0.04f) }
        root.add("ThermoPost", unitCyl, darkSteel, P3(THERMO_X, (benchTop + TH_Y0 - 0.03f) / 2, 0f)).also { it.scale = P3(0.007f, TH_Y0 - 0.03f - benchTop, 0.007f) }
        for (k in 0..7) {
            val y = thY(k * 100f); val major = k % 2 == 0
            root.add("TTick", seg(THERMO_X - 0.020f - if (major) 0.018f else 0.009f, y, THERMO_X - 0.019f, y, 0.0f, 0.0016f), tickMat)
        }
        setMark = root.add("SetMark", MeshGen.box(0.030f, 0.010f, 0.010f), Mat.Unlit(Rgb(1f, 0.6f, 0.2f)), P3(THERMO_X + 0.030f, thY(300f), 0f))

        // ---- speed histogram with the Maxwell curve
        val bw = (HX1 - HX0) / NB
        root.add("HistBoard", MeshGen.box(HX1 - HX0 + 0.06f, 0.56f, 0.012f), board, P3((HX0 + HX1) / 2, HB + 0.245f, HZ_ - 0.016f))
        root.add("HistBase", seg(HX0 - 0.01f, HB, HX1 + 0.01f, HB, HZ_ + 0.002f, 0.0016f), tickMat)
        for (vReal in listOf(500.0, 1000.0)) {
            val x = HX0 + (HX1 - HX0) * (vReal / V_MAX_REAL).toFloat()
            root.add("HTick", seg(x, HB - 0.014f, x, HB + 0.005f, HZ_ + 0.002f, 0.0014f), tickMat)
        }
        for (k in 1..4) root.add("HGrid", seg(HX0, HB + k * 0.1f, HX1, HB + k * 0.1f, HZ_ - 0.008f, 0.0008f), gridLine)
        bars = Array(NB) { k ->
            root.add("Bar$k", unitBox, bandMat[k], P3(HX0 + bw * (k + 0.5f), HB, HZ_)).also { it.scale = P3(bw * 0.84f, 0.001f, 0.010f) }
        }
        curveMesh = MeshGen.tube(curvePath(0.0533), 0.0026f, 6, false)
        root.add("Maxwell", curveMesh, curveMat)

        // ---- the gas itself: start in equilibrium at 300 K, then warm up 3 s so gauges & histogram are live
        gas.setBox(XLO_FULL.toDouble(), XHI.toDouble(), HY.toDouble(), HZ.toDouble())
        gas.thermalFloor = true
        gas.initMaxwell(N_CHOICES[nIdx], GasScale.modelT(tSetK.toDouble()))
        gas.xLo = XHI - LX * vFrac.toDouble(); gas.pistonTarget = gas.xLo
        band.fill(-1); curveT = -1.0; heaterAt = -1f
        pEma = gas.n * gas.temperature() / gas.volume * 1.04
        zEma = 1.04
        tEma = gas.temperature()
        gas.speedHistogram(histEma, vMaxScreen)
        for (k in 0 until TRAIL_N) { trail[k * 3] = gas.x[0].toFloat(); trail[k * 3 + 1] = gas.y[0].toFloat(); trail[k * 3 + 2] = gas.z[0].toFloat() }
        repeat(180) { advance(1f / 60f) }
        update(0f)
        return root
    }

    // ---------------------------------------------------------------- per frame

    override fun update(dt: Float) {
        advance(dt * SPEEDS[speedIdx])
        sync()
    }

    /** Physics + statistics for [sdt] seconds of simulation time. */
    private fun advance(sdt: Float) {
        val s = sdt.toDouble()
        gas.wallT = GasScale.modelT(tSetK.toDouble())
        gas.pistonTarget = XHI - LX * vFrac.toDouble()
        val want = N_CHOICES[nIdx]
        if (want != gas.n) { gas.setCount(want, gas.temperature().takeIf { it > 0 } ?: gas.wallT); band.fill(-1) }
        if (s <= 0) return
        gas.step(s)
        val p = gas.takePressure()
        val a = 1 - exp(-s / 1.5)
        pEma += a * (p - pEma)
        tEma += (1 - exp(-s / 3.0)) * (gas.temperature() - tEma)
        val z = p * gas.volume / (gas.n * gas.temperature())
        zEma += (1 - exp(-s / 5.0)) * (z - zEma)
        gas.speedHistogram(histTmp, vMaxScreen)
        val ah = 1 - exp(-s / 1.8)
        for (k in 0 until NB) histEma[k] += ah * (histTmp[k] - histEma[k])
        trailAcc += s
        while (trailAcc >= 1 / TRAIL_HZ) {
            trailAcc -= 1 / TRAIL_HZ
            System.arraycopy(trail, 3, trail, 0, (TRAIL_N - 1) * 3)
            trail[(TRAIL_N - 1) * 3] = gas.x[0].toFloat(); trail[(TRAIL_N - 1) * 3 + 1] = gas.y[0].toFloat(); trail[(TRAIL_N - 1) * 3 + 2] = gas.z[0].toFloat()
        }
    }

    private fun sync() {
        val toReal = GasScale.speedToReal
        for (i in 0 until CAP) {
            val m = mols[i]
            if (i >= gas.n) { m.visible = false; continue }
            m.visible = true
            m.pos = P3(gas.x[i].toFloat(), gas.y[i].toFloat(), gas.z[i].toFloat())
            val b = minOf(NB - 1, (gas.speed(i) * toReal / V_MAX_REAL * NB).toInt())
            if (b != band[i]) { band[i] = b; m.setMat(bandMat[b]) }
        }
        halo.pos = mols[0].pos
        // the tracer's trail ends at the molecule itself
        val last = (TRAIL_N - 1) * 3
        trail[last] = gas.x[0].toFloat(); trail[last + 1] = gas.y[0].toFloat(); trail[last + 2] = gas.z[0].toFloat()
        trailWriter.write(trailMesh, trail, 0.0017f); meshEdited(trailMesh)

        piston.pos = P3(gas.xLo.toFloat(), YC, 0f)

        // histogram bars + the Maxwell curve at the gas's measured temperature
        val bw = (HX1 - HX0) / NB
        for (k in 0 until NB) {
            val h = (histEma[k].toFloat() * H_SCALE).coerceIn(0.0015f, 0.50f)
            bars[k].scale = P3(bw * 0.84f, h, 0.010f); bars[k].pos = P3(HX0 + bw * (k + 0.5f), HB + h / 2, HZ_)
        }
        val tg = tEma
        if (abs(tg / curveT - 1) > 0.01) { curveT = tg; MeshGen.tubeInto(curveMesh, curvePath(tg), 0.0026f, 6, false); meshEdited(curveMesh) }

        // gauge, thermometer, heater
        val atm = pEma / pRef
        needle.rot = Q.axisAngle(0f, 0f, 1f, gaugeAngle(atm.coerceIn(0.0, 6.2)))
        val tk = GasScale.kelvin(tg).toFloat()
        val ch = (thY(tk) - TH_Y0).coerceIn(0.002f, TH_LEN)
        column.scale = P3(0.0105f, ch, 0.0105f); column.pos = P3(THERMO_X, TH_Y0 + ch / 2, 0f)
        setMark.pos = P3(THERMO_X + 0.030f, thY(tSetK), 0f)
        if (tSetK != heaterAt) {
            heaterAt = tSetK
            val c = heaterColour(tSetK)
            for (n in coils) n.setMat(Mat.Unlit(c, alpha = 1f, blend = Blend.ADD))
            setMark.setMat(Mat.Unlit(ProcTex.okLerp(c, Rgb(1f, 1f, 1f), 0.2f)))
            heaterPlate.setMat(Mat.Pbr(Rgb(0.30f, 0.31f, 0.33f), metallic = 1f, roughness = 0.4f, emissive = dim(c, 0.35f)))
        }

        // text refreshes at 10 Hz, and at once when the display language flips
        if (frame++ % 6 == 0 || Lang.en != textEn) { textEn = Lang.en; updateReadout(atm, tk); updateLabels(atm, tk) }
    }

    private fun updateReadout(atm: Double, tk: Float) {
        var sum = 0.0; for (i in 0 until gas.n) sum += gas.speed(i)
        val mean = if (gas.n > 0) sum / gas.n * GasScale.speedToReal else 0.0
        readoutText = tr("压强就是分子撞壁：p = NkT/V —— 压缩 V 则 p ∝ 1/V，加热 T 则 p ∝ T；速率服从麦克斯韦分布\n",
                "Pressure = molecules hitting walls: p = NkT/V; squeeze: p ∝ 1/V, heat: p ∝ T; speeds: Maxwell\n") +
            tr("p = %.2f atm   V = %d%%   T = %d K（加热板 %d K）   N = %d   平均速率 %d m/s（N₂）\n",
                "p = %.2f atm   V = %d%%   T = %d K (heater %d K)   N = %d   mean speed %d m/s (N₂)\n").format(
                atm, (vFrac * 100).roundToInt(), tk.roundToInt(), tSetK.roundToInt(), gas.n, mean.roundToInt()) +
            tr("pV/NkT = %.3f（理想气体 = 1；多出的来自分子自身体积）   ·   每个小球代表 %s 个 N₂ 分子",
                "pV/NkT = %.3f (ideal = 1; excess = molecules' own volume) · 1 ball = %s N₂ molecules").format(zEma, PER_BALL)
    }

    private fun updateLabels(atm: Double, tk: Float) {
        labels.clear()
        labels += LabelSpec("p = %.2f atm".format(atm), P3(GAUGE_X, GAUGE_Y + GAUGE_R + 0.045f, 0.02f))
        labels += LabelSpec("T = %d K".format(tk.roundToInt()), P3(THERMO_X, TH_Y0 + TH_LEN + 0.05f, 0.02f))
        labels += LabelSpec("V = %d%%".format((vFrac * 100).roundToInt()), P3(gas.xLo.toFloat() - R - PIST_T - ROD_LEN, YC + 0.12f, 0.02f))
        labels += LabelSpec(tr("加热板 %d K", "Heater %d K").format(tSetK.roundToInt()), P3((GX0 + GX1) / 2, GY0 - 0.075f, GZ + 0.05f))
        labels += LabelSpec(tr("速率分布 · 曲线 = 麦克斯韦", "Speed distribution · curve = Maxwell"), P3((HX0 + HX1) / 2, HB + 0.545f, HZ_))
        labels += LabelSpec("0 → 1400 m/s", P3((HX0 + HX1) / 2, HB - 0.045f, HZ_ + 0.01f))
    }

    // ---------------------------------------------------------------- interaction

    override val tapTargets get() = listOf(
        TapTarget("PistonHandle", "活塞 → 体积 100/75/50%", "Piston → V 100/75/50%"),
        TapTarget("Heater", "加热板 → 300/600/150 K", "Heater → 300/600/150 K"),
    )

    override fun isTarget(name: String) = name == "PistonHandle" || name == "Heater"

    /** Handle: volume 100 -> 75 -> 50 % (Boyle). Heater: 300 -> 600 -> 150 K (Gay-Lussac). */
    override fun onTap(name: String) {
        when (name) {
            "PistonHandle" -> vFrac = when { vFrac > 0.8f -> 0.75f; vFrac > 0.6f -> 0.5f; else -> 1f }
            "Heater" -> tSetK = when { tSetK < 250f -> 300f; tSetK < 450f -> 600f; else -> 150f }
        }
    }

    /** Boltzmann's start: every molecule at the same speed; watch the spike relax onto the curve. */
    fun equalSpeeds() {
        gas.equalizeSpeeds()
        gas.speedHistogram(histEma, vMaxScreen)
        band.fill(-1)
    }

    fun resetAll() {
        tSetK = 300f; vFrac = 1f
        gas.xLo = XLO_FULL.toDouble(); gas.pistonTarget = gas.xLo
        gas.initMaxwell(N_CHOICES[nIdx], GasScale.modelT(300.0))
        gas.speedHistogram(histEma, vMaxScreen); band.fill(-1)
        pEma = gas.n * gas.temperature() / gas.volume * 1.04
        tEma = gas.temperature()
    }

    @Composable
    override fun Panel() {
        LabPanel(readout) {
            LabRow {
                SegmentControl {
                    SPEED_LABELS.forEachIndexed { k, l -> SegmentItem(selected = speedIdx == k, onClick = { speedIdx = k }, title = { Text(l) }) }
                }
                Spacer(Modifier.width(20.dp))
                SegmentControl {
                    N_CHOICES.forEachIndexed { k, n -> SegmentItem(selected = nIdx == k, onClick = { nIdx = k }, title = { Text("N $n") }) }
                }
            }
            LabSlider(tr("加热板 T", "Heater T"), tSetK, 100f..600f, "%d K".format(tSetK.roundToInt())) { tSetK = (it / 10f).roundToInt() * 10f }
            LabSlider(tr("体积 V", "Volume V"), vFrac, V_MIN..1f, "%d%%".format((vFrac * 100).roundToInt())) { vFrac = (it * 20f).roundToInt() / 20f }
            LabRow {
                Button(onClick = { equalSpeeds() }) { Text(tr("全部同速（玻尔兹曼）", "All same speed (Boltzmann)")) }
                Spacer(Modifier.width(16.dp))
                Button(onClick = { resetAll() }) { Text(tr("复位", "Reset")) }
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    /** Dial angle (rad, CCW about +z from straight up) for [atm]: 0 atm at +120 deg, 6 atm at -120 deg. */
    private fun gaugeAngle(atm: Double): Float = Math.toRadians(120.0 - 40.0 * atm).toFloat()

    private fun thY(k: Float) = TH_Y0 + TH_LEN * (k / TH_MAX_K).coerceIn(0f, 1f)

    private fun curvePath(t: Double): FloatArray {
        val out = FloatArray(CURVE_N * 3)
        val vMax = vMaxScreen; val dv = vMax / NB
        for (k in 0 until CURVE_N) {
            val v = vMax * k / (CURVE_N - 1)
            val f = (Maxwell.pdf(v, t) * dv).toFloat()
            out[k * 3] = HX0 + (HX1 - HX0) * k / (CURVE_N - 1)
            out[k * 3 + 1] = HB + 0.002f + (f * H_SCALE).coerceAtMost(0.52f)
            out[k * 3 + 2] = HZ_ + 0.009f
        }
        return out
    }

    /** Heater colour: icy blue when cooling, dim when at room temperature, orange-red when hot. */
    private fun heaterColour(k: Float): Rgb {
        val warm = Rgb(1.00f, 0.42f, 0.12f); val cold = Rgb(0.35f, 0.72f, 1.00f); val idle = Rgb(0.30f, 0.22f, 0.16f)
        return if (k >= 300f) ProcTex.okLerp(idle, warm, ((k - 300f) / 300f).coerceIn(0f, 1f))
        else ProcTex.okLerp(idle, cold, ((300f - k) / 200f).coerceIn(0f, 1f))
    }

    private fun dim(c: Rgb, k: Float) = Rgb(c.r * k, c.g * k, c.b * k)
    private fun rotZ90() = Q.axisAngle(0f, 0f, 1f, (PI / 2).toFloat())
    private fun rotX90() = Q.axisAngle(1f, 0f, 0f, (PI / 2).toFloat())
    private fun seg(x0: Float, y0: Float, x1: Float, y1: Float, z: Float, r: Float) =
        MeshGen.tube(floatArrayOf(x0, y0, z, x1, y1, z), r, 6, false)
}
