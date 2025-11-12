package com.sl.biorhythms

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.FloatRange
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

// ---------------- DataStore ----------------
val Context.dataStore by preferencesDataStore("settings")

// ---------------- Модель ----------------
private enum class Cycle(
    val period: Int,
    val color: Color,
    val pick: (BPoint) -> Float
) {
    Physical(23, Color(0xFFEF5350), { it.physical }),
    Emotional(28, Color(0xFF42A5F5), { it.emotional }),
    Intellectual(33, Color(0xFF66BB6A), { it.intellectual })
}

private data class BPoint(
    val date: LocalDate,
    @FloatRange(from = -1.0, to = 1.0) val physical: Float,
    @FloatRange(from = -1.0, to = 1.0) val emotional: Float,
    @FloatRange(from = -1.0, to = 1.0) val intellectual: Float
)

private fun wave(days: Int, period: Int): Float {
    val angle = 2.0 * PI * (days % period) / period
    return sin(angle).toFloat()
}

private fun generateSeries(
    birth: LocalDate,
    center: LocalDate,
    daysBefore: Int,
    daysAfter: Int
): List<BPoint> {
    val start = center.minusDays(daysBefore.toLong())
    val end = center.plusDays(daysAfter.toLong())
    return generateSequence(start) { it.plusDays(1) }
        .takeWhile { !it.isAfter(end) }
        .map { day ->
            val d = ChronoUnit.DAYS.between(birth, day).toInt()
            BPoint(
                date = day,
                physical = wave(d, Cycle.Physical.period),
                emotional = wave(d, Cycle.Emotional.period),
                intellectual = wave(d, Cycle.Intellectual.period)
            )
        }.toList()
}

private fun Float.toPct() = (this * 100f).toInt()
private val dateFmt: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d LLL", Locale.getDefault())

// ---------------- Activity ----------------
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

// ---------------- UI ----------------
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("NewApi")
@Composable
fun App() {
    val activity = LocalContext.current as ComponentActivity
    val scope = rememberCoroutineScope()
    val keyDob = remember { longPreferencesKey("dob_epoch_day") }

    var birthDate by remember { mutableStateOf<LocalDate?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val epoch = activity.dataStore.data.first()[keyDob]
        if (epoch != null) birthDate = LocalDate.ofEpochDay(epoch) else showPicker = true
    }

    fun saveDob(date: LocalDate) = scope.launch {
        activity.dataStore.edit { it[keyDob] = date.toEpochDay() }
        birthDate = date
    }

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Биоритмы")
                            IconButton(onClick = { showInfo = true }) {
                                Icon(Icons.Outlined.Info, contentDescription = "Справка")
                            }
                        }
                    },
                    actions = {
                        TextButton(onClick = { showPicker = true }) { Text("Сменить дату") }
                    }
                )
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                if (birthDate == null) {
                    Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Укажите дату рождения, чтобы построить график биоритмов.",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { showPicker = true }) { Text("Выбрать дату") }
                    }
                } else {
                    BiorhythmScreen(birthDate = birthDate!!)
                }
            }

            if (showPicker) {
                val state = rememberDatePickerState()
                DatePickerDialog(
                    onDismissRequest = { showPicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            state.selectedDateMillis?.let {
                                val date = Instant.ofEpochMilli(it)
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDate()
                                saveDob(date)
                            }
                            showPicker = false
                        }) { Text("Готово") }
                    },
                    dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Отмена") } }
                ) {
                    DatePicker(state = state, showModeToggle = true)
                }
            }

            if (showInfo) {
                AlertDialog(
                    onDismissRequest = { showInfo = false },
                    confirmButton = { TextButton(onClick = { showInfo = false }) { Text("Понятно") } },
                    title = { Text("О биоритмах") },
                    text = {
                        Text(
                            "Три синусоидальных цикла, считаются с вашей даты рождения: " +
                                    "физический — 23 дня, эмоциональный — 28, интеллектуальный — 33. " +
                                    "Значения отображаются как проценты от −100 до +100. " +
                                    "Модель носит справочно-развлекательный характер и не является медицинской."
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun BiorhythmScreen(birthDate: LocalDate) {
    // Гарантированно используем foundation-версию через FQN
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        // Высота графика адаптивна к ориентации:
        val chartHeight = remember(this.maxWidth, this.maxHeight) {
            val portraitLike = this.maxHeight >= this.maxWidth
            val base = if (portraitLike) this.maxWidth * 0.45f else this.maxHeight * 0.60f
            base.coerceIn(220.dp, 520.dp)
        }

        val center = remember { LocalDate.now() }
        val span = 15
        val points = remember(birthDate, center) { generateSeries(birthDate, center, span, span) }
        val today = points[span]

        val scroll = rememberScrollState()
        // На всякий случай — тоже FQN, чтобы IDE не подсовывала ui.layout
        androidx.compose.foundation.layout.Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
        ) {
            Text("Дата рождения: $birthDate", style = MaterialTheme.typography.titleMedium)
            androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))

            BiorhythmChart(
                data = points,
                height = chartHeight,
                leftDays = span,
                rightDays = span,
                centerDate = center
            )

            androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))
            ValuesRow(today.physical, today.emotional, today.intellectual)
        }
    }
}

