// 凸透镜成像规律 (Kepler, Dioptrice 1611) — the textbook optical bench, with the rays drawn in space.
//
// LESSON: where the candle stands decides what the lens does. Beyond 2F: a small inverted real image
// between F′ and 2F′ (camera). Between F and 2F: a large inverted real image beyond 2F′ (projector).
// Inside F: no screen catches anything — the rays leave diverging, and their backward extensions
// meet at an upright, magnified VIRTUAL image on the candle's side (magnifier). At F: parallel, no image.
// The student DOES it: pinch-drag the candle and the screen along the rail and hunt for the sharp
// image, exactly as in the school lab — the image on the screen blurs by the true amount
// (blur circle c = D·|s − v|/v) — while the three principal rays and the whole cone of light through
// the lens aperture show in 3-D WHERE the image forms, with or without a screen there.
//
// SCALE: the real bench (1 m rail, f = 10/15/20 cm, a 7 cm lens, a 3.25 cm flame) at ×1.6 ALONG the
// axis and ×3.2 ACROSS it (heights and depth, i.e. a transverse exaggeration ×2, said once in a label):
// real ray heights are a few cm, invisible from 2.5 m. That is an affine map of the whole optical
// system — straight rays stay straight, every intersection (image points, F, 2F) stays where it is,
// the thin-lens numbers are unchanged; only the drawn angles steepen. The core works in real cm; the
// rail is marked in real cm and every number in the readout is the real one. The bench is yawed 25°
// so it reads long across the view while the screen's face (the image) still turns toward the viewer.
//
// Physics: core/lens/ThinLens (Gaussian thin lens, real-is-positive; JVM-tested ray by ray).
// Art: LensArt (stroke-font rail scale, baked blur levels of the flame, lensmaker-curved glass).

