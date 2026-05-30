package com.xiao.pocketir.ir

import android.os.SystemClock

object IrCooldown {
    private const val COOLDOWN_MS = 600L
    private var lastFireAt = 0L

    fun tryAcquire(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - lastFireAt < COOLDOWN_MS) return false
        lastFireAt = now
        return true
    }
}
