// app/src/main/java/com/quickshare/ui/home/HomeFragment.kt
package com.quickshare.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.quickshare.R
import com.quickshare.databinding.FragmentHomeBinding
import com.quickshare.service.TransferService
import com.quickshare.ui.common.PermissionHelper
import com.quickshare.utils.Constants
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupClickListeners()
        checkPermissions()
    }

    private fun setupClickListeners() {
        binding.btnSend.setOnClickListener {
            if (PermissionHelper.hasAllPermissions(requireContext())) {
                findNavController().navigate(R.id.action_home_to_files)
            } else {
                PermissionHelper.requestPermissions(this, REQUEST_CODE_PERMISSIONS)
            }
        }

        binding.btnReceive.setOnClickListener {
            if (PermissionHelper.hasAllPermissions(requireContext())) {
                startReceivingMode()
            } else {
                PermissionHelper.requestPermissions(this, REQUEST_CODE_PERMISSIONS)
            }
        }
    }

    private fun checkPermissions() {
        if (!PermissionHelper.hasAllPermissions(requireContext())) {
            PermissionHelper.requestPermissions(this, REQUEST_CODE_PERMISSIONS)
        }
    }

    private fun startReceivingMode() {
        // Start server and navigate to devices screen
        val intent = Intent(requireContext(), TransferService::class.java).apply {
            action = Constants.ACTION_START_SERVER
        }
        requireContext().startForegroundService(intent)

        findNavController().navigate(R.id.action_home_to_devices)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            val allGranted = grantResults.all { it == android.content.pm.PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                // Permissions granted
            } else {
                // Show rationale
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 100
    }
}