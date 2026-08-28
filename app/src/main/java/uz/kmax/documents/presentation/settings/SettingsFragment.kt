package uz.kmax.documents.presentation.settings

import android.text.format.Formatter
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
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
import uz.kmax.documents.databinding.FragmentSettingsBinding
import uz.kmax.documents.domain.model.PremiumEntitlement
import uz.kmax.documents.utils.DialogUtils
import uz.kmax.documents.utils.ViewModelFactory

class SettingsFragment : BaseFragmentNV<FragmentSettingsBinding>(FragmentSettingsBinding::inflate) {

    private val viewModel: SettingsViewModel by viewModels {
        val app = requireActivity().application as LumoraApplication
        ViewModelFactory(
            app.documentRepository, 
            preferenceManager = app.preferenceManager,
            billingRepository = app.billingRepository
        )
    }

    override fun onViewCreated() {
        setupListeners()
        setupEdgeToEdge()
        observeViewModel()
        displayAppInfo()
    }

    private fun setupEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.layoutTop.updatePadding(top = systemBars.top)
            binding.root.updatePadding(bottom = systemBars.bottom)
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadStorageInfo(requireContext())
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            navController.navigateUp()
        }

        binding.rowAppearance.setOnClickListener {
            showAppearanceDialog()
        }

        binding.btnClearTemp.setOnClickListener {
            showClearTempConfirmation()
        }

        binding.rowPremium.setOnClickListener {
            navController.navigate(R.id.premiumFragment)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.storageInfo.collect { info ->
                    updateStorageUi(info)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.entitlement.collect { entitlement ->
                    val isPremium = entitlement == PremiumEntitlement.PREMIUM
                    binding.tvPremiumTitle.text = getString(
                        if (isPremium) R.string.premium_settings_active else R.string.premium_settings_upgrade
                    )
                    binding.tvPremiumDesc.text = getString(
                        if (isPremium) R.string.premium_active_desc else R.string.premium_subtitle
                    )
                    binding.ivPremiumChevron.visibility = if (isPremium) View.GONE else View.VISIBLE
                    binding.ivPremiumIcon.setImageResource(if (isPremium) R.drawable.ic_check else R.drawable.ic_premium)
                }
            }
        }

        updateAppearanceLabel(viewModel.getAppearance())
    }

    private fun updateStorageUi(info: StorageInfo) {
        val context = requireContext()
        val docsSizeStr = Formatter.formatFileSize(context, info.totalDocumentsSize)
        
        val countText = resources.getQuantityString(R.plurals.documents_count, info.documentCount, info.documentCount)
        
        binding.tvDocStats.text = getString(R.string.settings_storage_doc_combined, countText, docsSizeStr)
        
        binding.tvPdfStats.text = Formatter.formatFileSize(context, info.pdfSize)
        binding.tvTempStats.text = Formatter.formatFileSize(context, info.temporarySize)
    }

    private fun showAppearanceDialog() {
        val options = arrayOf(
            getString(R.string.settings_appearance_system),
            getString(R.string.settings_appearance_light),
            getString(R.string.settings_appearance_dark)
        )
        
        val currentMode = viewModel.getAppearance()
        val checkedItem = when (currentMode) {
            AppCompatDelegate.MODE_NIGHT_NO -> 1
            AppCompatDelegate.MODE_NIGHT_YES -> 2
            else -> 0
        }

        DialogUtils.showLumoraSingleChoiceDialog(requireContext(), getString(R.string.settings_label_appearance), options, checkedItem) { which ->
            val newMode = when (which) {
                1 -> AppCompatDelegate.MODE_NIGHT_NO
                2 -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            viewModel.updateAppearance(newMode)
            AppCompatDelegate.setDefaultNightMode(newMode)
            updateAppearanceLabel(newMode)
        }
    }

    private fun updateAppearanceLabel(mode: Int) {
        val labelRes = when (mode) {
            AppCompatDelegate.MODE_NIGHT_NO -> R.string.settings_appearance_light
            AppCompatDelegate.MODE_NIGHT_YES -> R.string.settings_appearance_dark
            else -> R.string.settings_appearance_system
        }
        binding.tvAppearanceValue.setText(labelRes)
    }

    private fun showClearTempConfirmation() {
        DialogUtils.showLumoraDialog(
            requireContext(),
            title = getString(R.string.settings_clear_temp_title),
            message = getString(R.string.settings_clear_temp_msg),
            iconRes = R.drawable.ic_delete,
            primaryButtonText = getString(R.string.scanner_desc_delete_page),
            secondaryButtonText = getString(R.string.scanner_btn_reset),
            onPrimaryClick = {
                viewModel.clearTemporaryFiles(requireContext())
                Toast.makeText(requireContext(), R.string.settings_clear_temp_success, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun displayAppInfo() {
        try {
            val pInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            val version = pInfo.versionName
            binding.tvAppVersion.text = getString(R.string.settings_about_version, version)
        } catch (_: Exception) {
            binding.tvAppVersion.text = getString(R.string.settings_about_version, "1.0.0")
        }

        // Developer reset mechanism
        binding.tvAppVersion.setOnLongClickListener {
            (requireActivity().application as LumoraApplication).preferenceManager.onboardingCompleted = false
            Toast.makeText(requireContext(), R.string.settings_msg_onboarding_reset, Toast.LENGTH_SHORT).show()
            true
        }
    }
}