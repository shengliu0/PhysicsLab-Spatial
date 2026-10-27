// CometSandbox — pinch the ☄ icon, a comet appears at the pinch, swing your hand, let go: it flies on
// the orbit your throw gave it. Physics: core/comet/CometCore (gravity derived from the drawn Earth,
// soft-saturating hand-speed -> launch-speed mapping, leapfrog in display days).
//
// SHARED-SPACE CONSTRAINT. Continuous hand tracking is Full-Space only; in a Shared-Space volume the
// system reports the hand ONLY while it pinches one of our entities (drag gesture). So the comet is
// born the moment the pinch on the icon begins, rides the hand's displacement while pinched, and is
// thrown with the hand's peak velocity over the last 0.25 s when the pinch opens.

package com.bytedance.physicslab.solar.demos.solar

import com.bytedance.physicslab.solar.core.P3
import com.bytedance.physicslab.solar.core.SolarModel
import com.bytedance.physicslab.solar.core.comet.Comet
import com.bytedance.physicslab.solar.core.comet.CometSim
import com.bytedance.physicslab.solar.core.comet.CometThrow
import com.bytedance.physicslab.solar.core.comet.HandTracker
import com.bytedance.physicslab.solar.lab.Q
import com.bytedance.physicslab.solar.lab.spec.Blend
import com.bytedance.physicslab.solar.lab.spec.LabelSpec
import com.bytedance.physicslab.solar.lab.spec.Mat
import com.bytedance.physicslab.solar.lab.spec.Node
import com.bytedance.physicslab.solar.lab.tr
import com.bytedance.physicslab.solar.render.Image
import com.bytedance.physicslab.solar.render.MeshData
import com.bytedance.physicslab.solar.render.MeshGen
import com.bytedance.physicslab.solar.render.ProcTex
import com.bytedance.physicslab.solar.render.Rgb
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * @param frame converts between volume space and the solar (sun-root) frame; supplied by SolarDemo
 *              because the solar root is tilted, scaled and (in close-up) translated.
 */
class CometSandbox(private val model: SolarModel, private val frame: Frame, var clock: () -> Double = { System.nanoTime() / 1e9 }) {
    interface Frame {
        fun toSun(v: P3): P3          // volume point -> sun-root point
        fun toVolume(p: P3): P3       // sun-root point -> volume point
        fun dirToSun(v: P3): P3       // volume direction -> sun-root direction (rotation only)
        val scale: Float              // sun-root -> volume scale
    }

    companion object {
        const val MAX = 5
        /** Icon spot in VOLUME space: front-right, clear of the panel and of the orbits' near edge. */
        val ICON_POS = P3(0.70f, 0.05f, 0.52f)
        const val GAIN = 1.2f             // hand displacement -> comet displacement while held
        const val TRAIL = 60
        const val PRED = 140
        const val ESCAPE_R = 0.95         // sun-root metres: beyond this the comet has left the volume
    }

    private class Slot(val root: Node, val nucleus: Node, val coma: Node, val ion: Node, val dust: Node,
                       val trail: Node, val trailMesh: MeshData) {
        var comet: Comet? = null
        val hist = FloatArray(TRAIL * 3)
        var histN = 0
        var histAcc = 0f
        var flash = 0f                 // > 0 while the impact / exit flash plays
        var born = 0
    }

    private val slots = ArrayList<Slot>()
    private lateinit var predict: Node
    private lateinit var predictMesh: MeshData
    private lateinit var icon: Node
    private val hand = HandTracker()
    private var held: Slot? = null
    private var grabVol = P3(0f, 0f, 0f)
    private var predictAcc = 0f
    private var serial = 0
    var lastInfo = ""; private set

    private val mu get() = CometThrow.mu(model.orbitR(2))

    // ---------------------------------------------------------------- build

