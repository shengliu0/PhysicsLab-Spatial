// 杨氏双缝干涉 (Young, 1801) — light through two slits makes stripes: light is a wave.
//
// LESSON: two coherent slits; where the two paths differ by a whole number of wavelengths the
// waves add (bright), half a wavelength they cancel (dark). Fringe spacing Δy = λL/d — change the
// colour, the slit separation or the screen distance and watch the stripes respond. Cover one slit
// (gaze + pinch a brass tab) and the stripes vanish — the dark fringes even get BRIGHTER — which is
// the proof that the stripes come from the two waves interfering.
//
// HONEST SCALE. Everything is the real experiment in real units (λ in nm, d = 0.15-0.40 mm,
// a = 0.05 mm, L = 0.5-1.1 m): fringes are 1-5 mm apart, invisible from 2.5 m. So the bench is
// drawn at true scale ALONG the beam and ×20 ACROSS it (labelled on the screen): every angle the
// student sees is 20× the true one, every number in the readout is the true one.
//
// The bench is yawed 35° so both the light fan (in the beam plane) and the screen face (across
// the beam) are visible from the front; the screen stays exactly perpendicular to the beam so L is
// unambiguous. Above, a magnified, not-to-scale ripple-tank view of the slits shows WHY: circular
// waves from two slits, with lanes where crests meet crests and lanes that stay still.
//
// Physics: core/young/DoubleSlit (Fraunhofer + finite source-slit visibility, checked against a
// direct Huygens sum); colours: core/prism/Spectrum (CIE 1931 observer).

package com.bytedance.physicslab.solar.demos.young

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
import com.bytedance.physicslab.solar.core.prism.Spectrum
import com.bytedance.physicslab.solar.core.young.DoubleSlit
import com.bytedance.physicslab.solar.demos.prism.OptGeo
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
import com.bytedance.physicslab.solar.lab.ui.LabSwitch
import com.bytedance.physicslab.solar.render.Image
import com.bytedance.physicslab.solar.render.MeshData
import com.bytedance.physicslab.solar.render.MeshGen
import com.bytedance.physicslab.solar.render.ProcTex
import com.bytedance.physicslab.solar.render.Rgb
import com.bytedance.physicslab.solar.lab.ui.SegmentControl
import com.bytedance.physicslab.solar.lab.ui.SegmentItem
import com.pico.spatial.ui.design.Text
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

object YoungDemo : SpecDemo() {
    override val id = "young"
    override val title = "杨氏双缝干涉"  // i18n-ok (Chinese half of the title; English is DemoRegistry.TITLE_EN, shown via titleOf)
    override val credit = "Young · 1801"

    // ---------------------------------------------------------------- the real experiment (SI)
    private const val SLIT_A = 0.05e-3                 // slit width a
    private val D_MM = doubleArrayOf(0.15, 0.25, 0.40)  // slit separation d: d/a = 3, 5, 8 (missing orders)
    private const val SRC_A0 = 0.08e-3                 // source slit S0 width
    private const val SRC_L0 = 0.20                    // S0 -> double slit (m)
    private const val L_MIN = 0.50f
    private const val L_MAX = 1.10f
    private val PRESETS = floatArrayOf(650f, 532f, 450f)        // tap the lamp: red, green, blue, white
    private val L_PRESETS = floatArrayOf(0.60f, 0.85f, 1.10f)    // tap the screen
    private val SPEEDS = floatArrayOf(0.35f, 0.7f, 1.4f)         // ripple crests per second (schematic)
    // ripple-tank animation speed; a getter so the titles follow the display language
    private val SPEED_LABELS get() = listOf(tr("波动 慢", "Slow"), tr("波动 中", "Medium"), tr("波动 快", "Fast"))

    // ---------------------------------------------------------------- display
    private const val MAG = 20f                 // transverse magnification (y and z), labelled on screen
    private const val H2 = 0.24f                // fringe field half-height on the screen (display m) = ±12 mm real
    private const val W2 = 0.09f                // stripe half-length (slit half-length 4.5 mm × 20)
    private const val NR = 241                  // fringe rows
    private const val X_NEAR = 0.006f           // fan starts just past the slit plate
    private val YAW = Math.toRadians(40.0).toFloat()
    private val B0 = P3(-0.36f, 0.13f, 0.22f)   // world position of the double slit (bench origin)
    private const val RAIL_TOP = -0.335f        // bench-local (world -0.205)
    private const val S0_X = -0.20f
    private const val LAMP_X = -0.40f

    // ---------------------------------------------------------------- state (Compose-observed: taps update the panel)
    var lambdaNm by mutableFloatStateOf(532f); private set
    var white by mutableStateOf(false); private set
    var dIdx by mutableIntStateOf(1); private set
    var lTarget by mutableFloatStateOf(1.0f); private set
    var speedIdx by mutableIntStateOf(1); private set
    var coverTop by mutableStateOf(false); private set
    var coverBot by mutableStateOf(false); private set

