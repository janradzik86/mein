package pl.terminownik.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.navigation.compose.rememberNavController
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import pl.terminownik.app.AppContainer
import pl.terminownik.app.R
import pl.terminownik.app.domain.AppSettings
import pl.terminownik.app.domain.Section
import pl.terminownik.app.ui.screens.AboutScreen
import pl.terminownik.app.ui.screens.DeadlinesScreen
import pl.terminownik.app.ui.screens.ItemEditScreen
import pl.terminownik.app.ui.screens.ItemListScreen
import pl.terminownik.app.ui.screens.LicensesScreen
import pl.terminownik.app.ui.screens.OcrScreenRoute
import pl.terminownik.app.ui.screens.OnboardingScreen
import pl.terminownik.app.ui.screens.PrivacyScreen
import pl.terminownik.app.ui.screens.ScannerScreen
import pl.terminownik.app.ui.screens.SettingsScreen
import pl.terminownik.app.ui.screens.ShoppingScreen

/** Trasy nawigacji. Argumenty są proste (String/Long/Boolean), bez typów niestandardowych. */
object Routes {
    const val ONBOARDING = "onboarding"
    const val ITEMS = "items/{section}"
    const val DEADLINES = "deadlines"
    const val SHOPPING = "shopping"
    const val SETTINGS = "settings"
    const val EDIT = "edit/{section}/{itemId}"
    const val SCANNER = "scanner/{section}"
    const val OCR = "ocr/{section}"
    const val ABOUT = "about"
    const val PRIVACY = "privacy"
    const val LICENSES = "licenses"

    fun items(section: Section) = "items/${section.name}"
    fun edit(section: Section, itemId: Long = -1L) = "edit/${section.name}/$itemId"
    fun scanner(section: Section) = "scanner/${section.name}"
    fun ocr(section: Section) = "ocr/${section.name}"
}

private data class BottomItem(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
)

@Composable
fun TerminownikApp(
    container: AppContainer,
    settings: AppSettings,
    onReady: () -> Unit,
    startItemId: Long = -1L,
    startOnDeadlines: Boolean = false,
) {
    val navController = rememberNavController()
    LaunchedEffect(settings) { onReady() }

    // Nadrobienie pominiętych przypomnień przy każdym otwarciu aplikacji.
    LaunchedEffect(Unit) {
        runCatching { container.reminderScheduler.catchUp() }
    }

    // Powiadomienie może wskazywać konkretną pozycję albo widok terminów.
    LaunchedEffect(startItemId, startOnDeadlines) {
        if (startItemId > 0) {
            navController.navigate(Routes.edit(Section.PANTRY, startItemId)) { launchSingleTop = true }
        } else if (startOnDeadlines) {
            navController.navigate(Routes.DEADLINES) { launchSingleTop = true }
        }
    }

    if (!settings.onboardingDone) {
        OnboardingScreen(
            onFinish = {
                navController.navigate(Routes.items(Section.PANTRY)) {
                    popUpTo(Routes.ONBOARDING) { inclusive = true }
                }
            },
        )
        return
    }

    val bottomItems = listOf(
        BottomItem(Routes.items(Section.PANTRY), R.string.nav_pantry, Icons.Filled.Restaurant),
        BottomItem(Routes.items(Section.MEDICINE), R.string.nav_medicine, Icons.Filled.LocalPharmacy),
        BottomItem(Routes.DEADLINES, R.string.nav_deadlines, Icons.Filled.DateRange),
        BottomItem(Routes.SHOPPING, R.string.nav_shopping, Icons.Filled.ShoppingCart),
        BottomItem(Routes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings),
    )

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = bottomItems.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = { navController.navigateBottom(item.route) },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(stringResource(item.labelRes)) },
                            alwaysShowLabel = false,
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.items(Section.PANTRY),
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    onFinish = {
                        navController.navigate(Routes.items(Section.PANTRY)) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    },
                )
            }
            composable(
                Routes.ITEMS,
                arguments = listOf(navArgument("section") { type = NavType.StringType }),
            ) { entry ->
                val section = Section.fromRaw(entry.arguments?.getString("section"))
                ItemListScreen(
                    container = container,
                    section = section,
                    onOpenItem = { id -> navController.navigate(Routes.edit(section, id)) },
                    onAddItem = { navController.navigate(Routes.edit(section)) },
                    onScan = { navController.navigate(Routes.scanner(section)) },
                    onOcr = { navController.navigate(Routes.ocr(section)) },
                    onOpenDeadlines = { navController.navigateBottom(Routes.DEADLINES) },
                )
            }
            composable(Routes.DEADLINES) {
                DeadlinesScreen(
                    container = container,
                    onOpenItem = { section, id -> navController.navigate(Routes.edit(section, id)) },
                    onAddItem = { section -> navController.navigate(Routes.edit(section)) },
                    onScan = { section -> navController.navigate(Routes.scanner(section)) },
                    onOpenSettings = { navController.navigateBottom(Routes.SETTINGS) },
                )
            }
            composable(Routes.SHOPPING) {
                ShoppingScreen(container = container)
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    container = container,
                    onOpenAbout = { navController.navigate(Routes.ABOUT) },
                    onOpenPrivacy = { navController.navigate(Routes.PRIVACY) },
                    onOpenLicenses = { navController.navigate(Routes.LICENSES) },
                    onDataErased = {
                        navController.navigate(Routes.items(Section.PANTRY)) {
                            popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                        }
                    },
                )
            }
            composable(
                Routes.EDIT,
                arguments = listOf(
                    navArgument("section") { type = NavType.StringType },
                    navArgument("itemId") { type = NavType.LongType },
                ),
            ) { entry ->
                val section = Section.fromRaw(entry.arguments?.getString("section"))
                val itemId = entry.arguments?.getLong("itemId") ?: -1L
                ItemEditScreen(
                    container = container,
                    section = section,
                    itemId = itemId,
                    onDone = { navController.popBackStack() },
                    onScan = { navController.navigate(Routes.scanner(section)) },
                    onOcr = { navController.navigate(Routes.ocr(section)) },
                )
            }
            composable(
                Routes.SCANNER,
                arguments = listOf(navArgument("section") { type = NavType.StringType }),
            ) { entry ->
                val section = Section.fromRaw(entry.arguments?.getString("section"))
                ScannerScreen(
                    container = container,
                    section = section,
                    onUseResult = { interpretation ->
                        navController.navigate(Routes.edit(section)) {
                            launchSingleTop = true
                        }
                        // Dane skanu przekazujemy przez jednorazowy bufor w kontenerze.
                        container.pendingScan = interpretation
                    },
                    onManual = { navController.navigate(Routes.edit(section)) },
                    onOcr = { navController.navigate(Routes.ocr(section)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                Routes.OCR,
                arguments = listOf(navArgument("section") { type = NavType.StringType }),
            ) { entry ->
                val section = Section.fromRaw(entry.arguments?.getString("section"))
                OcrScreenRoute(
                    container = container,
                    section = section,
                    onUseResult = { candidate ->
                        container.pendingOcr = candidate
                        navController.navigate(Routes.edit(section)) { launchSingleTop = true }
                    },
                    onManual = { navController.navigate(Routes.edit(section)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.ABOUT) { AboutScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.PRIVACY) { PrivacyScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.LICENSES) { LicensesScreen(onBack = { navController.popBackStack() }) }
        }
    }
}

private fun NavHostController.navigateBottom(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
