package app.caddis.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.caddis.data.db.CaddisDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The shipped migrations on a real device's SQLite (the JVM tests run them on Robolectric's).
 * Room validates every table against the exported schema after each migration.
 */
@RunWith(AndroidJUnit4::class)
class MigrationInstrumentedTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), CaddisDatabase::class.java)

    private fun insertItem(db: SupportSQLiteDatabase, id: String, url: String?, platform: String, kind: String, type: String = "LINK") {
        db.execSQL(
            """INSERT INTO items (id, type, url, normalizedUrl, platform, kind, title, description, creator, thumbnailUrl,
               lengthSeconds, note, intentId, intentSource, reason, sourceApp, sourceFrom, status, createdAt, updatedAt,
               doneAt, deletedAt, returnRule, returnAt, lastSurfacedAt, surfacedCount, lastOpenedAt, lastReviewedAt,
               detailsFetchedAt, userEdited, revision)
               VALUES (?, ?, ?, ?, ?, ?, 'Kept', '', '', NULL, NULL, '', 'read',
               'GUESSED', '', '', '', 'ACTIVE', 1, 1, NULL, NULL, 'DEFAULT', NULL, NULL, 0, NULL, NULL, NULL, '', 1)""",
            arrayOf<Any?>(id, type, url, url?.removePrefix("https://"), platform, kind),
        )
    }

    private fun SupportSQLiteDatabase.platformAndKind(id: String): Pair<String, String> =
        query("SELECT platform, kind FROM items WHERE id = ?", arrayOf(id)).use { c ->
            c.moveToFirst()
            c.getString(0) to c.getString(1)
        }

    private fun SupportSQLiteDatabase.fetchedAt(id: String): Long? =
        query("SELECT detailsFetchedAt FROM items WHERE id = ?", arrayOf(id)).use { c ->
            c.moveToFirst()
            if (c.isNull(0)) null else c.getLong(0)
        }

    @Test
    fun version2SavesEndUpNamedByTheirHost() {
        helper.createDatabase(NAME, 2).use { db ->
            insertItem(db, "ig", "https://www.instagram.com/reel/Cx9/", "INSTAGRAM", "REEL")
            insertItem(db, "drive", "https://drive.google.com/file/d/1AbC/view", "WEB", "WEBPAGE")
            insertItem(db, "site", "https://www.nytimes.com/2025/story.html", "WEB", "WEBPAGE")
            insertItem(db, "note", null, "NOTE", "NOTE", type = "NOTE")
            db.execSQL("INSERT INTO intent_corrections (`key`, intentId, count) VALUES ('INSTAGRAM:REEL', 'cook', 3), ('WEB:WEBPAGE', 'read', 2)")
        }

        helper.runMigrationsAndValidate(NAME, 4, true, *CaddisDatabase.MIGRATIONS).use { db ->
            assertEquals("instagram.com" to "REEL", db.platformAndKind("ig"))
            assertEquals("drive.google.com" to "WEBPAGE", db.platformAndKind("drive"))
            assertEquals("nytimes.com" to "WEBPAGE", db.platformAndKind("site"))
            assertEquals("note" to "NOTE", db.platformAndKind("note"))
            val keys = db.query("SELECT `key` FROM intent_corrections").use { c ->
                buildSet { while (c.moveToNext()) add(c.getString(0)) }
            }
            assertEquals(setOf("instagram:REEL", "web:WEBPAGE"), keys)
        }
    }

    @Test
    fun version3SavesAreRenamedToTheirHostAndMarkedToBeReadAgain() {
        helper.createDatabase(NAME, 3).use { db ->
            insertItem(db, "chat", "https://chatgpt.com/share/6af2-1abc", "chatgpt.com", "WEBPAGE")
            insertItem(db, "note", null, "note", "NOTE", type = "NOTE")
            db.execSQL("UPDATE items SET detailsFetchedAt = 7")
        }
        helper.runMigrationsAndValidate(NAME, 4, true, *CaddisDatabase.MIGRATIONS).use { db ->
            assertEquals("chatgpt.com" to "WEBPAGE", db.platformAndKind("chat"))
            assertEquals(null, db.fetchedAt("chat"))
            assertEquals(7L, db.fetchedAt("note"))
        }
    }

    @Test
    fun everyVersionMigratesToTheCurrentSchema() {
        helper.createDatabase(NAME, 1).use { db -> insertItem(db, "a", "https://e.com", "WEB", "WEBPAGE") }
        helper.runMigrationsAndValidate(NAME, 4, true, *CaddisDatabase.MIGRATIONS).use { db ->
            assertEquals("e.com" to "WEBPAGE", db.platformAndKind("a"))
        }
    }

    private companion object {
        const val NAME = "migration-test"
    }
}
