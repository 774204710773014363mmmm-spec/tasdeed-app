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

    private fun buildRows(list: List<PaymentRecord>, sort: StatementSort, isMy: Boolean): List<RowData> {
        if (isMy) {
            val rows = list.map {
                RowData(it.note.ifEmpty { "دفعة" }, "", it.paymentDate, it.note, it.amount)
            }
            return when (sort) {
                StatementSort.NAME -> rows.sortedBy { it.name }
                StatementSort.METER -> rows
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
        isMy: Boolean = false
    ) {
        val rows = buildRows(list, sort, isMy)
        val today = vm.repo.currentDate()
        val brName = vm.branchName.value.ifEmpty { vm.branchKey.value ?: "" }
        val user = vm.user.value ?: ""
        val total = rows.sumOf { it.total }

        val doc = PdfDocument()
        val pageW = 842f
        val rowH = 34f
        val headerH = 120f
        val titleH = 60f
        val bodyTop = headerH + titleH + 20f
        val rowsPerPage: Int = (((1123 - bodyTop - 120) / rowH).toInt()).coerceAtLeast(5)
        var pageCount = 0
        val pageCountTotal = (rows.size / rowsPerPage) + 1
        val titlePaint = Paint().apply { color = 0xFF0284C7.toInt(); textSize = 34f; isFakeBoldText = true; textAlign = Paint.Align.CENTER }
        val subPaint = Paint().apply { color = 0xFF333333.toInt(); textSize = 20f; textAlign = Paint.Align.CENTER }
        val smallPaint = Paint().apply { color = 0xFF666666.toInt(); textSize = 15f; textAlign = Paint.Align.CENTER }
        val headerText = Paint().apply { color = android.graphics.Color.WHITE; textSize = 15f; isFakeBoldText = true; textAlign = Paint.Align.CENTER }
        val cellPaint = Paint().apply { color = 0xFF111111.toInt(); textSize = 14f; textAlign = Paint.Align.CENTER }
        val totalPaint = Paint().apply { color = 0xFFD4A843.toInt(); textSize = 26f; isFakeBoldText = true; textAlign = Paint.Align.CENTER }
        val zebra = Paint().apply { color = 0xFFF9F9F9.toInt() }

        val colW = floatArrayOf(40f, 210f, 105f, 120f, 165f, 122f)
        val cols = 6
        var offset = 0
        while (offset < rows.size) {
            val page = doc.startPage(PdfDocument.PageInfo.Builder(842, 1123, pageCount + 1).create())
            val canvas = page.canvas
            val chunk = rows.subList(offset, minOf(offset + rowsPerPage, rows.size))

            canvas.drawRect(0f, 0f, pageW, 4f, Paint().apply { color = 0xFF0284C7.toInt() })
            canvas.drawText("💰 تقرير المدفوعات - $user", pageW / 2f, 60f, titlePaint)
            canvas.drawText(brName, pageW / 2f, 95f, subPaint)
            canvas.drawText("التاريخ: $today | عدد المشتركين: ${rows.size}", pageW / 2f, 120f, smallPaint)
            canvas.drawRect(0f, 130f, pageW, 133f, Paint().apply { color = 0xFF0284C7.toInt() })

            canvas.drawText("الإجمالي: ${numFmt.format(total)} د.ع", pageW / 2f, bodyTop - 10, totalPaint)

            var y = bodyTop
            val headers = arrayOf("#", "المشترك", "العداد", "آخر تاريخ", "ملاحظة", "الإجمالي")
            var x = 40f
            headers.forEachIndexed { i, h ->
                canvas.drawRect(x, y, x + colW[i], y + rowH, Paint().apply { color = 0xFF0284C7.toInt() })
                canvas.drawText(h, x + colW[i] / 2f, y + rowH / 2f + 5f, headerText)
                x += colW[i]
            }
            y += rowH

            chunk.forEachIndexed { i, s ->
                val rowPaint = if (i % 2 == 0) zebra else Paint().apply { color = android.graphics.Color.WHITE }
                x = 40f
                canvas.drawRect(x, y, pageW - 40f, y + rowH, rowPaint)
                val vals = arrayOf(
                    (offset + i + 1).toString(),
                    s.name.ifEmpty { "-" },
                    s.meter.ifEmpty { "-" },
                    s.latestDate,
                    s.note.ifEmpty { "-" },
                    numFmt.format(s.total) + " د.ع"
                )
                for (j in 0 until cols) {
                    canvas.drawText(vals[j], x + colW[j] / 2f, y + rowH / 2f + 5f, cellPaint)
                    x += colW[j]
                }
                y += rowH
            }
            canvas.drawRect(40f, y, pageW - 40f, y + 2f, Paint().apply { color = 0xFFDDDDDD.toInt() })
            canvas.drawText("صفحة ${pageCount + 1} / $pageCountTotal", pageW / 2f, 1090f, smallPaint)
            doc.finishPage(page)
            pageCount++
            offset += rowsPerPage
        }

        doc.writeTo(out)
        doc.close()
    }

    fun exportExcel(
        out: OutputStream,
        vm: AppViewModel,
        periodName: String,
        list: List<PaymentRecord>,
        sort: StatementSort = StatementSort.DATE,
        isMy: Boolean = false
    ) {
        val rows = buildRows(list, sort, isMy)
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
        sb.append("</ss:Styles>\n")
        sb.append("<ss:Worksheet ss:Name=\"المدفوعات\">\n")
        sb.append("<ss:Table>\n")
        sb.append("<ss:Column ss:Width=\"8\"/><ss:Column ss:Width=\"28\"/><ss:Column ss:Width=\"15\"/><ss:Column ss:Width=\"18\"/><ss:Column ss:Width=\"25\"/><ss:Column ss:Width=\"18\"/>\n")
        sb.append("<ss:Row ss:Height=\"30\"><ss:Cell ss:MergeAcross=\"5\" ss:StyleID=\"title\"><ss:Data ss:Type=\"String\">💰 تقرير المدفوعات</ss:Data></ss:Cell></ss:Row>\n")
        sb.append("<ss:Row ss:Height=\"20\"><ss:Cell ss:MergeAcross=\"5\" ss:StyleID=\"sub\"><ss:Data ss:Type=\"String\">$brName | $user</ss:Data></ss:Cell></ss:Row>\n")
        sb.append("<ss:Row ss:Height=\"16\"><ss:Cell ss:MergeAcross=\"5\" ss:StyleID=\"date\"><ss:Data ss:Type=\"String\">$today | عدد المشتركين: ${rows.size}</ss:Data></ss:Cell></ss:Row>\n")
        sb.append("<ss:Row ss:Height=\"24\">")
        for (h in arrayOf("#", "اسم المشترك", "رقم العداد", "آخر تاريخ", "ملاحظة", "الإجمالي")) {
            sb.append("<ss:Cell ss:StyleID=\"head\"><ss:Data ss:Type=\"String\">$h</ss:Data></ss:Cell>")
        }
        sb.append("</ss:Row>\n")
        rows.forEachIndexed { i, s ->
            sb.append("<ss:Row>")
            sb.append("<ss:Cell><ss:Data ss:Type=\"Number\">${i + 1}</ss:Data></ss:Cell>")
            sb.append("<ss:Cell><ss:Data ss:Type=\"String\">${escXml(s.name.ifEmpty { "-" })}</ss:Data></ss:Cell>")
            sb.append("<ss:Cell><ss:Data ss:Type=\"String\">${escXml(s.meter.ifEmpty { "-" })}</ss:Data></ss:Cell>")
            sb.append("<ss:Cell><ss:Data ss:Type=\"String\">${s.latestDate}</ss:Data></ss:Cell>")
            sb.append("<ss:Cell><ss:Data ss:Type=\"String\">${escXml(s.note.ifEmpty { "-" })}</ss:Data></ss:Cell>")
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
}
