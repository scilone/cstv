package com.poc.iptvxtream.domain.model

/**
 * Type de média de la recherche avancée : choix **exclusif** Film ou Série.
 * `null` (côté filtre) = aucun type encore choisi → périmètre = les deux
 * catalogues (voir AdvancedCatalogSearchUseCase).
 *
 * Distinct de [MediaType] (préférences de piste audio/sous-titres, persisté),
 * volontairement non réutilisé pour ne pas coupler deux concepts différents.
 */
enum class SearchMediaType { FILM, SERIE }
