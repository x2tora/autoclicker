package com.xitora.autoclicker.data

import android.content.Context
import com.xitora.autoclicker.domain.ClickEngineCalculator
import com.xitora.autoclicker.model.AutoClickerDataState
import com.xitora.autoclicker.model.ClickPreset
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.util.UUID

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

    fun exportSinglePresetJson(preset: ClickPreset): String = encodeSinglePresetJson(preset)

    fun importSinglePresetJson(rawJson: String): Result<ClickPreset> = parseSinglePresetJson(rawJson)

    companion object {
        private val presetJsonCodec = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
            coerceInputValues = true
        }

        fun encodeSinglePresetJson(preset: ClickPreset): String {
            return presetJsonCodec.encodeToString(ClickPreset.serializer(), preset)
        }

        fun parseSinglePresetJson(rawJson: String): Result<ClickPreset> {
            return runCatching {
                val trimmed = rawJson.trim()
                require(trimmed.startsWith("{")) { "올바른 프리셋 JSON 형식이 아닙니다." }
                val rootObj = presetJsonCodec.parseToJsonElement(trimmed).jsonObject
                val presetObj = (rootObj["preset"] as? JsonObject) ?: run {
                    require(rootObj.containsKey("mode") && rootObj.containsKey("timing")) {
                        "개별 프리셋 JSON이 아닙니다. 전체 백업 파일은 설정 > 데이터 백업에서 복원하세요."
                    }
                    rootObj
                }
                val decoded = presetJsonCodec.decodeFromJsonElement(ClickPreset.serializer(), presetObj)
                decoded.copy(
                    id = UUID.randomUUID().toString(),
                    name = decoded.name.trim().ifBlank { "가져온 프리셋" },
                    targets = ClickEngineCalculator.reindexTargets(decoded.targets),
                    scenarioSteps = ClickEngineCalculator.reindexScenarioSteps(decoded.scenarioSteps),
                    createdAtMs = System.currentTimeMillis()
                )
            }
        }
    }
}
