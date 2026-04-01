package com.sheepspacefly.todomanager

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.sheepspacefly.todomanager.ui.theme.Colors
import android.content.Context
import androidx.compose.foundation.interaction.MutableInteractionSource
import java.time.LocalDate

@SuppressLint("NewApi")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TodoEditScreen(
    navController: NavController,
    itemId: Long,
    parentId: Long,
    depth: Int,
    viewModel: TodoViewModel,
    date: String = LocalDate.now().toString()
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    // 1. 初始化数据：如果是编辑模式，从 ViewModel 查找现有事项
    val existingItem = remember(itemId) { viewModel.findItemById(itemId) }

    // 2. 状态定义
    var content by remember { mutableStateOf(existingItem?.content ?: "") }
    var isReminderEnabled by remember { mutableStateOf(existingItem?.isReminderEnabled ?: false) }
    var deadlineTime by remember { mutableStateOf(existingItem?.deadlineTime ?: "12:00") }
    var reminderMinutes by remember { mutableStateOf(existingItem?.reminderMinutesBefore ?: 0) }

    var subItems by remember { mutableStateOf(existingItem?.subItems ?: emptyList()) }
    var newSubItemContent by remember { mutableStateOf("") }

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val reminderOptions = listOf(
        "截止时" to 0,
        "提前5分钟" to 5,
        "提前10分钟" to 10,
        "提前30分钟" to 30,
        "提前1小时" to 60,
        "提前1天" to 1440
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (itemId == -1L) "新增事项" else "编辑事项", color = Colors.DarkText) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Colors.DividerColor),
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null, tint = Colors.DarkText)
                    }
                },
                actions = {
                    // 如果是编辑模式，显示删除按钮
                    if (itemId != -1L) {
                        IconButton(onClick = { showDeleteConfirmDialog = true }) {
                            Icon(Icons.Default.Delete, "删除", tint = Colors.AmberYellow)
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface (
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(30.dp, 20.dp),
                color = Color.White
            ){
                val context = androidx.compose.ui.platform.LocalContext.current

                // 保存按钮
                Button(
                    onClick = {
                        if (content.isNotBlank()) {
                            saveTodo(
                                context = context,
                                viewModel = viewModel,
                                itemId = itemId,
                                parentId = parentId,
                                content = content,
                                isReminderEnabled = isReminderEnabled,
                                date = date,
                                deadlineTime = deadlineTime,
                                reminderMinutes = reminderMinutes,
                                subItems = subItems
                            )
                            navController.popBackStack()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Colors.DarkText
                    ),
                    enabled = content.isNotBlank()
                ) {
                    Text("保存事项", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color.White)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // 内容输入框
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("想要做什么？") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    focusedLabelColor = Colors.AmberYellow,
                    cursorColor = MaterialTheme.colorScheme.primary
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 提醒开关卡片
            Surface(
                color = Colors.LightGray,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Notifications, null, tint = Colors.AmberYellow)
                    Text("开启时间提醒", modifier = Modifier
                        .padding(start = 12.dp)
                        .weight(1f), color = Colors.DarkText)
                    CompositionLocalProvider(LocalRippleConfiguration provides null) {
                        Switch(
                            checked = isReminderEnabled,
                            onCheckedChange = { isReminderEnabled = it },
                            interactionSource = remember { MutableInteractionSource() },
                            colors = SwitchDefaults.colors(
                                uncheckedBorderColor = Colors.BoldGray,
                                checkedBorderColor = MaterialTheme.colorScheme.primary,
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.secondary
                            )
                        )
                    }
                }
            }

            // 提醒详细设置区
            if (isReminderEnabled) {
                Spacer(modifier = Modifier.height(24.dp))

                Text("截止时间", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    color = Color.White,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 解析当前时间
                        val parts = deadlineTime.split(":")
                        var hour24 = parts[0].toInt()
                        val minute = parts[1].toInt()

                        // 第一列：小时 (00-23)
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            WheelPicker(
                                items = (0..23).map { String.format("%02d", it) },
                                startIndex = hour24,
                                onItemSelected = { index ->
                                    deadlineTime = String.format("%02d:%02d", index, minute)
                                },
                                modifier = Modifier.fillMaxSize() // 填充整个左侧区域，扩大滑动范围
                            )
                            // 将单位固定在右侧，不参与滚动但也不遮挡滑动
                            Text("时", fontSize = 12.sp, color = Colors.DarkText, fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 16.dp))
                        }

                        VerticalDivider(
                            modifier = Modifier.height(120.dp),
                            thickness = 1.dp,
                            color = Colors.SoftGray
                        )

                        // 第二列：分钟 (00-59)
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            WheelPicker(
                                items = (0..59).map { String.format("%02d", it) },
                                startIndex = minute,
                                onItemSelected = { index ->
                                    deadlineTime = String.format("%02d:%02d", hour24, index)
                                },
                                modifier = Modifier.fillMaxSize() // 填充整个右侧区域
                            )
                            Text("分", fontSize = 12.sp, color = Colors.DarkText, fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 16.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text("提醒时间", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    reminderOptions.forEach { (label, mins) ->
                        FilterChip(
                            selected = reminderMinutes == mins,
                            onClick = { reminderMinutes = mins },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Colors.DarkText
                            )
                        )
                    }
                }
            }

            if(depth < 2){
                Spacer(modifier = Modifier.height(32.dp))
                Text("子事项", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))

                subItems.forEachIndexed { index, subItem ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(subItem.content, modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp), color = Colors.DarkText)
                        IconButton(onClick = { subItems = subItems.filterIndexed { i, _ -> i != index } }) {
                            Icon(Icons.Default.Close, "删除子项", tint = Color.Gray, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = newSubItemContent,
                        onValueChange = { newSubItemContent = it },
                        placeholder = { Text("添加子事项...", fontSize = 14.sp) },
                        modifier = Modifier.weight(1f),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                        ),
                        singleLine = true
                    )
                    IconButton(
                        onClick = {
                            if (newSubItemContent.isNotBlank()) {
                                val newSub = TodoItem(
                                    id = System.currentTimeMillis() + subItems.size,
                                    content = newSubItemContent,
                                    isCompleted = false,
                                    date = date
                                )
                                subItems = subItems + newSub
                                newSubItemContent = ""
                            }
                        }
                    ) {
                        Icon(Icons.Default.Add, "添加", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // --- 删除确认弹窗组件 ---
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false }, // 点击弹窗外部关闭
            title = { Text("确认删除", fontWeight = FontWeight.Bold) },
            text = { Text("确定要删除这个事项吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        existingItem?.let { viewModel.cancelAlarm(context, it) }
                        viewModel.deleteItem(itemId)
                        showDeleteConfirmDialog = false
                        navController.popBackStack() // 删除后返回主页
                    }
                ) {
                    Text("删除", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("取消", color = Colors.DarkText)
                }
            },
            containerColor = Color.White,
            shape = MaterialTheme.shapes.medium
        )
    }
}

@Composable
fun WheelPicker(
    items: List<String>,
    startIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val itemHeight = 40.dp
    val visibleCount = 3
    // 1. 移除 totalItems 的循环逻辑，先用简单列表测试稳定性，或者保留但确保计算正确
    val totalItems = if (items.size > 1) 10000 else items.size

    val initialIndex = remember(items, startIndex) {
        if (items.size > 1) {
            (totalItems / 2) - (totalItems / 2 % items.size) + startIndex
        } else {
            startIndex
        }
    }

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    var lastSelectedIndex by remember { mutableIntStateOf(startIndex) }

    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val layoutInfo = listState.layoutInfo
            // 视口的物理中心点
            val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f

            // 找到距离视口中心最近的 Item
            val closestItem = layoutInfo.visibleItemsInfo.minByOrNull {
                val itemCenter = it.offset + (it.size / 2f)
                Math.abs(itemCenter - viewportCenter)
            }

            closestItem?.let {
                val itemCenter = it.offset + (it.size / 2f)
                val distanceToCenter = itemCenter - viewportCenter

                // 这里的 currentIndex 计算要排除掉我们添加的顶部占位符
                val currentIndex = (it.index - 1) % items.size

                // 排除占位符的点击/选中逻辑
                if (it.index > 0 && it.index <= totalItems) {
                    if (Math.abs(distanceToCenter) > 1f) {
                        listState.animateScrollToItem(it.index - 1) // 减1是因为我们要让该项的上方留出占位空间
                    }

                    if (currentIndex != lastSelectedIndex && currentIndex >= 0) {
                        lastSelectedIndex = currentIndex
                        onItemSelected(currentIndex)
                    }
                }
            }
        }
    }

    Box(modifier = modifier.height(itemHeight * visibleCount), contentAlignment = Alignment.Center) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            // 【关键修改 1】：移除 contentPadding，改用 items 里的 Spacer
        ) {
            // 顶部占位
            item { Spacer(modifier = Modifier.height(itemHeight)) }

            items(totalItems) { index ->
                val item = items[index % items.size]

                // 【关键修改 2】：简化选中判断逻辑
                val isSelected = remember {
                    derivedStateOf {
                        val layoutInfo = listState.layoutInfo
                        val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
                        val itemInfo = layoutInfo.visibleItemsInfo.find { it.index == index + 1 }
                        if (itemInfo != null) {
                            val itemCenter = itemInfo.offset + itemInfo.size / 2f
                            Math.abs(itemCenter - viewportCenter) < itemHeight.value / 2f
                        } else false
                    }
                }.value

                Box(
                    modifier = Modifier
                        .height(itemHeight)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item,
                        fontSize = if (isSelected) 30.sp else 20.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) Colors.DarkText else Color.Gray.copy(alpha = 0.6f)
                    )
                }
            }

            // 底部占位
            item { Spacer(modifier = Modifier.height(itemHeight)) }
        }
    }
}


