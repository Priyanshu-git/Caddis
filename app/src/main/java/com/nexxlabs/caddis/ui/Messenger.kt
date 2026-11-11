package com.nexxlabs.caddis.ui

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/** A short confirmation, optionally with Undo (prototype toasts: "Marked “Watched”. Undo"). */
data class Toast(val message: String, val undo: (suspend () -> Unit)? = null, val id: Long = System.nanoTime())

@Singleton
class Messenger @Inject constructor() {
    private val _toasts = MutableSharedFlow<Toast>(extraBufferCapacity = 8)
    val toasts: SharedFlow<Toast> = _toasts.asSharedFlow()

    fun show(message: String, undo: (suspend () -> Unit)? = null) {
        _toasts.tryEmit(Toast(message, undo))
    }
}
