// 大象牙膏 · 催化分解 — elephant toothpaste: catalytic decomposition of H2O2.
//
// LESSON: hydrogen peroxide is full of energy (2 H2O2 -> 2 H2O + O2, -98 kJ/mol) yet sits in its
// bottle for years — the barrier (Ea ~75 kJ/mol) is too high. Pour in a catalyst and it goes: the
// catalyst offers a lower road (I- 56, catalase ~8 kJ/mol) and comes out unchanged. Dish soap traps
// the O2 as foam, and the heat it releases speeds it up further — with 30 % the mix boils in seconds
// and the foam erupts out of the cylinder. Here that is safe, repeatable and side by side:
//   * two 1 L graduated cylinders on trays (drawn 1:1): A with the chosen catalyst, B a control
//     (no catalyst, or the other concentration), poured at the same moment;
//   * the foam (a pool of parcels) rises in the glass, erupts as a column, flops over, piles on the
//     tray; steam above 70 C; a thermometer column and a live O2/T chart on the board;
//   * a glowing splint you can poke into the foam (pinch, or pinch-and-drag it): it relights -> O2;
//   * a floating glass cube shows the cycle at the molecular scale: I- -> IO- -> I-, "I- 消耗 0".
// When the demo opens it has just poured KI into 30 % (the classic eruption); pinch the cylinder to
// refill, pick a catalyst bottle from the rack, pinch the beaker to pour.
// Virtual only: 30 % H2O2 is corrosive — the demo gives no procedure for doing it for real.
//
// Physics: core/catalysis (kinetics, heat balance, gas). Visuals: FoamFx (one cylinder), MicroView.

package com.bytedance.physicslab.solar.demos.elephant

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.bytedance.physicslab.solar.core.P3
import com.bytedance.physicslab.solar.core.catalysis.Catalyst
import com.bytedance.physicslab.solar.core.catalysis.Kinetics
import com.bytedance.physicslab.solar.core.catalysis.Peroxide
import com.bytedance.physicslab.solar.core.catalysis.Reactor
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
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

object ElephantDemo : SpecDemo() {
    override val id = "elephant"
    override val title = "大象牙膏 · 催化分解"  // i18n-ok (zh half of DemoRegistry.titleOf; English in TITLE_EN)
    override val credit = "H₂O₂ + KI"

    // ---------------------------------------------------------------- geometry (metres, volume space)
    private const val PLINTH_Y = -0.262f
    private const val DECK = PLINTH_Y + 0.0175f              // plinth top
    private const val TRAY_Y = DECK + 0.008f                 // tray floor
    private const val TRAY_HALF = 0.17f
    private const val XA = -0.40f
    private const val XB = -0.02f
    private const val R_IN = 0.0325f                         // 1 L graduated cylinder, 1:1
    private const val R_OUT = 0.036f
    private const val FOOT_R = 0.056f
    private const val Y_BOT = TRAY_Y + 0.018f                // inner bottom (hex foot + glass)
    private const val MOUTH_Y = Y_BOT + 0.34f
    private const val V_PEROX = 100f; private const val V_SOAP = 5f; private const val V_CAT = 25f
    // beaker (100 mL) and its pour
    private const val BK_R = 0.027f; private const val BK_H = 0.07f
    private const val TILT = 1.83f                           // 105 deg
    private const val POUR_IN = 0.45f; private const val POUR_MIX = 0.62f; private const val POUR_OUT = 0.85f; private const val POUR_END = 1.35f
    // board: two thermometers and the chart
    private const val BX0 = 0.25f; private const val BX1 = 0.86f; private const val BY0 = -0.228f; private const val BY1 = 0.072f; private const val BZ = -0.10f
    private const val TXA = 0.288f; private const val TXB = 0.324f; private const val TY0 = -0.195f; private const val TY1 = 0.035f
    private const val CX0 = 0.385f; private const val CX1 = 0.838f; private const val CY0 = -0.195f; private const val CY1 = 0.035f
    private const val CURVE_N = 56
    // micro cube
    private val MICRO = P3(0.585f, 0.282f, -0.03f)
    private const val MICRO_HALF = 0.13f
    // splint
    private const val SPLINT_L = 0.17f
    private val SPLINT_HOME = P3(XA + 0.15f, DECK + 0.036f, 0.27f)
    private const val SPLINT_HOME_ANGLE = 0.10f

    private val SPEEDS = floatArrayOf(0.25f, 1f, 5f)
    private val SPEED_LABELS get() = listOf(tr("慢放 ×0.25", "Slow ×0.25"), tr("实时 ×1", "Real ×1"), tr("快进 ×5", "Fast ×5"))
    private val CATS = arrayOf(Catalyst.KI, Catalyst.CATALASE, Catalyst.MNO2, Catalyst.NONE)

    // ---------------------------------------------------------------- UI state (buildSpec reopens on the classic run; speed and colours persist)
    var wIdx = 2                 // 30 %
    var catIdx = 0               // KI
    var bMode = 0                // 0: B = no catalyst (control), 1: B = same catalyst, other concentration
    var startC = 25f
    var soap = true
    var colours = false          // off-white foam; food colouring adds pastel stripes
    var speedIdx = 1

    // ---------------------------------------------------------------- run state
    private var phase = READY
    private var pourT = 0f
    private var rA: Reactor? = null
    private var rB: Reactor? = null
    private var chartT = 0f
    private var frame = 0
    private var textLang = -1
    private var rnd = Random(11)
    private var runSeed = 0
    private var lastO2A = 0.0; private var lastO2B = 0.0
    private var gasLpsA = 0f; private var gasLpsB = 0f

    private const val READY = 0; private const val POURING = 1; private const val RUNNING = 2

    // splint
    private var spState = SP_HOME
    private var spT = 0f
    private var spFrom = SPLINT_HOME; private var spTo = SPLINT_HOME
    private var spAngFrom = SPLINT_HOME_ANGLE; private var spAngTo = SPLINT_HOME_ANGLE
    private var spPos = SPLINT_HOME; private var spAng = SPLINT_HOME_ANGLE
    private var flame = 0f; private var litFor = 0f; private var relitEver = false
    private var pokeHasO2 = false; private var lastPokeOk = false
    private const val SP_HOME = 0; private const val SP_IN = 1; private const val SP_HOLD = 2; private const val SP_BACK = 3; private const val SP_HELD = 4; private const val SP_LEFT = 5

    // ---------------------------------------------------------------- nodes
    // ONE pool of foam blobs for both cylinders (one shared mesh), split per run by the foam each pushes out
    private const val POOL = 440
    private lateinit var foamNodes: Array<Node>
    private val fxA = FoamFx("A", XA, 0f, Y_BOT, MOUTH_Y, R_IN, R_OUT, TRAY_Y, TRAY_HALF, FOOT_R, POOL)
    private val fxB = FoamFx("B", XB, 0f, Y_BOT, MOUTH_Y, R_IN, R_OUT, TRAY_Y, TRAY_HALF, FOOT_R, POOL)
    private val micro = MicroView(MICRO_HALF)
    private lateinit var beakerA: Node; private lateinit var beakerB: Node
    private lateinit var bkLiqA: Node; private lateinit var bkLiqB: Node
    private lateinit var streamA: Node; private lateinit var streamB: Node
    private lateinit var splint: Node
    private lateinit var ember: Node; private lateinit var emberGlow: Node
    private lateinit var flameOuter: Node; private lateinit var flameCore: Node
    private lateinit var steam: Array<Node>
    private lateinit var colA: Node; private lateinit var colB: Node
    private lateinit var curves: Array<MeshData>
    private lateinit var selRing: Node
    private lateinit var bottles: Array<Node>
    private val stX = FloatArray(STEAM_N); private val stY = FloatArray(STEAM_N); private val stZ = FloatArray(STEAM_N)
    private val stVx = FloatArray(STEAM_N); private val stVz = FloatArray(STEAM_N); private val stAge = FloatArray(STEAM_N) { -1f }
    private var steamAccA = 0f; private var steamAccB = 0f
    private var curveTurn = 0
    private val curveDirty = BooleanArray(4)
    private const val STEAM_N = 14

    // chart history (per cylinder): samples every dt, halved in resolution when full
    private class Hist {
        val t = FloatArray(HN); val v = FloatArray(HN); val c = FloatArray(HN); var n = 0; var dt = 0.05f; var next = 0f
        fun clear() { n = 0; dt = 0.05f; next = 0f }
        fun add(time: Float, vol: Float, tc: Float) {
            if (time < next) return
            if (n == HN) { for (k in 0 until HN / 2) { t[k] = t[2 * k]; v[k] = v[2 * k]; c[k] = c[2 * k] }; n = HN / 2; dt *= 2 }
            t[n] = time; v[n] = vol; c[n] = tc; n++; next = time + dt
        }
        /** value at [time] (0 = O2 L, 1 = T C); after the last sample, towards the live value. */
        fun at(time: Float, which: Int, now: Float, live: Float): Float {
            val a = if (which == 0) v else c
            if (n == 0) return live
            if (time >= t[n - 1]) { val span = now - t[n - 1]; return if (span <= 1e-6f) live else a[n - 1] + (live - a[n - 1]) * ((time - t[n - 1]) / span).coerceIn(0f, 1f) }
            var lo = 0; var hi = n - 1
            while (hi - lo > 1) { val mid = (lo + hi) / 2; if (t[mid] <= time) lo = mid else hi = mid }
            val f = ((time - t[lo]) / maxOf(1e-6f, t[hi] - t[lo])).coerceIn(0f, 1f)
            return a[lo] + (a[hi] - a[lo]) * f
        }
        companion object { const val HN = 400 }
    }
    private val histA = Hist(); private val histB = Hist()

