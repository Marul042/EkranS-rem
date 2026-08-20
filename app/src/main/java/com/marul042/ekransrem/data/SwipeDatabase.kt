package com.marul042.ekransrem.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.marul042.ekransrem.blocking.BlockRule

@Entity(tableName = "tracked_packages")
data class TrackedPackage(@androidx.room.PrimaryKey val packageName: String)

@Entity(tableName = "daily_swipes", primaryKeys = ["packageName", "day"])
data class DailySwipe(
    val packageName: String,
    val day: String,
    val count: Int
)

@Dao
interface SwipeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(item: DailySwipe)

    @Query("SELECT COALESCE(SUM(count), 0) FROM daily_swipes WHERE day = :day")
    suspend fun totalForDay(day: String): Int

    @Query("SELECT COALESCE(SUM(count), 0) FROM daily_swipes WHERE packageName = :packageName AND day = :day")
    suspend fun countForPackageOnDay(packageName: String, day: String): Int

    @Query("SELECT count FROM daily_swipes WHERE packageName = :packageName AND day = :day")
    suspend fun countForPackage(packageName: String, day: String): Int?

    @Query("SELECT * FROM block_rules WHERE enabled = 1")
    suspend fun enabledRules(): List<BlockRule>

    @Query("SELECT * FROM block_rules WHERE targetPackageName = :packageName LIMIT 1")
    suspend fun getRuleForPackage(packageName: String): BlockRule?

    @Query("SELECT packageName FROM tracked_packages")
    suspend fun trackedPackages(): List<String>
}

@Database(entities = [DailySwipe::class, BlockRule::class, TrackedPackage::class], version = 2, exportSchema = false)
abstract class SwipeDatabase : RoomDatabase() {
    abstract fun swipeDao(): SwipeDao

    companion object {
        @Volatile private var instance: SwipeDatabase? = null
        fun get(context: Context): SwipeDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context, SwipeDatabase::class.java, "screen_time.db")
                .addMigrations(MIGRATION_1_2)
                .build().also { instance = it }
        }

        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS block_rules (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, targetPackageName TEXT, dailyLimitMs INTEGER, keyword TEXT, enabled INTEGER NOT NULL)")
                database.execSQL("CREATE TABLE IF NOT EXISTS tracked_packages (packageName TEXT NOT NULL PRIMARY KEY)")
            }
        }
    }
}
