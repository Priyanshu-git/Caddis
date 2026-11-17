package com.nexxlabs.caddis.ui

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/** A short confirmation with an optional action, "Undo" unless [actionLabel] says otherwise (prototype toasts: "Marked “Watched”. Undo"). */
data class Toast(
    val message: String,
    val undo: (suspend () -> Unit)? = null,
    val id: Long = System.nanoTime(),
    val actionLabel: String = "Undo",
)

@Singleton
class Messenger @Inject constructor() {
    private val _toasts = MutableSharedFlow<Toast>(extraBufferCapacity = 8)
    val toasts: SharedFlow<Toast> = _toasts.asSharedFlow()

    fun show(message: String, actionLabel: String = "Undo", undo: (suspend () -> Unit)? = null) {
        _toasts.tryEmit(Toast(message, undo, actionLabel = actionLabel))
    }
}
