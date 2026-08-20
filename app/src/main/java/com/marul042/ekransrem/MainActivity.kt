package com.marul042.ekransrem

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.app.DatePickerDialog
import java.util.Calendar
import java.util.Locale
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Constraints
import androidx.work.NetworkType
import com.marul042.ekransrem.ai.DailySummaryWorker
import java.util.concurrent.TimeUnit
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.marul042.ekransrem.data.AppUsage
import com.marul042.ekransrem.data.AppCategory
import com.marul042.ekransrem.data.HourlyUsage
import com.marul042.ekransrem.data.LimitExceededInfo
import com.marul042.ekransrem.data.MotivationalQuotes
import com.marul042.ekransrem.data.UsageDay
import com.marul042.ekransrem.core.navigation.AppNavigation
import com.marul042.ekransrem.core.localization.AppLanguage
import com.marul042.ekransrem.core.localization.LanguageManager
import com.marul042.ekransrem.ui.UsageUiState
import com.marul042.ekransrem.ui.UsageViewModel
import com.marul042.ekransrem.ui.ShowMetric

private val DashboardOrange = Color(0xFFFF8A3D)
private val DashboardDark = Color(0xFF1E1E1E)

class MainActivity : ComponentActivity() {
    private var screenTimeViewModel: UsageViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "daily_summary",
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<DailySummaryWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
        )
        setContent { ScreenTimeApp() }
    }

    override fun onResume() {
        super.onResume()
        screenTimeViewModel?.refresh()
    }

    @Composable
    private fun ScreenTimeApp() {
        val viewModel: UsageViewModel = viewModel()
        screenTimeViewModel = viewModel
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        MaterialTheme(colorScheme = darkColorScheme(primary = DashboardOrange, background = DashboardDark, surface = DashboardDark)) {
            AppNavigation(
                dashboard = { 
                    DashboardScreen(
                        state, 
                        viewModel::selectDay, 
                        viewModel::selectDate, 
                        viewModel::setGamesOnly, 
                        viewModel::selectApp,
                        viewModel::selectHour,
                        viewModel::setCategory,
                        viewModel::setShowMetric,
                        viewModel::dismissLimitExceeded
                    ) 
                },
                settings = { SettingsTab(this@MainActivity) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardScreen(
    state: UsageUiState,
    onDaySelected: (UsageDay) -> Unit,
    onDateSelected: (Long) -> Unit,
    onGamesOnlyChanged: (Boolean) -> Unit,
    onAppSelected: (AppUsage?) -> Unit,
    onHourSelected: (Int?) -> Unit,
    onCategorySelected: (AppCategory) -> Unit,
    onShowMetricChanged: (ShowMetric) -> Unit,
    onDismissLimitExceeded: () -> Unit
) {
    var topTab by rememberSaveable { mutableIntStateOf(0) }
    var bottomTab by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current
    Scaffold(
        topBar = { 
            TopAppBar(
                title = { Text("Ekran Süresi", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { topTab = 1 }) {
                        Icon(Icons.Default.Settings, contentDescription = "Ayarlar")
                    }
                }
            ) 
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(bottomTab == 0, { bottomTab = 0 }, icon = { Icon(Icons.Default.BarChart, null) }, label = { Text("İstatistikler") })
                NavigationBarItem(bottomTab == 1, { bottomTab = 1 }, icon = { Icon(Icons.Default.Timer, null) }, label = { Text("Sınırlar") })
                NavigationBarItem(bottomTab == 2, { bottomTab = 2 }, icon = { Icon(Icons.Default.Block, null) }, label = { Text("Engelleme") })
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = topTab) {
                Tab(topTab == 0, { topTab = 0 }, text = { Text("İstatistikler") })
                Tab(topTab == 1, { topTab = 1 }, text = { Text("Ayarlar") })
            }
            when {
                topTab == 1 -> SettingsTab(context)
                bottomTab == 0 -> StatisticsTab(state, onDaySelected, onDateSelected, onGamesOnlyChanged, onAppSelected, onHourSelected, onCategorySelected, onShowMetricChanged, context)
                bottomTab == 1 -> PlaceholderTab("Sınırlar", "Uygulama limitleri yakında burada yönetilecek.")
                else -> PlaceholderTab("Engelleme", "Erişilebilirlik servisini Ayarlar sekmesinden etkinleştirin.")
            }
        }
    }
    
    // Show limit exceeded overlay if applicable
    state.limitExceededInfo?.let { limitInfo ->
        val appIcon = try {
            context.packageManager.getApplicationIcon(limitInfo.packageName)
        } catch (e: Exception) {
            context.packageManager.defaultActivityIcon
        }
        
        OverlayLimitDialog(
            limitInfo = limitInfo,
            appIcon = appIcon,
            onDismiss = onDismissLimitExceeded,
            context = context
        )
    }
}

@Composable
private fun StatisticsTab(
    state: UsageUiState, 
    onDaySelected: (UsageDay) -> Unit, 
    onDateSelected: (Long) -> Unit, 
    onGamesOnlyChanged: (Boolean) -> Unit, 
    onAppSelected: (AppUsage?) -> Unit,
    onHourSelected: (Int?) -> Unit,
    onCategorySelected: (AppCategory) -> Unit,
    onShowMetricChanged: (ShowMetric) -> Unit,
    context: Context
) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!state.hasUsageAccess) {
            AccessCard { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        } else {
            // Day and date selection
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(state.day == UsageDay.TODAY, { onDaySelected(UsageDay.TODAY) }, label = { Text("Bugün") })
                FilterChip(state.day == UsageDay.YESTERDAY, { onDaySelected(UsageDay.YESTERDAY) }, label = { Text("Dün") })
                FilterChip(!state.gamesOnly, { onGamesOnlyChanged(false) }, label = { Text("Tümü") })
                FilterChip(state.gamesOnly, { onGamesOnlyChanged(true) }, label = { Text("Oyunlar") })
            }
            Button(onClick = {
                val calendar = Calendar.getInstance()
                DatePickerDialog(
                    context,
                    { _, year, month, dayOfMonth ->
                        onDateSelected(Calendar.getInstance().apply {
                            set(year, month, dayOfMonth, 0, 0, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis)
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
                ).apply { datePicker.maxDate = System.currentTimeMillis() }.show()
            }) { Text("Belirli bir gün seç") }
            
            // Hourly usage chart
            HourlyUsageChart(
                state.hourlyUsage,
                state.selectedHour,
                onHourSelected,
                state.showMetric,
                onShowMetricChanged
            )
            
            // Summary grid
            SummaryGrid(state)
            
            // Category chips
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = state.category == AppCategory.ALL,
                    onClick = { onCategorySelected(AppCategory.ALL) },
                    label = { Text("Tüm Uygulamalar") }
                )
                FilterChip(
                    selected = state.category == AppCategory.GAMES,
                    onClick = { onCategorySelected(AppCategory.GAMES) },
                    label = { Text("Oyunlar") }
                )
                FilterChip(
                    selected = state.category == AppCategory.SOCIAL,
                    onClick = { onCategorySelected(AppCategory.SOCIAL) },
                    label = { Text("Sosyal") }
                )
                FilterChip(
                    selected = state.category == AppCategory.SYSTEM,
                    onClick = { onCategorySelected(AppCategory.SYSTEM) },
                    label = { Text("Sistem") }
                )
            }
            
            if (state.isLoading) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            else if (state.apps.isEmpty()) Text("Bu aralıkta kullanım verisi bulunamadı.")
            else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { 
                items(state.apps, key = AppUsage::packageName) { app -> 
                    EnhancedAppUsageRow(
                        app = app, 
                        totalTime = state.totalTimeMs,
                        onClick = { onAppSelected(app) },
                        showMetric = state.showMetric
                    ) 
                } 
            }
        }
    }
    state.selectedApp?.let { app ->
        AppDetailsDialog(app = app, onDismiss = { onAppSelected(null) })
    }
}

@Composable
private fun SummaryGrid(state: UsageUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryTile("Bugünkü Kullanım", formatDuration(state.totalTimeMs), Modifier.weight(1f))
            SummaryTile("Uygulamalar", state.apps.size.toString(), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryTile("Günlük Kaydırmalar", state.dailySwipes.toString(), Modifier.weight(1f))
            SummaryTile("En Çok Kullanılan", state.apps.firstOrNull()?.label ?: "-", Modifier.weight(1f))
        }
        if (state.dailySummary.isNotBlank()) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF292929))) {
                Column(Modifier.padding(14.dp)) {
                    Text("Günlük AI özeti", color = DashboardOrange, fontWeight = FontWeight.Bold)
                    Text(state.dailySummary, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun SummaryTile(title: String, value: String, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Color(0xFF292929))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = Color.LightGray, style = MaterialTheme.typography.labelMedium)
            Text(value, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SettingsTab(context: Context) {
    val languageManager = LanguageManager(context)
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Ayarlar", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Button(onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Timer, null); Text("Kullanım erişimini yönet") }
        Button(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Block, null); Text("Engelleme erişimini yönet") }
        Text("Dil", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                languageManager.setLanguage(AppLanguage.ENGLISH)
            }) { Text("English") }
            Button(onClick = {
                languageManager.setLanguage(AppLanguage.TURKISH)
            }) { Text("Türkçe") }
        }
    }
}

