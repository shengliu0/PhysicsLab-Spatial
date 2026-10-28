// 最速降线 · Brachistochrone (Johann Bernoulli, 1696) + 等时降线 · Tautochrone (Huygens, 1659).
//
// LESSON (race): four beads threaded on four polished rods, same start, same finish, no friction.
// The straight line is the SHORTEST path and comes LAST; the winner is the cycloid, which dives
// BELOW the finish line to buy speed early. Strobe dots every 0.1 s turn time into something you
// can count on the rod. Every bead reaches the finish at the same speed sqrt(2gh) — only the time
// differs. Optional: the generating wheel, rolling at CONSTANT speed, carries the cycloid bead
// on its rim (theta = t sqrt(g/r)).
// LESSON (tautochrone): four beads on four cycloid bowls, released from heights 0.37 m down to
// 2 cm, from both sides — they all reach the bottom at the same instant pi sqrt(r/g), and meet
// there again every 2T. Each bead leaves exactly 9 strobe dots (every T/8) on its first descent.
//
// Model: beads THREADED on frictionless wires (Bernoulli's statement). They slide; they do not
// roll (see core/brachistochrone for why the Unity build's rolling balls were the wrong drawing).
// Physics: core/brachistochrone (exact polyline time tables, closed forms, exact bowl SHM).

package com.bytedance.physicslab.solar.demos.brachistochrone

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bytedance.physicslab.solar.core.P3
import com.bytedance.physicslab.solar.core.brachistochrone.Cycloid
import com.bytedance.physicslab.solar.core.brachistochrone.Descent
import com.bytedance.physicslab.solar.core.brachistochrone.Lanes
import com.bytedance.physicslab.solar.core.brachistochrone.Track
import com.bytedance.physicslab.solar.lab.About
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
import com.bytedance.physicslab.solar.lab.ui.MappedRef
import com.bytedance.physicslab.solar.lab.ui.LabPanel
import com.bytedance.physicslab.solar.lab.ui.LabRow
import com.bytedance.physicslab.solar.lab.ui.LabSlider
import com.bytedance.physicslab.solar.lab.ui.LabSwitch
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
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

object BrachistochroneDemo : SpecDemo() {
    override val id = "brachistochrone"
    override val title = "最速降线"  // i18n-ok (zh half of DemoRegistry.titleOf; English in TITLE_EN)
    override val credit = "Bernoulli · 1696"

    // ---------------------------------------------------------------- parameters
    private const val G = 9.81
    private const val RUN = 1.40
    private const val X0 = -0.72f
    private const val Y0 = 0.40f                            // start bar tops out at 0.448 < 0.45 (menu clearance)
    private const val PLINTH_Y = -0.262f
    private const val DECK = PLINTH_Y + 0.0175f            // top of the slab
    private const val ROD_R = 0.009f
    private const val BEAD_R = 0.032f
    private const val DOT_R = 0.017f
    private const val STROBE = 0.1                          // s of simulated time between dots (race)
    private const val MAX_DOTS = 32
    private const val TRAIL_N = 24
    private const val TRAIL_DT = 0.12                       // s of sim time the comet tail covers
    private const val HOLD = 3.5f                           // s (wall) the result stays before a rerun
    private const val BOWL_R = 0.20
    private const val BOARD_Z = -0.36f
    private const val BOARD_H = 0.70f
    private const val BOARD_C = DECK + BOARD_H / 2
    private const val DASHES = 28
    private const val BOWL_TOP = 0.38f

    private val SPEEDS = floatArrayOf(0.1f, 0.25f, 1f)
    private val SPEED_LABELS get() = listOf(tr("慢 ×0.1", "×0.1"), tr("中 ×0.25", "×0.25"), tr("实时 ×1", "×1"))

    // lanes: 0 cycloid (hero, front), 1 cliff, 2 Galileo arc, 3 straight line (back)
    // display names only (lanes are identified by index): NAME follows the UI language,
    // NAME_EN is the English key term the Chinese UI shows next to the Chinese name
    private val NAME get() = listOf(tr("摆线", "Cycloid"), tr("陡降", "Cliff"), tr("圆弧", "Arc"), tr("直线", "Straight"))
    private val NAME_EN = listOf("cycloid", "cliff", "arc", "line")
    private val LANE_Z = floatArrayOf(0.24f, 0.08f, -0.08f, -0.24f)
    private val HUE = listOf(Rgb(1.00f, 0.76f, 0.26f), Rgb(0.70f, 0.56f, 1.00f), Rgb(0.28f, 0.86f, 0.74f), Rgb(0.46f, 0.72f, 1.00f))

    // tautochrone release parameters (bowl theta; > pi = right-hand side), one per lane
    private val TAUTO_PRESETS = doubleArrayOf(0.45, 1.20, 1.90, 2.45, 2.85)
    private val tautoK = intArrayOf(0, 1, 2, 3)            // preset index per lane
    private val tautoRight = booleanArrayOf(false, true, false, true)

    // ---------------------------------------------------------------- live state
    var mode = 0; var speedIdx = 1; var drop = 0.35; var showWheel = false; var showDots = true
    var pick = -1
    internal var t = 0.0
    private var wall = 0f
    private var holdAt = -1f
    internal val tracks = arrayOfNulls<Track>(4)
    internal val exact = DoubleArray(4)
    private var fit = Cycloid.fit(RUN, 0.35)

