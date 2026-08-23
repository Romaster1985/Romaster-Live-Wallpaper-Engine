package com.romaster.livewallengine.gallery

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.romaster.livewallengine.R
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class GalleryProjectAdapter(
    private val onClick: (GalleryProject) -> Unit
) : RecyclerView.Adapter<GalleryProjectAdapter.Holder>() {

    private val items = mutableListOf<GalleryProject>()
    private val executor = Executors.newFixedThreadPool(3)

    fun submit(list: List<GalleryProject>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_gallery_project, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position], onClick, executor)
    }

    override fun getItemCount(): Int = items.size

    class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val image: ImageView = itemView.findViewById(R.id.imageGalleryPreview)
        private val title: TextView = itemView.findViewById(R.id.textGalleryName)

        fun bind(
            project: GalleryProject,
            onClick: (GalleryProject) -> Unit,
            executor: java.util.concurrent.ExecutorService
        ) {
            title.text = project.name
            image.setImageDrawable(null)
            image.tag = project.previewUrl

            itemView.setOnClickListener { onClick(project) }

            executor.execute {
                try {
                    val connection =
                        URL(project.previewUrl).openConnection() as HttpURLConnection
                    connection.connectTimeout = 15_000
                    connection.readTimeout = 20_000
                    connection.instanceFollowRedirects = true
                    connection.setRequestProperty(
                        "User-Agent",
                        "Romaster-LiveWall-Engine"
                    )

                    connection.inputStream.use { stream ->
                        val bitmap = BitmapFactory.decodeStream(stream)
                        image.post {
                            if (image.tag == project.previewUrl && bitmap != null) {
                                image.setImageBitmap(bitmap)
                            }
                        }
                    }
                    connection.disconnect()
                } catch (_: Exception) {
                    // Se deja el placeholder de fondo
                }
            }
        }
    }
}
