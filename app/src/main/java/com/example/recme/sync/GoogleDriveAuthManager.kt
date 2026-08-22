package com.example.recme.sync

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope

/**
 * Manages Google Account authentication and OAuth permissions for Google Drive.
 * Utilizes the restricted drive.file scope so RecMe only accesses its own files.
 */
class GoogleDriveAuthManager(private val context: Context) {

    private val driveFileScope = Scope("https://www.googleapis.com/auth/drive.file")

    private val signInOptions: GoogleSignInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(driveFileScope)
        .build()

    private val signInClient: GoogleSignInClient = GoogleSignIn.getClient(context, signInOptions)

    fun getSignInIntent(): Intent {
        return signInClient.signInIntent
    }

    fun getSignedInAccount(): GoogleSignInAccount? {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        return if (account != null && GoogleSignIn.hasPermissions(account, driveFileScope)) {
            account
        } else {
            null
        }
    }

    fun isSignedIn(): Boolean {
        return getSignedInAccount() != null
    }

    fun getAccountEmail(): String? {
        return getSignedInAccount()?.email
    }

    fun signOut(onComplete: () -> Unit) {
        signInClient.signOut().addOnCompleteListener {
            onComplete()
        }
    }
}
