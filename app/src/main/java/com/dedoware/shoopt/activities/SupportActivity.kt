package com.dedoware.shoopt.activities

import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.android.billingclient.api.*
import com.dedoware.shoopt.R
import com.dedoware.shoopt.ShooptApplication
import com.dedoware.shoopt.analytics.AnalyticsService
import com.dedoware.shoopt.utils.getCurrencyManager

class SupportActivity : AppCompatActivity(), PurchasesUpdatedListener {

    private val TAG = "SupportActivity"

    private lateinit var billingClient: BillingClient
    private lateinit var analyticsService: AnalyticsService
    private var billingReady: Boolean = false
    // Map pour garder les ProductDetails récupérés depuis Google Play
    private val productDetailsMap: MutableMap<String, ProductDetails> = mutableMapOf()
    // timestamps pour mesurer latences (ms)
    private val attemptTimestampsMs: MutableMap<String, Long> = mutableMapOf()
    private val launchTimestampsMs: MutableMap<String, Long> = mutableMapOf()

    // IDs des produits définis dans Play Console (à créer: don1, don3, don5)
    private val productDon1 = "don1"
    private val productDon3 = "don3"
    private val productDon5 = "don5"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_support)

        Log.d(TAG, "onCreate: starting SupportActivity")

        analyticsService = AnalyticsService.getInstance(ShooptApplication.instance)
        analyticsService.trackScreenView("support_screen", "SupportActivity")

        // Initialiser BillingClient
        billingClient = BillingClient.newBuilder(this)
            .setListener(this)
            .enablePendingPurchases()
            .build()

        // trouver les boutons et les rendre cliquables immédiatement (afficheront un message si le service de paiement n'est pas prêt)
        val btn1: Button = findViewById(R.id.support_btn_1)
        val btn3: Button = findViewById(R.id.support_btn_3)
        val btn5: Button = findViewById(R.id.support_btn_5)
        val backBtn: ImageButton = findViewById(R.id.support_back_ib)

        // Ne pas désactiver les boutons ; launchPurchase vérifiera l'état du BillingClient et affichera un toast si nécessaire
        btn1.isEnabled = true
        btn3.isEnabled = true
        btn5.isEnabled = true

        // Ajout de toasts et logs de debug pour confirmer que les listeners sont bien installés
        btn1.setOnClickListener {
            Log.d(TAG, "click: support_btn_1")
            // Analytics: utilisateur a tenté de donner (clic sur bouton)
            // enregistrer le timestamp d'attempt
            attemptTimestampsMs[productDon1] = SystemClock.elapsedRealtime()
            val formatted = productDetailsMap[productDon1]?.oneTimePurchaseOfferDetails?.formattedPrice
            analyticsService.trackDonationAttempt(productDon1, formatted)
            Toast.makeText(this, getString(R.string.support_btn_label_1), Toast.LENGTH_SHORT).show()
            launchPurchase(productDon1)
        }
        btn3.setOnClickListener {
            Log.d(TAG, "click: support_btn_3")
            // enregistrer le timestamp d'attempt
            attemptTimestampsMs[productDon3] = SystemClock.elapsedRealtime()
            val formatted = productDetailsMap[productDon3]?.oneTimePurchaseOfferDetails?.formattedPrice
            analyticsService.trackDonationAttempt(productDon3, formatted)
            Toast.makeText(this, getString(R.string.support_btn_label_3), Toast.LENGTH_SHORT).show()
            launchPurchase(productDon3)
        }
        btn5.setOnClickListener {
            Log.d(TAG, "click: support_btn_5")
            // enregistrer le timestamp d'attempt
            attemptTimestampsMs[productDon5] = SystemClock.elapsedRealtime()
            val formatted = productDetailsMap[productDon5]?.oneTimePurchaseOfferDetails?.formattedPrice
            analyticsService.trackDonationAttempt(productDon5, formatted)
            Toast.makeText(this, getString(R.string.support_btn_label_5), Toast.LENGTH_SHORT).show()
            launchPurchase(productDon5)
        }

        backBtn.setOnClickListener { finish() }

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                Log.d(TAG, "onBillingSetupFinished: code=${billingResult.responseCode} message=${billingResult.debugMessage}")
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    billingReady = true
                    // (optionnel) on garde les boutons cliquables même si on active explicitement
                    runOnUiThread {
                        btn1.isEnabled = true
                        btn3.isEnabled = true
                        btn5.isEnabled = true
                        // Charger et afficher les prix localisés depuis Google Play Billing
                        loadProductPrices(btn1, btn3, btn5)
                        // Analytics: billing prêt pour les donations
                        analyticsService.trackDonationBillingReady()
                    }
                } else {
                    // Si la configuration échoue, laisser l'utilisateur savoir
                    runOnUiThread {
                        Toast.makeText(
                            this@SupportActivity,
                            getString(R.string.support_billing_not_ready),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                billingReady = false
                Log.w(TAG, "onBillingServiceDisconnected: billing service disconnected")
                // La librairie gère les reconnexions automatiques
                runOnUiThread {
                    Toast.makeText(
                        this@SupportActivity,
                        getString(R.string.support_billing_not_ready),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        })
    }

    private fun productIdToAmountCents(productId: String): Long {
        return when (productId) {
            productDon1 -> 100L
            productDon3 -> 300L
            productDon5 -> 500L
            else -> 0L
        }
    }

    private fun launchPurchase(productId: String) {
        Log.d(TAG, "launchPurchase: requested productId=$productId billingInitialized=${::billingClient.isInitialized} billingClientReady=${billingClient.isReady} billingReady=$billingReady")
        if (!::billingClient.isInitialized || !billingClient.isReady || !billingReady) {
            Log.w(TAG, "launchPurchase: billing not ready")
            // Afficher un dialog plus visible pour l'utilisateur
            runOnUiThread {
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.support_shoopt))
                    .setMessage(getString(R.string.support_billing_not_ready))
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
            return
        }

        // Si on a déjà ProductDetails en cache, l'utiliser directement
        val cached = productDetailsMap[productId]
        if (cached != null) {
            val pdParams = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(cached)
                .build()
            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(pdParams))
                .build()
            Log.d(TAG, "launchBillingFlow: launching flow for productId=$productId (cached)")
            // timing attempt->launch
            attemptTimestampsMs[productId]?.let { attemptTs ->
                val duration = SystemClock.elapsedRealtime() - attemptTs
                analyticsService.trackDonationTimingAttemptToLaunch(productId, duration)
            }
            analyticsService.trackDonationLaunch(productId)
            // enregistrer timestamp de lancement
            launchTimestampsMs[productId] = SystemClock.elapsedRealtime()
            billingClient.launchBillingFlow(this, flowParams)
            return
        }

        // Sinon, requêter les ProductDetails puis lancer le flow
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(productId)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(product))
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            Log.d(TAG, "queryProductDetailsAsync: code=${billingResult.responseCode} msg=${billingResult.debugMessage} listSize=${productDetailsList.size}")
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList.isNotEmpty()) {
                val pd = productDetailsList[0]
                // mettre en cache
                productDetailsMap[productId] = pd
                val pdParams = BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(pd)
                    .build()
                val flowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(listOf(pdParams))
                    .build()
                Log.d(TAG, "launchBillingFlow: launching flow for productId=$productId")
                // timing attempt->launch
                attemptTimestampsMs[productId]?.let { attemptTs ->
                    val duration = SystemClock.elapsedRealtime() - attemptTs
                    analyticsService.trackDonationTimingAttemptToLaunch(productId, duration)
                }
                analyticsService.trackDonationLaunch(productId)
                launchTimestampsMs[productId] = SystemClock.elapsedRealtime()
                billingClient.launchBillingFlow(this, flowParams)
            } else {
                Log.w(TAG, "queryProductDetailsAsync: no product details returned for $productId")
                analyticsService.trackDonationFailure(productId, "no_product_details: ${billingResult.debugMessage}")
                // Dialog explicite pour indiquer le problème et les actions possibles
                runOnUiThread {
                    AlertDialog.Builder(this)
                        .setTitle(getString(R.string.support_shoopt))
                        .setMessage(getString(R.string.support_purchase_error))
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
                Toast.makeText(this, getString(R.string.support_purchase_error), Toast.LENGTH_SHORT).show()
                analyticsService.trackDonationCancel()
            }
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        Log.d(TAG, "onPurchasesUpdated: code=${billingResult.responseCode} msg=${billingResult.debugMessage} purchasesCount=${purchases?.size ?: 0}")
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Toast.makeText(this, getString(R.string.support_purchase_cancelled), Toast.LENGTH_SHORT).show()
            analyticsService.trackDonationFailure(null, "user_canceled")
            analyticsService.trackDonationCancel()
        } else {
            Toast.makeText(this, getString(R.string.support_purchase_error), Toast.LENGTH_SHORT).show()
            analyticsService.trackDonationFailure(null, "billing_error:${billingResult.debugMessage}")
            analyticsService.trackDonationCancel()
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            // Consommer les produits consommables
            if (!purchase.isAcknowledged) {
                val consumeParams = ConsumeParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()

                billingClient.consumeAsync(consumeParams) { billingResult, _ ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        // Utiliser la propriété moderne 'products' (liste des productIds)
                        val prodId = purchase.products.firstOrNull() ?: ""
                        val amountCents = productIdToAmountCents(prodId)
                        // Si on a ProductDetails en cache, récupérer amountMicros réel
                        val pd = productDetailsMap[prodId]
                        val realAmountCents = pd?.oneTimePurchaseOfferDetails?.priceAmountMicros?.let { it / 10000L } ?: amountCents
                        analyticsService.trackDonationConsume(realAmountCents, true, prodId)
                        analyticsService.trackDonationSuccess(if (realAmountCents > 0) realAmountCents else null)
                        // timing launch->purchase
                        launchTimestampsMs[prodId]?.let { launchTs ->
                            val duration = SystemClock.elapsedRealtime() - launchTs
                            analyticsService.trackDonationTimingLaunchToPurchase(prodId, duration)
                        }
                        // afficher dialog + confetti
                        showThanksDialog(realAmountCents)
                    } else {
                        Toast.makeText(this, getString(R.string.support_purchase_error), Toast.LENGTH_SHORT).show()
                        val prodId = purchase.products.firstOrNull() ?: ""
                        val pd = productDetailsMap[prodId]
                        val realAmountCents = pd?.oneTimePurchaseOfferDetails?.priceAmountMicros?.let { it / 10000L }
                        analyticsService.trackDonationConsume(realAmountCents, false, prodId)
                        analyticsService.trackDonationFailure(prodId, "consume_failed:${billingResult.debugMessage}")
                        analyticsService.trackDonationCancel()
                    }
                }
            } else {
                // Déjà acknowledged
                val prodId = purchase.products.firstOrNull() ?: ""
                val amountCents = productIdToAmountCents(prodId)
                val pd = productDetailsMap[prodId]
                val realAmountCents = pd?.oneTimePurchaseOfferDetails?.priceAmountMicros?.let { it / 10000L } ?: amountCents
                analyticsService.trackDonationConsume(realAmountCents, true, prodId)
                analyticsService.trackDonationSuccess(if (realAmountCents > 0) realAmountCents else null)
                // timing launch->purchase
                launchTimestampsMs[prodId]?.let { launchTs ->
                    val duration = SystemClock.elapsedRealtime() - launchTs
                    analyticsService.trackDonationTimingLaunchToPurchase(prodId, duration)
                }
                showThanksDialog(realAmountCents)
            }
        }
    }

    private fun showThanksDialog(amountCents: Long?) {
        try {
            val inflater = LayoutInflater.from(this)
            val view = inflater.inflate(R.layout.dialog_thanks_confetti, null)
            val confettiView: ConfettiView = view.findViewById(R.id.confetti_view)
            val thanksMessage: android.widget.TextView = view.findViewById(R.id.thanks_message)
            val closeBtn: Button = view.findViewById(R.id.thanks_close_button)

            val builder = AlertDialog.Builder(this)
                .setView(view)
                .setCancelable(true)

            val dialog = builder.create()

            dialog.setOnShowListener {
                // lancer l'animation
                confettiView.start(1700L)
            }

            // Mettre à jour le message de remerciement pour inclure le montant si disponible
            if (amountCents != null && amountCents > 0) {
                val amountStr = String.format(java.util.Locale.getDefault(), "%.2f", amountCents.toDouble() / 100.0)
                thanksMessage.text = getString(R.string.support_thanks_with_amount, getString(R.string.support_thanks), "$amountStr ${getCurrencyManager().currentCurrency.value?.symbol ?: ""}".trim())
            } else {
                thanksMessage.text = getString(R.string.support_thanks)
            }

            closeBtn.setOnClickListener {
                confettiView.visibility = View.GONE
                dialog.dismiss()
            }

            dialog.show()
        } catch (_: Exception) {
            // Fallback: afficher un Toast si le dialog échoue
            Toast.makeText(this, getString(R.string.support_thanks), Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Interroge Google Play Billing pour récupérer les ProductDetails (et leurs prix formatés)
     * et met à jour les textes des boutons correspondants.
     */
    private fun loadProductPrices(btn1: Button, btn3: Button, btn5: Button) {
        try {
            if (!::billingClient.isInitialized || !billingClient.isReady || !billingReady) {
                // on ne peut pas interroger les ProductDetails si le client Billing n'est pas prêt
                return
            }

            val productList = listOf(productDon1, productDon3, productDon5).map { prodId ->
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(prodId)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            }

            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(productList)
                .build()

            billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
                Log.d(TAG, "loadProductPrices: code=${billingResult.responseCode} count=${productDetailsList.size}")
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList.isNotEmpty()) {
                    runOnUiThread {
                        // Map productId -> formatted price (local) ou fallback
                        val priceMap = mutableMapOf<String, String>()
                        for (pd in productDetailsList) {
                            val pid = pd.productId
                            val formatted = pd.oneTimePurchaseOfferDetails?.formattedPrice
                                ?: run {
                                    // fallback : formatter le montant fixe (1/3/5 EUR) via CurrencyManager
                                    val fallbackAmount = when (pid) {
                                        productDon1 -> 1.0
                                        productDon3 -> 3.0
                                        productDon5 -> 5.0
                                        else -> 0.0
                                    }
                                    // envoyer événement de fallback
                                    analyticsService.trackDonationPriceFallback(pid, "fallback_to_currency_manager")
                                    this.getCurrencyManager().formatPrice(fallbackAmount)
                                }
                            // mettre en cache ProductDetails
                            productDetailsMap[pid] = pd
                            // envoyer analytics product details (inclut montant en cents si disponible)
                            val amountCents = pd.oneTimePurchaseOfferDetails?.priceAmountMicros?.let { it / 10000L }
                            val currency = pd.oneTimePurchaseOfferDetails?.priceCurrencyCode
                            analyticsService.trackDonationProductDetails(pid, formatted, currency, amountCents)
                            priceMap[pid] = formatted
                        }

                        // Mettre à jour les textes des boutons (conserver le libellé puis afficher le prix local)
                        priceMap[productDon1]?.let { btn1.text = getString(R.string.support_btn_with_price, getString(R.string.support_btn_label_1), it) }
                        priceMap[productDon3]?.let { btn3.text = getString(R.string.support_btn_with_price, getString(R.string.support_btn_label_3), it) }
                        priceMap[productDon5]?.let { btn5.text = getString(R.string.support_btn_with_price, getString(R.string.support_btn_label_5), it) }
                    }
                } else {
                    Log.w(TAG, "loadProductPrices: billing result not OK or empty list")
                    analyticsService.trackDonationFailure(null, "product_details_fetch_failed:${billingResult.debugMessage}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "loadProductPrices: exception ${e.message}")
            analyticsService.trackDonationFailure(null, "product_details_exception:${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::billingClient.isInitialized && billingClient.isReady) {
            billingClient.endConnection()
        }
    }
}
