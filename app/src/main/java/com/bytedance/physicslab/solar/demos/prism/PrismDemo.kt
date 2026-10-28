// 牛顿棱镜色散 (Newton, 1666) — white light is a mixture of colours; glass sorts them.
//
// LESSON: n depends on wavelength (n_violet > n_red), so a prism bends violet more than red and a
// narrow white beam fans out into a continuous spectrum. Swap the glass (crown / flint / dense
// flint) and the spectrum grows 7x; turn the prism and the deviation passes through a MINIMUM.
// Then Newton's experimentum crucis: let one colour through a slot in the screen into a second
// prism — it is bent again, by the amount its colour dictates, but it does NOT split any more.
// The colours were in the white light all along; the prism only separates them.
//
// Everything is real and to scale: Sellmeier indices for SCHOTT N-BK7 / F2 / N-SF11, an exact
// per-wavelength ray trace (Snell at both faces, total internal reflection), a 3 mm beam, and
// CIE 1931 colours. 61 wavelengths x 2 edge rays are traced each time something moves; the fan in
// the air and the band on the screen are the exact overlap of those beams, which is why the
// light stays white near the prism and only separates further out (and why the crown-glass
// spectrum is short and impure — Goethe's coloured edges around a white middle).
//
// Physics: core/prism (Glass, PrismOptics, Ray2, Spectrum). Rendering trick: OptGeo (RGB layers).

package com.bytedance.physicslab.solar.demos.prism

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bytedance.physicslab.solar.core.P3
import com.bytedance.physicslab.solar.core.prism.Glasses
import com.bytedance.physicslab.solar.core.prism.PrismOptics
import com.bytedance.physicslab.solar.core.prism.Ray2
import com.bytedance.physicslab.solar.core.prism.Spectrum
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
import com.bytedance.physicslab.solar.render.Image
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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

object PrismDemo : SpecDemo() {
    override val id = "prism"
    override val title = "牛顿棱镜色散"  // i18n-ok (Chinese half of the title; English is DemoRegistry.TITLE_EN, shown via titleOf)
    override val credit = "Newton · 1666"

    // ---------------------------------------------------------------- the real apparatus (metres, rad)
    private val APEX = Math.toRadians(60.0)          // equilateral prism, as Newton's and every lab's
    private const val SIDE1 = 0.130                  // first prism side (a big demonstration prism)
    private const val DEPTH1 = 0.100f
    private const val SIDE2 = 0.115                  // second prism (experimentum crucis)
    private const val DEPTH2 = 0.085f
    private val ALPHA = Math.toRadians(35.0)         // incoming beam rises at 35 deg (keeps the arm in the volume)
    private val C1 = doubleArrayOf(-0.34, 0.29)      // first prism centroid (world x, y; z = 0)
    private const val SRC_DIST = 0.44                // slit -> prism centroid
    private const val BEAM_W = 0.003                 // beam width in the plane of the rays
    private const val RIB_H = 0.030f                 // slit length (across the plane, z)
    private const val R1_SINGLE = 0.62               // prism exit -> screen 1 (long throw: long spectrum)
    private const val R1_CRUCIS = 0.44               // screen 1 slides in to make room for prism 2
    private const val GAP2 = 0.13                    // screen 1 -> prism 2 centroid
    private const val R2 = 0.30                      // prism 2 exit -> screen 2
    private const val CARD_L = 0.24f                 // screen 1 length along the spectrum
    private val PHI = Math.toRadians(55.0)           // screens swung toward the viewer about the band axis
    private const val SLOT = 0.0025                  // slot in screen 1 (crucis)
    private val SEL_NM = doubleArrayOf(650.0, 600.0, 578.0, 530.0, 470.0, 420.0)
    // display names only (selection is by index); a getter so they follow the display language
    private val SEL_NAMES get() = listOf(tr("红", "red"), tr("橙", "orange"), tr("黄", "yellow"), tr("绿", "green"), tr("蓝", "blue"), tr("紫", "violet"))
    private const val REF_NM = 589.3                 // sodium D: the prism is set for its minimum deviation
    // 400..700 nm every 2.5 nm: at the violet end of dense flint 5 nm samples land 3.7 mm apart on
    // screen 1 — more than the 3 mm beam — and the spectrum broke into stripes
    private const val NL = 121
    private val LAMS = DoubleArray(NL) { 400.0 + 2.5 * it }
    private val W: Array<DoubleArray> = Array(NL) { k -> Spectrum.weights(LAMS[k]) }

    // ---------------------------------------------------------------- state (Compose-observed)
    var glassIdx by mutableIntStateOf(2); private set
    var dIncDeg by mutableFloatStateOf(0f); private set
    var crucis by mutableStateOf(false); private set
    var selIdx by mutableIntStateOf(3); private set

    private var dirty = true
    private var armTh = Double.NaN
    private val armO = doubleArrayOf(0.0, 0.0)
    private var incCur = 0.0                         // eased prism rotation (rad, relative to min dev)
    private var r1 = R1_SINGLE                       // eased screen distance

    // results of the last trace (for readout / labels)
    private val dev = DoubleArray(NL)                // deviation per wavelength (rad), NaN = TIR
    private var devRef = 0.0
    private var i1Cur = 0.0
    private var passNm = DoubleArray(0)
    private var dev2 = 0.0
    private var bandRed = P3(0f, 0f, 0f); private var bandViolet = P3(0f, 0f, 0f)
    private var tirCount = 0

    // ---------------------------------------------------------------- scene
    private lateinit var p1Md: MeshData
    private lateinit var p2Md: MeshData
    private lateinit var p1Edges: Array<Node>
    private lateinit var p2Edges: Array<Node>
    private lateinit var p1Glass: Node
    private lateinit var p2Glass: Node
    private lateinit var p2Group: Array<Node>
    private lateinit var inMd: MeshData; private lateinit var inGlowMd: MeshData; private lateinit var inGlassMd: MeshData
    private lateinit var fanMd: Array<MeshData>
    private lateinit var bandMd: Array<MeshData>
    private lateinit var spotMd: Array<MeshData>
    private lateinit var c2Md: Array<MeshData>         // slot -> P2, inside P2, P2 -> screen 2
    private lateinit var c2GlowMd: Array<MeshData>
    private lateinit var c2Beam: Array<Node>
    private lateinit var arcMd: MeshData
    private lateinit var screen1: Node
    private lateinit var plug: Node
    private lateinit var screen2: Node
    private lateinit var armBar: Node
    private lateinit var arm2Bar: Node
    private lateinit var p1Index: Node
    private lateinit var refMark: Node
    private lateinit var chart: Node
    private lateinit var curves: Array<Node>
    private lateinit var chartDots: Array<Node>

    private const val rArcHi = 0.192
    // n(λ) wall chart (single-prism mode), world placement
    private val CH_O = P3(0.43f, 0.05f, -0.28f)     // lower-left corner of the plot area
    private const val CH_W = 0.38f
    private const val CH_H = 0.30f
    private const val N_LO = 1.49
    private const val N_HI = 1.86
    private const val NZ = 16
    private const val NS = 64
    private const val NB = 140
    private const val NB2 = 48

    // ---------------------------------------------------------------- materials (sRGB)
    private val alu = Mat.Pbr(Rgb(0.80f, 0.82f, 0.85f), metallic = 1f, roughness = 0.32f)
    private val darkSteel = Mat.Pbr(Rgb(0.30f, 0.31f, 0.33f), metallic = 1f, roughness = 0.40f)
    private val anodized = Mat.Pbr(Rgb(0.06f, 0.065f, 0.075f), metallic = 0.4f, roughness = 0.45f)
    private val brass = Mat.Pbr(Rgb(0.96f, 0.78f, 0.45f), metallic = 1f, roughness = 0.22f)
    // a mid-dark matte card: a light one reads grey under the room light and washes the spectrum out
    private val ivory = Mat.Pbr(Rgb(0.20f, 0.20f, 0.19f), metallic = 0f, roughness = 0.92f)
    private val slabTex by lazy {
        ProcTex.bakePlanet(256, 128, Rgb(0.035f, 0.037f, 0.042f), Rgb(0.060f, 0.062f, 0.070f), Rgb(0.10f, 0.102f, 0.11f), 0f, 93)
    }
    private val slab get() = Mat.Pbr(Rgb(1f, 1f, 1f), metallic = 0f, roughness = 0.85f, tex = slabTex)
    private val tickMat = Mat.Unlit(Rgb(0.72f, 0.80f, 0.90f), alpha = 0.55f, blend = Blend.TRANSPARENT)
    private val arcMat = Mat.Unlit(Rgb(1f, 0.92f, 0.70f), alpha = 0.95f, blend = Blend.TRANSPARENT)
    private val dashMat = Mat.Unlit(Rgb(0.85f, 0.88f, 0.95f), alpha = 0.40f, blend = Blend.TRANSPARENT)
    private val RGB = arrayOf(Rgb(1f, 0f, 0f), Rgb(0f, 1f, 0f), Rgb(0f, 0f, 1f))

    /** Glass: faint body colour (dense flints are slightly yellow), see-through, glossy. */
    private fun glassMat(i: Int) = when (i) {
        0 -> Mat.Pbr(Rgb(0.70f, 0.90f, 0.92f), metallic = 0f, roughness = 0.03f, alpha = 0.20f)
        1 -> Mat.Pbr(Rgb(0.80f, 0.86f, 0.84f), metallic = 0f, roughness = 0.03f, alpha = 0.22f)
        else -> Mat.Pbr(Rgb(0.92f, 0.88f, 0.66f), metallic = 0f, roughness = 0.03f, alpha = 0.24f)
    }
    private val edgeMat = Mat.Pbr(Rgb(0.90f, 0.96f, 1.0f), metallic = 0f, roughness = 0.05f, alpha = 0.70f)