    // ---------------------------------------------------------------- look (sRGB)
    private val alu = Mat.Pbr(Rgb(0.80f, 0.82f, 0.85f), metallic = 1f, roughness = 0.32f)
    private val darkSteel = Mat.Pbr(Rgb(0.30f, 0.31f, 0.33f), metallic = 1f, roughness = 0.40f)
    private val slabTex by lazy { ProcTex.bakePlanet(256, 128, Rgb(0.07f, 0.075f, 0.085f), Rgb(0.12f, 0.125f, 0.14f), Rgb(0.20f, 0.205f, 0.22f), 0f, 77) }
    private val slab get() = Mat.Pbr(Rgb(1f, 1f, 1f), metallic = 0f, roughness = 0.55f, tex = slabTex)
    private val trayMat = Mat.Pbr(Rgb(0.13f, 0.17f, 0.24f), metallic = 0f, roughness = 0.38f)
    private val glass = Mat.Pbr(Rgb(0.86f, 0.93f, 1.0f), metallic = 0f, roughness = 0.04f, alpha = 0.16f)
    private val glassFoot = Mat.Pbr(Rgb(0.80f, 0.90f, 1.0f), metallic = 0f, roughness = 0.06f, alpha = 0.45f)
    private val tickMat = Mat.Unlit(Rgb(0.90f, 0.95f, 1.0f), alpha = 0.85f, blend = Blend.TRANSPARENT)
    private val board = Mat.Pbr(Rgb(0.05f, 0.06f, 0.08f), metallic = 0f, roughness = 0.08f, alpha = 0.86f)
    private val axisMat = Mat.Unlit(Rgb(0.75f, 0.85f, 0.95f), alpha = 0.75f, blend = Blend.TRANSPARENT)
    private val gridMat = Mat.Unlit(Rgb(0.55f, 0.70f, 0.85f), alpha = 0.20f, blend = Blend.TRANSPARENT)
    private val wood = Mat.Pbr(Rgb(0.62f, 0.44f, 0.26f), metallic = 0f, roughness = 0.6f)
    private val C_O2 = Rgb(0.35f, 0.82f, 1.0f)
    private val C_T = Rgb(1.0f, 0.50f, 0.20f)
    private val STRIPE_RGB = listOf(Rgb(1.0f, 0.66f, 0.72f), Rgb(0.66f, 0.80f, 1.0f), Rgb(0.68f, 0.94f, 0.76f))   // pastel pink / sky / mint
    private val puffImg: Image by lazy { ElephantMesh.puff() }
    private val foamPlain: Image by lazy { ElephantMesh.foamTex(null) }
    private val foamBlanket: Image by lazy { ElephantMesh.foamTex(null, 64, 64, 8f, 8f) }
    private val foamStriped: Image by lazy { val w = Rgb(1f, 1f, 1f); ElephantMesh.foamTex(listOf(STRIPE_RGB[0], w, STRIPE_RGB[1], w, STRIPE_RGB[2], w)) }
    private val steamMats by lazy { Array(6) { k -> Mat.Unlit(Rgb(0.90f, 0.93f, 0.97f), alpha = 0.06f + 0.05f * k, tex = puffImg, blend = Blend.ADD) } }

    private fun cat() = CATS[catIdx]
    private fun wA() = Peroxide.W[wIdx]
    private fun wB() = if (bMode == 0) wA() else if (wIdx == 0) Peroxide.W[2] else Peroxide.W[0]
    private fun catB() = if (bMode == 0) Catalyst.NONE else cat()
    private fun pct(w: Double) = "%d%%".format((w * 100).roundToInt())

    /** Foam colour: off-white; KI foam a touch warmer (a little I2 forms), MnO2 foam a little grey. */
    private fun foamTint(c: Catalyst) = when (c) {
        Catalyst.KI -> Rgb(1.0f, 0.97f, 0.91f)
        Catalyst.CATALASE -> Rgb(0.99f, 0.97f, 0.93f)
        Catalyst.MNO2 -> Rgb(0.86f, 0.86f, 0.85f)
        Catalyst.NONE -> Rgb(0.97f, 0.97f, 0.96f)
    }
    private fun liquidMat(c: Catalyst?, mixed: Boolean): Mat = when {
        !mixed || c == null -> Mat.Pbr(Rgb(0.72f, 0.88f, 1.0f), roughness = 0.05f, alpha = 0.40f)
        c == Catalyst.KI -> Mat.Pbr(Rgb(0.95f, 0.70f, 0.25f), roughness = 0.08f, alpha = 0.62f)
        c == Catalyst.CATALASE -> Mat.Pbr(Rgb(0.80f, 0.62f, 0.38f), roughness = 0.2f, alpha = 0.80f)
        c == Catalyst.MNO2 -> Mat.Pbr(Rgb(0.16f, 0.14f, 0.13f), roughness = 0.3f, alpha = 0.88f)
        else -> Mat.Pbr(Rgb(0.72f, 0.88f, 1.0f), roughness = 0.05f, alpha = 0.40f)
    }
    private fun beakerLiquidMat(c: Catalyst): Mat = when (c) {
        Catalyst.KI -> Mat.Pbr(Rgb(0.98f, 0.92f, 0.70f), roughness = 0.06f, alpha = 0.55f)
        Catalyst.CATALASE -> Mat.Pbr(Rgb(0.72f, 0.52f, 0.30f), roughness = 0.3f, alpha = 0.85f)
        Catalyst.MNO2 -> Mat.Pbr(Rgb(0.10f, 0.09f, 0.09f), roughness = 0.35f, alpha = 0.92f)
        Catalyst.NONE -> Mat.Pbr(Rgb(0.72f, 0.88f, 1.0f), roughness = 0.05f, alpha = 0.40f)
    }
    private fun foamMats(c: Catalyst): Array<Mat> {
        val t = foamTint(c)
        val whites = (0 until FoamFx.WHITES).map { k ->
            val l = floatArrayOf(1.0f, 0.97f, 0.94f, 0.99f)[k]                    // creamy: only a whisper of variation
            val col = Rgb(t.r * l, t.g * l, t.b * l)
            Mat.Pbr(col, metallic = 0f, roughness = 0.75f, emissive = Rgb(col.r * 0.24f, col.g * 0.24f, col.b * 0.24f))
        }
        val stripes = STRIPE_RGB.map { s -> Mat.Pbr(s, metallic = 0f, roughness = 0.72f, emissive = Rgb(s.r * 0.22f, s.g * 0.22f, s.b * 0.22f)) }
        return (whites + stripes).toTypedArray()
    }
    private fun innerMat(c: Catalyst) = Mat.Pbr(foamTint(c), metallic = 0f, roughness = 0.72f, tex = if (colours) foamStriped else foamPlain,
        emissive = Rgb(0.20f, 0.20f, 0.19f))
    private fun blanketMat(c: Catalyst) = Mat.Pbr(foamTint(c), metallic = 0f, roughness = 0.7f, tex = foamBlanket, emissive = Rgb(0.16f, 0.16f, 0.16f))

    // ---------------------------------------------------------------- build