    // ---------------------------------------------------------------- nodes
    private lateinit var raceRoot: Node
    private lateinit var bowlRoot: Node
    private lateinit var rodMesh: Array<MeshData>
    private lateinit var bowlMesh: MeshData
    internal lateinit var beads: Array<Node>
    private lateinit var halo: Node
    private lateinit var trailMesh: Array<MeshData>
    private lateinit var trails: Array<Node>
    private lateinit var dots: Array<Array<Node>>
    private lateinit var finishRoot: Node
    private lateinit var finishGlow: Node
    private lateinit var levelDashes: Array<Node>
    private lateinit var wheel: Node
    private lateinit var wheelRing: MeshData
    private lateinit var ceiling: Node
    private lateinit var contact: Node
    private val labelPos = Array(4) { P3(0f, 0f, 0f) }

    // ---------------------------------------------------------------- materials (sRGB)
    private val alu = Mat.Pbr(Rgb(0.80f, 0.82f, 0.85f), metallic = 1f, roughness = 0.32f)
    private val darkSteel = Mat.Pbr(Rgb(0.30f, 0.31f, 0.33f), metallic = 1f, roughness = 0.40f)
    private val slabTex by lazy {
        ProcTex.bakePlanet(256, 128, Rgb(0.07f, 0.075f, 0.085f), Rgb(0.12f, 0.125f, 0.14f), Rgb(0.20f, 0.205f, 0.22f), 0f, 77)
    }
    private val slab get() = Mat.Pbr(Rgb(1f, 1f, 1f), metallic = 0f, roughness = 0.55f, tex = slabTex)   // the pendulum bench slab: one product
    private val glass = Mat.Pbr(Rgb(0.05f, 0.06f, 0.08f), metallic = 0f, roughness = 0.08f, alpha = 0.82f)
    private val gridLine = Mat.Unlit(Rgb(0.55f, 0.70f, 0.85f), alpha = 0.16f, blend = Blend.TRANSPARENT)
    private fun rodMat(c: Rgb) = Mat.Pbr(c, metallic = 0.55f, roughness = 0.26f, emissive = dim(c, 0.22f))
    private fun beadMat(c: Rgb) = Mat.Pbr(ProcTex.okLerp(c, Rgb(1f, 1f, 1f), 0.15f), metallic = 0.05f, roughness = 0.14f, emissive = dim(c, 0.45f))

    // ---------------------------------------------------------------- build

