package com.sailracing.app

import android.app.Application
import com.sailracing.app.di.AppGraph
import com.sailracing.app.di.DefaultAppGraph

class SailRacingApplication : Application() {

    /** Replaceable so instrumentation and Robolectric tests can inject fakes before the UI starts. */
    lateinit var graph: AppGraph

    override fun onCreate() {
        super.onCreate()
        if (!::graph.isInitialized) graph = DefaultAppGraph(this)
    }
}
