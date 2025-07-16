package com.codexlens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.codexlens.data.model.ReadingNoteDao
import android.content.Context

class CodexLensViewModelFactory(
    private val context: Context,
    private val readingNoteDao: ReadingNoteDao
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val translatorHelper = MlkitTranslatorHelper(context)
        @Suppress("UNCHECKED_CAST")
        return CodexLensViewModel(translatorHelper, readingNoteDao) as T
    }
}
