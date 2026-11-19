package com.nexxlabs.caddis.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nexxlabs.caddis.ui.theme.C

/**
 * One field of the details editor, used on the save sheet and on Edit details so they read the same.
 * [onReset] is non-null when the person has written this field: a reset arrow beside it hands the field
 * back to Caddis (the caller empties it, which is what "hand back" already means), with a line saying so.
 */
@Composable
fun DetailField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    maxLength: Int,
    modifier: Modifier = Modifier,
    lines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    onReset: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth()) {
        Fine(label, Modifier.padding(top = 10.dp, bottom = 5.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            CField(
                value, onChange, placeholder, label = label, maxLength = maxLength, lines = lines,
                keyboardType = keyboardType, modifier = Modifier.weight(1f),
            )
            if (onReset != null) IconBtn(CIcons.Undo, "Reset $label", onReset, size = 18.dp, tint = C.colors.accent)
        }
        if (onReset != null) Fine("Edited by you. Reset to let Caddis fill it in.", Modifier.padding(top = 4.dp))
    }
}

/** Whether to offer a reset: there is text, and it is the person's own (saved earlier, or typed just now). */
fun canReset(draft: String, prefill: String, saved: Boolean): Boolean =
    draft.isNotBlank() && (saved || draft.trim() != prefill.trim())
