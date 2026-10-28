// 太阳系 · 开普勒定律 — the solar system as a SpecDemo, so it renders through the same adapter as
// every lab demo AND can be reviewed headless on the Mac (SoftRender).
//
// FOUR VIEWS (panel mode switch):
//   轨道      the orbits (JPL elements), Kepler cards, comet throwing — the original demo
//   地月      Sun-fixed Earth-Moon close-up: true directions, phases from lighting, the Moon as seen from
//             Earth, the 8-phase ring, the tilted orbit + line of nodes, real eclipses (EarthMoonView)
//   真实比例  sizes and distances on ONE ruler, magnifier bubbles (ToScaleView)
//   大小对比  true relative sizes, three zoom stages (SizeLineupView)
//
// RENDERING WITHOUT CUSTOM SHADERS (see ProcTex header):
//   sun     = unlit photosphere A (opaque) + photosphere B (transparent, opacity = boil phase)
//             + viewer-facing limb-darkening shell + viewer-facing additive corona disc
//   planets = unlit albedo + SUN-facing lighting shell (black, alpha = 1 - Lambert)
// The sun is the only light in the solar system, so nothing here uses scene lights or IBL — the
// room's lighting cannot leak onto the planets, and day/night is exact at every position.
// Physics: core/SolarCore (JPL elements, parity-tested against the Unity C#) + core/SolarModel
// (display mapping, the ELP lunar series, eclipse geometry, true-scale facts).

package com.bytedance.physicslab.solar.demos.solar

import android.util.Log
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bytedance.physicslab.solar.core.Eclipse
import com.bytedance.physicslab.solar.core.Moon
import com.bytedance.physicslab.solar.core.P3
import com.bytedance.physicslab.solar.core.ScaleFacts
import com.bytedance.physicslab.solar.core.Sky
import com.bytedance.physicslab.solar.core.Solar
import com.bytedance.physicslab.solar.core.SolarModel
import com.bytedance.physicslab.solar.lab.BakeCache
import com.bytedance.physicslab.solar.lab.About
import com.bytedance.physicslab.solar.lab.Bi
import com.bytedance.physicslab.solar.lab.Challenge
import com.bytedance.physicslab.solar.lab.Lesson
import com.bytedance.physicslab.solar.lab.LessonStep
import com.bytedance.physicslab.solar.lab.Q
import com.bytedance.physicslab.solar.lab.SpecDemo
import com.bytedance.physicslab.solar.lab.TapTarget
import com.bytedance.physicslab.solar.lab.spec.Blend
import com.bytedance.physicslab.solar.lab.spec.LabelSpec
import com.bytedance.physicslab.solar.lab.spec.Mat
import com.bytedance.physicslab.solar.lab.spec.Node
import com.bytedance.physicslab.solar.lab.tr
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
import com.bytedance.physicslab.solar.render.Palette
import com.bytedance.physicslab.solar.render.ProcTex
import com.bytedance.physicslab.solar.render.Rgb
import com.pico.spatial.ui.design.Text
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

/** Three speeds + pause (the user asked for slow/medium/fast levels so motion can be studied). */
private val RATES = doubleArrayOf(0.0, 2.0, 15.0, 60.0)
private val RATE_LABELS get() = listOf(tr("暂停", "Pause"), tr("慢 2天/秒", "2 days/s"), tr("中 15天/秒", "15 days/s"), tr("快 60天/秒", "60 days/s"))
/** Earth-Moon: a month takes 60 s / 10 s / 1.5 s (fast = the node line visibly swings: eclipse seasons). */
private val EM_RATES = doubleArrayOf(0.0, 0.5, 3.0, 20.0)
private val EM_RATE_LABELS get() = listOf(tr("暂停", "Pause"), tr("慢 0.5天/秒", "0.5 days/s"), tr("中 3天/秒", "3 days/s"), tr("快 20天/秒", "20 days/s"))
/** Eclipse playback, minutes of eclipse per second. */
private val LOCK_RATES = doubleArrayOf(0.0, 5.0, 20.0, 60.0)
private val LOCK_RATE_LABELS get() = listOf(tr("暂停", "Pause"), tr("慢 5分钟/秒", "5 min/s"), tr("中 20分钟/秒", "20 min/s"), tr("快 1小时/秒", "1 h/s"))
private val MODE_LABELS get() = listOf(tr("轨道", "Orbits"), tr("地月", "Earth–Moon"), tr("真实比例", "To scale"), tr("大小对比", "Size lineup"))
private val STAGE_LABELS get() = listOf(tr("① 太阳", "① Sun"), tr("② 巨行星", "② Giants"), tr("③ 岩石行星", "③ Rocky"))

/** "MERCURY" -> "Mercury" (core keeps the JPL upper-case name as its identity). */
private fun enName(upper: String) = upper.lowercase().replaceFirstChar { it.uppercase() }

object SolarDemo : SpecDemo() {
    override val id = "solar"
    override val title = "太阳系 · 开普勒定律"  // i18n-ok (zh half of DemoRegistry.titleOf; English in TITLE_EN)
    override val credit = "Kepler · 1609"

    enum class Mode { ORBITS, EARTH_MOON, TO_SCALE, SIZES }

    private const val TAG = "SolarScene"
    private const val HIT_MIN_M = 0.045f      // world metres, at any zoom
    /** Scene magnification inside the 2700 dp (2.16 m) volume: Neptune's 0.78 m orbit -> 1.01 m. */
    const val BASE_SCALE = 1.3f
    /**
     * The ecliptic is TILTED toward the viewer and lifted a little. Untilted, a Shared-Space volume
     * puts the ecliptic at eye height: the offline render from the real viewpoint (2.5 m, eye 0.1 m
     * above the plane) showed the whole system edge-on as one line — which is exactly why no
     * terminator and no Moon phase could be seen on the headset. Looking down 20 deg fixes it.
     */
    private val TILT = Q.axisAngle(1f, 0f, 0f, Math.toRadians(20.0).toFloat())
    private const val LIFT = 0.06f
    private const val ORBIT_PTS = 128
    private const val MOON_PTS = 96
    /** Eclipse playback loops over +- this many hours around greatest eclipse. */
    private const val LOCK_WINDOW_H = 2.8

    val model = SolarModel()

    private class PlanetView(
        val root: Node, val body: Node, val squash: Node, val shell: Node, val orbit: Node,
        val orbitMesh: MeshData, val ring: Node?, val tiltQ: Q, val oblate: Float,
    ) {
        var spinDeg = 0f
        var shapeT = Double.NaN
        var orbitUnit = FloatArray(0)
        var pos = P3(0f, 0f, 0f)
    }

    private lateinit var sroot: Node
    private lateinit var sunSpin: Node
    private lateinit var photoB: Node
    private lateinit var corona: Node
    private lateinit var limb: Node
    private lateinit var sunHit: Node
    private val pv = ArrayList<PlanetView>()
    private lateinit var moonPivot: Node
    private lateinit var moonBody: Node
    private lateinit var moonShell: Node
    private lateinit var moonOrbitMesh: MeshData
    private lateinit var sunBMat: Mat.Unlit
    private lateinit var coronaTex: Image
    private lateinit var cometIcon: Node

    // ---- the other views ----
    internal lateinit var em: EarthMoonView; private set
    internal lateinit var ts: ToScaleView; private set
    internal lateinit var sz: SizeLineupView; private set

    var mode = Mode.ORBITS; private set
    /** The eclipse the Earth-Moon view is locked on (null = free running). */
    var lockEvent: Eclipse.Event? = null; private set
    private var lunarNext = 0
    private var solarNext = 0
    private var modeShown: Mode? = null

    private var builtSpread = -1.0
    private var builtScale = -1f
    private var builtSize = -1.0
    private var builtMoonR = -1f
    private var clock = 0.0
    private var colourDirty = true
    private var statFrames = 0; private var statT = 0.0; private var statWorst = 0f
    /** mesh re-uploads requested since the last [takeMeshEdits] (performance test hook) */
    private var meshEdits = 0
    fun takeMeshEdits(): Int { val n = meshEdits; meshEdits = 0; return n }
    private fun edit(md: MeshData) { meshEdits++; meshEdited(md) }

    // ================================================================ build

