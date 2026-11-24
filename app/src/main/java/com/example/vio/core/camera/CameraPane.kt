package com.example.vio.core.camera

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.net.Uri
import android.provider.Settings
import android.util.Range
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExtendableBuilder
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.vio.core.vision.ImageBuffer
import java.util.concurrent.Executors
import kotlin.math.max

@Composable
fun CameraPane(
    modifier: Modifier = Modifier,
    onFrame: (ImageBuffer) -> Unit,
    onCameraError: (String) -> Unit,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (!granted) onCameraError("Camera permission is not granted.")
    }
    val hasCamera = remember {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }
    if (!hasCamera) {
        MessagePane(modifier, "This device does not have a camera.")
        return
    }
    if (!hasPermission) {
        Column(
            modifier = modifier.padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                "Camera access is required to map a place or estimate a zone.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(
                onClick = { launcher.launch(Manifest.permission.CAMERA) },
                modifier = Modifier.padding(top = 12.dp),
            ) {
                Text("Grant camera access")
            }
            TextButton(onClick = { context.openAppSettings() }) {
                Text("Open app settings")
            }
        }
        return
    }
    CameraPreview(modifier = modifier, onFrame = onFrame, onCameraError = onCameraError)
}

@Composable
private fun CameraPreview(
    modifier: Modifier,
    onFrame: (ImageBuffer) -> Unit,
    onCameraError: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val frameCallback by rememberUpdatedState(onFrame)
    val errorCallback by rememberUpdatedState(onCameraError)
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            contentDescription = "Camera preview"
        }
    }
    DisposableEffect(lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        val future = ProcessCameraProvider.getInstance(context)
        var lastFrameMs = 0L
        val listener = Runnable {
            try {
                val provider = future.get()
                val fps = normalFrameRate(provider)
                provider.unbindAll()
                val bound = runCatching { bindCamera(provider, lifecycleOwner, previewView, fps, steady = true) }
                    .recoverCatching {
                        provider.unbindAll()
                        bindCamera(provider, lifecycleOwner, previewView, fps = null, steady = true)
                    }
                    .getOrElse {
                        provider.unbindAll()
                        bindCamera(provider, lifecycleOwner, previewView, fps = null, steady = false)
                    }
                bound.setAnalyzer(executor) { image ->
                    try {
                        val now = android.os.SystemClock.elapsedRealtime()
                        if (now - lastFrameMs >= 700L) {
                            val buffer = image.toUprightGrayscale()
                            lastFrameMs = now
                            if (buffer != null) frameCallback(buffer)
                        }
                    } catch (_: Exception) {
                        errorCallback("A camera frame could not be read.")
                    } finally {
                        image.close()
                    }
                }
            } catch (error: Exception) {
                errorCallback(error.message ?: "The camera is unavailable.")
            }
        }
        future.addListener(listener, ContextCompat.getMainExecutor(context))
        onDispose {
            executor.shutdown()
            runCatching {
                if (future.isDone) future.get().unbindAll()
            }
        }
    }
    AndroidView(factory = { previewView }, modifier = modifier)
}

private fun bindCamera(
    provider: ProcessCameraProvider,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    previewView: PreviewView,
    fps: Range<Int>?,
    steady: Boolean,
): ImageAnalysis {
    val previewBuilder = Preview.Builder()
    val analysisBuilder = ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
    if (steady) {
        applySteadyCapture(previewBuilder, fps)
        applySteadyCapture(analysisBuilder, fps)
    }
    val preview = previewBuilder.build().also { it.surfaceProvider = previewView.surfaceProvider }
    val analysis = analysisBuilder.build()
    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
    return analysis
}

private fun applySteadyCapture(builder: ExtendableBuilder<*>, fps: Range<Int>?) {
    val extender = Camera2Interop.Extender(builder)
    extender.setCaptureRequestOption(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
    extender.setCaptureRequestOption(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_DISABLED)
    extender.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
    extender.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
    extender.setCaptureRequestOption(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_MINIMAL)
    extender.setCaptureRequestOption(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_FAST)
    if (fps != null) {
        extender.setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fps)
    }
}

private fun normalFrameRate(provider: ProcessCameraProvider): Range<Int>? {
    val info = CameraSelector.DEFAULT_BACK_CAMERA.filter(provider.availableCameraInfos).firstOrNull() ?: return null
    val ranges = Camera2CameraInfo.from(info)
        .getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
        ?: return null
    val daylight = ranges.filter { it.lower >= 15 && it.upper >= 24 }
    if (daylight.isNotEmpty()) return daylight.minBy { it.upper }
    return ranges.maxByOrNull { it.lower }
}

private fun ImageProxy.toUprightGrayscale(): ImageBuffer? {
    val plane = planes.firstOrNull() ?: return null
    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride
    val buffer = plane.buffer
    val step = max(1, max(width, height) / 480)
    val sampledWidth = (width + step - 1) / step
    val sampledHeight = (height + step - 1) / step
    val pixels = ByteArray(sampledWidth * sampledHeight)
    var out = 0
    var y = 0
    while (y < height) {
        val row = y * rowStride
        var x = 0
        while (x < width) {
            val index = row + x * pixelStride
            pixels[out++] = if (index < buffer.limit()) buffer.get(index) else 0
            x += step
        }
        y += step
    }
    val upright = ImageBuffer(sampledWidth, sampledHeight, pixels)
    return when (((imageInfo.rotationDegrees % 360) + 360) % 360) {
        90 -> upright.rotate90()
        180 -> upright.rotate180()
        270 -> upright.rotate270()
        else -> upright
    }
}

private fun ImageBuffer.rotate90(): ImageBuffer {
    val out = ByteArray(width * height)
    for (y in 0 until height) {
        for (x in 0 until width) {
            out[x * height + (height - 1 - y)] = pixels[y * width + x]
        }
    }
    return ImageBuffer(height, width, out)
}

private fun ImageBuffer.rotate180(): ImageBuffer {
    val out = ByteArray(pixels.size)
    for (index in pixels.indices) out[pixels.size - 1 - index] = pixels[index]
    return ImageBuffer(width, height, out)
}

private fun ImageBuffer.rotate270(): ImageBuffer {
    val out = ByteArray(width * height)
    for (y in 0 until height) {
        for (x in 0 until width) {
            out[(width - 1 - x) * height + y] = pixels[y * width + x]
        }
    }
    return ImageBuffer(height, width, out)
}

@Composable
private fun MessagePane(modifier: Modifier, message: String) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun Context.openAppSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null),
    )
    startActivity(intent)
}
