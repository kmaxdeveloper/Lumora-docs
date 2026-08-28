package uz.kmax.documents.presentation.documents

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil.load
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import uz.kmax.base.fragment.BaseFragmentNV
import uz.kmax.documents.LumoraApplication
import uz.kmax.documents.R
import uz.kmax.documents.databinding.FragmentDocumentDetailBinding
import uz.kmax.documents.databinding.ItemPageThumbnailBinding
import uz.kmax.documents.domain.model.Document
import uz.kmax.documents.domain.model.DocumentPage
import uz.kmax.documents.utils.DialogUtils
import uz.kmax.documents.utils.ViewModelFactory
import java.io.File
import java.text.DateFormat
import java.util.*
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView

class DocumentDetailFragment : BaseFragmentNV<FragmentDocumentDetailBinding>(FragmentDocumentDetailBinding::inflate) {

    private val viewModel: DocumentDetailViewModel by viewModels {
        val app = requireActivity().application as LumoraApplication
        ViewModelFactory(app.documentRepository, billingRepository = app.billingRepository)
    }

    private lateinit var adapter: DocumentPageAdapter

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
            binding.layoutBottomActions.updatePadding(bottom = navBars.bottom + 20.dpToPx())
            insets
        }
    }

    private fun setupRecyclerView() {
        adapter = DocumentPageAdapter { page ->
            Toast.makeText(requireContext(), getString(R.string.detail_page_clicked, page.pageIndex + 1), Toast.LENGTH_SHORT).show()
        }
        binding.rvPages.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            navController.navigateUp()
        }

        binding.btnMore.setOnClickListener {
            viewModel.currentDocument.value?.let { showMoreOptions(it) }
        }

        binding.btnCreatePdf.setOnClickListener {
            viewModel.generatePdf()
        }

        binding.btnEnhance.setOnClickListener {
            val bundle = Bundle().apply {
                putString("documentId", viewModel.currentDocument.value?.id)
            }
            navController.navigate(R.id.enhancementFragment, bundle)
        }

        binding.btnOcr.setOnClickListener {
            val bundle = Bundle().apply {
                putString("documentId", viewModel.currentDocument.value?.id)
            }
            navController.navigate(R.id.ocrFragment, bundle)
        }

        binding.btnOpenPdf.setOnClickListener {
            viewModel.currentDocument.value?.let { openPdf(it) }
        }

        binding.btnSharePdf.setOnClickListener {
            viewModel.currentDocument.value?.let { sharePdf(it) }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.currentDocument.filterNotNull().collect { document ->
                    displayDocument(document)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.pdfState.collect { state ->
                    updatePdfUi(state)
                }
            }
        }
    }

    private fun updatePdfUi(state: PdfState) {
        when (state) {
            is PdfState.Idle -> {
                binding.pdfProgressBar.visibility = View.GONE
                val hasPdf = viewModel.currentDocument.value?.pdfPath != null
                binding.btnCreatePdf.visibility = if (hasPdf) View.GONE else View.VISIBLE
                binding.layoutPdfReady.visibility = if (hasPdf) View.VISIBLE else View.GONE
            }
            is PdfState.Generating -> {
                binding.pdfProgressBar.visibility = View.VISIBLE
                binding.btnCreatePdf.visibility = View.GONE
                binding.layoutPdfReady.visibility = View.GONE
            }
            is PdfState.Success -> {
                viewModel.resetPdfState()
            }
            is PdfState.Error -> {
                binding.pdfProgressBar.visibility = View.GONE
                Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                viewModel.resetPdfState()
            }
        }
    }

    private fun displayDocument(document: Document) {
        binding.tvDocumentName.text = document.name
        
        if (document.pages.isNotEmpty()) {
            binding.ivDocument.visibility = View.GONE
            binding.rvPages.visibility = View.VISIBLE
            adapter.submitList(document.pages)
        } else {
            binding.ivDocument.visibility = View.VISIBLE
            binding.rvPages.visibility = View.GONE
            binding.ivDocument.load(File(document.activeImagePath))
        }
        
        val dateStr = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(document.createdAt))
        binding.tvDate.text = getString(R.string.detail_created_on, dateStr)

        val ocrProcessedCount = if (document.pages.isNotEmpty()) document.pages.count { it.hasOcr } else if (document.hasOcr) 1 else 0
        val totalPages = document.pages.size.coerceAtLeast(1)

        val ocrButtonText = when {
            ocrProcessedCount == totalPages -> getString(R.string.ocr_status_ready)
            ocrProcessedCount > 0 -> getString(R.string.ocr_status_extracted, ocrProcessedCount, totalPages)
            else -> getString(R.string.ocr_btn_extract)
        }
        binding.btnOcr.text = ocrButtonText

        if (document.pdfPath != null) {
            val sizeStr = android.text.format.Formatter.formatFileSize(requireContext(), document.pdfSize)
            binding.tvPdfInfo.text = getString(R.string.detail_pdf_metadata, sizeStr)
            binding.btnCreatePdf.visibility = View.GONE
            binding.layoutPdfReady.visibility = View.VISIBLE
        } else {
            binding.btnCreatePdf.visibility = View.VISIBLE
            binding.layoutPdfReady.visibility = View.GONE
        }
    }

    private fun openPdf(document: Document) {
        val path = document.pdfPath ?: return
        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(requireContext(), R.string.detail_msg_pdf_not_found, Toast.LENGTH_SHORT).show()
            return
        }

        val uri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), R.string.pdf_error_app_missing, Toast.LENGTH_SHORT).show()
        }
    }

    private fun sharePdf(document: Document) {
        val path = document.pdfPath ?: return
        val file = File(path)
        if (!file.exists()) return

        val uri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        startActivity(Intent.createChooser(intent, getString(R.string.pdf_btn_share)))
    }

    private fun showMoreOptions(document: Document) {
        val optionsList = mutableListOf<String>()
        optionsList.add(getString(R.string.documents_btn_rename))
        optionsList.add(getString(R.string.documents_btn_duplicate))
        optionsList.add(getString(R.string.documents_btn_info))
        
        if (document.pdfPath != null) {
            optionsList.add(getString(R.string.documents_btn_delete_pdf))
        }
        
        optionsList.add(getString(R.string.scanner_desc_delete_page))
        
        val displayOptions = optionsList.toTypedArray()
        
        DialogUtils.showLumoraChoiceDialog(requireContext(), document.name, displayOptions) { which ->
            val selected = displayOptions[which]
            when {
                selected == getString(R.string.documents_btn_rename) -> showRenameDialog(document)
                selected == getString(R.string.documents_btn_duplicate) -> viewModel.duplicateDocument()
                selected == getString(R.string.documents_btn_info) -> showDocumentInfoDialog(document)
                selected == getString(R.string.documents_btn_delete_pdf) -> showDeletePdfConfirmation()
                selected == getString(R.string.scanner_desc_delete_page) -> showDeleteConfirmation(document)
            }
        }
    }

    private fun showDocumentInfoDialog(document: Document) {
        val dateTimeFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        val sizeStr = android.text.format.Formatter.formatFileSize(requireContext(), document.fileSize)
        val pdfStatus = if (document.pdfPath != null) getString(R.string.detail_status_available) else getString(R.string.detail_status_not_generated)
        val ocrStatus = if (document.hasOcr) getString(R.string.detail_status_extracted) else getString(R.string.detail_status_not_processed)

        val info = StringBuilder().apply {
            append(getString(R.string.detail_info_pages, document.pageCount)).append("\n")
            append(getString(R.string.detail_info_size, sizeStr)).append("\n")
            append(getString(R.string.detail_info_pdf, pdfStatus)).append("\n")
            append(getString(R.string.detail_info_text, ocrStatus)).append("\n")
            append(getString(R.string.detail_info_created, dateTimeFormat.format(Date(document.createdAt)))).append("\n")
            append(getString(R.string.detail_info_modified, dateTimeFormat.format(Date(document.updatedAt))))
        }.toString()

        DialogUtils.showLumoraDialog(
            requireContext(),
            title = getString(R.string.detail_info_title),
            message = info,
            iconRes = R.drawable.ic_info,
            primaryButtonText = getString(R.string.scanner_btn_finish)
        )
    }

    private fun showDeletePdfConfirmation() {
        DialogUtils.showLumoraDialog(
            requireContext(),
            title = getString(R.string.documents_delete_pdf_title),
            message = getString(R.string.documents_delete_pdf_msg),
            iconRes = R.drawable.ic_delete,
            primaryButtonText = getString(R.string.documents_btn_delete_pdf),
            secondaryButtonText = getString(R.string.scanner_btn_reset),
            onPrimaryClick = { viewModel.deletePdf() }
        )
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
            onPrimaryClick = {
                viewModel.deleteDocument(document)
                navController.navigateUp()
            }
        )
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}

class DocumentPageAdapter(
    private val onPageClick: (DocumentPage) -> Unit
) : androidx.recyclerview.widget.ListAdapter<DocumentPage, DocumentPageAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ItemPageThumbnailBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemPageThumbnailBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(page: DocumentPage) {
            binding.ivPage.load(File(page.activeImagePath))
            binding.tvPageIndex.text = (page.pageIndex + 1).toString()
            binding.root.setOnClickListener { onPageClick(page) }
        }
    }

    companion object DiffCallback : androidx.recyclerview.widget.DiffUtil.ItemCallback<DocumentPage>() {
        override fun areItemsTheSame(oldItem: DocumentPage, newItem: DocumentPage): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: DocumentPage, newItem: DocumentPage): Boolean = oldItem == newItem
    }
}