    override fun buildSpec(cacheDir: File): Node {
        pv.clear(); builtSpread = -1.0; builtScale = -1f; builtSize = -1.0; builtMoonR = -1f
        colourDirty = true; clock = 0.0
        mode = Mode.ORBITS; modeShown = null; lockEvent = null; lunarNext = 0; solarNext = 0
        rateIdx = 2; reverse = false; model.followEarth = false; meshEdits = 0
        applyRate()
        val t0 = System.nanoTime()
        val b = Bakes.from(BakeCache.load(cacheDir, "solar", 4) { Bakes.make().all() })
        val x = BakeCache.load(cacheDir, "solar_views", 6) {
            listOf(SolarArt.bakeMoonHi(512, 256), SolarArt.bakeGlow(64), SolarArt.bakeRay(64), SolarArt.bakeSpot(64),
                ProcTex.bakeLightShell(256, ambient = 0.09f), ProcTex.bakeLightShell(256, ambient = 0.05f), SolarArt.bakeCoronaRing(256))
        }
        Log.i(TAG, "bakes ready in ${"%.0f".format((System.nanoTime() - t0) / 1e6)} ms (${BakeCache.lastSource})")
        coronaTex = b.corona
        val tex = SolarTex(b.sunA, b.sunB, b.limb, b.corona, b.light, b.ring, b.planets, b.moon, x[0], x[1], x[2], x[3], x[4], x[5], x[6])

        // the solar root is tilted/scaled/translated; an untransformed outer root holds volume-space
        // things (the ☄ comet icon, the other views) so they do not swing with the zoom
        outer = Node("SolarOuter")
        sroot = outer.add(Node("SolarRoot"))
        val sunRoot = sroot.add(Node("SunRoot"))
        val sphere = MeshGen.sphere(1f, 48, 96, MeshGen.SphereUv.EQUIRECT)
        val shellMesh = MeshGen.sphere(1f, 64, 64, MeshGen.SphereUv.POLAR_U)

        // ---- sun ----
        sunSpin = sunRoot.add(Node("SunSpin"))
        sunSpin.add("PhotosphereA", sphere, Mat.Unlit(tex = b.sunA))
        sunBMat = Mat.Unlit(tex = b.sunB, alpha = 0f, blend = Blend.TRANSPARENT)
        photoB = sunSpin.add("PhotosphereB", sphere, sunBMat).also { it.uniformScale(1.0015f) }
        val viewerPivot = sunRoot.add(Node("ViewerPivot")).also { it.billboard = true }
        // shell +Y (u = 0, alpha 0) must face the viewer: +90 deg about X maps +Y -> +Z
        limb = viewerPivot.add("LimbShell", shellMesh, Mat.Unlit(tex = b.limb, blend = Blend.TRANSPARENT))
        limb.rot = Q.axisAngle(1f, 0f, 0f, (PI / 2).toFloat())
        corona = viewerPivot.add("Corona", quad(2 * 2.30f), Mat.Unlit(tex = b.corona, blend = Blend.ADD))
        sunHit = sunRoot.add(Node("Sun"))

        // ---- planets ----
        val shellMat = Mat.Unlit(tex = b.light, blend = Blend.TRANSPARENT)
        for (i in 0 until 8) {
            val p = Solar.planets[i]
            val pRoot = sunRoot.add(Node("PlanetRoot_$i"))
            val tq = Q.axisAngle(0f, 0f, 1f, Math.toRadians(p.axialTiltDeg).toFloat())
            val tilt = pRoot.add(Node("Tilt_$i")).also { it.rot = tq }
            val body = tilt.add("Planet_$i", sphere, Mat.Unlit(tex = b.planets[i]))
            val squash = tilt.add(Node("Squash_$i"))
            val shell = squash.add("Shell_$i", shellMesh, shellMat)
            var ring: Node? = null
            if (p.hasRings) {
                val saturn = i == 5
                val br = Solar.bodyDisplayRadius(p.radiusKm).toFloat()       // x sizeK via node scale
                val tint = ProcTex.okLerp(Palette.mid[i], Rgb(1f, 1f, 1f), 0.45f)
                ring = tilt.add(if (saturn) "SaturnRings" else "UranusRings",
                    MeshGen.annulus(br * if (saturn) 1.28f else 1.55f, br * if (saturn) 2.27f else 1.95f, 128),
                    Mat.Unlit(tint, alpha = if (saturn) 1f else 0.28f, tex = b.ring, blend = Blend.TRANSPARENT))
            }
            val orbitMesh = MeshGen.tube(ring0(ORBIT_PTS), 0.0011f, 6, true)
            val orbit = sunRoot.add("Orbit_$i", orbitMesh, orbitMat(i, 0.42f))
            pv += PlanetView(pRoot, body, squash, shell, orbit, orbitMesh, ring, tq, SolarModel.OBLATE[i])
        }

        // ---- Moon: pivot rides Earth's POSITION only (ecliptic-aligned, not Earth-tilted) ----
        moonPivot = pv[2].root.add(Node("MoonPivot"))
        moonBody = moonPivot.add("Planet_8", sphere, Mat.Unlit(tex = b.moon))
        moonShell = moonPivot.add("MoonShell", shellMesh, shellMat)
        moonOrbitMesh = MeshGen.tube(ring0(MOON_PTS), 0.0008f, 5, true)
        moonPivot.add("MoonOrbit", moonOrbitMesh, Mat.Unlit(Rgb(0.80f, 0.84f, 0.95f), alpha = 0.45f, blend = Blend.TRANSPARENT))
        comets = CometSandbox(model, frame).also { it.build(outer, sunRoot) }
        cometIcon = outer.find("CometIcon")!!

        // ---- the other three views (volume space, hidden until chosen) ----
        em = EarthMoonView(tex).also { it.build(outer) }
        ts = ToScaleView(tex).also { it.build(outer) }
        sz = SizeLineupView(tex).also { it.build(outer) }
        applyVisibility()
        return outer
    }

    private lateinit var outer: Node
    lateinit var comets: CometSandbox
        private set

    /** volume <-> sun-root conversions for the comet sandbox (tilt, scale, translation). */
    private val frame = object : CometSandbox.Frame {
        override val scale get() = if (builtScale > 0f) builtScale else BASE_SCALE
        override fun toSun(v: P3): P3 {
            val d = TILT.conj().rotate(floatArrayOf((v.x - sroot.pos.x) / scale, (v.y - sroot.pos.y) / scale, (v.z - sroot.pos.z) / scale))
            return P3(d[0], d[1], d[2])
        }
        override fun toVolume(p: P3): P3 = toVolume(p, scale)
        override fun dirToSun(v: P3): P3 { val d = TILT.conj().rotate(floatArrayOf(v.x, v.y, v.z)); return P3(d[0], d[1], d[2]) }
    }

    private fun orbitMat(i: Int, a: Float) = Mat.Unlit(Palette.orbit(i), alpha = a, blend = Blend.TRANSPARENT)

    /** A placeholder closed path with the right point count (real geometry arrives on the first update). */
    private fun ring0(n: Int) = FloatArray(n * 3) { k ->
        val a = 2 * PI * (k / 3) / n
        when (k % 3) { 0 -> 0.1f * kotlin.math.cos(a).toFloat(); 2 -> 0.1f * sin(a).toFloat(); else -> 0f }
    }

    /** Unit-UV quad in the XY plane, facing +Z (the corona disc). */
    private fun quad(size: Float): MeshData = SolarArt.quad(size)

    // ================================================================ modes

    fun setMode(m: Mode) {
        if (m == mode) return
        if (lockEvent != null) exitLock()
        mode = m
        applyRate(); applyVisibility()
    }

    private fun applyVisibility() {
        if (modeShown == mode) return
        modeShown = mode
        sroot.visible = mode == Mode.ORBITS
        cometIcon.visible = mode == Mode.ORBITS
        em.root.visible = mode == Mode.EARTH_MOON; em.disc.visible = mode == Mode.EARTH_MOON
        ts.root.visible = mode == Mode.TO_SCALE
        sz.root.visible = mode == Mode.SIZES
    }

    /** Panel speed level (index into the rate tables) and direction — demo state, so the panel never resets. */
    var rateIdx = 2
    var reverse = false
    /** the panel's mode switch (index into Mode) */
    var modeIdx: Int
        get() = mode.ordinal
        set(v) = setMode(Mode.entries[v.coerceIn(0, 3)])
    var stageIdx: Int
        get() = if (::sz.isInitialized) sz.stage else 0
        set(v) { sz.go(v) }

    fun applyRate() {
        val sgn = if (reverse) -1.0 else 1.0
        model.daysPerSecond = sgn * when {
            mode == Mode.EARTH_MOON && lockEvent != null -> LOCK_RATES[rateIdx] / 1440.0
            mode == Mode.EARTH_MOON -> EM_RATES[rateIdx]
            else -> RATES[rateIdx]
        }
    }

    /** Jump to the next real eclipse of that kind (NASA catalogue) and lock the view on the alignment. */
    fun startEclipse(solar: Boolean) {
        val list = if (solar) Eclipse.SOLAR else Eclipse.LUNAR
        val i = if (solar) solarNext else lunarNext
        val ev = list[i % list.size]
        if (solar) solarNext = (i + 1) % list.size else lunarNext = (i + 1) % list.size
        if (mode != Mode.EARTH_MOON) { mode = Mode.EARTH_MOON; applyVisibility() }
        lockEvent = ev; em.setLock(ev)
        model.jd = ev.jd
        rateIdx = 0; applyRate()
        Log.i(TAG, "eclipse preset ${if (solar) "solar" else "lunar"} ${ev.date}")
    }

