package dev.aero.glyphlapse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.aero.glyphlapse.engine.LapseEngine
import dev.aero.glyphlapse.engine.TimeBreakdown
import dev.aero.glyphlapse.render.LapseRenderer
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Interface de configuration : date/heure de référence, format d'affichage,
 * mode secondes — persistés en SharedPreferences (le toy écoute) — plus une
 * préview live 25×25 partageant moteur et renderer avec le toy.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ConfigScreen() }
    }
}

private val BG = Color(0xFF0A0A0B)
private val PANEL = Color(0xFF141418)
private val TXT = Color(0xFFE4E4E1)
private val MUTED = Color(0xFF8A8A93)
private val ACCENT = Color(0xFFD71921)

private val FORMAT_LABELS = mapOf(
    LapseEngine.Format.DETAIL to "Détail",
    LapseEngine.Format.DETAIL2 to "Détail 2",
    LapseEngine.Format.COMPACT to "Compact",
    LapseEngine.Format.CYCLE to "Cycle",
    LapseEngine.Format.DAYS to "Jours",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigScreen() {
    val context = LocalContext.current
    val prefs = remember { Config.prefs(context) }
    val zone = remember { ZoneId.systemDefault() }
    val engine = remember { LapseEngine(zone) }
    val renderer = remember { LapseRenderer() }

    var refMillis by remember {
        mutableStateOf(prefs.getLong(Config.KEY_REF, LapseEngine.defaultRef(zone)))
    }
    var format by remember {
        mutableStateOf(
            runCatching {
                LapseEngine.Format.valueOf(prefs.getString(Config.KEY_FORMAT, null) ?: "")
            }.getOrDefault(LapseEngine.Format.DETAIL)
        )
    }
    var secondsMode by remember {
        mutableStateOf(
            runCatching {
                LapseEngine.SecondsMode.valueOf(prefs.getString(Config.KEY_SECONDS, null) ?: "")
            }.getOrDefault(LapseEngine.SecondsMode.RING)
        )
    }
    var frame by remember { mutableStateOf(IntArray(25 * 25)) }
    var diff by remember { mutableStateOf<TimeBreakdown.Diff?>(null) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }

    fun now() = System.nanoTime() / 1e9

    fun save() {
        prefs.edit()
            .putLong(Config.KEY_REF, refMillis)
            .putString(Config.KEY_FORMAT, format.name)
            .putString(Config.KEY_SECONDS, secondsMode.name)
            .apply()
    }

    LaunchedEffect(Unit) {
        while (true) {
            if (engine.refMillis != refMillis) engine.setRef(refMillis)
            if (engine.format != format) engine.setFormat(format, now())
            engine.secondsMode = secondsMode
            engine.drainEvents()
            val snap = engine.update(System.currentTimeMillis(), now())
            frame = renderer.render(snap)
            diff = snap.diff
            delay(33)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BG)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "GLYPH LAPSE",
            color = TXT,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 6.sp,
        )
        Spacer(Modifier.height(20.dp))

        MatrixPreview(frame)

        Spacer(Modifier.height(12.dp))
        DiffReadout(refMillis, diff, zone)
        Spacer(Modifier.height(20.dp))

        SectionTitle("Date de référence")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(refMillis), zone)
            ConfigButton(ldt.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.FRENCH))) {
                showDate = true
            }
            ConfigButton(ldt.format(DateTimeFormatter.ofPattern("HH:mm"))) {
                showTime = true
            }
        }
        Spacer(Modifier.height(20.dp))

        SectionTitle("Format d'affichage")
        ChipsRow(
            entries = LapseEngine.Format.entries.map { it to (FORMAT_LABELS[it] ?: it.name) },
            selected = format,
        ) { format = it; save() }
        Spacer(Modifier.height(16.dp))

        SectionTitle("Secondes — minute en cours")
        ChipsRow(
            entries = listOf(
                LapseEngine.SecondsMode.RING to "Anneau",
                LapseEngine.SecondsMode.HOURGLASS to "Sablier",
            ),
            selected = secondsMode,
        ) { secondsMode = it; save() }
        Spacer(Modifier.height(20.dp))

        Text(
            "Appui long sur le Glyph Button : format suivant.",
            color = MUTED,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
        )
    }

    if (showDate) {
        val ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(refMillis), zone)
        val state = rememberDatePickerState(
            initialSelectedDateMillis = ldt.toLocalDate()
                .atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { utc ->
                        val date = Instant.ofEpochMilli(utc)
                            .atZone(ZoneId.of("UTC")).toLocalDate()
                        refMillis = date.atTime(ldt.toLocalTime()).atZone(zone)
                            .toInstant().toEpochMilli()
                        save()
                    }
                    showDate = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDate = false }) { Text("Annuler") }
            },
        ) { DatePicker(state) }
    }

    if (showTime) {
        val ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(refMillis), zone)
        val state = rememberTimePickerState(
            initialHour = ldt.hour,
            initialMinute = ldt.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = {
                    refMillis = ldt.toLocalDate()
                        .atTime(state.hour, state.minute)
                        .atZone(zone).toInstant().toEpochMilli()
                    save()
                    showTime = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTime = false }) { Text("Annuler") }
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TimePicker(state)
                }
            },
        )
    }
}

