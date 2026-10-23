@file:OptIn(ExperimentalMaterial3Api::class)

package com.mydash.planner

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

// ---------- DATA ----------
data class Item(
    val id: Long, val kind: String, val title: String,
    val body: String = "", val scope: String = "Month",
    val due: Long = 0L, val priority: Int = 1,
    val target: Int = 10, val current: Int = 0,
    val done: Boolean = false, val pinned: Boolean = false,
    val days: String = ""
)

object Store {
    private fun file(c: Context) = File(c.filesDir, "items.json")
    fun load(c: Context): List<Item> {
        val f = file(c)
        if (!f.exists()) return emptyList()
        return try {
            val a = JSONArray(f.readText())
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                Item(
                    o.getLong("id"), o.getString("kind"), o.getString("title"),
                    o.optString("body"), o.optString("scope", "Month"),
                    o.optLong("due"), o.optInt("priority", 1),
                    o.optInt("target", 10), o.optInt("current"),
                    o.optBoolean("done"), o.optBoolean("pinned"), o.optString("days")
                )
            }
        } catch (e: Exception) { emptyList() }
    }
    fun save(c: Context, items: List<Item>) {
        val a = JSONArray()
        items.forEach {
            a.put(
                JSONObject().put("id", it.id).put("kind", it.kind).put("title", it.title)
                    .put("body", it.body).put("scope", it.scope).put("due", it.due)
                    .put("priority", it.priority).put("target", it.target).put("current", it.current)
                    .put("done", it.done).put("pinned", it.pinned).put("days", it.days)
            )
        }
        file(c).writeText(a.toString())
    }
}

// ---------- DATE HELPERS ----------
fun dayEnd(plus: Int): Long = Calendar.getInstance().apply {
    add(Calendar.DAY_OF_YEAR, plus)
    set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
}.timeInMillis

fun dayStart(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

fun localDayEnd(utcMillis: Long): Long {
    val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
    return Calendar.getInstance().apply {
        set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH), 23, 59, 59)
        set(Calendar.MILLISECOND, 999)
    }.timeInMillis
}

fun fmtDate(ms: Long): String = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date(ms))

fun dayKey(offset: Int): String {
    val c = Calendar.getInstance()
    c.add(Calendar.DAY_OF_YEAR, offset)
    return SimpleDateFormat("yyyyMMdd", Locale.US).format(c.time)
}

fun dayLetter(back: Int): String {
    val c = Calendar.getInstance()
    c.add(Calendar.DAY_OF_YEAR, -back)
    return SimpleDateFormat("EEE", Locale.getDefault()).format(c.time).take(1).uppercase()
}

fun habitSet(days: String): Set<String> = days.split(",").filter { it.isNotEmpty() }.toSet()

fun streak(days: String): Int {
    val s = habitSet(days)
    var o = if (dayKey(0) in s) 0 else -1
    var n = 0
    while (dayKey(o) in s) { n++; o-- }
    return n
}

// ---------- THEME (Nothing-inspired: black, white, red dot) ----------
class Pal(val bg: Color, val card: Color, val text: Color, val mute: Color, val line: Color)

val Red = Color(0xFFD71921)
val DarkPal = Pal(Color(0xFF000000), Color(0xFF111111), Color(0xFFFFFFFF), Color(0xFF8C8C8C), Color(0xFF2B2B2B))
val LightPal = Pal(Color(0xFFF1F1F1), Color(0xFFFFFFFF), Color(0xFF000000), Color(0xFF6E6E6E), Color(0xFFD9D9D9))
val LocalPal = compositionLocalOf { DarkPal }

@Composable
fun Tx(
    text: String, size: Int = 14, color: Color = LocalPal.current.text, bold: Boolean = false,
    deco: TextDecoration? = null, modifier: Modifier = Modifier, upper: Boolean = false,
    maxLines: Int = Int.MAX_VALUE
) {
    Text(
        if (upper) text.uppercase() else text, modifier = modifier, color = color,
        fontSize = size.sp, fontFamily = FontFamily.Monospace,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        textDecoration = deco, maxLines = maxLines, overflow = TextOverflow.Ellipsis
    )
}

@Composable
fun Dots(p: Float, n: Int = 20) {
    val pal = LocalPal.current
    val on = (p.coerceIn(0f, 1f) * n).roundToInt()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        repeat(n) { i ->
            Box(Modifier.size(7.dp).clip(CircleShape).background(if (i < on) Red else pal.line))
        }
    }
}

