package com.openjarvis.automation

import android.content.Context
import androidx.room.*
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class AutomationScheduleConverter {

    @TypeConverter
    fun fromSchedule(
        schedule: AutomationManager.AutomationSchedule
    ): String {
        return when (schedule) {
            is AutomationManager.AutomationSchedule.Daily ->
                "daily:${schedule.hour}:${schedule.minute}"

            is AutomationManager.AutomationSchedule.Weekly ->
                "weekly:${schedule.dayOfWeek}:${schedule.hour}:${schedule.minute}"

            is AutomationManager.AutomationSchedule.Interval ->
                "interval:${schedule.intervalMs}"

            is AutomationManager.AutomationSchedule.Once ->
                "once:${schedule.atMs}"
        }
    }

    @TypeConverter
    fun toSchedule(value: String): AutomationManager.AutomationSchedule {
        val parts = value.split(":")

        return when (parts.firstOrNull()) {
            "daily" -> AutomationManager.AutomationSchedule.Daily(
                hour = parts[1].toInt(),
                minute = parts[2].toInt()
            )

            "weekly" -> AutomationManager.AutomationSchedule.Weekly(
                dayOfWeek = parts[1].toInt(),
                hour = parts[2].toInt(),
                minute = parts[3].toInt()
            )

            "interval" -> AutomationManager.AutomationSchedule.Interval(
                intervalMs = parts[1].toLong()
            )

            "once" -> AutomationManager.AutomationSchedule.Once(
                atMs = parts[1].toLong()
            )

            else -> throw IllegalArgumentException(
                "Unknown automation schedule: $value"
            )
        }
    }
}

@Dao
interface AutomationDao {

    @Query("SELECT * FROM automations ORDER BY name")
    suspend fun getAll(): List<AutomationManager.Automation>

    @Query("SELECT * FROM automations WHERE id = :id")
    suspend fun getById(id: String): AutomationManager.Automation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(automation: AutomationManager.Automation)

    @Update
    suspend fun update(automation: AutomationManager.Automation)

    @Query("DELETE FROM automations WHERE id = :id")
    suspend fun delete(id: String)
}

@Database(
    entities = [AutomationManager.Automation::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(AutomationScheduleConverter::class)
abstract class AutomationDB : RoomDatabase() {

    abstract fun automationDao(): AutomationDao

    companion object {
        @Volatile
        private var INSTANCE: AutomationDB? = null

        fun getInstance(context: Context): AutomationDB {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AutomationDB::class.java,
                    "automations.db"
                ).build().also {
                    INSTANCE = it
                }
            }
        }
    }
}

class AutomationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getString("automation_id")
            ?: return Result.failure()

        return try {
            val db = AutomationDB.getInstance(applicationContext)
            val dao = db.automationDao()

            val automation = dao.getById(id)
                ?: return Result.failure()

            kotlinx.coroutines.delay(2000)

            val updated = automation.copy(
                lastRun = System.currentTimeMillis(),
                lastResult = "success",
                runCount = automation.runCount + 1
            )

            dao.update(updated)

            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}
