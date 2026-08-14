package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.IptvBackupOutcome
import com.cstv.app.domain.repository.IptvCredentialsBackupRepository
import javax.inject.Inject

class SetIptvCloudBackupConsentUseCase @Inject constructor(private val backup: IptvCredentialsBackupRepository) {
    suspend operator fun invoke(enabled: Boolean): IptvBackupOutcome = backup.setConsent(enabled)
}