    override fun buildSpec(cacheDir: File): Node {
        pick = -1                                           // every visit starts with the question
        val root = Node("BrachRoot")
        val unitCyl = MeshGen.cylinder(1f, 1f, 16)
        val sphere = MeshGen.sphere(1f, 18, 36, MeshGen.SphereUv.EQUIRECT)

        root.add("Plinth", MeshGen.box(1.78f, 0.035f, 0.80f), slab, P3(-0.02f, PLINTH_Y, 0f))
        // trim sits INSIDE the slab: only its 5 mm overhang shows, as an edge (it used to cover the top)
        root.add("PlinthBand", MeshGen.box(1.79f, 0.008f, 0.81f), alu, P3(-0.02f, PLINTH_Y, 0f))

        // smoked-glass backboard with a 10 cm grid: contrast against any room, and a ruler for heights
        root.add("Board", MeshGen.box(1.70f, BOARD_H, 0.012f), glass, P3(-0.02f, BOARD_C, BOARD_Z - 0.008f))
        for (k in 0..16) { val x = -0.80f + 0.1f * k; root.add("GridV", MeshGen.box(0.0016f, BOARD_H, 0.002f), gridLine, P3(x, BOARD_C, BOARD_Z)) }
        for (k in 0..6) { val y = -0.18f + 0.1f * k; root.add("GridH", MeshGen.box(1.70f, 0.0016f, 0.002f), gridLine, P3(-0.02f, y, BOARD_Z)) }
        // ================= race =================
        raceRoot = root.add(Node("Race"))
        // start tower: two posts + crossbar the four rods hang from
        for (z in floatArrayOf(-0.33f, 0.33f)) {
            raceRoot.add("StartPost", unitCyl, alu, P3(X0 - 0.045f, (DECK + Y0 + 0.04f) / 2, z)).also { it.scale = P3(0.011f, Y0 + 0.04f - DECK, 0.011f) }
            raceRoot.add("StartFoot", MeshGen.cylinder(0.032f, 0.012f, 20), darkSteel, P3(X0 - 0.045f, DECK + 0.006f, z))
        }
        raceRoot.add("StartBar", MeshGen.box(0.032f, 0.026f, 0.70f), alu, P3(X0 - 0.025f, Y0 + 0.035f, 0f))
        for (i in 0 until 4) raceRoot.add("Clamp$i", MeshGen.box(0.05f, 0.03f, 0.022f), darkSteel, P3(X0 - 0.005f, Y0 + 0.022f, LANE_Z[i]))

        // finish frame (moves with the drop)
        finishRoot = raceRoot.add(Node("Finish"))
        for (z in floatArrayOf(-0.33f, 0.33f)) {
            finishRoot.add(if (z < 0f) "FinPostBack" else "FinPostFront", unitCyl, alu, P3(0f, 0f, z))  // SDK names: [A-Za-z0-9_] only
            finishRoot.add("FinFoot", MeshGen.cylinder(0.032f, 0.012f, 20), darkSteel, P3(0.015f, DECK + 0.006f, z))
        }
        finishRoot.add("FinBar", MeshGen.box(0.026f, 0.022f, 0.70f), alu, P3(0f, 0f, 0f))
        finishGlow = finishRoot.add("FinGlow", MeshGen.box(0.008f, 0.008f, 0.66f), Mat.Unlit(Rgb(1f, 0.92f, 0.62f), alpha = 0.35f, blend = Blend.ADD), P3(0f, 0f, 0f))

        levelDashes = Array(DASHES) { raceRoot.add("Level", MeshGen.box(0.026f, 0.004f, 0.003f), Mat.Unlit(Rgb(1f, 0.85f, 0.45f), alpha = 0.55f, blend = Blend.ADD)) }
        rodMesh = Array(4) { MeshGen.tube(FloatArray(Lanes.N * 3), ROD_R, 8, false) }
        for (i in 0 until 4) raceRoot.add("Rod$i", rodMesh[i], rodMat(HUE[i]))

        beads = Array(4) { i ->
            root.add("Bead$i", sphere, beadMat(HUE[i])).also { it.uniformScale(BEAD_R); it.tapRadius = maxOf(1.75f, 0.045f / BEAD_R) }
        }
        halo = root.add("Halo", sphere, Mat.Unlit(Rgb(1f, 1f, 1f), alpha = 0.22f, blend = Blend.ADD)).also { it.uniformScale(BEAD_R * 1.75f); it.visible = false }
        trailMesh = Array(4) { MeshGen.tube(FloatArray(TRAIL_N * 3), 0.011f, 8, false) }
        trails = Array(4) { i -> root.add("Trail$i", trailMesh[i], Mat.Unlit(HUE[i], alpha = 0.55f, blend = Blend.ADD)) }
        dots = Array(4) { i ->
            Array(MAX_DOTS) { root.add("Dot$i", sphere, Mat.Unlit(ProcTex.okLerp(HUE[i], Rgb(1f, 1f, 1f), 0.35f), alpha = 0.75f, blend = Blend.TRANSPARENT)).also { it.uniformScale(DOT_R); it.visible = false } }
        }

        // the generating wheel (optional overlay, rides in front of the cycloid lane)
        wheelRing = MeshGen.tube(circle(1f, 72), 0.016f, 8, true)
        wheel = raceRoot.add(Node("Wheel"))
        val wheelMat = Mat.Unlit(Rgb(1f, 0.90f, 0.62f), alpha = 0.55f, blend = Blend.ADD)
        wheel.add("WheelRing", wheelRing, wheelMat)
        for (k in 0 until 6) {
            val a = (k * PI / 3).toFloat()
            wheel.add("Spoke$k", unitCyl, wheelMat, P3(-0.5f * sin(a), 0.5f * cos(a), 0f)).also {
                it.scale = P3(0.025f, 1f, 0.025f); it.rot = Q.axisAngle(0f, 0f, 1f, a)
            }
        }
        wheel.add("Hub", sphere, wheelMat).uniformScale(0.05f)
        ceiling = raceRoot.add("Ceiling", MeshGen.box(1f, 0.006f, 0.02f), Mat.Unlit(Rgb(1f, 0.90f, 0.62f), alpha = 0.45f, blend = Blend.ADD))
        contact = raceRoot.add("Contact", sphere, Mat.Unlit(Rgb(1f, 1f, 1f), alpha = 0.9f, blend = Blend.ADD)).also { it.uniformScale(0.011f) }

        // ================= tautochrone bowl =================
        bowlRoot = root.add(Node("Bowl"))
        bowlMesh = MeshGen.tube(bowlPath(), ROD_R, 8, false)
        val bowlLeft = (-PI * BOWL_R).toFloat(); val bowlRight = (PI * BOWL_R).toFloat()
        for (i in 0 until 4) bowlRoot.add("BowlRod$i", bowlMesh, rodMat(HUE[i]), P3(0f, 0f, LANE_Z[i]))
        for (x in floatArrayOf(bowlLeft - 0.03f, bowlRight + 0.03f)) {
            for (z in floatArrayOf(-0.33f, 0.33f)) {
                bowlRoot.add("BPost", unitCyl, alu, P3(x, (DECK + BOWL_TOP + 0.04f) / 2, z)).also { it.scale = P3(0.011f, BOWL_TOP + 0.04f - DECK, 0.011f) }
                bowlRoot.add("BFoot", MeshGen.cylinder(0.032f, 0.012f, 20), darkSteel, P3(x, DECK + 0.006f, z))
            }
            bowlRoot.add("BBar", MeshGen.box(0.03f, 0.026f, 0.70f), alu, P3(x, BOWL_TOP + 0.03f, 0f))
        }
        val bottomY = BOWL_TOP - 2 * BOWL_R.toFloat()
        bowlRoot.add("BottomGlow", MeshGen.box(0.008f, 0.008f, 0.66f), Mat.Unlit(Rgb(1f, 0.92f, 0.62f), alpha = 0.35f, blend = Blend.ADD), P3(0f, bottomY - ROD_R - 0.006f, 0f))

        rebuildRace()
        applyMode()
        restart()
        place()
        return root
    }

