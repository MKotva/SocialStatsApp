package com.example.socialstasts.persistance

import androidx.room.withTransaction
import com.example.socialstasts.helpers.UpdatePack
import com.example.socialstasts.mock.MockData
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
            val atEpochDay = today.toEpochDay()

            val postId = MockData.newPostId(accountName = accName, dayE = atEpochDay) + "_" + atMillis
            dao.upsertPost(
                PostEntity(
                    id = 0,
                    postId = postId,
                    accountId = ensureAccount(accName),
                    mediaType = mediaType,
                    mediaUri = mediaUri,
                    title = title,
                    description = description,
                    createdAtEpochDay = atEpochDay,
                    createdAtMillis = atMillis,
                    totalViews = 0,
                    totalNewViewers = 0
                )
            )

            dao.upsertDailyStat(
                PostDailyStatsEntity(
                    postId = postId,
                    epochDay = atEpochDay,
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
}