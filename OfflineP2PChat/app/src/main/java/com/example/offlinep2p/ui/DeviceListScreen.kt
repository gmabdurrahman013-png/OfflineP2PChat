package com.example.offlinep2p.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.offlinep2p.NearbyManager

/**
 * "Radar" screen: shows nearby devices found over Bluetooth/Wi-Fi Direct
 * (no internet or SIM connection involved at all) and lets the user tap
 * one to connect and start chatting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceListScreen(
    discovered: Map<String, String>,
    connected: Map<String, String>,
    onConnectClick: (endpointId: String) -> Unit,
    onOpenChat: (endpointId: String, name: String) -> Unit
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Nearby Devices") }) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {

            if (connected.isNotEmpty()) {
                Text("Connected", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                LazyColumn {
                    items(connected.entries.toList()) { (id, name) ->
                        ListItem(
                            headlineContent = { Text(name) },
                            supportingContent = { Text("Tap to chat") },
                            leadingContent = { Icon(Icons.Filled.Wifi, contentDescription = null) },
                            modifier = Modifier.clickableRow { onOpenChat(id, name) }
                        )
                        Divider()
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            Text("Available nearby", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            val availableToConnect = discovered.filterKeys { it !in connected.keys }
            if (availableToConnect.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
                    Text("Searching for nearby phones\u2026", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LazyColumn {
                    items(availableToConnect.entries.toList()) { (id, name) ->
                        ListItem(
                            headlineContent = { Text(name) },
                            supportingContent = { Text("Tap to connect") },
                            modifier = Modifier.clickableRow { onConnectClick(id) }
                        )
                        Divider()
                    }
                }
            }
        }
    }
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier =
    this.then(androidx.compose.foundation.clickable(onClick = onClick))
