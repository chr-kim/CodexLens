package com.codexlens

import android.Manifest
import android.content.ContentValues
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
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import com.codexlens.ui.theme.CodexLensTheme
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.math.abs
import kotlin.math.max
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.codexlens.data.model.ReadingNote
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.codexlens.data.model.AppDatabase


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
            val inputImage =
                InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
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
 * ML Kit 로컬 번역기 통합 헬퍼 클래스
 *
 * - 첫 사용 시 반드시 .prepare()로 모델 다운로드(오프라인 사용 가능)
 * - 이후 translate()로 텍스트 번역
 * - 리소스 해제 시 .close()
 */
class MlkitTranslatorHelper(
    context: Context,
    sourceLang: String = TranslateLanguage.ENGLISH,
    targetLang: String = TranslateLanguage.KOREAN
) {
    private val translatorOptions = TranslatorOptions.Builder()
        .setSourceLanguage(sourceLang)
        .setTargetLanguage(targetLang)
        .build()
    private val translator: Translator = Translation.getClient(translatorOptions)
    private var modelReady: Boolean = false
    
    /**
     * 번역 모델 다운로드 및 준비 (최초 한 번만 필요)
     * - 이미 설치되어있다면 즉시 완료
     * @param onReady: 준비 완료 콜백 (success = true/false)
     */
    fun prepare(onReady: (Boolean) -> Unit) {
        val conditions = DownloadConditions.Builder().requireWifi().build()
        if (modelReady) {
            onReady(true)
            return
        }
        translator.downloadModelIfNeeded(conditions)
            .addOnSuccessListener {
                modelReady = true
                onReady(true)
            }
            .addOnFailureListener { e ->
                Log.e("MlkitTranslator", "모델 다운로드 실패: $e")
                modelReady = false
                onReady(false)
            }
    }
    
    /**
     * 실제 텍스트 번역 실행.
     * - 반드시 prepare()로 사전 모델 다운로드 후 호출할 것!
     * - 번역 성공/실패는 콜백으로 반환
     */
    fun translate(
        sourceText: String,
        onResult: (success: Boolean, translated: String) -> Unit
    ) {
        if (!modelReady) {
            onResult(false, "번역 모델 준비되지 않음")
            return
        }
        translator.translate(sourceText)
            .addOnSuccessListener { translatedText ->
                onResult(true, translatedText)
            }
            .addOnFailureListener { e ->
                Log.e("MlkitTranslator", "번역 실패: $e")
                onResult(false, "번역 실패")
            }
    }
    
    /** 리소스 해제 (필수는 아니나 권장) */
    fun close() {
        translator.close()
    }
}

