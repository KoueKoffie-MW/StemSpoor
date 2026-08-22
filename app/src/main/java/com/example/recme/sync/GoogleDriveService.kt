package com.example.recme.sync

import android.content.Context
import android.util.Log
import com.example.recme.storage.SidecarData
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File as DriveFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Collections

/**
 * Handles communication with Google Drive REST API v3 to upload audio recordings, JSON sidecars, and Obsidian Vault Markdown notes.
 */
class GoogleDriveService(
    private val context: Context,
    account: GoogleSignInAccount
) {
    private val driveService: Drive
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    init {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            Collections.singleton("https://www.googleapis.com/auth/drive.file")
        ).apply {
            selectedAccount = account.account
        }

        driveService = Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("RecMe").build()
    }

    /**
     * Finds or creates the dedicated root folder "RecMe" in user's Google Drive.
     */
    suspend fun getOrCreateRecMeFolderId(): String = withContext(Dispatchers.IO) {
        val query = "mimeType = 'application/vnd.google-apps.folder' and name = 'RecMe' and trashed = false"
        val result = driveService.files().list()
            .setQ(query)
            .setSpaces("drive")
            .setFields("files(id, name)")
            .execute()

        val existingFolder = result.files?.firstOrNull()
        if (existingFolder != null) {
            return@withContext existingFolder.id
        }

        // Create new folder
        val folderMetadata = DriveFile().apply {
            name = "RecMe"
            mimeType = "application/vnd.google-apps.folder"
        }
        val createdFolder = driveService.files().create(folderMetadata)
            .setFields("id")
            .execute()

        Log.i(TAG, "Created new Google Drive folder 'RecMe' with ID: ${createdFolder.id}")
        return@withContext createdFolder.id
    }

    /**
     * Finds or creates a nested subfolder under parentFolderId.
     */
    suspend fun getOrCreateSubFolderId(parentFolderId: String, folderName: String): String = withContext(Dispatchers.IO) {
        val query = "mimeType = 'application/vnd.google-apps.folder' and name = '$folderName' and '$parentFolderId' in parents and trashed = false"
        val result = driveService.files().list()
            .setQ(query)
            .setSpaces("drive")
            .setFields("files(id, name)")
            .execute()

        val existing = result.files?.firstOrNull()
        if (existing != null) {
            return@withContext existing.id
        }

        val folderMetadata = DriveFile().apply {
            name = folderName
            parents = listOf(parentFolderId)
            mimeType = "application/vnd.google-apps.folder"
        }
        val created = driveService.files().create(folderMetadata)
            .setFields("id")
            .execute()

        Log.i(TAG, "Created subfolder '$folderName' under $parentFolderId -> ID: ${created.id}")
        return@withContext created.id
    }

    /**
     * Uploads or updates a single local file into the specified Drive folder.
     */
    suspend fun uploadOrUpdateFile(localFile: File, mimeType: String, parentFolderId: String): String = withContext(Dispatchers.IO) {
        val query = "name = '${localFile.name}' and '$parentFolderId' in parents and trashed = false"
        val listResult = driveService.files().list()
            .setQ(query)
            .setSpaces("drive")
            .setFields("files(id, name)")
            .execute()

        val existingFile = listResult.files?.firstOrNull()
        val mediaContent = FileContent(mimeType, localFile)

        if (existingFile != null) {
            // Update existing file content
            val updated = driveService.files().update(existingFile.id, null, mediaContent)
                .setFields("id, name, size")
                .execute()
            Log.i(TAG, "Updated existing ${localFile.name} -> Drive ID: ${updated.id}")
            return@withContext updated.id
        } else {
            // Create new file
            val fileMetadata = DriveFile().apply {
                name = localFile.name
                parents = listOf(parentFolderId)
            }
            val uploaded = driveService.files().create(fileMetadata, mediaContent)
                .setFields("id, name, size")
                .execute()
            Log.i(TAG, "Created new file ${localFile.name} -> Drive ID: ${uploaded.id}")
            return@withContext uploaded.id
        }
    }

    /**
     * Uploads audio file (.opus/.wav), .json sidecar, and companion .md note into Google Drive.
     */
    suspend fun uploadRecordingGroup(
        audioFile: File,
        jsonFile: File?,
        mdFile: File?
    ): Pair<String, String?> = withContext(Dispatchers.IO) {
        val rootId = getOrCreateRecMeFolderId()

        // 1. Upload audio file
        val audioMimeType = when {
            audioFile.name.endsWith(".opus", ignoreCase = true) -> "audio/ogg"
            audioFile.name.endsWith(".ogg", ignoreCase = true) -> "audio/ogg"
            else -> "audio/wav"
        }
        val audioDriveId = uploadOrUpdateFile(audioFile, audioMimeType, rootId)

        // 2. Upload JSON sidecar
        var jsonDriveId: String? = null
        if (jsonFile != null && jsonFile.exists()) {
            try {
                val content = jsonFile.readText(Charsets.UTF_8)
                val sidecar = json.decodeFromString<SidecarData>(content)
                val updatedSidecar = sidecar.copy(
                    driveFileId = audioDriveId,
                    driveSyncEpochMs = System.currentTimeMillis()
                )
                jsonFile.writeText(json.encodeToString(SidecarData.serializer(), updatedSidecar))

                jsonDriveId = uploadOrUpdateFile(jsonFile, "application/json", rootId)
                val finalSidecar = updatedSidecar.copy(driveJsonFileId = jsonDriveId)
                jsonFile.writeText(json.encodeToString(SidecarData.serializer(), finalSidecar))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to upload sidecar JSON for ${audioFile.name}", e)
            }
        }

        // 3. Upload companion Markdown note
        if (mdFile != null && mdFile.exists()) {
            try {
                uploadOrUpdateFile(mdFile, "text/markdown", rootId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to upload markdown note ${mdFile.name}", e)
            }
        }

        return@withContext Pair(audioDriveId, jsonDriveId)
    }

    /**
     * Uploads a Vault note into RecMe/vault/daily/ or RecMe/vault/topics/.
     */
    suspend fun uploadVaultNote(noteFile: File, subFolder: String): String = withContext(Dispatchers.IO) {
        val rootId = getOrCreateRecMeFolderId()
        val vaultId = getOrCreateSubFolderId(rootId, "vault")
        val subFolderId = getOrCreateSubFolderId(vaultId, subFolder)

        return@withContext uploadOrUpdateFile(noteFile, "text/markdown", subFolderId)
    }

    companion object {
        private const val TAG = "GoogleDriveService"
    }
}
