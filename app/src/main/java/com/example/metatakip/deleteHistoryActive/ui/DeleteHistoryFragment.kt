package com.example.metatakip.deleteHistoryActive.ui.deleteHistoryFragment

import android.os.Bundle
import android.view.View
import android.text.Editable
import android.text.TextWatcher
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.metatakip.R
import com.example.metatakip.databinding.FragmentDeleteHistoryBinding
import com.example.metatakip.deleteHistoryActive.deleteHistoryActiveModel.ui.DeleteHistoryViewModel
import com.example.metatakip.deleteHistoryActive.deleteHistoryActiveModel.ui.DeleteHistoryViewModelFactory
import com.example.metatakip.deleteHistoryActive.restore.DeleteHistoryQueryRepositoryImpl
import com.example.metatakip.deleteHistoryActive.restore.DeleteHistoryRepositoryImpl
import com.example.metatakip.deleteHistoryActive.restore.RestoreGuard
import com.example.metatakip.deleteHistoryActive.restore.RestoreUseCase
import com.google.android.material.snackbar.Snackbar

class DeleteHistoryFragment : Fragment(R.layout.fragment_delete_history) {

    private var _binding: FragmentDeleteHistoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: DeleteHistoryViewModel
    private lateinit var adapter: DeleteHistoryAdapter

    // =========================================================
    // 🔄 VIEW CREATED
    // =========================================================
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentDeleteHistoryBinding.bind(view)

        // ===============================
        // RecyclerView
        // ===============================
        adapter = DeleteHistoryAdapter()

        binding.recyclerDeleteHistory.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@DeleteHistoryFragment.adapter
        }

        // ===============================
        // 🔍 SEARCH (EditText + TextWatcher)
        // ===============================
        binding.editSearch.addTextChangedListener(object : TextWatcher {

            override fun beforeTextChanged(
                s: CharSequence?, start: Int, count: Int, after: Int
            ) {
                // boş
            }

            override fun onTextChanged(
                s: CharSequence?, start: Int, before: Int, count: Int
            ) {
                adapter.filter(s?.toString())
            }

            override fun afterTextChanged(s: Editable?) {
                // boş
            }
        })

        // ===============================
        // ViewModel
        // ===============================
        val queryRepository =
            DeleteHistoryQueryRepositoryImpl(requireContext())

        val restoreUseCase = RestoreUseCase(
            DeleteHistoryRepositoryImpl(requireContext()),
            RestoreGuard()
        )

        val factory =
            DeleteHistoryViewModelFactory(queryRepository, restoreUseCase)

        viewModel =
            ViewModelProvider(this, factory)[DeleteHistoryViewModel::class.java]

        // ===============================
        // Restore callbacks
        // ===============================
        adapter.onRestoreCustomer = { customer ->
            viewModel.restoreCustomer(customer)
        }

        adapter.onRestoreOrder = { order, customer ->
            viewModel.restoreOrder(order, customer)
        }

        adapter.onRestoreProduct = { product, order ->
            viewModel.restoreProduct(product, order)
        }

        observeViewModel()
    }

    // =========================================================
    // 🔙 BACK PRESS HANDLER
    // =========================================================
    fun handleBackPress(): Boolean {
        return ::adapter.isInitialized && adapter.collapseExpandedItems()
    }

    // =========================================================
    // 👀 OBSERVE
    // =========================================================
    private fun observeViewModel() {

        viewModel.deleteHistoryResult.observe(viewLifecycleOwner) { result ->
            adapter.submitData(
                result.deletedCustomers,
                result.activeCustomers
            )
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            message?.let {
                adapter.setRestoring(false)
                showSnackbar(it, isError = true)
                viewModel.clearErrorMessage()
            }
        }

        viewModel.successMessage.observe(viewLifecycleOwner) { message ->
            message?.let {
                showSnackbar(it, isError = false)
                viewModel.clearSuccessMessage()
            }
        }
    }

    // =========================================================
    // 🔔 SNACKBAR
    // =========================================================
    private fun showSnackbar(message: String, isError: Boolean) {
        val snackbar =
            Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)

        val colorRes =
            if (isError) android.R.color.holo_red_dark
            else android.R.color.holo_green_dark

        snackbar.setBackgroundTint(
            resources.getColor(colorRes, null)
        )
        snackbar.show()
    }

    // =========================================================
    // 🧹 CLEANUP
    // =========================================================
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
