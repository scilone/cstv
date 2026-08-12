package com.cstv.app.data.update

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Implémentation Android d'[ApkInspector] via `PackageManager` (F35, §4.4). */
@Singleton
class AndroidApkInspector @Inject constructor(
    @ApplicationContext private val context: Context
) : ApkInspector {

    override fun inspect(file: File): InspectedApk? {
        val info = try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
        } catch (e: Exception) {
            null
        } ?: return null
        val packageName = info.packageName
        val versionCode = versionCodeOf(info)
        return InspectedApk(packageName, versionCode)
    }

    private fun versionCodeOf(info: android.content.pm.PackageInfo): Int =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            info.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            info.versionCode
        }
}
