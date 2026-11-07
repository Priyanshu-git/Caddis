package app.caddis.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import app.caddis.ui.theme.C
import app.caddis.ui.theme.CaddisType

/** Single-line field styled like the prototype's `.field`. */
@Composable
fun CField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    label: String = placeholder,
    maxLength: Int = 500,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    onIme: (() -> Unit)? = null,
    boxed: Boolean = true,
    textStyle: TextStyle = CaddisType.bodyLarge,
) {
    val c = C.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    BasicTextField(
        value = value,
        onValueChange = { onValueChange(it.take(maxLength)) },
        singleLine = true,
        textStyle = textStyle.copy(color = c.fg),
        cursorBrush = SolidColor(c.accent),
        interactionSource = interaction,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(onAny = { onIme?.invoke() }),
        modifier = modifier.semantics { contentDescription = label },
        decorationBox = { inner ->
            val content: @Composable () -> Unit = {
                Box(Modifier.fillMaxWidth()) {
                    if (value.isEmpty()) Text(placeholder, style = textStyle, color = c.fg2)
                    inner()
                }
            }
            if (boxed) TextFieldBox(Modifier.fillMaxWidth(), focused) { content() } else content()
        },
    )
}
