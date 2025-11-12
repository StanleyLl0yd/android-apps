package com.sl.biorhythms

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.FloatRange
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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

private val Context.dataStore by preferencesDataStore("settings")

/* --- Модель --- */

private enum class Cycle(
    val period: Int,
    val color: Color,
    val pick: (BPoint) -> Float
) {
    Physical(23, Color(0xFFEF5350), { it.physical }),       // красный
    Emotional(28, Color(0xFF42A5F5), { it.emotional }),     // синий
    Intellectual(33, Color(0xFF66BB6A), { it.intellectual })// зелёный
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

/* --- UI --- */

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val activity = LocalContext.current as ComponentActivity
    val scope = rememberCoroutineScope()
    val keyDob = remember { longPreferencesKey("dob_epoch_day") }

    var birthDate by remember { mutableStateOf<LocalDate?>(null) }
    val showPicker = rememberSaveable { mutableStateOf(false) }
    val showInfo = remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val epoch = activity.dataStore.data.first()[keyDob]
        if (epoch != null) birthDate = LocalDate.ofEpochDay(epoch)
        else showPicker.value = true
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
                            IconButton({ showInfo.value = true }) {
                                Icon(Icons.Outlined.Info, contentDescription = "Справка")
                            }
                            Text("Биоритмы")
                        }
                    },
                    actions = {
                        TextButton({ showPicker.value = true }) { Text("Сменить дату") }
                    }
                )
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                if (birthDate == null) {
                    Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Укажите дату рождения, чтобы построить график.",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(16.dp))
                        Button({ showPicker.value = true }) { Text("Выбрать дату") }
                    }
                } else {
                    BiorhythmScreen(birthDate!!)
                }
            }

            if (showPicker.value) {
                val state = rememberDatePickerState()
                DatePickerDialog(
                    onDismissRequest = { showPicker.value = false },
                    confirmButton = {
                        TextButton({
                            state.selectedDateMillis?.let {
                                saveDob(
                                    Instant.ofEpochMilli(it)
                                        .atZone(ZoneId.systemDefault())
                                        .toLocalDate()
                                )
                            }
                            showPicker.value = false
                        }) { Text("Готово") }
                    },
                    dismissButton = { TextButton({ showPicker.value = false }) { Text("Отмена") } }
                ) { DatePicker(state = state, showModeToggle = true) }
            }

            if (showInfo.value) {
                AlertDialog(
                    onDismissRequest = { showInfo.value = false },
                    confirmButton = { TextButton({ showInfo.value = false }) { Text("Понятно") } },
                    title = { Text("О биоритмах") },
                    text = {
                        Text(
                            """
                            Три синусоидальных цикла с даты рождения:
                            • Физический — 23 дня
                            • Эмоциональный — 28 дней
                            • Интеллектуальный — 33 дня

                            Значения: −100…+100 (внутри −1…+1 × 100). Ноль — смена фазы.
                            Модель популярна, но научной валидности не имеет.
                            """.trimIndent()
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun BiorhythmScreen(birthDate: LocalDate) {
    val center = remember { LocalDate.now() }
    val span = 15
    val points = remember(birthDate, center) { generateSeries(birthDate, center, span, span) }
    val today = remember(points) { points[span] }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Дата рождения: $birthDate", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))

        BiorhythmChart(
            data = points,
            height = 260.dp,
            leftDays = span,
            rightDays = span,
            centerDate = center
        )

        Spacer(Modifier.height(12.dp))

        ValuesRow(today.physical, today.emotional, today.intellectual)
    }
}

@Composable
private fun ValuesRow(p: Float, e: Float, i: Float) {
    Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(1.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
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

            // фон
            drawRect(
                color = bgColor,
                topLeft = Offset(L, T),
                size = androidx.compose.ui.geometry.Size(R - L, B - T)
            )

            // горизонтальные линии
            listOf(-1f, -0.5f, 0f, 0.5f, 1f).forEach { yv ->
                drawLine(
                    color = when (yv) { 0f -> zeroLine; -0.5f, 0.5f -> gridHalf; else -> gridDay },
                    start = Offset(L, mapY(yv)),
                    end = Offset(R, mapY(yv)),
                    strokeWidth = if (yv == 0f) 2f else 1f
                )
            }

            val total = data.size - 1

            // вертикальная сетка по дням
            for (i in 0..total) {
                drawLine(
                    color = if (i == leftDays) todayColor else gridDay,
                    start = Offset(mapX(i, total), T),
                    end = Offset(mapX(i, total), B),
                    strokeWidth = if (i == leftDays) 2f else 1f
                )
            }

            // подписи дат
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
                    val x = mapX(i, total); val y = mapY(sel(bp))
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
            }
            Cycle.values().forEach { c ->
                drawPath(path = pathOf(c.pick), color = c.color, style = stroke)
            }
        }
    }
}
