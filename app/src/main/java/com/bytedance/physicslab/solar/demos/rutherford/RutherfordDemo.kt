// α 粒子散射 · 原子核的发现 (Geiger & Marsden 1909, Rutherford 1911).
//
// LESSON: shoot alphas at a gold leaf thinner than light's wavelength. Almost all go straight through —
// but about one in ten thousand comes BACK. Thomson's atom (charge spread like a pudding) can bend an
// alpha by a degree at most; only a tiny, heavy, charged nucleus can turn it round. The size that
// matters is the closest approach d = zZe^2/(4 pi eps0 E) (41 fm for 5.5 MeV on gold — 1/3900 of the atom).
//
// WHAT YOU SEE (two scales side by side):
//   LAB (left): a lead-shielded alpha source, a glowing collimated beam, a gold foil on a frame, and around
//   it a ring-shaped zinc-sulphide screen: every alpha that lands makes a green flash. A microscope on a
//   pivot arm swings round the ring (pinch-and-drag it) and counts flashes at its angle against the
//   Rutherford prediction. Under the ring, the counts so far as a log histogram (green bars) with the
//   1/sin^4(theta/2) law drawn over it (white line) — the bars grow into the line as the run goes on.
//   ATOM (right, magnified ~10^12 x): one nucleus, the dashed red sphere of radius d, and 24 exact Coulomb
//   hyperbolas in 3-D (impact parameters 3..80 fm, every azimuth, trimmed to the lens sphere) with alphas riding them — slowing,
//   turning, flying off. The cyan one is yours: pinch-drag the aim marker to set b and read theta live.
//   Pinch the nucleus: Thomson's atom — the hyperbolas straighten, the screen goes dark beyond a few degrees.
//
// Physics: core/rutherford/RutherfordCore (closed-form hyperbola + time law, checked against an RK4 orbit;
// Monte-Carlo counter whose histogram the tests check against 1/sin^4 bin by bin).
// Performance: the 24 trajectory tubes are built only when energy / foil / model change (debounced,
// uploaded round-robin); per frame only transforms move. The aimed path (240 + 60 verts) is rewritten
// only while it changes.

