package com.shoopt.app.utils;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.Transformation;

/**
 * A view that creates a shine effect overlay that can be placed on top of other views
 */
public class ShineEffectView extends View {

    private Paint mPaint;
    private int mShineColor = 0x55FFFFFF; // Semi-transparent white
    private ShineAnimation mAnimation;
    private float mTranslateX = -1f;
    private Path mClipPath;

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
        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setDither(true);

        mClipPath = new Path();

        mAnimation = new ShineAnimation();
        mAnimation.setDuration(2000);
        mAnimation.setRepeatCount(Animation.INFINITE);
        mAnimation.setRepeatMode(Animation.RESTART);
        mAnimation.setInterpolator(new LinearInterpolator());

        setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        setClipToOutline(true);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // Create a circular clip path
        mClipPath.reset();
        mClipPath.addCircle(w / 2f, h / 2f, Math.min(w, h) / 2f, Path.Direction.CW);
    }

    public void startAnimation() {
        setAnimation(mAnimation);
        mAnimation.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // Apply the circular clip path
        canvas.clipPath(mClipPath);

        if (mTranslateX < 0) {
            // Not set yet
            mTranslateX = -getWidth();
        }

        // Create gradient shader
        LinearGradient shader = new LinearGradient(
                mTranslateX, 0, mTranslateX + getWidth()/3, getHeight(),
                new int[] { 0x00FFFFFF, mShineColor, 0x00FFFFFF },
                new float[] { 0f, 0.5f, 1f },
                Shader.TileMode.CLAMP);

        mPaint.setShader(shader);

        // Draw the shine effect
        canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, Math.min(getWidth(), getHeight()) / 2f, mPaint);
    }

    private class ShineAnimation extends Animation {
        @Override
        protected void applyTransformation(float interpolatedTime, Transformation t) {
            mTranslateX = -getWidth() + interpolatedTime * getWidth() * 2;
            invalidate();
        }
    }
}
