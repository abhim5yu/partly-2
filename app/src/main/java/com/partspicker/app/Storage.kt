package com.partspicker.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class Storage(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("parts_picker", Context.MODE_PRIVATE)

    private fun <T> readArray(key: String, parse: (JSONObject) -> T): List<T>? {
        val raw = prefs.getString(key, null) ?: return null
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { parse(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun writeArray(key: String, items: List<JSONObject>) {
        prefs.edit().putString(key, JSONArray(items).toString()).apply()
    }

    fun loadParts(): List<Part>? = readArray(KEY_PARTS) {
        Part(
            it.getString("id"),
            it.getString("partNumber"),
            it.getString("location"),
            it.optString("variant", "")
        )
    }

    fun saveParts(list: List<Part>) = writeArray(KEY_PARTS, list.map {
        JSONObject()
            .put("id", it.id)
            .put("partNumber", it.partNumber)
            .put("location", it.location)
            .put("variant", it.variant)
    })

    fun loadLocations(): List<LocationItem>? = readArray(KEY_LOCATIONS) {
        LocationItem(it.getString("id"), it.getString("value"), it.optString("group", "Other locations"))
    }

    fun saveLocations(list: List<LocationItem>) = writeArray(KEY_LOCATIONS, list.map {
        JSONObject().put("id", it.id).put("value", it.value).put("group", it.group)
    })

    fun loadModelCodes(): List<ModelCode> = readArray(KEY_MODELS) {
        ModelCode(it.getString("id"), it.getString("code"), it.getString("model"))
    } ?: emptyList()

    fun saveModelCodes(list: List<ModelCode>) = writeArray(KEY_MODELS, list.map {
        JSONObject().put("id", it.id).put("code", it.code).put("model", it.model)
    })

    fun loadIgnored(): List<String> {
        val raw = prefs.getString(KEY_IGNORED, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { arr.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveIgnored(list: List<String>) {
        prefs.edit().putString(KEY_IGNORED, JSONArray(list).toString()).apply()
    }

    fun loadTheme(): ThemeMode =
        ThemeMode.values().firstOrNull { it.name == prefs.getString(KEY_THEME, null) } ?: ThemeMode.AUTO

    fun saveTheme(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
    }

    /** Bundles every raw pref string as-is into one JSON file — survives reinstalls. */
    fun exportBundle(): String {
        val obj = JSONObject()
        listOf(KEY_PARTS, KEY_LOCATIONS, KEY_MODELS, KEY_IGNORED, KEY_THEME).forEach { key ->
            val raw = prefs.getString(key, null)
            if (raw != null) obj.put(key, raw)
        }
        return obj.toString(2)
    }

    fun importBundle(json: String) {
        val obj = JSONObject(json)
        val editor = prefs.edit()
        listOf(KEY_PARTS, KEY_LOCATIONS, KEY_MODELS, KEY_IGNORED, KEY_THEME).forEach { key ->
            if (obj.has(key) && !obj.isNull(key)) editor.putString(key, obj.getString(key))
        }
        editor.apply()
    }

    companion object {
        const val KEY_PARTS = "partsPickerData"
        const val KEY_LOCATIONS = "partsPickerLocations"
        const val KEY_MODELS = "partsPickerModelCodes"
        const val KEY_IGNORED = "partsPickerIgnoredDuplicates"
        const val KEY_THEME = "partsPickerTheme"
    }
}