    override fun buildSpec(cacheDir: File): Node {
        val root = Node("ElephantRoot")
        val unitCyl = MeshGen.cylinder(1f, 1f, 24)
        val unitBox = MeshGen.box(1f, 1f, 1f)
        val sphere = MeshGen.sphere(1f, 10, 18, MeshGen.SphereUv.EQUIRECT)
        val ballMesh = MeshGen.sphere(1f, 14, 24, MeshGen.SphereUv.EQUIRECT)
        frame = 0; textLang = -1; curveTurn = 0; runSeed = 0
        rnd = Random(11)
        // every visit opens on the classic: KI into 30 %, B the uncatalysed control (speed and colours are kept)
        wIdx = 2; catIdx = 0; bMode = 0; startC = 25f; soap = true

        root.add("Plinth", MeshGen.box(1.78f, 0.035f, 0.82f), slab, P3(0f, PLINTH_Y, 0f))
        root.add("PlinthBand", MeshGen.box(1.79f, 0.008f, 0.83f), alu, P3(0f, PLINTH_Y, 0f))

        // ---- trays and cylinders
        for ((tag, cx) in listOf("A" to XA, "B" to XB)) buildCylinder(root, tag, cx, unitCyl)
        fxA.build(root, sphere, unitCyl, liquidMat(null, false), innerMat(Catalyst.KI),
            Mat.Unlit(Rgb(0.9f, 0.97f, 1f), alpha = 0.55f, blend = Blend.ADD), foamMats(Catalyst.KI)[0])
        fxB.build(root, sphere, unitCyl, liquidMat(null, false), innerMat(Catalyst.NONE),
            Mat.Unlit(Rgb(0.9f, 0.97f, 1f), alpha = 0.55f, blend = Blend.ADD), foamMats(Catalyst.NONE)[0])
        val blob = MeshGen.sphere(1f, 9, 16, MeshGen.SphereUv.EQUIRECT)
        foamNodes = Array(POOL) { i -> root.add("Foam_$i", blob, foamMats(Catalyst.KI)[0]).also { it.visible = false } }
        fxA.attach(foamNodes); fxB.attach(foamNodes)

        // ---- beakers on their stands (A's is the gaze target)
        beakerA = buildBeaker(root, "Beaker", XA, unitCyl).also { it.tapRadius = 0.06f }
        bkLiqA = beakerA.children.first { it.name == "BeakerLiq" }
        beakerB = buildBeaker(root, "BeakerB", XB, unitCyl)
        bkLiqB = beakerB.children.first { it.name == "BeakerBLiq" }
        streamA = root.add("StreamA", unitCyl, beakerLiquidMat(Catalyst.KI)).also { it.visible = false }
        streamB = root.add("StreamB", unitCyl, beakerLiquidMat(Catalyst.NONE)).also { it.visible = false }

        // ---- catalyst rack (left): KI, yeast / MnO2, water
        buildRack(root, unitCyl, unitBox)

        // ---- the glowing splint on its holder (front right of A)
        root.add("SplintRest", MeshGen.box(0.03f, 0.022f, 0.034f), wood, P3(SPLINT_HOME.x + 0.04f, DECK + 0.011f, SPLINT_HOME.z))
        root.add("SplintRest2", MeshGen.box(0.03f, 0.03f, 0.034f), wood, P3(SPLINT_HOME.x - 0.045f, DECK + 0.015f, SPLINT_HOME.z))
        splint = root.add(Node("Splint")).also { it.tapRadius = 0.055f }
        splint.add("SplintStick", unitCyl, Mat.Pbr(Rgb(0.86f, 0.70f, 0.46f), roughness = 0.7f), P3(0f, 0f, 0f)).also {
            it.scale = P3(0.0045f, SPLINT_L, 0.0045f); it.rot = Q.axisAngle(0f, 0f, 1f, (PI / 2).toFloat())
        }
        splint.add("SplintChar", unitCyl, Mat.Pbr(Rgb(0.10f, 0.08f, 0.07f), roughness = 0.9f), P3(-SPLINT_L / 2 + 0.008f, 0f, 0f)).also {
            it.scale = P3(0.0048f, 0.016f, 0.0048f); it.rot = Q.axisAngle(0f, 0f, 1f, (PI / 2).toFloat())
        }
        ember = splint.add("Ember", ballMesh, Mat.Unlit(Rgb(1f, 0.38f, 0.10f)), P3(-SPLINT_L / 2, 0f, 0f)).also { it.uniformScale(0.0062f) }
        emberGlow = splint.add("EmberGlow", ballMesh, Mat.Unlit(Rgb(1f, 0.35f, 0.08f), alpha = 0.35f, blend = Blend.ADD), P3(-SPLINT_L / 2, 0f, 0f)).also { it.uniformScale(0.016f) }
        flameOuter = root.add("FlameOuter", ballMesh, Mat.Unlit(Rgb(1.0f, 0.62f, 0.18f), alpha = 0.55f, blend = Blend.ADD)).also { it.visible = false }
        flameCore = root.add("FlameCore", ballMesh, Mat.Unlit(Rgb(1.0f, 0.92f, 0.65f), alpha = 0.85f, blend = Blend.ADD)).also { it.visible = false }

        // ---- steam wisps (billboards)
        val quad = ElephantMesh.quad()
        steam = Array(STEAM_N) { k -> root.add("Steam$k", quad, steamMats[0]).also { it.billboard = true; it.visible = false } }

        // ---- board: thermometers + chart
        buildBoard(root, unitBox)

        // ---- micro cube
        val mg = root.add(Node("Micro")).also { it.pos = MICRO }
        val h = MICRO_HALF
        mg.add("MicroBack", MeshGen.box(2 * h, 2 * h, 0.004f), Mat.Pbr(Rgb(0.06f, 0.08f, 0.12f), roughness = 0.08f, alpha = 0.72f), P3(0f, 0f, -h))
        mg.add("MicroGlass", ElephantMesh.merge(listOf(
            ElephantMesh.Part(unitBox, 0f, 0f, h, 2 * h, 2 * h, 0.003f), ElephantMesh.Part(unitBox, 0f, h, 0f, 2 * h, 0.003f, 2 * h),
            ElephantMesh.Part(unitBox, 0f, -h, 0f, 2 * h, 0.003f, 2 * h), ElephantMesh.Part(unitBox, -h, 0f, 0f, 0.003f, 2 * h, 2 * h),
            ElephantMesh.Part(unitBox, h, 0f, 0f, 0.003f, 2 * h, 2 * h))), Mat.Pbr(Rgb(0.75f, 0.88f, 1.0f), roughness = 0.04f, alpha = 0.07f))
        val rails = ArrayList<ElephantMesh.Part>()
        for (a in floatArrayOf(-h, h)) for (b in floatArrayOf(-h, h)) {
            rails += ElephantMesh.Part(unitBox, 0f, a, b, 2 * h + 0.006f, 0.006f, 0.006f)
            rails += ElephantMesh.Part(unitBox, a, 0f, b, 0.006f, 2 * h, 0.006f)
            rails += ElephantMesh.Part(unitBox, a, b, 0f, 0.006f, 0.006f, 2 * h)
        }
        mg.add("MicroRails", ElephantMesh.merge(rails), alu)
        micro.build(mg, ballMesh, unitBox)

        // ---- the opening scene: KI has just gone into the 30 % (pre-rolled 1.3 s), so the eruption
        // starts a moment after the demo appears; pinch the cylinder for a fresh start
        resetSplint()
        refill()
        mix()
        repeat(78) { simulate(1f / 60f) }
        update(0f)
        return root
    }

    private fun buildCylinder(root: Node, tag: String, cx: Float, unitCyl: MeshData) {
        val trayParts = listOf(
            ElephantMesh.Part(MeshGen.box(1f, 1f, 1f), 0f, -0.004f, 0f, 2 * TRAY_HALF, 0.008f, 2 * TRAY_HALF),
            ElephantMesh.Part(MeshGen.box(1f, 1f, 1f), -TRAY_HALF, 0.006f, 0f, 0.008f, 0.028f, 2 * TRAY_HALF + 0.008f),
            ElephantMesh.Part(MeshGen.box(1f, 1f, 1f), TRAY_HALF, 0.006f, 0f, 0.008f, 0.028f, 2 * TRAY_HALF + 0.008f),
            ElephantMesh.Part(MeshGen.box(1f, 1f, 1f), 0f, 0.006f, -TRAY_HALF, 2 * TRAY_HALF, 0.028f, 0.008f),
            ElephantMesh.Part(MeshGen.box(1f, 1f, 1f), 0f, 0.006f, TRAY_HALF, 2 * TRAY_HALF, 0.028f, 0.008f))
        root.add("Tray$tag", ElephantMesh.merge(trayParts), trayMat, P3(cx, TRAY_Y, 0f))
        root.add("Foot$tag", MeshGen.cylinder(FOOT_R, 0.012f, 6), glassFoot, P3(cx, TRAY_Y + 0.006f, 0f))
        root.add("Bottom$tag", MeshGen.cylinder(R_OUT, 0.006f, 28), glassFoot, P3(cx, TRAY_Y + 0.015f, 0f))
        root.add("Glass$tag", ElephantMesh.openTube(R_OUT, MOUTH_Y - Y_BOT + 0.004f, 32), glass, P3(cx, Y_BOT, 0f))
        root.add("Lip$tag", ElephantMesh.ring(R_OUT + 0.0015f, 0f, 0.0028f, 32), Mat.Pbr(Rgb(0.88f, 0.95f, 1.0f), roughness = 0.05f, alpha = 0.55f), P3(cx, MOUTH_Y + 0.003f, 0f))
        // graduations every 100 mL (long) and 50 mL (short), on the side facing the viewer
        val area = (PI * R_IN * R_IN).toFloat()
        val ticks = ArrayList<ElephantMesh.Part>()
        val tick = MeshGen.box(1f, 1f, 1f)
        for (ml in 50..1000 step 50) {
            val y = Y_BOT + ml * 1e-6f / area
            val long = ml % 100 == 0
            ticks += ElephantMesh.Part(tick, -0.006f, y, R_OUT + 0.0012f, if (long) 0.026f else 0.013f, 0.0022f, 0.0015f)
        }
        root.add("Grad$tag", ElephantMesh.merge(ticks), tickMat, P3(cx, 0f, 0f))
        // thermometer probe: a steel rod down the back of the cylinder
        root.add("Probe$tag", unitCyl, Mat.Pbr(Rgb(0.85f, 0.86f, 0.88f), metallic = 1f, roughness = 0.2f), P3(cx - 0.016f, (Y_BOT + 0.006f + MOUTH_Y + 0.07f) / 2, -0.016f))
            .also { it.scale = P3(0.0032f, MOUTH_Y + 0.07f - Y_BOT - 0.006f, 0.0032f) }
        root.add("ProbeCap$tag", unitCyl, Mat.Pbr(Rgb(0.10f, 0.10f, 0.11f), roughness = 0.5f), P3(cx - 0.016f, MOUTH_Y + 0.085f, -0.016f)).also { it.scale = P3(0.008f, 0.03f, 0.008f) }
        // beaker stand: a post and a little round platform beside the mouth
        val sx = cx + 0.125f; val sz = -0.035f; val top = MOUTH_Y - 0.035f
        root.add("Stand$tag", unitCyl, alu, P3(sx, (TRAY_Y + top) / 2, sz - 0.03f)).also { it.scale = P3(0.006f, top - TRAY_Y, 0.006f) }
        root.add("StandFoot$tag", MeshGen.cylinder(0.03f, 0.008f, 20), darkSteel, P3(sx, TRAY_Y + 0.004f, sz - 0.03f))
        root.add("StandArm$tag", MeshGen.box(0.008f, 0.006f, 0.03f), alu, P3(sx, top - 0.004f, sz - 0.015f))
        root.add("StandPlate$tag", MeshGen.cylinder(0.034f, 0.005f, 24), darkSteel, P3(sx, top - 0.0025f, sz))
        // the "Cylinder" gaze target (A only): a collider at mid height
        if (tag == "A") root.add(Node("Cylinder")).also { it.pos = P3(cx, (Y_BOT + MOUTH_Y) / 2 - 0.05f, 0f); it.tapRadius = 0.07f }
    }

