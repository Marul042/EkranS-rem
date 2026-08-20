package com.marul042.ekransrem.blocking

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import com.marul042.ekransrem.R
import com.marul042.ekransrem.data.DailySwipe
import com.marul042.ekransrem.data.SwipeDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppBlockingAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())
    private var activePackage: String? = null
    private var sessionStartedAt = 0L
    private var nudgesSent = 0
    private val nudgeSteps = longArrayOf(60_000L, 180_000L, 300_000L)

    override fun onServiceConnected() {
        super.onServiceConnected()
        scope.launch {
            val dao = SwipeDatabase.get(applicationContext).swipeDao()
            ActiveBlockRules.rules = dao.enabledRules()
            ActiveBlockRules.trackedPackages = ActiveBlockRules.defaultTrackedPackages + dao.trackedPackages()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return
        if (packageName != activePackage) {
            activePackage = packageName
            sessionStartedAt = System.currentTimeMillis()
            nudgesSent = 0
        }
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED && ActiveBlockRules.trackedPackages.contains(packageName)) {
            recordSwipe(packageName)
        }

        val rules = ActiveBlockRules.rules.filter { it.enabled && (it.targetPackageName == null || it.targetPackageName == packageName) }
        val keywordRules = rules.filter { !it.keyword.isNullOrBlank() }
        if (keywordRules.isNotEmpty()) {
            val root = rootInActiveWindow ?: return
            if (keywordRules.any { containsKeyword(root, it.keyword.orEmpty()) }) performGlobalAction(GLOBAL_ACTION_BACK)
        }
        maybeNudge(packageName)
    }

    override fun onInterrupt() = Unit

    private fun recordSwipe(packageName: String) {
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        scope.launch {
            val dao = SwipeDatabase.get(applicationContext).swipeDao()
            val count = dao.countForPackage(packageName, day) ?: 0
            dao.save(DailySwipe(packageName, day, count + 1))
        }
    }

    private fun maybeNudge(packageName: String) {
        if (!ActiveBlockRules.trackedPackages.contains(packageName)) return
        val elapsed = System.currentTimeMillis() - sessionStartedAt
        if (nudgesSent < nudgeSteps.size && elapsed >= nudgeSteps[nudgesSent]) {
            sendNudge(nudgesSent + 1)
            nudgesSent++
        }
    }

    private fun sendNudge(minutes: Int) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("screen_time_nudges", getString(R.string.nudge_channel_name), NotificationManager.IMPORTANCE_DEFAULT))
        manager.notify(minutes, NotificationCompat.Builder(this, "screen_time_nudges")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(getString(R.string.nudge_title))
            .setContentText(getString(R.string.nudge_message, minutes))
            .setAutoCancel(true)
            .build())
    }

    private fun containsKeyword(node: AccessibilityNodeInfo, keyword: String): Boolean {
        if (node.text?.toString()?.contains(keyword, true) == true || node.contentDescription?.toString()?.contains(keyword, true) == true) return true
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val found = containsKeyword(child, keyword)
            child.recycle()
            if (found) return true
        }
        return false
    }
}