@Composable
private fun SectionTitle(label: String) {
    Text(
        label.uppercase(Locale.FRENCH),
        color = MUTED,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 2.sp,
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentWidth(Alignment.Start)
            .padding(bottom = 8.dp),
    )
}

@Composable
private fun <T> ChipsRow(
    entries: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        entries.forEach { (value, label) ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(label, fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = PANEL,
                    labelColor = MUTED,
                    selectedContainerColor = Color(0xFF2A1214),
                    selectedLabelColor = TXT,
                ),
            )
        }
    }
}

@Composable
private fun DiffReadout(refMillis: Long, diff: TimeBreakdown.Diff?, zone: ZoneId) {
    if (diff == null) return
    val ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(refMillis), zone)
    val dateStr = ldt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRENCH))
    val head = if (diff.direction == TimeBreakdown.Direction.SINCE) "Depuis le" else "Jusqu'au"
    val parts = buildList {
        if (diff.years > 0) add("${diff.years} an" + if (diff.years > 1) "s" else "")
        if (diff.months > 0) add("${diff.months} mois")
        if (diff.days > 0) add("${diff.days} jour" + if (diff.days > 1) "s" else "")
        if (diff.hours > 0) add("${diff.hours} h")
        if (diff.minutes > 0) add("${diff.minutes} min")
        add("${diff.seconds} s")
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "$head $dateStr",
            color = ACCENT,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            parts.joinToString(" "),
            color = MUTED,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun MatrixPreview(frame: IntArray) {
    Canvas(
        modifier = Modifier
            .size(300.dp)
            .background(Color(0xFF0B0B0D), CircleShape)
    ) {
        val n = 25
        val cell = size.width / n
        val led = cell * 0.66f
        val inset = (cell - led) / 2
        for (y in 0 until n) {
            for (x in 0 until n) {
                val dx = x - 12
                val dy = y - 12
                if (dx * dx + dy * dy > 12.5f * 12.5f) continue
                val topLeft = Offset(x * cell + inset, y * cell + inset)
                drawRect(
                    color = Color.White.copy(alpha = 0.05f),
                    topLeft = topLeft,
                    size = Size(led, led),
                )
                val v = frame[y * n + x]
                if (v > 5) {
                    drawRect(
                        color = Color(0xFFF8F8F4).copy(alpha = v / 255f),
                        topLeft = topLeft,
                        size = Size(led, led),
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfigButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF1E1E22),
            contentColor = TXT,
        ),
    ) {
        Text(label, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}
