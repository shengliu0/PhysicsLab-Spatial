// 化学平衡 · 勒夏特列原理 (Le Chatelier, 1884) — 2 NO₂ (brown) ⇌ N₂O₄ (colourless), ΔH° = −57.2 kJ/mol.
//
// LESSON (macro + micro side by side): three identical sealed flasks of the same NO₂/N₂O₄ fill stand in
// ice water (0 °C), room-temperature water (25 °C) and hot water (80 °C) in front of a light box. Their
// colour is Beer–Lambert on c(NO₂), so Le Chatelier reads at a glance: pale yellow / orange-brown / dark
// red-brown (c = 4.9 / 12.7 / 45 mmol/L). Pinch a bath and the selected flask is lifted into it and
// changes colour as it warms or cools. Flask B carries a gas syringe: push the plunger and the colour
// goes DARKER at once (same molecules, less space) and then slowly PALER (the equilibrium shifts toward
// the side with fewer molecules, N₂O₄) — the strip chart on the right shows the jump and the relaxation.
// Above the selected flask floats a glass "molecule window": 100 NO₂ units as real-geometry molecules,
// pairs colliding into N₂O₄ (warm flash) and dimers breaking apart (cool flash) — at equal rates once at
// equilibrium, which is what "dynamic equilibrium" means. Its lid moves with flask B's syringe.
//
// Physics: core/equilibrium (closed-form equilibrium and relaxation, Beer–Lambert colour, exact
// Gillespie micro-chemistry that converges to the flask's own Kp). Chemistry is shown in slow motion:
// the real gas re-equilibrates in far under a millisecond, so the two steps of a compression would merge.
//
// THE FILL AND THE COLOUR. Every flask holds the same mixture: 1.000 bar total at 25 °C (P0 = 1.685 bar of
// NO₂ units), so Kp·P0 ~ 1-10 and the shifts are large (x(NO₂) 14 % / 32 % / 80 % at 0 / 25 / 80 °C). Hue
// and RELATIVE darkness are Beer–Lambert with NO₂'s own cross-sections; the absolute darkness is drawn
// for a 12 mm layer — at 1 bar the full 14 cm drawn bulb would be black (τ_green ≈ 15). (Real teaching
// tubes are ~2 cm across and filled nearer 0.1-0.3 bar for the same reason.) Gas is an alpha-blended
// layer computed to reproduce exactly light-box × transmittance in front of the light box.

