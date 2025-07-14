package com.codexlens

import com.codexlens.data.model.ReadingNote
import kotlin.jvm.java

// 파일: app/src/test/java/com/codexlens/data/db/ReadingNoteDaoTest.kt

//package com.codexlens.data.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadingNoteDaoTest {
    
    private lateinit var db: AppDatabase
    private lateinit var dao: ReadingNoteDao
    
    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
        dao = db.readingNoteDao()
    }
    
    @After
    fun teardown() {
        db.close()
    }
    
    @Test
    fun insertAndGetNote_returnsSavedNote() {
        // 1. 실패하는 테스트 작성
        val note = ReadingNote(
            originalText = "Hello",
            translatedText = "안녕",
            timestamp = System.currentTimeMillis()
        )
        dao.insert(note)
        val notes = dao.getAll()
        Assert.assertTrue(notes.any { it.originalText == "Hello" && it.translatedText == "안녕" })
    }
}
