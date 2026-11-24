package com.partspicker.app

import java.math.BigInteger
import java.util.UUID

data class RangeResult(val codes: List<String>? = null, val error: String? = null)

data class BulkResult(val rows: List<Pair<String, String>>, val skipped: Int)

object PartsLogic {
    const val MAX_RANGE_SIZE = 2000

    fun makeId(): String = UUID.randomUUID().toString().replace("-", "").take(14)

    private fun incrementTrailingNumber(prefix: String, numStr: String): String {
        val width = numStr.length
        val next = (BigInteger(numStr) + BigInteger.ONE).toString().padStart(width, '0')
        return prefix + next
    }

    fun nextLocationAfter(value: String): String {
        if (value.isEmpty()) return ""

        val withLetter = Regex("^(.*?)(\\d+)([A-Za-z])$").matchEntire(value)
        if (withLetter != null) {
            val prefix = withLetter.groupValues[1]
            val numStr = withLetter.groupValues[2]
            val letter = withLetter.groupValues[3].uppercase()[0]
            return if (letter == 'Z') {
                incrementTrailingNumber(prefix, numStr) + "A"
            } else {
                prefix + numStr + (letter.code + 1).toChar()
            }
        }

        val numericOnly = Regex("^(.*?)(\\d+)$").matchEntire(value)
        if (numericOnly != null) {
            return incrementTrailingNumber(numericOnly.groupValues[1], numericOnly.groupValues[2])
        }
        return ""
    }

    fun computeRangeCodes(start: String, end: String, letterFrom: String, letterTo: String): RangeResult {
        if (start.isEmpty() || end.isEmpty()) return RangeResult(error = "Enter both a start and an end code.")
        if (start.length != end.length) return RangeResult(error = "Start and end codes must be the same length.")

        var firstDiff = -1
        var lastDiff = -1
        for (i in start.indices) {
            if (start[i] != end[i]) {
                if (firstDiff == -1) firstDiff = i
                lastDiff = i
            }
        }
        if (firstDiff == -1) return RangeResult(error = "Start and end codes are identical.")

        val segStart = start.substring(firstDiff, lastDiff + 1)
        val segEnd = end.substring(firstDiff, lastDiff + 1)
        val digits = Regex("^\\d+$")
        if (!digits.matches(segStart) || !digits.matches(segEnd)) {
            return RangeResult(error = "The part that differs must be numbers, e.g. 01 to 14.")
        }

        val numStart = segStart.toLongOrNull()
        val numEnd = segEnd.toLongOrNull()
        if (numStart == null || numEnd == null) return RangeResult(error = "Those numbers are too large.")
        val width = segStart.length
        if (numEnd < numStart) return RangeResult(error = "The end code's number must be the same or higher than the start.")

        val prefix = start.substring(0, firstDiff)
        val suffix = start.substring(lastDiff + 1)

        val hasFrom = letterFrom.isNotEmpty()
        val hasTo = letterTo.isNotEmpty()
        if (hasFrom != hasTo) return RangeResult(error = "Enter both letter boxes, or leave both blank.")

        var letters: List<Char>? = null
        if (hasFrom && hasTo) {
            val single = Regex("^[A-Za-z]$")
            if (!single.matches(letterFrom) || !single.matches(letterTo)) {
                return RangeResult(error = "Letters must be a single A-Z character.")
            }
            val c1 = letterFrom.uppercase()[0]
            val c2 = letterTo.uppercase()[0]
            if (c2 < c1) return RangeResult(error = "The end letter must come at or after the start letter.")
            letters = (c1..c2).toList()
        }

        val numericCount = numEnd - numStart + 1
        val totalCount = if (letters != null) numericCount * letters.size else numericCount
        if (totalCount > MAX_RANGE_SIZE) {
            return RangeResult(error = "That range is $totalCount locations — max $MAX_RANGE_SIZE at a time. Narrow it down.")
        }

        val codes = ArrayList<String>()
        var n = numStart
        while (n <= numEnd) {
            val base = prefix + n.toString().padStart(width, '0') + suffix
            if (letters != null) {
                letters.forEach { codes.add(base + it) }
            } else {
                codes.add(base)
            }
            n++
        }
        return RangeResult(codes = codes)
    }

    fun parseBulkModelLines(text: String): BulkResult {
        val rx = Regex("^([A-Za-z0-9]+)[\\s,\\-:]+(.+)$")
        val rows = ArrayList<Pair<String, String>>()
        var skipped = 0
        text.split(Regex("\\r?\\n")).forEach { line ->
            val t = line.trim()
            if (t.isEmpty()) return@forEach
            val m = rx.find(t)
            if (m == null) {
                skipped++
                return@forEach
            }
            val code = m.groupValues[1].trim()
            val model = m.groupValues[2].trim()
            if (code.isEmpty() || model.isEmpty()) {
                skipped++
                return@forEach
            }
            rows.add(code to model)
        }
        return BulkResult(rows, skipped)
    }

    fun modelFor(partNumber: String, codes: List<ModelCode>): String {
        if (partNumber.isEmpty()) return ""
        val pn = partNumber.uppercase()
        var best: ModelCode? = null
        for (m in codes) {
            val code = m.code.uppercase()
            if (code.isNotEmpty() && pn.contains(code)) {
                if (best == null || code.length > best.code.length) best = m
            }
        }
        return best?.model ?: ""
    }

    fun findDuplicates(
        parts: List<Part>,
        ignored: List<String>,
        includeIgnored: Boolean = false
    ): List<DuplicateGroup> {
        return parts.groupBy { it.partNumber.lowercase() }
            .mapNotNull { (key, entries) ->
                val locs = entries.distinctBy { it.location.lowercase() }.map { it.location }
                if (locs.size > 1 && (includeIgnored || key !in ignored)) {
                    DuplicateGroup(entries.first().partNumber, locs)
                } else null
            }
    }
}
