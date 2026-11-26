package com.pdfmakerstudio.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pdfmakerstudio.app.ui.auth.AuthScreen
import com.pdfmakerstudio.app.ui.auth.AuthViewModel
import com.pdfmakerstudio.app.ui.strings.AppLanguage
import com.pdfmakerstudio.app.ui.studio.StudioScreen
import com.pdfmakerstudio.app.ui.studio.StudioViewModel
import com.pdfmakerstudio.app.ui.theme.PDFMakerStudioTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appContainer = (application as PdfMakerApplication).container

        setContent {
            PDFMakerStudioTheme {
                PdfMakerRoot(appContainer)
            }
        }
    }
}

@Composable
private fun PdfMakerRoot(appContainer: com.pdfmakerstudio.app.di.AppContainer) {
    var language by rememberSaveable { mutableStateOf(AppLanguage.ENGLISH) }
    val toggleLanguage = {
        language = if (language == AppLanguage.ENGLISH) AppLanguage.BENGALI else AppLanguage.ENGLISH
    }

    val currentUser by remember {
        appContainer.authRepository.observeAuthState()
    }.collectAsStateWithLifecycle(initialValue = appContainer.authRepository.currentUser)

    val user = currentUser
    if (user == null) {
        val authViewModel: AuthViewModel = viewModel(
            factory = AuthViewModel.Factory(appContainer.authRepository)
        )
        AuthScreen(
            viewModel = authViewModel,
            language = language,
            onToggleLanguage = toggleLanguage
        )
    } else {
        val studioViewModel: StudioViewModel = viewModel(
            key = "studio_${user.uid}",
            factory = StudioViewModel.Factory(
                user = user,
                authRepository = appContainer.authRepository,
                pdfCloudRepository = appContainer.pdfCloudRepository,
                pdfGenerator = appContainer.a4PdfGenerator,
                draftStore = appContainer.draftStore,
                downloadedStore = appContainer.downloadedStore
            )
        )
        StudioScreen(
            viewModel = studioViewModel,
            language = language,
            onToggleLanguage = toggleLanguage
        )
    }
}
