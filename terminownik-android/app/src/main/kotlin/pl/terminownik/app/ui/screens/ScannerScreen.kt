package pl.terminownik.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pl.terminownik.app.AppContainer
import pl.terminownik.app.R
import pl.terminownik.app.domain.DateSource
import pl.terminownik.app.domain.DateType
import pl.terminownik.app.domain.DayPrecision
import pl.terminownik.app.domain.ExpiryDate
import pl.terminownik.app.domain.PlFormat
import pl.terminownik.app.domain.Section
import pl.terminownik.app.scan.BarcodeInterpretation
import pl.terminownik.app.scan.BarcodeInterpreter
import pl.terminownik.app.scan.MlKitBarcodeAnalyzer
import pl.terminownik.app.scan.ScanMessage
import pl.terminownik.app.scan.ScannedBarcode
import pl.terminownik.app.notifications.PermissionState
import pl.terminownik.app.ui.components.NoticeBar
import pl.terminownik.app.ui.components.NoticeTone

data class ScanUiState(
    val result: BarcodeInterpretation? = null,
    val cameraDenied: Boolean = false,
    val cameraMissing: Boolean = false,
)

/**
 * Skaner kodów kreskowych i 2D.
 *
 * Interpretacja jest domenowa: EAN/UPC nigdy nie daje terminu ważności, a data z GS1
 * (AI 17) trafia do formularza jako propozycja WYMAGAJĄCA potwierdzenia.
 * Nazwa produktu pochodzi wyłącznie z lokalnego słownika kod → ostatnio wpisana nazwa.
 */
class ScannerViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    /** Analizator klatek – działa wyłącznie na urządzeniu (ML Kit bundled, bez sieci). */
    val analyzer = MlKitBarcodeAnalyzer { raw -> onBarcode(raw) }

    private var lastValue: String? = null
    private var lastValueAt: Long = 0L

    private fun onBarcode(raw: ScannedBarcode) {
        val now = System.currentTimeMillis()
        // Debounce: ten sam kod nie jest przetwarzany wielokrotnie przy jednym najechaniu.
        if (raw.rawValue == lastValue && now - lastValueAt < 3_000) return
        lastValue = raw.rawValue
        lastValueAt = now
        viewModelScope.launch {
            val normalized = raw.rawValue.filter { it.isDigit() }.ifBlank { raw.rawValue.trim() }
            val known = container.pantryRepository.lookupBarcodeName(normalized)
            val interpretation = BarcodeInterpreter.interpret(
                scanned = raw,
                knownName = known?.name,
                knownUnit = known?.unit,
            )
            _state.value = _state.value.copy(result = interpretation)
        }
    }

    fun reset() {
        lastValue = null
        _state.value = _state.value.copy(result = null)
    }

    fun setCameraDenied(denied: Boolean) {
        _state.value = _state.value.copy(cameraDenied = denied)
    }

    fun setCameraMissing(missing: Boolean) {
        _state.value = _state.value.copy(cameraMissing = missing)
    }
}