    fun exitLock() { lockEvent = null; em.setFree(); applyRate() }

    /** Step to the next principal phase (new / first quarter / full / last quarter) and pause there. */
    fun nextPhase() {
        phaseSteps++
        if (lockEvent != null) exitLock()
        val e = Sky.elongationDeg(model.jd)
        val target = (floor((e + 1.0) / 90.0) + 1) * 90.0
        var t = model.jd + (target - e) / 12.19
        repeat(8) {
            var d = Sky.elongationDeg(t) - target % 360.0
            d = ((d % 360.0) + 540.0) % 360.0 - 180.0
            t -= d / 12.19
        }
        model.jd = t.coerceIn(SolarModel.JD_MIN, SolarModel.JD_MAX)
        rateIdx = 0; applyRate()
    }

    /** Phases stepped so far (the guided track waits for the user to step a couple). */
    internal var phaseSteps = 0
    private var phaseStepsAtStep = 0

    /** Tonight's Moon: the device clock (UTC) + 69 s (TT - UTC, 2026). */
    fun goToday() {
        if (lockEvent != null) exitLock()
        model.jd = 2440587.5 + System.currentTimeMillis() / 86400000.0 + 69.2 / 86400.0
    }

    // ================================================================ per frame

    override fun update(dt: Float) {
        clock += dt
        val m = model
        applyVisibility()
        when (mode) {
            Mode.ORBITS -> { m.step(dt.toDouble()); updateOrbits(dt) }
            Mode.EARTH_MOON -> {
                m.step(dt.toDouble())
                lockEvent?.let { ev ->
                    val w = LOCK_WINDOW_H / 24.0
                    if (m.jd > ev.jd + w) m.jd = ev.jd - w
                    if (m.jd < ev.jd - w) m.jd = ev.jd + w
                }
                em.update(dt, m.jd) { edit(it) }
            }
            Mode.TO_SCALE -> ts.update()
            Mode.SIZES -> sz.update(dt)
        }
        readoutText = readout()
        updateLabels()

        statFrames++; statT += dt
        if (statT >= 5.0) {
            Log.i(TAG, "tick %.1f Hz, worst dt %.1f ms, mode %s, date %s".format(statFrames / statT, statWorst * 1000, mode, SolarModel.dateString(m.jd)))
            statFrames = 0; statT = 0.0; statWorst = 0f
        }
        if (dt > statWorst) statWorst = dt
    }

    private fun updateOrbits(dt: Float) {
        val m = model
        val scale = (BASE_SCALE * m.zoom).toFloat()
        val rescaled = abs(scale - builtScale) > 1e-3f
        val rebuild = abs(m.spread - builtSpread) > 0.004 || rescaled
        val resize = abs(m.sizeK - builtSize) > 0.005
        if (rescaled) { sroot.uniformScale(scale); sroot.rot = TILT; builtScale = scale; sroot.pos = P3(0f, LIFT, 0f) }

        // ---- sun: spin + granulation drift (Unity: 1 deg/s + 0.004 uv/s = 2.44 deg/s), boil cross-fade ----
        sunSpin.rot = Q.axisAngle(0f, 1f, 0f, Math.toRadians(clock * 2.44).toFloat())
        photoB.setMat(sunBMat.copy(alpha = ((sin(clock * 0.045 * 2 * PI) * 0.5 + 0.5) * 20).toInt() / 20f))
        if (rebuild) {
            val sr = m.sunRadius.toFloat()
            sunSpin.uniformScale(sr)
            limb.uniformScale(sr * 1.003f)
            corona.uniformScale(sr)
            // the orbit is the truth, the corona is decoration: dim it where Mercury runs through
            val dim = ProcTex.lerp(0.42f, 1f, ProcTex.smooth01((m.spread / 0.3).toFloat()))
            corona.setMat(Mat.Unlit(Rgb(dim, dim, dim), tex = coronaTex, blend = Blend.ADD))
            sunHit.tapRadius = max(sr * 1.15f, HIT_MIN_M / scale)
        }

        // ---- planets ----
        for (i in 0 until 8) {
            val v = pv[i]
            v.pos = m.planetPos(i)
            v.root.pos = v.pos
            v.spinDeg = (v.spinDeg + SolarModel.SPIN[i] * dt) % 360f
            v.body.rot = Q.axisAngle(0f, 1f, 0f, Math.toRadians(v.spinDeg.toDouble()).toFloat())
            if (resize || rebuild) {
                val r = m.bodyR(i).toFloat()
                v.body.scale = P3(r, r * (1f - v.oblate), r)
                v.squash.scale = P3(1f, 1f - v.oblate, 1f)
                v.shell.uniformScale(r * 1.004f)
                v.ring?.uniformScale(m.sizeK.toFloat())
                // gaze targets: >= 45 mm WORLD radius (~2 deg at 2.5 m), above eye-tracking error
                v.body.tapRadius = max(r * 1.6f, HIT_MIN_M / scale) / r
            }
            orientShell(v.shell, v.tiltQ, v.oblate, -v.pos.x, -v.pos.y, -v.pos.z)
            if (v.shapeT.isNaN() || abs(m.t - v.shapeT) > 0.5) {
                v.orbitUnit = m.orbitShapeUnit(i, ORBIT_PTS); v.shapeT = m.t
                rebuildOrbit(i)
            } else if (rebuild) rebuildOrbit(i)
        }
        if (colourDirty || rebuild) { refreshOrbitColours(); colourDirty = false }

        updateMoon(resize || rebuild)

        if (rebuild) builtSpread = m.spread
        if (resize || rebuild) builtSize = m.sizeK

        comets.update(dt) { edit(it) }
    }

    private fun updateMoon(resized: Boolean) {
        val m = model
        val mm = if (m.showMoon) m.moonMetrics() else null
        moonPivot.visible = mm != null
        if (mm == null) return
        val (orbitR, moonR) = mm
        val off = m.moonOffset(orbitR)
        moonBody.pos = off; moonShell.pos = off
        if (resized || abs(moonR - builtMoonR) > 1e-5f) {
            moonBody.uniformScale(moonR)
            moonShell.uniformScale(moonR * 1.004f)
            moonBody.tapRadius = max(moonR * 1.8f, HIT_MIN_M * 0.6f / builtScale) / moonR
            builtMoonR = moonR
            MeshGen.tubeInto(moonOrbitMesh, m.moonOrbitPath(orbitR, MOON_PTS), 0.0008f * BASE_SCALE / builtScale, 5, true)
            edit(moonOrbitMesh)
        }
        // the Moon is lit by the real sun direction -> its phase is geometry, not a lookup
        val e = pv[2].pos
        orientShell(moonShell, Q.identity(), 0f, -(e.x + off.x), -(e.y + off.y), -(e.z + off.z))
    }

    private fun rebuildOrbit(i: Int) {
        val v = pv[i]
        val r = model.orbitR(i).toFloat()
        // constant VISIBLE thickness at any zoom (the tube scales with the root otherwise)
        MeshGen.tubeInto(v.orbitMesh, FloatArray(v.orbitUnit.size) { v.orbitUnit[it] * r }, 0.0011f * BASE_SCALE / builtScale, 6, true)
        edit(v.orbitMesh)
    }

    private fun refreshOrbitColours() {
        val sel = model.selected
        for (i in 0 until 8) pv[i].orbit.setMat(orbitMat(i, when { sel == i -> 0.95f; sel in 0..7 -> 0.12f; else -> 0.42f }))
    }

    /** Point the shell's +Y at direction (dx,dy,dz) given in the planet root frame. */
    private fun orientShell(shell: Node, tiltQ: Q, oblate: Float, dx: Float, dy: Float, dz: Float) {
        val d = tiltQ.conj().rotate(floatArrayOf(dx, dy, dz))    // root frame -> tilt frame
        d[1] /= (1f - oblate)                                     // tilt -> squash frame
        shell.rot = Q.fromTo(0f, 1f, 0f, d[0], d[1], d[2])
    }

    /** Volume-space position of a point given in sun-root coordinates (tilt, scale, translation). */
    private fun toVolume(p: P3, scale: Float): P3 {
        val r = TILT.rotate(floatArrayOf(p.x * scale, p.y * scale, p.z * scale))
        return P3(sroot.pos.x + r[0], sroot.pos.y + r[1], sroot.pos.z + r[2])
    }

    // ================================================================ labels

    private fun lab(text: String, p: P3) { if (labels.size < 6) labels += LabelSpec(text, p) }
    private fun up(p: P3, dy: Float) = P3(p.x, p.y + dy, p.z)

    private fun updateLabels() {
        labels.clear()
        when (mode) {
            Mode.ORBITS -> orbitLabels()
            Mode.EARTH_MOON -> emLabels()
            Mode.TO_SCALE -> scaleLabels()
            Mode.SIZES -> sizeLabels()
        }
    }