    private val fanTex: Image by lazy { OptGeo.rampTex(8) { 1f } }
    private val bandTex: Image by lazy { OptGeo.rampTex(16) { v -> ProcTex.smooth01(v / 0.12f) * ProcTex.smooth01((1f - v) / 0.12f) } }
    private val beamTex: Image by lazy { OptGeo.beamTex(2.4f) }

    private val glass get() = Glasses.ALL[glassIdx]

    // ---------------------------------------------------------------- build

    override fun buildSpec(cacheDir: File): Node {
        dirty = true; armTh = Double.NaN; incCur = Math.toRadians(dIncDeg.toDouble()); r1 = if (crucis) R1_CRUCIS else R1_SINGLE
        val root = Node("PrismRoot")
        val unitCyl = MeshGen.cylinder(1f, 1f, 20)
        val unitBox = MeshGen.box(1f, 1f, 1f)

        // breadboard
        root.add("Plinth", MeshGen.box(1.72f, 0.030f, 0.56f), slab, P3(0f, -0.255f, -0.06f))
        root.add("PlinthTrim", MeshGen.box(1.73f, 0.008f, 0.57f), alu, P3(0f, -0.262f, -0.06f))

        // ---- light source: lamp + slit on a tilted mount, aimed up the beam line ----
        val din = doubleArrayOf(cos(ALPHA), sin(ALPHA))
        val pin = doubleArrayOf(-din[1], din[0])
        val e = 0.82 * SIDE1 / (2 * sqrt(3.0))
        val slit = doubleArrayOf(C1[0] + e * pin[0] - SRC_DIST * din[0], C1[1] + e * pin[1] - SRC_DIST * din[1])
        val lampRot = Q.axisAngle(0f, 0f, 1f, (ALPHA - PI / 2).toFloat())   // cylinder +Y -> beam direction
        val lampC = P3((slit[0] - 0.095 * din[0]).toFloat(), (slit[1] - 0.095 * din[1]).toFloat(), 0f)
        root.add("LampBody", MeshGen.cylinder(0.048f, 0.15f, 32), darkSteel, lampC).also { it.rot = lampRot }
        for (k in 0..2) {
            val o = -0.045 + k * 0.022
            root.add("Fin", MeshGen.cylinder(0.058f, 0.006f, 32), alu,
                P3(lampC.x + (o * din[0]).toFloat(), lampC.y + (o * din[1]).toFloat(), 0f)).also { it.rot = lampRot }
        }
        root.add("LensRing", MeshGen.cylinder(0.040f, 0.012f, 32), brass,
            P3(lampC.x + (0.080 * din[0]).toFloat(), lampC.y + (0.080 * din[1]).toFloat(), 0f)).also { it.rot = lampRot }
        root.add("SlitPlate", MeshGen.box(0.090f, 0.006f, 0.080f), anodized, P3(slit[0].toFloat(), slit[1].toFloat(), 0f)).also { it.rot = lampRot }
        // slit glow: quad X -> across the beam (in plane), Y -> slit length (z), normal -> beam
        root.add("SlitGlow", OptGeo.quad(0.006f, RIB_H), Mat.Unlit(Rgb(1f, 0.98f, 0.92f), alpha = 0.9f, tex = beamTex, blend = Blend.ADD),
            P3((slit[0] + 0.004 * din[0]).toFloat(), (slit[1] + 0.004 * din[1]).toFloat(), 0f)).also {
            it.rot = quatFromBasis(doubleArrayOf(pin[0], pin[1], 0.0), doubleArrayOf(0.0, 0.0, 1.0), doubleArrayOf(din[0], din[1], 0.0))
        }
        val lampBot = lampC.y - 0.05f
        root.add("LampPost", unitCyl, alu, P3(lampC.x, (lampBot - 0.24f) / 2, 0f)).also { it.scale = P3(0.011f, lampBot + 0.24f, 0.011f) }
        root.add("LampHolder", unitCyl, darkSteel, P3(lampC.x, -0.195f, 0f)).also { it.scale = P3(0.017f, 0.09f, 0.017f) }
        root.add("LampFoot", MeshGen.cylinder(0.05f, 0.012f, 32), darkSteel, P3(lampC.x, -0.234f, 0f))

        // ---- incoming beam (white ribbon + glow), inside-glass segment ----
        inGlowMd = OptGeo.grid(2, 2); inMd = OptGeo.grid(2, 2); inGlassMd = OptGeo.grid(2, 2)
        root.add("BeamGlow", inGlowMd, Mat.Unlit(Rgb(1f, 0.97f, 0.90f), alpha = 0.35f, tex = beamTex, blend = Blend.ADD))
        root.add("Beam", inMd, Mat.Unlit(Rgb(1f, 0.99f, 0.96f), alpha = 0.95f, blend = Blend.ADD))
        root.add("BeamInGlass", inGlassMd, Mat.Unlit(Rgb(1f, 0.99f, 0.96f), alpha = 0.80f, blend = Blend.ADD))

        // ---- first prism on a rotation mount ----
        val poly0 = Ray2.prism(C1[0], C1[1], APEX, SIDE1, din[0], din[1], PrismOptics.minDeviationIncidence(glass.n(REF_NM), APEX), true)
        // mesh relative to the centroid: the gaze collider is a sphere about the NODE origin
        p1Md = OptGeo.triPrism(relTo(poly0, C1[0], C1[1]), DEPTH1)
        p1Glass = root.add("Prism", p1Md, glassMat(glassIdx), P3(C1[0].toFloat(), C1[1].toFloat(), 0f)).also { it.tapRadius = 0.075f }
        p1Edges = Array(9) { root.add("P1Edge", unitCyl, edgeMat) }
        val mz = -DEPTH1 / 2 - 0.012f
        root.add("P1Disk", MeshGen.cylinder(0.088f, 0.012f, 48), anodized, P3(C1[0].toFloat(), C1[1].toFloat(), mz)).also { it.rot = Q.axisAngle(1f, 0f, 0f, (PI / 2).toFloat()) }
        root.add("P1DiskRim", MeshGen.cylinder(0.093f, 0.008f, 48), alu, P3(C1[0].toFloat(), C1[1].toFloat(), mz - 0.004f)).also { it.rot = Q.axisAngle(1f, 0f, 0f, (PI / 2).toFloat()) }
        p1Index = root.add("P1Index", unitBox, brass, P3(C1[0].toFloat(), C1[1].toFloat(), mz + 0.007f))
        root.add("P1Post", unitCyl, alu, P3(C1[0].toFloat(), ((C1[1] - 0.085) - 0.15).toFloat() / 2, mz - 0.010f)).also {
            it.scale = P3(0.014f, (C1[1] - 0.085 + 0.15).toFloat(), 0.014f)
        }
        root.add("P1Holder", unitCyl, darkSteel, P3(C1[0].toFloat(), -0.195f, mz - 0.010f)).also { it.scale = P3(0.019f, 0.09f, 0.019f) }
        root.add("P1Foot", MeshGen.cylinder(0.055f, 0.012f, 32), darkSteel, P3(C1[0].toFloat(), -0.234f, mz - 0.010f))

        // ---- deviation protractor around the first prism (behind the rays) ----
        val pz = -0.060f; val rArc = 0.200f
        for (dDeg in 0..80 step 5) {
            val a = ALPHA - Math.toRadians(dDeg.toDouble())
            val r0 = rArc + if (dDeg % 10 == 0) 0.000f else 0.008f; val r1 = rArc + 0.018f
            root.add("Tick", MeshGen.tube(floatArrayOf(
                (C1[0] + r0 * cos(a)).toFloat(), (C1[1] + r0 * sin(a)).toFloat(), pz,
                (C1[0] + r1 * cos(a)).toFloat(), (C1[1] + r1 * sin(a)).toFloat(), pz), 0.0012f, 6, false), tickMat)
        }
        // undeviated direction: the incoming line continued, dashed
        for (k in 0 until 8) {
            val r0 = 0.065 + k * 0.022; val r1 = r0 + 0.012
            root.add("Dash", MeshGen.tube(floatArrayOf(
                (C1[0] + e * pin[0] + r0 * din[0]).toFloat(), (C1[1] + e * pin[1] + r0 * din[1]).toFloat(), pz,
                (C1[0] + e * pin[0] + r1 * din[0]).toFloat(), (C1[1] + e * pin[1] + r1 * din[1]).toFloat(), pz), 0.0011f, 6, false), dashMat)
        }
        arcMd = MeshGen.tube(arcPath(0.0, rArcHi, pz), 0.0026f, 6, false)
        root.add("DevArc", arcMd, arcMat)

        // ---- the spectrum: fan in the air + band on the screen (3 RGB layers each) ----
        fanMd = Array(3) { OptGeo.grid(NZ, NS) }
        bandMd = Array(3) { OptGeo.grid(NB, 2) }
        for (c in 0..2) root.add("Fan$c", fanMd[c], Mat.Unlit(RGB[c], alpha = 1f, tex = fanTex, blend = Blend.ADD))
        for (c in 0..2) root.add("Band$c", bandMd[c], Mat.Unlit(RGB[c], alpha = 1f, tex = bandTex, blend = Blend.ADD))

        // ---- spectrometer arm: screen 1 swings round the prism to catch the spectrum ----
        armBar = root.add("ArmBar", unitBox, alu)
        screen1 = root.add(Node("Screen1Mount"))
        for (s in floatArrayOf(-1f, 1f)) {
            val len = (CARD_L / 2 - SLOT / 2).toFloat()
            screen1.add("Screen1", MeshGen.box(0.12f, len, 0.006f), ivory, P3(0f, s * (SLOT / 2 + len / 2).toFloat(), -0.003f)).also { it.tapRadius = 0.08f }
        }
        plug = screen1.add("Screen1", MeshGen.box(0.12f, (SLOT + 0.001).toFloat(), 0.006f), ivory, P3(0f, 0f, -0.003f))
        for (s in floatArrayOf(-1f, 1f)) {
            screen1.add("S1FrameH", MeshGen.box(0.128f, 0.006f, 0.010f), alu, P3(0f, s * (CARD_L / 2 + 0.003f), -0.004f))
            screen1.add("S1FrameV", MeshGen.box(0.006f, CARD_L + 0.012f, 0.010f), alu, P3(s * 0.063f, 0f, -0.004f))
        }
        screen1.add("S1Boss", MeshGen.box(0.020f, 0.024f, 0.040f), darkSteel, P3(-0.02f, -(CARD_L / 2 - 0.012f), -0.026f))

        // ---- experimentum crucis: second prism + screen 2, riding on the same arm ----
        val p2poly = Ray2.prism(0.0, 0.0, APEX, SIDE2, 1.0, 0.0, 1.0, false)
        p2Md = OptGeo.triPrism(p2poly, DEPTH2)
        p2Glass = root.add("Prism2", p2Md, glassMat(glassIdx))
        p2Edges = Array(9) { root.add("P2Edge", unitCyl, edgeMat) }
        arm2Bar = root.add("Arm2Bar", unitBox, alu)
        screen2 = root.add(Node("Screen2Mount"))
        screen2.add("Screen2", MeshGen.box(0.10f, 0.12f, 0.006f), ivory, P3(0f, 0f, -0.003f))
        for (s in floatArrayOf(-1f, 1f)) {
            screen2.add("S2FrameH", MeshGen.box(0.108f, 0.006f, 0.010f), alu, P3(0f, s * 0.063f, -0.004f))
            screen2.add("S2FrameV", MeshGen.box(0.006f, 0.132f, 0.010f), alu, P3(s * 0.053f, 0f, -0.004f))
        }
        refMark = screen2.add("RefMark", MeshGen.box(0.10f, 0.0015f, 0.002f), Mat.Unlit(Rgb(0.8f, 0.85f, 0.95f), alpha = 0.6f, blend = Blend.TRANSPARENT), P3(0f, 0f, 0.001f))
        spotMd = Array(3) { OptGeo.grid(NB2, 2) }
        val spotNodes = Array(3) { c -> root.add("Spot$c", spotMd[c], Mat.Unlit(RGB[c], alpha = 1f, tex = bandTex, blend = Blend.ADD)) }
        c2Md = Array(3) { OptGeo.grid(2, 2) }
        c2GlowMd = Array(3) { OptGeo.grid(2, 2) }
        c2Beam = Array(6) { k ->
            if (k < 3) root.add("CrucisBeam$k", c2Md[k], Mat.Unlit(Rgb(1f, 1f, 1f), alpha = 0.95f, blend = Blend.ADD))
            else root.add("CrucisGlow$k", c2GlowMd[k - 3], Mat.Unlit(Rgb(1f, 1f, 1f), alpha = 0.45f, tex = beamTex, blend = Blend.ADD))
        }
        p2Group = arrayOf(p2Glass, arm2Bar, screen2, *p2Edges, *spotNodes, *c2Beam)

        buildChart(root)
        update(0f)
        return root
    }

