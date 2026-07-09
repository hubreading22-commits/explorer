package com.collegefiles.app.config

import android.content.Context
import org.json.JSONObject

enum class Capability {
    VIEW_PDF,
    VIEW_IMAGE,
    VIEW_VIDEO,
    VIEW_AUDIO,
    VIEW_TEXT,
    VIEW_OFFICE,
    UPLOAD,
    DELETE,
    RENAME,
    CREATE_FOLDER
}

class ContentPolicy(context: Context) {
    private val capabilities = mutableSetOf<Capability>()

    init {
        try {
            val inputStream = context.assets.open("config.json")
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            val capsArray = jsonObject.optJSONArray("capabilities")
            
            if (capsArray != null) {
                for (i in 0 until capsArray.length()) {
                    val capStr = capsArray.getString(i)
                    try {
                        capabilities.add(Capability.valueOf(capStr))
                    } catch (e: IllegalArgumentException) {
                        // Ignore unknown capabilities from config
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback defaults if config.json is missing or corrupt
            capabilities.addAll(listOf(Capability.VIEW_PDF, Capability.VIEW_IMAGE, Capability.VIEW_TEXT))
        }
    }

    fun hasCapability(capability: Capability): Boolean {
        return capabilities.contains(capability)
    }
}