    private fun buildBeaker(root: Node, name: String, cx: Float, unitCyl: MeshData): Node {
        val g = root.add(Node(name))
        g.add("${name}Glass", ElephantMesh.openTube(BK_R, BK_H, 24), glass, P3(0f, 0f, 0f))
        g.add("${name}Base", MeshGen.cylinder(BK_R, 0.003f, 24), glassFoot, P3(0f, 0.0015f, 0f))
        g.add("${name}Rim", ElephantMesh.ring(BK_R + 0.001f, BK_H, 0.0018f, 24), Mat.Pbr(Rgb(0.88f, 0.95f, 1.0f), roughness = 0.05f, alpha = 0.6f))
        g.add("${name}Liq", unitCyl, beakerLiquidMat(Catalyst.KI))
        g.pos = beakerRest(cx)
        return g
    }

    private fun beakerRest(cx: Float) = P3(cx + 0.125f, MOUTH_Y - 0.035f, -0.035f)

    private fun buildRack(root: Node, unitCyl: MeshData, unitBox: MeshData) {
        val x0 = -0.88f; val x1 = -0.64f; val z = 0.10f; val d = 0.13f
        val y1 = DECK + 0.128f
        root.add("Rack", ElephantMesh.merge(listOf(
            ElephantMesh.Part(unitBox, x0, DECK + 0.13f, z, 0.008f, 0.26f, d), ElephantMesh.Part(unitBox, x1, DECK + 0.13f, z, 0.008f, 0.26f, d),
            ElephantMesh.Part(unitBox, (x0 + x1) / 2, DECK + 0.004f, z, x1 - x0, 0.008f, d), ElephantMesh.Part(unitBox, (x0 + x1) / 2, y1, z, x1 - x0, 0.008f, d),
            ElephantMesh.Part(unitBox, (x0 + x1) / 2, DECK + 0.13f, z - d / 2, x1 - x0, 0.26f, 0.004f))), wood)
        val spots = listOf(P3(-0.82f, DECK + 0.008f, z), P3(-0.70f, DECK + 0.008f, z), P3(-0.82f, y1 + 0.004f, z), P3(-0.70f, y1 + 0.004f, z))
        val glassClear = Mat.Pbr(Rgb(0.85f, 0.92f, 1.0f), roughness = 0.05f, alpha = 0.30f)
        bottles = Array(4) { k ->
            val g = root.add(Node("Bottle$k")).also { it.pos = P3(spots[k].x, spots[k].y + 0.038f, spots[k].z); it.tapRadius = 0.05f }
            when (k) {
                0 -> {   // KI solution: amber bottle, white cap and label
                    g.add("B0Body", unitCyl, Mat.Pbr(Rgb(0.62f, 0.33f, 0.08f), roughness = 0.12f, alpha = 0.85f), P3(0f, -0.002f, 0f)).also { it.scale = P3(0.024f, 0.068f, 0.024f) }
                    g.add("B0Label", unitCyl, Mat.Pbr(Rgb(0.95f, 0.95f, 0.92f), roughness = 0.6f), P3(0f, -0.006f, 0f)).also { it.scale = P3(0.0245f, 0.026f, 0.0245f) }
                    g.add("B0Neck", unitCyl, Mat.Pbr(Rgb(0.62f, 0.33f, 0.08f), roughness = 0.12f, alpha = 0.85f), P3(0f, 0.036f, 0f)).also { it.scale = P3(0.011f, 0.012f, 0.011f) }
                    g.add("B0Cap", unitCyl, Mat.Pbr(Rgb(0.95f, 0.95f, 0.95f), roughness = 0.4f), P3(0f, 0.047f, 0f)).also { it.scale = P3(0.013f, 0.014f, 0.013f) }
                }
                1 -> {   // yeast: a jar of tan granules, brown lid
                    g.add("B1Jar", unitCyl, glassClear, P3(0f, -0.006f, 0f)).also { it.scale = P3(0.029f, 0.06f, 0.029f) }
                    g.add("B1Fill", unitCyl, Mat.Pbr(Rgb(0.80f, 0.62f, 0.38f), roughness = 0.85f), P3(0f, -0.016f, 0f)).also { it.scale = P3(0.026f, 0.038f, 0.026f) }
                    g.add("B1Lid", unitCyl, Mat.Pbr(Rgb(0.45f, 0.26f, 0.12f), roughness = 0.5f), P3(0f, 0.030f, 0f)).also { it.scale = P3(0.030f, 0.012f, 0.030f) }
                }
                2 -> {   // MnO2: a jar of black powder, black lid
                    g.add("B2Jar", unitCyl, glassClear, P3(0f, -0.006f, 0f)).also { it.scale = P3(0.027f, 0.06f, 0.027f) }
                    g.add("B2Fill", unitCyl, Mat.Pbr(Rgb(0.09f, 0.08f, 0.08f), roughness = 0.9f), P3(0f, -0.020f, 0f)).also { it.scale = P3(0.024f, 0.03f, 0.024f) }
                    g.add("B2Lid", unitCyl, Mat.Pbr(Rgb(0.12f, 0.12f, 0.13f), roughness = 0.4f), P3(0f, 0.030f, 0f)).also { it.scale = P3(0.028f, 0.012f, 0.028f) }
                }
                else -> {   // water: a wash bottle
                    g.add("B3Body", unitCyl, Mat.Pbr(Rgb(0.90f, 0.95f, 1.0f), roughness = 0.35f, alpha = 0.55f), P3(0f, -0.004f, 0f)).also { it.scale = P3(0.024f, 0.064f, 0.024f) }
                    g.add("B3Water", unitCyl, Mat.Pbr(Rgb(0.45f, 0.70f, 1.0f), roughness = 0.1f, alpha = 0.45f), P3(0f, -0.014f, 0f)).also { it.scale = P3(0.021f, 0.04f, 0.021f) }
                    g.add("B3Cap", unitCyl, Mat.Pbr(Rgb(0.25f, 0.55f, 0.95f), roughness = 0.4f), P3(0f, 0.034f, 0f)).also { it.scale = P3(0.012f, 0.012f, 0.012f) }
                    g.add("B3Nozzle", unitCyl, Mat.Pbr(Rgb(0.90f, 0.95f, 1.0f), roughness = 0.35f, alpha = 0.8f), P3(0.010f, 0.052f, 0f)).also {
                        it.scale = P3(0.003f, 0.03f, 0.003f); it.rot = Q.axisAngle(0f, 0f, 1f, -0.7f)
                    }
                }
            }
            g
        }
        selRing = root.add("SelRing", MeshGen.annulus(0.030f, 0.040f, 40), Mat.Unlit(Rgb(0.35f, 0.85f, 1.0f), alpha = 0.85f, blend = Blend.ADD))
    }

