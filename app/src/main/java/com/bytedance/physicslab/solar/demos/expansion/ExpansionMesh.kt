package com.bytedance.physicslab.solar.demos.expansion

import com.bytedance.physicslab.solar.render.MeshData
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Mesh primitives this demo needs that render/MeshGen does not have. */
object ExpansionMesh {
    /**
     * A flat ring (washer) about +Y: hole radius [rIn], outer radius [rOut], thickness [h], centred
     * on the origin. Four surfaces (top, bottom, outer wall, bore), outward CCW winding.
     */
    fun washer(rIn: Float, rOut: Float, h: Float, seg: Int = 64): MeshData {
        val pos = ArrayList<Float>(); val nrm = ArrayList<Float>(); val uv = ArrayList<Float>(); val idx = ArrayList<Int>()
        fun v(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float, u: Float, w: Float): Int {
            pos += x; pos += y; pos += z; nrm += nx; nrm += ny; nrm += nz; uv += u; uv += w
            return pos.size / 3 - 1
        }
        val hh = h / 2
        // flat faces
        for (top in listOf(true, false)) {
            val y = if (top) hh else -hh; val ny = if (top) 1f else -1f
            val base = pos.size / 3
            for (s in 0..seg) {
                val a = 2.0 * PI * s / seg; val c = cos(a).toFloat(); val sn = sin(a).toFloat()
                v(c * rIn, y, sn * rIn, 0f, ny, 0f, 0f, s.toFloat() / seg)
                v(c * rOut, y, sn * rOut, 0f, ny, 0f, 1f, s.toFloat() / seg)
            }
            for (s in 0 until seg) {
                val a0 = base + s * 2; val a1 = a0 + 1; val b0 = a0 + 2; val b1 = a0 + 3
                if (top) { idx += a0; idx += b0; idx += a1; idx += a1; idx += b0; idx += b1 }
                else { idx += a0; idx += a1; idx += b0; idx += a1; idx += b1; idx += b0 }
            }
        }
        // walls: outer (normal out), bore (normal toward the axis)
        for (outer in listOf(true, false)) {
            val r = if (outer) rOut else rIn; val sgn = if (outer) 1f else -1f
            val base = pos.size / 3
            for (s in 0..seg) {
                val a = 2.0 * PI * s / seg; val c = cos(a).toFloat(); val sn = sin(a).toFloat()
                v(c * r, -hh, sn * r, sgn * c, 0f, sgn * sn, s.toFloat() / seg, 1f)
                v(c * r, hh, sn * r, sgn * c, 0f, sgn * sn, s.toFloat() / seg, 0f)
            }
            for (s in 0 until seg) {
                val a0 = base + s * 2; val a1 = a0 + 1; val b0 = a0 + 2; val b1 = a0 + 3
                if (outer) { idx += a0; idx += a1; idx += b0; idx += b0; idx += a1; idx += b1 }
                else { idx += a0; idx += b0; idx += a1; idx += b0; idx += b1; idx += a1 }
            }
        }
        return MeshData(pos.toFloatArray(), nrm.toFloatArray(), uv.toFloatArray(), idx.toIntArray())
    }
}
