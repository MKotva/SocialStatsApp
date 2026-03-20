package com.example.socialstasts.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.socialstasts.helpers.DayViewsRow
import com.example.socialstasts.persistance.AppDb
import com.example.socialstasts.persistance.StatsRepository
import kotlinx.coroutines.flow.Flow

class AccountViewModel(private val repo: StatsRepository, accName: String) : ViewModel() {
    val posts = repo.observePostsForAccountName(accName)
    fun observeDailyViews(accName: String, fromDay: Long, toDay: Long): Flow<List<DayViewsRow>> {
        return repo.observeAccountDailyViewsByName(
            accName = accName,
            fromDay = fromDay,
            toDay = toDay
        )
    }
}

class AccountViewModelFactory(private val accName: String) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        val context = checkNotNull(
            extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
        ).applicationContext

        val db = AppDb.get(context)
        val repo = StatsRepository(db, db.statsDao())

        return AccountViewModel(repo = repo, accName = accName) as T
    }
}