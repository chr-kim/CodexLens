package com.codexlens.data.model

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ReadingNote::class],
    version = 1, // 버전 증가 시 마이그레이션 필요
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun readingNoteDao(): ReadingNoteDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "codex_lens.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
