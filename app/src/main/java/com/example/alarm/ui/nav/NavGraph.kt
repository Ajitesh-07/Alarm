package com.example.alarm.ui.nav

import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.with
import com.example.alarm.R
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.example.alarm.data.AlarmDatabase
import com.example.alarm.data.AlarmRepository
import com.example.alarm.ui.screens.alarm.AlarmsScreen
import com.example.alarm.ui.screens.stopwatch.StopwatchPage
import com.example.alarm.ui.screens.alarm.AddAlarmScreen
import com.example.alarm.ui.screens.alarm.AddEditAlarmViewModel
import com.example.alarm.ui.screens.alarm.AddEditAlarmViewModelFactory
import com.example.alarm.ui.screens.alarm.AlarmScheduler
import com.example.alarm.ui.screens.alarm.AlarmViewModel
import com.example.alarm.ui.screens.alarm.AlarmViewModelFactory

sealed class BottomNavItem(val route: String, val label: String, val icon: Int) {
    object Alarm : BottomNavItem("alarm", "Alarm", R.drawable.alarm_image)
    object Stopwatch : BottomNavItem("stopwatch", "Stopwatch", R.drawable.stopwatch_icon_foreground)

    object AddAlarm {
        private const val baseRoute = "add_alarm"
        const val route = "$baseRoute?alarmId={alarmId}"
        fun createRoute(alarmId: Int): String {
            return "$baseRoute?alarmId=$alarmId"
        }
    }
}

@Composable
fun HomeNav(
    db: AlarmDatabase
) {
    val navController = rememberNavController()

    val dao = db.alarmDao()
    val repository = AlarmRepository(dao)
    val alarmViewModel: AlarmViewModel = viewModel(factory = AlarmViewModelFactory(repository))
    val alarmScheduler: AlarmScheduler = AlarmScheduler(LocalContext.current.applicationContext)

    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedAlarmIds by remember { mutableStateOf(emptySet<Int>()) }

    val animDuration = 350
    val animSpec = tween<Float>(durationMillis = animDuration)
    val animSpec2 = tween<IntOffset>(durationMillis = animDuration)

    Scaffold(
        bottomBar = {
            AnimatedContent(
                targetState = isSelectionMode,
                transitionSpec = {
                    (slideInVertically { it } + fadeIn()) togetherWith
                            (slideOutVertically { it } + fadeOut())
                },
                label = "BottomBarTransition"
            ) { sm ->
                if (sm) {
                    SelectionBottomBar(
                        selectedCount = selectedAlarmIds.size,
                        onCancel = {
                            isSelectionMode = false
                            selectedAlarmIds = emptySet()
                        },
                        onDelete = {
                            alarmViewModel.deleteAlarms(selectedAlarmIds.toList())
                            for (selectedAlarmID in selectedAlarmIds) {
                                alarmScheduler.cancelAlarm(selectedAlarmID)
                            }
                            isSelectionMode = false
                            selectedAlarmIds = emptySet()
                        },
                        onEdit = {
                            if (selectedAlarmIds.size == 1) {
                                val alarmId = selectedAlarmIds.first()
                                navController.navigate(BottomNavItem.AddAlarm.createRoute(alarmId))
                            }
                            isSelectionMode = false
                            selectedAlarmIds = emptySet()
                        }
                    )
                } else {
                    BottomNavigationBar(navController)
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding),
            startDestination = BottomNavItem.Alarm.route
        ) {
            composable(BottomNavItem.Alarm.route,
                enterTransition = { fadeIn(animationSpec = animSpec) },
                exitTransition = {
                    slideOutHorizontally(targetOffsetX = { -it },
                    animationSpec = animSpec2) },
                popEnterTransition = {
                        slideInHorizontally(initialOffsetX = { -it },
                        animationSpec = animSpec2) },
                popExitTransition = { fadeOut(animationSpec = animSpec) }
            ) {

                AlarmsScreen(
                    viewModel = alarmViewModel,
                    onNavigate = { alarmId ->
                        navController.navigate(BottomNavItem.AddAlarm.createRoute(alarmId))
                    },
                    isSelectionMode = isSelectionMode,
                    selectedAlarmIds = selectedAlarmIds,
                    onToggleSelection = { alarmId ->
                        if (selectedAlarmIds.contains(alarmId)) {
                            selectedAlarmIds -= alarmId
                        } else {
                            selectedAlarmIds += alarmId
                        }
                        if (selectedAlarmIds.isEmpty()) {
                            isSelectionMode = false
                        }
                    },
                    onStartSelectionMode = { alarmId ->
                        isSelectionMode = true
                        selectedAlarmIds = setOf(alarmId)
                    }
                )

            }

            composable(
                route = BottomNavItem.AddAlarm.route,
                arguments = listOf(
                    navArgument("alarmId") {
                        type = NavType.IntType
                        nullable = false
                        defaultValue = -1
                    }
                ),
                enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = animSpec2) },
                exitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = animSpec2) },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }, animationSpec = animSpec2) },
                popExitTransition = { fadeOut(animationSpec = animSpec) }
            ) { backStackEntry ->
                val alarmId = backStackEntry.arguments?.getInt("alarmId")!!

                Log.d("ALARM", "Navigating to alarm#$alarmId")
                val addEditViewModel: AddEditAlarmViewModel =
                    viewModel(factory = AddEditAlarmViewModelFactory(repository, alarmId))

                AddAlarmScreen(
                    viewModel = addEditViewModel,
                    onBack = { navController.popBackStack() },
                    onSave = { navController.popBackStack() }
                )
            }

            composable(BottomNavItem.Stopwatch.route) {
                StopwatchPage()
            }
        }
    }
}