    private var lCur = 1.0f
    private var dirty = true
    private var t = 0.0
    private var rippleAcc = 1f
    private val tabY = floatArrayOf(TAB_PARK, -TAB_PARK)

    // ---------------------------------------------------------------- scene refs
    private lateinit var bench: Node
    private lateinit var fanMd: Array<MeshData>
    private lateinit var scrMd: Array<MeshData>
    private lateinit var s0FanMd: MeshData
    private lateinit var s0Fan: Node
    private lateinit var lampBeam: Node
    private lateinit var filter: Node
    private lateinit var flare: Node
    private lateinit var slitGlow: Array<Node>
    private lateinit var flags: Array<Node>
    private lateinit var screen: Node
    private lateinit var bracket: Array<Node>
    private lateinit var inset: Node
    private lateinit var ripMd: Array<MeshData>
    private lateinit var barrier: Array<Node>
    private lateinit var tabs: Array<Node>
    private lateinit var leader: Node
    private lateinit var leaderMd: MeshData

    // ---------------------------------------------------------------- materials (sRGB)
    private val alu = Mat.Pbr(Rgb(0.80f, 0.82f, 0.85f), metallic = 1f, roughness = 0.32f)
    private val darkSteel = Mat.Pbr(Rgb(0.30f, 0.31f, 0.33f), metallic = 1f, roughness = 0.40f)
    private val anodized = Mat.Pbr(Rgb(0.06f, 0.065f, 0.075f), metallic = 0.4f, roughness = 0.45f)
    private val brass = Mat.Pbr(Rgb(0.96f, 0.78f, 0.45f), metallic = 1f, roughness = 0.22f)
    private val card = Mat.Pbr(Rgb(0.06f, 0.062f, 0.068f), metallic = 0f, roughness = 0.9f)
    private val slabTex by lazy {
        ProcTex.bakePlanet(256, 128, Rgb(0.035f, 0.037f, 0.042f), Rgb(0.060f, 0.062f, 0.070f), Rgb(0.10f, 0.102f, 0.11f), 0f, 91)
    }
    private val slab get() = Mat.Pbr(Rgb(1f, 1f, 1f), metallic = 0f, roughness = 0.85f, tex = slabTex)
    // opaque on purpose: a transparent backing sorts against the additive ripples and cuts them
    private val insetGlass = Mat.Pbr(Rgb(0.012f, 0.014f, 0.020f), metallic = 0f, roughness = 0.10f)
    private val tabMat = Mat.Pbr(Rgb(0.96f, 0.70f, 0.32f), metallic = 1f, roughness = 0.25f, emissive = Rgb(0.30f, 0.17f, 0.04f))
    private val tick = Mat.Unlit(Rgb(0.78f, 0.84f, 0.92f), alpha = 0.75f, blend = Blend.TRANSPARENT)
    private val leaderMat = Mat.Unlit(Rgb(0.70f, 0.78f, 0.90f), alpha = 0.45f, blend = Blend.TRANSPARENT)

    // ramp textures: U = intensity, V = profile (see OptGeo)
    private val fanTex: Image by lazy { OptGeo.rampTex(16) { v -> 0.55f + 0.45f * (1f - v) } }
    private val scrTex: Image by lazy { OptGeo.rampTex(16) { v -> ProcTex.smooth01(v / 0.14f) * ProcTex.smooth01((1f - v) / 0.14f) } }
    private val beamTex: Image by lazy { OptGeo.beamTex(2.6f) }
    private val s0Tex: Image by lazy {
        OptGeo.lumTex(16, 64) { u, v -> val s = (2 * v - 1) * 1.6f; DoubleSlit.sinc(PI * s).let { (it * it).toFloat() } * (0.45f + 0.55f * u) }
    }
    // ripple tank: N_CYC cosine cycles across U (never wraps), amplitude down V
    private const val N_CYC = 40
    private const val PH_K = 7f
    private val rippleTex: Image by lazy {
        OptGeo.lumTex(640, 16) { u, v -> 0.095f * (1f + (1f - v) * cos(2 * PI * N_CYC * u).toFloat()) }
    }
    private val RGB = arrayOf(Rgb(1f, 0f, 0f), Rgb(0f, 1f, 0f), Rgb(0f, 0f, 1f))

    // ---------------------------------------------------------------- inset geometry (inset-local)
    private val INSET_POS = P3(-0.50f, 0.315f, 0.16f)
    private const val IX0 = -0.26f
    private const val IX1 = 0.26f
    private const val IY = 0.11f
    private const val XB = -0.16f            // barrier
    private const val TAB_PARK = 0.085f
    private const val NXL = 7
    private const val NXR = 40
    private const val NYI = 26
    private val ripPhase = Array(2) { FloatArray((NXL + NXR) * NYI) }
    private val ripAmp = Array(2) { FloatArray((NXL + NXR) * NYI) }

    private fun dReal() = D_MM[dIdx] * 1e-3
    private fun lamM() = lambdaNm * 1e-9
    private fun lamModel() = 0.026f * (if (white) 550f else lambdaNm) / 550f
    private fun dModel() = (0.06f * D_MM[dIdx] / 0.25).toFloat()