/**
 * 앱 전체 UI 진입점: 권한 체크, 카메라·번역·노트 저장, 목록 출력 등 담당
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    // --- 상태 변수 선언 ---
    var cameraPermissionGranted by remember { mutableStateOf(false) }
    var filePermissionGranted by remember { mutableStateOf(false) }
    val context = LocalContext.current
    
    // --- 권한 상태 초기화 (앱 시작 시 현재 권한 상태 확인) ---
    LaunchedEffect(Unit) {
        cameraPermissionGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        
        filePermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    // --- 데이터 및 ViewModel 관련 ---
    val database = remember { AppDatabase.getInstance(context) }
    val readingNoteDao = remember { database.readingNoteDao() }
    val viewModel: CodexLensViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = CodexLensViewModelFactory(context, readingNoteDao)
    )
    
    // --- UI 상태 관련 ---
    val translateState = viewModel.translateState.collectAsState()
    var selectedBox by remember { mutableStateOf<TextBox?>(null) }
    var showTranslation by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("Codex Lens") }) },
        content = { innerPadding ->
            Surface(modifier = Modifier.padding(innerPadding)) {
                // ★★★ 개선된 권한 체크 로직 ★★★
                if (!cameraPermissionGranted) {
                    // 1단계: 카메라 권한 요청
                    CameraPermissionHandler(
                        onPermissionGranted = { cameraPermissionGranted = true },
                        onPermissionDenied = {
                            Toast.makeText(
                                context,
                                "카메라 권한이 필요합니다.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                } else if (!filePermissionGranted) {
                    // 2단계: 파일 권한 요청 (카메라 권한이 있을 때만)
                    FilePermissionHandler(
                        onGranted = { filePermissionGranted = true },
                        onDenied = {
                            Toast.makeText(
                                context,
                                "파일(저장소) 권한이 필요합니다.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                } else {
                    // 3단계: 모든 권한이 있을 때 메인 화면 표시
                    CameraPreview(
                        modifier = Modifier.fillMaxSize(),
                        onBoxTap = { box ->
                            selectedBox = box
                            showTranslation = true
                            viewModel.onBoxSelected(box)
                        }
                    )
                }
            }
        }
    )
    
    // 번역 결과를 보여주는 AlertDialog
    if (showTranslation && selectedBox != null) {
        AlertDialog(
            onDismissRequest = { showTranslation = false },
            title = { Text("번역 결과") },
            text = {
                when (val state = translateState.value) {
                    is TranslateUiState.Loading -> Text("번역 중…")
                    is TranslateUiState.Error -> Text(state.message)
                    is TranslateUiState.Success -> Text(state.translated)
                    else -> Text("")
                }
            },
            confirmButton = {
                // ✅ '노트 저장' 버튼을 명확하게 배치
                val canSave = translateState.value is TranslateUiState.Success
                TextButton(
                    enabled = canSave,
                    onClick = {
                        if (canSave) {
                            val note = ReadingNote(
                                originalText = selectedBox?.text.orEmpty(),
                                translatedText = (translateState.value as TranslateUiState.Success).translated,
                                timestamp = System.currentTimeMillis()
                            )
                            viewModel.saveNote(note)
                            // 저장 안내 토스트 (context 필요시 추가)
                            Toast.makeText(context, "노트가 저장되었습니다.", Toast.LENGTH_SHORT).show()
                        }
                        showTranslation = false
                    }
                ) { Text("노트 저장") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showTranslation = false }
                ) { Text("닫기") }
            }
        )
    }
    val noteList = viewModel.noteList.collectAsState()
    
    LaunchedEffect(Unit) {
        viewModel.loadAllNotes() // 앱 시작 시 최초 목록 불러오기
    }
    
    Text(
        text = "저장된 독서 노트",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
    )
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
        content = {
            items(noteList.value) { note ->
                Card(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "원문: ${note.originalText}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "번역: ${note.translatedText}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "저장 시각: ${
                                java.text.SimpleDateFormat("yy-MM-dd HH:mm")
                                    .format(java.util.Date(note.timestamp))
                            }",
                            style = MaterialTheme.typography.labelSmall, color = Color.Gray
                        )
                    }
                }
            }
        }
    )
    
}


/**
 * CameraX 기반 카메라 프리뷰 (상태 관리 및 로직의 중심)
 */
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onBoxTap: (TextBox) -> Unit
) {
    val mockTextBlocks = remember {
        listOf(
            "Hello, world!" to Rect(100, 300, 800, 400),
            "This is Codex Lens." to Rect(100, 450, 900, 550)
        )
    }
    val context = LocalContext.current
    val previewView = remember(context) {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }
    Box(modifier = modifier) {
        CameraPreviewWithOverlay(
            textBlocks = mockTextBlocks, // 추후 ocrBlocks 등으로 교체
            previewView = previewView,
            imageProxy = null,
            onProxyConsumed = { /* Not used for mock */ },
            onBoxTap = onBoxTap
        )
    }
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
    imageProxy: Any?, // mock용
    onProxyConsumed: () -> Unit,
    boxDelayMillis: Long = 800,
    onBoxTap: (TextBox) -> Unit
) {
    val density = LocalDensity.current.density
    val visibleBoxes = remember(textBlocks) {
        textBlocks.map { (text, rect) -> TextBox(text, RectF(rect)) }
    }
    val handleTap: (Offset) -> Unit = { offset ->
        for (box in visibleBoxes.asReversed()) {
            if (isPointInsideBox(offset, box.rect)) {
                onBoxTap(box)
                break
            }
        }
    }
    Box(modifier = modifier) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(visibleBoxes) {
                    detectTapGestures { tapOffset -> handleTap(tapOffset) }
                }
        ) {
            visibleBoxes.forEach { textBox ->
                drawRect(
                    color = Color.Red,
                    topLeft = Offset(textBox.rect.left, textBox.rect.top),
                    size = androidx.compose.ui.geometry.Size(
                        textBox.rect.width(), textBox.rect.height()
                    ),
                    style = Stroke(width = 4f)
                )
                val textPaint = Paint().asFrameworkPaint().apply {
                    color = android.graphics.Color.WHITE
                    style = android.graphics.Paint.Style.FILL
                    textSize = 48f * density
                    setShadowLayer(2f, 1f, 1f, android.graphics.Color.BLACK)
                }
                val textBounds = android.graphics.Rect()
                textPaint.getTextBounds(textBox.text, 0, textBox.text.length, textBounds)
                val textX =
                    textBox.rect.left + (textBox.rect.width() / 2) - (textBounds.width() / 2)
                val textY =
                    textBox.rect.top + (textBox.rect.height() / 2) + (textBounds.height() / 2)
                drawContext.canvas.nativeCanvas.drawText(
                    textBox.text,
                    textX,
                    textY,
                    textPaint
                )
            }
        }
    }
}


