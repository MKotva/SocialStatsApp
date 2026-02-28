package com.example.socialstasts.mock

import com.example.socialstasts.helpers.PostDraft
import com.example.socialstasts.helpers.PostTotalsUpdate
import com.example.socialstasts.helpers.UpdatePack
import com.example.socialstasts.persistance.AccountEntity
import com.example.socialstasts.persistance.PostDailyStatsEntity
import com.example.socialstasts.persistance.PostEntity
import java.time.LocalDate
import kotlin.math.abs
import kotlin.random.Random

object MockData {
    // How many past days of history to generate for a brand-new post/account
    const val NEW_ACC_HISTORY: Int = 14
    // Probability that we create a new account
    private const val ACC_PROB: Float = 0.15f
    // Probability that we add extra posts to an existing account
    private const val POST_PROB: Float = 0.5f
    //Possible social platforms for mock accounts
    private val SOCIALS = listOf("Facebook", "Instagram", "TikTok", "LinkedIn", "HackerPost")
    //Possible media types to assign to posts
    private val M_TYPES = listOf("IMAGE", "VIDEO")


    /**
     * Builds a single update pack to apply to the local DB
     */
    fun buildUpdate(
        existingAccounts: List<AccountEntity>,
        existingPosts: List<PostEntity>,
        today: LocalDate = LocalDate.now(),
        imageUris: List<String>,
        videoUris: List<String>,
        nonce: Long = 0L
    ): UpdatePack {
        val todayEpoch = today.toEpochDay()
        val effectiveNonce = if (nonce != 0L) nonce else System.nanoTime()
        val rnd = Random(abs(("update::$todayEpoch::$effectiveNonce").hashCode()))

        // Track existing names/ids so we don't generate duplicates
        val exNames = existingAccounts.mapTo(mutableSetOf()) { it.name }
        val exPostIds = existingPosts.mapTo(mutableSetOf()) { it.postId }

        val accInserts = mutableListOf<AccountEntity>()
        val postDrafts = mutableListOf<PostDraft>()
        val dailyStats = mutableListOf<PostDailyStatsEntity>()
        val totalsUpdates = mutableListOf<PostTotalsUpdate>()

        // First ever update for which we create exactly one account with one starter post
        if (existingAccounts.isEmpty()) {
            getPlatforms(exNames, rng = rnd)
                .firstOrNull()
                ?.let { accountName ->
                    createAccount(
                        accName = accountName,
                        todayE = todayEpoch,
                        rnd = rnd,
                        imgUris = imageUris,
                        vidUris = videoUris,
                        exPostIds = exPostIds,
                        accInserts = accInserts,
                        exNames = exNames,
                        postDrafts = postDrafts,
                        dailyStats = dailyStats
                    )
                }
        }

        //Try add one new account with one starter post
        tryGetAccountName(exNames, rnd)?.let { accName ->
            createAccount(
                accName = accName,
                todayE = todayEpoch,
                rnd = rnd,
                imgUris = imageUris,
                vidUris = videoUris,
                exPostIds = exPostIds,
                accInserts = accInserts,
                exNames = exNames,
                postDrafts = postDrafts,
                dailyStats = dailyStats
            )
        }

        //Try add 1..2 new posts to a random existing account
        tryAddPostsToAccount(
            accounts = existingAccounts,
            todayEpoch = todayEpoch,
            rnd = rnd,
            imgUris = imageUris,
            vidUris = videoUris,
            postIDs = exPostIds,
            postDrafts = postDrafts,
            dailyStats = dailyStats
        )

        //Always add today's stat for existing posts and cached totals
        existingPosts.forEach { post ->
            val todayRow = todayStatForExistingPost(post.postId, todayEpoch)
            dailyStats += todayRow
            totalsUpdates += PostTotalsUpdate(
                postId = post.postId,
                totalViews = post.totalViews + todayRow.views,
                totalNewViewers = post.totalNewViewers + todayRow.newViewers
            )
        }

        return UpdatePack(
            accountsToInsert = accInserts,
            postDraftsToInsert = postDrafts,
            dailyStatsToUpsert = dailyStats,
            postTotalsUpdates = totalsUpdates
        )
    }

