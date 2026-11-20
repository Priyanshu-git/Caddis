package com.nexxlabs.caddis.ui

import android.content.Intent
import com.nexxlabs.caddis.ui.settings.AppLinks
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The three outbound links in Settings: Rate, Share and Send feedback. */
@RunWith(RobolectricTestRunner::class)
class AppLinksTest {

    @Test
    fun rate_opensCaddisOnGooglePlay() {
        val i = AppLinks.rate()

        assertEquals(Intent.ACTION_VIEW, i.action)
        assertEquals("https://play.google.com/store/apps/details?id=com.nexxlabs.caddis", i.data.toString())
    }

    @Test
    fun share_sendsAPitchAndThePlayLink_andNothingFromTheLibrary() {
        val chooser = AppLinks.share()
        val send = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!

        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("text/plain", send.type)
        val text = send.getStringExtra(Intent.EXTRA_TEXT)!!
        assertTrue(text.contains("https://play.google.com/store/apps/details?id=com.nexxlabs.caddis"))
        assertFalse(send.hasExtra(Intent.EXTRA_STREAM))
    }

    @Test
    fun feedback_isABlankEmailToTheCaddisInbox() {
        val i = AppLinks.feedback()

        assertEquals(Intent.ACTION_SENDTO, i.action)
        assertEquals("mailto:", i.data.toString())
        assertArrayEquals(arrayOf("nexxlabs.apps@gmail.com"), i.getStringArrayExtra(Intent.EXTRA_EMAIL))
        assertEquals("Caddis feedback", i.getStringExtra(Intent.EXTRA_SUBJECT))
        assertFalse(i.hasExtra(Intent.EXTRA_TEXT))
    }

    @Test
    fun notificationSettings_opensCaddisOwnPageInAndroidSettings() {
        val i = AppLinks.notificationSettings("com.nexxlabs.caddis")

        assertEquals(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS, i.action)
        assertEquals("com.nexxlabs.caddis", i.getStringExtra(android.provider.Settings.EXTRA_APP_PACKAGE))
    }
}
