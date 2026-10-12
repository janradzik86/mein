package pl.terminownik.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import pl.terminownik.app.AppContainer
import pl.terminownik.app.R
import pl.terminownik.app.domain.DateSource
import pl.terminownik.app.domain.ExpiryDate
import pl.terminownik.app.domain.ExpiryFilter
import pl.terminownik.app.domain.ItemWithExpiry
import pl.terminownik.app.domain.PlFormat
import pl.terminownik.app.domain.Section
import pl.terminownik.app.ui.components.ConfirmDialog
import pl.terminownik.app.ui.components.EmptyState
import pl.terminownik.app.ui.components.ExpiryDateDialog
import pl.terminownik.app.ui.components.ItemCard
import pl.terminownik.app.ui.components.LoadingState
import pl.terminownik.app.ui.components.NoticeBar
import pl.terminownik.app.ui.components.NoticeTone

fun itemListViewModelFactory(container: AppContainer, section: Section?) = viewModelFactory {
    initializer {
        ItemListViewModel(container.pantryRepository, container.reminderScheduler, section)
    }
}

/** Lista pozycji jednej sekcji (Spiżarnia albo Apteczka). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemListScreen(
    container: AppContainer,
    section: Section,
    onOpenItem: (Long) -> Unit,
    onAddItem: () -> Unit,
    onScan: () -> Unit,
    onOcr: () -> Unit,
    onOpenDeadlines: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: ItemListViewModel = viewModel(
        key = "items-${section.name}",
        factory = itemListViewModelFactory(container, section),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var searchVisible by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Long?>(null) }
    var restockTarget by remember { mutableStateOf<ItemWithExpiry?>(null) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var locationMenuOpen by remember { mutableStateOf(false) }
    var fabOpen by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    var restockDone by remember { mutableStateOf(false) }

    val title = when (section) {
        Section.PANTRY -> stringResource(R.string.section_pantry)
        Section.MEDICINE -> stringResource(R.string.section_medicine)
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(title) },
                    actions = {
                        IconButton(onClick = {
                            searchVisible = !searchVisible
                            if (!searchVisible) viewModel.setQuery("")
                        }) {
                            Icon(
                                if (searchVisible) Icons.Filled.Close else Icons.Filled.Search,
                                contentDescription = stringResource(
                                    if (searchVisible) R.string.search_clear else R.string.search_hint,
                                ),
                            )
                        }
                        IconButton(onClick = { locationMenuOpen = true }) {
                            Icon(Icons.Filled.FilterList, contentDescription = stringResource(R.string.filter_storage))
                        }
                        DropdownMenu(expanded = locationMenuOpen, onDismissRequest = { locationMenuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.filter_storage_all)) },
                                onClick = {
                                    viewModel.setLocation(null)
                                    locationMenuOpen = false
                                },
                            )
                            state.locations.forEach { location ->
                                DropdownMenuItem(
                                    text = { Text(location) },
                                    onClick = {
                                        viewModel.setLocation(location)
                                        locationMenuOpen = false
                                    },
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            if (state.showArchived) {
                                                R.string.hide_archived
                                            } else {
                                                R.string.show_archived
                                            },
                                        )
                                    )
                                },
                                onClick = {
                                    viewModel.toggleArchived()
                                    locationMenuOpen = false
                                },
                            )
                        }
                        IconButton(onClick = { sortMenuOpen = true }) {
                            Icon(Icons.Filled.SwapVert, contentDescription = stringResource(R.string.sort_by_date))
                        }
                        DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sort_by_date)) },
                                onClick = {
                                    viewModel.setSort(SortBy.DATE)
                                    sortMenuOpen = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sort_by_name)) },
                                onClick = {
                                    viewModel.setSort(SortBy.NAME)
                                    sortMenuOpen = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sort_by_added)) },
                                onClick = {
                                    viewModel.setSort(SortBy.ADDED)
                                    sortMenuOpen = false
                                },
                            )
                        }
                    },
                )

                if (searchVisible) {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = viewModel::setQuery,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        label = { Text(stringResource(R.string.search_hint)) },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (state.query.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setQuery("") }) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = stringResource(R.string.search_clear),
                                    )
                                }
                            }
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                }

                ExpiryFilterRow(
                    current = state.filter,
                    expiring = state.expiringSoon,
                    expired = state.expired,
                    noDate = state.noDate,
                    unconfirmed = state.unconfirmed,
                    onSelect = viewModel::setFilter,
                )

                state.location?.let { location ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilterChip(
                            selected = true,
                            onClick = { viewModel.setLocation(null) },
                            label = { Text(location) },
                        )
                        Text(
                            stringResource(R.string.items_count, state.visible.size),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                } ?: run {
                    Text(
                        text = stringResource(R.string.items_count, state.visible.size),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }

                if (section == Section.MEDICINE && state.visible.isNotEmpty()) {
                    NoticeBar(
                        text = stringResource(R.string.disclaimer_medicine),
                        icon = Icons.Filled.LocalPharmacy,
                        tone = NoticeTone.INFO,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }

                when {
                    state.loading -> LoadingState()
                    state.visible.isEmpty() && state.totalCount == 0 -> EmptyState(
                        icon = if (section == Section.PANTRY) Icons.Filled.Restaurant else Icons.Filled.LocalPharmacy,
                        title = stringResource(
                            if (section == Section.PANTRY) R.string.empty_pantry_title else R.string.empty_medicine_title,
                        ),
                        body = stringResource(
                            if (section == Section.PANTRY) R.string.empty_pantry_body else R.string.empty_medicine_body,
                        ),
                        actionLabel = stringResource(R.string.action_add),
                        onAction = onAddItem,
                    )

                    state.visible.isEmpty() && state.query.isNotBlank() -> EmptyState(
                        icon = Icons.Filled.Search,
                        title = stringResource(R.string.empty_search_title),
                        body = stringResource(R.string.empty_search_body),
                        actionLabel = stringResource(R.string.search_clear),
                        onAction = { viewModel.setQuery("") },
                    )

                    state.visible.isEmpty() -> EmptyState(
                        icon = Icons.Filled.FilterList,
                        title = stringResource(R.string.empty_filter_title),
                        body = stringResource(R.string.empty_filter_body),
                        actionLabel = stringResource(R.string.filter_all),
                        onAction = { viewModel.setFilter(ExpiryFilter.ALL) },
                    )

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = 96.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.visible, key = { it.item.id }) { row ->
                            ItemCard(
                                row = row,
                                onClick = { onOpenItem(row.item.id) },
                                onUse = { viewModel.markUsed(row.item.id) },
                                onDiscard = { viewModel.markDiscarded(row.item.id) },
                                onReactivate = { viewModel.markActive(row.item.id) },
                                onRestock = { restockTarget = row },
                                onAddToShopping = { viewModel.addToShopping(row.item.id) },
                                onDelete = { deleteTarget = row.item.id },
                                onConfirmDate = { viewModel.confirmDate(row.item.id) },
                            )
                        }
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp),
            )

            Box(Modifier.align(Alignment.BottomEnd).padding(20.dp)) {
                ExtendedFloatingActionButton(
                    onClick = { fabOpen = !fabOpen },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.action_add)) },
                )
                DropdownMenu(expanded = fabOpen, onDismissRequest = { fabOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_add)) },
                        leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        onClick = {
                            fabOpen = false
                            onAddItem()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_scan)) },
                        leadingIcon = { Icon(Icons.Filled.CameraAlt, contentDescription = null) },
                        onClick = {
                            fabOpen = false
                            onScan()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_ocr)) },
                        leadingIcon = { Icon(Icons.Filled.TextFields, contentDescription = null) },
                        onClick = {
                            fabOpen = false
                            onOcr()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.nav_deadlines)) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        onClick = {
                            fabOpen = false
                            onOpenDeadlines()
                        },
                    )
                }
            }
        }
    }

    LaunchedEffect(restockDone) {
        if (restockDone) {
            restockDone = false
            snackbarHostState.showSnackbar(context.getString(R.string.restock_done))
        }
    }

    deleteTarget?.let { id ->
        ConfirmDialog(
            title = stringResource(R.string.form_delete_confirm_title),
            body = stringResource(R.string.form_delete_confirm_body),
            confirmLabel = stringResource(R.string.delete),
            destructive = true,
            onConfirm = {
                viewModel.delete(id)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }

    restockTarget?.let { row ->
        RestockDialog(
            item = row,
            onDismiss = { restockTarget = null },
            onConfirm = { quantity, expiry, batch ->
                viewModel.restock(row.item.id, quantity, expiry, batch)
                restockTarget = null
                restockDone = true
            },
        )
    }
}

/** Pasek filtrów terminów z licznikami. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpiryFilterRow(
    current: ExpiryFilter,
    expiring: Int,
    expired: Int,
    noDate: Int,
    unconfirmed: Int,
    onSelect: (ExpiryFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val filters = listOf(
        ExpiryFilter.ALL to stringResource(R.string.filter_all),
        ExpiryFilter.EXPIRING to "${stringResource(R.string.filter_expiring)} ($expiring)",
        ExpiryFilter.EXPIRED to "${stringResource(R.string.filter_expired)} ($expired)",
        ExpiryFilter.UNCONFIRMED to "${stringResource(R.string.filter_unconfirmed)} ($unconfirmed)",
        ExpiryFilter.NO_DATE to "${stringResource(R.string.filter_no_date)} ($noDate)",
    )
    Column(modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            filters.forEach { (filter, label) ->
                FilterChip(
                    selected = current == filter,
                    onClick = { onSelect(filter) },
                    label = { Text(label) },
                )
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

/**
 * Dialog uzupełnienia zapasu.
 *
 * Tworzy NOWĄ partię (specyfikacja: partie nie są łączone automatycznie),
 * dlatego wymaga podania terminu dla nowej partii i nie modyfikuje starej.
 */
