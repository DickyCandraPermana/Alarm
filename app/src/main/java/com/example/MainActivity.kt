package com.example

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Vibrator
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Alarm
import com.example.service.MathAlarmService
import com.example.ui.theme.MyApplicationTheme
import com.example.util.MathQuestion
import com.example.util.MathQuestionGenerator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private val viewModel: AlarmViewModel by viewModels {
        AlarmViewModel.Factory(application)
    }

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, "Izin notifikasi diberikan! Alarm Anda akan dikirimkan dengan andal.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Peringatan: Notifikasi dimatikan. Anda mungkin melewatkan alarm berbunyi.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Setup Window Flags to show content over keyguard / lockscreen & awake device screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            keyguardManager?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        enableEdgeToEdge()
        
        // Request post notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            MyApplicationTheme(darkTheme = true) { // Dark slate color theme is perfect for alarm apps
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MathAlarmApp(viewModel = viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MathAlarmApp(viewModel: AlarmViewModel) {
    val alarms by viewModel.alarms.collectAsStateWithLifecycle()
    val ringingAlarm by MathAlarmService.currentRingingAlarm.collectAsStateWithLifecycle()
    
    var showAddEditDialog by remember { mutableStateOf(false) }
    var alarmToEdit by remember { mutableStateOf<Alarm?>(null) }
    
    Box(modifier = Modifier.fillMaxSize()) {
        // Main Dashboard Scope
        Scaffold(
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AlarmOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "ArithmaWake",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Bangun dengan tantangan matematika",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 0.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        alarmToEdit = null
                        showAddEditDialog = true
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .testTag("add_alarm_fab")
                        .padding(bottom = 16.dp, end = 16.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Tambah Alarm")
                }
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 0.dp,
                    modifier = Modifier.navigationBarsPadding(),
                    windowInsets = WindowInsets.navigationBars
                ) {
                    NavigationBarItem(
                        selected = true,
                        onClick = { /* Stay on current tab */ },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Alarm,
                                contentDescription = "Sleek Alarms"
                            )
                        },
                        label = { Text("Alarm") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = { /* Simulated disabled */ },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.HourglassEmpty,
                                contentDescription = "Timer"
                            )
                        },
                        label = { Text("Timer") },
                        colors = NavigationBarItemDefaults.colors(
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = { /* Simulated disabled */ },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = "Stopwatch"
                            )
                        },
                        label = { Text("Stopwatch") },
                        colors = NavigationBarItemDefaults.colors(
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = { /* Simulated disabled */ },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Pengaturan"
                            )
                        },
                        label = { Text("Pengaturan") },
                        colors = NavigationBarItemDefaults.colors(
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    )
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.Top
            ) {
                // Time & Info Card
                RealTimeClockWidget()
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Daftar Alarm Anda",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (alarms.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Alarm,
                                contentDescription = null,
                                modifier = Modifier.size(72.dp),
                                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Belum ada alarm",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Ketuk tombol + di kanan bawah untuk membuat alarm matematika pertamamu.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.8f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 32.dp)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(alarms, key = { it.id }) { alarm ->
                            AlarmItemCard(
                                alarm = alarm,
                                onToggle = { isEnabled -> viewModel.toggleAlarm(alarm, isEnabled) },
                                onDelete = { viewModel.deleteAlarm(alarm) },
                                onClick = {
                                    alarmToEdit = alarm
                                    showAddEditDialog = true
                                }
                            )
                        }
                    }
                }
            }
        }

        // Add/Edit Alarm Configuration Dialog
        if (showAddEditDialog) {
            AddEditAlarmDialog(
                alarm = alarmToEdit,
                onDismiss = { showAddEditDialog = false },
                onSave = { savedAlarm ->
                    if (alarmToEdit == null) {
                        viewModel.addAlarm(savedAlarm)
                    } else {
                        viewModel.updateAlarm(savedAlarm)
                    }
                    showAddEditDialog = false
                }
            )
        }

        // Immersive Quiz Overlay (Displays Full Screen when an alarm is actively ringing)
        ringingAlarm?.let { activeAlarm ->
            MathAlarmQuizOverlay(alarm = activeAlarm)
        }
    }
}

