package com.sheepspacefly.todomanager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

class AlarmReceiver : BroadcastReceiver() {
    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("AlarmDebug", "AlarmReceiver onReceive called")
        val title = intent.getStringExtra("title") ?: "待办提醒"
        val content = intent.getStringExtra("content") ?: "你有一个事项需要处理"

        val notificationHelper = NotificationHelper(context)
        notificationHelper.showNotification(title, content)
    }
}