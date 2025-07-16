package com.codexlens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.codexlens.data.model.ReadingNoteDao
import android.content.Context

/**
 * CodexLensViewModel 생성을 위한 팩토리.
 * 외부 의존성(context, DAO 등) 주입.
 */
class CodexLensViewModelFactory(
    private val context: Context,
    private val readingNoteDao: ReadingNoteDao
) : ViewModelProvider.Factory {
    /**
     * ViewModel 인스턴스 생성 로직.
     * @param modelClass 생성할 ViewModel 타입
     */
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val translatorHelper = MlkitTranslatorHelper(context)
        @Suppress("UNCHECKED_CAST")
        return CodexLensViewModel(translatorHelper, readingNoteDao) as T
    }
}
