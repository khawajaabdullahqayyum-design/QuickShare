// app/src/main/java/com/quickshare/ui/transfer/TransferFragment.kt
package com.quickshare.ui.transfer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.quickshare.data.model.TransferItem
import com.quickshare.databinding.FragmentTransferBinding
import com.quickshare.network.transfer.TransferState
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class TransferFragment : Fragment() {

    private var _binding: FragmentTransferBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TransferViewModel by viewModels()
    private lateinit var adapter: TransferProgressAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTransferBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        observeTransferState()

        binding.btnCancel.setOnClickListener {
            // Cancel transfer
        }
    }

    private fun setupRecyclerView() {
        adapter = TransferProgressAdapter()
        binding.rvTransfers.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@TransferFragment.adapter
        }
    }

    private fun observeTransferState() {
        viewModel.transferItems.observe(viewLifecycleOwner) { items ->
            adapter.submitList(items)
            updateOverallProgress(items)
        }

        viewModel.transferState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is TransferState.Transferring -> {
                    binding.tvStatus.text = "Transferring..."
                }
                is TransferState.Completed -> {
                    binding.tvStatus.text = "Transfer completed"
                }
                is TransferState.Failed -> {
                    binding.tvStatus.text = "Transfer failed: ${state.error}"
                }
                else -> {}
            }
        }
    }

    private fun updateOverallProgress(items: List<TransferItem>) {
        if (items.isEmpty()) return

        val totalProgress = items.sumOf { it.progress } / items.size
        binding.progressBar.progress = totalProgress
        binding.tvProgress.text = "$totalProgress%"

        val totalSpeed = items.sumOf { it.speed }
        binding.tvSpeed.text = formatSpeed(totalSpeed)
    }

    private fun formatSpeed(speed: Long): String {
        return when {
            speed < 1024 -> "$speed B/s"
            speed < 1024 * 1024 -> "${speed / 1024} KB/s"
            else -> "${speed / (1024 * 1024)} MB/s"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
