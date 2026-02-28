package com.example.socialstasts

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.example.socialstasts.helpers.PickedMedia
import com.example.socialstasts.persistance.AccountEntity
import com.example.socialstasts.persistance.AppDb
import com.example.socialstasts.persistance.StatsDao
import com.example.socialstasts.persistance.StatsRepository
import kotlinx.coroutines.launch
import java.time.LocalDate
private const val ACCOUNT_ALL = "__ALL__"

class CreatePostActivity : ComponentActivity() {

    companion object { const val SELECTED_ACC = "account_name" }

    private lateinit var db: AppDb
    private lateinit var dao: StatsDao
    private lateinit var repo: StatsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getDatabase()

        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    CreatePostScreen(
                        selectedAccName = intent.getStringExtra(SELECTED_ACC),
                        loadAccounts = { dao.getAllAccounts() },
                        onBack = { finish() },
                        onCreate = { targets, title, description, picked ->
                            lifecycleScope.launch {
                                createPost(
                                    targets = targets,
                                    title = title,
                                    description = description,
                                    picked = picked
                                )

                                // Create post confirmation
                                Toast.makeText(
                                    this@CreatePostActivity,
                                    "Post created",
                                    Toast.LENGTH_SHORT
                                ).show()
                                finish()
                            }
                        }
                    )
                }
            }
        }
    }

    private fun getDatabase() {
        db = AppDb.get(this)
        dao = db.statsDao()
        repo = StatsRepository(db, dao)
    }

    /**
     * Creates the same post for each selected target account (TODO about subset)
     */
    private suspend fun createPost(targets: List<String>, title: String, description: String, picked: PickedMedia) {
        targets.forEach { accountName ->
            repo.createPost(
                accName = accountName,
                title = title,
                description = description,
                mediaType = picked.mediaType,
                mediaUri = picked.mediaUri,
                today = LocalDate.now()
            )
        }
    }
}


////////Composables////////
///////////////////////////

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreatePostScreen (
    selectedAccName: String?,
    loadAccounts: suspend () -> List<AccountEntity>,
    onBack: () -> Unit,
    onCreate: (targets: List<String>, title: String, description: String, picked: PickedMedia) -> Unit
) {
    var accounts by remember { mutableStateOf<List<AccountEntity>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Post composition:
    // - Selected accounts (or special "all") NOTE: Fixed from previous version
    // - Title of the post (for easier lookup, not actual post text)
    // - Description of the post (used as text of the post)
    // - Selected media
    // - Account dialog visibility
    var selectedAccKeys by remember {
        mutableStateOf(
            if (selectedAccName != null) setOf(selectedAccName) else setOf(ACCOUNT_ALL)
        )
    }
    var title by remember { mutableStateOf("") }         // internal post title/label
    var description by remember { mutableStateOf("") }   // user-facing post text
    var pickedMedia by remember { mutableStateOf<PickedMedia?>(null) }
    var showAccountDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        accounts = loadAccounts()
        isLoading = false

        // If user came with a preselected account keep it, else if no preselection, keep AL
        if (selectedAccName == null && accounts.isNotEmpty()) {
            selectedAccKeys = setOf(ACCOUNT_ALL)
        }
    }

    // Media picker
    val mediaPickerLauncher = summonMediaPicker(LocalContext.current) { picked ->
        pickedMedia = picked
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Post") },
                actions = {
                    TextButton(onClick = onBack) { Text("Back") }
                }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Loading / no accounts
            when {
                isLoading -> {
                    CircularProgressIndicator()
                    return@Column
                }

                accounts.isEmpty() -> {
                    Text("No accounts yet. Try update on home page.")
                    return@Column
                }
            }

            // Account selection summary card
            AccountSelectorCard(
                accounts = accounts,
                selectedAccKeys = selectedAccKeys,
                onClick = { showAccountDialog = true }
            )


            if (showAccountDialog) {
                AccountSelector(
                    accounts = accounts,
                    selected = selectedAccKeys,
                    onSelectedChange = { selectedAccKeys = it },
                    onDismiss = { showAccountDialog = false }
                )
            }

            // Android system picker for images/videos
            MediaSelectorCard(
                pickedMedia = pickedMedia,
                onClick = { mediaPickerLauncher.launch(arrayOf("image/*", "video/*")) }
            )


            pickedMedia?.let { picked ->
                PostPreview(
                    mediaType = picked.mediaType,
                    mediaUri = picked.mediaUri
                )
            }

            // Title
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Title") }
            )

            // Description
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Description") },
                minLines = 3
            )

            //Create post button
            Button(
                onClick = {
                    onCreate(
                        resolveTargetAccounts(accounts, selectedAccKeys),
                        title.trim(),
                        description.trim(),
                        pickedMedia ?: return@Button
                    )
                },
                enabled = title.isNotBlank() && pickedMedia != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Create")
            }
        }
    }
}

