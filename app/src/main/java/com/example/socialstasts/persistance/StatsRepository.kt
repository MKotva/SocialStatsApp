package com.example.socialstasts.persistance

import androidx.room.withTransaction
import com.example.socialstasts.helpers.DayViewsRow
import com.example.socialstasts.helpers.PickedMedia
import com.example.socialstasts.helpers.UpdatePack
import com.example.socialstasts.mock.MockData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate

class StatsRepository(private val db: AppDb, private val dao: StatsDao) {
    suspend fun applyUpdatePack(pack: UpdatePack) {
        db.withTransaction {
            if (pack.accountsToInsert.isNotEmpty()) {
                dao.insertAccounts(pack.accountsToInsert)
            }

            val nameToId = dao.getAllAccounts().associate { it.name to it.id }
            if (pack.postDraftsToInsert.isNotEmpty()) {
                dao.insertPosts(MockData.createPost(pack.postDraftsToInsert, nameToId))
            }

            if (pack.dailyStatsToUpsert.isNotEmpty()) {
                dao.upsertDailyStats(pack.dailyStatsToUpsert) //daily stats
            }

            pack.postTotalsUpdates.forEach { u ->
                dao.updatePostTotals(u.postId, u.totalViews, u.totalNewViewers) //cached totals
            }
        }
    }

    suspend fun createPost(
        accName: String,
        title: String,
        description: String,
        mediaType: String,
        mediaUri: String,
        today: LocalDate = LocalDate.now()
    ) {
        db.withTransaction {
            val atMillis = System.currentTimeMillis()
            val postId = MockData.newPostId(accountName = accName, dayE = today.toEpochDay()) + "_" + atMillis
            dao.upsertPost(
                PostEntity(
                    id = 0,
                    postId = postId,
                    accountId = ensureAccount(accName),
                    mediaType = mediaType,
                    mediaUri = mediaUri,
                    title = title,
                    description = description,
                    createdAtEpochDay = today.toEpochDay(),
                    createdAtMillis = atMillis,
                    totalViews = 0,
                    totalNewViewers = 0
                )
            )

            dao.upsertDailyStat(
                PostDailyStatsEntity(
                    postId = postId,
                    epochDay = today.toEpochDay(),
                    views = 0,
                    newViewers = 0
                )
            )
        }
    }

    /**
     * Ensures an account with the given exists in the database and returns its row id
     */
    private suspend fun ensureAccount(name: String): Long {
        val existing = dao.getAccountByName(name)
        if (existing != null) return existing.id

        val id = dao.insertAccount(AccountEntity(name = name))
        if (id != -1L) return id

        return dao.getAccountByName(name)!!.id
    }

    /**
     * Builds a mock update pack based on current DB state and seeded media
     */
    suspend fun runMockUpdate(imgUris: List<String>, vidUris: List<String>) {
        val pack = MockData.buildUpdate(
            existingAccounts = dao.getAllAccounts(),
            existingPosts = dao.getAllPosts(),
            today = LocalDate.now(),
            imageUris = imgUris,
            videoUris = vidUris
        )
        applyUpdatePack(pack)
    }

    suspend fun getAllAccounts(): List<AccountEntity> = dao.getAllAccounts()

    fun observeAccountSummariesLast7Days() = dao.observeAccountSummaries(
        fromDay7 = LocalDate.now().minusDays(6).toEpochDay(),
        toDay = LocalDate.now().toEpochDay()
    )

    fun observePostsForAccountName(accName: String): Flow<List<PostEntity>> {
        return flowOf(accName).flatMapLatest { name ->
            val accountId = dao.getAccountByName(name)?.id
            if (accountId == null) {
                flowOf(emptyList())
            } else {
                dao.observePostsForAccount(accountId)
            }
        }
    }

    fun observeAccountDailyViewsByName(accName: String, fromDay: Long, toDay: Long): Flow<List<DayViewsRow>> {
        return flowOf(Triple(accName, fromDay, toDay)).flatMapLatest { (name, start, end) ->
            val accountId = dao.getAccountByName(name)?.id
            if (accountId == null) {
                flowOf(emptyList())
            } else {
                dao.observeAccountDailyViews(accountId, start, end)
            }
        }
    }

    suspend fun createPostForTargets(
        targets: List<String>,
        title: String,
        description: String,
        picked: PickedMedia
    ) {
        targets.forEach { accountName ->
            createPost(
                accName = accountName,
                title = title,
                description = description,
                mediaType = picked.mediaType,
                mediaUri = picked.mediaUri,
                today = LocalDate.now()
            )
        }
    }
}