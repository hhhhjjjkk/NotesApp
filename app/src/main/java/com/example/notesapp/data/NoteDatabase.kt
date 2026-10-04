package com.example.notesapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// version 升至 5：Note 实体新增 richContent 字段（图文混排文档的 JSON 序列化）。
// 所有升级路径均由显式 Migration 覆盖，保留历史数据；
// fallbackToDestructiveMigrationOnDowngrade 仅在降级时兜底。
@Database(entities = [Note::class], version = 5, exportSchema = false)
abstract class NoteDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    companion object {
        // v2 -> v3：新增三列，全部带默认值，历史数据无副作用
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN type INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE notes ADD COLUMN isTrashed INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE notes ADD COLUMN trashedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        // v3 -> v4：新增 reminderAt 列
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN reminderAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        // v4 -> v5：新增 richContent 列（图文混排文档）。
        // 历史笔记该列为空串，读取时回退为「单段纯文本」，因此内容不丢失。
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN richContent TEXT NOT NULL DEFAULT ''")
            }
        }

        @Volatile
        private var INSTANCE: NoteDatabase? = null

        fun getInstance(context: Context): NoteDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NoteDatabase::class.java,
                    "notes_database.db"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    // 仅在降级时销毁数据；升级路径必须由 Migration 覆盖，避免用户笔记丢失
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build().also {
                        INSTANCE = it
                    }
            }
        }
    }
}
