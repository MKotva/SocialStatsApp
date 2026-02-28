package com.example.socialstasts.persistance

import androidx.room.*

@Entity(
    tableName = "accounts",
    indices = [Index(value = ["name"], unique = true)]
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

@Entity(
    tableName = "posts",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("accountId"), Index(value = ["postId"], unique = true)]
)
data class PostEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val postId: String,
    val accountId: Long,
    val mediaType: String,
    val mediaUri: String,
    val title: String,
    val description: String,
    val createdAtEpochDay: Long,
    val createdAtMillis: Long,
    val totalViews: Int,
    val totalNewViewers: Int
)

@Entity(
    tableName = "post_daily_stats",
    primaryKeys = ["postId", "epochDay"],
    indices = [Index("postId"), Index("epochDay")]
)
data class PostDailyStatsEntity (
    val postId: String,
    val epochDay: Long,
    val views: Int,
    val newViewers: Int
)