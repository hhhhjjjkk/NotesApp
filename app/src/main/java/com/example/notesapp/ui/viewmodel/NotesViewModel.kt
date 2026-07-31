package com.example.notesapp.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.notesapp.data.Note
import com.example.notesapp.data.NoteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotesViewModel(private val repository: NoteRepository) : ViewModel() {

    companion object {
        const val ACTION_SEND = "android.intent.action.SEND"
        const val EXTRA_TEXT = "android.intent.extra.TEXT"
    }

    private val searchQuery = MutableStateFlow("")

    val notes: StateFlow<List<Note>> = combine(
        searchQuery,
        repository.getAllNotes()
    ) { query, allNotes ->
        if (query.isBlank()) {
            allNotes
        } else {
            allNotes.filter {
                it.title.contains(query, ignoreCase = true) ||
                        it.content.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    var currentSearchQuery by mutableStateOf("")
        private set

    fun onSearchQueryChange(query: String) {
        currentSearchQuery = query
        searchQuery.value = query
    }

    fun saveNote(note: Note, onSaved: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repository.saveNote(note)
            onSaved(id)
        }
    }

    fun deleteNote(note: Note, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteNote(note)
            onDeleted()
        }
    }

    fun togglePin(note: Note) {
        viewModelScope.launch {
            repository.saveNote(note.copy(isPinned = !note.isPinned))
        }
    }

    fun shareText(content: String, sendIntent: (android.content.Intent) -> Unit) {
        val intent = android.content.Intent(ACTION_SEND).apply {
            type = "text/plain"
            putExtra(EXTRA_TEXT, content)
        }
        sendIntent(intent)
    }
}