    private fun orbitLabels() {
        val m = model
        val sel = m.selected
        val scale = builtScale
        if (sel in 0..7) {
            // the answer to "I pinched a planet — now what?": its Kepler card, next to it
            val p = Solar.planets[sel]
            val up = (m.bodyR(sel).toFloat() * 1.6f + 0.012f)
            val years = p.periodDays / 365.256
            val k3 = years * years / (p.a * p.a * p.a)
            lab(tr("${p.chinese} ${p.name}\n距日 %.2f AU · 公转 %.2f 年\nT²/a³ = %.3f（开普勒第三定律）",
                "${enName(p.name)}\n%.2f AU from the Sun · %.2f yr orbit\nT²/a³ = %.3f (Kepler's third law)").format(m.sunDistanceAu(sel), years, k3),
                toVolume(P3(pv[sel].pos.x, pv[sel].pos.y + up, pv[sel].pos.z), scale).let { P3(it.x, it.y + 0.05f, it.z) })
        }
        comets.labels(labels)
    }

    // labels are chips CENTRED on their point (20 sp, ~3 cm per line at 0.8 mm/dp): offsets clear the object
    private fun emLabels() {
        val v = em
        val ev = lockEvent
        lab(tr("☀ 太阳 · 方向真实（距离、大小未按比例）", "☀ Sun · true direction (distance & size not to scale)"), up(v.worldOf(v.sunNode), EarthMoonView.R_SUN * (if (v.locked) EarthMoonView.LOCK_SCALE else 1f) + 0.04f))
        val name = tr(Sky.PHASE_ZH[Sky.phaseIndex(v.elong)], Sky.PHASE_EN[Sky.phaseIndex(v.elong)])
        val dTop = up(EarthMoonView.DISC_POS, EarthMoonView.DISC_R * 1.38f + 0.05f)
        when {
            ev == null -> {
                val bin = Sky.phaseIndex(v.elong)
                lab(tr("从地球看（北在上）\n${Sky.PHASE_ZH[bin]} ${Sky.PHASE_EN[bin]}\n照亮 %.0f%% · 月龄 %.1f 天", "Seen from Earth (north up)\n$name · %.0f%% lit · %.1f days old").format(v.lit * 100, v.ageDays), dTop)
                lab(tr("🌕 满月位 · 捏 → 月全食", "🌕 Full-moon spot · pinch → lunar eclipse"), up(v.worldOf(v.mark(4)), 0.055f))
                lab(tr("🌑 新月位 · 捏 → 日全食", "🌑 New-moon spot · pinch → solar eclipse"), up(v.worldOf(v.mark(0)), -0.055f))
                lab(if (v.nodeSunDeg < 17) tr("交点线对准太阳 → 食季！", "Line of nodes points at the Sun → eclipse season!")
                    else tr("交点线 · 月球轨道倾斜 5.1°", "Line of nodes · Moon's orbit tilted 5.1°"), up(v.nodeLabelWorld, 0.04f))
                lab(tr("月球", "Moon"), up(v.worldOf(v.moonNode), 0.05f))
            }
            !ev.solar -> {
                val uf = v.lunarGeo?.umbraFraction ?: 0.0
                lab((if (uf >= 0.999) tr("从地球看：月全食（全食阶段）\n月面 %.0f%% 在本影中", "Seen from Earth: total phase\n%.0f%% of the Moon in the umbra")
                     else if (uf > 0) tr("从地球看：偏食阶段\n月面 %.0f%% 在本影中", "Seen from Earth: partial phase\n%.0f%% of the Moon in the umbra")
                     else tr("从地球看：还在半影里\n月面 %.0f%% 在本影中", "Seen from Earth: penumbral phase\n%.0f%% of the Moon in the umbra")).format(uf * 100), dTop)
                lab(tr("月球在地球本影里 → 古铜色", "Moon inside Earth's umbra → copper red"), up(v.worldOf(v.moonNode), 0.06f))
                lab(tr("地球本影 / 半影（截面按真实比例）", "Earth's umbra / penumbra (cross-section to scale)"), v.rigToWorld(EarthMoonView.D_MOON * 1.2f, -EarthMoonView.R_E * 1.75f, 0f))
                lab(tr("交点线指向太阳：只有这时才有食", "Line of nodes points at the Sun: only then eclipses"), up(v.nodeSunSideWorld, 0.045f))
            }
            else -> {
                val l = v.local
                lab(tr("从食甚点（%s）看\n太阳被遮 %.0f%%", "Seen from greatest eclipse (%s)\nSun %.0f%% covered").format(latLon(ev), (l?.obscuration ?: 0.0) * 100), dTop)
                lab(tr("月球挡在太阳前面", "The Moon in front of the Sun"), up(v.worldOf(v.moonNode), 0.06f))
                lab(tr("月影落在地球上（黑点 = 本影，已放大）", "The Moon's shadow on Earth (black dot = umbra, enlarged)"), up(v.worldOf(v.earthNode), EarthMoonView.R_E * EarthMoonView.LOCK_SCALE + 0.05f))
                lab(tr("交点线指向太阳：只有这时才有食", "Line of nodes points at the Sun: only then eclipses"), up(v.nodeFarSideWorld, 0.045f))
            }
        }
    }

    private fun scaleLabels() {
        val t = ts
        val sun = P3(ToScaleView.SUN_X, ToScaleView.LINE_Y, 0f)
        lab(tr("☀ 太阳 · 真实大小只有 0.5 mm", "☀ Sun · true size 0.5 mm"), P3(sun.x + 0.02f, sun.y + 0.06f, 0f))
        lab(tr("巨行星 · 放大镜 ×%.0f", "Giants · magnifier ×%.0f").format(ToScaleView.N_GIANT), P3(-0.40f, 0.43f, 0f))
        lab(tr("类地行星 · 放大镜 ×%.0f", "Rocky planets · magnifier ×%.0f").format(ToScaleView.N_ROCKY), P3(-0.675f, -0.165f, 0.06f))
        lab(tr("海王星 · 30 AU = 1.62 m（这把尺）", "Neptune · 30 AU = 1.62 m (this ruler)"), up(ToScaleView.BUBBLE_POS[7], t.bubbleR[7] + 0.05f))
        val sel = t.selected
        if (sel in 0..7) {
            val p = Solar.planets[sel]
            lab(tr("${p.chinese} ×%.0f\n距日 %.2f AU = %.1f cm", "${enName(p.name)} ×%.0f\n%.2f AU from the Sun = %.1f cm").format(ToScaleView.mag(sel), p.a, p.a * ToScaleView.M_PER_AU * 100),
                up(ToScaleView.BUBBLE_POS[sel], t.bubbleR[sel] + 0.05f))
        } else lab(tr("地球 · 1 AU = %.1f cm", "Earth · 1 AU = %.1f cm").format(ToScaleView.M_PER_AU * 100), up(ToScaleView.BUBBLE_POS[2], t.bubbleR[2] + 0.04f))
    }

    private fun sizeLabels() {
        val s = sz
        if (s.transitioning) return
        val (lc, lr) = SizeLineupView.LENS[s.stage]
        lab(if (s.stage < 2) tr("🔍 捏金色圈：放大看小的", "🔍 Pinch the gold ring: zoom in") else tr("↺ 捏金色圈：回到太阳", "↺ Pinch the gold ring: back to the Sun"), up(lc, lr + 0.05f))
        val sel = s.selected
        if (sel in 0..9) lab(bodyCard(sel), up(s.hitPos(sel), labelLift(sel) + 0.03f))
        for (i in SizeLineupView.PINCH[s.stage]) if (i != sel) lab(tr(ScaleFacts.nameZh(i), ScaleFacts.nameEn(i)), up(s.hitPos(i), labelLift(i)))
    }

    private fun labelLift(i: Int): Float {
        val r = sz.radiusAt(i, sz.stage)
        return when { i == SizeLineupView.SUN -> 0.06f; i == 5 -> r * 1.3f + 0.03f; else -> r + 0.035f }
    }

    private fun bodyCard(i: Int): String {
        val d = 2 * ScaleFacts.radiusKm(i)
        return tr("${ScaleFacts.nameZh(i)}\n平均直径 %,.0f km = %.2f 个地球\n体积 = %s 个地球", "${ScaleFacts.nameEn(i)}\nmean diameter %,.0f km = %.2f Earths\nvolume = %s Earths")
            .format(d, ScaleFacts.re(i), volText(ScaleFacts.earthVolumes(i)))
    }

    private fun volText(v: Double) = when { v >= 10 -> "%,.0f".format(v); v >= 1 -> "%.1f".format(v); else -> "%.3f (≈1/%.0f)".format(v, 1 / v) }

    // ================================================================ interaction + readout

    private val EM_TARGETS = setOf("EM_Sun", "EM_Moon", "EM_Earth", "EM_Phase_0", "EM_Phase_4")

