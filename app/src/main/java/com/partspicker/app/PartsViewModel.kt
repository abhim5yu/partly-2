package com.partspicker.app

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

data class UpdateInfo(val version: String, val url: String)

class PartsViewModel(app: Application) : AndroidViewModel(app) {
    private val storage = Storage(app)

    var parts by mutableStateOf<List<Part>>(emptyList()); private set
    var locations by mutableStateOf<List<LocationItem>>(emptyList()); private set
    var modelCodes by mutableStateOf<List<ModelCode>>(emptyList()); private set
    var sheetLinks by mutableStateOf<List<SheetLink>>(emptyList()); private set
    var ignoredDups by mutableStateOf<List<String>>(emptyList()); private set
    var themeMode by mutableStateOf(ThemeMode.AUTO); private set
    var navTransparency by mutableFloatStateOf(0.5f); private set

    var searchQuery by mutableStateOf("")
    var partNumberInput by mutableStateOf("")
    var locationInput by mutableStateOf("")
    var locationLocked by mutableStateOf(false)
    var formError by mutableStateOf("")
    var focusPartTick by mutableIntStateOf(0); private set

    var pendingDup by mutableStateOf<PendingDup?>(null); private set
    var pendingConflict by mutableStateOf<PendingConflict?>(null); private set
    var showDupView by mutableStateOf(false)
    var locSheetKey by mutableStateOf<String?>(null); private set
    var locSheetConfirming by mutableStateOf(false); private set
    var locArmedPartId by mutableStateOf<String?>(null); private set
    var editingPartId by mutableStateOf<String?>(null)
    var sheetError by mutableStateOf("")
    var backupStatus by mutableStateOf("")

    // --- Google Sheets (through the pasted Apps Script) ---
    var scriptUrl by mutableStateOf(""); private set
    var scriptToken by mutableStateOf(""); private set
    var sheetData by mutableStateOf<SheetData?>(null); private set
    var sheetDataFor by mutableStateOf<String?>(null); private set
    var sheetLoading by mutableStateOf(false); private set
    var addingToSheet by mutableStateOf(false); private set
    var sheetOpStatus by mutableStateOf(""); private set
    var sheetOpFailed by mutableStateOf(false); private set
    var lastAddedRow by mutableIntStateOf(-1); private set

    var updateInfo by mutableStateOf<UpdateInfo?>(null); private set
    private var updateCheckStarted = false

    init {
        parts = storage.loadParts() ?: emptyList()

        locations = storage.loadLocations() ?: run {
            val derived = parts.map { it.location }
                .filter { it.isNotEmpty() }
                .distinctBy { it.lowercase() }
                .map { LocationItem(PartsLogic.makeId(), it, "Other locations") }
            storage.saveLocations(derived)
            derived
        }
        modelCodes = storage.loadModelCodes()
        sheetLinks = storage.loadSheetLinks()
        ignoredDups = storage.loadIgnored()
        themeMode = storage.loadTheme()
        navTransparency = storage.loadNavTransparency()
        scriptUrl = storage.loadScriptUrl()
        scriptToken = storage.loadScriptToken()
    }

    fun setTheme(mode: ThemeMode) {
        themeMode = mode
        storage.saveTheme(mode)
    }

    /**
     * Live-updates the nav bar transparency while the slider is dragged (not saved yet).
     * @JvmName avoids a JVM clash with the property's own (private) setter.
     */
    @JvmName("applyNavTransparency")
    fun setNavTransparency(value: Float) {
        navTransparency = value.coerceIn(0f, 1f)
    }

    /** Saves the nav bar transparency once the slider is released. */
    fun saveNavTransparency() {
        storage.saveNavTransparency(navTransparency)
    }