    private fun buildBoard(root: Node, unitBox: MeshData) {
        root.add("Board", MeshGen.box(BX1 - BX0, BY1 - BY0, 0.012f), board, P3((BX0 + BX1) / 2, (BY0 + BY1) / 2, BZ - 0.010f))
        root.add("BoardFoot", MeshGen.box(BX1 - BX0 - 0.04f, 0.012f, 0.05f), darkSteel, P3((BX0 + BX1) / 2, DECK + 0.006f, BZ - 0.01f))
        val ax = ArrayList<ElephantMesh.Part>()
        ax += ElephantMesh.Part(unitBox, (CX0 + CX1) / 2, CY0, BZ, CX1 - CX0 + 0.004f, 0.003f, 0.002f)
        ax += ElephantMesh.Part(unitBox, CX0, (CY0 + CY1) / 2, BZ, 0.003f, CY1 - CY0, 0.002f)
        ax += ElephantMesh.Part(unitBox, CX1, (CY0 + CY1) / 2, BZ, 0.003f, CY1 - CY0, 0.002f)
        for (k in 0..4) {                                    // ticks on both axes (quarters)
            val y = CY0 + (CY1 - CY0) * k / 4f
            ax += ElephantMesh.Part(unitBox, CX0 - 0.007f, y, BZ, 0.012f, 0.0025f, 0.002f)
            ax += ElephantMesh.Part(unitBox, CX1 + 0.007f, y, BZ, 0.012f, 0.0025f, 0.002f)
            val x = CX0 + (CX1 - CX0) * k / 4f
            ax += ElephantMesh.Part(unitBox, x, CY0 - 0.007f, BZ, 0.0025f, 0.012f, 0.002f)
        }
        for (x in floatArrayOf(TXA, TXB)) for (c in 0..5) {   // thermometer ticks every 20 C
            val y = TY0 + (TY1 - TY0) * c / 5f
            ax += ElephantMesh.Part(unitBox, x - 0.016f, y, BZ, if (c % 5 == 0) 0.012f else 0.008f, 0.0022f, 0.002f)
        }
        root.add("ChartAxes", ElephantMesh.merge(ax), axisMat)
        val grid = ArrayList<ElephantMesh.Part>()
        for (k in 1..4) grid += ElephantMesh.Part(unitBox, (CX0 + CX1) / 2, CY0 + (CY1 - CY0) * k / 4f, BZ - 0.003f, CX1 - CX0, 0.0016f, 0.001f)
        for (k in 1..3) grid += ElephantMesh.Part(unitBox, CX0 + (CX1 - CX0) * k / 4f, (CY0 + CY1) / 2, BZ - 0.003f, 0.0016f, CY1 - CY0, 0.001f)
        root.add("ChartGrid", ElephantMesh.merge(grid), gridMat)
        // thermometer tracks (glass) and columns
        for (x in floatArrayOf(TXA, TXB)) {
            root.add("ThTrack", unitBox, Mat.Unlit(Rgb(0.30f, 0.38f, 0.50f), alpha = 0.35f, blend = Blend.TRANSPARENT), P3(x, (TY0 + TY1) / 2, BZ - 0.001f))
                .also { it.scale = P3(0.020f, TY1 - TY0 + 0.01f, 0.003f) }
            root.add("ThBulb", MeshGen.sphere(0.016f, 12, 20, MeshGen.SphereUv.EQUIRECT), Mat.Unlit(Rgb(1f, 0.45f, 0.2f)), P3(x, TY0 - 0.012f, BZ + 0.002f))
        }
        colA = root.add("ThColA", unitBox, Mat.Unlit(Rgb(1f, 0.5f, 0.2f)), P3(TXA, TY0, BZ + 0.002f))
        colB = root.add("ThColB", unitBox, Mat.Unlit(Rgb(1f, 0.5f, 0.2f)), P3(TXB, TY0, BZ + 0.002f))
        // 70 C: steam from here on (a short warm mark across both thermometers)
        root.add("SteamMark", unitBox, Mat.Unlit(Rgb(0.95f, 0.95f, 1f), alpha = 0.7f, blend = Blend.ADD), P3((TXA + TXB) / 2, TY0 + (TY1 - TY0) * 0.7f, BZ + 0.003f))
            .also { it.scale = P3(TXB - TXA + 0.03f, 0.002f, 0.002f) }
        curves = Array(4) { ElephantMesh.ribbon(CURVE_N) }
        val cm = listOf(Mat.Unlit(C_O2, alpha = 1f, blend = Blend.ADD), Mat.Unlit(C_T, alpha = 1f, blend = Blend.ADD),
            Mat.Unlit(C_O2, alpha = 0.42f, blend = Blend.ADD), Mat.Unlit(C_T, alpha = 0.42f, blend = Blend.ADD))
        for (k in 0 until 4) root.add("Curve$k", curves[k], cm[k])
        curveDirty.fill(true)
        for (k in 0 until 4) writeCurve(k)
    }

    // ---------------------------------------------------------------- experiment control

    /** Fresh solutions in both cylinders, catalyst beakers full, chart and micro view cleared. */
    fun refill() {
        phase = READY; pourT = 0f; rA = null; rB = null; chartT = 0f
        lastO2A = 0.0; lastO2B = 0.0; gasLpsA = 0f; gasLpsB = 0f
        runSeed++
        for (n in foamNodes) n.visible = false
        fxA.reset(V_PEROX + V_SOAP, 100 + runSeed); fxB.reset(V_PEROX + V_SOAP, 200 + runSeed)
        fxA.liquid.setMat(liquidMat(null, false)); fxB.liquid.setMat(liquidMat(null, false))
        histA.clear(); histB.clear()
        micro.reset(300 + runSeed)
        for (k in 0 until STEAM_N) { stAge[k] = -1f; steam[k].visible = false }
        steamAccA = 0f; steamAccB = 0f
        bkLiqA.setMat(beakerLiquidMat(cat())); bkLiqB.setMat(beakerLiquidMat(catB()))
        streamA.setMat(beakerLiquidMat(cat())); streamB.setMat(beakerLiquidMat(catB()))
        setBeakerLiquid(bkLiqA, 1f); setBeakerLiquid(bkLiqB, 1f)
        poseBeaker(beakerA, XA, 0f); poseBeaker(beakerB, XB, 0f)
        streamA.visible = false; streamB.visible = false
        applyLook()
        curveDirty.fill(true)
    }

    /** Pinch the beaker: tip the catalyst in (refilling first if this cylinder has already run). */
    fun pour() {
        if (phase != READY) refill()
        phase = POURING; pourT = 0f
    }

    /** The catalyst lands in the H2O2: start both reactors and size the foam parcels. */
    private fun mix() {
        val a = Reactor(wA(), cat(), startC.toDouble(), soap)
        val b = Reactor(wB(), catB(), startC.toDouble(), soap)
        rA = a; rB = b
        phase = if (phase == POURING) POURING else RUNNING
        if (phase == RUNNING) { setBeakerLiquid(bkLiqA, 0f); setBeakerLiquid(bkLiqB, 0f); poseBeaker(beakerA, XA, 0f); poseBeaker(beakerB, XB, 0f) }
        for ((fx, r) in listOf(fxA to a, fxB to b)) {
            fx.setLiquid(V_PEROX + V_SOAP + V_CAT)
            fx.liquid.setMat(liquidMat(r.catalyst, true))
        }
        // look ahead: how much foam will leave each mouth? -> split the blob pool, size the blobs
        val outA = expectedOut(a, fxA); val outB = expectedOut(b, fxB)
        val nB = if (outB <= 0f) 0 else maxOf(40, (POOL * outB / (outA + outB)).roundToInt()).coerceAtMost(POOL - if (outA > 0f) 40 else 0)
        val nA = if (outA <= 0f) 0 else POOL - nB
        fxA.planRun(outA, 0, nA); fxB.planRun(outB, nA, nB)
        applyLook()
        chartT = 0f
        curveDirty.fill(true)
    }

    private fun expectedOut(r: Reactor, fx: FoamFx): Float {
        val ahead = r.copy(); ahead.step(240.0)
        return maxOf(0f, (ahead.foamL * 1e-3).toFloat() - fx.freeVolume)
    }

    private fun applyLook() {
        fxA.mats = foamMats(cat()); fxB.mats = foamMats(catB())
        fxA.stripes = colours; fxB.stripes = colours
        fxA.setBodyMat(innerMat(cat()), blanketMat(cat())); fxB.setBodyMat(innerMat(catB()), blanketMat(catB()))
        fxA.recolour(); fxB.recolour()
    }

    fun selectCatalyst(k: Int) { catIdx = k; refill() }
    fun switchColours(on: Boolean) { colours = on; applyLook() }

    // ---------------------------------------------------------------- per frame

    override fun update(dt: Float) {
        simulate(dt * SPEEDS[speedIdx])
        updateSplint(dt)
        val a = rA
        micro.update(dt, cat(), a != null, (a?.let { (it.n / it.n0).toFloat() } ?: 1f), a?.rate() ?: 0.0, (a?.enzymeActive ?: 1.0).toFloat())
        // selected bottle ring
        val b = bottles[catIdx].pos
        selRing.pos = P3(b.x, b.y - 0.036f, b.z)
        // text at 10 Hz, and at once when the language flips
        val lang = if (Lang.en) 1 else 0
        if (frame++ % 6 == 0 || lang != textLang) { textLang = lang; updateReadout(); updateLabels() }
    }

