package com.bytedance.physicslab.solar.demos.gas

import com.bytedance.physicslab.solar.render.MeshGen
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class TubeWriterTest {
    @Test
    fun matchesMeshGenTubeExactly() {
        val r = java.util.Random(5)
        val path = FloatArray(48 * 3) { r.nextFloat() * 0.3f }
        path[9] = path[6]; path[10] = path[7]; path[11] = path[8]          // a repeated point (zero tangent)
        val ref = MeshGen.tube(path, 0.002f, 5, false)
        val md = MeshGen.tube(FloatArray(48 * 3), 0.002f, 5, false)
        TubeWriter(5).write(md, path, 0.002f)
        assertArrayEquals(ref.pos, md.pos, 1e-6f)
        assertArrayEquals(ref.nrm, md.nrm, 1e-6f)
    }
}