    fun build(outer: Node, sunRoot: Node) {
        slots.clear(); held = null; hand.reset(); serial = 0; lastInfo = ""
        val sphere = MeshGen.sphere(1f, 16, 32, MeshGen.SphereUv.EQUIRECT)
        val cone = unitCone(24)
        val glow = Mat.Unlit(Rgb(0.75f, 0.88f, 1f), alpha = 0.9f, tex = glowTex, blend = Blend.ADD)
        val ice = Mat.Pbr(Rgb(0.78f, 0.82f, 0.88f), metallic = 0f, roughness = 0.6f, emissive = Rgb(0.20f, 0.24f, 0.30f))
        val ionMat = Mat.Unlit(Rgb(0.45f, 0.70f, 1f), alpha = 0.55f, blend = Blend.ADD)
        val dustMat = Mat.Unlit(Rgb(1f, 0.85f, 0.55f), alpha = 0.35f, blend = Blend.ADD)
        val trailMat = Mat.Unlit(Rgb(0.80f, 0.85f, 1f), alpha = 0.35f, blend = Blend.ADD)

        // ---- the ☄ icon (volume space): a little comet on a stand-off ring, pinch target ----
        icon = outer.add(Node("CometIcon")).also { it.pos = ICON_POS; it.tapRadius = 0.07f }
        icon.add("CometIconNucleus", sphere, ice).uniformScale(0.016f)
        icon.add("CometIconComa", quad(), glow).also { it.billboard = true; it.uniformScale(0.045f) }
        icon.add("CometIconTail", cone, ionMat).also {
            it.scale = P3(0.012f, 0.10f, 0.012f); it.rot = Q.fromTo(0f, 1f, 0f, -0.55f, 0.65f, -0.2f)
        }

        // ---- comet pool (sun-root space, so comets tilt / zoom with the system) ----
        for (k in 0 until MAX) {
            val r = sunRoot.add(Node("Comet_$k")).also { it.visible = false }
            val nuc = r.add("Comet_${k}_nucleus", sphere, ice)
            val coma = r.add("Comet_${k}_coma", quad(), glow).also { it.billboard = true }
            val ion = r.add("Comet_${k}_ion", cone, ionMat)
            val dust = r.add("Comet_${k}_dust", cone, dustMat)
            val tm = MeshGen.tube(FloatArray(TRAIL * 3) { (it % 3).toFloat() * 1e-4f + it * 1e-6f }, 0.0012f, 5, false)
            val tr = sunRoot.add("Comet_${k}_trail", tm, trailMat).also { it.visible = false }
            slots += Slot(r, nuc, coma, ion, dust, tr, tm)
        }
        predictMesh = MeshGen.tube(FloatArray(PRED * 3) { it * 1e-5f }, 0.0010f, 5, false)
        predict = sunRoot.add("CometPredict", predictMesh, Mat.Unlit(Rgb(1f, 1f, 1f), alpha = 0.45f, blend = Blend.ADD)).also { it.visible = false }
    }

    // ---------------------------------------------------------------- interaction

    fun isDragTarget(name: String) = name == "CometIcon" || (name.startsWith("Comet_") && name.endsWith("_nucleus"))

    fun onDragStart(name: String) {
        val slot = if (name == "CometIcon") spawn(ICON_POS)
                   else slots.getOrNull(name.removePrefix("Comet_").substringBefore("_").toIntOrNull() ?: -1)?.takeIf { it.comet?.alive == true }
        val c = slot?.comet ?: return
        c.held = true; c.vx = 0.0; c.vy = 0.0; c.vz = 0.0
        held = slot
        grabVol = frame.toVolume(P3(c.x.toFloat(), c.y.toFloat(), c.z.toFloat()))
        hand.reset(); hand.add(clock(), 0.0, 0.0, 0.0)
    }

    /** Hand displacement in volume metres (already converted from view px by the host). */
    fun onDrag(dx: Float, dy: Float, dz: Float, edit: (MeshData) -> Unit) {
        val s = held ?: return
        val c = s.comet ?: return
        hand.add(clock(), dx.toDouble(), dy.toDouble(), dz.toDouble())
        val v = P3(grabVol.x + GAIN * hand.px.toFloat(), grabVol.y + GAIN * hand.py.toFloat(), grabVol.z + GAIN * hand.pz.toFloat())
        val p = frame.toSun(v)
        c.x = p.x.toDouble(); c.y = p.y.toDouble(); c.z = p.z.toDouble()
        predictAcc += 1f
        if (predictAcc >= 4f) { predictAcc = 0f; showPrediction(c, edit) }
    }

    fun onDragEnd() {
        val s = held ?: return
        held = null
        predict.visible = false
        val c = s.comet ?: return
        val v = hand.releaseVelocity()
        val speed = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
        val d = frame.dirToSun(P3(v[0].toFloat(), v[1].toFloat(), v[2].toFloat()))
        val k = CometThrow.launchFactor(speed)
        c.launch(mu, k, d.x.toDouble(), d.y.toDouble(), d.z.toDouble())
        describe(c, speed, k)
    }

    /** Tap on the icon / the panel button: a comet at the icon, launched on a near-circular path. */
    fun tapSpawn() {
        val s = spawn(ICON_POS) ?: return
        val c = s.comet ?: return
        c.launch(mu, 1.0, 0.0, 0.0, 0.0)          // zero direction -> prograde tangent
        describe(c, 1.0, 1.0)
    }

