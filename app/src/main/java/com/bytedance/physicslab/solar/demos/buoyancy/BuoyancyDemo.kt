// 阿基米德浮力 · Archimedes (c. 250 BC) — the overflow-can experiment, in 3D.
//
// LESSON: hang a cube on a spring scale and lower it into a can filled exactly to its spout. The
// scale reading drops (F_scale = G - F_buoy), water pours from the spout into a beaker, and the
// water in the beaker weighs exactly what the cube lost: F_buoy = G_overflow = rho_fluid g V_sub.
// Floaters (wood, ice; nylon only in brine) go slack on the string and float with the fraction
// rho_obj/rho_fluid under the surface — and then the beaker holds THEIR OWN weight of water.
// On the right, four columns (G − F示 = F浮 = G排) make the bookkeeping visible from across the room;
// the glyphs between them light up once everything has settled.
//
// Physics: core/buoyancy (implicit free surface, spring scale, drag + radiation damping, spout).

package com.bytedance.physicslab.solar.demos.buoyancy

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bytedance.physicslab.solar.core.P3
import com.bytedance.physicslab.solar.core.buoyancy.Buoy
import com.bytedance.physicslab.solar.core.buoyancy.OverflowRig
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
import kotlin.math.sqrt

object BuoyancyDemo : SpecDemo() {
    override val id = "buoyancy"
    override val title = "阿基米德浮力"  // i18n-ok (zh half of DemoRegistry.titleOf; English in TITLE_EN)
    override val credit: String get() = tr("Archimedes · 前250", "Archimedes · c. 250 BC")

    // ---------------------------------------------------------------- geometry (metres)
    private const val PLINTH_Y = -0.262f
    private const val DECK = PLINTH_Y + 0.0175f
    private const val A = 0.14                       // cube edge (2.74 L: big enough to read at 2.5 m)
    private const val TX = -0.40f                    // can centre x
    private const val HX = 0.24f; private const val HZ = 0.16f   // can inner half extents
    private const val FLOOR = -0.215                 // can inner floor
    private const val SPOUT = 0.065                  // spout lip = full level (28 cm of liquid)
    private const val RIM = 0.10f
    private const val WALL = 0.008f
    private const val BX = -0.062f                   // beaker centre x (under the spout outlet)
    private const val BR = 0.07f                     // beaker inner radius (3 L beaker)
    private const val BH = 0.21f                     // beaker height
    private const val OUT_Y = 0.03f                  // spout outlet (pipe turns down)
    private const val CRANE_Y = 0.43f                 // arm top 0.445 < 0.45 (menu clearance)
    private const val K_SCALE = 1000.0               // spring scale: 80 N over 8 cm
    private const val L0 = 0.11                      // hook point -> cube top, spring relaxed
    // arrows and columns are drawn to the scale of THIS object's weight (W arrow = 15 cm, G column
    // = 24 cm): the balance W = F浮 + F拉 is what must read at 2.5 m; the newtons are in the labels
    private const val ARROW_W = 0.15f
    private const val BAR_W = 0.24f
    private const val JET_N = 16
    private val COL_X = floatArrayOf(0.24f, 0.40f, 0.56f, 0.72f)
    private const val COL_W = 0.075f
    private const val START_ABOVE = 0.02             // cube bottom above the water when hanging

    private val SPEEDS = floatArrayOf(0.25f, 0.5f, 1f)
    private val SPEED_LABELS get() = listOf(tr("慢 ×0.25", "×0.25"), tr("中 ×0.5", "×0.5"), tr("实时 ×1", "×1"))

    /** Display name of a solid/liquid (core keeps both: Stuff.name = Chinese, Stuff.en = English). */
    private fun nm(s: Buoy.Stuff) = tr(s.name, s.en.replaceFirstChar { it.uppercase() })

    // ---------------------------------------------------------------- state
    var objIdx = 0; var fluidIdx = 1; var speedIdx = 1
    var lowered = true
    internal lateinit var rig: OverflowRig
    private var hookTop = 0.0
    private var settledFor = 0.0

    // ---------------------------------------------------------------- nodes
    private lateinit var water: Node
    private val meniscus = ArrayList<Node>()
    private lateinit var cube: Node
    private lateinit var vGhost: Node
    private lateinit var plate: Node
    private lateinit var spring: Node
    private lateinit var pointer: Node
    private lateinit var hookRod: Node
    private lateinit var hookBall: Node
    private lateinit var cord: Node
    private lateinit var stringMesh: MeshData
    private lateinit var stringNode: Node
    private lateinit var jetMesh: MeshData
    private lateinit var jet: Node
    private lateinit var beakerWater: Node
    private lateinit var arrows: Array<Arrow>
    private lateinit var cols: Array<Node>
    private lateinit var glyphs: Array<List<Node>>

    private class Arrow(val root: Node, val shaft: Node, val head: Node)

    // ---------------------------------------------------------------- look (sRGB)
    private val FLUID_RGB = listOf(Rgb(0.82f, 0.80f, 0.96f), Rgb(0.40f, 0.70f, 0.95f), Rgb(0.36f, 0.84f, 0.78f))
    private val FLUID_ALPHA = floatArrayOf(0.24f, 0.36f, 0.40f)
    private val C_W = Rgb(1.00f, 0.56f, 0.22f)       // weight: warm
    private val C_T = Rgb(0.90f, 0.91f, 0.95f)       // scale / string: neutral
    private val C_B = Rgb(0.25f, 1.00f, 0.62f)       // buoyancy: green, never lost against the water

