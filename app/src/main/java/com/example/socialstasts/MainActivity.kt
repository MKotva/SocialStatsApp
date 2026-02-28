package com.example.socialstasts

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.socialstasts.helpers.AccountSummaryRow
import com.example.socialstasts.mock.MediaSeeder
import com.example.socialstasts.mock.MockData
import com.example.socialstasts.persistance.AppDb
import com.example.socialstasts.persistance.StatsDao
import com.example.socialstasts.persistance.StatsRepository
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private lateinit var db: AppDb
    private lateinit var dao: StatsDao
    private lateinit var repo: StatsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Seed local media files used by mock post generation (images/videos for previews and posts)
        MediaSeeder.setDefaultMedia(this)
        getDatabase()

        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    MainRoute(
                        dao = dao,

                        // Update simulates a sync/import of data (in full version download from our server) //TODO:Rework
                        onUpdateClick = {
                            lifecycleScope.launch {
                                runMockUpdate(
                                    imgUris = MediaSeeder.listImageUris(this@MainActivity),
                                    vidUris = MediaSeeder.listVideoUris(this@MainActivity)
                                )
                            }
                        },

                        // Navigation actions from main screen
                        onNewPostClick = ::openCreatePostScreen,
                        onAccountClick = ::openAccountScreen
                    )
                }
            }
        }
    }

    /**
     * Gets the Room database and repository
     */
    private fun getDatabase() {
        db = AppDb.get(this)
        dao = db.statsDao()
        repo = StatsRepository(db, dao)
    }

    /**
     * Builds a mock update pack based on current DB state and seeded media
     */
    private suspend fun runMockUpdate(imgUris: List<String>, vidUris: List<String>) {
        val pack = MockData.buildUpdate(
            existingAccounts = dao.getAllAccounts(),
            existingPosts = dao.getAllPosts(),
            today = LocalDate.now(),
            imageUris = imgUris,
            videoUris = vidUris
        )
        repo.applyUpdatePack(pack)
    }

    private fun openCreatePostScreen() {
        startActivity(Intent(this, CreatePostActivity::class.java))
    }

    private fun openAccountScreen(accountName: String) {
        startActivity(
            Intent(this, AccountViewActivity::class.java).apply {
                putExtra(AccountViewActivity.ACC_NAME, accountName)
            }
        )
    }
}


////////Composables////////
///////////////////////////

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainRoute(
    dao: StatsDao,
    onUpdateClick: () -> Unit,
    onNewPostClick: () -> Unit,
    onAccountClick: (String) -> Unit
) {
    val today = remember { LocalDate.now() }
    val fromDay7 = remember(today) { today.minusDays(6).toEpochDay() }

    // Observe account summaries for the last 7 days + totals
    val summariesFlow = remember(fromDay7, today.toEpochDay() ) {
        dao.observeAccountSummaries(fromDay7 = fromDay7, toDay = today.toEpochDay() )
    }
    val summaries by summariesFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SocialStasts") },
                actions = {
                    TextButton(onClick = onUpdateClick) { Text("Update") }
                }
            )
        },
        //Button setting up create post to all accounts(switchable to single account in main activity)
        floatingActionButton = {
            FloatingActionButton(onClick = onNewPostClick) {
                Icon(Icons.Filled.Add, contentDescription = "New Post")
            }
        },
        floatingActionButtonPosition = FabPosition.End
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            //Show empty database placeholder or account cards
            if (summaries.isEmpty()) {
                EmptyDatabase()
            } else {
                AccountsScroller(
                    summaries = summaries,
                    onAccountClick = onAccountClick
                )
            }
        }
    }
}

// If the Room database is empty, this is default placeholder suggestion
@Composable
private fun EmptyDatabase() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("No accounts yet. Tap Update.")
    }
}

// Scrollable list of account summary cards
@Composable
private fun AccountsScroller(summaries: List<AccountSummaryRow>, onAccountClick: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(summaries, key = { it.accountId }) { row ->
            AccountCard(
                name = row.name,
                viewsLast7 = row.viewsLast7,
                totalViews = row.totalViews,
                postsLast7 = row.postsLast7,
                totalPosts = row.totalPosts,
                onClick = { onAccountClick(row.name) }
            )
        }
    }
}

// Clickable card summarizing one account's post/view metrics in scroll panel
@Composable
private fun AccountCard(
    name: String,
    viewsLast7: Int,
    totalViews: Int,
    postsLast7: Int,
    totalPosts: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            fun Int.format(): String = "%,d".format(this)
            MetricRow(
                left = "Views (7d): ${viewsLast7.format()}",
                right = "Total: ${totalViews.format()}"
            )
            MetricRow(
                left = "Posts (7d): ${postsLast7.format()}",
                right = "Total: ${totalPosts.format()}"
            )
        }
    }
}

// Reusable two-column metric row used inside summary cards
@Composable
private fun MetricRow(left: String, right: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(left)
        Text(right, fontWeight = FontWeight.Medium)
    }
}