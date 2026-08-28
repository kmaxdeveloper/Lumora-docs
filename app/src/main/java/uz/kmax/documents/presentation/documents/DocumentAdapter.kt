package uz.kmax.documents.presentation.documents

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import uz.kmax.documents.R
import uz.kmax.documents.databinding.ItemDocumentBinding
import uz.kmax.documents.domain.model.Document
import java.io.File
import java.text.DateFormat
import java.util.*

class DocumentAdapter(
    private val onItemClick: (Document) -> Unit,
    private val onLongClick: (Document) -> Unit,
    private val onMoreClick: (Document) -> Unit
) : ListAdapter<Document, DocumentAdapter.ViewHolder>(DiffCallback) {

    private var selectedIds = setOf<String>()

    fun updateSelection(selectedIds: Set<String>) {
        this.selectedIds = selectedIds
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ItemDocumentBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemDocumentBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(document: Document) {
            val context = binding.root.context
            binding.tvName.text = document.name
            
            val dateStr = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(document.createdAt))
            val pagesStr = context.resources.getQuantityString(R.plurals.pages_count, document.pageCount, document.pageCount)
            
            val metadataStr = if (document.pdfPath != null) {
                val pdfSizeStr = android.text.format.Formatter.formatFileSize(context, document.pdfSize)
                context.getString(R.string.item_doc_metadata_pdf, dateStr, pagesStr, pdfSizeStr)
            } else {
                val sizeStr = android.text.format.Formatter.formatFileSize(context, document.fileSize)
                context.getString(R.string.item_doc_metadata_no_pdf, dateStr, pagesStr, sizeStr)
            }
            
            binding.tvDate.text = metadataStr
            
            binding.ivPdfStatus.visibility = if (document.pdfPath != null) View.VISIBLE else View.GONE
            
            binding.ivThumbnail.load(File(document.activeImagePath)) {
                crossfade(true)
                placeholder(android.R.color.darker_gray)
            }

            val isSelected = selectedIds.contains(document.id)
            binding.viewSelectionOverlay.visibility = if (isSelected) View.VISIBLE else View.GONE
            binding.ivCheck.visibility = if (isSelected) View.VISIBLE else View.GONE
            binding.btnMore.visibility = if (selectedIds.isNotEmpty()) View.GONE else View.VISIBLE

            // Accessibility summary
            val pdfStatus = if (document.pdfPath != null) context.getString(R.string.item_doc_desc_pdf) else ""
            val ocrStatus = if (document.hasOcr) context.getString(R.string.detail_status_extracted) else ""
            binding.root.contentDescription = "${document.name}, $pagesStr, $dateStr, $pdfStatus $ocrStatus"

            binding.root.setOnClickListener { onItemClick(document) }
            binding.root.setOnLongClickListener { 
                onLongClick(document)
                true
            }
            binding.btnMore.setOnClickListener { onMoreClick(document) }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<Document>() {
        override fun areItemsTheSame(oldItem: Document, newItem: Document): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Document, newItem: Document): Boolean {
            return oldItem == newItem
        }
    }
}
