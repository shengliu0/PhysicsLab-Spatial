// FoamFx — what one graduated cylinder SHOWS: the liquid, the foam rising inside the glass, and the
// eruption. Pure Kotlin over Spec nodes; driven every frame by the reactor's numbers. Transforms only
// (the tray blanket is the one small mesh that is re-written, and only when foam lands).
//
//   inside   a foam core (unit cylinder, foam texture) from the liquid up to the foam surface;
//   column   once the glass is full the foam is EXTRUDED out of the mouth like toothpaste: a solid
//            two-segment core (cylinder - joint - cylinder - cap) whose length follows the exit speed
//            v = Q/A (Q = foam flow, A = mouth area; soft-capped at 1.6 m/s, braked by its own
//            weight: h = v^2 / 2g_col), which bends over the way it is about to flop and twists
//            slowly as it comes out. Blobs stuck on its skin scroll upward with the extrusion, so the
//            column reads as lumpy foam on the move, not as a pipe;
//   rope     at the top the foam breaks off as one coherent rope of overlapping blobs (all leave
//            with nearly the same velocity), arcs over and lands; blobs that come down by the glass
//            slide down the outside;
//   blanket  landed foam raises a coarse height field over the tray that is drawn as one smooth
//            foam surface (volume-true) with the last blobs sitting half-embedded on top.
// Blobs come from ONE pool shared by both cylinders (the demo splits it per run by the foam each
// will push out, so each blob stands for a fixed volume and the pool lasts the eruption).
// Colour: off-white. With food colouring the dye streaks run down the wall, so the column carries
// vertical pastel stripes (texture + skin blobs by angle) and, as the twisting column flops, the
// stripe on the flop side changes: the rope and the heap get coherent BANDS, never random balls.

package com.bytedance.physicslab.solar.demos.elephant

import com.bytedance.physicslab.solar.core.P3
import com.bytedance.physicslab.solar.lab.Q
import com.bytedance.physicslab.solar.lab.spec.Mat
import com.bytedance.physicslab.solar.lab.spec.Node
import com.bytedance.physicslab.solar.render.MeshData
import kotlin.math.PI
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh
import kotlin.random.Random

