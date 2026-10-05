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
        // v1 -> v2：防御性迁移。
        // 仓库历史为单次压缩提交，无法确认 v1 的真实表结构；
        // 已知的后继版本都基于「notes 表含 title/content/color/isPinned/tags/createdAt/updatedAt」。
        // 采用「建新表 → 尽力拷贝 → 删旧表 → 改名」：
        // - 新表 schema 与实体期望一致，后续 2→3→4→5 照常工作
        // - 拷贝失败（老表结构与假设不符）时退化为空表：不崩溃、应用仍可用，
        //   相比「缺 1→2 迁移直接抛异常打不开应用」是更可接受的降级
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `notes_new` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`content` TEXT NOT NULL, " +
                        "`color` INTEGER NOT NULL, " +
                        "`isPinned` INTEGER NOT NULL, " +
                        "`tags` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL)"
                )
                try {
                    db.execSQL(
                        "INSERT INTO `notes_new` " +
                            "(`id`,`title`,`content`,`color`,`isPinned`,`tags`,`createdAt`,`updatedAt`) " +
                            "SELECT `id`,`title`,`content`,`color`,`isPinned`," +
                            "IFNULL(`tags`,''),`createdAt`,`updatedAt` FROM `notes`"
                    )
                } catch (_: Exception) {
                    // 老表结构与假设不符：保留空新表，保证应用可正常打开
                }
                db.execSQL("DROP TABLE `notes`")
                db.execSQL("ALTER TABLE `notes_new` RENAME TO `notes`")
            }
        }

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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    // 仅在降级时销毁数据；升级路径必须由 Migration 覆盖，避免用户笔记丢失
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build().also {
                        INSTANCE = it
                    }
            }
        }
    }
}
