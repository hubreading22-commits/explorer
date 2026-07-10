package com.collegefiles.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.collegefiles.app.ui.explorer.ExplorerScreen
import com.collegefiles.app.ui.explorer.ExplorerViewModel
import com.collegefiles.app.ui.explorer.FileOpsViewModel
import com.collegefiles.app.ui.login.LoginScreen
import com.collegefiles.app.ui.login.LoginViewModel
import com.collegefiles.app.ui.shares.SharesScreen
import com.collegefiles.app.ui.shares.SharesViewModel
import com.collegefiles.app.ui.splash.SplashScreen
import com.collegefiles.app.ui.viewer.ViewerDestination
import com.collegefiles.app.ui.viewer.ViewerViewModel
import com.collegefiles.app.ui.viewer.audio.AudioPlayerScreen
import com.collegefiles.app.ui.viewer.image.ImageViewerScreen
import com.collegefiles.app.ui.viewer.image.ImageViewerViewModel
import com.collegefiles.app.ui.viewer.pdf.PdfViewerScreen
import com.collegefiles.app.ui.viewer.pdf.PdfViewerViewModel
import com.collegefiles.app.ui.viewer.text.TextViewerScreen
import com.collegefiles.app.ui.viewer.text.TextViewerViewModel
import com.collegefiles.app.ui.viewer.unsupported.RestrictedScreen
import com.collegefiles.app.ui.viewer.unsupported.UnsupportedScreen
import com.collegefiles.app.ui.viewer.video.VideoPlayerScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    val loginViewModel = remember { LoginViewModel() }
    val sharesViewModel = remember { SharesViewModel() }
    val viewerViewModel = remember { ViewerViewModel() }
    val fileOpsViewModel = remember { FileOpsViewModel() }

    NavHost(navController = navController, startDestination = "splash") {

        composable("splash") {
            SplashScreen(
                onSplashFinished = {
                    navController.navigate("login") {
                        popUpTo("splash") { inclusive = true }
                    }
                }
            )
        }

        composable("login") {
            LoginScreen(
                viewModel = loginViewModel,
                onLoginSuccess = {
                    navController.navigate("shares") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }

        composable("shares") {
            SharesScreen(
                viewModel = sharesViewModel,
                onNavigateToShare = { shareName ->
                    navController.navigate("explorer/$shareName")
                },
                onSessionExpired = {
                    navController.navigate("login") {
                        popUpTo("shares") { inclusive = true }
                    }
                }
            )
        }

        composable("explorer/{shareName}") { backStackEntry ->
            val shareName = backStackEntry.arguments?.getString("shareName") ?: ""
            val explorerViewModel = remember(shareName) { ExplorerViewModel(shareName) }

            ExplorerScreen(
                viewModel = explorerViewModel,
                fileOpsViewModel = fileOpsViewModel,
                onNavigateBackToShares = { navController.popBackStack() },
                onSessionExpired = {
                    navController.navigate("login") {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
                onFileClick = { file ->
                    viewerViewModel.selectFile(file)
                    navController.navigate("viewer")
                }
            )
        }

        composable("viewer") {
            val state by viewerViewModel.state.collectAsState()
            val file = state.file
            val onBack: () -> Unit = {
                viewerViewModel.clearFile()
                navController.popBackStack()
            }

            if (state.isRestricted) {
                RestrictedScreen(file = file, onBack = onBack)
            } else {
                when (state.destination) {
                    ViewerDestination.Pdf -> {
                        val cacheDir = LocalContext.current.cacheDir
                        PdfViewerScreen(
                            viewModel = remember(file?.path) { PdfViewerViewModel(file!!, cacheDir) },
                            onBack = onBack
                        )
                    }
                    ViewerDestination.Image -> ImageViewerScreen(
                        viewModel = remember(file?.path) { ImageViewerViewModel(file!!) },
                        onBack = onBack
                    )
                    ViewerDestination.Text -> TextViewerScreen(
                        viewModel = remember(file?.path) { TextViewerViewModel(file!!) },
                        onBack = onBack
                    )
                    ViewerDestination.Video -> VideoPlayerScreen(file = file, onBack = onBack)
                    ViewerDestination.Audio -> AudioPlayerScreen(file = file, onBack = onBack)
                    else -> UnsupportedScreen(file = file, onBack = onBack)
                }
            }
        }
    }
}
