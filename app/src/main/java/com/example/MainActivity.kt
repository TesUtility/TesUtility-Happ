package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.db.AppDatabase
import com.example.model.VlessConfig
import com.example.parser.VlessParser
import com.example.service.VpnController
import com.example.service.VpnStatus
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.RoutingScreen
import com.example.ui.screens.ServersScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.HappBgDark
import com.example.ui.theme.HappCardBorder
import com.example.ui.theme.HappSurfaceDark
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

enum class MainTab(val title: String, val icon: ImageVector) {
    HOME("Главная", Icons.Default.PowerSettingsNew),
    SERVERS("Серверы", Icons.Default.Storage),
    ROUTING("Маршруты", Icons.AutoMirrored.Filled.AltRoute),
    SETTINGS("Настройки", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle incoming vless:// intent if launched via link
        handleIncomingIntent(intent)

        setContent {
            MyApplicationTheme {
                MainAppScreen()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val data = intent?.data
        if (data != null && data.scheme.equals("vless", ignoreCase = true)) {
            val rawUri = data.toString()
            val parsed = VlessParser.parse(rawUri)
            if (parsed != null) {
                val db = AppDatabase.getInstance(applicationContext)
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    db.vlessDao().clearSelection()
                    val id = db.vlessDao().insert(parsed.copy(isSelected = true))
                    VpnController.setCurrentConfig(parsed.copy(id = id, isSelected = true))
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                        Toast.makeText(applicationContext, "Импортирован: ${parsed.name}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
}

@Composable
fun MainAppScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val db = remember { AppDatabase.getInstance(context) }
    var currentTab by remember { mutableStateOf(MainTab.HOME) }
    val currentConfig by VpnController.currentConfig.collectAsState()
    val vpnStatus by VpnController.status.collectAsState()

    // BackHandler for secondary tabs
    if (currentTab != MainTab.HOME) {
        BackHandler {
            currentTab = MainTab.HOME
        }
    }

    // Permission launcher for VPN Service
    val vpnPrepareLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val config = currentConfig
            if (config != null) {
                VpnController.startVpn(context, config)
            } else {
                Toast.makeText(context, "Выберите сервер для подключения", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Разрешение на VPN отклонено", Toast.LENGTH_SHORT).show()
        }
    }

    // Notification permission launcher for Android 13+
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Permission granted or denied handled gracefully */ }

    // On start, request notification permission on Android 13+ & load selected config
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Initialize active config from Room DB if not set
        if (currentConfig == null) {
            val selected = db.vlessDao().getSelectedConfig()
            if (selected != null) {
                VpnController.setCurrentConfig(selected)
            } else {
                // If no config selected yet, select first available after slight delay
                kotlinx.coroutines.delay(300)
                val first = db.vlessDao().getSelectedConfig()
                if (first != null) {
                    VpnController.setCurrentConfig(first)
                }
            }
        }
    }

    val onConnectAction: () -> Unit = {
        val config = currentConfig
        if (config == null) {
            Toast.makeText(context, "Выберите конфигурацию VLESS", Toast.LENGTH_SHORT).show()
            currentTab = MainTab.SERVERS
        } else {
            val vpnIntent = VpnService.prepare(context)
            if (vpnIntent != null) {
                vpnPrepareLauncher.launch(vpnIntent)
            } else {
                VpnController.startVpn(context, config)
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = HappBgDark,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            HappBottomBar(
                currentTab = currentTab,
                onTabSelected = { currentTab = it }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                MainTab.HOME -> HomeScreen(
                    onNavigateToServers = { currentTab = MainTab.SERVERS },
                    onConnectRequested = onConnectAction
                )
                MainTab.SERVERS -> ServersScreen(
                    onSelectAndConnect = { cfg ->
                        val vpnIntent = VpnService.prepare(context)
                        if (vpnIntent != null) {
                            vpnPrepareLauncher.launch(vpnIntent)
                        } else {
                            VpnController.startVpn(context, cfg)
                        }
                        currentTab = MainTab.HOME
                    }
                )
                MainTab.ROUTING -> RoutingScreen()
                MainTab.SETTINGS -> SettingsScreen()
            }
        }
    }
}

@Composable
fun HappBottomBar(
    currentTab: MainTab,
    onTabSelected: (MainTab) -> Unit
) {
    NavigationBar(
        containerColor = HappSurfaceDark,
        tonalElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .border(
                width = 1.dp,
                color = HappCardBorder,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            )
            .height(84.dp)
    ) {
        MainTab.values().forEach { tab ->
            val isSelected = currentTab == tab
            NavigationBarItem(
                selected = isSelected,
                onClick = { onTabSelected(tab) },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.title,
                        tint = if (isSelected) NeonCyan else TextMuted,
                        modifier = Modifier.size(24.dp)
                    )
                },
                label = {
                    Text(
                        text = tab.title,
                        color = if (isSelected) NeonCyan else TextMuted,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = Color(0xFF162536),
                    selectedIconColor = NeonCyan,
                    unselectedIconColor = TextMuted,
                    selectedTextColor = NeonCyan,
                    unselectedTextColor = TextMuted
                ),
                modifier = Modifier.testTag("tab_${tab.name.lowercase()}")
            )
        }
    }
}
