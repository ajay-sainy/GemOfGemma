package com.gemofgemma.ai.parsers

import com.gemofgemma.core.model.DetectionResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parses Gemma 4's detection JSON output into [DetectionResult] objects.
 *
 * Expected model output format:
 * ```json
 * [{"box_2d": [y1, x1, y2, x2], "label": "object_name"}]
 * ```
 * Coordinates are normalized to a 1000×1000 grid.
 */
@Singleton
class DetectionResponseParser @Inject constructor() {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Serializable
    private data class RawDetection(
        @SerialName("box_2d") val boxCoordinates: List<Float>,
        val label: String
    )

    fun parse(modelOutput: String): List<DetectionResult> {
        val jsonStr = extractJsonArray(modelOutput)
        return try {
            val rawDetections = json.decodeFromString<List<RawDetection>>(jsonStr)
            rawDetections.mapNotNull { raw ->
                if (raw.boxCoordinates.size == 4) {
                    DetectionResult(
                        label = raw.label,
                        y1 = raw.boxCoordinates[0],
                        x1 = raw.boxCoordinates[1],
                        y2 = raw.boxCoordinates[2],
                        x2 = raw.boxCoordinates[3]
                    )
                } else null
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun extractJsonArray(text: String): String {
        val start = text.indexOf('[')
        val end = text.lastIndexOf(']')
        if (start != -1 && end != -1 && end > start) {
            return text.substring(start, end + 1)
        }
        return text.trim()
    }
}
