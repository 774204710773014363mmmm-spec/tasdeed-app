package com.shawafi.smsapp

import android.content.Context
import android.net.Uri
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipInputStream

data class ImportResult(val rows: List<SmsRow>, val invalidPhones: List<String>, val error: String? = null)

/**
 * قراءة كشوفات Excel/CSV بدون أي مكتبات خارجية ثقيلة (لا POI ولا XMLBeans):
 * - .csv / .txt  : نصوص (UTF-8 أو windows-1256)
 * - .xls         : عبر مكتبة jxl الخفيفة (BIFF8)
 * - .xlsx        : فك ضغط يدوي + قراءة XML مباشرة
 */
object SmsImport {

    private val PHONE_HEADERS = listOf("رقم المشترك", "الجوال", "رقم الجوال", "الهاتف", "الموبايل", "موبايل", "phone", "mobile")
    private val NAME_HEADERS = listOf("اسم المشترك", "الاسم", "اسم", "name")
    private val PREV_HEADERS = listOf("القراءة السابقة", "قراءة سابقة", "السابقة", "previous", "prev")
    private val CUR_HEADERS = listOf("القراءة الحالية", "قراءة حالية", "الحالية", "current", "cur")
    private val ARR_HEADERS = listOf("المتأخرات", "متأخرات", "متأخر", "arrears", "متبقي")

    fun parse(context: Context, uri: Uri): ImportResult {
        return try {
            val name = uri.lastPathSegment ?: ""
            val ext = name.substringAfterLast('.', "").lowercase()
            val input = context.contentResolver.openInputStream(uri) ?: return ImportResult(emptyList(), emptyList(), "تعذر فتح الملف")
            input.use { stream ->
                val parsed: List<List<String>> = when (ext) {
                    "xls" -> readXls(stream)
                    "xlsx" -> readXlsx(stream)
                    else -> readCsv(stream)
                }
                buildRows(parsed)
            }
        } catch (t: Throwable) {
            // أي خطأ (حتى NoClassDefFoundError) يظهر كرسالة بدل تعليق التطبيق
            ImportResult(emptyList(), emptyList(), t.message ?: "تعذر قراءة الملف")
        }
    }

    private fun decode(bytes: ByteArray): String {
        val utf8 = String(bytes, Charsets.UTF_8)
        if (!utf8.contains('\uFFFD')) return utf8.removePrefix("\uFEFF")
        val sb = StringBuilder(bytes.size)
        for (b in bytes) {
            val u = b.toInt() and 0xFF
            sb.append(if (u <= 127) u.toChar() else (WIN1256[u] ?: '?'))
        }
        return sb.toString()
    }

    // جدول windows-1256 (لفك تشفير ملفات CSV العربية القديمة)
    private val WIN1256: Map<Int, Char> = run {
        val plain = mapOf(
            0x80 to '\u20AC', 0x82 to '\u201A', 0x83 to '\u0192', 0x84 to '\u201E',
            0x85 to '\u2026', 0x86 to '\u2020', 0x87 to '\u2021', 0x88 to '\u02C6',
            0x89 to '\u2030', 0x8A to '\u0679', 0x8B to '\u2039', 0x8C to '\u0152',
            0x8E to '\u0686', 0x91 to '\u2018', 0x92 to '\u2019', 0x93 to '\u201C',
            0x94 to '\u201D', 0x95 to '\u2022', 0x96 to '\u2013', 0x97 to '\u2014',
            0x98 to '\u06A9', 0x99 to '\u2122', 0x9A to '\u0691', 0x9B to '\u203A',
            0x9C to '\u0153', 0x9E to '\u0670', 0x9F to '\u06BA'
        )
        val arabic = mapOf(
            0xA1 to '،', 0xA2 to '\u00AD', 0xA4 to '\u00A4', 0xA6 to '|', 0xAA to '؛',
            0xBA to '؛', 0xBF to '\u061F',
            0xC0 to 'ء', 0xC1 to 'آ', 0xC2 to 'أ', 0xC3 to 'ؤ', 0xC4 to 'إ', 0xC5 to 'ئ',
            0xC6 to 'ا', 0xC7 to 'ب', 0xC8 to 'ة', 0xC9 to 'ت', 0xCA to 'ث', 0xCB to 'ج',
            0xCC to 'ح', 0xCD to 'خ', 0xCE to 'د', 0xCF to 'ذ', 0xD0 to 'ر', 0xD1 to 'ز',
            0xD2 to 'س', 0xD3 to 'ش', 0xD4 to 'ص', 0xD5 to 'ض', 0xD6 to 'ط', 0xD7 to 'ظ',
            0xD8 to 'ع', 0xD9 to 'غ', 0xDA to 'ـ', 0xDB to 'ف', 0xDC to 'ق', 0xDD to 'ك',
            0xDE to 'ل', 0xDF to 'م', 0xE0 to 'ن', 0xE1 to 'ه', 0xE2 to 'و', 0xE3 to 'ى',
            0xE4 to 'ي', 0xE5 to 'ً', 0xE6 to 'ٌ', 0xE7 to 'ٍ', 0xE8 to 'َ', 0xE9 to 'ُ',
            0xEA to 'ِ', 0xEB to 'ّ', 0xEC to 'ْ', 0xED to '\u0653', 0xEE to '\u0654', 0xEF to '\u0655',
            0xF0 to 'ء', 0xF1 to 'آ', 0xF2 to 'أ', 0xF3 to 'ؤ', 0xF4 to 'إ', 0xF5 to 'ئ',
            0xF6 to 'ا', 0xF7 to 'ب', 0xF8 to 'ة', 0xF9 to 'ت', 0xFA to 'ث', 0xFB to 'ج',
            0xFC to 'ح', 0xFD to 'خ', 0xFE to 'د', 0xFF to 'ذ'
        )
        plain + arabic
    }

