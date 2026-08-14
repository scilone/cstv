package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.AutoLoginOutcome
import com.cstv.app.domain.model.IptvRestoreOutcome
import com.cstv.app.domain.repository.AuthRepository
import com.cstv.app.domain.repository.IptvCredentialsBackupRepository
import com.cstv.app.domain.model.AccountExpiredException
import com.cstv.app.domain.model.InvalidCredentialsException
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

class RestoreIptvCredentialsUseCase @Inject constructor(private val backup: IptvCredentialsBackupRepository, private val auth: AuthRepository) {
    suspend operator fun invoke(): AutoLoginOutcome = when (val restored = backup.restore()) {
        IptvRestoreOutcome.Absent -> AutoLoginOutcome.NoCredentials
        IptvRestoreOutcome.Unavailable -> AutoLoginOutcome.Rejected(com.cstv.app.domain.model.AutoLoginRejection.CLOUD_RESTORE_UNAVAILABLE, "")
        IptvRestoreOutcome.Unreadable -> AutoLoginOutcome.Rejected(com.cstv.app.domain.model.AutoLoginRejection.CLOUD_RESTORE_FAILED, "")
        is IptvRestoreOutcome.Restored -> try {
            AutoLoginOutcome.Online(auth.login(restored.credentials).also { auth.saveCredentials(restored.credentials) })
        } catch (e: InvalidCredentialsException) {
            backup.invalidateRestored()
            AutoLoginOutcome.Rejected(com.cstv.app.domain.model.AutoLoginRejection.CLOUD_CREDENTIALS_INVALID, "")
        } catch (e: AccountExpiredException) {
            AutoLoginOutcome.Rejected(com.cstv.app.domain.model.AutoLoginRejection.ACCOUNT_EXPIRED, e.message ?: "Compte expiré.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AutoLoginOutcome.Rejected(com.cstv.app.domain.model.AutoLoginRejection.CLOUD_RESTORE_UNAVAILABLE, "")
        }
    }
}
