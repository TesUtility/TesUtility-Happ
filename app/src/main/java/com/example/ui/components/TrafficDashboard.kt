package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.TrafficStats
import com.example.ui.theme.HappCardBorder
import com.example.ui.theme.HappCardDark
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun TrafficDashboard(
    stats: TrafficStats,
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    val durationFormatted = String.format(
        Locale.getDefault(),
        "%02d:%02d:%02d",
        stats.durationSeconds / 3600,
        (stats.durationSeconds % 3600) / 60,
        stats.durationSeconds % 60
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(HappCardDark)
            .border(1.dp, HappCardBorder, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        // Duration Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = "Session Duration",
                    tint = TextMuted,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Время сессии",
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Text(
                text = if (isConnected) durationFormatted else "--:--:--",
                color = if (isConnected) NeonEmerald else TextMuted,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Speed Metrics Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SpeedItem(
                title = "Загрузка (Down)",
                speed = if (isConnected) formatSpeed(stats.downloadSpeedKb) else "0.0 KB/s",
                total = if (isConnected) String.format(Locale.US, "%.1f MB", stats.totalReceivedMb) else "0.0 MB",
                icon = Icons.Default.ArrowDownward,
                iconTint = NeonEmerald,
                modifier = Modifier.weight(1f)
            )

            SpeedItem(
                title = "Отдача (Up)",
                speed = if (isConnected) formatSpeed(stats.uploadSpeedKb) else "0.0 KB/s",
                total = if (isConnected) String.format(Locale.US, "%.1f MB", stats.totalSentMb) else "0.0 MB",
                icon = Icons.Default.ArrowUpward,
                iconTint = NeonCyan,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SpeedItem(
    title: String,
    speed: String,
    total: String,
    icon: ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF131923))
            .padding(12.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(24.dp)
                        .background(iconTint.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(14.dp)
                    )
                }

                Text(
                    text = title,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = speed,
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "Всего: $total",
                color = TextMuted,
                fontSize = 11.sp
            )
        }
    }
}

private fun formatSpeed(kbPerSec: Double): String {
    return if (kbPerSec >= 1024.0) {
        String.format(Locale.US, "%.2f MB/s", kbPerSec / 1024.0)
    } else {
        String.format(Locale.US, "%.1f KB/s", kbPerSec)
    }
}
