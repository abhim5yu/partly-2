package com.partspicker.app

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Writes a real .xlsx file (no external library): a zip of a few XML parts. */
object ExcelExport {
    private const val NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"

    fun write(out: OutputStream, parts: List<Part>, modelCodes: List<ModelCode>) {
        val partRows = ArrayList<List<String>>()
        partRows.add(listOf("Part number", "Part name", "Variant", "Model", "Location", "Notes"))
        parts.forEach {
            partRows.add(
                listOf(it.partNumber, it.name, it.variant, PartsLogic.modelFor(it.partNumber, modelCodes), it.location, it.notes)
            )
        }
        val modelRows = ArrayList<List<String>>()
        modelRows.add(listOf("Code", "Model"))
        modelCodes.forEach { modelRows.add(listOf(it.code, it.model)) }

        ZipOutputStream(out).use { zip ->
            fun entry(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entry("[Content_Types].xml", contentTypes())
            entry("_rels/.rels", rootRels())
            entry("xl/workbook.xml", workbook())
            entry("xl/_rels/workbook.xml.rels", workbookRels())
            entry("xl/styles.xml", styles())
            entry("xl/worksheets/sheet1.xml", sheet(partRows, listOf(20, 24, 20, 16, 16, 40)))
            entry("xl/worksheets/sheet2.xml", sheet(modelRows, listOf(14, 28)))
        }
    }

    private fun contentTypes() = XML +
        "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
        "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
        "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
        "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
        "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
        "<Override PartName=\"/xl/worksheets/sheet2.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
        "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>" +
        "</Types>"

    private fun rootRels() = XML +
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
        "<Relationship Id=\"rId1\" Type=\"$REL_NS/officeDocument\" Target=\"xl/workbook.xml\"/>" +
        "</Relationships>"

    private fun workbook() = XML +
        "<workbook xmlns=\"$NS\" xmlns:r=\"$REL_NS\"><sheets>" +
        "<sheet name=\"Parts\" sheetId=\"1\" r:id=\"rId1\"/>" +
        "<sheet name=\"Model codes\" sheetId=\"2\" r:id=\"rId2\"/>" +
        "</sheets></workbook>"

    private fun workbookRels() = XML +
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
        "<Relationship Id=\"rId1\" Type=\"$REL_NS/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
        "<Relationship Id=\"rId2\" Type=\"$REL_NS/worksheet\" Target=\"worksheets/sheet2.xml\"/>" +
        "<Relationship Id=\"rId3\" Type=\"$REL_NS/styles\" Target=\"styles.xml\"/>" +
        "</Relationships>"

    // cellXfs: 0 = normal, 1 = bold header, 2 = wrapped text
    private fun styles() = XML +
        "<styleSheet xmlns=\"$NS\">" +
        "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font>" +
        "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>" +
        "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills>" +
        "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>" +
        "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
        "<cellXfs count=\"3\">" +
        "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
        "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>" +
        "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyAlignment=\"1\"><alignment vertical=\"top\" wrapText=\"1\"/></xf>" +
        "</cellXfs>" +
        "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>" +
        "</styleSheet>"

    private fun sheet(rows: List<List<String>>, widths: List<Int>): String {
        val sb = StringBuilder(XML)
        sb.append("<worksheet xmlns=\"$NS\">")
        sb.append("<sheetViews><sheetView workbookViewId=\"0\">")
        sb.append("<pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/>")
        sb.append("<selection pane=\"bottomLeft\"/></sheetView></sheetViews>")
        sb.append("<cols>")
        widths.forEachIndexed { i, w ->
            sb.append("<col min=\"${i + 1}\" max=\"${i + 1}\" width=\"$w\" customWidth=\"1\"/>")
        }
        sb.append("</cols><sheetData>")
        rows.forEachIndexed { r, cells ->
            sb.append("<row r=\"${r + 1}\">")
            cells.forEachIndexed { c, value ->
                if (value.isEmpty()) return@forEachIndexed
                val style = if (r == 0) 1 else 2
                sb.append("<c r=\"${columnName(c)}${r + 1}\" t=\"inlineStr\" s=\"$style\">")
                sb.append("<is><t xml:space=\"preserve\">${escape(value)}</t></is></c>")
            }
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private fun columnName(index: Int): String {
        var n = index
        val sb = StringBuilder()
        do {
            sb.insert(0, ('A'.code + n % 26).toChar())
            n = n / 26 - 1
        } while (n >= 0)
        return sb.toString()
    }

    private fun escape(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            when {
                ch == '&' -> sb.append("&amp;")
                ch == '<' -> sb.append("&lt;")
                ch == '>' -> sb.append("&gt;")
                ch == '"' -> sb.append("&quot;")
                ch.code < 0x20 && ch != '\n' && ch != '\r' && ch != '\t' -> Unit
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }
}