    // ---------------------------------------------------------------- build

    override fun buildSpec(cacheDir: File): Node {
        lCur = lTarget; dirty = true; t = 0.0
        val root = Node("YoungRoot")
        val unitCyl = MeshGen.cylinder(1f, 1f, 24)
        val unitBox = MeshGen.box(1f, 1f, 1f)

        bench = root.add(Node("Bench")).also { it.pos = B0; it.rot = Q.axisAngle(0f, 1f, 0f, YAW) }
        val b = bench
        // optical table strip + rail on two feet
        b.add("Plinth", MeshGen.box(1.80f, 0.030f, 0.40f), slab, P3(0.36f, RAIL_TOP - 0.045f, 0f))
        // edge trim only: a sheet over the whole top would turn the dark breadboard grey
        b.add("PlinthBand", MeshGen.box(1.81f, 0.008f, 0.41f), alu, P3(0.36f, RAIL_TOP - 0.040f, 0f))
        b.add("Rail", MeshGen.box(1.72f, 0.030f, 0.070f), alu, P3(0.36f, RAIL_TOP - 0.015f, 0f))
        b.add("RailGroove", MeshGen.box(1.72f, 0.004f, 0.014f), darkSteel, P3(0.36f, RAIL_TOP + 0.0005f, 0f))

        fun carrier(x: Float, topY: Float, name: String): Node {
            val g = b.add(Node(name)).also { it.pos = P3(x, 0f, 0f) }
            g.add("Carrier", MeshGen.box(0.07f, 0.024f, 0.09f), darkSteel, P3(0f, RAIL_TOP + 0.012f, 0f))
            g.add("Thumb", unitCyl, brass, P3(0f, RAIL_TOP + 0.012f, 0.055f)).also {
                it.scale = P3(0.008f, 0.022f, 0.008f); it.rot = Q.axisAngle(1f, 0f, 0f, (PI / 2).toFloat())
            }
            // post holder (dark tube) + steel post, the standard optical-bench mount
            val base = RAIL_TOP + 0.024f
            g.add("Holder", unitCyl, darkSteel, P3(0f, base + 0.045f, 0f)).also { it.scale = P3(0.016f, 0.09f, 0.016f) }
            g.add("HolderRing", unitCyl, alu, P3(0f, base + 0.091f, 0f)).also { it.scale = P3(0.018f, 0.004f, 0.018f) }
            val h = topY - base - 0.09f
            g.add("Post", unitCyl, alu, P3(0f, base + 0.09f + h / 2, 0f)).also { it.scale = P3(0.0105f, h, 0.0105f) }
            return g
        }

        // ---- lamp + colour filter ----
        carrier(LAMP_X, -0.055f, "LampMount")
        val axisX = Q.axisAngle(0f, 0f, 1f, (-PI / 2).toFloat())   // cylinder +Y -> +X
        b.add("LampBody", MeshGen.cylinder(0.050f, 0.15f, 32), darkSteel, P3(LAMP_X, 0f, 0f)).also { it.rot = axisX }
        for (k in 0..2) b.add("Fin", MeshGen.cylinder(0.060f, 0.006f, 32), alu, P3(LAMP_X - 0.045f + k * 0.022f, 0f, 0f)).also { it.rot = axisX }
        b.add("LampCap", MeshGen.cylinder(0.040f, 0.020f, 32), anodized, P3(LAMP_X - 0.085f, 0f, 0f)).also { it.rot = axisX }
        b.add("LensRing", MeshGen.cylinder(0.042f, 0.012f, 32), brass, P3(LAMP_X + 0.080f, 0f, 0f)).also { it.rot = axisX }
        filter = b.add("Filter", MeshGen.cylinder(0.036f, 0.008f, 32), filterMat(), P3(LAMP_X + 0.088f, 0f, 0f)).also {
            it.rot = axisX; it.tapRadius = 0.06f
        }
        flare = b.add("Flare", MeshGen.sphere(0.030f, 12, 24, MeshGen.SphereUv.EQUIRECT), glowMat(0.35f), P3(LAMP_X + 0.094f, 0f, 0f))
        // condenser cone onto the source slit (two crossed sheets)
        lampBeam = b.add(Node("LampBeam"))
        for (k in 0..1) {
            val md = OptGeo.grid(2, 2)
            val x0 = LAMP_X + 0.094f; val x1 = S0_X
            if (k == 0) {
                OptGeo.setPos(md, 0, x0, 0.030f, 0f); OptGeo.setPos(md, 1, x1, 0.003f, 0f)
                OptGeo.setPos(md, 2, x0, -0.030f, 0f); OptGeo.setPos(md, 3, x1, -0.003f, 0f)
            } else {
                OptGeo.setPos(md, 0, x0, 0f, 0.030f); OptGeo.setPos(md, 1, x1, 0f, W2 * 0.9f)
                OptGeo.setPos(md, 2, x0, 0f, -0.030f); OptGeo.setPos(md, 3, x1, 0f, -W2 * 0.9f)
            }
            OptGeo.setUv(md, 0, 0f, 0f); OptGeo.setUv(md, 1, 1f, 0f); OptGeo.setUv(md, 2, 0f, 1f); OptGeo.setUv(md, 3, 1f, 1f)
            lampBeam.add("Cone$k", md, beamMat(0.55f))
        }

        // ---- source slit S0 ----
        carrier(S0_X, -0.045f, "S0Mount")
        b.add("S0Plate", MeshGen.box(0.006f, 0.090f, 0.22f), anodized, P3(S0_X, 0f, 0f))
        b.add("S0Frame", MeshGen.box(0.010f, 0.010f, 0.23f), alu, P3(S0_X, 0.050f, 0f))
        b.add("S0Glow", OptGeo.quad(2 * W2, 0.006f), slitGlowMat(), P3(S0_X + 0.0035f, 0f, 0f)).also { it.rot = Q.axisAngle(0f, 1f, 0f, (PI / 2).toFloat()) }
        s0FanMd = OptGeo.grid(2, 2)
        s0Fan = b.add("S0Fan", s0FanMd, Mat.Unlit(Rgb(1f, 1f, 1f), alpha = 0.8f, tex = s0Tex, blend = Blend.ADD))

        // ---- double slit ----
        carrier(0f, -0.075f, "DSMount")
        b.add("DSPlate", MeshGen.box(0.006f, 0.150f, 0.22f), anodized, P3(0f, 0f, 0f))
        b.add("DSFrameTop", MeshGen.box(0.012f, 0.012f, 0.23f), alu, P3(0f, 0.081f, 0f))
        b.add("DSFrameBot", MeshGen.box(0.012f, 0.012f, 0.23f), alu, P3(0f, -0.081f, 0f))
        slitGlow = Array(2) { k ->
            b.add("SlitGlow$k", OptGeo.quad(2 * W2, 0.005f), slitGlowMat(), P3(0.0035f, 0f, 0f)).also { it.rot = Q.axisAngle(0f, 1f, 0f, (PI / 2).toFloat()) }
        }
        flags = Array(2) { k ->
            b.add("Flag$k", MeshGen.box(0.004f, 0.068f, 0.21f), brass, P3(-0.006f, if (k == 0) 0.036f else -0.036f, 0f)).also { it.visible = false }
        }

        // ---- interference fan + fringes (three additive RGB layers each) ----
        fanMd = Array(3) { OptGeo.grid(NR, 2) }
        scrMd = Array(3) { OptGeo.grid(NR, 2) }
        for (c in 0..2) {
            b.add("Fan$c", fanMd[c], Mat.Unlit(RGB[c], alpha = 1f, tex = fanTex, blend = Blend.ADD))
        }

        // ---- screen (moves with L) ----
        screen = b.add(Node("ScreenMount"))
        screen.add("Carrier", MeshGen.box(0.07f, 0.024f, 0.09f), darkSteel, P3(0f, RAIL_TOP + 0.012f, 0f))
        val cardBot = -H2 - 0.020f
        screen.add("Holder", unitCyl, darkSteel, P3(0f, (RAIL_TOP + 0.024f + cardBot) / 2, 0f)).also { it.scale = P3(0.016f, cardBot - RAIL_TOP - 0.024f, 0.016f) }
        screen.add("Screen", MeshGen.box(0.010f, 2 * H2 + 0.02f, 0.30f), card, P3(0.005f, 0f, 0f)).also { it.tapRadius = 0.15f }
        for (s in floatArrayOf(-1f, 1f)) {
            screen.add("FrameH", MeshGen.box(0.014f, 0.012f, 0.31f), alu, P3(0.005f, s * (H2 + 0.016f), 0f))
            screen.add("FrameV", MeshGen.box(0.014f, 2 * H2 + 0.044f, 0.012f), alu, P3(0.005f, 0f, s * 0.156f))
        }
        // ruler: 1 mm real = 20 mm on screen, long tick every 5 mm
        val tickMd = MeshGen.box(0.002f, 0.0016f, 1f)
        for (k in -12..12) {
            val long = k % 5 == 0
            val len = if (long) 0.024f else 0.012f
            screen.add("Tick", tickMd, tick, P3(-0.001f, k * 0.020f, 0.146f - len / 2)).also { it.scale = P3(1f, 1f, len) }
        }
        for (c in 0..2) screen.add("Fringe$c", scrMd[c], Mat.Unlit(RGB[c], alpha = 1f, tex = scrTex, blend = Blend.ADD))
        val brMat = Mat.Unlit(Rgb(1f, 1f, 1f), alpha = 0.9f, blend = Blend.TRANSPARENT)
        bracket = arrayOf(
            screen.add("BrV", MeshGen.box(0.002f, 1f, 0.004f), brMat, P3(-0.002f, 0f, 0.118f)),
            screen.add("Br0", MeshGen.box(0.002f, 0.004f, 0.030f), brMat, P3(-0.002f, 0f, 0.118f)),
            screen.add("Br1", MeshGen.box(0.002f, 0.004f, 0.030f), brMat, P3(-0.002f, 0f, 0.118f)),
        )

        buildInset(root)
        update(0f)
        return root
    }

