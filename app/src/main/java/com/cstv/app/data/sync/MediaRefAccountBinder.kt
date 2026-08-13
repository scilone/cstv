package com.cstv.app.data.sync

import com.cstv.app.data.local.dao.CategoryRefDao
import com.cstv.app.data.local.dao.MediaRefDao
import javax.inject.Inject

/**
 * T20-6 : rattache les identités héritées de la migration 27→28
 * (`media_refs`/`category_refs` laissés à la sentinelle `accountKey = ''`,
 * le compte Xtream vivant hors de Room au moment du SQL de migration) au
 * premier compte authentifié avec succès.
 *
 * `UPDATE ... WHERE accountKey = ''` est idempotent par construction : un
 * appel après un premier rattachement ne trouve plus aucune ligne à `''` et
 * ne touche donc jamais une identité déjà liée à un compte (le sien ou un
 * autre). Tant qu'aucune authentification n'a eu lieu, les états hérités
 * restent invisibles — même comportement qu'un catalogue pas encore chargé.
 */
class MediaRefAccountBinder @Inject constructor(
    private val mediaRefDao: MediaRefDao,
    private val categoryRefDao: CategoryRefDao,
) {
    suspend fun bindTo(accountKey: String) {
        if (accountKey.isBlank()) return
        mediaRefDao.bindUnassigned(accountKey)
        categoryRefDao.bindUnassigned(accountKey)
    }
}
