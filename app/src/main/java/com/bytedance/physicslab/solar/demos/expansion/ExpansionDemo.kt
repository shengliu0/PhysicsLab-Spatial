// 热胀冷缩 · 铜球与环 ('s Gravesande, 1720) — the classic ball and ring.
//
// LESSON: a cold ball slips through the ring; heated in the flame it no longer fits and sits on the
// rim; as it cools (and warms the ring) it shrinks until — suddenly — it drops through. Heating the
// RING instead makes its hole BIGGER (everything dilates, holes included). The real gap is 50 µm on a
// 25.40 mm ball, so: the apparatus is drawn 6x, every change of size is shown x20 (switchable to
// true scale), and a clearance meter reads the real gap in micrometres. The ball glows only above
// the Draper point (525 °C), with the colour and brightness Planck's law gives for its temperature.
//
// Physics: core/expansion/ExpansionCore (tabulated alpha, exact clearance, lumped heat flow,
// Planck x CIE glow). Unity audit (cold ball already wider than the hole, x400 swelling, 3800-4900 K
// glow on a <= 793 K metal, no heat exchange) is in ExpansionCore's header.

package com.bytedance.physicslab.solar.demos.expansion

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bytedance.physicslab.solar.core.P3
import com.bytedance.physicslab.solar.core.expansion.BallRing
import com.bytedance.physicslab.solar.core.expansion.Blackbody
import com.bytedance.physicslab.solar.core.expansion.Expansion
import com.bytedance.physicslab.solar.core.expansion.Metals
import com.bytedance.physicslab.solar.lab.About
import com.bytedance.physicslab.solar.lab.Bi
import com.bytedance.physicslab.solar.lab.Challenge
import com.bytedance.physicslab.solar.lab.Lesson
import com.bytedance.physicslab.solar.lab.LessonStep
import com.bytedance.physicslab.solar.lab.Q
import com.bytedance.physicslab.solar.lab.SpecDemo
import com.bytedance.physicslab.solar.lab.TapTarget
import com.bytedance.physicslab.solar.lab.Lang
import com.bytedance.physicslab.solar.lab.tr
import com.bytedance.physicslab.solar.lab.spec.Blend
import com.bytedance.physicslab.solar.lab.spec.LabelSpec
import com.bytedance.physicslab.solar.lab.spec.Mat
import com.bytedance.physicslab.solar.lab.spec.Node
import com.bytedance.physicslab.solar.lab.ui.FieldRef
import com.bytedance.physicslab.solar.lab.ui.LabPanel
import com.bytedance.physicslab.solar.lab.ui.LabRow
import com.bytedance.physicslab.solar.lab.ui.LabSwitch
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
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object ExpansionDemo : SpecDemo() {
    override val id = "expansion"
    override val title = "热胀冷缩 · 铜球与环"   // i18n-ok: Chinese title; the English one is DemoRegistry.TITLE_EN (shown via titleOf)
    override val credit = "'s Gravesande · 1720"

    // ---------------------------------------------------------------- display scale
    private const val S = 6.0                    // apparatus drawn 6x (Ø25.4 mm ball -> 152 mm)
    private const val E_SHOWN = 20.0             // every CHANGE of size shown x20 (switchable)
    private const val PLINTH_Y = -0.262f
    private const val BENCH = PLINTH_Y + 0.0215f
    private val TILT = Math.toRadians(18.0)      // ring tipped toward the viewer so its hole is seen
    private val NY = cos(TILT).toFloat(); private val NZ = sin(TILT).toFloat()   // ring axis n
    private val RC = P3(0f, 0.01f, 0f)           // ring centre
    private const val S_ABOVE = 0.20f; private const val S_BELOW = -0.16f
    private const val TRAVEL_Y = 0.205f
    private val FLAME = P3(-0.45f, 0.06f, 0f)
    private val BEAKER = P3(-0.75f, -0.150f, 0f)
    private val LAMP = P3(0.068f, BENCH, 0.062f)
    private const val ROD_LEN = 0.10f
    private const val AXIS_V = 0.20f; private const val FREE_V = 0.32f
    // instruments
    private const val BX0 = 0.37f; private const val BX1 = 0.86f; private const val BZ = -0.13f
    private const val COL_B = 0.445f; private const val COL_R = 0.545f
    private const val TY0 = -0.15f; private const val TLEN = 0.44f; private const val TMAX_C = 800f
    private const val CLR_X = 0.73f; private const val CLR_Y0 = 0.06f; private const val CLR_PER_UM = 0.20f / 300f

    private val LAPSE = floatArrayOf(1f, 10f, 30f)
    // alpha chart, largest first: aluminium, brass, copper, steel
    private const val AX0 = -0.80f; private const val AW = 0.44f; private const val AX1 = AX0 + AW; private const val AZ = -0.22f
    private val chartOrder = intArrayOf(3, 0, 1, 2)
    // panel chips (English kept short: four metal chips share the row) and the readout's wording of the same speeds
    private val LAPSE_LABELS get() = listOf(tr("实时 ×1", "Real time"), tr("延时 ×10", "×10"), tr("延时 ×30", "×30"))
    private val LAPSE_NOTES get() = listOf(tr("实时 ×1", "real time ×1"), tr("延时 ×10", "time-lapse ×10"), tr("延时 ×30", "time-lapse ×30"))

    // ---------------------------------------------------------------- UI state
    var metalIdx by mutableIntStateOf(0)
    var speedIdx by mutableIntStateOf(1)
    var auto by mutableStateOf(true)
    var exaggerate by mutableStateOf(true)
    var burnerOn by mutableStateOf(false)
    var lampOn by mutableStateOf(false)

    // ---------------------------------------------------------------- state
    private val br = BallRing(Metals.BRASS)
    private val metal get() = Metals.ALL[metalIdx]
    private fun metalName(m: com.bytedance.physicslab.solar.core.expansion.Metal) = tr(m.nameZh, m.nameEn.replaceFirstChar { it.uppercase() })
    private var builtMetal = -1
    private val e get() = if (exaggerate) E_SHOWN else 1.0

    // wand (ball + rod + handle): either on the ring axis (coordinate s) or on a free path
    private var wandPos = P3(0f, 0f, 0f)
    private var onAxis = true
    private var s = 0f
    private var axisTarget = 0f
    private var resting = 0                       // +1 sits on the rim, -1 held against it from below
    private var dropV = 0f
    private val freePath = ArrayDeque<P3>()
    private enum class Cmd { AXIS_ABOVE, AXIS_BELOW, TO_ABOVE, TO_FLAME, TO_BEAKER, HEAT, QUENCH, WAIT }
    private val queue = ArrayDeque<Cmd>()
    private var cur: Cmd? = null
    private var waitT = 0f
    private var stage = 0                         // auto cycle position (drives the caption)
    private var t = 0f
    private var frame = 0
    private var textLang = -1                     // language the readout/labels were last written in (-1 = none)

    // glow lookup (Planck x CIE is ~0.5 ms per temperature: tabulate once)
    private const val GT0 = 600; private const val GT1 = 1400; private const val GDT = 4
    private val glowTab = FloatArray((GT1 - GT0) / GDT + 1)
    private val glowG = FloatArray((GT1 - GT0) / GDT + 1)
    private var glowFor = -1

    // nodes
    private lateinit var wand: Node
    private lateinit var ball: Node
    private lateinit var halo: Node
    private lateinit var rod: Node
    private lateinit var handle: Node
    private lateinit var ring: Node
    private lateinit var flame: Array<Node>
    private lateinit var lampFlame: Array<Node>
    private lateinit var steam: Array<Node>
    private lateinit var colBall: Node
    private lateinit var colRing: Node
    private lateinit var jamMark: Node
    private lateinit var clrBar: Node
    private lateinit var clrTip: Node
    private lateinit var alphaBars: Array<Node>
    private var chartFor = -1
    private val haloMats = Array(8) { k -> Mat.Unlit(Rgb(1f, 0.22f, 0.06f), alpha = 0.03f * (k + 1), blend = Blend.ADD) }
    private val steamMats = Array(5) { k -> Mat.Unlit(Rgb(0.85f, 0.88f, 0.92f), alpha = 0.05f * (k + 1), blend = Blend.TRANSPARENT) }
    private var lastBallMatKey = -1
    private var lastColKey = IntArray(2) { -1 }
    private var lastClrSign = 0

    // ---------------------------------------------------------------- materials
    private val alu = Mat.Pbr(Rgb(0.80f, 0.82f, 0.85f), metallic = 1f, roughness = 0.30f)
    private val darkSteel = Mat.Pbr(Rgb(0.30f, 0.31f, 0.33f), metallic = 1f, roughness = 0.40f)
    private val chrome = Mat.Pbr(Rgb(0.90f, 0.91f, 0.93f), metallic = 1f, roughness = 0.14f)
    private val wood = Mat.Pbr(Rgb(0.50f, 0.31f, 0.17f), metallic = 0f, roughness = 0.55f)
    private val slabTex by lazy {
        ProcTex.bakePlanet(256, 128, Rgb(0.07f, 0.075f, 0.085f), Rgb(0.12f, 0.125f, 0.14f), Rgb(0.20f, 0.205f, 0.22f), 0f, 77)
    }
    private val slab get() = Mat.Pbr(Rgb(1f, 1f, 1f), metallic = 0f, roughness = 0.55f, tex = slabTex)
    private val board = Mat.Pbr(Rgb(0.05f, 0.06f, 0.08f), metallic = 0f, roughness = 0.08f, alpha = 0.85f)
    private val glass = Mat.Pbr(Rgb(0.80f, 0.88f, 0.95f), metallic = 0f, roughness = 0.05f, alpha = 0.20f)
    private val water = Mat.Pbr(Rgb(0.30f, 0.55f, 0.80f), metallic = 0f, roughness = 0.10f, alpha = 0.55f)
    private val tickMat = Mat.Unlit(Rgb(0.75f, 0.85f, 0.95f), alpha = 0.70f, blend = Blend.TRANSPARENT)
    private val trackMat = Mat.Unlit(Rgb(0.30f, 0.38f, 0.50f), alpha = 0.35f, blend = Blend.TRANSPARENT)
    private val draperMat = Mat.Unlit(Rgb(1.0f, 0.25f, 0.12f), alpha = 0.9f, blend = Blend.ADD)
    private val passMat = Mat.Unlit(Rgb(0.30f, 0.95f, 0.45f))
    private val jamMat = Mat.Unlit(Rgb(1.00f, 0.28f, 0.22f))

    private fun metalColour(i: Int) = when (i) {
        0 -> Rgb(0.95f, 0.77f, 0.44f)   // brass
        1 -> Rgb(0.95f, 0.60f, 0.45f)   // copper
        2 -> Rgb(0.63f, 0.65f, 0.68f)   // steel
        else -> Rgb(0.88f, 0.89f, 0.91f) // aluminium
    }

    // ---------------------------------------------------------------- display geometry (the x E rule)
    // Every diameter D is drawn as S * (D_nom + E (D - D_nom)) about the ball's nominal 25.40 mm, so
    // the drawn order of ball vs hole is EXACTLY the real order: it passes on screen iff it passes.
    private fun disp(d: Double) = (S * (BallRing.BALL_D0 + e * (d - BallRing.BALL_D0))).toFloat()
    private val ballR get() = disp(br.ballD) / 2
    private val holeR get() = disp(br.holeD) / 2
    private val holeR0 get() = disp(BallRing.HOLE_D0) / 2
    private val ringT = (S * BallRing.RING_T).toFloat()
    private fun restH(): Float { val rb = ballR; val rh = holeR; return ringT / 2 * (holeR / holeR0) + sqrt(maxOf(0f, rb * rb - rh * rh)) }
    private fun axisPoint(sv: Float) = P3(RC.x, RC.y + NY * sv, RC.z + NZ * sv)

    // ---------------------------------------------------------------- build

    override fun buildSpec(cacheDir: File): Node {
        val root = Node("ExpansionRoot")
        val unitCyl = MeshGen.cylinder(1f, 1f, 24)
        val unitBox = MeshGen.box(1f, 1f, 1f)
        val unitSphere = MeshGen.sphere(1f, 24, 48, MeshGen.SphereUv.EQUIRECT)
        frame = 0; textLang = -1; lastBallMatKey = -1; lastColKey.fill(-1); lastClrSign = 0; chartFor = -1

        root.add("Plinth", MeshGen.box(1.80f, 0.035f, 0.56f), slab, P3(0f, PLINTH_Y, -0.03f))
        // trim sits INSIDE the slab: only its 5 mm overhang shows, as an edge (it used to cover the top)
        root.add("PlinthBand", MeshGen.box(1.81f, 0.008f, 0.57f), alu, P3(0f, PLINTH_Y, -0.03f))

        // ---- ring on its stand: post behind, arm to the back of the rim
        val tilt = Q.axisAngle(1f, 0f, 0f, TILT.toFloat())
        val ringFrame = root.add(Node("RingFrame")).also { it.pos = RC; it.rot = tilt }
        val rOut = (S * BallRing.RING_OD / 2).toFloat()
        ring = ringFrame.add("Ring", ExpansionMesh.washer(holeR0, rOut, ringT, 72), Mat.Pbr(metalColour(0), metallic = 1f, roughness = 0.25f))
        val back = P3(0f, RC.y + NZ * rOut * 0.98f, RC.z - NY * rOut * 0.98f)       // back rim point (ring plane)
        val postZ = -0.215f
        root.add("StandFoot", unitCyl, darkSteel, P3(0f, BENCH + 0.008f, postZ)).also { it.scale = P3(0.055f, 0.016f, 0.055f) }
        root.add("StandPost", unitCyl, alu, P3(0f, (BENCH + back.y) / 2, postZ)).also { it.scale = P3(0.013f, back.y - BENCH, 0.013f) }
        root.add("StandArm", unitCyl, alu, P3(0f, back.y, (postZ + back.z) / 2)).also { it.scale = P3(0.010f, back.z - postZ, 0.010f); it.rot = Q.axisAngle(1f, 0f, 0f, (PI / 2).toFloat()) }
        root.add("StandClamp", unitBox, darkSteel, P3(0f, back.y, postZ)).also { it.scale = P3(0.03f, 0.03f, 0.03f) }
        root.add("RingClamp", unitBox, darkSteel, P3(0f, back.y, back.z - 0.006f)).also { it.scale = P3(0.026f, 0.034f, 0.02f); it.rot = tilt }

        // ---- the wand: ball + rod + wooden handle, held along the ring axis
        wand = root.add(Node("Wand")).also { it.rot = tilt }
        ball = wand.add("Ball", unitSphere, Mat.Pbr(metalColour(0), metallic = 1f, roughness = 0.25f))
        ball.tapRadius = 1.25f                                             // >= 75 mm world
        halo = wand.add("Halo", unitSphere, haloMats[0])
        rod = wand.add("Rod", unitCyl, chrome)
        handle = wand.add("Handle", unitCyl, wood)

        // ---- Bunsen burner (heats the ball) — gaze target
        root.add("BurnerBase", unitCyl, darkSteel, P3(FLAME.x, BENCH + 0.008f, 0f)).also { it.scale = P3(0.055f, 0.016f, 0.055f) }
        root.add("BurnerTube", unitCyl, alu, P3(FLAME.x, (BENCH + 0.016f - 0.06f) / 2, 0f)).also {
            it.scale = P3(0.019f, -0.06f - BENCH - 0.016f, 0.019f)
        }
        root.add(Node("Burner")).also { it.pos = P3(FLAME.x, (BENCH - 0.06f) / 2, 0f); it.tapRadius = 0.06f }   // collider only, uniform scale
        root.add("BurnerCollar", unitCyl, chrome, P3(FLAME.x, BENCH + 0.07f, 0f)).also { it.scale = P3(0.022f, 0.022f, 0.022f) }
        root.add("BurnerTap", unitCyl, darkSteel, P3(FLAME.x + 0.045f, BENCH + 0.03f, 0f)).also { it.scale = P3(0.0075f, 0.06f, 0.0075f); it.rot = Q.axisAngle(0f, 0f, 1f, (PI / 2).toFloat()) }
        flame = arrayOf(
            root.add("FlameOuter", unitSphere, Mat.Unlit(Rgb(0.30f, 0.40f, 1.00f), alpha = 0.17f, blend = Blend.ADD), P3(FLAME.x, 0.02f, 0f)),
            root.add("FlameCone", unitSphere, Mat.Unlit(Rgb(0.45f, 0.80f, 1.00f), alpha = 0.75f, blend = Blend.ADD), P3(FLAME.x, -0.025f, 0f)),
            root.add("FlameTip", unitSphere, Mat.Unlit(Rgb(1.00f, 0.55f, 0.20f), alpha = 0.30f, blend = Blend.ADD), P3(FLAME.x, 0.085f, 0f)),
        )

        // gas hose: orange lab rubber from the burner tap, sagging to the back of the bench
        val hose = FloatArray(24 * 3) { k ->
            val u = (k / 3) / 23f
            when (k % 3) { 0 -> FLAME.x + 0.075f + 0.12f * u; 1 -> BENCH + 0.03f - 0.022f * sin(PI.toFloat() * u) * (1 - u) + 0.006f * u; else -> -0.24f * u }
        }
        root.add("Hose", MeshGen.tube(hose, 0.0065f, 10, false), Mat.Pbr(Rgb(0.80f, 0.36f, 0.16f), metallic = 0f, roughness = 0.45f))

        // ---- alcohol lamp under the ring's front-right rim (heats the RING) — gaze target
        root.add("LampBody", unitSphere, glass, P3(LAMP.x, BENCH + 0.035f, LAMP.z)).also { it.scale = P3(0.042f, 0.036f, 0.042f) }
        root.add(Node("Lamp")).also { it.pos = P3(LAMP.x, BENCH + 0.05f, LAMP.z); it.tapRadius = 0.05f }
        root.add("LampFuel", unitSphere, Mat.Pbr(Rgb(0.55f, 0.75f, 0.95f), alpha = 0.6f, roughness = 0.1f), P3(LAMP.x, BENCH + 0.025f, LAMP.z)).also { it.scale = P3(0.036f, 0.022f, 0.036f) }
        root.add("LampNeck", unitCyl, chrome, P3(LAMP.x, BENCH + 0.078f, LAMP.z)).also { it.scale = P3(0.010f, 0.016f, 0.010f) }
        lampFlame = arrayOf(
            root.add("LampFlame", unitSphere, Mat.Unlit(Rgb(0.35f, 0.55f, 1.00f), alpha = 0.45f, blend = Blend.ADD), P3(LAMP.x, BENCH + 0.125f, LAMP.z)),
            root.add("LampFlameCore", unitSphere, Mat.Unlit(Rgb(1.00f, 0.75f, 0.35f), alpha = 0.35f, blend = Blend.ADD), P3(LAMP.x, BENCH + 0.105f, LAMP.z)),
        )

        // ---- water beaker for quenching
        root.add("Beaker", unitCyl, glass, P3(BEAKER.x, BENCH + 0.105f, 0f)).also { it.scale = P3(0.100f, 0.21f, 0.100f) }
        root.add("Water", unitCyl, water, P3(BEAKER.x, BENCH + 0.0905f, 0f)).also { it.scale = P3(0.096f, 0.177f, 0.096f) }
        steam = Array(6) { k -> root.add("Steam$k", unitSphere, steamMats[0]).also { it.visible = false } }

        // ---- instrument board: two thermometers (ball, ring), the Draper line, the clearance meter
        root.add("Board", MeshGen.box(BX1 - BX0, 0.60f, 0.012f), board, P3((BX0 + BX1) / 2, 0.07f, BZ - 0.012f))
        for (x in floatArrayOf(COL_B, COL_R)) {
            root.add("Track", unitBox, trackMat, P3(x, TY0 + TLEN / 2, BZ)).also { it.scale = P3(0.034f, TLEN, 0.004f) }
            for (c in 0..8) {
                val y = tY(c * 100f); val major = c % 2 == 0
                root.add("Tick", seg(x - 0.017f - if (major) 0.016f else 0.008f, y, x - 0.017f, y, BZ + 0.002f, 0.0013f), tickMat)
            }
        }
        colBall = root.add("ColBall", unitBox, Mat.Unlit(Rgb(1f, 0.6f, 0.2f)), P3(COL_B, TY0, BZ + 0.004f))
        colRing = root.add("ColRing", unitBox, Mat.Unlit(Rgb(1f, 0.6f, 0.2f)), P3(COL_R, TY0, BZ + 0.004f))
        root.add("Draper", seg(COL_B - 0.05f, tY(525f), COL_R + 0.03f, tY(525f), BZ + 0.008f, 0.0022f), draperMat)
        jamMark = root.add("JamMark", unitBox, Mat.Unlit(Rgb(1f, 1f, 1f)), P3(COL_B + 0.026f, tY(124f), BZ + 0.006f)).also { it.scale = P3(0.018f, 0.006f, 0.004f) }
        // clearance meter: green zone above zero (passes), red below (jammed)
        root.add("ClrPass", unitBox, Mat.Unlit(Rgb(0.30f, 0.95f, 0.45f), alpha = 0.12f, blend = Blend.TRANSPARENT), P3(CLR_X, CLR_Y0 + 0.10f, BZ)).also { it.scale = P3(0.07f, 0.20f, 0.004f) }
        root.add("ClrJam", unitBox, Mat.Unlit(Rgb(1.0f, 0.30f, 0.25f), alpha = 0.12f, blend = Blend.TRANSPARENT), P3(CLR_X, CLR_Y0 - 0.10f, BZ)).also { it.scale = P3(0.07f, 0.20f, 0.004f) }
        root.add("ClrZero", seg(CLR_X - 0.05f, CLR_Y0, CLR_X + 0.05f, CLR_Y0, BZ + 0.008f, 0.0022f), Mat.Unlit(Rgb(0.95f, 0.97f, 1f)))
        for (k in -3..3) if (k != 0) root.add("ClrTick", seg(CLR_X + 0.036f, CLR_Y0 + k * 100 * CLR_PER_UM, CLR_X + 0.05f, CLR_Y0 + k * 100 * CLR_PER_UM, BZ + 0.004f, 0.0012f), tickMat)
        clrBar = root.add("ClrBar", unitBox, passMat, P3(CLR_X, CLR_Y0, BZ + 0.005f))
        clrTip = root.add("ClrTip", unitBox, Mat.Unlit(Rgb(1f, 1f, 1f)), P3(CLR_X, CLR_Y0, BZ + 0.009f)).also { it.scale = P3(0.05f, 0.005f, 0.004f) }

        // ---- alpha chart (back left): bar length = tabulated alpha, the chosen metal lit
        root.add("ChartBoard", MeshGen.box(AX1 - AX0 + 0.08f, 0.27f, 0.012f), board, P3((AX0 + AX1) / 2 + 0.02f, 0.265f, AZ - 0.012f))
        root.add("ChartAxis", seg(AX0, 0.145f, AX0, 0.385f, AZ + 0.002f, 0.0016f), tickMat)
        for (a in 5..20 step 5) { val x = AX0 + AW * a / 23.1f; root.add("ChartGrid", seg(x, 0.145f, x, 0.385f, AZ - 0.004f, 0.0008f), trackMat) }
        alphaBars = Array(Metals.ALL.size) { k ->
            val y = 0.355f - k * 0.063f
            root.add("ChartDot$k", unitSphere, Mat.Pbr(metalColour(chartOrder[k]), metallic = 1f, roughness = 0.25f), P3(AX0 - 0.03f, y, AZ + 0.01f)).uniformScale(0.016f)
            val len = AW * (Metals.ALL[chartOrder[k]].alpha / Metals.ALUMINIUM.alpha).toFloat()
            root.add("AlphaBar$k", unitBox, Mat.Unlit(metalColour(chartOrder[k])), P3(AX0 + len / 2, y, AZ + 0.004f)).also { it.scale = P3(len, 0.030f, 0.006f) }
        }

        restart()
        update(0f)
        return root
    }

    /** The hero moment: a glowing-hot ball sitting on a room-temperature ring. */
    fun restart() {
        br.metal = metal; builtMetal = metalIdx
        br.reset(273.15 + metal.heatTargetC, Expansion.T0)
        br.flameOnBall = false; br.flameOnRing = false; br.quench = false; br.contact = false
        burnerOn = false; lampOn = false
        queue.clear(); freePath.clear(); cur = null
        onAxis = true; resting = 0; dropV = 0f
        s = restH(); axisTarget = S_BELOW; wandPos = axisPoint(s)
        cur = Cmd.AXIS_BELOW; stage = 0; t = 0f
        if (auto) queue.addAll(CYCLE.drop(1))
        lastBallMatKey = -1; lastColKey.fill(-1); glowFor = -1
    }

    private val CYCLE = listOf(
        Cmd.AXIS_BELOW, Cmd.WAIT, Cmd.AXIS_ABOVE, Cmd.TO_BEAKER, Cmd.QUENCH,
        Cmd.TO_ABOVE, Cmd.AXIS_BELOW, Cmd.WAIT, Cmd.AXIS_ABOVE, Cmd.TO_FLAME, Cmd.HEAT, Cmd.TO_ABOVE)

    // ---------------------------------------------------------------- per frame

    override fun update(dt: Float) {
        if (builtMetal != metalIdx) restart()
        val lapse = LAPSE[speedIdx]
        t += dt
        // heat flows for where the ball is now, then the mechanics react to the new sizes
        val atFlame = !onAxis && freePath.isEmpty() && dist(wandPos, FLAME) < 0.01f
        val atBeaker = !onAxis && freePath.isEmpty() && dist(wandPos, BEAKER) < 0.01f
        br.flameOnBall = burnerOn && atFlame
        br.flameOnRing = lampOn
        br.quench = atBeaker
        br.contact = resting != 0
        br.step((dt * lapse).toDouble())
        // safety cut-offs (aluminium would melt in the flame: equilibrium 939 °C > 660 °C)
        if (burnerOn && br.tBall >= 273.15 + metal.heatTargetC) burnerOn = false
        if (lampOn && br.tRing >= 273.15 + 600.0) lampOn = false
        runCommands(dt)
        sync(dt)
    }

    private fun runCommands(dt: Float) {
        var c = cur
        if (c == null) { c = queue.removeFirstOrNull() ?: return; cur = c; begin(c); c = cur ?: return }
        val done = when (c) {
            Cmd.AXIS_ABOVE, Cmd.AXIS_BELOW -> axisStep(dt)
            Cmd.TO_ABOVE, Cmd.TO_FLAME, Cmd.TO_BEAKER -> freeStep(dt)
            Cmd.HEAT -> { if (!burnerOn) true else false }
            Cmd.QUENCH -> br.tBall < 273.15 + 32
            Cmd.WAIT -> { waitT -= dt; waitT <= 0 }
        }
        if (done) {
            cur = null
            if (auto && queue.isEmpty()) queue.addAll(CYCLE)
            if (auto) stage = (stage + 1) % CYCLE.size
        }
    }

    private fun begin(c: Cmd) {
        when (c) {
            Cmd.AXIS_ABOVE, Cmd.AXIS_BELOW -> {
                if (!onAxis) { queue.addFirst(c); queue.addFirst(Cmd.TO_ABOVE); cur = null; return }
                axisTarget = if (c == Cmd.AXIS_ABOVE) S_ABOVE else S_BELOW; dropV = 0f
            }
            Cmd.TO_ABOVE -> planFree(axisPoint(S_ABOVE))
            Cmd.TO_FLAME -> planFree(FLAME)
            Cmd.TO_BEAKER -> planFree(BEAKER)
            Cmd.HEAT -> burnerOn = true
            Cmd.QUENCH -> {}
            Cmd.WAIT -> waitT = 1.6f
        }
    }

    /** Lift to the travel height, cross, descend. Leaving the axis is only allowed from above the ring. */
    private fun planFree(target: P3) {
        freePath.clear()
        if (onAxis && s < S_ABOVE - 0.001f) { queue.addFirst(cur!!); queue.addFirst(Cmd.AXIS_ABOVE); cur = null; return }
        onAxis = false
        freePath.addLast(P3(wandPos.x, TRAVEL_Y, wandPos.z))
        freePath.addLast(P3(target.x, TRAVEL_Y, target.z))
        freePath.addLast(target)
    }

    private fun freeStep(dt: Float): Boolean {
        var budget = FREE_V * dt
        while (budget > 0 && freePath.isNotEmpty()) {
            val tgt = freePath.first(); val d = dist(wandPos, tgt)
            if (d <= budget) { wandPos = tgt; freePath.removeFirst(); budget -= d }
            else { val k = budget / d; wandPos = P3(wandPos.x + (tgt.x - wandPos.x) * k, wandPos.y + (tgt.y - wandPos.y) * k, wandPos.z + (tgt.z - wandPos.z) * k); budget = 0f }
        }
        if (freePath.isEmpty()) {
            // arriving above the ring puts the wand back on the axis
            val a = axisPoint(S_ABOVE)
            if (dist(wandPos, a) < 1e-4f) { onAxis = true; s = S_ABOVE }
            return true
        }
        return false
    }

    /** Move along the ring axis; the ring stops a ball that does not fit and lets it go the moment it does. */
    private fun axisStep(dt: Float): Boolean {
        val dir = if (axisTarget > s) 1f else -1f
        if (resting != 0) {
            if (br.passes) { resting = 0; dropV = if (dir < 0) 0.05f else 0f }   // it slips through
            else { s = resting * restH(); wandPos = axisPoint(s); return false }
        }
        val v = if (dropV > 0f) { dropV = minOf(1.2f, dropV + 4f * dt); dropV } else AXIS_V
        var next = s + dir * v * dt
        if ((dir > 0 && next > axisTarget) || (dir < 0 && next < axisTarget)) next = axisTarget
        if (!br.passes) {
            val h = restH()
            if (dir < 0 && s >= h - 1e-5f && next < h) { s = h; resting = 1; wandPos = axisPoint(s); return false }
            if (dir > 0 && s <= -h + 1e-5f && next > -h) { s = -h; resting = -1; wandPos = axisPoint(s); return false }
        }
        if (s > 0f && next <= 0f) { throughs++; throughC = ballC }      // the ball's centre went down through the ring
        s = next; wandPos = axisPoint(s)
        if (s == axisTarget) { dropV = 0f; return true }
        return false
    }

    private fun sync(dt: Float) {
        // wand: ball (display size), glow, rod, handle
        val rb = ballR
        wand.pos = wandPos
        ball.uniformScale(rb)
        rod.pos = P3(0f, rb + ROD_LEN / 2, 0f); rod.scale = P3(0.0055f, ROD_LEN, 0.0055f)
        handle.pos = P3(0f, rb + ROD_LEN + 0.025f, 0f); handle.scale = P3(0.015f, 0.05f, 0.015f)
        ring.uniformScale(holeR / holeR0)

        // incandescence from the ball's real temperature (Planck x CIE, invisible below 798 K)
        ensureGlowTable()
        val g = glowAt(br.tBall)
        val key = (g * 40).roundToInt() * 4 + metalIdx
        if (key != lastBallMatKey) {
            lastBallMatKey = key
            val gi = glowIndex(br.tBall)
            val chroma = Rgb(1f, glowG[gi], 0f)
            val base = ProcTex.okLerp(metalColour(metalIdx), Rgb(0.22f, 0.15f, 0.12f), 0.65f * g)
            ball.setMat(Mat.Pbr(base, metallic = 1f - 0.5f * g, roughness = 0.25f + 0.3f * g,
                emissive = if (g > 0f) Rgb(chroma.r * g, chroma.g * g, 0f) else null))
            ring.setMat(Mat.Pbr(metalColour(metalIdx), metallic = 1f, roughness = 0.25f))
        }
        halo.visible = g > 0.02f
        if (halo.visible) { halo.uniformScale(rb * (1.035f + 0.04f * g)); halo.setMat(haloMats[minOf(7, (g * 8).toInt())]) }

        // flames
        val fl = 1f + 0.06f * sin(t * 17f) + 0.04f * sin(t * 26.3f)
        for (n in flame) n.visible = burnerOn
        if (burnerOn) {
            // with a ball in it the flame spreads round the ball's lower half (drawn additively, so it shows around it)
            val wrap = if (!onAxis && freePath.isEmpty() && dist(wandPos, FLAME) < 0.01f) 1f else 0f
            flame[0].scale = P3(0.032f + 0.055f * wrap, (0.085f + 0.02f * wrap) * fl, 0.032f + 0.055f * wrap)
            flame[1].scale = P3(0.016f, 0.040f * fl, 0.016f)
            flame[2].scale = P3(0.022f + 0.05f * wrap, 0.03f * fl, 0.022f + 0.05f * wrap)
        }
        for (n in lampFlame) n.visible = lampOn
        if (lampOn) { lampFlame[0].scale = P3(0.016f, 0.045f * fl, 0.016f); lampFlame[1].scale = P3(0.008f, 0.02f * fl, 0.008f) }

        // steam while a hot ball is in the water
        val steaming = br.quench && br.tBall > 373.15
        for ((k, n) in steam.withIndex()) {
            n.visible = steaming
            if (!steaming) continue
            val ph = ((t * 0.7f + k / 6f) % 1f)
            n.pos = P3(BEAKER.x + 0.03f * sin(k * 2.1f + t), BENCH + 0.22f + ph * 0.20f, 0.02f * cos(k * 1.7f))
            n.uniformScale(0.02f + 0.035f * ph)
            n.setMat(steamMats[minOf(4, ((1f - ph) * 5).toInt())])
        }

        // the alpha chart: the chosen metal at full brightness, the others dimmed
        if (chartFor != metalIdx) {
            chartFor = metalIdx
            for ((k, n) in alphaBars.withIndex()) {
                val c = metalColour(chartOrder[k]); val on = chartOrder[k] == metalIdx
                n.setMat(if (on) Mat.Unlit(c) else Mat.Unlit(Rgb(c.r * 0.35f, c.g * 0.35f, c.b * 0.35f)))
            }
        }

        // instruments
        val cb = (br.tBall - 273.15).toFloat(); val cr = (br.tRing - 273.15).toFloat()
        setColumn(colBall, COL_B, cb, 0); setColumn(colRing, COL_R, cr, 1)
        jamMark.pos = P3(COL_B + 0.026f, tY((br.jamT - 273.15).toFloat()), BZ + 0.006f)
        val um = (br.clearance * 1e6).toFloat()
        val h = (um * CLR_PER_UM).coerceIn(-0.205f, 0.205f)
        clrBar.scale = P3(0.034f, maxOf(0.002f, abs(h)), 0.006f); clrBar.pos = P3(CLR_X, CLR_Y0 + h / 2, BZ + 0.005f)
        clrTip.pos = P3(CLR_X, CLR_Y0 + h, BZ + 0.009f)
        val sign = if (um >= 0) 1 else -1
        if (sign != lastClrSign) { lastClrSign = sign; clrBar.setMat(if (sign > 0) passMat else jamMat) }

        // text at 10 Hz, and at once when the display language flips (the panel already follows it)
        val lang = if (Lang.en) 1 else 0
        if (frame++ % 6 == 0 || lang != textLang) { textLang = lang; updateReadout(cb, cr, um, g); updateLabels(cb, cr, um) }
    }

    private fun setColumn(n: Node, x: Float, c: Float, k: Int) {
        val h = (tY(c) - TY0).coerceIn(0.003f, TLEN)
        n.scale = P3(0.026f, h, 0.004f); n.pos = P3(x, TY0 + h / 2, BZ + 0.004f)
        val key = (c / 25f).roundToInt()
        if (key != lastColKey[k]) { lastColKey[k] = key; n.setMat(Mat.Unlit(tempColour(c))) }
    }

    /** Thermometer fill: cool steel-blue at room temperature -> amber -> red-hot. */
    private fun tempColour(c: Float): Rgb {
        val f = (c / 700f).coerceIn(0f, 1f)
        return if (f < 0.5f) ProcTex.okLerp(Rgb(0.35f, 0.65f, 1.0f), Rgb(1.0f, 0.75f, 0.25f), f * 2)
        else ProcTex.okLerp(Rgb(1.0f, 0.75f, 0.25f), Rgb(1.0f, 0.22f, 0.10f), (f - 0.5f) * 2)
    }

    private fun caption(): String = when {
        resting > 0 -> tr("热球卡在环上：球在冷却，环在变热", "Hot ball stuck on the ring: ball cooling, ring warming")
        resting < 0 -> tr("环冷得更快，孔变小，球从下面被卡住", "Ring cools faster, hole shrinks: ball jammed from below")
        else -> when (cur) {
            Cmd.AXIS_BELOW -> if (br.tBall > 273.15 + 60) tr("间隙重新为正 → 穿过！", "Clearance positive again → through!") else tr("冷球：轻松穿过圆环", "Cold ball: slips through the ring")
            Cmd.WAIT -> tr("穿过了", "Through!")
            Cmd.TO_BEAKER, Cmd.QUENCH -> tr("放进水里冷却到室温", "Quench in water to room temperature")
            Cmd.TO_FLAME -> tr("放进火焰加热", "Into the flame to heat")
            Cmd.HEAT -> tr("火焰加热中", "Heating in the flame")
            Cmd.TO_ABOVE -> if (br.tBall > 273.15 + 200) tr("把热球放回环上……", "Hot ball back onto the ring…") else tr("把冷球放到环上", "Cold ball onto the ring")
            else -> if (lampOn) tr("加热圆环：孔变大", "Heating the ring: the hole grows") else tr("注视铜球捏合：试着穿过圆环", "Gaze at the ball and pinch: try the ring")
        }
    }

    private fun updateReadout(cb: Float, cr: Float, um: Float, g: Float) {
        val m = metal
        val glowNote = when {
            g > 0f -> tr("%d °C：%s（普朗克黑体光）", "%d °C: %s (Planck blackbody glow)").format(cb.roundToInt(),
                if (g > 0.6f) tr("明亮的樱桃红", "bright cherry red") else tr("暗红光", "dull red"))
            cb > 300f -> tr("%d °C 很烫却不发光：525 °C 以上才看得见", "%d °C: very hot, yet no glow until 525 °C").format(cb.roundToInt())
            else -> tr("525 °C（德雷珀点）以上金属才发出可见红光", "Metal glows red only above 525 °C (Draper point)")
        }
        readoutText = tr("热胀冷缩 ΔD = α·D·ΔT：热球卡在环上，冷却后穿过；加热圆环，孔也一起变大\n",
                "Thermal expansion ΔD = α·D·ΔT: a hot ball sticks, passes once cool; a heated ring's hole grows\n") +
            tr("%s α=%.1f×10⁻⁶/K   球 %d °C Ø%.3f mm   环 %d °C 孔 Ø%.3f mm   间隙 %+d µm %s\n",
                "%s α=%.1f×10⁻⁶/K  ball %d °C Ø%.3f mm  ring %d °C hole Ø%.3f mm  gap %+d µm %s\n").format(
                metalName(m), m.alpha * 1e6, cb.roundToInt(), br.ballD * 1000, cr.roundToInt(), br.holeD * 1000, um.roundToInt(),
                if (um >= 0) tr("能通过", "passes") else tr("卡住", "stuck")) +
            tr("%s   ·   球超过 %d °C 就过不去   ·   %s", "%s  ·  ball jams above %d °C  ·  %s").format(glowNote, (br.jamT - 273.15).roundToInt(), LAPSE_NOTES[speedIdx])
    }

    private fun updateLabels(cb: Float, cr: Float, um: Float) {
        labels.clear()
        labels += LabelSpec(caption(), P3(-0.17f, 0.36f, 0.08f))
        labels += LabelSpec(tr("球 %d °C   环 %d °C", "Ball %d °C   Ring %d °C").format(cb.roundToInt(), cr.roundToInt()), P3((COL_B + COL_R) / 2, TY0 + TLEN + 0.06f, BZ + 0.02f))
        labels += LabelSpec(tr("间隙 %+d µm %s", "Clearance %+d µm: %s").format(um.roundToInt(), if (um >= 0) tr("能过", "passes") else tr("卡住", "stuck")), P3(CLR_X, CLR_Y0 + 0.25f, BZ + 0.02f))
        labels += LabelSpec(tr("525 °C 开始发红光", "Glows red from 525 °C"), P3(COL_B - 0.02f, tY(525f) + 0.035f, BZ + 0.03f))
        labels += LabelSpec(if (exaggerate) tr("Ø25.4 mm 球放大 6×；尺寸变化再 ×20", "Ø25.4 mm ball drawn 6×; size changes a further ×20")
            else tr("Ø25.4 mm 球放大 6×；真实比例", "Ø25.4 mm ball drawn 6×; changes at true scale"), P3(-0.2f, BENCH + 0.03f, 0.27f))
        labels += LabelSpec(tr("线膨胀系数 α：铝 23 · 黄铜 19 · 铜 16.5 · 钢 12（×10⁻⁶/K）",
            "Linear expansion coefficient α (×10⁻⁶/K)\naluminium 23 · brass 19 · copper 16.5 · steel 12"), P3((AX0 + AX0 + AW) / 2, 0.43f, AZ))
    }

    // ---------------------------------------------------------------- observability (tests)

    /** Current command, rest state (+1 on the rim, -1 under it, 0 free), axis coordinate, temperatures, gap. */
    val command get() = cur?.name ?: "IDLE"
    val restState get() = resting
    val axisCoord get() = if (onAxis) s else Float.NaN
    val ballC get() = br.tBall - 273.15
    val ringC get() = br.tRing - 273.15
    val clearanceUm get() = br.clearance * 1e6
    val glowNow get() = glowAt(br.tBall)

    // ---------------------------------------------------------------- interaction

    override val tapTargets get() = listOf(
        TapTarget("Ball", "铜球 → 试穿圆环", "Ball → try the ring"),
        TapTarget("Burner", "本生灯 → 烧铜球", "Burner → heat ball") { burnerOn },
        TapTarget("Lamp", "酒精灯 → 烧圆环", "Spirit lamp → heat ring") { lampOn },
    )

    override fun isTarget(name: String) = name == "Ball" || name == "Burner" || name == "Lamp"

    // toggles: the note says the NEW state (the generic "✓ label" would be wrong when switching off)
    override fun tapMessage(name: String): String? = when (name) {
        "Burner" -> if (burnerOn) tr("本生灯点燃：铜球升温膨胀", "Burner on: the ball heats up and grows") else tr("本生灯熄灭", "Burner off")
        "Lamp" -> if (lampOn) tr("酒精灯点燃：圆环升温，孔也变大", "Lamp on: the ring heats up — its hole grows too") else tr("酒精灯熄灭", "Lamp off")
        else -> null
    }

    override fun onTap(name: String) {
        when (name) {
            "Ball" -> tryRing()
            "Burner" -> heatBall()
            "Lamp" -> { manual(); lampOn = !lampOn; if (lampOn) lampLits++ }
        }
    }

    private fun manual() { auto = false; queue.clear() }

    /** Lower the ball onto/through the ring — or lift it back if it is already down. */
    fun tryRing() {
        manual()
        val wasResting = resting
        cur = null; resting = 0; burnerOn = false; dropV = 0f
        if (onAxis && (s <= 0f || wasResting > 0)) queue.addLast(Cmd.AXIS_ABOVE)
        else { if (!onAxis) queue.addLast(Cmd.TO_ABOVE); queue.addLast(Cmd.AXIS_BELOW) }
    }

    fun heatBall() {
        manual()
        if (burnerOn) { burnerOn = false; cur = null; return }
        burns++
        cur = null; resting = 0
        queue.addLast(Cmd.TO_FLAME); queue.addLast(Cmd.HEAT)
    }

    fun quench() { quenches++; manual(); cur = null; resting = 0; burnerOn = false; queue.addLast(Cmd.TO_BEAKER); queue.addLast(Cmd.QUENCH) }

    // ---------------------------------------------------------------- guided track (导学)
    // Key insight: heat dilates EVERYTHING by the same factor 1 + αΔT — holes included. A heated ring's hole
    // gets BIGGER (the classic wrong guess: "the ring swells inward and the hole shrinks"). The real numbers
    // are tiny (brass α = 19×10⁻⁶/K: a 50 µm gap on Ø25.4 mm closes ~104 K above the ring), which is why the
    // clearance meter reads micrometres. Challenge = apply it: pass a red-hot ball by heating the RING. At the default
    // x10 time-lapse a ball on the ring cools ~25 K/s and drops by itself at ~290 °C: the challenge asks for > 350 °C
    // (needs the lamp) and starts the moment the ball sticks, so there is time to act.

    /** Event counters: user quenches, lamp lightings, burner heatings, ball passages down through the ring. */
    internal var quenches = 0
    internal var lampLits = 0
    internal var burns = 0
    internal var throughs = 0
    /** Ball temperature (°C) at the last passage. */
    internal var throughC = 0.0
    internal val holeUm get() = br.holeD * 1e6
    private var countAtStep = 0
    private var holeAtStep = 0.0

    // ---- background card (📖): context for a viewer who has never met this topic
    override val about = About(listOf(
        About.Page(
            Bi("这个实验在做什么？", "What is this experiment?"),
            Bi("台上有一个直径 25.4 mm 的铜球和一个圆环，冷的时候球刚好能穿过，只差 50 µm。注视铜球捏合，让它去穿圆环；捏本生灯烧球，捏酒精灯烧环，面板「水冷」把球泡进水里。右边的间隙表显示真实间隙，正数能过、负数卡住。尺寸变化默认放大 20 倍，可关掉看真实比例。", "A 25.4 mm brass ball and a ring it just fits through when cold — only 50 µm to spare. Gaze at the ball and pinch to try the ring; pinch the Bunsen burner to heat the ball, the spirit lamp to heat the ring, or tap “Quench” to dunk the ball in water. The gap meter on the right shows the real clearance: positive passes, negative jams. Size changes are drawn 20× larger; switch that off for true scale."),
        ),
        About.Page(
            Bi("一个老课堂演示", "An old classroom demo"),
            Bi("1720 年前后，荷兰物理学家斯赫拉弗桑德用这套球和环给学生演示：东西受热会变大。今天听来理所当然，当时却很要紧：温度计靠液体受热膨胀来读数；摆钟的摆杆夏天变长，钟就走慢。工匠必须知道金属到底会胀多少。这套演示后来进了几乎所有的物理课堂。", "Around 1720 the Dutch physicist 's Gravesande used this ball and ring to show students that things grow when heated. Obvious today, it mattered then: thermometers read the swelling of a liquid, and a pendulum clock runs slow in summer because its rod gets longer. Craftsmen needed to know how much metal grows. The demo has been in physics classrooms ever since."),
        ),
        About.Page(
            Bi("原子抖得更凶", "Atoms jiggle harder"),
            Bi("金属里的原子一直在原地抖动，加热后抖得更凶，彼此要多占一点地方，整块金属就均匀胀大：ΔD = α·D·ΔT。最容易想错的是圆环：孔不会被“挤小”，而是跟着变大——就像把照片整体放大，照片上的洞也一起变大。黄铜烧到 700 °C，直径只多 0.33 mm，却已远大于 50 µm 的间隙。", "Atoms in a metal are always jiggling in place. Heat them and they jiggle harder and need a little more room, so the whole piece grows evenly: ΔD = α·D·ΔT. The trap is the ring: its hole does not get squeezed smaller — it grows, like enlarging a photo, hole and all. Brass at 700 °C is only 0.33 mm wider, but that is far more than the 50 µm gap."),
        ),
        About.Page(
            Bi("今天它在哪里？", "Where is it today?"),
            Bi("铁轨接头留缝，桥面上有伸缩缝，都是给夏天的膨胀留余地。工厂装齿轮时，常把齿轮加热后套上轴，冷却后就紧紧抱住，这叫热装。老式恒温器里的双金属片，两种金属胀得不一样多，受热就弯，去推开关。拧不开的罐头铁盖，用热水冲一冲就松了，也是这个道理。", "Gaps between rails and the expansion joints in a bridge leave room for summer growth. Factories often heat a gear and slide it onto a shaft; as it cools it grips tight — a shrink fit. In an old thermostat, a bimetal strip of two metals that grow by different amounts bends when warm and flips a switch. A stuck metal lid on a jar loosens under hot water for the same reason."),
        ),
    ))

    override val lesson: Lesson by lazy {
        Lesson(
            listOf(
                LessonStep(
                    say = Bi("刚烧过的铜球卡在环上了（右边间隙表是负的）。点面板「水冷」，把它冷却到室温。", "The flame-hot ball is stuck on the ring (the gap meter on the right is negative). Tap “Quench” to cool it to room temperature."),
                    onEnter = { countAtStep = quenches },
                    done = { quenches > countAtStep && ballC < 40.0 },
                    autoplay = { quench() },
                    insight = Bi("冷了：球缩回 Ø25.40\u00A0mm，比孔（Ø25.45\u00A0mm）只小 50\u00A0µm，不到一根头发粗。", "Cold again: the ball is back to Ø25.40\u00A0mm, just 50\u00A0µm under the Ø25.45\u00A0mm hole — less than a hair."),
                ),
                LessonStep(
                    say = Bi("接下来用酒精灯只加热圆环。环受热膨胀，中间的孔会？", "Next, the spirit lamp heats only the ring. The ring expands — so its hole will…"),
                    options = listOf(
                        Bi("变小（往里胀）", "Shrinks"),
                        Bi("变大", "Grows"),
                        Bi("不变", "Same"),
                    ),
                    answer = 1,
                ),
                LessonStep(
                    say = Bi("捏酒精灯（或面板「酒精灯」）点火，盯住圆环和右边的间隙表。", "Pinch the spirit lamp (or “Spirit lamp” below) and watch the ring and the gap meter on the right."),
                    target = "Lamp",
                    onEnter = { countAtStep = lampLits; holeAtStep = holeUm },
                    done = { lampLits > countAtStep && holeUm - holeAtStep >= 100.0 },
                    autoplay = { if (!lampOn) onTap("Lamp") },
                    reveal = true,
                    insight = Bi("孔变大了！受热的环像照片放大一样整体变大，孔也跟着变大：每热 100\u00A0°C 大 48\u00A0µm。", "The hole grows! A heated ring enlarges like a photo, hole included: +48\u00A0µm per 100\u00A0°C."),
                ),
                LessonStep(
                    say = Bi("捏本生灯（或面板「本生灯」），把铜球烧到发红，到温会自动熄火。", "Pinch the Bunsen burner (or “Burner” below) to heat the ball red-hot; it switches off by itself."),
                    target = "Burner",
                    onEnter = { countAtStep = burns },
                    done = { burns > countAtStep && ballC >= minOf(500.0, metal.heatTargetC - 30) },   // still heating while the insight shows
                    autoplay = { heatBall() },
                    insight = Bi("烧到 700\u00A0°C 时，黄铜球直径会胀大 0.33\u00A0mm（ΔD = α·D·ΔT）—— 比孔大多了。", "At 700\u00A0°C the brass ball grows 0.33\u00A0mm wider (ΔD = α·D·ΔT) — far more than the 50\u00A0µm gap."),
                ),
                LessonStep(
                    say = Bi("捏铜球，把烧红的球放回圆环上。", "Pinch the ball to lower the red-hot ball onto the ring."),
                    target = "Ball",
                    onEnter = { countAtStep = throughs },
                    done = { restState == 1 && ballC > 300.0 },
                    autoplay = { tryRing() },
                ),
            ),
            challenge = Challenge(
                say = Bi("卡住了！别等它冷却：让它在 350\u00A0°C 以上穿过圆环。", "Stuck! Don't wait for it to cool: get it through the ring while it is still above 350\u00A0°C."),
                progress = {
                    if (throughs > countAtStep) tr("刚才穿过时球是 %d\u00A0°C（要 > 350\u00A0°C）", "It passed at %d\u00A0°C (needs > 350\u00A0°C)").format(throughC.roundToInt())
                    else tr("球 %d\u00A0°C · 环 %d\u00A0°C · 间隙 %+d\u00A0µm", "Ball %d\u00A0°C · ring %d\u00A0°C · gap %+d\u00A0µm").format(ballC.roundToInt(), ringC.roundToInt(), clearanceUm.roundToInt())
                },
                done = { throughs > countAtStep && throughC > 350.0 },
                autoplay = { if (!lampOn) onTap("Lamp") },
            ),
        )
    }

    @Composable
    override fun Panel() {
        LabPanel(readout) {
            LabRow {
                SegmentControl {
                    Metals.ALL.forEachIndexed { k, m -> SegmentItem(selected = metalIdx == k, onClick = { metalIdx = k }, title = { Text(metalName(m)) }) }
                }
                Spacer(Modifier.width(20.dp))
                SegmentControl {
                    LAPSE_LABELS.forEachIndexed { k, l -> SegmentItem(selected = speedIdx == k, onClick = { speedIdx = k }, title = { Text(l) }) }
                }
            }
            LabRow {
                LabSwitch(tr("自动演示", "Auto demo"), auto) { auto = it; if (it) restart() }
                LabSwitch(tr("尺寸变化 ×20", "Size change ×20"), exaggerate) { exaggerate = it }
                Button(onClick = { restart() }) { Text(tr("复位", "Reset")) }
                // try / heat ball / heat ring live in the "pinch the object" row (same actions, lit while burning)
                Button(onClick = { quench() }) { Text(tr("水冷", "Quench")) }
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    /** Planck x CIE once per process (~0.1 s on the headset): log10 of visible luminance over the
     *  Draper threshold, and the glow's green channel; emissivity is applied at lookup. */
    private val lumTab: FloatArray by lazy {
        val yd = Blackbody.luminance(Blackbody.DRAPER_K); val rgb = DoubleArray(3)
        FloatArray(glowTab.size) { i ->
            val tk = (GT0 + i * GDT).toDouble()
            Blackbody.linearSrgb(tk, rgb); glowG[i] = encode(rgb[1].toFloat())
            kotlin.math.log10(Blackbody.luminance(tk) / yd).toFloat()
        }
    }

    private fun ensureGlowTable() {
        if (glowFor == metalIdx) return
        glowFor = metalIdx
        val le = kotlin.math.log10(metal.emissivity).toFloat(); val lt = lumTab
        for (i in glowTab.indices) glowTab[i] = ((lt[i] + le) / Blackbody.GLOW_DECADES.toFloat()).coerceIn(0f, 1f)
    }

    private fun glowIndex(tk: Double) = ((tk - GT0) / GDT).roundToInt().coerceIn(0, glowTab.size - 1)
    private fun glowAt(tk: Double): Float = if (tk < GT0) 0f else glowTab[glowIndex(tk)]
    private fun encode(l: Float) = if (l <= 0.0031308f) l * 12.92f else 1.055f * Math.pow(l.toDouble(), 1 / 2.4).toFloat() - 0.055f

    private fun tY(c: Float) = TY0 + TLEN * (c / TMAX_C).coerceIn(0f, 1f)
    private fun dist(a: P3, b: P3): Float { val dx = a.x - b.x; val dy = a.y - b.y; val dz = a.z - b.z; return sqrt(dx * dx + dy * dy + dz * dz) }
    private fun seg(x0: Float, y0: Float, x1: Float, y1: Float, z: Float, r: Float) = MeshGen.tube(floatArrayOf(x0, y0, z, x1, y1, z), r, 6, false)
}
