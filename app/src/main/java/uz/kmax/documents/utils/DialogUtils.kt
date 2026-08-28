package uz.kmax.documents.utils

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import uz.kmax.documents.databinding.DialogBaseBinding
import uz.kmax.documents.databinding.DialogInputBinding
import uz.kmax.documents.databinding.DialogListBinding
import uz.kmax.documents.databinding.ItemDialogChoiceBinding

object DialogUtils {

    fun showLumoraDialog(
        context: Context,
        title: String,
        message: String,
        iconRes: Int? = null,
        primaryButtonText: String = "OK",
        secondaryButtonText: String? = null,
        onPrimaryClick: () -> Unit = {},
        onSecondaryClick: () -> Unit = {}
    ): AlertDialog {
        val binding = DialogBaseBinding.inflate(LayoutInflater.from(context))
        
        val dialog = MaterialAlertDialogBuilder(context)
            .setView(binding.root)
            .create()

        binding.dialogTitle.text = title
        binding.dialogMessage.text = message
        
        if (iconRes != null) {
            binding.dialogIcon.isVisible = true
            binding.dialogIcon.setImageResource(iconRes)
        } else {
            binding.dialogIcon.isVisible = false
        }

        binding.btnPrimary.text = primaryButtonText
        binding.btnPrimary.setOnClickListener {
            onPrimaryClick()
            dialog.dismiss()
        }

        if (secondaryButtonText != null) {
            binding.btnSecondary.isVisible = true
            binding.btnSecondary.text = secondaryButtonText
            binding.btnSecondary.setOnClickListener {
                onSecondaryClick()
                dialog.dismiss()
            }
        } else {
            binding.btnSecondary.isVisible = false
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
        return dialog
    }

    fun showLumoraInputDialog(
        context: Context,
        title: String,
        hint: String,
        prefillText: String = "",
        primaryButtonText: String = "Save",
        secondaryButtonText: String = "Cancel",
        onInputConfirm: (String) -> Unit
    ): AlertDialog {
        val binding = DialogInputBinding.inflate(LayoutInflater.from(context))
        
        val dialog = MaterialAlertDialogBuilder(context)
            .setView(binding.root)
            .create()

        binding.dialogTitle.text = title
        binding.inputLayout.hint = hint
        binding.editText.setText(prefillText)
        
        binding.btnPrimary.text = primaryButtonText
        binding.btnPrimary.setOnClickListener {
            val input = binding.editText.text.toString()
            if (input.isNotBlank()) {
                onInputConfirm(input)
                dialog.dismiss()
            } else {
                binding.inputLayout.error = "Field cannot be empty"
            }
        }

        binding.btnSecondary.text = secondaryButtonText
        binding.btnSecondary.setOnClickListener {
            dialog.dismiss()
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
        return dialog
    }

    fun showLumoraChoiceDialog(
        context: Context,
        title: String,
        choices: Array<String>,
        onChoiceSelected: (Int) -> Unit
    ): AlertDialog {
        val binding = DialogListBinding.inflate(LayoutInflater.from(context))
        
        val dialog = MaterialAlertDialogBuilder(context)
            .setView(binding.root)
            .create()

        binding.dialogTitle.text = title
        
        binding.rvChoices.layoutManager = LinearLayoutManager(context)
        binding.rvChoices.adapter = ChoiceAdapter(choices, -1) { position ->
            onChoiceSelected(position)
            dialog.dismiss()
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
        return dialog
    }

    fun showLumoraSingleChoiceDialog(
        context: Context,
        title: String,
        choices: Array<String>,
        checkedItem: Int,
        onChoiceSelected: (Int) -> Unit
    ): AlertDialog {
        val binding = DialogListBinding.inflate(LayoutInflater.from(context))
        
        val dialog = MaterialAlertDialogBuilder(context)
            .setView(binding.root)
            .create()

        binding.dialogTitle.text = title
        
        binding.rvChoices.layoutManager = LinearLayoutManager(context)
        binding.rvChoices.adapter = ChoiceAdapter(choices, checkedItem) { position ->
            onChoiceSelected(position)
            dialog.dismiss()
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
        return dialog
    }

    private class ChoiceAdapter(
        private val choices: Array<String>,
        private val checkedItem: Int = -1,
        private val onChoiceSelected: (Int) -> Unit
    ) : RecyclerView.Adapter<ChoiceAdapter.ViewHolder>() {

        class ViewHolder(val binding: ItemDialogChoiceBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(ItemDialogChoiceBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.binding.tvChoice.text = choices[position]
            
            if (position == checkedItem) {
                holder.binding.tvChoice.setTextColor(holder.binding.root.context.getColor(uz.kmax.documents.R.color.accent))
                holder.binding.tvChoice.setTypeface(null, android.graphics.Typeface.BOLD)
            } else {
                holder.binding.tvChoice.setTextColor(holder.binding.root.context.getColor(uz.kmax.documents.R.color.onSurface))
                holder.binding.tvChoice.setTypeface(null, android.graphics.Typeface.NORMAL)
            }
            
            holder.binding.root.setOnClickListener { onChoiceSelected(position) }
        }

        override fun getItemCount(): Int = choices.size
    }
}