package com.nexxlabs.caddis.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.nexxlabs.caddis.ui.Motion
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nexxlabs.caddis.domain.details.EditLimits
import com.nexxlabs.caddis.domain.model.Collection
import com.nexxlabs.caddis.ui.theme.C

/** Bottom sheet styled like the prototype: 26 dp top corners, grab handle, surface colour. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = C.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        containerColor = c.surface,
        contentColor = c.fg,
        scrimColor = c.scrim,
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 14.dp).size(width = 40.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(c.line))
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(start = 18.dp, end = 18.dp, bottom = 22.dp),
        ) {
            // Inside the keyboard padding, so growing sections ease open without lagging the keyboard.
            Column(Modifier.fillMaxWidth().animateContentSize(tween(Motion.Short, easing = Motion.Standard)), content = content)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Chips(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

/** Roughly two rows of chips; a third peeks out when there are more, so the area reads as scrollable. */
private val CollectionChipsMaxHeight = 100.dp

/**
 * Every collection as a toggle chip (LIB-02: an item can be in many), with a dashed "New" chip first.
 * Tapping "New" turns it into a name field in place: Done on the keyboard or the tick creates the collection and
 * files the save in it; the cross, or an empty name, cancels. [all] is newest first, so a new one lands beside "New".
 *
 * [draft] is the name being typed, or null when not naming. The owner holds it so a sheet that closes mid-name
 * can still create the collection (like the reason and title do).
 */
@Composable
fun CollectionChips(
    all: List<Collection>,
    selected: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    onCreate: (String) -> Unit,
    draft: String?,
    onDraft: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = C.colors
    val submit = {
        val name = draft.orEmpty().trim()
        onDraft(null)
        if (name.isNotEmpty()) onCreate(name)
    }
    val scroll = rememberScrollState()
    LaunchedEffect(all.firstOrNull()?.id) { scroll.animateScrollTo(0) }
    Column(modifier) {
        if (draft != null) {
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { focus.requestFocus() }
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                CField(
                    value = draft, onValueChange = { onDraft(it) }, placeholder = "Name it", label = "New collection name",
                    maxLength = EditLimits.COLLECTION_NAME, onIme = submit, modifier = Modifier.weight(1f).focusRequester(focus),
                )
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(c.accent)
                        .clickable(role = Role.Button, onClick = submit)
                        .semantics { contentDescription = "Create collection" },
                    contentAlignment = Alignment.Center,
                ) { Icon(CIcons.Check, null, tint = c.onAccent, modifier = Modifier.size(20.dp)) }
                IconBtn(CIcons.Close, "Cancel new collection", { onDraft(null) }, size = 18.dp)
            }
        }
        Column(Modifier.heightIn(max = CollectionChipsMaxHeight).verticalScroll(scroll)) {
            Chips {
                if (draft == null) CChip("New", onClick = { onDraft("") }, leading = CIcons.Plus, dashed = true, contentDescription = "New collection")
                all.forEach { col ->
                    val on = col.id in selected
                    CChip(col.name, selected = on, onClick = { onToggle(col.id, !on) }, leading = if (on) CIcons.Check else null)
                }
            }
        }
        if (all.isEmpty() && draft == null) Fine("Group saves your way, like “Dinner ideas”.", Modifier.padding(top = 8.dp))
    }
}

@Composable
fun CenterBox(content: @Composable () -> Unit) = Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { content() }
