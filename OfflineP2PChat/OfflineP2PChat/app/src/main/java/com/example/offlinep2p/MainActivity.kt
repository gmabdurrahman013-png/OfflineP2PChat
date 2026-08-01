package com.example.offlinep2p

import android.Manifest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import com.example.offlinep2p.data.AppDatabase
import com.example.offlinep2p.data.ChatMessage
import com.example.offlinep2p.ui.ChatScreen
import com.example.offlinep2p.ui.DeviceListScreen
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import kotlin.random.Random

class MainActivity : ComponentActivity() {

    private lateinit var nearbyManager: NearbyManager
    private lateinit var db: AppDatabase
    private val myName = "Phone-${Random.nextInt(1000, 9999)}"

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            nearbyManager.startAdvertising()
            nearbyManager.startDiscovery()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        db = AppDatabase.getInstance(applicationContext)
        nearbyManager = NearbyManager(applicationContext, myName)

        nearbyManager.onTextReceived = { endpointId, text ->
            val peerName = nearbyManager.connectedEndpoints.value[endpointId] ?: endpointId
            lifecycleScope.launch {
                db.chatDao().insert(
                    ChatMessage(
                        peerEndpointId = endpointId,
                        peerName = peerName,
                        isMine = false,
                        isImage = false,
                        textContent = text
                    )
                )
            }
        }

        nearbyManager.onImageReceived = { endpointId, filePath ->
            val peerName = nearbyManager.connectedEndpoints.value[endpointId] ?: endpointId
            lifecycleScope.launch {
                db.chatDao().insert(
                    ChatMessage(
                        peerEndpointId = endpointId,
                        peerName = peerName,
                        isMine = false,
                        isImage = true,
                        imagePath = filePath
                    )
                )
            }
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier) {
                    AppRoot(nearbyManager, db, ::requestNeededPermissions)
                }
            }
        }

        requestNeededPermissions()
    }

    private fun requestNeededPermissions() {
        val perms = mutableListOf(
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms += Manifest.permission.NEARBY_WIFI_DEVICES
            perms += Manifest.permission.READ_MEDIA_IMAGES
        } else {
            perms += Manifest.permission.ACCESS_FINE_LOCATION
            perms += Manifest.permission.READ_EXTERNAL_STORAGE
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    override fun onDestroy() {
        super.onDestroy()
        nearbyManager.stopAll()
    }
}

/**
 * Simple two-screen navigation (device list <-> chat) driven by local state,
 * no navigation library required.
 */
@Composable
fun AppRoot(nearbyManager: NearbyManager, db: AppDatabase, onRequestPermissions: () -> Unit) {
    val context = LocalContext.current
    val discovered by nearbyManager.discoveredEndpoints.collectAsState()
    val connected by nearbyManager.connectedEndpoints.collectAsState()

    var selectedPeer by remember { mutableStateOf<Pair<String, String>?>(null) } // endpointId to name
    val scope = rememberCoroutineScope()

    val currentPeer = selectedPeer
    if (currentPeer == null) {
        DeviceListScreen(
            discovered = discovered,
            connected = connected,
            onConnectClick = { endpointId -> nearbyManager.requestConnection(endpointId) },
            onOpenChat = { endpointId, name -> selectedPeer = endpointId to name }
        )
    } else {
        val (endpointId, name) = currentPeer
        val messages by remember(endpointId) {
            db.chatDao().getMessagesForPeer(endpointId)
        }.collectAsState(initial = emptyList())

        ChatScreen(
            peerName = name,
            messages = messages,
            onSendText = { text ->
                nearbyManager.sendText(endpointId, text)
                scope.launch {
                    db.chatDao().insert(
                        ChatMessage(
                            peerEndpointId = endpointId,
                            peerName = name,
                            isMine = true,
                            isImage = false,
                            textContent = text
                        )
                    )
                }
            },
            onSendImage = { uri: Uri ->
                scope.launch {
                    val destDir = File(context.filesDir, "chat_images").apply { mkdirs() }
                    val destFile = File(destDir, "img_${System.currentTimeMillis()}.jpg")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(destFile).use { output -> input.copyTo(output) }
                    }
                    nearbyManager.sendImage(endpointId, destFile)
                    db.chatDao().insert(
                        ChatMessage(
                            peerEndpointId = endpointId,
                            peerName = name,
                            isMine = true,
                            isImage = true,
                            imagePath = destFile.absolutePath
                        )
                    )
                }
            },
            onBack = { selectedPeer = null }
        )
    }
}
