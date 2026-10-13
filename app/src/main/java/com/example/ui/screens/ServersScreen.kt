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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.db.AppDatabase
import com.example.model.VlessConfig
import com.example.parser.VlessParser
import com.example.qr.QrUtils
import com.example.service.VpnController
import com.example.ui.components.EditConfigDialog
import com.example.ui.components.PingBadge
import com.example.ui.components.QrDialog
import com.example.ui.theme.DangerRed
import com.example.ui.theme.HappBgDark
import com.example.ui.theme.HappCardBorder
import com.example.ui.theme.HappCardDark
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServersScreen(
    onSelectAndConnect: (VlessConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val db = remember { AppDatabase.getInstance(context) }
    val configs by db.vlessDao().getAllConfigs().collectAsState(initial = emptyList())
    val currentConfig by VpnController.currentConfig.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("Все") }
    var showAddMenu by remember { mutableStateOf(false) }
    var editingConfig by remember { mutableStateOf<VlessConfig?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showQrForConfig by remember { mutableStateOf<VlessConfig?>(null) }
    var showImportTextDialog by remember { mutableStateOf(false) }
    var showCameraScanner by remember { mutableStateOf(false) }
    var isPingingAll by remember { mutableStateOf(false) }

    // Image Picker for scanning QR code from gallery
    val imagePickerLauncher = rememberLauncherForActivityResult(
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
                                extracted.forEach { db.vlessDao().insert(it) }
                                val first = extracted.first()
                                db.vlessDao().clearSelection()
                                val id = db.vlessDao().insert(first.copy(isSelected = true))
                                VpnController.setCurrentConfig(first.copy(id = id, isSelected = true))
                                val msg = if (extracted.size == 1) "Импортирован из QR: ${first.name}" else "Импортировано из QR: ${extracted.size} серверов"
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "В QR-коде не найдено ссылок VLESS", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "В изображении не найден QR-код", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Ошибка чтения изображения: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // QR Dialog
    showQrForConfig?.let { cfg ->
        QrDialog(config = cfg, onDismiss = { showQrForConfig = null })
    }

    // Edit/Create Dialog
    if (showCreateDialog) {
        EditConfigDialog(
            initialConfig = null,
            onDismiss = { showCreateDialog = false },
            onSave = { newCfg ->
                coroutineScope.launch {
                    val id = db.vlessDao().insert(newCfg)
                    if (newCfg.isSelected) {
                        db.vlessDao().selectConfig(id)
                        VpnController.setCurrentConfig(newCfg.copy(id = id))
                    }
                    showCreateDialog = false
                    Toast.makeText(context, "Конфигурация сохранена", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (showImportTextDialog) {
        com.example.ui.components.ImportTextDialog(
            onDismiss = { showImportTextDialog = false },
            onImportConfigs = { list ->
                showImportTextDialog = false
                coroutineScope.launch {
                    list.forEach { db.vlessDao().insert(it) }
                    val first = list.firstOrNull()
                    if (first != null) {
                        db.vlessDao().clearSelection()
                        val id = db.vlessDao().insert(first.copy(isSelected = true))
                        VpnController.setCurrentConfig(first.copy(id = id, isSelected = true))
                    }
                    val msg = if (list.size == 1) "Импортирован: ${first?.name}" else "Импортировано: ${list.size} серверов"
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    editingConfig?.let { cfg ->
        EditConfigDialog(
            initialConfig = cfg,
            onDismiss = { editingConfig = null },
            onSave = { updated ->
                coroutineScope.launch {
                    db.vlessDao().update(updated)
                    if (currentConfig?.id == updated.id) {
                        VpnController.setCurrentConfig(updated)
                    }
                    editingConfig = null
                    Toast.makeText(context, "Обновлено", Toast.LENGTH_SHORT).show()
                }
            }
        )
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
                        extracted.forEach { db.vlessDao().insert(it) }
                        val first = extracted.first()
                        db.vlessDao().clearSelection()
                        val id = db.vlessDao().insert(first.copy(isSelected = true))
                        VpnController.setCurrentConfig(first.copy(id = id, isSelected = true))
                        val msg = if (extracted.size == 1) "Импортирован из QR: ${first.name}" else "Импортировано из QR: ${extracted.size} серверов"
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "В QR-коде не найдено ссылок VLESS", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HappBgDark)
            .padding(top = 16.dp, start = 16.dp, end = 16.dp)
    ) {
        // Screen Header with Title and Add Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Конфигурации",
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${configs.size} серверов VLESS Reality",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }

            Box {
                Button(
                    onClick = { showAddMenu = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCyan,
                        contentColor = Color(0xFF001F26)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("add_config_button")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Добавить", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                DropdownMenu(
                    expanded = showAddMenu,
                    onDismissRequest = { showAddMenu = false },
                    modifier = Modifier
                        .background(HappCardDark)
                        .border(1.dp, HappCardBorder, RoundedCornerShape(12.dp))
                ) {
                    DropdownMenuItem(
                        text = { Text("Сканировать QR камерой", color = TextPrimary, fontWeight = FontWeight.SemiBold) },
                        leadingIcon = { Icon(Icons.Default.QrCode, contentDescription = null, tint = NeonCyan) },
                        onClick = {
                            showAddMenu = false
                            showCameraScanner = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Выбрать QR из галереи", color = TextPrimary) },
                        leadingIcon = { Icon(Icons.Default.QrCode, contentDescription = null, tint = NeonEmerald) },
                        onClick = {
                            showAddMenu = false
                            imagePickerLauncher.launch("image/*")
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Импорт из буфера обмена", color = TextPrimary) },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color(0xFF67E8F9)) },
                        onClick = {
                            showAddMenu = false
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                            if (clipText.isNotBlank()) {
                                val extracted = VlessParser.extractAll(clipText)
                                if (extracted.isNotEmpty()) {
                                    coroutineScope.launch {
                                        extracted.forEach { db.vlessDao().insert(it) }
                                        val first = extracted.firstOrNull()
                                        if (first != null) {
                                            db.vlessDao().clearSelection()
                                            val id = db.vlessDao().insert(first.copy(isSelected = true))
                                            VpnController.setCurrentConfig(first.copy(id = id, isSelected = true))
                                        }
                                        val msg = if (extracted.size == 1) "Добавлен и выбран: ${first?.name}" else "Добавлено серверов: ${extracted.size}"
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    }
                                } else {
                                    showImportTextDialog = true
                                }
                            } else {
                                showImportTextDialog = true
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Распознать из текста / чата", color = TextPrimary) },
                        leadingIcon = { Icon(Icons.Default.ContentPaste, contentDescription = null, tint = TextSecondary) },
                        onClick = {
                            showAddMenu = false
                            showImportTextDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Создать вручную", color = TextPrimary) },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFFA78BFA)) },
                        onClick = {
                            showAddMenu = false
                            showCreateDialog = true
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Поиск серверов по имени или IP...", color = TextMuted, fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = NeonCyan,
                unfocusedBorderColor = HappCardBorder,
                focusedContainerColor = Color(0xFF111722),
                unfocusedContainerColor = Color(0xFF111722)
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Filter Chips Row & Ping All Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                val filters = listOf("Все", "Reality", "Vision", "Быстрые (<70ms)")
                items(filters) { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                            selectedLabelColor = NeonCyan,
                            containerColor = Color(0xFF131924),
                            labelColor = TextMuted
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedFilter == filter,
                            borderColor = if (selectedFilter == filter) NeonCyan else Color(0xFF243044)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Ping All Button
            IconButton(
                onClick = {
                    if (!isPingingAll && configs.isNotEmpty()) {
                        isPingingAll = true
                        coroutineScope.launch {
                            configs.forEach { cfg ->
                                VpnController.measurePing(cfg.server, cfg.port) { ping ->
                                    coroutineScope.launch {
                                        db.vlessDao().updatePing(cfg.id, ping)
                                    }
                                }
                            }
                            isPingingAll = false
                            Toast.makeText(context, "Пинг всех серверов обновлен", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0xFF141C2A))
                    .size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Sensors,
                    contentDescription = "Test all ping",
                    tint = if (isPingingAll) NeonEmerald else TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Filter and Search configs
        val filteredConfigs = configs.filter { cfg ->
            val matchesQuery = searchQuery.isBlank() ||
                    cfg.name.contains(searchQuery, ignoreCase = true) ||
                    cfg.server.contains(searchQuery, ignoreCase = true)

            val matchesFilter = when (selectedFilter) {
                "Reality" -> cfg.isReality
                "Vision" -> cfg.isVision
                "Быстрые (<70ms)" -> cfg.pingMs in 0..70
                else -> true
            }

            matchesQuery && matchesFilter
        }

        // List of Servers
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(filteredConfigs, key = { it.id }) { config ->
                val isSelected = (currentConfig?.id == config.id) || config.isSelected

                ServerItemCard(
                    config = config,
                    isSelected = isSelected,
                    onSelect = {
                        coroutineScope.launch {
                            db.vlessDao().clearSelection()
                            db.vlessDao().selectConfig(config.id)
                            VpnController.setCurrentConfig(config.copy(isSelected = true))
                            onSelectAndConnect(config)
                        }
                    },
                    onShowQr = { showQrForConfig = config },
                    onEdit = { editingConfig = config },
                    onDelete = {
                        coroutineScope.launch {
                            db.vlessDao().delete(config)
                            if (currentConfig?.id == config.id) {
                                val remaining = db.vlessDao().getSelectedConfig()
                                VpnController.setCurrentConfig(remaining)
                            }
                            Toast.makeText(context, "Удалено", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}

@Composable
private fun ServerItemCard(
    config: VlessConfig,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onShowQr: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (isSelected) Color(0xFF131F2E) else HappCardDark)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) NeonCyan else HappCardBorder,
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onSelect)
            .padding(14.dp)
            .testTag("server_item_${config.id}")
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Radio indicator
                Icon(
                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Outlined.Circle,
                    contentDescription = null,
                    tint = if (isSelected) NeonCyan else TextMuted,
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(10.dp))

                // Country Flag
                Text(
                    text = config.countryFlag,
                    fontSize = 20.sp
                )

                Spacer(modifier = Modifier.width(10.dp))

                // Server title and subtitle
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = config.name,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${config.server}:${config.port}",
                        color = TextMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Ping Badge
                PingBadge(pingMs = config.pingMs)

                Spacer(modifier = Modifier.width(4.dp))

                // Options Menu
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier
                            .background(HappCardDark)
                            .border(1.dp, HappCardBorder, RoundedCornerShape(12.dp))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Показать QR-код", color = TextPrimary) },
                            leadingIcon = { Icon(Icons.Default.QrCode, contentDescription = null, tint = NeonCyan) },
                            onClick = {
                                showMenu = false
                                onShowQr()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Скопировать ссылку", color = TextPrimary) },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = TextSecondary) },
                            onClick = {
                                showMenu = false
                                val uri = if (config.rawUri.isNotBlank()) config.rawUri else VlessParser.toUri(config)
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("VLESS", uri))
                                Toast.makeText(context, "Ссылка скопирована", Toast.LENGTH_SHORT).show()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Редактировать", color = TextPrimary) },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFFA78BFA)) },
                            onClick = {
                                showMenu = false
                                onEdit()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Удалить", color = DangerRed) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = DangerRed) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Protocol Chips Tag Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TagChip(text = if (config.isReality) "REALITY" else config.security.uppercase(), color = NeonCyan)
                if (config.isVision) {
                    TagChip(text = "XTLS-VISION", color = NeonEmerald)
                }
                TagChip(text = config.network.uppercase(), color = Color(0xFFA78BFA))
                if (config.sni.isNotBlank()) {
                    TagChip(text = "SNI: ${config.sni}", color = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun TagChip(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
