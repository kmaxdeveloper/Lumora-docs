package uz.kmax.documents.presentation.documents

import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import uz.kmax.base.fragment.BaseFragmentNV
import uz.kmax.documents.LumoraApplication
import uz.kmax.documents.R
import uz.kmax.documents.databinding.FragmentEnhancementBinding
import uz.kmax.documents.domain.model.DocumentEnhancementMode
import uz.kmax.documents.utils.ViewModelFactory

class EnhancementFragment : BaseFragmentNV<FragmentEnhancementBinding>(FragmentEnhancementBinding::inflate) {

    private val viewModel: EnhancementViewModel by viewModels {
        val app = requireActivity().application as LumoraApplication
        ViewModelFactory(app.documentRepository, billingRepository = app.billingRepository)
    }

    private var currentMode = DocumentEnhancementMode.ORIGINAL

    override fun onViewCreated() {
        val documentId = arguments?.getString("documentId") ?: return
        viewModel.loadDocument(documentId)

        setupListeners()
        observeViewModel()
        selectMode(DocumentEnhancementMode.ORIGINAL)
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            navController.navigateUp()
        }

        binding.btnApply.setOnClickListener {
            viewModel.applyEnhancement()
        }

        binding.btnOriginal.setOnClickListener { selectMode(DocumentEnhancementMode.ORIGINAL) }
        binding.btnAuto.setOnClickListener { selectMode(DocumentEnhancementMode.AUTO) }
        binding.btnGrayscale.setOnClickListener { selectMode(DocumentEnhancementMode.GRAYSCALE) }
        binding.btnBW.setOnClickListener { selectMode(DocumentEnhancementMode.BLACK_AND_WHITE) }
    }

    private fun selectMode(mode: DocumentEnhancementMode) {
        currentMode = mode
        viewModel.setMode(mode)
        updateButtonStates(mode)
    }

    private fun updateButtonStates(selectedMode: DocumentEnhancementMode) {
        val buttons = mapOf(
            DocumentEnhancementMode.ORIGINAL to binding.btnOriginal,
            DocumentEnhancementMode.AUTO to binding.btnAuto,
            DocumentEnhancementMode.GRAYSCALE to binding.btnGrayscale,
            DocumentEnhancementMode.BLACK_AND_WHITE to binding.btnBW
        )

        buttons.forEach { (mode, button) ->
            if (mode == selectedMode) {
                button.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.primary))
                button.setTextColor(ContextCompat.getColor(requireContext(), R.color.onPrimary))
                button.strokeWidth = 0
            } else {
                button.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                button.setTextColor(ContextCompat.getColor(requireContext(), R.color.onSurface))
                button.strokeWidth = 1.dpToPx()
                button.setStrokeColorResource(R.color.outline)
            }
        }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is EnhancementUiState.Idle -> {
                            binding.progressBar.visibility = View.GONE
                        }
                        is EnhancementUiState.Processing -> {
                            binding.progressBar.visibility = View.VISIBLE
                            binding.btnApply.isEnabled = false
                        }
                        is EnhancementUiState.Preview -> {
                            binding.progressBar.visibility = View.GONE
                            binding.ivPreview.setImageBitmap(state.bitmap)
                            binding.btnApply.isEnabled = true
                        }
                        is EnhancementUiState.Success -> {
                            binding.progressBar.visibility = View.GONE
                            Toast.makeText(requireContext(), R.string.enhance_msg_success, Toast.LENGTH_SHORT).show()
                            navController.navigateUp()
                        }
                        is EnhancementUiState.Error -> {
                            binding.progressBar.visibility = View.GONE
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                        }
                        is EnhancementUiState.PremiumRequired -> {
                            binding.progressBar.visibility = View.GONE
                            navController.navigate(R.id.premiumFragment)
                        }
                    }
                }
            }
        }
    }
}
