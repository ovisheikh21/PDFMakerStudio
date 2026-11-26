package com.pdfmakerstudio.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.pdfmakerstudio.app.domain.model.AuthUser
import com.pdfmakerstudio.app.domain.repository.AuthRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirebaseAuthRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : AuthRepository {

    override val currentUser: AuthUser?
        get() = auth.currentUser?.toDomain()

    override fun observeAuthState(): Flow<AuthUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser?.toDomain())
        }
        auth.addAuthStateListener(listener)
        trySend(auth.currentUser?.toDomain())
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override suspend fun signInWithEmail(email: String, password: String): Result<AuthUser> =
        runCatching {
            val result = auth.signInWithEmailAndPassword(email.trim(), password).await()
            val user = result.user ?: error("Authentication succeeded but user is null")
            syncPrivateProfile(user)
            user.toDomain()
        }

    override suspend fun registerWithEmail(email: String, password: String): Result<AuthUser> =
        runCatching {
            val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
            val user = result.user ?: error("Registration succeeded but user is null")
            syncPrivateProfile(user)
            user.toDomain()
        }

    override suspend fun signInWithGoogleIdToken(idToken: String): Result<AuthUser> =
        runCatching {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = auth.signInWithCredential(credential).await()
            val user = result.user ?: error("Google Sign-In succeeded but user is null")
            syncPrivateProfile(user)
            user.toDomain()
        }

    override fun signOut() {
        auth.signOut()
    }

    private suspend fun syncPrivateProfile(user: FirebaseUser) {
        val profileData = hashMapOf<String, Any>(
            "uid" to user.uid,
            "email" to (user.email ?: "${user.uid}@pdfmakerstudio.app"),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        user.displayName?.takeIf { it.isNotBlank() }?.let {
            profileData["displayName"] = it.take(120)
        }
        runCatching {
            firestore.collection("users")
                .document(user.uid)
                .collection("private")
                .document("profile")
                .set(profileData)
                .await()
        }
    }

    private fun FirebaseUser.toDomain(): AuthUser = AuthUser(
        uid = uid,
        email = email ?: "",
        displayName = displayName
    )
}
