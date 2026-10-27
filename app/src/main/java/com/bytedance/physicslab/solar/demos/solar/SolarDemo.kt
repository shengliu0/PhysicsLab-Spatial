// 太阳系 · 开普勒定律 — the solar system as a SpecDemo, so it renders through the same adapter as
// every lab demo AND can be reviewed headless on the Mac (SoftRender).
//
// RENDERING WITHOUT CUSTOM SHADERS (see ProcTex header):
//   sun     = unlit photosphere A (opaque) + photosphere B (transparent, opacity = boil phase)
//             + viewer-facing limb-darkening shell + viewer-facing additive corona disc
//   planets = unlit albedo + SUN-facing lighting shell (black, alpha = 1 - Lambert)
// The sun is the only light in the solar system, so nothing here uses scene lights or IBL — the
// room's lighting cannot leak onto the planets, and day/night is exact at every position.
// Physics: core/SolarCore (JPL elements, parity-tested against the Unity C#) + core/SolarModel.

package com.bytedance.physicslab.solar.demos.solar

import android.util.Log
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bytedance.physicslab.solar.core.Moon
import com.bytedance.physicslab.solar.core.P3
import com.bytedance.physicslab.solar.core.Solar
import com.bytedance.physicslab.solar.core.SolarModel
import com.bytedance.physicslab.solar.lab.BakeCache
import com.bytedance.physicslab.solar.lab.Q
import com.bytedance.physicslab.solar.lab.SpecDemo
import com.bytedance.physicslab.solar.lab.TapTarget
import com.bytedance.physicslab.solar.lab.ui.FieldRef
import com.bytedance.physicslab.solar.lab.tr
import com.bytedance.physicslab.solar.lab.spec.Blend
import com.bytedance.physicslab.solar.lab.spec.LabelSpec
import com.bytedance.physicslab.solar.lab.spec.Mat
import com.bytedance.physicslab.solar.lab.spec.Node
import com.bytedance.physicslab.solar.lab.ui.LabPanel
import com.bytedance.physicslab.solar.lab.ui.LabRow
import com.bytedance.physicslab.solar.lab.ui.LabSlider
import com.bytedance.physicslab.solar.lab.ui.LabSwitch
import com.bytedance.physicslab.solar.render.Image
import com.bytedance.physicslab.solar.render.MeshData
import com.bytedance.physicslab.solar.render.MeshGen
import com.bytedance.physicslab.solar.render.Palette
import com.bytedance.physicslab.solar.render.ProcTex
import com.bytedance.physicslab.solar.render.Rgb
import com.bytedance.physicslab.solar.lab.ui.Button
import com.bytedance.physicslab.solar.lab.ui.SegmentControl
import com.bytedance.physicslab.solar.lab.ui.SegmentItem
import com.pico.spatial.ui.design.Text
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

/** Three speeds + pause (the user asked for slow/medium/fast levels so motion can be studied). */
private val RATES = doubleArrayOf(0.0, 2.0, 15.0, 60.0)
private val RATE_LABELS get() = listOf(tr("暂停", "Pause"), tr("慢 2天/秒", "2 days/s"), tr("中 15天/秒", "15 days/s"), tr("快 60天/秒", "60 days/s"))

/** "MERCURY" -> "Mercury" (core keeps the JPL upper-case name as its identity). */
private fun enName(upper: String) = upper.lowercase().replaceFirstChar { it.uppercase() }

object SolarDemo : SpecDemo() {
    override val id = "solar"
    override val title = "太阳系 · 开普勒定律"  // i18n-ok (zh half of DemoRegistry.titleOf; English in TITLE_EN)
    override val credit = "Kepler · 1609"

    private const val TAG = "SolarScene"
    private const val HIT_MIN_M = 0.045f      // world metres, at any zoom
    /** Scene magnification inside the 2700 dp (2.16 m) volume: Neptune's 0.78 m orbit -> 1.01 m. */
    const val BASE_SCALE = 1.3f
    /**
     * Earth-Moon close-up: 5x (Moon orbit ~0.56 m across). 8x was too big — on the headset the
     * Moon's orbit swept down into the control panel's sliders. The focus point sits in the free
     * space between the top bar and the panel, a little toward the viewer.
     */
    private const val CLOSEUP_ZOOM = 5.0
    private val CLOSEUP_AT = P3(0f, 0.15f, 0.20f)
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

