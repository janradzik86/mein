package pl.terminownik.app.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.terminownik.app.AppContainer
import pl.terminownik.app.R
import pl.terminownik.app.data.PantryRepository
import pl.terminownik.app.domain.CsvExport
import pl.terminownik.app.domain.PlFormat
import pl.terminownik.app.domain.Section
import pl.terminownik.app.domain.ShoppingItem
import pl.terminownik.app.ui.components.ConfirmDialog
import pl.terminownik.app.ui.components.EmptyState

data class ShoppingUiState(
    val loading: Boolean = true,
    val pending: List<ShoppingItem> = emptyList(),
    val done: List<ShoppingItem> = emptyList(),
)

/** Komunikaty listy zakupów – tekst dobiera warstwa UI. */
sealed interface ShoppingMessage {
    data class Exported(val rows: Int) : ShoppingMessage
    data class ExportFailed(val code: String) : ShoppingMessage
    data object Added : ShoppingMessage
    data object Cleared : ShoppingMessage
    data object NothingToExport : ShoppingMessage
}

class ShoppingViewModel(
    private val repository: PantryRepository,
    private val context: Context,
) : ViewModel() {

    private val message = MutableStateFlow<ShoppingMessage?>(null)
    val messageState: StateFlow<ShoppingMessage?> = message

    val uiState: StateFlow<ShoppingUiState> = repository.shoppingList
        .map { list ->
            val ordered = list.sortedByDescending { it.createdAt }
            ShoppingUiState(
                loading = false,
                pending = ordered.filter { !it.done },
                done = ordered.filter { it.done },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShoppingUiState())

    fun add(name: String, quantity: Double?, unit: String?, note: String?) {
        viewModelScope.launch {
            repository.addShoppingItem(
                ShoppingItem(
                    name = name,
                    quantity = quantity,
                    unit = unit,
                    note = note,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            message.value = ShoppingMessage.Added
        }
    }

    fun update(item: ShoppingItem, name: String, quantity: Double?, unit: String?, note: String?) {
        viewModelScope.launch {
            repository.updateShoppingItem(
                item.copy(name = name, quantity = quantity, unit = unit, note = note),
            )
        }
    }

    fun toggle(item: ShoppingItem) {
        viewModelScope.launch { repository.setShoppingDone(item.id, !item.done) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.deleteShoppingItem(id) }
    }

    fun clearDone() {
        viewModelScope.launch {
            repository.clearDoneShoppingItems()
            message.value = ShoppingMessage.Cleared
        }
    }

    /** Eksport do CSV przez systemowy selektor plików – bez uprawnień do pamięci. */
    fun exportCsv(uri: Uri, items: List<ShoppingItem>) {
        if (items.isEmpty()) {
            message.value = ShoppingMessage.NothingToExport
            return
        }
        viewModelScope.launch {
            val csv = withContext(Dispatchers.Default) {
                CsvExport.exportShoppingList(
                    items = items,
                    headerName = context.getString(R.string.csv_headers_name),
                    headerQuantity = context.getString(R.string.csv_headers_quantity),
                    headerUnit = context.getString(R.string.csv_headers_unit),
                    headerSection = context.getString(R.string.csv_headers_section),
                    headerDone = context.getString(R.string.csv_headers_done),
                    headerNote = context.getString(R.string.csv_headers_note),
                    labels = csvLabels(context),
                )
            }
            val written = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(csv.toByteArray(Charsets.UTF_8))
                    } ?: error("no-stream")
                }.isSuccess
            }
            message.value = if (written) {
                ShoppingMessage.Exported(items.size)
            } else {
                ShoppingMessage.ExportFailed("IO")
            }
        }
    }

    fun consumeMessage() {
        message.value = null
    }
}

fun shoppingViewModelFactory(container: AppContainer, context: Context) = viewModelFactory {
    initializer { ShoppingViewModel(container.pantryRepository, context) }
}

