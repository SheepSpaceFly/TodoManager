package com.sheepspacefly.todomanager

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.provider.Settings
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sheepspacefly.todomanager.ui.theme.TodoManagerTheme
import com.sheepspacefly.todomanager.ui.theme.Colors
import com.sheepspacefly.todomanager.ui.theme.ConfettiEffect
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

// --- 动画时长常量定义 ---
const val CALENDAR_ANIM_DURATION = 300 // 日历展开/收起时长
const val ITEM_ANIM_DURATION = 100     // 箭头旋转与事项展开/收起时长

// --- Data Model ---
data class TodoItem(
    val id: Long = System.currentTimeMillis(),
    val content: String,
    val isCompleted: Boolean = false,
    val date: String,
    val deadlineTime: String? = null,
    val isReminderEnabled: Boolean = false,
    val reminderMinutesBefore: Int = 0,
    val subItems: List<TodoItem>? = emptyList()
)

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        // 请求通知权限 (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
        }

        setContent {
            TodoManagerTheme {
                val customRippleConfig = RippleConfiguration(
                    color = Color.Gray, // 涟漪的颜色
                    // RippleAlpha 包含四个状态的透明度：压下、聚焦、悬停、拖动
                    // 我们重点调低第一个参数 (pressedAlpha)
                    rippleAlpha = RippleAlpha(
                        pressedAlpha = 0.05f, // 点击时极淡的效果
                        focusedAlpha = 0.05f,
                        hoveredAlpha = 0.02f,
                        draggedAlpha = 0.08f
                    )
                )

                // 使用 CompositionLocalProvider 将配置注入全局
                CompositionLocalProvider(LocalRippleConfiguration provides customRippleConfig) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        TodoApp()
                    }
                }
            }
        }
    }
}

sealed class Screen(val route: String) {
    object Main : Screen("main")
    object Edit : Screen("edit/{itemId}/{parentId}/{depth}?date={date}") {
        fun createRoute(itemId: Long = -1L, parentId: Long = -1L, depth: Int = 1, date: String? = null) =
            "edit/$itemId/$parentId/$depth" + if (date != null) "?date=$date" else ""
    }
}

@SuppressLint("NewApi")
@Composable
fun TodoApp() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val viewModel = remember { TodoViewModel(context) }

    NavHost(navController = navController, startDestination = Screen.Main.route) {
        // 主列表页
        composable(
            route = Screen.Main.route,
            popEnterTransition = {
                EnterTransition.None
            }
        ) {
            TodoManagerScreen(navController, viewModel)
        }
        // 编辑/新增页
        composable(
            route = Screen.Edit.route,
            arguments = listOf(
                navArgument("itemId") { type = NavType.LongType },
                navArgument("parentId") { type = NavType.LongType },
                navArgument("depth") { type = NavType.IntType },
                navArgument("date") { type = NavType.StringType; nullable = true }
            ),
            enterTransition = {
                slideInVertically(initialOffsetY = { it }) + fadeIn()
            },
            popExitTransition = {
                slideOutVertically(targetOffsetY = { it }) + fadeOut()
            }
        ) { backStackEntry ->
            val itemId = backStackEntry.arguments?.getLong("itemId") ?: -1L
            val parentId = backStackEntry.arguments?.getLong("parentId") ?: -1L
            val depth = backStackEntry.arguments?.getInt("depth") ?: 1
            val date = backStackEntry.arguments?.getString("date")

            // 调用你独立文件中的 TodoEditScreen
            TodoEditScreen(navController, itemId, parentId, depth, viewModel, date?:LocalDate.now().toString())
        }
    }
}

