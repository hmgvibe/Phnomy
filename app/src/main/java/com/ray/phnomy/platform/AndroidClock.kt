package com.ray.phnomy.platform

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.ray.phnomy.core.ClockReading
import com.ray.phnomy.core.FocusClock

/** elapsedRealtime includes time spent asleep and is never changed by a wall-clock edit. */
class AndroidClock(private val context: Context) : FocusClock {
    override fun read() = ClockReading(
        wallMillis = System.currentTimeMillis(),
        elapsedMillis = SystemClock.elapsedRealtime(),
        bootCount = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1),
    )
}
