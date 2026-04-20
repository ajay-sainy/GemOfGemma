package com.gemofgemma.core.data

import android.content.Context
import com.gemofgemma.core.model.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class ChatRepository(
    private val chatDao: ChatDao,
    private val context: Context
) {
    private val imageDir = File(context.filesDir, "chat_images").also { it.mkdirs() }

    fun getConversations(): Flow<List<ConversationEntity>> = chatDao.getConversations()

    suspend fun getLatestConversation(): ConversationEntity? = chatDao.getLatestConversation()

    suspend fun createConversation(title: String = "New Chat"): ConversationEntity {
        val entity = ConversationEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        // Don't insert yet — will be inserted on first message
        return entity
    }

    private suspend fun ensureConversationExists(conversationId: String, title: String = "New Chat") {
        val existing = chatDao.getConversationById(conversationId)
        if (existing == null) {
            chatDao.insertConversation(
                ConversationEntity(
                    id = conversationId,
                    title = title,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun saveMessage(conversationId: String, message: ChatMessage) {
        withContext(Dispatchers.IO) {
            // Ensure conversation is persisted on first message
            val title = if (message.role == ChatMessage.Role.USER && message.content.isNotBlank())
                message.content.take(50) else "New Chat"
            ensureConversationExists(conversationId, title)
            val imageUri = if (message.imageBytes != null) {
                val file = File(imageDir, "${message.id}.jpg")
                file.writeBytes(message.imageBytes!!)
                file.absolutePath
            } else null

            val entity = MessageEntity(
                id = message.id,
                conversationId = conversationId,
                role = message.role.name,
                content = message.content,
                timestamp = message.timestamp,
                messageType = message.messageType.name,
                imageUri = imageUri,
                thinkingContent = message.thinkingContent
            )
            chatDao.insertMessage(entity)
            chatDao.updateConversationTimestamp(conversationId, System.currentTimeMillis())
        }
    }

    suspend fun loadMessages(conversationId: String): List<ChatMessage> {
        return withContext(Dispatchers.IO) {
            chatDao.getMessagesList(conversationId).map { entity ->
                val imageBytes = entity.imageUri?.let { uri ->
                    try { File(uri).takeIf { it.exists() }?.readBytes() } catch (_: Exception) { null }
                }
                ChatMessage(
                    id = entity.id,
                    role = ChatMessage.Role.valueOf(entity.role),
                    content = entity.content,
                    timestamp = entity.timestamp,
                    imageBytes = imageBytes,
                    messageType = ChatMessage.MessageType.valueOf(entity.messageType),
                    thinkingContent = entity.thinkingContent
                )
            }
        }
    }

    suspend fun deleteConversation(id: String) {
        withContext(Dispatchers.IO) {
            chatDao.getMessagesList(id).forEach { msg ->
                msg.imageUri?.let { uri -> try { File(uri).delete() } catch (_: Exception) { } }
            }
            chatDao.deleteConversation(id)
        }
    }

    suspend fun updateConversationTitle(id: String, title: String) {
        chatDao.updateConversationTitle(id, title)
    }
}