    // ---------------------------------------------------------------- geometry

    private fun lanePath(i: Int): Pair<DoubleArray, DoubleArray> = when (i) {
        0 -> Lanes.cycloid(RUN, drop)
        1 -> Lanes.cliff(RUN, drop, Lanes.filletFor(drop))
        2 -> Lanes.arc(RUN, drop)
        else -> Lanes.straight(RUN, drop)
    }

    private fun rebuildRace() {
        fit = Cycloid.fit(RUN, drop)
        val fil = Lanes.filletFor(drop)
        exact[0] = Descent.cycloid(RUN, drop, G); exact[1] = Descent.cliff(RUN, drop, fil, G)
        exact[2] = Descent.arc(RUN, drop, G); exact[3] = Descent.straight(RUN, drop, G)
        val y1 = (Y0 - drop).toFloat(); val x1 = X0 + RUN.toFloat()
        for (i in 0 until 4) {
            val (xs, ys) = lanePath(i)
            tracks[i] = Track(xs, ys, G)
            val p = FloatArray(xs.size * 3) { k ->
                val j = k / 3
                when (k % 3) { 0 -> X0 + xs[j].toFloat(); 1 -> Y0 + ys[j].toFloat(); else -> LANE_Z[i] }
            }
            MeshGen.tubeInto(rodMesh[i], p, ROD_R, 8, false); meshEdited(rodMesh[i])
        }
        // finish frame at the finish height
        finishRoot.pos = P3(x1 + 0.03f, 0f, 0f)
        for (c in finishRoot.children) when {
            c.name.startsWith("FinPost") -> { c.pos = P3(0.015f, (DECK + y1 + 0.07f) / 2, c.pos.z); c.scale = P3(0.011f, y1 + 0.07f - DECK, 0.011f) }
            c.name == "FinBar" -> c.pos = P3(0f, y1 - 0.012f, 0f)
            c.name == "FinGlow" -> c.pos = P3(-0.03f, y1, 0f)
        }
        // finish level as a dashed line on the board: the cycloid visibly dives BELOW it
        for (k in 0 until DASHES) levelDashes[k].pos = P3(X0 + 0.05f * k + 0.013f, y1, BOARD_Z + 0.004f)
        // label anchors: each where its curve is most itself, so the four never stack
        val r = fit.r.toFloat()
        labelPos[0] = P3(X0 + (PI * fit.r).toFloat(), maxOf(Y0 - 2 * r - 0.075f, DECK + 0.03f), LANE_Z[0] + 0.06f)   // under the dive
        labelPos[1] = P3(X0 - 0.17f, Y0 - 0.5f * drop.toFloat(), LANE_Z[1])                              // beside the drop
        val (ax, ay) = lanePath(2); val xa = RUN * 0.62
        labelPos[2] = P3(X0 + xa.toFloat(), Y0 + yAtX(ax, ay, xa).toFloat() - 0.06f, LANE_Z[2])
        labelPos[3] = P3(X0 + (RUN * 0.42).toFloat(), Y0 - (0.42 * drop).toFloat() + 0.075f, LANE_Z[3])   // above the chord
        // the wheel: scaled unit ring, ceiling line from the start to the last contact point
        wheel.uniformScale(r)
        val ceilLen = (fit.r * fit.thetaEnd).toFloat() + 0.06f
        ceiling.pos = P3(X0 + ceilLen / 2 - 0.03f, Y0 + 0.004f, LANE_Z[0] + 0.035f)
        ceiling.scale = P3(ceilLen, 1f, 1f)
    }

    private fun yAtX(xs: DoubleArray, ys: DoubleArray, x: Double): Double {
        for (k in 1 until xs.size) if (xs[k] >= x && xs[k] > xs[k - 1]) {
            val f = (x - xs[k - 1]) / (xs[k] - xs[k - 1]); return ys[k - 1] + (ys[k] - ys[k - 1]) * f
        }
        return ys.last()
    }

    /** One cycloid bowl, centred on x = 0, cusps at BOWL_TOP. */
    private fun bowlPath(n: Int = 200): FloatArray {
        val out = FloatArray(n * 3)
        for (k in 0 until n) {
            val th = Cycloid.thetaAtArc(BOWL_R, 8 * BOWL_R * k / (n - 1))
            out[k * 3] = (Cycloid.x(BOWL_R, th) - PI * BOWL_R).toFloat()
            out[k * 3 + 1] = BOWL_TOP + Cycloid.y(BOWL_R, th).toFloat()
        }
        return out
    }

    private fun bowlPoint(th: Double) = P3((Cycloid.x(BOWL_R, th) - PI * BOWL_R).toFloat(), BOWL_TOP + Cycloid.y(BOWL_R, th).toFloat(), 0f)
    private fun tautoTheta0(i: Int): Double { val a = TAUTO_PRESETS[tautoK[i]]; return if (tautoRight[i]) 2 * PI - a else a }
    private fun tautoHeight(i: Int): Double { val c = cos(tautoTheta0(i) / 2); return 2 * BOWL_R * c * c }
    private val bowlT get() = Cycloid.tautochroneTime(BOWL_R, G)

