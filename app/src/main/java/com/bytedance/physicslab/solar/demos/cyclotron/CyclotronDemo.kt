// 回旋加速器 · 洛伦兹力 (Lawrence, 1932).
//
// LESSON: in a uniform B a charge circles with f = qB/(2 pi m) — no speed, no radius in it. So a
// fixed-frequency voltage across the gap between two hollow "dees" can kick the particle every
// half turn, forever in step, while its orbit spirals out (r = mv/qB). It leaves at the rim with
// E = (qBR)^2/2m, whatever the dee voltage (U only sets how many turns that takes).
//
// WHAT YOU SEE: the machine stands facing you (B points at you: the field is drawn as the textbook
// "dot" markers between the poles; the front pole piece is cut away). The beam is continuous, one
// bunch per RF period, and all bunches share one trajectory — so the beads ride the glowing spiral
// lined up like the spoke of a wheel: they all cross the gap at the same instant, at every radius.
// That rigid spoke IS the frequency's independence of speed. Detune the RF and the spoke curls,
// the crossings slide off the crest on the oscilloscope, the beam decelerates and never gets out.
// The dees glow red (+) / blue (-) as the RF swaps their polarity.
//
// Physics: core/cyclotron/CyclotronCore (relativistic Boris pusher, real SI numbers, real gap).

package com.bytedance.physicslab.solar.demos.cyclotron

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
import com.bytedance.physicslab.solar.core.cyclotron.Cyclotron
import com.bytedance.physicslab.solar.core.cyclotron.Lorentz
import com.bytedance.physicslab.solar.core.cyclotron.Phys
import com.bytedance.physicslab.solar.core.cyclotron.Track
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
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin

object CyclotronDemo : SpecDemo() {
    override val id = "cyclotron"
    override val title = "回旋加速器 · 洛伦兹力"   // i18n-ok: Chinese title; the English one is DemoRegistry.TITLE_EN (shown via titleOf)
    override val credit = "Lawrence · 1932"

    // ---------------------------------------------------------------- the machine (SI)
    private const val R_DEE = 0.140          // Lawrence's 11-inch dees: 28 cm across
    private const val R_EXT = 0.120          // deflector radius
    private const val GAP = 0.010
    private const val S = 2.0f               // display metres per real metre (drawn x2)
    private const val EXIT_RUN = 0.100       // real metres of straight flight to the target
    private val DETUNE = doubleArrayOf(0.96, 1.0, 1.04)
    private val DETUNE_LABELS get() = listOf(tr("射频 -4%", "RF -4%"), tr("同步 f=qB/2πm", "Tuned f=qB/2πm"), tr("射频 +4%", "RF +4%"))
    private val SEC_PER_TURN = floatArrayOf(3.2f, 1.6f, 0.8f)
    private val SPEED_LABELS get() = listOf(tr("慢", "Slow"), tr("中", "Medium"), tr("快", "Fast"))

    var speciesIdx = 0; var bT = 1.30f; var uKV = 60f; var detuneIdx = 1; var speedIdx = 1
    var bSign = 1

    // ---------------------------------------------------------------- layout (display metres)
    private val CENTER = P3(-0.32f, 0.095f, 0f)
    private const val YAW = 0.30f            // rad: the machine turns its face toward the scope
    private const val PLINTH_Y = -0.262f
    private const val RD = (R_DEE * S).toFloat()
    private const val GH = (GAP / 2 * S).toFloat()
    private const val COIL_R = 0.318f
    private const val COIL_A = 0.026f
    private const val COIL_Z = 0.080f
    private const val CH_X0 = 0.20f; private const val CH_W = 0.64f; private const val CH_Z = -0.14f
    private const val CH_UY = 0.285f; private const val CH_UA = 0.085f
    private const val CH_EY0 = -0.075f; private const val CH_EH = 0.20f
    private const val E_SCALE_MEV = 1.8      // top of the energy lane
    private const val NP = 1400               // spiral samples
    private const val NC = 600                // scope samples
    private const val NB = 24                 // bunch pool
    private const val NT = 18                 // tail samples
    private const val NX = 48                 // crossing-dot pool
    private const val MAX_TURNS = 22
    /** RF periods across the oscilloscope: the whole flight, so every crossing is on it. */
    private var span = 13.0

    // ---------------------------------------------------------------- state
    private var theta = 0.0                   // clock in RF periods; bunch k leaves the source at theta = k
    private lateinit var mach: Cyclotron
    private lateinit var track: Track
    private var dirty = true
    private var sinceRebuild = 1f
    private val tmp = DoubleArray(3)

    private lateinit var spiral: Node; private lateinit var spiralMesh: MeshData
    private val beads = arrayOfNulls<Node>(NB); private val halos = arrayOfNulls<Node>(NB)
    private lateinit var tails: Node; private lateinit var tailMesh: MeshData   // all comet tails in ONE mesh: one upload per frame
    private var tailVerts = 0
    private lateinit var uCurve: MeshData; private lateinit var eCurve: MeshData
    private lateinit var uCurveNode: Node; private lateinit var eCurveNode: Node
    private val xDots = arrayOfNulls<Node>(NX); private val eDots = arrayOfNulls<Node>(NX)
    private lateinit var cursor: Node; private lateinit var cursorDot: Node; private lateinit var eFinalLine: Node
    private lateinit var target: Node; private lateinit var targetGlow: Node
    private val rim = arrayOfNulls<Node>(2)
    private val dots = ArrayList<Node>(); private val crosses = ArrayList<Node>()
    private lateinit var mach3d: Node
    private var flash = 0f

