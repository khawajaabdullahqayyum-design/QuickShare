// app/src/main/java/com/quickshare/ui/files/FileAdapter.kt
package com.quickshare.ui.files

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.quickshare.R
import com.quickshare.data.model.FileItem
import com.quickshare.data.model.FileType
import com.quickshare.databinding.ItemFileBinding

class FileAdapter(
    private val onItemClick: (FileItem) -> Unit
) : ListAdapter<FileItem, FileAdapter.FileViewHolder>(FileDiffCallback()) {

    private val selectedItems = mutableSetOf<FileItem>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val binding = ItemFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FileViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val file = getItem(position)
        val isSelected = selectedItems.contains(file)
        holder.bind(file, isSelected)
    }

    fun setSelectedItems(items: Set<FileItem>) {
        selectedItems.clear()
        selectedItems.addAll(items)
        notifyDataSetChanged()
    }

    inner class FileViewHolder(
        private val binding: ItemFileBinding,
        private val onItemClick: (FileItem) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(file: FileItem, isSelected: Boolean) {
            binding.tvFileName.text = file.name
            binding.tvFileSize.text = file.formattedSize
            binding.checkboxSelected.visibility = if (isSelected) android.view.View.VISIBLE else android.view.View.GONE

            // Load thumbnail
            when (file.type) {
                FileType.IMAGE -> {
                    Glide.with(binding.root)
                        .load(file.uri)
                        .placeholder(R.drawable.ic_image)
                        .into(binding.ivThumbnail)
                }
                FileType.VIDEO -> {
                    Glide.with(binding.root)
                        .load(file.uri)
                        .placeholder(R.drawable.ic_video)
                        .into(binding.ivThumbnail)
                }
                FileType.DOCUMENT -> binding.ivThumbnail.setImageResource(R.drawable.ic_document)
                FileType.APK -> binding.ivThumbnail.setImageResource(R.drawable.ic_apk)
                else -> binding.ivThumbnail.setImageResource(R.drawable.ic_file)
            }

            binding.root.setOnClickListener {
                onItemClick(file)
            }
        }
    }

    class FileDiffCallback : DiffUtil.ItemCallback<FileItem>() {
        override fun areItemsTheSame(oldItem: FileItem, newItem: FileItem): Boolean {
            return oldItem.uri == newItem.uri
        }

        override fun areContentsTheSame(oldItem: FileItem, newItem: FileItem): Boolean {
            return oldItem == newItem
        }
    }
}