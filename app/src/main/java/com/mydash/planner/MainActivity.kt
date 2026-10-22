@file:OptIn(ExperimentalMaterial3Api::class)

package com.mydash.planner

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class Item(
    val id: Long, val kind: String, val title: String,
    val scope: String = "", val due: Long = 0L,
    val progress: Int = 0, val done: Boolean = false
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
                Item(o.getLong("id"), o.getString("kind"), o.getString("title"),
                    o.optString("scope"), o.optLong("due"), o.optInt("progress"), o.optBoolean("done"))
            }
        } catch (e: Exception) { emptyList() }
    }
    fun save(c: Context, items: List<Item>) {
        val a = JSONArray()
        items.forEach {
            a.put(JSONObject().put("id", it.id).put("kind", it.kind).put("title", it.title)
                .put("scope", it.scope).put("due", it.due).put("progress", it.progress).put("done", it.done))
        }
        file(c).writeText(a.toString())
    }
}

fun dayEnd(plus: Int): Long = Calendar.getInstance().apply {
    add(Calendar.DAY_OF_YEAR, plus)
    set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
}.timeInMillis

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { App() } }
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val items = remember { mutableStateListOf<Item>().also { it.addAll(Store.load(ctx)) } }
    var tab by remember { mutableStateOf(0) }
    var adding by remember { mutableStateOf(false) }
    val tabs = listOf("Today", "Tasks", "Goals", "Notes")
    fun persist() = Store.save(ctx, items)
    fun update(i: Item) {
        val ix = items.indexOfFirst { it.id == i.id }
        if (ix >= 0) { items[ix] = i; persist() }
    }
    fun remove(i: Item) { items.remove(i); persist() }

    val kind = when (tab) { 0, 1 -> "task"; 2 -> "goal"; else -> "note" }
    val endToday = dayEnd(0)
    val shown = when (tab) {
        0 -> items.filter { it.kind == "task" && !it.done && it.due <= endToday }
        1 -> items.filter { it.kind == "task" }.sortedWith(compareBy({ it.done }, { if (it.due == 0L) Long.MAX_VALUE else it.due }))
        2 -> items.filter { it.kind == "goal" }
        else -> items.filter { it.kind == "note" }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(tabs[tab]) }) },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = {}, label = { Text(t) })
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }) { Icon(Icons.Default.Add, "Add") }
        }
    ) { pad ->
        if (shown.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nothing here yet. Tap + to add.")
            }
        } else {
            LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(shown, key = { it.id }) { item ->
                    ItemRow(item, onChange = { update(it) }, onDelete = { remove(item) })
                }
            }
        }
    }

    if (adding) {
        AddDialog(kind, if (tab == 0) 0 else -1, onDismiss = { adding = false }) { title, scope, due ->
            items.add(Item(System.currentTimeMillis(), kind, title, scope, due))
            persist()
            adding = false
        }
    }
}

@Composable
fun ItemRow(item: Item, onChange: (Item) -> Unit, onDelete: () -> Unit) {
    val fmt = remember { SimpleDateFormat("EEE, d MMM", Locale.getDefault()) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.kind == "task") {
                    Checkbox(checked = item.done, onCheckedChange = { onChange(item.copy(done = it)) })
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        item.title, style = MaterialTheme.typography.titleMedium,
                        textDecoration = if (item.done) TextDecoration.LineThrough else null
                    )
                    if (item.kind == "task" && item.due > 0) {
                        val late = !item.done && item.due < System.currentTimeMillis()
                        Text("Due " + fmt.format(Date(item.due)), color = if (late) Color.Red else Color.Gray)
                    }
                    if (item.kind == "goal") Text(item.scope + " goal", color = Color.Gray)
                }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete") }
            }
            if (item.kind == "goal") {
                LinearProgressIndicator(progress = item.progress / 100f, modifier = Modifier.fillMaxWidth())
                Slider(
                    value = item.progress.toFloat(),
                    onValueChange = { onChange(item.copy(progress = it.toInt())) },
                    valueRange = 0f..100f
                )
                Text("${item.progress}% done")
            }
        }
    }
}

@Composable
fun AddDialog(kind: String, defaultDays: Int, onDismiss: () -> Unit, onAdd: (String, String, Long) -> Unit) {
    var title by remember { mutableStateOf("") }
    var scope by remember { mutableStateOf("Month") }
    var days by remember { mutableStateOf(defaultDays) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New $kind") },
        text = {
            Column {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") })
                Spacer(Modifier.height(8.dp))
                if (kind == "task") {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("No deadline" to -1, "Today" to 0, "Tomorrow" to 1, "1 week" to 7, "1 month" to 30).forEach { (l, d) ->
                            FilterChip(selected = days == d, onClick = { days = d }, label = { Text(l) })
                        }
                    }
                }
                if (kind == "goal") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Week", "Month", "Year").forEach {
                            FilterChip(selected = scope == it, onClick = { scope = it }, label = { Text(it) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isNotBlank()) onAdd(title.trim(), scope, if (days < 0) 0L else dayEnd(days))
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
