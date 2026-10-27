// 电解水 · Electrolysis of Water (Nicholson & Carlisle, 1800) — macro apparatus + micro electrode views.
//
// LESSON: electricity splits water into exactly twice as much hydrogen as oxygen, because every H₂
// takes 2 electrons and every O₂ gives up 4. In the middle, a Hofmann voltameter on a DC supply:
// bubbles stream off two platinum foils, gas collects under the stopcocks and the water in the
// cathode tube drops TWICE as far as in the anode tube (the displaced water rises in the reservoir).
// On either side a glass "magnifier" window (lines run from the foil to it) shows the half reaction
// at that foil with molecules: cathode 2 H₂O + 2 e⁻ → H₂ + 2 OH⁻ (it runs twice as often), anode
// 2 H₂O → O₂ + 4 H⁺ + 4 e⁻; Na⁺ and SO₄²⁻ drift in the field and carry the current but never react;
// electrons stream along the leads. Pure water hardly conducts (no ions, no bubbles) — add Na₂SO₄.
// Below the decomposition voltage nothing happens. Open a stopcock to test the gas: a burning
// splint pops at the hydrogen, a glowing splint bursts into flame in the oxygen.
//
// Interaction: pinch-drag the supply knob (or pinch to step 0/2/4/6/8/12 V); pinch the reagent bottle
// (Na₂SO₄ on/off); pinch a stopcock (gas test with the splint); pinch the reservoir (refill).
//
// Physics: core/electrolysis (Faraday's law with z read off the half reactions, ideal-gas volume at
// 25 °C / 1 atm, cell I = (U − U_dec)/R with R = K/κ, the micro model's atom tracks).

package com.bytedance.physicslab.solar.demos.electrolysis

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bytedance.physicslab.solar.core.P3
import com.bytedance.physicslab.solar.core.electrolysis.Cell
import com.bytedance.physicslab.solar.core.electrolysis.Echem
import com.bytedance.physicslab.solar.core.electrolysis.El
import com.bytedance.physicslab.solar.core.electrolysis.Electrolyte
import com.bytedance.physicslab.solar.core.electrolysis.HalfReaction
import com.bytedance.physicslab.solar.core.electrolysis.Micro
import com.bytedance.physicslab.solar.core.electrolysis.Voltameter
import com.bytedance.physicslab.solar.demos.buoyancy.BuoyMesh
import com.bytedance.physicslab.solar.lab.Lang
import com.bytedance.physicslab.solar.lab.Q
import com.bytedance.physicslab.solar.lab.SpecDemo
import com.bytedance.physicslab.solar.lab.TapTarget
import com.bytedance.physicslab.solar.lab.tr
import com.bytedance.physicslab.solar.lab.spec.Blend
import com.bytedance.physicslab.solar.lab.spec.LabelSpec
import com.bytedance.physicslab.solar.lab.spec.Mat
import com.bytedance.physicslab.solar.lab.spec.Node
import com.bytedance.physicslab.solar.lab.ui.Button
import com.bytedance.physicslab.solar.lab.ui.FieldRef
import com.bytedance.physicslab.solar.lab.ui.LabPanel
import com.bytedance.physicslab.solar.lab.ui.LabSlider
import com.bytedance.physicslab.solar.lab.ui.SegmentControl
import com.bytedance.physicslab.solar.lab.ui.SegmentItem
import com.bytedance.physicslab.solar.render.MeshData
import com.bytedance.physicslab.solar.render.MeshGen
import com.bytedance.physicslab.solar.render.ProcTex
import com.bytedance.physicslab.solar.render.Rgb
import com.pico.spatial.ui.design.Text
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

object ElectrolysisDemo : SpecDemo() {
    override val id = "electrolysis"
    override val title = "电解水"   // i18n-ok: Chinese title; the English one is DemoRegistry.TITLE_EN (shown via titleOf)
    override val credit = "Nicholson & Carlisle · 1800"

    // ---------------------------------------------------------------- layout (volume metres)
    private const val PLINTH_Y = -0.262f
    private const val BENCH = PLINTH_Y + 0.0175f
    private const val CX = -0.24f                  // cathode tube (−, H₂)
    private const val RX = -0.06f                  // reservoir column
    private const val NX = 0.12f                   // anode tube (+, O₂)
    private val TUBE_X = floatArrayOf(CX, NX)
    private const val TR = 0.030f                  // tube glass radius (drawn ~2x wide: the columns must read at 2.5 m)
    private const val WR = 0.0265f                 // water radius inside
    private const val TUBE_BOT = -0.19f
    private const val Y0 = 0.29f                   // 0 mL mark: inner top of the graduated tube
    private const val MPM = 0.0072f                // drawn metres per mL (0-50 mL over 36 cm)
    private const val CAP = 50.0
    private const val ELEC_Y = -0.125f             // Pt foil centre
    private const val FOIL_W = 0.032f; private const val FOIL_H = 0.060f
    private const val JUNC_Y = -0.168f
    private const val COCK_Y = 0.322f
    private const val NOZ_TOP = 0.385f
    private const val COL_TOP = 0.24f
    private const val BULB_Y0 = 0.27f; private const val BULB_Y1 = 0.40f
    private const val BULB_R = 0.070f; private const val BULB_WR = 0.066f
    private const val LEVEL0 = 0.29f
    private val RISE_PER_ML = MPM * (WR / BULB_WR) * (WR / BULB_WR)

    // magnifier windows: [0] cathode (plate on its right wall), [1] anode (plate on its left wall)
    private const val WIN_W = 0.30f; private const val WIN_H = 0.32f; private const val WIN_D = 0.10f
    private val WIN_C = arrayOf(P3(-0.575f, 0.07f, 0.02f), P3(0.425f, 0.07f, 0.02f))
    private const val PLATE_IN = WIN_W / 2 - 0.024f   // plate face, window-local |x|
    private const val N_BG = 6; private const val N_NA = 4; private const val N_SO4 = 2

    // power supply
    private const val PSX = 0.74f; private const val PS_W = 0.26f; private const val PS_H = 0.31f
    private const val PS_D = 0.20f; private const val PS_Z = -0.02f
    private const val PS_FRONT = PS_Z + PS_D / 2
    private const val AMY = -0.005f
    private const val AM_PIV = AMY - 0.045f
    private const val AM_FS = 2.5                  // ammeter full scale (A)
    private val KNOB = P3(PSX - 0.055f, -0.165f, PS_FRONT + 0.016f)
    private const val U_MAX = 12f
    private val U_STEPS = floatArrayOf(0f, 2f, 4f, 6f, 8f, 12f)

    // bottle
    private val BOTTLE = P3(-0.82f, BENCH, 0.10f)

    private val LAPSE = intArrayOf(1, 10, 60)
    private val SPEED_LABELS get() = listOf(tr("实时 ×1", "Real ×1"), tr("延时 ×10", "×10"), tr("延时 ×60", "×60"))

    // micro choreography (fractions of one reaction cycle)
    private const val P_SITE = 0.30f; private const val P_REACT = 0.36f; private const val P_RISE = 0.56f; private const val P_GONE = 0.93f
    private const val PERIOD_ANODE = 4.4f                    // display s per O₂ event at 0.8 A
    private const val I_REF = 0.8

    // ---------------------------------------------------------------- state
    var volts = 6f
    var salt = true
    var speedIdx = 1
    private val cell = Voltameter(CAP)
    private val electrolyte get() = if (salt) Electrolyte.NA2SO4 else Electrolyte.PURE
    private var clock = 0f
    private var needle = 0f; private var needleV = 0f
    private var seed = 1L
    private var frame = 0
    private var textEn = false
    private var cockResult = 0                               // 0 started, 1 no gas, 2 busy

    private class GasTest { var t = -1f; var vent0 = 0.0; val active get() = t >= 0f }
    private val tests = arrayOf(GasTest(), GasTest())

    // bubbles: [0, NB_H) hydrogen, [NB_H, NB_H + NB_O) oxygen
    private const val NB_H = 48; private const val NB_O = 24
    private val bOn = BooleanArray(NB_H + NB_O)
    private val bX = FloatArray(NB_H + NB_O); private val bY = FloatArray(NB_H + NB_O); private val bZ = FloatArray(NB_H + NB_O)
    private val bV = FloatArray(NB_H + NB_O); private val bPh = FloatArray(NB_H + NB_O); private val bR = FloatArray(NB_H + NB_O)
    private val bAcc = FloatArray(2)

    // ---------------------------------------------------------------- nodes
    private lateinit var bubbles: Array<Node>
    private lateinit var water: Array<Node>
    private lateinit var gasTint: Array<Node>
    private lateinit var gasCap: Array<Node>
    private lateinit var menisc: Array<Node>
    private lateinit var bulbWater: Node
    private lateinit var bulbMenisc: Node
    private lateinit var handles: Array<Node>
    private lateinit var puffs: Array<Array<Node>>
    private lateinit var splint: Array<Array<Node>>         // [tube][stick, ember, flameOuter, flameCore]
    private lateinit var popFlash: Array<Node>
    private lateinit var needleNode: Node
    private lateinit var knobRot: Node
    private lateinit var voltDots: Array<Node>
    private lateinit var led: Node
    private lateinit var leadPath: Array<FloatArray>
    private lateinit var leadBeads: Array<Array<Node>>
    private val leadPhase = DoubleArray(2)
    private lateinit var wins: Array<Win>

