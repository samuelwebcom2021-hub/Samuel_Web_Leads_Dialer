package com.tuempresa.autodialer.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tuempresa.autodialer.ui.theme.GoldAccent

/**
 * Estados del botón de acción principal.
 */
enum class ButtonState {
    NORMAL,
    LOADING,
    SUCCESS
}

@Composable
fun CrmActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    state: ButtonState = ButtonState.NORMAL,
    icon: ImageVector? = null,
    containerColor: Color = GoldAccent,
    contentColor: Color = Color.Black,
    enabled: Boolean = true,
    contentDescription: String? = null
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .height(56.dp)
            .semantics { 
                if (contentDescription != null) {
                    this.contentDescription = contentDescription 
                }
            },
        enabled = enabled && state == ButtonState.NORMAL,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.6f),
            disabledContentColor = contentColor.copy(alpha = 0.6f)
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        AnimatedContent(
            targetState = state,
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            },
            label = "ButtonContent"
        ) { targetState ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                when (targetState) {
                    ButtonState.NORMAL -> {
                        if (icon != null) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(text = text, style = MaterialTheme.typography.titleMedium)
                    }
                    ButtonState.LOADING -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = contentColor,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(text = "Guardando...", style = MaterialTheme.typography.bodyMedium)
                    }
                    ButtonState.SUCCESS -> {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Completado",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(text = "¡Guardado!", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
