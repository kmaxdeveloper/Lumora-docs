package uz.kmax.documents.presentation.scanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.core.Point
import uz.kmax.base.fragment.BaseFragmentNV
import uz.kmax.documents.LumoraApplication
import uz.kmax.documents.R
import uz.kmax.documents.databinding.FragmentImagePreviewBinding
import uz.kmax.documents.domain.scanner.PerspectiveCorrectionProcessor
import uz.kmax.documents.utils.BitmapOrientationHelper
import uz.kmax.documents.utils.ViewModelFactory
import java.io.File

/**
 * Previews the scanned image with perspective/rectangle correction applied.
 * Everything runs in DISPLAY-SPACE (EXIF-corrected upright bitmap coordinates).
 */
class ImagePreviewFragment : BaseFragmentNV<FragmentImagePreviewBinding>(FragmentImagePreviewBinding::inflate) {

    private val sessionViewModel: ScanSessionViewModel by activityViewModels {
        val app = requireActivity().application as LumoraApplication
        ViewModelFactory(app.documentRepository, billingRepository = app.billingRepository)
    }

    private val processor = PerspectiveCorrectionProcessor()

    private var processedBitmap: Bitmap? = null

    // Display-space corners: [TL_x, TL_y, TR_x, TR_y, BR_x, BR_y, BL_x, BL_y]
    private var currentCorners: FloatArray? = null
    private var currentOrigW: Int = 0
    private var currentOrigH: Int = 0
    private var isCornersFromScanner: Boolean = false

    private var imagePath: String? = null

    override fun onViewCreated() {
        imagePath = arguments?.getString("imagePath")
        currentCorners = arguments?.getFloatArray("corners")
        currentOrigW = arguments?.getInt("originalWidth") ?: 0
        currentOrigH = arguments?.getInt("originalHeight") ?: 0
        isCornersFromScanner = currentCorners != null && currentOrigW > 0

        imagePath?.let { processImage(it) }

        binding.btnBack.setOnClickListener { navController.navigateUp() }

        binding.btnEditCrop.setOnClickListener {
            val bundle = Bundle().apply {
                putString("imagePath", imagePath)
                putFloatArray("corners", currentCorners)
                putInt("originalWidth", currentOrigW)
                putInt("originalHeight", currentOrigH)
            }
            navController.navigate(R.id.cropEditorFragment, bundle)
        }

        binding.btnDone.setOnClickListener {
            processedBitmap?.let { bmp ->
                binding.progressBar.visibility = View.VISIBLE
                binding.btnDone.isEnabled = false
                sessionViewModel.addPage(requireContext(), bmp) {
                    binding.progressBar.visibility = View.GONE
                    if (!navController.popBackStack(R.id.reviewFragment, false)) {
                        navController.navigate(R.id.reviewFragment)
                    }
                }
            }
        }

        // Receive user-edited rectangular crop from CropEditorFragment
        setFragmentResultListener("crop_request") { _, bundle ->
            val newCorners = bundle.getFloatArray("corners")
            val newW = bundle.getInt("width")
            val newH = bundle.getInt("height")
            if (newCorners != null && imagePath != null && newW > 0 && newH > 0) {
                currentCorners = newCorners
                currentOrigW = newW
                currentOrigH = newH
                isCornersFromScanner = false // User manually adjusted rectangle
                processImage(imagePath!!)
            }
        }
    }

