package com.tuempresa.autodialer.excel

import com.tuempresa.autodialer.data.mapping.ContactMapper
import org.junit.Assert.*
import org.junit.Test

class PhoneNormalizationTest {

    @Test
    fun `normalize should handle various formats for Colombian prefix`() {
        val prefix = "+57"
        
        // Formatos válidos con prefijo
        assertEquals("+573001234567", ContactMapper.normalizePhone("+57 300 123 4567", prefix))
        assertEquals("+573001234567", ContactMapper.normalizePhone("+57-300-123-4567", prefix))
        assertEquals("+573001234567", ContactMapper.normalizePhone("+57.300.123.4567", prefix))
        assertEquals("+573001234567", ContactMapper.normalizePhone("(+57) 300 123 4567", prefix))

        // Números locales (sin prefijo)
        assertEquals("+573001234567", ContactMapper.normalizePhone("300 123 4567", prefix))
        assertEquals("+573001234567", ContactMapper.normalizePhone("03001234567", prefix))
    }

    @Test
    fun `normalize should handle US prefix`() {
        val prefix = "+1"
        assertEquals("+18471234567", ContactMapper.normalizePhone("847 123 4567", prefix))
        assertEquals("+18471234567", ContactMapper.normalizePhone("+1 847-123-4567", prefix))
    }
}