    // ---------------------------------------------------------------- materials (sRGB)
    private val copper = Mat.Pbr(Rgb(0.88f, 0.58f, 0.42f), metallic = 1f, roughness = 0.30f)
    private val darkSteel = Mat.Pbr(Rgb(0.19f, 0.20f, 0.22f), metallic = 0.85f, roughness = 0.42f)
    private val poleSteel = Mat.Pbr(Rgb(0.30f, 0.31f, 0.34f), metallic = 0.9f, roughness = 0.35f)
    private val yoke = Mat.Pbr(Rgb(0.17f, 0.21f, 0.28f), metallic = 0.3f, roughness = 0.55f)   // painted magnet iron
    private val alu = Mat.Pbr(Rgb(0.80f, 0.82f, 0.85f), metallic = 1f, roughness = 0.32f)
    private val glass = Mat.Pbr(Rgb(0.05f, 0.06f, 0.08f), metallic = 0f, roughness = 0.08f, alpha = 0.82f)
    private val slabTex by lazy {
        ProcTex.bakePlanet(256, 128, Rgb(0.07f, 0.075f, 0.085f), Rgb(0.12f, 0.125f, 0.14f), Rgb(0.20f, 0.205f, 0.22f), 0f, 77)
    }
    private val windTex by lazy { stripes(512, 128) }
    private val gridLine = Mat.Unlit(Rgb(0.55f, 0.70f, 0.85f), alpha = 0.25f, blend = Blend.TRANSPARENT)
    private val fieldRod = Mat.Unlit(Rgb(0.50f, 0.68f, 1.0f), alpha = 0.22f, blend = Blend.ADD)
    private val fieldDot = Mat.Unlit(Rgb(0.60f, 0.78f, 1.0f), alpha = 0.85f, blend = Blend.ADD)
    private val PLUS = Rgb(1.0f, 0.36f, 0.26f)
    private val MINUS = Rgb(0.32f, 0.58f, 1.0f)
    private val BEAM = listOf(Rgb(1.0f, 0.72f, 0.32f), Rgb(0.45f, 1.0f, 0.72f), Rgb(1.0f, 0.50f, 0.78f))
    private val beam get() = BEAM[speciesIdx]

    // ---------------------------------------------------------------- build

