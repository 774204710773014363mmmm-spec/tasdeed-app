package com.shawafi.tasdeed.ui.screens

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.shawafi.tasdeed.data.PaymentRecord
import com.shawafi.tasdeed.ui.AppViewModel
import java.io.OutputStream
import java.text.NumberFormat
import java.util.Locale

object ReportExporter {

    private val numFmt: NumberFormat get() = NumberFormat.getNumberInstance(Locale.US)

    private data class RowData(
        val name: String,
        val meter: String,
        val latestDate: String,
        val note: String,
        val total: Double
    )

    private fun buildRows(list: List<PaymentRecord>, sort: StatementSort, isMy: Boolean, merge: Boolean): List<RowData> {
        if (isMy || !merge) {
            val rows = list.map {
                RowData(
                    if (isMy) it.note.ifEmpty { "دفعة" } else it.subscriberName,
                    it.meterNumber,
                    it.paymentDate,
                    it.note,
                    it.amount
                )
            }
            return when (sort) {
                StatementSort.NAME -> rows.sortedBy { it.name }
                StatementSort.METER -> rows.sortedBy { it.meter.toLongOrNull() ?: Long.MAX_VALUE }
                StatementSort.DATE -> rows.sortedByDescending { it.latestDate }
            }
        }
        val g = groupPayments(list)
        val rows = g.map {
            RowData(it.name, it.meter, it.latestDate, it.notes.joinToString(" | "), it.total)
        }
        return when (sort) {
            StatementSort.NAME -> rows.sortedBy { it.name }
            StatementSort.METER -> rows.sortedBy { it.meter.toLongOrNull() ?: Long.MAX_VALUE }
            StatementSort.DATE -> rows.sortedByDescending { it.latestDate }
        }
    }