    override fun isTarget(name: String) = name.startsWith("Planet_") || name == "Sun" || name == "CometIcon" || name in EM_TARGETS ||
        name.startsWith("TS_Bubble_") || name == "TS_Sun" || name.startsWith("SZ_Body_") || name == "SZ_Lens"

    // ---- drags: comets (throw), Earth (turn the close-up) ----
    override fun isDragTarget(name: String) = comets.isDragTarget(name) || name == "EM_Earth"
    override fun onDragStart(name: String) { if (name != "EM_Earth") comets.onDragStart(name) }
    override fun onDrag(name: String, dx: Float, dy: Float, dz: Float) {
        if (name == "EM_Earth") em.turn(dx * 150f) else comets.onDrag(dx, dy, dz) { edit(it) }
    }
    override fun onDragEnd(name: String) { if (name != "EM_Earth") comets.onDragEnd() }

    override fun reviewVariants() = mapOf(
        "comets" to { setMode(Mode.ORBITS); model.selected = -1; repeat(3) { comets.tapSpawn(); repeat(40) { update(1f / 60f) } } },
        "jupiter" to { setMode(Mode.ORBITS); model.selected = 4; colourDirty = true },
        "closeup" to { setMode(Mode.EARTH_MOON); exitLock(); model.jd = Sky.jd(2025, 9, 3, 12, 0); rateIdx = 0; applyRate(); update(0f) },
        "lunar" to { lunarNext = 0; startEclipse(false); update(0f) },
        "solar" to { solarNext = 0; startEclipse(true); update(0f) },
        "solar2027" to { solarNext = 1; startEclipse(true); update(0f) },
        // partial phases: the Earth's shadow biting into the Moon / the Moon biting into the Sun
        "lunarPartial" to { lunarNext = 0; startEclipse(false); model.jd -= 75.0 / 1440; update(0f) },
        "solarPartial" to { solarNext = 1; startEclipse(true); model.jd -= 50.0 / 1440; update(0f) },
        "toscale" to { setMode(Mode.TO_SCALE); ts.selected = 2 },
        "sizes1" to { setMode(Mode.SIZES); sz.go(0, false); sz.selected = -1 },
        "sizes2" to { setMode(Mode.SIZES); sz.go(1, false); sz.selected = 4 },
        "sizes3" to { setMode(Mode.SIZES); sz.go(2, false); sz.selected = 2 },
    )

    // ---- guided track. Key insight: phases are geometry (which part of the always-half-lit Moon faces us), and
    // eclipses are rare because the Moon's orbit is tilted 5.1° — only full/new moons near a node line up.
    // ---- background card (📖): context for someone who has never thought about how planets move
    override val about = About(listOf(
        About.Page(
            Bi("这个实验在做什么？", "What is this experiment?"),
            Bi("这是按真实轨道数据运行的太阳系，面板上有四种模式。「轨道」：八大行星绕太阳转，捏一颗行星会出现它的开普勒数据卡，捏 ☄ 还能甩出一颗彗星。「地月」：看月相，跳到真实的日食、月食。「真实比例」：大小和距离用同一把尺。「大小对比」：太阳和行星按真实大小排成一排。", "A solar system running on real orbital data, with four modes on the panel. “Orbits”: the eight planets circle the Sun — pinch one for its Kepler card, or pinch ☄ to throw a comet. “Earth–Moon”: the Moon's phases, and jumps to real eclipses. “To scale”: sizes and distances on one ruler. “Size lineup”: the Sun and planets side by side at true size."),
        ),
        About.Page(
            Bi("从圆到椭圆", "From circles to ellipses"),
            Bi("两千年来，人们相信天体走的是完美的圆。1543 年哥白尼把太阳放到中心，却仍然用圆，预测总有偏差。第谷用肉眼把火星的位置记了二十多年；开普勒接过这些数据反复计算，1609 年发现火星走的是椭圆，1619 年又找到周期和距离的关系。1687 年，牛顿用万有引力解释了这三条定律。", "For two thousand years, heavenly bodies were thought to move in perfect circles. In 1543 Copernicus put the Sun at the centre but kept the circles, and predictions still drifted. Tycho Brahe charted Mars by eye for over twenty years. Kepler worked through his data and in 1609 found that Mars moves on an ellipse; in 1619 he linked period to distance. In 1687 Newton showed that gravity explains all three laws."),
        ),
        About.Page(
            Bi("开普勒三定律", "Kepler's three laws"),
            Bi("① 行星绕太阳走椭圆，太阳在椭圆的一个焦点上。② 离太阳近时走得快，远时走得慢：行星和太阳的连线，相同时间扫过相同面积——就像花样滑冰转圈，手臂一收就转得更快。③ 周期 T（年）的平方除以平均距离 a（AU）的立方，对每颗行星都一样：T²/a³ ≈ 1。捏任何一颗行星，看卡片上的这个数。", "① Each planet moves on an ellipse with the Sun at one focus. ② It speeds up near the Sun and slows far away: the line to the Sun sweeps equal areas in equal times — like a spinning skater who turns faster with arms pulled in. ③ Period T (years) squared over mean distance a (AU) cubed is the same for every planet: T²/a³ ≈ 1. Pinch any planet and check its card."),
        ),
        About.Page(
            Bi("今天在哪里？", "Where is it today?"),
            Bi("同样的轨道计算，让人能提前几百年算准日食、月食的时刻——「地月」模式里的食就是这样算出来的。发射火星探测器要等约 26 个月一次的窗口；天文学家用第三定律，从行星的周期和距离称出恒星的质量。再记住一个尺度：如果太阳是 1 米的球，地球只有 9 毫米，在 107 米之外。", "The same orbit maths predicts eclipses centuries ahead, to the minute — that is how the eclipses in “Earth–Moon” are found. Probes to Mars wait for a launch window that opens about every 26 months, and astronomers use the third law to weigh a star from its planet's period and distance. And one sense of scale: if the Sun were a 1 m ball, Earth would be 9 mm across, 107 m away."),
        ),
    ))

    override val lesson: Lesson by lazy {
        Lesson(
            listOf(
                LessonStep(
                    say = Bi("这是太阳系全景。把面板上的模式切到「地月」，看看月亮。", "This is the whole solar system. Switch the panel's mode to “Earth–Moon” to look at the Moon."),
                    done = { mode == Mode.EARTH_MOON },
                    autoplay = { setMode(Mode.EARTH_MOON) },
                ),
                LessonStep(
                    say = Bi("先猜：月亮为什么有圆缺（月相）？", "Predict first: why does the Moon have phases?"),
                    options = listOf(Bi("地球的影子遮住了它", "Earth's shadow covers it"), Bi("我们看到亮面的角度在变", "We see its lit half from changing angles")),
                    answer = 1,
                ),
                LessonStep(
                    say = Bi("捏月球两次，跳到下两个月相；看右边「从地球看」的圆盘。", "Pinch the Moon twice to step two phases; watch the “seen from Earth” disc on the right."),
                    target = "EM_Moon",
                    onEnter = { phaseStepsAtStep = phaseSteps },
                    done = { mode == Mode.EARTH_MOON && phaseSteps - phaseStepsAtStep >= 2 },
                    autoplay = { if (mode != Mode.EARTH_MOON) setMode(Mode.EARTH_MOON); repeat(2) { onTap("EM_Moon") } },
                    reveal = true,
                    insight = Bi("月球永远有一半被太阳照亮；月相只是这一半有多少朝向我们。地球的影子与月相无关。", "Half the Moon is always sunlit; the phase is just how much of that half faces us. Earth's shadow plays no part."),
                ),
                LessonStep(
                    say = Bi("再猜：每个月都有满月，为什么不是每个月都有月食？", "Predict: there is a full moon every month — so why not a lunar eclipse every month?"),
                    options = listOf(Bi("地球影子太小", "Earth's shadow is too small"), Bi("月球轨道倾斜了 5°", "The Moon's orbit is tilted 5°"), Bi("满月时太阳被挡住", "The Sun is hidden at full moon")),
                    answer = 1,
                ),
                LessonStep(
                    say = Bi("捏金色的「满月位」，跳到一次真实的月全食。", "Pinch the gold “full moon” marker to jump to a real total lunar eclipse."),
                    target = "EM_Phase_4",
                    done = { lockEvent?.solar == false },
                    autoplay = { if (mode != Mode.EARTH_MOON) setMode(Mode.EARTH_MOON); onTap("EM_Phase_4") },
                    reveal = true,
                    insight = Bi("月球轨道倾斜 5.1°，多数满月从影子上下错过；只有交点对准太阳（食季，约每半年一次）才会有食。", "The orbit is tilted 5.1°, so most full moons pass above or below the shadow; eclipses need a node aimed at the Sun — about every six months."),
                ),
            ),
            challenge = Challenge(
                say = Bi("挑战：若太阳是 1 米的球，地球多大、在多远？切到「真实比例」，捏地球的放大泡。", "Challenge: if the Sun were a 1 m ball, how big and how far is Earth? Switch to “To scale” and pinch Earth's bubble."),
                progress = { if (mode != Mode.TO_SCALE) tr("先切到「真实比例」", "Switch to “To scale” first") else tr("已选：%s", "Selected: %s").format(if (ts.selected in 0..7) tr(Solar.planets[ts.selected].chinese, enName(Solar.planets[ts.selected].name)) else "—") },
                done = { mode == Mode.TO_SCALE && ts.selected == 2 },
                autoplay = { setMode(Mode.TO_SCALE); onTap("TS_Bubble_2") },
            ),
        )
    }

