package pl.terminownik.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import pl.terminownik.app.AppContainer
import pl.terminownik.app.BuildConfig
import pl.terminownik.app.R
import pl.terminownik.app.data.backup.BackupManifestData
import pl.terminownik.app.data.backup.RestoreMode
import pl.terminownik.app.domain.PlFormat
import pl.terminownik.app.domain.ThemeMode
import pl.terminownik.app.notifications.ExactAlarmState
import pl.terminownik.app.notifications.PermissionState
import pl.terminownik.app.ui.components.ConfirmDialog
import pl.terminownik.app.ui.components.NoticeBar
import pl.terminownik.app.ui.components.NoticeTone
import pl.terminownik.app.ui.components.ReminderTimeDialog

/** Ustawienia: wygląd, przypomnienia, dane (CSV/kopia/odzyskiwanie/kasowanie), informacje. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    onOpenAbout: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenLicenses: () -> Unit,
    onDataErased: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: SettingsViewModel = viewModel(factory = settingsViewModelFactory(container, context))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var timeDialogOpen by remember { mutableStateOf(false) }
    var eraseDialogOpen by remember { mutableStateOf(false) }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) viewModel.exportCsv(uri, csvItemColumns(context), csvLabels(context))
    }
    val backupExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> if (uri != null) viewModel.exportBackup(uri) }
    val backupImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) viewModel.inspectBackup(uri) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.refreshPermissions() }

    LaunchedEffect(Unit) { viewModel.refreshPermissions() }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        val texts = mutableListOf<String>()
        when (message) {
            is SettingsMessage.BackupExported -> texts += context.getString(
                R.string.backup_export_success,
                message.summary.items,
                message.summary.photos,
                PlFormat.formatFileSize(message.summary.bytes),
            )

            is SettingsMessage.BackupRestored -> {
                texts += context.getString(
                    R.string.backup_import_success,
                    message.summary.items,
                    message.summary.photos,
                    message.summary.barcodeNames,
                    message.summary.shoppingItems,
                )
                if (message.summary.skippedItems > 0) {
                    texts += context.getString(
                        R.string.backup_import_skipped,
                        message.summary.skippedItems,
                    )
                }
                if (message.summary.warnings.isNotEmpty()) {
                    texts += context.getString(
                        R.string.backup_warnings,
                        message.summary.warnings
                            .map { context.getString(backupWarningText(it)) }
                            .joinToString(" • "),
                    )
                }
            }

            is SettingsMessage.BackupFailed -> texts += context.getString(backupErrorText(message.code))
            is SettingsMessage.CsvExported -> texts += context.getString(R.string.csv_export_success, message.rows)
            is SettingsMessage.CsvEmpty -> texts += context.getString(R.string.csv_export_empty)
            SettingsMessage.DataErased -> texts += context.getString(R.string.settings_erased)
            SettingsMessage.TestNotificationSent -> texts += context.getString(R.string.settings_test_sent)
        }
        texts.forEach { snackbarHostState.showSnackbar(it) }
        viewModel.consumeMessage()
        if (message == SettingsMessage.DataErased) onDataErased()
    }

    Box(Modifier.fillMaxSize()) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 32.dp),
            ) {
                TopAppBar(title = { Text(stringResource(R.string.settings_title)) })

                viewModel.busyLabelRes()?.let { labelRes ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(labelRes))
                    }
                }

                // --- Wygląd ---
                SettingsSection(stringResource(R.string.settings_section_appearance)) {
                    Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            ThemeMode.SYSTEM to R.string.theme_system,
                            ThemeMode.LIGHT to R.string.theme_light,
                            ThemeMode.DARK to R.string.theme_dark,
                        ).forEach { (mode, labelRes) ->
                            FilterChip(
                                selected = state.settings.themeMode == mode,
                                onClick = { viewModel.setTheme(mode) },
                                label = { Text(stringResource(labelRes)) },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.settings_section_theme_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    SwitchRow(
                        title = stringResource(R.string.show_archived),
                        checked = state.settings.showArchived,
                        onCheckedChange = viewModel::setShowArchived,
                    )
                    Text(
                        text = stringResource(R.string.settings_font_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // --- Przypomnienia ---
                SettingsSection(stringResource(R.string.settings_section_reminders)) {
                    SwitchRow(
                        title = stringResource(R.string.settings_reminders_enabled),
                        checked = state.settings.reminderEnabled,
                        onCheckedChange = viewModel::setRemindersEnabled,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (state.settings.reminderLeadDays == 0) {
                            stringResource(R.string.settings_lead_days_zero)
                        } else {
                            stringResource(R.string.settings_lead_days, state.settings.reminderLeadDays)
                        },
                        style = MaterialTheme.typography.labelLarge,
                    )
                    androidx.compose.material3.Slider(
                        value = state.settings.reminderLeadDays.toFloat(),
                        onValueChange = { viewModel.setLeadDays(it.toInt()) },
                        valueRange = 0f..60f,
                        steps = 59,
                    )
                    SwitchRow(
                        title = stringResource(R.string.settings_remind_due_day),
                        checked = state.settings.remindOnDueDay,
                        onCheckedChange = viewModel::setRemindOnDueDay,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(
                            R.string.settings_reminder_time,
                            "%02d:%02d".format(state.settings.reminderHourOfDay, state.settings.reminderMinute),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = { timeDialogOpen = true }) {
                        Text(stringResource(R.string.settings_reminder_time_action))
                    }
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))

                    val granted = state.notificationsGranted
                    Text(
                        text = stringResource(
                            R.string.settings_notifications_state,
                            stringResource(
                                if (granted) R.string.settings_state_granted else R.string.settings_state_denied,
                            ),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (!granted) {
                        TextButton(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    ContextCompat.startActivity(
                                        context,
                                        PermissionState.notificationSettingsIntent(context),
                                        null,
                                    )
                                    viewModel.refreshPermissions()
                                }
                            },
                        ) { Text(stringResource(R.string.settings_notifications_action)) }
                    }

                    when (state.exactAlarmState) {
                        ExactAlarmState.GRANTED -> Text(
                            text = stringResource(R.string.settings_state_not_needed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        ExactAlarmState.DENIED -> Column {
                            NoticeBar(
                                text = stringResource(R.string.settings_exact_alarm_denied_note),
                                icon = Icons.Filled.Warning,
                                tone = NoticeTone.WARNING,
                            )
                            TextButton(
                                onClick = {
                                    ContextCompat.startActivity(
                                        context,
                                        PermissionState.exactAlarmSettingsIntent(context),
                                        null,
                                    )
                                    viewModel.refreshPermissions()
                                },
                            ) { Text(stringResource(R.string.settings_exact_alarm_action)) }
                        }

                        ExactAlarmState.NOT_APPLICABLE -> Text(
                            text = stringResource(R.string.settings_state_not_needed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = viewModel::sendTestNotification) {
                        Icon(Icons.Filled.Notifications, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.settings_test_notification))
                    }
                    Spacer(Modifier.height(8.dp))
                    NoticeBar(
                        text = stringResource(R.string.settings_reminder_limitations),
                        icon = Icons.Filled.Info,
                        tone = NoticeTone.INFO,
                    )
                }

                // --- Dane ---
                SettingsSection(stringResource(R.string.settings_section_data)) {
                    state.stats?.let { stats ->
                        Text(
                            text = stringResource(
                                R.string.settings_stats,
                                stats.items,
                                stats.barcodeNames,
                                stats.shoppingPending,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = stringResource(
                                R.string.settings_photos,
                                stats.photoCount,
                                PlFormat.formatFileSize(stats.photoBytes),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(
                                R.string.settings_database_size,
                                PlFormat.formatFileSize(stats.databaseBytes),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    OutlinedButton(
                        onClick = {
                            csvLauncher.launch(
                                "${context.getString(R.string.csv_suggested_filename)}-" +
                                    "${PlFormat.timestampForFile()}.csv",
                            )
                        },
                    ) {
                        Icon(Icons.Filled.ShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.settings_export_csv))
                    }
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.backup_export_title), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                backupExportLauncher.launch(
                                    "${context.getString(R.string.backup_suggested_filename)}-" +
                                        PlFormat.timestampForFile() + ".zip",
                                )
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.settings_export_backup))
                        }
                        OutlinedButton(
                            onClick = { backupImportLauncher.launch(arrayOf("application/zip", "*/*")) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.settings_import_backup))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.backup_export_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = viewModel::refreshStats) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.retry))
                    }
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { eraseDialogOpen = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.settings_erase_all))
                    }
                    Text(
                        text = stringResource(R.string.settings_erase_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // --- O aplikacji ---
                SettingsSection(stringResource(R.string.settings_section_about)) {
                    TextButton(onClick = onOpenAbout) { Text(stringResource(R.string.about_title)) }
                    TextButton(onClick = onOpenPrivacy) { Text(stringResource(R.string.settings_privacy)) }
                    TextButton(onClick = onOpenLicenses) { Text(stringResource(R.string.settings_licenses)) }
                    TextButton(onClick = viewModel::repeatOnboarding) {
                        Text(stringResource(R.string.settings_repeat_onboarding))
                    }
                    Text(
                        text = stringResource(
                            R.string.settings_version,
                            BuildConfig.VERSION_NAME,
                            BuildConfig.VERSION_CODE,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    NoticeBar(
                        text = stringResource(R.string.settings_offline_note),
                        icon = Icons.Filled.Check,
                        tone = NoticeTone.INFO,
                    )
                }
            }
        }

        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }

    if (timeDialogOpen) {
        ReminderTimeDialog(
            initialHour = state.settings.reminderHourOfDay,
            initialMinute = state.settings.reminderMinute,
            onDismiss = { timeDialogOpen = false },
            onConfirm = { hour, minute ->
                viewModel.setReminderTime(hour, minute)
                timeDialogOpen = false
            },
        )
    }

    if (eraseDialogOpen) {
        ConfirmDialog(
            title = stringResource(R.string.settings_erase_title),
            body = stringResource(R.string.settings_erase_body),
            confirmLabel = stringResource(R.string.settings_erase_confirm),
            destructive = true,
            onConfirm = {
                eraseDialogOpen = false
                viewModel.eraseAllData()
            },
            onDismiss = { eraseDialogOpen = false },
        )
    }

    state.inspect?.let { inspect ->
        BackupInspectDialog(
            manifest = inspect.manifest,
            onDismiss = viewModel::dismissInspect,
            onChoose = viewModel::restoreBackup,
        )
    }
}

