package com.romaster.livewallengine.gallery

import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Lista y descarga proyectos desde:
 * https://github.com/Romaster1985/Romaster-Live-Wallpaper-Engine/tree/main/LiveWallpapers
 *
 * Convención: cada proyecto tiene Nombre.zip + Nombre.png con el mismo basename.
 */
object GitHubGalleryRepository {

    private const val OWNER = "Romaster1985"
    private const val REPO = "Romaster-Live-Wallpaper-Engine"
    private const val BRANCH = "main"
    private const val FOLDER = "LiveWallpapers"

    private const val CONTENTS_API =
        "https://api.github.com/repos/$OWNER/$REPO/contents/$FOLDER?ref=$BRANCH"

    private const val USER_AGENT =
        "Romaster-LiveWall-Engine"

    /**
     * Obtiene la lista de proyectos emparejando PNG + ZIP por nombre base.
     * Debe llamarse en un hilo de fondo.
     */
    fun listProjects(): List<GalleryProject> {
        val connection = openGet(CONTENTS_API)
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException(
                    "GitHub API HTTP $code"
                )
            }

            val body = connection.inputStream
                .bufferedReader()
                .use { it.readText() }

            val array = JSONArray(body)

            val pngByBase = mutableMapOf<String, String>()
            val zipByBase = mutableMapOf<String, Pair<String, String>>()

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                if (obj.optString("type") != "file") continue

                val name = obj.getString("name")
                val downloadUrl = obj.optString("download_url")
                if (downloadUrl.isNullOrBlank()) continue

                when {
                    name.endsWith(".png", ignoreCase = true) -> {
                        val base = name.substringBeforeLast(".")
                        pngByBase[base] = downloadUrl
                    }
                    name.endsWith(".zip", ignoreCase = true) -> {
                        val base = name.substringBeforeLast(".")
                        zipByBase[base] = name to downloadUrl
                    }
                }
            }

            return zipByBase.keys
                .sorted()
                .mapNotNull { base ->
                    val zip = zipByBase[base] ?: return@mapNotNull null
                    val preview = pngByBase[base] ?: return@mapNotNull null
                    GalleryProject(
                        name = base,
                        previewUrl = preview,
                        zipUrl = zip.second,
                        zipFileName = zip.first
                    )
                }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Descarga un ZIP a [destinationFile].
     * Debe llamarse en un hilo de fondo.
     */
    fun downloadZip(
        zipUrl: String,
        destinationFile: File
    ) {
        destinationFile.parentFile?.mkdirs()

        val connection = openGet(zipUrl)
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException(
                    "Descarga HTTP $code"
                )
            }

            connection.inputStream.use { input ->
                FileOutputStream(destinationFile).use { output ->
                    input.copyTo(output)
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun openGet(url: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 20_000
        connection.readTimeout = 60_000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.instanceFollowRedirects = true
        return connection
    }
}
