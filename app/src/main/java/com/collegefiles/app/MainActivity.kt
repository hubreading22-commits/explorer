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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize App Policy & Credential Store & Upload Manager
        AppModule.contentPolicy = ContentPolicy(applicationContext)
        AppModule.credentialStore = com.collegefiles.app.config.AndroidCredentialStore(applicationContext)
        AppModule.uploadManager = com.collegefiles.app.upload.UploadManager(applicationContext)
        
        // COLD BOOT DETECTION: Wipe credentials if the app was swiped away (fresh launch)
        if (savedInstanceState == null) {
            AppModule.credentialStore.clear()
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
                }
            }
        }
    }
}
