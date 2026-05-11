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

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE posts ADD COLUMN extrasJson TEXT NOT NULL DEFAULT '{}'"
            )
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `post_reminders` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `postId` INTEGER NOT NULL,
                    `fireAtMillis` INTEGER NOT NULL,
                    FOREIGN KEY(`postId`) REFERENCES `posts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_post_reminders_postId` ON `post_reminders` (`postId`)")
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE post_reminders ADD COLUMN repeatRule TEXT NOT NULL DEFAULT 'NONE'"
            )
        }
    }

    /**
     * Orphan posts/comments when an identity is deleted: nullable [posts.identityId] / [comments.identityId],
     * FK `ON DELETE SET_NULL` (was CASCADE).
     *
     * **Important:** Do not create `comments` with `REFERENCES posts_new` then rename `posts_new` — SQLite
     * keeps a stale parent table name and the DB can fail to open. Copy comments to a temp table, swap
     * `posts`, then recreate `comments` with `FOREIGN KEY … REFERENCES posts(id)`.
     */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("PRAGMA foreign_keys=OFF")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `posts_new` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `identityId` INTEGER,
                    `content` TEXT NOT NULL,
                    `imageUris` TEXT NOT NULL,
                    `extrasJson` TEXT NOT NULL DEFAULT '{}',
                    `createdAt` INTEGER NOT NULL,
                    `likeCount` INTEGER NOT NULL,
                    `commentCount` INTEGER NOT NULL,
                    `isLiked` INTEGER NOT NULL,
                    FOREIGN KEY(`identityId`) REFERENCES `identities`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `posts_new` (`id`,`identityId`,`content`,`imageUris`,`extrasJson`,`createdAt`,`likeCount`,`commentCount`,`isLiked`)
                SELECT `id`,`identityId`,`content`,`imageUris`,`extrasJson`,`createdAt`,`likeCount`,`commentCount`,`isLiked` FROM `posts`
                """.trimIndent()
            )
            db.execSQL("DROP TABLE IF EXISTS `comments_mig_backup`")
            db.execSQL("CREATE TABLE `comments_mig_backup` AS SELECT * FROM `comments`")
            db.execSQL("DROP TABLE `comments`")
            db.execSQL("DROP TABLE `posts`")
            db.execSQL("ALTER TABLE `posts_new` RENAME TO `posts`")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `comments_new` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `postId` INTEGER NOT NULL,
                    `identityId` INTEGER,
                    `content` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `replyingToCommentId` INTEGER,
                    `likeCount` INTEGER NOT NULL,
                    `likedBy` TEXT NOT NULL,
                    FOREIGN KEY(`postId`) REFERENCES `posts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`identityId`) REFERENCES `identities`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `comments_new` (`id`,`postId`,`identityId`,`content`,`createdAt`,`replyingToCommentId`,`likeCount`,`likedBy`)
                SELECT `id`,`postId`,`identityId`,`content`,`createdAt`,`replyingToCommentId`,`likeCount`,`likedBy` FROM `comments_mig_backup`
                """.trimIndent()
            )
            db.execSQL("DROP TABLE `comments_mig_backup`")
            db.execSQL("ALTER TABLE `comments_new` RENAME TO `comments`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_posts_identityId` ON `posts` (`identityId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_comments_postId` ON `comments` (`postId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_comments_identityId` ON `comments` (`identityId`)")
            db.execSQL("PRAGMA foreign_keys=ON")
        }
    }

    /**
     * Repair DBs that already ran the broken 6→7 script (`comments` referenced renamed `posts_new`).
     * Safe on a healthy v7 schema (recreates `comments` with correct FKs to `posts`).
     */
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("PRAGMA foreign_keys=OFF")
            db.execSQL("DROP TABLE IF EXISTS `comments_mig_r8`")
            db.execSQL("CREATE TABLE `comments_mig_r8` AS SELECT * FROM `comments`")
            db.execSQL("DROP TABLE `comments`")
            db.execSQL(
                """
                CREATE TABLE `comments` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `postId` INTEGER NOT NULL,
                    `identityId` INTEGER,
                    `content` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `replyingToCommentId` INTEGER,
                    `likeCount` INTEGER NOT NULL,
                    `likedBy` TEXT NOT NULL,
                    FOREIGN KEY(`postId`) REFERENCES `posts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`identityId`) REFERENCES `identities`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `comments` (`id`,`postId`,`identityId`,`content`,`createdAt`,`replyingToCommentId`,`likeCount`,`likedBy`)
                SELECT `id`,`postId`,`identityId`,`content`,`createdAt`,`replyingToCommentId`,`likeCount`,`likedBy` FROM `comments_mig_r8`
                """.trimIndent()
            )
            db.execSQL("DROP TABLE `comments_mig_r8`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_comments_postId` ON `comments` (`postId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_comments_identityId` ON `comments` (`identityId`)")
            db.execSQL("PRAGMA foreign_keys=ON")
        }
    }

    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE posts ADD COLUMN audioPath TEXT NOT NULL DEFAULT ''")
        }
    }

    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE comments ADD COLUMN audioPath TEXT NOT NULL DEFAULT ''")
        }
    }

    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE posts ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `identity_schedules` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `identityId` INTEGER NOT NULL,
                    `hour` INTEGER NOT NULL,
                    `minute` INTEGER NOT NULL,
                    `daysOfWeek` TEXT NOT NULL DEFAULT '',
                    `enabled` INTEGER NOT NULL DEFAULT 1,
                    FOREIGN KEY(`identityId`) REFERENCES `identities`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_identity_schedules_identityId` ON `identity_schedules` (`identityId`)")
        }
    }
}
