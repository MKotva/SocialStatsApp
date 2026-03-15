package com.example.socialstasts

import android.content.Context
import com.example.socialstasts.mock.MediaSeeder
import com.example.socialstasts.persistance.AppDb
import com.example.socialstasts.persistance.StatsRepository

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val db = AppDb.get(appContext)
    val repo = StatsRepository(db, db.statsDao())
    val mediaSeeder = MediaSeeder

    val context = appContext
}