package uz.kmax.documents.presentation.premium

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import uz.kmax.documents.LumoraApplication
import uz.kmax.documents.databinding.FragmentPremiumBinding
import uz.kmax.documents.domain.model.PremiumEntitlement
import uz.kmax.documents.utils.BillingConfig
import uz.kmax.documents.R

class PremiumFragment : Fragment() {

    private var _binding: FragmentPremiumBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PremiumViewModel by viewModels {
        val app = requireActivity().application as LumoraApplication
        PremiumViewModel.Factory(app.billingRepository)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPremiumBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        setupEdgeToEdge()
        observeViewModel()
    }

    private fun setupEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.layoutTop.updatePadding(top = systemBars.top)
            binding.root.updatePadding(bottom = systemBars.bottom)
            insets
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnMonthly.setOnClickListener {
            viewModel.purchase(requireActivity(), BillingConfig.PRODUCT_PREMIUM_MONTHLY)
        }

        binding.btnYearly.setOnClickListener {
            viewModel.purchase(requireActivity(), BillingConfig.PRODUCT_PREMIUM_YEARLY)
        }

        binding.btnNoAds.setOnClickListener {
            viewModel.purchase(requireActivity(), BillingConfig.PRODUCT_NO_ADS)
        }

        binding.btnRestore.setOnClickListener {
            viewModel.restore()
        }

        binding.btnContinue.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.entitlement.collectLatest { entitlement ->
                        val isPremium = entitlement == PremiumEntitlement.PREMIUM
                        binding.layoutActive.isVisible = isPremium
                    }
                }

                launch {
                    viewModel.products.collectLatest { products ->
                        products.forEach { product ->
                            when (product.id) {
                                BillingConfig.PRODUCT_PREMIUM_MONTHLY -> {
                                    binding.btnMonthly.text = getString(R.string.premium_btn_monthly, product.formattedPrice)
                                }
                                BillingConfig.PRODUCT_PREMIUM_YEARLY -> {
                                    binding.btnYearly.text = getString(R.string.premium_btn_yearly, product.formattedPrice)
                                }
                                BillingConfig.PRODUCT_NO_ADS -> {
                                    binding.btnNoAds.text = getString(R.string.premium_btn_no_ads, product.formattedPrice)
                                }
                            }
                        }
                    }
                }

                launch {
                    viewModel.purchaseResult.collectLatest { result ->
                        if (result.isSuccess) {
                            // Success is usually handled via entitlement flow
                        } else {
                            Toast.makeText(requireContext(), R.string.premium_error_purchase, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