    /** Everything that runs on SIMULATION time (slow motion slows the foam too). */
    private fun simulate(sdt: Float) {
        // pour
        if (phase == POURING) {
            val before = pourT
            pourT += sdt
            if (before < POUR_MIX && pourT >= POUR_MIX) mix()
            val k = when {
                pourT < POUR_IN -> smooth(pourT / POUR_IN)
                pourT < POUR_OUT -> 1f
                else -> smooth(1f - ((pourT - POUR_OUT) / (POUR_END - POUR_OUT)).coerceIn(0f, 1f))
            }
            poseBeaker(beakerA, XA, k); poseBeaker(beakerB, XB, k)
            val drain = ((pourT - POUR_IN) / (POUR_OUT - POUR_IN)).coerceIn(0f, 1f)
            setBeakerLiquid(bkLiqA, 1f - drain); setBeakerLiquid(bkLiqB, 1f - drain)
            val flowing = pourT in POUR_IN..POUR_OUT
            placeStream(streamA, XA, flowing); placeStream(streamB, XB, flowing)
            if (pourT >= POUR_END) { phase = RUNNING; streamA.visible = false; streamB.visible = false }
        }
        // chemistry
        val a = rA; val b = rB
        if (sdt > 0f) {
            a?.step(sdt.toDouble()); b?.step(sdt.toDouble())
            if (a != null) { gasLpsA = (Peroxide.gasLitres(a.nO2 - lastO2A, a.tK) / sdt).toFloat(); lastO2A = a.nO2 }
            if (b != null) { gasLpsB = (Peroxide.gasLitres(b.nO2 - lastO2B, b.tK) / sdt).toFloat(); lastO2B = b.nO2 }
        }
        fxA.update(sdt, ((a?.foamL ?: 0.0) * 1e-3).toFloat(), gasLpsA, soap)
        fxB.update(sdt, ((b?.foamL ?: 0.0) * 1e-3).toFloat(), gasLpsB, soap)
        // chart clock runs while either cylinder still reacts
        if (a != null && b != null && sdt > 0f) {
            val busy = !done(a) || !done(b)
            if (busy) {
                chartT += sdt
                histA.add(chartT, a.o2L25.toFloat(), a.tC.toFloat()); histB.add(chartT, b.o2L25.toFloat(), b.tC.toFloat())
                curveDirty.fill(true)
            }
        }
        // ONE mesh re-upload per frame (~20 us/vertex on the headset): the four chart curves (112
        // vertices each) and the two foam blankets (169) take turns, only when they changed
        for (j in 0 until 6) {
            val k = (curveTurn + j) % 6
            val dirty = when (k) { in 0..3 -> curveDirty[k]; 4 -> fxA.moundDirty; else -> fxB.moundDirty }
            if (!dirty) continue
            when (k) { in 0..3 -> writeCurve(k); 4 -> { fxA.writeMound(); meshEdited(fxA.moundMesh) }; else -> { fxB.writeMound(); meshEdited(fxB.moundMesh) } }
            curveTurn = (k + 1) % 6
            break
        }
        // thermometers
        setColumn(colA, TXA, (a?.tC ?: startC.toDouble()).toFloat())
        setColumn(colB, TXB, (b?.tC ?: startC.toDouble()).toFloat())
        steamStep(sdt, a, b)
    }

    private fun done(r: Reactor) = r.t > 3.0 && r.rate() < 1e-5

    private fun poseBeaker(g: Node, cx: Float, k: Float) {
        val rest = beakerRest(cx); val pour = P3(cx + 0.066f, MOUTH_Y + 0.05f, 0f)
        g.pos = P3(rest.x + (pour.x - rest.x) * k, rest.y + (pour.y - rest.y) * k, rest.z + (pour.z - rest.z) * k)
        g.rot = Q.axisAngle(0f, 0f, 1f, TILT * k)
    }

    private fun setBeakerLiquid(n: Node, f: Float) {
        val h = 0.0125f * f
        n.visible = f > 0.02f
        if (n.visible) { n.scale = P3(BK_R - 0.002f, h, BK_R - 0.002f); n.pos = P3(0f, 0.003f + h / 2, 0f) }
    }

    /** The pour: a thin stream from the beaker lip down into the cylinder. */
    private fun placeStream(s: Node, cx: Float, on: Boolean) {
        s.visible = on
        if (!on) return
        val top = MOUTH_Y + 0.008f; val bottom = fxA.liquidTop + 0.002f
        s.pos = P3(cx + 0.004f, (top + bottom) / 2, 0f); s.scale = P3(0.0035f, top - bottom, 0.0035f)
    }

    private fun setColumn(n: Node, x: Float, c: Float) {
        val h = ((c / 100f).coerceIn(0f, 1f) * (TY1 - TY0)).coerceAtLeast(0.002f)
        n.scale = P3(0.011f, h, 0.003f); n.pos = P3(x, TY0 + h / 2, BZ + 0.002f)
        val f = (c / 100f).coerceIn(0f, 1f)
        val col = if (f < 0.5f) ProcTex.okLerp(Rgb(0.35f, 0.65f, 1.0f), Rgb(1.0f, 0.75f, 0.25f), f * 2) else ProcTex.okLerp(Rgb(1.0f, 0.75f, 0.25f), Rgb(1.0f, 0.22f, 0.10f), (f - 0.5f) * 2)
        val q = Rgb((col.r * 20).roundToInt() / 20f, (col.g * 20).roundToInt() / 20f, (col.b * 20).roundToInt() / 20f)
        n.setMat(Mat.Unlit(q))
    }

    // ---------------------------------------------------------------- steam

    private fun steamStep(sdt: Float, a: Reactor?, b: Reactor?) {
        if (sdt <= 0f) return
        fun rate(r: Reactor?) = if (r == null) 0f else ((r.tC.toFloat() - 70f) / 30f).coerceIn(0f, 1f) * 7f + if (r.tK >= Peroxide.T_BOIL - 0.01 && r.rate() > 1e-3) 9f else 0f
        steamAccA += rate(a) * sdt; steamAccB += rate(b) * sdt
        while (steamAccA >= 1f) { steamAccA -= 1f; spawnSteam(fxA.steamSource()) }
        while (steamAccB >= 1f) { steamAccB -= 1f; spawnSteam(fxB.steamSource()) }
        for (k in 0 until STEAM_N) {
            if (stAge[k] < 0f) continue
            stAge[k] += sdt
            val life = stAge[k] / STEAM_LIFE
            if (life >= 1f) { stAge[k] = -1f; steam[k].visible = false; continue }
            stX[k] += stVx[k] * sdt; stZ[k] += stVz[k] * sdt; stY[k] += (0.09f + 0.05f * (1f - life)) * sdt
            val n = steam[k]
            n.pos = P3(stX[k], minOf(stY[k], 0.44f), stZ[k])
            n.uniformScale(0.05f + 0.12f * life)
            val a2 = minOf(1f, stAge[k] / 0.3f) * (1f - life)
            n.setMat(steamMats[(a2 * 5.99f).toInt().coerceIn(0, 5)])
            n.visible = true
        }
    }

    private fun spawnSteam(at: P3) {
        var k = -1; for (j in 0 until STEAM_N) if (stAge[j] < 0f) { k = j; break }
        if (k < 0) return
        stAge[k] = 0f
        stX[k] = at.x + (rnd.nextFloat() - 0.5f) * 0.05f; stY[k] = at.y + 0.02f; stZ[k] = at.z + (rnd.nextFloat() - 0.5f) * 0.05f
        stVx[k] = (rnd.nextFloat() - 0.5f) * 0.05f; stVz[k] = (rnd.nextFloat() - 0.3f) * 0.04f
    }
    private const val STEAM_LIFE = 2.4f

    // ---------------------------------------------------------------- chart

    private fun tAxis(): Float { val need = maxOf(5f, chartT * 1.05f); return T_AXES.firstOrNull { it >= need } ?: T_AXES.last() }
    private val T_AXES = floatArrayOf(5f, 10f, 20f, 30f, 60f, 120f, 180f, 300f, 600f)
    private fun vAxis(): Float {
        val m = maxOf(rA?.o2MaxL25 ?: Peroxide.gasLitres(Peroxide.moles(V_PEROX.toDouble(), wA()) / 2), rB?.o2MaxL25 ?: 0.0).toFloat()
        return V_AXES.firstOrNull { it >= m * 0.999f } ?: V_AXES.last()
    }
    private val V_AXES = floatArrayOf(0.5f, 1f, 1.5f, 2f, 3f, 4f, 5f, 6f, 8f, 10f, 12f, 15f, 20f)

    private fun writeCurve(k: Int) {
        curveDirty[k] = false
        val h = if (k < 2) histA else histB
        val r = if (k < 2) rA else rB
        val which = k % 2
        val ta = tAxis(); val va = vAxis()
        val xs = FloatArray(CURVE_N); val ys = FloatArray(CURVE_N)
        val live = if (r == null) (if (which == 0) 0f else startC) else if (which == 0) r.o2L25.toFloat() else r.tC.toFloat()
        val t0 = if (r == null) 0f else chartT
        for (i in 0 until CURVE_N) {
            val tau = minOf(ta * i / (CURVE_N - 1), t0)
            val v = if (r == null) live else h.at(tau, which, t0, live)
            xs[i] = CX0 + (CX1 - CX0) * tau / ta
            val f = if (which == 0) v / va else v / 100f
            ys[i] = CY0 + (CY1 - CY0) * f.coerceIn(0f, 1.02f)
        }
        ElephantMesh.ribbonInto(curves[k], xs, ys, BZ + 0.004f + 0.001f * k, 0.0024f)
        meshEdited(curves[k])
    }

    // ---------------------------------------------------------------- splint

    private fun resetSplint() {
        spState = SP_HOME; spT = 0f; spPos = SPLINT_HOME; spAng = SPLINT_HOME_ANGLE
        flame = 0f; litFor = 0f; relitEver = false; pokeHasO2 = false; lastPokeOk = false
    }

    private fun tipOf(pos: P3, ang: Float) = P3(pos.x - SPLINT_L / 2 * cos(ang), pos.y - SPLINT_L / 2 * sin(ang), pos.z)

