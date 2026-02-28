package com.example.socialstasts.helpers

data class AccountSummaryRow(
    val accountId: Long,
    val name: String,
    val totalViews: Int,
    val totalPosts: Int,
    val viewsLast7: Int,
    val postsLast7: Int,
)

data class DayViewsRow(
    val epochDay: Long,
    val views: Int
)