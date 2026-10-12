package pl.terminownik.app.ui.screens

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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import pl.terminownik.app.AppContainer
import pl.terminownik.app.R
import pl.terminownik.app.domain.ExpiryFilter
import pl.terminownik.app.domain.Section
import pl.terminownik.app.ui.components.EmptyState
import pl.terminownik.app.ui.components.ItemCard
import pl.terminownik.app.ui.components.NoticeBar
import pl.terminownik.app.ui.components.NoticeTone

/**
 * Widok terminów: kończące się, po terminie, brak daty i wymagające potwierdzenia.
 *
 * Łączy obie sekcje (Spiżarnia i Apteczka), bo tu liczy się czas, a nie miejsce.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeadlinesScreen(
    container: AppContainer,
    onOpenItem: (Section, Long) -> Unit,
    onAddItem: (Section) -> Unit,
    onScan: (Section) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val viewModel: ItemListViewModel = viewModel(
        key = "items-deadlines",
        factory = itemListViewModelFactory(container, null),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var deleteTarget by remember { mutableStateOf<Long?>(null) }
    var restockTarget by remember { mutableStateOf<pl.terminownik.app.domain.ItemWithExpiry?>(null) }
    var fabOpen by remember { mutableStateOf(false) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(title = { Text(stringResource(R.string.deadlines_title)) })

                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            ExpiryFilter.EXPIRING to stringResource(R.string.deadlines_tab_expiring),
                            ExpiryFilter.EXPIRED to stringResource(R.string.deadlines_tab_expired),
                            ExpiryFilter.UNCONFIRMED to stringResource(R.string.deadlines_tab_unconfirmed),
                            ExpiryFilter.NO_DATE to stringResource(R.string.deadlines_tab_no_date),
                            ExpiryFilter.ALL to stringResource(R.string.deadlines_tab_all),
                        ).forEach { (filter, label) ->
                            FilterChip(
                                selected = state.filter == filter,
                                onClick = { viewModel.setFilter(filter) },
                                label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(
                            R.string.deadlines_summary,
                            state.expiringSoon,
                            state.expired,
                            state.unconfirmed,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    if (state.settings.reminderEnabled) {
                        NoticeBar(
                            text = stringResource(
                                R.string.deadlines_reminder_state_on,
                                state.settings.reminderLeadDays,
                                "%02d:%02d".format(state.settings.reminderHourOfDay, state.settings.reminderMinute),
                            ),
                            icon = Icons.Filled.Notifications,
                            tone = NoticeTone.INFO,
                        )
                    } else {
                        Column {
                            NoticeBar(
                                text = stringResource(R.string.deadlines_reminder_state_off),
                                icon = Icons.Filled.Notifications,
                                tone = NoticeTone.WARNING,
                            )
                            TextButton(onClick = onOpenSettings) {
                                Text(stringResource(R.string.nav_settings))
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    NoticeBar(
                        text = stringResource(R.string.disclaimer_food),
                        icon = Icons.Filled.Restaurant,
                        tone = NoticeTone.INFO,
                    )
                }

                if (state.visible.isEmpty()) {
                    val title = when (state.filter) {
                        ExpiryFilter.EXPIRING -> stringResource(
                            R.string.deadlines_empty_expiring,
                            state.settings.reminderLeadDays,
                        )

                        ExpiryFilter.EXPIRED -> stringResource(R.string.deadlines_empty_expired)
                        ExpiryFilter.NO_DATE -> stringResource(R.string.deadlines_empty_no_date)
                        ExpiryFilter.UNCONFIRMED -> stringResource(R.string.deadlines_empty_unconfirmed)
                        ExpiryFilter.ALL -> stringResource(R.string.deadlines_empty_all)
                    }
                    EmptyState(
                        icon = Icons.Filled.DateRange,
                        title = title,
                        body = stringResource(R.string.empty_filter_body),
                        actionLabel = stringResource(R.string.action_add),
                        onAction = { onAddItem(Section.PANTRY) },
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.visible, key = { it.item.id }) { row ->
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (row.item.section == Section.PANTRY) {
                                            Icons.Filled.Restaurant
                                        } else {
                                            Icons.Filled.LocalPharmacy
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(
                                            if (row.item.section == Section.PANTRY) {
                                                R.string.section_pantry
                                            } else {
                                                R.string.section_medicine
                                            },
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                ItemCard(
                                    row = row,
                                    onClick = { onOpenItem(row.item.section, row.item.id) },
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
            }

            Box(Modifier.align(Alignment.BottomEnd).padding(20.dp)) {
                ExtendedFloatingActionButton(
                    onClick = { fabOpen = !fabOpen },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.action_add)) },
                )
                DropdownMenu(expanded = fabOpen, onDismissRequest = { fabOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.section_pantry)) },
                        leadingIcon = { Icon(Icons.Filled.Restaurant, contentDescription = null) },
                        onClick = {
                            fabOpen = false
                            onAddItem(Section.PANTRY)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.section_medicine)) },
                        leadingIcon = { Icon(Icons.Filled.LocalPharmacy, contentDescription = null) },
                        onClick = {
                            fabOpen = false
                            onAddItem(Section.MEDICINE)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_scan)) },
                        leadingIcon = { Icon(Icons.Filled.CameraAlt, contentDescription = null) },
                        onClick = {
                            fabOpen = false
                            onScan(Section.PANTRY)
                        },
                    )
                }
            }
        }
    }

    deleteTarget?.let { id ->
        pl.terminownik.app.ui.components.ConfirmDialog(
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
            },
        )
    }
}
