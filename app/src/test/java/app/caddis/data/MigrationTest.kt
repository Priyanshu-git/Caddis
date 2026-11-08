package app.caddis.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.caddis.data.db.CaddisDatabase
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
 * Builds a version-1 database from the exported schema in `app/schemas`, then opens it with Room and
 * the shipped migrations. Room checks every table against the current entities on open, so a
 * migration that drifts from the schema fails here.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun createVersion(version: Int, name: String) {
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
            db.execSQL(
                """INSERT INTO items (id, type, url, normalizedUrl, platform, kind, title, description, creator, thumbnailUrl,
                   lengthSeconds, note, intentId, intentSource, reason, sourceApp, sourceFrom, status, createdAt, updatedAt,
                   doneAt, deletedAt, returnRule, returnAt, lastSurfacedAt, surfacedCount, lastOpenedAt, lastReviewedAt,
                   detailsFetchedAt, userEdited, revision)
                   VALUES ('a', 'LINK', 'https://e.com', 'e.com', 'WEB', 'WEBPAGE', 'Kept', '', '', NULL, NULL, '', 'read',
                   'GUESSED', '', '', '', 'ACTIVE', 1, 1, NULL, NULL, 'DEFAULT', NULL, NULL, 0, NULL, NULL, NULL, '', 1)""",
            )
            db.version = version
        }
    }

    @Test
    fun `version 1 saves keep everything and gain an empty favicon`() = runBlocking {
        createVersion(1, "migrate.db")
        val db = Room.databaseBuilder(context, CaddisDatabase::class.java, "migrate.db")
            .addMigrations(*CaddisDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            val item = db.items().observe("a").first()!!
            assertEquals("Kept", item.title)
            assertNull(item.faviconUrl)
        } finally {
            db.close()
        }
    }
}