package com.bytedance.physicslab.solar.demos.equilibrium

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bytedance.physicslab.solar.core.P3
import com.bytedance.physicslab.solar.core.equilibrium.Dimerization
import com.bytedance.physicslab.solar.core.equilibrium.MicroBox
import com.bytedance.physicslab.solar.core.equilibrium.NO2Optics
import com.bytedance.physicslab.solar.core.equilibrium.Vessel
import com.bytedance.physicslab.solar.lab.Lang
import com.bytedance.physicslab.solar.lab.Q
import com.bytedance.physicslab.solar.lab.SpecDemo
import com.bytedance.physicslab.solar.lab.TapTarget
import com.bytedance.physicslab.solar.lab.spec.Blend
import com.bytedance.physicslab.solar.lab.spec.LabelSpec
import com.bytedance.physicslab.solar.lab.spec.Mat
import com.bytedance.physicslab.solar.lab.spec.Node
import com.bytedance.physicslab.solar.lab.tr
import com.bytedance.physicslab.solar.lab.ui.Button
import com.bytedance.physicslab.solar.lab.ui.LabPanel
import com.bytedance.physicslab.solar.lab.ui.LabRow
import com.bytedance.physicslab.solar.lab.ui.LabSlider
import com.bytedance.physicslab.solar.lab.ui.LabSwitch
import com.bytedance.physicslab.solar.lab.ui.SegmentControl
import com.bytedance.physicslab.solar.lab.ui.SegmentItem
import com.bytedance.physicslab.solar.render.Image
import com.bytedance.physicslab.solar.render.MeshData
import com.bytedance.physicslab.solar.render.MeshGen
import com.bytedance.physicslab.solar.render.ProcTex
import com.bytedance.physicslab.solar.render.Rgb
import com.pico.spatial.ui.design.Text
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object EquilibriumDemo : SpecDemo() {
    override val id = "equilibrium"
    override val title = "化学平衡 · 勒夏特列原理"  // i18n-ok (zh half of DemoRegistry.titleOf; English in TITLE_EN)
    override val credit = "Le Chatelier · 1884"

    // ---------------------------------------------------------------- layout (metres, volume space)
    private const val PLINTH_Y = -0.262f
    private const val DECK = PLINTH_Y + 0.0175f
    private val SX = floatArrayOf(-0.60f, -0.18f, 0.24f)          // stations: left / middle / right bath
    private const val SZ = 0.12f
    private const val STAND_H = 0.022f
    private const val STAND_W = 0.36f
    private const val STAND_TOP = DECK + STAND_H
    private const val BR = 0.150f                                  // beaker inner radius
    private const val BEAKER_TOP = -0.020f
    private const val INNER_BOTTOM = STAND_TOP + 0.006f
    private const val WATER = -0.045f
    private const val FR = 0.100f                                  // flask bulb radius (a 100 mL flask drawn ~3.5x life size)
    private const val FY = INNER_BOTTOM + FR + 0.003f              // bulb centre = flask origin
    private const val NECK_R = 0.021f
    private const val NECK_Y0 = FY + FR * 0.93f
    private const val NECK_TOP = 0.030f
    private const val STOPPER_TOP = NECK_TOP + 0.028f
    private const val LIFT = 0.215f                                // bulb clears the beaker rim when moved
    private const val ARC_FRONT = 0.14f; private const val ARC_BACK = 0.08f   // the two flasks pass 0.22 m apart
    // gas syringe on flask B: HORIZONTAL on a glass elbow (x offsets from the flask axis), so it never
    // crosses the molecule window and the plunger moves across the line of sight
    private const val Y_AX = 0.070f                                // syringe axis height (flask at rest)
    private const val SYR_X0 = 0.035f
    private const val SYR_X1 = 0.130f
    private const val SYR_R = 0.022f
    private const val SYR_RI = 0.019f
    private const val SEAL_X0 = 0.041f
    private const val STROKE = 0.080f                              // 0 -> 100 mL
    private const val ROD = 0.085f
    // molecule window: above and a little behind the selected flask
    private const val WIN = 0.165f                                 // half size (0.33 m glass cube)
    private const val Y_W = 0.26f
    private const val Z_W = -0.13f
    // light box behind the flasks (white background = how chemists show NO₂'s colour)
    private const val LB_X0 = -0.82f; private const val LB_X1 = 0.43f; private const val LB_Y1 = 0.045f; private const val LB_Z = -0.10f
    // strip chart
    private const val CH_X0 = 0.53f; private const val CH_X1 = 0.83f; private const val CH_Y0 = -0.17f; private const val CH_H = 0.30f
    private const val CH_Z = -0.10f
    private const val C_MAX = 60.0                                 // mmol/L at the top of the chart (grid every 15)
    private const val HIST = 60
    private const val HIST_DT = 0.4                                // s (simulated) per column

    // ---------------------------------------------------------------- physics constants
    private const val V_FLASK = 1.0e-4                             // m³ (100 mL)
    private const val V_SYR = 1.0e-4                               // syringe 0..100 mL
    private const val KF = 0.25                                    // bar⁻¹ s⁻¹, slow-motion association rate constant
    private const val TAU_TH = 4.0                                 // s, flask ↔ bath heat exchange
    private const val OPT_PATH = 0.012                             // m, display optical path for the bulb colour (see header)
    private const val N_UNITS = 60
    private val P0_FILL = Dimerization.p0ForTotal(1.0, Dimerization.T25)                 // 1.000 bar total at 25 °C
    private val C_UNITS = P0_FILL * Dimerization.P_STD / (Dimerization.R * Dimerization.T25)  // mol/m³ of NO₂ units
    private val SPEEDS = floatArrayOf(0.25f, 0.5f, 1f)
    private val SPEED_LABELS get() = listOf(tr("慢 ×0.25", "Slow ×0.25"), tr("中 ×0.5", "Medium ×0.5"), tr("快 ×1", "Fast ×1"))
    private val LETTER = arrayOf("A", "B", "C")
    private val DEFAULT_T = floatArrayOf(0f, 25f, 80f)

    // ---------------------------------------------------------------- UI state (snapshot state: panel follows taps)
    var speedIdx by mutableIntStateOf(1)
    var showMicro by mutableStateOf(true)
    var selected by mutableIntStateOf(1)
    var syringeTarget by mutableFloatStateOf(100f)                 // mL
    val bathT = mutableStateListOf(0f, 25f, 80f)                   // °C per station

    // ---------------------------------------------------------------- state
    private val stationOf = intArrayOf(0, 1, 2)                    // flask -> station
    private val flaskAt = intArrayOf(0, 1, 2)                      // station -> flask
    private lateinit var vessels: Array<Vessel>
    private var syringeMl = 100.0
    private val micro = MicroBox(N_UNITS, 1884L)
    private var microFlask = -1
    private var simT = 0.0
    private var realT = 0.0
    private var swapT = -1f
    private var swapF1 = 0; private var swapF2 = 0; private var swapS1 = 0; private var swapS2 = 0
    private var winX = SX[1]
    private val hist = Array(3) { FloatArray(HIST) }
    private var histHead = 0
    private var histAcc = 0.0
    private var chartDirty = true
    private var chartFor = -1
    private var frame = 0
    private var textEn = false
    private var lastTap = ""
    private var dragV0 = 0.0
    private var dragAcc = 0f
    // what just happened (readout line 3)
    private var evKind = 0                                          // 0 none, 1 volume, 2 temperature
    private var evFlask = 1
    private var evStart = -1e9; private var evLast = -1e9
    private var evV0 = 0.0; private var evV1 = 0.0; private var evC0 = 1.0
    private var evX0 = 0.0; private var evTb0 = 0f; private var evTb1 = 0f

    // ---------------------------------------------------------------- nodes
    private val flaskRoot = arrayOfNulls<Node>(3)
    private val gasBulb = arrayOfNulls<Node>(3)
    private val gasNeck = arrayOfNulls<Node>(3)
    private val gasIdx = IntArray(9) { -1 }                          // quantised optical depth shown (bulb, neck, barrel)
    private lateinit var barrelGas: Node
    private lateinit var plunger: Node
    private val water = arrayOfNulls<Node>(3)
    private val waterKey = IntArray(3) { Int.MIN_VALUE }
    private val ledRing = arrayOfNulls<Node>(3)
    private val ice = Array(3) { ArrayList<Node>() }
    private val steam = Array(3) { ArrayList<Node>() }
    private lateinit var window: Node
    private lateinit var lid: Node
    private lateinit var no2Nodes: Array<Node>
    private lateinit var no2Glows: Array<Node>
    private lateinit var n2o4Nodes: Array<Node>
    private lateinit var flashNodes: Array<Node>
    private lateinit var beams: Array<Node>
    private lateinit var selRing: Node
    private lateinit var cols: Array<Node>
    private val colIdx = IntArray(HIST) { -1 }

    // ---------------------------------------------------------------- materials (sRGB)
    private val alu = Mat.Pbr(Rgb(0.80f, 0.82f, 0.85f), metallic = 1f, roughness = 0.30f)
    private val darkSteel = Mat.Pbr(Rgb(0.30f, 0.31f, 0.33f), metallic = 1f, roughness = 0.40f)
    private val chrome = Mat.Pbr(Rgb(0.90f, 0.91f, 0.93f), metallic = 1f, roughness = 0.12f)
    private val brass = Mat.Pbr(Rgb(0.96f, 0.78f, 0.45f), metallic = 1f, roughness = 0.22f)
    private val rubber = Mat.Pbr(Rgb(0.42f, 0.17f, 0.13f), metallic = 0f, roughness = 0.65f)
    private val blackRubber = Mat.Pbr(Rgb(0.08f, 0.08f, 0.09f), metallic = 0f, roughness = 0.6f)
    private val plastic = Mat.Pbr(Rgb(0.93f, 0.93f, 0.91f), metallic = 0f, roughness = 0.35f)
    private val glass = Mat.Pbr(Rgb(0.86f, 0.93f, 1.0f), metallic = 0f, roughness = 0.04f, alpha = 0.06f)
    private val flaskGlass = Mat.Pbr(Rgb(0.90f, 0.95f, 1.0f), metallic = 0f, roughness = 0.02f, alpha = 0.09f)
    private val glassBack = Mat.Pbr(Rgb(0.10f, 0.13f, 0.17f), metallic = 0f, roughness = 0.06f, alpha = 0.60f)
    private val glassClear = Mat.Pbr(Rgb(0.70f, 0.82f, 0.95f), metallic = 0f, roughness = 0.04f, alpha = 0.06f)
    private val board = Mat.Pbr(Rgb(0.05f, 0.06f, 0.08f), metallic = 0f, roughness = 0.08f, alpha = 0.85f)
    private val gridLine = Mat.Unlit(Rgb(0.55f, 0.70f, 0.85f), alpha = 0.30f, blend = Blend.TRANSPARENT)
    private val tickMat = Mat.Unlit(Rgb(0.85f, 0.90f, 0.95f), alpha = 0.75f, blend = Blend.TRANSPARENT)
    private val LIGHT_BOX = Rgb(0.90f, 0.90f, 0.87f)
    private val lightBox = Mat.Unlit(LIGHT_BOX)
    private val iceMat = Mat.Pbr(Rgb(0.88f, 0.95f, 1.0f), metallic = 0f, roughness = 0.12f, alpha = 0.78f, emissive = Rgb(0.10f, 0.14f, 0.18f))
    private val beamMat = Mat.Unlit(Rgb(0.45f, 0.80f, 1.0f), alpha = 0.30f, blend = Blend.ADD)
    private val selMat = Mat.Unlit(Rgb(0.45f, 0.85f, 1.0f), alpha = 0.85f, blend = Blend.ADD)
    private val slabTex by lazy {
        ProcTex.bakePlanet(256, 128, Rgb(0.07f, 0.075f, 0.085f), Rgb(0.12f, 0.125f, 0.14f), Rgb(0.20f, 0.205f, 0.22f), 0f, 77)
    }
    private val slab get() = Mat.Pbr(Rgb(1f, 1f, 1f), metallic = 0f, roughness = 0.55f, tex = slabTex)
    private val atomTex: Image by lazy { EqMesh.atomTex() }
    private val blobTex: Image by lazy { EqMesh.blob() }
    private val molMat get() = Mat.Pbr(Rgb(1f, 1f, 1f), metallic = 0f, roughness = 0.32f, tex = atomTex)
    /** NO₂ is the coloured molecule: each one carries a soft brown glow, so the window browns like the flask. */
    private val no2Glow get() = Mat.Unlit(Rgb(0.90f, 0.48f, 0.10f), alpha = 0.42f, tex = blobTex, blend = Blend.ADD)

    // gas colour: Beer–Lambert transmittance over the light box, drawn as an alpha-blended layer that
    // reproduces LB·T exactly in front of the light box (two faces of a double-sided shell)
    private val gasCache = HashMap<Int, Mat>()
    private val colCache = HashMap<Int, Mat>()
    private const val TAU_STEP = 0.02
    private fun lbLin(k: Int) = ProcTex.toLinear(when (k) { 0 -> LIGHT_BOX.r; 1 -> LIGHT_BOX.g; else -> LIGHT_BOX.b }).toDouble()
    private fun gasMat(idx: Int): Mat = gasCache.getOrPut(idx) {
        val t = NO2Optics.transmittanceFromTau(idx * TAU_STEP)
        val aT = (1 - NO2Optics.minChannel(t)).coerceIn(0.0, 0.97)
        if (aT < 0.004) return@getOrPut Mat.Unlit(Rgb(1f, 1f, 1f), alpha = 0f, blend = Blend.TRANSPARENT)
        val c = DoubleArray(3) { k -> (lbLin(k) * (t[k] - (1 - aT)) / aT).coerceAtLeast(0.0) }
        val aS = 1 - sqrt(1 - aT)
        Mat.Unlit(Rgb(EqMesh.srgb(c[0]), EqMesh.srgb(c[1]), EqMesh.srgb(c[2])), alpha = aS.toFloat(), blend = Blend.TRANSPARENT)
    }
    /** What the gas looks like against the light box (the strip-chart columns use it). */
    private fun seenMat(idx: Int): Mat = colCache.getOrPut(idx) {
        val t = NO2Optics.transmittanceFromTau(idx * TAU_STEP)
        Mat.Unlit(Rgb(EqMesh.srgb(lbLin(0) * t[0]), EqMesh.srgb(lbLin(1) * t[1]), EqMesh.srgb(lbLin(2) * t[2])))
    }
    private fun tauIdx(c: Double, path: Double) = (NO2Optics.tauGreen(c, path) / TAU_STEP).roundToInt().coerceIn(0, 400)

    // ================================================================ build

    override fun buildSpec(cacheDir: File): Node {
        resetExperiment()
        frame = 0; textEn = Lang.en; lastTap = ""
        gasIdx.fill(-1); waterKey.fill(Int.MIN_VALUE); colIdx.fill(-1); ice.forEach { it.clear() }; steam.forEach { it.clear() }
        chartDirty = true; chartFor = -1
        micro.angstrom = 0.019; micro.half = (WIN - 0.005f).toDouble(); micro.marginA = 0.040; micro.marginB = 0.056; micro.vRms298 = 0.18
        val root = Node("EquilibriumRoot")
        val unitCyl = MeshGen.cylinder(1f, 1f, 24)
        val unitBox = MeshGen.box(1f, 1f, 1f)
        val quad = EqMesh.quad()

        // ---- bench + light box
        root.add("Plinth", MeshGen.box(1.78f, 0.035f, 0.78f), slab, P3(0f, PLINTH_Y, -0.05f))
        root.add("PlinthBand", MeshGen.box(1.79f, 0.008f, 0.79f), alu, P3(0f, PLINTH_Y, -0.05f))
        val lbW = LB_X1 - LB_X0; val lbH = LB_Y1 - DECK
        root.add("LightBox", MeshGen.box(lbW, lbH, 0.012f), lightBox, P3((LB_X0 + LB_X1) / 2, (DECK + LB_Y1) / 2, LB_Z))
        root.add("LightBoxBack", MeshGen.box(lbW + 0.03f, lbH + 0.015f, 0.02f), darkSteel, P3((LB_X0 + LB_X1) / 2, (DECK + LB_Y1 + 0.015f) / 2, LB_Z - 0.016f))
        root.add("LightBoxTop", MeshGen.box(lbW + 0.03f, 0.014f, 0.036f), alu, P3((LB_X0 + LB_X1) / 2, LB_Y1 + 0.007f, LB_Z - 0.006f))
        for (x in floatArrayOf(LB_X0 - 0.008f, LB_X1 + 0.008f))
            root.add("LightBoxSide", MeshGen.box(0.014f, lbH + 0.014f, 0.036f), alu, P3(x, (DECK + LB_Y1) / 2 + 0.007f, LB_Z - 0.006f))

        // ---- three stations: thermostat stand (coloured ring = bath temperature), beaker, water, ice, steam
        val beakerWall = EqMesh.openTube(BR + 0.003f, 0f, BEAKER_TOP - INNER_BOTTOM + 0.006f, 48)
        val ring = MeshGen.annulus(BR + 0.008f, BR + 0.022f, 72)
        val iceMesh = MeshGen.box(0.044f, 0.034f, 0.044f)
        for (s in 0 until 3) {
            val x = SX[s]
            root.add("Stand$s", MeshGen.box(STAND_W, STAND_H, STAND_W), darkSteel, P3(x, DECK + STAND_H / 2, SZ))
            root.add("StandFoot$s", MeshGen.box(STAND_W + 0.01f, 0.006f, STAND_W + 0.01f), alu, P3(x, DECK + 0.003f, SZ))
            ledRing[s] = root.add("Led$s", ring, heaterMat(bathT[s]), P3(x, STAND_TOP + 0.0012f, SZ))
            root.add("Beaker$s", beakerWall, glass, P3(x, INNER_BOTTOM - 0.006f, SZ))
            root.add("BeakerFoot$s", MeshGen.cylinder(BR + 0.003f, 0.006f, 48), glass, P3(x, INNER_BOTTOM - 0.003f, SZ))
            root.add("BeakerLip$s", MeshGen.tube(ringPath(BR + 0.004f, 64), 0.0035f, 6, true), glass, P3(x, BEAKER_TOP, SZ))
            for (k in 0..4) root.add("BTick$s", MeshGen.box(if (k % 2 == 0) 0.045f else 0.026f, 0.003f, 0.002f), tickMat,
                P3(x - BR * 0.55f, INNER_BOTTOM + 0.034f * (k + 1), SZ + BR * 0.84f))
            // the waterline: a thin bright ring so the (nearly invisible) water still reads
            root.add("Meniscus$s", MeshGen.tube(ringPath(BR - 0.003f, 72), 0.0024f, 5, true), Mat.Unlit(Rgb(0.62f, 0.82f, 1f), alpha = 0.32f, blend = Blend.ADD), P3(x, WATER, SZ))
            water[s] = root.add("Water$s", MeshGen.cylinder(BR - 0.001f, WATER - INNER_BOTTOM, 48), waterMat(bathT[s]), P3(x, (WATER + INNER_BOTTOM) / 2, SZ))
            for (k in 0 until 5) {
                val a = (k * 2 * PI / 5 + 0.4 + s).toFloat(); val rr = 0.104f + 0.010f * (k % 2)
                ice[s] += root.add("Ice${s}_$k", iceMesh, iceMat, P3(x + rr * kotlin.math.cos(a), WATER + 0.004f, SZ + rr * sin(a))).also {
                    it.rot = Q.axisAngle(0.3f, 1f, 0.2f, a * 1.7f)
                }
                steam[s] += root.add("Steam${s}_$k", quad, Mat.Unlit(Rgb(0.92f, 0.94f, 0.97f), alpha = 0.20f, tex = blobTex, blend = Blend.TRANSPARENT)).also { it.billboard = true }
            }
            // pinch zone for the bath: the lower front of the beaker (the flask's own zone is its bulb)
            root.add(Node("Bath$s")).also { it.pos = P3(x, DECK + 0.07f, SZ + BR * 0.85f); it.tapRadius = 0.06f }
        }
        selRing = root.add("SelRing", MeshGen.annulus(BR + 0.012f, BR + 0.025f, 72), selMat, P3(SX[1], BEAKER_TOP + 0.003f, SZ))

        // ---- flasks (origin = bulb centre)
        val bulbGlass = MeshGen.sphere(FR, 28, 48, MeshGen.SphereUv.EQUIRECT)
        val bulbGas = MeshGen.sphere(FR - 0.008f, 24, 40, MeshGen.SphereUv.EQUIRECT)
        for (f in 0 until 3) {
            val fr = root.add(Node("FlaskRig$f")).also { it.pos = restPos(stationOf[f]) }
            flaskRoot[f] = fr
            gasBulb[f] = fr.add("GasBulb$f", bulbGas, gasMat(0))
            fr.add("Flask$f", bulbGlass, flaskGlass).also { it.tapRadius = 0.105f }
            val neckLen = NECK_TOP - NECK_Y0
            fr.add("Neck$f", EqMesh.openTube(NECK_R, 0f, neckLen, 20), flaskGlass, P3(0f, NECK_Y0 - FY, 0f))
            gasNeck[f] = fr.add("GasNeck$f", MeshGen.cylinder(NECK_R - 0.003f, neckLen - 0.012f, 16), gasMat(0), P3(0f, (NECK_Y0 + NECK_TOP - 0.012f) / 2 - FY, 0f))
            fr.add("Lip$f", MeshGen.tube(ringPath(NECK_R + 0.0025f, 28), 0.0032f, 6, true), flaskGlass, P3(0f, NECK_TOP - FY, 0f))
            fr.add("Stopper$f", MeshGen.cylinder(0.0235f, 0.040f, 24), rubber, P3(0f, STOPPER_TOP - 0.020f - FY, 0f))
            if (f == 1) buildSyringe(fr, unitCyl)
        }

        // ---- molecule window (box-local frame = MicroBox frame)
        window = root.add(Node("MolWindow")).also { it.pos = P3(SX[1], Y_W, Z_W) }
        val w2 = 2 * WIN
        window.add("WinBack", MeshGen.box(w2, w2, 0.004f), glassBack, P3(0f, 0f, -WIN - 0.002f))
        window.add("WinFloor", MeshGen.box(w2, 0.004f, w2), glassBack, P3(0f, -WIN - 0.002f, 0f))
        window.add("WinFront", MeshGen.box(w2, w2, 0.004f), glassClear, P3(0f, 0f, WIN + 0.002f))
        window.add("WinTop", MeshGen.box(w2, 0.004f, w2), glassClear, P3(0f, WIN + 0.002f, 0f))
        for (sx in floatArrayOf(-1f, 1f)) window.add("WinSide", MeshGen.box(0.004f, w2, w2), glassClear, P3(sx * (WIN + 0.002f), 0f, 0f))
        val rail = 0.009f
        for (a in floatArrayOf(-1f, 1f)) for (b in floatArrayOf(-1f, 1f)) {
            window.add("WRailX", unitBox, alu, P3(0f, a * (WIN + rail / 2), b * (WIN + rail / 2))).also { it.scale = P3(w2 + 2 * rail, rail, rail) }
            window.add("WRailY", unitBox, alu, P3(a * (WIN + rail / 2), 0f, b * (WIN + rail / 2))).also { it.scale = P3(rail, w2, rail) }
            window.add("WRailZ", unitBox, alu, P3(a * (WIN + rail / 2), b * (WIN + rail / 2), 0f)).also { it.scale = P3(rail, rail, w2) }
        }
        lid = window.add(Node("Lid"))
        lid.add("LidPlate", MeshGen.box(w2 - 0.004f, 0.004f, w2 - 0.004f), Mat.Pbr(Rgb(0.80f, 0.88f, 0.98f), roughness = 0.05f, alpha = 0.22f))
        for (a in floatArrayOf(-1f, 1f)) {
            lid.add("LidRimX", unitBox, brass, P3(0f, 0f, a * (WIN - 0.005f))).also { it.scale = P3(w2 - 0.004f, 0.010f, 0.008f) }
            lid.add("LidRimZ", unitBox, brass, P3(a * (WIN - 0.005f), 0f, 0f)).also { it.scale = P3(0.008f, 0.010f, w2 - 0.004f) }
        }
        val no2Mesh = EqMesh.no2(micro.angstrom); val n2o4Mesh = EqMesh.n2o4(micro.angstrom)
        no2Glows = Array(micro.capA) { window.add("NO2Glow_$it", quad, no2Glow).also { n -> n.billboard = true; n.visible = false; n.uniformScale(0.095f) } }
        no2Nodes = Array(micro.capA) { window.add("NO2_$it", no2Mesh, molMat).also { n -> n.visible = false } }
        n2o4Nodes = Array(micro.capB) { window.add("N2O4_$it", n2o4Mesh, molMat).also { n -> n.visible = false } }
        flashNodes = Array(micro.flashCap) { k -> window.add("Flash_$k", quad, flashMat(0)).also { it.billboard = true; it.visible = false } }
        beams = Array(4) { root.add("Beam$it", unitCyl, beamMat) }

        // ---- strip chart: column height = c(NO₂), column colour = the gas colour against the light box
        val cw = (CH_X1 - CH_X0) / HIST
        root.add("ChartBoard", MeshGen.box(CH_X1 - CH_X0 + 0.07f, CH_H + 0.10f, 0.012f), board, P3((CH_X0 + CH_X1) / 2, CH_Y0 + CH_H / 2 + 0.02f, CH_Z - 0.016f))
        root.add("ChartFoot", MeshGen.box(CH_X1 - CH_X0 + 0.10f, 0.012f, 0.10f), darkSteel, P3((CH_X0 + CH_X1) / 2, DECK + 0.006f, CH_Z - 0.01f))
        root.add("ChartPost", unitCyl, darkSteel, P3((CH_X0 + CH_X1) / 2, (DECK + CH_Y0 - 0.03f) / 2, CH_Z - 0.03f)).also { it.scale = P3(0.008f, CH_Y0 - 0.03f - DECK, 0.008f) }
        for (k in 0..4) {
            val y = CH_Y0 + CH_H * k / 4
            root.add("ChartGrid", seg(CH_X0 - 0.012f, y, CH_X1 + 0.006f, y, CH_Z - 0.007f, if (k == 0) 0.0016f else 0.0008f), if (k == 0) tickMat else gridLine)
        }
        cols = Array(HIST) { k -> root.add("Col$k", unitBox, seenMat(0), P3(CH_X0 + cw * (k + 0.5f), CH_Y0, CH_Z)).also { it.scale = P3(cw * 0.82f, 0.001f, 0.008f) } }

        update(0f)
        return root
    }

    private fun buildSyringe(fr: Node, unitCyl: MeshData) {
        val ya = Y_AX - FY
        val toX = Q.axisAngle(0f, 0f, 1f, (-PI / 2).toFloat())          // local +y -> +x
        val barrelGlass = Mat.Pbr(Rgb(0.88f, 0.94f, 1.0f), roughness = 0.04f, alpha = 0.17f)
        fr.add("Riser", unitCyl, glass, P3(0f, (STOPPER_TOP + Y_AX) / 2 - FY, 0f)).also { it.scale = P3(0.0065f, Y_AX - STOPPER_TOP + 0.004f, 0.0065f) }
        fr.add("Elbow", MeshGen.sphere(0.0078f, 10, 16, MeshGen.SphereUv.EQUIRECT), glass, P3(0f, ya, 0f))
        fr.add("Nozzle", unitCyl, glass, P3(SYR_X0 / 2, ya, 0f)).also { it.scale = P3(0.0065f, SYR_X0, 0.0065f); it.rot = toX }
        fr.add("Barrel", EqMesh.openTube(SYR_R, 0f, SYR_X1 - SYR_X0, 32), barrelGlass, P3(SYR_X0, ya, 0f)).also { it.rot = toX }
        fr.add("BarrelBase", MeshGen.cylinder(SYR_R, 0.004f, 32), barrelGlass, P3(SYR_X0 + 0.002f, ya, 0f)).also { it.rot = toX }
        fr.add("Flange", MeshGen.box(0.005f, 0.072f, 0.034f), plastic, P3(SYR_X1, ya, 0f))
        for (k in 0..10) fr.add("SyrTick", MeshGen.box(0.002f, if (k % 5 == 0) 0.016f else 0.009f, 0.002f), tickMat,
            P3(SEAL_X0 + STROKE * k / 10, ya + SYR_R * 0.45f, SYR_R + 0.0012f))
        barrelGas = fr.add("BarrelGas", unitCyl, gasMat(0)).also { it.rot = toX }
        plunger = fr.add(Node("PlungerRig")).also { it.rot = toX }
        plunger.add("Seal", MeshGen.cylinder(SYR_RI, 0.010f, 28), blackRubber, P3(0f, 0f, 0f))
        plunger.add("Rod", unitCyl, plastic, P3(0f, ROD / 2, 0f)).also { it.scale = P3(0.006f, ROD, 0.006f) }
        plunger.add("Plunger", MeshGen.cylinder(0.030f, 0.008f, 32), plastic, P3(0f, ROD, 0f)).also { it.tapRadius = 0.055f }
    }

    // ================================================================ experiment control

    private fun resetExperiment() {
        // (snapshot-state fields are only written when they differ: buildSpec runs on a worker thread)
        for (k in 0 until 3) { stationOf[k] = k; flaskAt[k] = k; if (bathT[k] != DEFAULT_T[k]) bathT[k] = DEFAULT_T[k] }
        if (selected != 1) selected = 1
        if (syringeTarget != 100f) syringeTarget = 100f
        syringeMl = 100.0
        vessels = Array(3) { f ->
            val v = if (f == 1) V_FLASK + V_SYR else V_FLASK
            Vessel(C_UNITS * v, v, DEFAULT_T[f] + Dimerization.KELVIN)
        }
        simT = 0.0; realT = 0.0; swapT = -1f; winX = SX[1]
        histHead = 0; histAcc = 0.0
        for (f in 0 until 3) hist[f].fill(vessels[f].cNO2().toFloat())
        microFlask = -1
        evKind = 0; evStart = -1e9; evLast = -1e9
        dragAcc = 0f
    }

    fun resetAll() { resetExperiment(); chartDirty = true }

    private fun select(f: Int) { if (f != selected) { selected = f; chartDirty = true } }

    /** Put the selected flask into bath [s]: the flask already there takes its place (both lifted over). */
    private fun moveSelectedTo(s: Int): String {
        val f = selected
        val name = bathNames(bathT[s])
        swapT = -1f                                        // a move still in flight lands at once
        if (stationOf[f] == s) return tr("烧瓶 %s 已在%s里 —— 用面板滑块改水温", "Flask %s is already in the %s; the panel slider sets its temperature").format(LETTER[f], tr(name.first, name.second))
        val tFrom = bathT[stationOf[f]]
        val from = stationOf[f]; val other = flaskAt[s]
        stationOf[f] = s; stationOf[other] = from; flaskAt[s] = f; flaskAt[from] = other
        swapT = 0f; swapF1 = f; swapF2 = other; swapS1 = from; swapS2 = s
        startTempEvent(f, tFrom, bathT[s])
        val t = bathT[s]
        return when {
            t > tFrom + 2f -> tr("烧瓶 %s 放入%s %d °C：颜色变深 —— 吸热方向（分解成 NO₂）被促进",
                "Flask %s into %s at %d °C: it darkens, the endothermic split into NO₂ is favoured")
            t < tFrom - 2f -> tr("烧瓶 %s 放入%s %d °C：颜色变浅 —— 放热方向（结合成 N₂O₄）被促进",
                "Flask %s into %s at %d °C: it pales, the exothermic dimerisation to N₂O₄ is favoured")
            else -> tr("烧瓶 %s 放入%s %d °C：温度差不多，颜色几乎不变", "Flask %s into %s at %d °C: about the same temperature, little change")
        }.format(LETTER[f], tr(name.first, name.second), t.roundToInt())
    }

    private fun startTempEvent(f: Int, tb0: Float, tb1: Float) {
        if (!(evKind == 2 && evFlask == f && simT - evLast < 1.5)) {
            evKind = 2; evFlask = f; evStart = simT; evX0 = vessels[f].moleFractionNO2(); evTb0 = tb0
        }
        evTb1 = tb1; evLast = simT
    }

    /** Panel slider: the bath holding the selected flask. */
    fun setSelectedBath(t: Float) {
        val s = stationOf[selected]
        val old = bathT[s]
        if (old == t) return
        bathT[s] = t
        startTempEvent(selected, old, t)
    }

    /** Tap on the plunger: 100 -> 50 -> 0 -> 100 mL. */
    private fun cyclePlunger(): String {
        select(1)
        val target = when { syringeTarget > 75f -> 50f; syringeTarget > 25f -> 0f; else -> 100f }
        val v0 = V_FLASK * 1e6 + syringeTarget; val v1 = V_FLASK * 1e6 + target
        syringeTarget = target
        return if (target < 100f) tr("推到 %d mL（总体积 ×%.2f）：先变深，再慢慢变浅 —— 平衡向分子数少的 N₂O₄ 移动",
                "Pushed to %d mL (volume ×%.2f): darker at once, then slowly paler as it shifts to fewer molecules (N₂O₄)").format(target.roundToInt(), v1 / v0)
               else tr("拉回到 100 mL（总体积 ×%.2f）：先变浅，再慢慢回深 —— 平衡向分子数多的 NO₂ 移动",
                "Pulled back to 100 mL (volume ×%.2f): paler at once, then slowly darker as it shifts to more molecules (NO₂)").format(v1 / v0)
    }

    // ================================================================ per frame

    override fun update(dt: Float) {
        val sdt = (dt * SPEEDS[speedIdx]).toDouble()
        simT += sdt; realT += dt
        val b = vessels[1]

        // syringe follows its target quickly (real time): the compression itself is "instant"
        val prevV = b.volume; val prevC = b.cNO2()
        val step = 260.0 * dt
        syringeMl = if (abs(syringeTarget - syringeMl) <= step) syringeTarget.toDouble() else syringeMl + step * Math.signum(syringeTarget - syringeMl)
        b.volume = V_FLASK + syringeMl * 1e-6
        if (abs(b.volume - prevV) > 1e-12) {
            if (!(evKind == 1 && simT - evLast < 1.5)) { evKind = 1; evFlask = 1; evStart = simT; evV0 = prevV; evC0 = prevC }
            evV1 = b.volume; evLast = simT
        }

        // swap animation (real time)
        if (swapT >= 0f) { swapT += dt / 1.4f; if (swapT >= 1f) swapT = -1f }

        // chemistry + heat exchange in every flask
        for (f in 0 until 3) vessels[f].step(sdt, bathT[stationOf[f]] + Dimerization.KELVIN, KF, TAU_TH)

        // strip-chart history (simulated time)
        histAcc += sdt
        while (histAcc >= HIST_DT) {
            histAcc -= HIST_DT
            for (f in 0 until 3) hist[f][histHead] = vessels[f].cNO2().toFloat()
            histHead = (histHead + 1) % HIST
            chartDirty = true
        }

        // micro: the window magnifies the selected flask (same T, same P0, same rate constants)
        val v = vessels[selected]
        if (microFlask != selected) {
            microFlask = selected
            micro.reset(v.alpha, v.tGas, kr(v) * N_UNITS * (1 - v.alpha) / 2)
            micro.setTop(lidTop(selected))
        }
        micro.setTemperature(v.tGas)
        micro.setTop(lidTop(selected))
        if (showMicro) micro.step(sdt, Dimerization.kp(v.tGas) * v.p0() / N_UNITS, kr(v))

        place(dt)
        if (frame++ % 6 == 0 || dt == 0f || Lang.en != textEn) { textEn = Lang.en; updateReadout(); updateLabels() }
    }

    private fun kr(v: Vessel) = KF / Dimerization.kp(v.tGas)
    private fun lidTop(f: Int): Double = if (f == 1) -micro.half + 2 * micro.half * (vessels[1].volume / (V_FLASK + V_SYR)) else micro.half

    private fun restPos(s: Int) = P3(SX[s], FY, SZ)

    private fun smooth(t: Float): Float { val x = t.coerceIn(0f, 1f); return x * x * (3 - 2 * x) }

    /** Where flask [f] is drawn (along its arc while two flasks trade places). */
    private fun flaskPos(f: Int): P3 {
        if (swapT < 0f || (f != swapF1 && f != swapF2)) return restPos(stationOf[f])
        val (s0, s1, zs) = if (f == swapF1) Triple(swapS1, swapS2, ARC_FRONT) else Triple(swapS2, swapS1, -ARC_BACK)
        val t = swapT
        val h = smooth(t / 0.3f) * smooth((1 - t) / 0.3f)
        val k = smooth((t - 0.25f) / 0.5f)
        val zo = zs * sin(PI.toFloat() * ((t - 0.15f) / 0.7f).coerceIn(0f, 1f))
        return P3(SX[s0] + (SX[s1] - SX[s0]) * k, FY + LIFT * h, SZ + zo)
    }

    private fun place(dt: Float) {
        // flasks + their gas colour
        for (f in 0 until 3) {
            flaskRoot[f]!!.pos = flaskPos(f)
            val c = vessels[f].cNO2()
            setGas(f * 3, gasBulb[f]!!, tauIdx(c, OPT_PATH))
            setGas(f * 3 + 1, gasNeck[f]!!, tauIdx(c, OPT_PATH * (NECK_R - 0.0025f) / FR))
        }
        // syringe: plunger seal and the gas column between the nozzle end and the seal
        val seal = SEAL_X0 + STROKE * (syringeMl / 100.0).toFloat()
        plunger.pos = P3(seal, Y_AX - FY, 0f)
        val gx0 = SYR_X0 + 0.004f; val gx1 = seal - 0.005f
        barrelGas.visible = gx1 > gx0 + 0.001f
        barrelGas.pos = P3((gx0 + gx1) / 2, Y_AX - FY, 0f); barrelGas.scale = P3(SYR_RI - 0.0015f, maxOf(0.001f, gx1 - gx0), SYR_RI - 0.0015f)
        setGas(5, barrelGas, tauIdx(vessels[1].cNO2(), OPT_PATH * (SYR_RI - 0.0015f) / FR))

        // baths: water tint, thermostat ring, ice cubes, steam
        for (s in 0 until 3) {
            val t = bathT[s]
            val key = (t / 5f).roundToInt()
            if (key != waterKey[s]) { waterKey[s] = key; water[s]!!.setMat(waterMat(t)); ledRing[s]!!.setMat(heaterMat(t)) }
            val cold = t <= 4f
            for (n in ice[s]) n.visible = cold
            val nSteam = if (t < 45f) 0 else (1 + ((t - 45f) / 55f * 4f).toInt()).coerceAtMost(5)
            steam[s].forEachIndexed { k, n ->
                n.visible = k < nSteam
                if (n.visible) {
                    val ph = ((realT * 0.22 + k * 0.23 + s * 0.11) % 1.0).toFloat()
                    val a = (k * 2.4 + s).toFloat()
                    n.pos = P3(SX[s] + 0.10f * kotlin.math.cos(a) + 0.02f * ph, BEAKER_TOP + 0.005f + 0.12f * ph, SZ + 0.08f * sin(a))
                    n.uniformScale((0.025f + 0.035f * ph) * sin(PI.toFloat() * ph).coerceAtLeast(0.05f))
                }
            }
        }

        // selection ring + molecule window above the selected flask
        val sp = flaskPos(selected)
        val swapping = swapT >= 0f
        selRing.visible = !swapping
        selRing.pos = P3(sp.x, BEAKER_TOP + 0.003f, SZ)
        val k = 1 - exp(-dt / 0.12f)
        winX = if (dt == 0f) sp.x else winX + (sp.x - winX) * k
        val showWin = showMicro && !swapping
        window.visible = showWin
        window.pos = P3(winX, Y_W, Z_W)
        lid.visible = selected == 1
        lid.pos = P3(0f, micro.top.toFloat() + 0.004f, 0f)
        if (showWin) {
            for (i in 0 until micro.capA) {
                val n = no2Nodes[i]
                n.visible = micro.aAlive[i]
                no2Glows[i].visible = n.visible
                if (n.visible) {
                    n.pos = P3(micro.ax[i * 3].toFloat(), micro.ax[i * 3 + 1].toFloat(), micro.ax[i * 3 + 2].toFloat())
                    n.rot = Q(micro.aq[i * 4].toFloat(), micro.aq[i * 4 + 1].toFloat(), micro.aq[i * 4 + 2].toFloat(), micro.aq[i * 4 + 3].toFloat())
                    no2Glows[i].pos = P3(n.pos.x, n.pos.y, n.pos.z - 0.006f)
                }
            }
            for (i in 0 until micro.capB) {
                val n = n2o4Nodes[i]
                n.visible = micro.bAlive[i]
                if (n.visible) {
                    n.pos = P3(micro.bx[i * 3].toFloat(), micro.bx[i * 3 + 1].toFloat(), micro.bx[i * 3 + 2].toFloat())
                    n.rot = Q(micro.bq[i * 4].toFloat(), micro.bq[i * 4 + 1].toFloat(), micro.bq[i * 4 + 2].toFloat(), micro.bq[i * 4 + 3].toFloat())
                }
            }
            for (j in 0 until micro.flashCap) {
                val n = flashNodes[j]
                val age = micro.fAge[j]
                n.visible = age < micro.flashLife
                if (n.visible) {
                    val u = (age / micro.flashLife).toFloat()
                    n.pos = P3(micro.fx[j * 3].toFloat(), micro.fx[j * 3 + 1].toFloat(), micro.fx[j * 3 + 2].toFloat() + 0.004f)
                    n.uniformScale(0.13f * sin(PI.toFloat() * (0.15f + 0.85f * u)) * (1.2f - 0.5f * u))
                    n.setMat(flashMat(micro.fKind[j]))            // warm = bond formed, cool = bond broken
                }
            }
        }
        // the "magnifier" beams: bulb shoulder -> window floor corners
        for (b in 0 until 4) {
            val sx = if (b % 2 == 0) -1f else 1f; val sz = if (b < 2) -1f else 1f
            val a = P3(sp.x + sx * 0.045f, sp.y + 0.065f, sp.z + sz * 0.045f)
            val e = P3(winX + sx * WIN, Y_W - WIN, Z_W + sz * WIN)
            placeSeg(beams[b], a, e, 0.0016f)
            beams[b].visible = showWin
        }

        // strip chart (only when a column was added or the selection changed)
        if (chartDirty || chartFor != selected) {
            chartDirty = false; chartFor = selected
            val cw = (CH_X1 - CH_X0) / HIST
            val h = hist[selected]
            for (j in 0 until HIST) {
                val c = h[(histHead + j) % HIST].toDouble()
                val hh = (c / C_MAX * CH_H).toFloat().coerceIn(0.002f, CH_H)
                cols[j].pos = P3(CH_X0 + cw * (j + 0.5f), CH_Y0 + hh / 2, CH_Z)
                cols[j].scale = P3(cw * 0.82f, hh, 0.008f)
                val idx = tauIdx(c, OPT_PATH)
                if (idx != colIdx[j]) { colIdx[j] = idx; cols[j].setMat(seenMat(idx)) }
            }
        }
    }

    private val flashMats by lazy {
        arrayOf(Mat.Unlit(Rgb(1.0f, 0.70f, 0.30f), alpha = 0.95f, tex = blobTex, blend = Blend.ADD),
            Mat.Unlit(Rgb(0.40f, 0.75f, 1.0f), alpha = 0.95f, tex = blobTex, blend = Blend.ADD))
    }
    private fun flashMat(kind: Int) = flashMats[kind.coerceIn(0, 1)]

    private fun setGas(slot: Int, n: Node, idx: Int) { if (gasIdx[slot] != idx) { gasIdx[slot] = idx; n.setMat(gasMat(idx)) } }

    private fun placeSeg(n: Node, a: P3, b: P3, r: Float) {
        val dx = b.x - a.x; val dy = b.y - a.y; val dz = b.z - a.z
        val l = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1e-4f)
        n.pos = P3((a.x + b.x) / 2, (a.y + b.y) / 2, (a.z + b.z) / 2)
        n.rot = Q.fromTo(0f, 1f, 0f, dx, dy, dz)
        n.scale = P3(r, l, r)
    }

    // ================================================================ text

    private fun bathNames(t: Float): Pair<String, String> = when {
        t < -0.5f -> "冰盐水" to "ice-salt bath"   // i18n-ok (zh/en pair)
        t <= 4f -> "冰水" to "ice water"            // i18n-ok
        t < 40f -> "室温水" to "room-temp water"    // i18n-ok
        t < 97f -> "热水" to "hot water"            // i18n-ok
        else -> "沸水" to "boiling water"           // i18n-ok
    }

    private fun updateReadout() {
        val f = selected; val v = vessels[f]
        val tC = v.tGas - Dimerization.KELVIN
        val line1 = tr("勒夏特列：2NO₂（红棕）⇌ N₂O₄（无色），ΔH° = −57.2 kJ/mol —— 升温向吸热的分解方向移动（变深），加压向分子数少的方向移动\n",
            "Le Chatelier: 2NO₂ (brown) ⇌ N₂O₄ (colourless), ΔH° = −57.2 kJ/mol · heating shifts it to NO₂ (darker), squeezing to fewer molecules\n")
        val line2 = tr("烧瓶 %s · 气体 %.1f °C · Kp = %.3g bar⁻¹ · p(NO₂) %.3f + p(N₂O₄) %.3f = %.3f bar · NO₂ 摩尔分数 %.1f%%\n",
            "Flask %s · gas %.1f °C · Kp = %.3g bar⁻¹ · p(NO₂) %.3f + p(N₂O₄) %.3f = %.3f bar · x(NO₂) %.1f%%\n").format(
            LETTER[f], tC, Dimerization.kp(v.tGas), v.pNO2(), v.pN2O4(), v.pTotal(), 100 * v.moleFractionNO2())
        readoutText = line1 + line2 + whatHappened()
    }

    private fun whatHappened(): String {
        val recent = simT - evStart < 40.0 && evFlask == selected
        if (recent && evKind == 1) {
            val b = vessels[1]
            val v0 = (evV0 * 1e6).roundToInt(); val v1 = (evV1 * 1e6).roundToInt()
            val jump = evV0 / evV1; val now = b.cNO2() / evC0; val eq = b.cNO2Eq() / evC0
            return if (evV1 < evV0) tr("刚才压缩 %d → %d mL：c(NO₂) 先瞬间 ×%.2f（变深），现在 ×%.2f → 平衡时 ×%.2f（向 N₂O₄ 移动，变浅）",
                    "Squeezed %d → %d mL: c(NO₂) jumped ×%.2f (darker), now ×%.2f → ×%.2f at equilibrium (shift to N₂O₄, paler)").format(v0, v1, jump, now, eq)
                   else tr("刚才拉回 %d → %d mL：c(NO₂) 先瞬间 ×%.2f（变浅），现在 ×%.2f → 平衡时 ×%.2f（向 NO₂ 移动，回深）",
                    "Pulled %d → %d mL: c(NO₂) dropped ×%.2f (paler), now ×%.2f → ×%.2f at equilibrium (shift to NO₂, darker)").format(v0, v1, jump, now, eq)
        }
        if (recent && evKind == 2) {
            val v = vessels[evFlask]
            val xEq = Dimerization.moleFractionNO2(Dimerization.alphaEq(v.units * Dimerization.R * (evTb1 + Dimerization.KELVIN) / v.volume / Dimerization.P_STD, evTb1 + Dimerization.KELVIN))
            return (if (evTb1 > evTb0) tr("刚才：烧瓶 %s 到了 %d °C 的水里 —— 吸热方向（分解成 NO₂）被促进：NO₂ 摩尔分数 %.0f%% → %.0f%%（平衡 %.0f%%）",
                    "Flask %s went into %d °C water: the endothermic split to NO₂ is favoured, x(NO₂) %.0f%% → %.0f%% (eq. %.0f%%)")
                    else tr("刚才：烧瓶 %s 到了 %d °C 的水里 —— 放热方向（结合成 N₂O₄）被促进：NO₂ 摩尔分数 %.0f%% → %.0f%%（平衡 %.0f%%）",
                    "Flask %s went into %d °C water: the exothermic dimerisation is favoured, x(NO₂) %.0f%% → %.0f%% (eq. %.0f%%)")).format(
                LETTER[evFlask], evTb1.roundToInt(), 100 * evX0, 100 * v.moleFractionNO2(), 100 * xEq)
        }
        if (showMicro) {
            val sp = SPEEDS[speedIdx]
            // cold N₂O₄ splits slowly (k_r = k_f/Kp): below 1 event/s show two decimals, never a misleading "0.0"
            fun r(x: Double) = if (x < 1.0) "%.2f".format(x) else "%.1f".format(x)
            return tr("分子窗口：NO₂ %d 个 · N₂O₄ %d 个 —— 每秒结合 %s 次 ≈ 分解 %s 次：正逆反应同时进行、速率相等 = 动态平衡",
                "Molecule window: NO₂ %d · N₂O₄ %d · %s dimerisations/s ≈ %s splits/s: both directions at equal rates = dynamic equilibrium").format(
                micro.nChem, micro.mChem, r(micro.assocRate * sp), r(micro.dissocRate * sp))
        }
        val t = vessels[selected].tGas
        return tr("ΔG° = ΔH° − TΔS° = %+.1f kJ/mol → Kp = e^(−ΔG°/RT) = %.3g bar⁻¹；放热反应：温度越高 Kp 越小",
            "ΔG° = ΔH° − TΔS° = %+.1f kJ/mol → Kp = e^(−ΔG°/RT) = %.3g bar⁻¹; exothermic, so Kp falls as T rises").format(
            Dimerization.deltaG(t) / 1000, Dimerization.kp(t))
    }

    private fun updateLabels() {
        labels.clear()
        for (s in 0 until 3) {
            val n = bathNames(bathT[s])
            labels += LabelSpec("%s · %s %d °C".format(LETTER[flaskAt[s]], tr(n.first, n.second), bathT[s].roundToInt()),
                P3(SX[s], STAND_TOP - 0.002f, SZ + BR + 0.045f), visible = swapT < 0f)
        }
        labels += LabelSpec(tr("分子窗口 · NO₂（棕色光晕）%d 个 · N₂O₄ %d 个", "Molecules · NO₂ (brown glow) %d · N₂O₄ %d").format(micro.nChem, micro.mChem),
            P3(winX, Y_W + WIN + 0.025f, Z_W + WIN), visible = showMicro && swapT < 0f)
        labels += LabelSpec(tr("烧瓶 %s 的颜色深浅 = c(NO₂) · 每格 15 mmol/L", "Flask %s colour depth = c(NO₂) · 15 mmol/L per line").format(LETTER[selected]),
            P3((CH_X0 + CH_X1) / 2, CH_Y0 + CH_H + 0.06f, CH_Z + 0.01f))
        val bp = flaskPos(1)
        labels += LabelSpec(tr("注射器 %d mL · 共 %d mL", "Syringe %d mL · total %d mL").format(syringeMl.roundToInt(), (vessels[1].volume * 1e6).roundToInt()),
            P3(bp.x + 0.11f, bp.y - FY + Y_AX + 0.055f, bp.z + 0.03f), visible = swapT < 0f)
    }

    // ================================================================ interaction

    override val tapTargets: List<TapTarget> get() {
        val list = ArrayList<TapTarget>()
        for (f in 0 until 3) list += TapTarget("Flask$f", "烧瓶 ${LETTER[f]} → 选中", "Flask ${LETTER[f]} → select") { selected == f }
        for (s in 0 until 3) { val n = bathNames(bathT[s]); list += TapTarget("Bath$s", "${n.first} → 放入", "${n.second.replaceFirstChar { it.uppercase() }} → put in") }
        list += TapTarget("Plunger", "活塞 → 推/拉", "Plunger → push/pull")
        return list
    }

    override fun isTarget(name: String) = name.startsWith("Flask") || name.startsWith("Bath") || name == "Plunger"
    override fun isDragTarget(name: String) = name == "Plunger"

    override fun onTap(name: String) {
        lastTap = when {
            name == "Plunger" -> cyclePlunger()
            name.startsWith("Flask") -> {
                val f = name.removePrefix("Flask").toIntOrNull() ?: return
                select(f)
                val n = bathNames(bathT[stationOf[f]])
                tr("选中烧瓶 %s（%s %d °C）：分子窗口移到它上方", "Flask %s selected (%s %d °C): the molecule window moves above it").format(
                    LETTER[f], tr(n.first, n.second), bathT[stationOf[f]].roundToInt())
            }
            name.startsWith("Bath") -> moveSelectedTo(name.removePrefix("Bath").toIntOrNull() ?: return)
            else -> ""
        }
    }

    override fun tapMessage(name: String): String? = lastTap.ifEmpty { null }

    override fun onDragStart(name: String) { select(1); dragV0 = syringeTarget.toDouble(); dragAcc = 0f }
    override fun onDrag(name: String, dx: Float, dy: Float, dz: Float) {
        dragAcc += dx                                         // the plunger slides along +x (pull = right)
        syringeTarget = (dragV0 + dragAcc / STROKE * 100.0).toFloat().coerceIn(0f, 100f)
    }
    override fun onDragEnd(name: String) { syringeTarget = syringeTarget.roundToInt().toFloat() }

    override fun reviewVariants(): Map<String, () -> Unit> = mapOf(
        "squeezed" to { select(1); syringeTarget = 0f; syringeMl = 0.0; vessels[1].volume = V_FLASK; evKind = 1; evFlask = 1; evStart = simT; evLast = simT; evV0 = V_FLASK + V_SYR; evV1 = V_FLASK; evC0 = vessels[1].cNO2() / 2 },
        "ice_window" to { resetAll(); select(0); repeat(120) { update(1f / 60f) } },
        "ice_to_hot" to { resetAll(); select(0); moveSelectedTo(2); val s = speedIdx; speedIdx = 2; repeat(420) { update(1f / 60f) }; speedIdx = s },
    )

    @Composable
    override fun Panel() {
        LabPanel(readout) {
            LabRow {
                SegmentControl {
                    SPEED_LABELS.forEachIndexed { k, l -> SegmentItem(selected = speedIdx == k, onClick = { speedIdx = k }, title = { Text(l) }) }
                }
                Spacer(Modifier.width(16.dp))
                LabSwitch(tr("分子视图", "Molecule view"), showMicro) { showMicro = it; if (it) microFlask = -1 }
                Button(onClick = { resetAll() }) { Text(tr("复位", "Reset")) }
            }
            val tb = bathT[stationOf[selected]]
            LabSlider(tr("烧瓶 %s 的水浴", "Bath of flask %s").format(LETTER[selected]), tb, -10f..100f, "%d °C".format(tb.roundToInt())) {
                setSelectedBath(it.roundToInt().toFloat())
            }
            LabSlider(tr("注射器 B", "Syringe B"), syringeTarget, 0f..100f, "%d mL".format(syringeTarget.roundToInt())) {
                select(1); syringeTarget = (it / 5f).roundToInt() * 5f
            }
        }
    }

    // ================================================================ helpers

    private fun waterMat(t: Float): Mat {
        val cold = Rgb(0.62f, 0.88f, 1.0f); val room = Rgb(0.62f, 0.80f, 0.96f); val hot = Rgb(0.86f, 0.80f, 0.74f)
        val c = if (t < 25f) ProcTex.okLerp(room, cold, ((25f - t) / 30f).coerceIn(0f, 1f)) else ProcTex.okLerp(room, hot, ((t - 25f) / 70f).coerceIn(0f, 1f))
        return Mat.Pbr(c, metallic = 0f, roughness = 0.05f, alpha = 0.08f)
    }

    /** Thermostat ring: icy blue when cooling, dim at room temperature, orange-red when heating. */
    private fun heaterMat(t: Float): Mat {
        val warm = Rgb(1.00f, 0.42f, 0.12f); val cold = Rgb(0.30f, 0.72f, 1.00f); val idle = Rgb(0.28f, 0.26f, 0.24f)
        val c = if (t >= 25f) ProcTex.okLerp(idle, warm, ((t - 25f) / 55f).coerceIn(0f, 1f)) else ProcTex.okLerp(idle, cold, ((25f - t) / 25f).coerceIn(0f, 1f))
        return Mat.Unlit(c, alpha = 1f, blend = Blend.ADD)
    }

    private fun ringPath(r: Float, n: Int) = FloatArray(n * 3) { k -> val a = 2 * PI * (k / 3) / n; when (k % 3) { 0 -> (r * kotlin.math.cos(a)).toFloat(); 1 -> 0f; else -> (r * sin(a)).toFloat() } }

    private fun seg(x0: Float, y0: Float, x1: Float, y1: Float, z: Float, r: Float) = MeshGen.tube(floatArrayOf(x0, y0, z, x1, y1, z), r, 6, false)
}