    private val alu = Mat.Pbr(Rgb(0.80f, 0.82f, 0.85f), metallic = 1f, roughness = 0.32f)
    private val darkSteel = Mat.Pbr(Rgb(0.30f, 0.31f, 0.33f), metallic = 1f, roughness = 0.40f)
    private val steel = Mat.Pbr(Rgb(0.86f, 0.87f, 0.90f), metallic = 1f, roughness = 0.22f)
    private val slabTex by lazy {
        ProcTex.bakePlanet(256, 128, Rgb(0.07f, 0.075f, 0.085f), Rgb(0.12f, 0.125f, 0.14f), Rgb(0.20f, 0.205f, 0.22f), 0f, 77)
    }
    private val woodTex by lazy {
        ProcTex.bakePlanet(128, 64, Rgb(0.42f, 0.26f, 0.13f), Rgb(0.66f, 0.46f, 0.26f), Rgb(0.80f, 0.62f, 0.40f), 0.85f, 11)
    }
    private val slab get() = Mat.Pbr(Rgb(1f, 1f, 1f), metallic = 0f, roughness = 0.55f, tex = slabTex)
    private val glass = Mat.Pbr(Rgb(0.86f, 0.93f, 1.0f), metallic = 0f, roughness = 0.04f, alpha = 0.13f)
    private val enamel = Mat.Pbr(Rgb(0.93f, 0.93f, 0.90f), metallic = 0f, roughness = 0.35f)
    private val ink = Mat.Unlit(Rgb(0.10f, 0.11f, 0.13f))
    private fun objMat(i: Int): Mat = when (i) {
        0 -> Mat.Pbr(Rgb(1f, 1f, 1f), metallic = 0f, roughness = 0.6f, tex = woodTex)
        1 -> Mat.Pbr(Rgb(0.70f, 0.87f, 1.00f), metallic = 0f, roughness = 0.06f, emissive = Rgb(0.07f, 0.11f, 0.15f))
        2 -> Mat.Pbr(Rgb(0.93f, 0.90f, 0.82f), metallic = 0f, roughness = 0.4f)
        else -> Mat.Pbr(Rgb(0.86f, 0.87f, 0.90f), metallic = 1f, roughness = 0.28f)
    }
    private fun waterMat() = Mat.Pbr(FLUID_RGB[fluidIdx], metallic = 0f, roughness = 0.04f, alpha = FLUID_ALPHA[fluidIdx])
    private fun beakerWaterMat() = Mat.Pbr(FLUID_RGB[fluidIdx], metallic = 0f, roughness = 0.04f, alpha = minOf(0.85f, FLUID_ALPHA[fluidIdx] + 0.25f))

    // ---------------------------------------------------------------- build