    private fun applyMode() {
        raceRoot.visible = mode == 0
        bowlRoot.visible = mode == 1
        wheel.visible = mode == 0 && showWheel
        ceiling.visible = wheel.visible; contact.visible = wheel.visible
        for (i in 0 until 4) {            // beads live under raceRoot; in bowl mode reparent visually by position
            beads[i].visible = true
        }
        if (mode == 1) { for (i in 0 until 4) bowlBead(i) }
    }

    // ---------------------------------------------------------------- per frame

    override fun update(dt: Float) {
        wall += dt
        t += dt * SPEEDS[speedIdx]
        if (mode == 0) {
            val tEnd = exact.max()
            if (t >= tEnd) {
                if (holdAt < 0) holdAt = wall
                else if (wall - holdAt > HOLD) restart()
            }
        }
        place()
        updateReadout()
        updateLabels()
    }

    private val st = DoubleArray(2)
    private val pe = DoubleArray(4)

    private fun place() {
        raceRoot.visible = mode == 0; bowlRoot.visible = mode == 1
        if (mode == 0) placeRace() else placeBowl()
    }

    private fun lanePos(i: Int, s: Double): P3 {
        tracks[i]!!.eval(s, pe)
        return P3(X0 + pe[0].toFloat(), Y0 + pe[1].toFloat(), LANE_Z[i])
    }

    private fun placeRace() {
        for (i in 0 until 4) {
            val tr = tracks[i]!!
            tr.stateAt(t, st)
            val p = lanePos(i, st[0])
            beads[i].pos = p
            // comet tail: the last TRAIL_DT of path, sampled in time so it stretches with speed
            // (re-meshed only while it is visible: 4 small meshes, never once the race is over)
            trails[i].visible = t > 0 && t < tr.time + TRAIL_DT
            if (trails[i].visible) {
                val path = FloatArray(TRAIL_N * 3)
                for (k in 0 until TRAIL_N) {
                    val tk = (t - TRAIL_DT * (1 - k / (TRAIL_N - 1.0))).coerceIn(0.0, tr.time)
                    val q = lanePos(i, tr.sAt(tk))
                    path[k * 3] = q.x; path[k * 3 + 1] = q.y; path[k * 3 + 2] = q.z
                }
                MeshGen.tubeInto(trailMesh[i], path, 0.011f, 8, false); meshEdited(trailMesh[i])
            }
            // strobe dots: where the bead was at 0, 0.1, 0.2 ... s (count them = the time)
            for (k in 0 until MAX_DOTS) {
                val tk = k * STROBE
                val on = showDots && tk <= t && tk <= tr.time
                dots[i][k].visible = on
                if (on) dots[i][k].pos = lanePos(i, tr.sAt(tk))
            }
        }
        val done = exact.max()
        finishGlow.setMat(Mat.Unlit(Rgb(1f, 0.92f, 0.62f), alpha = if (exact.any { t >= it && t < it + 0.08 }) 1.0f else 0.35f, blend = Blend.ADD))
        if (t >= done) finishGlow.setMat(Mat.Unlit(Rgb(1f, 0.92f, 0.62f), alpha = 0.6f, blend = Blend.ADD))
        halo.visible = pick in 0..3
        if (pick in 0..3) halo.pos = beads[pick].pos
        // the generating wheel: theta = omega t exactly, rim point = the cycloid bead
        val vis = showWheel
        wheel.visible = vis; ceiling.visible = vis; contact.visible = vis
        if (vis) {
            val th = (Cycloid.omega(fit.r, G) * t).coerceAtMost(fit.thetaEnd)
            val cx = X0 + (fit.r * th).toFloat()
            wheel.pos = P3(cx, Y0 - fit.r.toFloat(), LANE_Z[0] + 0.035f)
            wheel.rot = Q.axisAngle(0f, 0f, 1f, th.toFloat())
            contact.pos = P3(cx, Y0, LANE_Z[0] + 0.035f)
        }
    }

    private fun bowlBead(i: Int) {
        val th = Cycloid.bowlTheta(BOWL_R, tautoTheta0(i), G, t)
        beads[i].pos = bowlPoint(th).let { P3(it.x, it.y, LANE_Z[i]) }
    }

    private fun placeBowl() {
        val T = bowlT
        for (i in 0 until 4) {
            bowlBead(i)
            trails[i].visible = false
            // first descent: 9 dots at k T/8 — the SAME count on every bowl = the same time
            for (k in 0 until MAX_DOTS) {
                val tk = k * T / 8
                val on = showDots && k <= 8 && tk <= t
                dots[i][k].visible = on
                if (on) dots[i][k].pos = bowlPoint(Cycloid.bowlTheta(BOWL_R, tautoTheta0(i), G, tk)).let { P3(it.x, it.y, LANE_Z[i]) }
            }
        }
        halo.visible = false
        val phase = (t / T)                       // bottom at odd multiples of T
        val near = kotlin.math.abs(phase - (2 * floor((phase + 1) / 2) - 1)) * T < 0.012
        bowlRoot.find("BottomGlow")?.setMat(Mat.Unlit(Rgb(1f, 0.92f, 0.62f), alpha = if (near && t > 0) 1.0f else 0.35f, blend = Blend.ADD))
    }

    private fun rank(i: Int) = 1 + (0 until 4).count { exact[it] < exact[i] }

