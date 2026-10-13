package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.example.qr.QrUtils
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
    var showImportDialog by remember { mutableStateOf(false) }
    var showQrOptionDialog by remember { mutableStateOf(false) }
    var isPinging by remember { mutableStateOf(false) }

    var showCameraScanner by remember { mutableStateOf(false) }

    val homeQrPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    if (bitmap != null) {
                        val qrText = QrUtils.decodeQrBitmap(bitmap)
                        if (!qrText.isNullOrBlank()) {
                            var extracted = VlessParser.extractAll(qrText)
                            if (extracted.isEmpty() && (qrText.startsWith("http://", ignoreCase = true) || qrText.startsWith("https://", ignoreCase = true))) {
                                Toast.makeText(context, "Загрузка подписки по ссылке...", Toast.LENGTH_SHORT).show()
                                extracted = VlessParser.fetchSubscription(qrText)
                            }
                            if (extracted.isNotEmpty()) {
                                val db = AppDatabase.getInstance(context)
                                extracted.forEach { db.vlessDao().insert(it) }
                                val first = extracted.first()
                                db.vlessDao().clearSelection()
                                val id = db.vlessDao().insert(first.copy(isSelected = true))
                                VpnController.setCurrentConfig(first.copy(id = id, isSelected = true))
                                val msg = if (extracted.size == 1) "Импортирован из QR: ${first.name}" else "Импортировано: ${extracted.size} серверов"
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "В QR-коде не найдено ссылок VLESS", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "Не удалось распознать QR-код на изображении", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Ошибка чтения изображения: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    if (showCameraScanner) {
        com.example.ui.components.CameraQrScannerDialog(
            onDismiss = { showCameraScanner = false },
            onQrDetected = { rawText, configs ->
                showCameraScanner = false
                coroutineScope.launch {
                    var extracted = configs
                    if (extracted.isEmpty() && (rawText.startsWith("http://", ignoreCase = true) || rawText.startsWith("https://", ignoreCase = true))) {
                        Toast.makeText(context, "Загрузка подписки по ссылке...", Toast.LENGTH_SHORT).show()
                        extracted = VlessParser.fetchSubscription(rawText)
                    }
                    if (extracted.isNotEmpty()) {
                        val db = AppDatabase.getInstance(context)
                        extracted.forEach { db.vlessDao().insert(it) }
                        val first = extracted.first()
                        db.vlessDao().clearSelection()
                        val id = db.vlessDao().insert(first.copy(isSelected = true))
                        VpnController.setCurrentConfig(first.copy(id = id, isSelected = true))
                        val msg = if (extracted.size == 1) "Импортирован из QR: ${first.name}" else "Импортировано: ${extracted.size} серверов"
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "В QR-коде не найдено ссылок VLESS", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    showQrDialogForConfig?.let { cfg ->
        QrDialog(config = cfg, onDismiss = { showQrDialogForConfig = null })
    }

    if (showQrOptionDialog) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showQrOptionDialog = false }) {
            androidx.compose.material3.Surface(
                shape = RoundedCornerShape(24.dp),
                color = HappCardDark,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2A3952)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Сканирование QR-кода",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    androidx.compose.material3.Button(
                        onClick = {
                            showQrOptionDialog = false
                            showCameraScanner = true
                        },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = NeonCyan,
                            contentColor = Color(0xFF001F29)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Сканировать камерой", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    androidx.compose.material3.OutlinedButton(
                        onClick = {
                            showQrOptionDialog = false
                            homeQrPickerLauncher.launch("image/*")
                        },
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2A3A52)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Выбрать фото QR из галереи", fontSize = 13.sp)
                    }

                    if (currentConfig != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        androidx.compose.material3.OutlinedButton(
                            onClick = {
                                showQrOptionDialog = false
                                showQrDialogForConfig = currentConfig
                            },
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = NeonEmerald),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonEmerald.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Показать QR текущего сервера", fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }

    if (showImportDialog) {
        com.example.ui.components.ImportTextDialog(
            onDismiss = { showImportDialog = false },
            onImportConfigs = { importedList ->
                showImportDialog = false
                coroutineScope.launch {
                    val db = AppDatabase.getInstance(context)
                    importedList.forEach { cfg ->
                        db.vlessDao().insert(cfg)
                    }
                    val first = importedList.firstOrNull()
                    if (first != null) {
                        db.vlessDao().clearSelection()
                        val id = db.vlessDao().insert(first.copy(isSelected = true))
                        VpnController.setCurrentConfig(first.copy(id = id, isSelected = true))
                    }
                    val msg = if (importedList.size == 1) "Импортирован: ${first?.name}" else "Импортировано серверов: ${importedList.size}"
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            }
        )
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
                        text = "TesUtility",
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
                        val extracted = VlessParser.extractAll(clipText)
                        if (extracted.isNotEmpty()) {
                            coroutineScope.launch {
                                val db = AppDatabase.getInstance(context)
                                extracted.forEach { cfg ->
                                    db.vlessDao().insert(cfg)
                                }
                                val first = extracted.first()
                                db.vlessDao().clearSelection()
                                val id = db.vlessDao().insert(first.copy(isSelected = true))
                                VpnController.setCurrentConfig(first.copy(id = id, isSelected = true))
                                val msg = if (extracted.size == 1) "Импортирован: ${first.name}" else "Импортировано: ${extracted.size} серверов"
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                        } else {
                            // If no link found directly, show the smart recognition dialog
                            showImportDialog = true
                        }
                    } else {
                        showImportDialog = true
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
