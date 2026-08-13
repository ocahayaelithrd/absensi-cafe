# Firestore memetakan dokumen ke data class lewat refleksi, jadi nama field
# model tidak boleh diacak R8.
-keep class id.omi.absensicafe.data.model.** { *; }
-keepclassmembers class id.omi.absensicafe.data.model.** { *; }

-dontwarn org.slf4j.**