    /** n(λ) for the three glasses: the steeper the curve, the longer the spectrum. */
    private fun buildChart(root: Node) {
        chart = root.add(Node("Chart"))
        val ox = CH_O.x; val oy = CH_O.y; val z = CH_O.z
        chart.add("ChartBoard", MeshGen.box(CH_W + 0.07f, CH_H + 0.09f, 0.008f), Mat.Pbr(Rgb(0.03f, 0.033f, 0.04f), metallic = 0f, roughness = 0.35f),
            P3(ox + CH_W / 2 - 0.005f, oy + CH_H / 2 - 0.02f, z - 0.006f))
        for (sgn in floatArrayOf(-1f, 1f)) {
            chart.add("ChartH", MeshGen.box(CH_W + 0.08f, 0.006f, 0.012f), alu, P3(ox + CH_W / 2 - 0.005f, oy + CH_H / 2 - 0.02f + sgn * (CH_H + 0.09f) / 2, z - 0.005f))
            chart.add("ChartV", MeshGen.box(0.006f, CH_H + 0.096f, 0.012f), alu, P3(ox + CH_W / 2 - 0.005f + sgn * (CH_W + 0.07f) / 2, oy + CH_H / 2 - 0.02f, z - 0.005f))
        }
        // two posts down to the breadboard
        for (dx in floatArrayOf(0.06f, CH_W - 0.07f)) {
            val top = oy - 0.065f; val bot = -0.24f
            chart.add("ChartPost", MeshGen.cylinder(1f, 1f, 16), alu, P3(ox + dx, (top + bot) / 2, z - 0.012f)).also { it.scale = P3(0.008f, top - bot, 0.008f) }
            chart.add("ChartFoot", MeshGen.cylinder(0.035f, 0.010f, 24), darkSteel, P3(ox + dx, -0.235f, z - 0.012f))
        }
        // axes + grid (n every 0.05)
        chart.add("AxisX", MeshGen.tube(floatArrayOf(ox, oy, z, ox + CH_W, oy, z), 0.0012f, 6, false), tickMat)
        chart.add("AxisY", MeshGen.tube(floatArrayOf(ox, oy, z, ox, oy + CH_H, z), 0.0012f, 6, false), tickMat)
        var n = 1.50
        while (n <= N_HI + 1e-9) {
            val y = chartY(n)
            chart.add("Grid", MeshGen.tube(floatArrayOf(ox, y, z, ox + CH_W, y, z), 0.0005f, 4, false), Mat.Unlit(Rgb(0.6f, 0.7f, 0.8f), alpha = 0.18f, blend = Blend.TRANSPARENT))
            n += 0.05
        }
        // the visible spectrum as the λ axis
        val strip = Image(256, 4)
        for (x in 0 until 256) {
            val c = Spectrum.display(400.0 + 300.0 * x / 255)
            val v = ProcTex.argb(1f, OptGeo.enc(c[0].toFloat()), OptGeo.enc(c[1].toFloat()), OptGeo.enc(c[2].toFloat()))
            for (y in 0 until 4) strip.px[y * 256 + x] = v
        }
        chart.add("LambdaStrip", OptGeo.quad(CH_W, 0.014f), Mat.Unlit(Rgb(1f, 1f, 1f), alpha = 0.9f, tex = strip, blend = Blend.ADD), P3(ox + CH_W / 2, oy - 0.014f, z))
        curves = Array(3) { g ->
            val pts = FloatArray(31 * 3) { k ->
                val l = 400.0 + 10.0 * (k / 3)
                when (k % 3) { 0 -> ox + CH_W * ((l - 400) / 300).toFloat(); 1 -> chartY(Glasses.ALL[g].n(l)); else -> z + 0.002f }
            }
            chart.add("Curve$g", MeshGen.tube(pts, 0.0022f, 6, false), curveMat(g == glassIdx))
        }
        // markers on the current glass: red (656) and violet (400) ends
        chartDots = Array(2) { chart.add("ChartDot$it", MeshGen.sphere(0.0065f, 10, 20, MeshGen.SphereUv.EQUIRECT), Mat.Unlit(if (it == 0) Rgb(1f, 0.2f, 0.15f) else Rgb(0.55f, 0.3f, 1f))) }
    }

    private fun chartY(n: Double) = CH_O.y + CH_H * ((n - N_LO) / (N_HI - N_LO)).toFloat()
    private fun curveMat(on: Boolean) = if (on) Mat.Unlit(Rgb(1f, 0.93f, 0.75f), alpha = 1f, blend = Blend.OPAQUE)
                                        else Mat.Unlit(Rgb(0.55f, 0.62f, 0.72f), alpha = 0.45f, blend = Blend.TRANSPARENT)

    private fun updateChart() {
        chart.visible = !crucis
        for (g in 0..2) curves[g].setMat(curveMat(g == glassIdx))
        val gl = glass
        chartDots[0].pos = P3(CH_O.x + CH_W * ((656.3 - 400) / 300).toFloat(), chartY(gl.n(656.3)), CH_O.z + 0.004f)
        chartDots[1].pos = P3(CH_O.x, chartY(gl.n(400.0)), CH_O.z + 0.004f)
    }

    // ---------------------------------------------------------------- per frame

