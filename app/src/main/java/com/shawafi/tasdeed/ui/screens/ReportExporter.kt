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
        val labelPaint = Paint().apply { color = 0xFF0284C7.toInt(); textSize = 20f; isFakeBoldText = true; textAlign = Paint.Align.RIGHT }
        val valuePaint = Paint().apply { color = 0xFF111111.toInt(); textSize = 19f; textAlign = Paint.Align.RIGHT }
        val totalPaint = Paint().apply { color = 0xFFD4A843.toInt(); textSize = 30f; isFakeBoldText = true; textAlign = Paint.Align.CENTER }
        val numPaint = Paint().apply { color = 0xFF059669.toInt(); textSize = 34f; isFakeBoldText = true; textAlign = Paint.Align.RIGHT }

        rows.forEachIndexed { i, s ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(842, 1123, i + 1).create())
            val canvas = page.canvas

            canvas.drawRect(0f, 0f, pageW, 6f, Paint().apply { color = 0xFF0284C7.toInt() })
            canvas.drawText("💰 تقرير المدفوعات - $user", pageW / 2f, 55f, titlePaint)
            canvas.drawText("$brName | $periodName", pageW / 2f, 88f, subPaint)
            canvas.drawText("التاريخ: $today | عدد الدفعات: ${rows.size}", pageW / 2f, 114f, smallPaint)
            canvas.drawText("الإجمالي الكلي: ${numFmt.format(total)} د.ع", pageW / 2f, 148f, totalPaint)
            canvas.drawRect(0f, 165f, pageW, 169f, Paint().apply { color = 0xFF0284C7.toInt() })

            // بطاقة الرقم الواحد - كل حقل في سطر مستقل مع التفاف النص الطويل
            val rightX = pageW - 60f
            val maxW = pageW - 230f
            var y = 220f
            canvas.drawText("الرقم ${i + 1} من ${rows.size}", pageW - 60f, y, Paint().apply { color = 0xFF999999.toInt(); textSize = 15f; textAlign = Paint.Align.RIGHT })

            y += 55f
            canvas.drawText("المشترك", rightX, y, labelPaint)
            y += 34f
            y = drawWrapped(canvas, valuePaint, s.name.ifEmpty { "-" }, rightX, y, maxW, 30f)
            y += 20f
            canvas.drawRect(60f, y - 14f, pageW - 60f, y - 12f, Paint().apply { color = 0xFFE5E7EB.toInt() })
            y += 28f

            canvas.drawText("رقم العداد", rightX, y, labelPaint)
            y += 34f
            y = drawWrapped(canvas, valuePaint, s.meter.ifEmpty { "-" }, rightX, y, maxW, 30f)
            y += 20f
            canvas.drawRect(60f, y - 14f, pageW - 60f, y - 12f, Paint().apply { color = 0xFFE5E7EB.toInt() })
            y += 28f

            canvas.drawText("آخر تاريخ", rightX, y, labelPaint)
            y += 34f
            y = drawWrapped(canvas, valuePaint, s.latestDate.ifEmpty { "-" }, rightX, y, maxW, 30f)
            y += 20f
            canvas.drawRect(60f, y - 14f, pageW - 60f, y - 12f, Paint().apply { color = 0xFFE5E7EB.toInt() })
            y += 28f

            canvas.drawText("الملاحظة", rightX, y, labelPaint)
            y += 34f
            y = drawWrapped(canvas, valuePaint, s.note.ifEmpty { "-" }, rightX, y, maxW, 30f)
            y += 20f
            canvas.drawRect(60f, y - 14f, pageW - 60f, y - 12f, Paint().apply { color = 0xFFE5E7EB.toInt() })
            y += 40f

            canvas.drawText("المبلغ:", rightX, y, labelPaint)
            canvas.drawText("${numFmt.format(s.total)} د.ع", rightX, y + 52f, numPaint)

            canvas.drawText("صفحة ${i + 1} / ${rows.size}", pageW / 2f, pageH - 40f, smallPaint)
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
