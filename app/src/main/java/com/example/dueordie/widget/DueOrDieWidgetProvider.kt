package com.example.dueordie.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import com.example.dueordie.DueOrDieApplication
import com.example.dueordie.MainActivity
import com.example.dueordie.R
import com.example.dueordie.data.model.TaskStatus
import com.example.dueordie.util.TimeFormatUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class DueOrDieWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val app = context.applicationContext as? DueOrDieApplication ?: return
            val repository = app.taskRepository

            CoroutineScope(Dispatchers.IO).launch {
                val tasks = repository.allTasks.firstOrNull() ?: emptyList()
                val now = System.currentTimeMillis()

                val activeTasks = tasks.filter { it.status != TaskStatus.COMPLETED }
                val nextTask = activeTasks.minByOrNull { it.deadlineTimestamp }

                val views = RemoteViews(context.packageName, R.layout.widget_due_or_die)

                // Intent to launch MainActivity when clicking the widget
                val intent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

                if (nextTask != null) {
                    val isOverdue = nextTask.isOverdue || nextTask.deadlineTimestamp < now
                    val countdownStr = TimeFormatUtils.formatCountdown(nextTask.deadlineTimestamp, now)

                    views.setTextViewText(R.id.widget_task_title, nextTask.title)
                    views.setTextViewText(R.id.widget_countdown_text, countdownStr)

                    if (isOverdue) {
                        views.setTextViewText(R.id.widget_status_badge, "🚨 OVERDUE")
                        views.setTextColor(R.id.widget_status_badge, Color.parseColor("#FF5252"))
                        views.setTextColor(R.id.widget_countdown_text, Color.parseColor("#FF5252"))
                    } else {
                        views.setTextViewText(R.id.widget_status_badge, "NEXT TASK")
                        views.setTextColor(R.id.widget_status_badge, Color.parseColor("#00E676"))
                        views.setTextColor(R.id.widget_countdown_text, Color.parseColor("#00E676"))
                    }
                } else {
                    views.setTextViewText(R.id.widget_task_title, "No active tasks")
                    views.setTextViewText(R.id.widget_countdown_text, "All deadlines met! Create a task to stay focused.")
                    views.setTextViewText(R.id.widget_status_badge, "CLEAR")
                    views.setTextColor(R.id.widget_status_badge, Color.parseColor("#AAAAAA"))
                    views.setTextColor(R.id.widget_countdown_text, Color.parseColor("#AAAAAA"))
                }

                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, DueOrDieWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId)
            }
        }
    }
}
