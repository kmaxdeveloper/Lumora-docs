package uz.kmax.documents.presentation.ai

import android.content.ClipboardManager
import android.content.Context
import android.view.View
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import uz.kmax.base.fragment.BaseFragmentNV
import uz.kmax.documents.LumoraApplication
import uz.kmax.documents.R
import uz.kmax.documents.databinding.FragmentSummaryBinding
import uz.kmax.documents.domain.model.ai.AiSummary
import uz.kmax.documents.utils.ViewModelFactory

class SummaryFragment : BaseFragmentNV<FragmentSummaryBinding>(FragmentSummaryBinding::inflate) {

    private val viewModel: SummaryViewModel by viewModels {
        val app = requireActivity().application as LumoraApplication
        // ViewModelFactory(app.documentRepository, aiRepository = app.aiRepository)
        ViewModelFactory(app.documentRepository, billingRepository = app.billingRepository)
    }

    override fun onViewCreated() {
        val documentId = arguments?.getString("documentId") ?: return
        
        checkDisclosure {
            viewModel.loadSummary(documentId)
        }

        setupListeners()
        observeViewModel()
    }

    private fun checkDisclosure(onApproved: () -> Unit) {
        // In production, use Preferences to track if shown
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("AI Processing")
            .setMessage(R.string.summary_disclosure)
            .setPositiveButton("I Understand") { _, _ -> onApproved() }
            .setNegativeButton("Cancel") { _, _ -> navController.navigateUp() }
            .show()
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            navController.navigateUp()
        }

        binding.btnCopy.setOnClickListener {
            val state = viewModel.uiState.value
            if (state is SummaryUiState.Success) {
                copyToClipboard(state.summary)
            }
        }

        binding.btnRegenerate.setOnClickListener {
            viewModel.retry()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    updateUi(state)
                }
            }
        }
    }

    private fun updateUi(state: SummaryUiState) {
        when (state) {
            is SummaryUiState.Idle -> {
                binding.layoutLoading.visibility = View.GONE
                binding.scrollView.visibility = View.GONE
            }
            is SummaryUiState.Loading, is SummaryUiState.Generating -> {
                binding.layoutLoading.visibility = View.VISIBLE
                binding.scrollView.visibility = View.GONE
                binding.tvLoadingStatus.setText(
                    if (state is SummaryUiState.Loading) R.string.ocr_status_reading else R.string.summary_status_analyzing
                )
            }
            is SummaryUiState.Success -> {
                binding.layoutLoading.visibility = View.GONE
                binding.scrollView.visibility = View.VISIBLE
                displaySummary(state.summary)
            }
            is SummaryUiState.Error -> {
                binding.layoutLoading.visibility = View.GONE
                Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun displaySummary(summary: AiSummary) {
        binding.tvSummary.text = summary.summary
        
        if (summary.keyPoints.isNotEmpty()) {
            binding.labelKeyPoints.visibility = View.VISIBLE
            binding.tvKeyPoints.visibility = View.VISIBLE
            binding.tvKeyPoints.text = summary.keyPoints.joinToString("\n") { "• $it" }
        } else {
            binding.labelKeyPoints.visibility = View.GONE
            binding.tvKeyPoints.visibility = View.GONE
        }

        if (summary.importantFacts.isNotEmpty()) {
            binding.labelFacts.visibility = View.VISIBLE
            binding.tvFacts.visibility = View.VISIBLE
            binding.tvFacts.text = summary.importantFacts.joinToString("\n") { "• $it" }
        } else {
            binding.labelFacts.visibility = View.GONE
            binding.tvFacts.visibility = View.GONE
        }
    }

    private fun copyToClipboard(summary: AiSummary) {
        val text = StringBuilder().apply {
            append(summary.summary).append("\n\n")
            if (summary.keyPoints.isNotEmpty()) {
                append("KEY POINTS:\n")
                summary.keyPoints.forEach { append("• ").append(it).append("\n") }
                append("\n")
            }
            if (summary.importantFacts.isNotEmpty()) {
                append("IMPORTANT FACTS:\n")
                summary.importantFacts.forEach { append("• ").append(it).append("\n") }
            }
        }.toString()

        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = android.content.ClipData.newPlainText("Document Summary", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(requireContext(), R.string.summary_copied_toast, Toast.LENGTH_SHORT).show()
    }
}