@Composable
private fun ValuesRow(p: Float, e: Float, i: Float) {
    Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(1.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ValueChip("Физический", p.toPct(), Cycle.Physical.color)
            ValueChip("Эмоциональный", e.toPct(), Cycle.Emotional.color)
            ValueChip("Интеллектуальный", i.toPct(), Cycle.Intellectual.color)
        }
    }
}

@Composable
private fun ValueChip(label: String, value: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(
            if (value > 0) "+$value" else "$value",
            style = MaterialTheme.typography.headlineSmall,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun BiorhythmChart(
    data: List<BPoint>,
    height: Dp,
    leftDays: Int,
    rightDays: Int,
    centerDate: LocalDate
) {
    val bgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    val gridDay = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val gridHalf = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
    val zeroLine = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
    val todayColor = MaterialTheme.colorScheme.onSurfaceVariant

    val df = remember { dateFmt }
    val leftDate = remember(centerDate) { centerDate.minusDays(leftDays.toLong()) }
    val rightDate = remember(centerDate) { centerDate.plusDays(rightDays.toLong()) }

    Card(Modifier.fillMaxWidth().height(height), elevation = CardDefaults.cardElevation(2.dp)) {
        Canvas(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 8.dp)) {
            val L = 16f
            val R = size.width - 8f
            val T = 8f
            val B = size.height - 28f

            fun mapX(i: Int, total: Int): Float {
                val t = i.toFloat() / total.coerceAtLeast(1)
                return L + t * (R - L)
            }

            fun mapY(@FloatRange(from = -1.0, to = 1.0) v: Float): Float {
                val t = (v + 1f) / 2f
                return B - t * (B - T)
            }

            // фон области графика
            drawRect(
                color = bgColor,
                topLeft = Offset(L, T),
                size = androidx.compose.ui.geometry.Size(R - L, B - T)
            )

            // горизонтальные линии сетки
            listOf(-1f, -0.5f, 0f, 0.5f, 1f).forEach { yv ->
                drawLine(
                    color = when (yv) { 0f -> zeroLine; -0.5f, 0.5f -> gridHalf; else -> gridDay },
                    start = Offset(L, mapY(yv)),
                    end = Offset(R, mapY(yv)),
                    strokeWidth = if (yv == 0f) 2f else 1f
                )
            }

            val total = data.size - 1

            // вертикальные линии по дням + «сегодня»
            for (i in 0..total) {
                drawLine(
                    color = if (i == leftDays) todayColor else gridDay,
                    start = Offset(mapX(i, total), T),
                    end = Offset(mapX(i, total), B),
                    strokeWidth = if (i == leftDays) 2f else 1f
                )
            }

            // подписи дат (d LLL)
            drawContext.canvas.nativeCanvas.apply {
                val p = android.graphics.Paint().apply {
                    color = android.graphics.Color.DKGRAY
                    textSize = 28f
                    isAntiAlias = true
                }
                val cx = mapX(leftDays, total)
                val cLabel = df.format(centerDate)
                drawText(df.format(leftDate), L, B + 24f, p)
                drawText(cLabel, cx - p.measureText(cLabel) / 2f, B + 24f, p)
                val rLabel = df.format(rightDate)
                drawText(rLabel, R - p.measureText(rLabel), B + 24f, p)
            }

            // кривые
            val stroke = Stroke(4f, cap = StrokeCap.Round)
            fun pathOf(sel: (BPoint) -> Float) = Path().apply {
                data.forEachIndexed { i, bp ->
                    val x = mapX(i, total)
                    val y = mapY(sel(bp))
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
            }
            Cycle.values().forEach { c ->
                drawPath(path = pathOf(c.pick), color = c.color, style = stroke)
            }
        }
    }
}