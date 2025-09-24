package com.dedoware.shoopt.analytics

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.DialogFragment
import com.dedoware.shoopt.R
import com.dedoware.shoopt.utils.UserPreferences
import com.dedoware.shoopt.utils.CrashlyticsManager

/**
 * Fragment de dialogue informatif concernant l'analytics (activé par défaut).
 */
class AnalyticsConsentDialogFragment : DialogFragment() {

    interface ConsentListener {
        fun onConsentGiven()
        fun onConsentDenied()
    }

    private var consentListener: ConsentListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Empêcher la fermeture par un tap en dehors ou par le bouton back pour forcer la confirmation
        isCancelable = false
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is ConsentListener) {
            consentListener = context
        } else {
            // Ne plus forcer l'implémentation mais garder la compatibilité
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_analytics_consent, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Bouton de confirmation (l'utilisateur indique qu'il a compris)
        view.findViewById<Button>(R.id.btn_confirm).setOnClickListener {
            CrashlyticsManager.log("AnalyticsConsentDialog: btn_confirm clicked")
            // Marquer que nous avons demandé le consentement afin de ne pas le redemander
            try {
                UserPreferences.setAnalyticsConsentRequested(requireContext(), true)
                CrashlyticsManager.log("AnalyticsConsentDialog: setAnalyticsConsentRequested=true")
            } catch (e: Exception) {
                CrashlyticsManager.log("AnalyticsConsentDialog: error setting consent requested: ${e.message ?: "null"}")
            }

            // Le résultat est publié ci-dessous; l'activité l'écoute via setFragmentResultListener

            // Publier un résultat afin que l'activité puisse l'écouter de manière robuste
            val result = Bundle().apply { putBoolean("confirmed", true) }
            parentFragmentManager.setFragmentResult("analytics_consent", result)
            CrashlyticsManager.log("AnalyticsConsentDialog: fragment result published")

            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    companion object {
        const val TAG = "AnalyticsConsentDialog"

        /**
         * Crée une nouvelle instance du fragment de dialogue informatif.
         */
        fun newInstance(): AnalyticsConsentDialogFragment {
            return AnalyticsConsentDialogFragment()
        }
    }
}
