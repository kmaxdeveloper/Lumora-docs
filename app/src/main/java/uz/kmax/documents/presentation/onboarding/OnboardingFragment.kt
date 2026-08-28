package uz.kmax.documents.presentation.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.navigation.NavOptions
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import uz.kmax.base.fragment.BaseFragmentNV
import uz.kmax.documents.LumoraApplication
import uz.kmax.documents.R
import uz.kmax.documents.databinding.FragmentOnboardingBinding
import uz.kmax.documents.databinding.ItemOnboardingBinding

class OnboardingFragment : BaseFragmentNV<FragmentOnboardingBinding>(FragmentOnboardingBinding::inflate) {

    override fun onViewCreated() {
        setupEdgeToEdge()
        setupViewPager()
        setupListeners()
    }

    private fun setupEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.btnSkip.updatePadding(top = systemBars.top)
            binding.layoutBottom.updatePadding(bottom = systemBars.bottom + 16.dpToPx())
            insets
        }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    private fun setupViewPager() {
        val items = listOf(
            OnboardingItem(
                R.drawable.ic_scan,
                getString(R.string.onboarding_title_1),
                getString(R.string.onboarding_desc_1)
            ),
            OnboardingItem(
                R.drawable.ic_pdf,
                getString(R.string.onboarding_title_2),
                getString(R.string.onboarding_desc_2)
            ),
            OnboardingItem(
                R.drawable.ic_gallery,
                getString(R.string.onboarding_title_3),
                getString(R.string.onboarding_desc_3)
            )
        )

        val adapter = OnboardingAdapter(items)
        binding.viewPager.adapter = adapter

        TabLayoutMediator(binding.pageIndicator, binding.viewPager) { _, _ -> }.attach()

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val isLastPage = position == items.size - 1
                if (isLastPage) {
                    binding.btnNext.setText(R.string.onboarding_btn_get_started)
                    binding.btnSkip.animate().alpha(0f).withEndAction {
                        binding.btnSkip.visibility = View.INVISIBLE
                    }.start()
                } else {
                    binding.btnNext.setText(R.string.onboarding_btn_next)
                    binding.btnSkip.visibility = View.VISIBLE
                    binding.btnSkip.animate().alpha(1f).start()
                }
            }
        })
    }

    private fun setupListeners() {
        binding.btnNext.setOnClickListener {
            val currentItem = binding.viewPager.currentItem
            val totalItems = binding.viewPager.adapter?.itemCount ?: 1
            if (currentItem < totalItems - 1) {
                binding.viewPager.setCurrentItem(currentItem + 1, true)
            } else {
                completeOnboarding()
            }
        }

        binding.btnSkip.setOnClickListener {
            completeOnboarding()
        }
    }

    private fun completeOnboarding() {
        val app = requireActivity().application as LumoraApplication
        app.preferenceManager.onboardingCompleted = true
        navigateToHome()
    }

    private fun navigateToHome() {
        navController.navigate(
            R.id.homeFragment, 
            null, 
            NavOptions.Builder()
                .setPopUpTo(R.id.onboardingFragment, true)
                .build()
        )
    }

    private data class OnboardingItem(
        val illustration: Int,
        val title: String,
        val description: String
    )

    private inner class OnboardingAdapter(private val items: List<OnboardingItem>) :
        RecyclerView.Adapter<OnboardingAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(
                ItemOnboardingBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        inner class ViewHolder(private val binding: ItemOnboardingBinding) :
            RecyclerView.ViewHolder(binding.root) {

            fun bind(item: OnboardingItem) {
                binding.ivIllustration.setImageResource(item.illustration)
                binding.tvTitle.text = item.title
                binding.tvDescription.text = item.description
            }
        }
    }
}
