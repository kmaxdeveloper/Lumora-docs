package uz.kmax.documents.presentation.scanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.os.Bundle
import android.util.Log
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.kmax.base.fragment.BaseFragmentNV
import uz.kmax.documents.databinding.FragmentCropEditorBinding
import uz.kmax.documents.ui.DocumentCropOverlay
import uz.kmax.documents.utils.BitmapOrientationHelper
import java.io.File

/**
 * Interactive Crop Editor.
 * Displays the upright EXIF-rotated image and allows manual adjustment of the 4 document corners.
 * Operates purely in DISPLAY-SPACE coordinates.
 */
class CropEditorFragment : BaseFragmentNV<FragmentCropEditorBinding>(FragmentCropEditorBinding::inflate) {

    private var displayBitmap: Bitmap? = null
    private var initCorners: FloatArray? = null
    private var initOrigW: Int = 0
    private var initOrigH: Int = 0

    private val cropOverlay: DocumentCropOverlay get() = binding.cropOverlay

    override fun onViewCreated() {
        val imagePath = arguments?.getString("imagePath")
        initCorners = arguments?.getFloatArray("corners")
        initOrigW = arguments?.getInt("originalWidth") ?: 0
        initOrigH = arguments?.getInt("originalHeight") ?: 0

        binding.btnBack.setOnClickListener { navController.navigateUp() }
        binding.btnReset.setOnClickListener { resetToFullBounds() }
        binding.btnApply.setOnClickListener { applyCrop() }

        if (imagePath != null) {
            loadImage(imagePath)
        }
    }

    private fun loadImage(path: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val file = File(path)
                    if (!file.exists()) error("File not found: $path")

                    val rotation = BitmapOrientationHelper.readExifRotation(path)
                    val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
                    val raw = BitmapFactory.decodeFile(path, opts) ?: error("Decode failed")
                    BitmapOrientationHelper.rotateBitmap(raw, rotation)
                }
            }

            result.onSuccess { bitmap ->
                displayBitmap = bitmap
                binding.ivOriginal.setImageBitmap(bitmap)
                binding.ivOriginal.post { setupOverlay() }
            }.onFailure { e ->
                Log.e("CropEditor", "Failed to load image", e)
                navController.navigateUp()
            }
        }
    }

    private fun setupOverlay() {
        val bmp = displayBitmap ?: return
        val viewW = binding.ivOriginal.width.toFloat()
        val viewH = binding.ivOriginal.height.toFloat()

        if (viewW <= 0f || viewH <= 0f) {
            binding.ivOriginal.post { setupOverlay() }
            return
        }

        val bmpW = bmp.width.toFloat()
        val bmpH = bmp.height.toFloat()

        val scale = minOf(viewW / bmpW, viewH / bmpH)
        val dx = (viewW - bmpW * scale) / 2f
        val dy = (viewH - bmpH * scale) / 2f

        val corners = initCorners
        if (corners != null && corners.size == 8 && initOrigW > 0 && initOrigH > 0) {
            val scaleX = bmpW / initOrigW.toFloat()
            val scaleY = bmpH / initOrigH.toFloat()

            val viewCorners = mutableListOf<PointF>()
            for (i in corners.indices step 2) {
                val bX = corners[i] * scaleX
                val bY = corners[i + 1] * scaleY
                viewCorners.add(PointF(bX * scale + dx, bY * scale + dy))
            }
            cropOverlay.setCorners(viewCorners)
        } else {
            resetToFullBounds()
        }
    }

    private fun resetToFullBounds() {
        val bmp = displayBitmap ?: return
        val viewW = binding.ivOriginal.width.toFloat()
        val viewH = binding.ivOriginal.height.toFloat()
        if (viewW <= 0f || viewH <= 0f) return

        val bmpW = bmp.width.toFloat()
        val bmpH = bmp.height.toFloat()

        val scale = minOf(viewW / bmpW, viewH / bmpH)
        val dx = (viewW - bmpW * scale) / 2f
        val dy = (viewH - bmpH * scale) / 2f

        cropOverlay.setCorners(listOf(
            PointF(dx, dy),
            PointF(dx + bmpW * scale, dy),
            PointF(dx + bmpW * scale, dy + bmpH * scale),
            PointF(dx, dy + bmpH * scale)
        ))
    }

    private fun applyCrop() {
        val bmp = displayBitmap ?: return
        val viewW = binding.ivOriginal.width.toFloat()
        val viewH = binding.ivOriginal.height.toFloat()
        val bmpW = bmp.width.toFloat()
        val bmpH = bmp.height.toFloat()

        val scale = minOf(viewW / bmpW, viewH / bmpH)
        val dx = (viewW - bmpW * scale) / 2f
        val dy = (viewH - bmpH * scale) / 2f

        // Convert view pixels -> display bitmap pixels
        val bitmapCorners = cropOverlay.getCorners().flatMap { p ->
            val bX = ((p.x - dx) / scale).coerceIn(0f, bmpW)
            val bY = ((p.y - dy) / scale).coerceIn(0f, bmpH)
            listOf(bX, bY)
        }.toFloatArray()

        setFragmentResult("crop_request", bundleOf(
            "corners" to bitmapCorners,
            "width" to bmp.width,
            "height" to bmp.height
        ))
        navController.navigateUp()
    }

    override fun onDestroyView() {
        binding.ivOriginal.setImageDrawable(null)
        displayBitmap?.recycle()
        displayBitmap = null
        super.onDestroyView()
    }
}
