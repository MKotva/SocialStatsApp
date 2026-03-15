package com.example.socialstasts.helpers

import com.example.socialstasts.components.Bucket
import java.time.LocalDate
import kotlin.math.min

/**
Builds buckets for the Bar chart as a sum of views per selected range
 */
fun buildBuckets(
    today: LocalDate,
    rangeDays: Int,
    bucketCount: Int,
    bucketDays: Int,
    daily: Map<Long, Int>
): Array<Bucket> {
    val tempBuckets = Array(bucketCount) { Bucket(0f, 0) }

    for (dayOffset in 0 until rangeDays) {
        val views = daily[today.minusDays((rangeDays - 1 - dayOffset).toLong()).toEpochDay()] ?: 0
        tempBuckets[min(bucketCount - 1, dayOffset / bucketDays)].observe(views.toFloat())
    }

    return Array(bucketCount) { i ->
        val b = tempBuckets[i]
        if (b.count == 0) Bucket(0f, 0) else Bucket(sum = b.sum, count = 1)
    }
}

fun Int.formatGrouped(): String = "%,d".format(this)
fun fileNameFromUri(uriString: String): String = uriString.substringAfterLast('/')