@Composable
private fun PlaceholderTab(title: String, message: String) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(message, color = Color.LightGray)
    }
}

@Composable
private fun AccessCard(onOpenSettings: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.Settings, contentDescription = null)
            Text("Kullanım erişimi gerekli", style = MaterialTheme.typography.titleLarge)
            Text("Ekran süresini hesaplamak için Android ayarlarından kullanım erişimine izin verin.")
            Button(onClick = onOpenSettings) { Text("Ayarlara git") }
        }
    }
}

@Composable
private fun AppUsageRow(app: AppUsage, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable(onClick = onClick), tonalElevation = 1.dp, shape = MaterialTheme.shapes.medium) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(app.icon.toBitmap().asImageBitmap(), contentDescription = app.label, Modifier.size(48.dp))
            Column(Modifier.padding(start = 12.dp)) {
                Text(app.label, style = MaterialTheme.typography.titleMedium)
                Text(formatDuration(app.totalTimeMs), style = MaterialTheme.typography.bodyMedium, color = Color.LightGray)
            }
        }
    }
}

@Composable
private fun AppDetailsDialog(app: AppUsage, onDismiss: () -> Unit) {
    val totalMinutes = app.totalTimeMs / 60_000
    val averageMinutes = if (app.swipeCount > 0) totalMinutes.toDouble() / app.swipeCount else 0.0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(app.label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Toplam süre: ${formatDuration(app.totalTimeMs)}")
                Text("Shorts/Reels kaydırma: ${app.swipeCount}")
                Text("Ortalama: ${"%.1f".format(Locale.getDefault(), averageMinutes)} dk/kaydırma")
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Kapat") } }
    )
}