@Composable
fun Pill(label: String, selected: Boolean, onClick: () -> Unit) {
    val pal = LocalPal.current
    val shape = RoundedCornerShape(50)
    Box(
        Modifier.clip(shape)
            .background(if (selected) Red else Color.Transparent)
            .border(1.dp, if (selected) Red else pal.line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) { Tx(label, 12, if (selected) Color.White else pal.text, upper = true) }
}

@Composable
fun RoundBtn(label: String, onClick: () -> Unit) {
    val pal = LocalPal.current
    Box(
        Modifier.size(38.dp).clip(CircleShape).border(1.dp, pal.mute, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Tx(label, 18) }
}

@Composable
fun Card2(onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val pal = LocalPal.current
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(pal.card).border(1.dp, pal.line, shape)
            .let { m -> if (onClick != null) m.clickable(onClick = onClick) else m }
            .padding(16.dp),
        content = content
    )
}

@Composable
fun Label(text: String) {
    Tx(text, 11, LocalPal.current.mute, upper = true, modifier = Modifier.padding(top = 10.dp, start = 4.dp))
}

@Composable
fun Empty(msg: String) {
    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
        Tx(msg, 13, LocalPal.current.mute)
    }
}

// ---------- ACTIVITY ----------
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("p", Context.MODE_PRIVATE) }
    var dark by remember { mutableStateOf(prefs.getBoolean("dark", true)) }
    val pal = if (dark) DarkPal else LightPal
    val items = remember { mutableStateListOf<Item>().also { it.addAll(Store.load(ctx)) } }
    var tab by remember { mutableStateOf(0) }
    var edit by remember { mutableStateOf<Item?>(null) }
    var focus by remember { mutableStateOf(false) }
    val tabs = listOf("Today", "Tasks", "Goals", "Habits", "Notes")
    val kinds = listOf("task", "task", "goal", "habit", "note")

    val act = ctx as ComponentActivity
    SideEffect {
        act.window.statusBarColor = pal.bg.toArgb()
        act.window.navigationBarColor = pal.bg.toArgb()
        val c = WindowCompat.getInsetsController(act.window, act.window.decorView)
        c.isAppearanceLightStatusBars = !dark
        c.isAppearanceLightNavigationBars = !dark
    }

    fun persist() = Store.save(ctx, items)
    fun upsert(i: Item) {
        val ix = items.indexOfFirst { it.id == i.id }
        if (ix >= 0) items[ix] = i else items.add(0, i)
        persist()
    }
    fun remove(i: Item) { items.removeAll { it.id == i.id }; persist() }

    val scheme = if (dark)
        darkColorScheme(primary = Red, background = pal.bg, surface = pal.card, onSurface = pal.text, onBackground = pal.text, surfaceVariant = pal.card, onSurfaceVariant = pal.mute)
    else
        lightColorScheme(primary = Red, background = pal.bg, surface = pal.card, onSurface = pal.text, onBackground = pal.text, surfaceVariant = pal.card, onSurfaceVariant = pal.mute)

    CompositionLocalProvider(LocalPal provides pal) {
        MaterialTheme(colorScheme = scheme) {
            Box(Modifier.fillMaxSize().background(pal.bg)) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Tx(SimpleDateFormat("EEEE, d MMM", Locale.getDefault()).format(Date()), 11, pal.mute, upper = true)
                            Tx(tabs[tab], 30, bold = true, upper = true)
                        }
                        Pill("Focus", false) { focus = true }
                        Spacer(Modifier.width(6.dp))
                        Pill(if (dark) "Light" else "Dark", false) {
                            dark = !dark
                            prefs.edit().putBoolean("dark", dark).apply()
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        when (tab) {
                            0 -> TodayScreen(items, { upsert(it) }, { edit = it }) {
                                upsert(Item(System.currentTimeMillis(), "task", it, due = dayEnd(0)))
                            }
                            1 -> TasksScreen(items, { upsert(it) }, { edit = it })
                            2 -> GoalsScreen(items, { upsert(it) }, { edit = it })
                            3 -> HabitsScreen(items, { upsert(it) }, { edit = it })
                            else -> NotesScreen(items) { edit = it }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        tabs.forEachIndexed { i, t ->
                            Column(
                                Modifier.clickable { tab = i }.padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Tx(t, 11, if (tab == i) pal.text else pal.mute, bold = tab == i, upper = true)
                                Spacer(Modifier.height(4.dp))
                                Box(Modifier.size(5.dp).clip(CircleShape).background(if (tab == i) Red else Color.Transparent))
                            }
                        }
                    }
                }
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 78.dp)
                        .size(58.dp).clip(CircleShape).background(Red)
                        .clickable {
                            edit = Item(System.currentTimeMillis(), kinds[tab], "", due = if (tab == 0) dayEnd(0) else 0L)
                        },
                    contentAlignment = Alignment.Center
                ) { Tx("+", 30, Color.White) }
            }

            edit?.let { e ->
                EditDialog(
                    e, items.any { it.id == e.id },
                    onSave = { upsert(it); edit = null },
                    onDelete = { remove(e); edit = null },
                    onDismiss = { edit = null }
                )
            }
            if (focus) FocusDialog { focus = false }
        }
    }
}

