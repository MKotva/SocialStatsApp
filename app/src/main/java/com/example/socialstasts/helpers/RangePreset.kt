package com.example.socialstasts.helpers

import java.time.LocalDate

/**
 * Chart range presets
 */
enum class RangePreset(
    val label: String,
    val days: Long, //total rendered day span
    val buckets: Int, //number of chart bars
    val bucketDays: Int //how many days are aggregated into one bar
) {
    WEEK("Week", 14, 14, 1),
    MONTH("Month", 28, 4, 7),
    SIX_MONTHS("6 months", 180, 6, 30),
    YEAR("Year", 360, 12, 30);

    /**
    * Moves window end backward by one period of the current preset
    */
    fun shiftBack(from: LocalDate): LocalDate = when (this) {
        WEEK -> from.minusDays(7)
        MONTH -> from.minusMonths(1)
        SIX_MONTHS -> from.minusMonths(6)
        YEAR -> from.minusYears(1)
    }

    /**
     * Moves window end forward by one period of the current preset
     */
    fun shiftForward(from: LocalDate): LocalDate = when (this) {
        WEEK -> from.plusDays(7)
        MONTH -> from.plusMonths(1)
        SIX_MONTHS -> from.plusMonths(6)
        YEAR -> from.plusYears(1)
    }
}