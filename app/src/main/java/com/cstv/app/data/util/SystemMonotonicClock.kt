package com.cstv.app.data.util

import android.os.SystemClock
import com.cstv.app.domain.util.MonotonicClock
import javax.inject.Inject

class SystemMonotonicClock @Inject constructor() : MonotonicClock {
    override fun elapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()
}
