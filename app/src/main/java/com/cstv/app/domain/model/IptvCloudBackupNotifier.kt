package com.cstv.app.domain.model

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class IptvCloudBackupNotice { SAVE_DEFERRED, DELETE_DEFERRED }

/** Process-local, non-sensitive feedback for cloud operations kept off the IPTV login path. */
@Singleton
class IptvCloudBackupNotifier @Inject constructor() {
    private val _notices = MutableSharedFlow<IptvCloudBackupNotice>(extraBufferCapacity = 1)
    val notices: SharedFlow<IptvCloudBackupNotice> = _notices.asSharedFlow()

    fun publish(notice: IptvCloudBackupNotice) { _notices.tryEmit(notice) }
}
