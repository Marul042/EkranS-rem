package com.marul042.ekransrem.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.marul042.ekransrem.data.AppUsage
import com.marul042.ekransrem.data.UsageDateRange
import com.marul042.ekransrem.data.UsageDay
import com.marul042.ekransrem.data.UsageRepository
import com.marul042.ekransrem.data.SwipeDatabase
import com.marul042.ekransrem.data.AppCategory
import com.marul042.ekransrem.data.HourlyUsage
import com.marul042.ekransrem.data.LimitExceededInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ShowMetric {
    SCREEN_TIME,
    SWIPE_COUNT
}

 data class UsageUiState(
    val hasUsageAccess: Boolean = false,
    val day: UsageDay = UsageDay.TODAY,
    val selectedDateMillis: Long? = null,
    val gamesOnly: Boolean = false,
    val apps: List<AppUsage> = emptyList(),
    val isLoading: Boolean = false,
    val dailySwipes: Int = 0,
    val dailySummary: String = "",
    val selectedApp: AppUsage? = null,
    // Phase 2 additions
    val hourlyUsage: Array<HourlyUsage> = emptyArray(),
    val selectedHour: Int? = null,
    val category: AppCategory = AppCategory.ALL,
    val showMetric: ShowMetric = ShowMetric.SCREEN_TIME,
    // Phase 3 additions
    val limitExceededInfo: LimitExceededInfo? = null
) {
    val totalTimeMs: Long get() = apps.sumOf(AppUsage::totalTimeMs)
}

class UsageViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = UsageRepository(application)
    private val _uiState = MutableStateFlow(UsageUiState())
    val uiState: StateFlow<UsageUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val access = repository.hasUsageAccess()
            _uiState.value = _uiState.value.copy(hasUsageAccess = access, isLoading = access)
            if (access) loadUsage() else _uiState.value = _uiState.value.copy(apps = emptyList(), isLoading = false)
        }
    }

    fun selectDay(day: UsageDay) {
        _uiState.value = _uiState.value.copy(day = day, selectedDateMillis = null, selectedHour = null)
        refresh()
    }

    fun selectDate(dateMillis: Long) {
        _uiState.value = _uiState.value.copy(selectedDateMillis = dateMillis, selectedHour = null)
        refresh()
    }

    fun setGamesOnly(gamesOnly: Boolean) {
        _uiState.value = _uiState.value.copy(gamesOnly = gamesOnly)
        refresh()
    }

    fun selectHour(hour: Int?) {
        _uiState.value = _uiState.value.copy(selectedHour = hour)
        refresh()
    }

    fun setCategory(category: AppCategory) {
        _uiState.value = _uiState.value.copy(category = category)
        refresh()
    }

    fun setShowMetric(metric: ShowMetric) {
        _uiState.value = _uiState.value.copy(showMetric = metric)
    }

    private suspend fun loadUsage() {
        val state = _uiState.value
        val dateRange = state.selectedDateMillis?.let(UsageDateRange::forDate)
            ?: UsageDateRange.forDay(state.day)
        
        // Get hourly usage for chart
        val hourlyUsage = repository.getHourlyUsage(dateRange)
        
        // Get apps for the date range
        var apps = repository.getUsage(dateRange, state.gamesOnly)
        
        // Filter by selected hour if applicable
        if (state.selectedHour != null) {
            // Note: Would need more granular hourly app data for perfect filtering
            // For now, we display all apps but could show visual indicator for hour filter
        }
        
        // Filter by category
        apps = apps.filter { app ->
            try {
                val appInfo = getApplication<Application>().packageManager
                    .getApplicationInfo(app.packageName, 0)
                state.category.matches(appInfo)
            } catch (e: Exception) {
                false
            }
        }
        
        val selectedDay = Calendar.getInstance().apply {
            state.selectedDateMillis?.let { timeInMillis = it }
            if (state.selectedDateMillis == null && state.day == UsageDay.YESTERDAY) add(Calendar.DAY_OF_YEAR, -1)
        }
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(selectedDay.time)
        val dao = SwipeDatabase.get(getApplication()).swipeDao()
        val swipes = dao.totalForDay(day)
        val appsWithSwipes = apps.map { app -> app.copy(swipeCount = dao.countForPackageOnDay(app.packageName, day)) }
        val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val preferences = getApplication<Application>().getSharedPreferences("settings", Application.MODE_PRIVATE)
        val summary = preferences.getString("daily_summary", "")
            .takeIf { preferences.getString("daily_summary_date", "") == todayKey }
            .orEmpty()
        
        // Check for limit exceeded
        val db = SwipeDatabase.get(getApplication())
        val limitExceeded = repository.checkForLimitExceeded(dateRange, db)
        
        _uiState.value = _uiState.value.copy(
            apps = appsWithSwipes, 
            hourlyUsage = hourlyUsage,
            dailySwipes = swipes, 
            dailySummary = summary, 
            limitExceededInfo = limitExceeded,
            isLoading = false
        )
    }

    fun selectApp(app: AppUsage?) {
        _uiState.value = _uiState.value.copy(selectedApp = app)
    }

    fun dismissLimitExceeded() {
        _uiState.value = _uiState.value.copy(limitExceededInfo = null)
    }
}