    // ---------- CSV ----------

    private fun readCsv(stream: InputStream): List<List<String>> {
        val text = decode(stream.readBytes())
        return text.split("\r\n", "\n", "\r")
            .map { parseCsvLine(it) }
            .filter { it.any { c -> c.isNotBlank() } }
    }

    private fun parseCsvLine(line: String): List<String> {
        val delim = if (line.contains('\t')) '\t' else ','
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> { cur.append('"'); i += 2 }
                c == '"' -> { inQuotes = !inQuotes; i++ }
                c == delim && !inQuotes -> { out.add(cur.toString()); cur.setLength(0); i++ }
                else -> { cur.append(c); i++ }
            }
        }
        out.add(cur.toString())
        return out
    }

    // ---------- XLS عبر jxl (BIFF8) ----------

    private fun readXls(stream: InputStream): List<List<String>> {
        val wb = jxl.Workbook.getWorkbook(stream)
        return try {
            val sheet = wb.getSheet(0)
            val rows = mutableListOf<List<String>>()
            for (r in 0 until sheet.rows) {
                val row = mutableListOf<String>()
                for (c in 0 until sheet.columns) row.add(sheet.getCell(c, r).contents.trim())
                rows.add(row)
            }
            rows
        } finally {
            wb.close()
        }
    }

    // ---------- XLSX يدوي (zip + xml) ----------

    private fun readXlsx(stream: InputStream): List<List<String>> {
        val shared = mutableListOf<String>()
        var sheetRows: MutableList<List<String>>? = null
        val zip = ZipInputStream(stream)
        var entry = zip.nextEntry
        while (entry != null) {
            when (entry.name) {
                "xl/sharedStrings.xml" -> readSharedStrings(zip, shared)
                "xl/worksheets/sheet1.xml" -> sheetRows = readSheet(zip, shared)
            }
            zip.closeEntry()
            entry = zip.nextEntry
        }
        return sheetRows ?: emptyList()
    }

    private fun newParser(zip: ZipInputStream) = XmlPullParserFactory.newInstance().newPullParser().apply {
        setInput(zip, "UTF-8")
    }

    private fun readSharedStrings(zip: ZipInputStream, out: MutableList<String>) {
        val parser = newParser(zip)
        var event = parser.eventType
        var inSi = false
        var inT = false
        val cur = StringBuilder()
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "si" -> { inSi = true; cur.setLength(0) }
                    "t" -> inT = true
                }
                XmlPullParser.TEXT -> if (inT) cur.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "t" -> inT = false
                    "si" -> if (inSi) { out.add(cur.toString()); inSi = false }
                }
            }
            event = parser.next()
        }
    }

    private fun readSheet(zip: ZipInputStream, shared: List<String>): MutableList<List<String>> {
        val out = mutableListOf<List<String>>()
        val parser = newParser(zip)
        var event = parser.eventType
        var curRow = HashMap<Int, String>()
        var maxCol = -1
        var curCol = -1
        var cellType = ""
        val cellValue = StringBuilder()
        var inCell = false
        var collectText = false
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> { curRow = HashMap(); maxCol = -1 }
                    "c" -> {
                        curCol = colRefToIdx(parser.getAttributeValue(null, "r") ?: "")
                        cellType = parser.getAttributeValue(null, "t") ?: ""
                        cellValue.setLength(0)
                        inCell = true
                    }
                    "v" -> collectText = true
                    "t" -> if (inCell) collectText = true
                }
                XmlPullParser.TEXT -> if (collectText && inCell) cellValue.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "c" -> {
                        if (inCell) {
                            val raw = cellValue.toString().trim()
                            val value = when (cellType) {
                                "s" -> raw.toIntOrNull()?.let { shared.getOrNull(it) } ?: ""
                                "b" -> if (raw == "1") "TRUE" else "FALSE"
                                else -> raw
                            }
                            if (curCol >= 0 && value.isNotEmpty()) {
                                curRow[curCol] = value
                                if (curCol > maxCol) maxCol = curCol
                            }
                            inCell = false
                        }
                    }
                    "row" -> if (maxCol >= 0) {
                        val cells = (0..maxCol).map { curRow[it] ?: "" }
                        if (cells.any { it.isNotBlank() }) out.add(cells)
                    }
                }
            }
            collectText = false
            event = parser.next()
        }
        return out
    }

    private fun colRefToIdx(ref: String): Int {
        var idx = 0
        for (ch in ref) {
            if (ch in 'A'..'Z') idx = idx * 26 + (ch - 'A' + 1) else break
        }
        return idx - 1
    }

    // ---------- تحويل إلى صفوف ----------

    private fun buildRows(parsed: List<List<String>>): ImportResult {
        if (parsed.isEmpty()) return ImportResult(emptyList(), emptyList())
        var startIdx = 0
        var phoneIdx = 0
        var nameIdx = 1
        var prevIdx = 2
        var curIdx = 3
        var arrIdx = 4
        var hasHeader = false

        for (i in parsed.indices) {
            val names = parsed[i].map { it.trim() }
            val phone = names.indexOfFirst { n -> PHONE_HEADERS.any { n.contains(it) } }
            val name = names.indexOfFirst { n -> NAME_HEADERS.any { n == it && n.isNotBlank() } }
            val prev = names.indexOfFirst { n -> PREV_HEADERS.any { n.contains(it) } }
            val cur = names.indexOfFirst { n -> CUR_HEADERS.any { n.contains(it) } }
            val arr = names.indexOfFirst { n -> ARR_HEADERS.any { n.contains(it) } }
            val found = listOfNotNull(phone.takeIf { it >= 0 }, name.takeIf { it >= 0 }, prev.takeIf { it >= 0 }, cur.takeIf { it >= 0 }, arr.takeIf { it >= 0 })
            if (found.size >= 2) {
                startIdx = i + 1
                hasHeader = true
                if (phone >= 0) phoneIdx = phone
                if (name >= 0) nameIdx = name
                if (prev >= 0) prevIdx = prev
                if (cur >= 0) curIdx = cur
                if (arr >= 0) arrIdx = arr
                break
            }
        }
        if (!hasHeader) startIdx = 0

        val rows = mutableListOf<SmsRow>()
        val invalid = mutableListOf<String>()
        for (i in startIdx until parsed.size) {
            val row = parsed[i]
            val maxIdx = maxOf(phoneIdx, nameIdx, prevIdx, curIdx, arrIdx)
            if (row.size <= maxIdx) continue
            val phoneRaw = row[phoneIdx].trim()
            val name = row[nameIdx].trim()
            val prev = parseNum(row[prevIdx])
            val cur = parseNum(row[curIdx])
            val arr = parseNum(row[arrIdx])
            if (phoneRaw.isBlank()) continue
            val norm = SmsPhone.normalize(phoneRaw)
            if (SmsPhone.isValid(norm)) {
                rows.add(
                    SmsRow(
                        id = UUID.randomUUID().toString(),
                        phone = norm,
                        name = name,
                        prevReading = prev,
                        curReading = cur,
                        arrears = arr
                    )
                )
            } else {
                invalid.add(phoneRaw)
            }
        }
        return ImportResult(rows, invalid)
    }

    fun parseNum(sRaw: String): Double {
        var s = sRaw.trim()
        s = s.replace('٠', '0').replace('١', '1').replace('٢', '2').replace('٣', '3')
            .replace('٤', '4').replace('٥', '5').replace('٦', '6').replace('٧', '7')
            .replace('٨', '8').replace('٩', '9')
        s = s.replace('۰', '0').replace('۱', '1').replace('۲', '2').replace('۳', '3')
            .replace('۴', '4').replace('۵', '5').replace('۶', '6').replace('۷', '7')
            .replace('۸', '8').replace('۹', '9')
        s = s.replace(",", "").replace("،", "")
        return s.toDoubleOrNull() ?: 0.0
    }
}