    private fun buildInset(root: Node) {
        inset = root.add(Node("Inset")).also { it.pos = INSET_POS }
        val w = IX1 - IX0 + 0.03f; val h = 2 * IY + 0.03f
        inset.add("InsetGlass", MeshGen.box(w, h, 0.006f), insetGlass, P3(0f, 0f, -0.004f))
        for (s in floatArrayOf(-1f, 1f)) {
            inset.add("InsetH", MeshGen.box(w + 0.012f, 0.006f, 0.010f), alu, P3(0f, s * (h / 2 + 0.003f), -0.003f))
            inset.add("InsetV", MeshGen.box(0.006f, h + 0.012f, 0.010f), alu, P3(s * (w / 2 + 0.003f), 0f, -0.003f))
        }
        // two ripple layers (one per slit), each: plane-wave grid left of the barrier + circular-wave grid right
        ripMd = Array(2) { buildRippleGrid() }
        for (k in 0..1) inset.add("Ripple$k", ripMd[k], Mat.Unlit(Rgb(1f, 1f, 1f), alpha = 1f, tex = rippleTex, blend = Blend.ADD))
        val box = MeshGen.box(1f, 1f, 1f)
        barrier = Array(3) { inset.add("Barrier$it", box, alu, P3(XB, 0f, 0.002f)) }
        tabs = Array(2) { k ->
            inset.add(if (k == 0) "ShutterTop" else "ShutterBot", MeshGen.box(0.030f, 0.026f, 0.014f), tabMat,
                P3(XB - 0.016f, tabY[k], 0.008f)).also { it.tapRadius = 0.05f }
        }
        // leader from the inset down to the real double slit
        leaderMd = MeshGen.tube(floatArrayOf(0f, 0f, 0f, 0f, -0.1f, 0f), 0.0012f, 6, false)
        leader = root.add("Leader", leaderMd, leaderMat)
        val top = toWorld(0f, 0.085f, 0f)
        val a = P3(INSET_POS.x + 0.10f, INSET_POS.y - IY - 0.018f, INSET_POS.z)
        MeshGen.tubeInto(leaderMd, floatArrayOf(a.x, a.y, a.z, top.x, top.y, top.z), 0.0012f, 6, false)
    }