    override val tapTargets get() = when (mode) {
        Mode.ORBITS -> listOf(
            TapTarget("CometIcon", "☄ → 放一颗彗星", "☄ → launch a comet"),
            TapTarget("PlanetNext", "下一颗行星 ›", "Next planet ›"),
            TapTarget("Sun", "太阳 → 回到全景", "Sun → overview"),
        )
        Mode.EARTH_MOON -> listOf(
            TapTarget("EM_Moon", "月球 → 下一个月相", "Moon → next phase"),
            TapTarget("EM_Phase_4", "满月位 → 月全食", "Full moon → lunar eclipse") { lockEvent?.solar == false },
            TapTarget("EM_Phase_0", "新月位 → 日全食", "New moon → solar eclipse") { lockEvent?.solar == true },
            TapTarget("EM_Earth", "地球 → 转动视角", "Earth → turn view"),
            TapTarget("EM_Sun", "太阳 → 回到全景", "Sun → overview"),
        )
        Mode.TO_SCALE -> listOf(TapTarget("TS_Sun", "太阳", "Sun") { ts.selected == 9 }) +
            (0 until 8).map { i -> TapTarget("TS_Bubble_$i", Solar.planets[i].chinese, enName(Solar.planets[i].name)) { ts.selected == i } }
        Mode.SIZES -> SizeLineupView.PINCH[sz.stage].map { i -> TapTarget("SZ_Body_$i", ScaleFacts.nameZh(i), ScaleFacts.nameEn(i)) { sz.selected == i } } +
            TapTarget("SZ_Lens", "🔍 金色圈 → 下一阶段", "🔍 Gold ring → next stage")
    }

    override fun tapMessage(name: String): String? = when {
        name == "CometIcon" -> tr("☄ 彗星出发！捏住图标再甩，速度和方向由你的手决定", "☄ Comet away! Pinch the icon and fling — your hand sets speed and direction")
        name == "Sun" -> if (sunWasOverview) tr("已经是全景 —— 捏一颗行星（蓝圈），看它的开普勒数据", "Already the overview — pinch a planet (blue ring) for its Kepler card")
                         else tr("回到全景：所有轨道", "Overview: all orbits")
        name == "EM_Sun" -> tr("回到轨道全景", "Back to the orbits overview")
        name == "EM_Moon" -> tr("下一个月相：%s（%s）", "Next phase: %s (%s)").format(
            tr(Sky.PHASE_ZH[Sky.phaseIndex(Sky.elongationDeg(model.jd))], Sky.PHASE_EN[Sky.phaseIndex(Sky.elongationDeg(model.jd))]), SolarModel.dateString(model.jd))
        name == "EM_Phase_4" -> lockEvent?.let { tr("月全食 %s：月球整个进入地球本影，变成古铜色", "Total lunar eclipse %s: the whole Moon inside Earth's umbra, copper red").format(it.date) }
        name == "EM_Phase_0" -> lockEvent?.let { tr("日全食 %s：月球的影子落在 %s", "Total solar eclipse %s: the Moon's shadow falls on %s").format(it.date, tr(it.whereZh, it.whereEn)) }
        name == "EM_Earth" -> tr("视角转了 90°（也可以捏住地球左右拖动）", "View turned 90° (or pinch Earth and drag sideways)")
        name == "TS_Sun" -> tr("太阳：直径 139 万 km，在这把尺上只有 0.5 mm", "The Sun: 1.39 million km across — 0.5 mm on this ruler")
        name.startsWith("TS_Bubble_") -> name.removePrefix("TS_Bubble_").toIntOrNull()?.let { compareLine(it) }
        name == "SZ_Lens" -> tr("阶段 %d：%s", "Stage %d: %s").format(sz.stage + 1, STAGE_LABELS[sz.stage].drop(2))
        name.startsWith("SZ_Body_") -> name.removePrefix("SZ_Body_").toIntOrNull()?.let { i ->
            tr("%s：直径 %.2f 个地球 · 体积 %s 个地球", "%s: %.2f Earths across · %s Earths by volume").format(tr(ScaleFacts.nameZh(i), ScaleFacts.nameEn(i)), ScaleFacts.re(i), volText(ScaleFacts.earthVolumes(i)))
        }
        else -> null                                         // planets answer with their Kepler card
    }

    /** The last Sun tap found nothing to undo (user log: Sun pinched 7x in a row — it "did nothing"). */
    private var sunWasOverview = false

    override fun onTap(name: String) {
        when {
            name == "CometIcon" -> { comets.tapSpawn(); return }
            name == "EM_Sun" -> { setMode(Mode.ORBITS); model.selected = -1; colourDirty = true; return }
            name == "EM_Moon" -> { nextPhase(); return }
            name == "EM_Phase_4" -> { startEclipse(false); return }
            name == "EM_Phase_0" -> { startEclipse(true); return }
            name == "EM_Earth" -> { em.turn(90f); return }
            name == "TS_Sun" -> { ts.selected = 9; return }
            name.startsWith("TS_Bubble_") -> { ts.selected = name.removePrefix("TS_Bubble_").toIntOrNull() ?: -1; return }
            name == "SZ_Lens" -> { sz.next(); return }
            name.startsWith("SZ_Body_") -> { sz.selected = name.removePrefix("SZ_Body_").toIntOrNull() ?: -1; return }
        }
        if (name == "Sun") {
            sunWasOverview = model.selected !in 0..7
        }
        model.selected = when {
            name == "PlanetNext" -> if (model.selected >= 7) -1 else model.selected + 1
            name == "Sun" -> -1
            name.startsWith("Planet_") -> name.removePrefix("Planet_").toIntOrNull()?.let {
                if (it == 8) 2 else if (model.selected == it) -1 else it
            } ?: -1
            else -> model.selected
        }
        colourDirty = true
        Log.i(TAG, "tap $name -> selected ${model.selected}")
    }

    // ---- readouts (3 lines, each must fit the panel in both languages) ----

    private fun readout(): String = when (mode) {
        Mode.ORBITS -> orbitReadout()
        Mode.EARTH_MOON -> lockEvent?.let { if (it.solar) solarReadout(it) else lunarReadout(it) } ?: emReadout()
        Mode.TO_SCALE -> scaleReadout()
        Mode.SIZES -> sizeReadout()
    }

    private fun rateText(): String {
        val m = model
        if (abs(m.daysPerSecond) < 1e-9) return tr("暂停", "Paused")
        return if (mode == Mode.EARTH_MOON && lockEvent != null) tr("%+.0f 分钟/秒", "%+.0f min/s").format(m.daysPerSecond * 1440)
               else if (abs(m.daysPerSecond) < 1) tr("%+.1f 天/秒", "%+.1f days/s").format(m.daysPerSecond) else tr("%+.0f 天/秒", "%+.0f days/s").format(m.daysPerSecond)
    }

    private fun timeString(jd: Double): String {
        val f = (jd + 0.5) - floor(jd + 0.5); val mins = (f * 1440).toInt()
        return "%02d:%02d".format(mins / 60, mins % 60)
    }

