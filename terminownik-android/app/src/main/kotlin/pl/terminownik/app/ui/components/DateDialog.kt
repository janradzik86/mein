package pl.terminownik.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import pl.terminownik.app.R
import pl.terminownik.app.domain.DateSource
import pl.terminownik.app.domain.DateType
import pl.terminownik.app.domain.DayPrecision
import pl.terminownik.app.domain.ExpiryDate
import pl.terminownik.app.domain.PlFormat

/**
 * Dialog wyboru terminu ważności.
 *
 * Pole dnia może zostać puste – wtedy zapisujemy precyzję „tylko miesiąc”
 * (ostatni dzień miesiąca) i oznaczamy datę jako wymagającą doprecyzowania.
 * Data ustawiona ręcznie jest potwierdzona; data z OCR/GS1 pozostaje niepotwierdzona,
 * dopóki użytkownik jej nie zatwierdzi.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpiryDateDialog(
    initial: ExpiryDate?,
    source: DateSource,
    onDismiss: () -> Unit,
    onConfirm: (ExpiryDate) -> Unit,
) {
    val today = remember { LocalDate.now(PlFormat.zone()) }
    var day by remember { mutableStateOf(initial?.let { PlFormat.localDateOf(it.epochDay).dayOfMonth.toString() } ?: "") }
    var month by remember {
        mutableStateOf(initial?.let { PlFormat.localDateOf(it.epochDay).monthValue.toString() } ?: today.monthValue.toString())
    }
    var year by remember {
        mutableStateOf(initial?.let { PlFormat.localDateOf(it.epochDay).year.toString() } ?: today.year.toString())
    }
    var dateType by remember { mutableStateOf(initial?.type ?: DateType.UNSPECIFIED) }
    var error by remember { mutableStateOf<String?>(null) }

    val isManual = source == DateSource.MANUAL

    fun applyPreset(date: LocalDate, keepDay: Boolean = true) {
        year = date.year.toString()
        month = date.monthValue.toString()
        day = if (keepDay) date.dayOfMonth.toString() else ""
        error = null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.date_dialog_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = day,
                        onValueChange = { day = it.filter(Char::isDigit).take(2) },
                        label = {
                            Text(
                                if (isManual) {
                                    stringResource(R.string.date_dialog_day)
                                } else {
                                    stringResource(R.string.date_dialog_day_optional)
                                }
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = month,
                        onValueChange = { month = it.filter(Char::isDigit).take(2) },
                        label = { Text(stringResource(R.string.date_dialog_month)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = year,
                        onValueChange = { year = it.filter(Char::isDigit).take(4) },
                        label = { Text(stringResource(R.string.date_dialog_year)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1.2f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = false,
                        onClick = { applyPreset(today) },
                        label = { Text(stringResource(R.string.date_dialog_today)) },
                    )
                    FilterChip(
                        selected = false,
                        onClick = { applyPreset(today.plusDays(7)) },
                        label = { Text(stringResource(R.string.date_dialog_in_week)) },
                    )
                    FilterChip(
                        selected = false,
                        onClick = { applyPreset(today.plusMonths(1)) },
                        label = { Text(stringResource(R.string.date_dialog_in_month)) },
                    )
                    FilterChip(
                        selected = false,
                        onClick = { applyPreset(today.plusYears(1)) },
                        label = { Text(stringResource(R.string.date_dialog_in_year)) },
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.field_date_type),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = dateType == DateType.USE_BY,
                        onClick = { dateType = DateType.USE_BY },
                        label = { Text(stringResource(R.string.date_type_use_by)) },
                    )
                    FilterChip(
                        selected = dateType == DateType.BEST_BEFORE,
                        onClick = { dateType = DateType.BEST_BEFORE },
                        label = { Text(stringResource(R.string.date_type_best_before)) },
                    )
                    FilterChip(
                        selected = dateType == DateType.UNSPECIFIED,
                        onClick = { dateType = DateType.UNSPECIFIED },
                        label = { Text(stringResource(R.string.date_type_unspecified)) },
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.date_type_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val preview = buildPreview(day, month, year)
                if (preview != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(preview, style = MaterialTheme.typography.bodyMedium)
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val yearValue = year.toIntOrNull()
                    val result = buildDate(day, month, year, dateType, source, today)
                    error = when {
                        result != null -> null
                        yearValue != null && yearValue > today.year + 50 ->
                            stringResource(R.string.error_date_far_future)

                        month.toIntOrNull() != null && day.toIntOrNull() != null && yearValue != null ->
                            stringResource(R.string.error_date_invalid)

                        else -> {
                            val monthLength = runCatching {
                                LocalDate.of(
                                    yearValue ?: today.year,
                                    (month.toIntOrNull() ?: today.monthValue).coerceIn(1, 12),
                                    1,
                                ).lengthOfMonth()
                            }.getOrDefault(31)
                            stringResource(R.string.date_dialog_error, monthLength)
                        }
                    }
                    if (result != null) onConfirm(result)
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun buildDate(
    day: String,
    month: String,
    year: String,
    type: DateType,
    source: DateSource,
    today: LocalDate,
): ExpiryDate? {
    val m = month.toIntOrNull() ?: return null
    val y = year.toIntOrNull() ?: return null
    if (m !in 1..12) return null
    if (y !in 1900..(today.year + 100)) return null
    val monthLength = LocalDate.of(y, m, 1).lengthOfMonth()
    return if (day.isBlank()) {
        // Znamy tylko miesiąc: zapisujemy ostatni dzień i oznaczamy precyzję.
        ExpiryDate(
            epochDay = LocalDate.of(y, m, monthLength).toEpochDay(),
            dayPrecision = DayPrecision.MONTH_ONLY,
            type = type,
            source = source,
            confirmed = false,
        )
    } else {
        val d = day.toIntOrNull() ?: return null
        if (d !in 1..monthLength) return null
        ExpiryDate(
            epochDay = LocalDate.of(y, m, d).toEpochDay(),
            dayPrecision = DayPrecision.EXACT,
            type = type,
            source = source,
            // Wpis ręczny jest świadomą decyzją użytkownika; odczyt automatyczny wymaga potwierdzenia.
            confirmed = source == DateSource.MANUAL,
        )
    }
}

@Composable
private fun buildPreview(day: String, month: String, year: String): String? {
    val m = month.toIntOrNull() ?: return null
    val y = year.toIntOrNull() ?: return null
    if (m !in 1..12 || y !in 1900..2200) return null
    val monthLength = runCatching { LocalDate.of(y, m, 1).lengthOfMonth() }.getOrNull() ?: return null
    return if (day.isBlank()) {
        stringResource(
            R.string.date_dialog_preview_month,
            PlFormat.formatMonthOnly(LocalDate.of(y, m, monthLength).toEpochDay()),
        )
    } else {
        val d = day.toIntOrNull() ?: return null
        if (d !in 1..monthLength) return null
        stringResource(R.string.date_dialog_preview, PlFormat.formatDate(LocalDate.of(y, m, d).toEpochDay()))
    }
}
