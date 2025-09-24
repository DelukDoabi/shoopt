package com.dedoware.shoopt.analytics

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.dedoware.shoopt.R
import com.dedoware.shoopt.utils.UserPreferences

/**
 * Fragment de dialogue pour demander le consentement utilisateur
 * concernant la collecte des données d'analytics conformément au RGPD.
 */
class AnalyticsConsentDialogFragment : DialogFragment() {

    interface ConsentListener {
        fun onConsentGiven()
        fun onConsentDenied()
    }

    private var consentListener: ConsentListener? = null
    private lateinit var consentTracker: ConsentTracker

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is ConsentListener) {
            consentListener = context
        } else {
            throw RuntimeException("$context doit implémenter ConsentListener")
        }

        // Initialisation du tracker de consentement
        consentTracker = ConsentTracker.getInstance(context)
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

        view.findViewById<Button>(R.id.btn_accept).setOnClickListener {
            // Activer l'analytics dans les préférences utilisateur
            UserPreferences.setAnalyticsEnabled(requireContext(), true)
            // Activer explicitement le tracking dans le service Analytics
            AnalyticsService.getInstance(requireContext()).enableTracking()

            // Suivre l'acceptation du consentement
            consentTracker.trackConsentAccepted()

            // Notifier l'activité parente
            consentListener?.onConsentGiven()
            dismiss()
        }

        view.findViewById<Button>(R.id.btn_decline).setOnClickListener {
            // Désactiver l'analytics dans les préférences utilisateur
            UserPreferences.setAnalyticsEnabled(requireContext(), false)
            // Désactiver explicitement le tracking dans le service Analytics
            AnalyticsService.getInstance(requireContext()).disableTracking()

            // Suivre le refus du consentement
            consentTracker.trackConsentDeclined()

            // Notifier l'activité parente
            consentListener?.onConsentDenied()
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
         * Crée une nouvelle instance du fragment de dialogue de consentement.
         */
        fun newInstance(): AnalyticsConsentDialogFragment {
            return AnalyticsConsentDialogFragment()
        }
    }
}
