// 파일 위치: app/src/main/java/com/codexlens/MainActivity.kt

package com.codexlens

import android.Manifest
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
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.codexlens.ui.theme.CodexLensTheme

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
 * Codex Lens 메인 화면
 * - CenterAlignedTopAppBar와 메인 콘텐츠(Content)로 구성
 * - 권한 요청 및 카메라 프리뷰 분기 처리
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
                    //카메라 권한이 허용되면, 화면 전체에 카메라 미리보기(Preview)가 표시
                    CameraPreview(modifier = Modifier.fillMaxSize())
                } else {
                    //권한 요청 UI를 띄우고, 결과에 따라 상태를 갱신하거나 안내 메시지 표시
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

//@Preview(showBackground = true)
@androidx.compose.ui.tooling.preview.Preview
@Composable
fun MainScreenPreview() {
    CodexLensTheme {
        MainScreen()
    }
}

/**
 * CameraX 기반 카메라 프리뷰 컴포저블
 * - LocalLifecycleOwner를 명시적으로 사용하여 안전성 강화
 */
@Composable
fun CameraPreview(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember {
        ProcessCameraProvider.getInstance(context) //카메라 하드웨어와의 연결을 비동기로 요청
    }
    
    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                try {
                    cameraProvider.unbindAll() //기존에 바인딩된 카메라 리소스 해제(중복 방지).
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview
                    )
                } catch (exc: Exception) {
                    Log.e("CameraPreview", "카메라 바인딩 실패", exc)
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
        modifier = modifier
    )
}

/**
 * 카메라 권한 요청 처리 컴포저블
 * - 최초 1회만 권한 요청
 * - 승인/거부 콜백 분리
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