    /** Is this point O2-rich: inside the foam, or in a cylinder whose air the O2 has pushed out? */
    private fun o2At(p: P3): Boolean {
        for ((fx, r) in listOf(fxA to rA, fxB to rB)) {
            if (r == null || r.nO2 <= 0.0) continue
            if (soap && fx.inFoam(p, 0.012f) && r.foamL > 0.02) return true
            val dx = p.x - fx.cx; val dz = p.z - fx.cz
            val inGlass = dx * dx + dz * dz < fx.rIn * fx.rIn && p.y > fx.liquidTop && p.y < fx.mouthY + 0.03f
            if (inGlass && (r.trappedL + r.escapedL) * 1e-3 > 0.5 * fx.freeVolume) return true
        }
        return false
    }

    /** Pinch the splint: poke it into A's foam (or B's, or into the empty cylinder: nothing). */
    fun pokeSplint() {
        if (spState == SP_HELD) return
        val target = (if (rA != null && rA!!.nO2 > 0) fxA.pokePoint() else null)
            ?: (if (rB != null && rB!!.nO2 > 0) fxB.pokePoint() else null)
        val tip = target ?: P3(XA, MOUTH_Y - 0.015f, 0f)
        fun insideGlass(cx: Float) = (tip.x - cx) * (tip.x - cx) + tip.z * tip.z < R_IN * R_IN && tip.y < MOUTH_Y + 0.01f
        val ang = if (insideGlass(XA) || insideGlass(XB)) 1.15f else 0.70f      // steep into a mouth, shallow into a pile
        pokeHasO2 = o2At(tip)
        spFrom = spPos; spAngFrom = spAng; spAngTo = ang
        spTo = P3(tip.x + SPLINT_L / 2 * cos(ang), tip.y + SPLINT_L / 2 * sin(ang), tip.z)
        spState = SP_IN; spT = 0f
    }

    private fun updateSplint(dt: Float) {
        when (spState) {
            SP_IN, SP_BACK -> {
                spT += dt
                val k = smooth((spT / 0.9f).coerceIn(0f, 1f))
                val lift = 0.12f * sin(PI.toFloat() * k)
                spPos = P3(spFrom.x + (spTo.x - spFrom.x) * k, spFrom.y + (spTo.y - spFrom.y) * k + lift, spFrom.z + (spTo.z - spFrom.z) * k)
                spAng = spAngFrom + (spAngTo - spAngFrom) * k
                if (spT >= 0.9f) { if (spState == SP_IN) { spState = SP_HOLD; spT = 0f } else { spState = SP_HOME; spPos = SPLINT_HOME; spAng = SPLINT_HOME_ANGLE } }
            }
            SP_HOLD, SP_LEFT -> { spT += dt; if (spT > 2.6f) goHome() }
            else -> {}
        }
        splint.pos = spPos
        splint.rot = Q.axisAngle(0f, 0f, 1f, spAng)
        // the ember relights in O2, keeps burning a little after, then glows again
        val tip = tipOf(spPos, spAng)
        val inO2 = o2At(tip)
        if (inO2) { flame = minOf(1f, flame + dt * 5f); litFor = 3f; relitEver = true; lastPokeOk = true }
        else if (litFor > 0f) { litFor -= dt; flame = maxOf(0.45f, flame - dt) }
        else flame = maxOf(0f, flame - dt * 1.5f)
        val t = frame * 0.016f
        val flick = 1f + 0.08f * sin(t * 23f) + 0.06f * sin(t * 37f)
        flameOuter.visible = flame > 0.02f; flameCore.visible = flame > 0.02f
        if (flame > 0.02f) {
            val s = flame * flick
            flameOuter.pos = P3(tip.x, tip.y + 0.022f * s, tip.z); flameOuter.scale = P3(0.012f * s, 0.032f * s, 0.012f * s)
            flameCore.pos = P3(tip.x, tip.y + 0.012f * s, tip.z); flameCore.scale = P3(0.0055f * s, 0.016f * s, 0.0055f * s)
        }
        val glow = if (flame > 0.02f) 1.3f else 0.85f + 0.15f * sin(t * 3.1f)
        emberGlow.uniformScale(0.016f * glow)
    }

    private fun goHome() { spFrom = spPos; spTo = SPLINT_HOME; spAngFrom = spAng; spAngTo = SPLINT_HOME_ANGLE; spState = SP_BACK; spT = 0f }

    // ---------------------------------------------------------------- text

    private fun catName(c: Catalyst) = when (c) {
        Catalyst.KI -> "KI"
        Catalyst.CATALASE -> tr("酵母（过氧化氢酶）", "yeast (catalase)")
        Catalyst.MNO2 -> "MnO₂"
        Catalyst.NONE -> tr("不加催化剂", "no catalyst")
    }
    private fun catShort(c: Catalyst) = when (c) {
        Catalyst.KI -> "KI"; Catalyst.CATALASE -> tr("酵母", "yeast"); Catalyst.MNO2 -> "MnO₂"; Catalyst.NONE -> tr("无催化剂", "no catalyst")
    }
    private fun fmtRate(r: Double) = when { r >= 0.095 -> "%.2f".format(r); r >= 1e-3 -> "%.3f".format(r); r <= 0.0 -> "0"; else -> "%.0e".format(r) }

    private fun updateReadout() {
        val a = rA; val b = rB; val c = cat()
        val line1 = if (c == Catalyst.NONE) tr("2H₂O₂ → 2H₂O + O₂↑  放热 98 kJ/mol · A：%s 双氧水，不加催化剂（活化能 75 kJ/mol）\n",
            "2H₂O₂ → 2H₂O + O₂↑, −98 kJ/mol · A: %s H₂O₂, no catalyst (Ea 75 kJ/mol)\n").format(pct(wA()))
        else tr("2H₂O₂ → 2H₂O + O₂↑  放热 98 kJ/mol · A：%s 双氧水 + %s（活化能 %d kJ/mol，不加催化剂时 75）\n",
            "2H₂O₂ → 2H₂O + O₂↑, −98 kJ/mol · A: %s H₂O₂ + %s (Ea %d kJ/mol; uncatalysed 75)\n")
            .format(pct(wA()), catName(c), (Kinetics.ea(c) / 1000).roundToInt())
        val o2Max = Peroxide.gasLitres(Peroxide.moles(V_PEROX.toDouble(), wA()) / 2)
        val line2 = if (a == null || b == null)
            tr("A、B：%d °C，捏烧杯倒入催化剂 · 最多可放出 O₂ %.1f L（25 °C）· B：%s %s\n",
               "A, B: %d °C — pinch the beaker to pour · at most %.1f L of O₂ (25 °C) · B: %s %s\n")
                .format(startC.roundToInt(), o2Max, pct(wB()), catShort(catB()))
        else {
            val boil = a.tK >= Peroxide.T_BOIL - 0.01 && a.rate() > 1e-3
            tr("A：%d °C%s · 速率 %s mol/(L·s) · O₂ %.2f / %.1f L（25 °C）· B（%s %s）：O₂ %.2f L，%d °C\n",
               "A: %d °C%s · rate %s mol/(L·s) · O₂ %.2f / %.1f L (25 °C) · B (%s %s): O₂ %.2f L, %d °C\n")
                .format(a.tC.roundToInt(), if (boil) tr(" 沸腾", " boiling") else "", fmtRate(a.rate()), a.o2L25, a.o2MaxL25,
                    pct(b.w), catShort(b.catalyst), b.o2L25, b.tC.roundToInt())
        }
        val line3 = when {
            c == Catalyst.NONE -> tr("不加催化剂：活化能 75 kJ/mol 太高，室温几乎不分解（一年 <1%）· 催化剂另开一条低能垒的路",
                "No catalyst: Ea 75 kJ/mol is too high — under 1% a year at room T · a catalyst opens a lower road")
            c == Catalyst.CATALASE && a != null && a.enzymeActive < 0.5 ->
                tr("过氧化氢酶活化能仅约 8 kJ/mol，可它是蛋白质：超过 60 °C 就变性失活（剩 %d%%）· 仅限虚拟演示",
                   "Catalase needs only ~8 kJ/mol, but it is a protein: above 60 °C it denatures (%d%% left) · virtual only")
                    .format((a.enzymeActive * 100).roundToInt())
            else -> {
                val turn = when {
                    a == null -> tr("催化剂反应后不变", "the catalyst ends unchanged")
                    c == Catalyst.KI -> tr("I⁻ 消耗 0，每个 I⁻ 已循环 %.1f 次", "I⁻ used up: 0; each I⁻ has cycled %.1f×").format(a.nO2 / a.iodideTotal)
                    c == Catalyst.CATALASE -> tr("酶消耗 0，每个酶分子已分解 %s 个 H₂O₂", "enzyme used up: 0; each molecule split %s H₂O₂").format(sci((a.n0 - a.n) / a.enzymeTotal))
                    else -> tr("MnO₂ 反应前后质量不变", "MnO₂ comes out unchanged")
                }
                tr("催化剂降低活化能、自身不消耗（%s）· 放热→升温→更快 · 仅限虚拟演示：30%% 双氧水有腐蚀性",
                   "Catalyst lowers Ea, is not used up (%s) · heat → faster · virtual only: 30%% H₂O₂ is corrosive").format(turn)
            }
        }
        readoutText = line1 + line2 + line3
    }

