package uz.kmax.documents.presentation.documents

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.PopupMenu
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.os.bundleOf
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import kotlinx.coroutines.launch
import uz.kmax.base.fragment.BaseFragmentNV
import uz.kmax.documents.LumoraApplication
import uz.kmax.documents.R
import uz.kmax.documents.databinding.FragmentDocumentsBinding
import uz.kmax.documents.domain.model.Document
import uz.kmax.documents.utils.DialogUtils
import uz.kmax.documents.utils.ViewModelFactory
import java.io.File

class DocumentsFragment : BaseFragmentNV<FragmentDocumentsBinding>(FragmentDocumentsBinding::inflate) {

    private val viewModel: DocumentsViewModel by viewModels {
        val app = requireActivity().application as LumoraApplication
        ViewModelFactory(app.documentRepository, billingRepository = app.billingRepository)
    }

    private lateinit var adapter: DocumentAdapter

    override fun onViewCreated() {
        setupRecyclerView()
        setupSearch()
        setupFilters()
        setupListeners()
        setupEdgeToEdge()
        observeViewModel()
        setupAds()
    }

    private fun setupAds() {
        val app = requireActivity().application as LumoraApplication
        uz.kmax.documents.domain.ads.AdsManager.loadBanner(
            container = binding.adBannerContainer,
            billingRepository = app.billingRepository,
            lifecycleOwner = viewLifecycleOwner
        )
    }