// ---------- SCREENS ----------
@Composable
fun TodayScreen(items: List<Item>, onUpsert: (Item) -> Unit, onEdit: (Item) -> Unit, onQuick: (String) -> Unit) {
    val s = dayStart()
    val e = dayEnd(0)
    val all = items.filter { it.kind == "task" && ((it.due in s..e) || (!it.done && it.due in 1 until s)) }
    val overdue = all.filter { !it.done && it.due < s }
    val todays = all.filter { it.due >= s }.sortedWith(compareBy<Item>({ it.done }, { -it.priority }))
    val habits = items.filter { it.kind == "habit" }
    val hDone = habits.count { dayKey(0) in habitSet(it.days) }
    val doneCount = all.count { it.done }

    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card2 {
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Tx("Done today", 11, LocalPal.current.mute, upper = true)
                        Tx("$doneCount/${all.size}", 44, bold = true)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Tx("Habits", 11, LocalPal.current.mute, upper = true)
                        Tx("$hDone/${habits.size}", 22, bold = true)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Dots(if (all.isEmpty()) 0f else doneCount.toFloat() / all.size, 24)
            }
        }
        item { QuickAdd(onQuick) }
        if (overdue.isNotEmpty()) {
            item { Label("Overdue") }
            items(overdue, key = { "o${it.id}" }) { TaskRow(it, onUpsert, onEdit) }
        }
        item { Label("Today") }
        if (todays.isEmpty()) item { Empty("Nothing planned. Add a task above.") }
        items(todays, key = { "t${it.id}" }) { TaskRow(it, onUpsert, onEdit) }
        if (habits.isNotEmpty()) {
            item { Label("Habits") }
            items(habits, key = { "h${it.id}" }) { HabitRow(it, onUpsert, onEdit, compact = true) }
        }
    }
}

@Composable
fun QuickAdd(onAdd: (String) -> Unit) {
    var t by remember { mutableStateOf("") }
    OutlinedTextField(
        value = t, onValueChange = { t = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
        shape = RoundedCornerShape(50),
        placeholder = { Tx("Quick add for today...", 14, LocalPal.current.mute) },
        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (t.isNotBlank()) { onAdd(t.trim()); t = "" } })
    )
}

@Composable
fun TasksScreen(items: List<Item>, onUpsert: (Item) -> Unit, onEdit: (Item) -> Unit) {
    var f by remember { mutableStateOf(0) }
    val list = items.filter { it.kind == "task" && (f == 0 || (f == 1 && !it.done) || (f == 2 && it.done)) }
        .sortedWith(compareBy<Item>({ it.done }, { -it.priority }, { if (it.due == 0L) Long.MAX_VALUE else it.due }))
    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("All", "Pending", "Done").forEachIndexed { i, l -> Pill(l, f == i) { f = i } }
            }
        }
        if (list.isEmpty()) item { Empty("No tasks here. Tap + to add.") }
        items(list, key = { it.id }) { TaskRow(it, onUpsert, onEdit) }
    }
}

@Composable
fun TaskRow(item: Item, onUpsert: (Item) -> Unit, onEdit: (Item) -> Unit) {
    val pal = LocalPal.current
    val late = !item.done && item.due > 0 && item.due < dayStart()
    Card2(onClick = { onEdit(item) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(26.dp).clip(CircleShape)
                    .background(if (item.done) Red else Color.Transparent)
                    .border(1.5.dp, if (item.done) Red else pal.mute, CircleShape)
                    .clickable { onUpsert(item.copy(done = !item.done)) },
                contentAlignment = Alignment.Center
            ) { if (item.done) Tx("v", 13, Color.White, bold = true) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Tx(
                    item.title, 16, if (item.done) pal.mute else pal.text,
                    deco = if (item.done) TextDecoration.LineThrough else null
                )
                if (item.due > 0) {
                    Tx((if (late) "Overdue - " else "Due ") + fmtDate(item.due), 11, if (late) Red else pal.mute, upper = true)
                }
                if (item.body.isNotBlank()) Tx(item.body, 12, pal.mute, maxLines = 2)
            }
            if (item.priority == 2) Tx("High", 10, Red, bold = true, upper = true)
            if (item.priority == 0) Tx("Low", 10, pal.mute, upper = true)
        }
    }
}