    /** One magnifier window: [s] = +1 plate on the window's +x wall (cathode), −1 on its −x wall (anode). */
    private class Win(val s: Float, val c: P3, val rx: HalfReaction) {
        val n = rx.tracks.size
        lateinit var atoms: Array<Node>
        lateinit var halos: Array<Node?>
        lateinit var elec: Array<Node>
        lateinit var sparks: Array<Node>
        lateinit var bg: Array<Node>
        lateinit var ions: Array<Node>
        lateinit var ionHalos: Array<Node>
        val bgA = Array(N_BG) { FloatArray(7) }
        val ionU = FloatArray(N_NA + N_SO4); val ionV = FloatArray(N_NA + N_SO4); val ionW = FloatArray(N_NA + N_SO4)
        val start = Array(n) { FloatArray(3) }; val site = Array(n) { FloatArray(3) }
        val prodStart = Array(rx.products.size) { FloatArray(3) }; val prodEnd = Array(rx.products.size) { FloatArray(3) }
        val prodOff = Array(n) { FloatArray(2) }
        /** tracks whose atom ends in the gas (product 0), and each product's first atom (its halo rides on it) */
        val gasTracks = rx.tracks.indices.filter { rx.tracks[it].to == 0 }.toIntArray()
        val firstAtom = IntArray(rx.products.size) { j -> rx.tracks.indices.first { rx.tracks[it].to == j } }
        var phase = 0f
        /** plate frame (u from the plate into the solution, v up, w toward the viewer) -> volume */
        fun at(u: Float, v: Float, w: Float) = P3(c.x + s * (PLATE_IN - u), c.y + v, c.z + w)
    }

    // ---------------------------------------------------------------- look (sRGB)
    private val alu = Mat.Pbr(Rgb(0.80f, 0.82f, 0.85f), metallic = 1f, roughness = 0.32f)
    private val darkSteel = Mat.Pbr(Rgb(0.30f, 0.31f, 0.33f), metallic = 1f, roughness = 0.40f)
    private val rubber = Mat.Pbr(Rgb(0.10f, 0.10f, 0.11f), metallic = 0f, roughness = 0.6f)
    private val platinum = Mat.Pbr(Rgb(0.90f, 0.91f, 0.93f), metallic = 1f, roughness = 0.18f, emissive = Rgb(0.10f, 0.10f, 0.11f))
    private val foilMat = Mat.Pbr(Rgb(0.92f, 0.93f, 0.95f), metallic = 0.6f, roughness = 0.22f, emissive = Rgb(0.50f, 0.51f, 0.55f))
    private val glass = Mat.Pbr(Rgb(0.86f, 0.93f, 1.0f), metallic = 0f, roughness = 0.04f, alpha = 0.16f)
    private val waterMat = Mat.Pbr(Rgb(0.28f, 0.58f, 0.98f), metallic = 0f, roughness = 0.05f, alpha = 0.46f)
    private val menMat = Mat.Unlit(Rgb(0.80f, 0.95f, 1.0f), alpha = 0.85f, blend = Blend.ADD)
    // gas-column brackets beside the tubes, in the atom colours: H white-cyan, O red
    private val gasMat = arrayOf(Mat.Unlit(Rgb(0.80f, 0.96f, 1.0f), alpha = 0.95f, blend = Blend.ADD),
                                 Mat.Unlit(Rgb(1.0f, 0.40f, 0.28f), alpha = 0.95f, blend = Blend.ADD))
    private val tickMat = Mat.Unlit(Rgb(0.85f, 0.93f, 1f), alpha = 0.75f, blend = Blend.TRANSPARENT)
    private val bubbleMat = Mat.Unlit(Rgb(0.82f, 0.94f, 1.0f), alpha = 0.80f, blend = Blend.ADD)
    private val panelGrey = Mat.Pbr(Rgb(0.80f, 0.82f, 0.84f), metallic = 0.6f, roughness = 0.45f)
    private val caseBlue = Mat.Pbr(Rgb(0.16f, 0.22f, 0.32f), metallic = 0.3f, roughness = 0.45f)
    private val cream = Mat.Pbr(Rgb(0.94f, 0.92f, 0.85f), metallic = 0f, roughness = 0.6f)
    private val ink = Mat.Unlit(Rgb(0.10f, 0.10f, 0.12f))
    private val RED = Rgb(0.85f, 0.15f, 0.12f)
    private val redLead = Mat.Pbr(RED, roughness = 0.45f)
    private val blackLead = Mat.Pbr(Rgb(0.09f, 0.09f, 0.10f), roughness = 0.45f)
    private val elecMat = Mat.Unlit(Rgb(0.45f, 0.95f, 1.0f), alpha = 1f, blend = Blend.ADD)
    private val oMat = Mat.Pbr(Rgb(0.95f, 0.22f, 0.18f), metallic = 0f, roughness = 0.30f, emissive = Rgb(0.30f, 0.05f, 0.04f))
    private val hMat = Mat.Pbr(Rgb(0.96f, 0.96f, 0.98f), metallic = 0f, roughness = 0.30f, emissive = Rgb(0.30f, 0.30f, 0.32f))
    private val naMat = Mat.Pbr(Rgb(0.68f, 0.42f, 1.0f), metallic = 0f, roughness = 0.30f, emissive = Rgb(0.20f, 0.12f, 0.32f))
    private val sMat = Mat.Pbr(Rgb(1.0f, 0.85f, 0.22f), metallic = 0f, roughness = 0.30f, emissive = Rgb(0.30f, 0.25f, 0.05f))
    private val catHalo = Mat.Unlit(Rgb(1.0f, 0.55f, 0.28f), alpha = 0.16f, blend = Blend.ADD)
    private val anHalo = Mat.Unlit(Rgb(0.35f, 0.65f, 1.0f), alpha = 0.16f, blend = Blend.ADD)
    private val sparkMat = Mat.Unlit(Rgb(1.0f, 0.95f, 0.75f), alpha = 0.9f, blend = Blend.ADD)
    private val zoomMat = Mat.Unlit(Rgb(0.55f, 0.80f, 1.0f), alpha = 0.22f, blend = Blend.ADD)
    private val winBack = Mat.Pbr(Rgb(0.03f, 0.045f, 0.07f), metallic = 0f, roughness = 0.35f, alpha = 0.92f)
    private val winFront = Mat.Pbr(Rgb(0.70f, 0.82f, 0.95f), metallic = 0f, roughness = 0.04f, alpha = 0.06f)
    private val wood = Mat.Pbr(Rgb(0.80f, 0.62f, 0.38f), metallic = 0f, roughness = 0.7f)
    private val emberMat = Mat.Unlit(Rgb(1.0f, 0.32f, 0.06f), alpha = 1f, blend = Blend.ADD)
    private val flameOuter = Mat.Unlit(Rgb(1.0f, 0.60f, 0.22f), alpha = 0.45f, blend = Blend.ADD)
    private val flameCore = Mat.Unlit(Rgb(1.0f, 0.92f, 0.62f), alpha = 0.70f, blend = Blend.ADD)
    private val puffMat = Mat.Unlit(Rgb(0.85f, 0.92f, 1.0f), alpha = 0.18f, blend = Blend.ADD)
    private val flashMats = Array(6) { k -> Mat.Unlit(Rgb(1.0f, 0.86f, 0.52f), alpha = 0.95f * (1f - k / 6f), blend = Blend.ADD) }
    private val dotOff = Mat.Unlit(Rgb(0.30f, 0.31f, 0.33f))
    private val dotOn = Mat.Unlit(Rgb(1.0f, 0.70f, 0.25f), alpha = 1f, blend = Blend.ADD)
    private val ledOff = Mat.Unlit(Rgb(0.15f, 0.18f, 0.15f))
    private val ledOn = Mat.Unlit(Rgb(0.30f, 1.0f, 0.45f), alpha = 1f, blend = Blend.ADD)
    private val ledTrip = Mat.Unlit(Rgb(1.0f, 0.25f, 0.15f), alpha = 1f, blend = Blend.ADD)
    private val slabTex by lazy {
        ProcTex.bakePlanet(256, 128, Rgb(0.07f, 0.075f, 0.085f), Rgb(0.12f, 0.125f, 0.14f), Rgb(0.20f, 0.205f, 0.22f), 0f, 77)
    }

    // ---------------------------------------------------------------- build

    override fun buildSpec(cacheDir: File): Node {
        // a fresh experiment on every (re)build: defaults, empty pools, filters at rest
        volts = 6f; salt = true; speedIdx = 1
        clock = 0f; needle = 0f; needleV = 0f; seed = 1L; frame = 0; cockResult = 0
        for (g in tests) { g.t = -1f; g.vent0 = 0.0 }
        bOn.fill(false); bAcc.fill(0f); leadPhase.fill(0.0)

        val root = Node("ElectroRoot")
        val unitCyl = MeshGen.cylinder(1f, 1f, 24)
        val unitBox = MeshGen.box(1f, 1f, 1f)
        val sphere = MeshGen.sphere(1f, 12, 20, MeshGen.SphereUv.EQUIRECT)
        val lowSphere = MeshGen.sphere(1f, 8, 14, MeshGen.SphereUv.EQUIRECT)

        root.add("Plinth", MeshGen.box(1.76f, 0.035f, 0.80f), Mat.Pbr(Rgb(1f, 1f, 1f), roughness = 0.55f, tex = slabTex), P3(0f, PLINTH_Y, -0.02f))
        // trim sits INSIDE the slab: only its 5 mm overhang shows, as an edge
        root.add("PlinthBand", MeshGen.box(1.77f, 0.008f, 0.81f), alu, P3(0f, PLINTH_Y, -0.02f))

        buildVoltameter(root, unitCyl, unitBox, sphere, lowSphere)
        buildSupply(root, unitCyl, unitBox, sphere)
        buildLeads(root, lowSphere)
        buildBottle(root, unitCyl, sphere)
        wins = arrayOf(Win(1f, WIN_C[0], Micro.CATHODE), Win(-1f, WIN_C[1], Micro.ANODE))
        for ((k, w) in wins.withIndex()) buildWindow(root, k, w, unitBox, unitCyl, sphere, lowSphere)

        // warm start: the current has been on for 2 min 45 s at 6 V; 1.5 s of display time (×10) makes it 3 min
        cell.refill()
        cell.step(165.0, volts.toDouble(), electrolyte)
        repeat(90) { update(1f / 60f) }
        return root
    }

