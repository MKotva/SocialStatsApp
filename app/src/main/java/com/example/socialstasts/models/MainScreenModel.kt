package com.example.socialstasts.models

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.socialstasts.AppContainer
import com.example.socialstasts.mock.MediaSeeder
import com.example.socialstasts.persistance.StatsRepository

class MainViewModel(
    private val repo: StatsRepository,
    private val mediaSeeder: MediaSeeder,
    private val context: Context
) : ViewModel() {
    val summaries = repo.observeAccountSummariesLast7Days()
    suspend fun runMockUpdate() {
        repo.runMockUpdate(
            imgUris = mediaSeeder.listImageUris(context),
            vidUris = mediaSeeder.listVideoUris(context)
        )
    }
}

class MainViewModelFactory(
    private val appContainer: AppContainer
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return MainViewModel(
            repo = appContainer.repo,
            mediaSeeder = appContainer.mediaSeeder,
            context = appContainer.context
        ) as T
    }
}