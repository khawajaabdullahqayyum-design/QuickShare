// app/src/main/java/com/quickshare/ui/transfer/TransferProgressAdapter.kt
package com.quickshare.ui.transfer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.quickshare.R
import com.quickshare.data.model.TransferItem
import com.quickshare.data.model.TransferStatus
import com.quickshare.databinding.ItemTransferProgressBinding

class TransferProgressAdapter :
    ListAdapter<TransferItem, TransferProgressAdapter.ViewHolder>(TransferItemDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTransferProgressBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemTransferProgressBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: TransferItem) {
            binding.tvFileName.text = item.fileName
            binding.tvFileSize.text = formatSize(item.fileSize)
            binding.progressBar.progress = item.progress
            binding.tvProgress.text = "${item.progress}%"
            binding.tvSpeed.text = item.formattedSpeed

            val statusIcon = when (item.status) {
                TransferStatus.COMPLETED -> R.drawable.ic_success
                TransferStatus.FAILED -> R.drawable.ic_error
                TransferStatus.TRANSFERRING -> R.drawable.ic_transfer
                else -> R.drawable.ic_pending
            }
            binding.ivStatus.setImageResource(statusIcon)
        }

        private fun formatSize(size: Long): String {
            return when {
                size < 1024 -> "$size B"
                size < 1024 * 1024 -> "${size / 1024} KB"
                size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)} MB"
                else -> "${size / (1024 * 1024 * 1024)} GB"
            }
        }
    }

    class TransferItemDiffCallback : DiffUtil.ItemCallback<TransferItem>() {
        override fun areItemsTheSame(oldItem: TransferItem, newItem: TransferItem): Boolean {
            return oldItem.fileId == newItem.fileId
        }

        override fun areContentsTheSame(oldItem: TransferItem, newItem: TransferItem): Boolean {
            return oldItem == newItem
        }
    }
}