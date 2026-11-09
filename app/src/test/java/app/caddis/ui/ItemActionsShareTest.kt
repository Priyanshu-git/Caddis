package app.caddis.ui

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import app.caddis.Fixture
import app.caddis.domain.model.ContentKind
import app.caddis.domain.model.Intents
import app.caddis.domain.model.Item
import app.caddis.domain.model.ItemType
import app.caddis.domain.model.Platform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class ItemActionsShareTest {
    private lateinit var f: Fixture
    private lateinit var app: Application
    private lateinit var actions: ItemActions

    private val link = Item(
        id = "i", type = ItemType.LINK, url = "https://youtu.be/abc123?si=tracker", normalizedUrl = "youtu.be/abc123",
        platform = Platform.YOUTUBE, kind = ContentKind.VIDEO, title = "How rivers carry stones",
        reason = "for my sister", intentId = Intents.KEEP, createdAt = Instant.EPOCH,
    )

    @Before fun setUp() {
        f = Fixture()
        app = ApplicationProvider.getApplicationContext()
        actions = ItemActions(app, f.repo, Messenger(), CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), f.clock)
    }

    @After fun tearDown() = f.close()

    @Test
    fun `share opens the chooser with the link, title and sign-off`() {
        actions.share(link)
        val chooser = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("text/plain", send.type)
        assertEquals("How rivers carry stones\nhttps://youtu.be/abc123\n\nShared via Caddis app", send.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals("How rivers carry stones", send.getStringExtra(Intent.EXTRA_SUBJECT))
    }

    @Test
    fun `share does nothing for a save without a link`() {
        actions.share(link.copy(type = ItemType.NOTE, url = null, platform = Platform.NOTE, kind = ContentKind.NOTE))
        assertNull(shadowOf(app).nextStartedActivity)
    }
}
