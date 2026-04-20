package com.gemofgemma.core.model

sealed class AiRequest {
    data class TextChat(
        val message: String,
        val conversationId: String,
        /** Full conversation history for context recovery after process death. */
        val history: List<ChatMessage> = emptyList()
    ) : AiRequest()

    data class VisionChat(
        val message: String,
        val imageBytes: ByteArray,
        val conversationId: String,
        val history: List<ChatMessage> = emptyList()
    ) : AiRequest() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is VisionChat) return false
            return message == other.message &&
                imageBytes.contentEquals(other.imageBytes) &&
                conversationId == other.conversationId &&
                history == other.history
        }

        override fun hashCode(): Int {
            var result = message.hashCode()
            result = 31 * result + imageBytes.contentHashCode()
            result = 31 * result + conversationId.hashCode()
            result = 31 * result + history.hashCode()
            return result
        }
    }
}
