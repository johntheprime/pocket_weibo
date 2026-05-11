package com.pocketweibo.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.pocketweibo.data.local.dao.CommentDao
import com.pocketweibo.data.local.dao.IdentityDao
import com.pocketweibo.data.local.dao.IdentityScheduleDao
import com.pocketweibo.data.local.dao.PostDao
import com.pocketweibo.data.local.dao.PostReminderDao
import com.pocketweibo.data.local.entity.CommentEntity
import com.pocketweibo.data.local.entity.IdentityEntity
import com.pocketweibo.data.local.entity.IdentityScheduleEntity
import com.pocketweibo.data.local.entity.PostEntity
import com.pocketweibo.data.local.entity.PostReminderEntity

@Database(
    entities = [IdentityEntity::class, PostEntity::class, CommentEntity::class, PostReminderEntity::class, IdentityScheduleEntity::class],
    version = 12,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun identityDao(): IdentityDao
    abstract fun identityScheduleDao(): IdentityScheduleDao
    abstract fun postDao(): PostDao
    abstract fun commentDao(): CommentDao
    abstract fun postReminderDao(): PostReminderDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pocketweibo_database"
                )
                    .addMigrations(
                        DatabaseMigrations.MIGRATION_2_3,
                        DatabaseMigrations.MIGRATION_3_4,
                        DatabaseMigrations.MIGRATION_4_5,
                        DatabaseMigrations.MIGRATION_5_6,
                        DatabaseMigrations.MIGRATION_6_7,
                        DatabaseMigrations.MIGRATION_7_8,
                        DatabaseMigrations.MIGRATION_8_9,
                        DatabaseMigrations.MIGRATION_9_10,
                        DatabaseMigrations.MIGRATION_10_11,
                        DatabaseMigrations.MIGRATION_11_12
                    )
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
