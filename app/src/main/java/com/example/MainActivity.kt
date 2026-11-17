package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CandlestickChart
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CandlestickChart
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.firebase.TradingRepository
import com.example.data.network.LiveMarketRepository
import com.example.model.UiState
import com.example.ui.TradingViewModel
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOutUser
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.LedgerScreen
import com.example.ui.screens.PositionsScreen
import com.example.ui.screens.RiskSettingsScreen
import com.example.ui.screens.TerminalScreen
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberOnContainer
import com.example.ui.theme.CrimsonBear
import com.example.ui.theme.EmeraldBull
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldOnContainer
import com.example.ui.theme.PipGuardTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

const val ROUTE_DASHBOARD = "dashboard"
const val ROUTE_TERMINAL = "terminal"
const val ROUTE_POSITIONS = "positions"
const val ROUTE_LEDGER = "ledger"
const val ROUTE_RISK = "risk"

private data class NavDestinationItem(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val navItems = listOf(
    NavDestinationItem(ROUTE_DASHBOARD, R.string.nav_dashboard, Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
    NavDestinationItem(ROUTE_TERMINAL, R.string.nav_chart, Icons.Filled.CandlestickChart, Icons.Outlined.CandlestickChart),
    NavDestinationItem(ROUTE_POSITIONS, R.string.nav_positions, Icons.Filled.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet),
    NavDestinationItem(ROUTE_LEDGER, R.string.nav_ledger, Icons.AutoMirrored.Filled.ReceiptLong, Icons.AutoMirrored.Outlined.ReceiptLong),
    NavDestinationItem(ROUTE_RISK, R.string.nav_risk, Icons.Filled.Shield, Icons.Outlined.Shield)
)

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PipGuardTheme(darkTheme = true) {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            TradingAppShell(
                currentUserId = user.uid,
                userDisplayName = user.displayName ?: user.email?.substringBefore("@") ?: "FX Trader",
                userEmail = user.email
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradingAppShell(
    currentUserId: String,
    userDisplayName: String,
    userEmail: String?
) {
    val viewModel: TradingViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                TradingViewModel(
                    repository = TradingRepository(db),
                    marketRepository = LiveMarketRepository(),
                    currentUserId = currentUserId,
                    initialDisplayName = userDisplayName
                )
            }
        }
    )

    val snapshot by viewModel.terminalSnapshot.collectAsStateWithLifecycle()
    val accountState by viewModel.accountState.collectAsStateWithLifecycle()
    val tradeHistoryState by viewModel.tradeHistoryState.collectAsStateWithLifecycle()
    val historySort by viewModel.historySort.collectAsStateWithLifecycle()
    val isRefreshingHistory by viewModel.isRefreshingHistory.collectAsStateWithLifecycle()
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()
    val latestEvent by viewModel.latestEvent.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val enabledOverlays by viewModel.enabledOverlays.collectAsStateWithLifecycle()
    val selectedOscillator by viewModel.selectedOscillator.collectAsStateWithLifecycle()
    val activeMarkingTool by viewModel.activeMarkingTool.collectAsStateWithLifecycle()
    val drawingsByPair by viewModel.drawingsByPair.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val snackbarHostState = remember { SnackbarHostState() }
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    LaunchedEffect(actionError) {
        val err = actionError
        if (!err.isNullOrBlank()) {
            snackbarHostState.showSnackbar(err)
            viewModel.dismissError()
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val useRail = maxWidth >= 720.dp

        Row(modifier = Modifier.fillMaxSize()) {
            if (useRail) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxHeight()
                        .windowInsetsPadding(WindowInsets.statusBars)
                ) {
                    Spacer(modifier = Modifier.padding(top = 12.dp))
                    navItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                        NavigationRailItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                NavItemIcon(
                                    item = item,
                                    selected = selected,
                                    openCount = snapshot.openTrades.size
                                )
                            },
                            label = { Text(stringResource(item.labelRes)) }
                        )
                    }
                }
            }

            Scaffold(
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.background,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Shield,
                                    contentDescription = null,
                                    tint = EmeraldBull,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }
                        },
                        actions = {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (snapshot.account.botArmed) EmeraldContainer else AmberContainer,
                                modifier = Modifier.padding(end = 12.dp)
                            ) {
                                Text(
                                    text = if (snapshot.account.circuitBreakerTripped) {
                                        "LOCKED"
                                    } else if (snapshot.account.botArmed) {
                                        "ARMED"
                                    } else {
                                        "STANDBY"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (snapshot.account.botArmed) EmeraldOnContainer else AmberOnContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                },
                bottomBar = {
                    if (!useRail) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            windowInsets = WindowInsets.navigationBars
                        ) {
                            navItems.forEach { item ->
                                val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(item.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        NavItemIcon(
                                            item = item,
                                            selected = selected,
                                            openCount = snapshot.openTrades.size
                                        )
                                    },
                                    label = { Text(stringResource(item.labelRes)) },
                                    modifier = Modifier.testTag("nav_${item.route}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    // Live execution banner
                    AnimatedVisibility(visible = latestEvent != null) {
                        val event = latestEvent
                        if (event != null) {
                            Surface(
                                color = if (event.isPositive) EmeraldContainer else AmberContainer,
                                border = BorderStroke(
                                    1.dp,
                                    if (event.isPositive) EmeraldBull.copy(alpha = 0.4f) else CrimsonBear.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = event.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (event.isPositive) EmeraldOnContainer else AmberOnContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = { viewModel.dismissEvent() }) {
                                        Text("Dismiss", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }

                    // Offline ECB feed notice if network failed
                    AnimatedVisibility(visible = snapshot.syncError != null) {
                        Surface(
                            color = AmberContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Live FX API unreachable — running on cached ECB anchors.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AmberOnContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { viewModel.syncLiveRates() }) {
                                    Text("Retry", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    when (val acc = accountState) {
                        is UiState.Loading -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = EmeraldBull)
                            }
                        }
                        is UiState.Error -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Cloud Ledger Error: ${acc.message}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = CrimsonBear
                                )
                            }
                        }
                        is UiState.Success -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .widthIn(max = 880.dp)
                                    .align(Alignment.CenterHorizontally)
                            ) {
                                NavHost(
                                    navController = navController,
                                    startDestination = ROUTE_DASHBOARD
                                ) {
                                    composable(ROUTE_DASHBOARD) {
                                        DashboardScreen(
                                            snapshot = snapshot,
                                            tradeHistoryState = tradeHistoryState,
                                            historySort = historySort,
                                            isRefreshingHistory = isRefreshingHistory,
                                            isBusy = isBusy,
                                            onSelectPairForChart = { pair ->
                                                viewModel.selectPair(pair)
                                                navController.navigate(ROUTE_TERMINAL) {
                                                    popUpTo(navController.graph.findStartDestination().id) {
                                                        saveState = true
                                                    }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            },
                                            onToggleBotArmed = viewModel::toggleBotArmed,
                                            onResetCircuitBreaker = viewModel::resetCircuitBreaker,
                                            onSyncLiveRates = viewModel::syncLiveRates,
                                            onViewAllPositions = {
                                                navController.navigate(ROUTE_POSITIONS) {
                                                    popUpTo(navController.graph.findStartDestination().id) {
                                                        saveState = true
                                                    }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            },
                                            onClosePosition = viewModel::closePositionManually,
                                            onSelectHistorySort = viewModel::setHistorySort,
                                            onRefreshHistory = viewModel::refreshTradeHistory
                                        )
                                    }
                                    composable(ROUTE_TERMINAL) {
                                        TerminalScreen(
                                            snapshot = snapshot,
                                            isBusy = isBusy,
                                            enabledOverlays = enabledOverlays,
                                            selectedOscillator = selectedOscillator,
                                            activeMarkingTool = activeMarkingTool,
                                            drawings = drawingsByPair[snapshot.selectedPair].orEmpty(),
                                            onSelectPair = viewModel::selectPair,
                                            onToggleBotArmed = viewModel::toggleBotArmed,
                                            onExecuteSetup = viewModel::executeManualSetup,
                                            onResetCircuitBreaker = viewModel::resetCircuitBreaker,
                                            onSyncLiveRates = viewModel::syncLiveRates,
                                            onToggleOverlay = viewModel::toggleOverlay,
                                            onSetAllOverlays = viewModel::setAllOverlays,
                                            onSelectOscillator = viewModel::selectOscillator,
                                            onSelectMarkingTool = viewModel::selectMarkingTool,
                                            onAddDrawing = { drawing ->
                                                viewModel.addDrawing(snapshot.selectedPair, drawing)
                                            },
                                            onUndoDrawing = { viewModel.undoDrawing(snapshot.selectedPair) },
                                            onClearDrawings = { viewModel.clearDrawings(snapshot.selectedPair) }
                                        )
                                    }
                                    composable(ROUTE_POSITIONS) {
                                        PositionsScreen(
                                            snapshot = snapshot,
                                            isBusy = isBusy,
                                            onClosePosition = viewModel::closePositionManually
                                        )
                                    }
                                    composable(ROUTE_LEDGER) {
                                        LedgerScreen(snapshot = snapshot)
                                    }
                                    composable(ROUTE_RISK) {
                                        RiskSettingsScreen(
                                            snapshot = snapshot,
                                            userEmail = userEmail,
                                            isBusy = isBusy,
                                            onSaveConfig = viewModel::updateRiskConfig,
                                            onResetAccount = viewModel::resetPaperAccount,
                                            onSignOut = {
                                                signOutUser(
                                                    credentialManager = credentialManager,
                                                    scope = scope
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavItemIcon(
    item: NavDestinationItem,
    selected: Boolean,
    openCount: Int
) {
    val icon = if (selected) item.selectedIcon else item.unselectedIcon
    if (item.route == ROUTE_POSITIONS && openCount > 0) {
        BadgedBox(
            badge = {
                Badge(
                    containerColor = EmeraldBull,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Text(openCount.toString())
                }
            }
        ) {
            Icon(imageVector = icon, contentDescription = stringResource(item.labelRes))
        }
    } else {
        Icon(imageVector = icon, contentDescription = stringResource(item.labelRes))
    }
}
