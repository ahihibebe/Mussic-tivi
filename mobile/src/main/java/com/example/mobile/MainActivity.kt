package com.example.mobile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mobile.ui.MobileServerScreen
import com.example.mobile.ui.theme.MobileServerWifiTheme
import com.example.mobile.viewmodel.MobileServerViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MobileServerViewModel by viewModels()

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onPermissionUpdated(granted)
    }

    private val safFolderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            viewModel.addSafFolder(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestAudioPermissionIfNeeded(autoPrompt = true)

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            MobileServerWifiTheme {
                MobileServerScreen(
                    uiState = uiState,
                    onToggleServer = viewModel::toggleServer,
                    onRescanMusic = viewModel::scanMusic,
                    onPickFolder = { safFolderLauncher.launch(null) },
                    onRequestPermission = { requestAudioPermissionIfNeeded(autoPrompt = true) },
                    onSearchQueryChange = viewModel::updateSearchQuery,
                    onTogglePreview = viewModel::togglePreview
                )
            }
        }
    }

    private fun requestAudioPermissionIfNeeded(autoPrompt: Boolean) {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        val granted = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        viewModel.onPermissionUpdated(granted)
        if (!granted && autoPrompt) {
            audioPermissionLauncher.launch(permission)
        }
    }
}