    override fun buildSpec(cacheDir: File): Node {
        val root = Node("BuoyRoot")
        meniscus.clear()               // the demo object outlives its scene: a rebuild must not reuse old nodes
        lastHookY = Float.NaN; lastTop = Float.NaN
        val unitBox = MeshGen.box(1f, 1f, 1f)
        val unitCyl = MeshGen.cylinder(1f, 1f, 24)
        val sphere = MeshGen.sphere(1f, 14, 28, MeshGen.SphereUv.EQUIRECT)

        root.add("Plinth", MeshGen.box(1.74f, 0.035f, 0.80f), slab, P3(-0.02f, PLINTH_Y, 0f))
        // trim sits INSIDE the slab: only its 5 mm overhang shows, as an edge (it used to cover the top)
        root.add("PlinthBand", MeshGen.box(1.75f, 0.008f, 0.81f), alu, P3(-0.02f, PLINTH_Y, 0f))

        // ---- the overflow can: glass walls on a steel base, aluminium corner posts ----
        val fl = FLOOR.toFloat()
        root.add("CanBase", MeshGen.box(2 * HX + 0.05f, fl - DECK, 2 * HZ + 0.05f), darkSteel, P3(TX, (fl + DECK) / 2, 0f))
        val wallH = RIM - fl
        for (sz in floatArrayOf(-1f, 1f)) root.add("GlassZ", MeshGen.box(2 * HX + 2 * WALL, wallH, WALL), glass, P3(TX, (RIM + fl) / 2, sz * (HZ + WALL / 2)))
        for (sx in floatArrayOf(-1f, 1f)) root.add("GlassX", MeshGen.box(WALL, wallH, 2 * HZ), glass, P3(TX + sx * (HX + WALL / 2), (RIM + fl) / 2, 0f))
        for (sx in floatArrayOf(-1f, 1f)) for (sz in floatArrayOf(-1f, 1f))
            root.add("CanPost", MeshGen.box(0.012f, wallH + 0.01f, 0.012f), alu, P3(TX + sx * (HX + WALL), (RIM + fl) / 2, sz * (HZ + WALL)))
        for (sz in floatArrayOf(-1f, 1f)) root.add("RimZ", MeshGen.box(2 * HX + 0.03f, 0.008f, 0.012f), alu, P3(TX, RIM, sz * (HZ + WALL)))
        for (sx in floatArrayOf(-1f, 1f)) root.add("RimX", MeshGen.box(0.012f, 0.008f, 2 * HZ + 0.03f), alu, P3(TX + sx * (HX + WALL), RIM, 0f))
        // graduation on the front-left post: a vessel you can read
        for (k in 0..5) root.add("CanTick", MeshGen.box(if (k % 2 == 0) 0.05f else 0.03f, 0.0025f, 0.002f), Mat.Unlit(Rgb(0.85f, 0.92f, 1f), alpha = 0.6f, blend = Blend.TRANSPARENT),
            P3(TX - HX + 0.02f, fl + 0.05f * k, HZ + WALL + 0.002f))

        water = root.add("Water", unitBox, waterMat())
        for (k in 0 until 4) meniscus += root.add("Meniscus$k", unitBox, Mat.Unlit(Rgb(0.85f, 0.95f, 1f), alpha = 0.55f, blend = Blend.ADD))

        // spout: pipe out of the right wall at the lip, elbow, short drop pipe over the beaker
        val sp = SPOUT.toFloat()
        val pipeX0 = TX + HX + WALL
        root.add("SpoutPipe", unitCyl, steel, P3((pipeX0 + BX) / 2, sp - 0.006f, 0f)).also {
            it.scale = P3(0.013f, BX - pipeX0 + 0.013f, 0.013f); it.rot = Q.axisAngle(0f, 0f, 1f, (PI / 2).toFloat())
        }
        root.add("SpoutDrop", unitCyl, steel, P3(BX, (sp - 0.006f + OUT_Y) / 2, 0f)).also { it.scale = P3(0.013f, sp - 0.006f - OUT_Y, 0.013f) }
        root.add("SpoutElbow", sphere, steel, P3(BX, sp - 0.006f, 0f)).uniformScale(0.0135f)

        // beaker: open glass cylinder + ticks every 100 mL + the collected water
        root.add("Beaker", MeshGen.tube(floatArrayOf(0f, -BH / 2, 0f, 0f, BH / 2, 0f), BR + 0.003f, 32, false), glass, P3(BX, DECK + BH / 2, 0f))
            .also { it.tapRadius = 0.075f }                       // node scale 1: 75 mm world radius
        root.add("BeakerFoot", MeshGen.cylinder(BR + 0.006f, 0.006f, 32), glass, P3(BX, DECK + 0.003f, 0f))
        val bArea = PI * BR * BR
        var ml = 250
        while (ml / 1e6 / bArea < BH - 0.01) {
            val y = DECK + 0.006f + (ml / 1e6 / bArea).toFloat()
            root.add("BTick", MeshGen.box(if (ml % 1000 == 0) 0.04f else 0.022f, 0.0026f, 0.002f), Mat.Unlit(Rgb(0.85f, 0.92f, 1f), alpha = 0.7f, blend = Blend.TRANSPARENT),
                P3(BX - 0.02f, y, BR + 0.0045f))
            ml += 250
        }
        beakerWater = root.add("BeakerWater", unitCyl, beakerWaterMat())
        jetMesh = BuoyMesh.taper(FloatArray(JET_N * 3), FloatArray(JET_N) { 0.003f }, 10)
        jet = root.add("Jet", jetMesh, beakerWaterMat())

        // ---- jib crane behind the can, arm forward over the cube ----
        root.add("CranePost", unitCyl, alu, P3(TX, (DECK + CRANE_Y) / 2, -0.28f)).also { it.scale = P3(0.015f, CRANE_Y - DECK, 0.015f) }
        root.add("CraneFoot", MeshGen.cylinder(0.045f, 0.014f, 24), darkSteel, P3(TX, DECK + 0.007f, -0.28f))
        root.add("CraneArm", MeshGen.box(0.03f, 0.03f, 0.31f), alu, P3(TX, CRANE_Y, -0.13f))
        root.add("Winch", unitCyl, darkSteel, P3(TX, CRANE_Y - 0.004f, 0f)).also { it.scale = P3(0.022f, 0.05f, 0.022f); it.rot = Q.axisAngle(1f, 0f, 0f, (PI / 2).toFloat()) }
        cord = root.add("Cord", unitCyl, ink)

        // ---- spring scale: enamel plate with 2 N ticks, coil, red pointer, hook rod ----
        plate = root.add(Node("Scale"))
        plate.add("Plate", MeshGen.box(0.070f, 0.12f, 0.012f), enamel, P3(0f, -0.05f, 0f))
        for (k in 0..8) plate.add("STick", MeshGen.box(if (k % 5 == 0) 0.028f else 0.016f, 0.0018f, 0.002f), ink, P3(0.014f, -0.025f - 0.01f * k, 0.0065f))
        spring = plate.add("Spring", BuoyMesh.helix(0.011f, 0.06f, 8, 0.0018f), steel, P3(-0.012f, -0.004f, 0.017f))
        pointer = plate.add("Pointer", MeshGen.box(0.044f, 0.005f, 0.006f), Mat.Unlit(Rgb(1f, 0.25f, 0.18f)), P3(0f, -0.025f, 0.018f))
        hookRod = root.add("HookRod", unitCyl, darkSteel)
        hookBall = root.add("Hook", sphere, darkSteel).also { it.uniformScale(0.007f) }
        stringMesh = MeshGen.tube(FloatArray(9 * 3), 0.0016f, 6, false)
        stringNode = root.add("String", stringMesh, ink)

        // ---- the cube, its displaced-volume ghost and its three forces ----
        cube = root.add("Cube", unitBox, objMat(objIdx)).also { it.scale = P3(A.toFloat(), A.toFloat(), A.toFloat()); it.tapRadius = 0.75f }
        vGhost = root.add("VSub", unitBox, Mat.Unlit(Rgb(0.55f, 0.85f, 1f), alpha = 0.22f, blend = Blend.ADD))
        val cone = BuoyMesh.cone(18)
        fun arrow(name: String, c: Rgb) = Node(name).let { g ->
            root.add(g)
            Arrow(g, g.add("${name}Shaft", unitCyl, Mat.Unlit(c)), g.add("${name}Head", cone, Mat.Unlit(c)))
        }
        arrows = arrayOf(arrow("ArrowW", C_W), arrow("ArrowT", C_T), arrow("ArrowB", C_B))

        // ---- the bookkeeping columns: G − F示 = F浮 = G排 ----
        root.add("ColBase", MeshGen.box(0.66f, 0.012f, 0.16f), darkSteel, P3(0.48f, DECK + 0.006f, 0f))
        root.add("ColBoard", MeshGen.box(0.66f, 0.33f, 0.008f), Mat.Pbr(Rgb(0.05f, 0.06f, 0.08f), roughness = 0.08f, alpha = 0.82f), P3(0.48f, DECK + 0.012f + 0.165f, -0.07f))
        // quarter lines of the object's weight: 25 / 50 / 75 / 100 % of G
        for (n in 1..4) root.add("ColGrid", MeshGen.box(0.66f, 0.0016f, 0.002f), Mat.Unlit(Rgb(0.55f, 0.70f, 0.85f), alpha = if (n == 4) 0.40f else 0.15f, blend = Blend.TRANSPARENT),
            P3(0.48f, DECK + 0.012f + n * BAR_W / 4, -0.065f))
        val colRgb = listOf(C_W, C_T, C_B, FLUID_RGB[1])
        cols = Array(4) { k -> root.add("Col$k", unitBox, Mat.Pbr(colRgb[k], metallic = 0.1f, roughness = 0.3f, emissive = dim(colRgb[k], 0.35f))) }
        val gy = DECK + 0.012f + 0.13f
        glyphs = arrayOf(
            listOf(root.add("Minus", MeshGen.box(0.05f, 0.012f, 0.012f), Mat.Unlit(Rgb(0.5f, 0.5f, 0.55f)), P3((COL_X[0] + COL_X[1]) / 2, gy, 0f))),
            listOf(root.add("Eq1a", MeshGen.box(0.05f, 0.012f, 0.012f), Mat.Unlit(Rgb(0.5f, 0.5f, 0.55f)), P3((COL_X[1] + COL_X[2]) / 2, gy + 0.014f, 0f)),
                   root.add("Eq1b", MeshGen.box(0.05f, 0.012f, 0.012f), Mat.Unlit(Rgb(0.5f, 0.5f, 0.55f)), P3((COL_X[1] + COL_X[2]) / 2, gy - 0.014f, 0f))),
            listOf(root.add("Eq2a", MeshGen.box(0.05f, 0.012f, 0.012f), Mat.Unlit(Rgb(0.5f, 0.5f, 0.55f)), P3((COL_X[2] + COL_X[3]) / 2, gy + 0.014f, 0f)),
                   root.add("Eq2b", MeshGen.box(0.05f, 0.012f, 0.012f), Mat.Unlit(Rgb(0.5f, 0.5f, 0.55f)), P3((COL_X[2] + COL_X[3]) / 2, gy - 0.014f, 0f))),
        )

        reset()
        place()
        return root
    }