    override fun update(dt: Float) {
        val target = Math.toRadians(dIncDeg.toDouble())
        val k = if (dt == 0f) 1.0 else 1 - exp(-dt * 8.0)
        var moving = false
        if (abs(incCur - target) > 1e-6) {
            incCur += (target - incCur) * k
            if (abs(incCur - target) < 1e-5) {
                incCur = target
                // sound: a soft detent TICK when the turning prism settles exactly at minimum deviation
                if (dt > 0f && target == 0.0) Sfx.play(Sfx.TICK, 0.35f, 1f, C1[0].toFloat() / 0.9f)
            }
            moving = true
        }
        val r1T = if (crucis) R1_CRUCIS else R1_SINGLE
        if (abs(r1 - r1T) > 1e-6) { r1 += (r1T - r1) * k; if (abs(r1 - r1T) < 1e-5) r1 = r1T; moving = true }
        val tir0 = tirCount
        if (dirty || moving || armMoving) { trace(dt); updateChart(); dirty = false }
        // sound: a thin glassy ring as the first colours get trapped by total internal reflection (violet end vanishes)
        if (dt > 0f && tir0 == 0 && tirCount > 0) Sfx.play(Sfx.GLASS, 0.3f, 0.8f, C1[0].toFloat() / 0.9f)
        updateReadout(); updateLabels()
    }

    private var armMoving = false

    /** The whole optical computation + every light mesh. */
    private fun trace(dt: Float) {
        val g = glass
        val din = doubleArrayOf(cos(ALPHA), sin(ALPHA)); val pin = doubleArrayOf(-din[1], din[0])
        val nRef = g.n(REF_NM)
        val iMin = PrismOptics.minDeviationIncidence(nRef, APEX)
        i1Cur = iMin + incCur
        val poly = Ray2.prism(C1[0], C1[1], APEX, SIDE1, din[0], din[1], i1Cur, true)
        OptGeo.triPrismInto(p1Md, relTo(poly, C1[0], C1[1]), DEPTH1); meshEdited(p1Md)
        placeEdges(p1Edges, poly, DEPTH1)
        val rot = atan2(poly[1] - C1[1], poly[0] - C1[0])
        p1Index.pos = P3((C1[0] + 0.078 * cos(rot)).toFloat(), (C1[1] + 0.078 * sin(rot)).toFloat(), -DEPTH1 / 2 - 0.005f)
        p1Index.rot = Q.axisAngle(0f, 0f, 1f, rot.toFloat()); p1Index.scale = P3(0.022f, 0.004f, 0.003f)

        // ---- trace: centre + two edge rays per wavelength ----
        val e = 0.82 * SIDE1 / (2 * sqrt(3.0))
        val sx = C1[0] + e * pin[0] - SRC_DIST * din[0]; val sy = C1[1] + e * pin[1] - SRC_DIST * din[1]
        val ex = Array(NL) { DoubleArray(4) }        // exit points of the two edge rays (x0,y0,x1,y1)
        val dir = Array(NL) { DoubleArray(2) }
        val ok = BooleanArray(NL)
        tirCount = 0
        for (k in 0 until NL) {
            val n = g.n(LAMS[k])
            var good = true
            for (s in 0..1) {
                val o = (if (s == 0) -0.5 else 0.5) * BEAM_W
                val tr = Ray2.traceConvex(poly, n, sx + o * pin[0], sy + o * pin[1], din[0], din[1])
                val out = tr.out
                if (tr.missed || out == null || tr.tir > 0) { good = false; break }
                ex[k][s * 2] = tr.pts[tr.pts.size - 2]; ex[k][s * 2 + 1] = tr.pts[tr.pts.size - 1]
                dir[k][0] = out[0]; dir[k][1] = out[1]
            }
            ok[k] = good
            dev[k] = if (good) -Ray2.angleBetween(din[0], din[1], dir[k][0], dir[k][1]) else Double.NaN
            if (!good) tirCount++
        }
        // reference ray (589 nm) for the incoming / inside-glass beam
        val ref = Array(2) { s ->
            val o = (if (s == 0) -0.5 else 0.5) * BEAM_W
            Ray2.traceConvex(poly, nRef, sx + o * pin[0], sy + o * pin[1], din[0], din[1])
        }
        val refC = Ray2.traceConvex(poly, nRef, sx, sy, din[0], din[1])
        devRef = refC.out?.let { if (refC.tir == 0) -Ray2.angleBetween(din[0], din[1], it[0], it[1]) else Double.NaN } ?: Double.NaN
        updateIncoming(sx, sy, din, pin, ref)

        // ---- arm target: centre of the spectrum, or the selected colour (crucis) ----
        var thT = armTh; var oxT = armO[0]; var oyT = armO[1]
        val good = (0 until NL).filter { ok[it] }
        if (good.isNotEmpty()) {
            if (crucis) {
                val nm = SEL_NM[selIdx]
                val tr = Ray2.traceConvex(poly, g.n(nm), sx, sy, din[0], din[1])
                val out = tr.out
                if (out != null && tr.tir == 0) { thT = atan2(out[1], out[0]); oxT = tr.pts[tr.pts.size - 2]; oyT = tr.pts[tr.pts.size - 1] }
            } else {
                val a0 = atan2(dir[good.first()][1], dir[good.first()][0]); val a1 = atan2(dir[good.last()][1], dir[good.last()][0])
                thT = (a0 + a1) / 2
                val c = refC.out
                if (c != null && refC.tir == 0) { oxT = refC.pts[refC.pts.size - 2]; oyT = refC.pts[refC.pts.size - 1] }
                else { oxT = (ex[good.first()][0] + ex[good.last()][0]) / 2; oyT = (ex[good.first()][1] + ex[good.last()][1]) / 2 }
            }
        }
        if (armTh.isNaN()) { armTh = thT; armO[0] = oxT; armO[1] = oyT }
        val ka = if (dt == 0f) 1.0 else 1 - exp(-dt * 6.0)
        armMoving = abs(thT - armTh) > 2e-5 || abs(oxT - armO[0]) > 2e-6 || abs(oyT - armO[1]) > 2e-6
        armTh += (thT - armTh) * ka; armO[0] += (oxT - armO[0]) * ka; armO[1] += (oyT - armO[1]) * ka
        if (armTh.isNaN()) return

        val u = doubleArrayOf(cos(armTh), sin(armTh)); val p = doubleArrayOf(-u[1], u[0])
        val ox = armO[0]; val oy = armO[1]

        // s(z): transverse position of ray (k, edge) where it crosses distance z along the arm
        fun sAt(k: Int, s: Int, z: Double): Double {
            val px = ex[k][s * 2] - ox; val py = ex[k][s * 2 + 1] - oy
            val du = dir[k][0] * u[0] + dir[k][1] * u[1]
            val t = (z - (px * u[0] + py * u[1])) / du
            return (px + t * dir[k][0]) * p[0] + (py + t * dir[k][1]) * p[1]
        }
        // Continuous in wavelength: between neighbouring samples the beam centre and half-width are
        // interpolated linearly, and the fraction of that 2.5 nm interval whose beam covers s is
        // computed exactly. No gaps however strongly the glass disperses (near grazing exit the
        // violet end stretches > 10 mm per sample).
        val pairs = (0 until NL - 1).filter { ok[it] && ok[it + 1] }.toIntArray()
        val cen = DoubleArray(NL); val hw = DoubleArray(NL)
        var zCached = Double.NaN
        fun light(z: Double, s: Double, out: DoubleArray) {
            if (z != zCached) {
                for (k in good) { val a0 = sAt(k, 0, z); val b0 = sAt(k, 1, z); cen[k] = (a0 + b0) / 2; hw[k] = abs(b0 - a0) / 2 }
                zCached = z
            }
            out[0] = 0.0; out[1] = 0.0; out[2] = 0.0
            for (k in pairs) {
                val f = intervalCover(s, cen[k], cen[k + 1], (hw[k] + hw[k + 1]) / 2)
                if (f > 0) for (ch in 0..2) out[ch] += 0.5 * (W[k][ch] + W[k + 1][ch]) * f
            }
        }

        // ---- fan in the air (from just past the exit face to screen 1) ----
        var z0 = 0.0
        for (k in good) {
            z0 = max(z0, (ex[k][0] - ox) * u[0] + (ex[k][1] - oy) * u[1])
            z0 = max(z0, (ex[k][2] - ox) * u[0] + (ex[k][3] - oy) * u[1])
        }
        z0 += 0.0015
        val zEnd = r1 - 0.002
        val col = DoubleArray(3)
        for (r in 0 until NZ) {
            val f = r / (NZ - 1.0)
            val z = z0 + (zEnd - z0) * f * (0.35 + 0.65 * f)
            var lo = 1e9; var hi = -1e9
            for (k in good) for (s in 0..1) { val v = sAt(k, s, z); lo = min(lo, v); hi = max(hi, v) }
            if (good.isEmpty()) { lo = 0.0; hi = 0.0 }
            lo -= 0.0012; hi += 0.0012
            for (c in 0 until NS) {
                val s = lo + (hi - lo) * c / (NS - 1.0)
                light(z, s, col)
                val i = r * NS + c
                for (ch in 0..2) {
                    OptGeo.setPos(fanMd[ch], i, (ox + z * u[0] + s * p[0]).toFloat(), (oy + z * u[1] + s * p[1]).toFloat(), 0f)
                    OptGeo.setUv(fanMd[ch], i, OptGeo.rampU(tone(col[ch], 5.0) * 0.85f), 0.5f)
                }
            }
        }
        for (ch in 0..2) meshEdited(fanMd[ch])

        // ---- screen 1: swung PHI toward the viewer about the band axis ----
        val q1 = cardRot(armTh)
        val c1 = doubleArrayOf(ox + r1 * u[0], oy + r1 * u[1])
        screen1.pos = P3(c1[0].toFloat(), c1[1].toFloat(), 0f); screen1.rot = q1
        plug.visible = !crucis
        // band rows along s, two columns at the ribbon edges (z0 = -h/2, +h/2)
        var blo = 1e9; var bhi = -1e9
        for (k in good) for (s in 0..1) { val v = sAt(k, s, r1); blo = min(blo, v); bhi = max(bhi, v) }
        if (good.isEmpty()) { blo = -0.01; bhi = 0.01 }
        blo = max(blo - 0.004, -CARD_L / 2 + 0.003); bhi = min(bhi + 0.004, CARD_L / 2 - 0.003)
        val nx = -cos(PHI) * u[0]; val ny = -cos(PHI) * u[1]; val nz = sin(PHI)   // card normal (toward prism + viewer)
        var sRed = 0.0; var sVio = 0.0
        good.firstOrNull()?.let { sVio = (sAt(it, 0, r1) + sAt(it, 1, r1)) / 2 }
        good.lastOrNull()?.let { sRed = (sAt(it, 0, r1) + sAt(it, 1, r1)) / 2 }
        for (r in 0 until NB) {
            val s = blo + (bhi - blo) * r / (NB - 1.0)
            for (j in 0..1) {
                val zz = (if (j == 0) -0.5 else 0.5) * RIB_H
                val dist = r1 + zz * tan(PHI)
                light(dist, s, col)
                if (crucis && abs(s) < SLOT / 2 + 0.0004) col.fill(0.0)
                val i = r * 2 + j
                val wx = ox + dist * u[0] + s * p[0] + 0.0015 * nx; val wy = oy + dist * u[1] + s * p[1] + 0.0015 * ny
                for (ch in 0..2) {
                    OptGeo.setPos(bandMd[ch], i, wx.toFloat(), wy.toFloat(), (zz + 0.0015 * nz).toFloat())
                    OptGeo.setUv(bandMd[ch], i, OptGeo.rampU(tone(col[ch], 9.0)), j.toFloat())
                }
            }
        }
        for (ch in 0..2) meshEdited(bandMd[ch])
        bandRed = P3((c1[0] + sRed * p[0]).toFloat(), (c1[1] + sRed * p[1]).toFloat(), 0f)
        bandViolet = P3((c1[0] + sVio * p[0]).toFloat(), (c1[1] + sVio * p[1]).toFloat(), 0f)

        // arm bar from the prism mount to the screen boss (behind the light); on to prism 2 in crucis mode
        // offset to the violet side, below the light, so it never shows through the additive fan
        val armLen = if (crucis) r1 + GAP2 else r1
        val off = -(CARD_L / 2 - 0.012)
        bar(armBar, C1[0] + off * p[0], C1[1] + off * p[1], ox + armLen * u[0] + off * p[0], oy + armLen * u[1] + off * p[1], -0.050f, 0.012f)
        // deviation arc: 0 .. deviation of the 589 nm line
        MeshGen.tubeInto(arcMd, arcPath(if (devRef.isNaN()) 0.0 else devRef, rArcHi, -0.060f), 0.0026f, 6, false); meshEdited(arcMd)

        // ---- experimentum crucis ----
        for (n in p2Group) n.visible = crucis
        if (crucis) crucisTrace(g, u, p, ox, oy, ex, dir, good, ::sAt)
    }

