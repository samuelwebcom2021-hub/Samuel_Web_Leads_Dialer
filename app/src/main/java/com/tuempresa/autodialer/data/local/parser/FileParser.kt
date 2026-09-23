package com.tuempresa.autodialer.data.local.parser

import android.net.Uri

data class RawFileData(
    val headers: List<String>,
    val rows: List<List<String>>
)

interface FileParser {
    /**
     * Lee solo los encabezados y una fila de muestra para la vista previa/mapeo.
     */
    suspend fun peek(uri: Uri): RawFileData

    /**
     * Lee el archivo completo.
     */
    suspend fun parseAll(uri: Uri): RawFileData
}
