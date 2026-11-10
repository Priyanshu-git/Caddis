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

    private fun insertItem(db: SQLiteDatabase, id: String, url: String?, platform: String, kind: String, type: String = "LINK", fetchedAt: Long? = null, title: String = "Kept") {
        db.execSQL(
            """INSERT INTO items (id, type, url, normalizedUrl, platform, kind, title, description, creator, thumbnailUrl,
               lengthSeconds, note, intentId, intentSource, reason, sourceApp, sourceFrom, status, createdAt, updatedAt,
               doneAt, deletedAt, returnRule, returnAt, lastSurfacedAt, surfacedCount, lastOpenedAt, lastReviewedAt,
               detailsFetchedAt, userEdited, revision)
               VALUES (?, ?, ?, ?, ?, ?, ?, '', '', NULL, NULL, '', 'read',
               'GUESSED', '', '', '', 'ACTIVE', 1, 1, NULL, NULL, 'DEFAULT', NULL, NULL, 0, NULL, NULL, ?, '', 1)""",
            arrayOf<Any?>(id, type, url, url?.removePrefix("https://"), platform, kind, title, fetchedAt),
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
    fun `version 2 saves end up named by their host, ready to be read again`() = runBlocking {
        createVersion(2, "migrate3.db") { db ->
            insertItem(db, "ig", "https://www.instagram.com/reel/Cx9/", "INSTAGRAM", "REEL", fetchedAt = 5)
            insertItem(db, "drive", "https://drive.google.com/file/d/1AbC/view", "WEB", "WEBPAGE")
            insertItem(db, "site", "https://www.nytimes.com/2025/story.html", "WEB", "WEBPAGE")
            insertItem(db, "pdf", "https://example.com/report.pdf", "WEB", "PDF")
            insertItem(db, "note", null, "NOTE", "NOTE", type = "NOTE")
            db.execSQL("INSERT INTO intent_corrections (`key`, intentId, count) VALUES ('INSTAGRAM:REEL', 'cook', 3), ('WEB:WEBPAGE', 'read', 2)")
        }
        val db = open("migrate3.db")
        try {
            suspend fun item(id: String) = db.items().observe(id).first()!!
            assertEquals("instagram.com", item("ig").platform.label)
            assertEquals("drive.google.com", item("drive").platform.label)
            assertEquals("nytimes.com", item("site").platform.label)
            assertEquals("example.com", item("pdf").platform.label)
            assertEquals(ContentKind.PDF, item("pdf").kind)
            assertEquals(ContentKind.REEL, item("ig").kind) // old kinds are kept
            assertEquals("note", item("note").platform.id)
            assertEquals("Kept", item("ig").title)
            assertNull(item("ig").detailsFetchedAt)
            assertEquals(setOf("instagram:REEL", "web:WEBPAGE"), db.intents().corrections().map { it.key }.toSet())
            assertEquals(3, db.intents().correction("instagram:REEL", "cook")!!.count)
        } finally {
            db.close()
        }
    }

    @Test
    fun `version 3 saves are named by their host and marked to be read again, notes and titles untouched`() = runBlocking {
        createVersion(3, "migrate4.db") { db ->
            insertItem(db, "chat", "https://chatgpt.com/share/6af2-1abc", "chatgpt.com", "WEBPAGE", fetchedAt = 7, title = "My plan")
            insertItem(db, "drive", "https://drive.google.com/file/d/1AbC/view", "google-drive", "FILE", fetchedAt = 7)
            insertItem(db, "m", "https://m.example.com/a", "example.com", "WEBPAGE", fetchedAt = 7)
            insertItem(db, "note", null, "note", "NOTE", type = "NOTE", fetchedAt = 7)
        }
        val db = open("migrate4.db")
        try {
            suspend fun item(id: String) = db.items().observe(id).first()!!
            assertEquals("chatgpt.com", item("chat").platform.label)
            assertNull(item("chat").detailsFetchedAt)
            assertEquals("My plan", item("chat").title)
            assertEquals("drive.google.com", item("drive").platform.label)
            assertEquals(ContentKind.FILE, item("drive").kind)
            assertEquals("example.com", item("m").platform.label)
            assertEquals("note", item("note").platform.id)
            assertEquals(7L, item("note").detailsFetchedAt?.toEpochMilli())
            assertEquals(listOf("chat", "drive", "m"), db.items().needingDetails().map { it.id }.sorted())
        } finally {
            db.close()
        }
    }
}
