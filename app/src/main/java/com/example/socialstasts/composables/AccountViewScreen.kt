package com.example.socialstasts.composables

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.example.socialstasts.models.AccountViewModel
import com.example.socialstasts.models.AccountViewModelFactory
import com.example.socialstasts.components.BarChart
import com.example.socialstasts.components.Group
import com.example.socialstasts.components.Series
import com.example.socialstasts.helpers.RangePreset
import com.example.socialstasts.helpers.buildBuckets
import com.example.socialstasts.helpers.fileNameFromUri
import com.example.socialstasts.helpers.formatGrouped
import com.example.socialstasts.persistance.PostEntity
import com.example.socialstasts.persistance.StatsRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

@Composable
fun AccountViewRoute(
    accName: String,
    repo: StatsRepository,
    onBack: () -> Unit,
    onNewPostClick: (String) -> Unit
) {
    val fac = remember(repo, accName) { AccountViewModelFactory(repo, accName) }
    val vm: AccountViewModel = viewModel(factory = fac)
    val posts by vm.posts.collectAsStateWithLifecycle(initialValue = emptyList())

    AccountDetailScreen(
        accName = accName,
        posts = posts,
        repo = repo,
        onBack = onBack,
        onNewPostClick = onNewPostClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountDetailScreen(
    accName: String,
    posts: List<PostEntity>,
    repo: StatsRepository,
    onBack: () -> Unit,
    onNewPostClick: (String) -> Unit
) {
    val today = remember { LocalDate.now() }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("d.M.yyyy") }

    // Current chart range selection + end date of displayed window
    var range by rememberSaveable { mutableStateOf(RangePreset.WEEK) }
    var windowEndEpochDay by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    val windowEnd = remember(windowEndEpochDay) { LocalDate.ofEpochDay(windowEndEpochDay) }

    // Observe daily view counts for current chart window
    val chartFlow = remember(accName, repo, range, windowEndEpochDay) {
        repo.observeAccountDailyViewsByName(
            accName = accName,
            fromDay = windowEnd.minusDays(range.days - 1).toEpochDay(),
            toDay = windowEnd.toEpochDay()
        )
    }
    val dailyRows by chartFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val dailyMap = remember(dailyRows) { dailyRows.associate { it.epochDay to it.views } }

    // Convert daily rows into chart buckets
    val buckets = remember(range, windowEnd, dailyMap) {
        buildBuckets(
            today = windowEnd,
            rangeDays = range.days.toInt(),
            bucketCount = range.buckets,
            bucketDays = range.bucketDays,
            daily = dailyMap
        )
    }

    // Swipe gesture state used to change chart periods left/right
    var dragX by remember { mutableFloatStateOf(0f) }
    val swipeThresholdPx = 80f

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(accName) },
                actions = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                }
            )
        },

        // FAB opens CreatePostActivity with this account preselected
        floatingActionButton = {
            FloatingActionButton(onClick = { onNewPostClick(accName) }) {
                Icon(Icons.Filled.Add, contentDescription = "New Post")
            }
        },
        floatingActionButtonPosition = FabPosition.End
    ) { padding ->

        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxWidth(),
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(Modifier.height(4.dp))
            }

            // Chart range selector
            item {
                RangeSelector(
                    value = range,
                    onChange = { selected ->
                        range = selected
                        windowEndEpochDay = minOf(today, LocalDate.ofEpochDay(windowEndEpochDay)).toEpochDay()
                    }
                )
            }

            //Date interval displayed above chart
            item {
                Text(
                    text = "${windowEnd.minusDays(range.days - 1).format(dateFormatter)}  →  ${windowEnd.format(dateFormatter)}",
                    style = MaterialTheme.typography.labelLarge
                )
            }

            // Actual chart area
            item {
                //Dragable area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .draggable(
                            orientation = Orientation.Horizontal,
                            state = rememberDraggableState { delta -> dragX += delta },
                            onDragStopped = {
                                if (abs(dragX) >= swipeThresholdPx) {
                                    if (dragX > 0f) {
                                        windowEndEpochDay = range.shiftBack(windowEnd).toEpochDay()
                                    } else if (windowEnd.isBefore(today)) {
                                        windowEndEpochDay = minOf(today, range.shiftForward(windowEnd)).toEpochDay()
                                    }
                                }
                                dragX = 0f
                            }
                        )
                ) {
                    //Using implementation of bar chart from our company (with little tweak)
                    BarChart(
                        groups = arrayOf(
                            Group(
                                series = arrayOf(
                                    Series(
                                        name = AnnotatedString("Views"),
                                        buckets = buckets,
                                        color = MaterialTheme.colorScheme.primary,
                                        unit = "",
                                        fmt = "%.0f"
                                    )
                                )
                            )
                        ),
                        totalHeight = 160.dp,
                        legendStride = maxOf(1, buckets.size / 4)
                    )
                }

                //Hint for swipe TODO:Think about pop-up hint
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Swipe left/right to change period",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                Text(
                    "Posts",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Post cards for the selected account
            items(posts, key = { it.id }) { post ->
                PostCard(
                    post = post,
                    dateFormatter = dateFormatter
                )
            }
        }
    }
}

@Composable
private fun RangeSelector(value: RangePreset, onChange: (RangePreset) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            RangePreset.entries.forEach { preset ->
                AssistChip(
                    onClick = { onChange(preset) },
                    label = { Text(preset.label) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (preset == value) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        labelColor = if (preset == value) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                )
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    // Reusable two-column row for post metadata/details
    Row(Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PostCard(
    post: PostEntity,
    dateFormatter: DateTimeFormatter,
    modifier: Modifier = Modifier
) {
    val creationDate = remember(post.createdAtEpochDay) {
        LocalDate.ofEpochDay(post.createdAtEpochDay)
    }

    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {

            // Post title
            Text(post.title, fontWeight = FontWeight.Medium)

            // Post description (actual content of the post)
            if (post.description.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = post.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Creation date
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Created: ${creationDate.format(dateFormatter)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(10.dp))

            // Image/video preview
            PostMediaPreview(
                mediaType = post.mediaType,
                mediaUri = post.mediaUri
            )

            Spacer(Modifier.height(10.dp))

            // Post stats
            StatRow("Type", post.mediaType)
            StatRow("File", fileNameFromUri(post.mediaUri))
            StatRow("Views (total)", post.totalViews.formatGrouped())
            StatRow("New viewers (total)", post.totalNewViewers.formatGrouped())
        }
    }
}

/**
 * Media preview for posts details
 * - image files load directly
 * - video files render a thumbnail frame //TODO(Next): Figure out how to play full video
 */
@Composable
fun PostMediaPreview(
    mediaType: String,
    mediaUri: String,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(180.dp)
) {
    val context = LocalContext.current
    val request = ImageRequest.Builder(context)
        .data(mediaUri)
        .crossfade(true)
        .allowHardware(false)
        .apply {
            if (mediaType.equals("VIDEO", ignoreCase = true)) {
                decoderFactory(VideoFrameDecoder.Factory())
            }
        }
        .build()

    AsyncImage(
        model = request,
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Crop
    )
}