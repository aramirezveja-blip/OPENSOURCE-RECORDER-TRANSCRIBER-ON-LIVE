package com.s1trok.voznota

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val listening: Boolean = false,
    val draft: String = "",     // texto ya confirmado
    val partial: String = "",   // texto en curso
    val notes: List<Note> = emptyList(),
    val message: String? = null
)

class TranscriptorViewModel(app: Application) : AndroidViewModel(app) {

    private val storage = StorageManager(app)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val notes = storage.load()
            _state.update { it.copy(notes = notes) }
        }

        viewModelScope.launch {
            VoiceService.serviceState.collect { serviceState ->
                _state.update {
                    it.copy(
                        listening = serviceState.listening,
                        draft = serviceState.draft,
                        partial = serviceState.partial,
                        message = serviceState.message ?: it.message
                    )
                }
            }
        }
    }

    fun startListening() {
        _state.update { it.copy(message = null) }
        VoiceService.setMessage(null)
        VoiceService.startService(getApplication())
    }

    fun stopListening() {
        VoiceService.stopService(getApplication())
    }

    fun showMessage(msg: String?) {
        VoiceService.setMessage(msg)
        _state.update { it.copy(message = msg) }
    }

    fun saveNote() {
        val s = _state.value
        val text = (s.draft + " " + s.partial).trim()
        if (text.isBlank()) return
        stopListening()
        viewModelScope.launch {
            runCatching { storage.add(Note(text = text)) }
                .onSuccess { notes ->
                    VoiceService.clearDraft()
                    _state.update { it.copy(notes = notes, draft = "", partial = "") }
                }
                .onFailure { showMessage("No se pudo guardar la nota.") }
        }
    }

    fun clearDraft() {
        VoiceService.clearDraft()
        _state.update { it.copy(draft = "", partial = "") }
    }

    fun deleteNote(id: Long) {
        viewModelScope.launch {
            runCatching { storage.delete(id) }
                .onSuccess { notes -> _state.update { it.copy(notes = notes) } }
                .onFailure { showMessage("No se pudo borrar la nota.") }
        }
    }
}