    fun exportPdf(
        out: OutputStream,
        vm: AppViewModel,
        periodName: String,
        list: List<PaymentRecord>,
        sort: StatementSort = StatementSort.DATE,
        isMy: Boolean = false,
        merge: Boolean = true
    ) {
        val rows = buildRows(list, sort, isMy, merge)
        val today = vm.repo.currentDate()
        val brName = vm.branchName.value.ifEmpty { vm.branchKey.value ?: "" }
        val user = vm.user.value ?: ""
        val total = rows.sumOf { it.total }

        val doc = PdfDocument()
        val pageW = 842f
        val pageH = 1123f
        val titlePaint = Paint().apply { color = 0xFF0284C7.toInt(); textSize = 30f; isFakeBoldText = true; textAlign = Paint.Align.CENTER }
        val subPaint = Paint().apply { color = 0xFF333333.toInt(); textSize = 17f; textAlign = Paint.Align.CENTER }
        val smallPaint = Paint().apply { color = 0xFF666666.toInt(); textSize = 14f; textAlign = Paint.Align.CENTER }
        val totalPaint = Paint().apply { color = 0xFFD4A843.toInt(); textSize = 30f; isFakeBoldText = true; textAlign = Paint.Align.CENTER }
        val headPaint = Paint().apply { color = 0xFFFFFFFF.toInt(); textSize = 15f; isFakeBoldText = true; textAlign = Paint.Align.CENTER }
        val headBg = Paint().apply { color = 0xFF0284C7.toInt() }
        val cellPaint = Paint().apply { color = 0xFF111111.toInt(); textSize = 14f; textAlign = Paint.Align.RIGHT }
        val cellNumPaint = Paint().apply { color = 0xFF059669.toInt(); textSize = 15f; isFakeBoldText = true; textAlign = Paint.Align.RIGHT }
        val seqPaint = Paint().apply { color = 0xFF475569.toInt(); textSize = 14f; textAlign = Paint.Align.CENTER }
        val altBg = Paint().apply { color = 0xFFF1F5F9.toInt() }

        // أعمدة من اليمين لليسار: م | اسم المشترك | رقم العداد | آخر تاريخ | ملاحظة | الإجمالي
        data class Col(val title: String, val w: Float)
        val cols = listOf(
            Col("م", 50f), Col("اسم المشترك", 220f), Col("رقم العداد", 110f),
            Col("آخر تاريخ", 100f), Col("ملاحظة", 130f), Col("الإجمالي", 112f)
        )
        val rightEdge = pageW - 60f
        val colRights = FloatArray(cols.size)
        var acc = rightEdge
        cols.forEachIndexed { i, c -> colRights[i] = acc; acc -= c.w }
        val leftEdge = acc

        val lineH = 20f
        fun linesFor(text: String, paint: Paint, maxW: Float): Int {
            if (text.isEmpty()) return 1
            var n = 1
            var remain = text
            while (remain.isNotEmpty()) {
                val m = FloatArray(1)
                val c = paint.breakText(remain, true, maxW, m)
                if (c <= 0) break
                remain = remain.substring(c)
                if (remain.isNotEmpty()) n++
            }
            return n
        }
        fun rowHeight(s: RowData): Float {
            val nameL = linesFor(s.name, cellPaint, cols[1].w - 20f)
            val noteL = linesFor(s.note, cellPaint, cols[4].w - 20f)
            return (maxOf(nameL, noteL) * lineH + 14f).coerceAtLeast(36f)
        }

        val headerTop = 200f
        val headerH = 36f
        val top = headerTop + headerH
        val bottom = pageH - 50f

        // توزيع الصفوف على صفحات: كل صفحة تمتلئ بالصفوف (≈26 صفاً) ثم صفحة جديدة
        val pages = mutableListOf<MutableList<RowData>>()
        var cur = mutableListOf<RowData>()
        var yy = top
        for (r in rows) {
            val h = rowHeight(r)
            if (yy + h > bottom && cur.isNotEmpty()) {
                pages.add(cur)
                cur = mutableListOf()
                yy = top
            }
            cur.add(r)
            yy += h
        }
        if (cur.isNotEmpty()) pages.add(cur)
        if (pages.isEmpty()) pages.add(mutableListOf())

        var globalIdx = 0
        pages.forEachIndexed { pi, pageRows ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(842, 1123, pi + 1).create())
            val canvas = page.canvas

            canvas.drawRect(0f, 0f, pageW, 6f, Paint().apply { color = 0xFF0284C7.toInt() })
            if (pi == 0) {
                canvas.drawText("💰 تقرير المدفوعات - $user", pageW / 2f, 55f, titlePaint)
                canvas.drawText("$brName | $periodName", pageW / 2f, 88f, subPaint)
                canvas.drawText("التاريخ: $today | عدد الدفعات: ${rows.size}", pageW / 2f, 114f, smallPaint)
                canvas.drawText("الإجمالي الكلي: ${numFmt.format(total)} د.ع", pageW / 2f, 148f, totalPaint)
                canvas.drawRect(0f, 165f, pageW, 169f, Paint().apply { color = 0xFF0284C7.toInt() })
            } else {
                canvas.drawText("💰 تقرير المدفوعات - $user (تابع)", pageW / 2f, 55f, subPaint)
            }

            // رأس الجدول
            canvas.drawRect(leftEdge, headerTop, rightEdge, headerTop + headerH, headBg)
            cols.forEachIndexed { i, c ->
                val cx = (colRights[i] + (colRights[i] - c.w)) / 2f
                canvas.drawText(c.title, cx, headerTop + 25f, headPaint)
            }

            var rowY = top
            pageRows.forEachIndexed { ri, s ->
                val h = rowHeight(s)
                val rowTop = rowY
                if (ri % 2 == 1) canvas.drawRect(leftEdge, rowTop, rightEdge, rowTop + h, altBg)
                canvas.drawText((globalIdx + 1).toString(), (colRights[0] + colRights[0] - cols[0].w) / 2f, rowTop + 24f, seqPaint)
                drawWrapped(canvas, cellPaint, s.name.ifEmpty { "-" }, colRights[1] - 10f, rowTop + 24f, cols[1].w - 20f, lineH)
                drawWrapped(canvas, cellPaint, s.meter.ifEmpty { "-" }, colRights[2] - 10f, rowTop + 24f, cols[2].w - 20f, lineH)
                drawWrapped(canvas, cellPaint, s.latestDate.ifEmpty { "-" }, colRights[3] - 10f, rowTop + 24f, cols[3].w - 20f, lineH)
                drawWrapped(canvas, cellPaint, s.note.ifEmpty { "-" }, colRights[4] - 10f, rowTop + 24f, cols[4].w - 20f, lineH)
                canvas.drawText(numFmt.format(s.total), colRights[5] - 10f, rowTop + 24f, cellNumPaint)
                globalIdx++
                rowY += h
            }

            canvas.drawText("صفحة ${pi + 1} / ${pages.size}", pageW / 2f, pageH - 30f, smallPaint)
            doc.finishPage(page)
        }

