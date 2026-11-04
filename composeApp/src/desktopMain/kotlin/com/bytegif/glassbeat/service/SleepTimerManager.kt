package com.bytegif.glassbeat.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Application-level singleton that keeps the sleep timer alive regardless of
 * song changes or screen navigation. Directly ported from GlassBeat Android.
 */
object SleepTimerManager {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var timerJob: Job? = null

    /** Epoch-ms at which the timer fires (0 = no timer). */
    var endTimeMs: Long = 0L
        private set

    /** True when "end of song" mode is active instead of a fixed duration. */
    var isEndOfSong: Boolean = false
        private set

    private val _display = MutableStateFlow("")
    val display: StateFlow<String> = _display.asStateFlow()

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    var onTimerExpired: (() -> Unit)? = null

    fun start(minutes: Long) {
        cancel()
        isEndOfSong = false
        val durationMs = minutes * 60_000L
        endTimeMs = System.currentTimeMillis() + durationMs
        _isActive.value = true

        timerJob = scope.launch {
            while (true) {
                val remaining = endTimeMs - System.currentTimeMillis()
                if (remaining <= 0L) {
                    cancel()
                    onTimerExpired?.invoke()
                    break
                }
                val m = (remaining / 60_000L).toInt()
                val s = ((remaining % 60_000L) / 1000L).toInt()
                _display.value = "%d:%02d".format(m, s)
                delay(1_000L)
            }
        }
    }

    fun setEndOfSong() {
        cancel()
        isEndOfSong = true
        _isActive.value = true
        _display.value = "End of song"
    }

    fun cancel() {
        timerJob?.cancel()
        timerJob = null
        endTimeMs = 0L
        isEndOfSong = false
        _isActive.value = false
        _display.value = ""
    }
}