    private fun orbitReadout(): String {
        val m = model
        val date = SolarModel.dateString(m.jd)
        val rate = rateText()
        val sel = m.selected
        if (sel !in 0..7 && comets.lastInfo.isNotEmpty()) {
            val ph = Moon.phaseDeg(m.jd)
            val lit = Moon.illuminatedFraction(m.jd) * 100
            return tr("$date   ·   $rate   ·   月相 %.0f° · %.0f%% 照亮", "$date   ·   $rate   ·   Moon %.0f° · %.0f%% lit").format(ph, lit) +
                "\n" + comets.lastInfo + "\n" + tr("再捏住 ☄ 图标可以再抛一颗（最多 ${CometSandbox.MAX} 颗）", "Pinch the ☄ icon again to throw another (up to ${CometSandbox.MAX})")
        }
        if (sel !in 0..7) {
            val ph = Moon.phaseDeg(m.jd)
            val lit = Moon.illuminatedFraction(m.jd) * 100
            return tr("$date   ·   $rate\n月相 %.0f° · %.0f%% 照亮   ·   选「地月」看月相、日月食；「真实比例」「大小对比」看宇宙有多空、太阳有多大\n捏任一行星（蓝圈）：高亮它的轨道，旁边出现它的开普勒数据卡",
                "$date   ·   $rate\nMoon phase %.0f° · %.0f%% lit   ·   “Earth–Moon”: phases & eclipses · “To scale” / “Size lineup”: how empty, how big\nPinch a planet (blue ring): its orbit lights up and its Kepler card appears").format(ph, lit)
        }
        val p = Solar.planets[sel]
        val years = p.periodDays / 365.256
        val k3 = years * years / (p.a * p.a * p.a)
        return (tr("$date   ·   $rate\n${p.chinese} ${p.name}   距日 %.3f AU   v = %.2f km/s\n", "$date   ·   $rate\n${enName(p.name)}   %.3f AU from the Sun   v = %.2f km/s\n") +
            tr("T = %.1f d (%.2f yr)   T²/a³ = %.3f   直径 %.2f × 地球", "T = %.1f d (%.2f yr)   T²/a³ = %.3f   diameter %.2f × Earth"))
            .format(m.sunDistanceAu(sel), m.speedKms(sel), p.periodDays, years, k3, p.radiusKm / Solar.EARTH_RADIUS_KM)
    }

    private fun emReadout(): String {
        val v = em; val m = model
        val bin = Sky.phaseIndex(v.elong)
        val name = tr(Sky.PHASE_ZH[bin], Sky.PHASE_EN[bin])
        val l1 = tr("%s %s   ·   %s   ·   月相：%s · 照亮 %.0f%% · 月龄 %.1f 天 · 距地 %,.0f km",
            "%s %s   ·   %s   ·   %s · %.0f%% lit · %.1f days old · %,.0f km away")
            .format(SolarModel.dateString(m.jd), timeString(m.jd), rateText(), name, v.lit * 100, v.ageDays, v.distKm)
        val lg = v.lunarGeo
        val above = v.moonLatDeg >= 0
        // positional arguments: the two languages put "above/below" and the angle in different orders
        val l2s = tr("月球在黄道面%1\$s %2\$.1f°；而地球的影子只有 %3\$.1f° 宽、太阳只有 0.5° 宽 —— 对不准就没有食",
            "The Moon is %2\$.1f° %1\$s the ecliptic; Earth's shadow is only %3\$.1f° wide, the Sun 0.5° — miss and there is no eclipse")
            .format(if (above) tr("上方", "above") else tr("下方", "below"), abs(v.moonLatDeg), 2 * (lg?.umbraDeg ?: 0.7))
        val sg = v.solarGeo
        val l3 = when {
            lg != null && lg.umbraFraction > 0 -> tr("🌕 此刻正在发生月食：月面 %.0f%% 在地球本影里", "🌕 A lunar eclipse is happening now: %.0f%% of the Moon in Earth's umbra").format(lg.umbraFraction * 100)
            sg != null && sg.anywhere -> tr("🌑 此刻正在发生日食：月影离地心 %.2f 个地球半径", "🌑 A solar eclipse is happening now: shadow axis %.2f Earth radii from the centre").format(abs(sg.gamma))
            v.nodeSunDeg < 17 -> tr("⚠ 食季：交点线离日地连线只有 %.0f°，这前后的满月 / 新月会发生日月食", "⚠ Eclipse season: the line of nodes is only %.0f° from the Sun — nearby full / new moons bring eclipses").format(v.nodeSunDeg)
            else -> tr("交点线离日地连线 %.0f°：要小于约 17° 才进入食季（一年两次）—— 所以不是每个月都有日月食", "Line of nodes %.0f° from the Sun line: below ~17° = eclipse season (twice a year) — so not every month").format(v.nodeSunDeg)
        }
        return "$l1\n$l2s\n$l3"
    }

    private fun lunarReadout(ev: Eclipse.Event): String {
        val m = model; val g = em.lunarGeo ?: Eclipse.lunar(m.jd)
        val dtMin = (m.jd - ev.jd) * 1440
        val l1 = tr("月全食 %s · NASA 目录：γ = %.4f · 本影食分 %.3f · 全食 %.0f 分钟 · %s", "Total lunar eclipse %s · NASA: γ = %.4f · umbral mag. %.3f · totality %.0f min · %s")
            .format(ev.date, ev.gamma, ev.magnitude, ev.totalMin, rateText())
        val l2 = tr("模型 %s TT（食甚 %+.0f 分钟）：月心离地影轴 %.2f 个地球半径 · 本影食分 %.2f · 月面 %.0f%% 在本影", "Model %s TT (greatest %+.0f min): Moon %.2f Earth radii from the axis · umbral mag. %.2f · %.0f%% in the umbra")
            .format(timeString(m.jd), dtMin, abs(g.gamma), g.umbralMag, g.umbraFraction * 100)
        val l3 = tr("古铜色 = 地球大气折射进影子的日落红光 · 天体放大、距离压缩，影子截面（以地球半径计）按真实比例",
            "Copper = sunset light bent into the shadow by Earth's air · sizes enlarged; shadow cross-section to scale")
        return "$l1\n$l2\n$l3"
    }

    private fun solarReadout(ev: Eclipse.Event): String {
        val m = model; val g = em.solarGeo ?: Eclipse.solar(m.jd)
        val totS = Math.round(ev.totalMin * 60).toInt(); val mins = totS / 60; val secs = totS % 60
        val l1 = tr("日全食 %s · NASA 目录：γ = %.4f · 食分 %.4f · 全食 %d 分 %d 秒 · 全食带 %.0f km 宽 · %s",
            "Total solar eclipse %s · NASA: γ = %.4f · magnitude %.4f · totality %d m %d s · path %.0f km · %s")
            .format(ev.date, ev.gamma, ev.magnitude, mins, secs, ev.pathKm, rateText())
        val cov = (em.local?.obscuration ?: 0.0) * 100
        val dtMin = (m.jd - ev.jd) * 1440
        val l2 = if (g.anywhere)
            tr("模型 %s TT（食甚 %+.0f 分钟）：月影轴离地心 %.2f 个地球半径 · 在 %s 太阳被遮 %.0f%%", "Model %s TT (greatest %+.0f min): shadow axis %.2f Earth radii from the centre · at %s the Sun is %.0f%% covered")
                .format(timeString(m.jd), dtMin, abs(g.gamma), latLon(ev), cov)
        else tr("模型 %s TT（食甚 %+.0f 分钟）：月影还没落到地球上", "Model %s TT (greatest %+.0f min): the Moon's shadow misses the Earth").format(timeString(m.jd), dtMin)
        val l3 = tr("全食带：%s · 只有本影扫过的窄带能看到全食（黑点已放大）", "Totality path: %s · only the narrow strip under the umbra (dot enlarged)")
            .format(tr(ev.whereZh, ev.whereEn))
        return "$l1\n$l2\n$l3"
    }

    private fun latLon(ev: Eclipse.Event) = "%.1f°%s %.1f°%s".format(abs(ev.latDeg), if (ev.latDeg >= 0) "N" else "S", abs(ev.lonDeg), if (ev.lonDeg >= 0) "E" else "W")

    private fun sizeText(mm: Double) = if (mm < 10) "%.1f mm".format(mm) else "%.1f cm".format(mm / 10)
    private fun distText(m: Double) = if (m < 1000) "%.0f m".format(m) else "%.1f km".format(m / 1000)
    private fun lightText(s: Double) = when { s < 3600 -> tr("%.1f 分钟", "%.1f min").format(s / 60); else -> tr("%.1f 小时", "%.1f h").format(s / 3600) }

    /** "If the Sun were a 1 m ball: Earth is a 9 mm pea, 107 m away" — computed from the real data. */
    private fun compareLine(i: Int): String {
        val p = Solar.planets[i]; val th = ScaleFacts.THINGS[i]
        return tr("如果太阳是 1 m 的球：%s是 %s 的%s，在 %s 外", "If the Sun were a 1 m ball, %s would be %s across (%s), %s away")
            .format(tr(p.chinese, enName(p.name)), sizeText(ScaleFacts.mmIfSunIsMetre(i)), tr(th.zh, th.en), distText(ScaleFacts.metresIfSunIsMetre(p.a)))
    }

