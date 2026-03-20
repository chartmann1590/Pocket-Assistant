package com.charles.pocketassistant.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.charles.pocketassistant.ui.TasksViewModel
import com.charles.pocketassistant.ui.components.SimpleInput

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(nav: NavHostController, vm: TasksViewModel = hiltViewModel()) {
    val today by vm.today.collectAsState()
    val upcoming by vm.upcoming.collectAsState()
    val open by vm.open.collectAsState()
    val done by vm.done.collectAsState()
    var tab by remember { mutableStateOf(0) }
    var newTask by remember { mutableStateOf("") }
    Scaffold(topBar = { TopAppBar(title = { Text("Tasks") }) }) { p ->
        Column(Modifier.fillMaxSize().padding(p)) {
            TabRow(selectedTabIndex = tab) {
                Tab(tab == 0, onClick = { tab = 0 }, text = { Text("Today") })
                Tab(tab == 1, onClick = { tab = 1 }, text = { Text("Upcoming") })
                Tab(tab == 2, onClick = { tab = 2 }, text = { Text("Done") })
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SimpleInput("Manual add task", newTask, { newTask = it }, Modifier.weight(1f))
                Button(onClick = { vm.addManualTask(newTask); newTask = "" }) { Text("Add") }
            }
            LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val list = when (tab) {
                    0 -> today.ifEmpty { open }
                    1 -> upcoming
                    else -> done
                }
                items(list) { task ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(task.title, Modifier.weight(1f))
                            TextButton(onClick = { vm.toggle(task) }) { Text(if (task.isDone) "Undo" else "Done") }
                        }
                    }
                }
            }
        }
    }
}
