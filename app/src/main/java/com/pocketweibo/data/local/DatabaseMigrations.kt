package com.pocketweibo.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Incremental migrations so app updates preserve local data.
 * Version 3 was a version-only bump relative to v2 (same tables/columns).
 */
object DatabaseMigrations {

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // No-op: schema unchanged from v2; avoids destructive rebuild.
        }
    }
}
