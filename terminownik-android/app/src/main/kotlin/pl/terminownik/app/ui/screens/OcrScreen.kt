package pl.terminownik.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
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
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import pl.terminownik.app.AppContainer
import pl.terminownik.app.R
import pl.terminownik.app.domain.OcrDateCandidate
import pl.terminownik.app.domain.OcrIssue
import pl.terminownik.app.domain.PlFormat
import pl.terminownik.app.domain.Section
import pl.terminownik.app.domain.toExpiryDate
import pl.terminownik.app.scan.MlKitOcrAnalyzer
import pl.terminownik.app.ui.components.NoticeBar
import pl.terminownik.app.ui.components.NoticeTone

data class OcrUiState(
    val busy: Boolean = false,
    val candidates: List<OcrDateCandidate> = emptyList(),
    val linesScanned: Int = 0,
    val failureCode: String? = null,
)

/**
 * Rozpoznawanie daty ważności z etykiety.
 *
 * OCR działa modelem wbudowanym (bez sieci) i ZAWSZE zwraca propozycje do potwierdzenia:
 * niejednoznaczny zapis dostaje kod problemu, a data trafia do formularza jako niepotwierdzona,
 * więc nie utworzy przypomnienia, dopóki użytkownik jej nie zatwierdzi.
 */
class OcrViewModel : ViewModel() {

    private val _state = MutableStateFlow(OcrUiState())
    val state: StateFlow<OcrUiState> = _state.asStateFlow()

    fun recognizeBitmap(bitmap: Bitmap) {
        _state.value = OcrUiState(busy = true)
        viewModelScope.launch {
            val lines = runCatching { MlKitOcrAnalyzer.recognize(bitmap) }.getOrDefault(emptyList())
            bitmap.recycle()
            publish(lines)
        }
    }

    fun recognizeUri(context: Context, uri: Uri) {
        _state.value = OcrUiState(busy = true)
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        BitmapFactory.decodeStream(input)
                    }
                }.getOrNull()
            }
            if (bitmap == null) {
                _state.value = OcrUiState(failureCode = "ocr_error")
            } else {
                val lines = runCatching { MlKitOcrAnalyzer.recognize(bitmap) }.getOrDefault(emptyList())
                bitmap.recycle()
                publish(lines)
            }
        }
    }

    private fun publish(lines: List<String>) {
        val result = pl.terminownik.app.domain.OcrDateParser.parse(lines)
        _state.value = OcrUiState(
            busy = false,
            candidates = result.candidates,
            linesScanned = result.linesScanned,
            failureCode = if (result.candidates.isEmpty()) "ocr_not_found" else null,
        )
    }

    fun reset() {
        _state.value = OcrUiState()
    }
}

