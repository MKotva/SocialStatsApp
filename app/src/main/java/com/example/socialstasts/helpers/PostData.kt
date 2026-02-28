package com.example.socialstasts.helpers

import com.example.socialstasts.persistance.AccountEntity
import com.example.socialstasts.persistance.PostDailyStatsEntity

data class PostDraft(
    val accountName: String,
    val postId: String,
    val mediaType: String,
    val mediaUri: String,
    val title: String,
    val createdAtEpochDay: Long,
    val createdAtMillis: Long,
    val totalViews: Int,
    val totalNewViewers: Int,
    )

data class PostTotalsUpdate(
    val postId: String,
    val totalViews: Int,
    val totalNewViewers: Int
    )

data class UpdatePack(
    val accountsToInsert: List<AccountEntity> = emptyList(),
    val postDraftsToInsert: List<PostDraft> = emptyList(),
    val dailyStatsToUpsert: List<PostDailyStatsEntity> = emptyList(),
    val postTotalsUpdates: List<PostTotalsUpdate> = emptyList()
    )
