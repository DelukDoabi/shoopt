package com.dedoware.shoopt.utils

import android.app.Activity
import android.view.View
import com.dedoware.shoopt.R

/**
 * Extension functions to easily integrate the AddFirstProductGuide into your activities.
 */

fun Activity.startFirstProductGuideAfterOnboarding(addProductButton: View, analyzeButton: View) {
    val guide = AddFirstProductGuide(this)
    guide.checkAndStartGuideAfterOnboarding()
    // Optionally, you can call guide.showAddProductButtonGuide(addProductButton) here if needed
}

fun Activity.continueFirstProductGuideIfNeeded(
    addProductButton: View? = null,
    scanBarcodeButton: View? = null,
    manualEntryButton: View? = null,
    scannerView: View? = null,
    barcodeField: View? = null,
    photoButton: View? = null,
    photoPreview: View? = null,
    formLayout: View? = null,
    saveButton: View? = null,
    rootView: View? = null,
    analyzeButton: View? = null
) {
    val guide = AddFirstProductGuide(this)
    when (guide.getCurrentGuideState()) {
        AddFirstProductGuide.GuideState.MAIN_SCREEN_ADD_BUTTON ->
            addProductButton?.let { guide.showAddProductButtonGuide(it) }
        AddFirstProductGuide.GuideState.PRODUCT_CHOICE_SCREEN ->
            if (scanBarcodeButton != null && manualEntryButton != null) {
                guide.showProductChoiceGuide(scanBarcodeButton, manualEntryButton)
            }
        AddFirstProductGuide.GuideState.BARCODE_SCANNER_SCREEN ->
            scannerView?.let { guide.showBarcodeScannerGuide(it) }
        AddFirstProductGuide.GuideState.PRODUCT_FORM_BARCODE_FILLED ->
            barcodeField?.let { guide.showBarcodeFilledGuide(it) }
        AddFirstProductGuide.GuideState.PRODUCT_FORM_PHOTO_BUTTON ->
            photoButton?.let { guide.showTakePhotoButtonGuide(it) }
        AddFirstProductGuide.GuideState.PRODUCT_FORM_PHOTO_WARNING ->
            photoPreview?.let { guide.showPhotoWarningGuide(it) }
        AddFirstProductGuide.GuideState.PRODUCT_FORM_FIELDS_AUTOFILLED ->
            formLayout?.let { guide.showFieldsAutofilledGuide(it) }
        AddFirstProductGuide.GuideState.PRODUCT_FORM_SAVE_BUTTON ->
            saveButton?.let { guide.showSaveProductButtonGuide(it) }
        AddFirstProductGuide.GuideState.MAIN_SCREEN_PRODUCT_ADDED ->
            rootView?.let { guide.showProductAddedGuide(it) }
        AddFirstProductGuide.GuideState.MAIN_SCREEN_ANALYZE_BUTTON ->
            analyzeButton?.let { guide.showAnalyzeButtonGuide(it) }
        else -> {}
    }
}

