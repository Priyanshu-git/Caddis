package com.nexxlabs.caddis.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nexxlabs.caddis.domain.model.Collection
import com.nexxlabs.caddis.ui.Motion

/**
 * The "Collections" part of an item page. It rests as a plain list of the collections this save is in, with an
 * Edit button ("Add" when there are none); a tap on a chip does nothing until Edit is pressed. Edit opens the
 * [CollectionChips] in place (every collection, tick on the ones it is in, a dashed "New" first) and turns into Done.
 * Done with a name still typed creates it, like closing the save sheet does.
 */
@Composable
fun CollectionsSection(
    all: List<Collection>,
    selected: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    onCreate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf<String?>(null) }
    val mine = all.filter { it.id in selected }
    val focus = LocalFocusManager.current
    // Editing opens chips below the fold; drop a focused text box's keyboard so it does not cover them.
    val startEditing = { focus.clearFocus(); editing = true }
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("Collections", Modifier.weight(1f))
            when {
                editing -> CButton(
                    "Done",
                    onClick = {
                        draft?.trim()?.takeIf { it.isNotEmpty() }?.let(onCreate)
                        draft = null
                        editing = false
                    },
                    style = BtnStyle.Primary, small = true,
                    modifier = Modifier.semantics { contentDescription = "Finish editing collections" },
                )
                mine.isEmpty() -> CButton(
                    "Add", onClick = startEditing, style = BtnStyle.Ghost, small = true,
                    modifier = Modifier.semantics { contentDescription = "Add to a collection" },
                )
                else -> CButton(
                    "Edit", onClick = startEditing, style = BtnStyle.Ghost, small = true,
                    modifier = Modifier.semantics { contentDescription = "Edit collections" },
                )
            }
        }
        AnimatedVisibility(!editing, enter = fadeIn(tween(Motion.Short)) + expandVertically(), exit = fadeOut(tween(Motion.Quick)) + shrinkVertically()) {
            if (mine.isEmpty()) Fine("Not in a collection yet.", Modifier.padding(top = 4.dp))
            else Chips(Modifier.padding(top = 6.dp)) { mine.forEach { CChip(it.name) } }
        }
        AnimatedVisibility(editing, enter = fadeIn(tween(Motion.Short)) + expandVertically(), exit = fadeOut(tween(Motion.Quick)) + shrinkVertically()) {
            CollectionChips(
                all = all, selected = selected, onToggle = onToggle, onCreate = onCreate,
                draft = draft, onDraft = { draft = it }, modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