    private fun buildRippleGrid(): MeshData {
        // grid(rows = NYI, cols = NXL + NXR) won't do — two sub-grids side by side share no vertices
        val left = OptGeo.grid(NYI, NXL); val right = OptGeo.grid(NYI, NXR)
        val nL = NYI * NXL
        val pos = FloatArray((nL + NYI * NXR) * 3); val nrm = FloatArray(pos.size); val uv = FloatArray(pos.size / 3 * 2)
        for (i in 0 until pos.size / 3) nrm[i * 3 + 2] = 1f
        val idx = IntArray(left.idx.size + right.idx.size)
        System.arraycopy(left.idx, 0, idx, 0, left.idx.size)
        for (k in right.idx.indices) idx[left.idx.size + k] = right.idx[k] + nL
        for (r in 0 until NYI) {
            val y = IY - 2 * IY * r / (NYI - 1)
            for (c in 0 until NXL) {
                val i = r * NXL + c
                pos[i * 3] = IX0 + (XB - IX0) * c / (NXL - 1); pos[i * 3 + 1] = y
            }
            for (c in 0 until NXR) {
                val i = nL + r * NXR + c
                val f = c / (NXR - 1f)
                pos[i * 3] = XB + (IX1 - XB) * f * f * (3f - 2f * f) * 0.35f + (IX1 - XB) * f * 0.65f; pos[i * 3 + 1] = y
            }
        }
        return MeshData(pos, nrm, uv, idx)
    }

    // ---------------------------------------------------------------- per frame

    override fun update(dt: Float) {
        t += dt * SPEEDS[speedIdx]
        // ease the screen toward its target distance (visibly slides along the rail)
        if (abs(lCur - lTarget) > 1e-4f) {
            lCur += (lTarget - lCur) * (1f - exp(-dt * 6f)).coerceAtLeast(if (dt == 0f) 0f else 0.02f)
            if (abs(lCur - lTarget) < 5e-4f) lCur = lTarget
            dirty = true
        }
        if (dirty) { rebuildOptics(); dirty = false }
        // tabs slide to cover / uncover
        for (k in 0..1) {
            val cov = if (k == 0) coverTop else coverBot
            val target = if (cov) (if (k == 0) 1f else -1f) * dModel() / 2 else (if (k == 0) TAB_PARK else -TAB_PARK)
            tabY[k] += (target - tabY[k]) * if (dt == 0f) 1f else (1f - exp(-dt * 10f))
            tabs[k].pos = P3(XB - 0.016f, tabY[k], 0.008f)
        }
        rippleAcc += dt
        if (rippleAcc >= 1f / 30f || dt == 0f) { rippleAcc = 0f; updateRipples() }
        updateReadout()
        updateLabels()
    }

