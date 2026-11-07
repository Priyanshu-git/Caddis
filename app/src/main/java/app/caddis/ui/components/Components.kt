package app.caddis.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.caddis.domain.model.Item
import app.caddis.ui.theme.C
import app.caddis.ui.theme.CaddisType
import coil3.compose.SubcomposeAsyncImage

enum class BtnStyle { Primary, Accent, Default, Danger, Ghost }

/** Buttons are at least 48 dp tall (38 dp small) — the 44 px touch target from the NFRs. */
@Composable
fun CButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: BtnStyle = BtnStyle.Default,
    small: Boolean = false,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val c = C.colors
    val shape = RoundedCornerShape(if (small) 19.dp else 14.dp)
    val (bg, fg, border) = when (style) {
        BtnStyle.Primary -> Triple(c.accent, c.onAccent, c.accent)
        BtnStyle.Accent -> Triple(Color.Transparent, c.accent, c.accent)
        BtnStyle.Default -> Triple(c.surface, c.fg, c.line)
        BtnStyle.Danger -> Triple(Color.Transparent, c.crit, c.crit)
        BtnStyle.Ghost -> Triple(Color.Transparent, c.fg2, Color.Transparent)
    }
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = if (small) 38.dp else 48.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .clip(shape)
            .background(bg)
            .border(1.dp, border, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (small) 14.dp else 18.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
        Text(text, style = if (small) CaddisType.buttonSm else CaddisType.button, color = fg, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun CChip(
    text: String,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    leading: ImageVector? = null,
    trailing: ImageVector? = null,
    contentDescription: String? = null,
) {
    val c = C.colors
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .heightIn(min = 36.dp)
            .clip(shape)
            .background(if (selected) c.accentTint else Color.Transparent)
            .border(1.dp, if (selected) c.accent else c.line, shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .semantics {
                if (contentDescription != null) this.contentDescription = contentDescription
                if (onClick != null) stateDescription = if (selected) "Selected" else "Not selected"
            }
            .padding(horizontal = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) Icon(leading, null, tint = c.fg, modifier = Modifier.size(16.dp))
        Text(text, style = if (selected) CaddisType.buttonSm else CaddisType.sub, color = c.fg, maxLines = 1)
        if (trailing != null) Icon(trailing, null, tint = c.fg, modifier = Modifier.size(14.dp))
    }
}

/** Small label, e.g. intent or "Due". Text, not colour, carries the meaning (NFR accessibility). */
@Composable
fun Tag(text: String, on: Boolean = false) {
    val c = C.colors
    val shape = RoundedCornerShape(11.dp)
    Box(
        Modifier
            .height(22.dp)
            .clip(shape)
            .background(if (on) c.accentTint else Color.Transparent)
            .border(1.dp, if (on) c.accent else c.line, shape)
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = CaddisType.tag, color = c.fg, maxLines = 1) }
}

@Composable
fun CCard(
    modifier: Modifier = Modifier,
    feature: Boolean = false,
    borderColor: Color? = null,
    onClick: (() -> Unit)? = null,
    padding: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = C.colors
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .clip(shape)
            .background(if (feature) c.accentTint else c.surface)
            .border(if (feature) 1.5.dp else 1.dp, borderColor ?: if (feature) c.accent else c.line, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

/** Thumbnail with a plain fallback. Meta links never have one (MET-01). */
@Composable
fun Thumb(item: Item, size: Dp, modifier: Modifier = Modifier, loading: Boolean = false, height: Dp = size, radius: Dp = 12.dp) {
    val c = C.colors
    val shape = RoundedCornerShape(radius)
    Box(
        modifier
            .size(width = size, height = height)
            .clip(shape)
            .background(c.tint)
            .border(1.dp, c.line, shape),
        contentAlignment = Alignment.Center,
    ) {
        val fallback: @Composable () -> Unit = {
            if (!loading) {
                Icon(
                    if (item.isNote) CIcons.Note else if (item.kind.isVideo) CIcons.Play else CIcons.Doc,
                    null, tint = c.fg2, modifier = Modifier.size(minOf(size, height) * 0.42f),
                )
            }
        }
        if (item.thumbnailUrl != null) {
            SubcomposeAsyncImage(
                model = item.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSizeCompat(size, height),
                loading = { fallback() },
                error = { fallback() },
            )
        } else fallback()
    }
}

private fun Modifier.matchParentSizeCompat(w: Dp, h: Dp) = this.size(width = w, height = h)

@Composable
fun ItemRow(
    item: Item,
    meta: String,
    onClick: () -> Unit,
    why: String? = null,
    right: (@Composable () -> Unit)? = null,
) {
    val c = C.colors
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clickable(onClick = onClick)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Thumb(item, 52.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.displayTitle, style = CaddisType.strong, color = c.fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(meta, style = CaddisType.fine, color = c.fg2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (why != null) Text(why, style = CaddisType.fine.copy(fontWeight = CaddisType.strong.fontWeight), color = c.accent)
            }
            right?.invoke()
        }
        HorizontalDivider(color = c.line)
    }
}

@Composable
fun IconBtn(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 22.dp, tint: Color = C.colors.fg) {
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(size)) }
}

