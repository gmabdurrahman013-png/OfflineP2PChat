package com.example.offlinep2p

import android.content.Context
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Wraps Google's Nearby Connections API to give simple advertise/discover/connect/send
 * behaviour with zero internet or SIM network required. Works over Bluetooth (for the
 * handshake and small text messages) and automatically upgrades to Wi-Fi Direct for
 * fast file (image) transfer.
 */
class NearbyManager(private val context: Context, private val myName: String) {

    companion object {
        private const val TAG = "NearbyManager"
        // Must match on both devices for them to be able to find each other.
        const val SERVICE_ID = "com.example.offlinep2p.SERVICE_ID"
        private val STRATEGY = Strategy.P2P_CLUSTER

        private const val TYPE_TEXT = "text"
        private const val TYPE_IMAGE_META = "image_meta"
    }

    private val connectionsClient = Nearby.getConnectionsClient(context)

    // endpointId -> human-readable name, for devices found while discovering
    private val _discoveredEndpoints = MutableStateFlow<Map<String, String>>(emptyMap())
    val discoveredEndpoints: StateFlow<Map<String, String>> = _discoveredEndpoints.asStateFlow()

    // endpointId -> name, for devices we are actively connected to
    private val _connectedEndpoints = MutableStateFlow<Map<String, String>>(emptyMap())
    val connectedEndpoints: StateFlow<Map<String, String>> = _connectedEndpoints.asStateFlow()

    // Fired whenever a text message or completed image arrives.
    var onTextReceived: ((endpointId: String, text: String) -> Unit)? = null
    var onImageReceived: ((endpointId: String, filePath: String) -> Unit)? = null

    // Tracks in-flight file payloads until they finish, so we can match them to their metadata.
    private val pendingFilePayloads = mutableMapOf<Long, Payload>()
    private val pendingImageFileNames = mutableMapOf<Long, String>()

    // ---------- Advertising (make this device discoverable) ----------

    fun startAdvertising() {
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        connectionsClient.startAdvertising(
            myName, SERVICE_ID, connectionLifecycleCallback, options
        ).addOnFailureListener { e -> Log.e(TAG, "Advertising failed", e) }
    }

    // ---------- Discovery (find other nearby devices) ----------

    fun startDiscovery() {
        val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        connectionsClient.startDiscovery(
            SERVICE_ID, endpointDiscoveryCallback, options
        ).addOnFailureListener { e -> Log.e(TAG, "Discovery failed", e) }
    }

    fun stopAll() {
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
        _discoveredEndpoints.value = emptyMap()
        _connectedEndpoints.value = emptyMap()
    }

    // ---------- Connecting ----------

    fun requestConnection(endpointId: String) {
        connectionsClient.requestConnection(myName, endpointId, connectionLifecycleCallback)
            .addOnFailureListener { e -> Log.e(TAG, "requestConnection failed", e) }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallbackCompat() {
        override fun onFound(endpointId: String, info: DiscoveredEndpointInfo) {
            _discoveredEndpoints.value = _discoveredEndpoints.value + (endpointId to info.endpointName)
        }

        override fun onLost(endpointId: String) {
            _discoveredEndpoints.value = _discoveredEndpoints.value - endpointId
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            // Auto-accept for simplicity. For production, show a confirmation dialog
            // with info.authenticationDigits so users can verify it's the right device.
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.statusCode == ConnectionsStatusCodes.STATUS_OK) {
                val name = _discoveredEndpoints.value[endpointId] ?: endpointId
                _connectedEndpoints.value = _connectedEndpoints.value + (endpointId to name)
            }
        }

        override fun onDisconnected(endpointId: String) {
            _connectedEndpoints.value = _connectedEndpoints.value - endpointId
        }
    }

    // ---------- Sending ----------

    fun sendText(endpointId: String, text: String) {
        val json = JSONObject().apply {
            put("type", TYPE_TEXT)
            put("content", text)
        }
        connectionsClient.sendPayload(endpointId, Payload.fromBytes(json.toString().toByteArray()))
    }

    fun sendImage(endpointId: String, file: File) {
        // 1) Tell the receiver an image is coming, with its filename, so it knows
        //    how to label the incoming file payload once it arrives.
        val metaJson = JSONObject().apply {
            put("type", TYPE_IMAGE_META)
            put("fileName", file.name)
        }
        connectionsClient.sendPayload(endpointId, Payload.fromBytes(metaJson.toString().toByteArray()))

        // 2) Send the actual file bytes; Nearby Connections will automatically use
        //    Wi-Fi Direct under the hood for speed if it's available.
        val filePayload = Payload.fromFile(file)
        connectionsClient.sendPayload(endpointId, filePayload)
    }

    // ---------- Receiving ----------

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> {
                    val bytes = payload.asBytes() ?: return
                    val json = JSONObject(String(bytes))
                    when (json.optString("type")) {
                        TYPE_TEXT -> onTextReceived?.invoke(endpointId, json.optString("content"))
                        TYPE_IMAGE_META -> pendingImageFileNames[payload.id] = json.optString("fileName")
                    }
                }
                Payload.Type.FILE -> {
                    pendingFilePayloads[payload.id] = payload
                }
                else -> Unit
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            if (update.status != PayloadTransferUpdate.Status.SUCCESS) return
            val payload = pendingFilePayloads.remove(update.payloadId) ?: return
            val fileUri = payload.asFile()?.asUri() ?: return

            val destDir = File(context.filesDir, "chat_images").apply { mkdirs() }
            val destFile = File(destDir, "img_${System.currentTimeMillis()}.jpg")

            context.contentResolver.openInputStream(fileUri)?.use { input ->
                FileOutputStream(destFile).use { output -> input.copyTo(output) }
            }

            onImageReceived?.invoke(endpointId, destFile.absolutePath)
        }
    }
}

/**
 * Small compatibility shim: newer play-services-nearby versions changed the
 * EndpointDiscoveryCallback method signatures slightly across releases, so we
 * centralize it here for easy adjustment if you bump the library version.
 */
abstract class EndpointDiscoveryCallbackCompat : com.google.android.gms.nearby.connection.EndpointDiscoveryCallback() {
    abstract fun onFound(endpointId: String, info: DiscoveredEndpointInfo)
    abstract fun onLost(endpointId: String)

    override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) = onFound(endpointId, info)
    override fun onEndpointLost(endpointId: String) = onLost(endpointId)
}
