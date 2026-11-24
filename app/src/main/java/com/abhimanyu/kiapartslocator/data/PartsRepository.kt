package com.abhimanyu.kiapartslocator.data

import kotlinx.coroutines.flow.Flow
import java.util.UUID

class PartsRepository(private val db: AppDatabase) {

    fun observeParts(): Flow<List<Part>> = db.partDao().observeAll()
    fun observeLocations(): Flow<List<LocationEntity>> = db.locationDao().observeAll()
    fun observeModelCodes(): Flow<List<ModelCode>> = db.modelCodeDao().observeAll()

    suspend fun addPart(partNumber: String, location: String) {
        db.partDao().upsert(Part(id = makeId(), partNumber = partNumber, location = location))
    }

    suspend fun deletePart(id: String) = db.partDao().deleteById(id)

    suspend fun upsertLocation(value: String, group: String) {
        db.locationDao().upsert(LocationEntity(id = makeId(), value = value, group = group))
    }

    suspend fun deleteLocation(id: String) = db.locationDao().deleteById(id)

    suspend fun upsertModelCode(code: String, model: String) {
        db.modelCodeDao().upsert(ModelCode(id = makeId(), code = code, model = model))
    }

    suspend fun deleteModelCode(id: String) = db.modelCodeDao().deleteById(id)

    companion object {
        fun makeId(): String = UUID.randomUUID().toString()

        fun getModelForPartNumber(partNumber: String, codes: List<ModelCode>): String {
            if (partNumber.isEmpty()) return ""
            val pn = partNumber.uppercase()
            var best: ModelCode? = null
            for (m in codes) {
                val code = m.code.uppercase()
                if (code.isNotEmpty() && pn.contains(code)) {
                    if (best == null || code.length > best!!.code.length) best = m
                }
            }
            return best?.model ?: ""
        }

        fun nextLocationAfter(value: String): String {
            if (value.isEmpty()) return ""
            val withLetter = Regex("^(.*?)(\\d+)([A-Za-z])$").find(value)
            if (withLetter != null) {
                val (prefix, numStr, letterStr) = withLetter.destructured
                val letter = letterStr.uppercase()[0]
                return if (letter == 'Z') {
                    incrementTrailingNumber(prefix, numStr) + "A"
                } else {
                    prefix + numStr + (letter + 1)
                }
            }
            val numericOnly = Regex("^(.*?)(\\d+)$").find(value)
            if (numericOnly != null) {
                val (prefix, numStr) = numericOnly.destructured
                return incrementTrailingNumber(prefix, numStr)
            }
            return ""
        }

        private fun incrementTrailingNumber(prefix: String, numStr: String): String {
            val width = numStr.length
            var next = (numStr.toInt() + 1).toString()
            while (next.length < width) next = "0$next"
            return prefix + next
        }
    }
}
