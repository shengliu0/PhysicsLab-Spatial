// 焰色反应与原子光谱 (Bunsen & Kirchhoff, 1860) — colour you can explain, and a game.
//
// LESSON: heat kicks electrons of a metal's atoms (or of hot molecules like CaOH) up to higher energy
// levels; falling back, each emits a photon of energy E = hc/λ. Every element has its own levels, so
// its own LINES — the flame colour is just what those lines add up to in the eye. Identify elements by
// their lines (Bunsen & Kirchhoff found caesium and rubidium this way), and see through cobalt glass
// why potassium's violet is hidden by the smallest trace of sodium.
//
// WHAT YOU SEE AND DO: a Bunsen burner, a rack of chloride salts and a platinum loop. Pinch a dish:
// the loop dips into it and carries the salt into the flame, which takes the colour COMPUTED from the
// salt's spectrum (core/flame: NIST lines + molecular bands -> CIE 1931 -> sRGB). The spectroscope
// strip on the right lights the lines at their true wavelengths, right above a Bunsen-Kirchhoff chart
// of all seven salts on the same wavelength scale (match lines by looking straight down). Top left:
// the emitter's energy levels, one electron excited by the heat and falling back with a photon of the
// strongest line. Raise the cobalt glass: sodium's D lines are absorbed, potassium's 766/404 nm pass.
// The "?" dish starts the game: an unknown salt (or a mixture), answer by pinching element cards.
//
// Rendering: flame = viewer-facing ADD sprites (baked once) animated by transforms; colours/alphas are
// set only when the sample, the glass or the fade step changes. Text in the scene (element symbols,
// nm axis, eV values) is a baked stroke font (StrokeText) — instrument markings, language-neutral.