package com.bytedance.physicslab.solar.demos.rutherford

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bytedance.physicslab.solar.core.P3
import com.bytedance.physicslab.solar.core.rutherford.AlphaCounter
import com.bytedance.physicslab.solar.core.rutherford.CoulombHyperbola
import com.bytedance.physicslab.solar.core.rutherford.Rutherford
import com.bytedance.physicslab.solar.demos.cyclotron.MeshKit
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
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object RutherfordDemo : SpecDemo() {
    override val id = "rutherford"
    override val title = "α 粒子散射 · 原子核的发现"   // i18n-ok: Chinese title; the English one is DemoRegistry.TITLE_EN
    override val credit = "Rutherford · 1911"

    // ---------------------------------------------------------------- controls (persist across rebuilds)
    var foilIdx = 0; var eMeV = 5.5f; var thomson = false; var speedIdx = 1; var beamOn = true
    var microDeg = 60; var aimFm = 30.0
    private val RATES = doubleArrayOf(1e3, 1e4, 1e5)                  // alphas per second through the foil
    private val BEAD_V = floatArrayOf(0.55f, 1f, 1.7f)                 // atom-window playback
    private val SPEED_LABELS get() = listOf(tr("慢 10³/s", "Slow 10³/s"), tr("中 10⁴/s", "Mid 10⁴/s"), tr("快 10⁵/s", "Fast 10⁵/s"))
    private val MICRO_PRESETS = intArrayOf(20, 45, 60, 90, 120, 150, 170)
    private val AIM_PRESETS = doubleArrayOf(70.0, 40.0, 20.0, 10.0, 4.0)

    // ---------------------------------------------------------------- layout (volume metres)
    private const val PLINTH_Y = -0.262f
    private const val SLAB_D = 0.62f                                    // shallow slab: no empty foreground
    private const val SLAB_Z = 0.07f
    private const val TOP = -0.2445f
    private val F0 = P3(-0.50f, 0.085f, 0.14f)                        // the foil = centre of the screen ring
    private const val R = 0.31f                                         // screen radius
    private const val TILT = 0.698f                                     // ring plane leans back 40 deg (about the beam): a 0.62 m ring in the 0.62 m tall band
    private const val SRC_X = -0.215f                                   // source block centre (ring-local x)
    private const val SRC_EXIT = -0.175f
    private const val BAR_R0 = R + 0.016f                               // histogram bars start here (lower half)
    private const val LPD = 0.0165f                                     // bar length per decade
    private const val FLOOR = 1e-7                                      // per-alpha fraction per 5 deg at bar length 0
    private const val LMAX = 0.100f
    private val W0 = P3(0.52f, 0.11f, 0.08f)                         // nucleus = centre of the atom window
    private const val R_S = 0.29f                                       // the atom "lens": a sphere of 125 fm around the nucleus
    private const val FM = R_S / 125f                                   // display metres per femtometre (2.3e12 x)
    private const val B_MAX_FM = 80.0                                   // every b <= 80 fm enters the sphere (r_min <= 113 fm at 4 MeV on Au)
    private const val R_CLIP = R_S - 0.004f                             // tubes and beads end inside the lens
    private const val NB = 24                                           // trajectory bank
    private const val NP = 44                                           // samples per bank trajectory
    private const val NA = 48                                           // samples on the aimed trajectory
    private const val NARC = 12
    private const val V_BEAD = 0.20f                                    // m/s (v at infinity) at "mid"
    private const val NFL = 80                                          // flash pool
    private const val NSP = 24                                          // spark pool
    private const val NBB = 10                                          // beam beads
    private const val NBAR = 35
    private const val WIN_R = 0.33f                                     // the dark porthole behind the atom
    private const val TRAJ_W = 0.0018f
    private const val AIM_W = 0.0034f
    private const val THEORY_W = 0.0024f

    // ---------------------------------------------------------------- state
    private val counter = AlphaCounter(1911)
    private var vis = java.util.Random(7)
    private var lastKey = ""
    private var bankDirty = false
    private var sinceChange = 0f
    private var barT = 0f
    private var clock = 0f                                               // seconds of demo time
    private var beadClock = 0f                                           // metres travelled at v_inf
    private var coreAcc = 0f
    private var eyeGlow = 0f
    private var microCont = 60.0
    private var aimKey = ""
    private var dragAim0 = 0.0
    private var pivotDeg = -1
    private val pBand = DoubleArray(3)

    private lateinit var lab: Node; private lateinit var pivot: Node; private lateinit var microNode: Node
    private lateinit var foilNode: Node; private lateinit var sourceGlow: Node; private lateinit var sourceHalo: Node
    private lateinit var beamIn: Node; private lateinit var beamInHalo: Node; private lateinit var beamOut: Node; private lateinit var beamOutHalo: Node
    private lateinit var spot: Node; private lateinit var spotCore: Node; private lateinit var eye: Node
    private val flashes = arrayOfNulls<Node>(NFL); private val flashAge = FloatArray(NFL) { 9f }; private var flashNext = 0
    private val sparks = arrayOfNulls<Node>(NSP); private val sparkAge = FloatArray(NSP) { 9f }
    private val sparkTo = Array(NSP) { FloatArray(3) }
    private val beamBeads = arrayOfNulls<Node>(NBB)
    private val bars = arrayOfNulls<Node>(NBAR)
    private lateinit var theory: Node; private lateinit var theoryMesh: MeshData
    // atom window
    private lateinit var atom: Node; private lateinit var nucleus: Node; private lateinit var nucGlow: Node
    private lateinit var dSphere: Node; private lateinit var dRing: Node; private lateinit var pudding: Node
    private val bankNode = arrayOfNulls<Node>(NB); private val bankMesh = arrayOfNulls<MeshData>(NB)
    private val bankPath = Array(NB) { FloatArray(NP * 3) }; private val bankT = Array(NB) { FloatArray(NP) }
    private val bankLine = arrayOfNulls<Node>(NB)
    private val beads = arrayOfNulls<Node>(NB); private val beadGlow = arrayOfNulls<Node>(NB); private val ghost = arrayOfNulls<Node>(NB)
    private val beadGap = FloatArray(NB); private val beadOff = FloatArray(NB)
    private lateinit var aimNode: Node; private lateinit var aimPath: Node; private lateinit var aimMesh: MeshData
    private lateinit var aimArc: Node; private lateinit var aimArcMesh: MeshData; private lateinit var aimGuide: Node
    private lateinit var aimBead: Node; private lateinit var aimBeadGlow: Node
    private val aimPts = FloatArray(NA * 3); private val aimT = FloatArray(NA)
    private val tmp = FloatArray(3)

    private val foil get() = Rutherford.FOILS[foilIdx]
    private val d get() = Rutherford.closestApproachFm(foil.z, eMeV.toDouble())
    private val nt get() = Rutherford.arealDensity(foil)
    private val sigma get() = Rutherford.multipleScatteringSigma(foil, d)
    private fun bOf(k: Int) = 3.0 + (B_MAX_FM - 3.0) * k / (NB - 1)
    private fun phiOf(k: Int) = 0.6 + k * 2.399963                    // golden angle: every azimuth

    // ---------------------------------------------------------------- materials (sRGB)
    private val ALPHA = Rgb(1.0f, 0.64f, 0.28f)
    private val ALPHA_HOT = Rgb(1.0f, 0.93f, 0.80f)
    private val ZNS = Rgb(0.50f, 1.0f, 0.62f)
    private val AIM = Rgb(0.40f, 0.88f, 1.0f)
    private val D_RED = Rgb(1.0f, 0.36f, 0.28f)
    private val PUD = Rgb(1.0f, 0.42f, 0.62f)
    private val FOIL_C = listOf(Rgb(1.0f, 0.80f, 0.38f), Rgb(0.93f, 0.93f, 0.96f), Rgb(0.80f, 0.82f, 0.85f))
    private val alu = Mat.Pbr(Rgb(0.80f, 0.82f, 0.85f), metallic = 1f, roughness = 0.32f)
    private val darkSteel = Mat.Pbr(Rgb(0.22f, 0.23f, 0.25f), metallic = 0.85f, roughness = 0.40f)
    private val lead = Mat.Pbr(Rgb(0.34f, 0.35f, 0.39f), metallic = 0.55f, roughness = 0.55f)
    private val brass = Mat.Pbr(Rgb(0.92f, 0.74f, 0.42f), metallic = 1f, roughness = 0.25f)
    private val blackTube = Mat.Pbr(Rgb(0.07f, 0.07f, 0.08f), metallic = 0.3f, roughness = 0.35f)
    private val screenMat = Mat.Pbr(Rgb(0.55f, 0.62f, 0.56f), metallic = 0f, roughness = 0.85f, emissive = Rgb(0.03f, 0.06f, 0.04f))
    private val panelGlass = Mat.Pbr(Rgb(0.05f, 0.06f, 0.08f), metallic = 0f, roughness = 0.08f, alpha = 0.82f)
    private val slabTex by lazy {
        ProcTex.bakePlanet(256, 128, Rgb(0.07f, 0.075f, 0.085f), Rgb(0.12f, 0.125f, 0.14f), Rgb(0.20f, 0.205f, 0.22f), 0f, 77)
    }
    private val glowTex: Image by lazy {
        val n = 64; val img = Image(n, n)
        for (y in 0 until n) for (x in 0 until n) {
            val dx = (x + 0.5f) / n * 2 - 1; val dy = (y + 0.5f) / n * 2 - 1
            val a = (1f - sqrt(dx * dx + dy * dy)).coerceIn(0f, 1f).pow(2.2f)
            img.px[y * n + x] = ProcTex.argb(1f, a, a, a)
        }
        img
    }
    /** ZnS scintillation: a hot white-green core in a soft halo (broader than the generic glow). */
    private val flashTex: Image by lazy {
        val n = 64; val img = Image(n, n)
        for (y in 0 until n) for (x in 0 until n) {
            val dx = (x + 0.5f) / n * 2 - 1; val dy = (y + 0.5f) / n * 2 - 1
            val r = sqrt(dx * dx + dy * dy)
            val halo = (1f - r).coerceIn(0f, 1f).pow(1.5f)
            val core = (1f - r / 0.28f).coerceIn(0f, 1f)
            val a = (halo + 0.8f * core).coerceAtMost(1f)
            img.px[y * n + x] = ProcTex.argb(1f, (a * (0.75f + 0.25f * core)).coerceAtMost(1f), a, (a * (0.8f + 0.2f * core)).coerceAtMost(1f))
        }
        img
    }
    private val flashMat by lazy { Mat.Unlit(ZNS, 1f, flashTex, Blend.ADD) }
    private val sparkMat by lazy { Mat.Unlit(ALPHA_HOT, 1f, glowTex, Blend.ADD) }
    private val trajMat = Mat.Unlit(ALPHA, 0.30f, blend = Blend.ADD)
    private val lineMat = Mat.Unlit(ALPHA, 0.26f, blend = Blend.ADD)
    private val beadMat = Mat.Unlit(ALPHA_HOT)
    private val nucMat = Mat.Unlit(Rgb(1.0f, 0.82f, 0.46f))
    private val nucThomsonMat = Mat.Unlit(PUD, 0.30f, blend = Blend.ADD)

    // ---------------------------------------------------------------- build

    override fun buildSpec(cacheDir: File): Node {
        // a rebuild must start from the same state: counter, pools, clocks, caches
        counter.reset(reseed = true); vis = java.util.Random(7)
        lastKey = ""; aimKey = ""; bankDirty = false; sinceChange = 1f; barT = 0f
        clock = 0f; beadClock = 0f; coreAcc = 0f; eyeGlow = 0f; microCont = microDeg.toDouble(); flashNext = 0
        flashAge.fill(9f); sparkAge.fill(9f); pivotDeg = -1

        val root = Node("RutherfordRoot")
        val unitCyl = MeshGen.cylinder(1f, 1f, 16)
        val sphere = MeshGen.sphere(1f, 14, 28, MeshGen.SphereUv.EQUIRECT)
        val unitBox = MeshGen.box(1f, 1f, 1f)
        val quad = quadMesh()

        root.add("Plinth", MeshGen.box(1.78f, 0.035f, SLAB_D), Mat.Pbr(Rgb(1f, 1f, 1f), roughness = 0.55f, tex = slabTex), P3(0f, PLINTH_Y, SLAB_Z))
        // trim sits INSIDE the slab: only its 5 mm overhang shows, as an edge
        root.add("PlinthBand", MeshGen.box(1.79f, 0.008f, SLAB_D + 0.01f), alu, P3(0f, PLINTH_Y, SLAB_Z))

        buildLab(root, unitCyl, sphere, unitBox, quad)
        buildAtom(root, unitCyl, sphere, quad)

        // warm start: the source has been running for a while (2 million alphas through the foil)
        applyParams(force = true)
        counter.fire(2e6, d, nt, sigma, !thomson)
        updateBars()
        update(0f)
        return root
    }

    private fun buildLab(root: Node, unitCyl: MeshData, sphere: MeshData, unitBox: MeshData, quad: MeshData) {
        val toX = Q.axisAngle(0f, 0f, 1f, (-PI / 2).toFloat())          // cylinder axis y -> x
        val toZ = Q.axisAngle(1f, 0f, 0f, (PI / 2).toFloat())           // cylinder axis y -> z
        // pedestal under the chamber's back plate
        root.add("Pedestal", MeshGen.box(0.18f, 0.030f, 0.08f), darkSteel, P3(F0.x, TOP + 0.015f, 0.32f))
        root.add("PedestalFoot", MeshGen.box(0.26f, 0.010f, 0.12f), alu, P3(F0.x, TOP + 0.005f, 0.32f))

        lab = root.add(Node("Lab")).also { it.pos = F0; it.rot = Q.axisAngle(1f, 0f, 0f, -TILT) }
        // the chamber back plate (dark glass), the ZnS screen ring and its housing
        lab.add("BackPlate", MeshGen.cylinder(R + 0.05f, 0.010f, 96), panelGlass, P3(0f, 0f, -0.058f)).rot = toZ
        lab.add("BackRim", MeshGen.tube(MeshKit.circle(R + 0.05f, 128, "xy", 0f, 0f, -0.052f), 0.0035f, 6, true), alu)
        lab.add("Screen", MeshKit.sectorPrism(R, R + 0.0025f, 0f, 2 * PI.toFloat(), -0.016f, 0.016f, 160), screenMat)
        lab.add("ScreenHousing", MeshKit.sectorPrism(R + 0.0025f, R + 0.012f, 0f, 2 * PI.toFloat(), -0.019f, 0.019f, 160), darkSteel)
        lab.add("Ticks", ticks(), Mat.Unlit(Rgb(0.85f, 0.92f, 1.0f), 0.65f, blend = Blend.TRANSPARENT))
        // hub on the back plate: the microscope arm pivots on it, the foil frame hangs from it
        lab.add("Hub", MeshGen.cylinder(0.012f, 0.020f, 24), darkSteel, P3(0f, 0f, -0.044f)).rot = toZ

        // the gold foil on its frame (pinch: next metal)
        foilNode = lab.add("Foil", MeshGen.box(0.0016f, 0.072f, 0.072f), foilMat(), P3(0f, 0f, 0f)).also { it.tapRadius = 0.055f }
        for (s in floatArrayOf(-1f, 1f)) {
            lab.add("FoilFrameH", MeshGen.box(0.004f, 0.006f, 0.084f), alu, P3(0f, s * 0.039f, 0f))
            lab.add("FoilFrameV", MeshGen.box(0.004f, 0.084f, 0.006f), alu, P3(0f, 0f, s * 0.039f))
        }

        // the source: a lead block with a collimator hole (pinch: beam on / off)
        lab.add("Source", MeshGen.box(0.076f, 0.076f, 0.076f), lead, P3(SRC_X, 0f, 0f)).tapRadius = 0.06f
        lab.add("SourceMount", MeshGen.box(0.03f, 0.03f, 0.020f), darkSteel, P3(SRC_X, 0f, -0.048f))
        lab.add("Collimator", MeshGen.cylinder(0.011f, 0.004f, 24), blackTube, P3(SRC_EXIT + 0.001f, 0f, 0f)).rot = toX
        sourceGlow = lab.add("SourceGlow", sphere, Mat.Unlit(ALPHA_HOT), P3(SRC_EXIT + 0.003f, 0f, 0f)).also { it.uniformScale(0.0065f) }
        sourceHalo = lab.add("SourceHalo", quad, Mat.Unlit(ALPHA, 0.9f, glowTex, Blend.ADD), P3(SRC_EXIT + 0.004f, 0f, 0f)).also { it.billboard = true; it.uniformScale(0.030f) }

        // the beam: collimated, then mostly straight through to the bright spot at 0 deg
        val lenIn = -SRC_EXIT; val lenOut = R - 0.004f
        beamIn = lab.add("BeamIn", unitCyl, Mat.Unlit(ALPHA, 0.85f, blend = Blend.ADD), P3(SRC_EXIT / 2, 0f, 0f)).also { it.rot = toX; it.scale = P3(0.0026f, lenIn, 0.0026f) }
        beamInHalo = lab.add("BeamInHalo", unitCyl, Mat.Unlit(ALPHA, 0.16f, blend = Blend.ADD), P3(SRC_EXIT / 2, 0f, 0f)).also { it.rot = toX; it.scale = P3(0.010f, lenIn, 0.010f) }
        beamOut = lab.add("BeamOut", unitCyl, Mat.Unlit(ALPHA, 0.50f, blend = Blend.ADD), P3(lenOut / 2, 0f, 0f)).also { it.rot = toX; it.scale = P3(0.0022f, lenOut, 0.0022f) }
        beamOutHalo = lab.add("BeamOutHalo", unitCyl, Mat.Unlit(ALPHA, 0.09f, blend = Blend.ADD), P3(lenOut / 2, 0f, 0f)).also { it.rot = toX; it.scale = P3(0.009f, lenOut, 0.009f) }
        for (k in 0 until NBB) beamBeads[k] = lab.add("BeamBead$k", sphere, beadMat).also { it.uniformScale(0.0042f) }
        spot = lab.add("Spot", quad, Mat.Unlit(ZNS, 1f, glowTex, Blend.ADD), P3(R - 0.004f, 0f, 0f)).also { it.billboard = true; it.uniformScale(0.034f) }
        spotCore = lab.add("SpotCore", sphere, Mat.Unlit(Rgb(0.85f, 1f, 0.88f)), P3(R - 0.004f, 0f, 0f)).also { it.uniformScale(0.0055f) }

        for (k in 0 until NFL) flashes[k] = lab.add("Flash$k", quad, flashMat).also { it.billboard = true; it.visible = false; it.uniformScale(0.02f) }
        for (k in 0 until NSP) sparks[k] = lab.add("Spark$k", quad, sparkMat).also { it.billboard = true; it.visible = false; it.uniformScale(0.013f) }

        // the microscope on its pivot arm (pinch-drag round the ring; pinch: next preset angle)
        pivot = lab.add(Node("MicroPivot"))
        pivot.add("MicroArm", MeshGen.box(R + 0.050f, 0.012f, 0.006f), darkSteel, P3((R + 0.070f) / 2, 0f, -0.031f))
        pivot.add("MicroBracket", MeshGen.box(0.012f, 0.012f, 0.030f), darkSteel, P3(R + 0.058f, 0f, -0.017f))
        microNode = pivot.add(Node("Microscope")).also { it.pos = P3(R + 0.058f, 0f, 0f); it.tapRadius = 0.06f }
        microNode.add("MicroTube", MeshGen.cylinder(0.0155f, 0.086f, 24), blackTube).rot = toX
        microNode.add("MicroCollar", MeshGen.cylinder(0.0185f, 0.012f, 24), brass, P3(-0.039f, 0f, 0f)).rot = toX
        microNode.add("MicroEyepiece", MeshGen.cylinder(0.0110f, 0.026f, 20), brass, P3(0.055f, 0f, 0f)).rot = toX
        eye = microNode.add("MicroEyeGlow", quad, Mat.Unlit(ZNS, 1f, glowTex, Blend.ADD), P3(0.070f, 0f, 0f)).also { it.billboard = true; it.visible = false }
        val va = (2.5 * PI / 180).toFloat()
        pivot.add("MicroView", MeshGen.tube(MeshKit.arc(R - 0.0035f, -va, va, 9), 0.0026f, 6, false), Mat.Unlit(ZNS, 0.55f, blend = Blend.ADD))

        // the counts so far, log scale, on the lower half of the ring + the theory line
        for (j in 0 until NBAR) bars[j] = lab.add("Bar$j", unitBox, Mat.Unlit(ZNS, 0.42f, blend = Blend.ADD)).also { it.visible = false }
        theoryMesh = MeshGen.tube(theoryPath(), THEORY_W, 4, false)
        theory = lab.add("Theory", theoryMesh, Mat.Unlit(Rgb(1f, 0.97f, 0.90f), 1f, blend = Blend.OPAQUE))
    }

    private fun buildAtom(root: Node, unitCyl: MeshData, sphere: MeshData, quad: MeshData) {
        val toX = Q.axisAngle(0f, 0f, 1f, (-PI / 2).toFloat())
        val toZ = Q.axisAngle(1f, 0f, 0f, (PI / 2).toFloat())
        // the window: a dark glass porthole behind the 3-D region, on a stand
        val backZ = W0.z - R_S - 0.03f
        root.add("WinBack", MeshGen.cylinder(WIN_R, 0.010f, 96), panelGlass, P3(W0.x + 0.04f, W0.y, backZ)).rot = toZ
        val postTop = W0.y - WIN_R
        root.add("WinPost", unitCyl, alu, P3(W0.x + 0.04f, (postTop + TOP) / 2, backZ)).scale = P3(0.011f, postTop - TOP, 0.011f)
        root.add("WinFoot", MeshGen.box(0.20f, 0.012f, 0.10f), alu, P3(W0.x + 0.04f, TOP + 0.006f, backZ))

        atom = root.add(Node("Atom")).also { it.pos = W0 }
        // a faint horizontal ring: the region is a 3-D ball, not a picture on the glass
        atom.add("Equator", MeshGen.tube(MeshKit.circle(R_S, 128, "xz"), 0.0010f, 4, true), Mat.Unlit(Rgb(0.55f, 0.72f, 1.0f), 0.22f, blend = Blend.ADD))
        // the lens rim: a billboard ring just outside the sphere's silhouette, so it outlines the ball from any side
        atom.add("LensRim", ringMesh(0.988f), Mat.Unlit(Rgb(0.70f, 0.82f, 1.0f), 0.55f, blend = Blend.ADD)).also { it.billboard = true; it.uniformScale(R_S * 1.035f) }
        // beam axis and the entrance disc the alphas come through
        atom.add("Axis", unitCyl, Mat.Unlit(Rgb(0.6f, 0.75f, 0.95f), 0.20f, blend = Blend.ADD)).also { it.rot = toX; it.scale = P3(0.0009f, 2 * R_CLIP, 0.0009f) }
        val rho = (B_MAX_FM * FM).toFloat() + 0.010f
        atom.add("Entrance", MeshGen.tube(MeshKit.circle(rho, 96, "yz", -sqrt(R_S * R_S - rho * rho), 0f, 0f), 0.0012f, 5, true),
            Mat.Unlit(Rgb(0.55f, 0.75f, 1.0f), 0.35f, blend = Blend.ADD))
        // scale bar: 50 fm under the nucleus
        val sb = 50f * FM
        val zb = -R_S - 0.03f + 0.012f                                  // on the porthole glass
        atom.add("ScaleBar", MeshGen.tube(floatArrayOf(-sb / 2, -0.285f, zb, sb / 2, -0.285f, zb), 0.0018f, 5, false), Mat.Unlit(Rgb(0.85f, 0.9f, 1f), 0.7f, blend = Blend.ADD))
        for (s in floatArrayOf(-1f, 1f)) atom.add("ScaleTick", MeshGen.tube(floatArrayOf(s * sb / 2, -0.278f, zb, s * sb / 2, -0.292f, zb), 0.0014f, 5, false),
            Mat.Unlit(Rgb(0.85f, 0.9f, 1f), 0.6f, blend = Blend.ADD))

        // the nucleus (pinch: Rutherford <-> Thomson), the sphere of closest approach, Thomson's pudding
        pudding = atom.add("Pudding", sphere, Mat.Unlit(PUD, 0.035f, blend = Blend.ADD)).also { it.uniformScale(R_S * 0.98f); it.visible = false }
        dSphere = atom.add("DSphere", sphere, Mat.Unlit(D_RED, 0.07f, blend = Blend.ADD))
        dRing = atom.add("DRing", ringMesh(0.955f), Mat.Unlit(D_RED, 0.55f, blend = Blend.ADD)).also { it.billboard = true }
        nucGlow = atom.add("NucleusGlow", quad, Mat.Unlit(Rgb(1.0f, 0.56f, 0.22f), 0.95f, glowTex, Blend.ADD)).also { it.billboard = true }
        nucleus = atom.add("Nucleus", sphere, nucMat)

        // the trajectory bank: exact hyperbolas (Rutherford) or straight lines (Thomson)
        for (k in 0 until NB) {
            writeBankPath(k)
            bankMesh[k] = MeshGen.tube(bankPath[k], TRAJ_W, 4, false)
            bankNode[k] = atom.add("Traj$k", bankMesh[k]!!, trajMat)
            val b = (bOf(k) * FM).toFloat(); val ph = phiOf(k)
            bankLine[k] = atom.add("TLine$k", unitCyl, lineMat, P3(0f, b * cos(ph).toFloat(), b * sin(ph).toFloat())).also {
                it.rot = toX; it.scale = P3(TRAJ_W, 2 * chord(bOf(k)), TRAJ_W); it.visible = false
            }
            beadGlow[k] = atom.add("AlphaGlow$k", quad, Mat.Unlit(ALPHA, 0.9f, glowTex, Blend.ADD)).also { it.billboard = true; it.uniformScale(0.020f) }
            ghost[k] = atom.add("AlphaGhost$k", sphere, Mat.Unlit(ALPHA, 0.55f, blend = Blend.ADD)).also { it.uniformScale(0.0035f) }
            beads[k] = atom.add("Alpha$k", sphere, beadMat).also { it.uniformScale(0.0058f) }
            beadGap[k] = 0.03f + 0.10f * vis.nextFloat(); beadOff[k] = 0.6f * vis.nextFloat()
        }

        // the aimed trajectory (cyan), its marker (pinch-drag: b), the undeflected line and the angle arc
        writeAimPath()
        aimMesh = MeshGen.tube(aimPts, AIM_W, 4, false)
        aimPath = atom.add("AimPath", aimMesh, Mat.Unlit(AIM, 0.95f, blend = Blend.ADD))
        aimArcMesh = MeshGen.tube(arcPath(), 0.0016f, 4, false)
        aimArc = atom.add("AimArc", aimArcMesh, Mat.Unlit(Rgb(1f, 1f, 1f), 0.8f, blend = Blend.ADD))
        aimGuide = atom.add("AimGuide", unitCyl, Mat.Unlit(Rgb(1f, 1f, 1f), 0.28f, blend = Blend.ADD)).also { it.rot = toX }
        aimNode = atom.add(Node("Aim")).also { it.tapRadius = 0.05f }
        aimNode.add("AimRing", ringMesh(0.78f), Mat.Unlit(AIM, 0.95f, blend = Blend.ADD)).also { it.billboard = true; it.uniformScale(0.020f) }
        aimNode.add("AimDot", sphere, Mat.Unlit(Rgb(0.85f, 0.97f, 1f))).uniformScale(0.0045f)
        aimBeadGlow = atom.add("AimAlphaGlow", quad, Mat.Unlit(AIM, 1f, glowTex, Blend.ADD)).also { it.billboard = true; it.uniformScale(0.024f) }
        aimBead = atom.add("AimAlpha", sphere, Mat.Unlit(Rgb(0.92f, 0.99f, 1f))).also { it.uniformScale(0.0065f) }
    }

    // ---------------------------------------------------------------- geometry builders

    /** Bank trajectory k in window-local metres, sampled uniformly in the hyperbolic anomaly. */
    private fun writeBankPath(k: Int) = hyperPath(bOf(k), phiOf(k), NP, bankPath[k], bankT[k])

    /** Half-length of the straight chord at impact parameter [bFm] inside the lens. */
    private fun chord(bFm: Double): Float { val b = (bFm * FM).toFloat(); return sqrt(maxOf(R_CLIP * R_CLIP - b * b, 1e-6f)) }

    /**
     * The exact hyperbola trimmed to the lens sphere: r(F) = a (e cosh F + 1) is even in F, so the alpha
     * enters at -F1 and leaves at +F1 with cosh F1 = (R/a - 1)/e. Every b <= B_MAX_FM gets in (r_min < R).
     */
    private fun hyperPath(bFm: Double, phi: Double, n: Int, out: FloatArray, times: FloatArray) {
        val dFm = d
        val unit = dFm * FM                                       // display metres per d
        val h = CoulombHyperbola(maxOf(abs(bFm), 0.3) / dFm)
        val c = ((R_CLIP / unit) / h.a - 1) / h.e
        val f1 = if (c > 1.0) ln(c + sqrt(c * c - 1)) else 1e-3   // acosh
        val f0 = -f1
        val cp = cos(phi); val sp = sin(phi)
        val t0 = h.time(f0)
        for (i in 0 until n) {
            val f = f0 + (f1 - f0) * i / (n - 1)
            val y = h.y(f) * unit
            out[i * 3] = (h.x(f) * unit).toFloat(); out[i * 3 + 1] = (y * cp).toFloat(); out[i * 3 + 2] = (y * sp).toFloat()
            times[i] = ((h.time(f) - t0) * unit).toFloat()           // metres travelled at v_inf
        }
    }

    private fun writeAimPath() {
        if (thomson) {
            val y = (aimFm * FM).toFloat()
            val c = chord(aimFm)
            for (i in 0 until NA) { aimPts[i * 3] = -c + 2 * c * i / (NA - 1); aimPts[i * 3 + 1] = y; aimPts[i * 3 + 2] = 0f; aimT[i] = 2 * c * i / (NA - 1) }
        } else hyperPath(aimFm, if (aimFm >= 0) 0.0 else PI, NA, aimPts, aimT)
    }

    private val aimTheta get() = Rutherford.deflection(aimFm, d)

    /** Arc at the asymptotes' crossing (-d/2, b) from the straight-on direction to the outgoing one. */
    private fun arcPath(): FloatArray {
        val a = (d / 2 * FM).toFloat(); val b = (aimFm * FM).toFloat(); val s = if (aimFm >= 0) 1f else -1f
        val th = aimTheta.toFloat(); val r = 0.060f
        return FloatArray(NARC * 3) { i ->
            val ang = th * (i / 3) / (NARC - 1)
            when (i % 3) { 0 -> -a + r * cos(ang); 1 -> b + s * r * sin(ang); else -> 0f }
        }
    }

    /** Theory line on the lower half: bar length of the predicted per-alpha fraction in each 5 deg bin. */
    private fun theoryPath(): FloatArray {
        val n = 69
        val dd = d; val ntt = nt; val sg = sigma
        return FloatArray(n * 3) { i ->
            val c = 7.5 + 2.5 * (i / 3)
            val t1 = (c - 2.5) * PI / 180; val t2 = (c + 2.5) * PI / 180
            val f = if (thomson) Rutherford.thomsonFractionBetween(t1, t2, sg) else Rutherford.fractionBetween(t1, t2, dd, ntt)
            val r = BAR_R0 + barLen(f)
            val a = c * PI / 180
            when (i % 3) { 0 -> (r * cos(a)).toFloat(); 1 -> (-r * sin(a)).toFloat(); else -> 0.006f }
        }
    }

    private fun barLen(f: Double): Float = if (f <= 0) 0f else (LPD * (log10(f) - log10(FLOOR))).toFloat().coerceIn(0f, LMAX)

    private fun ticks(): MeshData {
        val parts = ArrayList<MeshData>()
        for (deg in 0..180 step 10) {
            val a = (deg * PI / 180).toFloat()
            val l = if (deg % 30 == 0) 0.018f else 0.009f
            val r0 = R + 0.012f
            parts += MeshGen.tube(floatArrayOf(r0 * cos(a), r0 * sin(a), 0.0195f, (r0 + l) * cos(a), (r0 + l) * sin(a), 0.0195f), 0.0011f, 4, false)
        }
        return merge(parts)
    }

    private fun merge(parts: List<MeshData>): MeshData {
        val nv = parts.sumOf { it.vertexCount }; val ni = parts.sumOf { it.idx.size }
        val pos = FloatArray(nv * 3); val nrm = FloatArray(nv * 3); val uv = FloatArray(nv * 2); val idx = IntArray(ni)
        var v = 0; var t = 0
        for (p in parts) {
            System.arraycopy(p.pos, 0, pos, v * 3, p.pos.size); System.arraycopy(p.nrm, 0, nrm, v * 3, p.nrm.size)
            System.arraycopy(p.uv, 0, uv, v * 2, p.uv.size)
            for (i in p.idx.indices) idx[t + i] = p.idx[i] + v
            v += p.vertexCount; t += p.idx.size
        }
        return MeshData(pos, nrm, uv, idx)
    }

    private fun quadMesh() = MeshData(
        floatArrayOf(-1f, -1f, 0f, 1f, -1f, 0f, 1f, 1f, 0f, -1f, 1f, 0f),
        floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f),
        floatArrayOf(0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f),
        intArrayOf(0, 1, 2, 0, 2, 3))

    /** Flat ring (inner radius [rIn], outer 1) facing +z, for billboards. */
    private fun ringMesh(rIn: Float): MeshData {
        val n = 64
        val pos = FloatArray((n + 1) * 6); val nrm = FloatArray((n + 1) * 6); val uv = FloatArray((n + 1) * 4)
        for (k in 0..n) for (j in 0..1) {
            val a = 2 * PI * k / n; val r = if (j == 0) rIn else 1f; val v = k * 2 + j
            pos[v * 3] = (r * cos(a)).toFloat(); pos[v * 3 + 1] = (r * sin(a)).toFloat(); nrm[v * 3 + 2] = 1f
            uv[v * 2] = j.toFloat(); uv[v * 2 + 1] = k.toFloat() / n
        }
        val idx = IntArray(n * 6); var t = 0
        for (k in 0 until n) { val a0 = k * 2; idx[t++] = a0; idx[t++] = a0 + 1; idx[t++] = a0 + 2; idx[t++] = a0 + 1; idx[t++] = a0 + 3; idx[t++] = a0 + 2 }
        return MeshData(pos, nrm, uv, idx)
    }

    private fun foilMat() = Mat.Pbr(FOIL_C[foilIdx], metallic = 1f, roughness = if (foilIdx == 2) 0.42f else 0.22f)

    // ---------------------------------------------------------------- parameters

    /** Energy / foil / model changed: reset the run, rescale the nucleus; heavy geometry follows debounced. */
    private fun applyParams(force: Boolean = false) {
        val key = "$foilIdx/$eMeV/$thomson"
        if (key == lastKey && !force) return
        val first = lastKey.isEmpty()
        lastKey = key
        counter.reset()
        updateBars()
        foilNode.setMat(foilMat())
        val dd = (d * FM).toFloat()
        dSphere.uniformScale(dd); dRing.uniformScale(dd)
        dSphere.visible = !thomson; dRing.visible = !thomson
        pudding.visible = thomson
        val nr = if (thomson) 0.026f else (foil.nuclearRadiusFm * FM).toFloat()
        nucleus.uniformScale(nr); nucleus.tapRadius = 0.05f / nr
        nucleus.setMat(if (thomson) nucThomsonMat else nucMat)
        nucGlow.visible = !thomson; nucGlow.uniformScale(nr * 3.6f + 0.006f)
        for (k in 0 until NB) { bankNode[k]!!.visible = !thomson; bankLine[k]!!.visible = thomson }
        if (first) { bankDirty = false; sinceChange = 1f } else { bankDirty = true; sinceChange = 0f }
    }

    /** The debounced part: 24 trajectory tubes + the theory line (uploaded round-robin by the adapter). */
    private fun rebuildGeometry() {
        bankDirty = false
        if (!thomson) for (k in 0 until NB) {
            writeBankPath(k)
            MeshGen.tubeInto(bankMesh[k]!!, bankPath[k], TRAJ_W, 4, false); meshEdited(bankMesh[k]!!)
        }
        MeshGen.tubeInto(theoryMesh, theoryPath(), THEORY_W, 4, false); meshEdited(theoryMesh)
    }

    private fun updateAim() {
        val key = "$aimFm/$lastKey"
        if (key == aimKey) return
        aimKey = key
        writeAimPath()
        MeshGen.tubeInto(aimMesh, aimPts, AIM_W, 4, false); meshEdited(aimMesh)
        val b = (aimFm * FM).toFloat()
        aimNode.pos = P3(aimPts[0], aimPts[1], aimPts[2])               // where the alpha enters the lens
        aimArc.visible = !thomson && abs(aimTheta) > 0.03
        aimGuide.visible = !thomson
        if (!thomson) {
            MeshGen.tubeInto(aimArcMesh, arcPath(), 0.0016f, 4, false); meshEdited(aimArcMesh)
            val a = (d / 2 * FM).toFloat()
            val xe = chord(aimFm)
            aimGuide.pos = P3((xe - a) / 2, b, 0f); aimGuide.scale = P3(0.0010f, xe + a, 0.0010f)
        }
    }

    // ---------------------------------------------------------------- per frame

    override fun update(dt: Float) {
        applyParams()
        sinceChange += dt
        if (bankDirty && sinceChange > 0.25f) rebuildGeometry()
        updateAim()
        clock += dt

        // ---- the experiment: fire alphas, histogram, draw a fair sample of the flashes
        if (beamOn && dt > 0f) {
            val rate = RATES[speedIdx]
            val dd = d; val ntt = nt; val sg = sigma
            val edges = doubleArrayOf(5.0, 30.0, 90.0, 180.0)
            val caps = doubleArrayOf(18.0, 14.0, 8.0)
            for (b in 0 until 3) {
                val t1 = edges[b] * PI / 180; val t2 = edges[b + 1] * PI / 180
                val p = if (thomson) Rutherford.thomsonFractionBetween(t1, t2, sg) else Rutherford.fractionBetween(t1, t2, dd, ntt)
                pBand[b] = if (rate * p <= caps[b]) 1.0 else caps[b] / (rate * p)
            }
            val lo = microDeg - 2.5; val hi = microDeg + 2.5
            counter.fire(rate * dt, dd, ntt, sg, !thomson) { th ->
                val deg = th * 180 / PI
                if (deg >= lo && deg < hi) eyeGlow = minOf(1f, eyeGlow + 0.7f)
                val band = if (deg < 30) 0 else if (deg < 90) 1 else 2
                if (vis.nextDouble() < pBand[band]) launch(th)
            }
            // the straight-through cluster (multiple-scattering core): a few flashes around 0 deg
            coreAcc += dt * 14f
            val pIn = 1 - exp(-Rutherford.THETA_CUT * Rutherford.THETA_CUT / (2 * sg * sg))
            while (coreAcc >= 1f) { coreAcc -= 1f; flashAt(sqrt(-2 * sg * sg * ln(1 - vis.nextDouble() * pIn))) }
        }
        eyeGlow *= exp(-dt / 0.18f)
        eye.visible = eyeGlow > 0.04f
        if (eye.visible) eye.uniformScale(0.006f + 0.018f * eyeGlow)
        stepFlashes(dt)
        stepBeam()

        // ---- microscope pose and the histogram (bars at 4 Hz)
        if (microDeg != pivotDeg) { pivotDeg = microDeg; pivot.rot = Q.axisAngle(0f, 0f, 1f, (microDeg * PI / 180).toFloat()) }
        barT += dt
        if (barT >= 0.25f) { barT = 0f; updateBars() }

        stepAtom(dt)
        updateReadout(); updateLabels()
    }

    /** One drawn alpha: a spark flies from the foil to the screen, then the screen flashes there. */
    private fun launch(th: Double) {
        val side = if (vis.nextBoolean()) 1f else -1f
        val rs = R - 0.004f
        val zj = (vis.nextFloat() - 0.5f) * 0.022f
        val x = rs * cos(th).toFloat(); val y = side * rs * sin(th).toFloat()
        if (th > 12 * PI / 180) {
            val k = sparkAge.indices.firstOrNull { sparkAge[it] >= 0.12f }
            if (k != null) {
                sparkAge[k] = 0f; sparkTo[k][0] = x; sparkTo[k][1] = y; sparkTo[k][2] = zj
                sparks[k]!!.visible = true; sparks[k]!!.pos = P3(0f, 0f, 0f)
                return
            }
        }
        flash(x, y, zj)
    }

    private fun flashAt(th: Double) {
        val side = if (vis.nextBoolean()) 1f else -1f; val rs = R - 0.004f
        flash(rs * cos(th).toFloat(), side * rs * sin(th).toFloat(), (vis.nextFloat() - 0.5f) * 0.022f)
    }

    private fun flash(x: Float, y: Float, z: Float) {
        val k = flashNext; flashNext = (flashNext + 1) % NFL
        flashAge[k] = 0f
        flashes[k]!!.pos = P3(x, y, z); flashes[k]!!.visible = true
    }

    private fun stepFlashes(dt: Float) {
        val life = 0.55f
        for (k in 0 until NFL) {
            if (flashAge[k] >= life) { if (flashes[k]!!.visible) flashes[k]!!.visible = false; continue }
            flashAge[k] += dt
            val u = (flashAge[k] / life).coerceIn(0f, 1f)
            val pop = if (flashAge[k] < 0.05f) 0.7f + 6f * flashAge[k] else 1f
            flashes[k]!!.uniformScale(0.034f * pop * (1f - u).pow(0.6f) + 1e-4f)
            if (flashAge[k] >= life) flashes[k]!!.visible = false
        }
        for (k in 0 until NSP) {
            if (sparkAge[k] >= 0.12f) continue
            sparkAge[k] += dt
            val u = (sparkAge[k] / 0.12f).coerceIn(0f, 1f)
            val to = sparkTo[k]
            sparks[k]!!.pos = P3(to[0] * u, to[1] * u, to[2] * u)
            if (sparkAge[k] >= 0.12f) { sparks[k]!!.visible = false; flash(to[0], to[1], to[2]) }
        }
    }

    private fun stepBeam() {
        val on = beamOn
        beamIn.visible = on; beamInHalo.visible = on; beamOut.visible = on; beamOutHalo.visible = on
        spot.visible = on; spotCore.visible = on
        sourceHalo.uniformScale(if (on) 0.030f else 0.012f)
        val fl = 1f + 0.12f * sin(clock * 23f) * sin(clock * 7.3f)
        spot.uniformScale(0.034f * fl)
        val x0 = SRC_EXIT; val x1 = R - 0.006f; val span = x1 - x0
        val v = 0.32f * BEAD_V[speedIdx]
        for (k in 0 until NBB) {
            val n = beamBeads[k]!!
            n.visible = on
            if (!on) continue
            val u = ((clock * v / span + k.toFloat() / NBB) % 1f)
            n.pos = P3(x0 + span * u, 0f, 0f)
        }
    }

    private fun updateBars() {
        if (bars[0] == null) return
        val fired = counter.fired
        for (j in 0 until NBAR) {
            val lo = 5.0 + 5 * j; val c = lo + 2.5
            val cnt = counter.countBetween(lo, lo + 5)
            val len = if (fired > 0 && cnt > 0) barLen(cnt / fired) else 0f
            val n = bars[j]!!
            n.visible = len > 0.0015f
            if (!n.visible) continue
            val a = c * PI / 180
            val r = BAR_R0 + len / 2
            n.pos = P3((r * cos(a)).toFloat(), (-r * sin(a)).toFloat(), 0f)
            n.rot = Q.axisAngle(0f, 0f, 1f, (-a).toFloat())
            n.scale = P3(len, 0.013f, 0.008f)
        }
    }

    /** Beads ride their trajectories with the exact Coulomb clock (they slow down near the nucleus). */
    private fun stepAtom(dt: Float) {
        beadClock += dt * V_BEAD * BEAD_V[speedIdx]
        for (k in 0 until NB) {
            val total: Float; val pos: P3?; val back: P3?
            if (thomson) {
                val c = chord(bOf(k)); total = 2 * c
                val u = ((beadClock + beadOff[k]) % (total + beadGap[k]))
                val b = (bOf(k) * FM).toFloat(); val ph = phiOf(k)
                val yy = b * cos(ph).toFloat(); val zz = b * sin(ph).toFloat()
                pos = if (u < total) P3(-c + u, yy, zz) else null
                back = if (u < total) P3(-c + maxOf(0f, u - 0.022f), yy, zz) else null
            } else {
                val t = bankT[k]; total = t[NP - 1]
                val u = ((beadClock + beadOff[k]) % (total + beadGap[k]))
                pos = if (u < total) at(bankPath[k], t, u) else null
                back = if (u < total) at(bankPath[k], t, maxOf(0f, u - 0.022f)) else null
            }
            val on = pos != null
            beads[k]!!.visible = on; beadGlow[k]!!.visible = on; ghost[k]!!.visible = on
            if (!on) continue
            beads[k]!!.pos = pos!!; beadGlow[k]!!.pos = pos; ghost[k]!!.pos = back!!
        }
        val total = aimT[NA - 1]
        val u = beadClock % (total + 0.06f)
        val on = u < total
        aimBead.visible = on; aimBeadGlow.visible = on
        if (on) { val p = at(aimPts, aimT, u); aimBead.pos = p; aimBeadGlow.pos = p }
    }

    private fun at(path: FloatArray, t: FloatArray, tau: Float): P3 {
        var lo = 0; var hi = t.size - 1
        while (hi - lo > 1) { val m = (lo + hi) / 2; if (t[m] <= tau) lo = m else hi = m }
        val span = (t[hi] - t[lo]).coerceAtLeast(1e-9f)
        val f = ((tau - t[lo]) / span).coerceIn(0f, 1f)
        return P3(path[lo * 3] + (path[hi * 3] - path[lo * 3]) * f, path[lo * 3 + 1] + (path[hi * 3 + 1] - path[lo * 3 + 1]) * f,
            path[lo * 3 + 2] + (path[hi * 3 + 2] - path[lo * 3 + 2]) * f)
    }

    // ---------------------------------------------------------------- text

    private fun sup(n: Int): String {
        val s = n.toString(); val m = "⁰¹²³⁴⁵⁶⁷⁸⁹"
        return s.map { if (it == '-') '⁻' else m[it - '0'] }.joinToString("")
    }

    /** 7.9×10⁻⁵ */
    private fun sci(x: Double, digits: Int = 1): String {
        if (x <= 0) return "0"
        val e = kotlin.math.floor(log10(x)).toInt()
        val m = x / 10.0.pow(e)
        return if (e in -2..3) "%.${maxOf(0, digits + 1 - e - 1).coerceAtMost(3)}f".format(x) else "%.${digits}f×10%s".format(m, sup(e))
    }

    private fun num(x: Double) = when { x >= 100 -> "%.0f".format(x); x >= 10 -> "%.1f".format(x); else -> "%.2f".format(x) }

    private fun grouped(n: Long): String {
        val s = n.toString(); val sb = StringBuilder()
        for (i in s.indices) { if (i > 0 && (s.length - i) % 3 == 0) sb.append(','); sb.append(s[i]) }
        return sb.toString()
    }

    /** "1.3 万" / "12,600": one in N, rounded to 2-3 significant figures. */
    private fun oneIn(p: Double): String {
        val n = 1 / p
        val r = 10.0.pow(kotlin.math.floor(log10(n)) - 2)
        val nr = (n / r).roundToInt() * r
        return if (com.bytedance.physicslab.solar.lab.Lang.en || nr < 1e4) grouped(nr.toLong())
               else tr("%s万", "%s").format(num(nr / 1e4).trimEnd('0').trimEnd('.'))
    }

    private fun firedText(n: Double) = if (n < 1e4) "%.0f".format(n) else sci(n, 1)

    private fun updateReadout() {
        val f = foil; val dd = d; val ntt = nt; val sg = sigma
        val name = tr(f.zh, f.en)
        val l1 = if (!thomson) {
            val ratio = (f.atomRadiusFm / dd / 100).roundToInt() * 100
            tr("%s %s（Z=%d）· α %.1f MeV：最近距离 d = 2Ze²/4πε₀E = %.1f fm（约原子半径的 1/%d）· 瞄准 b = %.0f fm → θ = %.1f°",
                "%s %s (Z=%d) · α %.1f MeV: closest approach d = 2Ze²/4πε₀E = %.1f fm (≈1/%d of the atom) · aim b = %.0f fm → θ = %.1f°")
                .format(name, f.symbol, f.z, eMeV, dd, ratio, abs(aimFm), aimTheta * 180 / PI)
        } else {
            val th = Rutherford.thomsonAtomDeflection(aimFm, dd, f.atomRadiusFm) * 180 / PI
            tr("汤姆孙模型 · %s %s · α %.1f MeV：正电荷弥散在整个原子（半径 %.0f pm）里，电场极弱 → b = %.0f fm 时 θ = %s°",
                "Thomson model · %s %s · α %.1f MeV: charge spread over the whole atom (r = %.0f pm), weak field → b = %.0f fm gives θ = %s°")
                .format(name, f.symbol, eMeV, f.atomRadiusFm / 1000, abs(aimFm), sci(th, 0))
        }
        val lo = microDeg - 2.5; val hi = microDeg + 2.5
        val cnt = counter.countBetween(lo, hi)
        val fired = maxOf(counter.fired, 1.0)
        val per = cnt / fired * 1e5; val err = sqrt(cnt.toDouble()) / fired * 1e5
        val t1 = lo * PI / 180; val t2 = hi * PI / 180
        val l2 = if (!thomson) {
            val pred = Rutherford.fractionBetween(t1, t2, dd, ntt) * 1e5
            tr("显微镜 θ = %d°（±2.5°）：计数 %d 次 = 每 10⁵ 个 α %s ± %s 次 · 卢瑟福公式预测 %s 次",
                "Microscope θ = %d° (±2.5°): %d counts = %s ± %s per 10⁵ α · Rutherford formula predicts %s")
                .format(microDeg, cnt, num(per), num(err), num(pred))
        } else {
            val lg = 5 + Rutherford.thomsonLog10Beyond(t1, sg)
            val pred = if (lg > -2) num(10.0.pow(lg)) else "10" + sup(lg.toInt())
            tr("显微镜 θ = %d°（±2.5°）：计数 %d 次 = 每 10⁵ 个 α %s 次 · 汤姆孙模型预测 %s 次",
                "Microscope θ = %d° (±2.5°): %d counts = %s per 10⁵ α · Thomson model predicts %s")
                .format(microDeg, cnt, num(per), pred)
        }
        val l3 = if (!thomson) {
            val p = Rutherford.fractionBeyond(PI / 2, dd, ntt)
            tr("反弹（θ > 90°）比例 = n t π(d/2)² = %s → 约每 %s 个 α 弹回 1 个 · 已发射 %s 个，弹回 %d 个",
                "Bounce-back (θ > 90°): n t π(d/2)² = %s → about 1 in %s alphas · fired %s, bounced %d")
                .format(sci(p), oneIn(p), firedText(counter.fired), counter.beyond90)
        } else {
            val lg = Rutherford.thomsonLog10Beyond(PI / 2, sg)
            tr("汤姆孙：只有约 %.1f° 的随机小偏转（多次散射）→ θ > 90° 的概率 10%s，一个也不会弹回 · 已发射 %s 个",
                "Thomson: only ~%.1f° random small kicks (multiple scattering) → P(θ > 90°) = 10%s, none ever bounce · fired %s")
                .format(sg * 180 / PI, sup(lg.toInt()), firedText(counter.fired))
        }
        readoutText = "$l1\n$l2\n$l3"
    }

    private fun labWorld(x: Float, y: Float, z: Float): P3 {
        val c = cos(TILT); val s = sin(TILT)
        // rotation about x by -TILT: local y -> (0, c, -s), local z -> (0, s, c)
        return P3(F0.x + x, F0.y + c * y + s * z, F0.z - s * y + c * z)
    }

    private fun updateLabels() {
        labels.clear()
        val f = foil
        labels += LabelSpec(tr("α 源 %.1f MeV → %s箔 %s 1 μm", "α source %.1f MeV → %s foil 1 μm").format(eMeV, tr(f.zh, f.en), f.symbol).replace("  ", " "),
            labWorld(SRC_X, 0.085f, 0.02f))
        val a = (microDeg * PI / 180).toFloat()
        val rr = R - 0.075f
        val mp = labWorld(rr * cos(a), rr * sin(a), 0.02f)
        labels += LabelSpec(tr("θ = %d° · %d 次", "θ = %d° · %d counts").format(microDeg, counter.countBetween(microDeg - 2.5, microDeg + 2.5)),
            P3(mp.x, mp.y, mp.z + 0.02f))
        labels += LabelSpec(tr("计数（对数）· 白线 = 理论", "Counts (log) · white = theory"), labWorld(0.06f, -0.17f, 0.03f))
        labels += LabelSpec(if (thomson) tr("汤姆孙原子：正电荷均匀弥散", "Thomson atom: charge spread out")
                            else tr("放大 2×10¹² 倍 · %s核 Z=%d · 刻度 50 fm", "×2·10¹² · %s nucleus Z=%d · bar 50 fm").format(tr(f.zh, f.en), f.z),
            P3(W0.x - 0.33f, W0.y + 0.29f, W0.z))
        labels += LabelSpec(if (thomson) "b = %.0f fm → θ ≈ 0°".format(abs(aimFm)) else "b = %.0f fm → θ = %.0f°".format(abs(aimFm), aimTheta * 180 / PI),
            P3(W0.x + aimPts[0] - 0.08f, W0.y + aimPts[1] + (if (aimFm >= 0) 0.045f else -0.045f), W0.z + 0.03f))
        val dd = (d * FM).toFloat()
        val touch = d < f.contactFm
        labels += LabelSpec(if (touch) tr("d = %.1f fm < 核接触距离：核力介入", "d = %.1f fm < contact: nuclear force").format(d)
                            else tr("d = %.0f fm（正碰最近距离）", "d = %.0f fm (head-on turn point)").format(d),
            P3(W0.x + 0.03f, W0.y - dd - 0.035f, W0.z + 0.05f), visible = !thomson)
    }

    // ---------------------------------------------------------------- interaction

    override val tapTargets get() = listOf(
        TapTarget("Source", "α 源 → 开/关射束", "Source → beam on/off") { beamOn },
        TapTarget("Foil", "金属箔 → 换材料", "Foil → next metal"),
        TapTarget("Microscope", "显微镜 → 换角度", "Microscope → next θ"),
        TapTarget("Aim", "瞄准点 → 换 b", "Aim → next b"),
        TapTarget("Nucleus", "原子核 → 换模型", "Nucleus → model"),
    )

    override fun isTarget(name: String) = name == "Source" || name == "Foil" || name == "Microscope" || name == "Aim" || name == "Nucleus"
    override fun isDragTarget(name: String) = name == "Microscope" || name == "Aim"

    override fun onTap(name: String) {
        when (name) {
            "Source" -> beamOn = !beamOn
            "Foil" -> foilIdx = (foilIdx + 1) % Rutherford.FOILS.size
            "Microscope" -> { microDeg = MICRO_PRESETS.firstOrNull { it > microDeg } ?: MICRO_PRESETS[0]; microCont = microDeg.toDouble() }
            "Aim" -> aimFm = AIM_PRESETS.firstOrNull { it < abs(aimFm) - 0.5 } ?: AIM_PRESETS[0]
            "Nucleus" -> thomson = !thomson
        }
    }

    override fun tapMessage(name: String): String? = when (name) {
        "Source" -> if (beamOn) tr("射束打开：α 粒子射向金属箔", "Beam on: alphas fly at the foil") else tr("射束关闭：屏上不再有闪光", "Beam off: the screen goes dark")
        "Foil" -> {
            val r = Rutherford.fractionBeyond(PI / 2, d, nt) / Rutherford.fractionBeyond(PI / 2,
                Rutherford.closestApproachFm(79, eMeV.toDouble()), Rutherford.arealDensity(Rutherford.FOILS[0]))
            when (foilIdx) {
                0 -> tr("换成金箔：Z = 79，盖革和马斯登用的就是金", "Gold foil: Z = 79, as Geiger and Marsden used")
                1 -> tr("换成银箔：Z = 47 → 大角度散射只有金的 1/%.0f", "Silver foil: Z = 47 → 1/%.0f of gold's large-angle hits").format(1 / r)
                else -> tr("换成铝箔：Z 小 → 大角度散射少得多（只有金的 1/%.0f）", "Aluminium foil: small Z → far fewer large-angle hits (1/%.0f of gold)").format(1 / r)
            }
        }
        "Microscope" -> tr("显微镜转到 θ = %d°：预测每 10⁵ 个 α %s 次", "Microscope to θ = %d°: expect %s per 10⁵ alphas").format(microDeg,
            num(Rutherford.fractionBetween((microDeg - 2.5) * PI / 180, (microDeg + 2.5) * PI / 180, d, nt) * 1e5))
        "Aim" -> if (thomson) tr("瞄准 b = %.0f fm：汤姆孙原子里照样直线穿过", "Aim b = %.0f fm: straight through a Thomson atom").format(abs(aimFm))
                 else tr("瞄准 b = %.0f fm → θ = %.0f°（b 越小，偏得越狠）", "Aim b = %.0f fm → θ = %.0f° (smaller b, harder turn)").format(abs(aimFm), aimTheta * 180 / PI)
        "Nucleus" -> if (thomson) tr("汤姆孙模型：正电荷弥散 → α 几乎不偏转", "Thomson model: spread-out charge → alphas barely bend")
                     else tr("卢瑟福模型：正电荷集中在 10⁻¹⁴ m 的核里", "Rutherford model: all positive charge in a 10⁻¹⁴ m nucleus")
        else -> null
    }

    override fun onDragStart(name: String) {
        if (name == "Microscope") microCont = microDeg.toDouble()
        if (name == "Aim") dragAim0 = aimFm
    }

    override fun onDrag(name: String, dx: Float, dy: Float, dz: Float) {
        when (name) {
            "Microscope" -> {
                // hand motion -> ring-local, projected on the ring's tangent at the microscope
                val c = cos(TILT); val s = sin(TILT)
                val ly = c * dy - s * dz
                val a = microDeg * PI / 180
                val tang = -sin(a) * dx + cos(a) * ly
                microCont = (microCont + tang / (R + 0.06) * 180 / PI * 1.2).coerceIn(10.0, 170.0)
                microDeg = microCont.roundToInt()
            }
            "Aim" -> aimFm = (aimFm + dy / FM).coerceIn(-B_MAX_FM, B_MAX_FM).let { if (abs(it) < 0.5) (if (it < 0) -0.5 else 0.5) else it }
        }
    }

    override fun reviewVariants(): Map<String, () -> Unit> = linkedMapOf(
        "thomson" to { thomson = true; foilIdx = 0 },
        "aluminium" to { thomson = false; foilIdx = 2 },
    )

    fun resetCounts() { counter.reset(); updateBars() }

    @Composable
    override fun Panel() {
        var th by FieldRef(::thomson)
        var si by FieldRef(::speedIdx)
        var fi by FieldRef(::foilIdx)
        var e by FieldRef(::eMeV)
        LabPanel(readout) {
            LabRow {
                LabSwitch(tr("汤姆孙模型", "Thomson model"), th) { th = it }
                Spacer(Modifier.width(20.dp))
                SegmentControl {
                    SPEED_LABELS.forEachIndexed { k, l -> SegmentItem(selected = si == k, onClick = { si = k }, title = { Text(l) }) }
                }
                Spacer(Modifier.width(20.dp))
                SegmentControl {
                    Rutherford.FOILS.forEachIndexed { k, f -> SegmentItem(selected = fi == k, onClick = { fi = k }, title = { Text(tr(f.zh + " " + f.symbol, f.symbol)) }) }
                }
            }
            LabSlider(tr("α 能量", "α energy"), e, 4f..8f, "%.1f MeV".format(e)) { e = (it * 10).roundToInt() / 10f }
            Button(onClick = { resetCounts() }) { Text(tr("计数清零", "Reset counts")) }
        }
    }
}
