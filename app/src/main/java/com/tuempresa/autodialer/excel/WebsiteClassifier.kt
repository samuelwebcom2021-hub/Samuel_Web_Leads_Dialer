package com.tuempresa.autodialer.excel

import java.net.URI

/**
 * Analiza el valor de la columna "sitio web" del Excel. Esto NO es una IA real: son reglas
 * simples (dominio vs. lista de redes sociales conocidas + comparación de texto con el nombre
 * del negocio). Es suficiente para el caso de uso ("¿tiene página propia o solo Instagram?"),
 * pero no reemplaza una verificación real (por ejemplo, comprobar que el sitio responda).
 */
object WebsiteClassifier {

    enum class Type { DOMINIO_PROPIO, RED_SOCIAL, CONSTRUCTOR_WEB, SIN_SITIO_WEB, UNRECOGNIZED }

    data class Result(val type: Type, val domain: String?, val matchesBusinessName: Boolean)

    private val socialDomains = listOf(
        "instagram.com", "facebook.com", "fb.com", "fb.me",
        "wa.me", "api.whatsapp.com", "whatsapp.com",
        "linktr.ee", "beacons.ai", "linkin.bio",
        "twitter.com", "x.com", "tiktok.com", "youtube.com", "youtu.be"
    )

    private val builderDomains = listOf(
        "wix.com", "wixsite.com", "squarespace.com", "godaddysites.com",
        "weebly.com", "business.site", "jimdo.com", "wordpress.com"
    )

    fun classify(rawValue: String?, businessName: String?): Result {
        val cleaned = rawValue?.trim().orEmpty()
        if (cleaned.isBlank()) return Result(Type.SIN_SITIO_WEB, null, false)

        val domain = extractDomain(cleaned)
            ?: return Result(Type.UNRECOGNIZED, cleaned, false)

        val isSocial = socialDomains.any { domain == it || domain.endsWith(".$it") }
        if (isSocial) return Result(Type.RED_SOCIAL, domain, false)

        val isBuilder = builderDomains.any { domain == it || domain.endsWith(".$it") }
        if (isBuilder) return Result(Type.CONSTRUCTOR_WEB, domain, false)

        val matches = similarToBusinessName(domain, businessName)
        return Result(Type.DOMINIO_PROPIO, domain, matches)
    }

    private fun extractDomain(value: String): String? {
        val withScheme = if (!value.contains("://")) "http://$value" else value
        return try {
            URI(withScheme).host?.removePrefix("www.")?.lowercase()
        } catch (e: Exception) {
            null
        }
    }

    /** Compara la raíz del dominio contra el nombre del negocio (coincidencia aproximada, no exacta). */
    private fun similarToBusinessName(domain: String, businessName: String?): Boolean {
        if (businessName.isNullOrBlank()) return false
        val domainCore = domain.substringBefore(".").lowercase().filter { it.isLetterOrDigit() }
        val nameCore = businessName.lowercase().filter { it.isLetterOrDigit() }
        if (domainCore.isEmpty() || nameCore.isEmpty()) return false
        return nameCore.contains(domainCore) || domainCore.contains(nameCore)
    }
}
