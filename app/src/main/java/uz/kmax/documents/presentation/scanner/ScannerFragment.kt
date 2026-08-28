package uz.kmax.documents.presentation.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.graphics.PointF
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import kotlinx.coroutines.launch
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import uz.kmax.base.fragment.BaseFragmentNV
import uz.kmax.documents.LumoraApplication
import uz.kmax.documents.R
import uz.kmax.documents.databinding.FragmentScannerBinding
import uz.kmax.documents.domain.scanner.DocumentDetectionResult
import uz.kmax.documents.domain.scanner.DocumentDetector
import uz.kmax.documents.domain.scanner.DocumentStabilityEvaluator
import uz.kmax.documents.domain.scanner.StabilityResult
import uz.kmax.documents.ui.DocumentDetectionOverlay
import uz.kmax.documents.utils.ViewModelFactory
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ScannerFragment : BaseFragmentNV<FragmentScannerBinding>(FragmentScannerBinding::inflate) {

    private val viewModel: ScannerViewModel by viewModels()
    private val sessionViewModel: ScanSessionViewModel by activityViewModels {
        val app = requireActivity().application as LumoraApplication
        ViewModelFactory(app.documentRepository, billingRepository = app.billingRepository)
    }
    private val detector = DocumentDetector()
    private val stabilityEvaluator = DocumentStabilityEvaluator()

    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var flashMode = ImageCapture.FLASH_MODE_OFF

    private lateinit var cameraExecutor: ExecutorService

    private val detectionOverlay: DocumentDetectionOverlay 
        get() = binding.detectionOverlay as DocumentDetectionOverlay

    private val pickMultipleMedia = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) {
            sessionViewModel.importImages(requireContext(), uris)
            navController.navigate(R.id.reviewFragment)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startCamera()
            binding.layoutPermission.visibility = View.GONE
        } else {
            binding.layoutPermission.visibility = View.VISIBLE
        }
    }

    override fun onViewCreated() {
        cameraExecutor = Executors.newSingleThreadExecutor()

        setupListeners()
        setupEdgeToEdge()
        observeViewModel()

        checkPermission()
    }

    private fun setupEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutBottom) { v, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.updatePadding(bottom = navBars.bottom + 24.dpToPx())
            insets
        }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    private fun checkPermission() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                startCamera()
                binding.layoutPermission.visibility = View.GONE
            }
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                binding.layoutPermission.visibility = View.VISIBLE
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun setupListeners() {
        binding.btnClose.setOnClickListener {
            sessionViewModel.clear()
            navController.popBackStack(R.id.homeFragment, false)
        }

        binding.btnFlash.setOnClickListener {
            toggleFlash()
        }

        binding.btnCapture.setOnClickListener {
            if (sessionViewModel.canAddMorePages()) {
                captureImage()
            } else {
                showPremiumLimitDialog()
            }
        }

        binding.btnGallery.setOnClickListener {
            pickMultipleMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        binding.btnRequestPermission.setOnClickListener {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        binding.tvPageCount.setOnClickListener {
            if (sessionViewModel.pages.value.isNotEmpty()) {
                navController.navigate(R.id.reviewFragment)
            }
        }
    }

    private fun showPremiumLimitDialog() {
        uz.kmax.documents.utils.DialogUtils.showLumoraDialog(
            requireContext(),
            title = getString(R.string.premium_limit_pages_title),
            message = getString(R.string.premium_limit_pages_msg),
            iconRes = R.drawable.ic_premium,
            primaryButtonText = getString(R.string.premium_btn_upgrade),
            secondaryButtonText = getString(R.string.scanner_btn_reset),
            onPrimaryClick = {
                navController.navigate(R.id.premiumFragment)
            }
        )
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is ScannerUiState.Idle -> {
                            binding.btnCapture.isEnabled = true
                        }
                        is ScannerUiState.Capturing -> {
                            binding.btnCapture.isEnabled = false
                        }
                        is ScannerUiState.CaptureSuccess -> {
                            binding.btnCapture.isEnabled = true
                            navigateToPreview(state.file)
                        }
                        is ScannerUiState.Error -> {
                            binding.btnCapture.isEnabled = true
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                            viewModel.resetState()
                        }
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.detectionState.collect { result ->
                    val isStable = viewModel.stabilityState.value == StabilityResult.STABLE
                    detectionOverlay.setDetectionResult(result, isStable)
                    
                    if (result?.detected == true) {
                        binding.viewGuide.animate().alpha(0f).setDuration(200).start()
                    } else {
                        binding.viewGuide.animate().alpha(1f).setDuration(200).start()
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.stabilityState.collect { stability ->
                    updateStabilityUi(stability)
                    detectionOverlay.setDetectionResult(viewModel.detectionState.value, stability == StabilityResult.STABLE)
                    if (stability == StabilityResult.STABLE && viewModel.uiState.value == ScannerUiState.Idle) {
                        if (sessionViewModel.canAddMorePages()) {
                            captureImage()
                        } else {
                            // Don't auto-show dialog during scan, just stop auto-capture
                            // User will get dialog when clicking capture button manually
                            viewModel.onStabilityEvaluated(StabilityResult.NOT_DETECTED)
                        }
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                kotlinx.coroutines.flow.combine(
                    sessionViewModel.pages,
                    sessionViewModel.isPremium
                ) { pages, isPremium ->
                    pages to isPremium
                }.collect { (pages, isPremium) ->
                    binding.tvPageCount.visibility = if (pages.isNotEmpty()) View.VISIBLE else View.GONE
                    
                    if (isPremium) {
                        binding.tvPageCount.text = pages.size.toString()
                    } else {
                        binding.tvPageCount.text = getString(R.string.scanner_page_limit_indicator, pages.size, 10)
                    }
                }
            }
        }
    }

    private fun updateStabilityUi(stability: StabilityResult) {
        when (stability) {
            StabilityResult.NOT_DETECTED -> {
                binding.tvStatus.animate().alpha(0f).withEndAction { binding.tvStatus.visibility = View.GONE }.start()
            }
            StabilityResult.TOO_SMALL -> {
                binding.tvStatus.visibility = View.VISIBLE
                binding.tvStatus.alpha = 1f
                binding.tvStatus.setText(R.string.scanner_status_too_small)
            }
            StabilityResult.STABILIZING, StabilityResult.MOVING -> {
                binding.tvStatus.visibility = View.VISIBLE
                binding.tvStatus.alpha = 1f
                binding.tvStatus.setText(R.string.scanner_status_hold)
            }
            StabilityResult.STABLE -> {
                binding.tvStatus.visibility = View.VISIBLE
                binding.tvStatus.alpha = 1f
                binding.tvStatus.setText(R.string.scanner_status_capturing)
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.surfaceProvider = binding.viewFinder.surfaceProvider
                }

            imageCapture = ImageCapture.Builder()
                .setFlashMode(flashMode)
                .build()

            imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImageProxy(imageProxy)
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    viewLifecycleOwner, cameraSelector, preview, imageCapture, imageAnalyzer
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun processImageProxy(imageProxy: ImageProxy) {
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val bitmap = imageProxy.toBitmap()
        val mat = Mat(bitmap.height, bitmap.width, CvType.CV_8UC4)
        
        try {
            Utils.bitmapToMat(bitmap, mat)
            
            val result = detector.detect(mat)
            
            val stability = stabilityEvaluator.evaluate(result)
            viewModel.onStabilityEvaluated(stability)
            
            val transformedResult = transformResult(result, rotationDegrees)
            viewModel.onDocumentDetected(result, transformedResult)
        } catch (e: Exception) {
            Log.e(TAG, "Image analysis failed", e)
        } finally {
            mat.release()
            bitmap.recycle()
            imageProxy.close()
        }
    }

    private fun transformResult(result: DocumentDetectionResult, rotationDegrees: Int): DocumentDetectionResult {
        if (!result.detected) {
            previousCorners = null
            return result
        }

        val previewWidth = binding.viewFinder.width.toFloat()
        val previewHeight = binding.viewFinder.height.toFloat()
        
        if (previewWidth == 0f || previewHeight == 0f) return result

        val rotatedCorners = if (rotationDegrees != 0) {
            rotatePoints(result.corners, rotationDegrees, result.width.toFloat(), result.height.toFloat())
        } else {
            result.corners
        }
        
        val rotatedWidth = if (rotationDegrees % 180 != 0) result.height else result.width
        val rotatedHeight = if (rotationDegrees % 180 != 0) result.width else result.height

        val scale = maxOf(previewWidth / rotatedWidth, previewHeight / rotatedHeight)
        val scaledWidth = rotatedWidth * scale
        val scaledHeight = rotatedHeight * scale
        
        val offsetX = (previewWidth - scaledWidth) / 2f
        val offsetY = (previewHeight - scaledHeight) / 2f
        
        val finalCorners = rotatedCorners.map { p ->
            PointF(p.x * scale + offsetX, p.y * scale + offsetY)
        }
        
        val smoothedCorners = stabilizePoints(finalCorners)
        return result.copy(corners = smoothedCorners)
    }

    private fun rotatePoints(points: List<PointF>, degrees: Int, w: Float, h: Float): List<PointF> {
        return points.map { p ->
            when (degrees) {
                90 -> PointF(h - p.y, p.x)
                180 -> PointF(w - p.x, h - p.y)
                270 -> PointF(p.y, w - p.x)
                else -> p
            }
        }
    }

    private var previousCorners: List<PointF>? = null
    private val alpha = 0.4f

    private fun stabilizePoints(current: List<PointF>): List<PointF> {
        val prev = previousCorners
        if (prev == null || prev.size != current.size) {
            previousCorners = current
            return current
        }

        val stabilized = current.zip(prev).map { (curr, old) ->
            PointF(
                old.x + alpha * (curr.x - old.x),
                old.y + alpha * (curr.y - old.y)
            )
        }
        previousCorners = stabilized
        return stabilized
    }

    private fun toggleFlash() {
        flashMode = when (flashMode) {
            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
            else -> ImageCapture.FLASH_MODE_OFF
        }

        imageCapture?.flashMode = flashMode
        binding.btnFlash.setImageResource(
            if (flashMode == ImageCapture.FLASH_MODE_ON) R.drawable.ic_flash_on else R.drawable.ic_flash_off
        )
    }

    private fun captureImage() {
        val imageCapture = imageCapture ?: return
        viewModel.onCaptureStarted()

        val photoFile = File(
            requireContext().cacheDir,
            SimpleDateFormat(FILENAME_FORMAT, Locale.US)
                .format(System.currentTimeMillis()) + ".jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                    viewModel.onCaptureError(getString(R.string.scanner_error_capture))
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    viewModel.onCaptureSuccess(photoFile)
                }
            }
        )
    }

    private fun navigateToPreview(file: File) {
        val bundle = Bundle().apply {
            putString("imagePath", file.absolutePath)
            viewModel.lastRawDetection?.let { detection ->
                if (detection.detected) {
                    putFloatArray("corners", detection.corners.flatMap { listOf(it.x, it.y) }.toFloatArray())
                    putInt("originalWidth", detection.width)
                    putInt("originalHeight", detection.height)
                }
            }
        }
        navController.navigate(R.id.imagePreviewFragment, bundle)
        viewModel.resetState()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "ScannerFragment"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
    }
}