@Composable
fun BottomNavigationBar(
    navController: NavController
) {
    val items = listOf(
        BottomNavItem.Alarm,
        BottomNavItem.Stopwatch
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRouteFull = navBackStackEntry?.destination?.route
    val currentBaseRoute = currentRouteFull?.substringBefore('?')

    val animDuration = 350

    AnimatedVisibility(
        visible = currentBaseRoute in items.map { it.route },
        enter = fadeIn(animationSpec = tween(animDuration)) + slideInHorizontally(
            initialOffsetX = { it / 2 }, animationSpec = tween(animDuration)
        ),
        exit = fadeOut(animationSpec = tween(animDuration)) + slideOutHorizontally(
            targetOffsetX = { it / 2 }, animationSpec = tween(animDuration)
        )
    ) {
        NavigationBar(
            tonalElevation = 4.dp,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            windowInsets = NavigationBarDefaults.windowInsets
        ) {
            items.forEach { item ->
                NavigationBarItem(
                    icon = {
                        Icon(
                            painter = painterResource(item.icon),
                            contentDescription = item.label,
                            modifier = Modifier.size(48.dp)
                        )
                    },
                    onClick = {
                        navController.navigate(item.route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    label = {
                        Text(
                            text = item.label,
                            style = TextStyle(
                                fontWeight = if (item.route == currentBaseRoute) FontWeight.Bold else FontWeight.Normal
                            )
                        )
                    },
                    selected = item.route == currentBaseRoute,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                    )
                )
            }
        }
    }
}

@Composable
fun SelectionBottomBar(
    selectedCount: Int,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    NavigationBar(
        tonalElevation = 4.dp,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        NavigationBarItem(
            selected = false,
            onClick = onCancel,
            icon = { Icon(Icons.Filled.Close, contentDescription = "Cancel Selection") },
            label = { Text("Cancel") }
        )

        NavigationBarItem(
            selected = false,
            enabled = selectedCount == 1,
            onClick = onEdit,
            icon = { Icon(Icons.Filled.Edit, contentDescription = "Edit Alarm") },
            label = { Text("Edit") }
        )

        NavigationBarItem(
            selected = false,
            enabled = selectedCount > 0,
            onClick = onDelete,
            icon = { Icon(Icons.Filled.Delete, contentDescription = "Delete Alarms") },
            label = { Text("Delete") }
        )
    }
}