    // ---------------------------------------------------------------- experiment control

    /** New object/fluid: can full to the spout, beaker empty, cube hanging just above the liquid. */
    fun reset() {
        resets++
        rig = OverflowRig(A, Buoy.SOLIDS[objIdx].rho, Buoy.FLUIDS[fluidIdx].rho, (2 * HX * 2 * HZ).toDouble(), FLOOR, SPOUT, k = K_SCALE, restLen = L0)
        rig.hangAt(SPOUT + START_ABOVE)
        rig.refill()
        hookTop = rig.hook
        rig.hookSpeed = 0.08
        rig.hookTarget = if (lowered) rig.loweredHook(clear = 0.015) else hookTop
        settledFor = 0.0
        if (::cube.isInitialized) {
            cube.setMat(objMat(objIdx)); water.setMat(waterMat())
            beakerWater.setMat(beakerWaterMat()); jet.setMat(beakerWaterMat())
            cols[3].setMat(Mat.Pbr(FLUID_RGB[fluidIdx], metallic = 0.1f, roughness = 0.3f, emissive = dim(FLUID_RGB[fluidIdx], 0.35f)))
        }
    }

    fun toggleLower() { lowered = !lowered; rig.hookTarget = if (lowered) rig.loweredHook(clear = 0.015) else hookTop }
    fun pourBack() { rig.refill(); settledFor = 0.0 }

    // ---------------------------------------------------------------- per frame