@SuppressLint("NewApi")
@Composable
fun TodoManagerScreen(navController: NavHostController, viewModel: TodoViewModel) {
    var selectedDate  = viewModel.selectedDate
    var isCalendarExpanded by remember { mutableStateOf(false) }
    var expandedIds by remember { mutableStateOf(setOf<Long>()) }

    // 从 ViewModel 获取数据
    val allTodos = viewModel.allTodos
    val currentDayTodos = viewModel.currentDayTodos
    val rootDone = currentDayTodos.count { it.isCompleted }
    val rootTotal = currentDayTodos.size
    val isSortEnabled = false

    //神秘彩炮
    var confettiTriggerCount by remember { mutableIntStateOf(1) }
    val isAllDone = remember(currentDayTodos) {
        currentDayTodos.isNotEmpty() && currentDayTodos.all { it.isCompleted }
    }
    // 2. 监听 isAllDone，一旦变为 true，就增加计数器
    LaunchedEffect(isAllDone) {
        if (isAllDone) {
            confettiTriggerCount++ // 每次完成，数值都会变
        }
    }

    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()){
        Scaffold(
            topBar = {
                Column(modifier = Modifier
                    .fillMaxWidth()
                    .background(Colors.DividerColor)
                    .statusBarsPadding()) {

                    val formattedDate = remember(selectedDate) {
                        val dateFormatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日")
                        val dayOfWeekFormatter = DateTimeFormatter.ofPattern("EEEE", Locale.JAPANESE)
                        val dateStr = selectedDate.format(dateFormatter)
                        val dayOfWeekStr = selectedDate.format(dayOfWeekFormatter)

                        // 将日文星期转换为简写形式
                        when (dayOfWeekStr) {
                            "月曜日" -> "$dateStr   月"
                            "火曜日" -> "$dateStr   火"
                            "水曜日" -> "$dateStr   水"
                            "木曜日" -> "$dateStr   木"
                            "金曜日" -> "$dateStr   金"
                            "土曜日" -> "$dateStr   土"
                            "日曜日" -> "$dateStr   日"
                            else -> "$dateStr $dayOfWeekStr"
                        }
                    }

                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                        color = Colors.DarkText,
                        modifier = Modifier
                            .clickable { isCalendarExpanded = !isCalendarExpanded }
                            .padding(16.dp)
                    )
                    AnimatedVisibility(
                        visible = isCalendarExpanded,
                        enter = expandVertically(animationSpec = tween(CALENDAR_ANIM_DURATION)),
                        exit = shrinkVertically(animationSpec = tween(CALENDAR_ANIM_DURATION))
                    ) {
                        FullCalendarView(selectedDate, allTodos) { date ->
                            viewModel.updateSelectedDate(date)
                            isCalendarExpanded = false
                        }
                    }
                }
            },
            floatingActionButton = {
                Box(modifier = Modifier.padding(bottom = 32.dp, end = 16.dp)) {
                    FloatingActionButton(
                        onClick = {
//                        val notificationHelper = NotificationHelper(context)
//                        notificationHelper.showNotification("测试悬浮通知", "这是一条测试消息")
                            // 跳转到新增根事项页面
                            navController.navigate(Screen.Edit.createRoute(parentId = -1L, depth = 1, date = selectedDate.toString()))
                        },
                        containerColor = Colors.BrightYellow,
                        contentColor = Colors.DarkText,
                        shape = CircleShape
                    ) { Icon(Icons.Default.Add, null) }
                }
            }
        ) { padding ->
            Column(modifier = Modifier.padding(padding)) {
                Text(
                    text = "已完成：$rootDone / $rootTotal",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium, color = Colors.AmberYellow,
                    fontWeight = FontWeight.Medium, textAlign = TextAlign.Start
                )

                // --- 列表区 ---
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    val displayRoots = if (isSortEnabled) {
                        currentDayTodos.sortedBy { it.isCompleted }
                    } else {
                        currentDayTodos // 保持原始顺序（通常是添加顺序）
                    }
                    items(displayRoots, key = { it.id }) { rootItem ->
                        RecursiveTodoItem(
                            item = rootItem,
                            depth = 1,
                            isSortEnabled = isSortEnabled,
                            expandedIds = expandedIds,
                            onToggle = { targetItem ->
                                viewModel.toggleItemCompletion(targetItem, context)
                            },
                            onClick = { targetItem ->
                                if (expandedIds.contains(targetItem.id)) {
                                    expandedIds = expandedIds - targetItem.id
                                } else {
                                    expandedIds = expandedIds + targetItem.id
                                }
                            },
                            onLongPress = { targetItem, currentDepth ->
                                // 跳转到编辑页面
                                navController.navigate(
                                    Screen.Edit.createRoute(itemId = targetItem.id, depth = currentDepth)
                                )
                            }
                        )
                    }
                }
            }

            if (isCalendarExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Transparent) // 设为透明，或者 Color.Black.copy(alpha = 0.1f) 增加视觉反馈
                        .pointerInput(Unit) {
                            // 监听点击事件
                            detectTapGestures(
                                onTap = {
                                    isCalendarExpanded = false // 点击列表区域时关闭日历
                                }
                            )
                        }
                )
            }
        }

        //Boom!
        ConfettiEffect(
            trigger = isAllDone,
            triggerId = confettiTriggerCount, // 新增参数
            onAnimationEnd = { /* 回调 */ }
        )
    }
}

