package com.cstv.app.data.util

import com.cstv.app.domain.util.TimeProvider
import javax.inject.Inject

class SystemTimeProvider @Inject constructor() : TimeProvider {
    override fun nowMillis(): Long = System.currentTimeMillis()
}