    override fun update(dt: Float) {
        val depth0 = rig.depth; val balanced0 = balanced()
        rig.step(dt.toDouble() * SPEEDS[speedIdx])
        val still = abs(rig.v) < 2e-3 && rig.q < 2e-6 && abs(rig.hook - rig.hookTarget) < 1e-5
        settledFor = if (still) settledFor + dt else 0.0
        // sound: a soft low plop as the cube's bottom first touches the liquid
        if (depth0 <= 0.0 && rig.depth > 0.0) Sfx.play(Sfx.THUD, 0.35f, 1.5f, TX / 0.9f)
        // sound: water trickling out of the spout — FIZZ re-triggered ~1/s while it flows, gain ∝ outflow rate
        fizzWait -= dt
        if (rig.q > 2e-6 && fizzWait <= 0f) {
            fizzWait = 1f
            Sfx.play(Sfx.FIZZ, (0.12f + 0.3f * (rig.q / (A * A * 0.08)).toFloat()).coerceAtMost(0.4f), 1.2f, BX / 0.9f)
        }
        // sound: CHIME the moment the books balance (G − F示 = F浮 = G排 both light up) with the cube in the liquid
        if (!balanced0 && balanced() && rig.depth > 0.0) Sfx.play(Sfx.CHIME, 0.5f, 1f, 0.48f / 0.9f)
        place()
        updateReadout()
        updateLabels()
    }

    private fun place() {
        val r = rig
        val a = A.toFloat()
        val yb = r.y.toFloat(); val lvl = r.level.toFloat(); val fl = FLOOR.toFloat()
        // liquid body and its bright waterline
        water.pos = P3(TX, (fl + lvl) / 2, 0f); water.scale = P3(2 * HX, lvl - fl, 2 * HZ)
        meniscus[0].pos = P3(TX, lvl, HZ - 0.002f); meniscus[0].scale = P3(2 * HX, 0.003f, 0.003f)
        meniscus[1].pos = P3(TX, lvl, -HZ + 0.002f); meniscus[1].scale = P3(2 * HX, 0.003f, 0.003f)
        meniscus[2].pos = P3(TX - HX + 0.002f, lvl, 0f); meniscus[2].scale = P3(0.003f, 0.003f, 2 * HZ)
        meniscus[3].pos = P3(TX + HX - 0.002f, lvl, 0f); meniscus[3].scale = P3(0.003f, 0.003f, 2 * HZ)

        // cube and the volume it displaces
        cube.pos = P3(TX, yb + a / 2, 0f)
        val d = r.depth.toFloat()
        vGhost.visible = d > 0.0005f
        if (vGhost.visible) { vGhost.pos = P3(TX, yb + d / 2, 0f); vGhost.scale = P3(a + 0.006f, d + 0.003f, a + 0.006f) }

        // spring scale: plate top at the hook point, coil + pointer show the stretch
        val H = r.hook.toFloat()
        val x = r.stretch.toFloat()
        plate.pos = P3(TX, H, 0f)
        spring.scale = P3(1f, (0.021f + x) / 0.06f, 1f)
        pointer.pos = P3(0f, -0.025f - x, 0.018f)
        cord.pos = P3(TX, (CRANE_Y + H) / 2, 0f); cord.scale = P3(0.0018f, CRANE_Y - H, 0.0018f)
        val hookY = H - 0.025f - x - 0.07f
        hookRod.pos = P3(TX, (H - 0.025f - x + hookY) / 2, 0.012f); hookRod.scale = P3(0.003f, 0.07f, 0.003f)
        hookBall.pos = P3(TX, hookY, 0.012f)
        // string: straight when taut, a soft sag when slack (it cannot push)
        val top = yb + a
        val sag = maxOf(0f, top + 0.015f - hookY)                 // slack length
        if (hookY != lastHookY || top != lastTop) { lastHookY = hookY; lastTop = top; rebuildString(hookY, top, sag) }

        // overflow jet: from the outlet down to the beaker water, thinning as it speeds up
        val bLevel = DECK + 0.006f + (r.vOut / (PI * BR * BR)).toFloat()
        beakerWater.visible = r.vOut > 1e-7
        beakerWater.pos = P3(BX, (DECK + 0.006f + bLevel) / 2, 0f)
        beakerWater.scale = P3(BR - 0.001f, maxOf(0.0005f, bLevel - DECK - 0.006f), BR - 0.001f)
        jet.visible = r.q > 2e-6
        if (jet.visible) {
            val path = FloatArray(JET_N * 3); val rad = FloatArray(JET_N)
            val v0 = r.q / (PI * 0.011 * 0.011)
            for (k in 0 until JET_N) {
                val y = OUT_Y + (bLevel - OUT_Y) * k / (JET_N - 1f)
                val fall = (OUT_Y - y).toDouble()
                val v = sqrt(v0 * v0 + 2 * Buoy.G * fall)
                path[k * 3] = BX; path[k * 3 + 1] = y; path[k * 3 + 2] = 0f
                rad[k] = sqrt(r.q / (PI * v)).toFloat().coerceIn(0.0022f, 0.011f)
            }
            BuoyMesh.taperInto(jetMesh, path, rad, 10); meshEdited(jetMesh)
        }

        // forces on the cube (drawn in front of it): weight from the centre of mass, buoyancy from
        // the centre of buoyancy, string tension from the top
        val zf = a / 2 + 0.012f                                   // just in front of the cube face
        val ak = ARROW_W / r.weight.toFloat()
        setArrow(arrows[0], TX, yb + a / 2, zf, -(r.weight.toFloat() * ak))
        setArrow(arrows[1], TX + 0.032f, top, zf, r.tension.toFloat() * ak)
        setArrow(arrows[2], TX - 0.032f, yb + d / 2, zf, r.buoyancy.toFloat() * ak)

        // columns
        val vals = forces()
        for (k in 0 until 4) {
            val h = maxOf(0.002f, vals[k].toFloat() * barK())
            cols[k].pos = P3(COL_X[k], DECK + 0.012f + h / 2, 0f); cols[k].scale = P3(COL_W, h, COL_W)
        }
        val settled = settledFor > 0.6
        val on = Mat.Unlit(Rgb(0.55f, 1f, 0.70f)); val off = Mat.Unlit(Rgb(0.42f, 0.43f, 0.47f))
        val ok1 = settled && abs(vals[0] - vals[1] - vals[2]) < 0.03
        val ok2 = settled && abs(vals[2] - vals[3]) < 0.03 * maxOf(1.0, vals[2])
        for (g in glyphs[0]) g.setMat(if (ok1) on else off)
        for (g in glyphs[1]) g.setMat(if (ok1) on else off)
        for (g in glyphs[2]) g.setMat(if (ok2) on else off)
    }

