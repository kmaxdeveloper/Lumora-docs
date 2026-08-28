package uz.kmax.documents.presentation.ocr

import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import uz.kmax.base.fragment.BaseFragmentNV
import uz.kmax.documents.LumoraApplication
import uz.kmax.documents.R
import uz.kmax.documents.databinding.FragmentOcrBinding
import uz.kmax.documents.databinding.ItemPageOcrBinding
import uz.kmax.documents.domain.model.ocr.DocumentPageOcr
import uz.kmax.documents.utils.DialogUtils
import uz.kmax.documents.utils.ViewModelFactory

class OcrFragment : BaseFragmentNV<FragmentOcrBinding>(FragmentOcrBinding::inflate) {

    private val viewModel: OcrViewModel by viewModels {
        val app = requireActivity().application as LumoraApplication
        ViewModelFactory(
            app.documentRepository, 
            ocrRepository = app.ocrRepository,
            billingRepository = app.billingRepository,
            preferenceManager = app.preferenceManager
        )
    }

    private lateinit var adapter: PageOcrAdapter

    override fun onViewCreated() {
        val documentId = arguments?.getString("documentId") ?: return
        
        setupRecyclerView()
        viewModel.loadDocument(documentId)
        setupListeners()
        setupEdgeToEdge()
        observeViewModel()
    }

    private fun setupEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            binding.layoutBottom.updatePadding(bottom = navBars.bottom + 24.dpToPx())
            binding.rvOcrResults.updatePadding(bottom = navBars.bottom + 100.dpToPx())
            insets
        }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    private fun setupRecyclerView() {
        adapter = PageOcrAdapter()
        binding.rvOcrResults.layoutManager = LinearLayoutManager(requireContext())
        binding.rvOcrResults.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            if (viewModel.uiState.value is OcrUiState.Processing) {
                viewModel.cancelOcr()
            } else {
                navController.navigateUp()
            }
        }

        binding.btnCopy.setOnClickListener {
            val state = viewModel.uiState.value
            if (state is OcrUiState.Success) {
                copyAllToClipboard(state.pages)
            }
        }

        binding.btnRetry.setOnClickListener {
            viewModel.extractText()
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

    private fun updateUi(state: OcrUiState) {
        when (state) {
            is OcrUiState.Idle -> {
                binding.progressBar.visibility = View.GONE
                binding.tvProgress.visibility = View.GONE
                binding.tvProgress.announceForAccessibility("") // Clear
            }
            is OcrUiState.Processing -> {
                binding.progressBar.visibility = View.VISIBLE
                binding.tvProgress.visibility = View.VISIBLE
                val progressText = getString(R.string.ocr_status_reading_pages, state.current, state.total)
                binding.tvProgress.text = progressText
                binding.tvProgress.announceForAccessibility(progressText)
                binding.btnCopy.isEnabled = false
                binding.btnRetry.isEnabled = false
            }
            is OcrUiState.Success -> {
                binding.progressBar.visibility = View.GONE
                binding.tvProgress.visibility = View.GONE
                binding.btnCopy.isEnabled = true
                binding.btnRetry.isEnabled = true
                adapter.submitList(state.pages)
            }
            is OcrUiState.Error -> {
                binding.progressBar.visibility = View.GONE
                binding.tvProgress.visibility = View.GONE
                binding.btnCopy.isEnabled = false
                binding.btnRetry.isEnabled = true
                Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
            }
            is OcrUiState.PremiumRequired -> {
                binding.progressBar.visibility = View.GONE
                binding.tvProgress.visibility = View.GONE
                showOcrLimitDialog()
            }
        }
    }

    private fun showOcrLimitDialog() {
        DialogUtils.showLumoraDialog(
            requireContext(),
            title = getString(R.string.premium_limit_ocr_title),
            message = getString(R.string.premium_limit_ocr_msg),
            iconRes = R.drawable.ic_premium,
            primaryButtonText = getString(R.string.premium_btn_upgrade),
            secondaryButtonText = getString(R.string.scanner_desc_close_onboarding),
            onPrimaryClick = { navController.navigate(R.id.premiumFragment) }
        )
    }

    private fun copyAllToClipboard(pages: List<DocumentPageOcr>) {
        val fullText = StringBuilder()
        pages.sortedBy { it.pageIndex }.forEach { page ->
            fullText.append(getString(R.string.ocr_clipboard_page_separator, page.pageIndex + 1))
            fullText.append(page.text)
            fullText.append("\n\n")
        }

        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = android.content.ClipData.newPlainText("Extracted Text", fullText.toString())
        clipboard.setPrimaryClip(clip)
        Toast.makeText(requireContext(), getString(R.string.ocr_copied_toast), Toast.LENGTH_SHORT).show()
    }
}

class PageOcrAdapter : androidx.recyclerview.widget.ListAdapter<DocumentPageOcr, PageOcrAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ItemPageOcrBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(private val binding: ItemPageOcrBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(ocr: DocumentPageOcr) {
            binding.tvPageLabel.text = binding.root.context.getString(R.string.ocr_page_header, ocr.pageIndex + 1)
            binding.tvPageText.text = ocr.text
        }
    }

    companion object DiffCallback : androidx.recyclerview.widget.DiffUtil.ItemCallback<DocumentPageOcr>() {
        override fun areItemsTheSame(oldItem: DocumentPageOcr, newItem: DocumentPageOcr): Boolean = oldItem.pageId == newItem.pageId
        override fun areContentsTheSame(oldItem: DocumentPageOcr, newItem: DocumentPageOcr): Boolean = oldItem == newItem
    }
}