/** Lista zakupów: dodawanie, odhaczanie, edycja, usuwanie, eksport CSV. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingScreen(container: AppContainer) {
    val context = LocalContext.current
    val viewModel: ShoppingViewModel = viewModel(factory = shoppingViewModelFactory(container, context))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.messageState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var addDialogOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ShoppingItem?>(null) }
    var clearDialogOpen by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri -> if (uri != null) viewModel.exportCsv(uri, state.pending + state.done) }

    LaunchedEffect(message) {
        val current = message ?: return@LaunchedEffect
        val text = when (current) {
            is ShoppingMessage.Exported -> context.getString(R.string.csv_export_success, current.rows)
            is ShoppingMessage.ExportFailed -> context.getString(R.string.error_io)
            ShoppingMessage.Added -> context.getString(R.string.shopping_added)
            ShoppingMessage.Cleared -> context.getString(R.string.shopping_cleared)
            ShoppingMessage.NothingToExport -> context.getString(R.string.csv_export_empty)
        }
        snackbarHostState.showSnackbar(text)
        viewModel.consumeMessage()
    }

    Box(Modifier.fillMaxSize()) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(stringResource(R.string.shopping_title)) },
                    actions = {
                        TextButton(onClick = { clearDialogOpen = true }) {
                            Text(stringResource(R.string.shopping_clear_done))
                        }
                        TextButton(
                            onClick = {
                                exportLauncher.launch(
                                    "${context.getString(R.string.csv_suggested_filename_shopping)}-" +
                                        "${PlFormat.timestampForFile()}.csv",
                                )
                            },
                        ) { Text(stringResource(R.string.shopping_export)) }
                    },
                )

                Text(
                    text = stringResource(R.string.shopping_left, state.pending.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )

                if (state.pending.isEmpty() && state.done.isEmpty()) {
                    EmptyState(
                        icon = Icons.Filled.ShoppingCart,
                        title = stringResource(R.string.shopping_empty_title),
                        body = stringResource(R.string.shopping_empty_body),
                        actionLabel = stringResource(R.string.shopping_add),
                        onAction = { addDialogOpen = true },
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 96.dp),
                    ) {
                        items(state.pending, key = { it.id }) { item ->
                            ShoppingRow(
                                item = item,
                                onToggle = { viewModel.toggle(item) },
                                onEdit = { editing = item },
                                onDelete = { viewModel.delete(item.id) },
                            )
                        }
                        if (state.done.isNotEmpty()) {
                            item(key = "done-header") {
                                Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                                    HorizontalDivider()
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = stringResource(R.string.shopping_done_section),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            items(state.done, key = { it.id }) { item ->
                                ShoppingRow(
                                    item = item,
                                    onToggle = { viewModel.toggle(item) },
                                    onEdit = { editing = item },
                                    onDelete = { viewModel.delete(item.id) },
                                )
                            }
                        }
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { addDialogOpen = true },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.shopping_add)) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        )

        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }

    if (addDialogOpen) {
        ShoppingItemDialog(
            initial = null,
            onDismiss = { addDialogOpen = false },
            onConfirm = { name, quantity, unit, note ->
                viewModel.add(name, quantity, unit, note)
                addDialogOpen = false
            },
        )
    }

    editing?.let { item ->
        ShoppingItemDialog(
            initial = item,
            onDismiss = { editing = null },
            onConfirm = { name, quantity, unit, note ->
                viewModel.update(item, name, quantity, unit, note)
                editing = null
            },
        )
    }

    if (clearDialogOpen) {
        ConfirmDialog(
            title = stringResource(R.string.shopping_clear_done),
            body = stringResource(R.string.shopping_clear_done_body),
            confirmLabel = stringResource(R.string.delete),
            destructive = true,
            onConfirm = {
                clearDialogOpen = false
                viewModel.clearDone()
            },
            onDismiss = { clearDialogOpen = false },
        )
    }
}

@Composable
private fun ShoppingRow(
    item: ShoppingItem,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val toggleLabel = stringResource(
        if (item.done) R.string.shopping_mark_undone else R.string.shopping_mark_done,
    )
    val pantryLabel = stringResource(R.string.section_pantry)
    val medicineLabel = stringResource(R.string.section_medicine)
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = item.done,
                onCheckedChange = { onToggle() },
                modifier = Modifier.semantics {
                    contentDescription = toggleLabel
                },
            )
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (item.done) TextDecoration.LineThrough else TextDecoration.None,
                )
                val details = buildString {
                    item.quantity?.let { append(PlFormat.formatQuantityWithUnit(it, item.unit)) }
                    item.sectionHint?.let { section ->
                        if (isNotEmpty()) append(" · ")
                        append(if (section == Section.PANTRY) pantryLabel else medicineLabel)
                    }
                    item.note?.takeIf { it.isNotBlank() }?.let {
                        if (isNotEmpty()) append(" · ")
                        append(it)
                    }
                    if (isNotEmpty()) append(" · ")
                    append(PlFormat.formatDateTime(item.createdAt))
                }
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onEdit) { Text(stringResource(R.string.edit)) }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.shopping_delete))
            }
        }
    }
}

@Composable
private fun ShoppingItemDialog(
    initial: ShoppingItem?,
    onDismiss: () -> Unit,
    onConfirm: (String, Double?, String?, String?) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var quantity by remember { mutableStateOf(initial?.quantity?.let { PlFormat.formatQuantity(it) } ?: "") }
    var unit by remember { mutableStateOf(initial?.unit ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var nameError by remember { mutableStateOf(false) }
    var quantityError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.shopping_add else R.string.edit)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = false
                    },
                    label = { Text(stringResource(R.string.shopping_field_name)) },
                    singleLine = true,
                    isError = nameError,
                    supportingText = if (nameError) {
                        { Text(stringResource(R.string.shopping_error_name)) }
                    } else {
                        null
                    },
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = {
                            quantity = it.replace(',', '.')
                            quantityError = false
                        },
                        label = { Text(stringResource(R.string.shopping_field_quantity)) },
                        singleLine = true,
                        isError = quantityError,
                        supportingText = if (quantityError) {
                            { Text(stringResource(R.string.error_quantity_invalid)) }
                        } else {
                            null
                        },
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text(stringResource(R.string.shopping_field_unit)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.shopping_field_note)) },
                    maxLines = 3,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsed = if (quantity.isBlank()) null else quantity.toDoubleOrNull()
                    nameError = name.isBlank()
                    quantityError = quantity.isNotBlank() && parsed == null
                    if (!nameError && !quantityError) {
                        onConfirm(
                            name.trim(),
                            parsed?.takeIf { it >= 0 },
                            unit.trim().takeIf { it.isNotBlank() },
                            note.trim().takeIf { it.isNotBlank() },
                        )
                    }
                },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.save))
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        icon = { Icon(if (initial == null) Icons.Filled.Add else Icons.Filled.Undo, contentDescription = null) },
    )
}