    /** Asks the GitHub "latest release" API for the newest tag + page. Throws if it can't. */
    private fun fetchLatestRelease(): Pair<String, String> {
        val url = URL("https://api.github.com/repos/abhim5yu/partly-2/releases/latest")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val obj = JSONObject(body)
            val tag = obj.optString("tag_name", "").removePrefix("v").removePrefix("V")
            val htmlUrl = obj.optString("html_url", "https://github.com/abhim5yu/partly-2/releases")
            return tag to htmlUrl
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Checks the GitHub "latest release" API once per app session and, if its tag
     * differs from the currently installed version, surfaces an update banner.
     * Fails silently (no network, rate-limited, etc.) — this is a nice-to-have, not
     * something that should ever interrupt using the app.
     */
    fun checkForUpdate(currentVersion: String) {
        if (updateCheckStarted) return
        updateCheckStarted = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val (tag, htmlUrl) = fetchLatestRelease()
                if (tag.isNotEmpty() && tag != currentVersion) {
                    withContext(Dispatchers.Main) {
                        updateInfo = UpdateInfo(tag, htmlUrl)
                    }
                }
            } catch (_: Exception) {
                // No connectivity, rate-limited, or no releases yet — just skip it.
            }
        }
    }

    /**
     * Tapping the version number: checks every single time and reports the outcome via
     * [onResult] (shown as a toast). A newer version also raises the update banner.
     */
    fun checkForUpdateNow(currentVersion: String, onResult: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val message = try {
                val (tag, htmlUrl) = fetchLatestRelease()
                if (tag.isNotEmpty() && tag != currentVersion) {
                    withContext(Dispatchers.Main) { updateInfo = UpdateInfo(tag, htmlUrl) }
                    "Update available — v$tag"
                } else {
                    "You're up to date (v$currentVersion)"
                }
            } catch (_: Exception) {
                "Couldn't check for updates"
            }
            withContext(Dispatchers.Main) { onResult(message) }
        }
    }

    fun dismissUpdateBanner() { updateInfo = null }

    /** A sensible default name for the next sheet link: "Sheet1", "Sheet2", ... */
    fun suggestNextSheetName(): String {
        var n = sheetLinks.size + 1
        val used = sheetLinks.map { it.name.lowercase() }.toHashSet()
        while ("sheet$n".lowercase() in used) n++
        return "Sheet$n"
    }

    fun addSheetLink(name: String, url: String): Boolean {
        val n = name.trim().ifEmpty { suggestNextSheetName() }
        val u = url.trim()
        if (u.isEmpty()) {
            sheetError = "Paste a Google Sheets link."
            return false
        }
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            sheetError = "That doesn't look like a valid link."
            return false
        }
        sheetError = ""
        sheetLinks = sheetLinks + SheetLink(PartsLogic.makeId(), n, u)
        storage.saveSheetLinks(sheetLinks)
        return true
    }

    fun updateSheetLink(id: String, name: String, url: String) {
        val n = name.trim()
        val u = url.trim()
        if (n.isEmpty() || u.isEmpty()) return
        sheetLinks = sheetLinks.map { if (it.id == id) it.copy(name = n, url = u) else it }
        storage.saveSheetLinks(sheetLinks)
    }

    fun deleteSheetLink(id: String) {
        sheetLinks = sheetLinks.filter { it.id != id }
        storage.saveSheetLinks(sheetLinks)
    }

    /** Replaces the input boxes (name + column) of one sheet. */
    fun updateSheetFields(id: String, fields: List<SheetField>) {
        sheetLinks = sheetLinks.map { if (it.id == id) it.copy(fields = fields) else it }
        storage.saveSheetLinks(sheetLinks)
    }

    // ---------------- Google Sheets bridge ----------------

    fun saveScriptUrl(url: String) {
        scriptUrl = url.trim()
        storage.saveScriptUrl(scriptUrl)
    }

    /** Creates the private token the first time it's needed; the same one is baked into the copied script. */
    fun ensureScriptToken(): String {
        if (scriptToken.isBlank()) {
            scriptToken = UUID.randomUUID().toString().replace("-", "")
            storage.saveScriptToken(scriptToken)
        }
        return scriptToken
    }

    private fun reportSheetOp(text: String, failed: Boolean) {
        sheetOpStatus = text
        sheetOpFailed = failed
    }

    private fun friendlyError(e: Exception): String = when (e) {
        is java.net.UnknownHostException,
        is java.net.ConnectException,
        is java.net.SocketTimeoutException -> "Couldn't reach Google. Check your internet connection."
        else -> e.message?.takeIf { it.isNotBlank() } ?: "Something went wrong."
    }

    /** Calls the pasted Apps Script web app (plain GET) and returns its JSON answer. Blocking — call off the main thread. */
    private fun callScript(params: Map<String, String>): JSONObject {
        val base = scriptUrl.trim()
        if (base.isEmpty()) throw IllegalStateException("Connect Google first.")
        val query = params.entries.joinToString("&") {
            URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8")
        }
        val full = base + (if ('?' in base) "&" else "?") + query
        val conn = URL(full).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 15000
            conn.readTimeout = 60000
            conn.instanceFollowRedirects = true
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            return try {
                JSONObject(body)
            } catch (e: Exception) {
                throw IllegalStateException(
                    "The script didn't answer properly. Check it's deployed as a Web app with access set to Anyone."
                )
            }
        } finally {
            conn.disconnect()
        }
    }

    /** First time a sheet loads with no boxes yet: make one per header cell (up to 5). */
    private fun seedFieldsIfNeeded(sheetId: String, values: List<List<String>>) {
        val current = sheetLinks.firstOrNull { it.id == sheetId } ?: return
        if (current.fields.isNotEmpty()) return
        val header = values.firstOrNull() ?: return
        val defaults = header.withIndex()
            .filter { it.value.isNotBlank() }
            .take(5)
            .map { SheetField(it.value.trim(), it.index + 1) }
        if (defaults.isNotEmpty()) updateSheetFields(sheetId, defaults)
    }

    /** Fetches the latest contents of a sheet from Google. */
    fun loadSheet(sheet: SheetLink, keepStatus: Boolean = false) {
        if (sheetDataFor != sheet.id) {
            sheetData = null
            lastAddedRow = -1
        }
        if (!keepStatus) reportSheetOp("", false)
        if (scriptUrl.isBlank()) {
            reportSheetOp("Connect Google first (Sheets screen → Google connection).", true)
            return
        }
        val id = sheetIdOf(sheet.url)
        if (id == null) {
            reportSheetOp("That doesn't look like a Google Sheets link.", true)
            return
        }
        sheetLoading = true
        val gid = sheetGidOf(sheet.url) ?: ""
        val token = scriptToken
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val res = callScript(mapOf("token" to token, "action" to "read", "id" to id, "gid" to gid))
                if (!res.optBoolean("ok")) throw IllegalStateException(res.optString("error", "Unknown error"))
                val arr = res.getJSONArray("values")
                val values = List(arr.length()) { r ->
                    val row = arr.getJSONArray(r)
                    List(row.length()) { col -> row.optString(col, "") }
                }
                val data = SheetData(res.optString("title", ""), res.optString("tab", ""), values)
                withContext(Dispatchers.Main) {
                    sheetData = data
                    sheetDataFor = sheet.id
                    sheetLoading = false
                    seedFieldsIfNeeded(sheet.id, values)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    sheetLoading = false
                    reportSheetOp(friendlyError(e), true)
                }
            }
        }
    }

    /**
     * Writes one entry (a value per input box, in order) into the first empty row of the
     * sheet, then reloads the sheet. [onResult] tells the screen whether it worked.
     */
    fun addToSheet(sheet: SheetLink, values: List<String>, onResult: (Boolean) -> Unit) {
        if (scriptUrl.isBlank()) {
            reportSheetOp("Connect Google first (Sheets screen → Google connection).", true)
            onResult(false)
            return
        }
        val id = sheetIdOf(sheet.url)
        if (id == null) {
            reportSheetOp("That doesn't look like a Google Sheets link.", true)
            onResult(false)
            return
        }
        if (sheet.fields.isEmpty()) {
            reportSheetOp("Add at least one box first (pencil icon).", true)
            onResult(false)
            return
        }
        if (values.all { it.isBlank() }) {
            reportSheetOp("Fill in at least one box.", true)
            onResult(false)
            return
        }
        addingToSheet = true
        reportSheetOp("", false)
        val token = scriptToken
        val gid = sheetGidOf(sheet.url) ?: ""
        val cols = JSONArray(sheet.fields.map { it.col }).toString()
        val vals = JSONArray(values.map { it.trim() }).toString()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val res = callScript(
                    mapOf(
                        "token" to token, "action" to "add", "id" to id, "gid" to gid,
                        "cols" to cols, "vals" to vals
                    )
                )
                if (!res.optBoolean("ok")) throw IllegalStateException(res.optString("error", "Unknown error"))
                val row = res.optInt("row", -1)
                withContext(Dispatchers.Main) {
                    addingToSheet = false
                    lastAddedRow = row
                    reportSheetOp("Added to row $row.", false)
                    onResult(true)
                    loadSheet(sheet, keepStatus = true)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    addingToSheet = false
                    reportSheetOp(friendlyError(e), true)
                    onResult(false)
                }
            }
        }
    }

    // ---------------- Parts ----------------

    fun addPart() {
        val pn = partNumberInput.trim()
        val loc = locationInput.trim()
        if (pn.isEmpty() || loc.isEmpty()) {
            formError = "Enter both a part number and a location."
            return
        }
        formError = ""

        val elsewhere = parts
            .filter { it.partNumber.equals(pn, true) && !it.location.equals(loc, true) }
            .map { it.location }
            .distinctBy { it.lowercase() }
        // When the location is locked, don't interrupt with "this part is already
        // elsewhere, add it here too?" either — just add it straight away, same as
        // the existing-part-at-this-location prompt below.
        if (elsewhere.isNotEmpty() && !locationLocked) {
            pendingDup = PendingDup(pn, loc, elsewhere)
            return
        }
        continueAdd(pn, loc)
    }

    private fun continueAdd(pn: String, loc: String) {
        val existing = parts.firstOrNull { it.location.equals(loc, true) }
        // When the location is locked, the user is deliberately adding several parts
        // to the same location in a row — skip the "already has a part here" prompt
        // and just add it straight away instead of asking every time.
        if (existing != null && !locationLocked) {
            pendingConflict = PendingConflict(pn, loc, existing.partNumber)
            return
        }
        finalizeAdd(pn, loc)
    }

    private fun finalizeAdd(pn: String, loc: String) {
        parts = parts + Part(PartsLogic.makeId(), pn, loc)
        storage.saveParts(parts)
        resetFormAfterAdd(loc)
    }

    /**
     * Next-location suggestion after an add. If the just-used location is one of the
     * Saved locations, step to the next entry in that saved list (spreadsheet-style:
     * press Enter, move to the next saved row). If it isn't a saved location (or it's
     * the last one in the list), fall back to incrementing the location code itself.
     */
    private fun suggestNextLocation(current: String): String {
        val idx = locations.indexOfFirst { it.value.equals(current, ignoreCase = true) }
        if (idx != -1) {
            locations.getOrNull(idx + 1)?.let { return it.value }
        }
        return PartsLogic.nextLocationAfter(current)
    }

    /** Mirror of [suggestNextLocation], stepping back instead of forward. */
    private fun suggestPrevLocation(current: String): String {
        val idx = locations.indexOfFirst { it.value.equals(current, ignoreCase = true) }
        if (idx > 0) {
            locations.getOrNull(idx - 1)?.let { return it.value }
        }
        return PartsLogic.prevLocationBefore(current)
    }

    /** Manually step the Location field forward, via the arrow button — doesn't add a part. */
    fun stepLocationNext() {
        val cur = locationInput.trim()
        if (cur.isEmpty()) return
        locationInput = suggestNextLocation(cur)
    }

    /** Manually step the Location field back, via the arrow button — doesn't add a part. */
    fun stepLocationPrev() {
        val cur = locationInput.trim()
        if (cur.isEmpty()) return
        locationInput = suggestPrevLocation(cur)
    }

    private fun resetFormAfterAdd(loc: String) {
        partNumberInput = ""
        // When the location is locked, keep it as-is so multiple parts can be
        // added to the same location in a row without retyping it.
        locationInput = if (locationLocked) loc else suggestNextLocation(loc)
        focusPartTick++
    }

    fun cancelDupAdd() { pendingDup = null }

    fun confirmDupAdd() {
        val p = pendingDup ?: return
        pendingDup = null
        continueAdd(p.partNumber, p.location)
    }

    fun cancelConflict() { pendingConflict = null }

    fun conflictAddAnyway() {
        val p = pendingConflict ?: return
        pendingConflict = null
        finalizeAdd(p.partNumber, p.location)
    }

    fun conflictReplace() {
        val p = pendingConflict ?: return
        pendingConflict = null
        val existing = parts.firstOrNull { it.location.equals(p.location, true) }
        if (existing != null) {
            parts = parts.map { if (it.id == existing.id) it.copy(partNumber = p.partNumber) else it }
            storage.saveParts(parts)
        }
        resetFormAfterAdd(p.location)
    }

    fun openLocSheet(location: String) {
        locSheetKey = location.lowercase()
        locSheetConfirming = false
        locArmedPartId = null
        editingPartId = null
    }

    fun closeLocSheet() {
        locSheetKey = null
        locSheetConfirming = false
        locArmedPartId = null
        editingPartId = null
    }

    fun onLocDeleteTap() {
        if (!locSheetConfirming) {
            locSheetConfirming = true
            locArmedPartId = null
            return
        }
        val key = locSheetKey ?: return
        parts = parts.filter { it.location.lowercase() != key }
        storage.saveParts(parts)
        closeLocSheet()
    }

    fun onLocPartDeleteTap(id: String) {
        locSheetConfirming = false
        if (locArmedPartId != id) {
            locArmedPartId = id
            return
        }
        locArmedPartId = null
        parts = parts.filter { it.id != id }
        storage.saveParts(parts)
    }

    /** Toggles inline-edit mode for a part row inside the Location sheet. */
    fun startEditingPart(id: String) {
        locArmedPartId = null
        editingPartId = if (editingPartId == id) null else id
    }

    fun stopEditingPart() { editingPartId = null }

    /** Saves edits made to a part (part number, location, variant) from the Location sheet. */
    fun updatePart(id: String, partNumber: String, location: String, variant: String) {
        val pn = partNumber.trim()
        val loc = location.trim()
        if (pn.isEmpty() || loc.isEmpty()) return
        parts = parts.map { if (it.id == id) it.copy(partNumber = pn, location = loc, variant = variant.trim()) else it }
        storage.saveParts(parts)
        editingPartId = null
        // If the part moved to a different location, the sheet should now reflect that.
        locSheetKey = loc.lowercase()
    }

    fun ignoreDuplicate(key: String) {
        if (key !in ignoredDups) {
            ignoredDups = ignoredDups + key
            storage.saveIgnored(ignoredDups)
        }
    }

    fun restoreIgnored() {
        ignoredDups = emptyList()
        storage.saveIgnored(ignoredDups)
    }

    /**
     * Resolves a duplicate group: keeps [partNumber] only at [keepLocation] and removes
     * every other entry of that same part number from all its other locations.
     */
    fun resolveDuplicateToLocation(partNumber: String, keepLocation: String) {
        val pnLower = partNumber.lowercase()
        val keepLower = keepLocation.lowercase()
        parts = parts.filter { !(it.partNumber.lowercase() == pnLower && it.location.lowercase() != keepLower) }
        storage.saveParts(parts)
    }

    fun addModelCode(code: String, model: String): String? {
        val c = code.trim()
        val m = model.trim()
        if (c.isEmpty() || m.isEmpty()) return "Enter both a code and a model."
        if (modelCodes.any { it.code.equals(c, true) }) return "That code already exists — edit it instead."
        modelCodes = modelCodes + ModelCode(PartsLogic.makeId(), c, m)
        storage.saveModelCodes(modelCodes)
        return null
    }

    fun updateModelCode(id: String, code: String, model: String): Boolean {
        val c = code.trim()
        val m = model.trim()
        if (c.isEmpty() || m.isEmpty()) return false
        if (modelCodes.any { it.id != id && it.code.equals(c, true) }) return false
        modelCodes = modelCodes.map { if (it.id == id) it.copy(code = c, model = m) else it }
        storage.saveModelCodes(modelCodes)
        return true
    }

    fun deleteModelCode(id: String) {
        modelCodes = modelCodes.filter { it.id != id }
        storage.saveModelCodes(modelCodes)
    }

    fun addBulkModelCodes(rows: List<Pair<String, String>>): Pair<Int, Int> {
        var added = 0
        var updated = 0
        var list = modelCodes
        rows.forEach { (code, model) ->
            val existing = list.firstOrNull { it.code.equals(code, true) }
            if (existing != null) {
                list = list.map { if (it.id == existing.id) it.copy(model = model) else it }
                updated++
            } else {
                list = list + ModelCode(PartsLogic.makeId(), code, model)
                added++
            }
        }
        modelCodes = list
        storage.saveModelCodes(list)
        return added to updated
    }

    fun addLocations(codes: List<String>, group: String): Pair<Int, Int> {
        val existing = locations.map { it.value.lowercase() }.toHashSet()
        val fresh = ArrayList<LocationItem>()
        var skipped = 0
        codes.forEach { code ->
            if (!existing.add(code.lowercase())) {
                skipped++
            } else {
                fresh.add(LocationItem(PartsLogic.makeId(), code, group))
            }
        }
        locations = locations + fresh
        storage.saveLocations(locations)
        return fresh.size to skipped
    }

    fun deleteLocation(id: String) {
        locations = locations.filter { it.id != id }
        storage.saveLocations(locations)
    }

    fun exportBackup(uri: Uri) {
        try {
            getApplication<Application>().contentResolver.openOutputStream(uri)?.use { out ->
                out.write(storage.exportBundle().toByteArray())
            }
            backupStatus = "Backup saved."
        } catch (e: Exception) {
            backupStatus = "Export failed: ${e.message}"
        }
    }

    fun importBackup(uri: Uri) {
        try {
            val text = getApplication<Application>().contentResolver.openInputStream(uri)
                ?.bufferedReader()?.use { it.readText() } ?: return
            storage.importBundle(text)
            parts = storage.loadParts() ?: emptyList()
            locations = storage.loadLocations() ?: emptyList()
            modelCodes = storage.loadModelCodes()
            sheetLinks = storage.loadSheetLinks()
            ignoredDups = storage.loadIgnored()
            themeMode = storage.loadTheme()
            navTransparency = storage.loadNavTransparency()
            scriptUrl = storage.loadScriptUrl()
            scriptToken = storage.loadScriptToken()
            backupStatus = "Backup restored."
        } catch (e: Exception) {
            backupStatus = "Restore failed: ${e.message}"
        }
    }
}
