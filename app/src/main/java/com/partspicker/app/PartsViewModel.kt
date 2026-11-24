package com.partspicker.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel

private val DEMO_PARTS = listOf(
    Part("demo1", "A1023", "Aisle 1 - Bin 3"),
    Part("demo2", "B2047", "Aisle 2 - Bin 1"),
    Part("demo3", "C5590", "Shelf B - Row 2"),
    Part("demo4", "D0012", "Drawer 4"),
    Part("demo5", "E7781", "Aisle 1 - Bin 7")
)

private val DEMO_LOCATIONS = listOf(
    "Aisle 1 - Bin 3", "Aisle 2 - Bin 1", "Shelf B - Row 2", "Drawer 4", "Aisle 1 - Bin 7"
)

class PartsViewModel(app: Application) : AndroidViewModel(app) {
    private val storage = Storage(app)

    var parts by mutableStateOf<List<Part>>(emptyList()); private set
    var locations by mutableStateOf<List<LocationItem>>(emptyList()); private set
    var modelCodes by mutableStateOf<List<ModelCode>>(emptyList()); private set
    var ignoredDups by mutableStateOf<List<String>>(emptyList()); private set
    var themeMode by mutableStateOf(ThemeMode.AUTO); private set

    var searchQuery by mutableStateOf("")
    var partNumberInput by mutableStateOf("")
    var locationInput by mutableStateOf("")
    var formError by mutableStateOf("")
    var focusPartTick by mutableIntStateOf(0); private set

    var pendingDup by mutableStateOf<PendingDup?>(null); private set
    var pendingConflict by mutableStateOf<PendingConflict?>(null); private set
    var showDupView by mutableStateOf(false)
    var locSheetKey by mutableStateOf<String?>(null); private set
    var locSheetConfirming by mutableStateOf(false); private set
    var locArmedPartId by mutableStateOf<String?>(null); private set

    init {
        val storedParts = storage.loadParts()
        val fresh = storedParts == null
        parts = storedParts ?: DEMO_PARTS.also { storage.saveParts(it) }

        val storedLocs = storage.loadLocations()
        locations = storedLocs ?: run {
            val derived = if (fresh) {
                DEMO_LOCATIONS.map { LocationItem(PartsLogic.makeId(), it, "Demo locations") }
            } else {
                parts.map { it.location }
                    .filter { it.isNotEmpty() }
                    .distinctBy { it.lowercase() }
                    .map { LocationItem(PartsLogic.makeId(), it, "Other locations") }
            }
            storage.saveLocations(derived)
            derived
        }
        modelCodes = storage.loadModelCodes()
        ignoredDups = storage.loadIgnored()
        themeMode = storage.loadTheme()
    }

    fun setTheme(mode: ThemeMode) {
        themeMode = mode
        storage.saveTheme(mode)
    }

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
        if (elsewhere.isNotEmpty()) {
            pendingDup = PendingDup(pn, loc, elsewhere)
            return
        }
        continueAdd(pn, loc)
    }

    private fun continueAdd(pn: String, loc: String) {
        val existing = parts.firstOrNull { it.location.equals(loc, true) }
        if (existing != null) {
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

    private fun resetFormAfterAdd(loc: String) {
        partNumberInput = ""
        locationInput = PartsLogic.nextLocationAfter(loc)
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
    }

    fun closeLocSheet() {
        locSheetKey = null
        locSheetConfirming = false
        locArmedPartId = null
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
}
