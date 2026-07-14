package com.poc.iptvxtream.domain.model

data class Profile(
    val id: Int,
    val name: String,
    val avatarId: Int,
    val createdAt: Long
)

/**
 * Palette d'avatars locaux (couleur ARGB) proposée à la création/édition d'un
 * profil. `avatarId` d'un [Profile] est un index dans cette liste ; on borne
 * défensivement à l'affichage au cas où un index deviendrait invalide.
 */
object ProfileAvatars {
    val colors: List<Long> = listOf(
        0xFFE57373L, // rouge
        0xFF64B5F6L, // bleu
        0xFF81C784L, // vert
        0xFFFFB74DL, // orange
        0xFFBA68C8L, // violet
        0xFF4DD0E1L, // cyan
        0xFFF06292L, // rose
        0xFFA1887FL  // brun
    )

    fun colorFor(avatarId: Int): Long = colors[avatarId.coerceIn(0, colors.lastIndex)]

    val count: Int get() = colors.size
}
