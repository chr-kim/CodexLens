package com.codexlens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import android.graphics.Rect
import com.codexlens.data.model.ReadingNote
import com.codexlens.data.model.ReadingNoteDao

class CodexLensViewModel(
    private val translatorHelper: MlkitTranslatorHelper,
    private val readingNoteDao: ReadingNoteDao
) : ViewModel() {
    
    // OCR에서 인식된 텍스트/박스 리스트 관리
    private val _ocrBlocks = MutableStateFlow<List<Pair<String, Rect>>>(emptyList())
    val ocrBlocks: StateFlow<List<Pair<String, Rect>>> = _ocrBlocks
    
    private val _selectedBox = MutableStateFlow<TextBox?>(null)
    val selectedBox: StateFlow<TextBox?> = _selectedBox
    
    private val _translationResult = MutableStateFlow<String?>(null)
    val translationResult: StateFlow<String?> = _translationResult
    
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading
    
    private val _showTranslation = MutableStateFlow(false)
    val showTranslation: StateFlow<Boolean> = _showTranslation
    
    private val _translateState = MutableStateFlow<TranslateUiState>(TranslateUiState.Idle)
    val translateState: StateFlow<TranslateUiState> = _translateState
    
    init {
        prepareTranslator()
    }
    
    private fun prepareTranslator() {
        translatorHelper.prepare { /* 처리 Optional */ }
    }
    
    // OCR 결과(텍스트,Rect 쌍) 주입 (예: CustomAnalyzer/CameraPreview에서 호출)
    fun updateOcrBlocks(blocks: List<Pair<String, Rect>>) {
        _ocrBlocks.value = blocks
    }
    
    fun onBoxSelected(box: TextBox) {
        _selectedBox.value = box
        _loading.value = true
        _showTranslation.value = true
        _translateState.value = TranslateUiState.Loading
        translatorHelper.translate(box.text) { success, result ->
            _translateState.value = if (success) {
                TranslateUiState.Success(result)
            } else {
                TranslateUiState.Error(result)
            }
            _loading.value = false
        }
    }
    
    fun dismissTranslation() {
        _showTranslation.value = false
        _selectedBox.value = null
        _translationResult.value = null
        _loading.value = false
    }
    private val _noteList = MutableStateFlow<List<ReadingNote>>(emptyList())
    val noteList: StateFlow<List<ReadingNote>> = _noteList
    
    fun loadAllNotes() {
        viewModelScope.launch {
            _noteList.value = readingNoteDao.getAllNotes()
        }
    }
    // Note 저장마다 목록 자동 갱신 (옵션)
    fun saveNote(note: ReadingNote) {
        viewModelScope.launch {
            readingNoteDao.insert(note)
            loadAllNotes() // 저장 후 즉시 갱신
        }
    }
    
}

sealed class TranslateUiState {
    object Idle : TranslateUiState()
    object Loading : TranslateUiState()
    data class Success(val translated: String) : TranslateUiState()
    data class Error(val message: String) : TranslateUiState()
}
