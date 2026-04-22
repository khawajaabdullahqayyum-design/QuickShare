// app/src/main/java/com/quickshare/ui/files/FilePickerFragment.kt
package com.quickshare.ui.files

import android.Manifest
import android.content.ContentUris
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.quickshare.R
import com.quickshare.data.model.FileItem
import com.quickshare.data.model.FileType
import com.quickshare.databinding.FragmentFilePickerBinding
import com.quickshare.ui.transfer.TransferViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class FilePickerFragment : Fragment() {

    private var _binding: FragmentFilePickerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TransferViewModel by viewModels()
    private lateinit var fileAdapter: FileAdapter

    private val files = mutableListOf<FileItem>()
    private val selectedFiles = mutableSetOf<FileItem>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFilePickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupTabLayout()
        loadFiles(FileType.IMAGE)

        binding.btnSendSelected.setOnClickListener {
            sendSelectedFiles()
        }
    }

    private fun setupRecyclerView() {
        fileAdapter = FileAdapter(
            onItemClick = { file ->
                toggleSelection(file)
            }
        )

        binding.rvFiles.apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
            adapter = fileAdapter
        }
    }

    private fun setupTabLayout() {
        binding.tabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                val type = when (tab?.position) {
                    0 -> FileType.IMAGE
                    1 -> FileType.VIDEO
                    2 -> FileType.DOCUMENT
                    3 -> FileType.APK
                    else -> FileType.IMAGE
                }
                loadFiles(type)
            }

            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })
    }

    private fun loadFiles(fileType: FileType) {
        viewLifecycleOwner.lifecycleScope.launch {
            val loadedFiles = withContext(Dispatchers.IO) {
                queryFiles(fileType)
            }
            files.clear()
            files.addAll(loadedFiles)
            fileAdapter.submitList(files)
        }
    }

    private fun queryFiles(fileType: FileType): List<FileItem> {
        val collection = when (fileType) {
            FileType.IMAGE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }
            }
            FileType.VIDEO -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                } else {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                }
            }
            FileType.DOCUMENT -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
                } else {
                    MediaStore.Files.getContentUri("external")
                }
            }
            FileType.APK -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Files.getContentUri("external")
                }
            }
            FileType.OTHER -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
                } else {
                    MediaStore.Files.getContentUri("external")
                }
            }
        }

        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_MODIFIED
        )

        val selection = when (fileType) {
            FileType.APK -> "${MediaStore.MediaColumns.MIME_TYPE} = ? OR ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
            else -> null
        }

        val selectionArgs = when (fileType) {
            FileType.APK -> arrayOf("application/vnd.android.package-archive", "%.apk")
            else -> null
        }

        val sortOrder = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"

        val files = mutableListOf<FileItem>()
        val cursor: Cursor? = requireContext().contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )

        cursor?.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameColumn = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeColumn = it.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val mimeColumn = it.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)

            while (it.moveToNext()) {
                val id = it.getLong(idColumn)
                val name = it.getString(nameColumn)
                val size = it.getLong(sizeColumn)
                val mimeType = it.getString(mimeColumn) ?: "application/octet-stream"

                val contentUri = ContentUris.withAppendedId(collection, id)

                files.add(
                    FileItem(
                        uri = contentUri,
                        name = name,
                        size = size,
                        mimeType = mimeType,
                        type = fileType
                    )
                )
            }
        }

        return files
    }

    private fun toggleSelection(file: FileItem) {
        if (selectedFiles.contains(file)) {
            selectedFiles.remove(file)
        } else {
            selectedFiles.add(file)
        }
        fileAdapter.setSelectedItems(selectedFiles)
        updateSelectedCount()
    }

    private fun updateSelectedCount() {
        binding.btnSendSelected.text = "Send (${selectedFiles.size})"
        binding.btnSendSelected.isEnabled = selectedFiles.isNotEmpty()
    }

    private fun sendSelectedFiles() {
        if (selectedFiles.isEmpty()) return

        viewModel.setSelectedFiles(selectedFiles.toList())
        findNavController().navigate(R.id.action_files_to_devices)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}