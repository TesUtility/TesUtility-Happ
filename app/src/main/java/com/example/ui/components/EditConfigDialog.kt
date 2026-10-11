package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.VlessConfig
import com.example.parser.VlessParser
import com.example.ui.theme.HappCardDark
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.UUID

@Composable
fun EditConfigDialog(
    initialConfig: VlessConfig?,
    onDismiss: () -> Unit,
    onSave: (VlessConfig) -> Unit
) {
    var name by remember { mutableStateOf(initialConfig?.name ?: "VLESS Reality New") }
    var server by remember { mutableStateOf(initialConfig?.server ?: "") }
    var portText by remember { mutableStateOf(initialConfig?.port?.toString() ?: "443") }
    var uuid by remember { mutableStateOf(initialConfig?.uuid ?: UUID.randomUUID().toString()) }
    var flow by remember { mutableStateOf(initialConfig?.flow ?: "xtls-rprx-vision") }
    var sni by remember { mutableStateOf(initialConfig?.sni ?: "dl.google.com") }
    var pbk by remember { mutableStateOf(initialConfig?.publicKey ?: "") }
    var sid by remember { mutableStateOf(initialConfig?.shortId ?: "") }
    var fingerprint by remember { mutableStateOf(initialConfig?.fingerprint ?: "chrome") }
    var countryCode by remember { mutableStateOf(initialConfig?.countryCode ?: "DE") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = HappCardDark,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2A364F)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (initialConfig == null) "Новый VLESS Reality" else "Настройка сервера",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                HappTextField(label = "Название", value = name, onValueChange = { name = it })
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HappTextField(
                        label = "Сервер / IP",
                        value = server,
                        onValueChange = { server = it },
                        modifier = Modifier.weight(2f)
                    )
                    HappTextField(
                        label = "Порт",
                        value = portText,
                        onValueChange = { portText = it },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                HappTextField(label = "UUID", value = uuid, onValueChange = { uuid = it })
                Spacer(modifier = Modifier.height(8.dp))

                HappTextField(label = "Flow (XTLS)", value = flow, onValueChange = { flow = it })
                Spacer(modifier = Modifier.height(8.dp))

                HappTextField(label = "SNI (Server Name)", value = sni, onValueChange = { sni = it })
                Spacer(modifier = Modifier.height(8.dp))

                HappTextField(label = "Public Key (pbk)", value = pbk, onValueChange = { pbk = it })
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HappTextField(
                        label = "Short ID (sid)",
                        value = sid,
                        onValueChange = { sid = it },
                        modifier = Modifier.weight(1f)
                    )
                    HappTextField(
                        label = "Fingerprint",
                        value = fingerprint,
                        onValueChange = { fingerprint = it },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = {
                        val port = portText.toIntOrNull() ?: 443
                        val builtConfig = VlessConfig(
                            id = initialConfig?.id ?: 0,
                            name = name.ifBlank { "VLESS-$server" },
                            server = server.trim(),
                            port = port,
                            uuid = uuid.trim(),
                            flow = flow.trim(),
                            encryption = "none",
                            security = "reality",
                            publicKey = pbk.trim(),
                            shortId = sid.trim(),
                            sni = sni.trim(),
                            fingerprint = fingerprint.trim(),
                            countryCode = countryCode.trim(),
                            isSelected = initialConfig?.isSelected ?: false
                        )
                        val fullUri = VlessParser.toUri(builtConfig)
                        onSave(builtConfig.copy(rawUri = fullUri))
                    },
                    enabled = server.isNotBlank() && uuid.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCyan,
                        contentColor = Color(0xFF00222B)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Сохранить конфигурацию", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun HappTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp) },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedBorderColor = NeonCyan,
            unfocusedBorderColor = Color(0xFF28364B),
            focusedLabelColor = NeonCyan,
            unfocusedLabelColor = TextMuted,
            focusedContainerColor = Color(0xFF111722),
            unfocusedContainerColor = Color(0xFF111722)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    )
}