    private fun buildVoltameter(root: Node, unitCyl: MeshData, unitBox: MeshData, sphere: MeshData, lowSphere: MeshData) {
        // retort stand behind the apparatus
        root.add("StandBase", MeshGen.box(0.34f, 0.014f, 0.14f), darkSteel, P3(RX, BENCH + 0.007f, -0.14f))
        root.add("StandRod", unitCyl, alu, P3(RX, (BENCH + 0.43f) / 2, -0.14f)).also { it.scale = P3(0.007f, 0.43f - BENCH, 0.007f) }
        for (y in floatArrayOf(0.12f, -0.10f)) {
            root.add("ClampArm", MeshGen.box(0.014f, 0.014f, 0.09f), darkSteel, P3(RX, y, -0.095f))
            root.add("ClampBar", MeshGen.box(NX - CX + 0.06f, 0.014f, 0.014f), darkSteel, P3((CX + NX) / 2, y, -0.052f))
        }

        val tubeGlass = MeshGen.tube(floatArrayOf(0f, TUBE_BOT, 0f, 0f, Y0, 0f), TR, 28, false)
        val neck = BuoyMesh.taper(floatArrayOf(0f, Y0, 0f, 0f, Y0 + 0.012f, 0f, 0f, COCK_Y - 0.012f, 0f), floatArrayOf(TR, 0.013f, 0.008f), 20)
        water = Array(2) { root.add("TubeWater$it", unitCyl, waterMat) }
        gasTint = Array(2) { root.add("GasBar$it", unitBox, gasMat[it]) }
        gasCap = Array(2) { root.add("GasCap$it", unitBox, gasMat[it]) }
        menisc = Array(2) { root.add("Meniscus$it", unitCyl, menMat) }
        handles = Array(2) { Node("Handle$it") }
        for ((k, x) in TUBE_X.withIndex()) {
            root.add("Tube$k", tubeGlass, glass, P3(x, 0f, 0f))
            root.add("TubeFoot$k", unitCyl, glass, P3(x, TUBE_BOT, 0f)).also { it.scale = P3(TR, 0.004f, TR) }
            root.add("Neck$k", neck, glass, P3(x, 0f, 0f))
            root.add("Seal$k", unitCyl, rubber, P3(x, TUBE_BOT - 0.008f, 0f)).also { it.scale = P3(0.017f, 0.016f, 0.017f) }
            root.add("ElTerm$k", unitCyl, darkSteel, P3(x, TUBE_BOT - 0.021f, 0f)).also { it.scale = P3(0.005f, 0.012f, 0.005f) }
            // platinum foil facing the viewer, on a Pt wire through the seal
            root.add("Foil$k", MeshGen.box(FOIL_W, FOIL_H, 0.004f), foilMat, P3(x, ELEC_Y, 0f))
            root.add("FoilWire$k", unitCyl, platinum, P3(x, (TUBE_BOT + ELEC_Y - FOIL_H / 2) / 2, 0f)).also { it.scale = P3(0.0014f, ELEC_Y - FOIL_H / 2 - TUBE_BOT, 0.0014f) }
            // gas-column bracket: fixed cap at 0 mL, a bar down to the meniscus (outside, away from the reservoir)
            root.add("GasTop$k", unitBox, gasMat[k], P3(bracketX(k), Y0, 0f)).scale = P3(0.018f, 0.0035f, 0.004f)
            // graduations: 0 mL at the top, every 5 mL, long every 10 mL (front of the tube)
            for (ml in 0..50 step 5) {
                val major = ml % 10 == 0
                root.add("Tick$k", MeshGen.box(if (major) 0.026f else 0.014f, 0.0028f, 0.002f), tickMat,
                    P3(x - if (major) 0.0f else 0.006f, Y0 - ml * MPM, TR + 0.0012f))
            }
            // stopcock: glass barrel across the neck, a lever that turns 90° to open
            root.add("CockBarrel$k", unitCyl, glass, P3(x, COCK_Y, 0f)).also { it.scale = P3(0.013f, 0.05f, 0.013f); it.rot = Q.axisAngle(1f, 0f, 0f, (PI / 2).toFloat()) }
            root.add("CockPlug$k", unitCyl, Mat.Pbr(Rgb(0.92f, 0.92f, 0.90f), roughness = 0.35f), P3(x, COCK_Y, 0f)).also { it.scale = P3(0.009f, 0.056f, 0.009f); it.rot = Q.axisAngle(1f, 0f, 0f, (PI / 2).toFloat()) }
            val cock = root.add(Node(if (k == 0) "CockH" else "CockO")).also { it.pos = P3(x, COCK_Y, 0.03f); it.tapRadius = 0.05f }
            handles[k] = cock.add(Node("Lever$k"))
            handles[k].add("LeverBar$k", MeshGen.box(0.056f, 0.011f, 0.008f), Mat.Pbr(Rgb(0.92f, 0.92f, 0.90f), roughness = 0.35f))
            handles[k].add("LeverHub$k", MeshGen.cylinder(0.011f, 0.010f, 16), Mat.Pbr(Rgb(0.92f, 0.92f, 0.90f), roughness = 0.35f)).rot = Q.axisAngle(1f, 0f, 0f, (PI / 2).toFloat())
            root.add("Nozzle$k", unitCyl, glass, P3(x, (COCK_Y + 0.012f + NOZ_TOP) / 2, 0f)).also { it.scale = P3(0.0065f, NOZ_TOP - COCK_Y - 0.012f, 0.0065f) }
        }

        // the cross tube at the bottom and the central column up to the levelling reservoir
        root.add("Junction", MeshGen.tube(floatArrayOf(CX, JUNC_Y, 0f, NX, JUNC_Y, 0f), 0.013f, 20, false), glass)
        root.add("JunctionWater", unitCyl, waterMat, P3((CX + NX) / 2, JUNC_Y, 0f)).also {
            it.scale = P3(0.0105f, NX - CX - 2 * WR, 0.0105f); it.rot = Q.axisAngle(0f, 0f, 1f, (PI / 2).toFloat())
        }
        root.add("Column", MeshGen.tube(floatArrayOf(RX, JUNC_Y, 0f, RX, COL_TOP, 0f), 0.012f, 20, false), glass)
        root.add("ColumnWater", unitCyl, waterMat, P3(RX, (JUNC_Y + COL_TOP) / 2, 0f)).also { it.scale = P3(0.0095f, COL_TOP - JUNC_Y, 0.0095f) }
        val funnel = floatArrayOf(RX, COL_TOP, 0f, RX, (COL_TOP + BULB_Y0) / 2, 0f, RX, BULB_Y0, 0f)
        root.add("Funnel", BuoyMesh.taper(funnel, floatArrayOf(0.012f, 0.030f, BULB_R), 24), glass)
        root.add("FunnelWater", BuoyMesh.taper(funnel, floatArrayOf(0.0095f, 0.027f, BULB_WR), 24), waterMat)
        root.add("Bulb", MeshGen.tube(floatArrayOf(RX, BULB_Y0, 0f, RX, BULB_Y1, 0f), BULB_R, 32, false), glass)
        root.add("BulbRim", MeshGen.tube(circleXZ(BULB_R + 0.001f, 48, RX, BULB_Y1, 0f), 0.0025f, 6, true), Mat.Pbr(Rgb(0.90f, 0.95f, 1.0f), roughness = 0.05f, alpha = 0.45f))
        bulbWater = root.add("BulbWater", unitCyl, waterMat)
        bulbMenisc = root.add("BulbMeniscus", unitCyl, menMat)
        root.add(Node("Reservoir")).also { it.pos = P3(RX, (BULB_Y0 + BULB_Y1) / 2, 0f); it.tapRadius = 0.075f }

        // bubbles: one shared mesh, moved by transform
        bubbles = Array(NB_H + NB_O) { i -> root.add("Bubble$i", lowSphere, bubbleMat).also { it.visible = false } }

        // gas tests: escaping gas, the splints, the hydrogen pop
        puffs = Array(2) { k -> Array(5) { j -> root.add("Puff${k}_$j", lowSphere, puffMat).also { it.visible = false } } }
        splint = Array(2) { k ->
            arrayOf(root.add("Splint$k", unitCyl, wood), root.add("Ember$k", lowSphere, emberMat),
                root.add("FlameOut$k", lowSphere, flameOuter), root.add("FlameCore$k", lowSphere, flameCore))
        }
        popFlash = Array(2) { root.add("Pop$it", lowSphere, flashMats[0]).also { n -> n.visible = false } }
        for (a in splint) for (n in a) n.visible = false
    }