    private fun barK() = BAR_W / rig.weight.toFloat()

    private var fizzWait = 0f
    /** Both "=" glyphs lit: settled and the bookkeeping closes (same test as place()). */
    private fun balanced(): Boolean {
        if (settledFor <= 0.6) return false
        val v = forces()
        return abs(v[0] - v[1] - v[2]) < 0.03 && abs(v[2] - v[3]) < 0.03 * maxOf(1.0, v[2])
    }

    private var lastHookY = Float.NaN
    private var lastTop = Float.NaN
    /** the string is re-meshed only when its ends move (it is still most of the time) */
    private fun rebuildString(hookY: Float, top: Float, sag: Float) {
        val sp = FloatArray(9 * 3)
        for (k in 0 until 9) {
            val f = k / 8f
            sp[k * 3] = TX + if (sag > 0f) 0.5f * sag * kotlin.math.sin(PI.toFloat() * f) else 0f
            sp[k * 3 + 1] = hookY + (top - hookY) * f
            sp[k * 3 + 2] = 0.012f * (1 - f)
        }
        MeshGen.tubeInto(stringMesh, sp, 0.0016f, 6, false); meshEdited(stringMesh)
    }

    /** G, F_scale, F_buoy, G_overflow (N) */
    internal fun forces() = doubleArrayOf(rig.weight, rig.scaleReading, rig.buoyancy, rig.overflowWeight)

    private fun setArrow(ar: Arrow, x: Float, y: Float, z: Float, len: Float) {
        val L = abs(len)
        ar.root.visible = L > 0.004f
        if (!ar.root.visible) return
        val head = minOf(0.032f, 0.5f * L)
        ar.root.pos = P3(x, y, z)
        ar.root.rot = if (len < 0) Q.axisAngle(0f, 0f, 1f, PI.toFloat()) else Q.identity()
        ar.shaft.pos = P3(0f, (L - head) / 2, 0f); ar.shaft.scale = P3(0.0065f, L - head, 0.0065f)
        ar.head.pos = P3(0f, L - head, 0f); ar.head.scale = P3(0.017f, head, 0.017f)
    }

    private fun updateReadout() {
        val o = Buoy.SOLIDS[objIdx]; val f = Buoy.FLUIDS[fluidIdx]
        val v = forces()
        val floats = Buoy.floats(o.rho, f.rho)
        val verdict = if (floats) tr("漂浮，浸入 %.0f%%（= ρ物/ρ液）", "floats, %.0f%% submerged (= ρ_obj/ρ_liquid)").format(100 * o.rho / f.rho)
                      else tr("比液体重 → 下沉，只能靠测力计拉住", "denser → sinks; only the spring scale holds it")
        readoutText = tr("阿基米德原理：F浮 = G排 = ρ液 g V排 —— 浮力 = 排开液体的重力\n",
                "Archimedes' principle: buoyant force = weight of liquid displaced = ρ_liquid g V_sub\n") +
            tr("%s ρ=%.2f 放入 %s ρ=%.2f：%s\n", "%s ρ=%.2f in %s ρ=%.2f: %s\n").format(nm(o), o.rho / 1000, tr(f.name, f.en), f.rho / 1000, verdict) +
            tr("G %.2f − F示 %.2f = F浮 %.2f N · 溢出 %.0f mL，G排 %.2f N · %s",
                "W %.2f − scale %.2f = buoyancy %.2f N · overflow %.0f mL, weighs %.2f N · speed %s").format(
                v[0], v[1], v[2], rig.vOut * 1e6, v[3], SPEED_LABELS[speedIdx])
    }

    private fun updateLabels() {
        labels.clear()
        val v = forces()
        val names = listOf(tr("G物 重力", "Weight"), tr("F示 测力计", "Scale"), tr("F浮 浮力", "Buoyancy"), tr("G排 溢出的水", "Overflow"))
        for (k in 0 until 4) labels += LabelSpec("%s %.2f N".format(names[k], v[k]), P3(COL_X[k], DECK + 0.012f + v[k].toFloat() * barK() + 0.04f, 0.02f))
        val o = Buoy.SOLIDS[objIdx]; val f = Buoy.FLUIDS[fluidIdx]
        labels += LabelSpec("%s ρ %.2f g/cm³".format(nm(o), o.rho / 1000), P3(TX - HX - 0.13f, (rig.y + A / 2).toFloat(), 0.05f))
        labels += LabelSpec(tr("%s ρ %.2f · 溢水杯", "%s ρ %.2f · overflow can").format(nm(f), f.rho / 1000), P3(TX, FLOOR.toFloat() - 0.005f, HZ + 0.03f))
    }

    // ---------------------------------------------------------------- interaction

