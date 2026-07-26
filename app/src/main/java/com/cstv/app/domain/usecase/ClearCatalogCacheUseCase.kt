package com.cstv.app.domain.usecase

import androidx.room.withTransaction
import com.cstv.app.data.local.dao.CatalogSyncStateDao
import com.cstv.app.data.local.dao.LiveTvDao
import com.cstv.app.data.local.dao.SeriesDao
import com.cstv.app.data.local.dao.VodDao
import com.cstv.app.data.local.db.AppDatabase
import javax.inject.Inject

/**
 * Purge du catalogue local, réservée au **changement de compte à la connexion**.
 *
 * Ce n'est pas le pendant de la déconnexion : se déconnecter puis se reconnecter
 * au même compte doit retrouver l'application immédiatement utilisable. Le
 * risque visé — exposer un catalogue à un autre utilisateur de l'appareil — ne
 * se matérialise qu'au moment où un **autre** compte se connecte.
 *
 * Ne sont **pas** supprimés : profils, favoris, historique, positions de
 * lecture, préférences, notes et téléchargements.
 */
// `open` : la transaction Room ne peut pas s'exécuter contre une base mockée,
// et le projet n'a pas `mockito-inline` (voir AGENTS.md). Ouvrir la classe est
// le moyen le plus direct de doubler la purge dans les tests du
// `CatalogSyncManager`, qui vérifient *quand* elle est déclenchée, pas ce
// qu'elle efface — ce dernier point relevant de son propre test.
open class ClearCatalogCacheUseCase @Inject constructor(
    private val database: AppDatabase,
    private val liveTvDao: LiveTvDao,
    private val vodDao: VodDao,
    private val seriesDao: SeriesDao,
    private val syncStateDao: CatalogSyncStateDao
) {
    open suspend operator fun invoke() {
        database.withTransaction {
            liveTvDao.clearCategories()
            liveTvDao.clearAllStreams()
            liveTvDao.clearAllFts()
            liveTvDao.clearEpgCache()

            vodDao.clearCategories()
            vodDao.clearAllStreams()
            vodDao.clearAllFts()

            seriesDao.clearCategories()
            seriesDao.clearAllStreams()
            seriesDao.clearAllFts()
            seriesDao.clearAllSeasons()
            seriesDao.clearAllEpisodes()

            syncStateDao.clear()
        }
    }
}
