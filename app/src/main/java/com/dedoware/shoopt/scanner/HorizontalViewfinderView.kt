package com.dedoware.shoopt.scanner

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import com.journeyapps.barcodescanner.ViewfinderView

/**
 * ViewfinderView personnalisée qui dessine une ligne de scan horizontale
 * au lieu de la ligne verticale standard.
 */
class HorizontalViewfinderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewfinderView(context, attrs) {

    // Paint pour la ligne de scan avec une couleur rouge vive
    private val laserPaint: Paint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
        // Rendre la ligne plus visible
        alpha = 255 // Complètement opaque
    }

    private var scanLineAlpha = 1.0f

    /**
     * Définir l'alpha (transparence) de la ligne de scan
     */
    fun setScanLineAlpha(alpha: Float) {
        this.scanLineAlpha = alpha
        invalidate() // Redessiner la vue
    }

    /**
     * Dessiner la ligne de scan horizontale
     * Attention : cette méthode est appelée après le dessin standard du ViewfinderView
     * donc nous ne devons pas effacer ce qui a déjà été dessiné
     */
    override fun onDraw(canvas: Canvas) {
        // Appel à la méthode parente pour dessiner le cadre et garder l'aperçu caméra visible
        super.onDraw(canvas)

        // Récupérer le rectangle de cadrage
        val framingRect = framingRect ?: return

        // Appliquer l'alpha à la peinture
        laserPaint.alpha = (scanLineAlpha * 255).toInt()

        // Calculer la position Y de la ligne horizontale (au milieu)
        val lineY = framingRect.top + (framingRect.height() * 0.5f).toInt()

        // Dessiner une ligne horizontale épaisse (8 pixels)
        canvas.drawRect(
            framingRect.left.toFloat(),  // x1 (gauche)
            lineY - 4f,                 // y1 (4 pixels au-dessus du milieu)
            framingRect.right.toFloat(), // x2 (droite)
            lineY + 4f,                 // y2 (4 pixels en-dessous du milieu)
            laserPaint
        )
    }
}
