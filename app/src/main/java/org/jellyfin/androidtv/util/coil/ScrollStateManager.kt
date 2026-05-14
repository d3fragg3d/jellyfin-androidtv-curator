package org.jellyfin.androidtv.util.coil

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.util.concurrent.atomic.AtomicInteger

object ScrollStateManager {
    private val activeScrolls = AtomicInteger(0)
    private val _isScrolling = MutableStateFlow(false)
    val isScrolling: StateFlow<Boolean> = _isScrolling.asStateFlow()

    fun onScrollStart() {
        _isScrolling.value = activeScrolls.incrementAndGet() > 0
    }

    fun onScrollStop() {
        if (activeScrolls.decrementAndGet() <= 0) {
            activeScrolls.set(0)
            _isScrolling.value = false
        }
    }

    suspend fun awaitIdle() {
        if (isScrolling.value) isScrolling.first { !it }
    }

    @JvmStatic
    fun isCurrentlyScrolling(): Boolean = isScrolling.value
}