@Composable
fun RealTimeClockWidget() {
    var currentTime by remember { mutableStateOf("") }
    var currentDate by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            val cal = Calendar.getInstance()
            currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(cal.time)
            currentDate = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("id", "ID")).format(cal.time)
            delay(1000)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = currentDate.uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = currentTime,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.Light,
                    letterSpacing = (-2).sp
                ),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "DISIPLIN MATEMATIKA PAGI",
                    style = MaterialTheme.typography.bodySmall.copy(
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlarmItemCard(
    alarm: Alarm,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    val timeFormatted = String.format("%02d:%02d", alarm.hour, alarm.minute)
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    VibratorHelper.triggerVibrateFeedback(context)
                }
            )
            .testTag("alarm_card_${alarm.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (alarm.isEnabled) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f) 
            else 
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (alarm.isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            else Color.Transparent
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Time Label Row
                Row(
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = timeFormatted,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (alarm.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = alarm.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(6.dp))

                // Repetition list
                val daysText = getRepetitionDaysText(alarm.repeatDays)
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = daysText,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (alarm.isEnabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Difficulty Badges
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val badgeColor = when (alarm.difficulty.uppercase()) {
                        "EASY" -> Color(0xFF4CAF50)
                        "MEDIUM" -> Color(0xFFFF9800)
                        "HARD" -> Color(0xFFE91E63)
                        else -> Color(0xFFFF9800)
                    }
                    
                    val difficultyName = when (alarm.difficulty.uppercase()) {
                        "EASY" -> "Mudah"
                        "MEDIUM" -> "Sedang"
                        "HARD" -> "Sangat Sulit 🔥"
                        else -> "Sedang"
                    }

                    Box(
                        modifier = Modifier
                            .background(badgeColor.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = difficultyName,
                            style = MaterialTheme.typography.labelSmall,
                            color = badgeColor,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${alarm.correctAnswersRequired}x Matematika",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Controls
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(
                    checked = alarm.isEnabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier
                        .testTag("alarm_switch_${alarm.id}")
                        .scale(0.85f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("delete_alarm_${alarm.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Hapus",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

// Convert comma separated days to human readable Indonesian labels
fun getRepetitionDaysText(repeatDays: String): String {
    if (repeatDays.isEmpty()) return "Sekali saja"
    val daysList = repeatDays.split(",").mapNotNull { it.toIntOrNull() }.sorted()
    if (daysList.size == 7) return "Setiap hari"
    if (daysList.size == 5 && !daysList.contains(Calendar.SATURDAY) && !daysList.contains(Calendar.SUNDAY)) {
        return "Hari Kerja (Sen - Jum)"
    }
    if (daysList.size == 2 && daysList.contains(Calendar.SATURDAY) && daysList.contains(Calendar.SUNDAY)) {
        return "Akhir Pekan (Sab - Min)"
    }

    val dayNames = mapOf(
        Calendar.MONDAY to "Sen",
        Calendar.TUESDAY to "Sel",
        Calendar.WEDNESDAY to "Rab",
        Calendar.THURSDAY to "Kam",
        Calendar.FRIDAY to "Jum",
        Calendar.SATURDAY to "Sab",
        Calendar.SUNDAY to "Min"
    )

    return daysList.mapNotNull { dayNames[it] }.joinToString(", ")
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditAlarmDialog(
    alarm: Alarm?,
    onDismiss: () -> Unit,
    onSave: (Alarm) -> Unit
) {
    var hour by remember { mutableIntStateOf(alarm?.hour ?: 6) }
    var minute by remember { mutableIntStateOf(alarm?.minute ?: 30) }
    var label by remember { mutableStateOf(alarm?.label ?: "Alarm Pagi") }
    var difficulty by remember { mutableStateOf(alarm?.difficulty ?: "MEDIUM") }
    var answersRequired by remember { mutableIntStateOf(alarm?.correctAnswersRequired ?: 3) }
    var isVibrate by remember { mutableStateOf(alarm?.isVibrate ?: true) }
    
    // Day repetition states
    val initialDays = alarm?.getRepeatDaysList() ?: emptyList()
    var selectedDays by remember { mutableStateOf(initialDays.toSet()) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .testTag("add_edit_alarm_dialog"),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(
                        text = if (alarm == null) "Tambah Alarm Baru" else "Edit Alarm Matematika",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                // Time Spin Selector Row (Simple click adjust buttons, extremely reliable!)
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Atur Waktu Alarm",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Hour Picker
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(onClick = { hour = (hour + 1) % 24 }) {
                                    Icon(imageVector = Icons.Default.KeyboardArrowUp, contentDescription = "Jam Tambah")
                                }
                                Box(
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp))
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = String.format("%02d", hour),
                                        style = MaterialTheme.typography.displayMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                                IconButton(onClick = { hour = if (hour - 1 < 0) 23 else hour - 1 }) {
                                    Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = "Jam Kurang")
                                }
                            }

                            Text(
                                text = ":",
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.testTag("colon").padding(horizontal = 12.dp),
                                color = MaterialTheme.colorScheme.outline
                            )

                            // Minute Picker
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(onClick = { minute = (minute + 5) % 60 }) {
                                    Icon(imageVector = Icons.Default.KeyboardArrowUp, contentDescription = "Menit Tambah (+5)")
                                }
                                Box(
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp))
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = String.format("%02d", minute),
                                        style = MaterialTheme.typography.displayMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                                IconButton(onClick = { minute = if (minute - 5 < 0) 55 else minute - 5 }) {
                                    Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = "Menit Kurang (-5)")
                                }
                            }
                        }
                    }
                }

                // Label Text Field
                item {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("Label Alarm") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Repetitions (Days selection circle items)
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Hari Pengulangan",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val daysMap = listOf(
                                Calendar.MONDAY to "S",
                                Calendar.TUESDAY to "S",
                                Calendar.WEDNESDAY to "R",
                                Calendar.THURSDAY to "K",
                                Calendar.FRIDAY to "J",
                                Calendar.SATURDAY to "S",
                                Calendar.SUNDAY to "M"
                            )
                            
                            daysMap.forEach { (dayId, label) ->
                                val isSelected = selectedDays.contains(dayId)
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .clickable {
                                            selectedDays = if (isSelected) {
                                                selectedDays - dayId
                                            } else {
                                                selectedDays + dayId
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // Math Difficulty Selector Toggles
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Tingkat Kesulitan Matematika",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val difficulties = listOf(
                                "EASY" to "Mudah",
                                "MEDIUM" to "Sedang",
                                "HARD" to "Sangat Sulit"
                            )
                            difficulties.forEach { (diffKey, title) ->
                                val active = difficulty == diffKey
                                Button(
                                    onClick = { difficulty = diffKey },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    contentPadding = PaddingValues(vertical = 8.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(text = title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        val desc = when (difficulty) {
                            "EASY" -> "Penjumlahan & Pengurangan dasar (2 Digit)"
                            "MEDIUM" -> "Perkalian/Pembagian dicampur tambah/kurang"
                            "HARD" -> "Sangat Sulit! Persamaan ganda dalam tanda kurung & perkalian 3 digit."
                            else -> ""
                        }
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (difficulty == "HARD") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Number of Answers Required Stepper
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Kewajiban Menjawab Benar Berturut-turut",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start
                        ) {
                            IconButton(
                                onClick = { if (answersRequired > 1) answersRequired-- },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape).size(32.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Remove, contentDescription = "Kurang")
                            }
                            Text(
                                text = "$answersRequired Soal",
                                modifier = Modifier.padding(horizontal = 16.dp),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                            IconButton(
                                onClick = { if (answersRequired < 10) answersRequired++ },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape).size(32.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = "Tambah")
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Lulus jika terjawab semua",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }

                // Vibration config
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Vibration, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Getarkan Perangkat",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Switch(
                            checked = isVibrate,
                            onCheckedChange = { isVibrate = it }
                        )
                    }
                }

                // Actions Saved Button Row
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Batal")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val repeatString = selectedDays.joinToString(",")
                                val finalAlarm = Alarm(
                                    id = alarm?.id ?: 0,
                                    hour = hour,
                                    minute = minute,
                                    label = label.ifEmpty { "Alarm" },
                                    difficulty = difficulty,
                                    correctAnswersRequired = answersRequired,
                                    repeatDays = repeatString,
                                    isVibrate = isVibrate,
                                    isEnabled = true
                                )
                                onSave(finalAlarm)
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("save_alarm_button")
                        ) {
                            Text("Simpan Alarm", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MathAlarmQuizOverlay(alarm: Alarm) {
    val context = LocalContext.current
    
    var currentAnswerInput by remember { mutableStateOf("") }
    var solvedCount by remember { mutableIntStateOf(0) }
    var isQuizCompleted by remember { mutableStateOf(false) }
    
    // Live question generator state
    var activeQuestion by remember { mutableStateOf(MathQuestionGenerator.generateQuestion(alarm.difficulty)) }
    
    // UI Validation states
    var feedbackMessage by remember { mutableStateOf("") }
    var isValidationError by remember { mutableStateOf(false) }
    var isValidationSuccess by remember { mutableStateOf(false) }

    val totalRequired = alarm.correctAnswersRequired

    if (isQuizCompleted) {
        // Render Triumphant Wakeup Success Card (Screen fully unlocked!)
        WakeUpVictoryScreen {
            // Stop service (removes notifications, stops audio loop/vibration)
            MathAlarmService.stopAlarmService(context)
        }
    } else {
        // Immersive alert layout
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 500.dp)
                    .systemBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header Display Name/Warning
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AlarmOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "ALARM BERBUNYI!",
                        style = MaterialTheme.typography.titleMedium.copy(
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = alarm.label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    // Display current simple hour/minute
                    val calendar = Calendar.getInstance()
                    val hr = String.format("%02d", calendar.get(Calendar.HOUR_OF_DAY))
                    val mn = String.format("%02d", calendar.get(Calendar.MINUTE))
                    Text(
                        text = "Waktu: $hr:$mn",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Math Challenge Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    shape = RoundedCornerShape(32.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Badge row matching HTML
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "LEVEL: ${alarm.difficulty.uppercase()}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    letterSpacing = 2.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = Color(0xFF381E72),
                                        shape = RoundedCornerShape(100.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "$solvedCount/$totalRequired Selesai",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = Color(0xFFD0BCFF)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = "Pecahkan soal untuk matikan alarm:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Large Display mathematical expression
                        Text(
                            text = activeQuestion.expression,
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Underlined input field representation
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1C1B1F), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f, fill = false),
                                contentAlignment = Alignment.Center
                            ) {
                                if (currentAnswerInput.isEmpty()) {
                                    Text(
                                        text = "Jawaban...",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                } else {
                                    Text(
                                        text = currentAnswerInput,
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 2.sp
                                        ),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .background(Color(0xFFD0BCFF))
                            )
                        }

                        // Feedback Info messages
                        if (feedbackMessage.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = feedbackMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isValidationError) MaterialTheme.colorScheme.error else Color(0xFF4CAF50),
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Custom Numeric Pad (Optimized for thumb reach)
                Column(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val keyRows = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("-", "0", "Hapus")
                    )

                    keyRows.forEach { rowKeys ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowKeys.forEach { key ->
                                Button(
                                    onClick = {
                                        VibratorHelper.triggerVibrateFeedback(context)
                                        if (key == "Hapus") {
                                            if (currentAnswerInput.isNotEmpty()) {
                                                currentAnswerInput = currentAnswerInput.dropLast(1)
                                            }
                                        } else if (key == "-") {
                                            if (currentAnswerInput.isEmpty()) {
                                                currentAnswerInput = "-"
                                            }
                                        } else {
                                            if (currentAnswerInput.length < 8) {
                                                currentAnswerInput += key
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(56.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    if (key == "Hapus") {
                                        Icon(
                                            imageVector = Icons.Default.Backspace,
                                            contentDescription = "Hapus",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        Text(
                                            text = key,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Big Action Verification Submit key (Primary Accent Theme styled)
                    Button(
                        onClick = {
                            VibratorHelper.triggerVibrateFeedback(context)
                            val numericAnswer = currentAnswerInput.toIntOrNull()
                            if (numericAnswer == null) {
                                feedbackMessage = "Masukkan jawaban angka terlebih dahulu!"
                                isValidationError = true
                                isValidationSuccess = false
                            } else {
                                if (numericAnswer == activeQuestion.answer) {
                                    // Correct! Increment
                                    val nextCount = solvedCount + 1
                                    isValidationError = false
                                    isValidationSuccess = true
                                    
                                    if (nextCount >= totalRequired) {
                                        // All complete! Solve puzzle
                                        isQuizCompleted = true
                                    } else {
                                        feedbackMessage = "Benar! Mantap, siapkan soal berikutnya..."
                                        solvedCount = nextCount
                                        currentAnswerInput = ""
                                        // Generate new math problem on the exact difficulty
                                        activeQuestion = MathQuestionGenerator.generateQuestion(alarm.difficulty)
                                    }
                                } else {
                                    // WRONG! Reset consecutive completed math puzzles and feedback
                                    isValidationError = true
                                    isValidationSuccess = false
                                    Toast.makeText(context, "Jawaban Salah! Progress direset ke 0.", Toast.LENGTH_SHORT).show()
                                    VibratorHelper.triggerDoubleVibrateFeedback(context)
                                    feedbackMessage = "Salah! ${activeQuestion.expression} = ${activeQuestion.answer}. Progress Anda direset ke 0."
                                    solvedCount = 0
                                    currentAnswerInput = ""
                                    // Regenerate problem
                                    activeQuestion = MathQuestionGenerator.generateQuestion(alarm.difficulty)
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                            .testTag("submit_math_answer"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "JAWAB DAN PERIKSA",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WakeUpVictoryScreen(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0D1B2A),
                        Color(0xFF1B263B)
                    )
                )
            )
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = Color(0xFFFFD700),
                    modifier = Modifier.size(80.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Selamat Pagi! ☀️",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Otak Anda kini telah aktif sepenuhnya. Alarm telah dinonaktifkan dengan sukses.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(28.dp))
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("victory_dismiss_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = "Masuk ke Dashboard",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// Low level Vibrator component for physical game feedbacks
object VibratorHelper {
    fun triggerVibrateFeedback(context: Context) {
        try {
            val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (v != null && v.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(android.os.VibrationEffect.createOneShot(80, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(80)
                }
            }
        } catch (e: Exception) {
            // No permissions or error
        }
    }

    fun triggerDoubleVibrateFeedback(context: Context) {
        try {
            val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (v != null && v.hasVibrator()) {
                val pattern = longArrayOf(0, 100, 50, 100)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(android.os.VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(pattern, -1)
                }
            }
        } catch (e: Exception) {
            // No permissions or error
        }
    }
}