    private fun buildSupply(root: Node, unitCyl: MeshData, unitBox: MeshData, sphere: MeshData) {
        val y0 = BENCH
        root.add("PsCase", MeshGen.box(PS_W, PS_H, PS_D), caseBlue, P3(PSX, y0 + PS_H / 2, PS_Z))
        root.add("PsPanel", MeshGen.box(PS_W - 0.018f, PS_H - 0.018f, 0.004f), panelGrey, P3(PSX, y0 + PS_H / 2, PS_FRONT + 0.002f))
        for (sx in floatArrayOf(-1f, 1f)) for (sz in floatArrayOf(-1f, 1f))
            root.add("PsFoot", unitCyl, rubber, P3(PSX + sx * (PS_W / 2 - 0.03f), y0 + 0.003f, PS_Z + sz * (PS_D / 2 - 0.03f))).also { it.scale = P3(0.016f, 0.006f, 0.016f) }
        root.add("PsHandle", MeshGen.tube(floatArrayOf(PSX - 0.08f, y0 + PS_H, PS_Z, PSX - 0.07f, y0 + PS_H + 0.025f, PS_Z,
            PSX + 0.07f, y0 + PS_H + 0.025f, PS_Z, PSX + 0.08f, y0 + PS_H, PS_Z), 0.006f, 8, false), darkSteel)

        // ammeter: cream face, arc, ticks every 0.25 A (long every 0.5 A), red needle
        val zf = PS_FRONT + 0.006f
        root.add("AmBezel", MeshGen.box(0.214f, 0.118f, 0.006f), rubber, P3(PSX, AMY, PS_FRONT + 0.003f))
        root.add("AmFace", MeshGen.box(0.200f, 0.104f, 0.004f), cream, P3(PSX, AMY, zf))
        val arcR = 0.072f
        val arc = FloatArray(31 * 3) { k -> val a = amAngle(AM_FS * (k / 3) / 30.0); when (k % 3) { 0 -> PSX + arcR * sin(a); 1 -> AM_PIV + arcR * cos(a); else -> zf + 0.003f } }
        root.add("AmArc", MeshGen.tube(arc, 0.0012f, 6, false), ink)
        for (i in 0..10) {
            val a = amAngle(AM_FS * i / 10.0); val major = i % 2 == 0
            val r1 = arcR + if (major) 0.014f else 0.007f
            root.add("AmTick", MeshGen.tube(floatArrayOf(PSX + arcR * sin(a), AM_PIV + arcR * cos(a), zf + 0.003f, PSX + r1 * sin(a), AM_PIV + r1 * cos(a), zf + 0.003f),
                if (major) 0.0016f else 0.001f, 6, false), ink)
        }
        val needlePivot = root.add(Node("NeedlePivot")).also { it.pos = P3(PSX, AM_PIV, zf + 0.005f) }
        needleNode = needlePivot
        needlePivot.add("Needle", unitBox, Mat.Unlit(Rgb(0.85f, 0.10f, 0.08f)), P3(0f, 0.034f, 0f)).also { it.scale = P3(0.0032f, 0.080f, 0.002f) }
        root.add("NeedleHub", unitCyl, rubber, P3(PSX, AM_PIV, zf + 0.007f)).also { it.scale = P3(0.007f, 0.004f, 0.007f); it.rot = Q.axisAngle(1f, 0f, 0f, (PI / 2).toFloat()) }

        // voltage knob with a ring of 13 lamps (0..12 V) that light up to the setting
        val knob = root.add(Node("Knob")).also { it.pos = KNOB; it.tapRadius = 0.05f }
        knobRot = knob.add(Node("KnobRot"))
        knobRot.add("KnobBody", MeshGen.cylinder(0.030f, 0.026f, 32), Mat.Pbr(Rgb(0.08f, 0.08f, 0.09f), metallic = 0.2f, roughness = 0.4f)).rot = Q.axisAngle(1f, 0f, 0f, (PI / 2).toFloat())
        knobRot.add("KnobCap", MeshGen.cylinder(0.022f, 0.004f, 32), alu, P3(0f, 0f, 0.014f)).rot = Q.axisAngle(1f, 0f, 0f, (PI / 2).toFloat())
        knobRot.add("KnobPointer", unitBox, Mat.Unlit(Rgb(1f, 1f, 1f)), P3(0f, 0.019f, 0.0165f)).scale = P3(0.004f, 0.020f, 0.003f)
        voltDots = Array(13) { k ->
            val a = knobAngle(k.toFloat())
            root.add("VDot$k", sphere, dotOff, P3(KNOB.x - 0.046f * sin(a), KNOB.y + 0.046f * cos(a), PS_FRONT + 0.006f)).also { it.uniformScale(if (k % 2 == 0) 0.0042f else 0.0028f) }
        }
        // binding posts: red + and black −, with the signs printed above them
        for ((k, c) in listOf(Mat.Pbr(RED, roughness = 0.35f), Mat.Pbr(Rgb(0.10f, 0.10f, 0.11f), roughness = 0.35f)).withIndex()) {
            val px = PSX + 0.045f + 0.045f * k
            root.add("Post$k", unitCyl, c, P3(px, -0.195f, PS_FRONT + 0.015f)).also { it.scale = P3(0.011f, 0.03f, 0.011f); it.rot = Q.axisAngle(1f, 0f, 0f, (PI / 2).toFloat()) }
            root.add("SignH$k", unitBox, Mat.Unlit(Rgb(0.12f, 0.12f, 0.14f)), P3(px, -0.165f, PS_FRONT + 0.005f)).scale = P3(0.014f, 0.003f, 0.002f)
            if (k == 0) root.add("SignV", unitBox, Mat.Unlit(Rgb(0.12f, 0.12f, 0.14f)), P3(px, -0.165f, PS_FRONT + 0.005f)).scale = P3(0.003f, 0.014f, 0.002f)
        }
        led = root.add("Led", sphere, ledOff, P3(PSX + 0.09f, -0.120f, PS_FRONT + 0.005f)).also { it.uniformScale(0.0065f) }
    }

    private fun buildLeads(root: Node, lowSphere: MeshData) {
        val y = BENCH + 0.007f
        val zt = PS_FRONT + 0.03f
        // [0] red: + post -> anode foil terminal; [1] black: − post -> cathode foil terminal
        leadPath = arrayOf(
            spline(listOf(P3(PSX + 0.045f, -0.195f, zt), P3(PSX + 0.03f, y + 0.02f, zt + 0.05f), P3(0.50f, y, 0.17f), P3(0.24f, y, 0.13f),
                P3(NX + 0.03f, y, 0.05f), P3(NX, y + 0.018f, 0.006f), P3(NX, TUBE_BOT - 0.027f, 0f)), 90),
            spline(listOf(P3(PSX + 0.09f, -0.195f, zt), P3(PSX + 0.08f, y + 0.02f, zt + 0.09f), P3(0.40f, y, 0.22f), P3(-0.02f, y, 0.17f),
                P3(CX + 0.04f, y, 0.06f), P3(CX, y + 0.018f, 0.006f), P3(CX, TUBE_BOT - 0.027f, 0f)), 110),
        )
        root.add("LeadRed", MeshGen.tube(leadPath[0], 0.0042f, 8, false), redLead)
        root.add("LeadBlack", MeshGen.tube(leadPath[1], 0.0042f, 8, false), blackLead)
        leadBeads = Array(2) { l -> Array(12) { k -> root.add("LeadE${l}_$k", lowSphere, elecMat).also { it.uniformScale(0.0058f) } } }
    }

    private fun buildBottle(root: Node, unitCyl: MeshData, sphere: MeshData) {
        val b = BOTTLE
        val bottleGlass = Mat.Pbr(Rgb(0.85f, 0.92f, 1.0f), metallic = 0f, roughness = 0.04f, alpha = 0.24f)
        root.add("BottleBody", unitCyl, bottleGlass, P3(b.x, b.y + 0.06f, b.z)).scale = P3(0.042f, 0.12f, 0.042f)
        root.add("BottleSalt", unitCyl, Mat.Pbr(Rgb(0.95f, 0.95f, 0.97f), roughness = 0.85f, emissive = Rgb(0.12f, 0.12f, 0.13f)), P3(b.x, b.y + 0.032f, b.z)).scale = P3(0.038f, 0.058f, 0.038f)
        root.add("BottleLabel", unitCyl, Mat.Pbr(Rgb(0.96f, 0.96f, 0.93f), roughness = 0.7f), P3(b.x, b.y + 0.085f, b.z)).scale = P3(0.0428f, 0.040f, 0.0428f)
        root.add("BottleStripe", unitCyl, Mat.Pbr(Rgb(0.20f, 0.45f, 0.90f), roughness = 0.5f), P3(b.x, b.y + 0.085f, b.z)).scale = P3(0.0432f, 0.010f, 0.0432f)
        root.add("BottleShoulder", sphere, bottleGlass, P3(b.x, b.y + 0.12f, b.z)).scale = P3(0.042f, 0.022f, 0.042f)
        root.add("BottleNeck", unitCyl, bottleGlass, P3(b.x, b.y + 0.148f, b.z)).scale = P3(0.016f, 0.03f, 0.016f)
        root.add("BottleStopper", unitCyl, Mat.Pbr(Rgb(0.85f, 0.90f, 0.95f), roughness = 0.1f, alpha = 0.6f), P3(b.x, b.y + 0.170f, b.z)).scale = P3(0.019f, 0.016f, 0.019f)
        root.add("BottleKnob", sphere, Mat.Pbr(Rgb(0.85f, 0.90f, 0.95f), roughness = 0.1f, alpha = 0.6f), P3(b.x, b.y + 0.185f, b.z)).uniformScale(0.014f)
        root.add(Node("Bottle")).also { it.pos = P3(b.x, b.y + 0.095f, b.z); it.tapRadius = 0.075f }
    }

