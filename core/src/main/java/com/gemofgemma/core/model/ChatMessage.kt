package com.gemofgemma.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.util.UUID

@Serializable
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: Role,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    @Transient val imageBytes: ByteArray? = null,
    val detections: List<DetectionResult>? = null,
    val ocrBlocks: List<OcrTextBlock>? = null,
    val messageType: MessageType = MessageType.TEXT,
    val thinkingContent: String? = null
) {
    @Serializable
    enum class Role {
        USER,
        ASSISTANT,
        SYSTEM
    }

    @Serializable
    enum class MessageType {
        TEXT,
        IMAGE_QUERY,
        DETECTION,
        OCR,
        ACTION,
        ERROR
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ChatMessage) return false
        return id == other.id &&
            role == other.role &&
            content == other.content &&
            timestamp == other.timestamp &&
            imageBytes.contentEqualsNullable(other.imageBytes) &&
            detections == other.detections &&
            ocrBlocks == other.ocrBlocks &&
            messageType == other.messageType &&
            thinkingContent == other.thinkingContent
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + role.hashCode()
        result = 31 * result + content.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + (imageBytes?.contentHashCode() ?: 0)
        result = 31 * result + (detections?.hashCode() ?: 0)
        result = 31 * result + (ocrBlocks?.hashCode() ?: 0)
        result = 31 * result + messageType.hashCode()
        result = 31 * result + (thinkingContent?.hashCode() ?: 0)
        return result
    }
}

private fun ByteArray?.contentEqualsNullable(other: ByteArray?): Boolean {
    if (this === other) return true
    if (this == null || other == null) return false
    return this.contentEquals(other)
}
