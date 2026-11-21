package com.nexxlabs.caddis.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nexxlabs.caddis.MainActivity
import com.nexxlabs.caddis.R
import com.nexxlabs.caddis.domain.details.formatLength
import com.nexxlabs.caddis.domain.model.Intent as CaddisIntent
import com.nexxlabs.caddis.domain.model.Item
import com.nexxlabs.caddis.domain.model.WeeklySummary
import com.nexxlabs.caddis.domain.schedule.ItemReminder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Notifier @Inject constructor(@ApplicationContext private val context: Context) {

    fun createChannels() {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_REMINDERS, context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.channel_reminders_desc) },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_YOURS, context.getString(R.string.channel_yours), NotificationManager.IMPORTANCE_HIGH)
                .apply { description = context.getString(R.string.channel_yours_desc) },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_WEEKLY, context.getString(R.string.channel_weekly), NotificationManager.IMPORTANCE_LOW)
                .apply { description = context.getString(R.string.channel_weekly_desc) },
        )
    }

    fun canPost(): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** "Brown butter cookies · Cook · 12 min · fits this morning" */
    fun showPick(item: Item, intents: List<CaddisIntent>): Boolean {
        if (!canPost()) return false
        val intentName = intents.firstOrNull { it.id == item.intentId }?.name ?: ""
        val text = listOf(intentName, formatLength(item.lengthSeconds, item.kind.isVideo).ifBlank { item.kind.label })
            .filter { it.isNotBlank() }.joinToString(" · ")
        val n = NotificationCompat.Builder(context, CH_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(item.displayTitle)
            .setContentText(text)
            .setContentIntent(open(MainActivity.ROUTE_ITEM_PREFIX + item.id, REQ_PICK))
            .setAutoCancel(true)
            .build()
        return post(ID_PICK, n)
    }

    /**
     * A reminder the person set for [item]. It is not part of the one-a-day budget: they asked for it.
     * Tapping it (or Open) opens the item; "In 1 hour" and "Tomorrow" move the reminder without opening the app.
     */
    fun showReminder(item: Item, firedAt: Instant): Boolean {
        if (!canPost()) return false
        val at = item.returnAt ?: return false
        val id = reminderNotificationId(item.id)
        val route = (if (item.isNote) "note/" else MainActivity.ROUTE_ITEM_PREFIX) + item.id
        val open = openReminder(route, id)
        val n = NotificationCompat.Builder(context, CH_YOURS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(item.displayTitle)
            .setContentText(ItemReminder.notificationText(at, firedAt, ZoneId.systemDefault()))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setWhen(at.toEpochMilli())
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.reminder_action_open), open)
            .addAction(0, context.getString(R.string.reminder_action_hour), reminderAction(ReminderReceiver.ACTION_IN_AN_HOUR, item.id, id))
            .addAction(0, context.getString(R.string.reminder_action_tomorrow), reminderAction(ReminderReceiver.ACTION_TOMORROW, item.id, id))
            .build()
        return post(id, n)
    }

    fun cancelReminder(itemId: String) = NotificationManagerCompat.from(context).cancel(reminderNotificationId(itemId))

    private fun openReminder(route: String, notificationId: Int): PendingIntent {
        val i = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_ROUTE, route)
            .putExtra(MainActivity.EXTRA_CANCEL_NOTIFICATION, notificationId)
            .setData(Uri.parse("caddis-reminder://open/$notificationId"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, notificationId, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun reminderAction(action: String, itemId: String, notificationId: Int): PendingIntent {
        val i = Intent(context, ReminderReceiver::class.java)
            .setAction(action)
            .putExtra(ReminderReceiver.EXTRA_ITEM_ID, itemId)
            .setData(Uri.parse("caddis-reminder://$action/$notificationId"))
        return PendingIntent.getBroadcast(context, notificationId, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun showWeekly(s: WeeklySummary): Boolean {
        if (!canPost()) return false
        val n = NotificationCompat.Builder(context, CH_WEEKLY)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Your week in Caddis")
            .setContentText("${s.savedCount} saved · ${s.usedCount} used · ${s.deletedCount} deleted")
            .setContentIntent(open(MainActivity.ROUTE_WEEK, REQ_WEEK))
            .setAutoCancel(true)
            .build()
        return post(ID_WEEK, n)
    }

    private fun post(id: Int, n: android.app.Notification): Boolean = try {
        NotificationManagerCompat.from(context).notify(id, n)
        true
    } catch (e: SecurityException) {
        false
    }

    private fun open(route: String, req: Int): PendingIntent {
        val i = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_ROUTE, route)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, req, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    companion object {
        const val CH_REMINDERS = "reminders"
        const val CH_WEEKLY = "weekly"
        const val CH_YOURS = "your_reminders"

        /** One notification per item, clear of the pick (1) and the weekly summary (2). */
        fun reminderNotificationId(itemId: String): Int = 1000 + (itemId.hashCode() and 0xFFFFF)

        private const val ID_PICK = 1
        private const val ID_WEEK = 2
        private const val REQ_PICK = 10
        private const val REQ_WEEK = 11
    }
}