    private fun updateReadout() {
        val sp = SPEED_LABELS[speedIdx]
        readoutText = if (mode == 0) {
            val vF = Descent.finishSpeed(drop, G)
            val bet = if (pick in 0..3) {
                if (t >= exact[pick]) tr(" · 你猜${NAME[pick]}：第 ${rank(pick)} 名", " · your guess ${NAME[pick]}: #${rank(pick)}")
                else tr(" · 你猜${NAME[pick]}…", " · your guess: ${NAME[pick]}…")
            } else ""
            // predict -> observe -> explain: the lesson line would give the answer away, so until the
            // user has guessed (and the race is over) the first line asks the question instead
            (if (pick < 0 && t < exact.max()) tr("先猜：同一起点、同一终点，哪颗珠子最先到？捏住它，或点下方「我猜…」\n",
                "Guess first: same start, same finish — which bead wins? Pinch it, or tap “Guess” below\n")
            else tr("最速降线：直线最短，却不是最快 —— 摆线先俯冲到终点下方换速度，最快\n",
                "Brachistochrone: shortest ≠ fastest — the cycloid dives below the finish to gain speed\n")) +
                tr("精确用时  摆线 %.3f s  ·  陡降 %.3f s  ·  圆弧 %.3f s  ·  直线 %.3f s\n",
                    "Exact times  cycloid %.3f s  ·  cliff %.3f s  ·  arc %.3f s  ·  straight %.3f s\n").format(exact[0], exact[1], exact[2], exact[3]) +
                tr("t = %.2f s · 落差 %.2f m · 无摩擦，末速度都 = √(2gh) = %.2f m/s · %s%s",
                    "t = %.2f s · drop %.2f m · no friction: all finish at √(2gh) = %.2f m/s · speed %s%s").format(minOf(t, exact.max()), drop, vF, sp, bet)
        } else {
            val T = bowlT
            val meets = if (t < T) 0 else 1 + floor((t - T) / (2 * T)).toInt()
            tr("等时降线（惠更斯 1659）：摆线碗里从任何高度松手，到谷底用时都一样\n",
                "Tautochrone (Huygens 1659): from any height in a cycloid bowl, same time to the bottom\n") +
                tr("r = %.2f m → T = π√(r/g) = %.3f s · 起点高度 %.2f / %.2f / %.2f / %.2f m\n",
                    "r = %.2f m → T = π√(r/g) = %.3f s · start heights %.2f / %.2f / %.2f / %.2f m\n").format(
                    BOWL_R, T, tautoHeight(0), tautoHeight(1), tautoHeight(2), tautoHeight(3)) +
                tr("t = %.2f s · 四颗珠子在谷底同时相遇 %d 次（每 2T = %.3f s）· %s",
                    "t = %.2f s · all four beads met at the bottom: %d× (every 2T = %.3f s) · speed %s").format(t, meets, 2 * T, sp)
        }
    }

    private fun updateLabels() {
        labels.clear()
        if (mode == 0) {
            for (i in 0 until 4) {
                val txt = if (t >= exact[i]) "%s %s  %.3f s".format("①②③④"[rank(i) - 1], NAME[i], exact[i])
                          else tr("%s %s".format(NAME[i], NAME_EN[i]), NAME[i])
                labels += LabelSpec(txt, labelPos[i])
            }
            val verdict = if (pick in 0..3 && t >= exact.max()) {
                if (rank(pick) == 1) tr("\n你猜${NAME[pick]} —— 猜对了！✓", "\nYou guessed ${NAME[pick]} — right! ✓")
                else tr("\n你猜${NAME[pick]}：第 ${rank(pick)} 名 · 最快的是${NAME[0]}", "\nYou guessed ${NAME[pick]}: #${rank(pick)} · fastest: ${NAME[0]}")
            } else ""
            labels += LabelSpec(tr("终点 · 末速度都是 %.2f m/s", "Finish · all arrive at %.2f m/s").format(Descent.finishSpeed(drop, G)) + verdict, P3(X0 + RUN.toFloat() + 0.06f, (Y0 - drop).toFloat() + 0.12f, 0.36f))
            labels += LabelSpec(tr("光点每 0.1 s 一颗：点数 = 用时", "A dot every 0.1 s: dots = time"), P3(-0.05f, DECK + 0.05f, 0.42f), visible = showDots)
        } else {
            val T = bowlT
            for (i in 0 until 4) {
                val p = bowlPoint(tautoTheta0(i))
                val txt = if (t >= T) "h %.2f m → %.3f s".format(tautoHeight(i), T) else "h %.2f m".format(tautoHeight(i))
                labels += LabelSpec(txt, P3(p.x + if (tautoRight[i]) 0.10f else -0.10f, p.y + 0.05f, LANE_Z[i]))
            }
            labels += LabelSpec(tr("谷底 · 同时到达", "Bottom · all arrive together"), P3(0f, BOWL_TOP - 2 * BOWL_R.toFloat() - 0.07f, 0.36f))
            labels += LabelSpec(tr("每颗珠子都留下 9 个光点：间隔 T/8", "Every bead leaves 9 dots, T/8 apart"), P3(0f, BOWL_TOP + 0.05f, -0.30f), visible = showDots)
        }
    }

    // ---------------------------------------------------------------- interaction

