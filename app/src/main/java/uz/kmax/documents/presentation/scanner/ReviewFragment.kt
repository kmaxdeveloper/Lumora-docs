package uz.kmax.documents.presentation.scanner

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import coil.load
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import uz.kmax.base.fragment.BaseFragmentNV
import uz.kmax.documents.LumoraApplication
import uz.kmax.documents.R
import uz.kmax.documents.databinding.FragmentReviewBinding
import uz.kmax.documents.databinding.ItemPageReviewBinding
import uz.kmax.documents.utils.ViewModelFactory
import java.io.File

class ReviewFragment : BaseFragmentNV<FragmentReviewBinding>(FragmentReviewBinding::inflate) {

    private val sessionViewModel: ScanSessionViewModel by activityViewModels {
        val app = requireActivity().application as LumoraApplication
        ViewModelFactory(app.documentRepository, billingRepository = app.billingRepository)
    }

    private val pickMoreMedia = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) {
            sessionViewModel.importImages(requireContext(), uris)
        }
    }

    private lateinit var adapter: PageReviewAdapter

    override fun onViewCreated() {
        setupRecyclerView()
        setupListeners()
        setupEdgeToEdge()
        observeViewModel()
    }

    private fun setupEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            binding.layoutBottom.updatePadding(bottom = navBars.bottom + 20.dpToPx())
            binding.rvPages.updatePadding(bottom = navBars.bottom + 100.dpToPx())
            insets
        }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    private fun setupRecyclerView() {
        adapter = PageReviewAdapter()
        binding.rvPages.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPages.adapter = adapter

        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.bindingAdapterPosition
                val toPos = target.bindingAdapterPosition
                sessionViewModel.movePage(fromPos, toPos)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
        })
        itemTouchHelper.attachToRecyclerView(binding.rvPages)
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            navController.navigateUp()
        }

        binding.btnAddPage.setOnClickListener {
            showAddPageOptions()
        }

        binding.btnDone.setOnClickListener {
            sessionViewModel.saveDocument()
        }
    }

    private fun showAddPageOptions() {
        val options = arrayOf(
            getString(R.string.scanner_option_scan),
            getString(R.string.scanner_option_gallery)
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.scanner_btn_add_page)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> navController.popBackStack(R.id.scannerFragment, false)
                    1 -> pickMoreMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            }
            .show()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sessionViewModel.pages.collect { pages ->
                    adapter.submitList(pages)
                    binding.tvPageCount.text = resources.getQuantityString(R.plurals.pages_count, pages.size, pages.size)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sessionViewModel.saveState.collect { state ->
                    when (state) {
                        is ScanSessionViewModel.SaveState.Saving -> {
                            binding.progressBar.visibility = View.VISIBLE
                            binding.btnDone.isEnabled = false
                        }
                        is ScanSessionViewModel.SaveState.Success -> {
                            binding.progressBar.visibility = View.GONE
                            Toast.makeText(requireContext(), R.string.documents_msg_organized, Toast.LENGTH_SHORT).show()
                            val app = requireActivity().application as LumoraApplication
                            uz.kmax.documents.domain.ads.AdsManager.showInterstitial(
                                activity = requireActivity(),
                                billingRepository = app.billingRepository,
                                onDismiss = {
                                    navController.popBackStack(R.id.homeFragment, false)
                                }
                            )
                        }
                        is ScanSessionViewModel.SaveState.Error -> {
                            binding.progressBar.visibility = View.GONE
                            binding.btnDone.isEnabled = true
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                        }
                        else -> {
                            binding.progressBar.visibility = View.GONE
                            binding.btnDone.isEnabled = true
                        }
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sessionViewModel.importState.collect { state ->
                    when (state) {
                        is ScanSessionViewModel.ImportState.Loading -> {
                            binding.progressBar.visibility = View.VISIBLE
                            binding.btnDone.isEnabled = false
                        }
                        is ScanSessionViewModel.ImportState.Success -> {
                            binding.progressBar.visibility = View.GONE
                            binding.btnDone.isEnabled = true
                            sessionViewModel.resetImportState()
                        }
                        is ScanSessionViewModel.ImportState.Error -> {
                            binding.progressBar.visibility = View.GONE
                            binding.btnDone.isEnabled = true
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                            sessionViewModel.resetImportState()
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    private inner class PageReviewAdapter : androidx.recyclerview.widget.ListAdapter<String, PageReviewAdapter.ViewHolder>(DiffCallback) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(ItemPageReviewBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(getItem(position), position)
        }

        inner class ViewHolder(private val binding: ItemPageReviewBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(path: String, position: Int) {
                binding.ivThumbnail.load(File(path)) {
                    size(300, 300) // Efficient thumbnail
                    crossfade(true)
                }
                binding.tvPageIndex.text = getString(R.string.ocr_page_header, position + 1)
                binding.btnDelete.setOnClickListener { sessionViewModel.removePage(bindingAdapterPosition) }
            }
        }
    }

    companion object {
        private val DiffCallback = object : androidx.recyclerview.widget.DiffUtil.ItemCallback<String>() {
            override fun areItemsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
            override fun areContentsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
        }
    }
}
