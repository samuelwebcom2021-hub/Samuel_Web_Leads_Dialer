package com.tuempresa.autodialer.data.local.parser

import android.content.Context
import android.net.Uri
import com.opencsv.CSVParserBuilder
import com.opencsv.CSVReaderBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStreamReader

class CsvParser(private val context: Context) : FileParser {

    override suspend fun peek(uri: Uri): RawFileData = withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("No se pudo abrir el archivo")
        
        InputStreamReader(inputStream).use { reader ->
            // Detectar delimitador (heurística simple: primera línea)
            val firstLine = reader.run { 
                val lineReader = java.io.BufferedReader(InputStreamReader(context.contentResolver.openInputStream(uri)))
                lineReader.readLine() ?: ""
            }
            val delimiter = if (firstLine.contains(";")) ';' else ','
            
            val csvParser = CSVParserBuilder().withSeparator(delimiter).build()
            val csvReader = CSVReaderBuilder(InputStreamReader(context.contentResolver.openInputStream(uri)))
                .withCSVParser(csvParser)
                .build()

            val allLines = csvReader.readNext() ?: return@withContext RawFileData(emptyList(), emptyList())
            val headers = allLines.toList()
            val sample = csvReader.readNext()?.toList() ?: emptyList()
            
            RawFileData(headers, if (sample.isNotEmpty()) listOf(sample) else emptyList())
        }
    }

    override suspend fun parseAll(uri: Uri): RawFileData = withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("No se pudo abrir el archivo")

        InputStreamReader(inputStream).use { reader ->
            val firstLine = java.io.BufferedReader(InputStreamReader(context.contentResolver.openInputStream(uri))).readLine() ?: ""
            val delimiter = if (firstLine.contains(";")) ';' else ','
            
            val csvParser = CSVParserBuilder().withSeparator(delimiter).build()
            val csvReader = CSVReaderBuilder(InputStreamReader(context.contentResolver.openInputStream(uri)))
                .withCSVParser(csvParser)
                .build()

            val lines = csvReader.readAll()
            if (lines.isEmpty()) return@withContext RawFileData(emptyList(), emptyList())
            
            val headers = lines.first().toList()
            val rows = lines.drop(1).map { it.toList() }
            
            RawFileData(headers, rows)
        }
    }
}
