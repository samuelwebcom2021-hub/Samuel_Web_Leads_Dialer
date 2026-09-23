# Implementation Plan - AutoDialerCRM Import & Synchronization Fixes (v2)

This plan addresses the critical bugs in the lead import flow (Excel/CSV/Sheets) and the Google Sheets connection, while also streamlining the domain model to 6 core fields as requested.

## User Review Required

> [!IMPORTANT]
> - **Domain Model Reduction**: 23 fields (address, city, coordinates, hours, etc.) will be discarded during import and will no longer be stored in Room.
> - **Database Migration**: A Room migration (v9 to v10) will be performed. This will recreate the `contacts` table to remove the `extraDataJson` column. Existing contacts' core data will be preserved.
> - **Website Classification**: `websiteType` will be automatically classified into: `SIN_SITIO_WEB`, `RED_SOCIAL`, `CONSTRUCTOR_WEB`, `DOMINIO_PROPIO`.
> - **Deduplication**: Contacts will now be deduplicated based on their normalized `phoneNumber` within a batch.

## Proposed Changes

### [Data Layer]

#### [MODIFY] [ContactEntity.kt](file:///C:/Users/SM/Downloads/AutoDialerCRM/app/src/main/java/com/tuempresa/autodialer/data/ContactEntity.kt)
- Remove `extraDataJson` field.
- Ensure all other business fields match the requested 6 fields: `businessName`, `phoneNumber`, `websiteRaw`, `websiteType`, `rating`, `reviewCount`.

#### [MODIFY] [AppDatabase.kt](file:///C:/Users/SM/Downloads/AutoDialerCRM/app/src/main/java/com/tuempresa/autodialer/data/AppDatabase.kt)
- Increment `version` to 10.
- Implement `MIGRATION_9_10` to handle the removal of `extraDataJson`.

---

### [Excel/CSV/Import Layer]

#### [MODIFY] [WebsiteClassifier.kt](file:///C:/Users/SM/Downloads/AutoDialerCRM/app/src/main/java/com/tuempresa/autodialer/excel/WebsiteClassifier.kt)
- Update `Type` enum to use: `SIN_SITIO_WEB`, `RED_SOCIAL`, `CONSTRUCTOR_WEB`, `DOMINIO_PROPIO`.
- Update classification logic to detect "Constructores Web" (Wix, Squarespace, etc.).

#### [MODIFY] [TableImportProcessor.kt](file:///C:/Users/SM/Downloads/AutoDialerCRM/app/src/main/java/com/tuempresa/autodialer/excel/TableImportProcessor.kt)
- Update `process` method to map columns by **header name** instead of index where possible, or ensure the UI passes the correct indices based on header mapping.
- Discard `extraDataJson` and all non-essential fields.
- Robustly parse `rating` and `reviewCount`.

#### [MODIFY] [ExcelImporter.kt](file:///C:/Users/SM/Downloads/AutoDialerCRM/app/src/main/java/com/tuempresa/autodialer/excel/ExcelImporter.kt)
- Improve file type detection using `ContentResolver` MIME types.
- Ensure robust reading of both XLSX and CSV regardless of file extension.

---

### [UI & Integration Layer]

#### [MODIFY] [MainActivity.kt](file:///C:/Users/SM/Downloads/AutoDialerCRM/app/src/main/java/com/tuempresa/autodialer/ui/MainActivity.kt)
- Update `openExcelLauncher` to support CSV MIME types.
- Fix "Connect Sheets" by improving the error handling and potentially using a more robust file picker approach.
- Audit UI components (adapters, detail screens) to remove references to discarded fields.

#### [MODIFY] [ContactsViewModel.kt](file:///C:/Users/SM/Downloads/AutoDialerCRM/app/src/main/java/com/tuempresa/autodialer/ui/ContactsViewModel.kt)
- Update `analyzeExcelForImport` and `commitImport` to reflect the reduced domain model and deduplication by `phoneNumber`.

---

## Verification Plan

### Automated Tests
- **Unit Tests**:
  - `WebsiteClassifierTest`: Verify classification of various URLs.
  - `TableImportProcessorTest`: Verify transformation from raw 29-column data to 6-field model.
  - `PhoneNormalizationTest`: Verify deduplication logic and phone normalization.

### Manual Verification
- Import an `.xlsx` file with the 29-column structure and verify only 6 fields are saved.
- Import a `.csv` file and verify the same.
- Connect Google Sheets in Settings and verify the file picker shows only Sheets.
- Verify that Statistics and other screens still function correctly with the reduced data.