    private fun crucisTrace(g: com.bytedance.physicslab.solar.core.prism.Glass, u: DoubleArray, p: DoubleArray, ox: Double, oy: Double,
                            ex: Array<DoubleArray>, dir: Array<DoubleArray>, good: List<Int>, sAt: (Int, Int, Double) -> Double) {
        val nRef = g.n(REF_NM)
        val iRef = PrismOptics.minDeviationIncidence(nRef, APEX)
        val rin = SIDE2 / (2 * sqrt(3.0))
        val c2x = ox + (r1 + GAP2) * u[0] + rin * sin(iRef) * p[0]; val c2y = oy + (r1 + GAP2) * u[1] + rin * sin(iRef) * p[1]
        val poly = Ray2.prism(c2x, c2y, APEX, SIDE2, u[0], u[1], iRef, false)
        OptGeo.triPrismInto(p2Md, relTo(poly, c2x, c2y), DEPTH2); meshEdited(p2Md)
        p2Glass.pos = P3(c2x.toFloat(), c2y.toFloat(), 0f)   // node at the centroid (transparent sorting)
        placeEdges(p2Edges, poly, DEPTH2)
        // reference: the arm-axis ray at 589 nm fixes screen 2's frame
        val axS = doubleArrayOf(ox + r1 * u[0], oy + r1 * u[1])
        val refTr = Ray2.traceConvex(poly, nRef, axS[0], axS[1], u[0], u[1])
        val v2 = refTr.out ?: return
        val o2 = doubleArrayOf(refTr.pts[refTr.pts.size - 2], refTr.pts[refTr.pts.size - 1])
        val p2 = doubleArrayOf(-v2[1], v2[0])
        val th2 = atan2(v2[1], v2[0])
        val c2 = doubleArrayOf(o2[0] + R2 * v2[0], o2[1] + R2 * v2[1])
        screen2.pos = P3(c2[0].toFloat(), c2[1].toFloat(), 0f); screen2.rot = cardRot(th2)
        val off2 = -0.055
        bar(arm2Bar, c2x + off2 * p2[0], c2y + off2 * p2[1], c2[0] + off2 * p2[0], c2[1] + off2 * p2[1], -0.045f, 0.010f)

        // which wavelengths get through the slot, and where their beams sit inside it
        data class Pass(val k: Int, val a: Double, val b: Double, val frac: Double)
        val passes = ArrayList<Pass>()
        for (k in good) {
            val sa = sAt(k, 0, r1); val sb = sAt(k, 1, r1)
            val lo = max(min(sa, sb), -SLOT / 2); val hi = min(max(sa, sb), SLOT / 2)
            if (hi > lo) passes += Pass(k, lo, hi, (hi - lo) / abs(sb - sa))
        }
        passNm = DoubleArray(passes.size) { LAMS[passes[it].k] }
        // trace the passed edge rays through prism 2
        val out = passes.mapNotNull { ps ->
            val n = g.n(LAMS[ps.k])
            val rays = listOf(ps.a, ps.b).map { s ->
                Ray2.traceConvex(poly, n, axS[0] + s * p[0], axS[1] + s * p[1], dir[ps.k][0], dir[ps.k][1])
            }
            if (rays.any { it.out == null || it.tir > 0 }) null else Triple(ps, rays[0], rays[1])
        }
        val sel = SEL_NM[selIdx]
        val selTr = Ray2.traceConvex(poly, g.n(sel), axS[0], axS[1], u[0], u[1])
        selTr.out?.let { dev2 = Ray2.angleBetween(u[0], u[1], it[0], it[1]) }

        fun tAt(tr: com.bytedance.physicslab.solar.core.prism.Trace2, dist: Double): Double {
            val px = tr.pts[tr.pts.size - 2] - o2[0]; val py = tr.pts[tr.pts.size - 1] - o2[1]
            val d = tr.out!!
            val t = (dist - (px * v2[0] + py * v2[1])) / (d[0] * v2[0] + d[1] * v2[1])
            return (px + t * d[0]) * p2[0] + (py + t * d[1]) * p2[1]
        }
        var tlo = 1e9; var thi = -1e9
        for ((_, r0, r1) in out) { val a = tAt(r0, R2); val b = tAt(r1, R2); tlo = min(tlo, min(a, b)); thi = max(thi, max(a, b)) }
        if (out.isEmpty()) { tlo = -0.005; thi = 0.005 }
        val mid = (tlo + thi) / 2; val half = max(0.008, (thi - tlo) / 2 + 0.003)
        val col = DoubleArray(3)
        val nx = -cos(PHI) * v2[0]; val ny = -cos(PHI) * v2[1]; val nz = sin(PHI)
        val beamCol = DoubleArray(3)
        // the passed light is a few % of the white beam: expose the spot like a camera would
        // (peak channel -> 0.9), so its COLOUR and POSITION read; the readout gives the physics
        var peak = 1e-9
        for (r in 0 until NB2) for (j in 0..1) {
            val t = mid - half + 2 * half * r / (NB2 - 1.0)
            val dist = R2 + (if (j == 0) -0.5 else 0.5) * RIB_H * tan(PHI)
            col.fill(0.0)
            for ((ps, r0, r1) in out) {
                val a = tAt(r0, dist); val b = tAt(r1, dist)
                val c = cover(t, min(a, b), max(a, b))
                if (c > 0) for (ch in 0..2) col[ch] += W[ps.k][ch] * c
            }
            peak = max(peak, max(col[0], max(col[1], col[2])))
        }
        for (r in 0 until NB2) {
            val t = (mid - half + 2 * half * r / (NB2 - 1.0)).coerceIn(-0.058, 0.058)
            for (j in 0..1) {
                val zz = (if (j == 0) -0.5 else 0.5) * RIB_H
                val dist = R2 + zz * tan(PHI)
                col.fill(0.0)
                for ((ps, r0, r1) in out) {
                    val a = tAt(r0, dist); val b = tAt(r1, dist)
                    val c = cover(t, min(a, b), max(a, b))
                    if (c > 0) for (ch in 0..2) col[ch] += W[ps.k][ch] * c
                }
                val i = r * 2 + j
                val wx = o2[0] + dist * v2[0] + t * p2[0] + 0.0015 * nx; val wy = o2[1] + dist * v2[1] + t * p2[1] + 0.0015 * ny
                for (ch in 0..2) {
                    OptGeo.setPos(spotMd[ch], i, wx.toFloat(), wy.toFloat(), (zz + 0.0015 * nz).toFloat())
                    OptGeo.setUv(spotMd[ch], i, OptGeo.rampU((0.9 * col[ch] / peak).toFloat().coerceIn(0f, 1f)), j.toFloat())
                }
            }
        }
        for (ch in 0..2) meshEdited(spotMd[ch])
        // the passed beam: colour = what got through, normalised for display
        for ((ps, _, _) in out) for (ch in 0..2) beamCol[ch] += W[ps.k][ch] * ps.frac
        val m = max(beamCol[0], max(beamCol[1], beamCol[2])).coerceAtLeast(1e-9)
        val bc = Rgb(OptGeo.enc((beamCol[0] / m).toFloat().coerceAtLeast(0f)), OptGeo.enc((beamCol[1] / m).toFloat().coerceAtLeast(0f)),
            OptGeo.enc((beamCol[2] / m).toFloat().coerceAtLeast(0f)))
        for ((i, b) in c2Beam.withIndex()) {
            b.setMat(if (i < 3) Mat.Unlit(bc, alpha = 0.95f, blend = Blend.ADD) else Mat.Unlit(bc, alpha = 0.45f, tex = beamTex, blend = Blend.ADD))
            b.visible = out.isNotEmpty()
        }
        if (out.isEmpty()) return
        // beams: slot -> P2 entry, inside P2, P2 exit -> screen 2 (using the central passed wavelength)
        val (_, r0, r1) = out[out.size / 2]
        var seg = 0
        fun ribbon(md: MeshData, ax: Double, ay: Double, bx: Double, by: Double, cx: Double, cy: Double, dx: Double, dy: Double) {
            OptGeo.setPos(md, 0, ax.toFloat(), ay.toFloat(), 0f); OptGeo.setPos(md, 1, bx.toFloat(), by.toFloat(), 0f)
            OptGeo.setPos(md, 2, cx.toFloat(), cy.toFloat(), 0f); OptGeo.setPos(md, 3, dx.toFloat(), dy.toFloat(), 0f)
            meshEdited(md)
            // matching soft glow, 12 mm wide, around the centre line of the ribbon
            val g = c2GlowMd[seg++]
            val m0x = (ax + cx) / 2; val m0y = (ay + cy) / 2; val m1x = (bx + dx) / 2; val m1y = (by + dy) / 2
            val lx = m1x - m0x; val ly = m1y - m0y; val ll = sqrt(lx * lx + ly * ly).coerceAtLeast(1e-9)
            val nx = -ly / ll * 0.006; val ny = lx / ll * 0.006
            OptGeo.setPos(g, 0, (m0x + nx).toFloat(), (m0y + ny).toFloat(), 0f); OptGeo.setUv(g, 0, 0f, 0f)
            OptGeo.setPos(g, 1, (m1x + nx).toFloat(), (m1y + ny).toFloat(), 0f); OptGeo.setUv(g, 1, 1f, 0f)
            OptGeo.setPos(g, 2, (m0x - nx).toFloat(), (m0y - ny).toFloat(), 0f); OptGeo.setUv(g, 2, 0f, 1f)
            OptGeo.setPos(g, 3, (m1x - nx).toFloat(), (m1y - ny).toFloat(), 0f); OptGeo.setUv(g, 3, 1f, 1f)
            meshEdited(g)
        }
        val s0 = out[out.size / 2].first
        ribbon(c2Md[0], axS[0] + s0.a * p[0], axS[1] + s0.a * p[1], r0.pts[0], r0.pts[1], axS[0] + s0.b * p[0], axS[1] + s0.b * p[1], r1.pts[0], r1.pts[1])
        ribbon(c2Md[1], r0.pts[0], r0.pts[1], r0.pts[r0.pts.size - 2], r0.pts[r0.pts.size - 1], r1.pts[0], r1.pts[1], r1.pts[r1.pts.size - 2], r1.pts[r1.pts.size - 1])
        val e0 = r0.out!!; val e1 = r1.out!!
        // end both edge rays exactly where they meet screen 2's plane through its band axis (z = 0)
        val l0 = (R2 - ((r0.pts[r0.pts.size - 2] - o2[0]) * v2[0] + (r0.pts[r0.pts.size - 1] - o2[1]) * v2[1])) / (e0[0] * v2[0] + e0[1] * v2[1])
        val l1 = (R2 - ((r1.pts[r1.pts.size - 2] - o2[0]) * v2[0] + (r1.pts[r1.pts.size - 1] - o2[1]) * v2[1])) / (e1[0] * v2[0] + e1[1] * v2[1])
        ribbon(c2Md[2], r0.pts[r0.pts.size - 2], r0.pts[r0.pts.size - 1], r0.pts[r0.pts.size - 2] + l0 * e0[0], r0.pts[r0.pts.size - 1] + l0 * e0[1],
            r1.pts[r1.pts.size - 2], r1.pts[r1.pts.size - 1], r1.pts[r1.pts.size - 2] + l1 * e1[0], r1.pts[r1.pts.size - 1] + l1 * e1[1])
    }

