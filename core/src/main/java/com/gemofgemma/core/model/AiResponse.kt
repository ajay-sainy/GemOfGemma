package com.gemofgemma.core.model

sealed class AiResponse {
    data class TextResponse(
        val text: String
    ) : AiResponse()

    data class DetectionResponse(
        val detections: List<DetectionResult>
    ) : AiResponse()

    data class OcrResponse(
        val blocks: List<OcrTextBlock>,
        val fullText: String
    ) : AiResponse()

    data class ActionResponse(
        val actionResult: ActionResult
    ) : AiResponse()

    data class ErrorResponse(
        val message: String,
        val cause: Throwable? = null
    ) : AiResponse()
}

sealed class ActionResult {
    data class Success(val message: String) : ActionResult()
    data class Error(val message: String, val cause: Throwable? = null) : ActionResult()
    data class PermissionRequired(val permissions: List<String>) : ActionResult()
    data class NeedsConfirmation(val actionDescription: String, val onConfirm: () -> Unit) : ActionResult()
}
