package net.codekunde.fernplay.android.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import net.codekunde.fernplay.android.ui.theme.Fernplay

/** A ⋮ menu on the surface colour, as the design's sheets. */
@Composable
fun FernMenu(expanded: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        containerColor = Fernplay.colors.surface,
        content = content,
    )
}

/** One menu line: icon and label, 48 dp tall. */
@Composable
fun FernMenuItem(text: String, @DrawableRes icon: Int, onClick: () -> Unit) {
    val c = Fernplay.colors
    DropdownMenuItem(
        text = { Text(text, style = MaterialTheme.typography.bodyLarge, color = c.text) },
        leadingIcon = { Icon(painterResource(icon), contentDescription = null, tint = c.textDim) },
        onClick = onClick,
        modifier = Modifier.heightIn(min = 48.dp),
    )
}
