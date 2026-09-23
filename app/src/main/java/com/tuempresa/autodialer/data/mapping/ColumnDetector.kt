package com.tuempresa.autodialer.data.mapping

object ColumnDetector {
    
    data class DetectionResult(
        val phoneIndex: Int = -1,
        val nameIndex: Int = -1,
        val websiteIndex: Int = -1,
        val ratingIndex: Int = -1,
        val reviewCountIndex: Int = -1
    )

    private val PHONE_ALIASES = setOf(
        "phone_number", "phone", "telephone", "mobile", "cellphone", "celular", "telefono",
        "tel", "movil", "contacto", "phone number", "teléfono", "número", "numero"
    )

    private val NAME_ALIASES = setOf(
        "name", "business_name", "nombre", "negocio", "company_name", "empresa", "titulo",
        "title", "business name", "nombre de negocio"
    )

    private val WEBSITE_ALIASES = setOf(
        "website", "web", "sitio_web", "url", "link", "página web", "pagina web", "site", "sitio"
    )

    private val RATING_ALIASES = setOf(
        "rating", "calificacion", "estrellas", "score", "puntos", "calificación"
    )

    private val REVIEWS_ALIASES = setOf(
        "review_count", "reviews", "resenas", "comentarios", "count", "total_reviews",
        "reseñas totales", "reseñas"
    )

    fun detect(headers: List<String>): DetectionResult {
        var phone = -1
        var name = -1
        var web = -1
        var rating = -1
        var reviews = -1

        headers.forEachIndexed { index, header ->
            // Normalizar eliminando guiones y espacios adicionales para comparación exacta limpia
            val clean = header.lowercase().trim()
            val normalized = clean.replace("_", " ").replace("-", " ")
            val collapsed = clean.replace("_", "").replace("-", "").replace(" ", "")

            if (phone == -1) {
                if (PHONE_ALIASES.contains(clean) || PHONE_ALIASES.contains(normalized) || PHONE_ALIASES.contains(collapsed)) {
                    phone = index
                }
            }
            
            if (name == -1) {
                if (NAME_ALIASES.contains(clean) || NAME_ALIASES.contains(normalized) || NAME_ALIASES.contains(collapsed)) {
                    name = index
                }
            }

            if (web == -1) {
                if (WEBSITE_ALIASES.contains(clean) || WEBSITE_ALIASES.contains(normalized) || WEBSITE_ALIASES.contains(collapsed)) {
                    web = index
                }
            }

            if (rating == -1) {
                if (RATING_ALIASES.contains(clean) || RATING_ALIASES.contains(normalized) || RATING_ALIASES.contains(collapsed)) {
                    rating = index
                }
            }

            if (reviews == -1) {
                if (REVIEWS_ALIASES.contains(clean) || REVIEWS_ALIASES.contains(normalized) || REVIEWS_ALIASES.contains(collapsed)) {
                    reviews = index
                }
            }
        }

        return DetectionResult(phone, name, web, rating, reviews)
    }
}