    private var builtSpread = -1.0
    private var builtScale = -1f
    private var builtSize = -1.0
    private var builtMoonR = -1f
    private var followWas = false
    private var clock = 0.0
    private var colourDirty = true
    private var statFrames = 0; private var statT = 0.0; private var statWorst = 0f

    // ================================================================ build

    override fun buildSpec(cacheDir: File): Node {
        pv.clear(); builtSpread = -1.0; builtScale = -1f; builtSize = -1.0; builtMoonR = -1f
        followWas = false; colourDirty = true; clock = 0.0
        val t0 = System.nanoTime()
        val b = Bakes.from(BakeCache.load(cacheDir, "solar", 4) { Bakes.make().all() })
        Log.i(TAG, "bakes ready in ${"%.0f".format((System.nanoTime() - t0) / 1e6)} ms (${BakeCache.lastSource})")
        coronaTex = b.corona

        // the solar root is tilted/scaled/translated; an untransformed outer root holds volume-space
        // things (the ☄ comet icon) so they do not swing with the zoom or the close-up
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
        return outer
    }

    private lateinit var outer: Node
    lateinit var comets: CometSandbox
        private set

    /** volume <-> sun-root conversions for the comet sandbox (tilt, scale, close-up translation). */
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
    private fun quad(size: Float): MeshData {
        val h = size / 2
        return MeshData(
            floatArrayOf(-h, -h, 0f, h, -h, 0f, h, h, 0f, -h, h, 0f),
            floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f),
            floatArrayOf(0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f),
            intArrayOf(0, 1, 2, 0, 2, 3))
    }

    // ================================================================ per frame

    override fun update(dt: Float) {
        clock += dt
        val m = model
        m.step(dt.toDouble())

        val scale = (BASE_SCALE * m.zoom * if (m.followEarth) CLOSEUP_ZOOM else 1.0).toFloat()
        val rescaled = abs(scale - builtScale) > 1e-3f
        val rebuild = abs(m.spread - builtSpread) > 0.004 || rescaled
        val resize = abs(m.sizeK - builtSize) > 0.005
        if (rescaled) { sroot.uniformScale(scale); sroot.rot = TILT; builtScale = scale }

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

        // ---- close-up: translate the root so Earth sits at the volume centre ----
        if (m.followEarth) {
            val e = pv[2].pos
            val r = TILT.rotate(floatArrayOf(e.x * scale, e.y * scale, e.z * scale))
            sroot.pos = P3(CLOSEUP_AT.x - r[0], CLOSEUP_AT.y - r[1], CLOSEUP_AT.z - r[2])
        } else if (rescaled || followWas) sroot.pos = P3(0f, LIFT, 0f)
        if (followWas != m.followEarth) applyCloseupVisibility(m.followEarth)
        followWas = m.followEarth

        if (rebuild) builtSpread = m.spread
        if (resize || rebuild) builtSize = m.sizeK

        comets.update(dt) { meshEdited(it) }
        readoutText = readout()
        updateLabels(scale)
        comets.labels(labels)

        statFrames++; statT += dt
        if (statT >= 5.0) {
            Log.i(TAG, "tick %.1f Hz, worst dt %.1f ms, date %s, spread %.2f, selected %d".format(
                statFrames / statT, statWorst * 1000, SolarModel.dateString(m.jd), m.spread, m.selected))
            statFrames = 0; statT = 0.0; statWorst = 0f
        }
        if (dt > statWorst) statWorst = dt
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
            meshEdited(moonOrbitMesh)
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
        meshEdited(v.orbitMesh)
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

    /** Close-up = the Earth-Moon lesson: only Earth, Moon, their orbits and the Sun (the lamp). */
    private fun applyCloseupVisibility(closeup: Boolean) {
        for (i in 0 until 8) {
            val keep = !closeup || i == 2
            pv[i].root.visible = keep || i == 2
            pv[i].orbit.visible = keep
        }
    }

    private fun updateLabels(scale: Float) {
        val m = model
        labels.clear()
        val sel = m.selected
        if (sel in 0..7) {
            // the answer to "I pinched a planet — now what?": its Kepler card, next to it
            val p = Solar.planets[sel]
            val up = (m.bodyR(sel).toFloat() * 1.6f + 0.012f)
            val years = p.periodDays / 365.256
            val k3 = years * years / (p.a * p.a * p.a)
            labels += LabelSpec(tr("${p.chinese} ${p.name}\n距日 %.2f AU · 公转 %.2f 年\nT²/a³ = %.3f（开普勒第三定律）",
                "${enName(p.name)}\n%.2f AU from the Sun · %.2f yr orbit\nT²/a³ = %.3f (Kepler's third law)").format(m.sunDistanceAu(sel), years, k3),
                toVolume(P3(pv[sel].pos.x, pv[sel].pos.y + up, pv[sel].pos.z), scale).let { P3(it.x, it.y + 0.05f, it.z) })
        }
        if (m.followEarth && moonPivot.visible) {
            val e = pv[2].pos
            labels += LabelSpec(tr("地球", "Earth"), toVolume(P3(e.x, e.y + m.bodyR(2).toFloat() * 2.2f, e.z), scale))
            val mo = moonBody.pos
            val lit = Moon.illuminatedFraction(m.jd) * 100
            labels += LabelSpec(tr("月球 %.0f%% 照亮", "Moon %.0f%% lit").format(lit),
                toVolume(P3(e.x + mo.x, e.y + mo.y + builtMoonR * 2.6f, e.z + mo.z), scale))
            // the Sun is far outside the volume at this zoom: say where the light comes from
            val dl = kotlin.math.sqrt(e.x * e.x + e.y * e.y + e.z * e.z).coerceAtLeast(1e-6f)
            val reach = 0.30f / scale
            labels += LabelSpec(tr("☀ 阳光从这边来", "☀ Sunlight from this side"),
                toVolume(P3(e.x - e.x / dl * reach, e.y - e.y / dl * reach, e.z - e.z / dl * reach), scale))
        }
    }

    // ================================================================ interaction + readout

    override fun isTarget(name: String) = name.startsWith("Planet_") || name == "Sun" || name == "CometIcon"

    // ---- comets: pinch-and-throw (drag), or tap / panel button for a gentle launch ----
    override fun isDragTarget(name: String) = comets.isDragTarget(name)
    override fun onDragStart(name: String) = comets.onDragStart(name)
    override fun onDrag(name: String, dx: Float, dy: Float, dz: Float) = comets.onDrag(dx, dy, dz) { meshEdited(it) }
    override fun onDragEnd(name: String) = comets.onDragEnd()

    override fun reviewVariants() = mapOf(
        "comets" to { model.followEarth = false; model.selected = -1; repeat(3) { comets.tapSpawn(); repeat(40) { update(1f / 60f) } } },
        "closeup" to { model.followEarth = true },
        "jupiter" to { model.followEarth = false; model.selected = 4; colourDirty = true },
    )

    /** Panel speed level (index into RATES) and direction — demo state, so the panel never resets. */
    var rateIdx = 2
    var reverse = false

    override val tapTargets get() = listOf(
        TapTarget("CometIcon", "☄ → 放一颗彗星", "☄ → launch a comet"),
        TapTarget("PlanetNext", "下一颗行星 ›", "Next planet ›"),
        TapTarget("Sun", "太阳 → 回到全景", "Sun → overview"),
    )

    override fun tapMessage(name: String): String? = when {
        name == "CometIcon" -> tr("☄ 彗星出发！捏住图标再甩，速度和方向由你的手决定", "☄ Comet away! Pinch the icon and fling — your hand sets speed and direction")
        name == "Sun" -> if (sunWasOverview) tr("已经是全景 —— 捏一颗行星（蓝圈），看它的开普勒数据", "Already the overview — pinch a planet (blue ring) for its Kepler card")
                         else tr("回到全景：所有轨道", "Overview: all orbits")
        else -> null                                         // planets answer with their Kepler card
    }

    /** The last Sun tap found nothing to undo (user log: Sun pinched 7x in a row — it "did nothing"). */
    private var sunWasOverview = false

    override fun onTap(name: String) {
        if (name == "CometIcon") { comets.tapSpawn(); return }
        if (name == "Sun") {
            // "overview" = no selection AND no Earth-Moon close-up
            sunWasOverview = model.selected !in 0..7 && !model.followEarth
            model.followEarth = false
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

    private fun readout(): String {
        val m = model
        val date = SolarModel.dateString(m.jd)
        val rate = if (abs(m.daysPerSecond) < 1e-4) tr("暂停 paused", "Paused") else tr("%+.0f 天/秒", "%+.0f days/s").format(m.daysPerSecond)
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
            return tr("$date   ·   $rate\n月相 %.0f° · %.0f%% 照亮   ·   打开「地月特写」看月相与昼夜\n捏任一行星（蓝圈）：高亮它的轨道，旁边出现它的开普勒数据卡",
                "$date   ·   $rate\nMoon phase %.0f° · %.0f%% lit   ·   “Close-up” shows the Earth–Moon view\nPinch a planet (blue ring): its orbit lights up and its Kepler card appears").format(ph, lit)
        }
        val p = Solar.planets[sel]
        val years = p.periodDays / 365.256
        val k3 = years * years / (p.a * p.a * p.a)
        return (tr("$date   ·   $rate\n${p.chinese} ${p.name}   距日 %.3f AU   v = %.2f km/s\n", "$date   ·   $rate\n${enName(p.name)}   %.3f AU from the Sun   v = %.2f km/s\n") +
            tr("T = %.1f d (%.2f yr)   T²/a³ = %.3f   直径 %.2f × 地球", "T = %.1f d (%.2f yr)   T²/a³ = %.3f   diameter %.2f × Earth"))
            .format(m.sunDistanceAu(sel), m.speedKms(sel), p.periodDays, years, k3, p.radiusKm / Solar.EARTH_RADIUS_KM)
    }

    @Composable
    override fun Panel() {
        val m = model
        // the panel reads/writes the demo's own state (no copies): a pinch and a button stay in sync
        var rateIdx by FieldRef(::rateIdx)
        var reverse by FieldRef(::reverse)
        var spread by FieldRef(m::spread)
        var size by FieldRef(m::sizeExag)
        var moon by FieldRef(m::showMoon)
        var zoom by FieldRef(m::zoom)
        var closeup by FieldRef(m::followEarth)
        fun applyRate() { m.daysPerSecond = RATES[rateIdx] * if (reverse) -1 else 1 }

        LabPanel(readout) {
            LabRow {
                SegmentControl {
                    RATE_LABELS.forEachIndexed { i, label ->
                        SegmentItem(selected = rateIdx == i, onClick = { rateIdx = i; applyRate() }, title = { Text(label) })
                    }
                }
                Spacer(Modifier.width(20.dp))
                LabSwitch(tr("倒放", "Reverse"), reverse) { reverse = it; applyRate() }
                LabSwitch(tr("月球", "Moon"), moon) { moon = it; m.showMoon = it }
                LabSwitch(tr("地月特写", "Close-up"), closeup) { closeup = it; m.followEarth = it }
            }
            LabSlider(tr("真实比例", "True scale"), spread.toFloat(), 0f..1f, tr("均匀间距", "Even spacing")) { spread = it.toDouble(); m.spread = spread }
            LabSlider(tr("行星大小", "Planet size"), size.toFloat(), 1f..4f, "×%.1f".format(size)) { size = it.toDouble(); m.sizeExag = size }
            LabRow {
                LabSlider(tr("整体缩放", "Zoom"), zoom.toFloat(), 0.6f..3f, "×%.1f".format(zoom)) { zoom = it.toDouble(); m.zoom = zoom }
                Spacer(Modifier.width(20.dp))
                Button(onClick = { m.jd = 2451545.0; m.selected = -1; colourDirty = true }) { Text(tr("回到 2000-01-01", "Back to 2000-01-01")) }
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
