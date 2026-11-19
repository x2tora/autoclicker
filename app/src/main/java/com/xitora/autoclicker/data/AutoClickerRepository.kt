package com.xitora.autoclicker.data

import android.content.Context
import android.util.Base64
import com.xitora.autoclicker.domain.ClickEngineCalculator
import com.xitora.autoclicker.model.AutoClickerDataState
import com.xitora.autoclicker.model.ClickPreset
import com.xitora.autoclicker.model.PresetShareBundle
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

    fun exportSinglePresetJson(preset: ClickPreset): String {
        val imagesMap = linkedMapOf<String, String>()
        preset.scenarioSteps.forEach { step ->
            val paths = listOfNotNull(step.targetImagePath) + step.extraImagePaths
            paths.forEach { path ->
                if (!imagesMap.containsKey(path)) {
                    val file = File(path)
                    if (file.exists() && file.length() in 1L..2_000_000L) {
                        runCatching {
                            imagesMap[path] = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
                        }
                    }
                }
            }
        }
        return if (imagesMap.isEmpty()) {
            encodeSinglePresetJson(preset)
        } else {
            presetJsonCodec.encodeToString(
                PresetShareBundle.serializer(),
                PresetShareBundle(preset = preset, imagesBase64 = imagesMap)
            )
        }
    }

    fun importSinglePresetJson(rawJson: String): Result<ClickPreset> {
        return runCatching {
            val trimmed = rawJson.trim()
            val rootObj = presetJsonCodec.parseToJsonElement(trimmed).jsonObject
            if (rootObj.containsKey("preset") && rootObj.containsKey("imagesBase64")) {
                val bundle = presetJsonCodec.decodeFromJsonElement(PresetShareBundle.serializer(), rootObj)
                val dir = File(appContext.filesDir, "scenario_templates").apply { mkdirs() }
                val pathRemap = mutableMapOf<String, String>()
                bundle.imagesBase64.forEach { (origPath, b64) ->
                    runCatching {
                        val bytes = Base64.decode(b64, Base64.DEFAULT)
                        val outFile = File(dir, "tpl_imp_${UUID.randomUUID()}.png")
                        outFile.writeBytes(bytes)
                        pathRemap[origPath] = outFile.absolutePath
                    }
                }
                val remappedSteps = bundle.preset.scenarioSteps.map { step ->
                    step.copy(
                        targetImagePath = step.targetImagePath?.let { pathRemap[it] ?: it },
                        extraImagePaths = step.extraImagePaths.map { pathRemap[it] ?: it }
                    )
                }
                bundle.preset.copy(
                    id = UUID.randomUUID().toString(),
                    name = bundle.preset.name.trim().ifBlank { "가져온 프리셋" },
                    targets = ClickEngineCalculator.reindexTargets(bundle.preset.targets),
                    scenarioSteps = ClickEngineCalculator.reindexScenarioSteps(remappedSteps),
                    createdAtMs = System.currentTimeMillis()
                )
            } else {
                parseSinglePresetJson(trimmed).getOrThrow()
            }
        }
    }

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
