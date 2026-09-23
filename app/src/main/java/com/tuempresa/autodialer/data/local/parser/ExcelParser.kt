package com.tuempresa.autodialer.data.local.parser

import android.content.Context
import android.net.Uri
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ExcelParser(private val context: Context) : FileParser {

    override suspend fun peek(uri: Uri): RawFileData = withContext(Dispatchers.IO) {
        openWorkbook(uri).use { workbook ->
            val sheet = workbook.getSheetAt(0)
            val rows = mutableListOf<List<String>>()
            
            val headerRow = sheet.getRow(0) ?: return@withContext RawFileData(emptyList(), emptyList())
            val headers = rowToList(headerRow)
            
            val sampleRow = sheet.getRow(1)
            if (sampleRow != null) {
                rows.add(rowToList(sampleRow))
            }
            
            RawFileData(headers, rows)
        }
    }

    override suspend fun parseAll(uri: Uri): RawFileData = withContext(Dispatchers.IO) {
        openWorkbook(uri).use { workbook ->
            val sheet = workbook.getSheetAt(0)
            val headers: List<String>
            val rows = mutableListOf<List<String>>()

            val rowIterator = sheet.iterator()
            if (!rowIterator.hasNext()) return@withContext RawFileData(emptyList(), emptyList())

            headers = rowToList(rowIterator.next())

            while (rowIterator.hasNext()) {
                rows.add(rowToList(rowIterator.next()))
            }

            RawFileData(headers, rows)
        }
    }

    private fun openWorkbook(uri: Uri): Workbook {
        val inputStream: InputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("No se pudo abrir el archivo")
        
        return try {
            WorkbookFactory.create(inputStream)
        } catch (e: Exception) {
            // Reintentar con XSSF si falla la autodetección (a veces pasa con Streams)
            inputStream.close()
            val newStream = context.contentResolver.openInputStream(uri)!!
            if (uri.toString().endsWith(".xlsx", true)) {
                XSSFWorkbook(newStream)
            } else {
                HSSFWorkbook(newStream)
            }
        }
    }

    private fun rowToList(row: Row): List<String> {
        val list = mutableListOf<String>()
        val lastCellNum = row.lastCellNum.toInt()
        for (i in 0 until lastCellNum) {
            val cell = row.getCell(i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK)
            list.add(getCellValueAsString(cell))
        }
        return list
    }

    private fun getCellValueAsString(cell: Cell): String {
        return when (cell.cellType) {
            CellType.STRING -> cell.stringCellValue
            CellType.NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    cell.dateCellValue.toString()
                } else {
                    // Evitar notación científica para teléfonos si vienen como número
                    val df = DataFormatter()
                    df.formatCellValue(cell)
                }
            }
            CellType.BOOLEAN -> cell.booleanCellValue.toString()
            CellType.FORMULA -> {
                try {
                    cell.stringCellValue
                } catch (e: Exception) {
                    val df = DataFormatter()
                    df.formatCellValue(cell)
                }
            }
            else -> ""
        }.trim()
    }
}
