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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.Surface
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
            val parsed = VlessParser.findFirst(rawUri) ?: VlessParser.parse(rawUri)
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
                val initial = AppDatabase.getInitialConfigs().first()
                VpnController.setCurrentConfig(initial)
                coroutineScope.launch {
                    val count = db.vlessDao().getSelectedConfig()
                    if (count == null) {
                        db.vlessDao().insertAll(AppDatabase.getInitialConfigs())
                    }
                }
            }
        }
    }

    val onConnectAction: () -> Unit = {
        val config = currentConfig ?: AppDatabase.getInitialConfigs().first().also {
            VpnController.setCurrentConfig(it)
        }
        val vpnIntent = try {
            VpnService.prepare(context)
        } catch (e: Exception) {
            null
        }
        if (vpnIntent != null) {
            try {
                vpnPrepareLauncher.launch(vpnIntent)
            } catch (e: Exception) {
                VpnController.startVpn(context, config)
            }
        } else {
            VpnController.startVpn(context, config)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = HappBgDark,
        contentWindowInsets = WindowInsets.systemBars,
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
                        val vpnIntent = try {
                            VpnService.prepare(context)
                        } catch (e: Exception) {
                            null
                        }
                        if (vpnIntent != null) {
                            try {
                                vpnPrepareLauncher.launch(vpnIntent)
                            } catch (e: Exception) {
                                VpnController.startVpn(context, cfg)
                            }
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
    onTabSelected: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF131A26),
            tonalElevation = 6.dp,
            shadowElevation = 10.dp,
            border = BorderStroke(1.dp, Color(0xFF233045)),
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MainTab.values().forEach { tab ->
                    val isSelected = currentTab == tab
                    val tabColor by animateColorAsState(
                        targetValue = if (isSelected) NeonCyan else TextMuted,
                        animationSpec = tween(220),
                        label = "tab_color"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) NeonCyan.copy(alpha = 0.12f) else Color.Transparent)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = true, radius = 32.dp),
                                onClick = { onTabSelected(tab) }
                            )
                            .padding(vertical = 6.dp)
                            .testTag("tab_${tab.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title,
                                tint = tabColor,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = tab.title,
                                color = tabColor,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