    override val tapTargets get() = listOf(
        TapTarget("Cube", "物块 → 放入 / 提出", "Block → lower / lift") { lowered },
        TapTarget("Beaker", "量杯 → 把水倒回", "Beaker → pour back"),
    )

    override fun isTarget(name: String) = name == "Cube" || name == "Beaker"

    /** Gaze at the cube + pinch: lower / lift it. Gaze at the beaker + pinch: pour it back into the can. */
    override fun tapMessage(name: String): String? = when (name) {
        "Cube" -> if (lowered) tr("物块放入液体：看弹簧秤读数变小、液体溢出", "Block lowered: the scale reads less, liquid overflows") else tr("物块提出液面", "Block lifted out")
        else -> null
    }

    override fun onTap(name: String) {
        if (name == "Cube") toggleLower() else if (name == "Beaker") pourBack()
    }

    // ---------------------------------------------------------------- guided track (导学)
    // Key insight (Archimedes): the buoyant force IS the weight of the liquid pushed aside — the overflow weighs
    // exactly what the spring scale "lost", and a floating block pushes aside exactly its own weight (so a denser
    // liquid floats it higher). The challenge turns that into a density comparison: ice sinks in alcohol.

    /** reset() calls (every object/liquid choice, "Start over"): a step waits for a fresh, settled run. */
    internal var resets = 0
    private var resetsAtStep = 0
    /** Everything at rest (the columns' = signs light up after this). */
    internal val settled get() = settledFor > 0.6
    /** Same as the object / liquid chips on the panel. */
    internal fun selectSolid(k: Int) { objIdx = k; lowered = true; reset() }
    internal fun selectFluid(k: Int) { fluidIdx = k; lowered = true; reset() }
    private fun settledFresh(obj: Int, fluid: Int = -1) =
        objIdx == obj && (fluid < 0 || fluidIdx == fluid) && lowered && resets > resetsAtStep && settled

    // ---- background card (📖): context for someone who has never thought about why things float
    override val about = About(listOf(
        About.Page(
            Bi("这个实验在做什么？", "What is this experiment?"),
            Bi("物块挂在弹簧测力计下，放进装满到出水口的溢水杯：测力计读数变小，液体从出水口流进旁边的量杯。右边四根柱子依次是：物体重力 G、测力计读数 F示、浮力 F浮、溢出液体的重力 G排。捏物块可放入或提出，捏量杯把水倒回；面板可换物块（木、冰、尼龙、铝等）和液体（酒精、水、浓盐水）。", "A block on a spring scale is lowered into an overflow can filled to its spout: the scale reading drops and liquid spills into a beaker. The four columns on the right are the block's weight G, the scale reading, the buoyant force, and the weight of the overflow. Pinch the block to lower or lift it, pinch the beaker to pour back; the panel swaps blocks (wood, ice, nylon, aluminium…) and liquids (alcohol, water, brine)."),
        ),
        About.Page(
            Bi("国王的王冠", "The king's crown"),
            Bi("两千多年前，叙拉古的阿基米德在《论浮体》里写下了浮力的规律。流传更广的是罗马人维特鲁威记下的故事：国王怀疑金匠在王冠里掺了银，又不许把王冠熔掉。据说阿基米德在澡盆里看到水溢出来，想到可以比较王冠和同样重的纯金各挤走多少水——体积不一样，就说明掺了假。故事未必真实，思路却很漂亮。", "Over 2,000 years ago, Archimedes of Syracuse set out the law of buoyancy in On Floating Bodies. A more famous story, told by the Roman writer Vitruvius: the king suspected silver had been mixed into his gold crown, which could not be melted down. Watching his bath overflow, Archimedes saw he could compare the water pushed aside by the crown and by an equal weight of pure gold — a different volume means a fake. Maybe a legend, but a lovely idea."),
        ),
        About.Page(
            Bi("浮力从哪里来？", "Where does buoyancy come from?"),
            Bi("物体浸进液体，就占了一块液体的位置。想象先把那块液体装进一个看不见的袋子：周围的液体正好把它托住。再把袋里换成铝块——周围的液体分不出来，照样往上托同样大的力。所以浮力 = 排开液体的重力：F浮 = ρ液 g V排。比液体密就下沉；比液体疏就浮起，只沉下 ρ物/ρ液 那么多（木块在水里 60%）。", "A submerged object takes the place of a chunk of liquid. Picture that chunk in an invisible bag: the liquid around it holds it up exactly. Now swap the bag's contents for aluminium — the surrounding liquid can't tell and pushes up just as hard. So buoyancy equals the weight of liquid displaced: F = ρ_liquid g V. Denser than the liquid, it sinks; less dense, it floats with only ρ_obj/ρ_liquid under (60% for wood in water)."),
        ),
        About.Page(
            Bi("今天在哪里？", "Where is it today?"),
            Bi("几十万吨的钢铁巨轮能浮起来，是因为船身是空的，挤走的水足够重。潜水艇往水舱里灌水或排水来下潜、上浮，鱼用鱼鳔做同样的事。热气球是在“空气的海洋”里受浮力。人在死海里能躺着漂，因为盐水更密。冰山只露出约一成：冰的密度大约是海水的九成。", "A steel ship of hundreds of thousands of tonnes floats because its hull is hollow and pushes aside enough water. Submarines flood or empty ballast tanks to dive or rise; fish do the same with a swim bladder. A hot-air balloon floats in an ocean of air. You can lie back in the Dead Sea because brine is denser. And only about a tenth of an iceberg shows: ice is roughly 90% as dense as seawater."),
        ),
    ))

