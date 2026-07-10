package com.collegefiles.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.collegefiles.app.config.ContentPolicy
import com.collegefiles.app.di.AppModule
import com.collegefiles.app.ui.navigation.AppNavigation
import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.WorkManager
import androidx.activity.viewModels
import com.collegefiles.app.ui.sync.ShareReceiverViewModel
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val shareReceiverViewModel: ShareReceiverViewModel by viewModels {
        object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return ShareReceiverViewModel() as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize App Policy & Credential Store & Upload Manager
        AppModule.contentPolicy = ContentPolicy(applicationContext)
        AppModule.credentialStore = com.collegefiles.app.config.AndroidCredentialStore(applicationContext)
        AppModule.uploadManager = com.collegefiles.app.upload.UploadManager(applicationContext)
        AppModule.initialize(applicationContext)
        
        // COLD BOOT DETECTION: Wipe credentials if the app was swiped away (fresh launch)
        // EXCEPT if the app was launched specifically to handle an incoming share.
        val isShareIntent = intent?.action == Intent.ACTION_SEND || intent?.action == Intent.ACTION_SEND_MULTIPLE
        if (savedInstanceState == null && !isShareIntent) {
            AppModule.credentialStore.clear()
            WorkManager.getInstance(this).cancelAllWork()
            
            // Wipe sync cache on cold boot
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                AppModule.documentSessionService.clearAll()
            }
        }

        // Request POST_NOTIFICATIONS permission for Android 13+ so upload notifications show
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
        
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                    com.collegefiles.app.ui.sync.ShareReceiverDialogLayer(viewModel = shareReceiverViewModel)
                }
            }
        }
        
        handleIntent(intent)
    }
    
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIntent(it) }
    }
    
    private fun handleIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND) {
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            if (uri != null) {
                shareReceiverViewModel.handleSharedUris(listOf(uri))
            }
        } else if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
            val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            if (uris != null) {
                shareReceiverViewModel.handleSharedUris(uris)
            }
        }
    }
}
