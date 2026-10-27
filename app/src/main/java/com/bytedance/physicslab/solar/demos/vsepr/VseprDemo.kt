// 分子构型 · 价层电子对互斥 (Gillespie & Nyholm, 1957) — build a molecule and watch its shape assemble itself.
//
// LESSON: a molecule's shape is what its electron pairs do when they push each other apart on a sphere
// around the central atom, and lone pairs push harder. The user DOES chemistry with their hands: pinch the
// "+ bond" / "+ lone pair" pads to add an electron domain, pinch a ligand atom or a lone-pair balloon to take
// it away, and the molecule visibly slides downhill (~1 s) into its new shape — the lone pair squeezes NH3 to
// 107 deg, two of them fold the octahedron into a flat square (XeF4), a lone pair walks to the equator of a
// bipyramid (SF4 seesaw). Pinch-drag the central atom or the trackball to turn it; walk around it.
// Live bond-angle arcs (one per angle family) carry the computed value; the readout compares with experiment.
//
// Physics: core/vsepr (points on a sphere, E = sum 1/d^3, lone pairs at 0.8 R — fitted on NH3 + H2O only;
// gradient flow with an Armijo line search). Per frame only node transforms change: every domain is ONE arm
// node rotated to its direction; arcs are pools of unit cylinders / unit wedges placed by transform.

