package com.example.fa_scan

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.fa_scan.ui.camera.CameraScanScreen
import com.example.fa_scan.ui.home.HomeScreen
import com.example.fa_scan.ui.review.FormReviewScreen
import com.example.fa_scan.ui.theme.FaScanTheme

enum class AppScreen {
    HOME,
    CAMERA,
    REVIEW
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FaScanTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RegistrationScanApp()
                }
            }
        }
    }
}

@Composable
fun RegistrationScanApp() {
    var currentScreen by remember { mutableStateOf(AppScreen.HOME) }
    var capturedImageUri by remember { mutableStateOf<Uri?>(null) }

    Crossfade(targetState = currentScreen, label = "ScreenTransition") { screen ->
        when (screen) {
            AppScreen.HOME -> {
                HomeScreen(
                    onStartScanClick = {
                        currentScreen = AppScreen.CAMERA
                    }
                )
            }
            AppScreen.CAMERA -> {
                CameraScanScreen(
                    onImageCaptured = { uri ->
                        capturedImageUri = uri
                        currentScreen = AppScreen.REVIEW
                    },
                    onBackClick = {
                        currentScreen = AppScreen.HOME
                    }
                )
            }
            AppScreen.REVIEW -> {
                val uri = capturedImageUri
                if (uri != null) {
                    FormReviewScreen(
                        imageUri = uri,
                        onRetakeClick = {
                            currentScreen = AppScreen.CAMERA
                        },
                        onConfirmClick = {
                            currentScreen = AppScreen.HOME
                        },
                        onBackClick = {
                            currentScreen = AppScreen.CAMERA
                        }
                    )
                } else {
                    currentScreen = AppScreen.HOME
                }
            }
        }
    }
}
