package com.tuempresa.autodialer.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class RoomMigrationTest {
    private val TEST_DB = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    @Throws(IOException::class)
    fun migrate9To10() {
        var db = helper.createDatabase(TEST_DB, 9)

        // Insert data using version 9 schema
        db.execSQL("""
            INSERT INTO contacts (
                phoneNumber, businessName, websiteRaw, websiteType, rating, reviewCount, 
                extraDataJson, status, attemptCount, lastAttemptAt, nextAttemptAt, 
                lastOutcome, calendarReminderCreated, sheetRowNumber, importBatchId, 
                importBatchName, retryRound, whatsappNumber, priority, calendarEventId, 
                calendarTimeMillis
            ) VALUES (
                '+573001234567', 'Negocio Test', 'https://test.com', 'OWN_DOMAIN', 4.5, 100,
                '{"key":"value"}', 'PENDING', 0, NULL, NULL,
                NULL, 0, NULL, 123456,
                'Lote Test', 1, NULL, 0, NULL,
                NULL
            )
        """.trimIndent())

        db.close()

        // Run migration
        db = helper.runMigrationsAndValidate(TEST_DB, 10, true, AppDatabase.MIGRATION_9_10)

        // Verify data
        val cursor = db.query("SELECT * FROM contacts")
        assert(cursor.moveToFirst())
        
        val phoneNumberIdx = cursor.getColumnIndex("phoneNumber")
        val businessNameIdx = cursor.getColumnIndex("businessName")
        val websiteRawIdx = cursor.getColumnIndex("websiteRaw")
        val websiteTypeIdx = cursor.getColumnIndex("websiteType")
        val ratingIdx = cursor.getColumnIndex("rating")
        val reviewCountIdx = cursor.getColumnIndex("reviewCount")
        val extraDataJsonIdx = cursor.getColumnIndex("extraDataJson")

        assert(cursor.getString(phoneNumberIdx) == "+573001234567")
        assert(cursor.getString(businessNameIdx) == "Negocio Test")
        assert(cursor.getString(websiteRawIdx) == "https://test.com")
        assert(cursor.getString(websiteTypeIdx) == "OWN_DOMAIN")
        assert(cursor.getDouble(ratingIdx) == 4.5)
        assert(cursor.getInt(reviewCountIdx) == 100)
        
        // extraDataJson should NOT exist in version 10
        assert(extraDataJsonIdx == -1)
        
        cursor.close()
    }
}
