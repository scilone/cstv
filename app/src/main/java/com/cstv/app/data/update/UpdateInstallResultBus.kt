package com.cstv.app.data.update

import com.cstv.app.domain.update.PackageInstallResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Relais entre [UpdateInstallResultReceiver] (instancié par le système,
 * hors du graphe de composition classique) et [AndroidPackageInstaller]
 * (F35). `Singleton` Hilt : les deux sont injectés avec la même instance.
 */
@Singleton
class UpdateInstallResultBus @Inject constructor() {
    private val _results = MutableSharedFlow<PackageInstallResult>(extraBufferCapacity = 4)
    val results: Flow<PackageInstallResult> = _results.asSharedFlow()

    fun tryEmit(result: PackageInstallResult) {
        _results.tryEmit(result)
    }
}