    // ---------------------------------------------------------------- helpers

    private fun tone(i: Double, gain: Double): Float = (1 - exp(-gain * max(0.0, i))).toFloat()

    /**
     * Fraction of t in [0, 1] for which |s - (a + t (b - a))| < h: how much of one wavelength
     * interval, whose beam centre moves linearly from a to b, lights point s.
     */
    private fun intervalCover(s: Double, a: Double, b: Double, h: Double): Double {
        val d = b - a
        if (abs(d) < 1e-9) return cover(s, a - h, a + h)
        var t0 = (s - h - a) / d; var t1 = (s + h - a) / d
        if (t0 > t1) { val t = t0; t0 = t1; t1 = t }
        return (min(t1, 1.0) - max(t0, 0.0)).coerceAtLeast(0.0)
    }

    /** Soft-edged coverage of point s by the beam [lo, hi] (0.2 mm edge ramps). */
    private fun cover(s: Double, lo: Double, hi: Double): Double {
        val eps = 0.0002
        return ((s - lo) / eps + 0.5).coerceIn(0.0, 1.0) * ((hi - s) / eps + 0.5).coerceIn(0.0, 1.0)
    }

    /** Screen orientation: arm direction theta in the ray plane, card swung PHI toward the viewer. */
    private fun cardRot(theta: Double): Q = Q.axisAngle(0f, 0f, 1f, theta.toFloat()) * Q.axisAngle(0f, 1f, 0f, (PHI - PI / 2).toFloat())

    private fun relTo(poly: DoubleArray, cx: Double, cy: Double) = DoubleArray(poly.size) { poly[it] - if (it % 2 == 0) cx else cy }

    /** Rotation whose matrix has columns x, y, z (an orthonormal right-handed basis). */
    private fun quatFromBasis(x: DoubleArray, y: DoubleArray, z: DoubleArray): Q {
        val m00 = x[0]; val m10 = x[1]; val m20 = x[2]; val m01 = y[0]; val m11 = y[1]; val m21 = y[2]
        val m02 = z[0]; val m12 = z[1]; val m22 = z[2]
        val tr = m00 + m11 + m22
        val q = when {
            tr > 0 -> { val s = sqrt(tr + 1.0) * 2; doubleArrayOf((m21 - m12) / s, (m02 - m20) / s, (m10 - m01) / s, 0.25 * s) }
            m00 > m11 && m00 > m22 -> { val s = sqrt(1.0 + m00 - m11 - m22) * 2; doubleArrayOf(0.25 * s, (m01 + m10) / s, (m02 + m20) / s, (m21 - m12) / s) }
            m11 > m22 -> { val s = sqrt(1.0 + m11 - m00 - m22) * 2; doubleArrayOf((m01 + m10) / s, 0.25 * s, (m12 + m21) / s, (m02 - m20) / s) }
            else -> { val s = sqrt(1.0 + m22 - m00 - m11) * 2; doubleArrayOf((m02 + m20) / s, (m12 + m21) / s, 0.25 * s, (m10 - m01) / s) }
        }
        return Q(q[0].toFloat(), q[1].toFloat(), q[2].toFloat(), q[3].toFloat())
    }

    private fun bar(n: Node, x0: Double, y0: Double, x1: Double, y1: Double, z: Float, w: Float) {
        val dx = x1 - x0; val dy = y1 - y0; val l = sqrt(dx * dx + dy * dy)
        n.pos = P3(((x0 + x1) / 2).toFloat(), ((y0 + y1) / 2).toFloat(), z)
        n.rot = Q.axisAngle(0f, 0f, 1f, atan2(dy, dx).toFloat())
        n.scale = P3(l.toFloat(), w, w)
    }

