package pl.terminownik.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pl.terminownik.app.R
import pl.terminownik.app.domain.ExpiryBucket
import pl.terminownik.app.domain.ItemStatus
import pl.terminownik.app.domain.ItemWithExpiry
import pl.terminownik.app.domain.PlFormat

/**
 * Karta pozycji spisu.
 *
 * Pokazuje termin z jednoznacznym oznaczeniem stanu oraz przyciski:
 * zużyj / wyrzuć / przywróć, uzupełnij (nowa partia), dodaj do zakupów, usuń.
 * Każdy przycisk ma działanie – w aplikacji nie ma atrap.
 */
@Composable
fun ItemCard(
    row: ItemWithExpiry,
    onClick: () -> Unit,
    onUse: () -> Unit,
    onDiscard: () -> Unit,
    onReactivate: () -> Unit,
    onRestock: () -> Unit,
    onAddToShopping: () -> Unit,
    onDelete: () -> Unit,
    onConfirmDate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = row.item
    val assessment = row.assessment
    val archived = item.status != ItemStatus.ACTIVE
    val useLabel = stringResource(R.string.action_use)
    val discardLabel = stringResource(R.string.action_discard)
    val openLabel = stringResource(R.string.action_open_item)
    val bucketLabel = stringResource(badgeLabel(assessment.bucket))
    val expiredLabel = stringResource(R.string.badge_expired)
    val dueTodayLabel = stringResource(R.string.badge_due_today)
    val expiringLabel = stringResource(R.string.badge_expiring)
    val okLabel = stringResource(R.string.badge_ok)
    val noDateLabel = stringResource(R.string.badge_no_date)
    val unconfirmedLabel = stringResource(R.string.badge_unconfirmed)

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "$openLabel: ${item.name}. $bucketLabel"
            },
        colors = CardDefaults.cardColors(
            containerColor = if (archived) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textDecoration = if (archived) TextDecoration.LineThrough else TextDecoration.None,
                    )
                    val quantity = PlFormat.formatQuantityWithUnit(item.quantity, item.unit)
                    if (quantity.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.card_quantity, quantity),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    item.storageLocation?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = stringResource(R.string.card_location, it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    item.batchCode?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = stringResource(R.string.card_batch, it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    item.barcode?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = stringResource(R.string.card_code, it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (archived) {
                        Text(
                            text = stringResource(R.string.card_updated, PlFormat.formatDateTime(item.updatedAt)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                ExpiryBadge(row)
            }

            val expiry = item.expiryDate
            if (expiry != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.card_expiry, PlFormat.formatExpiry(expiry)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (assessment.needsConfirmation) {
                Spacer(Modifier.height(6.dp))
                NoticeBar(
                    text = stringResource(R.string.disclaimer_unconfirmed),
                    icon = Icons.Filled.Undo,
                    tone = NoticeTone.WARNING,
                )
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onConfirmDate) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_confirm_date))
                }
            }

            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (archived) {
                    TextButton(onClick = onReactivate) {
                        Icon(Icons.Filled.Undo, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_reactivate))
                    }
                } else {
                    IconButton(
                        onClick = onUse,
                        modifier = Modifier.semantics {
                            contentDescription = useLabel
                        },
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null)
                    }
                    IconButton(
                        onClick = onDiscard,
                        modifier = Modifier.semantics {
                            contentDescription = discardLabel
                        },
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                    }
                }
                var menuOpen by remember { mutableStateOf(false) }
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_restock)) },
                        leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onRestock()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_to_shopping)) },
                        leadingIcon = { Icon(Icons.Filled.ShoppingCart, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onAddToShopping()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete_item)) },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

/** Etykieta stanu terminu z kolorem (czerwony = po terminie, pomarańczowy = kończy się). */
@Composable
fun ExpiryBadge(row: ItemWithExpiry, modifier: Modifier = Modifier) {
    val assessment = row.assessment
    val (labelRes, container, content) = when (assessment.bucket) {
        ExpiryBucket.EXPIRED -> Triple(
            badgeLabel(ExpiryBucket.EXPIRED),
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
        )

        ExpiryBucket.DUE_TODAY -> Triple(
            R.string.badge_due_today,
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
        )

        ExpiryBucket.EXPIRING_SOON -> Triple(
            R.string.badge_expiring,
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onSurface,
        )

        ExpiryBucket.OK -> Triple(
            R.string.badge_ok,
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
        )

        ExpiryBucket.NO_DATE -> Triple(
            R.string.badge_no_date,
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )

        ExpiryBucket.UNCONFIRMED -> Triple(
            R.string.badge_unconfirmed,
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
    Column(horizontalAlignment = Alignment.End, modifier = modifier) {
        Surface(color = container, shape = MaterialTheme.shapes.small) {
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.labelSmall,
                color = content,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        val days = assessment.daysFromToday
        if (days != null && assessment.bucket != ExpiryBucket.UNCONFIRMED) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (days >= 0) {
                    stringResource(R.string.expiry_days_left, PlFormat.formatDayDistance(days))
                } else {
                    stringResource(R.string.expiry_days_ago, PlFormat.formatDayDistance(days))
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (row.item.status != ItemStatus.ACTIVE) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = when (row.item.status) {
                    ItemStatus.USED -> stringResource(R.string.item_status_used)
                    ItemStatus.DISCARDED -> stringResource(R.string.item_status_discarded)
                    ItemStatus.ACTIVE -> stringResource(R.string.item_status_active)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Etykieta stanu terminu – wspólna dla badge'a i opisu dla TalkBack. */
private fun badgeLabel(bucket: ExpiryBucket): Int = when (bucket) {
    ExpiryBucket.EXPIRED -> R.string.badge_expired
    ExpiryBucket.DUE_TODAY -> R.string.badge_due_today
    ExpiryBucket.EXPIRING_SOON -> R.string.badge_expiring
    ExpiryBucket.OK -> R.string.badge_ok
    ExpiryBucket.NO_DATE -> R.string.badge_no_date
    ExpiryBucket.UNCONFIRMED -> R.string.badge_unconfirmed
}
