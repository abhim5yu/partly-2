package com.partspicker.app

data class Part(val id: String, val partNumber: String, val location: String)

data class LocationItem(val id: String, val value: String, val group: String)

data class ModelCode(val id: String, val code: String, val model: String)

data class DuplicateGroup(val partNumber: String, val locations: List<String>)

data class PendingDup(val partNumber: String, val location: String, val elsewhere: List<String>)

data class PendingConflict(val partNumber: String, val location: String, val existingPartNumber: String)

enum class ThemeMode { AUTO, LIGHT, DARK }