    private fun scaleReadout(): String {
        val sel = ts.selected
        val l1 = tr("真实比例：大小和距离用同一把尺 · 1 AU = %.1f cm · 太阳直径 0.50 mm，地球只有 4.6 μm（4.6 微米）",
            "To scale: sizes and distances on one ruler · 1 AU = %.1f cm · the Sun is 0.50 mm across, Earth just 4.6 μm").format(ToScaleView.M_PER_AU * 100)
        if (sel == 9) {
            val proxima = 4.2465 * 9.4607e12 / (2 * ScaleFacts.SUN_KM) / 1000.0     // km, if the Sun were 1 m
            return l1 + "\n" + tr("如果太阳是 1 m 的球：地球在 108 m 外，海王星在 3.2 km 外，最近的恒星（比邻星）在 %,.0f km 外",
                "If the Sun were a 1 m ball: Earth 108 m away, Neptune 3.2 km, the nearest star (Proxima) %,.0f km away").format(proxima) +
                "\n" + tr("几乎全是空的：行星只占海王星轨道以内空间的不到一万亿分之一 · 捏一个放大镜换行星", "Almost all empty: planets fill under a trillionth of the space inside Neptune's orbit · pinch a bubble")
        }
        val i = if (sel in 0..7) sel else 2
        val p = Solar.planets[i]
        val l3 = tr("光从太阳到%s要 %s · 放大镜：类地行星 ×%.0f，巨行星 ×%.0f · 捏一个放大镜换行星",
            "Sunlight reaches %s in %s · magnifiers: rocky ×%.0f, giants ×%.0f · pinch a bubble to switch planets")
            .format(tr(p.chinese, enName(p.name)), lightText(ScaleFacts.lightSeconds(p.a)), ToScaleView.N_ROCKY, ToScaleView.N_GIANT)
        return "$l1\n${compareLine(i)}\n$l3"
    }

    private fun sizeReadout(): String {
        val s = sz
        val k = SizeLineupView.K[s.stage] * 100
        val l1 = tr("大小对比 · 阶段 %d/3：%s · 全部天体同一比例（1 个地球半径 = %.2f cm）", "Size lineup · stage %d/3: %s · every body on one scale (1 Earth radius = %.2f cm)")
            .format(s.stage + 1, listOf(tr("太阳 vs 巨行星", "the Sun vs the giants"), tr("巨行星 vs 其他", "giants vs the rest"), tr("岩石行星", "the rocky worlds"))[s.stage], k)
        val l2 = when (s.stage) {
            0 -> tr("太阳直径 = %.0f 个地球 ≈ %.0f 个木星；体积能装下 %.2f 百万个地球", "The Sun is %.0f Earths ≈ %.0f Jupiters across; it holds %.2f million Earths by volume")
                .format(ScaleFacts.re(9), ScaleFacts.re(9) / ScaleFacts.re(4), ScaleFacts.earthVolumes(9) / 1e6)
            1 -> tr("木星直径 = %.1f 个地球（体积 %,.0f 个），土星 %.1f 个（%,.0f 个）；天王星 %.1f、海王星 %.1f 个", "Jupiter is %.1f Earths across (%,.0f by volume), Saturn %.1f (%,.0f); Uranus %.1f, Neptune %.1f")
                .format(ScaleFacts.re(4), ScaleFacts.earthVolumes(4), ScaleFacts.re(5), ScaleFacts.earthVolumes(5), ScaleFacts.re(6), ScaleFacts.re(7))
            else -> tr("金星几乎和地球一样大（%.2f）；火星约一半（%.2f）；水星 %.2f；月球 %.2f ≈ 地球的 1/4", "Venus is almost Earth's twin (%.2f); Mars about half (%.2f); Mercury %.2f; the Moon %.2f ≈ a quarter of Earth")
                .format(ScaleFacts.re(1), ScaleFacts.re(3), ScaleFacts.re(0), ScaleFacts.re(8))
        }
        val sel = s.selected
        val l3 = if (sel in 0..9) tr("%s：平均直径 %,.0f km = %.2f 个地球 · 体积 = %s 个地球", "%s: mean diameter %,.0f km = %.2f Earths · volume = %s Earths")
            .format(tr(ScaleFacts.nameZh(sel), ScaleFacts.nameEn(sel)), 2 * ScaleFacts.radiusKm(sel), ScaleFacts.re(sel), volText(ScaleFacts.earthVolumes(sel)))
        else tr("捏一个天体：看它的直径、能装下几个地球 · 捏金色圆圈：%s", "Pinch a body: its diameter and how many Earths fit inside · pinch the gold ring: %s")
            .format(if (s.stage < 2) tr("放大看小的", "zoom in on the small ones") else tr("回到太阳", "back to the Sun"))
        return "$l1\n$l2\n$l3"
    }

    @Composable
    override fun Panel() {
        val m = model
        // the panel reads/writes the demo's own state (no copies): a pinch and a button stay in sync
        var rateIdx by FieldRef(::rateIdx)
        var reverse by FieldRef(::reverse)
        var modeI by FieldRef(::modeIdx)
        var stage by FieldRef(::stageIdx)
        var spread by FieldRef(m::spread)
        var size by FieldRef(m::sizeExag)
        var moon by FieldRef(m::showMoon)
        var zoom by FieldRef(m::zoom)
        val md = Mode.entries[modeI]
        val rateLabels = when { md == Mode.EARTH_MOON && lockEvent != null -> LOCK_RATE_LABELS; md == Mode.EARTH_MOON -> EM_RATE_LABELS; else -> RATE_LABELS }

        LabPanel(readout) {
            LabRow {
                SegmentControl {
                    MODE_LABELS.forEachIndexed { i, label -> SegmentItem(selected = modeI == i, onClick = { modeI = i }, title = { Text(label) }) }
                }
                Spacer(Modifier.width(20.dp))
                when (md) {
                    Mode.ORBITS, Mode.EARTH_MOON -> SegmentControl {
                        rateLabels.forEachIndexed { i, label -> SegmentItem(selected = rateIdx == i, onClick = { rateIdx = i; applyRate() }, title = { Text(label) }) }
                    }
                    Mode.SIZES -> SegmentControl {
                        STAGE_LABELS.forEachIndexed { i, label -> SegmentItem(selected = stage == i, onClick = { stage = i }, title = { Text(label) }) }
                    }
                    Mode.TO_SCALE -> {}
                }
            }
            when (md) {
                Mode.ORBITS -> {
                    LabSlider(tr("真实比例", "True scale"), spread.toFloat(), 0f..1f, tr("均匀间距", "Even spacing")) { spread = it.toDouble(); m.spread = spread }
                    LabSlider(tr("行星大小", "Planet size"), size.toFloat(), 1f..4f, "×%.1f".format(size)) { size = it.toDouble(); m.sizeExag = size }
                    LabRow {
                        LabSlider(tr("整体缩放", "Zoom"), zoom.toFloat(), 0.6f..3f, "×%.1f".format(zoom)) { zoom = it.toDouble(); m.zoom = zoom }
                        Spacer(Modifier.width(20.dp))
                        LabSwitch(tr("倒放", "Reverse"), reverse) { reverse = it; applyRate() }
                        LabSwitch(tr("月球", "Moon"), moon) { moon = it; m.showMoon = it }
                        Button(onClick = { m.jd = 2451545.0; m.selected = -1; colourDirty = true }) { Text(tr("回到 2000-01-01", "Back to 2000-01-01")) }
                    }
                }
                Mode.EARTH_MOON -> LabRow {
                    LabSwitch(tr("倒放", "Reverse"), reverse) { reverse = it; applyRate() }
                    Button(onClick = { goToday() }) { Text(tr("今天的月亮", "Tonight's Moon")) }
                    if (lockEvent != null) Button(onClick = { exitLock() }) { Text(tr("退出日月食视图", "Leave eclipse view")) }
                }
                else -> {}
            }
        }
    }

    // ================================================================ bakes

    private class Bakes(
        val sunA: Image, val sunB: Image, val limb: Image, val corona: Image, val light: Image,
        val ring: Image, val planets: List<Image>, val moon: Image,
    ) {
        fun all(): List<Image> = listOf(sunA, sunB, limb, corona, light, ring) + planets + moon

        companion object {
            fun from(l: List<Image>) = Bakes(l[0], l[1], l[2], l[3], l[4], l[5], l.subList(6, 14), l[14])

            fun make(): Bakes {
                val p = Palette
                val sunRaw = ProcTex.bakeSunChannels(512, 256, 20260731)
                return Bakes(
                    sunA = ProcTex.bakeSunPhotosphere(512, 256, 0, raw = sunRaw),
                    sunB = ProcTex.bakeSunPhotosphere(512, 256, 1, raw = sunRaw),
                    limb = ProcTex.bakeLimbShell(256),
                    corona = ProcTex.bakeCoronaDisc(256),
                    light = ProcTex.bakeLightShell(256),
                    ring = ProcTex.bakeRingProfile(256),
                    planets = (0 until 8).map { i ->
                        val tw = if (i == 4 || i == 5) 512 else 256
                        if (i == 2) ProcTex.bakeEarth(512, 256)
                        else ProcTex.bakePlanet(tw, tw / 2, p.deep[i], p.mid[i], p.high[i], p.band[i], 9000 + i * 37, p.caps[i])
                    },
                    moon = ProcTex.bakePlanet(160, 80, p.moonDeep, p.moonMid, p.moonHigh, 0f, 20260802),
                )
            }
        }
    }
}
