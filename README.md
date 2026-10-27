# Technical Documentation and Architecture of the VozNota Project

**Developed by:** Ángel Ramírez Vega
**Application:** VozNota — Continuous Background Voice Transcriber
**Platform:** Android (API 24+)

---

## 1. Technologies and Languages

| Category | Technology / Language | Version / Detail |
| :--- | :--- | :--- |
| **Main Language** | **Kotlin** | 2.0+ (Idiomatic, Coroutines, Flows, Type-Safety) |
| **UI Framework** | **Jetpack Compose** | Material Design 3 (Declarative Evolution) |
| **UI Architecture** | **MVVM (Model-View-ViewModel)** | Unidirectional Data Flow (UDF) + StateFlow |
| **Concurrency** | **Kotlin Coroutines & StateFlow** | `Dispatchers.IO`, `Mutex`, `viewModelScope` |
| **Voice Engine** | **Android SpeechRecognizer API** | Native `android.speech.SpeechRecognizer` |
| **Background Services** | **Foreground Service** | `foregroundServiceType="microphone"` (API 34+) |
| **Persistence** | **AtomicFile + JSON** | Private Storage (`filesDir/notes.json`) |
| **Build System** | **Gradle Kotlin DSL (build.gradle.kts)** | Android Gradle Plugin 8.x+ / AGP 9 |

---

## 2. Project Structure

The project follows the clean architecture recommended by Google, organized under the root package 'com.s1trok.voznota':
com.s1trok.voznota/
├── Note.kt                      # Immutable data model for recordings
├── StorageManager.kt            # Data layer and secure atomic storage
├── SpeechManager.kt             # Continuous and uninterrupted speech recognition engine
├── VoiceService.kt              # Foreground service for background recording
├── TranscriptorViewModel.kt     # ViewModel and state management (StateFlow)
└── MainActivity.kt              # User Interface (Jetpack Compose, Nothing Phone Theme)

---

## 3. Detailed Description of Components and Methods

### 3.1. Data Model — 'Note.kt'
Defines the immutable entity representing a saved note or transcription.


data class Note(
    val id: Long = System.currentTimeMillis(),
    val text: String,
    val createdAt: Long = System.currentTimeMillis()
)
## 3.2. Storage Layer — StorageManager.kt
Ensures notes are saved securely in private storage off the main thread, using atomic writes to prevent data corruption.

Key Methods:
suspend fun load(): List<Note>: Asynchronously reads and deserializes the notes.json file using Dispatchers.IO and mutual exclusion (Mutex).

suspend fun add(note: Note): List<Note>: Inserts the new note at the beginning of the list and atomically persists the result.

suspend fun delete(id: Long): List<Note>: Filters out the corresponding note and updates the JSON file.

private fun write(notes: List<Note>): Uses AtomicFile.startWrite() and AtomicFile.finishWrite() to ensure that if the process is interrupted during saving, the original file is not corrupted.

## 3.3. Speech Recognition Engine — SpeechManager.kt
Manages continuous recognition on top of the native Android API (SpeechRecognizer), bypassing standard limitations regarding restarts and system beeps.

Key Methods and Implementations:
start() / stop(): Controls the recognizer's state. Upon starting, it invokes muteSystemBeepSounds() and sets up the listeners.

scheduleListen(delayMs: Long): Schedules the listening restart using a Handler associated with the main thread (Looper.getMainLooper()), preventing busy session errors (ERROR_RECOGNIZER_BUSY).

muteSystemBeepSounds() / restoreSystemBeepSounds(): Uses AudioManager.adjustStreamVolume to temporarily mute system sounds (STREAM_SYSTEM and STREAM_NOTIFICATION), removing the beep emitted by Google when restarting the listening process.

Recognition Intent Configuration:

EXTRA_LANGUAGE_MODEL = LANGUAGE_MODEL_FREE_FORM

EXTRA_PARTIAL_RESULTS = true (Real-time transcription)

"android.speech.extra.DICTATION_MODE" = true (Continuous dictation mode)

EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS = 5000L (Tolerance for long pauses without cutting off)

## 3.4. Foreground Service — VoiceService.kt
Allows the microphone and transcription engine to continue running with the screen off or when the user leaves the application.

Key Methods and Concepts:
onStartCommand(intent, flags, startId): Processes the ACTION_START and ACTION_STOP commands. Registers the persistent notification and calls startForeground().

Android 14+ (API 34) Compatibility:

Kotlin
startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
buildNotification(contentText): Builds the persistent notification with an IMPORTANCE_LOW channel and includes a quick action with a PendingIntent to stop the recording.

serviceState (StateFlow): Broadcasts the current draft (draft), partial text (partial), and listening state to the entire app in real-time.

## 3.5. ViewModel — TranscriptorViewModel.kt
Maintains the UI state, making it resilient to configuration changes (like screen rotation).

Key Methods:
init: Subscribes to VoiceService.serviceState to synchronize transcription changes in real-time.

startListening() / stopListening(): Sends the signal to start or stop the VoiceService.

saveNote(): Appends the accumulated text as a note in StorageManager and clears the draft.

deleteNote(id: Long): Deletes a note by its unique identifier.

## 3.6. User Interface — MainActivity.kt
Implemented entirely with Jetpack Compose, applying the Nothing Phone / Dark Minimalist design (#080808 OLED Black, #FF2A2A Nothing Red).

Key Components:
TranscriptorScreen: Main composable that manages permission states, tabs (TRANSCRIBE and RECORDINGS), and alerts.

RecordingCard: Component that renders each saved note with its formatted date, word count, and action buttons (Copy to clipboard, Share, and Delete).

getRelativeDateGroup(timestamp: Long): Classifies recordings under relative time headers (TODAY, YESTERDAY, THIS WEEK, MONTH/YEAR).

## 4. Technical Concepts and Architecture Flow
Data Flow Diagram
flowchart TD
    A[User / Microphone] -->|PCM Audio| B[SpeechManager]
    B -->|Partial / Final| C[VoiceService]
    C -->|StateFlow| D[TranscriptorViewModel]
    D -->|StateFlow / UI State| E[MainActivity - Compose UI]
    D -->|Asynchronous Persistence| F[StorageManager - AtomicFile]
    F -->|notes.json| G[Disk / filesDir]
