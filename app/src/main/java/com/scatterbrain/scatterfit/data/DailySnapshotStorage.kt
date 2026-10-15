package com.scatterbrain.scatterfit.data

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class DailySnapshotStorage(private val file: File) {
    constructor(context: Context) : this(File(context.filesDir, "daily-snapshot.json"))

    private val json = Json { ignoreUnknownKeys = true }

    fun load(): AssembledDaily? {
        if (!file.exists()) return null
        return try {
            val text = file.readText()
            json.decodeFromString<AssembledDaily>(text)
        } catch (_: Exception) {
            null
        }
    }

    fun save(daily: AssembledDaily) {
        try {
            val text = json.encodeToString(daily)
            val parent = file.parentFile ?: return
            val tmp = File(parent, "${file.name}.tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(file)) {
                file.writeText(text)
                tmp.delete()
            }
        } catch (_: Exception) {
            // best-effort persistence
        }
    }
}
