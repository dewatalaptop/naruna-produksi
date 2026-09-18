package com.aiappbuilder.narunaproduksi.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aiappbuilder.narunaproduksi.R
import com.aiappbuilder.narunaproduksi.databinding.ActivityLoginBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private const val STATION_EMAIL_DOMAIN = "stasiun.naruna-produksi.local"

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Stay logged in per device, no auto-logout (spec section 6, item 6
        // — one shared tablet per station) — skip the login screen entirely
        // if a session already exists.
        if (auth.currentUser != null) {
            goToQueue()
            return
        }

        binding.loginButton.setOnClickListener { attemptLogin() }
    }

    private fun attemptLogin() {
        val username = binding.usernameInput.text.toString().trim().lowercase()
        val password = binding.passwordInput.text.toString()
        if (username.isEmpty() || password.isEmpty()) {
            showError(getString(R.string.login_error_empty))
            return
        }

        setLoading(true)
        val email = "$username@$STATION_EMAIL_DOMAIN"
        lifecycleScope.launch {
            try {
                auth.signInWithEmailAndPassword(email, password).await()
                goToQueue()
            } catch (e: FirebaseAuthInvalidUserException) {
                showLoginFailed()
            } catch (e: FirebaseAuthInvalidCredentialsException) {
                showLoginFailed()
            } catch (e: Exception) {
                showError(getString(R.string.login_error_network))
            } finally {
                setLoading(false)
            }
        }
    }

    private fun showLoginFailed() {
        showError(getString(R.string.login_error_failed))
    }

    private fun showError(message: String) {
        binding.loginError.text = message
        binding.loginError.visibility = View.VISIBLE
    }

    private fun setLoading(loading: Boolean) {
        binding.loginProgress.visibility = if (loading) View.VISIBLE else View.GONE
        binding.loginButton.isEnabled = !loading
    }

    private fun goToQueue() {
        startActivity(Intent(this, QueueActivity::class.java))
        finish()
    }
}
