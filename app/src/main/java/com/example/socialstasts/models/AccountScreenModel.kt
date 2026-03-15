package com.example.socialstasts.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.socialstasts.persistance.StatsRepository

class AccountViewModel(repo: StatsRepository, accName: String) : ViewModel() {
    val posts = repo.observePostsForAccountName(accName)
}

class AccountViewModelFactory(
    private val repo: StatsRepository,
    private val accName: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AccountViewModel(
            repo = repo,
            accName = accName
        ) as T
    }
}