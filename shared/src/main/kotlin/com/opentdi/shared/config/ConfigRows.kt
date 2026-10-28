package com.opentdi.shared.config

import com.opentdi.shared.model.SessionConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive

// explicitNulls stays on so a null field still gets its (empty) row.
private val rowsJson = Json {
    encodeDefaults = true
    allowSpecialFloatingPointValues = true
}

/**
 * Every [SessionConfig] field as a dotted key (`detection.baselineWindowS`, `audio.routeCapGain.NIGHTSTAND_SPEAKER`)
 * with its value in the §12.1 CSV dialect, in declaration order, for w_config.csv / p_config.csv.
 */
fun SessionConfig.toConfigRows(): List<Pair<String, String>> = flatten("", rowsJson.encodeToJsonElement(this))

/** The resolved thresholds as `resolved.*` rows (w_config.csv, p_config.csv). */
fun ResolvedThresholds.toConfigRows(): List<Pair<String, String>> = listOf(
    "resolved.dropFraction" to "$dropFraction",
    "resolved.rmssdRiseFraction" to "$rmssdRiseFraction",
    "resolved.stillThreshold" to "$stillThreshold",
    "resolved.holdS" to "$holdS",
    "resolved.holdShortS" to "$holdShortS",
)

private fun flatten(key: String, element: JsonElement): List<Pair<String, String>> = when (element) {
    // An empty map (audio.cueTexts by default) still gets its row, like a null.
    is JsonObject -> if (element.isEmpty()) {
        listOf(key to "")
    } else {
        element.flatMap { (k, v) -> flatten(if (key.isEmpty()) k else "$key.$k", v) }
    }
    is JsonArray -> listOf(key to element.joinToString(";") { it.jsonPrimitive.content })
    is JsonNull -> listOf(key to "")
    is JsonPrimitive -> listOf(
        key to when {
            element.isString -> element.content
            element.content == "true" -> "1"
            element.content == "false" -> "0"
            else -> element.content // numbers: Float/Double/Int/Long.toString(), as in the CSVs
        },
    )
}