/**
 * Simple media preview:
 * - IMAGE -> regular image load
 * - VIDEO -> thumbnail frame via Coil video decoder
 */
@Composable
private fun PostPreview(
    mediaType: String,
    mediaUri: String,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(180.dp)
) {
    val request = ImageRequest.Builder(LocalContext.current)
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

/**
 * Changed from the single account selection to multi-selection
 */
@Composable
private fun AccountSelectorCard(accounts: List<AccountEntity>, selectedAccKeys: Set<String>, onClick: () -> Unit) {
    val isAll = selectedAccKeys.contains(ACCOUNT_ALL) ||
            (selectedAccKeys.isNotEmpty() && selectedAccKeys.containsAll(accounts.map { it.name }
                .toSet()))

    val label = when {
        isAll -> "All accounts"
        selectedAccKeys.isEmpty() -> "None selected"
        else -> selectedAccKeys.joinToString(", ")
    }

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("Accounts", style = MaterialTheme.typography.labelMedium)
            Text(label)
        }
    }
}


/**
*Account hamburger selection with clickable rows
*/
@Composable
private fun AccountSelector(
    accounts: List<AccountEntity>,
    selected: Set<String>,
    onSelectedChange: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val names = accounts.map { it.name }.toSet()
    val isAll = selected.contains(ACCOUNT_ALL) ||
            (selected.isNotEmpty() && selected.containsAll(names))

    // Collapses all individually selected into ACCOUNT_ALL
    fun normalize(next: Set<String>): Set<String> {
        return if (next.isNotEmpty() && next.containsAll(names)) {
            setOf(ACCOUNT_ALL)
        } else next
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select accounts") },
        confirmButton = {
            Button(
                onClick = onDismiss,
                enabled = selected.isNotEmpty() || isAll
            ) { Text("Done") }
        },
        text = {
            Column {
                AccountSelectorRow(
                    text = "All accounts",
                    checked = isAll,
                    onToggle = {
                        if (!isAll) {
                            onSelectedChange(setOf(ACCOUNT_ALL))
                        }
                    }
                )

                accounts.forEach { account ->
                    AccountSelectorRow(
                        text = account.name,
                        checked = isAll || selected.contains(account.name),
                        onToggle = {
                            val next = when {
                                isAll -> setOf(account.name)
                                selected.contains(account.name) -> {
                                    if (selected.size == 1) selected else (selected - account.name)
                                }
                                else -> selected + account.name
                            }
                            onSelectedChange(normalize(next))
                        }
                    )
                }
            }
        }
    )
}


// CHANGE: checkbox row UI
@Composable
private fun AccountSelectorRow(
    text: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onToggle() }
        )
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}

// Remembers an Activity Result launcher for Android's document picker
@Composable
private fun summonMediaPicker(
    context: Context,
    onPicked: (PickedMedia) -> Unit
) = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
    if (uri == null) return@rememberLauncherForActivityResult

    val mediaType = inferMediaType(context, uri)
    val displayName = queryDisplayName(context.contentResolver, uri) ?: uri.lastPathSegment ?: "picked_media"

    // Persist read permission so the URI still works later (after restart) TODO: Think this thru before server connected version
    try {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    } catch (_: SecurityException) {
    }

    onPicked(
        PickedMedia(
            mediaType = mediaType,
            mediaUri = uri.toString(),
            displayName = displayName
        )
    )
}

/**
* Clickable card showing picker state
 */
@Composable
private fun MediaSelectorCard(pickedMedia: PickedMedia?, onClick: () -> Unit) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("Media", style = MaterialTheme.typography.labelMedium)
            Text(
                pickedMedia?.let { "${it.mediaType}: ${it.displayName}" }
                    ?: "Tap to pick image/video"
            )
        }
    }
}

////////Helpers////////
///////////////////////////


/**
 * Based on selected accounts, returns their names
 */
private fun resolveTargetAccounts(accounts: List<AccountEntity>, selectedAccKeys: Set<String>): List<String> {
    val names = accounts.map { it.name }
    val isAll = selectedAccKeys.contains(ACCOUNT_ALL) ||
            (selectedAccKeys.isNotEmpty() && selectedAccKeys.containsAll(names.toSet()))

    return if (isAll) { names } else { names.filter { selectedAccKeys.contains(it) } }
}

/**
 * Detects basic media type -> (Video/Image)
 */
private fun inferMediaType(context: Context, uri: Uri): String {
    val mime = context.contentResolver.getType(uri).orEmpty()
    return when {
        mime.startsWith("video/") -> "VIDEO"
        mime.startsWith("image/") -> "IMAGE"
        uri.toString().contains(".mp4", ignoreCase = true) -> "VIDEO"
        else -> "IMAGE"
    }
}


/**
 * Reads media name from content provider metadata, if available
 * */
private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? {
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && cursor.moveToFirst())
            return cursor.getString(idx)
    }
    return null
}