    /**
     * With probability ACC_PROB, picks a new platform name that isn't already in existingNames.
     * Returns null if probability check fails or if no platforms remain
     */
    private fun tryGetAccountName(existingNames: Set<String>, rng: Random): String? {
        if (rng.nextFloat() > ACC_PROB) return null
        val candidates = SOCIALS.filterNot { it in existingNames }
        return candidates.randomOrNull(rng)
    }

    /**
     * Converts post drafts into PostEntity row by resolving the account name to its DB id
     */
    fun createPost(drafts: List<PostDraft>, accNameToId: Map<String, Long>): List<PostEntity> {
        return drafts.map { d ->
            PostEntity(
                id = 0,
                postId = d.postId,
                accountId = accNameToId[d.accountName] ?: error("Missing accountId for accountName='${d.accountName}'"),
                mediaType = d.mediaType,
                mediaUri = d.mediaUri,
                title = d.title,
                description = "This is a testing description.",
                createdAtEpochDay = d.createdAtEpochDay,
                createdAtMillis = d.createdAtMillis,
                totalViews = d.totalViews,
                totalNewViewers = d.totalNewViewers
            )
        }
    }

    private fun createAccount(
        accName: String,
        todayE: Long,
        rnd: Random,
        imgUris: List<String>,
        vidUris: List<String>,
        exPostIds: MutableSet<String>,
        accInserts: MutableList<AccountEntity>,
        exNames: MutableSet<String>,
        postDrafts: MutableList<PostDraft>,
        dailyStats: MutableList<PostDailyStatsEntity>,
    ) {
        // Insert for the account itself
        accInserts += AccountEntity(name = accName)
        exNames += accName

        // Create one initial post draft for the account
        val draft = makePostDraft(
            accName = accName,
            today = todayE,
            title = "Mocked post: Lorem Ipsum",
            rnd = rnd,
            imgUris = imgUris,
            vidUris = vidUris,
            exPostIds = exPostIds
        )

        appendDraftWithHistory(draft, todayE, postDrafts, dailyStats)
    }

    /**
     * With probability POST_PROB, generates 1 or 2 new posts (drafts), with history, for a random
     * existing account
     */
    private fun tryAddPostsToAccount(
        accounts: List<AccountEntity>,
        todayEpoch: Long,
        rnd: Random,
        imgUris: List<String>,
        vidUris: List<String>,
        postIDs: MutableSet<String>,
        postDrafts: MutableList<PostDraft>,
        dailyStats: MutableList<PostDailyStatsEntity>,
    ) {
        if (accounts.isEmpty()) return
        if (rnd.nextFloat() >= POST_PROB) return

        repeat(rnd.nextInt(1, 3)) { idx ->
            val draft = makePostDraft(
                accName = accounts[rnd.nextInt(accounts.size)].name,
                today = todayEpoch,
                title = "Mocked post: Lorem Ipsum",
                rnd = rnd,
                imgUris = imgUris,
                vidUris = vidUris,
                exPostIds = postIDs
            )
            appendDraftWithHistory(draft, todayEpoch, postDrafts, dailyStats)
        }
    }

    /**
     * Appends the new draft to drafts list and generate history rows for that post to the dailyStats list
     */
    private fun appendDraftWithHistory(
        draft: PostDraft,
        today: Long,
        drafts: MutableList<PostDraft>,
        dailyStats: MutableList<PostDailyStatsEntity>
    ) {
        drafts += draft
        dailyStats += getHistoryForPost(
            postId = draft.postId,
            today = today,
            historyDays = NEW_ACC_HISTORY,
            seed = abs(("hist::${draft.postId}").hashCode())
        )
    }

    /**
     * Picks random platform name (can be updated to multitude)
     */
    private fun getPlatforms(existingNames: Set<String>, rng: Random, count: Int = 1): List<String> {
        val candidates = SOCIALS.filterNot { it in existingNames }.toMutableList()
        candidates.shuffle(rng)
        return candidates.take(count)
    }