@Composable
fun GoalsScreen(items: List<Item>, onUpsert: (Item) -> Unit, onEdit: (Item) -> Unit) {
    var f by remember { mutableStateOf(0) }
    val opts = listOf("All", "Week", "Month", "Year")
    val list = items.filter { it.kind == "goal" && (f == 0 || it.scope == opts[f]) }
    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                opts.forEachIndexed { i, l -> Pill(l, f == i) { f = i } }
            }
        }
        if (list.isEmpty()) item { Empty("No goals yet. Set a week, month or year target.") }
        items(list, key = { it.id }) { g ->
            val pal = LocalPal.current
            val p = if (g.target > 0) g.current.toFloat() / g.target else 0f
            Card2(onClick = { onEdit(g) }) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Tx(g.scope + " goal", 10, Red, bold = true, upper = true)
                        Tx(g.title, 17, bold = true)
                        if (g.due > 0) Tx("By " + fmtDate(g.due), 11, pal.mute, upper = true)
                        if (g.body.isNotBlank()) Tx(g.body, 12, pal.mute, maxLines = 2)
                    }
                    Tx("${(p * 100).roundToInt()}%", 26, bold = true)
                }
                Spacer(Modifier.height(12.dp))
                Dots(p, 24)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Tx("${g.current} / ${g.target}", 13, pal.mute, modifier = Modifier.weight(1f))
                    RoundBtn("-") { onUpsert(g.copy(current = (g.current - 1).coerceAtLeast(0))) }
                    Spacer(Modifier.width(8.dp))
                    RoundBtn("+") { onUpsert(g.copy(current = g.current + 1)) }
                }
            }
        }
    }
}

@Composable
fun HabitsScreen(items: List<Item>, onUpsert: (Item) -> Unit, onEdit: (Item) -> Unit) {
    val list = items.filter { it.kind == "habit" }
    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (list.isEmpty()) item { Empty("Build a daily habit. Tap + to add one.") }
        items(list, key = { it.id }) { HabitRow(it, onUpsert, onEdit, compact = false) }
    }
}

@Composable
fun HabitRow(item: Item, onUpsert: (Item) -> Unit, onEdit: (Item) -> Unit, compact: Boolean) {
    val pal = LocalPal.current
    val set = habitSet(item.days)
    val today = dayKey(0)
    val doneToday = today in set
    Card2(onClick = { onEdit(item) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Tx(item.title, 16, bold = true)
                Tx("Streak ${streak(item.days)} days", 10, pal.mute, upper = true)
            }
            Box(
                Modifier.size(30.dp).clip(CircleShape)
                    .background(if (doneToday) Red else Color.Transparent)
                    .border(1.5.dp, if (doneToday) Red else pal.mute, CircleShape)
                    .clickable {
                        val ns = if (doneToday) set - today else set + today
                        onUpsert(item.copy(days = ns.joinToString(",")))
                    },
                contentAlignment = Alignment.Center
            ) { if (doneToday) Tx("v", 14, Color.White, bold = true) }
        }
        if (!compact) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for (b in 6 downTo 0) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(14.dp).clip(CircleShape).background(if (dayKey(-b) in set) Red else pal.line))
                        Spacer(Modifier.height(4.dp))
                        Tx(dayLetter(b), 9, pal.mute)
                    }
                }
            }
        }
    }
}

@Composable
fun NotesScreen(items: List<Item>, onEdit: (Item) -> Unit) {
    val pal = LocalPal.current
    val list = items.filter { it.kind == "note" }.sortedByDescending { it.pinned }
    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (list.isEmpty()) item { Empty("Jot down ideas and things to remember.") }
        items(list, key = { it.id }) { n ->
            Card2(onClick = { onEdit(n) }) {
                Row {
                    Tx(n.title, 16, bold = true, modifier = Modifier.weight(1f), maxLines = 2)
                    if (n.pinned) Tx("Pinned", 10, Red, bold = true, upper = true)
                }
                if (n.body.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Tx(n.body, 13, pal.mute, maxLines = 5)
                }
            }
        }
    }
}