    override val lesson: Lesson by lazy {
        Lesson(
            listOf(
                LessonStep(
                    say = Bi("铝块挂在测力计上。把它浸没在水里：测力计少掉的读数，和溢出来的水，哪个重？", "An aluminium block hangs from a spring scale. Sunk in water, which weighs more: the scale's loss or the overflow?"),
                    options = listOf(
                        Bi("少掉的读数", "Scale's loss"),
                        Bi("溢出的水", "Overflow"),
                        Bi("一样重", "Same"),
                    ),
                    answer = 2,
                ),
                LessonStep(
                    say = Bi("在面板上选「铝块 2.70」：它会自动沉进溢水杯。等右边四根柱子停稳。", "Pick “Aluminium 2.70” on the panel: it sinks into the overflow can. Wait for the four columns on the right to settle."),
                    onEnter = { resetsAtStep = resets },
                    done = { settledFresh(3) },
                    autoplay = { selectSolid(3) },
                    reveal = true,
                    insight = Bi("一样重！测力计少了 26.9\u00A0N，溢出的水也正好重 26.9\u00A0N：浮力 = 排开液体的重力。", "Equal! The scale lost 26.9\u00A0N and the overflow weighs exactly 26.9\u00A0N: buoyancy = weight of liquid displaced."),
                ),
                LessonStep(
                    say = Bi("再猜：木块会浮在水上。它挤出去的水，和木块自己比？", "Predict: a wooden block floats on water. The water it pushes out, compared with the block's own weight?"),
                    options = listOf(
                        Bi("更轻", "Lighter"),
                        Bi("一样重", "Same"),
                        Bi("更重", "Heavier"),
                    ),
                    answer = 1,
                ),
                LessonStep(
                    say = Bi("选「木块 0.60」：它浮起来，测力计归零。比一比「G物」和「G排」两根柱子。", "Pick “Wood 0.60”: it floats and the scale drops to zero. Compare the “Weight” and “Overflow” columns."),
                    onEnter = { resetsAtStep = resets },
                    done = { settledFresh(0) },
                    autoplay = { selectSolid(0) },
                    reveal = true,
                    insight = Bi("一样重：16.2\u00A0N 的木块挤出 16.2\u00A0N 的水。漂浮时浮力 = 重力，所以只浸入 60%。", "Same: the 16.2\u00A0N block pushes out 16.2\u00A0N of water. Floating means buoyancy = weight, so only 60% is under."),
                ),
                LessonStep(
                    say = Bi("把液体换成「浓盐水 1.20」：木块会浮得更高，还是更低？", "Switch the liquid to “Brine 1.20”: does the wood float higher or lower?"),
                    onEnter = { resetsAtStep = resets },
                    done = { settledFresh(0, 2) },
                    autoplay = { if (objIdx != 0) selectSolid(0); selectFluid(2) },
                    insight = Bi("更高：盐水更「重」，只浸入 50%（0.60/1.20）就挤出 16.2\u00A0N。人在死海能躺着漂。", "Higher: brine is denser, so 50% under (0.60/1.20) already pushes out 16.2\u00A0N — why you float in the Dead Sea."),
                ),
            ),
            challenge = Challenge(
                say = Bi("冰块总会浮着吗？只换液体，让冰块沉下去。", "Does ice always float? Change only the liquid and make the ice block sink."),
                progress = {
                    val o = Buoy.SOLIDS[objIdx]; val f = Buoy.FLUIDS[fluidIdx]
                    tr("%s放进%s：%s", "%s in %s: %s").format(nm(o), tr(f.name, f.en), if (Buoy.floats(o.rho, f.rho)) tr("浮着", "floats") else tr("沉下去了", "sinks"))
                },
                done = { objIdx == 1 && !Buoy.floats(Buoy.SOLIDS[1].rho, Buoy.FLUIDS[fluidIdx].rho) && lowered && settled },
                autoplay = { selectSolid(1); selectFluid(0) },
            ),
        )
    }

    @Composable
    override fun Panel() {
        var oi by FieldRef(::objIdx)
        var fi by FieldRef(::fluidIdx)
        var si by FieldRef(::speedIdx)
        LabPanel(readout) {
            LabRow {
                SegmentControl {
                    Buoy.SOLIDS.forEachIndexed { k, s ->
                        SegmentItem(selected = oi == k, onClick = { oi = k; objIdx = k; lowered = true; reset() }, title = { Text("%s %.2f".format(nm(s), s.rho / 1000)) })
                    }
                }
            }
            LabRow {
                SegmentControl {
                    Buoy.FLUIDS.forEachIndexed { k, s ->
                        SegmentItem(selected = fi == k, onClick = { fi = k; fluidIdx = k; lowered = true; reset() }, title = { Text("%s %.2f".format(nm(s), s.rho / 1000)) })
                    }
                }
                Spacer(Modifier.width(20.dp))
                SegmentControl {
                    SPEED_LABELS.forEachIndexed { k, l -> SegmentItem(selected = si == k, onClick = { si = k; speedIdx = k }, title = { Text(l) }) }
                }
            }
            LabRow {
                // lower / lift and pour back live in the "pinch the object" row (same actions)
                Button(onClick = { lowered = true; reset() }) { Text(tr("重做", "Start over")) }
            }
        }
    }

    private fun dim(c: Rgb, k: Float) = Rgb(c.r * k, c.g * k, c.b * k)
}