    private fun makePostDraft(
        accName: String,
        today: Long,
        title: String,
        rnd: Random,
        imgUris: List<String>,
        vidUris: List<String>,
        exPostIds: MutableSet<String>
    ): PostDraft {
        // Ensure postId doesn't collide with any known post IDs
        val postId = getUUID(
            accountName = accName,
            baseDay = today,
            existingPostIds = exPostIds
        )

        // Pick media type and URI.
        val (finalType, finalUri) = pickMediaForType(
            requestedType = M_TYPES[rnd.nextInt(M_TYPES.size)],
            postId = postId,
            imgUris = imgUris,
            vidUris = vidUris
        )


        // Generate historical stats for the post
        val history = getHistoryForPost(
            postId = postId,
            today = today,
            historyDays = NEW_ACC_HISTORY,
            seed = abs(("hist::${postId}").hashCode())
        )

        // Created-at date is set to the first day of the history range
        val createdAtE = today - (NEW_ACC_HISTORY - 1).toLong()
        return PostDraft(
            accountName = accName,
            postId = postId,
            mediaType = finalType,
            mediaUri = finalUri,
            title = title,
            createdAtEpochDay = createdAtE,
            createdAtMillis = createdAtE *  86_400_000L,
            totalViews = history.sumOf { it.views },
            totalNewViewers = history.sumOf { it.newViewers }
        )
    }

    /**
     * Generates a postId and guarantees uniqueness against existingPostIds
     */
    private fun getUUID(accountName: String, baseDay: Long, existingPostIds: MutableSet<String>): String {
        var b = 1L
        while (true) {
            val candidate = newPostId(accountName, baseDay + b)
            if (existingPostIds.add(candidate)) return candidate
            b++
        }
    }

    /**
    * Chooses a media URI for the requested type
    */
    private fun pickMediaForType(
        requestedType: String,
        postId: String,
        imgUris: List<String>,
        vidUris: List<String>,
    ): Pair<String, String> {
        val rnd = Random(abs(("mediaPick::$postId").hashCode()))

        // Picks one random URI from lis
        fun pick(list: List<String>): String =
            if (list.isEmpty()) "" else list[rnd.nextInt(list.size)]

        // Prefer primary list/type, but fall back to secondary if primary is empty
        fun pickPreferred(primaryType: String, primary: List<String>, fallbackType: String, fallback: List<String>): Pair<String, String> {
            val first = pick(primary)
            if (first.isNotBlank()) return primaryType to first

            val second = pick(fallback)
            if (second.isNotBlank()) return fallbackType to second

            return primaryType to ""
        }

        return when (requestedType.uppercase()) {
            "IMAGE" -> pickPreferred("IMAGE", imgUris, "VIDEO", vidUris)
            "VIDEO" -> pickPreferred("VIDEO", vidUris, "IMAGE", imgUris)
            else -> {
                val img = pick(imgUris)
                if (img.isNotBlank()) "IMAGE" to img
                else {
                    val vid = pick(vidUris)
                    if (vid.isNotBlank()) "VIDEO" to vid else requestedType to ""
                }
            }
        }
    }

    fun getHistoryForPost(postId: String, today: Long, historyDays: Int, seed: Int): List<PostDailyStatsEntity> {
        val rnd = Random(seed)
        return (0 until historyDays).map { offset ->
            val views = if (rnd.nextFloat() < 0.15f) 0 else rnd.nextInt(50, 2500)
            PostDailyStatsEntity(
                postId = postId,
                epochDay = today - offset,
                views = views,
                newViewers = getNewViewers(rnd, views)
            )
        }
    }

    /**
     * Generates today's stats for an existing post
     */
    private fun todayStatForExistingPost(postId: String, todayE: Long): PostDailyStatsEntity {
        val rnd = Random(abs(("today::$postId::$todayE").hashCode()))
        val views = if (rnd.nextFloat() < 0.15f) 0 else rnd.nextInt(50, 3000)

        return PostDailyStatsEntity(
            postId = postId,
            epochDay = todayE,
            views = views,
            newViewers = getNewViewers(rnd, views)
        )
    }

    /**
     * Computes "new viewers" as 10%..35% of views
     */
    private fun getNewViewers(rng: Random, views: Int): Int {
        return (views * (0.10f + rng.nextFloat() * 0.25f)).toInt()
    }

    fun newPostId(accountName: String, dayE: Long): String {
        val safe = accountName.lowercase().replace(" ", "_")
        val tail = Random(abs(("id::$safe::$dayE").hashCode())).nextInt(1000, 9999)
        return "${safe}_${dayE}_$tail"
    }

    private fun <T> List<T>.randomOrNull(rng: Random): T? =
        if (isEmpty()) null else this[rng.nextInt(size)]
}