//data class TextBox(val text: String, val rect: RectF)


// 터치 hit test 유틸
fun isPointInsideBox(point: Offset, box: RectF): Boolean {
    return point.x in box.left .. box.right && point.y in box.top .. box.bottom
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
    
    val imageWidth =
        if (rotationDegrees == 90f || rotationDegrees == 270f) imageProxy.height else imageProxy.width
    val imageHeight =
        if (rotationDegrees == 90f || rotationDegrees == 270f) imageProxy.width else imageProxy.height
    
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
 * 카메라 권한 요청 및 승인 핸들러
 * @param onPermissionGranted 성공 콜백
 * @param onPermissionDenied 실패 콜백
 */
@Composable
fun CameraPermissionHandler(
    onPermissionGranted: () -> Unit,
    onPermissionDenied: () -> Unit
) {
    val context = LocalContext.current
    var cameraPermissionRequested by remember { mutableStateOf(false) }
    
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onPermissionGranted()
        } else {
            onPermissionDenied()
        }
    }
    
    LaunchedEffect(Unit) {
        if (!cameraPermissionRequested) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            cameraPermissionRequested = true
        }
    }
}

/**
 * 파일(저장소) 권한 요청 및 승인 핸들러
 * @param onGranted 권한 승인 콜백
 * @param onDenied 거부 콜백
 */
@Composable
fun FilePermissionHandler(
    onGranted: () -> Unit,
    onDenied: () -> Unit
) {
    val context = LocalContext.current
    var filePermissionRequested by remember { mutableStateOf(false) }
    
    // Android 13(API 33) 이상에서는 READ_MEDIA_IMAGES로, 이하에서는 READ_EXTERNAL_STORAGE로 분기
    val filePermission = if (android.os.Build.VERSION.SDK_INT >= 33) {
        // 노트 저장이 텍스트 파일만 대상일 경우, 추가 권한 요청 불필요할 수 있지만,
        // 만약 MediaStore에 저장(Download 경로 등)이라면 아래 권한 사용
        // 실제 이미지, 비디오 권한은 필요 없다면 무시!
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    
    val filePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onGranted() else onDenied()
    }
    
    LaunchedEffect(Unit) {
        if (!filePermissionRequested) {
            filePermissionLauncher.launch(filePermission)
            filePermissionRequested = true
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

@Composable
fun SaveNoteButton(data: String) {
    val context = LocalContext.current
    Button(onClick = {
        saveToFile(context, "note.txt", data.toByteArray())
    }) {
        Text("저장")
    }
}


fun saveToFile(context: Context, fileName: String, content: ByteArray) {
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
        put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
        put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/YourAppFolder")
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
    uri?.let {
        resolver.openOutputStream(it)?.use { it.write(content) }
    }
}


//@Preview(showBackground = true)
@androidx.compose.ui.tooling.preview.Preview
@Composable
fun MainScreenPreview() {
    CodexLensTheme {
        MainScreen()
    }
}