fun scannerViewModelFactory(container: AppContainer) = viewModelFactory {
    initializer { ScannerViewModel(container) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    container: AppContainer,
    section: Section,
    onUseResult: (BarcodeInterpretation) -> Unit,
    onManual: () -> Unit,
    onOcr: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: ScannerViewModel = viewModel(factory = scannerViewModelFactory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()

    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var permissionAsked by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        cameraGranted = granted
        permissionAsked = true
        viewModel.setCameraDenied(!granted)
    }

    LaunchedEffect(Unit) {
        if (!cameraGranted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(stringResource(R.string.scanner_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )

            Box(Modifier.fillMaxWidth().height(300.dp)) {
                if (cameraGranted) {
                    CameraPreview(
                        analyzer = viewModel.analyzer,
                        onError = { viewModel.setCameraMissing(true) },
                    )
                } else {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxSize()) {
                        Column(
                            Modifier.fillMaxSize().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                Icons.Filled.CameraAlt,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.scanner_permission_title),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = stringResource(
                                    if (permissionAsked) {
                                        R.string.scanner_permission_denied_body
                                    } else {
                                        R.string.scanner_permission_body
                                    },
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                                Text(stringResource(R.string.scanner_permission_action))
                            }
                            if (permissionAsked) {
                                Spacer(Modifier.height(6.dp))
                                OutlinedButton(
                                    onClick = {
                                        ContextCompat.startActivity(
                                            context,
                                            PermissionState.notificationSettingsIntent(context),
                                            null,
                                        )
                                    },
                                ) { Text(stringResource(R.string.scanner_permission_settings)) }
                            }
                        }
                    }
                }
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (state.cameraMissing) {
                    NoticeBar(
                        text = stringResource(R.string.scanner_no_camera),
                        icon = Icons.Filled.Warning,
                        tone = NoticeTone.ERROR,
                    )
                }
                if (state.cameraDenied) {
                    NoticeBar(
                        text = stringResource(R.string.scanner_permission_denied_body),
                        icon = Icons.Filled.Warning,
                        tone = NoticeTone.WARNING,
                    )
                }
                NoticeBar(
                    text = stringResource(R.string.scanner_hint),
                    icon = Icons.Filled.QrCodeScanner,
                    tone = NoticeTone.INFO,
                )

                val result = state.result
                if (result != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                text = stringResource(R.string.scanner_result_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = stringResource(R.string.scanner_code_label, result.barcode),
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                text = stringResource(R.string.scanner_format_label, result.formatName),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (!result.validProductCode) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = stringResource(R.string.scanner_msg_invalid_check_digit),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            result.knownName?.let { name ->
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = stringResource(R.string.scanner_known_name, name),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            result.proposedExpiryEpochDay?.let { epochDay ->
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = stringResource(
                                        R.string.scanner_gs1_expiry,
                                        PlFormat.formatExpiry(
                                            ExpiryDate(
                                                epochDay = epochDay,
                                                dayPrecision = result.proposedPrecision ?: DayPrecision.EXACT,
                                                type = DateType.UNSPECIFIED,
                                                source = DateSource.GS1_DATAMATRIX,
                                                confirmed = false,
                                            ),
                                        ),
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = stringResource(R.string.scanner_msg_ok),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            result.gs1?.let { gs1 ->
                                gs1.gtin?.let {
                                    Text(
                                        text = stringResource(R.string.scanner_gs1_gtin, it),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                gs1.lot?.let {
                                    Text(
                                        text = stringResource(R.string.scanner_gs1_lot, it),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                gs1.serial?.let {
                                    Text(
                                        text = stringResource(R.string.scanner_gs1_serial, it),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                gs1.netWeightKg?.let {
                                    Text(
                                        text = stringResource(R.string.scanner_gs1_weight, PlFormat.formatQuantity(it)),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                gs1.volumeLitres?.let {
                                    Text(
                                        text = stringResource(R.string.scanner_gs1_volume, PlFormat.formatQuantity(it)),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                gs1.count?.let {
                                    Text(
                                        text = stringResource(R.string.scanner_gs1_count, PlFormat.formatQuantity(it)),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                if (gs1.unrecognized.isNotEmpty()) {
                                    Text(
                                        text = stringResource(
                                            R.string.scanner_gs1_unrecognized,
                                            gs1.unrecognized.joinToString(", "),
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            result.messageCode?.let { code ->
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = if (code == ScanMessage.GS1_PARSE_ERROR) {
                                        stringResource(
                                            scanMessageText(code),
                                            result.gs1?.errors?.joinToString(", ").orEmpty(),
                                        )
                                    } else {
                                        stringResource(scanMessageText(code))
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { onUseResult(result) }) {
                                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.scanner_action_use))
                                }
                                OutlinedButton(onClick = viewModel::reset) {
                                    Icon(
                                        Icons.Filled.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.scanner_action_rescan))
                                }
                            }
                        }
                    }
                }

                NoticeBar(
                    text = stringResource(R.string.disclaimer_barcodes),
                    icon = Icons.Filled.Info,
                    tone = NoticeTone.INFO,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onManual) {
                        Text(stringResource(R.string.scanner_action_manual))
                    }
                    OutlinedButton(onClick = onOcr) {
                        Icon(Icons.Filled.TextFields, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.scanner_action_ocr))
                    }
                }
                Text(
                    text = sectionLabel(section),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun sectionLabel(section: Section): String = stringResource(
    when (section) {
        Section.PANTRY -> R.string.section_pantry
        Section.MEDICINE -> R.string.section_medicine
    },
)

/** Podgląd CameraX z analizatorem kodów. */
@Composable
private fun CameraPreview(
    analyzer: MlKitBarcodeAnalyzer,
    onError: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { java.util.concurrent.Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose { executor.shutdown() }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            PreviewView(ctx).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        },
        update = { previewView ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener(
                {
                    val provider = runCatching { future.get() }.getOrNull()
                    if (provider == null) {
                        onError()
                        return@addListener
                    }
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { it.setAnalyzer(executor, analyzer) }
                    runCatching {
                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis,
                        )
                    }.onFailure { onError() }
                },
                ContextCompat.getMainExecutor(context),
            )
        },
    )
}

/** Kody komunikatów skanera → zasoby tekstowe. */
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
