package com.example.utils

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

class GoogleSignInHelper(private val context: Context) {
    private val credentialManager = CredentialManager.create(context)

    suspend fun signIn(forcedEmail: String? = null): Result<String> {
        return try {
            // First check GoogleSignIn from play-services-auth if an account is already signed in
            val lastSignedInAccount = GoogleSignIn.getLastSignedInAccount(context)
            if (lastSignedInAccount != null && !lastSignedInAccount.email.isNullOrBlank()) {
                val email = lastSignedInAccount.email!!
                if (!email.equals("leornardcano@gmail.com", ignoreCase = true)) {
                    Log.i("GoogleSignIn", "Play Services Auth active account detected: $email")
                    return Result.success(email)
                }
            }

            val googleIdOptionBuilder = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId("YOUR_WEB_CLIENT_ID")
                .setAutoSelectEnabled(false)

            val googleIdOption = googleIdOptionBuilder.build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(context, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                Result.success(googleIdTokenCredential.id)
            } else {
                getPlayServicesAccount()
            }
        } catch (e: Exception) {
            Log.w("GoogleSignIn", "CredentialManager / PlayServices flow: ${e.message}")
            getPlayServicesAccount()
        }
    }

    private fun getPlayServicesAccount(): Result<String> {
        return try {
            // Use GoogleSignIn API from play-services-auth to query authenticated accounts
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build()
            val googleSignInClient = GoogleSignIn.getClient(context, gso)
            
            // Fallback checking last signed in account from Play Services
            val account = GoogleSignIn.getLastSignedInAccount(context)
            if (account != null && !account.email.isNullOrBlank()) {
                val email = account.email!!
                if (!email.equals("leornardcano@gmail.com", ignoreCase = true)) {
                    Log.i("GoogleSignIn", "Play-services-auth connected account: $email")
                    return Result.success(email)
                }
            }

            Result.failure(Exception("Por favor, inicia sesión con Google Play Services para conectar la cuenta activa del dispositivo."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

