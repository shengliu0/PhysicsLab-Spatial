// 光电效应 (Einstein, 1905; Lenard 1902, Millikan 1916).
//
// LESSON: light comes in photons of energy h nu. One photon frees at most one electron, which leaves
// with at most E_k = h nu - W. So the COLOUR (frequency) sets how fast the electrons fly, the
// BRIGHTNESS only sets how many — and below the threshold nu0 = W/h nothing comes out, however bright.
//
// WHAT YOU SEE: a big phototube — metal cathode K on the left, mesh anode A — lit by a lamp whose
// wavelength you slide from 700 nm to 200 nm. Photons (beads) march down the beam at the speed of
// light: their number is the intensity, their colour the energy. Electrons leave the lit spot with
// energies from 0 to E_k,max (Lambert directions); their speed is the energy. Turn the anode
// voltage negative and you watch them slow, turn round and fall back — at -U_c = -E_k,max/e the
// last one fails and the current is zero, whatever the intensity. On the right: Millikan's graph
// (E_k,max against nu: three metals, three parallel lines of slope h) and the I-U curve.
//
// Physics: core/photoelectric/PhotoCore (the same electron model drives the dots and the curve).

package com.bytedance.physicslab.solar.demos.photoelectric

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bytedance.physicslab.solar.core.P3
import com.bytedance.physicslab.solar.core.photoelectric.Photo
import com.bytedance.physicslab.solar.core.photoelectric.Photoelectron
import com.bytedance.physicslab.solar.core.photoelectric.PhotoTube
import com.bytedance.physicslab.solar.demos.cyclotron.MeshKit
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
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object PhotoelectricDemo : SpecDemo() {
    override val id = "photoelectric"
    override val title = "光电效应"   // i18n-ok: Chinese title; the English one is DemoRegistry.TITLE_EN (shown via titleOf)
    override val credit = "Einstein · 1905"

    // ---------------------------------------------------------------- controls
    var metalIdx = 1; var lambda = 405f; var intensity = 0.70f; var voltage = 0f; var speedIdx = 1
    private const val P_MAX = 1e-3                                   // 1 mW of light on the cathode at 100 %
    private val K_V = floatArrayOf(0.20f, 0.40f, 0.75f)             // display m/s per sqrt(eV)
    private val PHOTON_V = floatArrayOf(0.25f, 0.5f, 0.9f)
    private val SPEED_LABELS get() = listOf(tr("慢", "Slow"), tr("中", "Medium"), tr("快", "Fast"))
    private val U_MIN = -3f; private val U_MAX = 4f

    // ---------------------------------------------------------------- layout (display metres)
    private const val PLINTH_Y = -0.262f; private const val TOP = -0.2445f
    private const val TY = 0.10f                                     // tube axis height
    private const val KX = -0.56f                                    // cathode face
    private const val D = 0.20f                                      // cathode-anode gap
    private const val AXP = KX + D                                   // anode plane
    private val tube = PhotoTube()                                   // anode radius 1.2 d, spot 0.12 d
    private val RA = (tube.anodeRatio * D).toFloat()                 // 0.24
    private val SPOT = (tube.spotRatio * D).toFloat()
    private const val TR = 0.268f                                    // glass radius
    private const val TX0 = -0.67f; private const val TX1 = -0.12f   // glass ends
    private val LAMP = P3(-0.06f, 0.33f, -0.30f)                    // behind and above: the front stays clear
    private val SPOT_C = P3(KX + 0.002f, TY, 0f)
    // charts
    private const val CX0 = 0.21f; private const val CW = 0.60f; private const val CZ = -0.10f
    private const val EY0 = 0.19f; private const val EH = 0.20f      // E_k-nu chart
    private const val IY0 = -0.17f; private const val IH = 0.20f     // I-U chart
    private const val NU0 = 4.0e14; private const val NU1 = 1.4e15; private const val EK_TOP = 4.0
    private const val NE = 110; private const val NPH = 14; private const val NC = 120

    // ---------------------------------------------------------------- state
    private class E(var alive: Boolean = false, var s: Photoelectron? = null, var e: Double = 0.0, var age: Float = 0f,
                    var passed: Boolean = false)
    private val el = Array(NE) { E() }
    private var emitAcc = 0.0; private var emitted = 0; private var photonPhase = 0f
    private val eNodes = arrayOfNulls<Node>(NE); private val eHalo = arrayOfNulls<Node>(NE)
    private val photons = arrayOfNulls<Node>(NPH)
    private lateinit var cathode: Node; private lateinit var spotNode: Node; private lateinit var beamCore: Node; private lateinit var beamHalo: Node
    private lateinit var aperture: Node; private lateinit var apertureGlow: Node
    private lateinit var ivLive: MeshData; private lateinit var ivGhost: MeshData
    private lateinit var ivLiveNode: Node; private lateinit var ivGhostNode: Node
    private lateinit var ivDot: Node; private lateinit var ucTick: Node
    private val metalLines = arrayOfNulls<Node>(3); private val metalDots = arrayOfNulls<Node>(3)
    private lateinit var nuMarker: Node; private lateinit var nuDot: Node
    private var lastKey = ""

    private val metal get() = Photo.METALS[metalIdx]
    private fun metalName(m: Photo.Metal) = tr(m.name, m.nameEn)
    private val ek get() = Photo.ekMax(lambda.toDouble(), metal.workEv)
    private val iSat get() = Photo.saturationCurrent(lambda.toDouble(), metal.workEv, P_MAX * intensity)
    private val iSat100 get() = Photo.saturationCurrent(lambda.toDouble(), metal.workEv, P_MAX)
    private val current get() = tube.current(voltage.toDouble(), iSat, ek)

    // ---------------------------------------------------------------- materials (sRGB)
    private val METAL_C = listOf(Rgb(0.86f, 0.74f, 0.48f), Rgb(0.82f, 0.82f, 0.80f), Rgb(0.62f, 0.68f, 0.76f))
    private val METAL_LINE = listOf(Rgb(1.0f, 0.80f, 0.40f), Rgb(0.85f, 0.88f, 0.95f), Rgb(0.45f, 0.75f, 1.0f))
    private val ELECTRON = Rgb(0.55f, 0.95f, 1.0f)
    private val alu = Mat.Pbr(Rgb(0.80f, 0.82f, 0.85f), metallic = 1f, roughness = 0.32f)
    private val nickel = Mat.Pbr(Rgb(0.72f, 0.72f, 0.70f), metallic = 1f, roughness = 0.25f)
    private val bakelite = Mat.Pbr(Rgb(0.07f, 0.07f, 0.08f), metallic = 0f, roughness = 0.35f)
    private val lampBody = Mat.Pbr(Rgb(0.20f, 0.22f, 0.26f), metallic = 0.8f, roughness = 0.35f)
    private val tubeGlass = Mat.Pbr(Rgb(0.08f, 0.11f, 0.14f), metallic = 0f, roughness = 0.06f, alpha = 0.12f)   // glass shows as reflections, not as a tint
    private val panelGlass = Mat.Pbr(Rgb(0.05f, 0.06f, 0.08f), metallic = 0f, roughness = 0.08f, alpha = 0.82f)
    private val axisMat = Mat.Unlit(Rgb(0.70f, 0.78f, 0.88f), alpha = 0.55f, blend = Blend.TRANSPARENT)
    private val gridLine = Mat.Unlit(Rgb(0.55f, 0.70f, 0.85f), alpha = 0.18f, blend = Blend.TRANSPARENT)
    private val slabTex by lazy {
        ProcTex.bakePlanet(256, 128, Rgb(0.07f, 0.075f, 0.085f), Rgb(0.12f, 0.125f, 0.14f), Rgb(0.20f, 0.205f, 0.22f), 0f, 77)
    }

    private fun beamRgb(l: Float = lambda): Rgb {
        val c = Photo.spectralRgb(l.toDouble())
        // soften off the raw primaries a touch (15 % toward white) so it sits with the scene
        return Rgb((c[0] * 0.85 + 0.15).toFloat(), (c[1] * 0.85 + 0.15).toFloat(), (c[2] * 0.85 + 0.15).toFloat())
    }

    // ---------------------------------------------------------------- build

    override fun buildSpec(cacheDir: File): Node {
        // a rebuild (re-entering the demo) must re-apply every setting to the NEW nodes: a stale
        // lastKey made applySettings() return early and left unit meshes at 1 m scale
        lastKey = ""; emitAcc = 0.0; emitted = 0; photonPhase = 0f
        val root = Node("PhotoRoot")
        val unitCyl = MeshGen.cylinder(1f, 1f, 24)
        val sphere = MeshGen.sphere(1f, 12, 24, MeshGen.SphereUv.EQUIRECT)
        val toX = Q.axisAngle(0f, 0f, 1f, (PI / 2).toFloat())         // cylinder axis y -> x

        root.add("Plinth", MeshGen.box(1.74f, 0.035f, 0.92f), Mat.Pbr(Rgb(1f, 1f, 1f), roughness = 0.55f, tex = slabTex), P3(0f, PLINTH_Y, -0.04f))
        // trim sits INSIDE the slab: only its 5 mm overhang shows, as an edge (it used to cover the top)
        root.add("PlinthBand", MeshGen.box(1.75f, 0.008f, 0.93f), alu, P3(0f, PLINTH_Y, -0.04f))

        // ---- the phototube: glass envelope, base, cathode K, mesh anode A
        val tubeMid = (TX0 + TX1) / 2; val tubeLen = TX1 - TX0
        // an open glass wall (no milky end caps) + bright rims where the glass edge catches the light
        root.add("Glass", MeshKit.sectorPrism(TR - 0.003f, TR, 0f, 2 * PI.toFloat(), -tubeLen / 2, tubeLen / 2, 72), tubeGlass, P3(tubeMid, TY, 0f)).rot =
            Q.axisAngle(0f, 1f, 0f, (PI / 2).toFloat())
        val rim = MeshGen.tube(MeshKit.circle(TR, 72, "yz"), 0.0022f, 6, true)
        root.add("GlassRim", rim, Mat.Unlit(Rgb(0.75f, 0.88f, 1.0f), 0.45f, blend = Blend.ADD), P3(TX1, TY, 0f))
        root.add("GlassDome", MeshGen.sphere(1f, 16, 48, MeshGen.SphereUv.EQUIRECT), tubeGlass, P3(TX1, TY, 0f)).scale = P3(0.06f, TR, TR)
        root.add("Base", MeshGen.cylinder(TR + 0.01f, 0.06f, 64), bakelite, P3(TX0 - 0.03f, TY, 0f)).rot = toX
        root.add("BaseRing", MeshGen.tube(MeshKit.circle(TR + 0.005f, 64, "yz", TX0 + 0.002f, TY, 0f), 0.006f, 8, true), alu)
        for (s in floatArrayOf(-1f, 1f)) {     // cradles
            val cx = if (s < 0) TX0 + 0.06f else TX1 - 0.06f
            root.add("Cradle", MeshGen.box(0.05f, TY - TR - TOP + 0.02f, 0.20f), bakelite, P3(cx, (TY - TR + TOP) / 2 + 0.01f, 0f))
        }
        cathode = root.add("Cathode", MeshGen.cylinder(0.165f, 0.012f, 64), Mat.Pbr(METAL_C[metalIdx], metallic = 1f, roughness = 0.30f),
            P3(KX - 0.006f, TY, 0f)).also { it.rot = toX; it.tapRadius = 0.17f }
        root.add("CathodeStem", unitCyl, nickel, P3(KX - 0.04f, TY, 0f)).also { it.scale = P3(0.008f, 0.07f, 0.008f); it.rot = toX }
        // mesh anode: a ring and a coarse wire grid (transparent to the light, opaque to electrons)
        root.add("AnodeRing", MeshGen.tube(MeshKit.circle(RA, 72, "yz", AXP, TY, 0f), 0.006f, 8, true), nickel)
        var g = -RA + 0.04f
        while (g < RA - 0.01f) {
            val h = sqrt(RA * RA - g * g)
            root.add("AnodeWire", MeshGen.tube(floatArrayOf(AXP, TY + g, -h, AXP, TY + g, h), 0.0016f, 5, false), nickel)
            root.add("AnodeWire", MeshGen.tube(floatArrayOf(AXP, TY - h, g, AXP, TY + h, g), 0.0016f, 5, false), nickel)
            g += 0.06f
        }
        root.add("AnodeLead", unitCyl, nickel, P3(AXP, TY - RA - (TR - RA) / 2, 0f)).scale = P3(0.004f, TR - RA, 0.004f)
        spotNode = root.add("LitSpot", MeshGen.cylinder(1f, 1f, 32), Mat.Unlit(beamRgb(), 0.5f, blend = Blend.ADD), SPOT_C).also { it.rot = toX }

        // ---- the lamp (gaze + pinch: step through the mercury lines)
        val dir = norm(SPOT_C - LAMP)
        val qd = Q.fromTo(0f, 1f, 0f, dir.x, dir.y, dir.z)
        root.add("LampHousing", MeshGen.cylinder(0.058f, 0.20f, 32), lampBody, LAMP - dir * 0.115f).also { it.rot = qd; it.tapRadius = 0.08f }
        root.add("LampCap", MeshGen.cylinder(0.064f, 0.025f, 32), alu, LAMP - dir * 0.222f).rot = qd
        root.add("LampFins", MeshGen.cylinder(0.066f, 0.008f, 32), alu, LAMP - dir * 0.15f).rot = qd
        aperture = root.add("Aperture", MeshGen.cylinder(0.046f, 0.012f, 32), Mat.Unlit(beamRgb()), LAMP - dir * 0.008f).also { it.rot = qd }
        apertureGlow = root.add("ApertureGlow", sphere, Mat.Unlit(beamRgb(), 0.35f, blend = Blend.ADD), LAMP).also { it.uniformScale(0.048f) }
        val post = P3(LAMP.x - dir.x * 0.12f, 0f, LAMP.z - dir.z * 0.12f)
        val postTop = LAMP.y - dir.y * 0.12f - 0.058f
        root.add("LampPost", unitCyl, alu, P3(post.x, (postTop + TOP) / 2, post.z)).scale = P3(0.009f, postTop - TOP, 0.009f)
        root.add("LampFoot", MeshGen.cylinder(0.04f, 0.012f, 24), alu, P3(post.x, TOP + 0.006f, post.z))
        val beamLen = (SPOT_C - LAMP).length
        val beamMid = (SPOT_C + LAMP) * 0.5f
        beamCore = root.add("BeamCore", unitCyl, Mat.Unlit(beamRgb(), 0.7f, blend = Blend.ADD), beamMid).also { it.rot = qd; it.scale = P3(0.005f, beamLen, 0.005f) }
        beamHalo = root.add("BeamHalo", unitCyl, Mat.Unlit(beamRgb(), 0.12f, blend = Blend.ADD), beamMid).also { it.rot = qd; it.scale = P3(0.026f, beamLen, 0.026f) }
        for (k in 0 until NPH) photons[k] = root.add("Photon$k", sphere, Mat.Unlit(beamRgb(), 1f, blend = Blend.ADD)).also { it.uniformScale(0.011f) }

        // ---- electrons
        val eMat = Mat.Unlit(Rgb(0.85f, 1.0f, 1.0f))
        val hMat = Mat.Unlit(ELECTRON, 0.35f, blend = Blend.ADD)
        for (k in 0 until NE) {
            eNodes[k] = root.add("Electron$k", sphere, eMat).also { it.uniformScale(0.0065f); it.visible = false }
            eHalo[k] = root.add("ElectronGlow$k", sphere, hMat).also { it.uniformScale(0.0125f); it.visible = false }
        }

        // ---- meter box (its numbers are in the label above it), with leads to K and A
        root.add("Meter", MeshGen.box(0.22f, 0.10f, 0.12f), bakelite, P3(-0.36f, TOP + 0.05f, 0.30f))
        root.add("MeterFace", MeshGen.box(0.17f, 0.05f, 0.004f), Mat.Unlit(Rgb(0.06f, 0.16f, 0.12f)), P3(-0.36f, TOP + 0.055f, 0.362f))
        root.add("LeadK", MeshGen.tube(curve(P3(-0.42f, TOP + 0.10f, 0.28f), P3(-0.60f, TOP + 0.05f, 0.15f), P3(TX0 - 0.03f, TY - 0.20f, 0.0f)), 0.0035f, 6, false),
            Mat.Pbr(Rgb(0.12f, 0.12f, 0.13f), roughness = 0.5f))
        root.add("LeadA", MeshGen.tube(curve(P3(-0.30f, TOP + 0.10f, 0.28f), P3(-0.22f, TOP + 0.04f, 0.10f), P3(AXP, TY - TR - 0.01f, 0.0f)), 0.0035f, 6, false),
            Mat.Pbr(Rgb(0.75f, 0.16f, 0.14f), roughness = 0.5f))

        buildCharts(root, sphere, unitCyl)
        restyle()
        // warm start: the lamp has been on for a while
        repeat(180) { stepElectrons(1f / 60f) }
        update(0f)
        return root
    }

    private fun buildCharts(root: Node, sphere: MeshData, unitCyl: MeshData) {
        root.add("ChartPanel", MeshGen.box(CW + 0.12f, 0.70f, 0.012f), panelGlass, P3(CX0 + CW / 2 - 0.01f, 0.12f, CZ - 0.012f))
        root.add("ChartFoot", MeshGen.box(0.30f, 0.02f, 0.12f), alu, P3(CX0 + CW / 2, TOP + 0.01f, CZ))
        root.add("ChartStem", unitCyl, alu, P3(CX0 + CW / 2, (TOP + 0.12f - 0.35f) / 2, CZ - 0.012f)).scale = P3(0.012f, 0.12f - 0.35f - TOP, 0.012f)
        val z = CZ - 0.003f
        // --- E_k vs nu
        root.add("EAxisX", seg(CX0, EY0, CX0 + CW, EY0, z, 0.0013f), axisMat)
        root.add("EAxisY", seg(CX0, EY0, CX0, EY0 + EH, z, 0.0013f), axisMat)
        for (k in 1..4) root.add("EGrid", seg(CX0, ey(k.toDouble()), CX0 + CW, ey(k.toDouble()), z, 0.0007f), gridLine)
        // spectrum strip under the frequency axis: you can see where visible light ends and UV begins
        val strip = Image(256, 1)
        for (i in 0 until 256) {
            val nu = NU0 + (NU1 - NU0) * (i + 0.5) / 256; val l = Photo.C / nu * 1e9
            val c = if (l < 380) doubleArrayOf(0.20, 0.12, 0.30) else Photo.spectralRgb(l)
            strip.px[i] = ProcTex.argb(1f, c[0].toFloat(), c[1].toFloat(), c[2].toFloat())
        }
        root.add("Spectrum", MeshKit.quadXY(CW, 0.014f), Mat.Unlit(Rgb(1f, 1f, 1f), tex = strip), P3(CX0 + CW / 2, EY0 - 0.014f, z))
        for (m in 0..2) {
            val w = Photo.METALS[m].workEv
            val nu0 = Photo.thresholdHz(w)
            metalLines[m] = root.add("Millikan$m", seg(ex(nu0), EY0, ex(NU1), ey(Photo.H * NU1 / Photo.QE - w), z + 0.001f, 0.0024f),
                Mat.Unlit(METAL_LINE[m], 0.9f, blend = Blend.ADD))
            metalDots[m] = root.add("Threshold$m", sphere, Mat.Unlit(METAL_LINE[m]), P3(ex(nu0), EY0, z + 0.002f)).also { it.uniformScale(0.0065f) }
        }
        nuMarker = root.add("NuMarker", unitCyl, Mat.Unlit(beamRgb(), 0.6f, blend = Blend.ADD))
        nuDot = root.add("NuDot", sphere, Mat.Unlit(Rgb(1f, 1f, 1f)))
        // --- I vs U
        root.add("IAxisX", seg(CX0, IY0, CX0 + CW, IY0, z, 0.0013f), axisMat)
        root.add("IAxisY", seg(ux(0f), IY0, ux(0f), IY0 + IH, z, 0.0013f), axisMat)
        for (u in -3..4) if (u != 0) root.add("UTick", seg(ux(u.toFloat()), IY0 - 0.006f, ux(u.toFloat()), IY0, z, 0.0009f), axisMat)
        ivGhost = MeshGen.tube(FloatArray(NC * 3) { if (it % 3 == 0) it * 1e-4f else 0f }, 0.0018f, 5, false)
        ivLive = MeshGen.tube(FloatArray(NC * 3) { if (it % 3 == 0) it * 1e-4f else 0f }, 0.0030f, 6, false)
        ivGhostNode = root.add("IVGhost", ivGhost, Mat.Unlit(ELECTRON, 0.30f, blend = Blend.ADD))
        ivLiveNode = root.add("IVLive", ivLive, Mat.Unlit(ELECTRON, 0.95f, blend = Blend.ADD))
        ivDot = root.add("IVDot", sphere, Mat.Unlit(Rgb(1f, 1f, 1f))).also { it.uniformScale(0.009f) }
        ucTick = root.add("UcTick", unitCyl, Mat.Unlit(Rgb(1f, 0.55f, 0.35f)))
    }

    // ---------------------------------------------------------------- chart mapping
    private fun ex(nu: Double) = CX0 + CW * ((nu - NU0) / (NU1 - NU0)).toFloat().coerceIn(0f, 1f)
    private fun ey(e: Double) = EY0 + EH * (e / EK_TOP).toFloat().coerceIn(0f, 1.05f)
    private fun ux(u: Float) = CX0 + CW * ((u - U_MIN) / (U_MAX - U_MIN))

    /** Everything that depends on the controls, rewritten only when one changes. */
    private fun restyle() {
        val key = "$metalIdx/$lambda/$intensity/$voltage"
        if (key == lastKey) return
        lastKey = key
        val c = beamRgb(); val uv = lambda < 380f
        cathode.setMat(Mat.Pbr(METAL_C[metalIdx], metallic = 1f, roughness = if (metalIdx == 2) 0.38f else 0.26f))
        val a = 0.25f + 0.75f * intensity
        beamCore.setMat(Mat.Unlit(c, (if (uv) 0.35f else 0.75f) * a, blend = Blend.ADD))
        beamHalo.setMat(Mat.Unlit(c, (if (uv) 0.06f else 0.13f) * a, blend = Blend.ADD))
        beamHalo.scale = P3(0.012f + 0.022f * intensity, beamHalo.scale.y, 0.012f + 0.022f * intensity)
        aperture.setMat(Mat.Unlit(c)); apertureGlow.setMat(Mat.Unlit(c, 0.25f + 0.35f * intensity, blend = Blend.ADD))
        spotNode.setMat(Mat.Unlit(c, 0.25f + 0.55f * intensity, blend = Blend.ADD))
        spotNode.scale = P3(SPOT * 1.4f, 0.004f, SPOT * 1.4f)
        for (k in 0 until NPH) photons[k]!!.setMat(Mat.Unlit(c, if (uv) 0.6f else 1f, blend = Blend.ADD))
        // Millikan chart: selected metal bright, the operating point on its line
        val nu = Photo.freqHz(lambda.toDouble())
        for (m in 0..2) metalLines[m]!!.setMat(Mat.Unlit(METAL_LINE[m], if (m == metalIdx) 1f else 0.28f, blend = Blend.ADD))
        val e = ek
        nuMarker.setMat(Mat.Unlit(c, 0.7f, blend = Blend.ADD))
        val top = ey(maxOf(e, 0.0)); val nx = ex(nu)
        nuMarker.pos = P3(nx, (EY0 + maxOf(top, EY0 + 0.012f)) / 2, CZ + 0.001f)
        nuMarker.scale = P3(0.0016f, maxOf(top - EY0, 0.012f), 0.0016f)
        nuDot.pos = P3(nx, top, CZ + 0.003f); nuDot.uniformScale(if (e > 0) 0.011f else 0.008f)
        nuDot.setMat(Mat.Unlit(if (e > 0) Rgb(1f, 1f, 1f) else Rgb(1f, 0.35f, 0.3f)))
        // I-U curves: ghost = full intensity, live = the current intensity (same U_c!)
        val ref = maxOf(iSat100, 1e-12)
        fun path(isat: Double): FloatArray {
            val out = FloatArray(NC * 3)
            for (i in 0 until NC) {
                val u = U_MIN + (U_MAX - U_MIN) * i / (NC - 1)
                out[i * 3] = ux(u); out[i * 3 + 1] = IY0 + IH * 0.86f * (tube.current(u.toDouble(), isat, e) / ref).toFloat(); out[i * 3 + 2] = CZ + 0.001f
            }
            return out
        }
        MeshGen.tubeInto(ivGhost, path(iSat100), 0.0018f, 5, false); meshEdited(ivGhost)
        MeshGen.tubeInto(ivLive, path(iSat), 0.0030f, 6, false); meshEdited(ivLive)
        ivDot.pos = P3(ux(voltage), IY0 + IH * 0.86f * (current / ref).toFloat(), CZ + 0.004f)
        ucTick.visible = e > 0 && -e > U_MIN
        ucTick.pos = P3(ux(-e.toFloat()), IY0, CZ + 0.003f); ucTick.scale = P3(0.0028f, 0.03f, 0.0028f)
    }

    // ---------------------------------------------------------------- electrons

    private fun stepElectrons(dt: Float) {
        val e = ek
        val kv = K_V[speedIdx]
        // emission: a Poisson-ish stream whose RATE is the photocurrent (intensity x yield)
        if (e > 0) {
            val rate = (24.0 * iSat / 4e-6).coerceAtMost(70.0)
            emitAcc += rate * dt
            while (emitAcc >= 1.0) {
                emitAcc -= 1.0
                val slot = el.indexOfFirst { !it.alive }
                if (slot < 0) break
                el[slot].apply { alive = true; s = Photoelectron.sample(emitted++ % 4096, tube.spotRatio); this.e = e * s!!.u; age = 0f; passed = false }
                // sound: one electron knocked out of the cathode — a quiet TICK. Brightness sets how OFTEN it
                // ticks, the colour (its kinetic energy) sets the pitch; below threshold: silence, however bright
                if (dt > 0f) Sfx.play(Sfx.TICK, 0.15f, (0.7f + 0.6f * sqrt((el[slot].e / 3.0).toFloat())).coerceIn(0.6f, 1.7f), KX / 0.9f)
            }
        }
        val a = kv * kv * voltage / (2 * D)                         // display acceleration along +x
        for (p in el) {
            if (!p.alive) continue
            p.age += dt
            val s = p.s!!
            val vn = kv * sqrt((p.e * s.c2).toFloat()); val vt = kv * sqrt((p.e * (1 - s.c2)).toFloat())
            val t = p.age
            val x = vn * t + 0.5f * a * t * t
            val ly = (s.rx * D).toFloat() + vt * t * cos(s.phi).toFloat()
            val lz = (s.ry * D).toFloat() + vt * t * sin(s.phi).toFloat()
            if (x < 0f && t > 0.05f) { p.alive = false; continue }              // turned back onto the cathode
            if (!p.passed && x >= D) {
                if (hypot(ly, lz) <= RA) { p.alive = false; continue }         // collected: this is the current
                p.passed = true                                                // missed the anode, flies on
            }
            if (hypot(ly, lz) > TR - 0.01f || KX + x > TX1 - 0.01f || p.age > 3.2f) p.alive = false      // slow strays end on the glass
        }
        photonPhase += PHOTON_V[speedIdx] * dt / (SPOT_C - LAMP).length
    }

    // ---------------------------------------------------------------- per frame

    override fun update(dt: Float) {
        restyle()
        stepElectrons(dt)
        val kv = K_V[speedIdx]; val a = kv * kv * voltage / (2 * D)
        for (k in 0 until NE) {
            val p = el[k]
            eNodes[k]!!.visible = p.alive; eHalo[k]!!.visible = p.alive
            if (!p.alive) continue
            val s = p.s!!; val t = p.age
            val vn = kv * sqrt((p.e * s.c2).toFloat()); val vt = kv * sqrt((p.e * (1 - s.c2)).toFloat())
            val x = KX + 0.004f + vn * t + 0.5f * a * t * t
            val pos = P3(x, TY + (s.rx * D).toFloat() + vt * t * cos(s.phi).toFloat(), (s.ry * D).toFloat() + vt * t * sin(s.phi).toFloat())
            eNodes[k]!!.pos = pos; eHalo[k]!!.pos = pos
        }
        // photons: count = intensity, fixed speed (c), colour = energy
        val n = (2 + intensity * (NPH - 2)).roundToInt()
        val dir = SPOT_C - LAMP
        for (k in 0 until NPH) {
            photons[k]!!.visible = k < n && intensity > 0.01f
            val f = ((photonPhase + k.toFloat() / n) % 1f)
            photons[k]!!.pos = LAMP + dir * f
        }
        updateReadout(); updateLabels()
    }

    private fun updateReadout() {
        val e = ek; val w = metal.workEv; val hv = Photo.photonEv(lambda.toDouble())
        val l1 = tr("光子能量 hν = %.2f eV（λ = %.0f nm%s）  %s %s 逸出功 W = %.2f eV → %s",
            "hν = %.2f eV (λ = %.0f nm%s)  %s %s work function W = %.2f eV → %s").format(
            hv, lambda, if (lambda < 380) tr("，紫外", ", UV") else "", metalName(metal), metal.symbol, w,
            if (e > 0) "E_k,max = hν − W = %.2f eV".format(e) else tr("hν < W：一个电子也打不出来，光再强也没用", "hν < W: no electrons, however bright"))
        val nu0 = Photo.thresholdHz(w)
        // each line must fit the 984 dp panel at 22 sp (UiPreviewTest fails on a wrapped readout)
        val l2 = if (e > 0) tr("遏止电压 U_c = E_k,max/e = %.2f V（与光强无关）· 截止波长 λ₀ = %.0f nm · 最快 %.2f×10⁵ m/s",
            "Stopping voltage U_c = %.2f V (any intensity) · threshold λ₀ = %.0f nm · fastest %.2f×10⁵ m/s").format(
            e, Photo.thresholdNm(w), Photo.speedOf(e) / 1e5)
            else tr("截止频率 ν₀ = W/h = %.2f×10¹⁴ Hz（λ₀ = %.0f nm）：把波长调到 %.0f nm 以下才有光电子",
                "Threshold frequency ν₀ = W/h = %.2f×10¹⁴ Hz (λ₀ = %.0f nm): photoelectrons only below %.0f nm").format(nu0 / 1e14, Photo.thresholdNm(w), Photo.thresholdNm(w))
        val l3 = tr("U_AK = %+.2f V  I = %.2f μA  饱和 %.2f μA ∝ 光强 %d%% · 光强只改变电子数目，不改变速度",
            "U_AK = %+.2f V  I = %.2f μA  saturation %.2f μA ∝ intensity %d%% · brighter = more, not faster").format(
            voltage, current * 1e6, iSat * 1e6, (intensity * 100).roundToInt())
        readoutText = "$l1\n$l2\n$l3"
    }

    private fun updateLabels() {
        labels.clear()
        labels += LabelSpec(tr("阴极 K · %s %s  W=%.2f eV", "Cathode K · %s %s  W=%.2f eV").format(metalName(metal), metal.symbol, metal.workEv), P3(KX, TY + TR + 0.06f, 0.02f))
        labels += LabelSpec(tr("阳极 A", "Anode A"), P3(AXP + 0.02f, TY + TR + 0.035f, 0.02f))
        labels += LabelSpec("λ = %.0f nm · hν = %.2f eV".format(lambda, Photo.photonEv(lambda.toDouble())), P3(LAMP.x + 0.06f, LAMP.y + 0.10f, LAMP.z))
        labels += LabelSpec(tr("E_k,max – ν：三种金属，斜率都是 h", "E_k,max – ν: three metals, same slope h"), P3(CX0 + CW / 2, EY0 + EH + 0.035f, CZ + 0.01f))
        labels += LabelSpec(if (ek > 0) tr("I – U：遏止电压 −U_c = −%.2f V", "I – U: stopping voltage −U_c = −%.2f V").format(ek) else tr("I – U：没有光电流", "I – U: no photocurrent"), P3(CX0 + CW / 2, IY0 + IH + 0.035f, CZ + 0.01f))
        labels += LabelSpec("U = %+.2f V   I = %.2f μA".format(voltage, current * 1e6), P3(-0.36f, TOP + 0.16f, 0.30f))
    }

    // ---------------------------------------------------------------- interaction

    override val tapTargets get() = listOf(
        TapTarget("LampHousing", "汞灯 → 换谱线", "Lamp → next line"),
        TapTarget("Cathode", "阴极 → 换金属", "Cathode → next metal"),
    )

    override fun isTarget(name: String) = name == "LampHousing" || name == "Cathode"

    override fun onTap(name: String) {
        when (name) {
            "LampHousing" -> {
                val lines = Photo.HG_LINES
                val k = lines.indexOfFirst { it > lambda + 0.5 }
                lambda = (if (k < 0) lines[0] else lines[k]).toFloat()
            }
            "Cathode" -> metalIdx = (metalIdx + 1) % Photo.METALS.size
        }
    }

    // ---------------------------------------------------------------- guided track (导学)
    // KEY INSIGHT: whether electrons come out depends on the light's FREQUENCY, not its brightness. On sodium
    // (W = 2.28 eV, λ₀ = 544 nm) bright red 650 nm light (1.91 eV photons) frees none; dim violet 405 nm
    // (3.06 eV) does — brightness only sets HOW MANY; the fastest carry E_k,max = hν − W = 0.78 eV (U_c = 0.78 V).
    private var intensityAtStep = 0f
    private val aliveElectrons get() = el.count { it.alive }

    // ---- background card (📖): context for a viewer who has never met this topic
    override val about = About(listOf(
        About.Page(
            Bi("这个实验在做什么？", "What is this experiment?"),
            Bi("一只大真空管：左边是金属阴极 K，右边是网状阳极 A。后方的汞灯射来一束光，光子像珠子一样奔向阴极，被打出的电子飞向阳极。可以滑动波长（700 到 200 nm）和光强，捏阴极换金属，调阳极电压 U_AK 让电子减速。右边两张图：最快电子的能量随频率怎样变，电流随电压怎样变。", "A large vacuum tube: metal cathode K on the left, mesh anode A on the right. The lamp at the back sends a beam; photons march like beads to the cathode, and the electrons they knock out fly to the anode. Slide the wavelength (700 to 200 nm) and the intensity, pinch the cathode to change the metal, and set the anode voltage U_AK to slow the electrons. Right: top electron energy vs frequency, and current vs voltage."),
        ),
        About.Page(
            Bi("光是波，还是颗粒？", "Is light a wave or a particle?"),
            Bi("1887 年，赫兹发现紫外光照在金属上，电火花更容易跳出来。后来勒纳德测到：光再亮，飞出的电子也不会更快；而红光再亮，往往一个电子也打不出。这和“光是波”完全对不上——波越强，应该能量越大。1905 年，爱因斯坦提出光是一份一份的“光子”。密立根起初并不相信，可他 1916 年的精密测量反而证实了它。", "In 1887 Hertz noticed that ultraviolet light on metal makes sparks jump more easily. Later Lenard found that brighter light does not make the electrons faster, and red light, however bright, often frees none at all. That clashed with light as a wave — a stronger wave should carry more energy. In 1905 Einstein proposed light comes in packets: photons. Millikan was doubtful, yet his careful 1916 measurements confirmed it."),
        ),
        About.Page(
            Bi("一份一份地给", "Energy in packets"),
            Bi("光的能量是一份一份交付的。每份（一个光子）的能量 E = hν 只由颜色决定：紫光大，红光小。一个光子最多打出一个电子，不够的能量也没法攒。就像只收一元硬币的售货机：投一百个一角也不行，投一个一元就出货。所以颜色决定电子飞多快，亮度只决定电子有多少；频率太低，再亮也没用。", "Light delivers energy in packets. Each packet, a photon, carries E = hν, set only by the colour: violet a lot, red a little. One photon frees at most one electron, and photons cannot pool their energy. Like a vending machine that takes only one-yuan coins: a hundred ten-cent coins buy nothing, one coin works. So colour sets how fast electrons fly, brightness only how many; below the threshold frequency, no brightness helps."),
        ),
        About.Page(
            Bi("今天它在哪里？", "Where is it today?"),
            Bi("手机相机的每个像素，都在把照进来的光子变成电子，再数一数，靠的是光电效应的近亲。太阳能电池把光子变成电流。光电倍增管能数出单个光子，用在医院的 PET 扫描仪和地下的中微子探测器里。路灯的光控开关、烟雾报警器里的光电传感器，也都靠光“打”出电子。爱因斯坦正是因光电效应获得 1921 年诺贝尔奖。", "Every pixel in a phone camera turns incoming photons into electrons and counts them — a close cousin of this effect. Solar cells turn photons into current. Photomultiplier tubes count single photons in hospital PET scanners and underground neutrino detectors. Light-sensing streetlight switches and optical smoke alarms also rely on light freeing electrons. Einstein's Nobel Prize of 1921 was awarded for the photoelectric effect."),
        ),
    ))

    override val lesson: Lesson by lazy {
        Lesson(
            listOf(
                LessonStep(
                    say = Bi("钠阴极正被 405 nm 紫光照着，电子不断飞出。换成红光（650 nm），还有电子吗？", "Violet 405 nm light is knocking electrons off the sodium cathode. Switch to red (650 nm) — any electrons?"),
                    options = listOf(
                        Bi("有，少一些", "Yes, fewer"),
                        Bi("一个也没有", "None at all"),
                        Bi("有，飞得更快", "Yes, faster ones"),
                    ),
                    answer = 1,
                    onEnter = { metalIdx = 1; lambda = 405f; intensity = 0.7f; voltage = 0f },
                ),
                LessonStep(
                    say = Bi("捏住后方的汞灯（或面板「汞灯 → 换谱线」），一直换到红光 650 nm。", "Pinch the lamp at the back (or “Lamp → next line” below) until it shows red 650 nm."),
                    target = "LampHousing",
                    done = { lambda >= 600f && ek <= 0.0 },
                    autoplay = { while (lambda < 649f) onTap("LampHousing") },
                    reveal = true,
                    insight = Bi("一个也没有：红光光子只有 1.91 eV，小于钠的逸出功 2.28 eV，一个光子打不出一个电子。", "None: a red photon carries only 1.91 eV, less than sodium's work function 2.28 eV — it cannot free an electron."),
                ),
                LessonStep(
                    say = Bi("那把红光开到最亮呢？光强 100% 时会有电子吗？", "So make the red light as bright as it goes. At 100 % intensity, any electrons?"),
                    options = listOf(
                        Bi("会，够亮就行", "Yes, if bright enough"),
                        Bi("还是没有", "Still none"),
                    ),
                    answer = 1,
                ),
                LessonStep(
                    say = Bi("向右拖动面板「光强」滑块到 100%，盯住阴极。", "Drag the “Intensity” slider right to 100 % and watch the cathode."),
                    onEnter = { intensityAtStep = intensity },
                    done = { lambda >= 600f && intensity >= 0.95f && abs(intensity - intensityAtStep) > 0.01f },
                    autoplay = { intensity = 1f },
                    reveal = true,
                    insight = Bi("还是没有！更亮只是光子更多，每个仍只有 1.91 eV。出不出电子看频率（颜色），不看亮度。", "Still none! Brighter means more photons, each still 1.91 eV. Frequency (colour) decides, not brightness."),
                ),
                LessonStep(
                    say = Bi("把「波长 λ」滑块拖回 450 nm 以下的紫光，再把「光强」调到 20% 以下（很暗）。", "Drag “Wavelength” back below 450 nm (violet), then turn “Intensity” below 20 % (very dim)."),
                    done = { lambda <= 450f && intensity <= 0.2f && intensity > 0.01f && ek > 0.0 && aliveElectrons > 0 },
                    autoplay = { lambda = 405f; intensity = 0.15f },
                    insight = Bi("很暗的紫光照样打出电子，只是少些。405 nm 时 E_k,max = 3.06 − 2.28 = 0.78 eV，与亮度无关。", "Dim violet still frees electrons, just fewer. At 405 nm E_k,max = 3.06 − 2.28 = 0.78 eV, whatever the brightness."),
                ),
            ),
            challenge = Challenge(
                say = Bi("把「电压 U_AK」调成负值，让电流刚好降到 0：这就是遏止电压，量出最快电子的能量。", "Make “Anode U_AK” negative until the current just reaches 0: that stopping voltage measures the fastest electrons."),
                progress = {
                    if (ek <= 0.0) tr("现在没有光电子 —— 先换成紫光", "No photoelectrons now — pick violet light first")
                    else tr("U_AK = %+.2f V · I = %.3f μA（目标：I 刚好归零，误差 0.05 V 内）", "U_AK = %+.2f V · I = %.3f μA (goal: I just reaches 0, within 0.05 V)").format(voltage, current * 1e6)
                },
                done = { ek > 0.0 && abs(voltage + ek) <= 0.05 },
                autoplay = { if (ek <= 0.0) lambda = 405f; voltage = -ek.toFloat() },
            ),
        )
    }

    @Composable
    override fun Panel() {
        var mi by FieldRef(::metalIdx)
        var si by FieldRef(::speedIdx)
        var l by FieldRef(::lambda)
        var inten by FieldRef(::intensity)
        var u by FieldRef(::voltage)
        LabPanel(readout) {
            LabRow {
                SegmentControl {
                    Photo.METALS.forEachIndexed { k, m -> SegmentItem(selected = mi == k, onClick = { mi = k; metalIdx = k }, title = { Text(tr("${m.name} ${m.symbol} ${m.workEv} eV", "${m.symbol} ${m.workEv} eV")) }) }
                }
                Spacer(Modifier.width(20.dp))
                SegmentControl {
                    SPEED_LABELS.forEachIndexed { k, s -> SegmentItem(selected = si == k, onClick = { si = k; speedIdx = k }, title = { Text(s) }) }
                }
            }
            LabSlider(tr("波长 λ", "Wavelength"), l, 200f..700f, "%.0f nm".format(l)) { l = it; lambda = it }
            LabSlider(tr("光强", "Intensity"), inten, 0f..1f, "%d %%".format((inten * 100).roundToInt())) { inten = it; intensity = it }
            LabSlider(tr("电压 U_AK", "Anode U_AK"), u, U_MIN..U_MAX, "%+.2f V".format(u)) { u = it; voltage = it }
        }
    }

    // ---------------------------------------------------------------- helpers

    private fun seg(x0: Float, y0: Float, x1: Float, y1: Float, z: Float, r: Float) =
        MeshGen.tube(floatArrayOf(x0, y0, z, x1, y1, z), r, 6, false)

    private fun norm(p: P3): P3 { val l = p.length; return P3(p.x / l, p.y / l, p.z / l) }

    /** Quadratic Bezier a -> b -> c, 24 samples. */
    private fun curve(a: P3, b: P3, c: P3): FloatArray = FloatArray(24 * 3) { k ->
        val t = (k / 3) / 23f; val it = 1 - t
        when (k % 3) { 0 -> it * it * a.x + 2 * it * t * b.x + t * t * c.x; 1 -> it * it * a.y + 2 * it * t * b.y + t * t * c.y; else -> it * it * a.z + 2 * it * t * b.z + t * t * c.z }
    }
}