    /** Nine glass-edge rods for a prism of cross-section [poly] extruded over [depth]. */
    private fun placeEdges(edges: Array<Node>, poly: DoubleArray, depth: Float) {
        val r = 0.0016f
        for (i in 0 until 3) {
            val j = (i + 1) % 3
            val ax = poly[i * 2].toFloat(); val ay = poly[i * 2 + 1].toFloat(); val bx = poly[j * 2].toFloat(); val by = poly[j * 2 + 1].toFloat()
            val l = sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay))
            for ((k, z) in listOf(depth / 2, -depth / 2).withIndex()) {
                val n = edges[i * 2 + k]
                n.pos = P3((ax + bx) / 2, (ay + by) / 2, z)
                n.rot = Q.fromTo(0f, 1f, 0f, bx - ax, by - ay, 0f)
                n.scale = P3(r, l, r)
            }
            val n = edges[6 + i]
            n.pos = P3(ax, ay, 0f); n.rot = Q.axisAngle(1f, 0f, 0f, (PI / 2).toFloat()); n.scale = P3(r, depth, r)
        }
    }

    private fun arcPath(dev: Double, r: Double, z: Float): FloatArray {
        val n = 40
        return FloatArray(n * 3) { k ->
            val a = ALPHA - dev * (k / 3) / (n - 1)
            when (k % 3) { 0 -> (C1[0] + r * cos(a)).toFloat(); 1 -> (C1[1] + r * sin(a)).toFloat(); else -> z }
        }
    }

    private fun updateIncoming(sx: Double, sy: Double, din: DoubleArray, pin: DoubleArray, ref: Array<com.bytedance.physicslab.solar.core.prism.Trace2>) {
        val ok = ref.all { !it.missed }
        val ent = if (ok) doubleArrayOf(ref[0].pts[0], ref[0].pts[1], ref[1].pts[0], ref[1].pts[1]) else
            doubleArrayOf(sx + 0.3 * din[0], sy + 0.3 * din[1], sx + 0.3 * din[0], sy + 0.3 * din[1])
        val h = BEAM_W / 2
        fun set(md: MeshData, a0x: Double, a0y: Double, b0x: Double, b0y: Double, a1x: Double, a1y: Double, b1x: Double, b1y: Double) {
            OptGeo.setPos(md, 0, a0x.toFloat(), a0y.toFloat(), 0f); OptGeo.setUv(md, 0, 0f, 0f)
            OptGeo.setPos(md, 1, b0x.toFloat(), b0y.toFloat(), 0f); OptGeo.setUv(md, 1, 1f, 0f)
            OptGeo.setPos(md, 2, a1x.toFloat(), a1y.toFloat(), 0f); OptGeo.setUv(md, 2, 0f, 1f)
            OptGeo.setPos(md, 3, b1x.toFloat(), b1y.toFloat(), 0f); OptGeo.setUv(md, 3, 1f, 1f)
            meshEdited(md)
        }
        set(inMd, sx - h * pin[0], sy - h * pin[1], ent[0], ent[1], sx + h * pin[0], sy + h * pin[1], ent[2], ent[3])
        val gw = 0.011
        val mx = (ent[0] + ent[2]) / 2; val my = (ent[1] + ent[3]) / 2
        set(inGlowMd, sx + gw * pin[0], sy + gw * pin[1], mx + gw * pin[0], my + gw * pin[1], sx - gw * pin[0], sy - gw * pin[1], mx - gw * pin[0], my - gw * pin[1])
        if (ok && ref.all { it.count >= 2 }) {
            val a = ref[0]; val b = ref[1]
            set(inGlassMd, a.pts[0], a.pts[1], a.pts[a.pts.size - 2], a.pts[a.pts.size - 1], b.pts[0], b.pts[1], b.pts[b.pts.size - 2], b.pts[b.pts.size - 1])
        }
    }

    // ---------------------------------------------------------------- text

    private fun deg(r: Double) = Math.toDegrees(r)

    private fun updateReadout() {
        val g = glass
        val dV = dev[0]; val dR = dev[NL - 1]
        val line1 = tr("白光含各色光：玻璃对紫光折射率大、对红光小，所以紫光偏折最多、红光最少",
            "White light is all colours mixed: n(violet) > n(red), so violet is bent most, red least")
        val line2 = tr("%s：n 红 %.3f → 紫 %.3f  ·  D 红 %s  紫 %s  ·  光谱展开 %s",
            "%s: n red %.3f → violet %.3f  ·  D red %s  violet %s  ·  spread %s").format(
            g.id, g.n(656.3), g.n(400.0), fmtDeg(dR), fmtDeg(dV), if (dR.isNaN() || dV.isNaN()) "—" else "%.1f°".format(deg(dV - dR)))
        val dMin = PrismOptics.minDeviation(g.n(REF_NM), APEX)
        val line3 = when {
            crucis -> {
                val nm = SEL_NM[selIdx]
                val impure = passNm.size > 1 && passNm.last() - passNm.first() > 80
                if (impure) tr("判决性实验：%s 色散太小，狭缝漏过 %.0f–%.0f nm，颜色不纯——换火石玻璃",
                    "Experimentum crucis: %s barely disperses, slot passes %.0f–%.0f nm (impure) — use flint").format(g.id, passNm.first(), passNm.last())
                else tr("判决性实验：只让%s光 %.0f nm 通过 → 第二棱镜再偏折 %.1f°，仍是%s光，不再分色",
                    "Experimentum crucis: %s %.0f nm alone → prism 2 bends it %.1f°, still pure %s").format(SEL_NAMES[selIdx], nm, deg(abs(dev2)), SEL_NAMES[selIdx])
            }
            tirCount == NL -> tr("入射角 %.1f°：全部光在出射面全反射，屏上无光——请转回最小偏向",
                "Incidence %.1f°: all colours totally reflected inside — screen dark; return to min deviation").format(deg(i1Cur))
            tirCount > 0 -> tr("入射角 %.1f°：短波光先在出射面全反射，光谱紫端消失",
                "Incidence %.1f°: short wavelengths totally reflect at the exit face first; violet end lost").format(deg(i1Cur))
            abs(incCur) < Math.toRadians(0.3) -> tr("入射角 %.1f°：最小偏向 D = %.2f°（玻璃内光线平行底边），转动棱镜 D 只增不减",
                "Incidence %.1f°: minimum deviation D = %.2f° (ray inside ∥ base); turning only raises D").format(deg(i1Cur), deg(devRef))
            else -> tr("入射角 %.1f°：D(589 nm) = %.2f°，比最小偏向 %.2f° 大 %.2f°",
                "Incidence %.1f°: D(589 nm) = %.2f°, above the minimum %.2f° by %.2f°").format(deg(i1Cur), deg(devRef), deg(dMin), deg(devRef - dMin))
        }
        readoutText = "$line1\n$line2\n$line3"
    }

    private fun fmtDeg(r: Double) = if (r.isNaN()) tr("全反射", "TIR") else "%.1f°".format(deg(r))

    private fun updateLabels() {
        val g = glass
        labels.clear()
        if (!crucis) labels += LabelSpec(tr("白光 + 狭缝", "White light + slit"), P3((C1[0] - 0.40).toFloat(), 0.16f, 0.05f))
        labels += LabelSpec("%s  n_d = %.3f".format(tr(g.label, g.labelEn), g.nd), P3(C1[0].toFloat(), (C1[1] + 0.145).toFloat(), 0.04f))
        if (!devRef.isNaN()) {
            val a = ALPHA - devRef / 2
            labels += LabelSpec(tr("偏向角 D = %.1f°", "Deviation D = %.1f°").format(deg(devRef)), P3((C1[0] + 0.27 * cos(a)).toFloat() + 0.04f, (C1[1] + 0.27 * sin(a)).toFloat(), 0.0f))
        }
        val u = doubleArrayOf(cos(armTh), sin(armTh)); val p = doubleArrayOf(-u[1], u[0])
        if (tirCount < NL) {
            labels += LabelSpec(tr("红", "Red"), P3(bandRed.x + (0.035 * p[0]).toFloat() + (0.05 * u[0]).toFloat(), bandRed.y + (0.035 * p[1]).toFloat(), 0.06f))
            labels += LabelSpec(tr("紫", "Violet"), P3(bandViolet.x - (0.035 * p[0]).toFloat() + (0.05 * u[0]).toFloat(), bandViolet.y - (0.035 * p[1]).toFloat(), 0.06f))
        }
        if (crucis) labels += LabelSpec(tr("第二棱镜：只偏折，不再分色", "2nd prism: bends it, no new colours"), P3(screen2.pos.x, screen2.pos.y + 0.10f, 0.05f))
        else labels += LabelSpec(tr("折射率 n(λ)：曲线越陡，光谱越长", "Index n(λ): steeper curve, longer spectrum"), P3(CH_O.x + CH_W / 2, CH_O.y + CH_H + 0.06f, CH_O.z))
    }

    // ---------------------------------------------------------------- guided track (导学)
    // KEY INSIGHT: white light is a mixture; glass bends violet more than red (n depends on λ), so a
    // prism SORTS the colours — it does not make them: one colour sent through a second prism is bent
    // again by its own amount but never splits (Newton's experimentum crucis).

    /** Crucis is on, settled, and the slot passes one pure colour (not crown glass's 150 nm mix). */
    internal fun crucisPure() = crucis && !dirty && !armMoving && passNm.isNotEmpty() && passNm.last() - passNm.first() <= 80
    /** How far prism 2 bends the selected colour (degrees). */
    internal fun bend2Deg() = deg(abs(dev2))
    private var glassAtStep = 0
    private var selAtStep = 0

    // ---- background card (📖): context for someone who has never asked where colours come from
    override val about = About(listOf(
        About.Page(
            Bi("这个实验在做什么？", "What is this experiment?"),
            Bi("一束 3 毫米宽的白光从狭缝射出，穿过玻璃三棱镜，在光屏上展开成从红到紫的光谱。旁边的曲线是这种玻璃的折射率随波长的变化。捏棱镜可换玻璃（冕牌、火石、重火石）；面板「转动棱镜」改变入射角，偏向角会先变小、再变大；打开「判决性实验」，只让一种颜色穿过屏上的小缝，射进第二个棱镜。", "A 3 mm beam of white light leaves a slit, passes a glass prism and spreads into a red-to-violet spectrum on the screen. The chart beside it shows how this glass's refractive index changes with wavelength. Pinch the prism to change glass (crown, flint, dense flint); “Turn prism” changes the entry angle — the deviation shrinks, then grows again; “Experimentum crucis” lets one colour through a slot in the screen into a second prism."),
        ),
        About.Page(
            Bi("颜色从哪里来？", "Where do colours come from?"),
            Bi("牛顿以前，人们大多认为白光是最纯的光，颜色是棱镜给光“染”上去的。1666 年前后，二十出头的牛顿在暗屋里放进一束阳光，让它穿过棱镜。他又做了“判决性实验”：只取出一种颜色送进第二个棱镜，它只会再拐弯，不会再分色。1672 年他发表结论：白光是各种颜色混合成的，玻璃只是把它们分开。", "Before Newton, most people thought white light was the purest light and that a prism somehow stained it with colour. Around 1666 the twenty-something Newton let a beam of sunlight into a dark room and through a prism. Then came his “crucial experiment”: he sent a single colour into a second prism, and it was bent again but not split. In 1672 he published his conclusion: white light is a mixture of colours, and glass only sorts them."),
        ),
        About.Page(
            Bi("不同颜色，拐弯不同", "Each colour bends differently"),
            Bi("光进入玻璃会变慢、拐弯，而各种颜色变慢的程度不同：紫光最多，拐得最厉害；红光最少。这就像一排人并肩从跑道冲进沙地，有人陷得深、有人陷得浅，队形就散开了。玻璃的折射率随颜色变化越大，光谱就拉得越长：重火石里展开 10.8°，冕牌只有 1.6°。", "Light slows down and bends as it enters glass, and each colour slows by a different amount: violet most, so it bends most; red least. Picture a row of runners crossing from a track onto sand, some sinking deeper than others — the row spreads out. The more the glass's index changes with colour, the longer the spectrum: 10.8° in dense flint, only 1.6° in crown."),
        ),
        About.Page(
            Bi("今天在哪里？", "Where is it today?"),
            Bi("彩虹就是阳光在无数小水滴里折射、分色。天文学家用光谱仪把星光拆开，从光谱里的暗线读出恒星由什么元素组成、是在远去还是靠近。色散也有麻烦：普通透镜让不同颜色焦点不同，画面边缘出彩边；把冕牌和火石两片玻璃粘成一组，就能把它抵消——今天的相机和显微镜镜头里都有这种组合。", "A rainbow is sunlight bent and sorted by countless water drops. Astronomers spread starlight into a spectrum and read its dark lines to learn what a star is made of and whether it is moving away or toward us. Dispersion is also a nuisance: a simple lens focuses each colour at a different spot, leaving coloured fringes. Cementing a crown and a flint lens together cancels it — camera and microscope lenses use such pairs today."),
        ),
    ))

    override val lesson: Lesson by lazy {
        Lesson(
            listOf(
                LessonStep(
                    say = Bi("一束细细的白光射进玻璃棱镜。哪种颜色被玻璃偏折得最多？", "A thin beam of white light enters the glass prism. Which colour does the glass bend the most?"),
                    options = listOf(
                        Bi("红光", "Red"),
                        Bi("紫光", "Violet"),
                        Bi("各色一样多", "All the same"),
                    ),
                    answer = 1,
                    onEnter = { if (crucis) applyCrucis(false); if (tirCount > 0) applyInc(0f) },
                ),
                LessonStep(
                    say = Bi("捏住棱镜（或面板「棱镜」按钮）换一种玻璃，看光屏上红端和紫端怎么变。", "Pinch the prism (or “Prism” below) to change the glass; watch the red and violet ends on the screen."),
                    target = "Prism",
                    onEnter = { glassAtStep = glassIdx },
                    done = { glassIdx != glassAtStep && !dirty },
                    autoplay = { onTap("Prism") },
                    reveal = true,
                    insight = Bi("紫光总是偏折最多：玻璃对紫光折射率大。光谱长度：重火石 10.8°，冕牌只有 1.6°。", "Violet is always bent most: glass has a higher index for violet. Spectrum: 10.8° in dense flint, only 1.6° in crown."),
                ),
                LessonStep(
                    say = Bi("牛顿只让一种颜色穿过屏上的小缝，射进第二个棱镜。出来的光会？", "Newton let just one colour through a slot in the screen into a second prism. What comes out?"),
                    options = listOf(
                        Bi("又分出彩虹", "A new rainbow"),
                        Bi("同一颜色，只偏折", "Same colour, just bent"),
                        Bi("变回白光", "White again"),
                    ),
                    answer = 1,
                ),
                LessonStep(
                    say = Bi("打开面板「判决性实验」（或捏光屏）。冕牌玻璃分不纯，要用火石或重火石。", "Turn on “Experimentum crucis” (or pinch the screen). Use flint or dense flint: crown can't isolate a colour."),
                    target = "Screen1",
                    done = { crucisPure() },
                    autoplay = { if (glassIdx == 0) applyGlass(2); if (!crucis) onTap("Screen1") },
                    reveal = true,
                    insight = Bi("一种颜色进去，同一种颜色出来：第二棱镜只让它再偏折，不再分色。颜色本来就在白光里。", "One colour in, the same colour out: prism 2 bends it again but makes no new colours. They were in the white light."),
                ),
                LessonStep(
                    say = Bi("捏光屏（或面板颜色按钮）换一种颜色送进第二棱镜。红光和紫光谁偏得多？", "Pinch the screen (or a colour button) to send another colour into prism 2. Is red or violet bent more?"),
                    target = "Screen1",
                    onEnter = { selAtStep = selIdx },
                    done = { selIdx != selAtStep && crucis && !dirty && !armMoving },
                    autoplay = { onTap("Screen1") },
                    insight = Bi("每种颜色有自己固定的偏折：重火石里红光 65.3°，紫光 73.3°，和第一个棱镜一样。", "Each colour keeps its own bend: in dense flint red 65.3°, violet 73.3° — same order as in prism 1."),
                ),
            ),
            challenge = Challenge(
                say = Bi("让第二棱镜把选出的光偏折超过 72°。想想：选哪种颜色，用哪种玻璃？", "Make prism 2 bend the selected colour by more than 72°. Think: which colour, which glass?"),
                progress = {
                    when {
                        !crucis -> tr("先打开「判决性实验」", "Turn on “Experimentum crucis” first")
                        !crucisPure() -> tr("颜色不纯 —— 换火石玻璃", "Impure colour — use flint glass")
                        else -> tr("%s光偏折 %.1f°（目标 > 72°）", "%s bent %.1f° (goal > 72°)").format(SEL_NAMES[selIdx], bend2Deg())
                    }
                },
                done = { crucisPure() && bend2Deg() > 72.0 },
                autoplay = { applyGlass(2); applyCrucis(true); applySel(SEL_NM.size - 1) },
            ),
        )
    }

    // ---------------------------------------------------------------- interaction

    override val tapTargets get() = listOf(
        TapTarget("Prism", "棱镜 → 换玻璃", "Prism → next glass"),
        TapTarget("Screen1", "光屏 → 判决实验 / 换色", "Screen → crucis / colour"),
    )

    override fun isTarget(name: String) = name == "Prism" || name == "Screen1"

    override fun onTap(name: String) {
        when (name) {
            "Prism" -> applyGlass((glassIdx + 1) % Glasses.ALL.size)
            "Screen1" -> if (!crucis) applyCrucis(true) else applySel((selIdx + 1) % SEL_NM.size)
        }
    }

    fun applyGlass(i: Int) {
        // sound: a glass prism of a different glass is set on the table
        if (i != glassIdx) Sfx.play(Sfx.GLASS, 0.4f, 1f + 0.1f * i, C1[0].toFloat() / 0.9f)
        glassIdx = i; dirty = true; p1Glass.setMat(glassMat(i)); p2Glass.setMat(glassMat(i))
    }
    fun applyInc(deg: Float) { dIncDeg = deg.coerceIn(-12f, 20f) }
    fun applyCrucis(on: Boolean) { crucis = on; dirty = true }
    fun applySel(i: Int) { selIdx = i; dirty = true }

    @Composable
    override fun Panel() {
        LabPanel(readout) {
            LabRow {
                SegmentControl {
                    listOf(tr("冕牌 BK7", "Crown BK7"), tr("火石 F2", "Flint F2"), tr("重火石 SF11", "Dense flint SF11")).forEachIndexed { k, s ->
                        SegmentItem(selected = glassIdx == k, onClick = { applyGlass(k) }, title = { Text(s) })
                    }
                }
                Spacer(Modifier.width(20.dp))
                LabSwitch(tr("判决性实验", "Experimentum crucis"), crucis) { applyCrucis(it) }
            }
            LabRow {
                LabSlider(tr("转动棱镜", "Turn prism"), dIncDeg, -12f..20f, "%+.0f°".format(dIncDeg)) { applyInc(it) }
                Spacer(Modifier.width(12.dp))
                Button(onClick = { applyInc(0f) }, enabled = dIncDeg != 0f) { Text(tr("最小偏向", "Minimum deviation")) }
            }
            if (crucis) LabRow {
                Text(tr("选出颜色  ", "Select colour  "))
                SegmentControl {
                    // sentence-case names for the buttons (no-op for Chinese)
                    SEL_NAMES.forEachIndexed { k, s -> SegmentItem(selected = selIdx == k, onClick = { applySel(k) }, title = { Text(s.replaceFirstChar { it.uppercaseChar() }) }) }
                }
            }
        }
    }
}
