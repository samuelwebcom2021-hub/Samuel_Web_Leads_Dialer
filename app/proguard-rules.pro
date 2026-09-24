# Reglas de ProGuard / R8 para AutoDialerCRM

# Room Data Entities y Converters
-keep class com.tuempresa.autodialer.data.** { *; }
-keepclassmembers class com.tuempresa.autodialer.data.** { *; }

# Apache POI (Excel) y OpenCSV
-keep class org.apache.poi.** { *; }
-dontwarn org.apache.poi.**
-keep class com.opencsv.** { *; }
-dontwarn com.opencsv.**

# Firebase & Credential Manager
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
