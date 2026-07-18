# Phase 38 : règles R8 minimales pour le build release.
# La plupart des libs (Retrofit, OkHttp, Room, Hilt, Media3) embarquent
# déjà leurs propres règles consumer ; on ne garde ici que ce qui casse
# sous obfuscation par réflexion (Gson) ou n'a pas de règle consumer.

# --- Gson : désérialisation par réflexion des DTOs Xtream ---
# Sans ceci, R8 renomme les champs et Gson ne peut plus mapper le JSON
# (@SerializedName pointe vers un nom qui n'existe plus après obfuscation).
-keep class com.cstv.app.data.remote.dto.** { <fields>; }
-keep class com.cstv.app.domain.model.** { <fields>; }

# TypeAdapters Gson enregistrés manuellement (AppModule.provideGson) :
# Gson y accède par réflexion, pas par un site d'appel visible de R8.
-keep class com.cstv.app.data.remote.gson.** { *; }

-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# --- Retrofit : signatures génériques des interfaces de service ---
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep interface com.cstv.app.data.remote.api.XtreamApiService { *; }
