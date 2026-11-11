# R8 rules for the release build. Room 2.7 and kotlinx-serialization 1.8 ship consumer rules too;
# these make the app's own needs explicit.

# Room: it loads the generated JuiceDatabase_Impl and *Dao_Impl classes by name, and reads entity
# fields reflectively-by-name through the generated code.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Database class * { *; }
-keep @androidx.room.Entity class com.juice.data.** { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class com.juice.data.*_Impl { *; }

# kotlinx.serialization: standard rules for @Serializable classes (companions and generated
# serializers). The app has none today (GroqClient builds JsonObject trees), but this keeps
# any added later working.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.juice.**$$serializer { *; }
-keepclassmembers class com.juice.** { *** Companion; }
-keepclasseswithmembers class com.juice.** { kotlinx.serialization.KSerializer serializer(...); }
