// MeshKit — a few primitives MeshGen does not have, used by the three electromagnetism demos
// (cyclotron, faraday, photoelectric). Pure Kotlin, outputs render.MeshData like MeshGen.
// Candidates for promotion to render/MeshGen once reviewed.
//
// Every triangle is wound by the builder itself: it compares the face normal with the vertex
// normals it was given and flips the winding when they disagree, so a shape is described by its
// surfaces and normals only and can never come out inside-out (SoftRender culls back faces).

package com.bytedance.physicslab.solar.demos.cyclotron

import com.bytedance.physicslab.solar.render.MeshData
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class MeshBuilder {
    private val p = ArrayList<Float>(); private val n = ArrayList<Float>(); private val t = ArrayList<Float>()
    private val ix = ArrayList<Int>()

    fun v(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float, u: Float = 0f, w: Float = 0f): Int {
        val l = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-12f)
        p += x; p += y; p += z; n += nx / l; n += ny / l; n += nz / l; t += u; t += w
        return p.size / 3 - 1
    }

    fun tri(a: Int, b: Int, c: Int) {
        val ax = p[a * 3]; val ay = p[a * 3 + 1]; val az = p[a * 3 + 2]
        val ux = p[b * 3] - ax; val uy = p[b * 3 + 1] - ay; val uz = p[b * 3 + 2] - az
        val wx = p[c * 3] - ax; val wy = p[c * 3 + 1] - ay; val wz = p[c * 3 + 2] - az
        val fx = uy * wz - uz * wy; val fy = uz * wx - ux * wz; val fz = ux * wy - uy * wx
        val sx = n[a * 3] + n[b * 3] + n[c * 3]; val sy = n[a * 3 + 1] + n[b * 3 + 1] + n[c * 3 + 1]; val sz = n[a * 3 + 2] + n[b * 3 + 2] + n[c * 3 + 2]
        if (fx * sx + fy * sy + fz * sz >= 0) { ix += a; ix += b; ix += c } else { ix += a; ix += c; ix += b }
    }

    fun quad(a: Int, b: Int, c: Int, d: Int) { tri(a, b, c); tri(a, c, d) }

    fun build() = MeshData(p.toFloatArray(), n.toFloatArray(), t.toFloatArray(), ix.toIntArray())
}

object MeshKit {
    /**
     * Annular-sector prism in the XY plane: radii [ri]..[ro], angles [a0]..[a1] (rad, CCW from +X),
     * z from [z0] to [z1]. Smooth radial normals on the curved faces, flat elsewhere.
     * ri = 0 gives a pie slice; a full ring is a0 = 0, a1 = 2 pi (end caps then coincide).
     */
    fun sectorPrism(ri: Float, ro: Float, a0: Float, a1: Float, z0: Float, z1: Float, seg: Int): MeshData {
        val m = MeshBuilder()
        fun ring(r: Float, z: Float, nx: (Float) -> Float, ny: (Float) -> Float, nz: Float) =
            IntArray(seg + 1) { k ->
                val a = a0 + (a1 - a0) * k / seg
                m.v(r * cos(a), r * sin(a), z, nx(a), ny(a), nz, k.toFloat() / seg, 0f)
            }
        // outer / inner curved faces
        val o0 = ring(ro, z0, { cos(it) }, { sin(it) }, 0f); val o1 = ring(ro, z1, { cos(it) }, { sin(it) }, 0f)
        for (k in 0 until seg) m.quad(o0[k], o0[k + 1], o1[k + 1], o1[k])
        if (ri > 1e-6f) {
            val i0 = ring(ri, z0, { -cos(it) }, { -sin(it) }, 0f); val i1 = ring(ri, z1, { -cos(it) }, { -sin(it) }, 0f)
            for (k in 0 until seg) m.quad(i0[k], i0[k + 1], i1[k + 1], i1[k])
        }
        // front (+z) and back (-z) faces
        for ((z, nz) in listOf(z1 to 1f, z0 to -1f)) {
            val o = ring(ro, z, { 0f }, { 0f }, nz)
            if (ri > 1e-6f) {
                val i = ring(ri, z, { 0f }, { 0f }, nz)
                for (k in 0 until seg) m.quad(i[k], i[k + 1], o[k + 1], o[k])
            } else {
                val c = m.v(0f, 0f, z, 0f, 0f, nz)
                for (k in 0 until seg) m.tri(c, o[k], o[k + 1])
            }
        }
        // end caps (skip for a closed ring)
        if (a1 - a0 < 2 * PI.toFloat() - 1e-3f) for (a in listOf(a0, a1)) {
            val sgn = if (a == a0) -1f else 1f
            val nx = -sin(a) * sgn; val ny = cos(a) * sgn
            val c = cos(a); val s = sin(a)
            val q = intArrayOf(
                m.v(ri * c, ri * s, z0, nx, ny, 0f), m.v(ro * c, ro * s, z0, nx, ny, 0f),
                m.v(ro * c, ro * s, z1, nx, ny, 0f), m.v(ri * c, ri * s, z1, nx, ny, 0f))
            m.quad(q[0], q[1], q[2], q[3])
        }
        return m.build()
    }

