package com.codexlens.data.model

// ReadingNote.kt

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * 독서 노트 데이터 클래스
 * @property id 고유 식별자 (자동 증가)
 * @property originalText 원문 텍스트
 * @property translatedText 번역된 텍스트
 * @property timestamp 저장 시각 (밀리초)
 */
@Entity(tableName = "reading_notes")
data class ReadingNote(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val originalText: String,
    val translatedText: String,
    val timestamp: Long
)

@Dao
interface ReadingNoteDao {
    /**
     * 노트 1건을 DB에 저장.
     * @param note 저장할 노트 엔티티
     * @return 새로 추가된 row의 id값
     */
    @Insert
    suspend fun insert(note: ReadingNote): Long
    /**
     * 저장된 모든 노트 조회(최신순 정렬).
     * @return 모든 노트 리스트
     */
    @Query("SELECT * FROM reading_notes ORDER BY timestamp DESC")
    suspend fun getAllNotes(): List<ReadingNote>
}
