package com.example.offlinep2p.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    @Insert
    suspend fun insert(message: ChatMessage): Long

    @Query("SELECT * FROM messages WHERE peerEndpointId = :endpointId ORDER BY timestamp ASC")
    fun getMessagesForPeer(endpointId: String): Flow<List<ChatMessage>>

    @Query("SELECT DISTINCT peerEndpointId FROM messages")
    suspend fun getAllConversationPeerIds(): List<String>
}
