package com.cstv.app.data.sync

import com.cstv.app.data.local.dao.DownloadDao
import com.cstv.app.data.local.dao.MediaRefDao
import com.cstv.app.data.local.dao.OrphanedDownloadRow
import com.cstv.app.data.local.dao.TrailerCacheDao
import com.cstv.app.di.IptvLog
import com.cstv.app.domain.model.DownloadContentId
import com.cstv.app.domain.model.MediaKind
import javax.inject.Inject

/**
 * Indirection sur `DownloadService.sendRemoveDownload` (méthode statique du
 * framework media3, non simulable en JVM sans Robolectric, absent du
 * projet) : la production démarre réellement le service, les tests
 * fournissent un double contrôlable pour exercer l'ordre marquer/retirer/
 * supprimer et le chemin d'échec sans toucher Android.
 */
fun interface DownloadContentRemover {
    fun remove(contentId: String)
}

/**
 * T20 §4.5 : nettoyage applicatif déclenché seulement après un cycle
 * catalogue complet et réussi (les six `CatalogSection.CATALOG_SECTIONS` en
 * succès pour le compte courant, cf. `CatalogSyncManagerImpl`) — une réponse
 * partielle de panel ne doit jamais déclencher une destruction de fichiers.
 *
 * Deux nettoyages indépendants :
 * - `trailer_cache` : lignes sans média catalogue correspondant. Cache
 *   global, non lié à un `accountKey` (clé `mediaType`/`catalogId`, cf.
 *   [TrailerCacheDao.deleteOrphaned]).
 * - Téléchargements orphelins du compte courant : le média a disparu du
 *   catalogue mais son identité `media_refs` reste référencée par
 *   `downloaded_media`, donc `ON DELETE CASCADE` ne se déclenche jamais pour
 *   un média simplement absent du bouquet — le nettoyage doit rester
 *   applicatif, dans l'ordre imposé : marquer `ORPHANED` (commit Room),
 *   retirer le contenu media3, puis supprimer la ligne. Un échec laisse la
 *   ligne en `ORPHANED` : invisible à l'écran Téléchargements, reprise au
 *   prochain cycle, jamais annoncée nettoyée alors qu'elle ne l'est pas.
 */
class CatalogReconciler @Inject constructor(
    private val downloadDao: DownloadDao,
    private val trailerCacheDao: TrailerCacheDao,
    private val mediaRefDao: MediaRefDao,
    private val downloadContentRemover: DownloadContentRemover,
) {
    suspend fun reconcile(accountKey: String) {
        runCatching { trailerCacheDao.deleteOrphaned() }
            .onFailure { IptvLog.e("RECONCILE", "Purge trailer_cache orpheline impossible", it) }

        val orphaned = runCatching { downloadDao.findOrphaned(accountKey) }.getOrElse {
            IptvLog.e("RECONCILE", "Recherche des téléchargements orphelins impossible", it)
            return
        }
        if (orphaned.isEmpty()) return

        orphaned.forEach { reconcileOne(it) }
        // Purge finale : un média mort n'a plus aucune raison de conserver son
        // identité une fois son dernier état (le téléchargement) supprimé.
        runCatching { mediaRefDao.purgeUnreferenced() }
    }

    private suspend fun reconcileOne(download: OrphanedDownloadRow) {
        val kind = MediaKind.fromStorage(download.kind)
        if (kind != MediaKind.MOVIE && kind != MediaKind.EPISODE) {
            // Identité mal formée (ne devrait pas arriver) : ni supprimable en
            // confiance ni exploitable pour retirer un contenu media3.
            IptvLog.e("RECONCILE", "Téléchargement orphelin ${download.mediaUid} de kind inattendu '${download.kind}', ignoré", null)
            return
        }
        runCatching {
            downloadDao.markOrphaned(download.mediaUid)
            downloadContentRemover.remove(DownloadContentId.of(kind, download.providerId))
            downloadDao.deleteByMediaUid(download.mediaUid)
        }.onFailure {
            IptvLog.e(
                "RECONCILE",
                "Suppression du téléchargement orphelin ${download.mediaUid} incomplète, reprise au prochain cycle",
                it
            )
        }
    }
}
