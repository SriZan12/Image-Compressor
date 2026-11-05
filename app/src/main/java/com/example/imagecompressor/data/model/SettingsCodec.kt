package com.example.imagecompressor.data.model

import org.json.JSONObject

/** Small, version-tolerant values for Room and SavedStateHandle; never stores image bytes. */
object SettingsCodec {
  fun encode(value: CompressionSettings): String = JSONObject().apply {
    put("quality", value.quality); put("target", value.targetBytes ?: JSONObject.NULL)
    put("resizeMode", value.resizeMode.name); put("percent", value.resizePercent)
    put("width", value.customWidth); put("height", value.customHeight)
    put("aspect", value.preserveAspectRatio); put("format", value.outputFormat.name)
    put("targetResize", value.allowTargetResize)
  }.toString()

  fun decode(json: String?): CompressionSettings = try {
    if (json == null) CompressionSettings() else JSONObject(json).let {
      CompressionSettings(quality = it.getInt("quality"), targetBytes = if (it.isNull("target")) null else it.getLong("target"),
        resizeMode = ResizeMode.valueOf(it.getString("resizeMode")), resizePercent = it.getInt("percent"),
        customWidth = it.getString("width"), customHeight = it.getString("height"),
        preserveAspectRatio = it.getBoolean("aspect"), outputFormat = OutputFormat.valueOf(it.getString("format")),
        allowTargetResize = it.optBoolean("targetResize", true))
    }
  } catch (_: Exception) { CompressionSettings() }
}
