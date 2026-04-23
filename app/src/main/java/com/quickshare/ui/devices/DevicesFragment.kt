// app/src/main/java/com/quickshare/ui/devices/DevicesFragment.kt
package com.quickshare.ui.devices

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.quickshare.data.model.ConnectionStatus
import com.quickshare.data.model.Device
import com.quickshare.databinding.FragmentDevicesBinding
import com.quickshare.ui.transfer.TransferViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DevicesFragment : Fragment() {

    private var _binding: FragmentDevicesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TransferViewModel by viewModels()
    private lateinit var deviceAdapter: DeviceAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDevicesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupSwipeRefresh()
        observeDevices()

        viewModel.startDiscovery()
    }

    private fun setupRecyclerView() {
        deviceAdapter = DeviceAdapter { device ->
            connectToDevice(device)
        }

        binding.rvDevices.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = deviceAdapter
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refreshDiscovery()
        }
    }

    private fun observeDevices() {
        viewModel.devices.observe(viewLifecycleOwner) { devices ->
            deviceAdapter.submitList(devices)
            binding.tvEmpty.visibility = if (devices.isEmpty()) View.VISIBLE else View.GONE
            binding.rvDevices.visibility = if (devices.isEmpty()) View.GONE else View.VISIBLE
        }

        viewModel.isDiscovering.observe(viewLifecycleOwner) { isDiscovering ->
            binding.swipeRefresh.isRefreshing = isDiscovering
        }

        viewModel.connectionStatus.observe(viewLifecycleOwner) { status ->
            when (status) {
                ConnectionStatus.CONNECTED -> {
                    binding.tvStatus.text = "Connected"
                    if (viewModel.selectedFiles.value?.isNotEmpty() == true) {
                        // Navigate to transfer screen
                        // findNavController().navigate(R.id.action_devices_to_transfer)
                    }
                }
                ConnectionStatus.CONNECTING -> {
                    binding.tvStatus.text = "Connecting..."
                }
                ConnectionStatus.FAILED -> {
                    binding.tvStatus.text = "Connection failed"
                }
                else -> {
                    binding.tvStatus.text = "Available devices nearby"
                }
            }
        }
    }

    private fun connectToDevice(device: Device) {
        viewModel.connectToDevice(device)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.stopDiscovery()
        _binding = null
    }
}