    override fun buildSpec(cacheDir: File): Node {
        rebuildPhysics()
        theta = 1000.0                                  // the beam has been running: steady state
        val root = Node("CyclotronRoot")
        val unitCyl = MeshGen.cylinder(1f, 1f, 16)
        val sphere = MeshGen.sphere(1f, 16, 32, MeshGen.SphereUv.EQUIRECT)

        root.add("Plinth", MeshGen.box(1.74f, 0.035f, 0.92f), Mat.Pbr(Rgb(1f, 1f, 1f), roughness = 0.55f, tex = slabTex), P3(0f, PLINTH_Y, -0.04f))
        // trim sits INSIDE the slab: only its 5 mm overhang shows, as an edge (it used to cover the top)
        root.add("PlinthBand", MeshGen.box(1.75f, 0.008f, 0.93f), alu, P3(0f, PLINTH_Y, -0.04f))

        // ---- the magnet + dees, in machine-local coordinates (dee plane = local XY, B along z)
        mach3d = root.add(Node("Machine")).also { it.pos = CENTER; it.rot = Q.axisAngle(0f, 1f, 0f, YAW) }
        val m = mach3d
        // back pole piece and return yoke (the front pole is cut away so you can see in)
        m.add("PoleBack", MeshGen.cylinder(0.30f, 0.05f, 64), poleSteel, P3(0f, 0f, -0.078f)).also { it.rot = rx90() }
        val yokeH = 0.70f; val footY = PLINTH_Y + 0.0175f - CENTER.y
        m.add("YokeBack", MeshGen.box(0.80f, yokeH, 0.05f), yoke, P3(0f, footY + yokeH / 2, -0.128f))
        for (sx in floatArrayOf(-1f, 1f)) {
            m.add("YokeSide", MeshGen.box(0.07f, yokeH, 0.27f), yoke, P3(sx * 0.40f, footY + yokeH / 2, -0.015f))
            // clamps: each coil is bolted to the side columns at 3 and 9 o'clock
            for (z in floatArrayOf(-COIL_Z, COIL_Z))
                m.add("CoilClamp", MeshGen.box(0.040f, 0.055f, 0.045f), alu, P3(sx * (COIL_R + COIL_A + 0.008f), 0f, z))
        }
        // coils: copper windings either side of the dees
        val coilPath = MeshKit.circle(COIL_R, 128)
        val coilMesh = MeshGen.tube(coilPath, COIL_A, 14, true)
        val wind = Mat.Pbr(Rgb(0.80f, 0.48f, 0.26f), metallic = 0.9f, roughness = 0.40f, tex = windTex)
        m.add("CoilBack", coilMesh, wind, P3(0f, 0f, -COIL_Z))
        m.add("CoilFront", coilMesh, wind, P3(0f, 0f, COIL_Z))

        // the dees: hollow copper "D" boxes, front lids removed (the beam lives inside)
        val back = MeshKit.dSlab(RD, GH, -0.036f, -0.030f, 48)
        val a0 = kotlin.math.acos(GH / RD)
        val wall = MeshKit.sectorPrism(RD - 0.006f, RD, -a0, a0, -0.036f, 0.032f, 48)
        val rimPath = MeshKit.arc(RD - 0.003f, -a0, a0, 48, z = 0.033f)
        val rimMesh = MeshGen.tube(rimPath, 0.0048f, 6, false)
        for (side in 0..1) {
            val d = m.add(Node("Dee$side")).also { if (side == 1) it.rot = Q.axisAngle(0f, 0f, 1f, PI.toFloat()) }
            d.add("DeeBack$side", back, copper)
            d.add("DeeWall$side", wall, copper)
            rim[side] = d.add("DeeRim$side", rimMesh, Mat.Unlit(PLUS, 0.5f, blend = Blend.ADD))
        }
        // feedthrough stems from each dee to the RF line (behind, through the pole gap)

        // B field between the poles: textbook dot / cross markers on short rods along z
        val rod = MeshGen.cylinder(1f, 1f, 6)
        val crossMesh = MeshGen.box(0.020f, 0.0028f, 0.0028f)
        var gx = -0.24f
        while (gx <= 0.241f) {
            var gy = -0.24f
            while (gy <= 0.241f) {
                if (hypot(gx, gy) < 0.25f && abs(gx) > GH + 0.02f) {
                    m.add("BRod", rod, fieldRod, P3(gx, gy, 0.005f)).also { it.scale = P3(0.0016f, 0.10f, 0.0016f); it.rot = rx90() }
                    dots += m.add("BDot", sphere, fieldDot, P3(gx, gy, 0.058f)).also { it.uniformScale(0.0065f) }
                    val c = m.add(Node("BCross")).also { it.pos = P3(gx, gy, 0.058f) }
                    c.add("BX1", crossMesh, fieldDot).also { it.rot = Q.axisAngle(0f, 0f, 1f, (PI / 4).toFloat()) }
                    c.add("BX2", crossMesh, fieldDot).also { it.rot = Q.axisAngle(0f, 0f, 1f, (-PI / 4).toFloat()) }
                    crosses += c
                }
                gy += 0.08f
            }
            gx += 0.08f
        }

        // ion source at the centre (gaze + pinch: change the particle)
        m.add("IonSource", sphere, Mat.Unlit(Rgb(1f, 0.95f, 0.85f)), P3(0f, 0f, 0f)).also {
            it.uniformScale(0.011f); it.tapRadius = 0.05f / 0.011f
        }
        m.add("IonSourceGlow", sphere, Mat.Unlit(Rgb(1f, 0.85f, 0.6f), 0.35f, blend = Blend.ADD)).uniformScale(0.022f)

        // the spiral every bunch follows, and the bunches riding it
        spiralMesh = MeshGen.tube(spiralPath(), 0.0022f, 5, false)
        spiral = m.add("Spiral", spiralMesh, Mat.Unlit(beam, 0.45f, blend = Blend.ADD))
        for (k in 0 until NB) {
            beads[k] = m.add("Bunch$k", sphere, Mat.Unlit(Rgb(1f, 0.97f, 0.90f))).also { it.uniformScale(0.0085f) }
            halos[k] = m.add("Halo$k", sphere, Mat.Unlit(beam, 0.40f, blend = Blend.ADD)).also { it.uniformScale(0.019f) }
        }
        tailMesh = mergedTails()
        tails = m.add("Tails", tailMesh, Mat.Unlit(beam, 0.85f, blend = Blend.ADD))
        target = m.add("Target", MeshGen.box(0.03f, 0.045f, 0.04f), poleSteel)
        targetGlow = m.add("TargetGlow", sphere, Mat.Unlit(beam, 0.5f, blend = Blend.ADD))

        // magnet power supply (gaze + pinch: reverse the magnet current, B flips)
        root.add("MagnetPSU", MeshGen.box(0.15f, 0.11f, 0.11f), darkSteel, P3(0.06f, PLINTH_Y + 0.075f, 0.24f)).also {
            it.tapRadius = 0.065f / 0.075f
        }
        root.add("PSUFace", MeshGen.box(0.12f, 0.07f, 0.004f), Mat.Unlit(Rgb(0.10f, 0.16f, 0.22f)), P3(0.06f, PLINTH_Y + 0.08f, 0.297f))
        root.add("PSULamp", sphere, Mat.Unlit(MINUS, 1f), P3(0.06f, PLINTH_Y + 0.08f, 0.30f)).uniformScale(0.012f)

        buildScope(root, unitCyl, sphere)
        applyLook()
        update(0f)
        return root
    }

