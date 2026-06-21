package com.romaster.livewallengine.storage

import android.content.Context
import android.net.Uri
import com.romaster.livewallengine.project.ProjectManager
import java.io.File
import java.util.zip.ZipInputStream

object ProjectImporter {

    fun import(

        context: Context,

        uri: Uri

    ) {

        context.contentResolver
            .openInputStream(uri)
            ?.use { input ->

                ZipInputStream(input).use { zip ->

                    var entry =
                        zip.nextEntry

                    while (entry != null) {

                        val outFile = when {

                            entry.name == "project.json" ->

                                File(
                                    AppDirectories.projects(context),
                                    "current_project.json"
                                )

                            entry.name.startsWith("videos/") ->

                                File(
                                    context.filesDir,
                                    entry.name
                                )

                            entry.name.startsWith("audio/") ->

                                File(
                                    context.filesDir,
                                    entry.name
                                )

                            entry.name.startsWith("fonts/") ->

                                File(
                                    context.filesDir,
                                    entry.name
                                )

                            else -> null

                        }

                        if (

                            outFile != null &&
                            !entry.isDirectory

                        ) {

                            outFile.parentFile?.mkdirs()

                            outFile.outputStream().use {

                                zip.copyTo(it)

                            }

                        }

                        zip.closeEntry()

                        entry =
                            zip.nextEntry

                    }

                }

            }

        val project =
            StorageManager.loadProject(context)
                ?: return

        ProjectManager.setProject(project)

    }

}