private fun formatDuration(milliseconds: Long): String {
    val totalMinutes = milliseconds / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "$hours sa $minutes dk" else "$minutes dk"
}

@Composable
private fun HourlyUsageChart(
    hourlyUsage: Array<HourlyUsage>,
    selectedHour: Int?,
    onHourSelected: (Int?) -> Unit,
    showMetric: ShowMetric,
    onShowMetricChanged: (ShowMetric) -> Unit
) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF292929))) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Header with toggle
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (showMetric == ShowMetric.SCREEN_TIME) "Ekran Süresi" else "Kaydırma Sayısı",
                    fontWeight = FontWeight.Bold,
                    color = DashboardOrange
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                    onShowMetricChanged(if (showMetric == ShowMetric.SCREEN_TIME) ShowMetric.SWIPE_COUNT else ShowMetric.SCREEN_TIME)
                }) {
                    Text(if (showMetric == ShowMetric.SCREEN_TIME) "Saat" else "Sayı", fontSize = 12.sp)
                    Switch(
                        checked = showMetric == ShowMetric.SWIPE_COUNT,
                        onCheckedChange = { checked ->
                            onShowMetricChanged(if (checked) ShowMetric.SWIPE_COUNT else ShowMetric.SCREEN_TIME)
                        },
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
            
            // Hourly bars (0-23)
            val maxScreenTimeMs = hourlyUsage.maxOfOrNull { it.totalTimeMs } ?: 1L
            val maxSwipeCount = hourlyUsage.maxOfOrNull { it.totalSwipeCount } ?: 1
            val maxValue = if (showMetric == ShowMetric.SCREEN_TIME) maxScreenTimeMs else maxSwipeCount.toLong()
            
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                hourlyUsage.forEachIndexed { index, hourData ->
                    val value = if (showMetric == ShowMetric.SCREEN_TIME) hourData.totalTimeMs else hourData.totalSwipeCount.toLong()
                    val progress = if (maxValue > 0) (value.toFloat() / maxValue.toFloat()) else 0f
                    val isSelected = selectedHour == index
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isSelected) Color(0xFF3D4F5C) else Color.Transparent,
                                shape = MaterialTheme.shapes.small
                            )
                            .clickable {
                                onHourSelected(if (isSelected) null else index)
                            }
                            .padding(4.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                hourData.displayHour,
                                fontSize = 12.sp,
                                color = if (isSelected) DashboardOrange else Color.White
                            )
                            LinearProgressIndicator(
                                progress = progress,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(24.dp),
                                color = if (isSelected) DashboardOrange else Color.Gray,
                                trackColor = Color(0xFF3F3F3F)
                            )
                            Text(
                                if (showMetric == ShowMetric.SCREEN_TIME) 
                                    formatDuration(hourData.totalTimeMs) 
                                else 
                                    "${hourData.totalSwipeCount}",
                                fontSize = 12.sp,
                                color = Color.LightGray
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EnhancedAppUsageRow(
    app: AppUsage,
    totalTime: Long,
    onClick: () -> Unit,
    showMetric: ShowMetric
) {
    Surface(Modifier.fillMaxWidth().clickable(onClick = onClick), tonalElevation = 1.dp, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Image(app.icon.toBitmap().asImageBitmap(), contentDescription = app.label, Modifier.size(48.dp))
                Column(Modifier.weight(1f)) {
                    Text(app.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (showMetric == ShowMetric.SCREEN_TIME) 
                            formatDuration(app.totalTimeMs) 
                        else 
                            "${app.swipeCount} kaydırma",
                        style = MaterialTheme.typography.bodyMedium, 
                        color = Color.LightGray
                    )
                }
                Text(
                    if (showMetric == ShowMetric.SCREEN_TIME) {
                        val percentage = if (totalTime > 0) (app.totalTimeMs * 100 / totalTime).toInt() else 0
                        "$percentage%"
                    } else {
                        "${app.swipeCount}"
                    },
                    fontWeight = FontWeight.Bold,
                    color = DashboardOrange
                )
            }
            // Progress bar
            LinearProgressIndicator(
                progress = if (totalTime > 0 && showMetric == ShowMetric.SCREEN_TIME) 
                    (app.totalTimeMs.toFloat() / totalTime.toFloat()).coerceIn(0f, 1f) 
                else 
                    0f,
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = DashboardOrange,
                trackColor = Color(0xFF3F3F3F)
            )
        }
    }
}

