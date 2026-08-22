# ADR-0009: Automated Google Drive Cloud Sync via WorkManager

## Status
Accepted

## Context
RecMe operates as an automated 24/7 background speech recorder. While local storage management and 32 kbps Opus compression reduce local disk usage, users need automatic offloading to cloud storage (Google Drive) for desktop accessibility, multi-device backup, and persistent archival without manual USB file transfers.

## Decision
1. **Google Drive REST API with Restricted `drive.file` Scope:**
   - Authenticate via Google Identity / Credential Manager requesting the `https://www.googleapis.com/auth/drive.file` OAuth scope.
   - **Privacy Isolation:** The application is strictly sandboxed to files and folders it creates itself; it has zero access to personal or sensitive user files stored in Google Drive.
2. **Dedicated Cloud Folder Structure:**
   - Upload audio files (`.opus` / `.wav`) and paired `.json` sidecars directly into a single dedicated root folder: `RecMe/`.
3. **Background Sync Engine (`WorkManager`):**
   - When a recording file is finalized (and compressed if Opus is enabled), an asynchronous `GoogleDriveSyncWorker` is queued via Android `WorkManager`.
   - **Network Constraints:** Configurable "Wi-Fi Only" policy (default: enabled) ensuring large audio uploads do not consume cellular data unexpectedly.
   - **Guaranteed Delivery:** Uses exponential backoff and retry mechanisms to handle network dropouts.
4. **Local Retention & Storage Tiering:**
   - Local phone recordings are retained by default.
   - Users can toggle **"Delete local files after successful cloud upload"** in Settings to automatically prune local audio once Google Drive confirms successful upload of both audio and JSON.
5. **UI Sync Indicators:**
   - Per-item sync state badges (`Uploaded`, `Syncing`, `Pending`, `Error`) in the Recordings list.
   - Top-level **"Sync All"** button in Recordings and Settings for immediate on-demand synchronization.

## Consequences
- **Pros:**
  - Automated set-and-forget speech archival to Google Drive.
  - Strict data privacy isolation via `drive.file`.
  - Zero mobile data consumption when Wi-Fi only constraint is active.
  - Optional local disk space reclamation upon cloud confirmation.
- **Cons:**
  - Requires a registered OAuth 2.0 Android Client ID in Google Cloud Console.