    private fun buildWindow(root: Node, k: Int, w: Win, unitBox: MeshData, unitCyl: MeshData, sphere: MeshData, lowSphere: MeshData) {
        val c = w.c
        val back = c.z - WIN_D / 2; val front = c.z + WIN_D / 2
        root.add("WinBack$k", MeshGen.box(WIN_W, WIN_H, 0.004f), winBack, P3(c.x, c.y, back))
        root.add("WinFront$k", MeshGen.box(WIN_W, WIN_H, 0.003f), winFront, P3(c.x, c.y, front))
        val rail = 0.007f
        for (sy in floatArrayOf(-1f, 1f)) for (z in floatArrayOf(back, front))
            root.add("WinRailX$k", unitBox, alu, P3(c.x, c.y + sy * (WIN_H / 2 + rail / 2), z)).scale = P3(WIN_W + 2 * rail, rail, rail)
        for (sx in floatArrayOf(-1f, 1f)) for (z in floatArrayOf(back, front))
            root.add("WinRailY$k", unitBox, alu, P3(c.x + sx * (WIN_W / 2 + rail / 2), c.y, z)).scale = P3(rail, WIN_H, rail)
        for (sx in floatArrayOf(-1f, 1f)) for (sy in floatArrayOf(-1f, 1f))
            root.add("WinRailZ$k", unitBox, alu, P3(c.x + sx * (WIN_W / 2 + rail / 2), c.y + sy * (WIN_H / 2 + rail / 2), c.z)).scale = P3(rail, rail, WIN_D)
        val bottom = c.y - WIN_H / 2 - rail
        root.add("WinPost$k", unitCyl, alu, P3(c.x, (BENCH + bottom) / 2, back + 0.01f)).scale = P3(0.007f, bottom - BENCH, 0.007f)
        root.add("WinFoot$k", MeshGen.cylinder(0.035f, 0.010f, 24), darkSteel, P3(c.x, BENCH + 0.005f, back + 0.01f))
        // the electrode plate on the wall nearest its foil
        val px = c.x + w.s * (PLATE_IN + 0.009f)
        root.add("WinPlate$k", MeshGen.box(0.018f, WIN_H - 0.01f, WIN_D - 0.012f), platinum, P3(px, c.y, c.z))
        // zoom lines: from the real foil to the window's near face (this window magnifies THAT foil)
        val foil = P3(TUBE_X[k], ELEC_Y, 0f)
        val nearX = c.x + w.s * (WIN_W / 2 + rail)
        for (sy in floatArrayOf(-1f, 1f)) for (sz in floatArrayOf(-1f, 1f))
            root.add("Zoom$k", MeshGen.tube(floatArrayOf(foil.x, foil.y, foil.z, nearX, c.y + sy * WIN_H / 2, c.z + sz * WIN_D / 2), 0.0011f, 5, false), zoomMat)

        // background water molecules (tumbling, not reacting)
        w.bg = Array(N_BG) { j ->
            val a = w.bgA[j]
            a[0] = 0.10f + 0.12f * rnd(); a[1] = -0.125f + 0.25f * (j + rnd()) / N_BG; a[2] = -0.03f + 0.06f * rnd()
            a[3] = 6.28f * rnd(); a[4] = 0.25f + 0.25f * rnd(); a[5] = 0.3f + 0.3f * rnd(); a[6] = 6.28f * rnd()
            val g = root.add(Node("Wat${k}_$j"))
            g.add("WatO${k}_$j", sphere, oMat).uniformScale(R_O)
            for (h in 0..1) g.add("WatH${k}_${j}_$h", sphere, hMat, waterH(if (h == 0) 52.25f else -52.25f)).uniformScale(R_H)
            g
        }

        // reaction atoms (one per track of the half reaction), their ion halos, electrons, sparks
        layoutReaction(w)
        w.atoms = Array(w.n) { t -> root.add("Rx${k}_$t", sphere, if (w.rx.tracks[t].el == El.O) oMat else hMat).also { it.uniformScale(if (w.rx.tracks[t].el == El.O) R_O else R_H) } }
        w.halos = Array(w.rx.products.size) { j ->
            val q = w.rx.products[j].charge
            if (q == 0) null else root.add("RxHalo${k}_$j", lowSphere, if (q > 0) catHalo else anHalo).also { it.uniformScale(if (w.rx.products[j].atoms.size == 1) 0.016f else 0.023f) }
        }
        w.elec = Array(w.rx.eIn + w.rx.eOut) { e -> root.add("RxE${k}_$e", lowSphere, elecMat).also { it.uniformScale(0.0062f) } }
        w.sparks = Array(w.rx.reactants.size) { i -> root.add("Spark${k}_$i", lowSphere, sparkMat).also { it.visible = false } }

        // spectators: 4 Na⁺ and 2 SO₄²⁻ (Na₂SO₄ is neutral: 4 x (+1) + 2 x (−2) = 0)
        val ions = ArrayList<Node>(); val halos = ArrayList<Node>()
        for (j in 0 until N_NA + N_SO4) {
            w.ionU[j] = 0.08f + 0.15f * (j + 0.5f) / (N_NA + N_SO4) + 0.02f * rnd()
            w.ionV[j] = if (j % 2 == 0) 0.085f + 0.04f * rnd() else -0.085f - 0.04f * rnd()
            w.ionW[j] = -0.025f + 0.02f * rnd()
            if (j < N_NA) {
                ions += root.add("Na${k}_$j", sphere, naMat).also { it.uniformScale(0.0115f) }
                halos += root.add("NaHalo${k}_$j", lowSphere, catHalo).also { it.uniformScale(0.0185f) }
            } else {
                val g = root.add(Node("SO4${k}_$j"))
                g.add("S${k}_$j", sphere, sMat).uniformScale(0.0105f)
                val d = 0.0165f / sqrt(3f)
                for ((m, v) in listOf(floatArrayOf(1f, 1f, 1f), floatArrayOf(1f, -1f, -1f), floatArrayOf(-1f, 1f, -1f), floatArrayOf(-1f, -1f, 1f)).withIndex())
                    g.add("SO${k}_${j}_$m", sphere, oMat, P3(v[0] * d, v[1] * d, v[2] * d)).uniformScale(0.0085f)
                ions += g
                halos += root.add("SO4Halo${k}_$j", lowSphere, anHalo).also { it.uniformScale(0.028f) }
            }
        }
        w.ions = ions.toTypedArray(); w.ionHalos = halos.toTypedArray()
    }

    // ---------------------------------------------------------------- micro geometry

    private const val R_O = 0.0135f; private const val R_H = 0.0088f
    private const val BOND_OH = 0.0165f
    private fun waterH(deg: Float): P3 { val a = Math.toRadians(deg.toDouble()); return P3((BOND_OH * cos(a)).toFloat(), (BOND_OH * sin(a)).toFloat(), 0f) }

    /** Start / adsorption-site / product positions (plate frame) for every atom track of [w]'s half reaction. */
    private fun layoutReaction(w: Win) {
        val rx = w.rx
        val cathode = rx.eIn > 0
        for (i in rx.reactants.indices) {
            val up = if (i == 0) 1f else -1f
            val siteU = if (cathode) 0.027f else 0.018f
            val tr = rx.tracks.withIndex().filter { it.value.from == i }
            val oToGas = tr.any { it.value.el == El.O && it.value.to == 0 }
            for ((k, t) in tr) {
                val slot = rx.slotInReactant(k)
                // the atom that goes into the gas faces the plate (−u); the rest point away
                val off = when {
                    t.el == El.O -> floatArrayOf(0f, 0f)
                    oToGas -> polar(BOND_OH, if (slot == 1) 52.25f else -52.25f)
                    t.to == 0 -> polar(BOND_OH, 180f)
                    else -> polar(BOND_OH, up * 75.5f)
                }
                w.site[k][0] = siteU + off[0]; w.site[k][1] = up * 0.034f + off[1]; w.site[k][2] = up * 0.006f
                w.start[k][0] = 0.14f + off[0]; w.start[k][1] = up * 0.10f + off[1]; w.start[k][2] = 0.012f
            }
        }
        for (j in rx.products.indices) {
            val into = rx.tracks.indices.filter { rx.tracks[it].to == j }
            for (a in 0..2) w.prodStart[j][a] = into.map { w.site[it][a] }.average().toFloat()
            val v = w.prodStart[j][1]
            if (j == 0) { for (a in 0..2) w.prodEnd[j][a] = w.prodStart[j][a] }
            else { w.prodEnd[j][0] = 0.222f; w.prodEnd[j][1] = if (cathode) sign(v) * 0.078f else (v * 2.9f).coerceIn(-0.118f, 0.118f); w.prodEnd[j][2] = w.prodStart[j][2] }
        }
        for (k in rx.tracks.indices) {
            val t = rx.tracks[k]; val prod = rx.products[t.to]; val slot = rx.slotInProduct(k)
            val o = when {
                prod.atoms.size == 1 -> floatArrayOf(0f, 0f)
                prod.atoms.all { it == El.H } -> floatArrayOf(0f, if (slot == 0) 0.0058f else -0.0058f)        // H₂
                prod.atoms.all { it == El.O } -> floatArrayOf(0f, if (slot == 0) 0.0090f else -0.0090f)        // O₂
                slot == 0 -> floatArrayOf(0f, 0f)                                                              // OH⁻: O
                else -> polar(BOND_OH, sign(w.prodStart[t.to][1]) * 40f)                                     // OH⁻: H, away from the plate
            }
            w.prodOff[k][0] = o[0]; w.prodOff[k][1] = o[1]
        }
    }

    private fun polar(r: Float, deg: Float): FloatArray { val a = Math.toRadians(deg.toDouble()); return floatArrayOf((r * cos(a)).toFloat(), (r * sin(a)).toFloat()) }

