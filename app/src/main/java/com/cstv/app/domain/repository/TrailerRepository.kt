package com.cstv.app.domain.repository

import com.cstv.app.domain.model.TrailerMedia
import com.cstv.app.domain.model.TrailerPreview

interface TrailerRepository {
    /** Retourne null silencieusement quand aucune source conforme n'est disponible. */
    suspend fun getTrailerPreview(media: TrailerMedia): TrailerPreview?

    suspend fun invalidate(media: TrailerMedia)

    /** Appelé lors d'un changement de session Xtream pour ne jamais réutiliser un ancien résultat. */
    fun clearSessionCache()
}