package com.bytedance.physicslab.solar.demos.vsepr

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bytedance.physicslab.solar.core.P3
import com.bytedance.physicslab.solar.core.vsepr.Vsepr
import com.bytedance.physicslab.solar.core.vsepr.VseprData
import com.bytedance.physicslab.solar.core.vsepr.VseprSim
import com.bytedance.physicslab.solar.lab.Lang
import com.bytedance.physicslab.solar.lab.Q
import com.bytedance.physicslab.solar.lab.SpecDemo
import com.bytedance.physicslab.solar.lab.TapTarget
import com.bytedance.physicslab.solar.lab.tr
import com.bytedance.physicslab.solar.lab.spec.Blend
import com.bytedance.physicslab.solar.lab.spec.LabelSpec
import com.bytedance.physicslab.solar.lab.spec.Mat
import com.bytedance.physicslab.solar.lab.spec.Node
import com.bytedance.physicslab.solar.lab.spec.SoftRender
import com.bytedance.physicslab.solar.lab.ui.Button
import com.bytedance.physicslab.solar.lab.ui.LabPanel
import com.bytedance.physicslab.solar.lab.ui.LabRow
import com.bytedance.physicslab.solar.lab.ui.LabSwitch
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
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object VseprDemo : SpecDemo() {
    override val id = "vsepr"
    override val title = "分子构型 · 价层电子对互斥"  // i18n-ok (zh half of DemoRegistry.titleOf; English in TITLE_EN)
    override val credit = "Gillespie–Nyholm · 1957"

    // ---------------------------------------------------------------- layout (metres, volume space)
    private const val YC = 0.12f                  // molecule centre
    private const val ZC = 0.0f
    // the molecule group is scaled up to S_MAX (bonds ~0.30 m, a tetrahedron ~0.6 m across) and shrunk
    // automatically so that, as presented, every atom and balloon stays inside the action band
    private const val S_MAX = 1.65f
    private const val BAND_TOP = 0.415f
    private const val BAND_BOT = -0.195f
    private const val R_CENTRAL = 0.062f
    private const val R_MODEL = 0.135f            // where a bonding pair's electrons are drawn (model sphere)
    private const val LOBE_C = R_MODEL * 0.8f     // lone-pair point: 0.8 of it (Vsepr.LP_RADIUS)
    private const val LOBE_TIP = 0.188f           // balloon tip (arm frame)
    private const val LOBE_R = 0.050f             // balloon half-width at its widest
    private const val R_ARC = 0.112f
    private const val R_LABEL = 0.172f
    private const val PLINTH_Y = -0.262f
    private const val TABLE_Y = -0.226f           // low turntable disc: the molecule floats above it
    private const val TB_X = -0.27f               // trackball, front-left of the turntable: out of every atom's reach
    private const val TB_Z = 0.235f
    private const val PAD_X = 0.70f
    private const val PAD_Y = 0.000f
    private const val PAD_Z = 0.17f
    private const val GEO_Z = -0.285f             // back shelf: the five electron-pair geometries (small, low)
    private const val GEO_Y = -0.200f
    private const val GEO_K = 0.70f               // shelf model size factor
    private const val ARC_SEG = 12
    private const val MAX_ARCS = 3
    private const val RATE = 48f                  // flow steps per second at x1 (2.5 deg cap -> 120 deg/s)
    private const val ALIGN_S = 1.1f              // display frame eases into presentation pose (sim s)
    private const val GROW_S = 0.30f
    private const val SPIN = 0.35f                // auto-spin rad/s
    private const val DRAG_GAIN = 7f              // rad per metre of hand travel
    private val DEFAULT_PRESET = 3                // NH3: the lone pair is the lesson
    private val SPEEDS = floatArrayOf(0.2f, 0.5f, 1f)
    private val SPEED_LABELS get() = listOf(tr("慢放 ×0.2", "Slow ×0.2"), tr("中 ×0.5", "Medium ×0.5"), tr("实时 ×1", "Real ×1"))

    // ---------------------------------------------------------------- elements (CPK colours, sRGB)
    private class El(val color: Rgb, val r: Float, val bond: Float)
    private val EL = mapOf(
        "H" to El(Rgb(0.95f, 0.95f, 0.97f), 0.031f, 0.170f),
        "B" to El(Rgb(1.00f, 0.64f, 0.60f), 0.045f, 0.188f),
        "C" to El(Rgb(0.38f, 0.39f, 0.42f), 0.045f, 0.188f),
        "N" to El(Rgb(0.25f, 0.42f, 1.00f), 0.045f, 0.188f),
        "O" to El(Rgb(1.00f, 0.18f, 0.15f), 0.042f, 0.185f),
        "F" to El(Rgb(0.68f, 0.93f, 0.42f), 0.040f, 0.190f),
        "Cl" to El(Rgb(0.16f, 0.86f, 0.26f), 0.048f, 0.198f),
        "S" to El(Rgb(1.00f, 0.88f, 0.22f), 0.050f, 0.190f),
        "P" to El(Rgb(1.00f, 0.52f, 0.10f), 0.050f, 0.190f),
        "Xe" to El(Rgb(0.30f, 0.84f, 0.96f), 0.053f, 0.195f),
    )
    private fun el(s: String) = EL[s] ?: EL.getValue("F")

    // ---------------------------------------------------------------- UI state (Compose snapshot state: taps update the panel)
    var presetIdx by mutableIntStateOf(DEFAULT_PRESET)
    var speedIdx by mutableIntStateOf(2)
    var modified by mutableStateOf(false)
    var showAngles by mutableStateOf(true)
    var showSphere by mutableStateOf(true)
    var autoSpin by mutableStateOf(false)

    // ---------------------------------------------------------------- state
    private val sim = VseprSim()
    private var central = "N"
    private val ligand = Array(Vsepr.MAX_DOMAINS) { "H" }
    private var doubleBond = false
    private val prevU = DoubleArray(3 * Vsepr.MAX_DOMAINS)
    private val disp = DoubleArray(3 * Vsepr.MAX_DOMAINS)
    private var acc = 0f
    private val grow = FloatArray(Vsepr.MAX_DOMAINS) { 1f }
    private val V0 get() = Q.axisAngle(1f, 0f, 0f, 0.22f) * Q.axisAngle(0f, 1f, 0f, 0.62f)   // 3-fold: one bond toward you, none hidden behind
    private var userRot = Q.identity()
    private var alignFrom = Q.identity()
    private var alignTo = Q.identity()
    private var alignT = 99f
    private var dragging = false
    private var seenRevision = -1
    private var startE = 0.0
    private val arcI = IntArray(MAX_ARCS); private val arcJ = IntArray(MAX_ARCS)
    private val arcDeg = DoubleArray(MAX_ARCS)
    private var arcCount = 0
    private var ghostT = 0f
    private var ghostLone = false
    private var frame = 0
    private var textEn = false
    private var poseDirty = true
    private var padT = 0f
    private var lastMsg: (() -> String)? = null
    private var molScale = S_MAX
    private var molScaleTarget = S_MAX
    private var titleY = 0.40f
    private var molY = YC
    private var molYTarget = YC

    // ---------------------------------------------------------------- nodes
    private class Slot(val arm: Node, val bA: Node, val bB: Node, val bC: Node, val bD: Node,
                       val atom: Node, val lobe: Node, val core: Node, val dot1: Node, val dot2: Node)
    private lateinit var mol: Node
    private lateinit var center: Node
    private lateinit var slots: Array<Slot>
    private lateinit var ghost: Slot
    private lateinit var shells: Array<Node>
    private lateinit var arcRim: Array<Array<Node>>
    private lateinit var arcFan: Array<Array<Node>>
    private lateinit var padBondG: Node
    private lateinit var padLoneG: Node
    private lateinit var padPlus: Array<Node>
    private lateinit var padRing: Array<Node>
    private lateinit var geoGroups: Array<Node>
    private lateinit var geoBalls: Array<ArrayList<Node>>
    private var geoLit = -1

    // ---------------------------------------------------------------- materials (sRGB)
    private val alu = Mat.Pbr(Rgb(0.80f, 0.82f, 0.85f), metallic = 1f, roughness = 0.30f)
    private val darkSteel = Mat.Pbr(Rgb(0.30f, 0.31f, 0.33f), metallic = 1f, roughness = 0.40f)
    private val chrome = Mat.Pbr(Rgb(0.90f, 0.91f, 0.93f), metallic = 1f, roughness = 0.12f)
    private val slabTex by lazy {
        ProcTex.bakePlanet(256, 128, Rgb(0.07f, 0.075f, 0.085f), Rgb(0.12f, 0.125f, 0.14f), Rgb(0.20f, 0.205f, 0.22f), 0f, 77)
    }
    private val slab get() = Mat.Pbr(Rgb(1f, 1f, 1f), metallic = 0f, roughness = 0.55f, tex = slabTex)
    private val smoked = Mat.Pbr(Rgb(0.06f, 0.07f, 0.09f), metallic = 0f, roughness = 0.08f, alpha = 0.85f)
    private val lobeMat = Mat.Pbr(Rgb(0.70f, 0.58f, 1.00f), metallic = 0f, roughness = 0.06f, alpha = 0.38f, emissive = Rgb(0.13f, 0.08f, 0.30f))
    private val lobeCoreMat = Mat.Unlit(Rgb(0.55f, 0.38f, 1.00f), alpha = 0.32f, blend = Blend.ADD)
    private val dotMat = Mat.Unlit(Rgb(1.00f, 0.90f, 0.45f))
    private val shellMat = Mat.Unlit(Rgb(0.45f, 0.72f, 1.00f), alpha = 0.16f, blend = Blend.ADD)
    private val geoOn = Mat.Unlit(Rgb(0.45f, 0.85f, 1.00f), alpha = 1f, blend = Blend.ADD)
    private val geoOff = Mat.Pbr(Rgb(0.62f, 0.64f, 0.68f), metallic = 0.5f, roughness = 0.30f)
    private val geoStickOn = Mat.Unlit(Rgb(0.30f, 0.62f, 0.95f), alpha = 0.9f, blend = Blend.ADD)
    private val geoStickOff = Mat.Pbr(Rgb(0.45f, 0.46f, 0.50f), metallic = 0.5f, roughness = 0.35f)
    private val ARC_HUE = listOf(Rgb(1.00f, 0.80f, 0.30f), Rgb(0.35f, 0.88f, 1.00f), Rgb(1.00f, 0.48f, 0.82f))
    private val arcRimMat = ARC_HUE.map { Mat.Unlit(it, alpha = 1f, blend = Blend.ADD) }
    private val arcFanMat = ARC_HUE.map { Mat.Unlit(it, alpha = 0.20f, blend = Blend.TRANSPARENT) }
    private val plusOn = Mat.Unlit(Rgb(1.00f, 0.84f, 0.35f), alpha = 1f, blend = Blend.ADD)
    private val plusOff = Mat.Unlit(Rgb(0.30f, 0.30f, 0.32f), alpha = 1f, blend = Blend.ADD)

    private fun atomMat(c: Rgb) = Mat.Pbr(c, metallic = 0f, roughness = 0.28f, emissive = dim(c, 0.10f))
    private fun bondMat(c: Rgb) = Mat.Pbr(ProcTex.okLerp(c, Rgb(0.82f, 0.84f, 0.88f), 0.30f), metallic = 0.15f, roughness = 0.32f, emissive = dim(c, 0.05f))

    // ---------------------------------------------------------------- build

    override fun buildSpec(cacheDir: File): Node {
        // every cached field starts fresh (rebuild idempotence)
        speedIdx = 2; showAngles = true; showSphere = true; autoSpin = false; dragging = false
        userRot = V0; alignFrom = Q.identity(); alignTo = Q.identity(); alignT = 99f
        acc = 0f; seenRevision = -1; arcCount = 0; ghostT = 0f; frame = 0; poseDirty = true; padT = 0f; lastMsg = null
        molScale = S_MAX; molScaleTarget = S_MAX; titleY = 0.40f; molY = YC; molYTarget = YC
        grow.fill(1f)

        val root = Node("VseprRoot")
        val sphere = MeshGen.sphere(1f, 22, 44, MeshGen.SphereUv.EQUIRECT)
        val dotSphere = MeshGen.sphere(1f, 8, 14, MeshGen.SphereUv.EQUIRECT)
        val unitCyl = MeshGen.cylinder(1f, 1f, 18)
        val thinCyl = MeshGen.cylinder(1f, 1f, 6)
        val lobe = lobeMesh()
        val wedge = wedgeMesh()

        // ---- bench: honed granite slab with an aluminium edge, like the other benches
        root.add("Plinth", MeshGen.box(1.66f, 0.035f, 0.64f), slab, P3(0f, PLINTH_Y, -0.01f))
        root.add("PlinthBand", MeshGen.box(1.67f, 0.008f, 0.65f), alu, P3(0f, PLINTH_Y, -0.01f))
        val benchTop = PLINTH_Y + 0.0175f

        // ---- display stand: foot, chrome post, smoked-glass turntable with a glowing rim
        root.add("StandFoot", unitCyl, darkSteel, P3(0f, (benchTop + TABLE_Y) / 2, ZC)).also { it.scale = P3(0.10f, TABLE_Y - benchTop, 0.10f) }
        root.add("Turntable", unitCyl, smoked, P3(0f, TABLE_Y, ZC)).also { it.scale = P3(0.19f, 0.008f, 0.19f) }
        root.add("TableRim", MeshGen.tube(circle(0.19f, TABLE_Y + 0.005f, 72), 0.0030f, 6, true),
            Mat.Unlit(Rgb(0.40f, 0.72f, 1.00f), alpha = 0.75f, blend = Blend.ADD)).also { it.pos = P3(0f, 0f, ZC) }
        // trackball at the front of the turntable: pinch-drag to turn the molecule, tap = auto-spin
        val tb = root.add("Trackball", sphere, Mat.Pbr(Rgb(0.30f, 0.55f, 1.00f), metallic = 0.2f, roughness = 0.18f, emissive = Rgb(0.04f, 0.10f, 0.25f)),
            P3(TB_X, TABLE_Y + 0.036f, TB_Z))
        tb.uniformScale(0.032f); tb.tapRadius = 1.75f                    // 56 mm
        root.add("TrackOrbit", MeshGen.tube(circle(0.050f, 0f, 48), 0.0022f, 6, true), Mat.Unlit(Rgb(0.45f, 0.78f, 1.00f), alpha = 0.8f, blend = Blend.ADD),
            P3(TB_X, TABLE_Y + 0.036f, TB_Z)).also { it.rot = Q.axisAngle(1f, 0f, 0f, 0.35f) }
        root.add("TrackCup", unitCyl, darkSteel, P3(TB_X, TABLE_Y + 0.004f, TB_Z)).also { it.scale = P3(0.026f, 0.016f, 0.026f) }

        // ---- the molecule
        mol = root.add(Node("Mol")).also { it.pos = P3(0f, YC, ZC) }
        center = mol.add("Center", sphere, atomMat(el(central).color)).also { it.uniformScale(R_CENTRAL); it.tapRadius = 1.15f }
        // the model's sphere (bonding pairs on it, lone pairs at 0.8 R inside): three faint great circles
        val ringO = MeshGen.tube(circle(R_MODEL, 0f, 72), 0.0012f, 5, true)
        val ringRot = listOf(Q.identity(), Q.axisAngle(1f, 0f, 0f, (PI / 2).toFloat()), Q.axisAngle(0f, 0f, 1f, (PI / 2).toFloat()))
        shells = Array(3) { k -> mol.add("Shell$k", ringO, shellMat).also { it.rot = ringRot[k] } }
        slots = Array(Vsepr.MAX_DOMAINS) { i -> buildSlot(mol, "$i", sphere, dotSphere, unitCyl, lobe, tappable = true) }
        ghost = buildSlot(mol, "G", sphere, dotSphere, unitCyl, lobe, tappable = false)
        ghost.arm.visible = false
        arcRim = Array(MAX_ARCS) { k -> Array(ARC_SEG) { s -> mol.add("ArcR${k}_$s", thinCyl, arcRimMat[k]) } }
        arcFan = Array(MAX_ARCS) { k -> Array(ARC_SEG) { s -> mol.add("ArcW${k}_$s", wedge, arcFanMat[k]) } }

        // ---- the two pads: + bond (left), + lone pair (right)
        padRing = Array(2) { k ->
            val x = if (k == 0) -PAD_X else PAD_X
            root.add("PadPost$k", unitCyl, alu, P3(x, (benchTop + PAD_Y - 0.105f) / 2, PAD_Z)).also { it.scale = P3(0.013f, PAD_Y - 0.105f - benchTop, 0.013f) }
            root.add("PadFoot$k", unitCyl, darkSteel, P3(x, benchTop + 0.008f, PAD_Z)).also { it.scale = P3(0.065f, 0.016f, 0.065f) }
            root.add("PadDisc$k", unitCyl, smoked, P3(x, PAD_Y - 0.105f, PAD_Z)).also { it.scale = P3(0.100f, 0.012f, 0.100f) }
            root.add("PadRing$k", MeshGen.tube(circle(0.100f, PAD_Y - 0.098f, 64), 0.0034f, 6, true),
                Mat.Unlit(if (k == 0) Rgb(0.55f, 0.95f, 0.45f) else Rgb(0.72f, 0.55f, 1.00f), alpha = 0.8f, blend = Blend.ADD)).also { it.pos = P3(x, 0f, PAD_Z) }
        }
        padBondG = root.add(Node("PadBondG")).also { it.pos = P3(-PAD_X, PAD_Y, PAD_Z) }
        padBondG.add("PadStub", unitCyl, bondMat(Rgb(0.68f, 0.93f, 0.42f)), P3(0f, -0.068f, 0f)).also { it.scale = P3(0.014f, 0.085f, 0.014f) }
        padBondG.add("PadBond", sphere, atomMat(el("F").color)).also { it.uniformScale(0.048f); it.tapRadius = 1.75f }   // 84 mm
        padLoneG = root.add(Node("PadLoneG")).also { it.pos = P3(PAD_X, PAD_Y + 0.022f, PAD_Z); it.uniformScale(1.45f) }
        padLoneG.add("PadLone", lobe, lobeMat, P3(0f, 0f, 0f)).also { it.tapRadius = 0.060f }                       // x1.45 = 87 mm
        padLoneG.add("PadLoneCore", lobe, lobeCoreMat).also { it.uniformScale(0.6f) }
        for (s in floatArrayOf(-1f, 1f)) padLoneG.add("PadLoneDot", dotSphere, dotMat, P3(s * 0.013f, 0f, 0f)).uniformScale(0.0072f)
        val plusBar = MeshGen.box(1f, 1f, 1f)
        padPlus = Array(4) { k ->
            val x = if (k < 2) -PAD_X + 0.105f else PAD_X - 0.105f              // inner side, toward the molecule
            root.add("Plus$k", plusBar, plusOn, P3(x, PAD_Y + 0.075f, PAD_Z)).also {
                it.scale = if (k % 2 == 0) P3(0.048f, 0.013f, 0.013f) else P3(0.013f, 0.048f, 0.013f)
            }
        }

        // ---- back shelf: the five electron-pair geometries (2..6 pairs); the current family lights up
        root.add("GeoRail", MeshGen.box(0.74f, 0.008f, 0.035f), darkSteel, P3(0f, benchTop + 0.004f, GEO_Z))
        geoGroups = Array(5) { g ->
            val np = g + 2; val x = -0.32f + 0.16f * g
            root.add("GeoPost$g", unitCyl, darkSteel, P3(x, (benchTop + GEO_Y - 0.045f) / 2, GEO_Z)).also { it.scale = P3(0.004f, GEO_Y - 0.045f - benchTop, 0.004f) }
            root.add(Node("Geo$np")).also { it.pos = P3(x, GEO_Y, GEO_Z) }
        }
        geoBalls = Array(5) { g ->
            val np = g + 2; val grp = geoGroups[g]
            val ideal = Vsepr.ideal(np); val lone = BooleanArray(np)
            val u = Vsepr.rotateAll(Vsepr.canonicalRotation(ideal, lone, np), ideal, np)
            grp.add("GeoHub$g", sphere, geoOff).uniformScale(0.012f * GEO_K)
            val out = ArrayList<Node>()
            for (k in 0 until np) {
                val d = P3(u[3 * k].toFloat(), u[3 * k + 1].toFloat(), u[3 * k + 2].toFloat())
                out += grp.add("GeoStick${g}_$k", unitCyl, geoStickOff, d * (0.026f * GEO_K)).also {
                    it.scale = P3(0.0024f, 0.052f * GEO_K, 0.0024f); it.rot = Q.fromTo(0f, 1f, 0f, d.x, d.y, d.z) }
                out += grp.add("GeoBall${g}_$k", sphere, geoOff, d * (0.052f * GEO_K)).also { it.uniformScale(0.0105f * GEO_K) }
            }
            out
        }
        geoLit = -1

        choosePreset(DEFAULT_PRESET)
        update(0f)
        return root
    }

    private fun buildSlot(parent: Node, tag: String, sphere: MeshData, dotSphere: MeshData, cyl: MeshData, lobe: MeshData, tappable: Boolean): Slot {
        val arm = parent.add(Node("Arm$tag"))
        val bA = arm.add("BondA$tag", cyl, null); val bB = arm.add("BondB$tag", cyl, null)
        val bC = arm.add("BondC$tag", cyl, null); val bD = arm.add("BondD$tag", cyl, null)
        val atom = arm.add((if (tappable) "VAtom" else "GAtom") + tag, sphere, null)
        val lb = arm.add((if (tappable) "VLobe" else "GLobe") + tag, lobe, lobeMat, P3(0f, LOBE_C, 0f))
        if (tappable) lb.tapRadius = 0.055f
        val core = arm.add("LobeCore$tag", lobe, lobeCoreMat, P3(0f, LOBE_C, 0f)).also { it.uniformScale(0.6f) }
        val d1 = arm.add("DotA$tag", dotSphere, dotMat).also { it.uniformScale(0.0072f) }
        val d2 = arm.add("DotB$tag", dotSphere, dotMat).also { it.uniformScale(0.0072f) }
        return Slot(arm, bA, bB, bC, bD, atom, lb, core, d1, d2)
    }

    /** Dress slot [s] as a bond to [lig] or as a lone pair. */
    private fun dress(s: Slot, isLone: Boolean, lig: String, dbl: Boolean, tappable: Boolean) {
        for (b in listOf(s.bA, s.bB, s.bC, s.bD, s.atom)) b.visible = !isLone
        s.lobe.visible = isLone; s.core.visible = isLone
        if (isLone) {
            s.dot1.pos = P3(-0.013f, LOBE_C, 0f); s.dot2.pos = P3(0.013f, LOBE_C, 0f)
            if (tappable) s.atom.tapRadius = 0f
            if (tappable) s.lobe.tapRadius = 0.055f
            return
        }
        if (tappable) s.lobe.tapRadius = 0f
        val e = el(lig); val L = e.bond
        s.atom.pos = P3(0f, L, 0f); s.atom.uniformScale(e.r); s.atom.setMat(atomMat(e.color))
        if (tappable) s.atom.tapRadius = 0.055f / e.r
        val y0 = 0.034f; val y1 = L - e.r * 0.5f; val mid = (R_CENTRAL + L - e.r) / 2
        val rb = if (dbl) 0.0078f else 0.0115f; val off = if (dbl) 0.0135f else 0f
        val cm = bondMat(el(central).color); val lm = bondMat(e.color)
        fun put(n: Node, x: Float, a: Float, b: Float, m: Mat) { n.pos = P3(x, (a + b) / 2, 0f); n.scale = P3(rb, b - a, rb); n.setMat(m) }
        put(s.bA, -off, y0, mid, cm); put(s.bB, -off, mid, y1, lm)
        put(s.bC, off, y0, mid, cm); put(s.bD, off, mid, y1, lm)
        s.bC.visible = dbl; s.bD.visible = dbl
        val dx = if (dbl) 0.029f else 0.020f
        s.dot1.pos = P3(-dx, R_MODEL, 0f); s.dot2.pos = P3(dx, R_MODEL, 0f)
    }

    private fun dressAll() {
        center.setMat(atomMat(el(central).color))
        for (i in 0 until sim.n) dress(slots[i], sim.lone[i], ligand[i], doubleBond, tappable = true)
        for (i in sim.n until Vsepr.MAX_DOMAINS) { slots[i].atom.tapRadius = 0f; slots[i].lobe.tapRadius = 0f }
        poseDirty = true
    }

    // ---------------------------------------------------------------- actions

    fun choosePreset(k: Int) {
        val p = VseprData.presets[k]
        presetIdx = k; modified = false
        central = p.central; doubleBond = p.doubleBond
        for (i in 0 until Vsepr.MAX_DOMAINS) ligand[i] = p.ligand
        sim.reset(p.nB, p.nL)
        afterChange(snapFrame = true)
        grow.fill(0.15f)
        dressAll()
    }

    /** Replay the self-assembly of the current composition from a fresh bud. */
    fun reassemble() {
        val nB = sim.nBonds; val nL = sim.nLone
        val keep = (0 until sim.n).filter { !sim.lone[it] }.map { ligand[it] }
        sim.reset(nB, nL, variant = sim.revision % 7 + 1)
        for (i in 0 until nB) ligand[i] = keep.getOrElse(i) { ligand[0] }
        afterChange(snapFrame = true)
        grow.fill(0.15f)
        dressAll()
    }

    private fun addDomain(isLone: Boolean): Boolean {
        val k = sim.add(isLone)
        if (k < 0) return false
        if (!isLone) ligand[k] = VseprData.presets[presetIdx].ligand
        modified = true
        grow[k] = 0f
        afterChange(snapFrame = false)
        dressAll()
        return true
    }

    private fun removeDomain(k: Int): Boolean {
        if (k !in 0 until sim.n) return false
        val wasLone = sim.lone[k]; val lig = ligand[k]
        val gx = disp[3 * k]; val gy = disp[3 * k + 1]; val gz = disp[3 * k + 2]
        if (!sim.remove(k)) return false
        for (i in k until Vsepr.MAX_DOMAINS - 1) { ligand[i] = ligand[i + 1]; grow[i] = grow[i + 1]; for (a in 0 until 3) disp[3 * i + a] = disp[3 * (i + 1) + a] }
        // the removed domain shrinks away where it was
        ghostLone = wasLone; ghostT = 1f
        dress(ghost, wasLone, lig, doubleBond, tappable = false)
        ghost.arm.rot = armRot(gx, gy, gz, viewInMol())
        modified = true
        afterChange(snapFrame = false)
        dressAll()
        return true
    }

    /** A new prediction exists: arcs follow its angle families; the display frame eases toward its pose. */
    private fun afterChange(snapFrame: Boolean) {
        seenRevision = sim.revision
        startE = sim.energy
        System.arraycopy(sim.u, 0, prevU, 0, 3 * sim.n)
        acc = 0f
        val present: Q
        if (snapFrame) { present = quatOf(sim.presentRotation); alignFrom = present; alignTo = present; alignT = 99f }
        else {
            // of the equivalent presentation frames, the one nearest the current view: no needless spin
            val now = currentAlign(); val inv = now.conj()
            val up = inv.rotate(floatArrayOf(0f, 1f, 0f)); val xh = inv.rotate(floatArrayOf(1f, 0f, 0f))
            present = quatOf(Vsepr.canonicalRotation(sim.target, sim.lone, sim.n,
                doubleArrayOf(up[0].toDouble(), up[1].toDouble(), up[2].toDouble()), doubleArrayOf(xh[0].toDouble(), xh[1].toDouble(), xh[2].toDouble())))
            alignFrom = now; alignTo = present; alignT = 0f
        }
        // arcs: one per family of equal bond angles, on the pair that will face the viewer best
        val endM = userRot * present
        fitTo(endM, snap = snapFrame)
        val cls = Vsepr.angleClasses(sim.target, sim.lone, sim.n) { i, j -> arcScore(endM, i, j) }
        arcCount = min(MAX_ARCS, cls.size)
        for (k in 0 until arcCount) { arcI[k] = cls[k].i; arcJ[k] = cls[k].j; arcDeg[k] = cls[k].deg }
        poseDirty = true
    }

    /**
     * Largest molecule scale (<= S_MAX) that keeps every atom and balloon of the predicted end state inside the
     * action band as presented by [m]. World yaw (auto-spin) never changes heights, so this holds while it spins.
     */
    private fun fitTo(m: Q, snap: Boolean) {
        var top = R_MODEL; var bot = R_MODEL                 // the model-sphere rings at least
        val t = sim.target
        for (i in 0 until sim.n) {
            val w = m.rotate(floatArrayOf(t[3 * i].toFloat(), t[3 * i + 1].toFloat(), t[3 * i + 2].toFloat()))
            if (sim.lone[i]) {
                top = max(top, max(w[1] * LOBE_C + LOBE_R, w[1] * LOBE_TIP)); bot = max(bot, max(-w[1] * LOBE_C + LOBE_R, -w[1] * LOBE_TIP))
            } else {
                val e = el(ligand[i])
                top = max(top, w[1] * e.bond + e.r); bot = max(bot, -w[1] * e.bond + e.r)
            }
        }
        // scale to the band, then centre the molecule's own extents in it (kept near YC so it stays on its stand)
        var sc = min(S_MAX, (BAND_TOP - BAND_BOT) / (top + bot))
        val y = ((BAND_TOP + BAND_BOT) / 2 + (bot - top) / 2 * sc).coerceIn(YC - 0.07f, YC + 0.07f)
        sc = minOf(sc, (BAND_TOP - y) / top, (y - BAND_BOT) / bot)
        molScaleTarget = sc; molYTarget = y
        if (snap) { molScale = sc; molY = y }
        titleY = min(0.45f, y + top * sc + 0.05f)
    }

    private fun arcScore(m: Q, i: Int, j: Int): Double {
        val t = sim.target
        val a = floatArrayOf(t[3 * i].toFloat(), t[3 * i + 1].toFloat(), t[3 * i + 2].toFloat())
        val b = floatArrayOf(t[3 * j].toFloat(), t[3 * j + 1].toFloat(), t[3 * j + 2].toFloat())
        val nrm = cross(a, b); val l = len(nrm)
        val mid = m.rotate(norm(floatArrayOf(a[0] + b[0], a[1] + b[1], a[2] + b[2])))
        if (l < 1e-3f) return 0.5 + 0.2 * mid[1]
        val nw = m.rotate(floatArrayOf(nrm[0] / l, nrm[1] / l, nrm[2] / l))
        return abs(nw[2]).toDouble() + 0.35 * mid[2] + 0.15 * mid[1]          // faces the viewer, in front, on top
    }

    private fun currentAlign(): Q = slerp(alignFrom, alignTo, VseprSim.smooth((alignT / ALIGN_S).toDouble()).toFloat())

    // ---------------------------------------------------------------- per frame

    override fun update(dt: Float) {
        val sp = SPEEDS[speedIdx]
        if (sim.revision != seenRevision) afterChange(snapFrame = true)
        val n = sim.n
        // ---- the flow: RATE steps per simulated second, interpolated between steps
        val moving = !sim.settled
        if (moving) {
            acc += dt * sp * RATE
            var k = 0
            while (acc >= 1f && k < 8) { System.arraycopy(sim.u, 0, prevU, 0, 3 * n); sim.advance(); acc -= 1f; k++ }
            if (acc >= 1f) acc = 0.99f
            if (sim.visuallySettled) { sim.polish(); System.arraycopy(sim.u, 0, prevU, 0, 3 * n); acc = 0f }
        }
        val f = if (sim.settled) 1.0 else acc.toDouble()
        for (i in 0 until n) {
            var x = prevU[3 * i] + (sim.u[3 * i] - prevU[3 * i]) * f; var y = prevU[3 * i + 1] + (sim.u[3 * i + 1] - prevU[3 * i + 1]) * f
            var z = prevU[3 * i + 2] + (sim.u[3 * i + 2] - prevU[3 * i + 2]) * f
            val l = sqrt(x * x + y * y + z * z).coerceAtLeast(1e-9); x /= l; y /= l; z /= l
            disp[3 * i] = x; disp[3 * i + 1] = y; disp[3 * i + 2] = z
        }

        // ---- display frame: user rotation x presentation alignment (eased)
        val aligning = alignT < ALIGN_S
        alignT += dt * sp
        if (autoSpin && !dragging) userRot = Q.axisAngle(0f, 1f, 0f, SPIN * dt) * userRot
        val growing = grow.any { it < 1f }
        val rescaling = abs(molScale - molScaleTarget) > 1e-4f || abs(molY - molYTarget) > 1e-4f
        if (rescaling) { val k = min(1f, dt * 5f); molScale += (molScaleTarget - molScale) * k; molY += (molYTarget - molY) * k }
        for (i in 0 until Vsepr.MAX_DOMAINS) grow[i] = min(1f, grow[i] + dt * sp / GROW_S)
        val needPose = moving || aligning || growing || rescaling || autoSpin || dragging || poseDirty || ghostT > 0f || dt == 0f
        if (needPose) {
            poseDirty = false
            mol.rot = userRot * currentAlign()
            mol.uniformScale(molScale); mol.pos = P3(0f, molY, ZC)
            val view = viewInMol()
            for (i in 0 until Vsepr.MAX_DOMAINS) {
                val s = slots[i]
                if (i >= n) { s.arm.visible = false; continue }
                s.arm.visible = true
                s.arm.uniformScale(max(0.02f, VseprSim.smooth(grow[i].toDouble()).toFloat()))
                s.arm.rot = armRot(disp[3 * i], disp[3 * i + 1], disp[3 * i + 2], view)
            }
            for (sh in shells) sh.visible = showSphere
            if (ghostT > 0f) {
                ghostT = max(0f, ghostT - dt / 0.28f)
                ghost.arm.visible = ghostT > 0f
                ghost.arm.uniformScale(max(0.02f, ghostT))
            }
            placeArcs()
        }
        // ---- pads: the samples turn slowly; dim when the shell is full
        padT += dt
        padBondG.rot = Q.axisAngle(0f, 1f, 0f, padT * 0.8f) * Q.axisAngle(0f, 0f, 1f, -0.5f)
        padLoneG.rot = Q.axisAngle(0f, 1f, 0f, -padT * 0.8f)
        val full = n >= Vsepr.MAX_DOMAINS
        for (p in padPlus) p.setMat(if (full) plusOff else plusOn)
        // the electron-pair geometry shelf: turn slowly, light the current family
        for (g in 0 until 5) geoGroups[g].rot = Q.axisAngle(1f, 0f, 0f, 0.30f) * Q.axisAngle(0f, 1f, 0f, padT * 0.35f + g * 0.7f)
        if (geoLit != n) {
            geoLit = n
            for (g in 0 until 5) for ((k, nd) in geoBalls[g].withIndex())
                nd.setMat(if (g == n - 2) (if (k % 2 == 0) geoStickOn else geoOn) else (if (k % 2 == 0) geoStickOff else geoOff))
        }

        if (frame++ % 6 == 0 || Lang.en != textEn || dt == 0f) { textEn = Lang.en; updateText() }
        else updateLabelPositions()
    }

    private fun viewInMol(): FloatArray = mol.rot.conj().rotate(floatArrayOf(0f, 0f, 1f))

    /** Arm frame: +Y along the domain, +X across the line of sight (double bonds and electron dots side by side). */
    private fun armRot(x: Double, y: Double, z: Double, view: FloatArray): Q {
        val yv = floatArrayOf(x.toFloat(), y.toFloat(), z.toFloat())
        var xv = cross(yv, view)
        if (len(xv) < 0.15f) xv = cross(yv, if (abs(yv[0]) < 0.9f) floatArrayOf(1f, 0f, 0f) else floatArrayOf(0f, 1f, 0f))
        xv = norm(xv)
        val zv = cross(xv, yv)
        return quatFromBasis(xv, yv, zv)
    }

    private fun placeArcs() {
        for (k in 0 until MAX_ARCS) {
            val on = showAngles && k < arcCount
            val fan = on && arcDeg[k] < 150.0          // wide (near-trans) angles: rim only, a big fan would hide the core
            for (s in 0 until ARC_SEG) { arcRim[k][s].visible = on; arcFan[k][s].visible = fan }
            if (!on) continue
            val i = arcI[k]; val j = arcJ[k]
            val a = floatArrayOf(disp[3 * i].toFloat(), disp[3 * i + 1].toFloat(), disp[3 * i + 2].toFloat())
            val b = floatArrayOf(disp[3 * j].toFloat(), disp[3 * j + 1].toFloat(), disp[3 * j + 2].toFloat())
            val (c, th) = arcPlane(a, b)
            val nrm = cross(a, c)
            val d = th / ARC_SEG
            for (s in 0 until ARC_SEG) {
                val p0 = arcPt(a, c, s * d); val p1 = arcPt(a, c, (s + 1) * d)
                val dx = p1[0] - p0[0]; val dy = p1[1] - p0[1]; val dz = p1[2] - p0[2]
                val l = sqrt(dx * dx + dy * dy + dz * dz) * R_ARC
                val rim = arcRim[k][s]
                rim.pos = P3((p0[0] + p1[0]) / 2 * R_ARC, (p0[1] + p1[1]) / 2 * R_ARC, (p0[2] + p1[2]) / 2 * R_ARC)
                rim.rot = Q.fromTo(0f, 1f, 0f, dx, dy, dz)
                rim.scale = P3(0.0024f, max(l * 1.04f, 1e-4f), 0.0024f)
                val m = arcPt(a, c, (s + 0.5f) * d)
                val t = floatArrayOf(-sin((s + 0.5f) * d) * a[0] + cos((s + 0.5f) * d) * c[0],
                    -sin((s + 0.5f) * d) * a[1] + cos((s + 0.5f) * d) * c[1], -sin((s + 0.5f) * d) * a[2] + cos((s + 0.5f) * d) * c[2])
                val w = arcFan[k][s]
                w.rot = quatFromBasis(m, t, nrm)
                w.scale = P3(R_ARC * cos(d / 2), max(R_ARC * sin(d / 2), 1e-5f), 1f)
            }
        }
    }

    /** In-plane unit vector c (perpendicular to a) and the angle a->b. Antiparallel pairs arc over the side the viewer sees best. */
    private fun arcPlane(a: FloatArray, b: FloatArray): Pair<FloatArray, Float> {
        val cosT = (a[0] * b[0] + a[1] * b[1] + a[2] * b[2]).coerceIn(-1f, 1f)
        val th = acos(cosT)
        val c0 = floatArrayOf(b[0] - cosT * a[0], b[1] - cosT * a[1], b[2] - cosT * a[2])
        if (len(c0) > 0.05f) return norm(c0) to th
        // (anti)parallel: arc through screen-up, or screen-down if a lone pair sits up there
        val upM = mol.rot.conj().rotate(floatArrayOf(0f, 1f, 0f))
        var c = norm(floatArrayOf(upM[0] - a[0] * dot(upM, a), upM[1] - a[1] * dot(upM, a), upM[2] - a[2] * dot(upM, a)))
        for (i in 0 until sim.n) if (sim.lone[i] && c[0] * disp[3 * i] + c[1] * disp[3 * i + 1] + c[2] * disp[3 * i + 2] > 0.7) { c = floatArrayOf(-c[0], -c[1], -c[2]); break }
        return c to th
    }

    private fun arcPt(a: FloatArray, c: FloatArray, phi: Float) =
        floatArrayOf(cos(phi) * a[0] + sin(phi) * c[0], cos(phi) * a[1] + sin(phi) * c[1], cos(phi) * a[2] + sin(phi) * c[2])

    // ---------------------------------------------------------------- text

    private fun shapeName(nB: Int, nL: Int) = Vsepr.molecularShape(nB, nL).let { tr(it.zh, it.en) }
    private fun liveAngle(k: Int) = Vsepr.angleDeg(disp, arcI[k], arcJ[k])
    private fun anglesText(u: DoubleArray): String =
        Vsepr.angleClasses(u, sim.lone, sim.n).joinToString(" / ") { "%.1f°".format(it.deg) }

    private fun updateText() {
        val nB = sim.nBonds; val nL = sim.nLone; val n = sim.n
        val p = VseprData.presets[presetIdx]
        val ax = Vsepr.formula(nB, nL)
        val eg = Vsepr.electronGeometry(n).let { tr(it.zh, it.en) }
        val name = if (!modified) "${p.formula} · $ax" else ax
        val l1 = tr("%s · 电子对：%s → 分子构型：%s", "%s · electron pairs: %s → shape: %s").format(name, eg, shapeName(nB, nL))
        val ref = VseprData.reference(nB, nL)
        val l2 = if (!sim.settled) {
            tr("正在弛豫：每对电子沿球面“下坡”，排斥能 E %.3f → %.4f（只降不升）", "Relaxing: each pair slides downhill on the sphere, E %.3f → %.4f (never rises)").format(startE, sim.energy)
        } else if (ref != null) {
            val calc = ref.angles.joinToString("  ") { a -> "∠%s %.1f°".format(tr(a.labelZh, a.labelEn), VseprData.measure(a.stat, sim.u, sim.lone, n)) }
            val exp = ref.angles.joinToString(" / ") { "%.1f°".format(it.exp) }
            val who = if (!modified) ref.formula else tr("同类 ", "same class: ") + ref.formula
            tr("计算 %s · 实验 %s %s（%s）· E = %.4f", "model %s · measured %s %s (%s) · E = %.4f").format(calc, who, exp, tr(ref.sourceZh, ref.sourceEn), sim.energy)
        } else {
            tr("计算键角 %s · 这一类没有预设分子可比 · E = %.4f", "model angles %s · no preset molecule in this class · E = %.4f").format(anglesText(sim.u), sim.energy)
        }
        readoutText = l1 + "\n" + l2 + "\n" + whyLine(nB, nL)
        labels.clear()
        labels += LabelSpec("%s · %s".format(if (!modified) p.formula else ax, shapeName(nB, nL)), P3(0f, titleY, ZC + 0.04f))
        labels += LabelSpec(tr("＋ 键", "+ bond"), P3(-PAD_X, PAD_Y + 0.135f, PAD_Z))
        labels += LabelSpec(tr("＋ 孤对", "+ lone pair"), P3(PAD_X, PAD_Y + 0.135f, PAD_Z))
        for (k in 0 until MAX_ARCS) labels += LabelSpec(if (k < arcCount) "%.1f°".format(liveAngle(k)) else "", arcLabelPos(k), visible = showAngles && k < arcCount)
    }

    private fun updateLabelPositions() {
        for (k in 0 until MAX_ARCS) if (labels.size > 3 + k) labels[3 + k] = labels[3 + k].copy(pos = arcLabelPos(k), visible = showAngles && k < arcCount)
    }

    private fun arcLabelPos(k: Int): P3 {
        if (k >= arcCount) return P3(0f, molY, ZC)
        val i = arcI[k]; val j = arcJ[k]
        val a = floatArrayOf(disp[3 * i].toFloat(), disp[3 * i + 1].toFloat(), disp[3 * i + 2].toFloat())
        val b = floatArrayOf(disp[3 * j].toFloat(), disp[3 * j + 1].toFloat(), disp[3 * j + 2].toFloat())
        val (c, th) = arcPlane(a, b)
        val m = mol.rot.rotate(arcPt(a, c, th / 2))
        val r = R_LABEL * molScale
        return P3(m[0] * r, molY + m[1] * r, ZC + m[2] * r + 0.01f)
    }

    private fun whyLine(nB: Int, nL: Int): String = when {
        nL == 0 -> tr("没有孤对：%d 对电子在球面上互相排斥、尽量远离 → 规则多面体，键角只由几何决定",
            "No lone pairs: %d pairs repel on a sphere and spread evenly → a regular polyhedron").format(nB)
        nB + nL <= 4 -> tr("孤对只被一个核拉住，离核更近、更“胖”：排斥 LP–LP > LP–BP > BP–BP，把键角挤小",
            "A lone pair is held by one nucleus: closer in, fatter; LP–LP > LP–BP > BP–BP squeezes the bonds")
        nB == 4 && nL == 1 -> tr("孤对选赤道位（只有 2 个 90° 邻居，轴向有 3 个）；真实孤对在赤道面摊得更开，赤–赤角实测更小",
            "LP goes equatorial (two 90° neighbours, not three); a real LP spreads wider there → smaller eq angle")
        nB == 3 && nL == 2 -> tr("两对孤对都占赤道位、相隔约 120°；三根键成 T 形，轴向键被推得略向后弯",
            "Both LPs take equatorial sites ~120° apart; the bonds form a T, the axial ones bent back")
        nB == 2 && nL == 3 -> tr("三对孤对占满赤道面、互成 120°，两根键只能站在轴上 → 直线形",
            "Three LPs fill the equator 120° apart; the two bonds are left on the axis → linear")
        nB == 5 && nL == 1 -> tr("孤对把 4 根底面键往下压：顶–底角小于 90°",
            "The LP pushes the four basal bonds down: apex–base angle below 90°")
        nB == 4 && nL == 2 -> tr("两对孤对互为反位（180°）：最强的 LP–LP 排斥离得最远 → 平面正方形",
            "The two LPs sit trans (180°): the strongest repulsion, LP–LP, kept farthest apart")
        else -> tr("孤对先抢彼此离得最远的位置，剩下的位置留给键",
            "Lone pairs take the sites farthest from each other; bonds get what is left")
    }

    // ---------------------------------------------------------------- interaction

    override val tapTargets: List<TapTarget> get() {
        val out = arrayListOf(
            TapTarget("PadBond", "＋键 → 加一根键", "+ bond → add"),
            TapTarget("PadLone", "＋孤对 → 加孤对电子", "+ lone pair → add"),
            TapTarget("Center", "中心原子 → 摆正视角", "Centre atom → reset"),
            TapTarget("Trackball", "旋转球 → 自动旋转", "Trackball → auto-spin") { autoSpin },
        )
        var lp = 0; var bd = 0
        for (i in 0 until sim.n) {
            if (sim.lone[i]) { lp++; out += TapTarget("VLobe$i", "孤对 $lp → 移除", "LP $lp → remove") }
            else { bd++; out += TapTarget("VAtom$i", "${ligand[i]}$bd → 移除", "${ligand[i]}$bd → remove") }
        }
        return out
    }

    override fun isTarget(name: String) = name == "PadBond" || name == "PadLone" || name == "Center" || name == "Trackball" ||
        name.startsWith("VAtom") || name.startsWith("VLobe")

    private enum class Did { ADD_BOND, ADD_LONE, REMOVE_BOND, REMOVE_LONE }

    override fun onTap(name: String) {
        val nB0 = sim.nBonds
        lastMsg = when {
            name == "PadBond" || name == "PadLone" -> {
                val isLone = name == "PadLone"
                if (!addDomain(isLone)) ({ tr("已满：中心原子周围最多 6 对电子", "Full: at most 6 electron pairs") })
                else describe(if (isLone) Did.ADD_LONE else Did.ADD_BOND, "")
            }
            name == "Center" -> { userRot = V0; autoSpin = false; poseDirty = true; fitTo(userRot * alignTo, snap = false)
                ({ tr("视角摆正：孤对朝上，键横跨视线", "View reset: lone pairs up, bonds across your view") }) }
            name == "Trackball" -> { autoSpin = !autoSpin; poseDirty = true
                val on = autoSpin
                ({ if (on) tr("自动旋转：开（也可捏住拖动）", "Auto-spin on (or pinch and drag to turn)") else tr("自动旋转：关", "Auto-spin off") }) }
            name.startsWith("VAtom") || name.startsWith("VLobe") -> {
                val k = name.substring(5).toIntOrNull() ?: -1
                val wantLone = name.startsWith("VLobe")
                when {
                    k !in 0 until sim.n || sim.lone[k] != wantLone -> ({ tr("这里已经没有它了", "Nothing there any more") })
                    !wantLone && nB0 <= Vsepr.MIN_BONDS -> ({ tr("至少保留 2 根键，才有键角可看", "Keep at least 2 bonds to have an angle") })
                    else -> { val lig = ligand[k]; removeDomain(k); describe(if (wantLone) Did.REMOVE_LONE else Did.REMOVE_BOND, lig) }
                }
            }
            else -> null
        }
    }

    /** What the tap did, in the language current when the note is shown (the prediction is already known). */
    private fun describe(did: Did, lig: String): () -> String {
        val nB = sim.nBonds; val nL = sim.nLone
        val ax = Vsepr.formula(nB, nL); val shape = Vsepr.molecularShape(nB, nL)
        val ang = anglesText(sim.target)
        return {
            val sh = tr(shape.zh, shape.en)
            when (did) {
                Did.ADD_LONE -> tr("加一对孤对电子：%s %s，键角被挤到 ≈%s", "Added a lone pair: %s %s, angles squeezed to ≈%s").format(ax, sh, ang)
                Did.ADD_BOND -> tr("加一根键：%s %s，键角 ≈%s", "Added a bond: %s %s, angles ≈%s").format(ax, sh, ang)
                Did.REMOVE_LONE -> tr("拿走一对孤对：%s %s，键角张开到 ≈%s", "Removed a lone pair: %s %s, angles open to ≈%s").format(ax, sh, ang)
                Did.REMOVE_BOND -> tr("拿走一个 %s：%s %s，键角 ≈%s", "Removed %s: %s %s, angles ≈%s").format(lig, ax, sh, ang)
            }
        }
    }

    override fun tapMessage(name: String): String? = lastMsg?.invoke()

    /** A removed domain's note sits where it was (its slot now holds the next domain), above the shrinking ghost. */
    override fun anchorOf(name: String): FloatArray? {
        if ((name.startsWith("VAtom") || name.startsWith("VLobe")) && ghostT > 0f) {
            val w = SoftRender.worldOf(if (ghostLone) ghost.lobe else ghost.atom).t
            return floatArrayOf(w[0], w[1] + 0.09f, w[2] + 0.03f)
        }
        return super.anchorOf(name)
    }

    // pinch-and-drag the central atom or the trackball: dx turns about Y, dy tilts about X (like rolling a ball)
    override fun isDragTarget(name: String) = name == "Center" || name == "Trackball"
    override fun onDragStart(name: String) { dragging = true }
    override fun onDrag(name: String, dx: Float, dy: Float, dz: Float) {
        userRot = Q.axisAngle(0f, 1f, 0f, dx * DRAG_GAIN) * Q.axisAngle(1f, 0f, 0f, -dy * DRAG_GAIN) * userRot
        poseDirty = true
    }
    override fun onDragEnd(name: String) { dragging = false; fitTo(userRot * alignTo, snap = false) }   // tilted: re-fit the band

    /** Review states: run straight to the settled molecule. */
    private fun settleNow() {
        sim.finish(); System.arraycopy(sim.u, 0, prevU, 0, 3 * sim.n); acc = 0f; grow.fill(1f); alignT = 99f; ghostT = 0f; poseDirty = true
    }

    override fun reviewVariants(): Map<String, () -> Unit> = mapOf(
        "sf4" to { choosePreset(6); settleNow() },
        "xef4" to { choosePreset(10); settleNow() },
        "ch4_plus_lp" to { choosePreset(2); settleNow(); onTap("PadLone"); settleNow() },
    )

    @Composable
    override fun Panel() {
        LabPanel(readout) {
            LabRow {
                SegmentControl {
                    VseprData.presets.forEachIndexed { k, p ->
                        SegmentItem(selected = !modified && presetIdx == k, onClick = { choosePreset(k) }, title = { Text(p.formula) })
                    }
                }
            }
            LabRow {
                SegmentControl {
                    SPEED_LABELS.forEachIndexed { k, l -> SegmentItem(selected = speedIdx == k, onClick = { speedIdx = k }, title = { Text(l) }) }
                }
                Spacer(Modifier.width(20.dp))
                LabSwitch(tr("键角", "Angles"), showAngles) { showAngles = it; poseDirty = true }
                LabSwitch(tr("球面模型", "Sphere model"), showSphere) { showSphere = it; poseDirty = true }
                Button(onClick = { reassemble() }) { Text(tr("重新组装", "Reassemble")) }
            }
        }
    }

    // ---------------------------------------------------------------- meshes

    /** A lone-pair balloon about +Y, origin at its bulb centre (the model's lone-pair point). */
    private fun lobeMesh(): MeshData {
        val y0 = 0.030f; val y1 = LOBE_TIP; val rMax = LOBE_R
        val tc = (LOBE_C - y0) / (y1 - y0)
        val p = (ln(0.5) / ln(tc.toDouble())).toFloat()          // widest exactly at the lone-pair point
        val rings = 22; val seg = 24
        val pos = FloatArray((rings + 1) * (seg + 1) * 3); val nrm = FloatArray(pos.size); val uv = FloatArray((rings + 1) * (seg + 1) * 2)
        fun prof(t: Float) = rMax * sin(PI.toFloat() * t.coerceIn(0f, 1f).pow(p)).coerceAtLeast(0f).pow(0.62f)
        var v = 0
        for (r in 0..rings) {
            val t = r.toFloat() / rings
            val y = y0 + (y1 - y0) * t; val rad = prof(t)
            val dt = 0.5f / rings
            val dr = (prof(t + dt) - prof(t - dt)) / ((y1 - y0) * 2 * dt)
            for (s in 0..seg) {
                val a = 2 * PI.toFloat() * s / seg; val ca = cos(a); val sa = sin(a)
                pos[v * 3] = rad * ca; pos[v * 3 + 1] = y - LOBE_C; pos[v * 3 + 2] = rad * sa
                val nl = sqrt(1 + dr * dr)
                nrm[v * 3] = ca / nl; nrm[v * 3 + 1] = -dr / nl; nrm[v * 3 + 2] = sa / nl
                if (r == 0) { nrm[v * 3] = 0f; nrm[v * 3 + 1] = -1f; nrm[v * 3 + 2] = 0f }
                if (r == rings) { nrm[v * 3] = 0f; nrm[v * 3 + 1] = 1f; nrm[v * 3 + 2] = 0f }
                uv[v * 2] = s.toFloat() / seg; uv[v * 2 + 1] = t
                v++
            }
        }
        val idx = IntArray(rings * seg * 6); var k = 0
        for (r in 0 until rings) for (s in 0 until seg) {
            val a = r * (seg + 1) + s; val b = a + seg + 1
            idx[k++] = a; idx[k++] = a + 1; idx[k++] = b
            idx[k++] = a + 1; idx[k++] = b + 1; idx[k++] = b
        }
        return MeshData(pos, nrm, uv, idx)
    }

    /** Unit isosceles wedge: apex at the origin, base corners (1, -1, 0) and (1, 1, 0). */
    private fun wedgeMesh() = MeshData(floatArrayOf(0f, 0f, 0f, 1f, -1f, 0f, 1f, 1f, 0f), floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f),
        floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f), intArrayOf(0, 1, 2))

    private fun circle(r: Float, y: Float, n: Int) = FloatArray(n * 3) { k ->
        val a = 2 * PI * (k / 3) / n
        when (k % 3) { 0 -> (r * cos(a)).toFloat(); 1 -> y; else -> (r * sin(a)).toFloat() }
    }

    // ---------------------------------------------------------------- small maths

    private fun ln(x: Double) = kotlin.math.ln(x)
    private fun dim(c: Rgb, k: Float) = Rgb(c.r * k, c.g * k, c.b * k)
    private fun dot(a: FloatArray, b: FloatArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
    private fun len(a: FloatArray) = sqrt(dot(a, a))
    private fun norm(a: FloatArray): FloatArray { val l = len(a).coerceAtLeast(1e-12f); return floatArrayOf(a[0] / l, a[1] / l, a[2] / l) }
    private fun cross(a: FloatArray, b: FloatArray) = floatArrayOf(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])

    /** Rotation whose columns are the images of the local x, y, z axes. */
    private fun quatFromBasis(x: FloatArray, y: FloatArray, z: FloatArray): Q =
        quatFromMatrix(x[0], y[0], z[0], x[1], y[1], z[1], x[2], y[2], z[2])

    /** Row-major 3x3 (Double) -> quaternion. */
    private fun quatOf(m: DoubleArray): Q = quatFromMatrix(m[0].toFloat(), m[1].toFloat(), m[2].toFloat(), m[3].toFloat(), m[4].toFloat(),
        m[5].toFloat(), m[6].toFloat(), m[7].toFloat(), m[8].toFloat())

    private fun quatFromMatrix(m00: Float, m01: Float, m02: Float, m10: Float, m11: Float, m12: Float, m20: Float, m21: Float, m22: Float): Q {
        val tr = m00 + m11 + m22
        val q = when {
            tr > 0f -> { val s = sqrt(tr + 1f) * 2f; Q((m21 - m12) / s, (m02 - m20) / s, (m10 - m01) / s, 0.25f * s) }
            m00 > m11 && m00 > m22 -> { val s = sqrt(1f + m00 - m11 - m22) * 2f; Q(0.25f * s, (m01 + m10) / s, (m02 + m20) / s, (m21 - m12) / s) }
            m11 > m22 -> { val s = sqrt(1f + m11 - m00 - m22) * 2f; Q((m01 + m10) / s, 0.25f * s, (m12 + m21) / s, (m02 - m20) / s) }
            else -> { val s = sqrt(1f + m22 - m00 - m11) * 2f; Q((m02 + m20) / s, (m12 + m21) / s, 0.25f * s, (m10 - m01) / s) }
        }
        val l = sqrt(q.x * q.x + q.y * q.y + q.z * q.z + q.w * q.w).coerceAtLeast(1e-12f)
        return Q(q.x / l, q.y / l, q.z / l, q.w / l)
    }

    private fun slerp(a: Q, b0: Q, t: Float): Q {
        var b = b0
        var d = a.x * b.x + a.y * b.y + a.z * b.z + a.w * b.w
        if (d < 0f) { b = Q(-b.x, -b.y, -b.z, -b.w); d = -d }
        if (d > 0.9995f || t <= 0f || t >= 1f) {
            if (t >= 1f) return b
            if (t <= 0f) return a
            val q = Q(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t, a.w + (b.w - a.w) * t)
            val l = sqrt(q.x * q.x + q.y * q.y + q.z * q.z + q.w * q.w); return Q(q.x / l, q.y / l, q.z / l, q.w / l)
        }
        val th = acos(d); val s = sin(th)
        val wa = sin((1 - t) * th) / s; val wb = sin(t * th) / s
        return Q(a.x * wa + b.x * wb, a.y * wa + b.y * wb, a.z * wa + b.z * wb, a.w * wa + b.w * wb)
    }
}
