package com.romaster.livewallengine.storage

import com.romaster.livewallengine.model.WallpaperProject
import kotlinx.serialization.json.Json

object ProjectSerializer {

    private val json = Json {

        prettyPrint = true

        ignoreUnknownKeys = true
    }

    fun encode(
        project: WallpaperProject
    ): String {

        return json.encodeToString(
            WallpaperProject.serializer(),
            project
        )
    }

    fun decode(
        text: String
    ): WallpaperProject {

        return json.decodeFromString(
            WallpaperProject.serializer(),
            text
        )
    }
}