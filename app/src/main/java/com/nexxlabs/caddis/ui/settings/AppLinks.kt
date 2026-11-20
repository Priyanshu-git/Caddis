package com.nexxlabs.caddis.ui.settings

import android.content.Intent
import android.net.Uri

/** Where Caddis points people outside the app: its Google Play page and the feedback inbox. */
object AppLinks {
    const val PLAY_URL = "https://play.google.com/store/apps/details?id=com.nexxlabs.caddis"
    const val FEEDBACK_EMAIL = "nexxlabs.apps@gmail.com"
    const val FEEDBACK_SUBJECT = "Caddis feedback"
    const val SHARE_TEXT = "Caddis turns what you save into what you use.\n$PLAY_URL"

    /** Opens the Play listing (the Play app when it is installed) so the person can rate Caddis. */
    fun rate(): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_URL))

    /** The system share sheet with a one-line pitch and the Play link; nothing about the sender's library. */
    fun share(): Intent {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, SHARE_TEXT)
        return Intent.createChooser(send, "Share Caddis")
    }

    /** Caddis's own page in Android's notification settings, for when Android is blocking reminders. */
    fun notificationSettings(packageName: String): Intent =
        Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName)

    /** A blank email to the Caddis inbox. `mailto:` restricts the choice to email apps. */
    fun feedback(): Intent =
        Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
            .putExtra(Intent.EXTRA_EMAIL, arrayOf(FEEDBACK_EMAIL))
            .putExtra(Intent.EXTRA_SUBJECT, FEEDBACK_SUBJECT)
}
