package com.codexlens

import android.Manifest
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import com.codexlens.ui.theme.CodexLensTheme
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.delay
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object OCRConstants {
    const val TEXT_SIMILARITY_THRESHOLD = 0.7   // 텍스트 유사도 기준(0.6~0.8)
    const val BOX_POSITION_THRESHOLD = 40       // 박스 좌표 변화 임계값(픽셀)
    const val BOX_DELAY_MILLIS = 800L           // 바운딩박스 표시 시간(ms)
    const val SMOOTHING_ALPHA = 0.4f            // 위치 스무딩 계수
    const val TEXT_SIZE_DP = 48f                // 오버레이 텍스트 크기(dp)
    const val MIN_BOX_SPACING = 3f              // 바운딩박스 간 최소 간격(픽셀)
    const val TEXT_VERTICAL_OFFSET = 8f         // 텍스트 세로 오프셋(픽셀)
}

/**
 * MainActivity - 앱 진입점
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CodexLensTheme {
                MainScreen()
            }
        }
    }
}

/**
 * CameraX + ML Kit 텍스트 인식 분석기
 */
class CustomAnalyzer(
    private val onTextDetected: (List<Pair<String, Rect>>, ImageProxy) -> Unit
) : ImageAnalysis.Analyzer {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    
    @androidx.camera.core.ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            recognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    val result = mutableListOf<Pair<String, Rect>>()
                    for (block in visionText.textBlocks) {
                        for (line in block.lines) {
                            result.add(line.text to (line.boundingBox ?: Rect()))
                        }
                    }
                    onTextDetected(result, imageProxy)
                }
                .addOnFailureListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
}

/**
 * Codex Lens 메인 화면
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    var permissionGranted by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("Codex Lens") }) },
        content = { innerPadding ->
            Surface(modifier = Modifier.padding(innerPadding)) {
                if (permissionGranted) {
                    CameraPreview(modifier = Modifier.fillMaxSize())
                } else {
                    CameraPermissionHandler(
                        onPermissionGranted = { permissionGranted = true },
                        onPermissionDenied = {
                            Toast.makeText(
                                context,
                                "카메라 권한이 필요합니다.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                }
            }
        }
    )
}

/**
 * CameraX 기반 카메라 프리뷰 (상태 관리 및 로직의 중심)
 */
@Composable
fun CameraPreview(modifier: Modifier = Modifier) {
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val context = LocalContext.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val displayedTextBlocks = remember { mutableStateOf(emptyList<Pair<String, Rect>>()) }
    var imageProxyForCoords by remember { mutableStateOf<ImageProxy?>(null) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    
    DisposableEffect(Unit) { onDispose { cameraExecutor.shutdown() } }
    
    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }
    
    val onTextDetectedCallback = rememberUpdatedState { newBlocks: List<Pair<String, Rect>>, proxy: ImageProxy ->
        imageProxyForCoords?.close()
        imageProxyForCoords = proxy
        displayedTextBlocks.value = newBlocks
    }
    
    val imageAnalysis = remember {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(
                    cameraExecutor,
                    CustomAnalyzer { blocks, proxy ->
                        onTextDetectedCallback.value(blocks, proxy)
                    }
                )
            }
    }
    
    LaunchedEffect(cameraProviderFuture) {
        val cameraProvider = cameraProviderFuture.get()
        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )
        } catch (exc: Exception) {
            Log.e("CameraPreview", "카메라 바인딩 실패", exc)
        }
    }
    
    CameraPreviewWithOverlay(
        modifier = modifier,
        textBlocks = displayedTextBlocks.value,
        previewView = previewView,
        imageProxy = imageProxyForCoords,
        onProxyConsumed = { imageProxyForCoords = null }
    )
}

/**
 * 바운딩박스 데이터 클래스 (위치 정보 포함)
 */
data class TextBox(
    val text: String,
    val rect: RectF,
    val adjustedRect: RectF = rect
)

/**
 * 바운딩박스 중첩 방지 알고리즘 적용
 */
