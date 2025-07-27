package com.shoopt.app.utils;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;

/**
 * A view that creates an elegant shine effect overlay for the splash screen logo
 */
public class ShineEffectView extends View {

    private Paint mShineLinearPaint;
    private Paint mShineGlowPaint;
    private Path mClipPath;

    // Shine effect colors
    private int mShineColor = 0x55FFFFFF; // Semi-transparent white for linear gradient
    private int mGlowColor = 0x40FFFFFF; // Subtle white glow

    // Animation properties
    private float mTranslateX = -1f;
    private float mGlowScale = 0f;
    private ValueAnimator mLinearShineAnimator;
    private ValueAnimator mGlowAnimator;

    public ShineEffectView(Context context) {
        super(context);
        init();
    }

    public ShineEffectView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ShineEffectView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // Initialize paints
        mShineLinearPaint = new Paint();
        mShineLinearPaint.setAntiAlias(true);
        mShineLinearPaint.setDither(true);

        mShineGlowPaint = new Paint();
        mShineGlowPaint.setAntiAlias(true);
        mShineGlowPaint.setDither(true);

        mClipPath = new Path();

        // Make view outline perfectly circular to avoid artifacts
        setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        setClipToOutline(true);

        setupAnimations();
    }

    private void setupAnimations() {
        // Setup linear shine animation (moving gradient)
        mLinearShineAnimator = ValueAnimator.ofFloat(0f, 1f);
        mLinearShineAnimator.setDuration(2500);
        mLinearShineAnimator.setRepeatCount(ValueAnimator.INFINITE);
        mLinearShineAnimator.setInterpolator(new LinearInterpolator());
        mLinearShineAnimator.addUpdateListener(animation -> {
            mTranslateX = -getWidth() + (float) animation.getAnimatedValue() * getWidth() * 2;
            invalidate();
        });

        // Setup pulsing glow animation
        mGlowAnimator = ValueAnimator.ofFloat(0f, 1f, 0f);
        mGlowAnimator.setDuration(3000);
        mGlowAnimator.setRepeatCount(ValueAnimator.INFINITE);
        mGlowAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        mGlowAnimator.addUpdateListener(animation -> {
            mGlowScale = (float) animation.getAnimatedValue();
            invalidate();
        });
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // Create a circular clip path
        mClipPath.reset();
        mClipPath.addCircle(w / 2f, h / 2f, Math.min(w, h) / 2f, Path.Direction.CW);
    }

    public void startAnimations() {
        if (mLinearShineAnimator != null && !mLinearShineAnimator.isRunning()) {
            mLinearShineAnimator.start();
        }
        if (mGlowAnimator != null && !mGlowAnimator.isRunning()) {
            mGlowAnimator.start();
        }
    }

    public void stopAnimations() {
        if (mLinearShineAnimator != null) {
            mLinearShineAnimator.cancel();
        }
        if (mGlowAnimator != null) {
            mGlowAnimator.cancel();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startAnimations();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopAnimations();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // Get dimensions
        final int width = getWidth();
        final int height = getHeight();
        final float centerX = width / 2f;
        final float centerY = height / 2f;
        final float radius = Math.min(width, height) / 2f;

        // Apply circular clipping to avoid square artifacts
        canvas.clipPath(mClipPath);

        // Draw the pulsing glow effect
        if (mGlowScale > 0) {
            RadialGradient glowGradient = new RadialGradient(
                    centerX, centerY, radius * 1.2f,
                    new int[]{mGlowColor, Color.TRANSPARENT},
                    new float[]{0.2f, 1f},
                    Shader.TileMode.CLAMP
            );
            mShineGlowPaint.setShader(glowGradient);
            canvas.drawCircle(centerX, centerY, radius * mGlowScale, mShineGlowPaint);
        }

        // Draw the moving shine effect
        if (mTranslateX >= -width && mTranslateX <= width * 2) {
            // Create a thin diagonal gradient for a refined shine effect
            LinearGradient shineGradient = new LinearGradient(
                    mTranslateX, 0, mTranslateX + width / 4f, height,
                    new int[]{0x00FFFFFF, mShineColor, 0x00FFFFFF},
                    new float[]{0f, 0.5f, 1f},
                    Shader.TileMode.CLAMP
            );
            mShineLinearPaint.setShader(shineGradient);
            canvas.drawCircle(centerX, centerY, radius, mShineLinearPaint);
        }
    }
}
