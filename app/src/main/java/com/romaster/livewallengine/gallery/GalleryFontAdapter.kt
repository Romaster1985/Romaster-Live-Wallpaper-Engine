/*
 * Copyright 2026 Román Ignacio Romero (Romaster)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Nota: Este proyecto incluye ColorPickerView (skydoves) licenciado bajo Apache 2.0.
 */

package com.romaster.livewallengine.gallery

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.romaster.livewallengine.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class GalleryFontAdapter(
    private val scope: CoroutineScope,
    private val cacheDir: File,
    private val onClick: (GalleryFont) -> Unit
) : RecyclerView.Adapter<GalleryFontAdapter.VH>() {

    private val items = mutableListOf<GalleryFont>()
    private val jobs = mutableMapOf<Int, Job>()

    fun submit(list: List<GalleryFont>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_gallery_font, parent, false)
        return VH(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val font = items[position]
        holder.name.text = font.name
        holder.preview.typeface = Typeface.DEFAULT
        holder.preview.text = SAMPLE
        holder.progress.visibility = View.VISIBLE
        holder.itemView.setOnClickListener { onClick(font) }

        jobs[position]?.cancel()
        jobs[position] = scope.launch {
            try {
                val local = File(cacheDir, font.fileName)
                if (!local.exists() || local.length() == 0L) {
                    withContext(Dispatchers.IO) {
                        GitHubGalleryRepository.downloadFile(
                            font.downloadUrl,
                            local
                        )
                    }
                }
                val tf = withContext(Dispatchers.IO) {
                    try {
                        Typeface.createFromFile(local)
                    } catch (_: Exception) {
                        null
                    }
                }
                if (holder.bindingAdapterPosition == position) {
                    holder.progress.visibility = View.GONE
                    if (tf != null) {
                        holder.preview.typeface = tf
                    }
                }
            } catch (_: Exception) {
                if (holder.bindingAdapterPosition == position) {
                    holder.progress.visibility = View.GONE
                }
            }
        }
    }

    override fun onViewRecycled(holder: VH) {
        val pos = holder.bindingAdapterPosition
        if (pos != RecyclerView.NO_POSITION) {
            jobs[pos]?.cancel()
        }
        super.onViewRecycled(holder)
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val preview: TextView = view.findViewById(R.id.textFontPreview)
        val name: TextView = view.findViewById(R.id.textFontName)
        val progress: ProgressBar = view.findViewById(R.id.progressFontPreview)
    }

    companion object {
        const val SAMPLE = "Aa Bb Cc 0123\n!?@#\$%&*"
    }
}
