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

        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<ss:Workbook xmlns:ss=\"urn:schemas-microsoft-com:office:spreadsheet\">\n")
        sb.append("<ss:Styles>\n")
        sb.append("<ss:Style ss:ID=\"title\"><ss:Font ss:Bold=\"1\" ss:Size=\"20\" ss:Color=\"#059669\"/></ss:Style>\n")
        sb.append("<ss:Style ss:ID=\"sub\"><ss:Font ss:Size=\"12\" ss:Color=\"#667085\"/></ss:Style>\n")
        sb.append("<ss:Style ss:ID=\"date\"><ss:Font ss:Size=\"10\" ss:Color=\"#98A2B3\"/></ss:Style>\n")
        sb.append("<ss:Style ss:ID=\"head\"><ss:Font ss:Bold=\"1\" ss:Color=\"#FFFFFF\"/><ss:Interior ss:Color=\"#059669\" ss:Pattern=\"Solid\"/></ss:Style>\n")
        sb.append("<ss:Style ss:ID=\"total\"><ss:Font ss:Bold=\"1\" ss:Size=\"18\" ss:Color=\"#D4A843\"/></ss:Style>\n")
        sb.append("<ss:Style ss:ID=\"wrapName\"><ss:Alignment ss:WrapText=\"1\"/><ss:Font ss:Size=\"11\"/></ss:Style>\n")
        sb.append("<ss:Style ss:ID=\"wrapNote\"><ss:Alignment ss:WrapText=\"1\"/><ss:Font ss:Size=\"10\" ss:Color=\"#444444\"/></ss:Style>\n")
        sb.append("</ss:Styles>\n")
        sb.append("<ss:Worksheet ss:Name=\"المدفوعات\">\n")
        sb.append("<ss:Table>\n")
        sb.append("<ss:Column ss:Width=\"8\"/><ss:Column ss:Width=\"28\"/><ss:Column ss:Width=\"15\"/><ss:Column ss:Width=\"18\"/><ss:Column ss:Width=\"25\"/><ss:Column ss:Width=\"18\"/>\n")
        sb.append("<ss:Row ss:Height=\"30\"><ss:Cell ss:MergeAcross=\"5\" ss:StyleID=\"title\"><ss:Data ss:Type=\"String\">💰 تقرير المدفوعات</ss:Data></ss:Cell></ss:Row>\n")
        sb.append("<ss:Row ss:Height=\"20\"><ss:Cell ss:MergeAcross=\"5\" ss:StyleID=\"sub\"><ss:Data ss:Type=\"String\">$brName | $user</ss:Data></ss:Cell></ss:Row>\n")
        sb.append("<ss:Row ss:Height=\"16\"><ss:Cell ss:MergeAcross=\"5\" ss:StyleID=\"date\"><ss:Data ss:Type=\"String\">$today | عدد الدفعات: ${rows.size}</ss:Data></ss:Cell></ss:Row>\n")
        sb.append("<ss:Row ss:Height=\"24\">")
        for (h in arrayOf("#", "اسم المشترك", "رقم العداد", "آخر تاريخ", "ملاحظة", "الإجمالي")) {
            sb.append("<ss:Cell ss:StyleID=\"head\"><ss:Data ss:Type=\"String\">$h</ss:Data></ss:Cell>")
        }
        sb.append("</ss:Row>\n")
        rows.forEachIndexed { i, s ->
            // كل رقم في صف مستقل مع خلايا ملفوفة وارتفاع صف يناسب عدد الأسطر
            val nameLines = estLines(s.name, 14)
            val noteLines = estLines(s.note, 12)
            val rowHeight = (maxOf(nameLines, noteLines, 1) * 16 + 6).coerceAtLeast(20)
            sb.append("<ss:Row ss:Height=\"$rowHeight\">")
            sb.append("<ss:Cell><ss:Data ss:Type=\"Number\">${i + 1}</ss:Data></ss:Cell>")
            sb.append("<ss:Cell ss:StyleID=\"wrapName\"><ss:Data ss:Type=\"String\">${escXml(s.name.ifEmpty { "-" })}</ss:Data></ss:Cell>")
            sb.append("<ss:Cell><ss:Data ss:Type=\"String\">${escXml(s.meter.ifEmpty { "-" })}</ss:Data></ss:Cell>")
            sb.append("<ss:Cell><ss:Data ss:Type=\"String\">${s.latestDate}</ss:Data></ss:Cell>")
            sb.append("<ss:Cell ss:StyleID=\"wrapNote\"><ss:Data ss:Type=\"String\">${escXml(s.note.ifEmpty { "-" })}</ss:Data></ss:Cell>")
            sb.append("<ss:Cell><ss:Data ss:Type=\"Number\">${s.total}</ss:Data></ss:Cell>")
            sb.append("</ss:Row>\n")
        }
        sb.append("<ss:Row ss:Height=\"26\"><ss:Cell ss:MergeAcross=\"5\" ss:StyleID=\"total\"><ss:Data ss:Type=\"String\">الإجمالي: ${numFmt.format(total)} د.ع</ss:Data></ss:Cell></ss:Row>\n")
        sb.append("</ss:Table>\n")
        sb.append("</ss:Worksheet>\n")
        sb.append("</ss:Workbook>\n")

        out.write(sb.toString().toByteArray(Charsets.UTF_8))
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
