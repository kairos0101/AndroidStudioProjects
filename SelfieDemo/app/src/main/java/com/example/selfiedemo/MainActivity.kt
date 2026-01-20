package com.example.selfiedemo

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.selfiedemo.databinding.ActivityMainBinding
import androidx.camera.lifecycle.ProcessCameraProvider
import kotlinx.coroutines.*
import androidx.camera.core.Camera
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.content.Context
import android.widget.Toast
import android.util.Log
import androidx.activity.viewModels
import androidx.camera.core.ImageAnalysis
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.mediapipe.tasks.vision.core.RunningMode
import android.Manifest

class MainActivity : AppCompatActivity() {

    // View binding
    private var _viewBinding: ActivityMainBinding? = null
    private val viewBinding: ActivityMainBinding
        get() = _viewBinding ?: throw IllegalStateException("binding absent")

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        _viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        if (!hasPermissions(baseContext)) {
            requestPermissions(
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CODE_CAMERA_PERMISSION
            )
        } else {
            setupCamera()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                launch {
                    viewModel.uiEvents.collect { uiEvent: MainViewModel.UiEvent ->
                        when (uiEvent) {
                            is MainViewModel.UiEvent.Face -> drawFaces(uiEvent.face)
                            is MainViewModel.UiEvent.Gesture -> drawGestures(uiEvent.gestures)
                        }
                    }
                }
            }
        }
    }

    private fun drawFaces(resultBundle: FaceResultBundle) {
        // Pass necessary information to OverlayView for drawing on the canvas
        viewBinding.overlayFace.setResults(
            resultBundle.result,
            resultBundle.inputImageHeight,
            resultBundle.inputImageWidth,
            RunningMode.LIVE_STREAM
        )
        // Force a redraw
        viewBinding.overlayFace.invalidate()
    }

    private fun drawGestures(resultBundle: GestureResultBundle) {
        // Pass necessary information to OverlayView for drawing on the canvas
        viewBinding.overlayGesture.setResults(
            resultBundle.results.first(),
            resultBundle.inputImageHeight,
            resultBundle.inputImageWidth,
            RunningMode.LIVE_STREAM
        )
        // Force a redraw
        viewBinding.overlayGesture.invalidate()
    }

    override fun onDestroy() {
        super.onDestroy()
        _viewBinding = null
    }

    // Camera
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null

    private fun setupCamera() {
        viewBinding.viewFinder.post {
            cameraProvider?.unbindAll()

            ProcessCameraProvider.getInstance(baseContext).let {
                it.addListener(
                    {
                        cameraProvider = it.get()

                        bindCameraUseCases()
                    },
                    Dispatchers.Main.asExecutor()
                )
            }
        }
    }

    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider
            ?: throw IllegalStateException("Camera initialization failed.")

        val cameraSelector =
            CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_FRONT).build()

        // Only using the 4:3 ratio because this is the closest to MediaPipe models
        val resolutionSelector =
            ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .build()
        val targetRotation = viewBinding.viewFinder.display.rotation

        // Preview use case.
        preview = Preview.Builder()
            .setResolutionSelector(resolutionSelector)
            .setTargetRotation(targetRotation)
            .build()

        // ImageAnalysis. Using RGBA 8888 to match how MediaPipe models work
        imageAnalysis =
            ImageAnalysis.Builder()
                .setResolutionSelector(resolutionSelector)
                .setTargetRotation(targetRotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                // The analyzer can then be assigned to the instance
                .also {
                    it.setAnalyzer(
                        // Forcing a serial executor without parallelism
                        // to avoid packets sent to MediaPipe out-of-order
                        Dispatchers.Default.limitedParallelism(1).asExecutor()
                    ) { image ->
                        if (isHelperReady)
                            viewModel.recognizeLiveStream(image)
                    }
                }

        // Must unbind the use-cases before rebinding them
        cameraProvider.unbindAll()

        try {
            // A variable number of use-cases can be passed here -
            // camera provides access to CameraControl & CameraInfo
            camera = cameraProvider.bindToLifecycle(
                this, cameraSelector, preview, imageAnalysis
            )

            // Attach the viewfinder's surface provider to preview use case
            preview?.surfaceProvider = viewBinding.viewFinder.surfaceProvider
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun hasPermissions(context: Context) = PERMISSIONS_REQUIRED.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            REQUEST_CODE_CAMERA_PERMISSION -> {
                if (PackageManager.PERMISSION_GRANTED == grantResults.getOrNull(0)) {
                    setupCamera()
                } else {
                    val messageResId =
                        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA))
                            R.string.permission_request_camera_rationale
                        else
                            R.string.permission_request_camera_message
                    Toast.makeText(baseContext, getString(messageResId), Toast.LENGTH_LONG).show()
                }
            }
            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    private var imageAnalysis: ImageAnalysis? = null

    companion object {
        private const val TAG = "MainActivity"
        // Permissions
        private val PERMISSIONS_REQUIRED = arrayOf(Manifest.permission.CAMERA)
        private const val REQUEST_CODE_CAMERA_PERMISSION = 233
    }

    private var isHelperReady = false

    override fun onResume() {
        super.onResume()
        viewModel.setupHelper(baseContext)
        isHelperReady = true
    }

    override fun onPause() {
        super.onPause()
        isHelperReady = false
        viewModel.shutdownHelper()
    }
}