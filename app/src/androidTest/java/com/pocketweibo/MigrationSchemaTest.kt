package com.pocketweibo

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pocketweibo.data.local.AppDatabase
import com.pocketweibo.data.local.DatabaseMigrations
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Roadmap P1 (REVIEW-2 §5): validates migrations against the exported schema JSONs in app/schemas.
 *
 * Schemas only exist from version 16 onward (exportSchema was off before), so older steps cannot be
 * checked with MigrationTestHelper yet. Each future migration N -> N+1 must add a test here.
 */
@RunWith(AndroidJUnit4::class)
class MigrationSchemaTest {

    private val dbName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun currentSchema_opensWithRegisteredMigrations() {
        helper.createDatabase(dbName, 16).close()
        Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
            dbName
        ).addMigrations(
            DatabaseMigrations.MIGRATION_15_16
        ).build().apply {
            openHelper.writableDatabase
            close()
        }
    }
}