package com.bytedance.physicslab.solar.demos.lens

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
import com.bytedance.physicslab.solar.core.lens.ThinLens
import com.bytedance.physicslab.solar.core.lens.ThinLens.Case
import com.bytedance.physicslab.solar.demos.prism.OptGeo
import com.bytedance.physicslab.solar.lab.About
import com.bytedance.physicslab.solar.lab.BakeCache
import com.bytedance.physicslab.solar.lab.Bi
import com.bytedance.physicslab.solar.lab.Challenge
import com.bytedance.physicslab.solar.lab.Lesson
import com.bytedance.physicslab.solar.lab.LessonStep
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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object LensDemo : SpecDemo() {
    override val id = "lens"
    override val title = "凸透镜成像规律"  // i18n-ok (Chinese half of the title; English is DemoRegistry.TITLE_EN)
    override val credit = "Kepler · 1611"

    // ---------------------------------------------------------------- the real bench (cm)
    private const val S = 0.016f                 // AXIAL display metres per real cm: 1 m rail -> 1.6 m
    private const val TX = 2f                    // transverse exaggeration (heights / depth across the axis)
    private const val ST = S * TX                // TRANSVERSE display metres per real cm
    val F_CM = intArrayOf(10, 15, 20)
    private const val D_CM = 7.0                 // lens clear aperture (a 7 cm school lens)
    private const val TIP_CM = 3.0               // flame tip above the optical axis
    private const val BASE_CM = -0.25            // flame base (just above the wick)
    private const val HF_CM = TIP_CM - BASE_CM   // flame height
    private const val U_MIN = 4.0                // carriers cannot get closer than 4 cm to the lens
    private const val U_MAX = 48.0
    private const val S_MIN = 4.0
    private const val S_MAX = 48.0
    private const val V_FIT = 46.0               // presets keep v <= 46 cm so the screen can still reach it
    private const val SHARP_MM = 1.0             // blur circle below 1 mm = sharp to the eye
    private const val SNAP_MM = 2.5              // releasing the screen this close snaps it into focus

    // ---------------------------------------------------------------- display layout (bench-local m)
    private val YAW = Math.toRadians(25.0).toFloat()
    private val B0 = P3(0.0f, 0f, 0.0f)          // lens position (bench origin) in the volume
    private const val AXIS_Y = 0.10f             // about eye height: the user looks ACROSS the rays
    private const val PLINTH_Y = -0.240f
    private const val RAIL_TOP = -0.180f
    private const val X_LIM = 0.84f              // rays end here (rail end, inside the volume)
    private val LENS_R = (D_CM / 2 * ST).toFloat()
    private const val CARD_W = 10f * ST          // screen card across (z): 10 cm real
    private const val CARD_LO = -7.5f * ST       // card bottom / top relative to the axis (7.5 cm below, 3 cm above):
    private const val CARD_HI = 3.0f * ST        // the image is inverted, so most of the card is below the axis
    private const val LENS_LIP = 0.003f          // glass beyond the clear aperture, under the ring
    private const val LENS_EDGE = 0.008f         // edge thickness (display)
    private const val N_DASH = 24
    private const val N_AP = 12                  // rim rays through the aperture
    private const val NC = 20                    // cone surface segments (3 cones = 147 vertices per drag frame)

    // ---------------------------------------------------------------- state (taps update the panel via uiEpoch)
    var fIdx by mutableIntStateOf(0); private set
    var showRays by mutableStateOf(true)
    var showVirtual by mutableStateOf(true)
    var uT = 15.0; private set                  // target positions (cm), what the hand / presets set
    var sT = 30.0; private set
    private var uCur = 15.0
    private var sCur = 30.0
    private var dragging: String? = null
    private var t = 0f
    private var laid = DoubleArray(5) { Double.NaN }
    private var lensShown = -1

    // results of the last layout (readout / labels / tests)
    var vCm = 0.0; private set
    var blurMm = 0.0; private set
    var sharp = false; private set
    private var case = Case.BETWEEN
    private var imagePointShown = false

    // ---------------------------------------------------------------- scene refs
    private lateinit var bench: Node
    private lateinit var candleMount: Node
    private lateinit var screenMount: Node
    private lateinit var lensNode: Node
    private lateinit var glintNode: Node
    private lateinit var lensPlane: Node
    private lateinit var lensMeshes: Array<MeshData>
    private lateinit var flame: Array<Node>
    private lateinit var ghostFlame: Array<Node>
    private lateinit var ghost: Node
    private lateinit var rayCore: Array<Array<Node>>       // [ray][incident, after]
    private lateinit var rayGlow: Array<Array<Node>>
    private lateinit var dashes: Array<Array<Node>>
    private lateinit var apIn: Array<Node>
    private lateinit var apOut: Array<Node>
    private lateinit var coneInMd: MeshData
    private lateinit var coneOutMd: MeshData
    private lateinit var conePhMd: MeshData
    private lateinit var coneIn: Node
    private lateinit var coneOut: Node
    private lateinit var conePh: Node
    private lateinit var imgMd: Array<MeshData>
    private lateinit var imgNode: Array<Node>
    private lateinit var imgPoint: Array<Node>
    private lateinit var focusDots: Array<Node>
    private lateinit var focusTags: Array<Node>
    private lateinit var focusDrops: Array<Node>
    private lateinit var zones: Array<Node>
    private lateinit var sharpBar: Node
    private lateinit var blurTex: List<Image>
    private val imgAlpha = FloatArray(2) { -1f }
    private val imgLevel = IntArray(2) { -1 }

    // ---------------------------------------------------------------- materials (sRGB)
    private val alu = Mat.Pbr(Rgb(0.80f, 0.82f, 0.85f), metallic = 1f, roughness = 0.32f)
    private val darkSteel = Mat.Pbr(Rgb(0.30f, 0.31f, 0.33f), metallic = 1f, roughness = 0.40f)
    private val anodized = Mat.Pbr(Rgb(0.05f, 0.055f, 0.065f), metallic = 0.4f, roughness = 0.45f)
    private val brass = Mat.Pbr(Rgb(0.96f, 0.78f, 0.45f), metallic = 1f, roughness = 0.22f)
    private val wax = Mat.Pbr(Rgb(0.94f, 0.89f, 0.80f), metallic = 0f, roughness = 0.55f, emissive = Rgb(0.16f, 0.08f, 0.025f))
    private val waxTop = Mat.Pbr(Rgb(0.98f, 0.86f, 0.62f), metallic = 0f, roughness = 0.30f, emissive = Rgb(0.45f, 0.24f, 0.06f))
    private val wick = Mat.Pbr(Rgb(0.07f, 0.06f, 0.05f), metallic = 0f, roughness = 0.8f)
    private val card = Mat.Pbr(Rgb(0.46f, 0.46f, 0.45f), metallic = 0f, roughness = 0.95f)
    private val glass = Mat.Pbr(Rgb(0.78f, 0.90f, 0.96f), metallic = 0f, roughness = 0.04f, alpha = 0.30f)
    private val slabTex by lazy {
        ProcTex.bakePlanet(256, 128, Rgb(0.035f, 0.037f, 0.042f), Rgb(0.060f, 0.062f, 0.070f), Rgb(0.10f, 0.102f, 0.11f), 0f, 97)
    }
    private val slab get() = Mat.Pbr(Rgb(1f, 1f, 1f), metallic = 0f, roughness = 0.85f, tex = slabTex)

    private val RAY_C = arrayOf(Rgb(1.00f, 0.78f, 0.28f), Rgb(1.00f, 0.96f, 0.88f), Rgb(1.00f, 0.45f, 0.36f))
    private val WARM = Rgb(1.00f, 0.74f, 0.40f)
    private val ZONE_C = arrayOf(Rgb(0.30f, 0.58f, 1.00f), Rgb(0.32f, 0.88f, 0.48f), Rgb(1.00f, 0.62f, 0.20f))
    private val glowTex: Image by lazy { LensArt.glowTex() }
    private val fadeTex: Image by lazy { LensArt.fadeTex() }

    // ---------------------------------------------------------------- helpers
    private fun d(cm: Double) = (cm * S).toFloat()            // along the axis
    private fun tv(cm: Double) = (cm * ST).toFloat()          // across the axis
    val f get() = F_CM[fIdx].toDouble()
    private fun toWorld(x: Float, y: Float, z: Float): P3 {
        val c = cos(YAW); val s = sin(YAW)
        return P3(B0.x + x * c + z * s, B0.y + y, B0.z - x * s + z * c)
    }

    // ---------------------------------------------------------------- build

    override fun buildSpec(cacheDir: File): Node {
        fIdx = 0; showRays = true; showVirtual = true
        uT = 15.0; sT = 30.0; uCur = uT; sCur = sT; dragging = null; t = 0f
        laid.fill(Double.NaN); imgAlpha.fill(-1f); imgLevel.fill(-1); lensShown = -1
        blurTex = BakeCache.load(cacheDir, "lens_blur", 2) { LensArt.blurLevels() }

        val root = Node("LensRoot")
        val unitCyl = MeshGen.cylinder(1f, 1f, 20)
        val thinCyl = MeshGen.cylinder(1f, 1f, 8)
        val unitSphere = MeshGen.sphere(1f, 14, 28, MeshGen.SphereUv.EQUIRECT)
        bench = root.add(Node("Bench")).also { it.pos = B0; it.rot = Q.axisAngle(0f, 1f, 0f, YAW) }
        val b = bench

        // ---- plinth, rail, scale plate ----
        b.add("Plinth", MeshGen.box(1.72f, 0.030f, 0.30f), slab, P3(0f, PLINTH_Y, 0f))
        b.add("PlinthBand", MeshGen.box(1.73f, 0.008f, 0.31f), alu, P3(0f, PLINTH_Y + 0.005f, 0f))
        val railH = RAIL_TOP - (PLINTH_Y + 0.015f)
        b.add("Rail", MeshGen.box(1.66f, railH, 0.07f), alu, P3(0f, RAIL_TOP - railH / 2, 0f))
        b.add("RailGroove", MeshGen.box(1.66f, 0.004f, 0.016f), darkSteel, P3(0f, RAIL_TOP + 0.0005f, 0f))
        for (sx in floatArrayOf(-1f, 1f)) b.add("RailEnd", MeshGen.box(0.016f, railH + 0.012f, 0.08f), darkSteel, P3(sx * 0.838f, RAIL_TOP - railH / 2 + 0.006f, 0f))
        val plateH = railH - 0.004f
        val plateZ = 0.0365f
        b.add("ScalePlate", MeshGen.box(1.64f, plateH, 0.003f), anodized, P3(0f, RAIL_TOP - plateH / 2 - 0.002f, plateZ))
        val rulerW = (LensArt.CM_HI - LensArt.CM_LO) * S
        // zones (u > 2f, f < u < 2f, u < f, and on the image side f..2f, beyond 2f): tints behind the ticks
        zones = Array(5) { k ->
            b.add("Zone$k", OptGeo.quad(1f, plateH * 0.92f), Mat.Unlit(ZONE_C[if (k < 3) k else k - 3], alpha = 0.15f, blend = Blend.TRANSPARENT),
                P3(0f, RAIL_TOP - plateH / 2 - 0.002f, plateZ + 0.0020f))
        }
        val rulerTex = BakeCache.load(cacheDir, "lens_ruler", 2) { listOf(LensArt.rulerTex()) }[0]
        b.add("Ruler", OptGeo.quad(rulerW, plateH * 0.94f), Mat.Unlit(Rgb(0.93f, 0.95f, 0.98f), alpha = 0.95f, tex = rulerTex, blend = Blend.TRANSPARENT),
            P3(0f, RAIL_TOP - plateH / 2 - 0.002f, plateZ + 0.0035f))

        // ---- optical axis, F / 2F marks ----
        b.add("Axis", thinCyl, Mat.Unlit(Rgb(0.70f, 0.80f, 0.92f), alpha = 0.22f, blend = Blend.TRANSPARENT), P3(0f, AXIS_Y, 0f)).also {
            it.rot = Q.axisAngle(0f, 0f, 1f, (PI / 2).toFloat()); it.scale = P3(0.0009f, 2 * X_LIM, 0.0009f)
        }
        val tagNames = listOf("2F", "F", "F'", "2F'")
        focusDots = Array(4) { k ->
            val fMain = k == 1 || k == 2
            b.add("FDot$k", unitSphere, Mat.Unlit(if (fMain) Rgb(1f, 0.82f, 0.35f) else Rgb(0.80f, 0.86f, 0.95f), alpha = 1f, blend = Blend.ADD)).also {
                it.uniformScale(if (fMain) 0.0055f else 0.0045f)
            }
        }
        focusTags = Array(4) { k ->
            val tex = LensArt.tagTex(tagNames[k])
            b.add("FTag$k", OptGeo.quad(0.070f, 0.035f), Mat.Unlit(if (k == 1 || k == 2) Rgb(1f, 0.85f, 0.45f) else Rgb(0.82f, 0.88f, 0.96f),
                alpha = 0.95f, tex = tex, blend = Blend.TRANSPARENT))
        }
        focusDrops = Array(4) { b.add("FDrop$it", thinCyl, Mat.Unlit(Rgb(0.75f, 0.82f, 0.92f), alpha = 0.16f, blend = Blend.TRANSPARENT)) }

        // ---- carriers ----
        fun carrier(g: Node, topY: Float) {
            g.add("Carrier", MeshGen.box(0.064f, 0.022f, 0.086f), darkSteel, P3(0f, RAIL_TOP + 0.011f, 0f))
            g.add("Thumb", unitCyl, brass, P3(0f, RAIL_TOP + 0.011f, 0.052f)).also {
                it.scale = P3(0.008f, 0.020f, 0.008f); it.rot = Q.axisAngle(1f, 0f, 0f, (PI / 2).toFloat())
            }
            val base = RAIL_TOP + 0.022f
            val hh = min(0.080f, (topY - base) * 0.6f)              // post holder, then the steel post
            g.add("Holder", unitCyl, darkSteel, P3(0f, base + hh / 2, 0f)).also { it.scale = P3(0.015f, hh, 0.015f) }
            g.add("HolderRing", unitCyl, alu, P3(0f, base + hh + 0.001f, 0f)).also { it.scale = P3(0.017f, 0.004f, 0.017f) }
            val h = topY - base - hh
            if (h > 0.002f) g.add("Post", unitCyl, alu, P3(0f, base + hh + h / 2, 0f)).also { it.scale = P3(0.0095f, h, 0.0095f) }
        }

        // ---- candle ----
        candleMount = b.add(Node("CandleMount"))
        // a 2 cm candle drawn at the transverse scale (the candle body plays no optical role)
        val candleTop = AXIS_Y - 0.014f
        val candleH = 0.150f
        val dishY = candleTop - candleH - 0.005f
        carrier(candleMount, dishY - 0.005f)
        candleMount.add("Dish", MeshGen.cylinder(0.052f, 0.008f, 32), brass, P3(0f, dishY, 0f))
        candleMount.add("Cup", MeshGen.cylinder(0.039f, 0.024f, 32), brass, P3(0f, dishY + 0.014f, 0f))
        candleMount.add("Candle", MeshGen.cylinder(0.032f, candleH, 32), wax, P3(0f, candleTop - candleH / 2, 0f)).also { it.tapRadius = 0.09f }
        candleMount.add("WaxPool", MeshGen.cylinder(0.029f, 0.004f, 32), waxTop, P3(0f, candleTop + 0.0005f, 0f))
        candleMount.add("Wick", unitCyl, wick, P3(0f, candleTop + 0.007f, 0f)).also { it.scale = P3(0.0022f, 0.014f, 0.0022f) }
        val flameG = candleMount.add(Node("Flame")).also { it.pos = P3(0f, AXIS_Y, 0f) }
        flame = buildFlame(flameG, unitSphere, 1f, 1f)

        // ---- lens on its holder (fixed at the 50 cm mark) ----
        val lensMount = b.add(Node("LensMount"))
        carrier(lensMount, AXIS_Y - LENS_R - 0.034f)
        lensMount.add("Fork", MeshGen.box(0.020f, 0.028f, 0.030f), darkSteel, P3(0f, AXIS_Y - LENS_R - 0.020f, 0f))
        lensMount.add("LensRing", LensArt.ringYZ(LENS_R + 0.0070f, 0.0060f), darkSteel, P3(0f, AXIS_Y, 0f))
        lensMeshes = Array(3) { k ->
            // the real BK7 lens (lensmaker curvature for this f) drawn at the transverse scale
            val rc = ThinLens.biconvexRadius(F_CM[k].toDouble(), 1.5168) * ST
            LensArt.lens(LENS_R + LENS_LIP, rc.toFloat(), LENS_EDGE)
        }
        lensNode = lensMount.add("Lens", lensMeshes[0], glass, P3(0f, AXIS_Y, 0f)).also { it.tapRadius = 0.13f }
        lensMount.add("LensEdge", LensArt.ringYZ(LENS_R + 0.0035f, 0.0020f), Mat.Unlit(Rgb(0.70f, 0.90f, 1f), alpha = 0.40f, blend = Blend.ADD), P3(0f, AXIS_Y, 0f))
        // the thin-lens plane (textbook symbol): principal rays that pass outside the glass bend here
        lensPlane = b.add("LensPlane", thinCyl, Mat.Unlit(Rgb(0.70f, 0.85f, 1f), alpha = 0.20f, blend = Blend.TRANSPARENT))
        // a glint on the candle-side face, upper left as seen by the viewer
        val glint = FloatArray(14 * 3) { k ->
            val a = Math.toRadians(25.0 + 50.0 * (k / 3) / 13.0); val r = LENS_R * 0.78
            when (k % 3) { 0 -> 0f; 1 -> (r * sin(a)).toFloat(); else -> (-r * cos(a)).toFloat() }
        }
        glintNode = lensMount.add("Glint", MeshGen.tube(glint, 0.0026f, 6, false), Mat.Unlit(Rgb(0.85f, 0.95f, 1f), alpha = 0.55f, blend = Blend.ADD), P3(0f, AXIS_Y, 0f))

        // ---- screen ----
        screenMount = b.add(Node("ScreenMount"))
        val cardMidY = AXIS_Y + (CARD_LO + CARD_HI) / 2
        val cardH = CARD_HI - CARD_LO
        carrier(screenMount, AXIS_Y + CARD_LO - 0.014f)
        screenMount.add("Screen", MeshGen.box(0.008f, cardH, CARD_W), card, P3(0.004f, cardMidY, 0f)).also { it.tapRadius = 0.20f }
        for (sy in floatArrayOf(-1f, 1f)) screenMount.add("FrameH", MeshGen.box(0.013f, 0.013f, CARD_W + 0.022f), alu, P3(0.005f, cardMidY + sy * (cardH / 2 + 0.0055f), 0f))
        for (sz in floatArrayOf(-1f, 1f)) screenMount.add("FrameV", MeshGen.box(0.013f, cardH + 0.024f, 0.013f), alu, P3(0.005f, cardMidY, sz * (CARD_W / 2 + 0.0055f)))
        screenMount.add("CardFoot", MeshGen.box(0.024f, 0.014f, 0.08f), darkSteel, P3(0.005f, AXIS_Y + CARD_LO - 0.013f, 0f))
        sharpBar = screenMount.add("SharpBar", MeshGen.box(0.002f, 0.007f, CARD_W * 0.9f), Mat.Unlit(Rgb(0.35f, 1f, 0.55f), alpha = 0.9f, blend = Blend.ADD),
            P3(-0.0035f, AXIS_Y + CARD_HI + 0.0055f, 0f))
        imgMd = Array(2) { OptGeo.grid(2, 2) }
        imgNode = Array(2) { k -> screenMount.add("Image$k", imgMd[k], Mat.Unlit(Rgb(1f, 1f, 1f), alpha = 0f, tex = blurTex[0], blend = Blend.ADD)) }

        // ---- rays: principal (core + glow per segment), dashes, aperture rim rays, cone surfaces ----
        rayCore = Array(3) { i -> Array(2) { b.add("Ray${i}_$it", thinCyl, Mat.Unlit(RAY_C[i], alpha = 0.95f, blend = Blend.ADD)) } }
        rayGlow = Array(3) { i -> Array(2) { b.add("RayGlow${i}_$it", thinCyl, Mat.Unlit(RAY_C[i], alpha = 0.10f, blend = Blend.ADD)) } }
        dashes = Array(3) { i -> Array(N_DASH) { b.add("Dash${i}_$it", thinCyl, Mat.Unlit(RAY_C[i], alpha = 0.75f, blend = Blend.ADD)) } }
        val apMat = Mat.Unlit(WARM, alpha = 0.30f, blend = Blend.ADD)
        apIn = Array(N_AP) { b.add("ApIn$it", thinCyl, apMat) }
        apOut = Array(N_AP) { b.add("ApOut$it", thinCyl, apMat) }
        coneInMd = OptGeo.grid(2, NC + 1); coneOutMd = OptGeo.grid(3, NC + 1); conePhMd = OptGeo.grid(2, NC + 1)
        coneIn = b.add("ConeIn", coneInMd, Mat.Unlit(WARM, alpha = 0.075f, blend = Blend.ADD))
        coneOut = b.add("ConeOut", coneOutMd, Mat.Unlit(WARM, alpha = 0.085f, blend = Blend.ADD))
        conePh = b.add("ConePh", conePhMd, Mat.Unlit(WARM, alpha = 0.035f, blend = Blend.ADD))

        // ---- where the image is: point marker (real image), ghost candle (virtual image) ----
        imgPoint = arrayOf(
            b.add("ImgPoint", unitSphere, Mat.Unlit(Rgb(1f, 0.95f, 0.85f), alpha = 1f, blend = Blend.ADD)).also { it.uniformScale(0.0055f) },
            b.add("ImgPointGlow", OptGeo.quad(1f, 1f), Mat.Unlit(WARM, alpha = 0.55f, tex = glowTex, blend = Blend.ADD)).also { it.uniformScale(0.055f); it.billboard = true },
        )
        ghost = b.add(Node("Ghost"))
        val gTop = -0.014f; val gH = 0.090f
        // an open tube (no caps) whose texture fades out downward: only the top of the candle shows
        val gMd = MeshGen.tube(floatArrayOf(0f, gTop, 0f, 0f, gTop - gH / 2, 0f, 0f, gTop - gH, 0f), 0.032f, 24, false)
        for (i in 0 until gMd.vertexCount) gMd.uv[i * 2] = (0.5f + gMd.uv[i * 2] * (fadeTex.w - 1)) / fadeTex.w   // no U wrap-around
        ghost.add("GhostBody", gMd, Mat.Unlit(Rgb(1f, 0.86f, 0.66f), alpha = 0.14f, tex = fadeTex, blend = Blend.ADD))
        ghostFlame = buildFlame(ghost, unitSphere, 0.18f, 0.25f)

        update(0f)
        return root
    }

    /** Layered additive flame (outer, core, blue base, tip, glow) about the wick top at the group origin. */
    /** Layered additive flame (outer, core, blue base, tip, glow) about the wick top at the group origin,
     *  sized at the transverse scale: the tip is TIP_CM = 3.0 cm above the wick (0.096 m). */
    private fun buildFlame(g: Node, sphere: MeshData, k: Float, glow: Float): Array<Node> = arrayOf(
        g.add("FlameOuter", sphere, Mat.Unlit(Rgb(1.00f, 0.50f, 0.12f), alpha = 0.55f * k, blend = Blend.ADD), P3(0f, 0.020f * TX, 0f)),
        g.add("FlameCore", sphere, Mat.Unlit(Rgb(1.00f, 0.90f, 0.62f), alpha = 0.85f * k, blend = Blend.ADD), P3(0f, 0.0145f * TX, 0f)),
        g.add("FlameBlue", sphere, Mat.Unlit(Rgb(0.30f, 0.45f, 1.00f), alpha = 0.45f * k, blend = Blend.ADD), P3(0f, 0.0012f * TX, 0f)),
        g.add("FlameTip", sphere, Mat.Unlit(Rgb(1.00f, 0.62f, 0.25f), alpha = 0.35f * k, blend = Blend.ADD), P3(0f, 0.0375f * TX, 0f)),
        g.add("FlameGlow", OptGeo.quad(1f, 1f), Mat.Unlit(Rgb(1f, 0.70f, 0.35f), alpha = 0.40f * glow, tex = glowTex, blend = Blend.ADD), P3(0f, 0.019f * TX, 0f))
            .also { it.billboard = true },
    )

    /** Flame flicker: transforms only (the optics use the nominal flame). */
    private fun flicker(n: Array<Node>, ph: Float, glow: Float) {
        val fl = 1f + 0.06f * sin(t * 17f + ph) + 0.04f * sin(t * 26.3f + ph)
        val sw = (0.0014f * sin(t * 5.3f + ph) + 0.0007f * sin(t * 11.1f)) * TX
        n[0].scale = P3(0.0138f * TX, 0.0258f * TX * fl, 0.0138f * TX); n[0].pos = P3(sw * 0.6f, 0.020f * TX * fl, 0f)
        n[1].scale = P3(0.0072f * TX, 0.0168f * TX * fl, 0.0072f * TX)
        n[2].scale = P3(0.0072f * TX, 0.0054f * TX, 0.0072f * TX)
        n[3].scale = P3(0.0054f * TX, 0.0126f * TX * fl, 0.0054f * TX); n[3].pos = P3(sw, 0.0372f * TX * fl, 0f)
        n[4].uniformScale(glow * (0.96f + 0.04f * fl))
    }

    // ---------------------------------------------------------------- per frame

    override fun update(dt: Float) {
        t += dt
        val k = if (dt == 0f) 1.0 else 1 - exp(-dt * 9.0)
        if (dragging != "Candle") { uCur += (uT - uCur) * k; if (abs(uCur - uT) < 1e-4) uCur = uT }
        if (dragging != "Screen") { sCur += (sT - sCur) * k; if (abs(sCur - sT) < 1e-4) sCur = sT }
        flicker(flame, 0f, 0.26f)
        if (ghost.visible) flicker(ghostFlame, 1.3f, 0.10f)
        val key = doubleArrayOf(uCur, sCur, fIdx.toDouble(), if (showRays) 1.0 else 0.0, if (showVirtual) 1.0 else 0.0)
        if (!key.contentEquals(laid)) { layout(); laid = key }
        updateReadout(); updateLabels()
    }

    /** Everything that depends on u, s, f: positions, rays, cones, the image on the screen, the ghosts. */
    private fun layout() {
        val f = f; val u = uCur; val s = sCur
        val v = ThinLens.imageDistance(u, f)
        case = ThinLens.classify(u, f)
        val atF = case == Case.AT_F || v.isInfinite() || abs(v) > 1e5
        vCm = if (atF) Double.POSITIVE_INFINITY else v
        val m = if (atF) Double.NaN else ThinLens.magnification(u, v)
        val c = ThinLens.blurDiameter(D_CM, s, vCm)
        blurMm = c * 10
        sharp = !atF && v > 0 && blurMm <= SHARP_MM

        val uD = d(u); val sD = d(s); val fD = d(f); val hD = tv(TIP_CM)
        val vD = if (atF) Float.NaN else d(v)
        candleMount.pos = P3(-uD, 0f, 0f)
        screenMount.pos = P3(sD, 0f, 0f)
        if (lensShown != fIdx) {
            lensShown = fIdx
            lensNode.setMesh(lensMeshes[fIdx])
            // the glint sits on the candle-side surface, at 78 % of the radius
            val rc = ThinLens.biconvexRadius(f, 1.5168) * ST; val a = (LENS_R + LENS_LIP).toDouble()
            val xg = LENS_EDGE / 2 + ThinLens.sag(rc, a) - ThinLens.sag(rc, 0.78 * LENS_R) + 0.0012
            glintNode.pos = P3(-xg.toFloat(), AXIS_Y, 0f)
        }

        // F / 2F marks and the zones on the scale
        val xs = floatArrayOf(-2 * fD, -fD, fD, 2 * fD)
        for (i in 0 until 4) {
            focusDots[i].pos = P3(xs[i], AXIS_Y, 0f)
            // dot on the axis, a faint drop line, the tag just above the rail (out of the rays' way)
            focusTags[i].pos = P3(xs[i], RAIL_TOP + 0.046f, 0.050f)
            seg(focusDrops[i], xs[i], AXIS_Y - 0.010f, 0f, xs[i], RAIL_TOP + 0.066f, 0f, 0.0008f)
        }
        val x0 = d(-50.0); val x1 = d(50.0)
        zoneSpan(zones[0], x0, -2 * fD); zoneSpan(zones[1], -2 * fD, -fD); zoneSpan(zones[2], -fD, 0f)
        zoneSpan(zones[3], fD, 2 * fD); zoneSpan(zones[4], 2 * fD, x1)

        // ---- principal rays ----
        val tx = -uD; val ty = AXIS_Y + hD
        val lines = ThinLens.principalRays(u, TIP_CM, f)
        val real = !atF && v > 0
        val virt = !atF && v < 0
        // the lens-plane line spans the glass and every point where a principal ray crosses the plane
        var pLo = -LENS_R - 0.025f; var pHi = LENS_R + 0.025f
        for (ln in lines) if (ln != null) { val y = tv(ln.y0); if (AXIS_Y + y in (RAIL_TOP + 0.04f)..0.42f) { pLo = min(pLo, y - 0.025f); pHi = max(pHi, y + 0.025f) } }
        seg(lensPlane, 0f, AXIS_Y + pLo, 0f, 0f, min(AXIS_Y + pHi, 0.43f), 0f, 0.0010f)
        for (i in 0 until 3) {
            val ln = lines[i]
            val y0 = ln?.let { tv(it.y0) }
            // construction rays may cross the lens plane outside the glass; drawn while that point is in the volume
            val ok = showRays && ln != null && AXIS_Y + y0!! in (RAIL_TOP + 0.04f)..0.42f
            for (n in rayCore[i] + rayGlow[i]) n.visible = ok
            val spans = ArrayList<FloatArray>()                  // dashed spans (x0, y0, x1, y1), bench-local
            if (ok) {
                val k = ln!!.slope.toFloat() * TX                  // drawn slope: transverse/axial = ×2
                val ly = AXIS_Y + y0!!
                segRay(i, 0, tx, ty, 0f, ly)
                val xe = rayEnd(y0, k, sD)
                segRay(i, 1, 0f, ly, xe, ly + k * xe)
                if (real && abs(xe - sD) < 1e-6f && vD > sD) {
                    val xp = min(vD + 0.05f, X_LIM)
                    if (xp > sD + 0.004f) spans += floatArrayOf(sD, ly + k * sD, xp, ly + k * xp)
                }
                if (virt) {
                    val xb = max(vD, -X_LIM)
                    val xs0 = if (i == 1) tx else 0f                      // the chief ray's extension starts at the tip
                    if (xs0 - xb > 0.004f) spans += floatArrayOf(xs0, ly + k * xs0, xb, ly + k * xb)
                    if (i == 2) spans += floatArrayOf(-fD, AXIS_Y, tx, ty)   // the "from F" construction line
                }
            }
            dashSpans(i, spans)
        }

        // ---- the cone of light through the aperture ----
        // each rim ray stops where it reaches the screen plane — or leaves the screen's silhouette
        // (light that misses the card is not drawn on into the room)
        fun xEnd(yl: Float, zl: Float, sy: Float, sz: Float): Float {
            var xe = sD
            if (sy > 1e-6f) xe = min(xe, (CARD_HI - yl) / sy) else if (sy < -1e-6f) xe = min(xe, (CARD_LO - yl) / sy)
            if (sz > 1e-6f) xe = min(xe, (CARD_W / 2 - zl) / sz) else if (sz < -1e-6f) xe = min(xe, (-CARD_W / 2 - zl) / sz)
            return max(xe, 0.002f)
        }
        for (k in 0 until N_AP) {
            val a = 2 * PI * k / N_AP
            val yl = (LENS_R * sin(a)).toFloat(); val zl = (LENS_R * cos(a)).toFloat()
            apIn[k].visible = showRays; apOut[k].visible = showRays
            if (!showRays) continue
            seg(apIn[k], tx, ty, 0f, 0f, AXIS_Y + yl, zl, 0.0009f)
            val sy = (yl - hD) / uD - yl / fD; val sz = zl / uD - zl / fD
            val xe = xEnd(yl, zl, sy, sz)
            seg(apOut[k], 0f, AXIS_Y + yl, zl, xe, AXIS_Y + yl + sy * xe, zl + sz * xe, 0.0009f)
        }
        coneIn.visible = showRays; coneOut.visible = showRays; lensPlane.visible = showRays && lensPlane.visible
        conePh.visible = showRays && real && vD > sD
        if (showRays) {
            /** Row [row] of a cone mesh: each rim ray at x = [at](its end x). */
            fun ring(md: MeshData, row: Int, at: (Float) -> Float) {
                for (j in 0..NC) {
                    val a = 2 * PI * j / NC
                    val yl = (LENS_R * sin(a)).toFloat(); val zl = (LENS_R * cos(a)).toFloat()
                    val sy = (yl - hD) / uD - yl / fD; val sz = zl / uD - zl / fD
                    val x = at(xEnd(yl, zl, sy, sz))
                    OptGeo.setPos(md, row * (NC + 1) + j, x, AXIS_Y + yl + sy * x, zl + sz * x)
                }
            }
            for (j in 0..NC) {
                val a = 2 * PI * j / NC
                OptGeo.setPos(coneInMd, j, tx, ty, 0f)
                OptGeo.setPos(coneInMd, NC + 1 + j, 0f, AXIS_Y + (LENS_R * sin(a)).toFloat(), (LENS_R * cos(a)).toFloat())
            }
            meshEdited(coneInMd)
            ring(coneOutMd, 0) { 0f }
            ring(coneOutMd, 1) { xe -> if (real && vD > 0f && vD < xe) vD else xe / 2 }    // the apex row when the image is in front of the screen
            ring(coneOutMd, 2) { xe -> xe }
            meshEdited(coneOutMd)
            if (conePh.visible) { ring(conePhMd, 0) { sD }; ring(conePhMd, 1) { min(vD, X_LIM) }; meshEdited(conePhMd) }
        }

        // ---- the picture on the screen: flame scaled by the chief rays (−s/u), blurred by c ----
        updateScreenImage(u, s, c)
        sharpBar.visible = sharp

        // ---- where the image is ----
        // the image point (where the cone pinches): shown while the screen is NOT there
        imagePointShown = showRays && real && vD <= X_LIM && !sharp
        imgPoint.forEach { it.visible = imagePointShown }
        if (imagePointShown) {
            val ip = P3(vD, AXIS_Y + (m * TIP_CM * ST).toFloat(), 0f)
            imgPoint[0].pos = ip; imgPoint[1].pos = ip
        }
        ghost.visible = showVirtual && virt && vD >= -X_LIM && AXIS_Y + m * hD <= 0.43f     // the whole ghost stays in the volume
        if (ghost.visible) { ghost.pos = P3(vD, AXIS_Y, 0f); ghost.uniformScale(m.toFloat()) }
    }

    /** Where a refracted principal ray stops: on the card if it hits it, else just past the screen (inside the volume). */
    private fun rayEnd(y0: Float, k: Float, sD: Float): Float {
        val ys = y0 + k * sD
        if (ys in CARD_LO..CARD_HI) return sD
        var xe = min(X_LIM, sD + 0.06f)
        val lo = RAIL_TOP + 0.01f - AXIS_Y; val hi = 0.40f - AXIS_Y
        if (k > 1e-6f) xe = min(xe, (hi - y0) / k)
        if (k < -1e-6f) xe = min(xe, (lo - y0) / k)
        return max(xe, 0.01f)
    }

    private fun segRay(i: Int, j: Int, ax: Float, ay: Float, bx: Float, by: Float) {
        seg(rayCore[i][j], ax, ay, 0f, bx, by, 0f, 0.0020f)
        seg(rayGlow[i][j], ax, ay, 0f, bx, by, 0f, 0.0050f)
    }

    /** Lay ray i's dash pool along [spans] (period ~28 mm, 60 % duty), hide the rest. */
    private fun dashSpans(i: Int, spans: List<FloatArray>) {
        var used = 0
        val total = spans.sumOf { sp -> hypot(sp[2] - sp[0], sp[3] - sp[1]).toDouble() }.toFloat()
        for ((si, sp) in spans.withIndex()) {
            val len = hypot(sp[2] - sp[0], sp[3] - sp[1])
            val left = N_DASH - used
            if (left <= 0) break
            val want = if (si == spans.size - 1) left else max(1, (N_DASH * len / total).roundToInt())
            val n = min(left, min(want, max(1, (len / 0.028f).roundToInt())))
            for (q in 0 until n) {
                val a = q.toFloat() / n; val b2 = (q + 0.6f) / n
                seg(dashes[i][used + q], sp[0] + (sp[2] - sp[0]) * a, sp[1] + (sp[3] - sp[1]) * a, 0f,
                    sp[0] + (sp[2] - sp[0]) * b2, sp[1] + (sp[3] - sp[1]) * b2, 0f, 0.0015f)
            }
            used += n
        }
        for (q in used until N_DASH) dashes[i][q].visible = false
    }

    private fun hypot(a: Float, b: Float) = sqrt(a * a + b * b)

    /** Unit cylinder (radius 1, height 1 along +Y) stretched between two bench-local points. */
    private fun seg(n: Node, ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, r: Float) {
        val dx = bx - ax; val dy = by - ay; val dz = bz - az
        val l = sqrt(dx * dx + dy * dy + dz * dz)
        if (l < 1e-5f) { n.visible = false; return }
        n.visible = true
        n.pos = P3((ax + bx) / 2, (ay + by) / 2, (az + bz) / 2)
        n.rot = Q.fromTo(0f, 1f, 0f, dx, dy, dz)
        n.scale = P3(r, l, r)
    }

    private fun zoneSpan(n: Node, xa: Float, xb: Float) {
        val a = max(xa, d(LensArt.CM_LO.toDouble() - 50.0)); val b = min(xb, d(LensArt.CM_HI.toDouble() - 50.0))
        n.visible = b - a > 1e-4f
        n.pos = P3((a + b) / 2, n.pos.y, n.pos.z); n.scale = P3(max(b - a, 1e-4f), 1f, 1f)
    }

    /**
     * Two cross-faded quads (neighbouring blur levels), sized and clipped to the card. Geometry is in
     * screen-mount coordinates: the card face is the plane x = 0 of the mount.
     */
    private fun updateScreenImage(u: Double, s: Double, c: Double) {
        val mS = ThinLens.screenScale(u, s)                      // < 0: always upside down on the screen
        val hImg = (abs(mS) * HF_CM * ST).toFloat()              // projected flame height (display, transverse)
        val cD = (c * ST).toFloat()                              // the blur circle lies in the screen plane: transverse
        val r = cD / max(hImg, 1e-6f)
        val B = LensArt.BLUR
        var k0 = B.size - 1; var w1 = 0f
        for (k in 0 until B.size - 1) if (r < B[k + 1]) {
            k0 = k
            val g = { x: Float -> ln(x + 0.03f) }
            w1 = ((g(r) - g(B[k])) / (g(B[k + 1]) - g(B[k]))).coerceIn(0f, 1f)
            break
        }
        // brightness: the same light over a larger picture is dimmer (compressed for display)
        val gl = (25.0 / s).pow(0.5).toFloat().coerceIn(0.6f, 1f)
        val yBase = (mS * BASE_CM * ST).toFloat()
        for (layer in 0..1) {
            val k = if (layer == 0) k0 else min(k0 + 1, B.size - 1)
            val w = if (layer == 0) 1f - w1 else w1
            val rk = if (k == B.size - 1) max(r, B[k]) else B[k]
            val top = yBase + (-LensArt.Y0 + rk / 2) * hImg
            val bot = yBase - (LensArt.Y1 + rk / 2) * hImg
            val hz = (LensArt.XW + rk / 2) * hImg
            val node = imgNode[layer]
            val alpha = ((gl * w) * 32f).roundToInt() / 32f
            val ok = alpha > 0f && !(layer == 1 && k0 == B.size - 1)
            // clip to the card (2 mm margin)
            val cy0 = max(bot, CARD_LO + 0.002f); val cy1 = min(top, CARD_HI - 0.002f)
            val cz0 = max(-hz, -CARD_W / 2 + 0.002f); val cz1 = min(hz, CARD_W / 2 - 0.002f)
            node.visible = ok && cy1 > cy0 && cz1 > cz0
            if (!node.visible) continue
            val md = imgMd[layer]
            fun vAt(y: Float) = (y - bot) / (top - bot)          // top edge = flame base (texture bottom): inverted
            fun uAt(z: Float) = (hz - z) / (2 * hz)              // mirrored too: a 180° rotation
            val ys = floatArrayOf(cy1, cy1, cy0, cy0); val zs = floatArrayOf(cz0, cz1, cz0, cz1)
            for (q in 0 until 4) {
                OptGeo.setPos(md, q, -0.0020f, AXIS_Y + ys[q], zs[q])
                OptGeo.setUv(md, q, uAt(zs[q]), vAt(ys[q]))
            }
            meshEdited(md)
            if (imgLevel[layer] != k || imgAlpha[layer] != alpha) {
                imgLevel[layer] = k; imgAlpha[layer] = alpha
                node.setMat(Mat.Unlit(Rgb(1f, 1f, 1f), alpha = alpha, tex = blurTex[k], blend = Blend.ADD))
            }
        }
    }

    // ---------------------------------------------------------------- text

    private fun caseLine(): String = when (case) {
        Case.BEYOND_2F -> tr("u > 2f：倒立、缩小的实像，f < v < 2f —— 照相机", "u > 2f: inverted, smaller, real image; f < v < 2f — camera")
        Case.AT_2F -> tr("u = 2f：倒立、等大的实像，v = 2f —— 可用来测焦距", "u = 2f: inverted, same-size, real image; v = 2f — a way to measure f")
        Case.BETWEEN -> tr("f < u < 2f：倒立、放大的实像，v > 2f —— 投影仪", "f < u < 2f: inverted, magnified, real image; v > 2f — projector")
        Case.AT_F -> tr("u = f：折射光平行射出，不成像 —— 探照灯", "u = f: the light leaves parallel, no image — searchlight")
        Case.INSIDE_F -> tr("u < f：正立、放大的虚像，与物同侧 —— 放大镜", "u < f: upright, magnified, virtual image on the candle's side — magnifier")
    }

    private fun mm(x: Double) = if (x < 9.95) "%.1f".format(x) else "%.0f".format(x)

    private fun updateReadout() {
        val f = F_CM[fIdx]; val u = uCur; val s = sCur; val v = vCm
        val line1 = if (v.isInfinite())
            "u = f = %.1f cm   ·   1/v = 1/f − 1/u = 0   →   v = ∞   ·   m → ∞".format(u)
        else {
            val iu = 1 / u; val iv = 1 / v
            "u = %.1f cm   v = %.1f cm   f = %d cm   ·   1/u + 1/v = %.4f %s %.4f = %.4f = 1/f ✓   ·   m = −v/u = %+.2f".format(
                u, v, f, iu, if (iv >= 0) "+" else "−", abs(iv), iu + iv, -v / u).replace("-", "−")
        }
        val line3 = when {
            v.isInfinite() -> tr("光屏 s = %.1f cm：光斑 ⌀ %s mm ≈ 透镜口径，光屏放在哪里都一样 —— 不成像",
                "Screen s = %.1f cm: a patch ⌀ %s mm ≈ the lens aperture, wherever the screen is — no image").format(s, mm(blurMm))
            v < 0 -> tr("光屏 s = %.1f cm：只有一片光斑 ⌀ %s mm —— 虚像不能用光屏承接，要透过透镜看",
                "Screen s = %.1f cm: just a patch ⌀ %s mm — a virtual image can't be caught; look through the lens").format(s, mm(blurMm))
            sharp -> tr("光屏 s = %.1f cm = v：清晰 ✓   光斑 c = %s mm", "Screen s = %.1f cm = v: sharp ✓   blur c = %s mm").format(s, mm(blurMm))
            v > S_MAX -> tr("像距 v = %.1f cm 超出光具座（光屏最远 %.0f cm）：屏上只有模糊光斑 —— 把蜡烛移远些",
                "v = %.1f cm is past the bench end (screen reaches %.0f cm): only a blur — move the candle back").format(v, S_MAX)
            else -> tr("光屏 s = %.1f cm：模糊，光斑 c = D·|s − v|/v = %s mm —— 向%s移 %.1f cm 就清晰",
                "Screen s = %.1f cm: blurred, c = D·|s − v|/v = %s mm — move it %s by %.1f cm").format(
                s, mm(blurMm), if (s > v) tr("透镜", "toward the lens") else tr("远处", "away from the lens"), abs(s - v))
        }
        readoutText = "$line1\n${caseLine()}\n$line3"
    }

    private fun updateLabels() {
        labels.clear()
        val uD = d(uCur); val sD = d(sCur)
        labels += LabelSpec(tr("蜡烛 u = %.1f cm", "Candle u = %.1f cm").format(uCur), toWorld(-uD, AXIS_Y + tv(TIP_CM) + 0.075f, 0f))
        labels += LabelSpec(tr("凸透镜 f = %d cm", "Convex lens f = %d cm").format(F_CM[fIdx]), toWorld(0f, AXIS_Y + LENS_R + 0.055f, 0f))
        labels += LabelSpec(if (sharp) tr("清晰 ✓  v = %.1f cm", "Sharp ✓  v = %.1f cm").format(vCm)
                            else tr("光屏 s = %.1f cm", "Screen s = %.1f cm").format(sCur), toWorld(sD, AXIS_Y + CARD_HI + 0.055f, 0f))
        labels += LabelSpec(tr("垂直光轴方向放大 ×2，便于观察", "Across the axis ×2 for visibility"), toWorld(-0.80f, RAIL_TOP + 0.055f, 0.04f))
        if (ghost.visible) {
            val m = -vCm / uCur
            labels += LabelSpec(tr("虚像：正立、放大 ×%.1f · 透过透镜看", "Virtual image: upright ×%.1f · seen through the lens").format(m),
                toWorld(d(vCm), AXIS_Y + (m * TIP_CM * ST).toFloat() + 0.055f, 0f))
        }
        if (imagePointShown) {
            val m = -vCm / uCur
            labels += LabelSpec(tr("实像在这里 v = %.1f cm", "Real image forms here, v = %.1f cm").format(vCm),
                toWorld(d(vCm), AXIS_Y + (m * TIP_CM * ST).toFloat() - 0.050f, 0f))
        }
    }

    // ---------------------------------------------------------------- guided track (导学)
    // KEY INSIGHT: the candle's distance u, measured against f and 2f, decides the image: beyond 2f
    // smaller, between f and 2f larger (both real and inverted), inside f no screen catches anything —
    // an upright virtual image (magnifier); at f exactly, no image at all.

    /** Case of the candle where it actually stands (not where it is heading). */
    internal fun caseNow() = ThinLens.classify(uCur, f)
    /** Image size / candle size on the screen side (v / u); NaN with no real image. */
    internal fun magAbs() = if (vCm.isInfinite() || vCm <= 0) Double.NaN else vCm / uCur

    // ---- background card (📖): context for a viewer who has never met lenses
    override val about = About(listOf(
        About.Page(
            Bi("这个实验在做什么？", "What is this experiment?"),
            Bi("一支蜡烛、一片凸透镜、一块光屏。移动蜡烛，看光屏上能不能接到清晰的像，像是放大还是缩小、正立还是倒立。你的眼睛、手机镜头、投影仪、放大镜，都是同一条规律在起作用。", "A candle, a convex lens and a screen. Move the candle and see whether the screen catches a sharp image — bigger or smaller, upright or upside down. Your eye, a phone camera, a projector and a magnifier all run on this one rule."),
        ),
        About.Page(
            Bi("光为什么会拐弯？", "Why does light bend?"),
            Bi("光在玻璃里走得比在空气里慢（约慢三分之一）。凸透镜中间厚、边缘薄，斜着进入的光会向厚的一侧偏折，于是从一点发出的光被重新聚到另一点 —— 这个点就是“像”。平行光聚到的那一点叫焦点，到透镜的距离叫焦距 f。", "Light is slower in glass than in air (by about a third). A convex lens is thick in the middle and thin at the edge, so rays bend toward the thick part, and light leaving one point is gathered again at another point — the image. Parallel light meets at the focal point; its distance from the lens is the focal length f."),
        ),
        About.Page(
            Bi("一条公式管所有情况", "One formula for every case"),
            Bi("物距 u、像距 v、焦距 f 满足 1/u + 1/v = 1/f（开普勒 1611 年给出了成像的道理）。蜡烛在 2f 以外：缩小倒立的实像（照相机）；在 f 和 2f 之间：放大倒立的实像（投影仪）；在 f 以内：光屏接不到，透过透镜看到放大正立的虚像（放大镜）。", "Object distance u, image distance v and focal length f obey 1/u + 1/v = 1/f (Kepler explained lens imaging in 1611). Beyond 2f: a smaller, inverted real image (a camera). Between f and 2f: a larger, inverted real image (a projector). Inside f: no image on the screen — look through the lens for an enlarged upright virtual image (a magnifier)."),
        ),
        About.Page(
            Bi("今天它在哪里？", "Where is it today?"),
            Bi("你眼睛里的晶状体就是一片会变焦的凸透镜：看近处时肌肉把它挤得更厚、焦距更短。近视是像落在视网膜前面，所以戴凹透镜把光稍微“推开”。头显里的镜片、自动对焦的手机镜头，做的都是同一件事：让 1/u + 1/v = 1/f 在你需要的地方成立。", "The lens in your eye is a convex lens that refocuses: for near objects muscles squeeze it thicker, shortening f. Short sight puts the image in front of the retina, so a concave lens spreads the light a little. The lenses in a headset and an autofocus phone camera do the same job: make 1/u + 1/v = 1/f hold where you need it."),
        ),
    ))

    override val lesson: Lesson by lazy {
        Lesson(
            listOf(
                LessonStep(
                    say = Bi("蜡烛在 f 和 2f 之间，屏上是倒立、放大的清晰像。把蜡烛移到 2f 以外，像会？", "The candle is between f and 2f: a sharp, inverted, enlarged image. Move the candle beyond 2f — the image will…"),
                    options = listOf(
                        Bi("变得更大", "Grow larger"),
                        Bi("倒立、缩小", "Inverted, smaller"),
                        Bi("变成正立", "Turn upright"),
                    ),
                    answer = 1,
                    onEnter = { if (ThinLens.classify(uT, f) != Case.BETWEEN) setCase(Case.BETWEEN) },
                ),
                LessonStep(
                    say = Bi("捏住蜡烛往远离透镜的方向拖，越过 2F 标记，再捏光屏自动对焦（或面板「u > 2f」）。", "Pinch-drag the candle away from the lens past the 2F mark, then pinch the screen to focus (or “u > 2f” below)."),
                    target = "Candle",
                    done = { caseNow() == Case.BEYOND_2F && sharp },
                    autoplay = { setCase(Case.BEYOND_2F) },
                    reveal = true,
                    insight = Bi("倒立、缩小，像落在 f 和 2f 之间：f = 10 cm、u = 30 cm 时 v = 15 cm，像只有一半大 —— 照相机。", "Inverted and smaller: for u > 2f the image lands between f and 2f. f = 10, u = 30 cm → v = 15 cm, half size — a camera."),
                ),
                LessonStep(
                    say = Bi("再把蜡烛推到焦点 F 以内（比 f 还近）。光屏上会出现？", "Now push the candle inside the focal point F (closer than f). What will the screen show?"),
                    options = listOf(
                        Bi("更大的倒立像", "A bigger inverted image"),
                        Bi("怎么移屏都没像", "No image, anywhere"),
                        Bi("很小的正立像", "A tiny upright image"),
                    ),
                    answer = 1,
                ),
                LessonStep(
                    say = Bi("把蜡烛拖到 F 以内（或面板「u < f · 放大镜」），再前后拖光屏找像。", "Drag the candle inside F (or “u < f · magnifier” below), then slide the screen to hunt for an image."),
                    target = "Candle",
                    done = { caseNow() == Case.INSIDE_F },
                    autoplay = { setCase(Case.INSIDE_F) },
                    reveal = true,
                    insight = Bi("屏上接不到：光穿过透镜后散开。逆着光看，蜡烛一侧有正立、放大的虚像（虚线）—— 放大镜。", "Nothing on the screen: the light leaves spreading out. Looking back, an upright, enlarged virtual image (dashed) — a magnifier."),
                ),
                LessonStep(
                    say = Bi("把蜡烛恰好放在 F 上（拖到 F 附近会吸附，或面板「u = f」）。", "Put the candle exactly on F (it snaps there when released nearby, or tap “u = f” below)."),
                    target = "Candle",
                    done = { caseNow() == Case.AT_F },
                    autoplay = { setCase(Case.AT_F) },
                    insight = Bi("光线平行射出，哪里都不成像。F 正是实像与虚像的分界，2F 是放大与缩小的分界。", "The light leaves parallel: no image anywhere. F divides real from virtual; 2F divides larger from smaller."),
                ),
            ),
            challenge = Challenge(
                say = Bi("换成 f = 15 cm 的透镜，在光屏上得到和蜡烛一样大的清晰像。", "Switch to the f = 15 cm lens and get a sharp image on the screen exactly as tall as the candle."),
                progress = {
                    val m = magAbs()
                    tr("f = %d cm · 像是蜡烛的 %s 倍 · %s", "f = %d cm · image %s× the candle · %s").format(
                        F_CM[fIdx], if (m.isNaN()) "—" else "%.2f".format(m), if (sharp) tr("清晰", "sharp") else tr("不清晰", "not sharp"))
                },
                done = { fIdx == 1 && sharp && abs(magAbs() - 1.0) < 0.03 },
                autoplay = { applyF(1); setCase(Case.AT_2F) },
            ),
        )
    }

    // ---------------------------------------------------------------- interaction

    override val tapTargets get() = listOf(
        TapTarget("Candle", "蜡烛 → 下一种情况", "Candle → next case"),
        TapTarget("Lens", "透镜 → 换焦距", "Lens → next focal length"),
        TapTarget("Screen", "光屏 → 自动对焦", "Screen → auto-focus"),
    )

    override fun isTarget(name: String) = name == "Candle" || name == "Lens" || name == "Screen"
    override fun isDragTarget(name: String) = name == "Candle" || name == "Screen"

    override fun onTap(name: String) {
        when (name) {
            "Candle" -> {
                val cur = ThinLens.classify(uT, f)
                setCase(Case.values()[(cur.ordinal + 1) % Case.values().size])
            }
            "Lens" -> applyF((fIdx + 1) % F_CM.size)
            "Screen" -> autoFocus()
        }
    }

    override fun tapMessage(name: String): String? = when (name) {
        "Candle" -> when (ThinLens.classify(uT, f)) {
            Case.BEYOND_2F -> tr("u > 2f：倒立、缩小的实像（照相机）", "u > 2f: inverted, smaller real image (camera)")
            Case.AT_2F -> tr("u = 2f：倒立、等大的实像，v = 2f", "u = 2f: inverted, same-size real image, v = 2f")
            Case.BETWEEN -> tr("f < u < 2f：倒立、放大的实像（投影仪）", "f < u < 2f: inverted, magnified real image (projector)")
            Case.AT_F -> tr("u = f：光线平行射出，不成像", "u = f: the rays leave parallel — no image")
            Case.INSIDE_F -> tr("u < f：正立、放大的虚像（放大镜）", "u < f: upright, magnified virtual image (magnifier)")
        }
        "Lens" -> tr("换透镜 f = %d cm（%s），蜡烛不动 → %s", "Lens f = %d cm (%s), candle kept → %s").format(F_CM[fIdx], when (fIdx) {
            0 -> tr("最凸，焦距最短", "most curved, shortest f"); 1 -> tr("曲率居中", "medium curvature"); else -> tr("最扁，焦距最长", "flattest, longest f")
        }, when (ThinLens.classify(uT, f)) {
            Case.BEYOND_2F -> "u > 2f"; Case.AT_2F -> "u = 2f"; Case.BETWEEN -> "f < u < 2f"; Case.AT_F -> "u = f"; Case.INSIDE_F -> "u < f"
        })
        "Screen" -> {
            val v = ThinLens.imageDistance(uT, f)
            when {
                ThinLens.classify(uT, f) == Case.AT_F || v.isInfinite() -> tr("u = f：不成像，光屏放哪里都是一片光斑", "u = f: no image — just a patch wherever the screen is")
                v < 0 -> tr("虚像：光屏接不到 —— 用眼睛透过透镜看", "Virtual image: no screen can catch it — look through the lens")
                v > S_MAX -> tr("像距 %.1f cm 超出光具座，光屏够不着", "Image at %.1f cm, past the bench end — out of the screen's reach").format(v)
                else -> tr("自动对焦：光屏移到 v = %.1f cm，清晰 ✓", "Auto-focus: screen to v = %.1f cm, sharp ✓").format(v)
            }
        }
        else -> null
    }

    override fun onDragStart(name: String) { dragging = name }

    override fun onDrag(name: String, dx: Float, dy: Float, dz: Float) {
        // hand motion projected on the rail direction (the bench is yawed), x1.2 so a sideways sweep covers it
        val along = (dx * cos(YAW) - dz * sin(YAW)) * 1.2f
        val cm = along / S
        when (name) {
            "Candle" -> { uT = (uT - cm).coerceIn(U_MIN, U_MAX); uCur = uT }
            "Screen" -> { sT = (sT + cm).coerceIn(S_MIN, S_MAX); sCur = sT }
        }
    }

    override fun onDragEnd(name: String) {
        dragging = null
        when (name) {
            // magnetic detents at f and 2f: the special cases can be hit by hand
            "Candle" -> for (m in doubleArrayOf(f, 2 * f)) if (abs(uT - m) < 0.6) uT = m
            // released close to focus: snap into it (blur < 2.5 mm), the "click" of finding the image
            "Screen" -> {
                val v = ThinLens.imageDistance(uT, f)
                if (v > 0 && !v.isInfinite() && ThinLens.blurDiameter(D_CM, sT, v) * 10 < SNAP_MM) sT = v.coerceIn(S_MIN, S_MAX)
            }
        }
    }

    // ---------------------------------------------------------------- actions (panel + taps)

    fun applyF(k: Int) { fIdx = k }

    fun setCase(c: Case) {
        uT = ThinLens.presetU(c, f, U_MAX, V_FIT).coerceIn(U_MIN, U_MAX)
        autoFocus()
    }

    /** Slide the screen to the image if there is a real one on the bench. */
    fun autoFocus() {
        val v = ThinLens.imageDistance(uT, f)
        if (ThinLens.classify(uT, f) != Case.AT_F && v > 0 && !v.isInfinite() && v <= S_MAX) sT = v
    }

    /** Move the screen to [cm] (tests / review). */
    fun placeScreen(cm: Double) { sT = cm.coerceIn(S_MIN, S_MAX) }
    fun placeCandle(cm: Double) { uT = cm.coerceIn(U_MIN, U_MAX) }

    override fun reviewVariants(): Map<String, () -> Unit> = mapOf(
        "camera" to { applyF(0); setCase(Case.BEYOND_2F) },
        "blurred" to { applyF(1); setCase(Case.BETWEEN); placeScreen(sT - 9.0) },
        "virtual" to { applyF(1); setCase(Case.INSIDE_F); placeScreen(30.0) },
        "parallel" to { applyF(2); setCase(Case.AT_F); placeScreen(30.0) },
    )

    @Composable
    override fun Panel() {
        LabPanel(readout) {
            LabRow {
                SegmentControl {
                    F_CM.forEachIndexed { k, fc -> SegmentItem(selected = fIdx == k, onClick = { applyF(k) }, title = { Text("f = %d cm".format(fc)) }) }
                }
                Spacer(Modifier.width(16.dp))
                LabSwitch(tr("光线", "Rays"), showRays) { showRays = it }
                LabSwitch(tr("虚像", "Virtual image"), showVirtual) { showVirtual = it }
                Button(onClick = { autoFocus() }) { Text(tr("自动对焦", "Auto-focus")) }
            }
            LabRow {
                SegmentControl {
                    val cur = ThinLens.classify(uT, f)
                    listOf(Case.BEYOND_2F to tr("u > 2f · 照相机", "u > 2f · camera"), Case.AT_2F to "u = 2f",
                        Case.BETWEEN to tr("f < u < 2f · 投影仪", "f < u < 2f · projector"), Case.AT_F to "u = f",
                        Case.INSIDE_F to tr("u < f · 放大镜", "u < f · magnifier")).forEach { (c, l) ->
                        SegmentItem(selected = cur == c, onClick = { setCase(c) }, title = { Text(l) })
                    }
                }
            }
        }
    }
}
