package com.mydash.planner

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import java.util.Calendar

object Reminders {
    const val CH = "deadlines"

    private fun pi(ctx: Context, item: Item): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java)
            .putExtra("title", item.title).putExtra("kind", item.kind).putExtra("id", item.id)
        return PendingIntent.getBroadcast(
            ctx, (item.id % 1_000_000_000L).toInt(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun cancel(ctx: Context, item: Item) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pi(ctx, item))
    }

    fun schedule(ctx: Context, item: Item) {
        cancel(ctx, item)
        if (item.done || item.due <= 0L || (item.kind != "task" && item.kind != "goal")) return
        val c = Calendar.getInstance().apply {
            timeInMillis = item.due
            set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= System.currentTimeMillis()) return
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.set(AlarmManager.RTC_WAKEUP, c.timeInMillis, pi(ctx, item))
    }

    fun scheduleAll(ctx: Context, items: List<Item>) = items.forEach { schedule(ctx, it) }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(Reminders.CH, "Deadlines", NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val title = intent.getStringExtra("title") ?: "Task"
        val kind = intent.getStringExtra("kind") ?: "task"
        val n = Notification.Builder(ctx, Reminders.CH)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(if (kind == "goal") "Goal deadline today" else "Due today")
            .setContentText(title)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify((intent.getLongExtra("id", 0L) % 1_000_000_000L).toInt(), n)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) Reminders.scheduleAll(ctx, Store.load(ctx))
    }
}

class TasksWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) mgr.updateAppWidget(id, build(ctx))
    }

    companion object {
        fun build(ctx: Context): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_tasks)
            val e = dayEnd(0)
            val tasks = Store.load(ctx)
                .filter { it.kind == "task" && !it.done && it.due in 1..e }
                .sortedWith(compareBy<Item>({ -it.priority }, { it.due }))
            val rows = intArrayOf(R.id.w1, R.id.w2, R.id.w3, R.id.w4, R.id.w5)
            rows.forEachIndexed { i, r ->
                if (i < tasks.size) {
                    v.setTextViewText(r, "- " + tasks[i].title)
                    v.setViewVisibility(r, View.VISIBLE)
                } else v.setViewVisibility(r, View.GONE)
            }
            v.setTextViewText(R.id.w_count, "${tasks.size} left")
            v.setViewVisibility(R.id.w_empty, if (tasks.isEmpty()) View.VISIBLE else View.GONE)
            val open = PendingIntent.getActivity(ctx, 1, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            v.setOnClickPendingIntent(R.id.w_root, open)
            return v
        }

        fun refresh(ctx: Context) {
            val m = AppWidgetManager.getInstance(ctx)
            val ids = m.getAppWidgetIds(ComponentName(ctx, TasksWidget::class.java))
            for (id in ids) m.updateAppWidget(id, build(ctx))
        }
    }
}
