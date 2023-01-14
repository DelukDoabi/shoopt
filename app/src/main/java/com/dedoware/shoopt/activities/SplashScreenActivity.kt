package com.dedoware.shoopt.activities

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.dedoware.shoopt.R
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


class SplashScreenActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.splash_screen)

        supportActionBar?.hide()

        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        val executor = Executors.newSingleThreadScheduledExecutor()
        executor.schedule({
            // Code to redirect to main activity
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            finish()
        }, 3000, TimeUnit.MILLISECONDS)  // redirect after 3 seconds
    }
}