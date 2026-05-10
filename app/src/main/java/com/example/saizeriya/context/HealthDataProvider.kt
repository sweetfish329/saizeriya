package com.example.saizeriya.context

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.example.saizeriya.data.model.HealthData
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Health Connect APIから健康データを取得するProvider。
 *
 * 使用前にパーミッション確認が必要:
 * - android.permission.health.READ_STEPS
 * - android.permission.health.READ_TOTAL_CALORIES_BURNED
 * - android.permission.health.READ_SLEEP
 */
open class HealthDataProvider(private val context: Context) {

    private val healthConnectClient: HealthConnectClient by lazy {
        HealthConnectClient.getOrCreate(context)
    }

    /**
     * 直近24時間の健康データを収集する。
     *
     * @return HealthData（歩数・消費カロリー・睡眠時間）
     * @throws Exception パーミッション未付与またはHealth Connect未インストール時
     */
    open suspend fun collect(): HealthData {
        val now = Instant.now()
        val yesterday = now.minus(24, ChronoUnit.HOURS)
        val timeRange = TimeRangeFilter.between(yesterday, now)

        // 歩数取得
        val steps = readSteps(timeRange)

        // 消費カロリー取得
        val calories = readCalories(timeRange)

        // 睡眠時間取得
        val sleepMinutes = readSleep(timeRange)

        return HealthData(
            stepsLast24h = steps,
            caloriesBurnedLast24h = calories,
            sleepDurationMinutes = sleepMinutes
        )
    }

    /**
     * 直近24時間の合計歩数を取得する。
     */
    private suspend fun readSteps(timeRange: TimeRangeFilter): Int {
        val request = ReadRecordsRequest(
            recordType = StepsRecord::class,
            timeRangeFilter = timeRange
        )
        val response = healthConnectClient.readRecords(request)
        return response.records.sumOf { it.count.toInt() }
    }

    /**
     * 直近24時間の消費カロリーを取得する。
     */
    private suspend fun readCalories(timeRange: TimeRangeFilter): Double {
        val request = ReadRecordsRequest(
            recordType = TotalCaloriesBurnedRecord::class,
            timeRangeFilter = timeRange
        )
        val response = healthConnectClient.readRecords(request)
        return response.records.sumOf {
            it.energy.inKilocalories
        }
    }

    /**
     * 直近24時間の睡眠時間（分）を取得する。
     */
    private suspend fun readSleep(timeRange: TimeRangeFilter): Int {
        val request = ReadRecordsRequest(
            recordType = SleepSessionRecord::class,
            timeRangeFilter = timeRange
        )
        val response = healthConnectClient.readRecords(request)
        return response.records.sumOf { record ->
            ChronoUnit.MINUTES.between(record.startTime, record.endTime).toInt()
        }
    }

    companion object {
        /** Health Connect が利用可能か確認する */
        fun isAvailable(context: Context): Boolean {
            val status = HealthConnectClient.getSdkStatus(context)
            return status == HealthConnectClient.SDK_AVAILABLE
        }
    }
}