@Composable
fun RestockDialog(
    item: ItemWithExpiry,
    onDismiss: () -> Unit,
    onConfirm: (Double?, ExpiryDate?, String?) -> Unit,
) {
    var quantityText by remember { mutableStateOf(item.item.quantity?.let { PlFormat.formatQuantity(it) } ?: "") }
    var batch by remember { mutableStateOf(item.item.batchCode ?: "") }
    var expiry by remember { mutableStateOf<ExpiryDate?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var quantityError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.restock_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(R.string.restock_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = {
                        quantityText = it.replace(',', '.')
                        quantityError = false
                    },
                    label = { Text(stringResource(R.string.restock_quantity)) },
                    singleLine = true,
                    isError = quantityError,
                    supportingText = if (quantityError) {
                        { Text(stringResource(R.string.error_quantity_invalid)) }
                    } else {
                        null
                    },
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = batch,
                    onValueChange = { batch = it },
                    label = { Text(stringResource(R.string.restock_batch)) },
                    singleLine = true,
                )
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { showDatePicker = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        expiry?.let { PlFormat.formatExpiry(it) }
                            ?: stringResource(R.string.date_set_action),
                    )
                }
                if (expiry != null) {
                    NoticeBar(
                        text = stringResource(R.string.disclaimer_barcodes),
                        icon = Icons.Filled.Search,
                        tone = NoticeTone.INFO,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsed = if (quantityText.isBlank()) {
                        null
                    } else {
                        quantityText.toDoubleOrNull()
                    }
                    if (quantityText.isNotBlank() && parsed == null) {
                        quantityError = true
                    } else {
                        onConfirm(parsed, expiry, batch.takeIf { it.isNotBlank() })
                    }
                },
            ) { Text(stringResource(R.string.restock_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )

    if (showDatePicker) {
        ExpiryDateDialog(
            initial = expiry,
            source = DateSource.MANUAL,
            onDismiss = { showDatePicker = false },
            onConfirm = { picked ->
                expiry = picked
                showDatePicker = false
            },
        )
    }
}
