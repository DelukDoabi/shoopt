package com.dedoware.shoopt.ui.settings

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.dedoware.shoopt.R
import com.dedoware.shoopt.utils.CurrencyManager
import kotlinx.coroutines.launch

/**
 * Dialogue moderne de sélection de devise
 * Permet à l'utilisateur de choisir parmi toutes les devises disponibles
 */
class CurrencySelectionDialog : DialogFragment() {

    private lateinit var radioGroup: RadioGroup
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

        // Remplir les options de devise depuis le CurrencyManager
        populateCurrencyOptions()

        return builder
            .setView(view)
            .setTitle(getString(R.string.select_currency))
            .setPositiveButton(getString(R.string.confirm)) { _, _ ->
                val selectedId = radioGroup.checkedRadioButtonId
                if (selectedId != -1) {
                    val radioButton = view.findViewById<RadioButton>(selectedId)
                    val currencyCode = radioButton.tag as String
                    listener?.onCurrencySelected(currencyCode)
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
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

    fun setOnCurrencySelectedListener(listener: OnCurrencySelectedListener) {
        this.listener = listener
    }

    interface OnCurrencySelectedListener {
        fun onCurrencySelected(currencyCode: String)
    }
}