fun adjustOverlappingBoxes(boxes: List<TextBox>): List<TextBox> {
    if (boxes.isEmpty()) return emptyList()
    
    val adjustedBoxes = boxes.toMutableList()
    val spacing = OCRConstants.MIN_BOX_SPACING
    
    // Y 좌표 기준으로 정렬 (위에서 아래로)
    adjustedBoxes.sortBy { it.rect.top }
    
    for (i in 0 until adjustedBoxes.size - 1) {
        val current = adjustedBoxes[i]
        val next = adjustedBoxes[i + 1]
        
        // 수직 중첩 검사
        if (current.rect.bottom + spacing > next.rect.top) {
            val overlap = (current.rect.bottom + spacing) - next.rect.top
            
            // 다음 박스를 아래로 이동
            val newRect = RectF(
                next.rect.left,
                next.rect.top + overlap,
                next.rect.right,
                next.rect.bottom + overlap
            )
            
            adjustedBoxes[i + 1] = next.copy(adjustedRect = newRect)
        }
    }
    
    return adjustedBoxes
}

/**
 * 카메라 프리뷰 위에 바운딩박스 및 텍스트 오버레이
 * - 박스/텍스트 유사성 기반 유지 + 위치 스무딩, 표시 딜레이 반영
 * - 텍스트 위치 정확도 개선 및 바운딩박스 중첩 방지
 */
@Composable
fun CameraPreviewWithOverlay(
    modifier: Modifier = Modifier,
    textBlocks: List<Pair<String, Rect>>,
    previewView: PreviewView,
    imageProxy: ImageProxy?,
    onProxyConsumed: () -> Unit,
    boxDelayMillis: Long = OCRConstants.BOX_DELAY_MILLIS
) {
    val density = LocalDensity.current
    var visibleBoxes by remember { mutableStateOf<List<TextBox>>(emptyList()) }
    var lastUpdate by remember { mutableLongStateOf(System.currentTimeMillis()) }
    
    LaunchedEffect(textBlocks) {
        val now = System.currentTimeMillis()
        
        if (textBlocks.isNotEmpty()) {
            val newBoxes = textBlocks.map { (text, rect) ->
                TextBox(text, RectF(rect))
            }
            
            visibleBoxes = if (visibleBoxes.isEmpty()) {
                adjustOverlappingBoxes(newBoxes)
            } else {
                val matchedBoxes = newBoxes.map { newBox ->
                    val match = visibleBoxes.maxByOrNull { oldBox ->
                        val sim = calculateTextSimilarity(oldBox.text, newBox.text)
                        val boxSim = if (isBoxSimilar(oldBox.rect.toRect(), newBox.rect.toRect(), OCRConstants.BOX_POSITION_THRESHOLD)) 1 else 0
                        if (sim > OCRConstants.TEXT_SIMILARITY_THRESHOLD && boxSim == 1) sim + boxSim else 0.0
                    }
                    
                    if (match != null) {
                        TextBox(
                            newBox.text,
                            smoothRect(match.rect, newBox.rect, alpha = OCRConstants.SMOOTHING_ALPHA)
                        )
                    } else {
                        newBox
                    }
                }
                adjustOverlappingBoxes(matchedBoxes)
            }
            lastUpdate = now
        } else {
            val thisRequest = lastUpdate
            delay(boxDelayMillis)
            if (lastUpdate == thisRequest) {
                visibleBoxes = emptyList()
            }
        }
    }
    
    Box(modifier = modifier) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )
        
        imageProxy?.let { proxy ->
            val textPaint = remember {
                Paint().asFrameworkPaint().apply {
                    color = android.graphics.Color.WHITE
                    style = android.graphics.Paint.Style.FILL
                    setShadowLayer(2f, 1f, 1f, android.graphics.Color.BLACK) // 텍스트 가독성 개선
                }
            }
            
            LaunchedEffect(density) {
                textPaint.textSize = OCRConstants.TEXT_SIZE_DP * density.density
            }
            
            Canvas(modifier = Modifier.fillMaxSize()) {
                visibleBoxes.forEach { textBox ->
                    val mappedRect = transformBoundingBox(
                        sourceRect = textBox.adjustedRect.toRect(),
                        imageProxy = proxy,
                        previewView = previewView
                    )
                    
                    // 바운딩박스 그리기
                    drawRect(
                        color = Color.Red,
                        topLeft = Offset(mappedRect.left, mappedRect.top),
                        size = androidx.compose.ui.geometry.Size(mappedRect.width(), mappedRect.height()),
                        style = Stroke(width = 2f)
                    )
                    
                    // 텍스트 측정을 위한 임시 Rect
                    val textBounds = android.graphics.Rect()
                    textPaint.getTextBounds(textBox.text, 0, textBox.text.length, textBounds)
                    
                    // 텍스트 위치 계산 (바운딩박스 상단 중앙에 정확히 배치)
                    val textX = mappedRect.left + (mappedRect.width() / 2) - (textBounds.width() / 2)
                    val textY = mappedRect.top - OCRConstants.TEXT_VERTICAL_OFFSET - textBounds.bottom
                    
                    drawContext.canvas.nativeCanvas.drawText(
                        textBox.text,
                        textX,
                        textY,
                        textPaint
                    )
                }
            }
        }
        
        LaunchedEffect(imageProxy) {
            imageProxy?.close()
            onProxyConsumed()
        }
    }
}

