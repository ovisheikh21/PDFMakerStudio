package com.pdfmakerstudio.app.domain.repository

import com.pdfmakerstudio.app.domain.model.AuthUser
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val currentUser: AuthUser?
    fun observeAuthState(): Flow<AuthUser?>
    suspend fun signInWithEmail(email: String, password: String): Result<AuthUser>
    suspend fun registerWithEmail(email: String, password: String): Result<AuthUser>
    suspend fun signInWithGoogleIdToken(idToken: String): Result<AuthUser>
    fun signOut()
}
