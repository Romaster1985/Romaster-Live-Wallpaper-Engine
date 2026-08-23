package com.romaster.livewallengine.gallery

/**
 * Proyecto publicado en la carpeta LiveWallpapers/ del repositorio.
 * El nombre mostrado es el basename del PNG/ZIP (sin extensión).
 */
data class GalleryProject(
    val name: String,
    val previewUrl: String,
    val zipUrl: String,
    val zipFileName: String
)
