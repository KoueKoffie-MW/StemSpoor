# **Product Requirements Document (PRD): VAD Smart Recorder App**

## **1\. App Overview**

An Android application that continuously listens to the microphone in the background but only records human speech. It uses on-device AI (Silero VAD) to discard silence, saving disk space. Recordings are split into manageable chunks with a companion JSON file detailing the exact real-world timestamps of when speech occurred.

## **2\. Core Audio & AI Engine**

* **Audio Format:** Uncompressed WAV (16-bit PCM, Mono, 16 kHz).  
* **VAD Model:** Silero VAD running locally via ONNX Runtime Android.  
* **Buffering (Pre/Post-Roll):** 600ms of audio saved *before* speech triggers, and 600ms saved *after* speech ends to prevent clipping.  
* **Sensitivity:** Configurable in settings with a recommended default (e.g., 0.5 probability threshold).

## **3\. Storage & File Management**

* **Target Limit:** \~50 MB per file.  
* **Smart Splitting:** When 50 MB is reached, the app must **not** cut mid-sentence. It must wait for the next silent pause (VAD trigger end) before closing the file and starting the next one.  
* **Location:** Public shared storage (e.g., Documents/VAD\_Recordings/) so files are easily accessible via USB or other apps.  
* **Naming Convention:** YYYYMMDD-HHMM-Part001.wav (and .json).  
* **Retention:** Files are kept indefinitely. No automatic deletion.

## **4\. Timestamps & Sidecar Data**

* **Format:** JSON file saved alongside every WAV file.  
* **Data Structure:** Must record the absolute wall-clock time (Epoch / ISO 8601\) for the start and end of every speech segment.  
* **Purpose:** Allows custom playback tools to show exactly when words were spoken in real life, despite the silence being removed.

## **5\. Background Operation & Lifecycle**

* **Always-On:** Uses an Android Foreground Service with a persistent notification ("Listening for speech...").  
* **Auto-Restart:** App should attempt to resume recording automatically if restarted or after a device reboot (Boot Receiver).  
* **Battery Exemption:** App must request the REQUEST\_IGNORE\_BATTERY\_OPTIMIZATIONS permission to prevent Android from aggressively killing it in the background.

## **6\. User Interface (Jetpack Compose)**

* **Orientation:** Full support for both Portrait and Landscape rotation.  
* **Minimal Main Screen:** A simple Start/Stop toggle button and a status text (e.g., "Listening (Silent)" vs. "Recording (Speech Detected)"). Settings gear icon for VAD sensitivity.  
* **Playback Screen:** A custom player that reads the sidecar .json file. It should display a timeline or list of timestamps, allowing the user to tap a specific timestamp and jump right to that moment in the condensed WAV file.

## **7\. Tech Stack Specifications for AI Developer**

* **Language:** Kotlin  
* **UI Framework:** Jetpack Compose  
* **Concurrency:** Kotlin Coroutines (for audio processing queues)  
* **AI Engine:** com.microsoft.onnxruntime:onnxruntime-android  
* **Permissions:** RECORD\_AUDIO, FOREGROUND\_SERVICE\_MICROPHONE, POST\_NOTIFICATIONS, REQUEST\_IGNORE\_BATTERY\_OPTIMIZATIONS.