    /** Product [j]'s centre at phase [p] (plate frame). */
    private fun prodCentre(w: Win, j: Int, p: Float, out: FloatArray) {
        val s = w.prodStart[j]
        if (j == 0) {
            val r = ((p - P_RISE) / (1f - P_RISE)).coerceIn(0f, 1f)
            out[0] = s[0] + 0.010f * r; out[1] = s[1] + 0.20f * r * r * (3 - 2 * r) + 0.04f * r; out[2] = s[2]
        } else {
            val e = w.prodEnd[j]; val f = ease(((p - P_REACT) / (1f - P_REACT)).coerceIn(0f, 1f))
            for (a in 0..2) out[a] = s[a] + (e[a] - s[a]) * f
        }
    }

    // ---------------------------------------------------------------- per frame

    override fun update(dt: Float) {
        clock += dt
        val lapse = LAPSE[speedIdx]
        // the experiment (lab time = display time x lapse)
        cell.step(dt.toDouble() * lapse, volts.toDouble(), electrolyte)
        val i = cell.current
        stepTests(dt)
        placeVoltameter(dt, i, lapse)
        placeSupply(dt, i)
        placeLeads(dt, i)
        val rate = if (i > 1e-3) (i / I_REF).coerceIn(0.5, 1.6).toFloat() else 0f
        for (w in wins) placeWindow(w, dt, rate)
        if (frame++ % 6 == 0 || dt == 0f || Lang.en != textEn) { textEn = Lang.en; updateReadout(); updateLabels() }
    }

    private fun meniscusY(gasMl: Double) = Y0 - gasMl.toFloat() * MPM
    /** gas bracket x: outside each tube, on the side away from the reservoir */
    private fun bracketX(k: Int) = TUBE_X[k] + (if (k == 0) -1f else 1f) * (TR + 0.010f)

    private fun placeVoltameter(dt: Float, i: Double, lapse: Int) {
        val gas = doubleArrayOf(cell.gasH, cell.gasO)
        for (k in 0..1) {
            val x = TUBE_X[k]
            val ym = meniscusY(gas[k])
            val bot = TUBE_BOT + 0.003f
            water[k].pos = P3(x, (bot + ym) / 2, 0f); water[k].scale = P3(WR, ym - bot, WR)
            menisc[k].pos = P3(x, ym, 0f); menisc[k].scale = P3(WR + 0.0008f, 0.004f, WR + 0.0008f)
            gasTint[k].visible = gas[k] > 0.05; gasCap[k].visible = gasTint[k].visible
            if (gasTint[k].visible) {
                gasTint[k].pos = P3(bracketX(k), (ym + Y0) / 2, 0f); gasTint[k].scale = P3(0.0065f, Y0 - ym, 0.004f)
                gasCap[k].pos = P3(bracketX(k), ym, 0f); gasCap[k].scale = P3(0.018f, 0.0035f, 0.004f)
            }
        }
        val lvl = LEVEL0 + (cell.gasH + cell.gasO).toFloat() * RISE_PER_ML
        bulbWater.pos = P3(RX, (BULB_Y0 + lvl) / 2, 0f); bulbWater.scale = P3(BULB_WR, lvl - BULB_Y0, BULB_WR)
        bulbMenisc.pos = P3(RX, lvl, 0f); bulbMenisc.scale = P3(BULB_WR + 0.001f, 0.0028f, BULB_WR + 0.001f)

        // bubbles: emission ∝ the gas each foil makes (display mL/s), O₂ exactly half of H₂
        val mlPerS = if (cell.full) 0.0 else i * cell.kH * lapse
        val rateH = if (mlPerS < 1e-4) 0f else minOf(32f, (20.0 * sqrt(mlPerS)).toFloat())
        val rates = floatArrayOf(rateH, rateH / 2f)
        for (k in 0..1) {
            bAcc[k] += rates[k] * dt
            val lo = if (k == 0) 0 else NB_H; val hi = if (k == 0) NB_H else NB_H + NB_O
            while (bAcc[k] >= 1f) {
                bAcc[k] -= 1f
                val b = (lo until hi).firstOrNull { !bOn[it] } ?: break
                bOn[b] = true
                bX[b] = TUBE_X[k] + (rnd() - 0.5f) * (FOIL_W - 0.004f)
                bY[b] = ELEC_Y + (rnd() - 0.5f) * (FOIL_H - 0.01f)
                bZ[b] = (if (rnd() < 0.5f) -1f else 1f) * (0.004f + 0.007f * rnd())
                bV[b] = 0.16f + 0.10f * rnd(); bPh[b] = 6.28f * rnd(); bR[b] = 0.0032f + 0.0028f * rnd()
            }
            val top = meniscusY(gas[k])
            for (b in lo until hi) {
                val n = bubbles[b]
                if (bOn[b]) {
                    bY[b] += bV[b] * dt
                    if (bY[b] + bR[b] >= top) bOn[b] = false
                }
                n.visible = bOn[b]
                if (bOn[b]) {
                    val wob = 0.0022f * sin(bPh[b] + 11f * clock)
                    n.pos = P3((bX[b] + wob).coerceIn(TUBE_X[k] - WR + bR[b] + 0.001f, TUBE_X[k] + WR - bR[b] - 0.001f), bY[b], bZ[b])
                    n.uniformScale(bR[b])
                }
            }
        }
    }

    private fun placeSupply(dt: Float, i: Double) {
        // ammeter needle: a damped follower (a real moving-coil meter swings, it does not jump)
        val target = amAngle(i.coerceIn(0.0, AM_FS * 1.04))
        val w = 10f
        if (dt > 0f) { needleV += (w * w * (target - needle) - 2 * w * needleV) * dt; needle += needleV * dt }
        needleNode.rot = Q.axisAngle(0f, 0f, 1f, -needle)
        knobRot.rot = Q.axisAngle(0f, 0f, 1f, -knobAngle(volts))
        for (k in 0..12) voltDots[k].setMat(if (k <= volts + 0.05f && volts > 0.05f) dotOn else dotOff)
        led.setMat(when { volts <= 0.05f -> ledOff; cell.full -> ledTrip; else -> ledOn })
    }

    private fun placeLeads(dt: Float, i: Double) {
        val show = i > 1e-3
        // electrons leave the − post toward the cathode, and come back from the anode into the + post
        val v = 0.10 * (i / I_REF).coerceIn(0.0, 2.2)
        leadPhase[0] -= v * dt; leadPhase[1] += v * dt * 0.82
        for (l in 0..1) for (k in 0 until 12) {
            val n = leadBeads[l][k]
            n.visible = show
            if (show) n.pos = alongPath(leadPath[l], (((k / 12.0 + leadPhase[l]) % 1.0) + 1.0) % 1.0)
        }
    }

    private val tmp = FloatArray(3)

    private fun placeWindow(w: Win, dt: Float, rate: Float) {
        val rx = w.rx
        val period = PERIOD_ANODE * rx.electronsPer(rx.products[0]) / Micro.Z_O2      // cathode: half the anode's period
        // run while current flows; when it stops, finish the cycle and wait at its start
        if (rate > 0f) w.phase += dt * rate / period
        else if (w.phase > 0f) w.phase += dt / period
        if (w.phase >= 1f) w.phase = if (rate > 0f) w.phase - 1f else 0f
        val p = w.phase
        val cathode = rx.eIn > 0

        for (k in 0 until w.n) {
            val n = w.atoms[k]; val t = rx.tracks[k]
            val a = w.site[k]
            if (p < P_REACT || p >= P_GONE) {
                // the next pair of waters sets off as soon as the last products have left (p wraps at 1)
                val f = ease((((if (p >= P_GONE) p - 1f else p) + 1f - P_GONE) / (P_SITE + 1f - P_GONE)).coerceIn(0f, 1f))
                val s0 = w.start[k]
                n.pos = w.at(s0[0] + (a[0] - s0[0]) * f, s0[1] + (a[1] - s0[1]) * f, s0[2] + (a[2] - s0[2]) * f)
                n.visible = true
            } else {
                // bonds break at the plate: each atom glides from its site onto its (moving) product
                prodCentre(w, t.to, p, tmp)
                val f = ease(((p - P_REACT) / 0.16f).coerceIn(0f, 1f))
                n.pos = w.at(a[0] + (tmp[0] + w.prodOff[k][0] - a[0]) * f, a[1] + (tmp[1] + w.prodOff[k][1] - a[1]) * f, a[2] + (tmp[2] - a[2]) * f)
                n.visible = if (t.to == 0) tmp[1] < 0.132f else p < P_GONE
            }
        }
        for (j in rx.products.indices) {
            val h = w.halos[j] ?: continue
            h.visible = p > P_REACT + 0.04f && p < P_GONE
            if (h.visible) {
                prodCentre(w, j, p, tmp)
                val f = ease(((p - P_REACT) / 0.16f).coerceIn(0f, 1f))
                val o = w.firstAtom[j]
                val s = w.site[o]
                h.pos = w.at(s[0] + (tmp[0] + w.prodOff[o][0] - s[0]) * f, s[1] + (tmp[1] + w.prodOff[o][1] - s[1]) * f, tmp[2])
            }
        }
        // electrons: cathode — come down the plate from its lead to the two H's that become H₂;
        // anode — leave the two waters at the plate and climb to the lead
        val gasTracks = w.gasTracks
        for ((e, n) in w.elec.withIndex()) {
            if (cathode) {
                val tv = w.site[gasTracks[e % gasTracks.size]][1]
                val f = ease((p / P_SITE).coerceIn(0f, 1f))
                n.visible = p < P_REACT - 0.01f
                n.pos = w.at(0.006f, 0.14f + (tv - 0.14f) * f, 0.030f)
            } else {
                val i = e / 2; val v0 = (if (i == 0) 0.034f else -0.034f) + (if (e % 2 == 0) 0.009f else -0.009f)
                val f = ease(((p - P_REACT) / 0.42f).coerceIn(0f, 1f))
                n.visible = p > P_REACT - 0.02f && p < P_REACT + 0.42f
                n.pos = w.at(0.006f, v0 + (0.14f - v0) * f, 0.030f)
            }
        }
        for ((i, n) in w.sparks.withIndex()) {
            val b = ((p - (P_SITE - 0.02f)) / 0.12f)
            n.visible = b in 0f..1f
            if (n.visible) { n.pos = w.at(0.004f, if (i == 0) 0.034f else -0.034f, 0.006f); n.uniformScale(0.006f + 0.016f * sin(PI.toFloat() * b)) }
        }

        // background waters: slow wander + tumble
        for ((j, g) in w.bg.withIndex()) {
            val a = w.bgA[j]
            val u = a[0] + 0.016f * sin(a[3] + a[4] * clock); val v = a[1] + 0.014f * sin(a[6] + a[5] * clock)
            g.pos = w.at(u.coerceIn(0.08f, 0.235f), v, a[2] + 0.01f * sin(a[3] + 0.7f * clock))
            g.rot = Q.axisAngle(sin(a[3]), cos(a[6]), 0.5f, a[3] + 0.6f * clock * a[4] * 3f)
        }
        // spectator ions: drift in the field (cations toward the cathode, anions toward the anode)
        val towardPlateCation = if (cathode) -1f else 1f
        for (j in w.ions.indices) {
            val ion = w.ions[j]; val halo = w.ionHalos[j]
            ion.visible = salt; halo.visible = salt
            if (!salt) continue
            val dir = if (j < N_NA) towardPlateCation else -towardPlateCation
            w.ionU[j] += dir * 0.020f * rate * dt
            if (w.ionU[j] < 0.075f) w.ionU[j] += 0.16f
            if (w.ionU[j] > 0.235f) w.ionU[j] -= 0.16f
            val pos = w.at(w.ionU[j], w.ionV[j] + 0.006f * sin(clock * 1.3f + j), w.ionW[j])
            ion.pos = pos; halo.pos = pos
            val edge = w.ionU[j] < 0.085f || w.ionU[j] > 0.225f
            ion.visible = !edge; halo.visible = !edge
            if (j >= N_NA) ion.rot = Q.axisAngle(0.3f, 1f, 0.2f, clock * 0.8f + j)
        }
    }

