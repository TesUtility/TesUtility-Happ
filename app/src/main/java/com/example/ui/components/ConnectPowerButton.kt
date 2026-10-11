package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.VpnStatus
import com.example.ui.theme.DangerRed
import com.example.ui.theme.HappCardDark
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary

@Composable
fun ConnectPowerButton(
    status: VpnStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_transition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (status == VpnStatus.CONNECTED || status == VpnStatus.CONNECTING) 1.15f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = if (status == VpnStatus.CONNECTED) 0.6f else 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    val (buttonGradient, glowColor, statusText, statusSubtext) = when (status) {
        VpnStatus.DISCONNECTED -> Quad(
            Brush.verticalGradient(listOf(Color(0xFF1E2837), Color(0xFF131B26))),
            NeonCyan.copy(alpha = 0.15f),
            "Отключено",
            "Нажмите для защиты"
        )
        VpnStatus.CONNECTING -> Quad(
            Brush.verticalGradient(listOf(Color(0xFF003842), Color(0xFF002229))),
            NeonCyan.copy(alpha = glowAlpha),
            "Подключение...",
            "Установка VLESS Reality туннеля"
        )
        VpnStatus.CONNECTED -> Quad(
            Brush.verticalGradient(listOf(Color(0xFF00E676), Color(0xFF00A352))),
            NeonEmerald.copy(alpha = glowAlpha),
            "Защищено",
            "VLESS Reality • XTLS-Vision активен"
        )
        VpnStatus.DISCONNECTING -> Quad(
            Brush.verticalGradient(listOf(Color(0xFF421515), Color(0xFF2B0E0E))),
            DangerRed.copy(alpha = 0.3f),
            "Отключение...",
            "Завершение сессии"
        )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(200.dp)
        ) {
            // Radiating Pulsing Outer Glow Ring
            if (status == VpnStatus.CONNECTED || status == VpnStatus.CONNECTING) {
                Box(
                    modifier = Modifier
                        .size(190.dp)
                        .scale(pulseScale)
                        .background(glowColor, CircleShape)
                )
            }

            // Secondary decorative subtle border ring
            Box(
                modifier = Modifier
                    .size(174.dp)
                    .border(
                        width = 2.dp,
                        color = when (status) {
                            VpnStatus.CONNECTED -> NeonEmerald.copy(alpha = 0.6f)
                            VpnStatus.CONNECTING -> NeonCyan.copy(alpha = 0.8f)
                            else -> Color(0x33FFFFFF)
                        },
                        shape = CircleShape
                    )
            )

            // Main Interactive Circular Power Button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(144.dp)
                    .shadow(
                        elevation = if (status == VpnStatus.CONNECTED) 24.dp else 8.dp,
                        shape = CircleShape,
                        spotColor = if (status == VpnStatus.CONNECTED) NeonEmerald else NeonCyan
                    )
                    .background(buttonGradient, CircleShape)
                    .border(
                        width = 3.dp,
                        color = when (status) {
                            VpnStatus.CONNECTED -> Color(0x80FFFFFF)
                            VpnStatus.CONNECTING -> NeonCyan
                            else -> Color(0x2200E5FF)
                        },
                        shape = CircleShape
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true, radius = 72.dp),
                        onClick = onClick
                    )
                    .testTag("vpn_power_button")
            ) {
                if (status == VpnStatus.CONNECTING) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(72.dp),
                        color = NeonCyan,
                        strokeWidth = 3.dp
                    )
                }

                Icon(
                    imageVector = if (status == VpnStatus.CONNECTED) Icons.Default.Shield else Icons.Default.PowerSettingsNew,
                    contentDescription = "VPN Power Button",
                    tint = when (status) {
                        VpnStatus.CONNECTED -> Color(0xFF002914)
                        VpnStatus.CONNECTING -> NeonCyan
                        else -> NeonCyan
                    },
                    modifier = Modifier.size(54.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = statusText,
            color = TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = statusSubtext,
            color = if (status == VpnStatus.CONNECTED) NeonEmerald else TextMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