    private fun spawn(atVolume: P3): Slot? {
        if (!::icon.isInitialized) return null
        // free slot, else recycle the oldest
        val s = slots.firstOrNull { it.comet?.alive != true && it.flash <= 0f } ?: slots.minByOrNull { it.born } ?: return null
        val p = frame.toSun(atVolume)
        s.comet = Comet(p.x.toDouble(), p.y.toDouble(), p.z.toDouble())
        s.born = ++serial; s.histN = 0; s.flash = 0f
        s.root.visible = true
        return s
    }

    private fun describe(c: Comet, handSpeed: Double, k: Double) {
        val (a, e) = c.elements(mu)
        val aE = model.orbitR(2)
        lastInfo = if (a.isFinite() && a > 0) {
            val years = (a / aE).pow(1.5)
            tr("彗星：手速 %.1f m/s → %.2f 倍圆轨道速度 · 周期 %.1f 地球年 · e = %.2f".format(handSpeed, k, years, e),
               "Comet: hand %.1f m/s → %.2f× circular speed · period %.1f Earth yr · e = %.2f".format(handSpeed, k, years, e))
        } else tr("彗星：手速 %.1f m/s → %.2f 倍圆轨道速度 · 超过逃逸速度，飞出太阳系".format(handSpeed, k),
                  "Comet: hand %.1f m/s → %.2f× circular speed · above escape speed, leaving the solar system".format(handSpeed, k))
    }

    private fun showPrediction(c: Comet, edit: (MeshData) -> Unit) {
        val v = hand.releaseVelocity()
        val speed = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
        val d = frame.dirToSun(P3(v[0].toFloat(), v[1].toFloat(), v[2].toFloat()))
        val ghost = Comet(c.x, c.y, c.z)
        ghost.launch(mu, CometThrow.launchFactor(speed), d.x.toDouble(), d.y.toDouble(), d.z.toDouble())
        val path = CometSim.predict(ghost, mu, model.sunRadius * 1.05, ESCAPE_R, PRED, 3 * 365.25)
        MeshGen.tubeInto(predictMesh, path, 0.0010f / max(frame.scale / SolarDemo.BASE_SCALE, 0.3f), 5, false)
        edit(predictMesh)
        predict.visible = true
    }

    // ---------------------------------------------------------------- per frame

    fun update(dt: Float, edit: (MeshData) -> Unit) {
        val days = model.daysPerSecond * dt
        val zoomFix = 1f / max(frame.scale / SolarDemo.BASE_SCALE, 0.3f)     // keep sizes sane at any zoom
        val aE = model.orbitR(2)
        for (s in slots) {
            val c = s.comet ?: continue
            if (c.alive) CometSim.step(c, mu, days, model.sunRadius * 1.05, ESCAPE_R)
            if (!c.alive && s.flash <= 0f && s.root.visible && c.fate.isNotEmpty()) {
                s.flash = 0.6f
                if (c.fate == "sun") lastInfo = tr("彗星撞进了太阳（近日点小于太阳半径）", "The comet fell into the Sun (perihelion inside it)")
            }
            if (s.flash > 0f) {
                s.flash -= dt
                val f = (s.flash / 0.6f).coerceIn(0f, 1f)
                s.coma.uniformScale(0.05f * (2f - f) * zoomFix)
                s.ion.visible = false; s.dust.visible = false
                if (s.flash <= 0f) { s.root.visible = false; s.trail.visible = false; s.comet = null }
                continue
            }
            if (!c.alive) continue
            val p = P3(c.x.toFloat(), c.y.toFloat(), c.z.toFloat())
            s.root.pos = p
            s.nucleus.uniformScale(0.006f * zoomFix)
            // brightness grows toward the sun (sublimation): coma and tails scale with ~1/r
            val r = max(c.r, 1e-4)
            val act = (aE / r).coerceIn(0.25, 4.0).toFloat()
            s.coma.uniformScale(0.018f * zoomFix * sqrt(act))
            val anti = norm(p.x, p.y, p.z)
            val lenIon = min(0.16f, 0.035f * act) * zoomFix
            s.ion.visible = true; s.dust.visible = true
            s.ion.scale = P3(0.004f * zoomFix, lenIon, 0.004f * zoomFix)
            s.ion.rot = Q.fromTo(0f, 1f, 0f, anti[0], anti[1], anti[2])
            // dust tail curves back along the orbit: anti-sun bent away from the velocity
            val sp = max(c.speed, 1e-9).toFloat()
            val dd = norm(anti[0] * 0.75f - c.vx.toFloat() / sp * 0.25f, anti[1] * 0.75f - c.vy.toFloat() / sp * 0.25f, anti[2] * 0.75f - c.vz.toFloat() / sp * 0.25f)
            s.dust.scale = P3(0.008f * zoomFix, lenIon * 0.7f, 0.008f * zoomFix)
            s.dust.rot = Q.fromTo(0f, 1f, 0f, dd[0], dd[1], dd[2])
            // trail: sampled every 1/20 s while flying
            if (!c.held) {
                s.histAcc += dt
                if (s.histAcc >= 0.05f) {
                    s.histAcc = 0f
                    if (s.histN < TRAIL) s.histN++
                    System.arraycopy(s.hist, 3, s.hist, 0, (TRAIL - 1) * 3)
                    s.hist[(TRAIL - 1) * 3] = p.x; s.hist[(TRAIL - 1) * 3 + 1] = p.y; s.hist[(TRAIL - 1) * 3 + 2] = p.z
                    if (s.histN >= 3) {
                        val path = FloatArray(TRAIL * 3)
                        val first = (TRAIL - s.histN) * 3
                        for (k in 0 until TRAIL * 3) path[k] = s.hist[max(k, first + (k % 3))]
                        MeshGen.tubeInto(s.trailMesh, path, 0.0011f * zoomFix, 5, false)
                        edit(s.trailMesh)
                        s.trail.visible = true
                    }
                }
            }
        }
    }

