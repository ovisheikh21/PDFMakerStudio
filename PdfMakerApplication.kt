package com.pdfmakerstudio.app

import android.app.Application
import com.pdfmakerstudio.app.di.AppContainer

class PdfMakerApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