        doc.writeTo(out)
        doc.close()
    }

    // يرسم النص ملتفاً بعدة أسطر على يمين الصفحة ويرجع إحداثي السطر التالي
    private fun drawWrapped(
        canvas: android.graphics.Canvas,
        paint: Paint,
        text: String,
        rightX: Float,
        startY: Float,
        maxWidth: Float,
        lineHeight: Float
    ): Float {
        if (text.isEmpty()) return startY + lineHeight
        var y = startY
        var remain = text
        while (remain.isNotEmpty()) {
            val measured = FloatArray(1)
            val n = paint.breakText(remain, true, maxWidth, measured)
            if (n <= 0) break
            canvas.drawText(remain.substring(0, n), rightX, y, paint)
            y += (lineHeight + 6f)
            remain = remain.substring(n)
        }
        return y
    }

    fun exportExcel(
        out: OutputStream,
        vm: AppViewModel,
        periodName: String,
        list: List<PaymentRecord>,
        sort: StatementSort = StatementSort.DATE,
        isMy: Boolean = false,
        merge: Boolean = true
    ) {
        val rows = buildRows(list, sort, isMy, merge)
        val today = vm.repo.currentDate()
        val brName = vm.branchName.value.ifEmpty { vm.branchKey.value ?: "" }
        val user = vm.user.value ?: ""
        val total = rows.sumOf { it.total }

        // ── بناء OOXML worksheet حقيقي (inline strings) ──
        fun cellStr(ref: String, text: String, style: Int = 0): String =
            "<c r=\"$ref\" s=\"$style\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${escXml(text)}</t></is></c>"
        fun cellNum(ref: String, v: Double, style: Int = 0): String {
            val nv = if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
            return "<c r=\"$ref\" s=\"$style\"><v>$nv</v></c>"
        }
        fun cellNumI(ref: String, v: Int, style: Int = 0): String =
            "<c r=\"$ref\" s=\"$style\"><v>$v</v></c>"

        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">\n")
        sb.append("<sheetViews><sheetView rightToLeft=\"1\" workbookViewId=\"0\"/></sheetViews>\n")
        sb.append("<cols>")
        val widths = doubleArrayOf(8.0, 30.0, 16.0, 18.0, 26.0, 18.0)
        for (ci in widths.indices) sb.append("<col min=\"${ci + 1}\" max=\"${ci + 1}\" width=\"${widths[ci]}\" customWidth=\"1\"/>")
        sb.append("</cols>\n")
        sb.append("<sheetData>\n")
        // صف العنوان
        sb.append("<row r=\"1\" ht=\"30\" customHeight=\"1\">")
        sb.append(cellStr("A1", "💰 تقرير المدفوعات", 1))
        sb.append("</row>\n")
        sb.append("<row r=\"2\" ht=\"20\" customHeight=\"1\">")
        sb.append(cellStr("A2", "$brName | $user", 2))
        sb.append("</row>\n")
        sb.append("<row r=\"3\" ht=\"16\" customHeight=\"1\">")
        sb.append(cellStr("A3", "$today | عدد الدفعات: ${rows.size}", 2))
        sb.append("</row>\n")
        // صف العناوين
        val heads = arrayOf("#", "اسم المشترك", "رقم العداد", "آخر تاريخ", "ملاحظة", "الإجمالي")
        sb.append("<row r=\"4\" ht=\"24\" customHeight=\"1\">")
        for ((ci, h) in heads.withIndex()) sb.append(cellStr(colRef(ci, 4), h, 3))
        sb.append("</row>\n")
        // البيانات
        rows.forEachIndexed { i, s ->
            val r = i + 5
            sb.append("<row r=\"$r\">")
            sb.append(cellNumI(colRef(0, r), i + 1))
            sb.append(cellStr(colRef(1, r), s.name.ifEmpty { "-" }))
            sb.append(cellStr(colRef(2, r), s.meter.ifEmpty { "-" }))
            sb.append(cellStr(colRef(3, r), s.latestDate))
            sb.append(cellStr(colRef(4, r), s.note.ifEmpty { "-" }))
            sb.append(cellNum(colRef(5, r), s.total))
            sb.append("</row>\n")
        }
        // الإجمالي
        val tr = rows.size + 5
        sb.append("<row r=\"$tr\" ht=\"26\" customHeight=\"1\">")
        sb.append(cellStr(colRef(0, tr), "الإجمالي: ${numFmt.format(total)} د.ع", 4))
        sb.append("</row>\n")
        sb.append("</sheetData>\n")
        sb.append("</worksheet>")

        writeXlsx(out, sb.toString(), "المدفوعات")
    }

    private fun colRef(col: Int, row: Int): String {
        var c = col
        val sbx = StringBuilder()
        c += 1
        while (c > 0) {
            sbx.insert(0, ('A' + (c - 1) % 26))
            c = (c - 1) / 26
        }
        return "$sbx$row"
    }

    private fun writeXlsx(out: OutputStream, sheetXml: String, sheetName: String) {
        val contentTypes = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
            "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
            "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
            "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
            "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
            "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>" +
            "</Types>"
        val rels = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
            "</Relationships>"
        val workbook = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" " +
            "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
            "<sheets><sheet name=\"${escXml(sheetName)}\" sheetId=\"1\" r:id=\"rId1\"/></sheets>" +
            "</workbook>"
        val xlRels = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
            "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>" +
            "</Relationships>"
        val styles = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
            "<fonts count=\"4\">" +
            "<font><sz val=\"11\"/><name val=\"Arial\"/></font>" +
            "<font><b/><sz val=\"16\"/><color rgb=\"FF059669\"/><name val=\"Arial\"/></font>" +
            "<font><sz val=\"10\"/><color rgb=\"FF667085\"/><name val=\"Arial\"/></font>" +
            "<font><b/><sz val=\"11\"/><color rgb=\"FFFFFFFF\"/><name val=\"Arial\"/></font>" +
            "</fonts>" +
            "<fills count=\"3\">" +
            "<fill><patternFill patternType=\"none\"/></fill>" +
            "<fill><patternFill patternType=\"gray125\"/></fill>" +
            "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF059669\"/><bgColor indexed=\"64\"/></patternFill></fill>" +
            "</fills>" +
            "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>" +
            "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
            "<cellXfs count=\"5\">" +
            "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
            "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\" readingOrder=\"2\"/></xf>" +
            "<xf numFmtId=\"0\" fontId=\"2\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\" readingOrder=\"2\"/></xf>" +
            "<xf numFmtId=\"0\" fontId=\"3\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\" readingOrder=\"2\"/></xf>" +
            "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyAlignment=\"1\"><alignment horizontal=\"right\" vertical=\"center\" readingOrder=\"2\"/></xf>" +
            "</cellXfs>" +
            "</styleSheet>"
        val zos = java.util.zip.ZipOutputStream(out)
        fun put(name: String, content: String) {
            zos.putNextEntry(java.util.zip.ZipEntry(name))
            zos.write(content.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        put("[Content_Types].xml", contentTypes)
        put("_rels/.rels", rels)
        put("xl/workbook.xml", workbook)
        put("xl/_rels/workbook.xml.rels", xlRels)
        put("xl/styles.xml", styles)
        put("xl/worksheets/sheet1.xml", sheetXml)
        zos.finish()
        zos.flush()
    }

    data class SubGroup(
        val key: String,
        var name: String,
        var meter: String,
        var num: String,
        var total: Double,
        var latestDate: String,
        val notes: MutableList<String> = mutableListOf(),
        val ids: MutableList<String> = mutableListOf()
    )

    fun groupPayments(list: List<PaymentRecord>): List<SubGroup> {
        val map = LinkedHashMap<String, SubGroup>()
        for (p in list) {
            val key = p.subscriberId.ifEmpty { p.meterNumber.ifEmpty { p.subscriberName.ifEmpty { "غير معروف" } } }
            val g = map.getOrPut(key) { SubGroup(key, p.subscriberName, p.meterNumber, p.subscriberNumber, 0.0, "") }
            g.total += p.amount
            if (p.paymentDate > g.latestDate) g.latestDate = p.paymentDate
            if (p.note.isNotBlank() && !g.notes.contains(p.note)) g.notes.add(p.note)
            g.ids.add(p.localId)
        }
        return map.values.toList()
    }

    private fun escXml(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")

    // تقدير عدد الأسطر اللازمة لسلسلة عربية داخل عمود بعرض معين (بالأحرف)
    private fun estLines(text: String, colChars: Int): Int {
        if (text.isEmpty()) return 1
        val charsPerLine = (colChars / 2).coerceAtLeast(4)
        return ((text.length + charsPerLine - 1) / charsPerLine).coerceAtLeast(1)
    }
}