@Composable
private fun OverlayLimitDialog(
    limitInfo: LimitExceededInfo,
    appIcon: android.graphics.drawable.Drawable,
    onDismiss: () -> Unit,
    context: Context
) {
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.95f),
        shape = MaterialTheme.shapes.large,
        containerColor = Color(0xFF292929),
        title = {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Image(
                    appIcon.toBitmap().asImageBitmap(),
                    contentDescription = limitInfo.appLabel,
                    modifier = Modifier.size(40.dp)
                )
                Text(
                    limitInfo.appLabel,
                    fontWeight = FontWeight.Bold,
                    color = DashboardOrange,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Usage stat
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF3F3F3F))
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Bugünkü Kullanım", color = Color.LightGray, fontSize = 12.sp)
                        Text(
                            formatDuration(limitInfo.usedTimeMs),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = DashboardOrange
                        )
                        Text(
                            "Limit: ${formatDuration(limitInfo.dailyLimitMs)}",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                }
                
                // Tabbed area
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = Color(0xFF1E1E1E),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = selectedTabIndex == 0,
                        onClick = { selectedTabIndex = 0 },
                        text = { Text("Alıntı", fontSize = 12.sp) }
                    )
                    Tab(
                        selected = selectedTabIndex == 1,
                        onClick = { selectedTabIndex = 1 },
                        text = { Text("Grafik", fontSize = 12.sp) }
                    )
                }
                
                // Tab content
                when (selectedTabIndex) {
                    0 -> {
                        // Quote tab
                        Card(
                            Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF3F3F3F))
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    MotivationalQuotes.getRandomQuote(),
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                )
                            }
                        }
                    }
                    1 -> {
                        // Chart tab - mini bar chart showing usage
                        Card(
                            Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF3F3F3F))
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Günlük Kullanım", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(40.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    LinearProgressIndicator(
                                        progress = minOf(1f, (limitInfo.usedTimeMs.toFloat() / (limitInfo.dailyLimitMs * 1.5f))),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(24.dp),
                                        color = if (limitInfo.usedTimeMs > limitInfo.dailyLimitMs) Color.Red else DashboardOrange,
                                        trackColor = Color(0xFF2A2A2A)
                                    )
                                    Text(
                                        "${limitInfo.quotaPercentage}%",
                                        fontWeight = FontWeight.Bold,
                                        color = if (limitInfo.usedTimeMs > limitInfo.dailyLimitMs) Color.Red else DashboardOrange
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Warning message
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFD32F2F).copy(alpha = 0.2f)),
                    border = CardDefaults.cardBorder().copy(color = { Color(0xFFD32F2F) }, width = { 1.dp })
                ) {
                    Text(
                        "Bu uygulama için günlük kullanım limiti aşıldı.",
                        modifier = Modifier.padding(12.dp),
                        color = Color(0xFFFF8A8A),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // Trigger GLOBAL_ACTION_HOME
                    val intent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    onDismiss()
                }
            ) {
                Text("Kapat")
            }
        }
    )
}
