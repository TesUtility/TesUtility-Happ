package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.db.AppDatabase
import com.example.model.VlessConfig
import com.example.parser.VlessParser
import com.example.service.VpnController
import com.example.service.VpnStatus
import com.example.ui.components.ActiveServerCard
import com.example.ui.components.ConnectPowerButton
import com.example.ui.components.QrDialog
import com.example.ui.components.TrafficDashboard
import com.example.ui.theme.HappBgDark
import com.example.ui.theme.HappCardBorder
import com.example.ui.theme.HappCardDark
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onNavigateToServers: () -> Unit,
    onConnectRequested: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val status by VpnController.status.collectAsState()
    val stats by VpnController.trafficStats.collectAsState()
    val currentConfig by VpnController.currentConfig.collectAsState()

    var showQrDialogForConfig by remember { mutableStateOf<VlessConfig?>(null) }
    var isPinging by remember { mutableStateOf(false) }

    showQrDialogForConfig?.let { cfg ->
        QrDialog(config = cfg, onDismiss = { showQrDialogForConfig = null })
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HappBgDark)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Happ / Incy Minimalist Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .background(NeonCyan.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "App Logo",
                        tint = NeonCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Vless VPN",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Happ Edition • Xray Reality",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }

            // Core Engine Pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF161F2E))
                    .border(1.dp, Color(0xFF233047), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "REALITY",
                    color = NeonCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Center Piece: Large Animated Happ Power Button
        ConnectPowerButton(
            status = status,
            onClick = {
                if (status == VpnStatus.CONNECTED) {
                    VpnController.stopVpn(context)
                } else if (status == VpnStatus.DISCONNECTED) {
                    onConnectRequested()
                }
            }
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Active Server Card
        ActiveServerCard(
            config = currentConfig,
            onClick = onNavigateToServers
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Traffic Dashboard
        TrafficDashboard(
            stats = stats,
            isConnected = status == VpnStatus.CONNECTED
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Quick Actions Row (Happ / Incy inspired buttons)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickActionButton(
                icon = Icons.Default.ContentPaste,
                label = "Буфер обмена",
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                    if (clipText.isNotBlank()) {
                        val parsed = VlessParser.parse(clipText)
                        if (parsed != null) {
                            coroutineScope.launch {
                                val db = AppDatabase.getInstance(context)
                                db.vlessDao().clearSelection()
                                val id = db.vlessDao().insert(parsed.copy(isSelected = true))
                                VpnController.setCurrentConfig(parsed.copy(id = id, isSelected = true))
                                Toast.makeText(context, "Импортирован: ${parsed.name}", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            Toast.makeText(context, "В буфере нет валидной ссылки vless://", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "Буфер обмена пуст", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.weight(1f)
            )

            QuickActionButton(
                icon = Icons.Default.QrCode,
                label = "QR-код",
                onClick = {
                    val config = currentConfig
                    if (config != null) {
                        showQrDialogForConfig = config
                    } else {
                        Toast.makeText(context, "Сначала выберите сервер", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.weight(1f)
            )

            QuickActionButton(
                icon = Icons.Default.Sensors,
                label = if (isPinging) "Замер..." else "Тест пинга",
                onClick = {
                    val config = currentConfig
                    if (config != null && !isPinging) {
                        isPinging = true
                        VpnController.measurePing(config.server, config.port) { resultPing ->
                            isPinging = false
                            coroutineScope.launch {
                                val db = AppDatabase.getInstance(context)
                                db.vlessDao().updatePing(config.id, resultPing)
                                VpnController.setCurrentConfig(config.copy(pingMs = resultPing))
                                Toast.makeText(context, "Пинг: $resultPing ms", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else if (config == null) {
                        Toast.makeText(context, "Сначала выберите сервер", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun QuickActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(HappCardDark)
            .border(1.dp, HappCardBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = NeonCyan,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
