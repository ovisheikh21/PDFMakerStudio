package com.pdfmakerstudio.app.di

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.pdfmakerstudio.app.R
import com.pdfmakerstudio.app.data.draft.StudioDraftStore
import com.pdfmakerstudio.app.data.pdf.A4PdfGenerator
import com.pdfmakerstudio.app.data.pdf.StudioDownloadedStore
import com.pdfmakerstudio.app.data.repository.FirebaseAuthRepository
import com.pdfmakerstudio.app.data.repository.FirebasePdfCloudRepository
import com.pdfmakerstudio.app.domain.repository.AuthRepository
import com.pdfmakerstudio.app.domain.repository.PdfCloudRepository

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    private val firebaseApp: FirebaseApp = FirebaseApp.initializeApp(appContext)
        ?: FirebaseApp.getInstance()

    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance(firebaseApp)

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(
        firebaseApp,
        appContext.getString(R.string.firebase_database_id)
    )

    private val storage: FirebaseStorage = FirebaseStorage.getInstance(firebaseApp)

    val authRepository: AuthRepository by lazy {
        FirebaseAuthRepository(firebaseAuth, firestore)
    }

    val pdfCloudRepository: PdfCloudRepository by lazy {
        FirebasePdfCloudRepository(firestore, storage)
    }

    val a4PdfGenerator: A4PdfGenerator by lazy {
        A4PdfGenerator(appContext)
    }

    val draftStore: StudioDraftStore by lazy {
        StudioDraftStore(appContext)
    }

    val downloadedStore: StudioDownloadedStore by lazy {
        StudioDownloadedStore(appContext)
    }
}