/**
 * Podgląd kopii przed importem i wybór trybu odtworzenia.
 *
 * Użytkownik zawsze widzi, co jest w pliku, i świadomie wybiera zastąpienie albo dołączenie.
 */
@Composable
private fun BackupInspectDialog(
    manifest: BackupManifestData,
    onDismiss: () -> Unit,
    onChoose: (RestoreMode) -> Unit,
) {
    val created = if (manifest.createdAtEpochMillis > 0) {
        PlFormat.formatDateTime(manifest.createdAtEpochMillis)
    } else {
        stringResource(R.string.backup_warning_timestamp)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_import_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(
                        R.string.backup_inspect_found,
                        created,
                        manifest.formatVersion,
                        manifest.counts["items"] ?: 0,
                        manifest.counts["photos"] ?: 0,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.backup_import_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.backup_mode_title), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.backup_mode_replace), style = MaterialTheme.typography.labelLarge)
                Text(
                    text = stringResource(R.string.backup_mode_replace_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.backup_mode_merge), style = MaterialTheme.typography.labelLarge)
                Text(
                    text = stringResource(R.string.backup_mode_merge_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = { onChoose(RestoreMode.REPLACE_ALL) }) {
                Text(stringResource(R.string.backup_mode_replace))
            }
        },
        dismissButton = {
            Column {
                OutlinedButton(onClick = { onChoose(RestoreMode.MERGE) }) {
                    Text(stringResource(R.string.backup_mode_merge))
                }
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) { content() }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
