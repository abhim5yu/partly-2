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

    /** Google Sheets used for stock taking — "Sheet1", "Sheet2", etc. — with their input boxes. */
    fun loadSheetLinks(): List<SheetLink> = readArray(KEY_SHEETS) { o ->
        val fa = o.optJSONArray("fields")
        val fields = if (fa == null) emptyList() else List(fa.length()) { i ->
            val f = fa.getJSONObject(i)
            SheetField(f.optString("label", ""), f.optInt("col", 1))
        }
        SheetLink(o.getString("id"), o.getString("name"), o.getString("url"), fields)
    } ?: emptyList()

    fun saveSheetLinks(list: List<SheetLink>) = writeArray(KEY_SHEETS, list.map { s ->
        JSONObject()
            .put("id", s.id)
            .put("name", s.name)
            .put("url", s.url)
            .put("fields", JSONArray(s.fields.map { f ->
                JSONObject().put("label", f.label).put("col", f.col)
            }))
    })

    /** The Google Apps Script web-app URL that lets the app read/write the sheets. */
    fun loadScriptUrl(): String = prefs.getString(KEY_SCRIPT_URL, "") ?: ""

    fun saveScriptUrl(value: String) {
        prefs.edit().putString(KEY_SCRIPT_URL, value).apply()
    }

    /** Private token shared between the app and the pasted script. */
    fun loadScriptToken(): String = prefs.getString(KEY_SCRIPT_TOKEN, "") ?: ""

    fun saveScriptToken(value: String) {
        prefs.edit().putString(KEY_SCRIPT_TOKEN, value).apply()
    }

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

    /** How see-through the floating nav bar is (0 = nearly solid, 1 = clearest). */
    fun loadNavTransparency(): Float =
        prefs.getString(KEY_NAV_GLASS, null)?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 0.5f

    fun saveNavTransparency(value: Float) {
        prefs.edit().putString(KEY_NAV_GLASS, value.toString()).apply()
    }

    private val bundleKeys = listOf(
        KEY_PARTS, KEY_LOCATIONS, KEY_MODELS, KEY_SHEETS, KEY_IGNORED, KEY_THEME,
        KEY_NAV_GLASS, KEY_SCRIPT_URL, KEY_SCRIPT_TOKEN
    )

    /** Bundles every raw pref string as-is into one JSON file — survives reinstalls. */
    fun exportBundle(): String {
        val obj = JSONObject()
        bundleKeys.forEach { key ->
            val raw = prefs.getString(key, null)
            if (raw != null) obj.put(key, raw)
        }
        return obj.toString(2)
    }

    fun importBundle(json: String) {
        val obj = JSONObject(json)
        val editor = prefs.edit()
        bundleKeys.forEach { key ->
            if (obj.has(key) && !obj.isNull(key)) editor.putString(key, obj.getString(key))
        }
        editor.apply()
    }

    companion object {
        const val KEY_PARTS = "partsPickerData"
        const val KEY_LOCATIONS = "partsPickerLocations"
        const val KEY_MODELS = "partsPickerModelCodes"
        const val KEY_SHEETS = "partsPickerSheetLinks"
        const val KEY_IGNORED = "partsPickerIgnoredDuplicates"
        const val KEY_THEME = "partsPickerTheme"
        const val KEY_NAV_GLASS = "partsPickerNavGlass"
        const val KEY_SCRIPT_URL = "partsPickerScriptUrl"
        const val KEY_SCRIPT_TOKEN = "partsPickerScriptToken"
    }
}