    /** Everything that depends on λ, d, L or which slits are open. */
    private fun rebuildOptics() {
        val lam = lamM(); val d = dReal(); val l = lCur
        val open1 = !coverTop; val open2 = !coverBot
        val col = sourceLinear()
        val vis = { lm: Double -> DoubleSlit.visibility(SRC_A0, d, lm, SRC_L0) }
        val v0 = vis(lam)
        for (r in 0 until NR) {
            val y = H2 - 2 * H2 * r / (NR - 1)                    // display y on the screen
            val yt = (y / MAG).toDouble()                         // true y (m)
            val s = yt / sqrt(yt * yt + l.toDouble() * l)
            val i = if (white) DoubleSlit.whiteRgb(s, d, SLIT_A, open1, open2, vis)
                    else DoubleSlit.intensity(s, lam, d, SLIT_A, v0, open1, open2).let { v -> DoubleArray(3) { c -> v * col[c] } }
            for (c in 0..2) {
                val ii = i[c].coerceAtLeast(0.0).toFloat()
                val us = OptGeo.rampU(1f - exp(-3.0f * ii))
                val uf = OptGeo.rampU(0.40f * (1f - exp(-2.3f * ii)))
                val a = r * 2
                OptGeo.setPos(fanMd[c], a, X_NEAR, y * X_NEAR / l, 0f); OptGeo.setUv(fanMd[c], a, uf, 0f)
                OptGeo.setPos(fanMd[c], a + 1, l, y, 0f); OptGeo.setUv(fanMd[c], a + 1, uf, 1f)
                OptGeo.setPos(scrMd[c], a, -0.0015f, y, -W2); OptGeo.setUv(scrMd[c], a, us, 0f)
                OptGeo.setPos(scrMd[c], a + 1, -0.0015f, y, W2); OptGeo.setUv(scrMd[c], a + 1, us, 1f)
            }
        }
        for (c in 0..2) { meshEdited(fanMd[c]); meshEdited(scrMd[c]) }
        screen.pos = P3(l, 0f, 0f)

        // source-slit diffraction fan: central lobe half-width M λ L0 / a0 at the double slit
        val lamFan = if (white) 600e-9 else lam
        val hw = (MAG * lamFan * SRC_L0 / SRC_A0 * 1.6).toFloat()
        OptGeo.setPos(s0FanMd, 0, S0_X + 0.004f, 0.002f, 0f); OptGeo.setUv(s0FanMd, 0, 0f, 0f)
        OptGeo.setPos(s0FanMd, 1, -0.004f, hw, 0f); OptGeo.setUv(s0FanMd, 1, 1f, 0f)
        OptGeo.setPos(s0FanMd, 2, S0_X + 0.004f, -0.002f, 0f); OptGeo.setUv(s0FanMd, 2, 0f, 1f)
        OptGeo.setPos(s0FanMd, 3, -0.004f, -hw, 0f); OptGeo.setUv(s0FanMd, 3, 1f, 1f)
        meshEdited(s0FanMd)

        val src = sourceRgb()
        s0Fan.setMat(Mat.Unlit(src, alpha = 0.85f, tex = s0Tex, blend = Blend.ADD))
        for (n in lampBeam.children) n.setMat(beamMat(0.55f))
        filter.setMat(filterMat()); flare.setMat(glowMat(0.35f))
        for (k in 0..1) {
            val open = if (k == 0) open1 else open2
            slitGlow[k].pos = P3(0.0035f, (if (k == 0) 1f else -1f) * (MAG * d / 2).toFloat(), 0f)
            slitGlow[k].visible = open
            slitGlow[k].setMat(slitGlowMat())
            flags[k].visible = !open
        }
        bench.find("S0Glow")?.setMat(slitGlowMat())

        // Δy bracket: zero order to first bright order (formula value), on the screen's near edge
        val dy = (MAG * DoubleSlit.fringeSpacing(if (white) 550e-9 else lam, l.toDouble(), d)).toFloat()
        bracket[0].pos = P3(-0.002f, dy / 2, 0.118f); bracket[0].scale = P3(1f, dy, 1f)
        bracket[1].pos = P3(-0.002f, 0f, 0.118f)
        bracket[2].pos = P3(-0.002f, dy, 0.118f)

        // inset: barrier with two openings at ±d_model/2
        val dm = dModel(); val gap = 0.010f
        val topLo = dm / 2 + gap / 2
        barrier[0].pos = P3(XB, (IY + topLo) / 2, 0.002f); barrier[0].scale = P3(0.008f, IY - topLo, 0.010f)
        barrier[1].pos = P3(XB, 0f, 0.002f); barrier[1].scale = P3(0.008f, (dm - gap).coerceAtLeast(0.002f), 0.010f)
        barrier[2].pos = P3(XB, -(IY + topLo) / 2, 0.002f); barrier[2].scale = P3(0.008f, IY - topLo, 0.010f)
        rebuildRippleStatics()
        val rc = sourceRgb()
        for (k in 0..1) inset.find("Ripple$k")?.setMat(Mat.Unlit(rc, alpha = 1f, tex = rippleTex, blend = Blend.ADD))
    }