    /** A "D": the part of the disc of radius [r] with x >= [xMin], as a slab from z0 to z1. */
    fun dSlab(r: Float, xMin: Float, z0: Float, z1: Float, seg: Int): MeshData {
        val m = MeshBuilder()
        val a0 = kotlin.math.acos((xMin / r).coerceIn(-1f, 1f))
        val pts = Array(seg + 1) { k -> val a = -a0 + 2 * a0 * k / seg; floatArrayOf(r * cos(a), r * sin(a)) }
        val cx = (xMin + r) / 2
        for ((z, nz) in listOf(z1 to 1f, z0 to -1f)) {
            val c = m.v(cx, 0f, z, 0f, 0f, nz)
            val ring = IntArray(seg + 1) { m.v(pts[it][0], pts[it][1], z, 0f, 0f, nz) }
            for (k in 0 until seg) m.tri(c, ring[k], ring[k + 1])
            m.tri(c, ring[seg], ring[0])          // the chord
        }
        for (k in 0 until seg) {                   // curved edge
            val (x0, y0) = pts[k].let { it[0] to it[1] }; val (x1, y1) = pts[k + 1].let { it[0] to it[1] }
            m.quad(m.v(x0, y0, z0, x0, y0, 0f), m.v(x1, y1, z0, x1, y1, 0f), m.v(x1, y1, z1, x1, y1, 0f), m.v(x0, y0, z1, x0, y0, 0f))
        }
        val (xa, ya) = pts[seg].let { it[0] to it[1] }; val (xb, yb) = pts[0].let { it[0] to it[1] }
        m.quad(m.v(xa, ya, z0, -1f, 0f, 0f), m.v(xb, yb, z0, -1f, 0f, 0f), m.v(xb, yb, z1, -1f, 0f, 0f), m.v(xa, ya, z1, -1f, 0f, 0f))
        return m.build()
    }

    /** A flat one-sided rectangle w x h centred at the origin, normal +z (u right, v down). */
    fun quadXY(w: Float, h: Float): MeshData {
        val m = MeshBuilder()
        val a = m.v(-w / 2, -h / 2, 0f, 0f, 0f, 1f, 0f, 1f); val b = m.v(w / 2, -h / 2, 0f, 0f, 0f, 1f, 1f, 1f)
        val c = m.v(w / 2, h / 2, 0f, 0f, 0f, 1f, 1f, 0f); val d = m.v(-w / 2, h / 2, 0f, 0f, 0f, 1f, 0f, 0f)
        m.quad(a, b, c, d)
        return m.build()
    }

    /** Points of a circle of radius [r] about the origin in the plane spanned by axes (u, v). */
    fun circle(r: Float, n: Int, plane: String = "xy", cx: Float = 0f, cy: Float = 0f, cz: Float = 0f): FloatArray =
        FloatArray(n * 3) { k ->
            val a = 2.0 * PI * (k / 3) / n
            val c = (r * cos(a)).toFloat(); val s = (r * sin(a)).toFloat()
            when (plane) {
                "xy" -> when (k % 3) { 0 -> cx + c; 1 -> cy + s; else -> cz }
                "yz" -> when (k % 3) { 0 -> cx; 1 -> cy + c; else -> cz + s }
                else -> when (k % 3) { 0 -> cx + c; 1 -> cy; else -> cz + s }   // "xz"
            }
        }

    /** Arc in the XY plane, angles a0..a1 (rad), n points. */
    fun arc(r: Float, a0: Float, a1: Float, n: Int, cx: Float = 0f, cy: Float = 0f, z: Float = 0f): FloatArray =
        FloatArray(n * 3) { k ->
            val a = a0 + (a1 - a0) * (k / 3) / (n - 1)
            when (k % 3) { 0 -> cx + r * cos(a); 1 -> cy + r * sin(a); else -> z }
        }
}
