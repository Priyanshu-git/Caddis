package app.caddis.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.caddis.domain.model.Collection
import app.caddis.ui.theme.C

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
            content = content,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Chips(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

/** Toggle chips for the item's collections (LIB-02: an item can be in many). */
@Composable
fun CollectionPicker(all: List<Collection>, selected: Set<String>, onToggle: (String, Boolean) -> Unit, modifier: Modifier = Modifier) {
    if (all.isEmpty()) {
        Fine("No collections yet. Create one in Library › Collections.", modifier)
        return
    }
    Chips(modifier) {
        all.forEach { col ->
            val on = col.id in selected
            CChip(col.name, selected = on, onClick = { onToggle(col.id, !on) })
        }
    }
}

@Composable
fun CenterBox(content: @Composable () -> Unit) = Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { content() }
