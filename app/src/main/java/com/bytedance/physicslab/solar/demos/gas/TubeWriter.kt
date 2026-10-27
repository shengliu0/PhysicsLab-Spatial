package com.bytedance.physicslab.solar.demos.gas

import com.bytedance.physicslab.solar.render.MeshData
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Allocation-free rewrite of an open tube's vertices (same algorithm and vertex order as
 * MeshGen.tube(path, r, sides, closed = false)), for traces rebuilt EVERY frame. MeshGen.tubeInto
 * builds a temporary MeshData per call; at 60-90 Hz that is ~15 KB of garbage per trace per frame.
 */
class TubeWriter(private val sides: Int) {
    private val ca = FloatArray(sides + 1) { cos(2.0 * PI * it / sides).toFloat() }
    private val sa = FloatArray(sides + 1) { sin(2.0 * PI * it / sides).toFloat() }

    fun write(md: MeshData, path: FloatArray, r: Float) {
        val n = path.size / 3
        require(md.pos.size == n * (sides + 1) * 3) { "TubeWriter: topology mismatch" }
        var px = 0f; var py = 1f; var pz = 0f
        var k = 0
        for (i in 0 until n) {
            val ip = minOf(i + 1, n - 1); val im = maxOf(i - 1, 0)
            var tx = path[ip * 3] - path[im * 3]; var ty = path[ip * 3 + 1] - path[im * 3 + 1]; var tz = path[ip * 3 + 2] - path[im * 3 + 2]
            val tl = sqrt(tx * tx + ty * ty + tz * tz).coerceAtLeast(1e-12f)
            tx /= tl; ty /= tl; tz /= tl
            val d = px * tx + py * ty + pz * tz
            px -= d * tx; py -= d * ty; pz -= d * tz
            var pl = sqrt(px * px + py * py + pz * pz)
            if (pl < 1e-6f) {
                px = -ty; py = tx; pz = 0f; pl = sqrt(px * px + py * py)
                if (pl < 1e-6f) { px = 0f; py = -tz; pz = ty; pl = sqrt(py * py + pz * pz) }
            }
            px /= pl; py /= pl; pz /= pl
            val bx = ty * pz - tz * py; val by = tz * px - tx * pz; val bz = tx * py - ty * px
            for (s in 0..sides) {
                val nx = px * ca[s] + bx * sa[s]; val ny = py * ca[s] + by * sa[s]; val nz = pz * ca[s] + bz * sa[s]
                md.pos[k * 3] = path[i * 3] + nx * r; md.pos[k * 3 + 1] = path[i * 3 + 1] + ny * r; md.pos[k * 3 + 2] = path[i * 3 + 2] + nz * r
                md.nrm[k * 3] = nx; md.nrm[k * 3 + 1] = ny; md.nrm[k * 3 + 2] = nz
                k++
            }
        }
    }
}
