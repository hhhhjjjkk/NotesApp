package com.example.notesapp

import android.app.Application
import com.example.notesapp.data.DataStoreManager
import com.example.notesapp.data.NoteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NotesApplication : Application() {
    lateinit var dataStoreManager: DataStoreManager
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        dataStoreManager = DataStoreManager(this)

        // 后台预热数据库，避免首次查询卡顿
        appScope.launch {
            NoteDatabase.getInstance(this@NotesApplication).openHelper.writableDatabase
        }
    }
}