fun ocrViewModelFactory() = viewModelFactory { initializer { OcrViewModel() } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrScreenRoute(
    container: AppContainer,
    section: Section,
    onUseResult: (OcrDateCandidate) -> Unit,
    onManual: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: OcrViewModel = viewModel(factory = ocrViewModelFactory())
    val state by viewModel.state.collectAsStateWithLifecycle()

    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var permissionAsked by remember { mutableStateOf(false) }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        cameraGranted = granted
        permissionAsked = true
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) viewModel.recognizeUri(context, uri) }

    LaunchedEffect(Unit) {
        if (!cameraGranted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(stringResource(R.string.ocr_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )

            Box(Modifier.fillMaxWidth().height(280.dp)) {
                if (cameraGranted) {
                    OcrCameraPreview(imageCapture = imageCapture)
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
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(stringResource(R.string.scanner_permission_title),
                                style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                stringResource(
                                    if (permissionAsked) {
                                        R.string.ocr_permission_denied
                                    } else {
                                        R.string.ocr_hint
                                    },
                                ),
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                                Text(stringResource(R.string.scanner_permission_action))
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
                NoticeBar(
                    text = stringResource(R.string.ocr_hint),
                    icon = Icons.Filled.Info,
                    tone = NoticeTone.INFO,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (cameraGranted) {
                        Button(
                            onClick = {
                                captureAndRecognize(context, imageCapture, viewModel::recognizeBitmap) {}
                            },
                        ) {
                            Icon(
                                Icons.Filled.CameraAlt,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.ocr_action_capture))
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                    ) {
                        Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.ocr_action_pick))
                    }
                }

                if (state.busy) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.ocr_processing))
                    }
                }

                state.failureCode?.let { code ->
                    NoticeBar(
                        text = stringResource(ocrFailureText(code)),
                        icon = Icons.Filled.Info,
                        tone = NoticeTone.WARNING,
                    )
                }

                if (state.candidates.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.ocr_results_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = stringResource(R.string.ocr_results_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.candidates.forEach { candidate ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    text = stringResource(
                                        R.string.ocr_candidate,
                                        PlFormat.formatExpiry(candidate.toExpiryDate()),
                                    ),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = stringResource(R.string.ocr_source_line, candidate.sourceText),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                candidate.matchedLabel?.let { label ->
                                    Text(
                                        text = stringResource(R.string.ocr_label_found, label),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    text = stringResource(ocrConfidenceText(candidate.confidence.name)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                candidate.issueCode?.let { issue ->
                                    Spacer(Modifier.height(4.dp))
                                    NoticeBar(
                                        text = stringResource(ocrIssueText(issue)),
                                        icon = Icons.Filled.Info,
                                        tone = NoticeTone.WARNING,
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Button(onClick = { onUseResult(candidate) }) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.ocr_use_selected))
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = viewModel::reset) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.ocr_retry))
                        }
                        OutlinedButton(onClick = onManual) {
                            Text(stringResource(R.string.ocr_edit_manually))
                        }
                    }
                }

                NoticeBar(
                    text = stringResource(R.string.disclaimer_unconfirmed),
                    icon = Icons.Filled.Info,
                    tone = NoticeTone.WARNING,
                )
                Text(
                    text = stringResource(
                        when (section) {
                            Section.PANTRY -> R.string.section_pantry
                            Section.MEDICINE -> R.string.section_medicine
                        },
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Zdjęcie z ImageCapture → dekodowanie → OCR na urządzeniu. */
private fun captureAndRecognize(
    context: Context,
    imageCapture: ImageCapture,
    onBitmap: (Bitmap) -> Unit,
    onFailure: () -> Unit,
) {
    val file = File(context.cacheDir, "ocr-capture-${System.currentTimeMillis()}.jpg")
    val options = ImageCapture.OutputFileOptions.Builder(file).build()
    imageCapture.takePicture(
        options,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                file.delete()
                if (bitmap == null) onFailure() else onBitmap(bitmap)
            }

            override fun onError(exception: ImageCaptureException) {
                file.delete()
                onFailure()
            }
        },
    )
}

@Composable
private fun OcrCameraPreview(imageCapture: ImageCapture) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

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
                    val provider = runCatching { future.get() }.getOrNull() ?: return@addListener
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    runCatching {
                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageCapture,
                        )
                    }
                },
                ContextCompat.getMainExecutor(context),
            )
        },
    )
}

private fun ocrFailureText(code: String): Int = when (code) {
    "ocr_not_found" -> R.string.ocr_no_results
    else -> R.string.ocr_failed
}

private fun ocrConfidenceText(confidence: String): Int = when (confidence) {
    "HIGH" -> R.string.ocr_confidence_high
    "MEDIUM" -> R.string.ocr_confidence_medium
    else -> R.string.ocr_confidence_low
}

private fun ocrIssueText(issue: String): Int = when (issue) {
    OcrIssue.AMBIGUOUS_ORDER -> R.string.ocr_issue_ambiguous
    OcrIssue.ORDER_MMDD -> R.string.ocr_issue_order
    OcrIssue.MONTH_ONLY -> R.string.ocr_issue_month_only
    OcrIssue.TWO_DIGIT_YEAR -> R.string.ocr_issue_year
    OcrIssue.IMPLAUSIBLE_YEAR -> R.string.ocr_issue_year
    OcrIssue.NO_LABEL -> R.string.ocr_issue_no_label
    else -> R.string.ocr_failed
}