/** Header with a back button: "← Title [right]". */
@Composable
fun TopBar(title: String, onBack: (() -> Unit)?, right: (@Composable RowScope.() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (onBack != null) IconBtn(CIcons.Back, "Back", onBack) else Spacer(Modifier.width(8.dp))
        Text(title, style = CaddisType.h2, color = C.colors.fg, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        right?.invoke(this)
    }
}

@Composable
fun BigTitle(title: String, right: (@Composable RowScope.() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp, top = 10.dp, bottom = 6.dp).heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = CaddisType.h1, color = C.colors.fg, modifier = Modifier.weight(1f))
        right?.invoke(this)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) =
    Text(text, style = CaddisType.section, color = C.colors.accent, modifier = modifier)

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)) =
    Text(text.uppercase(), style = CaddisType.eyebrow, color = C.colors.fg2, modifier = modifier)

@Composable
fun Fine(text: String, modifier: Modifier = Modifier, color: Color = C.colors.fg2, align: TextAlign? = null) =
    Text(text, style = CaddisType.fine, color = color, modifier = modifier, textAlign = align)

@Composable
fun Sub(text: String, modifier: Modifier = Modifier, style: TextStyle = CaddisType.sub, align: TextAlign? = null) =
    Text(text, style = style, color = C.colors.fg2, modifier = modifier, textAlign = align)

@Composable
fun Dot(color: Color = C.colors.good) = Box(Modifier.size(8.dp).clip(CircleShape).background(color))

/** On/off switch drawn like the prototype's toggle; announced as a switch. */
@Composable
fun Toggle(on: Boolean, label: String, onChange: (Boolean) -> Unit) {
    val c = C.colors
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .size(width = 46.dp, height = 28.dp)
            .clip(shape)
            .background(if (on) c.accentTint else c.tint)
            .border(1.5.dp, if (on) c.accent else c.line, shape)
            .clickable(role = Role.Switch) { onChange(!on) }
            .semantics {
                contentDescription = label
                stateDescription = if (on) "On" else "Off"
            },
    ) {
        Box(
            Modifier
                .padding(3.dp)
                .offset(x = if (on) 18.dp else 0.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(if (on) c.accent else c.fg2),
        )
    }
}

/** A settings row: title (+ optional subtitle) on the left, [trailing] on the right. */
@Composable
fun SettingRow(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val c = C.colors
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = if (subtitle != null) CaddisType.strong else CaddisType.bodyLarge, color = c.fg)
                if (subtitle != null) Fine(subtitle)
            }
            trailing()
        }
        HorizontalDivider(color = c.line)
    }
}

@Composable
fun Chevron() = Icon(CIcons.Chevron, null, tint = C.colors.fg2, modifier = Modifier.size(18.dp))

/** "You're all caught up" rings from the prototype. */
@Composable
fun Rings() {
    val c = C.colors
    Box(Modifier.size(112.dp).border(1.dp, c.line, CircleShape), contentAlignment = Alignment.Center) {
        Box(Modifier.size(68.dp).border(1.5.dp, c.fg2, CircleShape), contentAlignment = Alignment.Center) {
            Box(Modifier.size(26.dp).clip(CircleShape).background(c.accentTint).border(2.dp, c.accent, CircleShape))
        }
    }
}

@Composable
fun EmptyState(title: String?, body: String, action: (@Composable () -> Unit)? = null, rings: Boolean = true) {
    Column(
        Modifier.fillMaxWidth().padding(top = 34.dp, bottom = 14.dp, start = 10.dp, end = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (rings) {
            Rings()
            Spacer(Modifier.height(10.dp))
        }
        if (title != null) Text(title, style = CaddisType.h2, color = C.colors.fg, textAlign = TextAlign.Center)
        Sub(body, align = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(4.dp))
            action()
        }
    }
}

/** App mark: three gathered pebbles in a tinted rounded square. */
@Composable
fun AppMark(size: Dp) {
    val c = C.colors
    val shape = RoundedCornerShape(size * 0.3f)
    Box(
        Modifier.size(size).clip(shape).background(c.accentTint).border(BorderStroke(1.5.dp, c.accent), shape),
        contentAlignment = Alignment.Center,
    ) { Icon(CIcons.Mark, null, tint = c.accent, modifier = Modifier.size(size * 0.6f)) }
}

@Composable
fun TextFieldBox(modifier: Modifier = Modifier, focused: Boolean = false, content: @Composable () -> Unit) {
    val c = C.colors
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(c.bg)
            .border(1.dp, if (focused) c.accent else c.line, shape)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) { content() }
}
