package com.codexlens.data.model

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * 앱 전역 데이터베이스 - RoomDatabase 기반 싱글톤.
 * @property readingNoteDao 독서 노트 DAO 제공
 * @see ReadingNote
 */
@Database(
    entities = [ReadingNote::class],
    version = 1, // 버전 증가 시 마이그레이션 필요
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    /**
     * 독서 노트 DAO 반환
     */
    abstract fun readingNoteDao(): ReadingNoteDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        /**
         * AppDatabase (싱글톤) 인스턴스를 반환.
         * @param context 앱 컨텍스트
         */
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