class FoamFx(
    private val tag: String,
    val cx: Float, val cz: Float,
    /** inner bottom of the cylinder */
    val yBottom: Float,
    val mouthY: Float,
    val rIn: Float, val rOut: Float,
    /** tray floor (top surface) and half size */
    val trayY: Float, val trayHalf: Float,
    /** hexagonal foot radius: the height field is blocked under it */
    private val footR: Float,
    /** most blobs this cylinder can ever be given from the shared pool */
    private val capacity: Int,
) {
    // ---------------------------------------------------------------- nodes
    private var shared: Array<Node> = emptyArray()
    private var offset = 0
    /** blobs of the shared pool this cylinder may use this run */
    var count = 0; private set
    lateinit var inner: Node; private set
    lateinit var dome: Node; private set
    lateinit var liquid: Node; private set
    lateinit var mound: Node; private set
    private lateinit var col1: Node
    private lateinit var col2: Node
    private lateinit var joint: Node
    private lateinit var cap: Node
    private lateinit var skin: Array<Node>
    private lateinit var bubbles: Array<Node>

    // ---------------------------------------------------------------- blob state (local index 0 until count)
    private val px = FloatArray(capacity); private val py = FloatArray(capacity); private val pz = FloatArray(capacity)
    private val vx = FloatArray(capacity); private val vy = FloatArray(capacity); private val vz = FloatArray(capacity)
    private val pr = FloatArray(capacity); private val st = IntArray(capacity); private val tt = FloatArray(capacity)
    private val grow = FloatArray(capacity)
    private val bandOf = IntArray(capacity); private val whiteOf = IntArray(capacity)
    private val settledOrder = IntArray(capacity); private var nSettled = 0; private var growPtr = 0
    private var used = 0
    // skin blobs in 6 lanes (one per stripe sector) x 3 rows: a dyed lane reads as one continuous stripe
    private val skinF = FloatArray(SKIN); private val skinA = FloatArray(SKIN) { ((it % SECTORS) + 0.5f) / SECTORS * 2 * PI.toFloat() }
    private val bx = FloatArray(BUBBLES); private val by = FloatArray(BUBBLES); private val bz = FloatArray(BUBBLES); private val bv = FloatArray(BUBBLES)

    // height field over the tray, and the foam blanket drawn from it (13 x 13 vertices)
    private val cell = 2 * trayHalf / NG
    private val hf = FloatArray(NG * NG)
    private val blocked = BooleanArray(NG * NG)
    val moundMesh: MeshData = grid()
    /** The blanket changed: the demo re-writes it ([writeMound]) and uploads it (169 vertices). */
    var moundDirty = false; private set

    // ---------------------------------------------------------------- run state
    var parcelVol = 50e-6f; private set               // m^3 of foam per blob
    private var r0 = 0.02f
    private var pending = 0f
    private var lastOut = 0f
    private var fluxEma = 0f
    var exitSpeed = 0f; private set
    var columnH = 0f; private set
    var innerTop = 0f; private set                     // y of the foam surface inside (or the mouth)
    var liquidTop = 0f; private set
    var overflowing = false; private set
    private var lean = LEAN0                            // lean/flop direction (angle in XZ; pi/2 = +z, toward the viewer)
    private var tilt = 0f
    private var twist = 0f
    private var rnd = Random(1)
    // column geometry (world), refreshed every frame
    private var jX = 0f; private var jY = 0f; private var jZ = 0f
    private var tX = 0f; private var tY = 0f; private var tZ = 0f
    private var d2x = 0f; private var d2y = 1f; private var d2z = 0f
    /** blob materials: [0, WHITES) off-whites, then the pastel stripe colours */
    var mats: Array<Mat> = emptyArray()
    var stripes = false

    val areaIn get() = (PI * rIn * rIn).toFloat()
    private val rCol get() = rIn * 1.10f
    /** Volume (m^3) from the liquid surface to the mouth. */
    val freeVolume get() = (mouthY - liquidTop) * areaIn
    private val showCol get() = columnH > 0.008f

    fun build(root: Node, sphere: MeshData, unitCyl: MeshData, liquidMat: Mat, innerMat: Mat, bubbleMat: Mat, skinMat: Mat) {
        liquid = root.add("Liquid$tag", unitCyl, liquidMat)
        inner = root.add("FoamIn$tag", unitCyl, innerMat).also { it.visible = false }
        dome = root.add("FoamDome$tag", sphere, innerMat).also { it.visible = false }
        col1 = root.add("FoamColA$tag", unitCyl, innerMat).also { it.visible = false }
        joint = root.add("FoamJoint$tag", sphere, innerMat).also { it.visible = false }
        col2 = root.add("FoamColB$tag", unitCyl, innerMat).also { it.visible = false }
        cap = root.add("FoamCap$tag", sphere, innerMat).also { it.visible = false }
        skin = Array(SKIN) { k -> root.add("FoamSkin${tag}_$k", sphere, skinMat).also { it.visible = false } }
        mound = root.add("Mound$tag", moundMesh, innerMat).also { it.visible = false }
        bubbles = Array(BUBBLES) { i -> root.add("Bub${tag}_$i", sphere, bubbleMat).also { it.visible = false } }
        for (i in 0 until NG) for (j in 0 until NG) {
            val x = -trayHalf + (i + 0.5f) * cell; val z = -trayHalf + (j + 0.5f) * cell
            blocked[i * NG + j] = sqrt(x * x + z * z) < footR + 0.004f
        }
    }

    /** The blob pool shared by both cylinders (the demo hides it on refill). */
    fun attach(pool: Array<Node>) { shared = pool }

    /** The core, column and inner foam (texture/tint), and the tray blanket. */
    fun setBodyMat(m: Mat, blanket: Mat) {
        inner.setMat(m); dome.setMat(m); col1.setMat(m); col2.setMat(m); joint.setMat(m); cap.setMat(m); mound.setMat(blanket)
    }

    /** Fresh cylinder: [liquidMl] of liquid, no foam anywhere. */
    fun reset(liquidMl: Float, seed: Int) {
        rnd = Random(seed)
        st.fill(FREE); grow.fill(1f)
        count = 0; offset = 0; nSettled = 0; growPtr = 0; used = 0
        hf.fill(0f); writeMound(); moundDirty = true; mound.visible = false   // flat again (and re-uploaded)
        pending = 0f; lastOut = 0f; fluxEma = 0f; exitSpeed = 0f; columnH = 0f; overflowing = false
        lean = LEAN0; tilt = 0f; twist = 0f
        for (k in 0 until SKIN) skinF[k] = ((k / SECTORS) / 3f + (k % SECTORS) * 0.055f) % 1f
        setLiquid(liquidMl)
        innerTop = liquidTop
        for (n in listOf(inner, dome, col1, col2, joint, cap)) n.visible = false
        for (n in skin) n.visible = false
        for (b in bubbles) b.visible = false
        bv.fill(0f)
    }

    fun setLiquid(ml: Float) {
        val h = ml * 1e-6f / areaIn
        liquidTop = yBottom + h
        liquid.scale = P3(rIn - 0.0012f, maxOf(0.001f, h), rIn - 0.0012f)
        liquid.pos = P3(cx, yBottom + h / 2, cz)
    }

    /** This run: [n] blobs of the shared pool from [from], sized so they carry [expectedOutM3]. */
    fun planRun(expectedOutM3: Float, from: Int, n: Int) {
        offset = from; count = minOf(n, capacity)
        parcelVol = if (count > 0) (expectedOutM3 / (0.85f * count)).coerceIn(2e-6f, 80e-6f) else 80e-6f
        // drawn a touch larger than their volume so neighbours overlap: no see-through gaps
        r0 = cbrt(3 * parcelVol / (4 * PI.toFloat())) * 1.10f
    }

    /**
     * Advance [dt] (simulation seconds). [foamM3] = foam volume the reactor has made so far,
     * [gasLps] = gas production (bubbles), [soap] = foam at all.
     */
    fun update(dt: Float, foamM3: Float, gasLps: Float, soap: Boolean) {
        // ---- foam inside the glass, and what overflows
        val free = freeVolume
        val inside = minOf(foamM3, free)
        val hIn = inside / areaIn
        innerTop = liquidTop + hIn
        inner.visible = hIn > 0.002f
        if (inner.visible) { inner.scale = P3(rIn - 0.0016f, hIn, rIn - 0.0016f); inner.pos = P3(cx, liquidTop + hIn / 2, cz); inner.rot = Q.axisAngle(0f, 1f, 0f, twist) }
        val out = maxOf(0f, foamM3 - free)
        overflowing = out > 0f
        val dOut = out - lastOut; lastOut = out
        if (dt > 0f) {
            fluxEma += (dOut / dt - fluxEma) * (1f - exp(-dt / 0.12f))
            pending += maxOf(0f, dOut)
        }
        exitSpeed = 1.6f * tanh(maxOf(0f, fluxEma) / (areaIn * 1.15f) / 1.6f)
        dome.visible = overflowing
        if (overflowing) { dome.scale = P3(rIn * 1.12f, 0.016f, rIn * 1.12f); dome.pos = P3(cx, mouthY + 0.001f, cz) }

        // ---- the extruded column: length from the exit speed, collapsing when the push stops
        if (dt > 0f) {
            // flops wander, mostly to the front-left: across the line of sight, never straight at the viewer
            lean += ((LEAN0 - lean) * 0.8f) * dt + (rnd.nextFloat() - 0.5f) * 2.0f * sqrt(dt)
            val hT = if (exitSpeed > 0.15f) minOf(H_MAX, exitSpeed * exitSpeed / (2 * G_COL)) else 0f
            columnH = if (hT > columnH) columnH + (hT - columnH) * (1f - exp(-dt / 0.12f)) else maxOf(hT, columnH - 0.45f * dt)
            tilt += ((columnH / H_MAX) * TILT_MAX - tilt) * (1f - exp(-dt / 0.3f))
            twist += dt * (0.4f + 2.2f * exitSpeed)                // the column turns as it comes out
        }
        placeColumn(dt)

        // ---- emit (the rope breaks off the column top, or the foam oozes over the rim)
        var guard = 0
        while (pending >= parcelVol && guard++ < 60) { pending -= parcelVol; emit() }

        // ---- move
        if (dt > 0f) for (i in 0 until used) when (st[i]) {
            FLY -> stepFly(i, dt)
            WALL -> stepWall(i, dt)
            else -> {}
        }
        for (i in 0 until used) if (st[i] == FLY || st[i] == WALL) shared[offset + i].pos = P3(px[i], py[i], pz[i])

        updateBubbles(dt, gasLps, soap)
    }

    private fun dirOf(t: Float) = floatArrayOf(cos(lean) * sin(t), cos(t), sin(lean) * sin(t))

    /** Core: mouth -> joint -> top, bending over toward the lean; skin blobs scroll up it. */
    private fun placeColumn(dt: Float) {
        val on = showCol
        col1.visible = on; col2.visible = on; joint.visible = on; cap.visible = on
        val skinOn = on && columnH > 0.03f
        if (!on) { for (n in skin) n.visible = false; return }
        val t1 = tilt * 0.35f; val t2 = tilt * 1.25f
        val l1 = columnH * 0.55f; val l2 = columnH * 0.5f
        val d1 = dirOf(t1); val d2 = dirOf(t2)
        val ax = sin(lean); val az = -cos(lean)
        val spin = Q.axisAngle(0f, 1f, 0f, twist)
        val q1 = Q.axisAngle(ax, 0f, az, t1) * spin; val q2 = Q.axisAngle(ax, 0f, az, t2) * spin
        jX = cx + d1[0] * l1; jY = mouthY + d1[1] * l1; jZ = cz + d1[2] * l1
        tX = jX + d2[0] * l2; tY = jY + d2[1] * l2; tZ = jZ + d2[2] * l2
        d2x = d2[0]; d2y = d2[1]; d2z = d2[2]
        val r = rCol
        col1.pos = P3(cx + d1[0] * l1 / 2, mouthY + d1[1] * l1 / 2, cz + d1[2] * l1 / 2); col1.rot = q1; col1.scale = P3(r, l1, r)
        joint.pos = P3(jX, jY, jZ); joint.rot = q1; joint.uniformScale(r * 0.99f)
        col2.pos = P3(jX + d2[0] * l2 / 2, jY + d2[1] * l2 / 2, jZ + d2[2] * l2 / 2); col2.rot = q2; col2.scale = P3(r * 0.97f, l2, r * 0.97f)
        cap.pos = P3(tX, tY, tZ); cap.rot = q2; cap.scale = P3(r * 1.12f, r * 0.85f, r * 1.12f)
        // skin: blobs half-sunk into the core, carried up at the extrusion speed (wrap to the mouth)
        val len = l1 + l2
        for (k in 0 until SKIN) {
            val n = skin[k]
            if (!skinOn) { n.visible = false; continue }
            skinF[k] = (skinF[k] + 0.45f * exitSpeed * dt / maxOf(0.03f, len)) % 1f
            val s = skinF[k] * len
            val inFirst = s < l1
            val q = if (inFirst) q1 else q2
            val bxw = if (inFirst) cx + d1[0] * s else jX + d2[0] * (s - l1)
            val byw = if (inFirst) mouthY + d1[1] * s else jY + d2[1] * (s - l1)
            val bzw = if (inFirst) cz + d1[2] * s else jZ + d2[2] * (s - l1)
            val o = q.rotate(floatArrayOf(cos(skinA[k]) * r * 0.80f, 0f, sin(skinA[k]) * r * 0.80f))
            n.pos = P3(bxw + o[0], byw + o[1], bzw + o[2]); n.rot = q
            val sz = r * (0.50f + 0.08f * (k % 3))
            n.scale = P3(sz, sz * 0.78f, sz)
            n.visible = true
        }
    }

    /** Skin blob colour follows the stripe at its angle on the column (same as the core's texture). */
    fun recolour() {
        if (mats.isEmpty()) return
        for (k in 0 until SKIN) skin[k].setMat(mats[if (stripes) stripeMat(sectorOf(skinA[k]), k) else k % WHITES])
        for (i in 0 until used) shared[offset + i].setMat(mats[matFor(i)])
    }

    private fun sectorOf(angle: Float) = ((((angle / (2 * PI.toFloat())) % 1f) + 1f) % 1f * SECTORS).toInt().coerceIn(0, SECTORS - 1)
    /** Even sectors carry dye (pink / blue / mint), odd ones are plain foam. */
    private fun stripeMat(sector: Int, white: Int) = if (sector % 2 == 0 && mats.size > WHITES) WHITES + (sector / 2) % (mats.size - WHITES) else white % WHITES
    private fun matFor(i: Int) = if (stripes) stripeMat(bandOf[i], whiteOf[i]) else whiteOf[i]

    private fun emit() {
        // blobs are only freed by reset(), so the next free one is simply the next unused
        if (used >= count) {                                      // pool spent: settled foam swells instead
            if (nSettled == 0) return
            val k = settledOrder[growPtr % nSettled]; growPtr++
            if (grow[k] < 1.8f) { grow[k] *= 1.08f; squash(shared[offset + k], pr[k] * grow[k], true) }
            return
        }
        val i = used++
        val r = r0 * (0.88f + 0.30f * rnd.nextFloat())
        pr[i] = r; grow[i] = 1f
        val v = exitSpeed
        if (showCol) {
            // break off the top as a rope: the same velocity for all, a little scatter
            val a = rnd.nextFloat() * 2 * PI.toFloat(); val rr = rCol * 0.75f * sqrt(rnd.nextFloat())
            px[i] = tX + rr * cos(a); pz[i] = tZ + rr * sin(a); py[i] = tY + (rnd.nextFloat() - 0.3f) * rCol * 0.8f
            val push = 0.12f + 0.20f * (v / 1.6f)
            vx[i] = d2x * 0.35f * v + cos(lean) * push + (rnd.nextFloat() - 0.5f) * 0.06f
            vz[i] = d2z * 0.35f * v + sin(lean) * push + (rnd.nextFloat() - 0.5f) * 0.06f
            vy[i] = d2y * 0.35f * v * (0.8f + 0.4f * rnd.nextFloat())
        } else {
            // ooze over the rim
            val a = lean + (rnd.nextFloat() - 0.5f) * 2.4f
            val rho = rIn * (0.4f + 0.6f * sqrt(rnd.nextFloat()))
            px[i] = cx + rho * cos(a); pz[i] = cz + rho * sin(a); py[i] = mouthY + 0.004f
            val vh = 0.05f + 0.10f * rnd.nextFloat()
            vx[i] = cos(a) * vh; vz[i] = sin(a) * vh; vy[i] = 0f
        }
        tt[i] = 0f
        st[i] = FLY
        // dye band: whichever stripe of the turning column is on the flop side right now (the twist only
        // ever grows, so consecutive foam shares a band: pink, plain, blue, plain, mint, plain ...)
        bandOf[i] = sectorOf(LEAN0 + twist); whiteOf[i] = rnd.nextInt(WHITES)
        val n = shared[offset + i]
        squash(n, r, false)
        if (mats.isNotEmpty()) n.setMat(mats[matFor(i)])
        n.pos = P3(px[i], py[i], pz[i])
        n.visible = true
    }

    /** Soft blobs: slightly flattened ellipsoids, more so once they have landed. */
    private fun squash(n: Node, r: Float, landed: Boolean) {
        n.scale = if (landed) P3(r * 1.08f, r * 0.86f, r * 1.08f) else P3(r * 1.04f, r * 0.92f, r * 1.04f)
    }

    private fun stepFly(i: Int, dt: Float) {
        vy[i] -= 9.8f * dt
        val k = exp(-1.2f * dt); vx[i] *= k; vz[i] *= k
        px[i] += vx[i] * dt; py[i] += vy[i] * dt; pz[i] += vz[i] * dt
        // keep it on the tray
        val lim = trayHalf - pr[i] * 0.8f
        if (px[i] < cx - lim) { px[i] = cx - lim; vx[i] = -0.3f * vx[i] }
        if (px[i] > cx + lim) { px[i] = cx + lim; vx[i] = -0.3f * vx[i] }
        if (pz[i] < cz - lim) { pz[i] = cz - lim; vz[i] = -0.3f * vz[i] }
        if (pz[i] > cz + lim) { pz[i] = cz + lim; vz[i] = -0.3f * vz[i] }
        val dx = px[i] - cx; val dz = pz[i] - cz; val d = sqrt(dx * dx + dz * dz)
        // coming down next to (or onto) the glass below the rim: slide down the outside
        if (py[i] < mouthY && d < rOut + pr[i] * 0.7f) {
            val ux = if (d > 1e-4f) dx / d else cos(lean); val uz = if (d > 1e-4f) dz / d else sin(lean)
            px[i] = cx + ux * (rOut + pr[i] * 0.7f); pz[i] = cz + uz * (rOut + pr[i] * 0.7f)
            st[i] = WALL; tt[i] = if (rnd.nextFloat() < 0.10f) 0.3f + 1.2f * rnd.nextFloat() else -1f
            vy[i] = -(0.14f + 0.12f * rnd.nextFloat())
            return
        }
        if (vy[i] < 0f && py[i] - pr[i] * 0.5f <= trayY + heightAt(px[i], pz[i])) settle(i)
    }

    private fun stepWall(i: Int, dt: Float) {
        py[i] += vy[i] * dt
        if (tt[i] > 0f) { tt[i] -= dt; if (tt[i] <= 0f) { st[i] = STUCK; squash(shared[offset + i], pr[i], true); return } }
        if (py[i] - pr[i] * 0.5f <= trayY + heightAt(px[i], pz[i])) settle(i)
    }

    private fun cellIndex(v: Float, c: Float) = ((v - c + trayHalf) / cell).toInt().coerceIn(0, NG - 1)

    private fun heightAt(x: Float, z: Float): Float {
        val k = cellIndex(x, cx) * NG + cellIndex(z, cz)
        return if (blocked[k]) 0f else hf[k]
    }

    /** Land: roll downhill on the height field, stick, raise the blanket (volume kept). */
    private fun settle(i: Int) {
        var ci = cellIndex(px[i], cx); var cj = cellIndex(pz[i], cz)
        if (blocked[ci * NG + cj]) {                               // pushed out from under the foot
            val dx = px[i] - cx; val dz = pz[i] - cz; val d = sqrt(dx * dx + dz * dz).coerceAtLeast(1e-4f)
            ci = cellIndex(cx + dx / d * (footR + cell), cx); cj = cellIndex(cz + dz / d * (footR + cell), cz)
        }
        val add = parcelVol / (cell * cell)               // foam thickness one blob makes on one cell
        val step = maxOf(pr[i] * 0.55f, add * 0.6f)       // foam slumps: a low angle of repose
        for (roll in 0 until 16) {
            val h0 = hf[ci * NG + cj]
            var bi = ci; var bj = cj; var best = h0
            for (di in -1..1) for (dj in -1..1) {
                val ni = ci + di; val nj = cj + dj
                if (ni < 0 || nj < 0 || ni >= NG || nj >= NG || blocked[ni * NG + nj]) continue
                val h = hf[ni * NG + nj] + rnd.nextFloat() * 0.002f
                if (h < best) { best = h; bi = ni; bj = nj }
            }
            if (best < h0 - step) { ci = bi; cj = bj } else break
        }
        val k = ci * NG + cj
        val dx = px[i] - (cx - trayHalf + (ci + 0.5f) * cell); val dz = pz[i] - (cz - trayHalf + (cj + 0.5f) * cell)
        val jit = cell * 0.48f
        px[i] = cx - trayHalf + (ci + 0.5f) * cell + dx.coerceIn(-jit, jit)
        pz[i] = cz - trayHalf + (cj + 0.5f) * cell + dz.coerceIn(-jit, jit)
        // the blanket keeps the volume: 60 % on this cell, 5 % on each neighbour
        var own = 0.6f * add
        for (di in -1..1) for (dj in -1..1) {
            if (di == 0 && dj == 0) continue
            val ni = ci + di; val nj = cj + dj
            if (ni < 0 || nj < 0 || ni >= NG || nj >= NG || blocked[ni * NG + nj]) own += 0.05f * add else hf[ni * NG + nj] += 0.05f * add
        }
        hf[k] += own
        py[i] = trayY + hf[k] - pr[i] * (0.40f + 0.15f * rnd.nextFloat())   // mostly sunk into the blanket: bumps, not pillows
        moundDirty = true
        st[i] = SETTLED
        settledOrder[nSettled++] = i
        val n = shared[offset + i]
        squash(n, pr[i], true)
        n.pos = P3(px[i], py[i], pz[i])
    }

    // ---------------------------------------------------------------- the foam blanket on the tray

    private fun grid(): MeshData {
        val n = NG + 1
        val pos = FloatArray(n * n * 3); val nrm = FloatArray(n * n * 3); val uv = FloatArray(n * n * 2)
        for (i in 0 until n) for (j in 0 until n) {
            val v = i * n + j
            pos[v * 3] = cx - trayHalf + i * cell; pos[v * 3 + 1] = trayY; pos[v * 3 + 2] = cz - trayHalf + j * cell
            nrm[v * 3 + 1] = 1f; uv[v * 2] = i.toFloat() / NG; uv[v * 2 + 1] = j.toFloat() / NG
        }
        val idx = IntArray(NG * NG * 6); var t = 0
        for (i in 0 until NG) for (j in 0 until NG) {
            val a = i * n + j; val b = a + n
            idx[t++] = a; idx[t++] = a + 1; idx[t++] = b          // CCW seen from +Y
            idx[t++] = a + 1; idx[t++] = b + 1; idx[t++] = b
        }
        return MeshData(pos, nrm, uv, idx)
    }

    private fun vertexH(i: Int, j: Int): Float {
        var s = 0f; var c = 0
        for (a in i - 1..i) for (b in j - 1..j) if (a in 0 until NG && b in 0 until NG) { s += hf[a * NG + b]; c++ }
        val h = if (c > 0) s / c else 0f
        return if (i == 0 || j == 0 || i == NG || j == NG) h * 0.45f else h
    }

    /** Re-write the blanket from the height field (in place, same topology). */
    fun writeMound() {
        moundDirty = false
        val n = NG + 1
        var top = 0f
        val h = FloatArray(n * n) { v -> vertexH(v / n, v % n) }
        for (i in 0 until n) for (j in 0 until n) {
            val v = i * n + j
            moundMesh.pos[v * 3 + 1] = trayY + 0.0015f + h[v]
            top = maxOf(top, h[v])
            val hx = h[minOf(n - 1, i + 1) * n + j] - h[maxOf(0, i - 1) * n + j]
            val hz = h[i * n + minOf(n - 1, j + 1)] - h[i * n + maxOf(0, j - 1)]
            var nx = -hx; var ny = 2 * cell; var nz = -hz
            val l = sqrt(nx * nx + ny * ny + nz * nz); nx /= l; ny /= l; nz /= l
            moundMesh.nrm[v * 3] = nx; moundMesh.nrm[v * 3 + 1] = ny; moundMesh.nrm[v * 3 + 2] = nz
        }
        if (::mound.isInitialized) mound.visible = top > 0.003f
    }

    // ---------------------------------------------------------------- bubbles in the liquid

    private fun updateBubbles(dt: Float, gasLps: Float, soap: Boolean) {
        val want = if (gasLps <= 1e-4f) 0 else minOf(BUBBLES, (3 + 3 * kotlin.math.log10(1 + gasLps * 200f)).toInt())
        val top = if (soap) liquidTop else liquidTop + 0.035f
        for (k in 0 until BUBBLES) {
            val b = bubbles[k]
            if (bv[k] == 0f) {
                if (k >= want || dt == 0f) { b.visible = false; continue }
                val a = rnd.nextFloat() * 2 * PI.toFloat(); val r = rIn * 0.8f * sqrt(rnd.nextFloat())
                bx[k] = cx + r * cos(a); bz[k] = cz + r * sin(a); by[k] = yBottom + 0.004f + rnd.nextFloat() * (liquidTop - yBottom) * 0.5f
                bv[k] = 0.10f + 0.12f * rnd.nextFloat() + if (soap) 0f else 0.15f
            }
            by[k] += bv[k] * dt
            if (by[k] > top) { bv[k] = 0f; b.visible = false; continue }
            b.visible = true
            b.pos = P3(bx[k], by[k], bz[k]); b.uniformScale(0.0045f + 0.002f * (k % 3))
        }
    }

    // ---------------------------------------------------------------- queries

    private fun segDist2(p: P3, ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float): Float {
        val ux = bx - ax; val uy = by - ay; val uz = bz - az
        val l2 = ux * ux + uy * uy + uz * uz
        val t = if (l2 < 1e-9f) 0f else (((p.x - ax) * ux + (p.y - ay) * uy + (p.z - az) * uz) / l2).coerceIn(0f, 1f)
        val dx = p.x - (ax + ux * t); val dy = p.y - (ay + uy * t); val dz = p.z - (az + uz * t)
        return dx * dx + dy * dy + dz * dz
    }

    /** Is [p] inside foam (a blob, the column, or the foam inside the glass)? */
    fun inFoam(p: P3, slack: Float): Boolean {
        val dx = p.x - cx; val dz = p.z - cz
        if (dx * dx + dz * dz < rIn * rIn && p.y > liquidTop && p.y < innerTop + if (overflowing) 0.03f else 0f) return true
        if (showCol) {
            val rr = (rCol + slack) * (rCol + slack)
            if (segDist2(p, cx, mouthY, cz, jX, jY, jZ) < rr || segDist2(p, jX, jY, jZ, tX, tY, tZ) < rr) return true
        }
        for (i in 0 until used) {
            if (st[i] == FREE) continue
            val ex = p.x - px[i]; val ey = p.y - py[i]; val ez = p.z - pz[i]
            val r = pr[i] * grow[i] * 1.1f + slack
            if (ex * ex + ey * ey + ez * ez < r * r) return true
        }
        // the blanket on the tray
        if (kotlin.math.abs(dx) < trayHalf && kotlin.math.abs(dz) < trayHalf && p.y < trayY + heightAt(p.x, p.z) + slack && heightAt(p.x, p.z) > 0.005f) return true
        return false
    }

    /** A good place to poke the splint: the top of the foam on the tray in front, else inside the mouth. */
    fun pokePoint(): P3? {
        var best = -1; var score = -1e9f
        for (i in 0 until used) if (st[i] == SETTLED || st[i] == STUCK) {
            val s = py[i] + 0.8f * (pz[i] - cz) + 0.3f * (px[i] - cx)
            if (s > score) { score = s; best = i }
        }
        if (best >= 0) return P3(px[best], py[best] + pr[best] * 0.2f, pz[best])
        if (innerTop > liquidTop + 0.03f) return P3(cx, minOf(mouthY - 0.02f, innerTop - 0.015f), cz)
        return null
    }

    /** Where steam leaves: the top of the column, or the mouth. */
    fun steamSource(): P3 = if (showCol) P3(tX + d2x * rCol * 0.6f, tY + d2y * rCol * 0.6f, tZ + d2z * rCol * 0.6f)
        else P3(cx, mouthY + if (overflowing) 0.02f else 0.005f, cz)     // steam rises out of the mouth

    val activeParcels get() = (0 until used).count { st[it] != FREE }
    val parcelsUsed get() = used
    /** Highest point of any foam (does the eruption stay inside the volume?). */
    val maxFoamY: Float get() {
        var m = maxOf(innerTop, if (showCol) maxOf(jY, tY) + rCol else 0f)
        for (i in 0 until used) if (st[i] != FREE) m = maxOf(m, py[i] + pr[i] * grow[i])
        return m
    }

    companion object {
        const val FREE = 0; const val FLY = 2; const val WALL = 3; const val SETTLED = 4; const val STUCK = 5
        const val NG = 12
        const val WHITES = 4
        const val SECTORS = 6
        const val SKIN = 18
        const val BUBBLES = 10
        const val G_COL = 4.5f                 // braking of the column (m/s^2): its own weight, partly carried by the foam below
        const val H_MAX = 0.24f                // longest column above the mouth (m)
        const val TILT_MAX = 0.62f             // how far it bends over at full length (top segment 1.25x)
        const val LEAN0 = 2.36f                // 135 deg: front-left
    }
}