/**
 * 处理保存逻辑的辅助函数
 */
/**
 * 处理保存逻辑的辅助函数
 */
@SuppressLint("NewApi")
private fun saveTodo(
    context: Context, // 新增 context 参数
    viewModel: TodoViewModel,
    itemId: Long,
    parentId: Long,
    content: String,
    isReminderEnabled: Boolean,
    date:String,
    deadlineTime: String,
    reminderMinutes: Int,
    subItems: List<TodoItem>
) {
    val currentTodos = viewModel.allTodos
    var savedItem: TodoItem? = null // 用于记录最终保存的对象以设置闹钟

    if (itemId == -1L) {
        // 新增模式
        val newItem = TodoItem(
            content = content,
            date = date,
            isReminderEnabled = isReminderEnabled,
            deadlineTime = if (isReminderEnabled) deadlineTime else null,
            reminderMinutesBefore = reminderMinutes,
            subItems = subItems
        )
        savedItem = newItem

        if (parentId == -1L) {
            viewModel.updateTodos(currentTodos + newItem)
        } else {
            val newList = viewModel.updateItemsRecursive(currentTodos, parentId) { parent ->
                parent.copy(subItems = (parent.subItems ?: emptyList()) + newItem)
            }
            viewModel.updateTodos(newList)
        }
    } else {
        // 编辑模式
        val newList = viewModel.updateItemsRecursive(currentTodos, itemId) { oldItem ->
            val updated = oldItem.copy(
                content = content,
                isReminderEnabled = isReminderEnabled,
                deadlineTime = if (isReminderEnabled) deadlineTime else null,
                reminderMinutesBefore = reminderMinutes,
                subItems = subItems
            )
            savedItem = updated
            updated
        }
        viewModel.updateTodos(newList)
    }

    // --- 第5步核心：处理闹钟调度 ---
    savedItem?.let { item ->
        if (item.isReminderEnabled) {
            // 如果开启了提醒，先取消旧的（防止重复），再设置新的
            viewModel.cancelAlarm(context, item)
            viewModel.scheduleAlarm(context, item)
        } else {
            // 如果关闭了提醒，确保取消已有的闹钟
            viewModel.cancelAlarm(context, item)
        }
    }
}