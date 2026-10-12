package pl.terminownik.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import pl.terminownik.app.AppContainer
import pl.terminownik.app.R
import pl.terminownik.app.domain.DateType
import pl.terminownik.app.domain.ItemStatus
import pl.terminownik.app.domain.PlFormat
import pl.terminownik.app.domain.toExpiryDate
import pl.terminownik.app.domain.OcrIssue
import pl.terminownik.app.domain.Section
import pl.terminownik.app.scan.ScanMessage
import pl.terminownik.app.ui.components.ConfirmDialog
import pl.terminownik.app.ui.components.ExpiryDateDialog
import pl.terminownik.app.ui.components.LoadingState
import pl.terminownik.app.ui.components.NoticeBar
import pl.terminownik.app.ui.components.NoticeTone

/** Formularz pozycji: dodawanie nowej i edycja istniejącej. */
@OptIn(ExperimentalMaterial3Api::class)
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ItemEditScreen(
    container: AppContainer,
    section: Section,
    itemId: Long,
    onDone: () -> Unit,
    onScan: () -> Unit,
    onOcr: () -> Unit,
) {
    val viewModel: ItemEditViewModel = viewModel(
        key = "edit-${section.name}-$itemId",
        factory = itemEditViewModelFactory(container, section, itemId),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val savedSnackbar = remember { SnackbarHostState() }
    var datePickerOpen by remember { mutableStateOf(false) }
    var deleteDialogOpen by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) viewModel.importPhoto(uri)
    }

    // Wynik skanowania / OCR przekazany z poprzedniego ekranu.
    LaunchedEffect(Unit) {
        container.consumePendingScan()?.let { viewModel.applyScan(it) }
        container.consumePendingOcr()?.let { candidate ->
            viewModel.applyOcr(
                expiry = candidate.toExpiryDate(),
                issueCode = candidate.issueCode,
            )
        }
    }

    LaunchedEffect(state.saved) {
        if (state.saved) {
            viewModel.consumeSaved()
            savedSnackbar.showSnackbar(context.getString(R.string.form_saved))
            onDone()
        }
    }

    if (state.loading) {
        LoadingState()
        return
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            when {
                                !state.isNew -> R.string.edit_title_edit
                                section == Section.PANTRY -> R.string.edit_title_new_pantry
                                else -> R.string.edit_title_new_medicine
                            },
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close))
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { deleteDialogOpen = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete_item))
                        }
                    }
                    IconButton(onClick = viewModel::save) {
                        Icon(Icons.Filled.Save, contentDescription = stringResource(R.string.save))
                    }
                },
            )

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.itemMissing) {
                    NoticeBar(
                        text = stringResource(R.string.form_item_not_found),
                        icon = Icons.Filled.Info,
                        tone = NoticeTone.WARNING,
                    )
                }

                // --- Sekcja -------------------------------------------------
                Text(stringResource(R.string.field_section), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.section == Section.PANTRY,
                        onClick = { viewModel.onSectionChange(Section.PANTRY) },
                        label = { Text(stringResource(R.string.section_pantry)) },
                        leadingIcon = { Icon(Icons.Filled.Restaurant, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                    FilterChip(
                        selected = state.section == Section.MEDICINE,
                        onClick = { viewModel.onSectionChange(Section.MEDICINE) },
                        label = { Text(stringResource(R.string.section_medicine)) },
                        leadingIcon = {
                            Icon(Icons.Filled.LocalPharmacy, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                    )
                }

                // --- Nazwa --------------------------------------------------
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::onNameChange,
                    label = { Text(stringResource(R.string.field_name)) },
                    placeholder = { Text(stringResource(R.string.field_name_hint)) },
                    singleLine = true,
                    isError = state.errors.nameEmpty || state.errors.nameTooLong,
                    supportingText = when {
                        state.errors.nameEmpty -> {
                            { Text(stringResource(R.string.error_name_required)) }
                        }

                        state.errors.nameTooLong -> {
                            { Text(stringResource(R.string.error_name_too_long)) }
                        }

                        else -> null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                state.knownName?.let { known ->
                    Text(
                        stringResource(R.string.dictionary_hint),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilterChip(
                        selected = state.name == known,
                        onClick = viewModel::applyKnownName,
                        label = { Text(stringResource(R.string.dictionary_use_name, known)) },
                    )
                }

                // --- Ilość i jednostka --------------------------------------
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.quantityText,
                        onValueChange = viewModel::onQuantityChange,
                        label = { Text(stringResource(R.string.field_quantity)) },
                        placeholder = { Text(stringResource(R.string.field_quantity_hint)) },
                        singleLine = true,
                        isError = state.errors.quantityInvalid,
                        supportingText = if (state.errors.quantityInvalid) {
                            { Text(stringResource(R.string.error_quantity_invalid)) }
                        } else {
                            null
                        },
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = state.unit,
                        onValueChange = viewModel::onUnitChange,
                        label = { Text(stringResource(R.string.field_unit)) },
                        placeholder = { Text(stringResource(R.string.field_unit_hint)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }

                OutlinedTextField(
                    value = state.storageLocation,
                    onValueChange = viewModel::onLocationChange,
                    label = { Text(stringResource(R.string.field_location)) },
                    placeholder = { Text(stringResource(R.string.field_location_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.batchCode,
                    onValueChange = viewModel::onBatchChange,
                    label = { Text(stringResource(R.string.field_batch)) },
                    singleLine = true,
                    supportingText = { Text(stringResource(R.string.field_batch_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )

                HorizontalDivider()

                // --- Termin ważności ----------------------------------------
                Text(stringResource(R.string.field_expiry), style = MaterialTheme.typography.titleSmall)
                val expiry = state.expiry
                if (expiry != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(PlFormat.formatExpiry(expiry), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = stringResource(R.string.field_date_source),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = when (expiry.source) {
                                    DateSource.MANUAL -> stringResource(R.string.source_manual)
                                    DateSource.GS1_DATAMATRIX -> stringResource(R.string.source_gs1)
                                    DateSource.OCR -> stringResource(R.string.source_ocr)
                                    DateSource.BACKUP_IMPORT -> stringResource(R.string.source_import)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = if (expiry.confirmed) {
                                    stringResource(R.string.date_confirmed)
                                } else {
                                    stringResource(R.string.date_needs_confirmation)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (expiry.confirmed) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                            )
                            if (expiry.dayPrecision == pl.terminownik.app.domain.DayPrecision.MONTH_ONLY) {
                                Spacer(Modifier.height(4.dp))
                                NoticeBar(
                                    text = stringResource(R.string.disclaimer_month_only),
                                    icon = Icons.Filled.Info,
                                    tone = NoticeTone.WARNING,
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { datePickerOpen = true }) {
                            Text(stringResource(R.string.date_change_action))
                        }
                        if (!expiry.confirmed) {
                            Button(onClick = viewModel::confirmExpiry) {
                                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.confirm_date_action))
                            }
                        }
                        TextButton(onClick = viewModel::clearExpiry) {
                            Text(stringResource(R.string.date_clear_action))
                        }
                    }
                    Text(
                        text = stringResource(R.string.field_date_type),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = expiry.type == DateType.USE_BY,
                            onClick = { viewModel.onDateTypeChange(DateType.USE_BY) },
                            label = { Text(stringResource(R.string.date_type_use_by)) },
                        )
                        FilterChip(
                            selected = expiry.type == DateType.BEST_BEFORE,
                            onClick = { viewModel.onDateTypeChange(DateType.BEST_BEFORE) },
                            label = { Text(stringResource(R.string.date_type_best_before)) },
                        )
                        FilterChip(
                            selected = expiry.type == DateType.UNSPECIFIED,
                            onClick = { viewModel.onDateTypeChange(DateType.UNSPECIFIED) },
                            label = { Text(stringResource(R.string.date_type_unspecified)) },
                        )
                    }
                    Text(
                        stringResource(R.string.date_type_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    NoticeBar(
                        text = stringResource(R.string.date_not_set),
                        icon = Icons.Filled.Info,
                        tone = NoticeTone.INFO,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { datePickerOpen = true }) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.date_set_action))
                        }
                        OutlinedButton(onClick = onOcr) {
                            Icon(Icons.Filled.TextFields, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.action_ocr))
                        }
                    }
                }

                state.ocrIssue?.let { issue ->
                    NoticeBar(
                        text = stringResource(ocrIssueText(issue)),
                        icon = Icons.Filled.Info,
                        tone = NoticeTone.WARNING,
                    )
                }
                state.scanMessage?.let { code ->
                    NoticeBar(
                        text = stringResource(scanMessageText(code)),
                        icon = Icons.Filled.QrCodeScanner,
                        tone = NoticeTone.INFO,
                    )
                }

                HorizontalDivider()

                // --- Kod kreskowy -------------------------------------------
                Text(stringResource(R.string.field_barcode), style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = state.barcode,
                    onValueChange = viewModel::onBarcodeChange,
                    label = { Text(stringResource(R.string.field_barcode)) },
                    singleLine = true,
                    isError = state.errors.barcodeInvalid != null,
                    supportingText = {
                        Text(
                            state.errors.barcodeInvalid?.let { stringResource(barcodeErrorText(it)) }
                                ?: stringResource(R.string.disclaimer_barcodes),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                state.knownName?.let {
                    TextButton(onClick = viewModel::forgetKnownName) {
                        Text(stringResource(R.string.dictionary_forget))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onScan) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_scan))
                    }
                }
                if (state.dictionaryForgotten) {
                    NoticeBar(
                        text = stringResource(R.string.dictionary_forgotten),
                        icon = Icons.Filled.Check,
                        tone = NoticeTone.INFO,
                    )
                }
                NoticeBar(
                    text = stringResource(R.string.disclaimer_barcodes),
                    icon = Icons.Filled.Info,
                    tone = NoticeTone.INFO,
                )

                HorizontalDivider()

                // --- Zdjęcie -------------------------------------------------
                Text(stringResource(R.string.field_photo), style = MaterialTheme.typography.titleSmall)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier.size(84.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (state.photoFile != null) {
                            AsyncImage(
                                model = state.photoFile,
                                contentDescription = stringResource(R.string.field_photo),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Filled.Image,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    Column {
                        OutlinedButton(
                            onClick = {
                                photoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                        ) {
                            Text(
                                stringResource(
                                    if (state.photoFile == null) R.string.photo_add else R.string.photo_replace,
                                ),
                            )
                        }
                        if (state.photoFile != null) {
                            TextButton(onClick = viewModel::removePhoto) {
                                Text(stringResource(R.string.photo_remove))
                            }
                        }
                    }
                }
                if (state.photoImportFailed) {
                    NoticeBar(
                        text = stringResource(R.string.photo_import_failed),
                        icon = Icons.Filled.Info,
                        tone = NoticeTone.ERROR,
                    )
                }

                HorizontalDivider()

                // --- Przypomnienie -------------------------------------------
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.field_reminder), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.settings_lead_days, state.leadDays),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = state.reminderEnabled, onCheckedChange = viewModel::onReminderChange)
                }

                // --- Notatka --------------------------------------------------
                OutlinedTextField(
                    value = state.note,
                    onValueChange = viewModel::onNoteChange,
                    label = { Text(stringResource(R.string.field_note)) },
                    placeholder = { Text(stringResource(R.string.field_note_hint)) },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )

                // --- Stan (tylko edycja) --------------------------------------
                if (!state.isNew) {
                    HorizontalDivider()
                    Text(stringResource(R.string.form_status), style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.status == ItemStatus.ACTIVE,
                            onClick = viewModel::markActive,
                            label = { Text(stringResource(R.string.item_status_active)) },
                        )
                        FilterChip(
                            selected = state.status == ItemStatus.USED,
                            onClick = viewModel::markUsed,
                            label = { Text(stringResource(R.string.item_status_used)) },
                        )
                        FilterChip(
                            selected = state.status == ItemStatus.DISCARDED,
                            onClick = viewModel::markDiscarded,
                            label = { Text(stringResource(R.string.item_status_discarded)) },
                        )
                    }
                }

                HorizontalDivider()
                NoticeBar(
                    text = stringResource(R.string.disclaimer_food),
                    icon = Icons.Filled.Restaurant,
                    tone = NoticeTone.INFO,
                )
                NoticeBar(
                    text = stringResource(R.string.disclaimer_medicine),
                    icon = Icons.Filled.LocalPharmacy,
                    tone = NoticeTone.INFO,
                )

                Spacer(Modifier.height(8.dp))
                Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.save))
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    SnackbarHost(hostState = savedSnackbar, modifier = Modifier.align(Alignment.BottomCenter))

    if (datePickerOpen) {
        ExpiryDateDialog(
            initial = state.expiry,
            source = DateSource.MANUAL,
            onDismiss = { datePickerOpen = false },
            onConfirm = { picked ->
                viewModel.setExpiry(picked)
                datePickerOpen = false
            },
        )
    }

    if (deleteDialogOpen) {
        ConfirmDialog(
            title = stringResource(R.string.form_delete_confirm_title),
            body = stringResource(R.string.form_delete_confirm_body),
            confirmLabel = stringResource(R.string.delete),
            destructive = true,
            onConfirm = {
                deleteDialogOpen = false
                viewModel.delete()
            },
            onDismiss = { deleteDialogOpen = false },
        )
    }
}

/** Mapowanie kodów komunikatów skanera na zasoby tekstowe. */
private fun scanMessageText(code: String): Int = when (code) {
    ScanMessage.OK -> R.string.scanner_msg_ok
    ScanMessage.INVALID_CHECK_DIGIT -> R.string.scanner_msg_invalid_check_digit
    ScanMessage.EAN_NO_EXPIRY -> R.string.scanner_msg_ean_no_expiry
    ScanMessage.GS1_NO_DATE -> R.string.scanner_msg_gs1_no_date
    ScanMessage.GS1_DATE_AMBIGUOUS -> R.string.scanner_msg_gs1_date_ambiguous
    ScanMessage.GS1_PARSE_ERROR -> R.string.scanner_msg_gs1_parse_error
    ScanMessage.CAMERA_DENIED -> R.string.scanner_permission_denied_body
    ScanMessage.NO_CAMERA -> R.string.scanner_no_camera
    else -> R.string.scanner_msg_ean_no_expiry
}

/** Mapowanie kodów problemów OCR na zasoby tekstowe. */
private fun ocrIssueText(code: String): Int = when (code) {
    OcrIssue.AMBIGUOUS_ORDER -> R.string.ocr_issue_ambiguous
    OcrIssue.ORDER_MMDD -> R.string.ocr_issue_order
    OcrIssue.MONTH_ONLY -> R.string.ocr_issue_month_only
    OcrIssue.TWO_DIGIT_YEAR -> R.string.ocr_issue_year
    OcrIssue.IMPLAUSIBLE_YEAR -> R.string.ocr_issue_year
    OcrIssue.NO_LABEL -> R.string.ocr_issue_no_label
    else -> R.string.ocr_failed
}

/** Kody walidacji kodu kreskowego na zasoby tekstowe. */
private fun barcodeErrorText(key: String): Int = when (key) {
    "error_barcode_checksum" -> R.string.scanner_msg_invalid_check_digit
    "error_barcode_length" -> R.string.error_barcode_length
    "error_barcode_invalid_chars" -> R.string.error_barcode_invalid_chars
    else -> R.string.error_barcode_invalid_chars
}
