package uz.kmax.documents.presentation.home

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.activityViewModels
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
import uz.kmax.documents.databinding.FragmentHomeBinding
import uz.kmax.documents.domain.model.Document
import uz.kmax.documents.presentation.documents.DocumentAdapter
import uz.kmax.documents.presentation.scanner.ScanSessionViewModel
import uz.kmax.documents.utils.DialogUtils
import uz.kmax.documents.utils.ViewModelFactory
import java.util.Calendar

class HomeFragment : BaseFragmentNV<FragmentHomeBinding>(FragmentHomeBinding::inflate) {

    private val viewModel: HomeViewModel by viewModels {
        val app = requireActivity().application as LumoraApplication
        ViewModelFactory(app.documentRepository, billingRepository = app.billingRepository)
    }

    private val sessionViewModel: ScanSessionViewModel by activityViewModels {
        ViewModelFactory((requireActivity().application as LumoraApplication).documentRepository)
    }

    private val pickMultipleMedia = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) {
            sessionViewModel.clear()
            sessionViewModel.importImages(requireContext(), uris)
            navController.navigate(R.id.reviewFragment)
        }
    }

    private lateinit var adapter: DocumentAdapter

    override fun onViewCreated() {
        setupGreeting()
        setupRecyclerView()
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
        uz.kmax.documents.domain.ads.AdsManager.preloadInterstitial(
            context = requireContext(),
            billingRepository = app.billingRepository
        )
    }

    private fun setupEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(
                top = systemBars.top,
                bottom = systemBars.bottom
            )
            insets
        }
    }

    private fun setupGreeting() {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        
        val greeting = when (hour) {
            in 0..11 -> getString(R.string.home_greeting_morning)
            in 12..16 -> getString(R.string.home_greeting_afternoon)
            else -> getString(R.string.home_greeting_evening)
        }
        
        binding.tvGreeting.text = greeting
    }

    private fun setupRecyclerView() {
        adapter = DocumentAdapter(
            onItemClick = { document ->
                val bundle = Bundle().apply {
                    putString("documentId", document.id)
                }
                navController.navigate(R.id.documentDetailFragment, bundle)
            },
            onLongClick = { _ ->
                // Long click not handled on home
            },
            onMoreClick = { document ->
                showMoreOptions(document)
            }
        )
        binding.rvRecentDocuments.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnSettings.setOnClickListener {
            navController.navigate(R.id.settingsFragment)
        }

        binding.cardScan.setOnClickListener {
            sessionViewModel.clear()
            navController.navigate(R.id.scannerFragment)
        }

        binding.btnImport.setOnClickListener {
            pickMultipleMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        /* btnAi disabled for V1
        binding.btnAi.setOnClickListener {
            navController.navigate(R.id.aiFragment)
        }
        */
        
        binding.layoutEmptyState.setOnClickListener {
            sessionViewModel.clear()
            navController.navigate(R.id.scannerFragment)
        }

        binding.btnSeeAll.setOnClickListener {
            navController.navigate(R.id.documentsFragment)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.recentDocuments.collect { docs ->
                    adapter.submitList(docs)
                    binding.layoutEmptyState.visibility = if (docs.isEmpty()) View.VISIBLE else View.GONE
                    binding.rvRecentDocuments.visibility = if (docs.isEmpty()) View.GONE else View.VISIBLE
                    binding.btnSeeAll.visibility = if (docs.isEmpty()) View.GONE else View.VISIBLE
                }
            }
        }
    }

    private fun showMoreOptions(document: Document) {
        val options = arrayOf(
            getString(R.string.documents_btn_rename),
            getString(R.string.scanner_desc_delete_page)
        )
        DialogUtils.showLumoraChoiceDialog(requireContext(), document.name, options) { which ->
            val selected = options[which]
            when (selected) {
                getString(R.string.documents_btn_rename) -> showRenameDialog(document)
                getString(R.string.scanner_desc_delete_page) -> showDeleteConfirmation(document)
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

}