    // ---------------------------------------------------------------- gas tests

    private const val T_AT = 0.7f; private const val T_OPEN = 0.85f; private const val T_VENT = 1.0f
    private const val T_REACT = 0.95f; private const val T_CLOSE = 2.0f; private const val T_END = 2.8f

    private fun startTest(k: Int) {
        val g = tests[k]
        cockResult = when {
            g.active -> 2
            (if (k == 0) cell.gasH else cell.gasO) < 0.5 -> 1
            else -> { g.t = 0f; g.vent0 = 0.0; 0 }
        }
    }

    private fun stepTests(dt: Float) {
        for (k in 0..1) {
            val g = tests[k]
            val parts = splint[k]
            val x = TUBE_X[k]
            if (!g.active) {
                for (n in parts) n.visible = false
                popFlash[k].visible = false
                for (n in puffs[k]) n.visible = false
                handles[k].rot = Q.identity()
                continue
            }
            val t0 = g.t
            g.t += dt
            val t = g.t
            // venting: the tube empties over T_VENT s (the reservoir head pushes the water back up)
            if (t0 < T_OPEN && t >= T_OPEN) g.vent0 = if (k == 0) cell.gasH else cell.gasO
            if (t >= T_OPEN && t0 < T_OPEN + T_VENT) {
                val a = maxOf(t0, T_OPEN); val b = minOf(t, T_OPEN + T_VENT)
                if (b > a) cell.vent(k == 0, g.vent0 * (b - a) / T_VENT)
            }
            val open = ((t - (T_OPEN - 0.15f)) / 0.15f).coerceIn(0f, 1f) * (1f - ((t - T_CLOSE) / 0.15f).coerceIn(0f, 1f))
            handles[k].rot = Q.axisAngle(0f, 0f, 1f, open * (PI / 2).toFloat())
            // splint: in along its own axis, hold at the nozzle, back out
            val dirX = if (k == 0) -0.90f else 0.90f
            val dir = norm(dirX, 0.24f, 0.36f)
            val tip = P3(x - dirX * 0.012f, NOZ_TOP + 0.005f, 0.012f)
            val inF = ease((t / T_AT).coerceIn(0f, 1f)) * (1f - ease(((t - (T_CLOSE + 0.1f)) / (T_END - T_CLOSE - 0.1f)).coerceIn(0f, 1f)))
            val back = 0.16f * (1f - inF)
            val tp = P3(tip.x + dir[0] * back, tip.y + dir[1] * back, tip.z + dir[2] * back)
            val len = 0.16f
            parts[0].visible = true
            parts[0].pos = P3(tp.x + dir[0] * len / 2, tp.y + dir[1] * len / 2, tp.z + dir[2] * len / 2)
            parts[0].rot = Q.fromTo(0f, 1f, 0f, dir[0], dir[1], dir[2]); parts[0].scale = P3(0.0042f, len, 0.0042f)
            val fl = 1f + 0.08f * sin(clock * 23f) + 0.05f * sin(clock * 37f)
            if (k == 0) {
                // hydrogen: a burning splint; the gas meets the flame -> a squeaky pop that blows it out
                val lit = t < T_REACT
                parts[1].visible = !lit; parts[1].pos = tp; parts[1].uniformScale(0.0045f)
                setFlame(parts, tp, lit, 1f, fl)
                val f = (t - T_REACT) / 0.32f
                popFlash[k].visible = f in 0f..1f
                if (popFlash[k].visible) {
                    popFlash[k].pos = P3(x, NOZ_TOP + 0.012f, 0f)
                    popFlash[k].uniformScale(0.012f + 0.040f * sqrt(f))
                    popFlash[k].setMat(flashMats[minOf(5, (f * 6).toInt())])
                }
            } else {
                // oxygen: a glowing splint (an ember) bursts back into flame
                val relit = ((t - T_REACT) / 0.25f).coerceIn(0f, 1f)
                parts[1].visible = true; parts[1].pos = tp
                parts[1].uniformScale(0.0052f * (if (relit > 0f) 1.2f else 0.85f + 0.15f * sin(clock * 6f)))
                setFlame(parts, tp, relit > 0f, 0.4f + 0.9f * relit, fl)
                popFlash[k].visible = false
            }
            // escaping gas puffs from the nozzle while the tap is open
            val venting = t > T_OPEN && t < T_OPEN + T_VENT
            for ((j, n) in puffs[k].withIndex()) {
                n.visible = venting
                if (venting) {
                    val ph = ((t * 2.2f + j / 5f) % 1f)
                    n.pos = P3(x + 0.006f * sin(j * 2.1f + t * 5f), NOZ_TOP + 0.005f + 0.05f * ph, 0.006f * cos(j * 1.7f))
                    n.uniformScale(0.005f + 0.012f * ph)
                }
            }
            if (t >= T_END) { g.t = -1f; for (n in parts) n.visible = false; for (n in puffs[k]) n.visible = false; popFlash[k].visible = false }
        }
    }

    private fun setFlame(parts: Array<Node>, tip: P3, on: Boolean, size: Float, fl: Float) {
        parts[2].visible = on; parts[3].visible = on
        if (!on) return
        parts[2].pos = P3(tip.x, tip.y + 0.022f * size * fl, tip.z); parts[2].scale = P3(0.015f * size, 0.036f * size * fl, 0.015f * size)
        parts[3].pos = P3(tip.x, tip.y + 0.011f * size * fl, tip.z); parts[3].scale = P3(0.0075f * size, 0.017f * size * fl, 0.0075f * size)
    }

    // ---------------------------------------------------------------- text

    private fun amps(i: Double) = when {
        i <= 0.0 -> "0 A"
        i < 1e-3 -> "%.1f µA".format(i * 1e6)
        else -> "%.2f A".format(i)
    }