    override val tapTargets get() = if (mode == 0) listOf(
        TapTarget("Bead0", "我猜摆线最快", "Guess: cycloid"), TapTarget("Bead1", "我猜陡降最快", "Guess: cliff"),
        TapTarget("Bead2", "我猜圆弧最快", "Guess: arc"), TapTarget("Bead3", "我猜直线最快", "Guess: line"),
    ) else listOf(
        TapTarget("Bead0", "珠 1 → 换高度", "Bead 1 → height"), TapTarget("Bead1", "珠 2 → 换高度", "Bead 2 → height"),
        TapTarget("Bead2", "珠 3 → 换高度", "Bead 3 → height"), TapTarget("Bead3", "珠 4 → 换高度", "Bead 4 → height"),
    )

    override fun isTarget(name: String) = name.startsWith("Bead")

    // ---------------------------------------------------------------- guided track (导学)

    private var dropAtStep = 0.0
    private fun raceOver() = mode == 0 && t >= exact.max()
    private fun bowlMeets(): Int { val T = bowlT; return if (mode != 1 || t < T) 0 else 1 + floor((t - T) / (2 * T)).toInt() }

    // ---- background card (📖): context for someone who assumes the straight line is fastest
    override val about = About(listOf(
        About.Page(
            Bi("这个实验在做什么？", "What is this experiment?"),
            Bi("四根光滑的轨道，起点、终点都相同：摆线、陡降、圆弧、直线，每根上穿着一颗珠子，没有摩擦。先捏你猜最快的那颗珠子，比赛重新开始；频闪光点每 0.1 秒留一颗，数点就是数时间。拖「落差 h」改变终点高度。切到「等时」模式，四颗珠子从不同高度滑进摆线形的碗。", "Four polished rods share one start and one finish: cycloid, cliff, arc and straight line, each threading a frictionless bead. Pinch the bead you think is fastest and the race restarts; strobe dots fall every 0.1 s, so counting dots counts time. Drag “Drop h” to move the finish. Switch to “Tautochrone” and four beads slide into cycloid bowls from different heights."),
        ),
        About.Page(
            Bi("一道公开挑战题", "A public challenge"),
            Bi("1696 年，约翰·伯努利在期刊上向全欧洲的数学家出了一道题：一颗珠子只靠重力，从一点滑到另一点（不在正下方），走哪条曲线最快？答案出人意料：不是直线。第二年，他本人、他哥哥雅各布、莱布尼茨、牛顿等人都交了答案——摆线。为解这类“找最好的曲线”的问题，后来发展出了一门新数学：变分法。", "In 1696 Johann Bernoulli set a problem for the mathematicians of Europe in a journal: a bead slides under gravity alone from one point to another, not directly below. Which curve is fastest? Surprisingly, not the straight line. The next year answers came from Bernoulli himself, his brother Jakob, Leibniz, Newton and others: the cycloid. Problems like this — find the best curve — grew into new mathematics, the calculus of variations."),
        ),
        About.Page(
            Bi("先俯冲，再赶路", "Dive first, then travel"),
            Bi("摆线是车轮滚动时，轮缘上一点画出的曲线（打开「滚轮作图」就能看到）。它一开始几乎竖直往下，早早把高度换成速度；路虽然更长，后面却一路都快。就像赶路时先绕几步上快速路，反而比挤近路早到。没有摩擦时，所有珠子到终点的速度都一样，差的只是用时。摆线碗还有个本事：从任何高度松手，到底的时间都一样（惠更斯，1659）。", "A cycloid is the curve traced by a point on a rolling wheel's rim (turn on “Rolling wheel”). It starts almost straight down, turning height into speed early: a longer path, but faster all the way — like taking the motorway instead of the short, slow street. Without friction every bead finishes at the same speed; only the time differs. And in a cycloid bowl, the trip to the bottom takes the same time from any height (Huygens, 1659)."),
        ),
        About.Page(
            Bi("今天在哪里？", "Where is it today?"),
            Bi("惠更斯把等时曲线用在摆钟上，想让摆幅变化不影响走时。伯努利这道题开创的变分法，今天用来规划火箭入轨、无人机和机器人的运动轨迹，问的都是“哪条路最省时、最省力”。滑梯和过山车常常一开头就很陡，也是“先换来速度”的道理。最值得记住的一句：最短的路，不一定最快。", "Huygens used the tautochrone in pendulum clocks, so the size of the swing would not change the timing. The calculus of variations born from Bernoulli's puzzle now plans rocket ascents and the paths of drones and robots — all asking which route is quickest or cheapest. Slides and roller coasters often start steep for the same reason: get speed early. The line to remember: the shortest path is not always the fastest."),
        ),
    ))

