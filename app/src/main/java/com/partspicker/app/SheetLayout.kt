package com.partspicker.app

/** The input boxes live on a grid this many cells wide... */
const val GRID_COLS = 5

/** ...and at least this many rows tall (it grows if there are more boxes than fit). */
const val GRID_ROWS = 7

fun SheetField.isPlaced(): Boolean = gx >= 0 && gw > 0

/** True if a w x h box at (x, y) overlaps none of the placed boxes (ignoring the one with [ignoreId]). */
fun isFree(list: List<SheetField>, ignoreId: String?, x: Int, y: Int, w: Int, h: Int): Boolean =
    list.none {
        it.id != ignoreId && it.isPlaced() &&
            x < it.gx + it.gw && x + w > it.gx &&
            y < it.gy + it.gh && y + h > it.gy
    }

/** First free spot (column, row) for a w x h box; falls back to a fresh row at the bottom. */
fun findSpot(placed: List<SheetField>, w: Int, h: Int): Pair<Int, Int> {
    val used = placed.maxOfOrNull { it.gy + it.gh } ?: 0
    for (y in 0..used) {
        for (x in 0..(GRID_COLS - w)) {
            if (isFree(placed, null, x, y, w, h)) return x to y
        }
    }
    return 0 to used
}

/**
 * Gives every box a spot on the grid. Boxes that already have one keep it (clamped to the
 * grid). If none are placed yet, the first two boxes get a full row each and the rest share
 * rows (up to 4 across). Boxes added later go into the first free spot.
 */
fun List<SheetField>.laidOut(): List<SheetField> {
    // keep existing placements valid
    val clamped = map {
        if (!it.isPlaced()) it else {
            val w = it.gw.coerceIn(1, GRID_COLS)
            it.copy(gw = w, gx = it.gx.coerceIn(0, GRID_COLS - w), gy = it.gy.coerceAtLeast(0), gh = it.gh.coerceAtLeast(1))
        }
    }
    if (clamped.all { it.isPlaced() }) return clamped

    if (clamped.none { it.isPlaced() }) {
        val out = ArrayList<SheetField>()
        var row = 0
        var i = 0
        val n = clamped.size
        while (i < n && i < 2) {
            out += clamped[i].copy(gx = 0, gy = row, gw = GRID_COLS, gh = 1)
            row++
            i++
        }
        while (i < n) {
            val k = minOf(4, n - i)
            val base = GRID_COLS / k
            val extra = GRID_COLS % k
            var x = 0
            for (j in 0 until k) {
                val w = base + if (j < extra) 1 else 0
                out += clamped[i + j].copy(gx = x, gy = row, gw = w, gh = 1)
                x += w
            }
            row++
            i += k
        }
        return out
    }

    val out = clamped.toMutableList()
    for (idx in out.indices) {
        if (out[idx].isPlaced()) continue
        val placed = out.filter { it.isPlaced() }
        val w = 2
        val (x, y) = findSpot(placed, w, 1)
        out[idx] = out[idx].copy(gx = x, gy = y, gw = w, gh = 1)
    }
    return out
}