/**
 * RectF를 Rect로 변환하는 확장 함수
 */
fun RectF.toRect(): Rect =
    Rect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())

/**
 * ML Kit의 좌표를 화면 프리뷰 좌표로 변환합니다. (회전 및 비율 고려)
 */
fun transformBoundingBox(
    sourceRect: Rect,
    imageProxy: ImageProxy,
    previewView: PreviewView
): RectF {
    val matrix = Matrix()
    val rotationDegrees = imageProxy.imageInfo.rotationDegrees.toFloat()
    
    val imageWidth = if (rotationDegrees == 90f || rotationDegrees == 270f) imageProxy.height else imageProxy.width
    val imageHeight = if (rotationDegrees == 90f || rotationDegrees == 270f) imageProxy.width else imageProxy.height
    
    val previewWidth = previewView.width.toFloat()
    val previewHeight = previewView.height.toFloat()
    
    val scaleX = previewWidth / imageWidth
    val scaleY = previewHeight / imageHeight
    val scale = max(scaleX, scaleY)
    
    matrix.postScale(scale, scale)
    
    val scaledWidth = imageWidth * scale
    val scaledHeight = imageHeight * scale
    val dx = (previewWidth - scaledWidth) / 2
    val dy = (previewHeight - scaledHeight) / 2
    matrix.postTranslate(dx, dy)
    
    val targetRectF = RectF(sourceRect)
    matrix.mapRect(targetRectF)
    
    return targetRectF
}

/**
 * 카메라 권한 요청 처리 컴포저블
 */
@Composable
fun CameraPermissionHandler(
    onPermissionGranted: () -> Unit,
    onPermissionDenied: () -> Unit
) {
    val context = LocalContext.current
    var permissionRequested by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onPermissionGranted()
        } else {
            onPermissionDenied()
        }
    }
    
    LaunchedEffect(Unit) {
        if (!permissionRequested) {
            launcher.launch(Manifest.permission.CAMERA)
            permissionRequested = true
        }
    }
}

/**
 * 두 텍스트 사이의 유사도(Jaccard) 계산
 */
fun calculateTextSimilarity(text1: String, text2: String): Double {
    if (text1.isBlank() || text2.isBlank()) return 0.0
    val words1 = text1.split(Regex("\\s+")).toSet()
    val words2 = text2.split(Regex("\\s+")).toSet()
    val intersection = words1.intersect(words2).size
    val union = words1.union(words2).size
    return if (union == 0) 0.0 else intersection.toDouble() / union.toDouble()
}

/**
 * 두 박스의 픽셀 유사성 비교
 */
fun isBoxSimilar(a: Rect, b: Rect, threshold: Int = 50): Boolean =
    (abs(a.left - b.left) < threshold &&
        abs(a.top - b.top) < threshold &&
        abs(a.right - b.right) < threshold &&
        abs(a.bottom - b.bottom) < threshold)

/**
 * 박스 좌표 스무딩(Moving Average 적용)
 */
fun smoothRect(old: RectF, new: RectF, alpha: Float = 0.4f): RectF =
    RectF(
        old.left + (new.left - old.left) * alpha,
        old.top + (new.top - old.top) * alpha,
        old.right + (new.right - old.right) * alpha,
        old.bottom + (new.bottom - old.bottom) * alpha
    )
