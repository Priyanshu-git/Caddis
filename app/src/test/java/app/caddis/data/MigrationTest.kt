package app.caddis.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.caddis.data.db.CaddisDatabase
import app.caddis.domain.model.ContentKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Builds an old database from the exported schema in `app/schemas`, then opens it with Room and
 * the shipped migrations. Room checks every table against the current entities on open, so a
 * migration that drifts from the schema fails here.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun insertItem(db: SQLiteDatabase, id: String, url: String?, platform: String, kind: String, type: String = "LINK") {
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

    private fun createVersion(version: Int, name: String, seed: (SQLiteDatabase) -> Unit) {
        val schema = JSONObject(File("schemas/app.caddis.data.db.CaddisDatabase/$version.json").readText()).getJSONObject("database")
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs(); delete() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                val table = e.getString("tableName")
                db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
                e.optJSONArray("indices")?.let { idx ->
                    for (j in 0 until idx.length()) db.execSQL(idx.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            seed(db)
            db.version = version
        }
    }

    private fun open(name: String) = Room.databaseBuilder(context, CaddisDatabase::class.java, name)
        .addMigrations(*CaddisDatabase.MIGRATIONS)
        .allowMainThreadQueries()
        .build()

    @Test
    fun `version 1 saves keep everything and gain an empty favicon`() = runBlocking {
        createVersion(1, "migrate.db") { insertItem(it, "a", "https://e.com", "WEB", "WEBPAGE") }
        val db = open("migrate.db")
        try {
            val item = db.items().observe("a").first()!!
            assertEquals("Kept", item.title)
            assertNull(item.faviconUrl)
            assertEquals("e.com", item.platform.id)
        } finally {
            db.close()
        }
    }

    @Test
    fun `version 2 platforms become ids, and plain web saves are read again from their link`() = runBlocking {
        createVersion(2, "migrate3.db") { db ->
            insertItem(db, "ig", "https://www.instagram.com/reel/Cx9/", "INSTAGRAM", "REEL")
            insertItem(db, "yt", "https://www.youtube.com/watch?v=x", "YOUTUBE", "VIDEO")
            insertItem(db, "drive", "https://drive.google.com/file/d/1AbC/view", "WEB", "WEBPAGE")
            insertItem(db, "site", "https://www.nytimes.com/2025/story.html", "WEB", "WEBPAGE")
            insertItem(db, "pdf", "https://example.com/report.pdf", "WEB", "PDF")
            insertItem(db, "note", null, "NOTE", "NOTE", type = "NOTE")
            db.execSQL("INSERT INTO intent_corrections (`key`, intentId, count) VALUES ('INSTAGRAM:REEL', 'cook', 3), ('WEB:WEBPAGE', 'read', 2)")
        }
        val db = open("migrate3.db")
        try {
            suspend fun platform(id: String) = db.items().observe(id).first()!!.platform.id
            assertEquals("instagram", platform("ig"))
            assertEquals("youtube", platform("yt"))
            assertEquals("google-drive", platform("drive"))
            assertEquals(ContentKind.FILE, db.items().observe("drive").first()!!.kind)
            assertEquals("nytimes.com", platform("site"))
            assertEquals("example.com", platform("pdf"))
            assertEquals(ContentKind.PDF, db.items().observe("pdf").first()!!.kind)
            assertEquals("note", platform("note"))
            assertEquals("Kept", db.items().observe("ig").first()!!.title)
            assertEquals(setOf("instagram:REEL", "web:WEBPAGE"), db.intents().corrections().map { it.key }.toSet())
            assertEquals(3, db.intents().correction("instagram:REEL", "cook")!!.count)
        } finally {
            db.close()
        }
    }
}