    /** Per-vertex phase (in wavelengths) and amplitude for the two ripple layers. */
    private fun rebuildRippleStatics() {
        val lm = lamModel(); val dm = dModel()
        val md = ripMd[0]
        val nL = NYI * NXL
        for (k in 0..1) {
            val sy = if (k == 0) dm / 2 else -dm / 2
            val open = if (k == 0) !coverTop else !coverBot
            for (i in 0 until md.vertexCount) {
                val x = md.pos[i * 3]; val y = md.pos[i * 3 + 1]
                if (i < nL) { ripPhase[k][i] = (x - XB) / lm; ripAmp[k][i] = 1f }
                else {
                    val r = sqrt((x - XB) * (x - XB) + (y - sy) * (y - sy))
                    ripPhase[k][i] = r / lm
                    // 2-D circular wave: amplitude ∝ 1/sqrt(r) (finite at the slit opening)
                    ripAmp[k][i] = if (open) sqrt(0.035f / (r + 0.035f)) else 0f
                }
            }
        }
    }

    private fun updateRipples() {
        val f = (t - floor(t)).toFloat()            // seamless: shifting U by one whole cycle = same image
        for (k in 0..1) {
            val md = ripMd[k]
            for (i in 0 until md.vertexCount) {
                val u = ((ripPhase[k][i] + PH_K + 1f - f) / N_CYC).coerceIn(0.001f, 0.999f)
                OptGeo.setUv(md, i, u, (1f - ripAmp[k][i]).coerceIn(0.02f, 0.98f))
            }
            meshEdited(md)
        }
    }

    // ---------------------------------------------------------------- colour of the light

    /** Linear RGB of the source (max channel 1). */
    private fun sourceLinear(): DoubleArray = if (white) doubleArrayOf(1.0, 1.0, 1.0) else Spectrum.display(lambdaNm.toDouble())

    private fun sourceRgb(): Rgb { val c = sourceLinear(); return Rgb(OptGeo.enc(c[0].toFloat()), OptGeo.enc(c[1].toFloat()), OptGeo.enc(c[2].toFloat())) }

    private fun filterMat(): Mat {
        val c = sourceRgb()
        return Mat.Pbr(c, metallic = 0f, roughness = 0.08f, emissive = Rgb(c.r * 0.85f, c.g * 0.85f, c.b * 0.85f))
    }

    private fun glowMat(a: Float) = Mat.Unlit(sourceRgb(), alpha = a, blend = Blend.ADD)
    private fun beamMat(a: Float) = Mat.Unlit(sourceRgb(), alpha = a, tex = beamTex, blend = Blend.ADD)
    private fun slitGlowMat() = Mat.Unlit(sourceRgb(), alpha = 0.95f, tex = beamTex, blend = Blend.ADD)

    // ---------------------------------------------------------------- text

    private fun toWorld(x: Float, y: Float, z: Float): P3 {
        val c = cos(YAW); val s = sin(YAW)
        return P3(B0.x + x * c + z * s, B0.y + y, B0.z - x * s + z * c)
    }

    private fun lambdaText() = if (white) tr("白光", "white light") else "%.0f nm".format(lambdaNm)

    private fun updateReadout() {
        val d = dReal(); val l = lCur.toDouble()
        val dy = DoubleSlit.fringeSpacing(if (white) 550e-9 else lamM(), l, d) * 1e3
        val line1 = tr("两缝的光波叠加：路程差 = mλ 处加强成亮纹，(m+½)λ 处抵消成暗纹",
            "Waves from both slits superpose: path difference mλ → bright fringe, (m+½)λ → dark")
        val line2 = if (white) tr("白光 · d = %.2f mm · L = %.2f m → 中央白纹，各级内蓝外红，约 4 级后混成白色",
                        "White · d = %.2f mm · L = %.2f m → white centre, blue-to-red orders, all white beyond ~4").format(D_MM[dIdx], l)
                    else tr("λ = %.0f nm   d = %.2f mm   L = %.2f m   →   条纹间距 Δy = λL/d = %.2f mm",
                        "λ = %.0f nm   d = %.2f mm   L = %.2f m   →   fringe spacing Δy = λL/d = %.2f mm").format(lambdaNm, D_MM[dIdx], l, dy)
        val line3 = when {
            coverTop && coverBot -> tr("两条缝都遮住：屏上一片黑", "Both slits covered: the screen goes dark")
            coverTop || coverBot -> tr("只开一条缝：条纹消失，只剩单缝衍射——原来的暗纹处反而变亮",
                "One slit open: fringes vanish, single-slit diffraction only — old dark fringes brighten")
            else -> {
                val ratio = D_MM[dIdx] / (SLIT_A * 1e3)
                val v = DoubleSlit.visibility(SRC_A0, d, if (white) 550e-9 else lamM(), SRC_L0)
                tr("缝宽 a = 0.05 mm，d/a = %.0f：第 %.0f 级缺级 · 条纹清晰度 V = %.2f · 屏上横向放大 ×20",
                    "Slit width a = 0.05 mm, d/a = %.0f: order %.0f missing · visibility V = %.2f · screen ×20 across").format(ratio, ratio, v)
            }
        }
        readoutText = "$line1\n$line2\n$line3"
    }