@Composable
fun RecursiveTodoItem(
    item: TodoItem,
    depth: Int,
    isSortEnabled: Boolean,
    expandedIds: Set<Long>,
    onToggle: (TodoItem) -> Unit,
    onClick: (TodoItem) -> Unit,
    onLongPress: (TodoItem, Int) -> Unit
) {
    val isExpanded = expandedIds.contains(item.id)
    val subs = item.subItems ?: emptyList()

    Column {
        TodoItemRow(
            todo = item,
            depth = depth,
            isExpanded = isExpanded,
            onToggle = { onToggle(item) },
            onLongPress = { onLongPress(item, depth) },
            onClick = { onClick(item) }
        )

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(animationSpec = tween(ITEM_ANIM_DURATION)) + fadeIn(animationSpec = tween(ITEM_ANIM_DURATION)),
            exit = shrinkVertically(animationSpec = tween(ITEM_ANIM_DURATION)) + fadeOut(animationSpec = tween(ITEM_ANIM_DURATION))
        ) {
            Column {
                val displaySubs = if (isSortEnabled) {
                    subs.sortedBy { it.isCompleted }
                } else {
                    subs
                }

                displaySubs.forEach { subItem ->
                    RecursiveTodoItem(
                        item = subItem,
                        depth = depth + 1,
                        isSortEnabled = isSortEnabled,
                        expandedIds = expandedIds,
                        onToggle = onToggle,
                        onClick = onClick,
                        onLongPress = onLongPress
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TodoItemRow(todo: TodoItem, depth: Int, isExpanded: Boolean, onToggle: () -> Unit, onLongPress: () -> Unit, onClick: () -> Unit) {
    val lineProgress = remember { Animatable(if (todo.isCompleted) 1f else 0f) }
    LaunchedEffect(todo.isCompleted) { lineProgress.animateTo(if (todo.isCompleted) 1f else 0f, tween(400)) }

    val arrowRotation by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        animationSpec = tween(durationMillis = ITEM_ANIM_DURATION)
    )

    Column(modifier = Modifier
        .fillMaxWidth()
        .background(Color.White)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onDoubleClick = onToggle,
                    onLongClick = onLongPress,
                    onClick = onClick
                )
                .padding(vertical = 12.dp, horizontal = 16.dp)
                .padding(start = ((depth - 1) * 24).dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val subs = todo.subItems ?: emptyList()

            // 左侧图标区域
            Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                if (subs.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.rotate(arrowRotation),
                        tint = if (todo.isCompleted) Color.LightGray else Color.Gray
                    )
                } else {
                    // 没有子项时显示一个小圆点，增加笔记本清单感
                    Canvas(modifier = Modifier.size(6.dp)) {
                        drawCircle(color = if (todo.isCompleted) Color.LightGray else Color.Gray)
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            Text(
                text = todo.content,
                fontSize = (18 - depth).coerceAtLeast(14).sp,
                modifier = Modifier
                    .weight(1f)
                    .drawWithContent {
                        drawContent()
                        if (lineProgress.value > 0f) {
                            drawLine(
                                Color.Gray,
                                Offset(0f, size.height / 2f),
                                Offset(size.width * lineProgress.value, size.height / 2f),
                                1.5.dp.toPx()
                            )
                        }
                    },
                color = if (todo.isCompleted) Color.Gray else Colors.DarkText
            )

            if (subs.isNotEmpty()) {
                val done = subs.count { it.isCompleted }
                Text(
                    "${done}/${subs.size}",
                    fontSize = 14.sp,
                    color = Color.LightGray,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        // --- 笔记本横线效果 ---
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            thickness = 1.dp, // 细线
            color = Color.LightGray.copy(alpha = 0.4f) // 淡淡的灰色
        )
    }
}

@SuppressLint("NewApi")
@Composable
fun FullCalendarView(
    selectedDate: LocalDate,
    allTodos: List<TodoItem>, // 新增参数：传入所有待办事项
    onDateSelected: (LocalDate) -> Unit
) {
    var currentMonth by remember { mutableStateOf(YearMonth.from(selectedDate)) }
    val daysInMonth = currentMonth.lengthOfMonth()
    val firstOfMonth = currentMonth.atDay(1)
    val dayOfWeekOffset = firstOfMonth.dayOfWeek.value % 7

    Column(modifier = Modifier
        .fillMaxWidth()
        .background(Color.White)
        .padding(15.dp, 0.dp)) {
        // 月份选择器
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { currentMonth = currentMonth.minusMonths(1) }) { Icon(Icons.Default.KeyboardArrowLeft, null) }
            Text(text = currentMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.CHINESE)), fontWeight = FontWeight.Bold)
            IconButton(onClick = { currentMonth = currentMonth.plusMonths(1) }) { Icon(Icons.Default.KeyboardArrowRight, null) }
        }
        // 星期表头
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("日", "一", "二", "三", "四", "五", "六").forEach { day ->
                Text(text = day, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 12.sp, color = Color.Gray)
            }
        }
        // 日期网格
        Column {
            for (row in 0 until 6) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (col in 0 until 7) {
                        val slotIndex = row * 7 + col
                        val dayNumber = slotIndex - dayOfWeekOffset + 1
                        if (dayNumber in 1..daysInMonth) {
                            val date = currentMonth.atDay(dayNumber)
                            val isSelected = date == selectedDate

                            // --- 新增逻辑：计算该日期未完成的根事项数量 ---
                            val incompleteRootCount = allTodos.count {
                                it.date == date.toString() && !it.isCompleted
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .padding(2.dp)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        CircleShape
                                    )
                                    .clickable { onDateSelected(date) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = dayNumber.toString(),
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                // --- 新增 UI：右上角显示数量角标 ---
                                if (incompleteRootCount > 0 && !isSelected) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.TopEnd // 定位到右上角
                                    ) {
                                        Surface(
                                            color = if (isSelected) Color.White else Colors.Red,
                                            shape = CircleShape,
                                            modifier = Modifier
                                                .size(14.dp)
                                                .offset(x = (-2).dp, y = 2.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = incompleteRootCount.toString(),
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    textAlign = TextAlign.Center,
                                                    lineHeight = 9.sp // 强制行高与字号一致，防止文字内部偏移
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

sealed class DialogMode {
    object AddRoot : DialogMode()
    data class AddSub(val parentId: Long) : DialogMode()
    data class Edit(val item: TodoItem, val depth: Int) : DialogMode()
}

fun saveTodos(todos: List<TodoItem>, prefs: android.content.SharedPreferences, gson: Gson) {
    prefs.edit().putString("todo_list", gson.toJson(todos)).apply()
}
fun loadTodos(prefs: android.content.SharedPreferences, gson: Gson): List<TodoItem> {
    val json = prefs.getString("todo_list", null) ?: return emptyList()
    return gson.fromJson(json, object : TypeToken<List<TodoItem>>() {}.type)
}