package com.example.socialstasts.composables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.socialstasts.models.MainViewModel
import com.example.socialstasts.helpers.AccountSummaryRow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainRoute(vm: MainViewModel, onNewPostClick: () -> Unit, onAccountClick: (String) -> Unit) {
    val summaries by vm.summaries.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SocialStasts") },
                actions = {
                    TextButton(
                        onClick = {
                            scope.launch { vm.runMockUpdate() }
                        }
                    ) { Text("Update") }
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