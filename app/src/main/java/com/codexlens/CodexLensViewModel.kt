package com.codexlens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import android.graphics.Rect
import com.codexlens.data.model.ReadingNote
import com.codexlens.data.model.ReadingNoteDao

/**
 * OCR·번역 및 노트 저장을 전담하는 ViewModel.
 * @property translatorHelper ML Kit 번역 헬퍼
 * @property readingNoteDao Room DB DAO
 */
class CodexLensViewModel(
    private val translatorHelper: MlkitTranslatorHelper,
    private val readingNoteDao: ReadingNoteDao
) : ViewModel() {
    
    /**
     * OCR 결과(텍스트/박스) 리스트
     */
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
    
    /**
     * 번역 결과, 실패, 로딩 등 UI 상태 표현(StateFlow)
     */
    private val _translateState = MutableStateFlow<TranslateUiState>(TranslateUiState.Idle)
    val translateState: StateFlow<TranslateUiState> = _translateState
    
    init {
        prepareTranslator()
    }
    
    private fun prepareTranslator() {
        translatorHelper.prepare { /* 처리 Optional */ }
    }
    
    /**
     * OCR 인식 박스 갱신
     * @param blocks (텍스트, 위치정보) 리스트
     */
    // OCR 결과(텍스트,Rect 쌍) 주입 (예: CustomAnalyzer/CameraPreview에서 호출)
    fun updateOcrBlocks(blocks: List<Pair<String, Rect>>) {
        _ocrBlocks.value = blocks
    }
    /**
     * 텍스트 박스 선택 시 번역 요청 및 상태 갱신
     * @param box 선택 박스
     */
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
    /**
     * 번역 팝업/상태 초기화
     */
    fun dismissTranslation() {
        _showTranslation.value = false
        _selectedBox.value = null
        _translationResult.value = null
        _loading.value = false
    }
    private val _noteList = MutableStateFlow<List<ReadingNote>>(emptyList())
    val noteList: StateFlow<List<ReadingNote>> = _noteList
    /**
     * 저장된 모든 노트 조회(최신순)
     */
    fun loadAllNotes() {
        viewModelScope.launch {
            _noteList.value = readingNoteDao.getAllNotes()
        }
    }
    /**
     * 노트 1건 DB 저장 및 즉시 목록 갱신
     * @param note 저장할 노트
     */
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
