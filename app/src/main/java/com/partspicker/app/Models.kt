package com.partspicker.app

data class Part(val id: String, val partNumber: String, val location: String, val variant: String = "")

data class LocationItem(val id: String, val value: String, val group: String)

data class ModelCode(val id: String, val code: String, val model: String)

/** A link to an external Google Sheet shown in the Sheets section (Sheet1, Sheet2, etc.). */
data class SheetLink(val id: String, val name: String, val url: String)

data class DuplicateGroup(val partNumber: String, val locations: List<String>)

data class PendingDup(val partNumber: String, val location: String, val elsewhere: List<String>)

data class PendingConflict(val partNumber: String, val location: String, val existingPartNumber: String)

enum class ThemeMode { AUTO, LIGHT, DARK }
