package com.sakayori.music.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sakayori.music.ui.theme.typo
import org.jetbrains.compose.resources.stringResource
import com.sakayori.music.generated.resources.Res
import com.sakayori.music.generated.resources.network_error_subtitle
import com.sakayori.music.generated.resources.network_error_title
import com.sakayori.music.generated.resources.retry

@Composable
fun ErrorState(
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Rounded.ErrorOutline,
    title: String,
    subtitle: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(64.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = title,
                style = typo().titleMedium,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = subtitle,
                    style = typo().bodySmall,
                    color = Color.White.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                )
            }
            if (onRetry != null) {
                Spacer(Modifier.height(24.dp))
                TextButton(onClick = onRetry) {
                    Text(stringResource(Res.string.retry), color = Color(0xFF00BCD4))
                }
            }
        }
    }
}

@Composable
fun NetworkErrorState(
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    ErrorState(
        modifier = modifier,
        icon = Icons.Rounded.CloudOff,
        title = stringResource(Res.string.network_error_title),
        subtitle = stringResource(Res.string.network_error_subtitle),
        onRetry = onRetry,
    )
}
