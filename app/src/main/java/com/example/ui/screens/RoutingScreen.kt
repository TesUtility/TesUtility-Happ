package com.example.ui.screens

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.VpnController
import com.example.ui.theme.HappBgDark
import com.example.ui.theme.HappCardBorder
import com.example.ui.theme.HappCardDark
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun RoutingScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var routingMode by remember { mutableStateOf("Bypass LAN & RU") }
    var bypassLan by remember { mutableStateOf(true) }
    var blockAds by remember { mutableStateOf(true) }
    var enableIpv6 by remember { mutableStateOf(false) }
    var selectedDns by remember { mutableStateOf("Cloudflare (1.1.1.1)") }
    var splitTunnelingEnabled by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HappBgDark)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "Маршрутизация",
            color = TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Настройка правил трафика и DNS",
            color = TextMuted,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Routing Mode Section
        SectionHeader(title = "Режим проксирования", icon = Icons.AutoMirrored.Filled.AltRoute)

        Spacer(modifier = Modifier.height(10.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RoutingModeCard(
                title = "Обход РФ и LAN (Рекомендуется)",
                description = "Прямое подключение к локальным сайтам и сервисам, всё остальное через VLESS Reality",
                isSelected = routingMode == "Bypass LAN & RU",
                onClick = {
                    routingMode = "Bypass LAN & RU"
                    VpnController.addLog("[Routing] Mode set: Bypass LAN & Russian domains")
                }
            )

            RoutingModeCard(
                title = "Глобальный прокси (Все сайты)",
                description = "Весь сетевой трафик устройства шифруется и направляется в туннель",
                isSelected = routingMode == "Global",
                onClick = {
                    routingMode = "Global"
                    VpnController.addLog("[Routing] Mode set: Global proxy (All traffic)")
                }
            )

            RoutingModeCard(
                title = "Только заблокированные",
                description = "Туннелирование трафика на основе правил geoip / geosite",
                isSelected = routingMode == "Rules",
                onClick = {
                    routingMode = "Rules"
                    VpnController.addLog("[Routing] Mode set: Geosite rules")
                }
            )
        }

        Spacer(modifier = Modifier.height(22.dp))

        // Traffic Features
        SectionHeader(title = "Параметры безопасности", icon = Icons.Default.Security)

        Spacer(modifier = Modifier.height(10.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(HappCardDark)
                .border(1.dp, HappCardBorder, RoundedCornerShape(18.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ToggleRow(
                title = "Блокировка рекламы (AdGuard DNS)",
                subtitle = "Фильтрация баннеров, трекеров и фишинговых сайтов",
                icon = Icons.Default.Public,
                checked = blockAds,
                onCheckedChange = {
                    blockAds = it
                    VpnController.addLog("[Routing] AdBlock filter: $it")
                }
            )

            ToggleRow(
                title = "Обход локальной сети (LAN)",
                subtitle = "Прямой доступ к роутеру, принтерам и устройствам Wi-Fi",
                icon = Icons.Default.Lan,
                checked = bypassLan,
                onCheckedChange = {
                    bypassLan = it
                    VpnController.addLog("[Routing] LAN Bypass: $it")
                }
            )

            ToggleRow(
                title = "Раздельное туннелирование",
                subtitle = "Выборочный пропуск приложений в обход VPN",
                icon = Icons.Default.Apps,
                checked = splitTunnelingEnabled,
                onCheckedChange = {
                    splitTunnelingEnabled = it
                    Toast.makeText(context, if (it) "Раздельное туннелирование включено" else "Отключено", Toast.LENGTH_SHORT).show()
                }
            )
        }

        Spacer(modifier = Modifier.height(22.dp))

        // DNS Server Selection
        SectionHeader(title = "DNS Сервер", icon = Icons.Default.Dns)

        Spacer(modifier = Modifier.height(10.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(HappCardDark)
                .border(1.dp, HappCardBorder, RoundedCornerShape(18.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val dnsList = listOf(
                "Cloudflare (1.1.1.1)" to "Быстрый приватный DNS без логирования",
                "Google (8.8.8.8)" to "Максимальная стабильность и скорость",
                "Quad9 (9.9.9.9)" to "Встроенная защита от вредоносных программ",
                "AdGuard DNS" to "Блокировка рекламы на уровне DNS"
            )

            dnsList.forEach { (dnsName, dnsDesc) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            selectedDns = dnsName
                            VpnController.addLog("[DNS] Primary resolver set to $dnsName")
                        }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = dnsName,
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = dnsDesc,
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }

                    if (selectedDns == dnsName) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(24.dp)
                                .background(NeonCyan.copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}

@Composable
private fun SectionHeader(title: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = NeonCyan,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            color = TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun RoutingModeCard(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) Color(0xFF14202F) else HappCardDark)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) NeonCyan else HappCardBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
            if (isSelected) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(22.dp)
                        .background(NeonCyan, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color(0xFF00222B),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .background(Color(0xFF121926), CircleShape)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, color = TextMuted, fontSize = 11.sp)
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF001F26),
                checkedTrackColor = NeonCyan,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = Color(0xFF1E2838)
            )
        )
    }
}
