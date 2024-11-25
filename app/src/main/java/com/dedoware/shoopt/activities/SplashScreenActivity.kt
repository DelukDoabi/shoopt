package com.dedoware.shoopt.activities

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.dedoware.shoopt.R
import com.google.firebase.auth.FirebaseAuth
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


class SplashScreenActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContentView(R.layout.splash_screen)

        forceFullScreen()

        displayVersion()

        redirectToMainScreen()
    }

    private fun forceFullScreen() {
        supportActionBar?.hide()

        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
    }

    private fun redirectToMainScreen() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        val targetActivity = if (currentUser != null) MainActivity::class.java else LoginActivity::class.java
        val executor = Executors.newSingleThreadScheduledExecutor()
        executor.schedule({
            val intent = Intent(this, targetActivity)
            startActivity(intent)
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            finish()
        }, 2000, TimeUnit.MILLISECONDS)
    }

    private fun displayVersion() {
        val versionName = packageManager.getPackageInfo(packageName, 0).versionName
        val versionTextView: TextView = findViewById(R.id.shoopt_version_TV)
        versionTextView.text = "Version: $versionName"
    }
}