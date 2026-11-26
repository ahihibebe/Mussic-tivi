package com.example.mobile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
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
    private var hasPromptedPermission = false

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

    private val safFilesLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addSafFiles(uris)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )

        requestAudioPermissionIfNeeded(fromUserClick = false)

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            MobileServerWifiTheme {
                MobileServerScreen(
                    uiState = uiState,
                    onToggleServer = viewModel::toggleServer,
                    onRescanMusic = { viewModel.scanMusic(forceRestart = false) },
                    onStopScan = viewModel::stopScan,
                    onPickFolder = { safFolderLauncher.launch(null) },
                    onRemoveFolder = viewModel::removeSafFolder,
                    onPickFiles = {
                        safFilesLauncher.launch(arrayOf("audio/*", "application/ogg", "application/octet-stream", "*/*"))
                    },
                    onRequestPermission = { requestAudioPermissionIfNeeded(fromUserClick = true) },
                    onSearchQueryChange = viewModel::updateSearchQuery,
                    onSelectFormat = viewModel::selectFormat,
                    onPlayOnTv = viewModel::playTrackOnTv,
                    onScanForTvs = viewModel::scanForTvs,
                    onManualTvIpChange = viewModel::updateManualTvIp,
                    onConnectToTv = viewModel::connectToTv,
                    onDisconnectFromTv = viewModel::disconnectFromTv,
                    onSendTvControl = viewModel::sendTvControl,
                    onCreateWebRtcOffer = viewModel::createWebRtcOffer,
                    onWebRtcAnswerInputChange = viewModel::onWebRtcAnswerInputChange,
                    onApplyWebRtcAnswer = { viewModel.applyWebRtcAnswer() },
                    onConnectWebRtcAutoToTv = viewModel::connectWebRtcAutoToTv,
                    onDisconnectWebRtc = viewModel::disconnectWebRtc,
                    onTogglePreview = viewModel::togglePreview,
                    onRemoveTrack = viewModel::removeTrack,
                    onRestoreExcludedTracks = viewModel::restoreExcludedTracks
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val granted = ContextCompat.checkSelfPermission(this, requiredAudioPermission()) == PackageManager.PERMISSION_GRANTED
        viewModel.onPermissionUpdated(granted)
    }

    private fun requiredAudioPermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    private fun requestAudioPermissionIfNeeded(fromUserClick: Boolean) {
        val permission = requiredAudioPermission()
        val granted = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        viewModel.onPermissionUpdated(granted)
        if (!granted) {
            if (fromUserClick && hasPromptedPermission && !shouldShowRequestPermissionRationale(permission)) {
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", packageName, null)
                )
                startActivity(intent)
            } else {
                hasPromptedPermission = true
                audioPermissionLauncher.launch(permission)
            }
        }
    }
}
