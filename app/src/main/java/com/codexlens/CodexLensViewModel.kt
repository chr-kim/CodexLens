import android.content.Context
import android.graphics.Rect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codexlens.MlkitTranslatorHelper
import com.codexlens.TextBox
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.codexlens.data.model.ReadingNote
import com.codexlens.data.model.ReadingNoteDao

/**
 * OCR·번역 및 노트 저장 로직을 담당하는 ViewModel
 * @property translatorHelper ML Kit 번역 헬퍼 인스턴스
 * @property readingNoteDao Room DB(Dao)
 */
class CodexLensViewModel(
    private val translatorHelper: MlkitTranslatorHelper,
    private val readingNoteDao: ReadingNoteDao
) : ViewModel() {
    
    // OCR 결과(텍스트, 위치정보 쌍)
    private val _ocrBlocks = MutableStateFlow<List<Pair<String, Rect>>>(emptyList())
    val ocrBlocks: StateFlow<List<Pair<String, Rect>>> get() = _ocrBlocks
    
    // 박스 선택 상태
    private val _selectedBox = MutableStateFlow<TextBox?>(null)
    val selectedBox: StateFlow<TextBox?> get() = _selectedBox
    
    // 번역 팝업/로딩 등 UI용 상태 관리
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> get() = _loading
    
    private val _showTranslation = MutableStateFlow(false)
    val showTranslation: StateFlow<Boolean> get() = _showTranslation
    
    /**
     * 번역 결과 및 에러 등 UI 상태(StateFlow로 변화 감지)
     */
    private val _translateState = MutableStateFlow<TranslateUiState>(TranslateUiState.Idle)
    val translateState: StateFlow<TranslateUiState> get() = _translateState
    
    // --- 번역 모델 준비 (MainScreen 등에서 '권한 승인 후' 호출 필요) ---
    /**
     * 번역 모델 다운로드 및 준비(컴포저블/액티비티에서 context를 명확히 전달)
     * @param context Composable 등에서 전달받은 Context
     * @param onReady(선택) 준비 완료 콜백(성공/실패)
     */
    fun prepareTranslator(context: Context, onReady: ((Boolean) -> Unit)? = null) {
        translatorHelper.prepare { success ->
            // 토스트, 상태관리 등 추가 안내 필요시 콜백 활용
            onReady?.invoke(success)
        }
    }
    
    /**
     * OCR 인식 결과(텍스트+박스) 주입
     */
    fun updateOcrBlocks(blocks: List<Pair<String, Rect>>) {
        _ocrBlocks.value = blocks
    }
    
    /**
     * 텍스트 박스 터치 시 번역 실행 및 상태 갱신
     * @param box 사용자가 선택한 박스 데이터
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
     * 번역 결과 팝업 및 상태 초기화
     */
    fun dismissTranslation() {
        _showTranslation.value = false
        _selectedBox.value = null
        _loading.value = false
        _translateState.value = TranslateUiState.Idle
    }
    
    // --- 독서 노트 리스트 관리 ---
    private val _noteList = MutableStateFlow<List<ReadingNote>>(emptyList())
    val noteList: StateFlow<List<ReadingNote>> get() = _noteList
    
    /**
     * Room DB로부터 모든 노트 최신순 로딩
     */
    fun loadAllNotes() {
        viewModelScope.launch {
            _noteList.value = readingNoteDao.getAllNotes()
        }
    }
    
    /**
     * 노트 저장(DB insert) 및 목록 자동 갱신
     * @param note 저장할 ReadingNote 인스턴스
     */
    fun saveNote(note: ReadingNote) {
        viewModelScope.launch {
            readingNoteDao.insert(note)
            loadAllNotes() // 저장 후 곧바로 리스트 갱신
        }
    }
}

/**
 * 번역 결과 UI State 표현 sealed class
 */
sealed class TranslateUiState {
    object Idle : TranslateUiState()
    object Loading : TranslateUiState()
    data class Success(val translated: String) : TranslateUiState()
    data class Error(val message: String) : TranslateUiState()
}
