package com.partspicker.app

data class Part(val id: String, val partNumber: String, val location: String, val variant: String = "")

data class LocationItem(val id: String, val value: String, val group: String)

data class ModelCode(val id: String, val code: String, val model: String)

/**
 * One input box for a sheet: its display name, which sheet column (1 = A, 2 = B, ...) it
 * fills, and a stable id used only to track it through the drag-to-reorder grid (never
 * shown anywhere in the UI).
 */
data class SheetField(val label: String, val col: Int, val id: String = PartsLogic.makeId())

/** A Google Sheet shown in the Sheets section (Sheet1, Sheet2, etc.) plus its input boxes. */
data class SheetLink(
    val id: String,
    val name: String,
    val url: String,
    val fields: List<SheetField> = emptyList()
)

/** The latest contents of a Google Sheet as plain text cells (row 0 = header row). */
data class SheetData(val title: String, val tab: String, val values: List<List<String>>)

data class DuplicateGroup(val partNumber: String, val locations: List<String>)

data class PendingDup(val partNumber: String, val location: String, val elsewhere: List<String>)

data class PendingConflict(val partNumber: String, val location: String, val existingPartNumber: String)

enum class ThemeMode { AUTO, LIGHT, DARK }
