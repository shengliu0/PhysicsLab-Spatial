package com.bytedance.physicslab.solar

import com.bytedance.physicslab.solar.content.LabVolume
import com.bytedance.physicslab.solar.lab.DemoRegistry
import com.bytedance.physicslab.solar.lab.LabHost
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.dsl.DefaultWindowContainer
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope

fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        LabHost.register(DemoRegistry.all)
        DefaultWindowContainer {
            PicoTheme {
                LabVolume(firstDemo = DemoRegistry.all.first().id)
            }
        }
    }