    private fun buildScope(root: Node, unitCyl: MeshData, sphere: MeshData) {
        root.add("ScopePanel", MeshGen.box(CH_W + 0.07f, 0.52f, 0.012f), glass, P3(CH_X0 + CH_W / 2, 0.135f, CH_Z - 0.012f))
        root.add("ScopeFoot", MeshGen.box(0.30f, 0.02f, 0.12f), alu, P3(CH_X0 + CH_W / 2, PLINTH_Y + 0.03f, CH_Z))
        root.add("ScopeStem", unitCyl, alu, P3(CH_X0 + CH_W / 2, (PLINTH_Y + 0.03f + 0.135f - 0.26f) / 2, CH_Z - 0.012f)).also {
            it.scale = P3(0.012f, 0.135f - 0.26f - PLINTH_Y - 0.03f, 0.012f)
        }
        val z = CH_Z - 0.004f
        root.add("UZero", seg(CH_X0, CH_UY, CH_X0 + CH_W, CH_UY, z, 0.0012f), gridLine)
        root.add("EZero", seg(CH_X0, CH_EY0, CH_X0 + CH_W, CH_EY0, z, 0.0012f), gridLine)
        root.add("EAxis", seg(CH_X0, CH_EY0, CH_X0, CH_EY0 + CH_EH + 0.01f, z, 0.0012f), gridLine)
        eFinalLine = root.add("EFinal", seg(CH_X0, 0f, CH_X0 + CH_W, 0f, z, 0.0010f), Mat.Unlit(Rgb(1f, 1f, 1f), 0.35f, blend = Blend.TRANSPARENT))
        uCurve = MeshGen.tube(FloatArray(NC * 3) { if (it % 3 == 0) it * 1e-4f else 0f }, 0.0022f, 5, false)
        eCurve = MeshGen.tube(FloatArray(NC * 3) { if (it % 3 == 0) it * 1e-4f else 0f }, 0.0026f, 5, false)
        uCurveNode = root.add("UCurve", uCurve, Mat.Unlit(Rgb(0.45f, 0.65f, 1.0f), 0.75f, blend = Blend.ADD))
        eCurveNode = root.add("ECurve", eCurve, Mat.Unlit(beam, 0.95f, blend = Blend.ADD))
        for (k in 0 until NX) {
            xDots[k] = root.add("XDot$k", sphere, Mat.Unlit(beam)).also { it.uniformScale(0.0085f) }
            eDots[k] = root.add("EDot$k", sphere, Mat.Unlit(beam)).also { it.uniformScale(0.0042f) }
        }
        cursor = root.add("Cursor", unitCyl, Mat.Unlit(Rgb(1f, 1f, 1f), 0.30f, blend = Blend.ADD))
        cursorDot = root.add("CursorDot", sphere, Mat.Unlit(Rgb(1f, 1f, 1f), 0.9f, blend = Blend.ADD))
    }

    // ---------------------------------------------------------------- physics plumbing

