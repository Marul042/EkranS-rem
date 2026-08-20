package com.marul042.ekransrem.blocking

import androidx.room.Entity

/** User-defined blocking constraint. Rules are inactive unless enabled explicitly. */
@Entity(tableName = "block_rules")
data class BlockRule(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val targetPackageName: String? = null,
    val dailyLimitMs: Long? = null,
    val keyword: String? = null,
    val enabled: Boolean = false
)

object ActiveBlockRules {
    val defaultTrackedPackages = setOf(
        "com.google.android.youtube",
        "com.instagram.android",
        "com.facebook.katana",
        "com.zhiliaoapp.musically"
    )

    @Volatile
    var rules: List<BlockRule> = emptyList()

    @Volatile
    var trackedPackages: Set<String> = emptySet()
}
