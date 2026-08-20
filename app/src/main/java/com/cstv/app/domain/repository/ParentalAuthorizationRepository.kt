package com.cstv.app.domain.repository

import com.cstv.app.data.repository.MediaClassificationKind

/**
 * F45 (évolution F44) : autorisation permanente d'un média pour un profil
 * bridé, accordée par PIN et synchronisée dans le cloud comme le reste des
 * réglages du profil (contrairement au PIN lui-même, jamais synchronisé).
 */
interface ParentalAuthorizationRepository {
    suspend fun isAuthorized(profileId: Int, kind: MediaClassificationKind, providerId: Int): Boolean
    suspend fun authorize(profileId: Int, kind: MediaClassificationKind, providerId: Int)
}
