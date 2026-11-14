package com.xitora.autoclicker.data

import android.content.Context
import com.xitora.autoclicker.model.AutoClickerDataState
import kotlinx.serialization.json.Json
import java.io.File

class AutoClickerRepository(context: Context) {
    private val appContext = context.applicationContext
    private val stateFile = File(appContext.filesDir, "autoclicker_state.json")

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    fun loadState(): AutoClickerDataState {
        return runCatching {
            if (!stateFile.exists()) {
                AutoClickerDataState()
            } else {
                val raw = stateFile.readText(Charsets.UTF_8)
                if (raw.isBlank()) AutoClickerDataState()
                else json.decodeFromString(AutoClickerDataState.serializer(), raw)
            }
        }.getOrDefault(AutoClickerDataState())
    }

    fun saveState(state: AutoClickerDataState) {
        runCatching {
            val tmp = File(appContext.filesDir, "autoclicker_state.json.tmp")
            val encoded = json.encodeToString(AutoClickerDataState.serializer(), state)
            tmp.writeText(encoded, Charsets.UTF_8)
            if (stateFile.exists()) stateFile.delete()
            tmp.renameTo(stateFile)
        }
    }

    fun exportJson(state: AutoClickerDataState): String {
        return json.encodeToString(AutoClickerDataState.serializer(), state)
    }

    fun importJson(rawJson: String): Result<AutoClickerDataState> {
        return runCatching {
            val trimmed = rawJson.trim()
            require(trimmed.startsWith("{")) { "올바른 JSON 객체가 아닙니다." }
            json.decodeFromString(AutoClickerDataState.serializer(), trimmed)
        }
    }
}