    /** Labels: the icon hint, and the held comet's live launch estimate. */
    fun labels(out: MutableList<LabelSpec>) {
        out += LabelSpec(tr("☄ 捏住拖出彗星，挥手松开抛出", "☄ Pinch, swing, release to throw a comet"),
            P3(ICON_POS.x, ICON_POS.y + 0.10f, ICON_POS.z))
        val s = held ?: return
        val c = s.comet ?: return
        val v = hand.releaseVelocity()
        val sp = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
        val vp = frame.toVolume(P3(c.x.toFloat(), c.y.toFloat(), c.z.toFloat()))
        out += LabelSpec(tr("手速 %.1f m/s → %.2f×v圆".format(sp, CometThrow.launchFactor(sp)),
                            "hand %.1f m/s → %.2f×v_circ".format(sp, CometThrow.launchFactor(sp))),
            P3(vp.x, vp.y + 0.07f, vp.z))
    }

    val count get() = slots.count { it.comet?.alive == true }

    // ---------------------------------------------------------------- meshes / textures

    private val glowTex: Image by lazy {
        val n = 64; val img = Image(n, n)
        for (y in 0 until n) for (x in 0 until n) {
            val dx = (x + 0.5f) / n * 2 - 1; val dy = (y + 0.5f) / n * 2 - 1
            val r = sqrt(dx * dx + dy * dy)
            val a = (1f - r).coerceIn(0f, 1f).pow(2.2f)
            img.px[y * n + x] = ProcTex.argb(1f, a, a, a)
        }
        img
    }

    private fun quad(): MeshData = MeshData(
        floatArrayOf(-1f, -1f, 0f, 1f, -1f, 0f, 1f, 1f, 0f, -1f, 1f, 0f),
        floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f),
        floatArrayOf(0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f),
        intArrayOf(0, 1, 2, 0, 2, 3))

    /** Cone along +Y: base radius 1 at y = 0, apex at y = 1 (tails). */
    private fun unitCone(seg: Int): MeshData {
        val pos = ArrayList<Float>(); val nrm = ArrayList<Float>(); val uv = ArrayList<Float>(); val idx = ArrayList<Int>()
        for (k in 0..seg) {
            val a = 2 * PI * k / seg; val c = cos(a).toFloat(); val s = sin(a).toFloat()
            pos += listOf(c, 0f, s); nrm += listOf(c, 0.5f, s); uv += listOf(k.toFloat() / seg, 1f)
            pos += listOf(0f, 1f, 0f); nrm += listOf(c, 0.5f, s); uv += listOf(k.toFloat() / seg, 0f)
        }
        for (k in 0 until seg) { val b = k * 2; idx += listOf(b, b + 1, b + 2) }
        return MeshData(pos.toFloatArray(), nrm.toFloatArray(), uv.toFloatArray(), idx.toIntArray())
    }

    private fun norm(x: Float, y: Float, z: Float): FloatArray {
        val l = max(sqrt(x * x + y * y + z * z), 1e-9f); return floatArrayOf(x / l, y / l, z / l)
    }
}