    private fun processImage(path: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnDone.isEnabled = false
        binding.btnEditCrop.isEnabled = false

        val snapCorners = currentCorners?.copyOf()
        val snapOrigW = currentOrigW
        val snapOrigH = currentOrigH
        val fromScanner = isCornersFromScanner

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val file = File(path)
                if (!file.exists()) throw Exception("File not found: $path")

                // 1. Read EXIF & load raw bitmap
                val rotation = BitmapOrientationHelper.readExifRotation(path)
                val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
                val rawBitmap = BitmapFactory.decodeFile(path, opts)
                    ?: throw Exception("Failed to decode $path")

                // 2. Rotate to upright display orientation
                val displayBitmap = BitmapOrientationHelper.rotateBitmap(rawBitmap, rotation)
                val dispW = displayBitmap.width
                val dispH = displayBitmap.height

                // 3. Map corners and crop
                var finalDisplayCorners: FloatArray? = null
                var finalResultBmp: Bitmap? = null

                if (snapCorners != null && snapCorners.size == 8 && snapOrigW > 0 && snapOrigH > 0) {
                    if (fromScanner) {
                        // Automatic camera detection (perspective unwarp)
                        val rawDisplayPoints = mutableListOf<Point>()
                        val analysisDispW = if (rotation == 90 || rotation == 270) snapOrigH else snapOrigW
                        val analysisDispH = if (rotation == 90 || rotation == 270) snapOrigW else snapOrigH
                        val scaleX = dispW.toDouble() / analysisDispW
                        val scaleY = dispH.toDouble() / analysisDispH

                        for (i in snapCorners.indices step 2) {
                            val dp = BitmapOrientationHelper.sensorToDisplay(
                                snapCorners[i].toDouble(),
                                snapCorners[i + 1].toDouble(),
                                snapOrigW,
                                snapOrigH,
                                rotation
                            )
                            val ptX = (dp.x * scaleX).coerceIn(0.0, dispW.toDouble())
                            val ptY = (dp.y * scaleY).coerceIn(0.0, dispH.toDouble())
                            rawDisplayPoints.add(Point(ptX, ptY))
                        }

                        val orderedPoints = BitmapOrientationHelper.orderDisplayCorners(rawDisplayPoints)
                        finalResultBmp = processor.process(displayBitmap, orderedPoints)
                        finalDisplayCorners = orderedPoints.flatMap { listOf(it.x.toFloat(), it.y.toFloat()) }.toFloatArray()
                    } else {
                        // Manual Rectangular Crop (Lossless & perfectly upright rectangle)
                        val scaleX = dispW.toFloat() / snapOrigW
                        val scaleY = dispH.toFloat() / snapOrigH

                        val scaledXCoords = listOf(
                            snapCorners[0] * scaleX,
                            snapCorners[2] * scaleX,
                            snapCorners[4] * scaleX,
                            snapCorners[6] * scaleX
                        )
                        val scaledYCoords = listOf(
                            snapCorners[1] * scaleY,
                            snapCorners[3] * scaleY,
                            snapCorners[5] * scaleY,
                            snapCorners[7] * scaleY
                        )

                        val minX = scaledXCoords.minOrNull()?.toInt()?.coerceIn(0, dispW - 1) ?: 0
                        val maxX = scaledXCoords.maxOrNull()?.toInt()?.coerceIn(minX + 1, dispW) ?: dispW
                        val minY = scaledYCoords.minOrNull()?.toInt()?.coerceIn(0, dispH - 1) ?: 0
                        val maxY = scaledYCoords.maxOrNull()?.toInt()?.coerceIn(minY + 1, dispH) ?: dispH

                        val cropW = (maxX - minX).coerceIn(1, dispW - minX)
                        val cropH = (maxY - minY).coerceIn(1, dispH - minY)

                        finalResultBmp = Bitmap.createBitmap(displayBitmap, minX, minY, cropW, cropH)
                        finalDisplayCorners = floatArrayOf(
                            minX.toFloat(), minY.toFloat(),
                            maxX.toFloat(), minY.toFloat(),
                            maxX.toFloat(), maxY.toFloat(),
                            minX.toFloat(), maxY.toFloat()
                        )
                    }
                }

                val finalBmp = finalResultBmp ?: displayBitmap

                withContext(Dispatchers.Main) {
                    if (finalDisplayCorners != null) {
                        currentCorners = finalDisplayCorners
                        currentOrigW = dispW
                        currentOrigH = dispH
                        isCornersFromScanner = false
                    } else {
                        currentCorners = floatArrayOf(
                            0f, 0f,
                            dispW.toFloat(), 0f,
                            dispW.toFloat(), dispH.toFloat(),
                            0f, dispH.toFloat()
                        )
                        currentOrigW = dispW
                        currentOrigH = dispH
                        isCornersFromScanner = false
                    }

                    processedBitmap = finalBmp
                    binding.ivPreview.setImageBitmap(finalBmp)
                    binding.progressBar.visibility = View.GONE
                    binding.btnDone.isEnabled = true
                    binding.btnEditCrop.isEnabled = true
                }
            } catch (e: Exception) {
                Log.e("ImagePreview", "Processing failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), getString(R.string.scanner_error_capture), Toast.LENGTH_SHORT).show()
                    binding.progressBar.visibility = View.GONE
                    navController.navigateUp()
                }
            }
        }
    }

    override fun onDestroyView() {
        binding.ivPreview.setImageDrawable(null)
        super.onDestroyView()
        processedBitmap = null
    }
}
