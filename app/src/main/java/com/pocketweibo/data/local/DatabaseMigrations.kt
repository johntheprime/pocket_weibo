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
     * FK `ON DELETE SET NULL` (was CASCADE).
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
                CREATE TABLE IF NOT EXISTS `comments_new` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `postId` INTEGER NOT NULL,
                    `identityId` INTEGER,
                    `content` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `replyingToCommentId` INTEGER,
                    `likeCount` INTEGER NOT NULL,
                    `likedBy` TEXT NOT NULL,
                    FOREIGN KEY(`postId`) REFERENCES `posts_new`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
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
            db.execSQL(
                """
                INSERT INTO `comments_new` (`id`,`postId`,`identityId`,`content`,`createdAt`,`replyingToCommentId`,`likeCount`,`likedBy`)
                SELECT `id`,`postId`,`identityId`,`content`,`createdAt`,`replyingToCommentId`,`likeCount`,`likedBy` FROM `comments`
                """.trimIndent()
            )
            db.execSQL("DROP TABLE `comments`")
            db.execSQL("DROP TABLE `posts`")
            db.execSQL("ALTER TABLE `posts_new` RENAME TO `posts`")
            db.execSQL("ALTER TABLE `comments_new` RENAME TO `comments`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_posts_identityId` ON `posts` (`identityId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_comments_postId` ON `comments` (`postId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_comments_identityId` ON `comments` (`identityId`)")
            db.execSQL("PRAGMA foreign_keys=ON")
        }
    }
}
