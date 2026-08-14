package com.cstv.app.domain.usecase

import com.cstv.app.domain.repository.CstvAuthRepository
import com.cstv.app.domain.repository.IptvCredentialsBackupRepository
import javax.inject.Inject

class SignOutCstvUseCase @Inject constructor(private val backup: IptvCredentialsBackupRepository, private val cstv: CstvAuthRepository) {
    suspend operator fun invoke() { backup.deleteForCstvSignOut(); cstv.signOut() }
}
