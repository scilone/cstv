package com.cstv.app.data.update

import java.io.File

/** Résultat minimal de l'inspection d'un fichier APK local (F35). */
data class InspectedApk(val packageName: String, val versionCode: Int)

/**
 * Inspecte un fichier local pour savoir s'il s'agit d'un APK exploitable.
 * Implémentation Android : `AndroidApkInspector` (`PackageManager`, non
 * testable sur JVM). Une fausse implémentation couvre [UpdateApkStore] en
 * test unitaire (§4.13).
 */
interface ApkInspector {
    /** `null` si le fichier n'est pas lisible comme APK. */
    fun inspect(file: File): InspectedApk?
}

/**
 * Gère `cacheDir/app_updates` (RG12) : nommage, réutilisation, purge. Ne
 * connaît ni réseau, ni installation — uniquement le système de fichiers
 * (§4.4/§4.7).
 */
class UpdateApkStore(
    private val directory: File,
    private val apkInspector: ApkInspector,
    private val applicationId: String
) {
    fun partFileFor(versionCode: Int): File = File(directory, "cstv-$versionCode.apk.part")
    fun finalFileFor(versionCode: Int): File = File(directory, "cstv-$versionCode.apk")

    /** Garantit l'existence du répertoire avant écriture. */
    fun ensureDirectory(): File = directory.apply { mkdirs() }

    /**
     * APK déjà en cache pour cette Release, réutilisable si sa taille
     * correspond à celle annoncée et que `PackageManager` reconnaît le bon
     * package et le bon `versionCode` (§4.4). `null` sinon.
     */
    fun findReusableApk(versionCode: Int, expectedSizeBytes: Long): File? {
        val candidate = finalFileFor(versionCode)
        return candidate.takeIf { isValid(it, versionCode, expectedSizeBytes) }
    }

    /** Renommage atomique `.part` → `.apk` en fin de téléchargement. */
    fun promotePart(versionCode: Int): File {
        val part = partFileFor(versionCode)
        val final = finalFileFor(versionCode)
        if (final.exists()) final.delete()
        if (!part.renameTo(final)) {
            // Repli copie+suppression : `renameTo` échoue parfois entre systèmes
            // de fichiers distincts (rare pour un même cacheDir, mais possible
            // sur certains OEM). Toujours dans `cacheDir`, jamais un stockage partagé.
            part.copyTo(final, overwrite = true)
            part.delete()
        }
        return final
    }

    /**
     * F35-R2 : un APK qui vient d'être promu depuis un `.part` n'a jamais été
     * contrôlé — une réponse tronquée ou un asset au mauvais package/version
     * serait sinon transmis tel quel à `PackageInstaller`. Mêmes critères que
     * [findReusableApk] ; supprime le fichier final invalide plutôt que de le
     * laisser traîner dans le cache.
     */
    fun validatePromoted(versionCode: Int, expectedSizeBytes: Long): File? {
        val candidate = finalFileFor(versionCode)
        if (isValid(candidate, versionCode, expectedSizeBytes)) return candidate
        candidate.delete()
        return null
    }

    private fun isValid(file: File, versionCode: Int, expectedSizeBytes: Long): Boolean {
        if (!file.isFile || file.length() != expectedSizeBytes) return false
        val inspected = apkInspector.inspect(file) ?: return false
        return inspected.packageName == applicationId && inspected.versionCode == versionCode
    }

    fun deletePart(versionCode: Int) {
        partFileFor(versionCode).delete()
    }

    /**
     * Purge au démarrage (§4.7/RG13) : supprime les `.part` d'une tentative
     * interrompue, tout APK illisible ou d'un autre package, et tout APK dont
     * le `versionCode` est ≤ celui installé. Conserve un APK valide de
     * version supérieure (mise à jour obligatoire déjà téléchargée).
     */
    fun purge(installedVersionCode: Int) {
        val files = directory.listFiles() ?: return
        for (file in files) {
            when {
                file.name.endsWith(".part") -> file.delete()
                file.name.endsWith(".apk") -> purgeIfObsolete(file, installedVersionCode)
                else -> Unit
            }
        }
    }

    private fun purgeIfObsolete(file: File, installedVersionCode: Int) {
        val inspected = apkInspector.inspect(file)
        val obsolete = inspected == null ||
            inspected.packageName != applicationId ||
            inspected.versionCode <= installedVersionCode
        if (obsolete) file.delete()
    }
}
