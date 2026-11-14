package com.codelearn.duo

import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        setContent {
            CodeLearnTheme { AppRoot() }
        }
    }
}

private data class NavTab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    NavTab("learn", "Learn", Icons.AutoMirrored.Filled.MenuBook),
    NavTab("quiz", "Quiz", Icons.Filled.School),
    NavTab("practice", "Practice", Icons.Filled.DataObject),
    NavTab("files", "Editor", Icons.Filled.Code),
    NavTab("more", "More", Icons.Filled.MoreHoriz)
)

private fun tabFor(route: String?): String = when {
    route == null -> "learn"
    route.startsWith("courses") -> "learn"
    route.startsWith("examples") || route.startsWith("challenge") -> "practice"
    else -> route
}

@Composable
fun AppRoot() {
    val vm: AppViewModel = viewModel()
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val hideBar = route != null && (route.startsWith("lesson") || route.startsWith("editor") || route.startsWith("quizplay"))
    val selectedTab = tabFor(route)

    Scaffold(
        containerColor = Pal.bg,
        bottomBar = {
            if (!hideBar) {
                NavigationBar(containerColor = Pal.card) {
                    TABS.forEach { t ->
                        NavigationBarItem(
                            selected = selectedTab == t.route,
                            onClick = {
                                nav.navigate(t.route) {
                                    popUpTo("learn") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(t.icon, contentDescription = t.label) },
                            label = { Text(t.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Pal.green, selectedTextColor = Pal.green,
                                unselectedIconColor = Pal.muted, unselectedTextColor = Pal.muted,
                                indicatorColor = Pal.elevated
                            )
                        )
                    }
                }
            }
        }
    ) { pad ->
        NavHost(
            navController = nav,
            startDestination = "learn",
            modifier = Modifier.padding(pad).consumeWindowInsets(pad).background(Pal.bg)
        ) {
            composable("learn") {
                LearnScreen(vm, onSwitchCourse = { nav.navigate("courses") }, onStart = { c, n -> nav.navigate("lesson/$c/$n") })
            }
            composable("courses") { SelectCourseScreen(vm) { nav.popBackStack() } }
            composable(
                "lesson/{courseId}/{chapter}",
                arguments = listOf(navArgument("chapter") { type = NavType.IntType })
            ) { e ->
                LessonPlayerScreen(
                    vm, e.arguments?.getString("courseId") ?: "python_basics", e.arguments?.getInt("chapter") ?: 1,
                    onClose = { nav.popBackStack() },
                    onTry = { path -> nav.navigate("editor?path=" + Uri.encode(path)) }
                )
            }
            composable("quiz") {
                QuizScreen(vm, onSwitchCourse = { nav.navigate("courses") }, onPlay = { mode, ch -> nav.navigate("quizplay/$mode/$ch") })
            }
            composable(
                "quizplay/{mode}/{chapter}",
                arguments = listOf(navArgument("chapter") { type = NavType.IntType })
            ) { e ->
                QuizPlayScreen(vm, e.arguments?.getString("mode") ?: "quick", e.arguments?.getInt("chapter") ?: 0) { nav.popBackStack() }
            }
            composable("practice") {
                PracticeScreen(vm, onExamples = { nav.navigate("examples/$it") }, onChallenges = { nav.navigate("challenges/$it") })
            }
            composable("examples/{courseId}") { e ->
                ExamplesScreen(vm, e.arguments?.getString("courseId") ?: "python_basics", { nav.popBackStack() }) { path ->
                    nav.navigate("editor?path=" + Uri.encode(path))
                }
            }
            composable("challenges/{courseId}") { e ->
                val cid = e.arguments?.getString("courseId") ?: "python_basics"
                ChallengesScreen(vm, cid, { nav.popBackStack() }) { id -> nav.navigate("challenge/$cid/$id") }
            }
            composable("challenge/{courseId}/{id}") { e ->
                ChallengeScreen(vm, e.arguments?.getString("courseId") ?: "python_basics", e.arguments?.getString("id") ?: "") { nav.popBackStack() }
            }
            composable("files") {
                EditorFilesScreen(vm) { path -> nav.navigate("editor?path=" + Uri.encode(path)) }
            }
            composable(
                "editor?path={path}",
                arguments = listOf(navArgument("path") { type = NavType.StringType; defaultValue = "" })
            ) { e ->
                CodeEditorScreen(vm, e.arguments?.getString("path") ?: "") { nav.popBackStack() }
            }
            composable("more") { MoreScreen(vm) }
        }
    }
}