// ---------- DIALOGS ----------
@Composable
fun EditDialog(start: Item, exists: Boolean, onSave: (Item) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val pal = LocalPal.current
    val k = start.kind
    var title by remember { mutableStateOf(start.title) }
    var body by remember { mutableStateOf(start.body) }
    var due by remember { mutableStateOf(start.due) }
    var pr by remember { mutableStateOf(start.priority) }
    var scope by remember { mutableStateOf(start.scope) }
    var target by remember { mutableStateOf(start.target.toString()) }
    var pinned by remember { mutableStateOf(start.pinned) }
    var picker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = pal.card,
        title = { Tx((if (exists) "Edit " else "New ") + k, 14, Red, bold = true, upper = true) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Tx(if (k == "note") "Title (optional)" else "Title", 12, pal.mute) }
                )
                if (k == "task" || k == "goal" || k == "note") {
                    OutlinedTextField(
                        value = body, onValueChange = { body = it }, modifier = Modifier.fillMaxWidth(),
                        minLines = if (k == "note") 4 else 2,
                        label = { Tx(if (k == "note") "Note" else "Details", 12, pal.mute) }
                    )
                }
                if (k == "task") {
                    Tx("Priority", 10, pal.mute, upper = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Low", "Med", "High").forEachIndexed { i, l -> Pill(l, pr == i) { pr = i } }
                    }
                }
                if (k == "goal") {
                    Tx("Period", 10, pal.mute, upper = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Week", "Month", "Year").forEach { Pill(it, scope == it) { scope = it } }
                    }
                    OutlinedTextField(
                        value = target, onValueChange = { target = it.filter { c -> c.isDigit() }.take(6) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = { Tx("Target number (e.g. 12 books)", 12, pal.mute) }
                    )
                }
                if (k == "task" || k == "goal") {
                    Tx("Deadline", 10, pal.mute, upper = true)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val presets = listOf(0L, dayEnd(0), dayEnd(1), dayEnd(7))
                        Pill("None", due == 0L) { due = 0L }
                        Pill("Today", due == dayEnd(0)) { due = dayEnd(0) }
                        Pill("Tomorrow", due == dayEnd(1)) { due = dayEnd(1) }
                        Pill("+1 week", due == dayEnd(7)) { due = dayEnd(7) }
                        Pill("Pick date", due > 0 && due !in presets) { picker = true }
                    }
                    if (due > 0) Tx(fmtDate(due), 12, pal.text)
                }
                if (k == "note") {
                    Row { Pill(if (pinned) "Pinned" else "Pin note", pinned) { pinned = !pinned } }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val t = if (title.isBlank() && k == "note") body.trim().lines().firstOrNull().orEmpty().take(40) else title.trim()
                if (t.isNotBlank()) {
                    onSave(
                        start.copy(
                            title = t, body = body.trim(), due = due, priority = pr, scope = scope,
                            target = (target.toIntOrNull() ?: 10).coerceAtLeast(1), pinned = pinned
                        )
                    )
                }
            }) { Tx("Save", 13, Red, bold = true, upper = true) }
        },
        dismissButton = {
            Row {
                if (exists) TextButton(onClick = onDelete) { Tx("Delete", 13, pal.mute, upper = true) }
                TextButton(onClick = onDismiss) { Tx("Cancel", 13, pal.mute, upper = true) }
            }
        }
    )

    if (picker) {
        val st = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { picker = false },
            confirmButton = {
                TextButton(onClick = {
                    st.selectedDateMillis?.let { due = localDayEnd(it) }
                    picker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picker = false }) { Text("Cancel") } }
        ) { DatePicker(state = st) }
    }
}

@Composable
fun FocusDialog(onClose: () -> Unit) {
    val pal = LocalPal.current
    var total by remember { mutableStateOf(25 * 60) }
    var left by remember { mutableStateOf(25 * 60) }
    var running by remember { mutableStateOf(false) }

    LaunchedEffect(running) {
        while (running && left > 0) { delay(1000); left-- }
        if (left <= 0) running = false
    }

    AlertDialog(
        onDismissRequest = onClose,
        containerColor = pal.card,
        title = { Tx("Focus timer", 14, Red, bold = true, upper = true) },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Tx(if (left <= 0) "DONE" else "%02d:%02d".format(left / 60, left % 60), 54, bold = true)
                Spacer(Modifier.height(14.dp))
                Dots(1f - left / total.toFloat(), 25)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(15, 25, 45).forEach { m ->
                        Pill("$m min", total == m * 60) {
                            if (!running) { total = m * 60; left = total }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(if (running) "Pause" else "Start", true) {
                        if (left <= 0) left = total
                        running = !running
                    }
                    Pill("Reset", false) { running = false; left = total }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Tx("Close", 13, pal.mute, upper = true) } }
    )
}
