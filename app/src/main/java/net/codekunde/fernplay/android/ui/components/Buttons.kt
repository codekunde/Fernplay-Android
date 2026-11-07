package net.codekunde.fernplay.android.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.codekunde.fernplay.android.ui.theme.Fernplay

enum class ButtonKind { Filled, Secondary, Outline }

/** The design's pill buttons: green, pale green or outlined; 56 dp tall by default. */
@Composable
fun FernButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    kind: ButtonKind = ButtonKind.Filled,
    height: Dp = 56.dp,
    enabled: Boolean = true,
) {
    val c = Fernplay.colors
    val shape = RoundedCornerShape(height / 2)
    val padding = PaddingValues(horizontal = if (icon != null) 16.dp else 22.dp)
    val content: @Composable () -> Unit = {
        if (icon != null) Icon(painterResource(icon), contentDescription = null, modifier = Modifier.padding(end = 8.dp).size(20.dp))
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
    when (kind) {
        ButtonKind.Outline -> OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.height(height),
            shape = shape,
            contentPadding = padding,
            border = BorderStroke(1.5.dp, c.outline),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = c.text),
        ) { content() }
        else -> Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.height(height),
            shape = shape,
            contentPadding = padding,
            colors = if (kind == ButtonKind.Filled) {
                ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent)
            } else {
                ButtonDefaults.buttonColors(containerColor = c.accentContainer, contentColor = c.onAccentContainer)
            },
        ) { content() }
    }
}

/** Snackbar in the design's colours: light card, dark text, green action. */
@Composable
fun FernSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier.padding(horizontal = 12.dp, vertical = 8.dp)) { data ->
        Snackbar(
            snackbarData = data,
            shape = RoundedCornerShape(14.dp),
            containerColor = Color(0xFFD7DAE3),
            contentColor = Color(0xFF1A1C23),
            actionColor = Color(0xFF2A4E27),
        )
    }
}