    private fun sci(v: Double): String {
        if (v <= 0) return "0"
        val e = kotlin.math.floor(kotlin.math.log10(v)).toInt()
        return "%.1f×10%s".format(v / Math.pow(10.0, e.toDouble()), e.toString().map { "⁰¹²³⁴⁵⁶⁷⁸⁹"[it - '0'] }.joinToString(""))
    }

    private fun updateLabels() {
        labels.clear()
        val a = rA; val b = rB
        labels += LabelSpec("A · %s + %s · %d °C".format(pct(wA()), catShort(cat()), (a?.tC ?: startC.toDouble()).roundToInt()), P3(XA, 0.445f, 0.02f))
        labels += LabelSpec(tr("B 对照 · %s + %s · %d °C", "B control · %s + %s · %d °C").format(pct(wB()), catShort(catB()), (b?.tC ?: startC.toDouble()).roundToInt()), P3(XB, 0.445f, 0.02f))
        labels += LabelSpec(tr("O₂（蓝，0–%s L）与温度（橙，0–100 °C）· 0–%d s", "O₂ (blue, 0–%s L) and T (orange, 0–100 °C) · 0–%d s")
            .format(trimNum(vAxis()), tAxis().roundToInt()), P3((CX0 + CX1) / 2 - 0.03f, BY1 + 0.035f, BZ))
        val c = cat()
        labels += LabelSpec(when (c) {
            Catalyst.KI -> tr("微观（示意）：I⁻ + H₂O₂ → IO⁻ → I⁻ + O₂\nI⁻ 消耗 0", "Micro (sketch): I⁻ + H₂O₂ → IO⁻ → I⁻ + O₂\nI⁻ used up: 0")
            Catalyst.CATALASE -> tr("微观（示意）：酶的铁 Fe → Fe=O → Fe\n酶消耗 0", "Micro (sketch): the enzyme's iron Fe → Fe=O → Fe\nenzyme used up: 0")
            Catalyst.MNO2 -> tr("微观（示意）：H₂O₂ 在 MnO₂ 表面分解\nMnO₂ 消耗 0", "Micro (sketch): H₂O₂ splits on the MnO₂ surface\nMnO₂ used up: 0")
            Catalyst.NONE -> tr("微观（示意）：没有催化剂\nH₂O₂ 分子就这样漂着", "Micro (sketch): no catalyst\nthe H₂O₂ just drifts")
        }, P3(MICRO.x, MICRO.y + MICRO_HALF + 0.06f, MICRO.z))
        labels += LabelSpec(tr("催化剂：%s", "Catalyst: %s").format(catName(c)), P3(-0.76f, DECK + 0.30f, 0.10f))
        labels += LabelSpec(when {
            flame > 0.02f -> tr("复燃！泡沫里是 O₂", "Relit! The foam is O₂")
            spState == SP_HOLD && !lastPokeOk -> tr("没有 O₂：只冒火星", "No O₂: it only glows")
            else -> tr("带火星的木条 · 检验 O₂", "Glowing splint · O₂ test")
        }, P3(SPLINT_HOME.x + 0.03f, DECK + 0.10f, SPLINT_HOME.z + 0.04f))
    }

    private fun trimNum(v: Float) = if (v == v.roundToInt().toFloat()) "%d".format(v.roundToInt()) else "%.1f".format(v)

    // ---------------------------------------------------------------- interaction

    override val tapTargets get() = listOf(
        TapTarget("Beaker", "烧杯 → 倒入", "Beaker → pour"),
        TapTarget("Cylinder", "量筒 → 重新装液", "Cylinder → refill"),
        TapTarget("Splint", "木条 → 检验 O₂", "Splint → test O₂") { flame > 0.02f },
        TapTarget("Bottle0", "KI → 选用", "KI → use") { catIdx == 0 },
        TapTarget("Bottle1", "酵母 → 选用", "Yeast → use") { catIdx == 1 },
        TapTarget("Bottle2", "MnO₂ → 选用", "MnO₂ → use") { catIdx == 2 },
        TapTarget("Bottle3", "清水 → 不加", "Water → none") { catIdx == 3 },
    )

    override fun isTarget(name: String) = name == "Beaker" || name == "Cylinder" || name == "Splint" || name.startsWith("Bottle")

    override fun onTap(name: String) {
        when (name) {
            "Beaker" -> pour()
            "Cylinder" -> refill()
            "Splint" -> pokeSplint()
            else -> name.removePrefix("Bottle").toIntOrNull()?.let { if (it in 0..3) selectCatalyst(it) }
        }
    }

    override fun tapMessage(name: String): String? = when (name) {
        "Beaker" -> when (cat()) {
            Catalyst.KI -> tr("倒入 KI：I⁻ 催化分解，%s 双氧水", "KI poured in: iodide catalysis, %s H₂O₂").format(pct(wA()))
            Catalyst.CATALASE -> tr("加酵母：过氧化氢酶比 KI 快得多", "Yeast added: catalase is far faster than KI")
            Catalyst.MNO2 -> tr("倒入 MnO₂：在固体表面催化", "MnO₂ added: catalysis on the solid's surface")
            Catalyst.NONE -> tr("只倒入清水：没有催化剂，几乎不反应", "Only water: no catalyst, almost nothing happens")
        }
        "Cylinder" -> tr("重新装液：%s 双氧水 100 mL%s", "Refilled: 100 mL of %s H₂O₂%s").format(pct(wA()), if (soap) tr(" + 洗洁精", " + dish soap") else tr("（不加洗洁精）", " (no soap)"))
        "Splint" -> if (pokeHasO2) tr("木条伸进泡沫：复燃 → 这是 O₂", "Splint into the foam: it relights → O₂") else tr("这里还没有 O₂：先倒入催化剂", "No O₂ here yet: pour the catalyst first")
        else -> when (name.removePrefix("Bottle").toIntOrNull()) {
            0 -> tr("选用 KI：碘离子，活化能 56 kJ/mol", "KI chosen: iodide, Ea 56 kJ/mol")
            1 -> tr("选用酵母：过氧化氢酶，活化能约 8 kJ/mol", "Yeast chosen: catalase, Ea ~8 kJ/mol")
            2 -> tr("选用 MnO₂：固体催化剂", "MnO₂ chosen: a solid catalyst")
            3 -> tr("不加催化剂：活化能 75 kJ/mol", "No catalyst: Ea 75 kJ/mol")
            else -> null
        }
    }

    override fun isDragTarget(name: String) = name == "Splint"
    override fun onDragStart(name: String) { spState = SP_HELD; spT = 0f; spAng = 0.70f }
    override fun onDrag(name: String, dx: Float, dy: Float, dz: Float) {
        if (spState != SP_HELD) return
        spPos = P3((spPos.x + dx).coerceIn(-0.85f, 0.85f), (spPos.y + dy).coerceIn(DECK + 0.05f, 0.42f), (spPos.z + dz).coerceIn(-0.45f, 0.55f))
    }
    override fun onDragEnd(name: String) { if (spState == SP_HELD) { spState = SP_LEFT; spT = 0f } }

    override fun reviewVariants(): Map<String, () -> Unit> = mapOf(
        "stripes" to { colours = true; refill(); pour(); repeat(220) { update(1f / 60f) }; colours = false },
        "relight" to { refill(); pour(); repeat(480) { update(1f / 60f) }; pokeSplint(); repeat(50) { update(1f / 60f) } },
        "yeast3" to { wIdx = 0; selectCatalyst(1); bMode = 1; refill(); pour(); repeat(420) { update(1f / 60f) } },
        "nosoap" to { wIdx = 2; selectCatalyst(0); bMode = 0; soap = false; refill(); pour(); repeat(200) { update(1f / 60f) }; soap = true },
    )

    @Composable
    override fun Panel() {
        var wi by FieldRef(::wIdx)
        var si by FieldRef(::speedIdx)
        var bm by FieldRef(::bMode)
        var sp by FieldRef(::soap)
        var co by FieldRef(::colours)
        var tc by FieldRef(::startC)
        LabPanel(readout) {
            SegmentControl {
                Peroxide.W.forEachIndexed { k, w -> SegmentItem(selected = wi == k, onClick = { wi = k; refill() }, title = { Text(pct(w)) }) }
            }
            SegmentControl {
                SPEED_LABELS.forEachIndexed { k, l -> SegmentItem(selected = si == k, onClick = { si = k }, title = { Text(l) }) }
            }
            SegmentControl {
                SegmentItem(selected = bm == 0, onClick = { bm = 0; refill() }, title = { Text(tr("B：不加催化剂", "B: no catalyst")) })
                SegmentItem(selected = bm == 1, onClick = { bm = 1; refill() }, title = { Text(tr("B：%s", "B: %s").format(pct(if (wIdx == 0) Peroxide.W[2] else Peroxide.W[0]))) })
            }
            LabSlider(tr("起始温度", "Start T"), tc, 5f..60f, "%d °C".format(tc.roundToInt())) {
                val v = (it / 5f).roundToInt() * 5f
                if (v != tc) { tc = v; refill() }
            }
            LabSwitch(tr("洗洁精", "Dish soap"), sp) { sp = it; refill() }
            LabSwitch(tr("食用色素", "Food colouring"), co) { switchColours(it) }
        }
    }

    private fun smooth(t: Float) = t * t * (3 - 2 * t)
}
