package com.example.socialstasts

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.example.socialstasts.components.BarChart
import com.example.socialstasts.components.Bucket
import com.example.socialstasts.components.Group
import com.example.socialstasts.components.Series
import com.example.socialstasts.helpers.RangePreset
import com.example.socialstasts.persistance.AppDb
import com.example.socialstasts.persistance.PostEntity
import com.example.socialstasts.persistance.StatsDao
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.min

class AccountViewActivity : ComponentActivity() {
    companion object { const val ACC_NAME = "account_name" }
    private lateinit var db: AppDb
    private lateinit var dao: StatsDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getDatabase()

        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    AccountViewRoute(
                        dao = dao,
                        accName = intent.getStringExtra(ACC_NAME).orEmpty(),
                        onBack = { finish() }
                    )
                }
            }
        }
    }

    private fun getDatabase() {
        db = AppDb.get(this)
        dao = db.statsDao()
    }
}

////////Composables////////
///////////////////////////

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountViewRoute(dao: StatsDao, accName: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val today = remember { LocalDate.now() }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("d MMM uuuu") }

    // Current chart range selection + end date of displayed window
    var range by remember { mutableStateOf(RangePreset.WEEK) }
    var windowEnd by remember { mutableStateOf(today) }


    // Resolve account summary from account namee
    val summariesFlow = remember(today) {
        val fromDay = today.minusDays(360 - 1).toEpochDay()
        val toDay = today.toEpochDay()
        dao.observeAccountSummaries(fromDay, toDay)
    }
    val summaries by summariesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val summary = remember(summaries, accName) {
        summaries.firstOrNull { it.name == accName }
    }


    // Observe all posts for the selected account (or empty until account resolves)
    val postsFlow = remember(summary?.accountId) {
        summary?.let { dao.observePostsForAccount(it.accountId) } ?: flowOf(emptyList())
    }
    val posts by postsFlow.collectAsStateWithLifecycle(initialValue = emptyList())


    // Observe daily view counts for current chart window
    val chartFlow = remember(summary?.accountId, range, windowEnd) {
        val accId = summary?.accountId
        if (accId == null) {
            flowOf(emptyList())
        } else {
            val fromDay = windowEnd.minusDays(range.days - 1).toEpochDay()
            val toDay = windowEnd.toEpochDay()
            dao.observeAccountDailyViews(accId, fromDay, toDay)
        }
    }
    val dailyRows by chartFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    // Convert daily rows into chart buckets
    val buckets = remember(dailyRows, range, windowEnd) {
        buildBuckets(
            today = windowEnd,
            rangeDays = range.days.toInt(),
            bucketCount = range.buckets,
            bucketDays = range.bucketDays,
            daily = dailyRows.associate { it.epochDay to it.views }
        )
    }

    // Swipe gesture state used to change chart periods left/right
    var dragX by remember { mutableStateOf(0f) }
    val swipeThresholdPx = with(LocalDensity.current) { 72.dp.toPx() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(accName.ifBlank { "Account" }) },
                actions = {
                    TextButton(onClick = onBack) { Text("Back") }
                }
            )
        },

        // FAB opens CreatePostActivity with this account preselected
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    context.startActivity(
                        Intent(context, CreatePostActivity::class.java).apply {
                            putExtra(CreatePostActivity.SELECTED_ACC, accName)
                        }
                    )
                }
            ) {
                Icon(Icons.Filled.Add, contentDescription = "New Post")
            }
        }
    ) { padding ->
        // Check if summary is resolved or show a loading/not-found state
        if (summary == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Loading… (or account not found)")
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // Chart range selector
            item {
                RangeSelector(
                    value = range,
                    onChange = {
                        range = it
                        windowEnd = today
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
                                        windowEnd = range.shiftBack(windowEnd)
                                    } else if (windowEnd.isBefore(today)) {
                                        windowEnd = minOf(today, range.shiftForward(windowEnd))
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
                    fontWeight = FontWeight.SemiBold
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


////////Helpers////////
///////////////////////////

/**
Builds buckets for the Bar chart as a sum of views per selected range
 */
private fun buildBuckets(
    today: LocalDate,
    rangeDays: Int,
    bucketCount: Int,
    bucketDays: Int,
    daily: Map<Long, Int>
): Array<Bucket> {
    val tempBuckets = Array(bucketCount) { Bucket(0f, 0) }

    for (dayOffset in 0 until rangeDays) {
        val views = daily[today.minusDays((rangeDays - 1 - dayOffset).toLong()).toEpochDay()] ?: 0
        tempBuckets[min(bucketCount - 1, dayOffset / bucketDays)].observe(views.toFloat())
    }

    return Array(bucketCount) { i ->
        val b = tempBuckets[i]
        if (b.count == 0) Bucket(0f, 0) else Bucket(sum = b.sum, count = 1)
    }
}

private fun Int.formatGrouped(): String = "%,d".format(this)
private fun fileNameFromUri(uriString: String): String = uriString.substringAfterLast('/')