    private val worker by lazy { java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "cyclotron-fly").apply { isDaemon = true } } }
    private var job: java.util.concurrent.Future<Pair<Cyclotron, Track>>? = null

    private fun fly(sp: com.bytedance.physicslab.solar.core.cyclotron.Species, b: Double, u: Double, detune: Double): Pair<Cyclotron, Track> {
        val c = Cyclotron(sp, b, u, GAP, R_EXT, detune)
        return c to c.fly(stepsPerTurn = 720, maxTurns = if (detune == 1.0) MAX_TURNS else 13, exitRun = EXIT_RUN)
    }

    private fun spanOf(tr: Track) = maxOf(6.0, ceil(tr.tEnd / mach.trf) + 0.5)

    /** Synchronous (build time). */
    private fun rebuildPhysics() {
        val r = fly(Lorentz.SPECIES[speciesIdx], bSign * bT.toDouble(), uKV * 1e3, DETUNE[detuneIdx])
        mach = r.first; track = r.second; span = spanOf(track)
        dirty = false; sinceRebuild = 0f
    }

    /** NB tails of NT samples, concatenated into one tube mesh with fixed topology. */
    private fun mergedTails(): MeshData {
        val one = MeshGen.tube(FloatArray(NT * 3) { if (it % 3 == 0) it * 1e-4f else 0f }, 0.0045f, 5, false)
        tailVerts = one.vertexCount
        val pos = FloatArray(one.pos.size * NB); val nrm = FloatArray(one.nrm.size * NB); val uv = FloatArray(one.uv.size * NB)
        val idx = IntArray(one.idx.size * NB)
        for (k in 0 until NB) {
            System.arraycopy(one.pos, 0, pos, k * one.pos.size, one.pos.size); System.arraycopy(one.nrm, 0, nrm, k * one.nrm.size, one.nrm.size)
            System.arraycopy(one.uv, 0, uv, k * one.uv.size, one.uv.size)
            for (i in one.idx.indices) idx[k * one.idx.size + i] = one.idx[i] + k * tailVerts
        }
        return MeshData(pos, nrm, uv, idx)
    }

    /** Rewrite tail [slot] in place; null parks it as a speck hidden inside the ion source. */
    private fun writeTail(slot: Int, path: FloatArray?) {
        val p = path ?: FloatArray(NT * 3) { if (it % 3 == 0) it * 1e-5f else if (it % 3 == 2) -0.004f else 0f }
        val t = MeshGen.tube(p, if (path == null) 0.0005f else 0.0045f, 5, false)
        System.arraycopy(t.pos, 0, tailMesh.pos, slot * tailVerts * 3, t.pos.size)
        System.arraycopy(t.nrm, 0, tailMesh.nrm, slot * tailVerts * 3, t.nrm.size)
    }

    /** Machine-local display position of a real-space point. */
    private fun lx(x: Double) = (x * S).toFloat()

    private fun spiralPath(): FloatArray {
        val out = FloatArray(NP * 3)
        for (i in 0 until NP) {
            track.at(track.tEnd * i / (NP - 1), tmp)
            out[i * 3] = lx(tmp[0]); out[i * 3 + 1] = lx(tmp[1]); out[i * 3 + 2] = 0f
        }
        return out
    }

    private fun applyLook() {
        val c = beam
        spiral.setMat(Mat.Unlit(c, 0.42f, blend = Blend.ADD))
        tails.setMat(Mat.Unlit(c, 0.85f, blend = Blend.ADD))
        for (k in 0 until NB) halos[k]!!.setMat(Mat.Unlit(c, 0.40f, blend = Blend.ADD))
        eCurveNode.setMat(Mat.Unlit(c, 0.95f, blend = Blend.ADD))
        for (k in 0 until NX) { eDots[k]!!.setMat(Mat.Unlit(c)); xDots[k]!!.setMat(Mat.Unlit(c)) }
        targetGlow.setMat(Mat.Unlit(c, 0.5f, blend = Blend.ADD))
        for (d in dots) d.visible = bSign > 0
        for (x in crosses) x.visible = bSign < 0
        MeshGen.tubeInto(spiralMesh, spiralPath(), 0.0022f, 5, false); meshEdited(spiralMesh)
        // the target sits where the extraction channel ends
        val n = track.n - 1
        target.pos = P3(lx(track.x[n]), lx(track.y[n]), 0f)
        target.visible = track.extracted
        writeScope()
    }

    private fun writeScope() {
        val tr = track; val trf = mach.trf
        val z = CH_Z + 0.002f
        val uPath = FloatArray(NC * 3); val ePath = FloatArray(NC * 3)
        val amp = CH_UA * (uKV / 100f)
        for (i in 0 until NC) {
            val tau = span * trf * i / (NC - 1)
            val x = CH_X0 + CH_W * i / (NC - 1)
            uPath[i * 3] = x; uPath[i * 3 + 1] = CH_UY + amp * (mach.gapVoltage(tau) / mach.u0).toFloat(); uPath[i * 3 + 2] = z
            tr.at(minOf(tau, tr.tEnd), tmp)
            ePath[i * 3] = minOf(x, xOfTau(tr.tEnd)); ePath[i * 3 + 1] = eY(tmp[2]); ePath[i * 3 + 2] = z + 0.001f
        }
        MeshGen.tubeInto(uCurve, uPath, 0.0022f, 5, false); meshEdited(uCurve)
        MeshGen.tubeInto(eCurve, ePath, 0.0026f, 5, false); meshEdited(eCurve)
        val cr = tr.crossings
        for (k in 0 until NX) {
            val on = k < cr.size && cr[k].t <= span * trf && (!tr.extracted || cr[k].t <= tr.extractIdx * tr.dt)
            xDots[k]!!.visible = on; eDots[k]!!.visible = on
            if (!on) continue
            val tc = cr[k].t - 0.5 * GAP / maxOf(1e5, speedAt(cr[k].t))
            val x = xOfTau(tc)
            xDots[k]!!.pos = P3(x, CH_UY + amp * (mach.gapVoltage(tc) / mach.u0).toFloat(), z + 0.004f)
            tr.at(cr[k].t, tmp)
            eDots[k]!!.pos = P3(xOfTau(cr[k].t), eY(tmp[2]), z + 0.004f)
        }
        val ef = eY(mach.eFinal)
        eFinalLine.pos = P3(0f, ef, 0f)
        eFinalLine.visible = mach.eFinal / Phys.MEV < E_SCALE_MEV
    }

    private fun speedAt(t: Double): Double {
        track.at(t, tmp)
        val sp = mach.sp
        val g = 1 + tmp[2] / (sp.m * Phys.C * Phys.C)
        return Phys.C * kotlin.math.sqrt(1 - 1 / (g * g))
    }

    private fun xOfTau(tau: Double) = CH_X0 + CH_W * (tau / (span * mach.trf)).toFloat().coerceIn(0f, 1f)
    private fun eY(ej: Double) = CH_EY0 + CH_EH * (ej / Phys.MEV / E_SCALE_MEV).toFloat().coerceIn(0f, 1.05f)

    // ---------------------------------------------------------------- per frame

    override fun update(dt: Float) {
        sinceRebuild += dt
        // parameter changes: fly the new trajectory on a worker (tens of ms on the headset), adopt it when done
        if (dirty && job == null && sinceRebuild > 0.10f) {
            val sp = Lorentz.SPECIES[speciesIdx]; val b = bSign * bT.toDouble(); val u = uKV * 1e3; val dn = DETUNE[detuneIdx]
            dirty = false; sinceRebuild = 0f
            job = worker.submit<Pair<Cyclotron, Track>> { fly(sp, b, u, dn) }
        }
        job?.let { j -> if (j.isDone) { job = null; val r = j.get(); mach = r.first; track = r.second; span = spanOf(track); applyLook() } }
        theta += dt / SEC_PER_TURN[speedIdx]
        val tr = track; val trf = mach.trf
        val flightRf = tr.tEnd / trf                     // flight length in RF periods

        // bunches: one leaves the source every RF period, all on the same trajectory
        val kNew = floor(theta).toLong()
        var slot = 0
        var k = kNew
        while (slot < NB && theta - k <= flightRf) {
            val tau = (theta - k) * trf
            tr.at(tau, tmp)
            val p = P3(lx(tmp[0]), lx(tmp[1]), 0f)
            beads[slot]!!.pos = p; halos[slot]!!.pos = p
            beads[slot]!!.visible = true; halos[slot]!!.visible = true
            val path = FloatArray(NT * 3)
            for (j in 0 until NT) {
                val tj = maxOf(0.0, tau - 0.24 * trf * j / (NT - 1))
                tr.at(tj, tmp)
                path[j * 3] = lx(tmp[0]); path[j * 3 + 1] = lx(tmp[1]); path[j * 3 + 2] = 0f
            }
            if (tau < 0.24 * trf) path[(NT - 1) * 3] += 1e-4f      // keep the tube non-degenerate
            writeTail(slot, path)
            slot++; k--
        }
        for (s in slot until NB) { beads[s]!!.visible = false; halos[s]!!.visible = false; writeTail(s, null) }
        meshEdited(tailMesh)

        // the dees swap polarity with the RF: E_x = U/g, so U > 0 means the LEFT dee is at +U/2
        val u = mach.gapVoltage((theta - kNew) * trf) / mach.u0
        val a = (0.15 + 0.75 * abs(u)).toFloat()
        rim[1]!!.setMat(Mat.Unlit(if (u > 0) PLUS else MINUS, a, blend = Blend.ADD))     // Dee1 = left (rotated 180)
        rim[0]!!.setMat(Mat.Unlit(if (u > 0) MINUS else PLUS, a, blend = Blend.ADD))

        // the target flashes as each bunch arrives
        if (tr.extracted) {
            val since = ((theta - flightRf) % 1.0 + 1.0) % 1.0     // RF periods since the last bunch arrived
            flash = if (since in 0.0..0.5) (1 - since / 0.5).toFloat() else 0f
            targetGlow.pos = target.pos
            targetGlow.uniformScale(0.012f + 0.022f * flash)
            targetGlow.visible = flash > 0.02f
        } else targetGlow.visible = false

        // the scope cursor follows one tracer bunch (every M-th), so the dots light up as it crosses
        val mPer = ceil(flightRf).toLong() + 2
        val kTr = mPer * floor(theta / mPer).toLong()
        val tauTr = minOf((theta - kTr) * trf, tr.tEnd)
        val cx = xOfTau(tauTr)
        cursor.pos = P3(cx, (CH_EY0 + CH_UY + CH_UA) / 2, CH_Z + 0.006f)
        cursor.scale = P3(0.0012f, CH_UY + CH_UA - CH_EY0 + 0.02f, 0.0012f)
        tr.at(tauTr, tmp)
        cursorDot.pos = P3(cx, eY(tmp[2]), CH_Z + 0.008f); cursorDot.uniformScale(0.0075f)
        updateReadout(); updateLabels()
    }

    private fun halfTurnNs(i: Int): Double {
        val c = track.crossings
        return if (i + 1 < c.size) (c[i + 1].t - c[i].t) * 1e9 else Double.NaN
    }

    private fun updateReadout() {
        val sp = mach.sp; val tk = track
        val fMHz = mach.fc / 1e6
        val nCross = tk.crossings.count { !tk.extracted || it.t <= tk.extractIdx * tk.dt }
        val slow = SEC_PER_TURN[speedIdx] / mach.trf
        val i2 = 2; val iL = maxOf(3, nCross - 2)
        // each line must fit the 984 dp panel at 22 sp (UiPreviewTest fails on a wrapped readout)
        val l1 = tr("回旋频率 f = qB/2πm = %.2f MHz：与速度、半径无关 → 固定频率的电压一直同步",
            "f = qB/2πm = %.2f MHz at every speed and radius → a fixed-frequency RF stays in step").format(fMHz)
        val l2 = if (tk.extracted) {
            val e = tk.ek[tk.extractIdx]
            val rExit = kotlin.math.sqrt(2 * sp.m * e) / (sp.q * bT)          // r = p/qB at the deflector
            tr("%s  B=%.2f T  U=%.0f kV：过缝 %d 次 → r=%.1f cm 引出，E=%.2f MeV（与 U 无关）",
                "%s  B=%.2f T  U=%.0f kV: %d crossings → out at r=%.1f cm, E=%.2f MeV (not set by U)").format(
                tr(sp.name, sp.nameEn), bT, uKV, nCross, rExit * 100, e / Phys.MEV)
        } else {
            val peakTurn = tk.ek.indices.maxBy { tk.ek[it] } * tk.dt / mach.tc
            tr("%s  射频失谐 %+.0f%%：过缝时刻逐圈滑离波峰，第 %.0f 圈起减速，最高 %.2f MeV，出不去",
                "%s  RF detuned %+.0f%%: phase slips off the crest, slows from turn %.0f, peak %.2f MeV, stuck").format(
                tr(sp.name, sp.nameEn), (DETUNE[detuneIdx] - 1) * 100, peakTurn, tk.maxEk / Phys.MEV)
        }
        val c = tk.crossings
        val l3 = if (!tk.extracted) {
            tr("射频周期 %.2f ns ≠ 回旋周期 %.2f ns：每圈错开 %.0f° · 慢放 %.1e 倍",
                "RF period %.2f ns ≠ orbit %.2f ns: crossings shift %.0f° per turn · slowed %.1e×").format(
                mach.trf * 1e9, mach.tc * 1e9, abs(mach.tc / mach.trf - 1) * 360, slow)
        } else if (c.size > iL + 1) {
            val ratio = kotlin.math.sqrt(c[iL].let { tk.at(it.t, tmp); tmp[2] } / c[i2].let { tk.at(it.t, tmp); tmp[2] })
            tr("半圈用时：第 %d 次 %.2f ns，第 %d 次 %.2f ns —— 半径 ×%.1f，时间几乎不变 · 慢放 %.1e 倍",
                "Half turn: #%d %.2f ns, #%d %.2f ns — radius ×%.1f, time nearly the same · slowed %.1e×").format(
                i2 + 1, halfTurnNs(i2), iL + 1, halfTurnNs(iL), ratio, slow)
        } else tr("慢放 %.1e 倍", "Slowed %.1e×").format(slow)
        readoutText = "$l1\n$l2\n$l3"
    }

    private fun updateLabels() {
        labels.clear()
        val u = mach.u0 / 1e3
        labels += LabelSpec(tr("D 形盒 · 交变电压 ±%.0f kV", "Dees · RF voltage ±%.0f kV").format(u / 2), world(-0.20f, RD + 0.065f, 0.04f))
        labels += LabelSpec("B = %.2f T %s".format(bT, if (bSign > 0) tr("⊙ 指向你", "⊙ toward you") else tr("⊗ 背离你", "⊗ away from you")), world(0.30f, COIL_R + 0.05f, COIL_Z))
        labels += LabelSpec(tr("离子源", "Ion source"), world(0f, -0.055f, 0.03f))
        if (track.extracted) labels += LabelSpec(tr("引出 %.2f MeV", "Extracted %.2f MeV").format(track.ek[track.extractIdx] / Phys.MEV),
            world(target.pos.x, target.pos.y + 0.06f, 0.03f))
        labels += LabelSpec(tr("缝隙电压 U(t)  ● 过缝时刻", "Gap voltage U(t)  ● crossings"), P3(CH_X0 + CH_W / 2, CH_UY + CH_UA + 0.045f, CH_Z + 0.01f))
        labels += LabelSpec(tr("动能：每过缝 +qU", "Kinetic energy: +qU per crossing"), P3(CH_X0 + 0.14f, CH_EY0 + CH_EH + 0.03f, CH_Z + 0.01f))
    }

    private fun world(x: Float, y: Float, z: Float): P3 {
        val c = cos(YAW); val s = sin(YAW)
        return P3(CENTER.x + c * x + s * z, CENTER.y + y, CENTER.z - s * x + c * z)
    }

    // ---------------------------------------------------------------- guided track (导学)
    // KEY INSIGHT: in a magnetic field one lap takes T = 2πm/(qB) whatever the speed — faster particles
    // just run a bigger circle — so a FIXED-frequency voltage keeps kicking them in step while the
    // spiral widens. Detune it 4 % and the kicks slip; the exit energy (qBR)²/2m does not depend on U.

    /** The shown trajectory matches the current settings (no flight pending on the worker). */
    internal fun settled() = !dirty && job == null
    /** Energy at extraction (MeV), NaN when the beam never gets out. */
    internal fun exitMeV() = if (track.extracted) track.ek[track.extractIdx] / Phys.MEV else Double.NaN
    private fun setTuning(k: Int) { if (detuneIdx != k) { detuneIdx = k; dirty = true } }
    private var thetaAtStep = 0.0
    private var uAtStep = 60f

    override val lesson: Lesson by lazy {
        Lesson(
            listOf(
                LessonStep(
                    say = Bi("每过一次缝，质子被电压推一下，更快、圈更大。外圈的大圈转一圈用时会？", "Each gap crossing kicks the proton: faster, bigger circle. Does a big outer lap take more or less time?"),
                    options = listOf(
                        Bi("更长，圈大", "Longer: bigger circle"),
                        Bi("更短，速度快", "Shorter: it's faster"),
                        Bi("一样长", "Exactly the same"),
                    ),
                    answer = 2,
                    onEnter = { setTuning(1) },
                ),
                LessonStep(
                    say = Bi("盯住发光的珠子：它们像车轮辐条排成一线，同时过缝。看 3 圈和读数第 3 行。", "Watch the beads: lined up like a wheel spoke, they cross the gap together. Watch 3 laps and readout line 3."),
                    onEnter = { thetaAtStep = theta },
                    done = { settled() && theta - thetaAtStep >= 3.0 },
                    reveal = true,
                    insight = Bi("一样长：1.3 T 下质子每半圈都约 25 ns，半径却大了近 3 倍。T = 2πm/qB 与速度无关。", "The same: at 1.3 T every proton half-lap takes ~25 ns while the radius grows ~3×. T = 2πm/(qB) has no speed in it."),
                ),
                LessonStep(
                    say = Bi("把面板射频切到「射频 +4%」：电压比粒子转圈快 4%。看辐条和示波器上的点。", "Switch the panel to “RF +4%”: the voltage runs 4% faster than the orbit. Watch the spoke and the scope dots."),
                    done = { detuneIdx != 1 && settled() && !track.extracted },
                    autoplay = { setTuning(2) },
                    insight = Bi("失步：每圈错开 14°，几圈后反被减速，最高约 0.5 MeV，出不去。频率必须等于 qB/2πm。", "Out of step: crossings slip 14° per lap, soon it is braked — peak ~0.5 MeV, never out. The RF must equal qB/2πm."),
                ),
                LessonStep(
                    say = Bi("切回同步后，把 D 形盒电压 U 加大。质子引出时的能量会？", "Back in tune, raise the dee voltage U. The protons' exit energy will…"),
                    options = listOf(
                        Bi("变高", "Go up"),
                        Bi("不变，圈数变少", "Stay, fewer laps"),
                        Bi("变低", "Go down"),
                    ),
                    answer = 1,
                ),
                LessonStep(
                    say = Bi("点「同步」，再把「D 形盒电压 U」拖动 20 kV 以上，看读数第 2 行。", "Tap “Tuned f=qB/2πm”, then drag “Dee voltage” by 20 kV or more; watch readout line 2."),
                    onEnter = { uAtStep = uKV },
                    done = { detuneIdx == 1 && abs(uKV - uAtStep) >= 20f && settled() && track.extracted },
                    autoplay = { setTuning(1); uKV = if (uKV < 80f) 100f else 50f; dirty = true },
                    reveal = true,
                    insight = Bi("几乎不变：E = (qBR)²/2m 只看 B 和半径。U 从 60 加到 100 kV，过缝 21 次变 13 次。", "Barely changes: E = (qBR)²/2m depends on B and radius only. 60 → 100 kV: 21 crossings become 13, still ~1.2 MeV."),
                ),
            ),
            challenge = Challenge(
                say = Bi("让粒子引出时的能量超过 1.5 MeV。提示：加电压没用。", "Get the beam out with more than 1.5 MeV. Hint: more voltage won't help."),
                progress = {
                    val e = exitMeV()
                    if (e.isNaN()) tr("粒子出不去 —— 先调回同步", "Beam stuck — tune the RF first")
                    else tr("引出 %.2f MeV（目标 > 1.50 MeV）", "Out at %.2f MeV (goal > 1.50 MeV)").format(e)
                },
                done = { settled() && detuneIdx == 1 && exitMeV() > 1.5 },
                autoplay = { setTuning(1); speciesIdx = 0; bT = 1.5f; dirty = true },
            ),
        )
    }

    // ---------------------------------------------------------------- interaction

    override val tapTargets get() = listOf(
        TapTarget("IonSource", "离子源 → 换粒子", "Ion source → particle"),
        TapTarget("MagnetPSU", "磁铁电源 → B 反向", "Magnet supply → flip B"),
    )

    override fun isTarget(name: String) = name == "IonSource" || name == "MagnetPSU"

    override fun onTap(name: String) {
        when (name) {
            "IonSource" -> speciesIdx = (speciesIdx + 1) % Lorentz.SPECIES.size
            "MagnetPSU" -> bSign = -bSign
        }
        dirty = true; sinceRebuild = 1f
    }

    @Composable
    override fun Panel() {
        var sp by FieldRef(::speciesIdx)
        var si by FieldRef(::speedIdx)
        var di by FieldRef(::detuneIdx)
        var b by FieldRef(::bT)
        var u by FieldRef(::uKV)
        LabPanel(readout) {
            LabRow {
                SegmentControl {
                    Lorentz.SPECIES.forEachIndexed { k, s -> SegmentItem(selected = sp == k, onClick = { sp = k; speciesIdx = k; dirty = true }, title = { Text(tr(s.name, s.nameEn)) }) }
                }
                Spacer(Modifier.width(20.dp))
                SegmentControl {
                    SPEED_LABELS.forEachIndexed { k, l -> SegmentItem(selected = si == k, onClick = { si = k; speedIdx = k }, title = { Text(l) }) }
                }
            }
            LabSlider(tr("磁场 B", "Field B"), b, 0.8f..1.5f, "%.2f T".format(b)) { b = it; bT = it; dirty = true }
            LabSlider(tr("D 形盒电压 U", "Dee voltage"), u, 50f..100f, "%.0f kV".format(u)) { u = it; uKV = it; dirty = true }
            LabRow {
                SegmentControl {
                    DETUNE_LABELS.forEachIndexed { k, l -> SegmentItem(selected = di == k, onClick = { di = k; detuneIdx = k; dirty = true }, title = { Text(l) }) }
                }
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    private fun rx90() = Q.axisAngle(1f, 0f, 0f, (PI / 2).toFloat())
    private fun seg(x0: Float, y0: Float, x1: Float, y1: Float, z: Float, r: Float) =
        MeshGen.tube(floatArrayOf(x0, y0, z, x1, y1, z), r, 6, false)

    /** Copper winding: bright turns with dark grooves, varying along U (around the coil ring). */
    private fun stripes(w: Int, n: Int): Image {
        val img = Image(w, 2)
        for (x in 0 until w) {
            val f = 0.5f + 0.5f * cos(2 * PI.toFloat() * n * (x + 0.5f) / w)
            val v = 0.55f + 0.45f * f * f
            for (y in 0 until 2) img.px[y * w + x] = ProcTex.argb(1f, v, v, v)
        }
        return img
    }
}