    private fun updateReadout() {
        val i = cell.current
        val lapse = LAPSE[speedIdx]
        val m = (cell.t / 60).toInt(); val s = (cell.t % 60).toInt()
        val mode = if (lapse == 1) tr("实时", "real time") else tr("延时 ×%d", "time-lapse ×%d").format(lapse)
        val qTxt = if (cell.q < 10) "%.1f".format(cell.q) else "%.0f".format(cell.q)
        val l1 = tr("U = %.1f V   I = %s   ·   通电 %d min %02d s（%s）   ·   电量 Q = ∫I dt = %s C",
            "U = %.1f V   I = %s   ·   lab time %d min %02d s (%s)   ·   charge Q = ∫I dt = %s C").format(volts, amps(i), m, s, mode, qTxt)
        val ratio = if (cell.producedO > 0.05) "%.2f : 1".format(cell.producedH / cell.producedO) else "— : 1"
        val l2 = tr("量得 V(H₂) : V(O₂) = %.1f : %.1f mL = %s   ·   法拉第 Q/2F → %.1f mL，Q/4F → %.1f mL（25 °C，1 atm）",
            "Measured V(H₂) : V(O₂) = %.1f : %.1f mL = %s   ·   Faraday Q/2F → %.1f mL, Q/4F → %.1f mL (25 °C, 1 atm)").format(
            cell.producedH, cell.producedO, ratio, cell.predictedH, cell.predictedO)
        val l3 = when {
            cell.full -> tr("%s 管已满 50 mL，电源自动断开 → 打开活塞检验气体，或捏储液球重新加满",
                "The %s tube is full (50 mL): supply cut off → test the gas, or pinch the reservoir to refill").format(if (cell.gasH >= CAP - 1e-6) "H₂" else "O₂")
            volts <= 0.05f -> tr("电源关闭：捏住旋钮拖动（或用滑块）加电压", "Supply off: pinch and drag the knob (or use the slider) to apply a voltage")
            volts <= Cell.U_DEC -> tr("U 低于分解电压 2.2 V（热力学 1.23 V + 电极过电位）→ 没有持续电流，不出气",
                "U below the 2.2 V decomposition voltage (1.23 V thermodynamic + overpotentials) → no current, no gas")
            !salt -> tr("纯水几乎不电离：κ ≈ 5.5 µS/m，R ≈ %.1f MΩ → 加 Na₂SO₄：Na⁺、SO₄²⁻ 导电但不参加反应",
                "Pure water barely ionises: κ ≈ 5.5 µS/m, R ≈ %.1f MΩ → add Na₂SO₄: Na⁺, SO₄²⁻ carry current, never react").format(Cell.resistance(Electrolyte.PURE) / 1e6)
            else -> tr("为何 2 : 1？2H₂O → 2H₂ + O₂：每个 H₂ 得 2 个 e⁻，每个 O₂ 失 4 个 e⁻   ·   能量效率 1.48 V / U = %.0f%%",
                "Why 2 : 1? 2H₂O → 2H₂ + O₂: each H₂ takes 2 e⁻, each O₂ gives up 4 e⁻   ·   efficiency 1.48 V / U = %.0f%%").format(100 * Echem.efficiency(volts.toDouble()))
        }
        readoutText = "$l1\n$l2\n$l3"
    }

    private fun updateLabels() {
        labels.clear()
        labels += LabelSpec(tr("阴极 − · H₂ %.1f mL", "Cathode − · H₂ %.1f mL").format(cell.gasH), P3(CX - 0.02f, 0.47f, 0.03f))
        labels += LabelSpec(tr("阳极 + · O₂ %.1f mL", "Anode + · O₂ %.1f mL").format(cell.gasO), P3(NX + 0.02f, 0.47f, 0.03f))
        labels += LabelSpec(tr("阴极：2H₂O + 2e⁻ → H₂ + 2OH⁻", "Cathode: 2H₂O + 2e⁻ → H₂ + 2OH⁻"), P3(WIN_C[0].x, WIN_C[0].y + WIN_H / 2 + 0.045f, WIN_C[0].z))
        labels += LabelSpec(tr("阳极：2H₂O → O₂ + 4H⁺ + 4e⁻", "Anode: 2H₂O → O₂ + 4H⁺ + 4e⁻"), P3(WIN_C[1].x, WIN_C[1].y + WIN_H / 2 + 0.045f, WIN_C[1].z))
        labels += LabelSpec(tr("电源 %.1f V · %s", "Supply %.1f V · %s").format(volts, amps(cell.current)), P3(PSX, BENCH + PS_H + 0.07f, PS_FRONT))
        labels += LabelSpec(if (salt) tr("电解质 Na₂SO₄", "Electrolyte Na₂SO₄") else tr("纯水（无电解质）", "Pure water (no electrolyte)"), P3(BOTTLE.x, BOTTLE.y + 0.25f, BOTTLE.z))
    }

    // ---------------------------------------------------------------- interaction

    override val tapTargets get() = listOf(
        TapTarget("Knob", "电源旋钮 → 0/2/4/6/8/12 V", "Knob → 0/2/4/6/8/12 V"),
        TapTarget("Bottle", "试剂瓶 → 加 Na₂SO₄", "Bottle → add Na₂SO₄") { salt },
        TapTarget("CockH", "阴极活塞 → 检验 H₂", "Cathode tap → test H₂"),
        TapTarget("CockO", "阳极活塞 → 检验 O₂", "Anode tap → test O₂"),
        TapTarget("Reservoir", "储液球 → 加满重来", "Reservoir → refill"),
    )

    override fun isTarget(name: String) = name == "Knob" || name == "Bottle" || name == "CockH" || name == "CockO" || name == "Reservoir"
    override fun isDragTarget(name: String) = name == "Knob"

    override fun onTap(name: String) {
        when (name) {
            "Knob" -> volts = U_STEPS.firstOrNull { it > volts + 0.05f } ?: 0f
            "Bottle" -> salt = !salt
            "CockH" -> startTest(0)
            "CockO" -> startTest(1)
            "Reservoir" -> refill()
        }
        frame = 0
    }

    override fun tapMessage(name: String): String? {
        val i = Cell.current(volts.toDouble(), electrolyte)
        return when (name) {
            "Knob" -> if (volts <= Cell.U_DEC) tr("电压 %.0f V：低于分解电压 2.2 V，没有电流", "%.0f V: below the 2.2 V decomposition voltage, no current").format(volts)
                      else tr("电压 %.0f V → 电流 %s", "%.0f V → current %s").format(volts, amps(i))
            "Bottle" -> when {
                !salt -> tr("换成纯水：几乎不导电（%s），气泡停止", "Pure water now: barely conducts (%s), the bubbles stop").format(amps(i))
                volts <= Cell.U_DEC -> tr("加入 Na₂SO₄：溶液能导电了，但 U < 2.2 V，先调高电压", "Na₂SO₄ added: it conducts now, but U < 2.2 V — turn the voltage up")
                else -> tr("加入 Na₂SO₄：溶液导电，电流 %s，气泡出现", "Na₂SO₄ added: the water conducts, %s, bubbles appear").format(amps(i))
            }
            "CockH" -> when (cockResult) {
                1 -> tr("阴极管里还没有气体：先通电收集", "No gas in the cathode tube yet: run the current first")
                2 -> tr("检验进行中…", "Test in progress…")
                else -> tr("开阴极活塞，燃着的木条凑近：“噗”的爆鸣 → 是 H₂", "Cathode tap open, burning splint: a squeaky pop → hydrogen")
            }
            "CockO" -> when (cockResult) {
                1 -> tr("阳极管里还没有气体：先通电收集", "No gas in the anode tube yet: run the current first")
                2 -> tr("检验进行中…", "Test in progress…")
                else -> tr("开阳极活塞，带火星的木条复燃 → 是 O₂", "Anode tap open, glowing splint bursts into flame → oxygen")
            }
            "Reservoir" -> tr("重新加满：两管充满溶液，时间和电量归零", "Refilled: both tubes full of solution, time and charge reset")
            else -> null
        }
    }

    private var dragU = 0f
    override fun onDragStart(name: String) { dragU = volts }
    /** Hand right / up turns the knob up: 30 V per metre of hand travel (40 cm ≈ full scale). */
    override fun onDrag(name: String, dx: Float, dy: Float, dz: Float) {
        dragU = (dragU + (dx + dy) * 30f).coerceIn(0f, U_MAX)
        volts = (dragU * 10f).roundToInt() / 10f
    }
    override fun onDragEnd(name: String) { frame = 0 }

    fun refill() {
        cell.refill()
        for (g in tests) g.t = -1f
        bOn.fill(false); bAcc.fill(0f)
    }

    fun resetAll() { volts = 6f; salt = true; speedIdx = 1; refill() }

    override fun reviewVariants(): Map<String, () -> Unit> = mapOf(
        "tests" to { startTest(0); startTest(1) },
        "purewater" to { salt = false; refill() },
    )

    @Composable
    override fun Panel() {
        var u by FieldRef(::volts)
        var si by FieldRef(::speedIdx)
        LabPanel(readout) {
            LabSlider(tr("电压 U", "Voltage U"), u, 0f..U_MAX, "%.1f V".format(u)) { u = (it * 2f).roundToInt() / 2f; volts = u }
            SegmentControl {
                SPEED_LABELS.forEachIndexed { k, l -> SegmentItem(selected = si == k, onClick = { si = k; speedIdx = k }, title = { Text(l) }) }
            }
            Spacer(Modifier.width(4.dp))
            // Na₂SO₄ on/off, the gas tests and refill live in the "pinch the object" row (same actions, lit while on)
            Button(onClick = { resetAll() }) { Text(tr("复位", "Reset")) }
        }
    }

    // ---------------------------------------------------------------- helpers

    /** Ammeter needle angle (rad, clockwise from up): ±50° over 0..AM_FS. */
    private fun amAngle(i: Double): Float = Math.toRadians(-50.0 + 100.0 * i / AM_FS).toFloat()
    /** Knob pointer angle (rad, clockwise from up): −135° at 0 V, +135° at 12 V. */
    private fun knobAngle(u: Float): Float = Math.toRadians(-135.0 + 270.0 * u / U_MAX).toFloat()

    private fun ease(t: Float) = t * t * (3f - 2f * t)
    private fun norm(x: Float, y: Float, z: Float): FloatArray { val l = sqrt(x * x + y * y + z * z); return floatArrayOf(x / l, y / l, z / l) }
    private fun rnd(): Float { seed = (seed * 6364136223846793005L + 1442695040888963407L); return ((seed ushr 40).toInt() and 0xFFFFFF) / 16777216f }

    private fun circleXZ(r: Float, n: Int, cx: Float, y: Float, cz: Float) = FloatArray(n * 3) { k ->
        val a = 2.0 * PI * (k / 3) / n
        when (k % 3) { 0 -> cx + r * cos(a).toFloat(); 1 -> y; else -> cz + r * sin(a).toFloat() }
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
}
