package com.dedoware.shoopt.activities

import android.os.Bundle
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
            Toast.makeText(this, getString(R.string.support_btn_label_1), Toast.LENGTH_SHORT).show()
            launchPurchase(productDon1)
        }
        btn3.setOnClickListener {
            Log.d(TAG, "click: support_btn_3")
            Toast.makeText(this, getString(R.string.support_btn_label_3), Toast.LENGTH_SHORT).show()
            launchPurchase(productDon3)
        }
        btn5.setOnClickListener {
            Log.d(TAG, "click: support_btn_5")
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

        // Utiliser l'API moderne: QueryProductDetailsParams
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
                val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(pd)
                    .build()

                val flowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(listOf(productDetailsParams))
                    .build()

                Log.d(TAG, "launchBillingFlow: launching flow for productId=$productId")
                billingClient.launchBillingFlow(this, flowParams)
            } else {
                Log.w(TAG, "queryProductDetailsAsync: no product details returned for $productId")
                // Dialog explicite pour indiquer le problème et les actions possibles
                runOnUiThread {
                    AlertDialog.Builder(this)
                        .setTitle(getString(R.string.support_shoopt))
                        .setMessage(getString(R.string.support_purchase_error) + "\n\n" + "Vérifiez que le produit '$productId' est créé dans la Play Console et qu'il est disponible pour les tests.")
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
            analyticsService.trackDonationCancel()
        } else {
            Toast.makeText(this, getString(R.string.support_purchase_error), Toast.LENGTH_SHORT).show()
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
                        analyticsService.trackDonationSuccess(if (amountCents > 0) amountCents else null)
                        // afficher dialog + confetti
                        showThanksDialog(amountCents)
                    } else {
                        Toast.makeText(this, getString(R.string.support_purchase_error), Toast.LENGTH_SHORT).show()
                        analyticsService.trackDonationCancel()
                    }
                }
            } else {
                // Déjà acknowledged
                val prodId = purchase.products.firstOrNull() ?: ""
                val amountCents = productIdToAmountCents(prodId)
                analyticsService.trackDonationSuccess(if (amountCents > 0) amountCents else null)
                showThanksDialog(amountCents)
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
                thanksMessage.text = getString(R.string.support_thanks) + " (€$amountStr)"
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
                                    this.getCurrencyManager().formatPrice(fallbackAmount)
                                }
                            priceMap[pid] = formatted
                        }

                        // Mettre à jour les textes des boutons (conserver le libellé puis afficher le prix local)
                        priceMap[productDon1]?.let { btn1.text = "${getString(R.string.support_btn_label_1)} - $it" }
                        priceMap[productDon3]?.let { btn3.text = "${getString(R.string.support_btn_label_3)} - $it" }
                        priceMap[productDon5]?.let { btn5.text = "${getString(R.string.support_btn_label_5)} - $it" }
                    }
                } else {
                    Log.w(TAG, "loadProductPrices: billing result not OK or empty list")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "loadProductPrices: exception ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::billingClient.isInitialized && billingClient.isReady) {
            billingClient.endConnection()
        }
    }
}
