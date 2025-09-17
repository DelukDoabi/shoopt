package com.dedoware.shoopt.ui.settings

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.dedoware.shoopt.R
import com.dedoware.shoopt.utils.CurrencyManager
import com.dedoware.shoopt.utils.CrashlyticsManager
import kotlinx.coroutines.launch

/**
 * Dialogue moderne de sélection de devise avec conversion en temps réel
 * Permet à l'utilisateur de choisir parmi toutes les devises disponibles
 * et affiche des informations sur l'état de la conversion
 */
class CurrencySelectionDialog : DialogFragment() {

    private lateinit var radioGroup: RadioGroup
    private lateinit var loadingContainer: LinearLayout
    private lateinit var conversionInfoText: TextView
    private var listener: OnCurrencySelectedListener? = null
    private lateinit var currentCurrency: String
    private lateinit var currencyManager: CurrencyManager

    companion object {
        fun newInstance(currentCurrency: String): CurrencySelectionDialog {
            val fragment = CurrencySelectionDialog()
            val args = Bundle().apply {
                putString("currency", currentCurrency)
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentCurrency = arguments?.getString("currency") ?: "EUR"
        currencyManager = CurrencyManager.getInstance(requireContext())
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireActivity())
        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.dialog_currency_selection, null)

        radioGroup = view.findViewById(R.id.currency_radio_group)
        loadingContainer = view.findViewById(R.id.loading_container)
        conversionInfoText = view.findViewById(R.id.currency_conversion_info)

        // Observer les états de conversion
        observeConversionStates()

        // Remplir les options de devise depuis le CurrencyManager
        populateCurrencyOptions()

        val dialog = builder
            .setView(view)
            .setTitle(getString(R.string.select_currency))
            .setPositiveButton(getString(R.string.confirm)) { _, _ ->
                val selectedId = radioGroup.checkedRadioButtonId
                if (selectedId != -1) {
                    val radioButton = view.findViewById<RadioButton>(selectedId)
                    val currencyCode = radioButton.tag as String

                    // Si la devise a changé, nous préchargeons les taux de conversion
                    if (currencyCode != currentCurrency) {
                        preloadExchangeRates(currencyCode)
                    }

                    listener?.onCurrencySelected(currencyCode)
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()

        return dialog
    }

    private fun observeConversionStates() {
        // Observer l'état de chargement des conversions
        currencyManager.conversionInProgress.observe(this) { isLoading ->
            loadingContainer.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        // Observer les erreurs de conversion
        currencyManager.conversionError.observe(this) { errorMessage ->
            if (errorMessage != null) {
                conversionInfoText.text = errorMessage
                conversionInfoText.setTextColor(resources.getColor(android.R.color.holo_red_dark, null))
            } else {
                conversionInfoText.text = getString(R.string.currency_conversion_info)
                conversionInfoText.setTextColor(resources.getColor(android.R.color.darker_gray, null))
            }
        }
    }

    private fun populateCurrencyOptions() {
        lifecycleScope.launch {
            currencyManager.availableCurrencies.observe(this@CurrencySelectionDialog) { currencies ->
                radioGroup.removeAllViews()

                currencies.forEach { currency ->
                    val radioButton = RadioButton(context).apply {
                        id = currency.code.hashCode() // Utiliser le hashcode pour un ID unique
                        text = "${currency.code} - ${currency.name}"
                        tag = currency.code
                        isChecked = currency.code == currentCurrency
                        setPadding(16, 12, 16, 12)
                    }
                    radioGroup.addView(radioButton)
                }
            }
        }
    }

    /**
     * Précharge les taux de change pour la nouvelle devise sélectionnée
     */
    private fun preloadExchangeRates(currencyCode: String) {
        try {
            loadingContainer.visibility = View.VISIBLE
            currencyManager.setCurrency(currencyCode)

            Toast.makeText(
                context,
                getString(R.string.currency_conversion_success),
                Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur lors du préchargement des taux de change: ${e.message}")
            CrashlyticsManager.logException(e)

            Toast.makeText(
                context,
                getString(R.string.currency_conversion_error),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun setOnCurrencySelectedListener(listener: OnCurrencySelectedListener) {
        this.listener = listener
    }

    interface OnCurrencySelectedListener {
        fun onCurrencySelected(currencyCode: String)
    }
}
