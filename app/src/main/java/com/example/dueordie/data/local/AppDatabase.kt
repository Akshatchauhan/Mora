package com.example.dueordie.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.dueordie.data.model.Task

@Database(entities = [Task::class], version = 4, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val existingColumns = mutableSetOf<String>()
                db.query("PRAGMA table_info(`tasks`)").use { cursor ->
                    val nameIndex = cursor.getColumnIndex("name")
                    while (cursor.moveToNext()) {
                        if (nameIndex >= 0) {
                            existingColumns.add(cursor.getString(nameIndex))
                        }
                    }
                }
                if ("requirePhotoProof" !in existingColumns) {
                    db.execSQL("ALTER TABLE `tasks` ADD COLUMN `requirePhotoProof` INTEGER NOT NULL DEFAULT 0")
                }
                if ("photoPath" !in existingColumns) {
                    db.execSQL("ALTER TABLE `tasks` ADD COLUMN `photoPath` TEXT")
                }
                if ("isHostageModeEnabled" !in existingColumns) {
                    db.execSQL("ALTER TABLE `tasks` ADD COLUMN `isHostageModeEnabled` INTEGER NOT NULL DEFAULT 0")
                }
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val existingColumns = mutableSetOf<String>()
                db.query("PRAGMA table_info(`tasks`)").use { cursor ->
                    val nameIndex = cursor.getColumnIndex("name")
                    while (cursor.moveToNext()) {
                        if (nameIndex >= 0) existingColumns.add(cursor.getString(nameIndex))
                    }
                }
                if ("notionPageId" !in existingColumns) {
                    db.execSQL("ALTER TABLE `tasks` ADD COLUMN `notionPageId` TEXT")
                }
                if ("notionSyncDirty" !in existingColumns) {
                    db.execSQL("ALTER TABLE `tasks` ADD COLUMN `notionSyncDirty` INTEGER NOT NULL DEFAULT 1")
                }
                if ("lastSyncedAtTimestamp" !in existingColumns) {
                    db.execSQL("ALTER TABLE `tasks` ADD COLUMN `lastSyncedAtTimestamp` INTEGER")
                }
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val existingColumns = mutableSetOf<String>()
                db.query("PRAGMA table_info(`tasks`)").use { cursor ->
                    val nameIndex = cursor.getColumnIndex("name")
                    while (cursor.moveToNext()) {
                        if (nameIndex >= 0) existingColumns.add(cursor.getString(nameIndex))
                    }
                }
                if ("priority" !in existingColumns) {
                    db.execSQL("ALTER TABLE `tasks` ADD COLUMN `priority` TEXT NOT NULL DEFAULT 'MEDIUM'")
                }
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "dueordie_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
