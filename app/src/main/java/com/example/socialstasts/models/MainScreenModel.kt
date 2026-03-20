package com.example.socialstasts.models

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.socialstasts.mock.MediaSeeder
import com.example.socialstasts.persistance.AppDb
import com.example.socialstasts.persistance.StatsRepository

class MainViewModel(
    private val repo: StatsRepository,
    private val context: Context
) : ViewModel() {
    val summaries = repo.observeAccountSummariesLast7Days()

    suspend fun runMockUpdate() {
        repo.runMockUpdate(
            imgUris = MediaSeeder.listImageUris(context),
            vidUris = MediaSeeder.listVideoUris(context)
        )
    }
}

object MainViewModelFactory : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        modelClass: Class<T>,
        extras: CreationExtras
    ): T {
        val app = checkNotNull(
            extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
        )

        val db = AppDb.get(app.applicationContext)
        val repo = StatsRepository(db, db.statsDao())

        return MainViewModel(
            repo = repo,
            context = app.applicationContext
        ) as T
    }
}