    private fun updateLabels() {
        val d = dReal(); val l = lCur
        val dy = DoubleSlit.fringeSpacing(if (white) 550e-9 else lamM(), l.toDouble(), d) * 1e3
        labels.clear()
        labels += LabelSpec(tr("光源 · ${lambdaText()}", "Source · ${lambdaText()}"), toWorld(LAMP_X, 0.085f, 0f))
        labels += LabelSpec(tr("双缝 d = %.2f mm", "Double slit d = %.2f mm").format(D_MM[dIdx]), toWorld(0f, -0.125f, 0.13f))
        labels += LabelSpec(tr("屏 L = %.2f m · 横向放大 ×20", "Screen L = %.2f m · ×20 across beam").format(l), toWorld(l, 0.265f, 0f))
        labels += LabelSpec("Δy = %.2f mm".format(dy), toWorld(l, (MAG * dy * 1e-3 / 2).toFloat(), 0.20f))
        labels += LabelSpec(tr("波前示意（不按比例）· 捏合铜片可遮缝", "Wavefronts (not to scale) · pinch a brass tab to cover a slit"),
            P3(INSET_POS.x, INSET_POS.y + IY + 0.045f, INSET_POS.z))
    }

    // ---------------------------------------------------------------- interaction

    override val tapTargets get() = listOf(
        TapTarget("ShutterTop", "上挡片 → 遮上缝", "Top tab → cover slit") { coverTop }, TapTarget("ShutterBot", "下挡片 → 遮下缝", "Lower tab → cover slit") { coverBot },
        TapTarget("Filter", "滤光片 → 换颜色", "Filter → colour"), TapTarget("Screen", "光屏 → 换距离", "Screen → distance"),
    )

    override fun isTarget(name: String) = name == "ShutterTop" || name == "ShutterBot" || name == "Filter" || name == "Screen"

    override fun tapMessage(name: String): String? = when (name) {
        "ShutterTop", "ShutterBot" -> when {
            coverTop && coverBot -> tr("两条缝都遮住：屏上没有光", "Both slits covered: the screen goes dark")
            coverTop || coverBot -> tr("只剩一条缝：干涉条纹消失，只剩单缝衍射", "One slit left: the fringes vanish, single-slit diffraction remains")
            else -> tr("两缝都打开：干涉条纹回来了", "Both slits open: the fringes are back")
        }
        else -> null
    }

    override fun onTap(name: String) {
        when (name) {
            "ShutterTop" -> applyCover(0, !coverTop)
            "ShutterBot" -> applyCover(1, !coverBot)
            "Filter" -> {
                val k = if (white) -1 else PRESETS.indexOfFirst { abs(it - lambdaNm) < 1f }
                if (k == PRESETS.size - 1) applyWhite(true) else { white = false; applyLambda(PRESETS[(k + 1).coerceAtMost(PRESETS.size - 1)]) }
            }
            "Screen" -> {
                val k = L_PRESETS.indexOfFirst { it > lTarget + 0.01f }
                applyL(if (k < 0) L_PRESETS[0] else L_PRESETS[k])
            }
        }
    }

    fun applyLambda(nm: Float) { lambdaNm = nm.coerceIn(400f, 700f); white = false; dirty = true }
    fun applyWhite(on: Boolean) { white = on; dirty = true }
    fun applyD(i: Int) { dIdx = i; dirty = true }
    fun applyL(m: Float) { lTarget = m.coerceIn(L_MIN, L_MAX) }
    fun applyCover(k: Int, on: Boolean) { if (k == 0) coverTop = on else coverBot = on; dirty = true }

    @Composable
    override fun Panel() {
        LabPanel(readout) {
            LabRow {
                LabSlider(tr("波长 λ", "Wavelength"), lambdaNm, 400f..700f, if (white) tr("白光", "White") else "%.0f nm".format(lambdaNm)) { applyLambda(it) }
                Spacer(Modifier.width(16.dp))
                LabSwitch(tr("白光", "White light"), white) { applyWhite(it) }
            }
            LabRow {
                LabSlider(tr("屏距 L", "Screen L"), lTarget, L_MIN..L_MAX, "%.2f m".format(lTarget)) { applyL(it) }
                Spacer(Modifier.width(16.dp))
                SegmentControl {
                    SPEED_LABELS.forEachIndexed { k, s -> SegmentItem(selected = speedIdx == k, onClick = { speedIdx = k }, title = { Text(s) }) }
                }
            }
            LabRow {
                SegmentControl {
                    D_MM.forEachIndexed { k, v -> SegmentItem(selected = dIdx == k, onClick = { applyD(k) }, title = { Text("d %.2f mm".format(v)) }) }
                }
            }
        }
    }
}
