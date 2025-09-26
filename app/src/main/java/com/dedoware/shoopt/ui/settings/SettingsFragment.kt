package com.dedoware.shoopt.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import com.dedoware.shoopt.R
import com.dedoware.shoopt.activities.SupportActivity
import com.dedoware.shoopt.databinding.FragmentSettingsBinding
import com.dedoware.shoopt.utils.getCurrencyManager

/**
 * Fragment de paramètres avec sélection de devise
 */
class SettingsFragment : Fragment(), CurrencySelectionDialog.OnCurrencySelectedListener {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupCurrencySelector()

        // Observer pour détecter les changements de devise
        requireContext().getCurrencyManager().currentCurrency.observe(viewLifecycleOwner, Observer { currency ->
            binding.currencyValue.text = getString(R.string.currency_value_format, currency.code, currency.symbol, currency.name)
        })

        // Ouvrir l'écran de support (Tip Jar) quand on clique sur la CardView
        binding.supportContainer.setOnClickListener {
            try {
                val intent = Intent(requireContext(), SupportActivity::class.java)
                startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(context, getString(R.string.unexpected_error), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupCurrencySelector() {
        // Configurer le sélecteur de devise
        binding.currencyContainer.setOnClickListener {
            showCurrencySelectionDialog()
        }
    }

    private fun showCurrencySelectionDialog() {
        val currentCurrency = requireContext().getCurrencyManager().currentCurrency.value?.code ?: "EUR"
        val dialog = CurrencySelectionDialog.newInstance(currentCurrency)
        dialog.setOnCurrencySelectedListener(this)
        dialog.show(childFragmentManager, "currency_selection")
    }

    override fun onCurrencySelected(currencyCode: String) {
        // Mettre à jour la devise sélectionnée
        requireContext().getCurrencyManager().setCurrency(currencyCode)

        // Afficher un message de confirmation
        Toast.makeText(
            context,
            getString(R.string.currency_changed, currencyCode),
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
