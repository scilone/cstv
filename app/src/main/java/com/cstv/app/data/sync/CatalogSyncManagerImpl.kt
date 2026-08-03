package com.cstv.app.data.sync

import android.database.sqlite.SQLiteFullException
import com.cstv.app.data.local.dao.CatalogSyncStateDao
import com.cstv.app.data.local.entity.CatalogSection
import com.cstv.app.data.local.storage.CredentialsManager
import com.cstv.app.data.remote.api.RequestPriority
import com.cstv.app.di.IptvLog
import com.cstv.app.domain.model.AccountExpiredException
import com.cstv.app.domain.model.CategoryType
import com.cstv.app.domain.model.InvalidCredentialsException
import com.cstv.app.domain.model.NetworkTimeoutException
import com.cstv.app.domain.model.ServerUnreachableException
import com.cstv.app.domain.network.NetworkMonitor
import com.cstv.app.domain.repository.CategoryPreferenceRepository
import com.cstv.app.domain.repository.LiveTvRepository
import com.cstv.app.domain.repository.SeriesRepository
import com.cstv.app.domain.repository.VodRepository
import com.cstv.app.domain.sync.CatalogStatus
import com.cstv.app.domain.sync.CatalogSyncManager
import com.cstv.app.domain.sync.SectionSyncOutcome
import com.cstv.app.domain.sync.SyncFailureKind
import com.cstv.app.domain.sync.SyncOutcome
import com.cstv.app.domain.sync.SyncState
import com.cstv.app.domain.sync.SyncTrigger
import com.cstv.app.domain.usecase.ClearCatalogCacheUseCase
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CatalogSyncManagerImpl @Inject constructor(
    private val liveTvRepository: LiveTvRepository,
    private val vodRepository: VodRepository,
    private val seriesRepository: SeriesRepository,
    private val syncStateDao: CatalogSyncStateDao,
    private val credentialsManager: CredentialsManager,
    private val settingsManager: com.cstv.app.data.local.storage.SettingsManager,
    private val networkMonitor: NetworkMonitor,
    private val syncStateInitializer: CatalogSyncStateInitializer,
    private val clearCatalogCacheUseCase: ClearCatalogCacheUseCase,
    private val categoryPreferenceRepository: CategoryPreferenceRepository
) : CatalogSyncManager {

    /**
     * Non réentrant et interrogé par `tryLock()` : une demande concurrente ne
     * se met pas en file d'attente, elle repart avec [SyncOutcome.AlreadyRunning].
     * Le worker tourne dans le process de l'app, donc ce mutex couvre aussi le
     * chemin planifié.
     */
    private val mutex = Mutex()

    @Volatile private var lastReconnectSyncAt = 0L

    // Scope de la durée de vie du singleton : la veille de reconnexion doit
    // survivre à toute navigation, mais reste en priorité arrière-plan.
    private val managerScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO + RequestPriority.background)

    init {
        observeReconnections()
    }

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    override val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    override val catalogStatus: Flow<CatalogStatus> =
        combine(
            syncStateDao.observeAll(),
            networkMonitor.isOnline,
            _syncState
        ) { states, isOnline, currentSync ->
            val byCatalogSection = states.filter { it.section in CatalogSection.CATALOG_SECTIONS }
            val isComplete = CatalogSection.CATALOG_SECTIONS.all { section ->
                byCatalogSection.firstOrNull { it.section == section }?.let { it.lastSuccessAt > 0L } == true
            }
            // La fraîcheur globale est dérivée, jamais stockée : un MIN à 0
            // signifie un catalogue incomplet, donc inéligible au hors-ligne.
            val lastFullSyncAt = if (isComplete) byCatalogSection.minOf { it.lastSuccessAt } else 0L
            val lastFailureKind = byCatalogSection
                .maxByOrNull { it.lastFailureAt }
                ?.takeIf { it.lastFailureAt > 0L }
                ?.lastFailureKind
                ?.let { runCatching { SyncFailureKind.valueOf(it) }.getOrNull() }

            CatalogStatus(
                isComplete = isComplete,
                lastFullSyncAt = lastFullSyncAt,
                isStale = isCatalogStale(lastFullSyncAt, System.currentTimeMillis()),
                // La connectivité seule est optimiste : un transport actif ne
                // garantit pas que le panel réponde.
                isOffline = !isOnline || lastFailureKind == SyncFailureKind.NETWORK,
                // T7-R1 : connectivité réelle, sans l'historique d'échec ci-dessus.
                isNetworkOnline = isOnline,
                isSyncing = currentSync is SyncState.Running,
                lastFailureKind = lastFailureKind
            )
        }.distinctUntilChanged()

    /**
     * Resynchronisation au retour du réseau.
     *
     * Deux garde-fous, parce qu'une connexion instable qui bat produirait
     * exactement le trafic panel que T4 supprime : un débounce de 5 s absorbe
     * le battement, et un plafond d'un déclenchement par fenêtre de 15 min
     * empêche l'accumulation. Le `Mutex` couvre le reste.
     */
    @OptIn(FlowPreview::class)
    private fun observeReconnections() {
        managerScope.launch {
            // Un StateFlow ne réémet déjà que sur changement de valeur :
            // distinctUntilChanged y serait sans effet.
            networkMonitor.isOnline
                .debounce(RECONNECT_DEBOUNCE_MILLIS)
                .collect { isOnline ->
                    if (!isOnline) return@collect
                    val now = System.currentTimeMillis()
                    if (now - lastReconnectSyncAt < RECONNECT_MIN_INTERVAL_MILLIS) return@collect
                    lastReconnectSyncAt = now
                    // syncIfStale() ne part que si le catalogue est périmé : un
                    // simple aller-retour réseau ne redéclenche rien.
                    syncIfStale()
                }
        }
    }

    override suspend fun syncIfStale(): SyncOutcome {
        syncStateInitializer.ensureInitialized()
        if (!networkMonitor.isCurrentlyOnline()) return SyncOutcome.Skipped

        val states = syncStateDao.getAll().associateBy { it.section }
        val now = System.currentTimeMillis()
        val stale = CatalogSection.CATALOG_SECTIONS.any { section ->
            isCatalogStale(states[section]?.lastSuccessAt ?: 0L, now)
        }
        if (!stale) return SyncOutcome.Skipped

        return syncNow(SyncTrigger.STARTUP)
    }

    /**
     * Fraîcheur du catalogue (T7, D1) : le TTL dépend de la fréquence choisie
     * par l'utilisateur (`SettingsManager`), jamais d'une valeur fixe. Une
     * fréquence `DISABLED` n'autorise pas de synchronisation fondée sur l'âge
     * du cache (règle métier 1), mais l'absence totale de catalogue reste un
     * besoin de synchronisation quelle que soit la fréquence (règle métier 3).
     * Partagée par [catalogStatus] et [syncIfStale] pour qu'ils ne divergent
     * jamais (règle métier 2).
     */
    private fun isCatalogStale(lastSuccessAt: Long, now: Long): Boolean {
        if (lastSuccessAt <= 0L) return true
        val ttlMillis = CacheTtl.catalogMillisFor(settingsManager.getSyncFrequency()) ?: return false
        return CacheTtl.isExpired(lastSuccessAt, ttlMillis, now)
    }

    override suspend fun syncNow(trigger: SyncTrigger): SyncOutcome {
        if (!mutex.tryLock()) return SyncOutcome.AlreadyRunning
        return try {
            runSync()
        } finally {
            mutex.unlock()
        }
    }

    /**
     * Tout le corps s'exécute sous [RequestPriority.background] : la
     * synchronisation cède devant toute navigation utilisateur, et le cooldown
     * 403/429 de `XtreamRequestGate` s'applique.
     */
    private suspend fun runSync(): SyncOutcome = withContext(RequestPriority.background) {
        syncStateInitializer.ensureInitialized()

        val credentials = credentialsManager.getCredentials()
            ?: return@withContext SyncOutcome.Skipped
        val accountKey = CatalogServerKey.from(credentials)

        // Catégories avant flux pour chaque type : une catégorie manquante rend
        // ses flux invisibles. Live → VOD → Séries, enrichissement en dernier.
        val sections: List<Pair<String, suspend () -> Int>> = listOf(
            CatalogSection.LIVE_CATEGORIES to {
                val categories = liveTvRepository.syncLiveCategories()
                logOrphanedHiddenCategories(CategoryType.LIVE, categories.map { it.categoryId })
                categories.size
            },
            CatalogSection.LIVE_STREAMS to { liveTvRepository.syncLiveStreams().size },
            CatalogSection.VOD_CATEGORIES to {
                val categories = vodRepository.syncVodCategories()
                logOrphanedHiddenCategories(CategoryType.VOD, categories.map { it.categoryId })
                categories.size
            },
            CatalogSection.VOD_STREAMS to { vodRepository.syncVodStreams().size },
            CatalogSection.SERIES_CATEGORIES to {
                val categories = seriesRepository.syncSeriesCategories()
                logOrphanedHiddenCategories(CategoryType.SERIES, categories.map { it.categoryId })
                categories.size
            },
            CatalogSection.SERIES_STREAMS to { seriesRepository.syncSeriesStreams().size }
        )

        var firstFailure: SyncFailureKind? = null

        sections.forEachIndexed { index, (section, action) ->
            _syncState.value = SyncState.Running(section, index, sections.size)
            when (val outcome = runSection(action)) {
                is SectionSyncOutcome.Success -> {
                    syncStateDao.markSuccess(section, accountKey, System.currentTimeMillis(), outcome.itemCount)
                }
                is SectionSyncOutcome.Failure -> {
                    // lastSuccessAt reste inchangé : une synchronisation en échec
                    // ne renouvelle pas la date de fraîcheur.
                    syncStateDao.markFailure(section, accountKey, System.currentTimeMillis(), outcome.kind.name)
                    if (firstFailure == null) firstFailure = outcome.kind
                    if (outcome.kind == SyncFailureKind.AUTH) {
                        // Seul cas d'arrêt immédiat : continuer avec des
                        // identifiants rejetés ne produit que du trafic refusé
                        // et rapproche du bannissement.
                        val at = System.currentTimeMillis()
                        _syncState.value = SyncState.Failed(SyncFailureKind.AUTH, at)
                        return@withContext SyncOutcome.Failure(SyncFailureKind.AUTH, at)
                    }
                }
            }
        }

        // Enrichissement en dernier et borné : les sections catalogue sont déjà
        // committées quand il démarre, une fenêtre de worker tuée ne les perd pas.
        _syncState.value = SyncState.Running(CatalogSection.ENRICHMENT, sections.size, sections.size + 1)
        when (val outcome = runSection { vodRepository.enrichPendingMovies() + seriesRepository.enrichPendingSeries() }) {
            is SectionSyncOutcome.Success ->
                syncStateDao.markSuccess(CatalogSection.ENRICHMENT, accountKey, System.currentTimeMillis(), outcome.itemCount)
            is SectionSyncOutcome.Failure ->
                syncStateDao.markFailure(CatalogSection.ENRICHMENT, accountKey, System.currentTimeMillis(), outcome.kind.name)
        }

        runCatching { liveTvRepository.purgeExpiredEpg() }

        val at = System.currentTimeMillis()
        val failure = firstFailure
        if (failure != null) {
            _syncState.value = SyncState.Failed(failure, at)
            SyncOutcome.Failure(failure, at)
        } else {
            _syncState.value = SyncState.Success(at)
            SyncOutcome.Success(at)
        }
    }

    private suspend fun runSection(action: suspend () -> Int): SectionSyncOutcome =
        try {
            val count = action()
            // Un panel qui répond une liste vide n'est pas un catalogue vide
            // légitime : le remplacement atomique l'a déjà refusé côté DAO, on
            // le compte ici comme un échec panel plutôt que comme un succès.
            if (count == 0) {
                SectionSyncOutcome.Failure(SyncFailureKind.PANEL)
            } else {
                SectionSyncOutcome.Success(count)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SectionSyncOutcome.Failure(classify(e), e)
        }

    /**
     * Diagnostic pour une régression déjà observée : des catégories masquées
     * réapparaissent après une synchronisation. Le masquage est stocké sur le
     * `categoryId` renvoyé par le panel (jamais recalculé côté app) ; si ce
     * même `categoryId` est absent du catalogue fraîchement synchronisé, sa
     * préférence devient orpheline et la catégorie reste visible sans qu'aucun
     * code local ne soit en cause. Purement informatif, ne modifie rien.
     */
    private suspend fun logOrphanedHiddenCategories(type: CategoryType, currentCategoryIds: List<String>) {
        runCatching {
            val hiddenIds = categoryPreferenceRepository.getPreferences(type)
                .filterValues { it.hidden }
                .keys
            val orphaned = hiddenIds - currentCategoryIds.toSet()
            if (orphaned.isNotEmpty()) {
                IptvLog.w(
                    "CATSYNC",
                    "Catégories masquées absentes après sync ${type.value} : $orphaned " +
                        "(préférence orpheline, categoryId probablement réattribué côté panel)"
                )
            }
        }
    }

    private fun classify(e: Throwable): SyncFailureKind = when (e) {
        is InvalidCredentialsException, is AccountExpiredException -> SyncFailureKind.AUTH
        is SQLiteFullException -> SyncFailureKind.STORAGE
        is JsonSyntaxException -> SyncFailureKind.PARSE
        is HttpException -> SyncFailureKind.PANEL
        is SocketTimeoutException, is NetworkTimeoutException, is ServerUnreachableException -> SyncFailureKind.NETWORK
        is IOException -> SyncFailureKind.NETWORK
        else -> e.cause?.let { if (it !== e) classify(it) else null } ?: SyncFailureKind.UNKNOWN
    }

    /**
     * Seul déclencheur de purge du catalogue : une déconnexion n'en provoque
     * aucune. Le contrôle est fait ici, à la connexion, parce que c'est le seul
     * moment où un catalogue risque d'être exposé à un autre compte.
     */
    override suspend fun onAccountAuthenticated(accountKey: String): Boolean {
        // Volontairement sans ensureInitialized() : la reprise des horodatages
        // historiques appartient au démarrage, où les identifiants en place sont
        // encore ceux du dernier compte synchronisé. L'appeler ici stamperait la
        // clé du compte qui vient de se connecter sur le catalogue du précédent.
        val storedKey = syncStateDao.getAccountKey()

        return when {
            storedKey == accountKey -> false
            // Clé absente : soit première installation (tables vides), soit
            // catalogue d'origine inconnue. Les identifiants mémorisés ne
            // suffisent pas à prouver son serveur d'origine.
            storedKey == null -> {
                clearCatalogCacheUseCase()
                clearLegacySyncStamps()
                false
            }
            // Les installations créées avant la règle "catalogue par serveur"
            // stockaient une clé host:port:username opaque. Les identifiants
            // encore mémorisés permettent de reconnaître sans ambiguïté le
            // même serveur et de migrer cette clé sans perdre le cache.
            credentialsManager.getCredentials()?.let(CatalogServerKey::from) == accountKey -> {
                syncStateDao.updateAccountKey(accountKey)
                false
            }
            else -> {
                clearCatalogCacheUseCase()
                clearLegacySyncStamps()
                true
            }
        }
    }

    /**
     * Les horodatages `SharedPreferences` d'avant T4 survivraient à la purge et
     * l'initialiseur les reprendrait, faisant passer un catalogue vide pour un
     * catalogue frais.
     */
    private fun clearLegacySyncStamps() {
        settingsManager.setLiveAllStreamsSyncedAt(0L)
        settingsManager.setVodAllStreamsSyncedAt(0L)
        settingsManager.setSeriesAllStreamsSyncedAt(0L)
    }

    companion object {
        private const val RECONNECT_DEBOUNCE_MILLIS = 5_000L
        private const val RECONNECT_MIN_INTERVAL_MILLIS = 15 * 60 * 1000L
    }
}
