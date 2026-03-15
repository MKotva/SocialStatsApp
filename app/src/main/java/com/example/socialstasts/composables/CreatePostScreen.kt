package com.example.socialstasts.composables

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.example.socialstasts.createpost.ACCOUNT_ALL
import com.example.socialstasts.createpost.CreatePostUiState
import com.example.socialstasts.createpost.CreatePostViewModel
import com.example.socialstasts.createpost.CreatePostViewModelFactory
import com.example.socialstasts.createpost.buildPickedMedia
import com.example.socialstasts.helpers.PickedMedia
import com.example.socialstasts.helpers.resolveTargetAccounts
import com.example.socialstasts.helpers.getAccountsLabel
import com.example.socialstasts.persistance.AccountEntity
import com.example.socialstasts.persistance.StatsRepository
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@Composable
fun CreatePostRoute(selectedAccName: String?, repo: StatsRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val factory = remember(repo, selectedAccName) { CreatePostViewModelFactory(repo, selectedAccName) }
    val vm: CreatePostViewModel = viewModel(factory = factory)
    val uiState by vm.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.postCreated) {
        if (uiState.postCreated) {
            Toast.makeText(context, "Post created", Toast.LENGTH_SHORT).show()
            vm.onPostCreatedConsumed()
            onBack()
        }
    }

    CreatePostScreen(
        uiState = uiState,
        onBack = onBack,
        onSelectedAccKeysChange = vm::onSelectedAccountsChanged,
        onTitleChange = vm::onTitleChange,
        onDescriptionChange = vm::onDescriptionChange,
        onPickedMedia = vm::onPickedMedia,
        onCreate = vm::createPost
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreatePostScreen(
    uiState: CreatePostUiState,
    onBack: () -> Unit,
    onSelectedAccKeysChange: (Set<String>) -> Unit,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onPickedMedia: (PickedMedia) -> Unit,
    onCreate: () -> Unit
) {
    var showAccountDialog by remember { mutableStateOf(false) }
    val mediaPicker  = summonMediaPicker(LocalContext.current, onPickedMedia)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Post") },
                actions = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator()
                return@Column
            }

            AccountSelectorCard(
                onShowAccountDialogChange = { showAccountDialog = it },
                uiState = uiState
            )

            // Title
            OutlinedTextField(
                value = uiState.title,
                onValueChange = onTitleChange,
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Description
            OutlinedTextField(
                value = uiState.description,
                onValueChange = onDescriptionChange,
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4
            )

            //picker for images/videos
            uiState.pickedMedia?.let { picked ->
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Selected media")
                        Spacer(Modifier.height(8.dp))
                        MediaPreview(picked = picked)
                        Spacer(Modifier.height(8.dp))
                        Text(picked.displayName)
                    }
                }
            }

            // Android system picker for images/video
            Button(
                onClick = {
                    mediaPicker.launch(arrayOf("image/*", "video/*"))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Pick media")
            }

            //Create post button
            Button(
                onClick = onCreate,
                enabled = uiState.title.isNotBlank() &&
                        uiState.pickedMedia != null &&
                        resolveTargetAccounts(uiState.accounts, uiState.selectedAccKeys).isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Create")
            }
        }
    }

    if (showAccountDialog) {
        AccountSelector(
            accounts = uiState.accounts,
            selected = uiState.selectedAccKeys,
            onSelectedChange = onSelectedAccKeysChange,
            onDismiss = { showAccountDialog = false }
        )
    }
}

/**
 * Simple media preview:
 * - IMAGE -> regular image load
 * - VIDEO -> thumbnail frame via Coil video decoder
 */
@Composable
private fun MediaPreview(
    picked: PickedMedia,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(180.dp)
) {
    val context = LocalContext.current
    val request = ImageRequest.Builder(context)
        .data(picked.mediaUri)
        .crossfade(true)
        .allowHardware(false)
        .apply {
            if (picked.mediaType.equals("VIDEO", ignoreCase = true)) {
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
private fun AccountSelectorCard(onShowAccountDialogChange: (Boolean) -> Unit, uiState: CreatePostUiState) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onShowAccountDialogChange(true) }
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Accounts", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                text = getAccountsLabel(uiState.accounts, uiState.selectedAccKeys),
                style = MaterialTheme.typography.bodyLarge
            )
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
            .padding(vertical = 8.dp),
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
) = rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocument()) { uri: Uri? ->
    if (uri != null) {
        val picked = buildPickedMedia(context, uri)
        if (picked != null) {
            onPicked(picked)
        } else {
            Toast.makeText(context, "Unsupported media", Toast.LENGTH_SHORT).show()
        }
    }
}