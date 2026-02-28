package com.example.socialstasts.persistance

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.socialstasts.helpers.AccountSummaryRow
import com.example.socialstasts.helpers.DayViewsRow
import kotlinx.coroutines.flow.Flow

@Dao
interface StatsDao {

    // Inserts one account if name is not already present (seemed reasonable)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAccount(account: AccountEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAccounts(accounts: List<AccountEntity>): List<Long>


    // Inserts or replaces a single post
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPost(post: PostEntity): Long

    // Inserts multiple posts, ignoring duplicates
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPosts(posts: List<PostEntity>)



    // Inserts or replaces one daily stat row
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDailyStat(row: PostDailyStatsEntity)

    // Inserts or replaces multiple daily stat rows
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDailyStats(rows: List<PostDailyStatsEntity>)



    // Updates total counters on a post identified by postId
    @Query("""
        UPDATE posts
        SET totalViews = :totalViews,
            totalNewViewers = :totalNewViewers
        WHERE postId = :postId
    """)
    suspend fun updatePostTotals(postId: String, totalViews: Int, totalNewViewers: Int)

    // Returns account by exact name, or null if missing
    @Query("SELECT * FROM accounts WHERE name = :name LIMIT 1")
    suspend fun getAccountByName(name: String): AccountEntity?

    // Get all accounts ordered by name
    @Query("SELECT * FROM accounts ORDER BY name")
    suspend fun getAllAccounts(): List<AccountEntity>

    // Gets all posts ordered by postId
    @Query("SELECT * FROM posts ORDER BY postId")
    suspend fun getAllPosts(): List<PostEntity>

    // Streams posts for one account, newest first by created day
    @Query("""
        SELECT * FROM posts
        WHERE accountId = :accountId
        ORDER BY createdAtEpochDay DESC, postId DESC
    """)
    fun observePostsForAccount(accountId: Long): Flow<List<PostEntity>>

    // Streams summed daily views for one account within a day range
    @Query("""
        SELECT d.epochDay AS epochDay, COALESCE(SUM(d.views), 0) AS views
        FROM posts p
        JOIN post_daily_stats d ON d.postId = p.postId
        WHERE p.accountId = :accountId
          AND d.epochDay BETWEEN :fromDay AND :toDay
        GROUP BY d.epochDay
        ORDER BY d.epochDay
    """)
    fun observeAccountDailyViews(accountId: Long, fromDay: Long, toDay: Long): Flow<List<DayViewsRow>>

    // Streams account cards summary: total views/posts + 7-day views/posts window metrics
    @Query("""
        SELECT 
          a.id AS accountId,
          a.name AS name,
          COALESCE(SUM(p.totalViews), 0) AS totalViews,
          COALESCE(COUNT(p.id), 0) AS totalPosts,
          COALESCE((
            SELECT SUM(d.views)
            FROM post_daily_stats d
            JOIN posts p2 ON p2.postId = d.postId
            WHERE p2.accountId = a.id
              AND d.epochDay BETWEEN :fromDay7 AND :toDay
          ), 0) AS viewsLast7,
          COALESCE((
            SELECT COUNT(*)
            FROM posts p3
            WHERE p3.accountId = a.id
              AND p3.createdAtEpochDay BETWEEN :fromDay7 AND :toDay
          ), 0) AS postsLast7
        FROM accounts a
        LEFT JOIN posts p ON p.accountId = a.id
        GROUP BY a.id, a.name
        ORDER BY totalViews DESC
    """)
    fun observeAccountSummaries(fromDay7: Long, toDay: Long): Flow<List<AccountSummaryRow>>
}