package com.bytedance.physicslab.solar.demos.flame

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bytedance.physicslab.solar.core.P3
import com.bytedance.physicslab.solar.core.flame.CobaltGlass
import com.bytedance.physicslab.solar.core.flame.Feature
import com.bytedance.physicslab.solar.core.flame.FlameColour
import com.bytedance.physicslab.solar.core.flame.Photon
import com.bytedance.physicslab.solar.core.flame.Salt
import com.bytedance.physicslab.solar.core.flame.Salts
import com.bytedance.physicslab.solar.core.flame.Sample
import com.bytedance.physicslab.solar.core.prism.Spectrum
import com.bytedance.physicslab.solar.demos.prism.OptGeo
import com.bytedance.physicslab.solar.lab.BakeCache
import com.bytedance.physicslab.solar.lab.Lang
import com.bytedance.physicslab.solar.lab.LabHost
import com.bytedance.physicslab.solar.lab.Q
import com.bytedance.physicslab.solar.lab.SpecDemo
import com.bytedance.physicslab.solar.lab.TapTarget
import com.bytedance.physicslab.solar.lab.tr
import com.bytedance.physicslab.solar.lab.spec.Blend
import com.bytedance.physicslab.solar.lab.spec.LabelSpec
import com.bytedance.physicslab.solar.lab.spec.Mat
import com.bytedance.physicslab.solar.lab.spec.Node
import com.bytedance.physicslab.solar.lab.ui.Button
import com.bytedance.physicslab.solar.lab.ui.LabPanel
import com.bytedance.physicslab.solar.lab.ui.LabRow
import com.bytedance.physicslab.solar.lab.ui.LabSwitch
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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object FlameDemo : SpecDemo() {
    override val id = "flame"
    override val title = "焰色反应与原子光谱"   // i18n-ok: Chinese title; the English one is DemoRegistry.TITLE_EN (shown via titleOf)
    override val credit = "Bunsen & Kirchhoff · 1860"

    // ---------------------------------------------------------------- layout (volume metres)
    private const val PLINTH_Y = -0.262f
    private const val BENCH = PLINTH_Y + 0.0175f
    private const val FX = -0.12f; private const val FZ = 0.02f              // flame axis
    private const val TUBE_TOP = -0.035f                                      // burner mouth (a tall burner: the flame is the hero)
    private val TIP_FLAME = P3(FX + 0.004f, 0.062f, FZ + 0.012f)             // loop tip while testing (just above the inner cone)
    private const val TRAVEL_Y = 0.10f
    private const val RACK_Z = 0.27f
    private const val SHELF_TOP = BENCH + 0.030f
    private val DISH_X = FloatArray(8) { -0.86f + 0.086f * it }              // 7 salts + the "?" dish
    private const val DISH_R = 0.032f; private const val DISH_H = 0.014f
    private const val DISH_Y = SHELF_TOP + DISH_H / 2
    private const val TILE_Y = -0.166f
    private const val CARD_Y = -0.045f
    // spectroscope strip and the reference chart share one wavelength scale
    private const val X0 = 0.22f; private const val X1 = 0.86f
    private const val STRIP_Y = 0.352f; private const val STRIP_H = 0.085f
    private const val BOARD_Z = -0.14f; private const val SZ = BOARD_Z + 0.010f
    private const val ROW0_Y = 0.222f; private const val ROW_PITCH = 0.050f; private const val ROW_H = 0.032f
    // energy-level diagram
    private const val EX0 = -0.80f; private const val EX1 = -0.58f; private const val EY0 = 0.085f
    private const val E_SCALE = 0.083f; private const val EZ = -0.10f
    // cobalt glass: held in front of the flame, or resting on the bench
    private val GLASS_UP = P3(FX, 0.14f, 0.21f)
    private val GLASS_DOWN = P3(0.47f, BENCH + 0.040f, 0.30f)
    // resting: long side along x, leaning back 68 deg on two wooden blocks (normal up and toward the viewer)
    private val GLASS_DOWN_ROT = Q.axisAngle(1f, 0f, 0f, Math.toRadians(-68.0).toFloat()) * Q.axisAngle(0f, 0f, 1f, (PI / 2).toFloat())
    private val LOOP_DIR = norm(P3(0.55f, 0.45f, 0.50f))                     // tip -> handle
    private const val LOOP_V = 0.9f
    // flame sprite sizes (m): Bunsen mantle, two salt plume layers, the exposed core, the inner cone
    private const val MANTLE_W = 0.11f; private const val MANTLE_H = 0.30f
    private const val P0_W = 0.26f; private const val P0_H = 0.44f
    private const val P1_W = 0.17f; private const val P1_H = 0.34f
    private const val CORE_W = 0.11f; private const val CORE_H = 0.19f
    private const val CONE_W = 0.036f; private const val CONE_H = 0.080f
    private const val CYCLE = 2.8f                                            // energy-diagram animation period (s)
    private const val NPH = 24                                                // photon wave-packet beads
    private const val POOL = 16                                               // strip line slots

    // ---------------------------------------------------------------- UI state (panel-observed)
    var cobaltUp by mutableStateOf(false); private set
    var showSpectrum by mutableStateOf(true)
    var showLevels by mutableStateOf(true)
    var burnerOn by mutableStateOf(true); private set
    var mysteryOn by mutableStateOf(false); private set
    var solved by mutableStateOf(false); private set

    // ---------------------------------------------------------------- state
    private const val DEFAULT = 2                                        // opens on LiCl: a crimson flame, 670.8 nm
    private var t = 0f
    private var frame = 0
    private var tip = TIP_FLAME
    private val path = ArrayDeque<Pair<P3, Float>>()                        // waypoint, pause after (s)
    private var pause = 0f
    private var carrying: Sample? = null
    private var carryingUnknown = false
    private var carryingIdx = -1                                              // salt index (or -1 unknown / mixture)
    private var shown: Sample? = null
    private var shownUnknown = false
    private var shownIdx = -1
    private var heat = 0f
    private var glassT = 0f                                                   // 0 down .. 1 up (animated)
    private var glassShown = -1f
    private var mystery: List<Int>? = null
    private val found = HashSet<Int>()
    private var wrong = 0
    private var mysteryCount = 0
    private var rng = java.util.Random(1860)
    private var lastAnswer = 0                                                // +1 right, -1 wrong (for the note)
    private var lastAnswerIdx = -1
    private var cardFlash = FloatArray(7)
    private var styleKey = ""
    private var textKey = ""
    private var diagramIdx = -1

    private val MYSTERY_POOL = listOf(listOf(0, 1), listOf(0), listOf(1), listOf(2), listOf(3), listOf(4), listOf(5), listOf(6),
        listOf(0, 2), listOf(1, 2), listOf(4, 5))

    // ---------------------------------------------------------------- nodes
    private lateinit var loop: Node
    private lateinit var bead: Node
    private lateinit var beadGlow: Node
    private lateinit var mantle: Node
    private lateinit var cone: Node
    private lateinit var plume: Array<Node>
    private lateinit var core: Node
    private lateinit var tongues: Array<Node>
    private lateinit var glassRig: Node
    private lateinit var board: Node
    private lateinit var lineCore: Array<Node>
    private lateinit var lineGlow: Array<Node>
    private lateinit var continuum: Node
    private lateinit var tags: Array<Node>
    private lateinit var uvMark: Node
    private lateinit var stripBg: Node
    private lateinit var tCurve: Node
    private lateinit var rowGlow: Array<Node>
    private lateinit var diagramRoot: Node
    private lateinit var diagrams: Array<Node>
    private lateinit var diagramQ: Node
    private lateinit var electron: Node
    private lateinit var electronGlow: Node
    private lateinit var kick: Node
    private lateinit var photons: Array<Node>
    private lateinit var cards: Array<Node>
    private lateinit var cardPlates: Array<Node>
    private lateinit var beam: Node
    private lateinit var knob: Node

    // ---------------------------------------------------------------- materials (sRGB)
    private val alu = Mat.Pbr(Rgb(0.80f, 0.82f, 0.85f), metallic = 1f, roughness = 0.30f)
    private val darkSteel = Mat.Pbr(Rgb(0.30f, 0.31f, 0.33f), metallic = 1f, roughness = 0.40f)
    private val chrome = Mat.Pbr(Rgb(0.90f, 0.91f, 0.93f), metallic = 1f, roughness = 0.14f)
    private val brass = Mat.Pbr(Rgb(0.96f, 0.78f, 0.45f), metallic = 1f, roughness = 0.22f)
    private val blackTube = Mat.Pbr(Rgb(0.05f, 0.05f, 0.06f), metallic = 0.5f, roughness = 0.35f)
    private val porcelain = Mat.Pbr(Rgb(0.90f, 0.90f, 0.87f), metallic = 0f, roughness = 0.25f)
    private val wood = Mat.Pbr(Rgb(0.45f, 0.28f, 0.15f), metallic = 0f, roughness = 0.55f)
    private lateinit var slabTex: Image
    private val slab get() = Mat.Pbr(Rgb(1f, 1f, 1f), metallic = 0f, roughness = 0.55f, tex = slabTex)
    private val boardMat = Mat.Pbr(Rgb(0.03f, 0.033f, 0.04f), metallic = 0f, roughness = 0.35f)
    private val tileMat = Mat.Pbr(Rgb(0.05f, 0.055f, 0.065f), metallic = 0.2f, roughness = 0.45f)
    private val cobaltMat = Mat.Pbr(Rgb(0.12f, 0.20f, 0.85f), metallic = 0f, roughness = 0.05f, alpha = 0.38f)
    private val tickMat = Mat.Unlit(Rgb(0.72f, 0.80f, 0.90f), alpha = 0.6f, blend = Blend.TRANSPARENT)
    private val levelMat = Mat.Unlit(Rgb(0.80f, 0.86f, 0.95f), alpha = 0.85f, blend = Blend.TRANSPARENT)
    private val WHITE = Rgb(1f, 1f, 1f)
    private val TEXT = Rgb(0.88f, 0.92f, 1.0f)

    // ---------------------------------------------------------------- baked textures
    // ~150 ms of bakes on the Mac (~2 s on the headset): all of them go through BakeCache, keyed by the
    // list of in-scene markings, so a second visit (and every later launch) reads them in ~10 ms.
    private const val BAKE_V = 1
    private lateinit var plumeTex: Image
    private lateinit var coneTex: Image
    private lateinit var gaussTex: Image
    private lateinit var dotTex: Image
    private lateinit var rainbow: Image
    private lateinit var rainbowCo: Image
    private lateinit var rowRainbow: Image
    private lateinit var contTex: Image
    private lateinit var contTexCo: Image
    private val texts = HashMap<String, Image>()
    private val unitQuad: MeshData by lazy { OptGeo.quad(1f, 1f) }
    /** A stroke-font marking (from the cache; baked on the spot only for a string missing from [markings]). */
    private fun txt(s: String): Image = texts.getOrPut(s) { StrokeText.bake(s, 64) }
    private fun evText(e: Double) = String.format(java.util.Locale.US, "%.2f eV", e)
    private fun tagText(f: Feature) = if (f.isLine) String.format(java.util.Locale.US, "%.1f", f.nm) else String.format(java.util.Locale.US, "%.0f", f.nm)

    /** Every in-scene marking, known up front so its bake can be cached. */
    private fun markings(): List<String> {
        val l = LinkedHashSet<String>()
        for (s in Salts.ALL) l += s.symbol
        l += listOf("?", "400", "500", "600", "700", "nm", "UV", "0 eV")
        for (s in Salts.ALL) {
            l += tagText(s.main)
            for (i in s.diagram) { val f = s.features[i]; l += evText(f.upperEv); if (f.lowerEv > 0) l += evText(f.lowerEv); l += f.emitter }
        }
        return l.toList()
    }

    private fun loadTextures(cacheDir: File) {
        val marks = markings()
        val gauss = { l: Double -> exp(-0.5 * ((l - 500.0) / 106.0).pow(2)) }
        val imgs = BakeCache.load(cacheDir, "flame", BAKE_V * 65536 + (marks.hashCode() and 0xFFFF)) {
            listOf(
                ProcTex.bakePlanet(256, 128, Rgb(0.07f, 0.075f, 0.085f), Rgb(0.12f, 0.125f, 0.14f), Rgb(0.20f, 0.205f, 0.22f), 0f, 77),
                OptGeo.lumTex(48, 96) { u, v -> plumeProfile(u, v) },
                OptGeo.lumTex(32, 64) { u, v -> coneProfile(u, v) },
                OptGeo.lumTex(64, 2) { u, _ -> exp(-0.5f * ((u - 0.5f) / 0.1415f).pow(2)) },
                OptGeo.lumTex(32, 32) { u, v -> exp(-((u - 0.5f).pow(2) + (v - 0.5f).pow(2)) / (2 * 0.16f * 0.16f)) },
                spectrumImage(0.055, false) { 1.0 }, spectrumImage(0.055, true) { 1.0 }, spectrumImage(0.028, false) { 1.0 },
                spectrumImage(1.0, false, gauss), spectrumImage(1.0, true, gauss),
            ) + marks.map { StrokeText.bake(it, 64) }
        }
        slabTex = imgs[0]; plumeTex = imgs[1]; coneTex = imgs[2]; gaussTex = imgs[3]; dotTex = imgs[4]
        rainbow = imgs[5]; rainbowCo = imgs[6]; rowRainbow = imgs[7]; contTex = imgs[8]; contTexCo = imgs[9]
        texts.clear(); marks.forEachIndexed { i, m -> texts[m] = imgs[10 + i] }
    }

    private fun plumeProfile(u: Float, v: Float): Float {
        val s = 1f - v                                                   // 0 at the base, 1 at the tip
        val hw = 0.34f * (if (s < 0.18f) sqrt(s / 0.18f) else ((1f - s) / 0.82f).pow(0.8f)).coerceAtLeast(0.002f)
        val dx = (u - 0.5f) / hw
        val window = 1f - ProcTex.smooth01((abs(u - 0.5f) - 0.40f) / 0.10f)       // exactly 0 at the quad's sides
        return exp(-2.2f * dx * dx) * window * ProcTex.smooth01(s / 0.08f) * (1f - s).pow(0.35f)
    }

    private fun coneProfile(u: Float, v: Float): Float {
        val s = 1f - v
        val hw = 0.40f * (1f - s).pow(0.9f) + 0.02f
        val d = abs(u - 0.5f)
        val edge = exp(-((d - hw) / 0.07f).pow(2))
        val fill = 0.30f * (1f - ProcTex.smooth01((d - hw + 0.04f) / 0.08f))
        return (0.95f * edge + fill) * ProcTex.smooth01(s / 0.04f) * (1f - s.pow(4))
    }

    /** 256 x 1 spectrum image: display colour of λ x [k] x [f](λ), optionally through cobalt glass. */
    private fun spectrumImage(k: Double, cobalt: Boolean, f: (Double) -> Double): Image {
        val img = Image(256, 1)
        for (x in 0 until 256) {
            val l = 380.0 + 400.0 * (x + 0.5) / 256
            val c = Spectrum.display(l)
            val edge = ProcTex.smooth01(((l - 380.0) / 45.0).toFloat()) * ProcTex.smooth01(((780.0 - l) / 70.0).toFloat())
            val tt = if (cobalt) CobaltGlass.transmission(l).pow(0.45) else 1.0
            val m = k * f(l) * edge * tt
            img.px[x] = ProcTex.argb(1f, OptGeo.enc((c[0] * m).toFloat()), OptGeo.enc((c[1] * m).toFloat()), OptGeo.enc((c[2] * m).toFloat()))
        }
        return img
    }

    // ---------------------------------------------------------------- build

    override fun buildSpec(cacheDir: File): Node {
        // REBUILD: every piece of state back to the opening scene (loop in the flame with LiCl)
        t = 0f; frame = 0; path.clear(); pause = 0f; heat = 1f; glassT = 0f
        cobaltUp = false; burnerOn = true; mysteryOn = false; solved = false; showSpectrum = true; showLevels = true
        mystery = null; found.clear(); wrong = 0; mysteryCount = 0; rng = java.util.Random(1860); lastAnswer = 0; lastAnswerIdx = -1
        cardFlash = FloatArray(7); styleKey = ""; textKey = ""; diagramIdx = -1; glassShown = -1f
        tip = TIP_FLAME; carrying = Sample(Salts.ALL[DEFAULT]); carryingUnknown = false; carryingIdx = DEFAULT
        shown = carrying; shownUnknown = false; shownIdx = DEFAULT

        loadTextures(cacheDir)
        val root = Node("FlameRoot")
        val unitCyl = MeshGen.cylinder(1f, 1f, 24)
        val unitBox = MeshGen.box(1f, 1f, 1f)
        val sphere = MeshGen.sphere(1f, 14, 28, MeshGen.SphereUv.EQUIRECT)
        val quad = OptGeo.quad(1f, 1f)
        val baseQuad = OptGeo.grid(2, 2).also {                    // pivot at the bottom centre (flame base)
            OptGeo.setPos(it, 0, -0.5f, 1f, 0f); OptGeo.setUv(it, 0, 0f, 0f); OptGeo.setPos(it, 1, 0.5f, 1f, 0f); OptGeo.setUv(it, 1, 1f, 0f)
            OptGeo.setPos(it, 2, -0.5f, 0f, 0f); OptGeo.setUv(it, 2, 0f, 1f); OptGeo.setPos(it, 3, 0.5f, 0f, 0f); OptGeo.setUv(it, 3, 1f, 1f)
        }

        root.add("Plinth", MeshGen.box(1.80f, 0.035f, 0.80f), slab, P3(0f, PLINTH_Y, 0.01f))
        root.add("PlinthBand", MeshGen.box(1.81f, 0.008f, 0.81f), alu, P3(0f, PLINTH_Y, 0.01f))

        buildBurner(root, unitCyl, sphere, baseQuad)
        buildRack(root, unitBox, sphere)
        buildLoop(root, unitCyl, sphere, quad)
        buildGlass(root, unitBox, unitCyl)
        buildSpectroscope(root, unitCyl, unitBox)
        buildBoard(root, unitBox, quad)
        buildDiagrams(root, unitCyl, sphere, quad)

        update(0f)
        return root
    }

    private fun buildBurner(root: Node, unitCyl: MeshData, sphere: MeshData, baseQuad: MeshData) {
        root.add("BurnerBase", MeshGen.cylinder(0.052f, 0.012f, 40), darkSteel, P3(FX, BENCH + 0.006f, FZ))
        root.add("BurnerTube", unitCyl, alu, P3(FX, (BENCH + 0.012f + TUBE_TOP) / 2, FZ)).also { it.scale = P3(0.0115f, TUBE_TOP - BENCH - 0.012f, 0.0115f) }
        root.add("BurnerCollar", unitCyl, chrome, P3(FX, BENCH + 0.052f, FZ)).also { it.scale = P3(0.0145f, 0.026f, 0.0145f) }
        for (k in 0..1) root.add("AirHole", unitCyl, blackTube, P3(FX + if (k == 0) -0.0125f else 0.0125f, BENCH + 0.052f, FZ + 0.004f)).also {
            it.scale = P3(0.0035f, 0.012f, 0.0035f)
        }
        root.add("BurnerMouth", MeshGen.cylinder(0.0125f, 0.004f, 24), chrome, P3(FX, TUBE_TOP - 0.002f, FZ))
        // gas inlet, valve and hose to the back of the bench
        val vx = FX + 0.060f; val vy = BENCH + 0.024f
        root.add("Inlet", unitCyl, brass, P3((FX + vx) / 2, vy, FZ)).also { it.scale = P3(0.0045f, vx - FX, 0.0045f); it.rot = Q.axisAngle(0f, 0f, 1f, (PI / 2).toFloat()) }
        root.add("Valve", MeshGen.cylinder(0.012f, 0.030f, 20), brass, P3(vx, vy, FZ)).also { it.rot = Q.axisAngle(0f, 0f, 1f, (PI / 2).toFloat()) }
        root.add("ValveStem", unitCyl, chrome, P3(vx, vy + 0.018f, FZ)).also { it.scale = P3(0.003f, 0.02f, 0.003f) }
        knob = root.add("GasTap", MeshGen.sphere(0.013f, 12, 24, MeshGen.SphereUv.EQUIRECT), Mat.Pbr(Rgb(0.80f, 0.12f, 0.10f), metallic = 0.1f, roughness = 0.35f),
            P3(vx, vy + 0.031f, FZ)).also { it.tapRadius = 0.045f }
        val hose = FloatArray(24 * 3) { k ->
            val u = (k / 3) / 23f
            when (k % 3) { 0 -> vx + 0.015f + 0.10f * u; 1 -> vy - 0.016f * sin(PI.toFloat() * u) * (1 - u) - 0.006f * u; else -> FZ - 0.25f * u }
        }
        root.add("Hose", MeshGen.tube(hose, 0.0065f, 10, false), Mat.Pbr(Rgb(0.80f, 0.36f, 0.16f), metallic = 0f, roughness = 0.45f))

        // the flame: Bunsen's own pale-blue mantle + bright inner cone, then the salt's colour on top
        fun sprite(name: String, w: Float, h: Float, y: Float, tex: Image, c: Rgb, a: Float) =
            root.add(name, baseQuad, Mat.Unlit(c, a, tex, Blend.ADD), P3(FX, y, FZ)).also { it.scale = P3(w, h, 1f); it.billboard = true }
        mantle = sprite("Mantle", MANTLE_W, MANTLE_H, TUBE_TOP - 0.004f, plumeTex, Rgb(0.30f, 0.38f, 0.95f), 0.30f)
        plume = arrayOf(sprite("Plume0", P0_W, P0_H, TUBE_TOP - 0.006f, plumeTex, WHITE, 0f), sprite("Plume1", P1_W, P1_H, TUBE_TOP - 0.004f, plumeTex, WHITE, 0f))
        cone = sprite("InnerCone", CONE_W, CONE_H, TUBE_TOP - 0.002f, coneTex, Rgb(0.40f, 0.68f, 1.0f), 0.95f)
        core = sprite("SaltCore", CORE_W, CORE_H, TIP_FLAME.y - 0.06f, plumeTex, WHITE, 0f)
        tongues = Array(4) { sprite("Tongue$it", 0.07f, 0.12f, 0.15f, plumeTex, WHITE, 0f) }
    }

    private fun buildRack(root: Node, unitBox: MeshData, sphere: MeshData) {
        val xl = DISH_X.first() - 0.05f; val xr = DISH_X.last() + 0.05f
        root.add("Shelf", MeshGen.box(xr - xl, 0.012f, 0.12f), alu, P3((xl + xr) / 2, SHELF_TOP - 0.006f, RACK_Z - 0.01f))
        for (x in floatArrayOf(xl + 0.03f, xr - 0.03f)) root.add("ShelfLeg", unitBox, darkSteel, P3(x, (BENCH + SHELF_TOP - 0.012f) / 2, RACK_Z - 0.01f)).also {
            it.scale = P3(0.02f, SHELF_TOP - 0.012f - BENCH, 0.10f)
        }
        root.add("TileRail", MeshGen.box(xr - xl, 0.010f, 0.016f), darkSteel, P3((xl + xr) / 2, SHELF_TOP + 0.004f, RACK_Z - 0.058f))
        val dishMesh = MeshGen.cylinder(DISH_R, DISH_H, 36)
        val tileMesh = MeshGen.box(0.074f, 0.050f, 0.006f)
        val tilt = Q.axisAngle(1f, 0f, 0f, Math.toRadians(-14.0).toFloat())
        for (i in 0..7) {
            val x = DISH_X[i]
            val salt = if (i < 7) Salts.ALL[i] else null
            root.add(if (i < 7) "Dish$i" else "DishQ", dishMesh, porcelain, P3(x, DISH_Y, RACK_Z)).also { it.tapRadius = 0.045f }
            val saltC = when (i) { 6 -> Rgb(0.30f, 0.62f, 0.58f); 7 -> Rgb(0.70f, 0.70f, 0.74f); else -> Rgb(0.93f, 0.93f, 0.95f) }
            root.add("Salt$i", sphere, Mat.Pbr(saltC, metallic = 0f, roughness = 0.75f), P3(x, DISH_Y + DISH_H / 2, RACK_Z)).also { it.scale = P3(0.025f, 0.008f, 0.025f) }
            val tile = root.add(Node("Tile$i")).also { it.pos = P3(x, TILE_Y, RACK_Z - 0.06f); it.rot = tilt }
            tile.add("TilePlate$i", tileMesh, tileMat)
            val label = salt?.symbol ?: "?"
            textNode(tile, "TileText$i", label, 0.050f, P3(0f, 0f, 0.0045f), if (i < 7) TEXT else Rgb(1f, 0.85f, 0.45f))
        }
        // answer cards (mystery mode only): one per element, floating above its dish
        val plate = MeshGen.box(0.070f, 0.054f, 0.006f)
        cardPlates = Array(7) { i -> Node("unused$i") }
        cards = Array(7) { i ->
            val c = root.add("Card$i", plate, Mat.Unlit(Rgb(0.07f, 0.11f, 0.20f)), P3(DISH_X[i], CARD_Y, RACK_Z - 0.035f)).also {
                it.tapRadius = 0.045f; it.rot = tilt; it.visible = false
            }
            cardPlates[i] = c.add("CardRim$i", MeshGen.box(0.078f, 0.062f, 0.002f), Mat.Unlit(Rgb(0.35f, 0.65f, 1.0f), 0.55f, blend = Blend.ADD), P3(0f, 0f, -0.0035f))
            textNode(c, "CardText$i", Salts.ALL[i].symbol, 0.052f, P3(0f, 0f, 0.0040f), WHITE)
            c
        }
    }

    private fun buildLoop(root: Node, unitCyl: MeshData, sphere: MeshData, quad: MeshData) {
        loop = root.add(Node("Loop"))
        val q = Q.fromTo(0f, 1f, 0f, LOOP_DIR.x, LOOP_DIR.y, LOOP_DIR.z)
        val wireL = 0.085f; val handleL = 0.13f
        loop.add("Wire", unitCyl, Mat.Pbr(Rgb(0.85f, 0.85f, 0.87f), metallic = 1f, roughness = 0.2f), LOOP_DIR * (wireL / 2)).also { it.rot = q; it.scale = P3(0.0016f, wireL, 0.0016f) }
        loop.add("Handle", unitCyl, wood, LOOP_DIR * (wireL + handleL / 2)).also { it.rot = q; it.scale = P3(0.0075f, handleL, 0.0075f) }
        loop.add("Ferrule", unitCyl, brass, LOOP_DIR * (wireL + 0.004f)).also { it.rot = q; it.scale = P3(0.0085f, 0.012f, 0.0085f) }
        val ring = FloatArray(25 * 3) { k -> val a = 2 * PI * (k / 3) / 24; when (k % 3) { 0 -> (0.0065 * cos(a)).toFloat(); 1 -> 0f; else -> (0.0065 * sin(a)).toFloat() } }
        loop.add("LoopRing", MeshGen.tube(ring, 0.0014f, 5, true), Mat.Pbr(Rgb(0.85f, 0.85f, 0.87f), metallic = 1f, roughness = 0.2f)).also { it.rot = Q.axisAngle(1f, 0f, 0f, 0.5f) }
        bead = loop.add("Bead", sphere, Mat.Pbr(Rgb(0.93f, 0.93f, 0.95f), roughness = 0.6f)).also { it.uniformScale(0.0055f) }
        beadGlow = loop.add("BeadGlow", quad, Mat.Unlit(WHITE, 0f, dotTex, Blend.ADD)).also { it.uniformScale(0.05f); it.billboard = true }
    }

    private fun buildGlass(root: Node, unitBox: MeshData, unitCyl: MeshData) {
        glassRig = root.add(Node("CobaltRig")).also { it.pos = GLASS_DOWN; it.rot = GLASS_DOWN_ROT }
        val w = 0.16f; val h = 0.36f
        glassRig.add("Cobalt", MeshGen.box(w, h, 0.006f), cobaltMat).also { it.tapRadius = 0.075f }
        for (s in floatArrayOf(-1f, 1f)) {
            glassRig.add("GlassFrameV", unitBox, alu, P3(s * (w / 2 + 0.003f), 0f, 0f)).also { it.scale = P3(0.006f, h + 0.012f, 0.009f) }
            glassRig.add("GlassFrameH", unitBox, alu, P3(0f, s * (h / 2 + 0.003f), 0f)).also { it.scale = P3(w + 0.012f, 0.006f, 0.009f) }
        }
        glassRig.add("GlassHandle", unitCyl, wood, P3(w / 2 + 0.045f, -h / 2 + 0.03f, 0f)).also { it.scale = P3(0.007f, 0.08f, 0.007f); it.rot = Q.axisAngle(0f, 0f, 1f, (PI / 2).toFloat()) }
        // the cradle it rests on
        // two small wooden wedges it leans on while resting
        for (dx in floatArrayOf(-0.12f, 0.12f)) root.add("GlassCradle", unitBox, wood, P3(GLASS_DOWN.x + dx, BENCH + 0.032f, GLASS_DOWN.z - 0.072f)).also { it.scale = P3(0.025f, 0.064f, 0.02f) }
    }

    private fun buildSpectroscope(root: Node, unitCyl: MeshData, unitBox: MeshData) {
        // a Kirchhoff-Bunsen spectroscope: collimator slit facing the flame, prism table, telescope
        val ty = TIP_FLAME.y; val tx = 0.065f; val tz = -0.05f
        root.add("ScopeTable", MeshGen.cylinder(0.034f, 0.010f, 36), darkSteel, P3(tx, ty - 0.022f, tz))
        root.add("ScopePost", unitCyl, alu, P3(tx, (BENCH + ty - 0.027f) / 2, tz)).also { it.scale = P3(0.008f, ty - 0.027f - BENCH, 0.008f) }
        root.add("ScopeFoot", MeshGen.cylinder(0.040f, 0.010f, 32), darkSteel, P3(tx, BENCH + 0.005f, tz))
        val poly = doubleArrayOf(-0.018, -0.012, 0.018, -0.012, 0.0, 0.019)
        root.add("ScopePrism", OptGeo.triPrism(poly, 0.024f), Mat.Pbr(Rgb(0.80f, 0.90f, 0.95f), roughness = 0.04f, alpha = 0.35f), P3(tx, ty - 0.003f, tz)).also {
            it.rot = Q.axisAngle(1f, 0f, 0f, (-PI / 2).toFloat())
        }
        val toFlame = norm(P3(FX - tx, 0f, FZ - tz))
        val slit = P3(FX + 0.055f, ty, tz + (FZ - tz) * (1f - 0.055f / (tx - FX)))
        val colLen = (P3(tx, ty, tz) - slit).length - 0.03f
        val colMid = slit + toFlame * (-colLen / 2)
        root.add("Collimator", unitCyl, brass, colMid).also { it.rot = Q.fromTo(0f, 1f, 0f, toFlame.x, 0f, toFlame.z); it.scale = P3(0.011f, colLen, 0.011f) }
        root.add("SlitCap", unitCyl, blackTube, slit + toFlame * (-0.003f)).also { it.rot = Q.fromTo(0f, 1f, 0f, toFlame.x, 0f, toFlame.z); it.scale = P3(0.013f, 0.008f, 0.013f) }
        val teleDir = norm(P3(0.55f, 0.25f, -0.55f))
        root.add("Telescope", unitCyl, blackTube, P3(tx, ty, tz) + teleDir * 0.075f).also { it.rot = Q.fromTo(0f, 1f, 0f, teleDir.x, teleDir.y, teleDir.z); it.scale = P3(0.012f, 0.11f, 0.012f) }
        root.add("Eyepiece", unitCyl, brass, P3(tx, ty, tz) + teleDir * 0.135f).also { it.rot = Q.fromTo(0f, 1f, 0f, teleDir.x, teleDir.y, teleDir.z); it.scale = P3(0.009f, 0.02f, 0.009f) }
        // light from the flame into the slit
        val bl = (slit - P3(FX, ty, FZ)).length
        beam = root.add("ScopeBeam", unitCyl, Mat.Unlit(WHITE, 0f, blend = Blend.ADD), slit + toFlame * (bl / 2)).also {
            it.rot = Q.fromTo(0f, 1f, 0f, toFlame.x, 0f, toFlame.z); it.scale = P3(0.004f, bl, 0.004f)
        }
    }

    private fun lx(nm: Double) = X0 + (X1 - X0) * ((nm - 380.0) / 400.0).toFloat()
    private const val MPN = (X1 - X0) / 400f                                  // metres per nm on the strip

    private fun buildBoard(root: Node, unitBox: MeshData, quad: MeshData) {
        board = root.add(Node("Board"))
        val bx0 = X0 - 0.075f; val bx1 = X1 + 0.02f; val by0 = ROW0_Y - 6 * ROW_PITCH - 0.045f; val by1 = STRIP_Y + STRIP_H / 2 + 0.045f
        board.add("BoardPanel", MeshGen.box(bx1 - bx0, by1 - by0, 0.012f), boardMat, P3((bx0 + bx1) / 2, (by0 + by1) / 2, BOARD_Z - 0.006f))
        for (s in floatArrayOf(-1f, 1f)) {
            board.add("BoardEdgeH", unitBox, alu, P3((bx0 + bx1) / 2, if (s < 0) by0 else by1, BOARD_Z - 0.004f)).also { it.scale = P3(bx1 - bx0 + 0.008f, 0.006f, 0.016f) }
            board.add("BoardEdgeV", unitBox, alu, P3(if (s < 0) bx0 else bx1, (by0 + by1) / 2, BOARD_Z - 0.004f)).also { it.scale = P3(0.006f, by1 - by0 + 0.008f, 0.016f) }
        }
        for (x in floatArrayOf(bx0 + 0.06f, bx1 - 0.06f)) {
            board.add("BoardPost", MeshGen.cylinder(1f, 1f, 16), alu, P3(x, (by0 + BENCH) / 2, BOARD_Z - 0.02f)).also { it.scale = P3(0.008f, by0 - BENCH, 0.008f) }
            board.add("BoardFoot", MeshGen.cylinder(0.032f, 0.010f, 24), darkSteel, P3(x, BENCH + 0.005f, BOARD_Z - 0.02f))
        }
        // ---- live strip: dark, a faint rainbow so you know where you are, lines on top
        board.add("StripBack", quad, Mat.Unlit(Rgb(0.012f, 0.013f, 0.018f)), P3((X0 + X1) / 2, STRIP_Y, SZ - 0.002f)).also { it.scale = P3(X1 - X0 + 0.012f, STRIP_H + 0.012f, 1f) }
        stripBg = board.add("StripBg", quad, Mat.Unlit(WHITE, 1f, rainbow, Blend.ADD), P3((X0 + X1) / 2, STRIP_Y, SZ - 0.001f)).also { it.scale = P3(X1 - X0, STRIP_H, 1f) }
        continuum = board.add("Continuum", quad, Mat.Unlit(WHITE, 0f, contTex, Blend.ADD), P3((X0 + X1) / 2, STRIP_Y, SZ)).also { it.scale = P3(X1 - X0, STRIP_H, 1f) }
        lineGlow = Array(POOL) { board.add("LineGlow$it", quad, Mat.Unlit(WHITE, 0f, gaussTex, Blend.ADD), P3(X0, STRIP_Y, SZ + 0.001f)).also { n -> n.visible = false } }
        lineCore = Array(POOL) { board.add("LineCore$it", quad, Mat.Unlit(WHITE, 1f, blend = Blend.ADD), P3(X0, STRIP_Y, SZ + 0.002f)).also { n -> n.visible = false } }
        // cobalt glass transmission T(λ) drawn across the strip (only while the glass is up)
        val tp = FloatArray(161 * 3) { k ->
            val i = k / 3; val l = 380.0 + 2.5 * i
            when (k % 3) { 0 -> lx(l); 1 -> STRIP_Y - STRIP_H / 2 + STRIP_H * CobaltGlass.transmission(l).toFloat() / CobaltGlass.T0.toFloat() * 0.92f; else -> SZ + 0.004f }
        }
        tCurve = board.add("TCurve", MeshGen.tube(tp, 0.0014f, 4, false), Mat.Unlit(Rgb(0.55f, 0.75f, 1.0f), 0.85f, blend = Blend.ADD)).also { it.visible = false }
        // axis: ticks every 50 nm, numbers every 100 nm
        val ay = STRIP_Y - STRIP_H / 2 - 0.006f
        for (l in 400..750 step 50) {
            val major = l % 100 == 0
            board.add("Tick$l", unitBox, tickMat, P3(lx(l.toDouble()), ay - (if (major) 0.007f else 0.004f), SZ)).also { it.scale = P3(0.0018f, if (major) 0.014f else 0.008f, 0.001f) }
            if (major) textNode(board, "Num$l", "$l", 0.034f, P3(lx(l.toDouble()), ay - 0.030f, SZ), TEXT, 0.9f)
        }
        textNode(board, "NmUnit", "nm", 0.030f, P3(X1 - 0.005f, ay - 0.029f, SZ), TEXT, 0.75f)
        uvMark = board.add(Node("UvMark"))
        textNode(uvMark, "UvText", "UV", 0.030f, P3(X0 - 0.030f, STRIP_Y, SZ), Rgb(0.70f, 0.55f, 1.0f), 0.9f)
        tags = Array(2) { textNode(board, "Tag$it", "589.0", 0.032f, P3(X0, STRIP_Y + STRIP_H / 2 + 0.020f, SZ), TEXT, 1f).also { n -> n.visible = false } }

        // ---- the reference chart: one row per salt, same λ scale, after Kirchhoff & Bunsen's plate
        rowGlow = Array(7) { r ->
            val y = ROW0_Y - r * ROW_PITCH
            val s = Salts.ALL[r]
            val g = board.add("RowGlow$r", quad, Mat.Unlit(Rgb(0.35f, 0.60f, 1.0f), 0.18f, blend = Blend.ADD), P3((X0 - 0.068f + X1 + 0.006f) / 2, y, SZ - 0.003f)).also {
                it.scale = P3(X1 - X0 + 0.074f, ROW_H + 0.014f, 1f); it.visible = false
            }
            board.add("RowBack$r", quad, Mat.Unlit(Rgb(0.010f, 0.011f, 0.015f)), P3((X0 + X1) / 2, y, SZ - 0.002f)).also { it.scale = P3(X1 - X0, ROW_H, 1f) }
            board.add("RowBg$r", quad, Mat.Unlit(WHITE, 1f, rowRainbow, Blend.ADD), P3((X0 + X1) / 2, y, SZ - 0.001f)).also { it.scale = P3(X1 - X0, ROW_H, 1f) }
            textNode(board, "RowSym$r", s.symbol, 0.042f, P3(X0 - 0.034f, y, SZ), TEXT, 0.95f)
            val maxW = s.features.filter { it.visible }.maxOf { it.power }
            for ((k, f) in s.features.withIndex()) {
                if (!f.visible) continue
                val b = FlameColour.lineBrightness(f.power, maxW)
                if (f.fwhm >= 100.0) {
                    board.add("RowCont$r", quad, Mat.Unlit(gray(0.30f), 1f, contTex, Blend.ADD), P3((X0 + X1) / 2, y, SZ)).also { it.scale = P3(X1 - X0, ROW_H, 1f) }
                    continue
                }
                val c = lineColour(f.nm, b)
                if (f.isLine) {
                    board.add("RowGlow${r}_$k", quad, Mat.Unlit(c, 0.55f, gaussTex, Blend.ADD), P3(lx(f.nm), y, SZ + 0.001f)).also { it.scale = P3(0.012f, ROW_H, 1f) }
                    board.add("RowLine${r}_$k", quad, Mat.Unlit(c, 1f, blend = Blend.ADD), P3(lx(f.nm), y, SZ + 0.002f)).also { it.scale = P3(0.0022f, ROW_H, 1f) }
                } else {
                    board.add("RowBand${r}_$k", quad, Mat.Unlit(c, 1f, gaussTex, Blend.ADD), P3(lx(f.nm), y, SZ + 0.001f)).also { it.scale = P3(3f * f.fwhm.toFloat() * MPN, ROW_H, 1f) }
                }
            }
            g
        }
    }

    private fun buildDiagrams(root: Node, unitCyl: MeshData, sphere: MeshData, quad: MeshData) {
        diagramRoot = root.add(Node("Levels"))
        diagramRoot.add("LevelsPanel", MeshGen.box(0.44f, 0.43f, 0.010f), boardMat, P3((EX0 + EX1) / 2 + 0.085f, EY0 + 0.14f, EZ - 0.012f))
        diagramRoot.add("LevelsAxis", unitCyl, tickMat, P3(EX0 - 0.022f, EY0 + 0.165f, EZ)).also { it.scale = P3(0.0012f, 0.33f, 0.0012f) }
        diagrams = Array(7) { i ->
            val g = diagramRoot.add(Node("Diagram$i")).also { it.visible = false }
            val s = Salts.ALL[i]
            val feats = s.diagram.map { s.features[it] }
            // levels: ground + every upper/lower used (deduplicated)
            val lv = ArrayList<Double>(); lv += 0.0
            for (f in feats) { for (e in listOf(f.upperEv, f.lowerEv)) if (lv.none { abs(it - e) < 0.03 }) lv += e }
            for (e in lv) {
                val y = EY0 + E_SCALE * e.toFloat()
                g.add("Level", unitCyl, levelMat, P3((EX0 + EX1) / 2, y, EZ)).also { it.scale = P3(0.0022f, EX1 - EX0, 0.0022f); it.rot = Q.axisAngle(0f, 0f, 1f, (PI / 2).toFloat()) }
                if (e > 0) textNode(g, "LevelEv", evText(e), 0.040f, P3(EX1 + 0.012f, y, EZ), TEXT, 0.95f, left = true)
                else textNode(g, "LevelEv", "0 eV", 0.030f, P3(EX1 + 0.012f, y, EZ), TEXT, 0.6f, left = true)
            }
            // downward arrows for the drawn transitions, in their line colours
            for ((k, f) in feats.withIndex()) {
                val x = EX0 + 0.050f + 0.075f * k
                val yu = EY0 + E_SCALE * f.upperEv.toFloat(); val yl = EY0 + E_SCALE * f.lowerEv.toFloat()
                val c = lineColour(f.nm, 1.0)
                g.add("Arrow", unitCyl, Mat.Unlit(c, 0.9f, blend = Blend.ADD), P3(x, (yu + yl) / 2 + 0.004f, EZ + 0.002f)).also { it.scale = P3(0.003f, yu - yl - 0.008f, 0.003f) }
                for (sx in floatArrayOf(-1f, 1f)) {
                    val hx = x + sx * 0.0055f; val hy = yl + 0.010f
                    g.add("ArrowHead", MeshGen.tube(floatArrayOf(hx, hy, EZ + 0.002f, x, yl + 0.002f, EZ + 0.002f), 0.0018f, 5, false), Mat.Unlit(c, 0.9f, blend = Blend.ADD))
                }
            }
            textNode(g, "Emitter", feats[0].emitter, 0.050f, P3(EX0 + 0.075f, EY0 - 0.040f, EZ), Rgb(1f, 0.85f, 0.45f), 1f, left = true)
            g
        }
        diagramQ = diagramRoot.add(Node("DiagramQ")).also { it.visible = false }
        diagramQ.add("QGround", unitCyl, levelMat, P3((EX0 + EX1) / 2, EY0, EZ)).also { it.scale = P3(0.0022f, EX1 - EX0, 0.0022f); it.rot = Q.axisAngle(0f, 0f, 1f, (PI / 2).toFloat()) }
        textNode(diagramQ, "QMark", "?", 0.12f, P3((EX0 + EX1) / 2, EY0 + 0.12f, EZ), Rgb(1f, 0.85f, 0.45f), 0.9f)
        electron = diagramRoot.add("Electron", sphere, Mat.Unlit(Rgb(0.80f, 0.97f, 1.0f))).also { it.uniformScale(0.0065f) }
        electronGlow = diagramRoot.add("ElectronGlow", quad, Mat.Unlit(Rgb(0.45f, 0.85f, 1.0f), 0.8f, dotTex, Blend.ADD)).also { it.uniformScale(0.032f); it.billboard = true }
        kick = diagramRoot.add("HeatKick", quad, Mat.Unlit(Rgb(1.0f, 0.50f, 0.15f), 0.9f, dotTex, Blend.ADD)).also { it.uniformScale(0.001f); it.billboard = true }
        val bead = MeshGen.sphere(1f, 6, 12, MeshGen.SphereUv.EQUIRECT)
        photons = Array(NPH) { diagramRoot.add("Photon$it", bead, Mat.Unlit(WHITE, 1f, blend = Blend.ADD)).also { n -> n.uniformScale(0.0045f); n.visible = false } }
    }

    // ---------------------------------------------------------------- per frame

    override fun update(dt: Float) {
        t += dt; frame++
        stepLoop(dt)
        // what is in the flame: the loop's sample while the tip sits in the flame and the gas burns
        val inFlame = burnerOn && dist(tip, TIP_FLAME) < 0.012f
        val fs = if (inFlame) carrying else null
        if (fs != null && fs !== shown && heat < 0.02f) { shown = fs; shownUnknown = carryingUnknown; shownIdx = carryingIdx }
        val target = if (fs != null && fs === shown) 1f else 0f
        heat = if (dt == 0f) heat else approach(heat, target, dt * if (target > heat) 2.8f else 5f)
        // cobalt glass: slides between the bench and the line of sight
        glassT = if (dt == 0f) glassT else approach(glassT, if (cobaltUp) 1f else 0f, dt * 2.6f)
        if (glassT != glassShown) {                                           // transforms pushed only while it moves
            glassShown = glassT
            val e = ProcTex.smooth01(glassT)
            glassRig.pos = lerp(GLASS_DOWN, GLASS_UP, e) + P3(0f, 0.06f * sin(PI.toFloat() * e), 0f)
            glassRig.rot = slerp(GLASS_DOWN_ROT, Q.identity(), e)
        }
        for (i in 0 until 7) if (cardFlash[i] > 0f) { cardFlash[i] = max(0f, cardFlash[i] - dt); if (cardFlash[i] == 0f) styleKey = "" }
        restyle()
        animateFlame()
        animateLevels()
        val lang = if (Lang.en) "en" else "zh"
        val key = "$lang/${shownKey()}/$cobaltUp/$burnerOn/$mysteryOn/$solved/${found.size}/$wrong/${(heat * 4).roundToInt()}"
        if (frame % 6 == 0 || key != textKey || dt == 0f) { textKey = key; updateReadout(); updateLabels() }
    }

    // ---- the loop: waypoint path at constant speed, pausing in the salt
    private fun stepLoop(dt: Float) {
        var budget = LOOP_V * dt
        while (budget > 0f && path.isNotEmpty()) {
            if (pause > 0f) { pause -= budget / LOOP_V; if (pause > 0f) { budget = 0f; break }; budget = -pause * LOOP_V; pause = 0f }
            val (p, wait) = path.first(); val d = dist(tip, p)
            if (d <= budget) { tip = p; path.removeFirst(); budget -= d; pause = wait }
            else { val k = budget / d; tip = P3(tip.x + (p.x - tip.x) * k, tip.y + (p.y - tip.y) * k, tip.z + (p.z - tip.z) * k); budget = 0f }
        }
        loop.pos = tip
    }

    /** Send the loop to dish [i] (0..7), dip, and back into the flame carrying [sample]. */
    private fun dipInto(i: Int, sample: Sample, unknown: Boolean, idx: Int) {
        path.clear(); pause = 0f
        val dish = P3(DISH_X[i], DISH_Y + DISH_H / 2 + 0.004f, RACK_Z)
        path += P3(tip.x, TRAVEL_Y, tip.z) to 0f
        path += P3(dish.x, TRAVEL_Y, dish.z) to 0f
        path += dish to 0.25f
        path += P3(dish.x, TRAVEL_Y, dish.z) to 0f
        path += P3(TIP_FLAME.x, TRAVEL_Y, TIP_FLAME.z) to 0f
        path += TIP_FLAME to 0f
        carrying = sample; carryingUnknown = unknown; carryingIdx = idx
    }

    /** Review / opening: the loop already in the flame with [sample]. */
    private fun testNow(sample: Sample, unknown: Boolean, idx: Int) {
        path.clear(); pause = 0f; tip = TIP_FLAME; loop.pos = tip
        carrying = sample; carryingUnknown = unknown; carryingIdx = idx
        shown = sample; shownUnknown = unknown; shownIdx = idx; heat = 1f; styleKey = ""
    }

    private fun shownKey() = shown?.salts?.joinToString("+") { it.symbol } ?: "-"
    private val hidden get() = shownUnknown && !solved && mysteryOn

    /** Everything that depends on the sample / glass / fade step: set only when it changes. */
    private fun restyle() {
        val hq = (heat * 10).roundToInt() / 10f
        val key = "${shownKey()}/$shownUnknown/$cobaltUp/$burnerOn/$hq/$showSpectrum/$showLevels/$mysteryOn/$solved/${found.joinToString()}/${cardFlash.joinToString { if (it > 0f) "1" else "0" }}"
        if (key == styleKey) return
        styleKey = key
        val s = shown
        val lit = burnerOn && s != null && hq > 0f
        // ---- flame
        mantle.visible = burnerOn; cone.visible = burnerOn
        mantle.setMat(Mat.Unlit(Rgb(0.30f, 0.38f, 0.95f), if (cobaltUp) 0.24f else 0.30f, plumeTex, Blend.ADD))
        val xyz = if (s != null) FlameColour.xyz(s, cobaltUp) else DoubleArray(3)
        val c = FlameColour.chroma(xyz); val lv = FlameColour.level(xyz)
        val body = rgbOf(c, lv); val hot = rgbOf(FlameColour.exposed(c, 3.0), lv)
        val layerA = floatArrayOf(0.55f, 0.75f)
        for ((k, n) in plume.withIndex()) { n.visible = lit; n.setMat(Mat.Unlit(body, layerA[k] * hq, plumeTex, Blend.ADD)) }
        core.visible = lit; core.setMat(Mat.Unlit(hot, 0.95f * hq, plumeTex, Blend.ADD))
        for (n in tongues) { n.visible = lit; n.setMat(Mat.Unlit(body, 0.22f * hq, plumeTex, Blend.ADD)) }
        bead.setMat(Mat.Pbr(if (carrying != null) Rgb(0.93f, 0.93f, 0.95f) else Rgb(0.6f, 0.6f, 0.62f), roughness = 0.6f, emissive = if (lit) dim(hot, 0.8f * hq) else null))
        beadGlow.visible = lit; beadGlow.setMat(Mat.Unlit(hot, 0.7f * hq, dotTex, Blend.ADD))
        beam.visible = lit && showSpectrum; beam.setMat(Mat.Unlit(body, 0.35f * hq, blend = Blend.ADD))
        // ---- spectroscope strip
        board.visible = showSpectrum
        stripBg.setMat(Mat.Unlit(WHITE, 1f, if (cobaltUp) rainbowCo else rainbow, Blend.ADD))
        tCurve.visible = cobaltUp
        for (n in lineCore) n.visible = false
        for (n in lineGlow) n.visible = false
        continuum.visible = false; uvMark.visible = false
        for (n in tags) n.visible = false
        if (lit && s != null) layoutStrip(s, hq)
        // ---- chart rows: the salt(s) in the flame glow (not while it is the unknown)
        val inRows = if (lit && !hidden && s != null) s.salts.map { Salts.ALL.indexOf(it) } else emptyList()
        for ((r, g) in rowGlow.withIndex()) g.visible = r in inRows
        // ---- level diagram: the first salt's emitter
        diagramRoot.visible = showLevels
        diagramIdx = if (s != null && burnerOn) Salts.ALL.indexOf(dominantSalt(s)) else -1
        for ((i, g) in diagrams.withIndex()) g.visible = i == diagramIdx && !hidden
        diagramQ.visible = hidden
        // ---- mystery cards
        for (i in 0 until 7) {
            cards[i].visible = mysteryOn
            val ok = i in found
            cardPlates[i].setMat(Mat.Unlit(when { ok -> Rgb(0.35f, 1.0f, 0.55f); cardFlash[i] > 0f -> Rgb(1.0f, 0.30f, 0.25f); else -> Rgb(0.35f, 0.65f, 1.0f) },
                if (ok || cardFlash[i] > 0f) 0.9f else 0.45f, blend = Blend.ADD))
        }
    }

    /** The salt of a sample that dominates what you see (behind cobalt glass: what still gets through). */
    private fun dominantSalt(s: Sample): Salt = s.parts.maxByOrNull { (salt, a) ->
        val x = FlameColour.xyz(Sample(listOf(salt to a)), cobaltUp); FlameColour.level(x) * 10 + x[1] / (1 + x[1])
    }!!.first

    private fun layoutStrip(s: Sample, hq: Float) {
        val feats = s.weighted()
        val vis = feats.filter { it.first.visible && it.first.fwhm < 100.0 }
        val maxW = vis.maxOfOrNull { it.second } ?: return
        var k = 0
        for ((f, w) in vis) {
            if (k >= POOL) break
            val tt = if (cobaltUp) CobaltGlass.transmission(f.nm) else 1.0
            val b = FlameColour.lineBrightness(w * tt, maxW) * hq
            if (b < 0.01) continue
            val col = lineColour(f.nm, b)
            val x = lx(f.nm)
            if (f.isLine) {
                lineCore[k].visible = true; lineCore[k].pos = P3(x, STRIP_Y, SZ + 0.002f); lineCore[k].scale = P3(0.0034f, STRIP_H, 1f)
                lineCore[k].setMat(Mat.Unlit(col, 1f, blend = Blend.ADD))
                lineGlow[k].visible = true; lineGlow[k].pos = P3(x, STRIP_Y, SZ + 0.001f); lineGlow[k].scale = P3(0.022f, STRIP_H, 1f)
                lineGlow[k].setMat(Mat.Unlit(col, 0.7f, gaussTex, Blend.ADD))
            } else {
                lineGlow[k].visible = true; lineGlow[k].pos = P3(x, STRIP_Y, SZ + 0.001f); lineGlow[k].scale = P3(3f * f.fwhm.toFloat() * MPN, STRIP_H, 1f)
                lineGlow[k].setMat(Mat.Unlit(col, 1f, gaussTex, Blend.ADD))
            }
            k++
        }
        // potassium's faint continuum
        feats.firstOrNull { it.first.fwhm >= 100.0 }?.let {
            continuum.visible = true
            continuum.setMat(Mat.Unlit(gray(0.30f * hq), 1f, if (cobaltUp) contTexCo else contTex, Blend.ADD))
        }
        uvMark.visible = feats.any { it.first.nm < 380.0 }
        // the main line of each salt, labelled in nm above the strip
        for ((j, salt) in s.salts.take(2).withIndex()) {
            val m = salt.main
            val tt = if (cobaltUp) CobaltGlass.transmission(m.nm) else 1.0
            if (tt < 0.05) continue
            val label = tagText(m)
            val n = tags[j]
            val img = txt(label); val h = 0.032f
            n.visible = true
            n.setMat(Mat.Unlit(TEXT, 1f, img, Blend.ADD))
            n.scale = P3(h * img.w / img.h, h, 1f)
            n.pos = P3(lx(m.nm) + if (j == 1 && s.salts.size == 2 && abs(lx(m.nm) - lx(s.salts[0].main.nm)) < 0.06f) 0.06f else 0f, STRIP_Y + STRIP_H / 2 + 0.020f, SZ)
        }
    }

    private fun animateFlame() {
        val f1 = 1f + 0.06f * sin(t * 17f) + 0.04f * sin(t * 26.3f) + 0.03f * sin(t * 7.1f)
        val f2 = 1f + 0.05f * sin(t * 19.7f + 1f) + 0.04f * sin(t * 31f)
        val sway = 0.004f * sin(t * 5.1f) + 0.002f * sin(t * 13.3f)
        mantle.scale = P3(MANTLE_W, MANTLE_H * f2, 1f)
        plume[0].scale = P3(P0_W * (1f + 0.03f * sin(t * 9f)), P0_H * f1, 1f); plume[0].pos = P3(FX + sway, TUBE_TOP - 0.006f, FZ)
        plume[1].scale = P3(P1_W, P1_H * f2, 1f); plume[1].pos = P3(FX + sway * 0.6f, TUBE_TOP - 0.004f, FZ)
        core.scale = P3(CORE_W, CORE_H * (0.95f + 0.05f * f1), 1f)
        cone.scale = P3(CONE_W, CONE_H * (1f + 0.02f * sin(t * 23f)), 1f)
        for ((k, n) in tongues.withIndex()) {
            val ph = (t * 1.5f + k * 0.25f) % 1f
            n.pos = P3(FX + sway * 1.5f + 0.004f * sin(k * 2.3f + t * 3f), TIP_FLAME.y + 0.06f + 0.20f * ph, FZ + 0.002f * k)
            val sc = (1f - ph) * 1.1f
            n.scale = P3(0.07f * sc, 0.12f * sc + 0.0005f, 1f)
        }
        beadGlow.uniformScale(0.06f * (0.9f + 0.1f * f1))
    }

    /** One electron of the strongest line's emitter: kicked up by the heat, falls back, emits a photon. */
    private fun animateLevels() {
        val i = diagramIdx
        val on = showLevels && i >= 0 && !hidden && burnerOn && heat > 0.5f
        electron.visible = on; electronGlow.visible = on; kick.visible = on
        if (!on) { for (p in photons) p.visible = false; return }
        val s = Salts.ALL[i]; val f = s.features[s.diagram[0]]
        val x = EX0 + 0.050f
        val yl = EY0 + E_SCALE * f.lowerEv.toFloat(); val yu = EY0 + E_SCALE * f.upperEv.toFloat()
        val ph = t % CYCLE
        val y = when {
            ph < 0.35f -> yl
            ph < 0.75f -> yl + (yu - yl) * ProcTex.smooth01((ph - 0.35f) / 0.40f)
            ph < 1.30f -> yu + 0.0015f * sin(ph * 40f)
            ph < 1.48f -> yu + (yl - yu) * ((ph - 1.30f) / 0.18f).pow(2)
            else -> yl
        }
        electron.pos = P3(x - 0.012f, y, EZ + 0.006f); electronGlow.pos = P3(x - 0.012f, y, EZ + 0.004f)
        // the heat's kick: an orange flash at the electron just before it jumps
        val kp = ((ph - 0.12f) / 0.35f)
        kick.uniformScale(if (kp in 0f..1f) 0.05f * sin(PI.toFloat() * kp) + 0.001f else 0.001f)
        kick.pos = P3(x - 0.012f, yl, EZ + 0.003f)
        // the photon: a wave packet with the line's colour and (scaled) wavelength, flying right
        val age = ph - 1.30f
        if (age < 0f) { for (p in photons) p.visible = false; return }
        val pm = Mat.Unlit(lineColour(f.nm, 1.0), 1f, blend = Blend.ADD)
        val head = x + 0.01f + 0.21f * age
        val lam = (f.nm * 6.0e-5).toFloat()
        val ym = (yu + yl) / 2
        for ((k, p) in photons.withIndex()) {
            val sx = head - k * 0.0046f
            p.visible = sx > x + 0.006f && age < 1.45f
            val env = sin(PI.toFloat() * (k + 0.5f) / NPH)
            p.pos = P3(sx, ym + 0.016f * env * sin(2f * PI.toFloat() * (sx - head) / lam), EZ + 0.008f)
            p.setMat(pm)                                                         // equal Mat -> no change pushed
        }
    }

    // ---------------------------------------------------------------- text

    private fun saltName(s: Salt) = tr(s.zh, s.en.replaceFirstChar { it.uppercase() })
    private fun colourWord(xyz: DoubleArray) = FlameColour.name(xyz).let { tr(it.first, it.second) }
    private fun nm(f: Feature) = if (f.isLine) "%.1f".format(f.nm) else "~%.0f".format(f.nm)
    /** The two strongest visible features of a salt, strongest first, e.g. "589.0 / 589.6". */
    private fun mainLines(s: Salt) = s.features.filter { it.visible }.sortedByDescending { it.power }.take(2).joinToString(" / ") { nm(it) }

    private fun updateReadout() {
        val s = shown
        val lit = burnerOn && s != null && heat > 0.3f
        val line1: String; val line2: String; val line3: String
        if (!burnerOn) {
            line1 = tr("本生灯已关：捏合燃气阀点燃", "Burner off: pinch the gas tap to light it")
            line2 = tr("光子能量 E = hc/λ，hc = 1239.84 eV·nm", "Photon energy E = hc/λ, hc = 1239.84 eV·nm")
            line3 = why(null)
        } else if (!lit || s == null) {
            line1 = tr("本生灯：淡蓝色火焰，自己几乎不发可见光 —— 捏合一盘盐试试", "Bunsen burner: a pale blue flame that gives off almost no visible light — pinch a salt dish")
            line2 = tr("光子能量 E = hc/λ，hc = 1239.84 eV·nm", "Photon energy E = hc/λ, hc = 1239.84 eV·nm")
            line3 = why(null)
        } else {
            val xyz = FlameColour.xyz(s, cobaltUp)
            val col = if (FlameColour.level(xyz) < 0.15) tr("几乎看不见", "almost invisible") else colourWord(xyz)
            line1 = if (hidden) tr("未知样品 · 焰色 %s · 谱线 %s nm", "Unknown sample · flame %s · lines %s nm").format(col, s.salts.joinToString(" · ") { mainLines(it) })
                    else tr("%s · 焰色 %s · 主线 %s nm", "%s · flame %s · main lines %s nm").format(
                        s.salts.joinToString(" + ") { "${saltName(it)} ${it.symbol}" }, col, s.salts.joinToString(" · ") { mainLines(it) })
            val dom = dominantSalt(s)
            val f = dom.features[dom.diagram[0]]
            line2 = if (mysteryOn) mysteryLine() else
                tr("光子能量 E = hc/λ = 1239.84 eV·nm ÷ %.1f nm = %.3f eV（%s）", "Photon energy E = hc/λ = 1239.84 eV·nm ÷ %.1f nm = %.3f eV (%s)").format(
                    f.nm, Photon.energyEv(f.nm), transitionText(f))
            line3 = when {
                cobaltUp -> cobaltLine(if (hidden) null else s)                       // never name the unknown
                mysteryOn && !solved -> tr("对照下方谱线表，捏元素卡作答；黄光太强？举起钴玻璃再看", "Match the lines with the chart, pinch element cards; too yellow? raise the cobalt glass")
                s.salts.contains(Salts.NA) && s.salts.contains(Salts.K) -> tr("钠的黄光盖住了钾的紫色 —— 举起钴玻璃，钾就现形了", "Sodium's yellow hides potassium's violet — raise the cobalt glass and potassium shows up")
                else -> why(if (s.salts.size == 1) dom else null)
            }
        }
        readoutText = "$line1\n$line2\n$line3"
    }

    private fun transitionText(f: Feature) = if (f.upperName.isNotEmpty()) "${f.emitter} ${f.upperName} → ${f.lowerName}"
        else tr("%s 分子带", "%s molecular band").format(f.emitter)

    private fun mysteryLine(): String {
        val m = mystery ?: return ""
        return if (solved) tr("✓ 全部认出：%s（错 %d 次）", "✓ All identified: %s (%d wrong)").format(m.joinToString(" + ") { Salts.ALL[it].symbol }, wrong)
        else tr("未知样品：已认出 %d / %d 种元素%s · 错 %d 次", "Unknown sample: %d of %d elements found%s · %d wrong").format(
            found.size, m.size, if (found.isEmpty()) "" else " (" + found.sorted().joinToString(" ") { Salts.ALL[it].symbol } + ")", wrong)
    }

    private fun cobaltLine(s: Sample?): String {
        val na = s?.salts?.contains(Salts.NA) == true; val k = s?.salts?.contains(Salts.K) == true
        return when {
            na && k -> tr("钴玻璃：钠 589 nm 只透过 %s，钾 766.5 / 404.4 nm 透过 %s → 黄色退去，钾的紫色出现",
                "Cobalt glass: Na 589 nm passes only %s, K 766.5 / 404.4 nm pass %s → yellow gone, potassium's violet shows").format(
                pct(CobaltGlass.transmission(588.995)), pct(CobaltGlass.transmission(766.49)))
            na -> tr("钴玻璃：钠 589 nm 只透过 %s → 钠的黄光被完全挡住", "Cobalt glass: Na 589 nm passes only %s → sodium's yellow is blocked").format(pct(CobaltGlass.transmission(588.995)))
            else -> tr("蓝色钴玻璃吸收 540–650 nm（Co²⁺ 三重吸收带），紫、蓝和深红光能透过", "Cobalt glass absorbs 540–650 nm (the Co²⁺ triplet); violet, blue and deep red pass")
        }
    }

    private fun pct(x: Double) = if (x >= 0.01) "%.0f%%".format(x * 100) else if (x >= 1e-4) "%.2f%%".format(x * 100) else "%.4f%%".format(x * 100)

    /** One-line WHY for the salt in the flame: its own levels -> its own lines (+ the one fact worth knowing). */
    private fun why(s: Salt?): String = when (s) {
        Salts.NA -> tr("能级各不相同 → 谱线各不相同：钠 D 线正落在人眼最敏感处，一点钠就把火焰染黄", "Own levels → own lines: sodium's D lines sit where the eye is most sensitive, so a trace turns flames yellow")
        Salts.K -> tr("钾的 766.5 nm 红线人眼几乎看不见，404.4 nm 紫线 + 弱连续谱 → 紫色；有钠时要透过钴玻璃看", "K's 766.5 nm red line is nearly invisible; 404.4 nm violet + a weak continuum → violet; with Na use cobalt glass")
        Salts.LI -> tr("锂 2p → 2s 跃迁 1.85 eV → 670.8 nm 深红；烟花的红色来自锂盐和锶盐", "Lithium 2p → 2s, 1.85 eV → 670.8 nm crimson; red fireworks use lithium and strontium salts")
        Salts.CA -> tr("橙红色主要来自 CaOH 分子带（622、554 nm）；钙原子自己的 422.7 nm 线是紫色", "The orange-red is mostly CaOH bands (622, 554 nm); calcium's own atomic line, 422.7 nm, is violet")
        Salts.SR -> tr("锶的红色来自 SrOH 分子带（606–682 nm）；红色烟花就是锶盐", "Strontium's red comes from SrOH bands (606–682 nm); red fireworks are strontium salts")
        Salts.BA -> tr("钡 553.5 nm 线 + BaCl 带 → 黄绿色；绿色烟花就是钡盐", "Barium's 553.5 nm line + BaCl bands → yellow-green; green fireworks are barium salts")
        Salts.CU -> tr("铜最强的 324.8 / 327.4 nm 线在紫外看不见；蓝绿色来自 CuCl 分子带", "Copper's strongest lines, 324.8 / 327.4 nm, are ultraviolet; the blue-green is CuCl bands")
        else -> tr("每种元素有自己的能级 → 自己的谱线：烟花的颜色、光谱分析认元素都靠它", "Each element has its own energy levels → its own lines: fireworks and spectral analysis rely on this")
    }

    private fun updateLabels() {
        labels.clear()
        val s = shown
        val lit = burnerOn && s != null && heat > 0.3f
        val flameText = when {
            !burnerOn -> tr("本生灯已关", "Burner off")
            !lit || s == null -> tr("本生灯：淡蓝色火焰", "Bunsen flame: pale blue")
            else -> {
                val xyz = FlameColour.xyz(s, cobaltUp)
                val col = if (FlameColour.level(xyz) < 0.15) tr("几乎看不见", "almost invisible") else colourWord(xyz)
                val who = if (hidden) tr("未知样品", "unknown sample") else s.salts.joinToString(" + ") { it.formula }
                (if (cobaltUp) tr("透过钴玻璃：%s · %s", "Through cobalt glass: %s · %s") else tr("焰色：%s · %s", "Flame: %s · %s")).format(col, who)
            }
        }
        labels += LabelSpec(flameText, P3(FX, 0.43f, 0.06f))
        labels += LabelSpec(if (cobaltUp) tr("上：透过钴玻璃的谱线（蓝线 = 透过率）· 下：各元素谱线", "Top: lines through cobalt glass (blue = transmission) · below: each element")
            else tr("上：分光镜看到的谱线 · 下：各元素谱线（本生–基尔霍夫 1860）", "Top: what the spectroscope sees · below: each element (Bunsen–Kirchhoff 1860)"),
            P3((X0 + X1) / 2 - 0.03f, ROW0_Y - 6 * ROW_PITCH - 0.075f, BOARD_Z + 0.02f), visible = showSpectrum)
        val di = diagramIdx
        if (di >= 0 && !hidden) {
            val f = Salts.ALL[di].features[Salts.ALL[di].diagram[0]]
            labels += LabelSpec(tr("能级跃迁：E = hc/λ = %.2f eV → %s nm", "Energy levels: E = hc/λ = %.2f eV → %s nm").format(Photon.energyEv(f.nm), nm(f).removePrefix("~")),
                P3((EX0 + EX1) / 2 + 0.08f, EY0 - 0.075f, EZ + 0.02f), visible = showLevels)
        } else labels += LabelSpec(tr("能级跃迁：先认出样品", "Energy levels: identify the sample first"), P3((EX0 + EX1) / 2 + 0.08f, EY0 - 0.075f, EZ + 0.02f), visible = showLevels && hidden)
        labels += LabelSpec(if (cobaltUp) tr("蓝色钴玻璃（举起）", "Cobalt glass (raised)") else tr("蓝色钴玻璃", "Cobalt glass"),
            if (cobaltUp) P3(FX + 0.21f, 0.30f, 0.22f) else P3(GLASS_DOWN.x + 0.05f, BENCH + 0.17f, GLASS_DOWN.z))
        labels += LabelSpec(when {
            mysteryOn && !solved -> tr("未知样品：捏元素卡作答", "Unknown sample: pinch element cards")
            mysteryOn -> tr("✓ 认出来了！捏 ? 盘再来一题", "✓ Solved! Pinch the ? dish for another")
            else -> tr("铂丝蘸盐 → 放进火焰", "Platinum loop: dip a salt → into the flame")
        }, P3((DISH_X.first() + DISH_X.last()) / 2, BENCH - 0.012f, RACK_Z + 0.09f))
    }

    // ---------------------------------------------------------------- interaction

    override val tapTargets: List<TapTarget> get() {
        val l = ArrayList<TapTarget>()
        for (i in 0 until 7) { val f = Salts.ALL[i].formula; l += TapTarget("Dish$i", "$f → 蘸取", "$f → dip") { shownIdx == i && !shownUnknown && heat > 0.3f && burnerOn } }
        l += TapTarget("DishQ", "? 盘 → 未知样品", "? dish → unknown sample") { mysteryOn && !solved }
        l += TapTarget("GasTap", "燃气阀 → 开/关", "Gas tap → on/off") { burnerOn }
        l += TapTarget("Cobalt", "钴玻璃 → 举起/放下", "Cobalt glass → raise/lower") { cobaltUp }
        if (mysteryOn) for (i in 0 until 7) { val sy = Salts.ALL[i].symbol; l += TapTarget("Card$i", "答 $sy", "Answer $sy") { i in found } }
        return l
    }

    override fun isTarget(name: String) = name.startsWith("Dish") || name == "GasTap" || name == "Cobalt" || (name.startsWith("Card") && mysteryOn)

    override fun onTap(name: String) {
        when {
            name == "DishQ" -> newMystery()
            name.startsWith("Dish") -> { val i = name.removePrefix("Dish").toIntOrNull() ?: return; dipInto(i, Sample(Salts.ALL[i]), false, i) }
            name == "GasTap" -> burnerOn = !burnerOn
            name == "Cobalt" -> cobaltUp = !cobaltUp
            name.startsWith("Card") -> answer(name.removePrefix("Card").toIntOrNull() ?: return)
        }
    }

    override fun tapMessage(name: String): String? = when {
        name == "DishQ" -> tr("未知样品进火焰：读它的谱线，捏元素卡作答", "Unknown sample into the flame: read its lines, pinch element cards")
        name.startsWith("Dish") -> {
            val s = Salts.ALL[name.removePrefix("Dish").toIntOrNull() ?: 0]
            val m = s.main; val xyz = FlameColour.xyz(Sample(s), false)
            if (!burnerOn) tr("蘸取 %s：先捏燃气阀点燃本生灯", "Dipped %s: pinch the gas tap to light the burner first").format(s.formula)
            else tr("蘸取 %s：%s 的 %s nm %s → %s色火焰", "Dipped %s: %s %s nm %s → %s flame").format(s.formula, m.emitter, nm(m).removePrefix("~"),
                if (m.isLine) tr("线", "line") else tr("带", "band"), colourWord(xyz))
        }
        name == "GasTap" -> if (burnerOn) tr("点燃本生灯：淡蓝色火焰，几乎不发光", "Burner lit: a pale blue, nearly invisible flame") else tr("关闭燃气：火焰熄灭", "Gas off: the flame goes out")
        name == "Cobalt" -> if (cobaltUp) tr("举起钴玻璃：540–650 nm 被吸收，钠的黄光消失", "Cobalt glass up: 540–650 nm absorbed, sodium's yellow disappears")
                            else tr("放下钴玻璃：看到火焰本来的颜色", "Cobalt glass down: the flame's own colour again")
        name.startsWith("Card") -> {
            val s = Salts.ALL[lastAnswerIdx.coerceIn(0, 6)]
            when {
                lastAnswer > 0 && solved -> tr("✓ 对！%s 的 %s nm 就在谱上 —— 全部认出", "✓ Right! %s's %s nm line is there — all identified").format(s.symbol, nm(s.main).removePrefix("~"))
                lastAnswer > 0 -> tr("✓ 对！%s 的 %s nm 就在谱上 —— 还有一种", "✓ Right! %s's %s nm line is there — one more to find").format(s.symbol, nm(s.main).removePrefix("~"))
                else -> tr("✗ 不是 %s：谱上没有它的 %s nm", "✗ Not %s: its %s nm line is missing").format(s.symbol, nm(s.main).removePrefix("~"))
            }
        }
        else -> null
    }

    private fun newMystery() {
        val prev = mystery
        var pick = if (mysteryCount == 0) MYSTERY_POOL[0] else MYSTERY_POOL[rng.nextInt(MYSTERY_POOL.size)]
        var guard = 0
        while (pick == prev && guard++ < 10) pick = MYSTERY_POOL[rng.nextInt(MYSTERY_POOL.size)]
        mysteryCount++
        mystery = pick; found.clear(); wrong = 0; solved = false; mysteryOn = true; lastAnswer = 0
        dipInto(7, Sample(*pick.map { Salts.ALL[it] }.toTypedArray()), true, -1)
    }

    private fun answer(i: Int) {
        val m = mystery ?: return
        if (solved) return                                                   // the round is over: the note keeps saying so
        lastAnswerIdx = i
        if (i in m) { found += i; lastAnswer = 1; if (found.containsAll(m)) solved = true }
        else { wrong++; lastAnswer = -1; cardFlash[i] = 0.8f }
    }

    fun reveal() { val m = mystery ?: return; found.addAll(m); solved = true }
    fun applyCobalt(on: Boolean) { cobaltUp = on }

    override fun reviewVariants(): Map<String, () -> Unit> = mapOf(
        "cobalt" to { testNow(Sample(Salts.NA, Salts.K), false, -1); cobaltUp = true; glassT = 1f; glassShown = -1f },
        "mystery" to { cobaltUp = false; glassT = 0f; glassShown = -1f; newMystery(); testNow(carrying!!, true, -1) },
        "copper" to { mysteryOn = false; mystery = null; testNow(Sample(Salts.CU), false, 6) },
    )

    @Composable
    override fun Panel() {
        LabPanel(readout) {
            LabRow {
                LabSwitch(tr("蓝色钴玻璃", "Cobalt glass"), cobaltUp) { applyCobalt(it) }
                LabSwitch(tr("分光镜谱线", "Spectroscope"), showSpectrum) { showSpectrum = it }
                LabSwitch(tr("能级图", "Energy levels"), showLevels) { showLevels = it }
                Button(onClick = { LabHost.onTap("DishQ", fromPanel = true) }) { Text(tr("新的未知样品", "New unknown sample")) }
                if (mysteryOn && !solved) Button(onClick = { reveal() }) { Text(tr("揭晓答案", "Reveal")) }
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    private fun textNode(parent: Node, name: String, s: String, h: Float, pos: P3, c: Rgb, a: Float = 1f, left: Boolean = false): Node {
        val img = txt(s); val w = h * img.w / img.h
        return parent.add(name, unitQuad, Mat.Unlit(c, a, img, Blend.ADD), if (left) P3(pos.x + w / 2, pos.y, pos.z) else pos).also { it.scale = P3(w, h, 1f) }
    }

    /** Display colour of λ at brightness [b] (linear), sRGB-encoded. */
    private fun lineColour(nm: Double, b: Double): Rgb { val c = Spectrum.display(nm); return Rgb(OptGeo.enc((c[0] * b).toFloat()), OptGeo.enc((c[1] * b).toFloat()), OptGeo.enc((c[2] * b).toFloat())) }
    private fun rgbOf(c: DoubleArray, k: Double) = Rgb(OptGeo.enc((c[0] * k).toFloat()), OptGeo.enc((c[1] * k).toFloat()), OptGeo.enc((c[2] * k).toFloat()))
    private fun gray(v: Float) = Rgb(v, v, v)
    private fun dim(c: Rgb, k: Float) = Rgb(c.r * k, c.g * k, c.b * k)
    private fun approach(v: Float, target: Float, step: Float) = if (v < target) min(target, v + step) else max(target, v - step)
    private fun dist(a: P3, b: P3) = (a - b).length
    private fun lerp(a: P3, b: P3, k: Float) = P3(a.x + (b.x - a.x) * k, a.y + (b.y - a.y) * k, a.z + (b.z - a.z) * k)
    private fun norm(p: P3): P3 { val l = p.length; return P3(p.x / l, p.y / l, p.z / l) }
    private fun slerp(a: Q, b: Q, k: Float): Q {
        var bx = b.x; var by = b.y; var bz = b.z; var bw = b.w
        if (a.x * bx + a.y * by + a.z * bz + a.w * bw < 0f) { bx = -bx; by = -by; bz = -bz; bw = -bw }
        val x = a.x + (bx - a.x) * k; val y = a.y + (by - a.y) * k; val z = a.z + (bz - a.z) * k; val w = a.w + (bw - a.w) * k
        val l = sqrt(x * x + y * y + z * z + w * w)
        return Q(x / l, y / l, z / l, w / l)
    }
}
