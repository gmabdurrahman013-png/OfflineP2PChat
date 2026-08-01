package com.example.offlinep2p.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single chat message stored fully offline in the local Room database.
 * Nothing here is ever sent to a server — it only ever travels device-to-device
 * over Bluetooth / Wi-Fi Direct via the Nearby Connections API.
 */
@Entity(tableName = "messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val peerEndpointId: String,      // which nearby device this conversation is with
    val peerName: String,            // display name of that device/user
    val isMine: Boolean,             // true if I sent it, false if received
    val isImage: Boolean,
    val textContent: String? = null, // used when isImage == false
    val imagePath: String? = null,   // local file path, used when isImage == true
    val timestamp: Long = System.currentTimeMillis()
)
