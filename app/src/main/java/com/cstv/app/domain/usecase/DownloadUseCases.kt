package com.cstv.app.domain.usecase

import com.cstv.app.data.repository.ContentClassificationRepository
import com.cstv.app.data.repository.MediaClassificationKind
import com.cstv.app.domain.model.AccessDecision
import com.cstv.app.domain.model.AgeRating
import com.cstv.app.domain.model.BlockReason
import com.cstv.app.domain.model.DownloadRequestData
import com.cstv.app.domain.model.DownloadedItem
import com.cstv.app.domain.model.ParentalAccessPolicy
import com.cstv.app.domain.model.ParentalActionType
import com.cstv.app.domain.repository.DownloadRepository
import com.cstv.app.domain.repository.ProfileRepository
import com.cstv.app.domain.repository.SeriesRepository
import com.cstv.app.domain.repository.VodRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveDownloadsUseCase @Inject constructor(
    private val repository: DownloadRepository
) {
    operator fun invoke(): Flow<List<DownloadedItem>> = repository.observeDownloads()
}

/** Résultat d'une demande de téléchargement (F44, §8.4/§8.7). */
sealed interface DownloadStartResult {
    data object Started : DownloadStartResult

    /**
     * Profil bridé, classification au-dessus du niveau autorisé (ou
     * inconnue — refus défensif, §8.4). Aucune file d'attente : la demande
     * doit être rejouée explicitement, elle n'est jamais réévaluée plus tard.
     */
    data class RequiresParentalPin(val reason: BlockReason, val mediaUid: String) : DownloadStartResult
}

/**
 * F44 : seul point d'entrée qui crée effectivement un `DownloadRequest` — la
 * garde parentale s'applique ici, jamais seulement dans l'écran qui la
 * déclenche (§8.4). Un téléchargement déjà présent sur l'appareil n'est pas
 * revalidé (§8.7, écart assumé) : cette garde ne s'applique qu'à la création
 * d'une nouvelle demande.
 */
class StartDownloadUseCase @Inject constructor(
    private val repository: DownloadRepository,
    private val profileRepository: ProfileRepository,
    private val vodRepository: VodRepository,
    private val seriesRepository: SeriesRepository,
    private val classificationRepository: ContentClassificationRepository,
    private val parentalAccessPolicy: ParentalAccessPolicy,
) {
    suspend operator fun invoke(data: DownloadRequestData): DownloadStartResult {
        evaluateParentalAccess(data)?.let { return it }
        repository.startDownload(data)
        return DownloadStartResult.Started
    }

    /**
     * `null` = autorisé. Un profil non bridé ne déclenche jamais
     * [ContentClassificationRepository] (§8.2).
     */
    private suspend fun evaluateParentalAccess(data: DownloadRequestData): DownloadStartResult.RequiresParentalPin? {
        val activeProfile = profileRepository.getProfiles().firstOrNull { it.id == profileRepository.currentProfileId() }
        val maxAgeRating = AgeRating.fromValueOrNull(activeProfile?.maxAgeRating) ?: return null

        val target = resolveClassificationTarget(data)
            ?: return DownloadStartResult.RequiresParentalPin(BlockReason.UNCLASSIFIED, data.contentId)
        val classification = classificationRepository.classificationFor(target.kind, target.title, target.year, target.providerId)
            ?: classificationRepository.classificationFor(target.kind, target.title, target.year)

        val decision = parentalAccessPolicy.evaluate(maxAgeRating, classification, ParentalActionType.DOWNLOAD)
        return (decision as? AccessDecision.PinRequired)?.let {
            DownloadStartResult.RequiresParentalPin(it.reason, data.contentId)
        }
    }

    private data class ClassificationTarget(val kind: MediaClassificationKind, val title: String, val year: Int?, val providerId: Int)

    /** Classification de la série entière pour un épisode (décision F44 étape 1). */
    private suspend fun resolveClassificationTarget(data: DownloadRequestData): ClassificationTarget? {
        val seriesId = data.seriesId
        return if (seriesId != null) {
            seriesRepository.getStreamById(seriesId)
                ?.let { ClassificationTarget(MediaClassificationKind.SERIES, it.cleanTitle.ifBlank { it.name }, it.releaseYear, it.seriesId) }
        } else {
            vodRepository.getStreamById(data.streamId)
                ?.let { ClassificationTarget(MediaClassificationKind.MOVIE, it.cleanTitle.ifBlank { it.name }, it.releaseYear, it.streamId) }
        }
    }
}

class RemoveDownloadUseCase @Inject constructor(
    private val repository: DownloadRepository
) {
    suspend operator fun invoke(contentId: String) = repository.removeDownload(contentId)
}

class GetDownloadsUsedBytesUseCase @Inject constructor(
    private val repository: DownloadRepository
) {
    suspend operator fun invoke(): Long = repository.getUsedBytes()
}
