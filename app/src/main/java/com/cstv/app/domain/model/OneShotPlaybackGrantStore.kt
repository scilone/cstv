package com.cstv.app.domain.model

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

internal data class OneShotPlaybackGrant(val profileId: Int, val mediaUid: String, val requestNonce: String)

/**
 * Grants de déverrouillage ponctuel (F44, §8.4), en mémoire uniquement —
 * jamais persistés, perdus au redémarrage du process. Un PIN valide pour
 * `PLAY` en émet un, lié à `profileId + mediaUid + requestNonce` ; il est
 * consommé (et détruit) au lancement effectif de cette lecture précise et
 * n'autorise ni une seconde lecture du même média, ni un autre média, ni un
 * téléchargement.
 */
@Singleton
class OneShotPlaybackGrantStore @Inject constructor() {
    // LinkedHashMap conserve l'ordre d'émission pour évacuer les grants
    // abandonnés lorsque le parent annule avant le lancement du lecteur.
    private val grants = LinkedHashMap<OneShotPlaybackGrant, Unit>()

    /** Émet un nonce unique liant ce grant à `profileId + mediaUid`. */
    @Synchronized
    fun issue(profileId: Int, mediaUid: String): String {
        val nonce = UUID.randomUUID().toString()
        if (grants.size >= MAX_GRANTS) {
            grants.remove(grants.entries.first().key)
        }
        grants[OneShotPlaybackGrant(profileId, mediaUid, nonce)] = Unit
        return nonce
    }

    /**
     * Consomme le grant s'il existe et correspond exactement aux trois
     * champs ; `true` une seule fois par grant émis.
     */
    @Synchronized
    fun consume(profileId: Int, mediaUid: String, requestNonce: String): Boolean =
        grants.remove(OneShotPlaybackGrant(profileId, mediaUid, requestNonce)) != null

    private companion object {
        private const val MAX_GRANTS = 256
    }
}