    private fun setupEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.layoutTop.updatePadding(top = systemBars.top)
            binding.rvDocuments.updatePadding(bottom = systemBars.bottom + 80.dpToPx())
            binding.layoutEmpty.updatePadding(bottom = systemBars.bottom)
            insets
        }
    }

    private fun setupRecyclerView() {
        adapter = DocumentAdapter(
            onItemClick = { document ->
                if (viewModel.isSelectionMode.value) {
                    viewModel.toggleSelection(document.id)
                } else {
                    val bundle = bundleOf("documentId" to document.id)
                    navController.navigate(R.id.documentDetailFragment, bundle)
                }
            },
            onLongClick = { document ->
                viewModel.toggleSelection(document.id)
            },
            onMoreClick = { document ->
                showMoreOptions(document)
            }
        )
        binding.rvDocuments.adapter = adapter
    }

    private fun setupSearch() {
        binding.cardSearch.setOnClickListener {
            binding.etSearch.requestFocus()
            // Optional: show keyboard manually if needed, but requestFocus usually triggers it
        }

        binding.etSearch.addTextChangedListener { text ->
            viewModel.setSearchQuery(text?.toString() ?: "")
            binding.btnClearSearch.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
        }

        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.text = null
        }
    }

    private fun setupFilters() {
        binding.chipGroupFilters.setOnCheckedStateChangeListener { _, checkedIds ->
            val filter = when (checkedIds.firstOrNull()) {
                R.id.chipPdf -> DocumentFilter.PDF
                R.id.chipNoPdf -> DocumentFilter.NO_PDF
                R.id.chipOcr -> DocumentFilter.OCR
                R.id.chipNoOcr -> DocumentFilter.NO_OCR
                else -> DocumentFilter.ALL
            }
            viewModel.setFilter(filter)
        }
    }

    private fun setupListeners() {
        binding.btnScan.setOnClickListener {
            navController.navigate(R.id.scannerFragment)
        }

        binding.btnSort.setOnClickListener { view ->
            showSortMenu(view)
        }

        binding.btnClearFilters.setOnClickListener {
            binding.etSearch.text = null
            binding.chipGroupFilters.check(R.id.chipAll)
            viewModel.clearFilters()
        }

        binding.btnCloseSelection.setOnClickListener {
            viewModel.clearSelection()
        }

        binding.btnBulkDelete.setOnClickListener {
            showBulkDeleteConfirmation()
        }

        binding.btnBulkShare.setOnClickListener {
            viewModel.onBulkShareRequested()
        }
    }

    private fun showSortMenu(view: View) {
        val popup = PopupMenu(requireContext(), view)
        popup.menu.add(0, 0, 0, R.string.documents_sort_newest)
        popup.menu.add(0, 1, 1, R.string.documents_sort_oldest)
        popup.menu.add(0, 2, 2, R.string.documents_sort_name_az)
        popup.menu.add(0, 3, 3, R.string.documents_sort_name_za)
        popup.menu.add(0, 4, 4, R.string.documents_sort_largest)
        
        popup.setOnMenuItemClickListener { item ->
            val sort = when (item.itemId) {
                0 -> DocumentSort.NEWEST
                1 -> DocumentSort.OLDEST
                2 -> DocumentSort.NAME_AZ
                3 -> DocumentSort.NAME_ZA
                4 -> DocumentSort.LARGEST
                else -> DocumentSort.NEWEST
            }
            viewModel.setSortOrder(sort)
            true
        }
        popup.show()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.documents.collect { docs ->
                    adapter.submitList(docs)
                    updateEmptyState(docs.isEmpty())
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.totalCount.collect { count ->
                    binding.tvCount.text = resources.getQuantityString(R.plurals.documents_count, count, count)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.selectedIds.collect { ids ->
                    adapter.updateSelection(ids)
                    binding.layoutSelection.visibility = if (ids.isNotEmpty()) View.VISIBLE else View.GONE
                    binding.tvSelectionCount.text = getString(R.string.documents_selected_count, ids.size)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        DocumentsEvent.PremiumRequired -> {
                            navController.navigate(R.id.premiumFragment)
                        }
                        DocumentsEvent.NavigateToBulkShare -> {
                            shareSelectedDocuments()
                        }
                    }
                }
            }
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        if (!isEmpty) {
            binding.layoutEmpty.visibility = View.GONE
            binding.rvDocuments.visibility = View.VISIBLE
            return
        }

        binding.rvDocuments.visibility = View.GONE
        binding.layoutEmpty.visibility = View.VISIBLE

        val query = viewModel.searchQuery.value
        val filter = viewModel.filter.value
        val totalCount = viewModel.totalCount.value

        when {
            totalCount == 0 -> {
                binding.tvEmptyTitle.setText(R.string.documents_empty_title)
                binding.tvEmptyDesc.setText(R.string.documents_empty_desc)
                binding.btnScan.visibility = View.VISIBLE
                binding.btnClearFilters.visibility = View.GONE
            }
            query.isNotEmpty() -> {
                binding.tvEmptyTitle.setText(R.string.documents_empty_search_title)
                binding.tvEmptyDesc.setText(R.string.documents_empty_search_desc)
                binding.btnScan.visibility = View.GONE
                binding.btnClearFilters.visibility = View.VISIBLE
            }
            filter != DocumentFilter.ALL -> {
                binding.tvEmptyTitle.setText(R.string.documents_empty_filter_title)
                binding.tvEmptyDesc.setText(R.string.documents_empty_filter_desc)
                binding.btnScan.visibility = View.GONE
                binding.btnClearFilters.visibility = View.VISIBLE
            }
        }
    }

    private fun showMoreOptions(document: Document) {
        val options = arrayOf(
            getString(R.string.documents_btn_rename),
            getString(R.string.documents_btn_duplicate),
            getString(R.string.scanner_desc_delete_page)
        )
        DialogUtils.showLumoraChoiceDialog(requireContext(), document.name, options) { which ->
            when (which) {
                0 -> showRenameDialog(document)
                1 -> viewModel.duplicateDocument(document)
                2 -> showDeleteConfirmation(document)
            }
        }
    }

    private fun showRenameDialog(document: Document) {
        DialogUtils.showLumoraInputDialog(
            requireContext(),
            title = getString(R.string.documents_rename_title),
            hint = getString(R.string.documents_rename_title),
            prefillText = document.name,
            primaryButtonText = getString(R.string.documents_btn_rename),
            secondaryButtonText = getString(R.string.scanner_btn_reset),
            onInputConfirm = { newName ->
                viewModel.renameDocument(document, newName)
            }
        )
    }

    private fun showDeleteConfirmation(document: Document) {
        DialogUtils.showLumoraDialog(
            requireContext(),
            title = getString(R.string.documents_delete_single_title),
            message = getString(R.string.documents_delete_single_msg),
            iconRes = R.drawable.ic_delete,
            primaryButtonText = getString(R.string.scanner_desc_delete_page),
            secondaryButtonText = getString(R.string.scanner_btn_reset),
            onPrimaryClick = { viewModel.deleteDocument(document) }
        )
    }

    private fun showBulkDeleteConfirmation() {
        val count = viewModel.selectedIds.value.size
        DialogUtils.showLumoraDialog(
            requireContext(),
            title = getString(R.string.documents_delete_bulk_title, count),
            message = getString(R.string.documents_delete_bulk_msg),
            iconRes = R.drawable.ic_delete,
            primaryButtonText = getString(R.string.scanner_desc_delete_page),
            secondaryButtonText = getString(R.string.scanner_btn_reset),
            onPrimaryClick = { viewModel.deleteSelectedDocuments() }
        )
    }

    private fun shareSelectedDocuments() {
        val selectedIds = viewModel.selectedIds.value
        val files = mutableListOf<Uri>()
        var containsPdf = false
        var containsImage = false
        
        viewLifecycleOwner.lifecycleScope.launch {
            selectedIds.forEach { id ->
                val doc = (requireActivity().application as LumoraApplication).documentRepository.getDocument(id)
                doc?.let {
                    val isPdf = it.pdfPath != null
                    val path = it.pdfPath ?: it.activeImagePath
                    val file = File(path)
                    if (file.exists()) {
                        val uri = FileProvider.getUriForFile(
                            requireContext(),
                            "${requireContext().packageName}.fileprovider",
                            file
                        )
                        files.add(uri)
                        if (isPdf) containsPdf = true else containsImage = true
                    }
                }
            }

            if (files.isEmpty()) return@launch

            val intent = if (files.size == 1) {
                Intent(Intent.ACTION_SEND).apply {
                    type = if (containsPdf) "application/pdf" else "image/jpeg"
                    putExtra(Intent.EXTRA_STREAM, files[0])
                }
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = when {
                        containsPdf && !containsImage -> "application/pdf"
                        !containsPdf && containsImage -> "image/jpeg"
                        else -> "*/*"
                    }
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(files))
                }
            }
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(intent, getString(R.string.documents_share_chooser_title)))
        }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}
