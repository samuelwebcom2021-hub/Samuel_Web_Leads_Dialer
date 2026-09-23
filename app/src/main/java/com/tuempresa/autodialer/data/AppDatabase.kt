package com.tuempresa.autodialer.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        ContactEntity::class, 
        SyncLogEntity::class, 
        CallAttemptEntity::class, 
        BatchEntity::class, 
        ContactExtraFieldEntity::class, 
        PendingSyncOperation::class, 
        ReminderEntity::class,
        CallSessionEntity::class,
        AgendaItemEntity::class
    ], 
    version = 24, 
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun contactDao(): ContactDao
    abstract fun syncLogDao(): SyncLogDao
    abstract fun callAttemptDao(): CallAttemptDao
    abstract fun batchDao(): BatchDao
    abstract fun syncDao(): SyncDao
    abstract fun reminderDao(): ReminderDao
    abstract fun callSessionDao(): CallSessionDao
    abstract fun agendaItemDao(): AgendaItemDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        val MIGRATION_23_24 = object : androidx.room.migration.Migration(23, 24) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_agenda_items_operationId` ON `agenda_items` (`operationId`)")
            }
        }

        val MIGRATION_22_23 = object : androidx.room.migration.Migration(22, 23) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE contacts ADD COLUMN ownerPhone TEXT")
                database.execSQL("ALTER TABLE contacts ADD COLUMN notes TEXT")
            }
        }

        val MIGRATION_21_22 = object : androidx.room.migration.Migration(21, 22) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE agenda_items ADD COLUMN operationId TEXT")
            }
        }

        val MIGRATION_20_21 = object : androidx.room.migration.Migration(20, 21) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE call_sessions ADD COLUMN sessionId INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_19_20 = object : androidx.room.migration.Migration(19, 20) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // 1. Crear tabla agenda_items
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `agenda_items` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `type` TEXT NOT NULL, 
                        `contactId` INTEGER NOT NULL, 
                        `folderId` INTEGER NOT NULL, 
                        `scheduledAt` INTEGER NOT NULL, 
                        `status` TEXT NOT NULL, 
                        `reason` TEXT, 
                        `createdFromCallId` TEXT, 
                        `syncState` TEXT NOT NULL DEFAULT 'CREATED_LOCAL', 
                        `lastUpdated` INTEGER NOT NULL DEFAULT 0, 
                        `remoteId` TEXT
                    )
                """.trimIndent())

                // 2. Normalizar call_sessions (eliminar contactName y nextContactName)
                database.execSQL("""
                    CREATE TABLE `call_sessions_new` (
                        `callId` TEXT NOT NULL, 
                        `folderId` INTEGER NOT NULL, 
                        `contactId` INTEGER NOT NULL, 
                        `dialedNumber` TEXT NOT NULL, 
                        `sim` TEXT NOT NULL, 
                        `state` TEXT NOT NULL, 
                        `result` TEXT, 
                        `startTime` INTEGER NOT NULL, 
                        `answerTime` INTEGER, 
                        `endTime` INTEGER, 
                        `isAutomated` INTEGER NOT NULL, 
                        `isIncoming` INTEGER NOT NULL, 
                        `alternateNumber` TEXT, 
                        `whatsappNumber` TEXT, 
                        `notes` TEXT, 
                        PRIMARY KEY(`callId`)
                    )
                """.trimIndent())
                database.execSQL("""
                    INSERT INTO `call_sessions_new` (
                        callId, folderId, contactId, dialedNumber, sim, state, result, 
                        startTime, answerTime, endTime, isAutomated, isIncoming, 
                        alternateNumber, whatsappNumber, notes
                    )
                    SELECT 
                        callId, folderId, contactId, dialedNumber, sim, state, result, 
                        startTime, answerTime, endTime, isAutomated, isIncoming, 
                        alternateNumber, whatsappNumber, notes
                    FROM call_sessions
                """.trimIndent())
                database.execSQL("DROP TABLE call_sessions")
                database.execSQL("ALTER TABLE call_sessions_new RENAME TO call_sessions")
            }
        }

        val MIGRATION_18_19 = object : androidx.room.migration.Migration(18, 19) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Migración para reminders
                database.execSQL("ALTER TABLE reminders ADD COLUMN syncState TEXT NOT NULL DEFAULT 'CREATED_LOCAL'")
                database.execSQL("ALTER TABLE reminders ADD COLUMN remoteId TEXT")
                
                // Migración para call_attempts
                database.execSQL("ALTER TABLE call_attempts ADD COLUMN syncState TEXT NOT NULL DEFAULT 'CREATED_LOCAL'")
                database.execSQL("ALTER TABLE call_attempts ADD COLUMN lastUpdated INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE call_attempts ADD COLUMN remoteId TEXT")
            }
        }

        val MIGRATION_17_18 = object : androidx.room.migration.Migration(17, 18) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE contacts ADD COLUMN syncState TEXT NOT NULL DEFAULT 'CREATED_LOCAL'")
                database.execSQL("ALTER TABLE contacts ADD COLUMN lastUpdated INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE contacts ADD COLUMN remoteId TEXT")

                database.execSQL("ALTER TABLE batches ADD COLUMN syncState TEXT NOT NULL DEFAULT 'CREATED_LOCAL'")
                database.execSQL("ALTER TABLE batches ADD COLUMN lastUpdated INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE batches ADD COLUMN remoteId TEXT")
            }
        }

        val MIGRATION_16_17 = object : androidx.room.migration.Migration(16, 17) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Eliminar columna residual de Google Sheets de la tabla batches
                database.execSQL("CREATE TABLE `batches_new` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, `isArchived` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                database.execSQL("INSERT INTO `batches_new` (id, name, isArchived) SELECT id, name, isArchived FROM batches")
                database.execSQL("DROP TABLE batches")
                database.execSQL("ALTER TABLE batches_new RENAME TO batches")
            }
        }

        val MIGRATION_15_16 = object : androidx.room.migration.Migration(15, 16) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Schemas son idénticos en esta transición según archivos JSON
            }
        }

        val MIGRATION_14_15 = object : androidx.room.migration.Migration(14, 15) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Schemas son idénticos en esta transición según archivos JSON
            }
        }

        val MIGRATION_13_14 = object : androidx.room.migration.Migration(13, 14) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // 1. Limpiar tabla contacts (eliminar campos de Sheets/Calendar)
                database.execSQL("""
                    CREATE TABLE `contacts_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `phoneNumber` TEXT NOT NULL, 
                        `businessName` TEXT NOT NULL, 
                        `websiteRaw` TEXT, 
                        `websiteType` TEXT, 
                        `rating` REAL, 
                        `reviewCount` INTEGER, 
                        `status` TEXT NOT NULL, 
                        `attemptCount` INTEGER NOT NULL, 
                        `lastAttemptAt` INTEGER, 
                        `nextAttemptAt` INTEGER, 
                        `lastOutcome` TEXT, 
                        `importBatchId` INTEGER NOT NULL, 
                        `importBatchName` TEXT NOT NULL, 
                        `retryRound` INTEGER NOT NULL, 
                        `whatsappNumber` TEXT, 
                        `priority` INTEGER NOT NULL
                    )
                """.trimIndent())
                database.execSQL("""
                    INSERT INTO `contacts_new` (
                        id, phoneNumber, businessName, websiteRaw, websiteType, rating, reviewCount, 
                        status, attemptCount, lastAttemptAt, nextAttemptAt, lastOutcome, 
                        importBatchId, importBatchName, retryRound, whatsappNumber, priority
                    )
                    SELECT 
                        id, phoneNumber, businessName, websiteRaw, websiteType, rating, reviewCount, 
                        status, attemptCount, lastAttemptAt, nextAttemptAt, lastOutcome, 
                        importBatchId, importBatchName, retryRound, whatsappNumber, priority
                    FROM contacts
                """.trimIndent())
                database.execSQL("DROP TABLE contacts")
                database.execSQL("ALTER TABLE contacts_new RENAME TO contacts")

                // 2. Actualizar call_attempts
                database.execSQL("ALTER TABLE `call_attempts` ADD COLUMN `durationMillis` INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE `call_attempts` ADD COLUMN `result` TEXT NOT NULL DEFAULT 'FAILED'")
                database.execSQL("ALTER TABLE `call_attempts` ADD COLUMN `alternateNumber` TEXT")
                database.execSQL("ALTER TABLE `call_attempts` ADD COLUMN `whatsappNumber` TEXT")
                database.execSQL("ALTER TABLE `call_attempts` ADD COLUMN `notes` TEXT")

                // 3. Crear call_sessions
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `call_sessions` (
                        `callId` TEXT NOT NULL, 
                        `folderId` INTEGER NOT NULL, 
                        `contactId` INTEGER NOT NULL, 
                        `contactName` TEXT NOT NULL, 
                        `dialedNumber` TEXT NOT NULL, 
                        `sim` TEXT NOT NULL, 
                        `state` TEXT NOT NULL, 
                        `result` TEXT, 
                        `startTime` INTEGER NOT NULL, 
                        `answerTime` INTEGER, 
                        `endTime` INTEGER, 
                        `isAutomated` INTEGER NOT NULL, 
                        `isIncoming` INTEGER NOT NULL, 
                        `nextContactName` TEXT, 
                        `alternateNumber` TEXT, 
                        `whatsappNumber` TEXT, 
                        `notes` TEXT, 
                        PRIMARY KEY(`callId`)
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_12_13 = object : androidx.room.migration.Migration(12, 13) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `reminders` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `contactId` INTEGER, 
                        `title` TEXT NOT NULL, 
                        `description` TEXT NOT NULL, 
                        `triggerTimeMillis` INTEGER NOT NULL, 
                        `ringtoneUri` TEXT, 
                        `isCompleted` INTEGER NOT NULL, 
                        `lastUpdateTime` INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_11_12 = object : androidx.room.migration.Migration(11, 12) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `pending_sync_operations` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `timestamp` INTEGER NOT NULL, 
                        `entityType` TEXT NOT NULL, 
                        `entityId` TEXT NOT NULL, 
                        `operation` TEXT NOT NULL, 
                        `dataJson` TEXT NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_10_11 = object : androidx.room.migration.Migration(10, 11) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `contact_extra_fields` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `contactId` INTEGER NOT NULL, 
                        `key` TEXT NOT NULL, 
                        `value` TEXT NOT NULL, 
                        FOREIGN KEY(`contactId`) REFERENCES `contacts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE 
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_contact_extra_fields_contactId` ON `contact_extra_fields` (`contactId`)")
            }
        }

        val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // 1. Crear la tabla de carpetas
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `batches` (
                        `id` INTEGER NOT NULL, 
                        `name` TEXT NOT NULL, 
                        `isArchived` INTEGER NOT NULL DEFAULT 0, 
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())

                // 2. Poblar la tabla desde los contactos existentes (deduplicado)
                database.execSQL("""
                    INSERT OR IGNORE INTO `batches` (`id`, `name`)
                    SELECT DISTINCT `importBatchId`, `importBatchName` FROM `contacts`
                """.trimIndent())
            }
        }

        val MIGRATION_9_10 = object : androidx.room.migration.Migration(9, 10) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Room no soporta DROP COLUMN fácilmente en versiones antiguas de SQLite, 
                // así que recreamos la tabla sin extraDataJson.
                
                // 1. Crear tabla temporal
                database.execSQL("""
                    CREATE TABLE `contacts_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `phoneNumber` TEXT NOT NULL,
                        `businessName` TEXT NOT NULL,
                        `websiteRaw` TEXT,
                        `websiteType` TEXT,
                        `rating` REAL,
                        `reviewCount` INTEGER,
                        `status` TEXT NOT NULL,
                        `attemptCount` INTEGER NOT NULL,
                        `lastAttemptAt` INTEGER,
                        `nextAttemptAt` INTEGER,
                        `lastOutcome` TEXT,
                        `importBatchId` INTEGER NOT NULL,
                        `importBatchName` TEXT NOT NULL,
                        `retryRound` INTEGER NOT NULL,
                        `whatsappNumber` TEXT,
                        `priority` INTEGER NOT NULL
                    )
                """.trimIndent())

                // 2. Copiar datos
                database.execSQL("""
                    INSERT INTO `contacts_new` (
                        id, phoneNumber, businessName, websiteRaw, websiteType, rating, reviewCount,
                        status, attemptCount, lastAttemptAt, nextAttemptAt, lastOutcome,
                        importBatchId, importBatchName, retryRound, whatsappNumber, priority
                    )
                    SELECT 
                        id, phoneNumber, businessName, websiteRaw, websiteType, rating, reviewCount,
                        status, attemptCount, lastAttemptAt, nextAttemptAt, lastOutcome,
                        importBatchId, importBatchName, retryRound, whatsappNumber, priority
                    FROM contacts
                """.trimIndent())

                // 3. Eliminar vieja y renombrar nueva
                database.execSQL("DROP TABLE contacts")
                database.execSQL("ALTER TABLE contacts_new RENAME TO contacts")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "autodialer.db"
                )
                    .addMigrations(
                        MIGRATION_8_9, 
                        MIGRATION_9_10, 
                        MIGRATION_10_11, 
                        MIGRATION_11_12, 
                        MIGRATION_12_13,
                        MIGRATION_13_14,
                        MIGRATION_14_15,
                        MIGRATION_15_16,
                        MIGRATION_16_17,
                        MIGRATION_17_18,
                        MIGRATION_18_19,
                        MIGRATION_19_20,
                        MIGRATION_20_21,
                        MIGRATION_21_22,
                        MIGRATION_22_23,
                        MIGRATION_23_24
                    )
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
