package com.tuempresa.autodialer.data.mapping

import com.tuempresa.autodialer.data.ContactEntity
import com.tuempresa.autodialer.data.ContactExtraFieldEntity
import com.tuempresa.autodialer.excel.WebsiteClassifier

object ContactMapper {

    sealed class MapResult {
        data class Success(val contact: ContactEntity, val extraFields: List<ContactExtraFieldEntity>) : MapResult()
        data class Error(val reason: String, val rawValue: String? = null) : MapResult()
    }

    fun map(
        headers: List<String>,
        row: List<String>,
        phoneIndex: Int,
        nameIndex: Int,
        webIndex: Int,
        ratingIndex: Int,
        reviewsIndex: Int,
        countryPrefix: String
    ): MapResult {
        val rawPhone = row.getOrNull(phoneIndex)
        if (rawPhone.isNullOrBlank()) {
            return MapResult.Error("Número de teléfono vacío", rawPhone)
        }

        val normalizedPhone = normalizePhone(rawPhone, countryPrefix)
        if (normalizedPhone == null) {
            return MapResult.Error("Formato de teléfono no válido o de otro país", rawPhone)
        }

        val businessName = if (nameIndex >= 0) row.getOrNull(nameIndex).orEmpty().trim() else ""
        val websiteRaw = if (webIndex >= 0) row.getOrNull(webIndex)?.trim() else null
        val rating = if (ratingIndex >= 0) row.getOrNull(ratingIndex)?.replace(",", ".")?.toDoubleOrNull() else null
        val reviews = if (reviewsIndex >= 0) row.getOrNull(reviewsIndex)?.toIntOrNull() else null
        
        val websiteType = if (!websiteRaw.isNullOrBlank()) {
            WebsiteClassifier.classify(websiteRaw, businessName).type.name
        } else {
            WebsiteClassifier.Type.SIN_SITIO_WEB.name
        }

        val contact = ContactEntity(
            phoneNumber = normalizedPhone,
            businessName = businessName,
            websiteRaw = websiteRaw,
            websiteType = websiteType,
            rating = rating,
            reviewCount = reviews
        )

        val extraFields = mutableListOf<ContactExtraFieldEntity>()
        headers.forEachIndexed { index, header ->
            if (index != phoneIndex && index != nameIndex && index != webIndex && 
                index != ratingIndex && index != reviewsIndex) {
                val value = row.getOrNull(index)
                if (!value.isNullOrBlank()) {
                    extraFields.add(ContactExtraFieldEntity(contactId = 0, key = header, value = value))
                }
            }
        }

        return MapResult.Success(contact, extraFields)
    }

    fun normalizePhone(raw: String, countryPrefix: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null
        
        val hasPlus = trimmed.startsWith("+")
        val cleaned = trimmed.replace(Regex("[^0-9]"), "")
        if (cleaned.length < 7) return null
        
        val purePrefix = countryPrefix.replace(Regex("[^0-9]"), "") // ej: "57"
        
        if (hasPlus) {
            if (cleaned.startsWith(purePrefix)) {
                return "+$cleaned"
            } else {
                // Es de otro país, se descarta por completo
                return null
            }
        }
        
        // No tiene '+' inicial en el archivo
        // Caso A: El número ya contiene el código del país al inicio (ej: 573222184563)
        if (cleaned.startsWith(purePrefix)) {
            if ((purePrefix == "57" && cleaned.length == 12) ||
                (purePrefix == "52" && cleaned.length == 12) ||
                (purePrefix == "1" && cleaned.length == 11) ||
                (purePrefix == "34" && cleaned.length == 11) ||
                (purePrefix != "57" && purePrefix != "52" && purePrefix != "1" && purePrefix != "34")
            ) {
                return "+$cleaned"
            }
        }
        
        // Caso B: Si empieza por un prefijo de otro país conocido, lo descartamos para evitar mezclas
        val otherPrefixes = listOf("57", "52", "1", "34").filter { it != purePrefix }
        for (other in otherPrefixes) {
            if (cleaned.startsWith(other)) {
                if ((other == "57" && cleaned.length == 12) ||
                    (other == "52" && cleaned.length == 12) ||
                    (other == "1" && cleaned.length == 11) ||
                    (other == "34" && cleaned.length == 11)
                ) {
                    return null
                }
            }
        }
        
        // Caso C: Es un número puramente local del país seleccionado, le agregamos el prefijo
        val finalNumber = if (cleaned.startsWith("0")) cleaned.substring(1) else cleaned
        return "+$purePrefix$finalNumber"
    }
}