    override val lesson: Lesson by lazy {
        Lesson(
            listOf(
                LessonStep(
                    say = Bi("四颗珠子从同一高度出发，滑到同一个终点。你猜哪一条轨道最快？", "Four beads start at the same height and slide to the same finish. Which track is fastest?"),
                    options = listOf(Bi("直线（最短）", "Straight (shortest)"), Bi("圆弧", "Arc"), Bi("陡降", "Cliff"), Bi("摆线", "Cycloid")),
                    answer = 3,
                ),
                LessonStep(
                    say = Bi("捏住你猜的那颗珠子（或点下方「我猜…」），比赛重新开始。", "Pinch the bead you picked (or tap “Guess” below) — the race restarts."),
                    target = "Bead0",
                    done = { mode == 0 && pick >= 0 },
                    autoplay = { selectMode(0); onTap("Bead0") },
                ),
                LessonStep(
                    say = Bi("盯住终点：谁第一个到？", "Watch the finish line: who arrives first?"),
                    done = { raceOver() },
                    reveal = true,
                    insight = Bi("摆线最快：它先陡降，早早把高度换成速度，虽然路更长，却一路都更快。", "The cycloid wins: it drops steeply first, turning height into speed early — a longer path, but faster all the way."),
                ),
                LessonStep(
                    say = Bi("拖动「落差 h」改变终点高度，再看一场：摆线是否还是最快？", "Change the drop h with its slider and watch again: is the cycloid still fastest?"),
                    onEnter = { dropAtStep = drop },
                    done = { kotlin.math.abs(drop - dropAtStep) > 0.02 && raceOver() },
                    autoplay = { applyDrop(if (drop < 0.4) 0.45 else 0.25) },
                    insight = Bi("无论落差多少，摆线都最快 —— 这就是约翰·伯努利 1696 年那道挑战题的答案。", "Whatever the drop, the cycloid wins — the answer to Johann Bernoulli's 1696 challenge."),
                ),
                LessonStep(
                    say = Bi("切到「等时」模式：四颗珠子从不同高度同时松手。", "Switch to “Tautochrone”: four beads released from different heights."),
                    done = { bowlMeets() >= 1 },
                    autoplay = { selectMode(1) },
                    insight = Bi("它们同时到达谷底！摆线还是等时曲线（惠更斯 1659）：从哪里松手，用时都一样。", "They reach the bottom together! The cycloid is also the tautochrone (Huygens 1659): same time from any height."),
                ),
            ),
            challenge = Challenge(
                say = Bi("捏一颗珠子换个起点高度，看它们还能否连续相遇 3 次。", "Pinch a bead to change its start height — do they still meet 3 times in a row?"),
                progress = { tr("相遇 %d / 3 次", "Met %d / 3 times").format(bowlMeets().coerceAtMost(3)) },
                done = { bowlMeets() >= 3 },
                autoplay = { if (mode != 1) selectMode(1); onTap("Bead2") },
            ),
        )
    }

    override fun tapMessage(name: String): String? {
        val i = name.removePrefix("Bead").toIntOrNull() ?: return null
        return if (mode == 0) tr("你猜${NAME[i]}最快 —— 重新开赛，看谁先到终点！", "Your guess: ${NAME[i]} — race restarted, watch the finish!")
        else tr("珠 ${i + 1} 起点高度 → %.2f m（到谷底时间不变）", "Bead ${i + 1} start height → %.2f m (same time to the bottom)").format(tautoHeight(i))
    }

    /** Race: gaze at a bead + pinch = guess that lane wins, and rerun. Bowl: change its release height. */
    override fun onTap(name: String) {
        val i = name.removePrefix("Bead").toIntOrNull() ?: return
        if (mode == 0) pick = i
        else tautoK[i] = (tautoK[i] + 1) % TAUTO_PRESETS.size
        restart()
    }

    fun restart() { t = 0.0; holdAt = -1f }

    fun selectMode(m: Int) { mode = m; applyMode(); restart(); place() }
    fun applyDrop(d: Double) { drop = d.coerceIn(0.20, 0.50); rebuildRace(); restart(); place() }

    @Composable
    override fun Panel() {
        var m by FieldRef(::mode)
        var si by FieldRef(::speedIdx)
        var d by MappedRef(::drop, { it.toFloat() }, { it.toDouble() })
        var wh by FieldRef(::showWheel)
        var dt by FieldRef(::showDots)
        LabPanel(readout) {
            LabRow {
                SegmentControl {
                    SegmentItem(selected = m == 0, onClick = { m = 0; selectMode(0) }, title = { Text(tr("竞速 Race", "Race")) })
                    SegmentItem(selected = m == 1, onClick = { m = 1; selectMode(1) }, title = { Text(tr("等时 Tautochrone", "Tautochrone")) })
                }
                Spacer(Modifier.width(20.dp))
                SegmentControl {
                    SPEED_LABELS.forEachIndexed { k, l -> SegmentItem(selected = si == k, onClick = { si = k; speedIdx = k }, title = { Text(l) }) }
                }
            }
            if (m == 0) LabSlider(tr("落差 h", "Drop h"), d, 0.20f..0.50f, "%.2f m".format(d)) { d = it; applyDrop(it.toDouble()) }
            LabRow {
                if (m == 0) LabSwitch(tr("滚轮作图", "Rolling wheel"), wh) { wh = it; showWheel = it }
                LabSwitch(tr("频闪光点", "Strobe dots"), dt) { dt = it; showDots = it }
                Button(onClick = { restart() }) { Text(if (m == 0) tr("再比一次", "Race again") else tr("同时松手", "Release all")) }
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    private fun dim(c: Rgb, k: Float) = Rgb(c.r * k, c.g * k, c.b * k)
    private fun circle(r: Float, n: Int) = FloatArray(n * 3) { k ->
        val a = 2 * PI * (k / 3) / n
        when (k % 3) { 0 -> (r * cos(a)).toFloat(); 1 -> (r * sin(a)).toFloat(); else -> 0f }
    }
    @Suppress("unused") private fun len(a: P3) = sqrt(a.x * a.x + a.y * a.y + a.z * a.z)
}
