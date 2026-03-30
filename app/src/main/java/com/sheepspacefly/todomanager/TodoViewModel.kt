package com.sheepspacefly.todomanager

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.LocalDateTime
import java.time.ZoneId

class TodoViewModel(context: Context) : ViewModel() {
    private val sharedPreferences = context.getSharedPreferences("todo_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    // 使用 Compose 状态管理，确保 UI 自动刷新
    var allTodos by mutableStateOf(loadTodos())
        private set

    // --- 数据持久化 ---
    private fun loadTodos(): List<TodoItem> {
        val json = sharedPreferences.getString("todo_list", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<TodoItem>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveTodos(todos: List<TodoItem>) {
        sharedPreferences.edit().putString("todo_list", gson.toJson(todos)).apply()
    }

    // 更新全局列表并保存
    fun updateTodos(newList: List<TodoItem>) {
        allTodos = newList
        saveTodos(allTodos)
    }

    // --- 核心业务逻辑 ---

    /**
     * 切换事项完成状态（包含递归逻辑）
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun toggleItemCompletion(targetItem: TodoItem, context: Context) {
        val newState = !targetItem.isCompleted
        val newList = allTodos.map { root ->
            if (root.id == targetItem.id) {
                toggleRecursive(root, newState)
            } else {
                root.copy(subItems = updateItemsRecursive(root.subItems, targetItem.id) {
                    toggleRecursive(it, newState)
                })
            }
        }.map { checkAutoCompletion(it) }

        updateTodos(newList)

        //更新闹钟
        newList.forEach { syncAlarmsRecursive(it, context) }
    }

    /**
     * 递归查找并更新特定 ID 的事项
     */
    fun updateItemsRecursive(
        list: List<TodoItem>?,
        targetId: Long,
        transform: (TodoItem) -> TodoItem
    ): List<TodoItem> {
        return list?.map { item ->
            if (item.id == targetId) transform(item)
            else item.copy(subItems = updateItemsRecursive(item.subItems, targetId, transform))
        } ?: emptyList()
    }

    /**
     * 递归设置子项状态（当父项被勾选，所有子项同步）
     */
    private fun toggleRecursive(item: TodoItem, completed: Boolean): TodoItem {
        return item.copy(
            isCompleted = completed,
            subItems = (item.subItems ?: emptyList()).map { toggleRecursive(it, completed) }
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun syncAlarmsRecursive(item: TodoItem, context: Context) {
        if (item.isCompleted) {
            cancelAlarm(context, item)
        } else if (item.isReminderEnabled) {
            // 只有未完成且开启了提醒的事项才需要设置闹钟
            scheduleAlarm(context, item)
        }

        // 递归处理子项
        item.subItems?.forEach { syncAlarmsRecursive(it, context) }
    }

    /**
     * 自动检查完成状态（当所有子项完成，父项自动标记完成）
     */
    private fun checkAutoCompletion(item: TodoItem): TodoItem {
        val subs = item.subItems ?: emptyList()
        val newSubItems = subs.map { checkAutoCompletion(it) }
        val allSubsDone = newSubItems.isNotEmpty() && newSubItems.all { it.isCompleted }

        return item.copy(
            subItems = newSubItems,
            isCompleted = if (newSubItems.isNotEmpty()) allSubsDone else item.isCompleted
        )
    }

    /**
     * 递归删除事项
     */
    fun deleteItem(targetId: Long) {
        val newList = removeItemRecursive(allTodos, targetId)
        updateTodos(newList)
    }

    private fun removeItemRecursive(list: List<TodoItem>?, targetId: Long): List<TodoItem> {
        return list?.filter { it.id != targetId }?.map {
            it.copy(subItems = removeItemRecursive(it.subItems, targetId))
        } ?: emptyList()
    }

    /**
     * 根据 ID 查找单个事项（用于编辑页初始化）
     */
    fun findItemById(id: Long): TodoItem? {
        fun search(list: List<TodoItem>): TodoItem? {
            for (item in list) {
                if (item.id == id) return item
                val found = search(item.subItems ?: emptyList())
                if (found != null) return found
            }
            return null
        }
        return search(allTodos)
    }

    @SuppressLint("ScheduleExactAlarm")
    @RequiresApi(Build.VERSION_CODES.O)
    fun scheduleAlarm(context: Context, item: TodoItem) {
        // 确保有必要的数据
        if (!item.isReminderEnabled || item.deadlineTime == null || item.date.isNullOrBlank()) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("title", "记得完成这件事哦！")
            putExtra("content", item.content)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            item.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            // 1. 解析日期 (item.date 格式通常为 "2023-10-27")
            val localDate = java.time.LocalDate.parse(item.date)

            // 2. 解析时间 (item.deadlineTime 格式为 "14:30")
            val timeParts = item.deadlineTime!!.split(":")
            val hour = timeParts[0].toInt()
            val minute = timeParts[1].toInt()

            // 3. 组合日期和时间，并减去提前提醒的分钟数
            val targetDateTime = localDate.atTime(hour, minute)
                .minusMinutes(item.reminderMinutesBefore.toLong())

            // 4. 转换为系统毫秒数
            val triggerAtMillis = targetDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

            // 5. 检查时间是否在未来
            if (triggerAtMillis > System.currentTimeMillis()) {
                Log.d("AlarmDebug", "设置闹钟成功: 事项[${item.content}] 提醒时间[$targetDateTime]")
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                Log.w("AlarmDebug", "提醒时间已过期，跳过设置: $targetDateTime")
            }
        } catch (e: Exception) {
            Log.e("AlarmDebug", "解析日期时间出错: ${e.message}")
        }
    }

    // 同样添加一个取消闹钟的函数
    fun cancelAlarm(context: Context, item: TodoItem) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            item.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}