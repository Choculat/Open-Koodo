package sh.margot.open_koodo

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import sh.margot.open_koodo.auth.LoginActivity
import sh.margot.open_koodo.databinding.ActivityMainBinding
import sh.margot.open_koodo.network.KoodoApiClient
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        binding.swipeRefresh.setColorSchemeResources(R.color.koodo_orange)
        binding.swipeRefresh.setOnRefreshListener { load() }
        binding.logoutButton.setOnClickListener { confirmLogout() }
        binding.toolbar.setOnMenuItemClickListener {
            if (it.itemId == R.id.action_logout) { confirmLogout(); true } else false
        }

        // If no local session at all, go straight to login without a network call.
        if (!KoodoApiClient.cookieJar.hasSessionFor("prepaidselfserve.koodomobile.com")) {
            goToLogin(); return
        }
        load()
    }

    private fun load() {
        binding.swipeRefresh.isRefreshing = true
        lifecycleScope.launch {
            if (KoodoApiClient.checkStatus().getOrNull()?.optString("status") != "running") {
                if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED))
                    goToLogin()
                return@launch
            }

            val userD  = async { KoodoApiClient.getUserDetails() }
            val planD  = async { KoodoApiClient.getPlanUsage() }
            val fundsD = async { KoodoApiClient.getFunds() }

            // Account card
            val user = userD.await().getOrNull()
                ?.optJSONObject("response")?.optJSONArray("data")?.optJSONObject(0)
            binding.emailText.text       = user?.optString("emailAddress")?.ifBlank { "—" } ?: "—"
            binding.phoneText.text       = formatPhone(user?.optString("phoneNumber") ?: "")
            binding.accountStateText.text = when (user?.optString("subscriptionCurrentState")) {
                "1" -> "Active ✓"; "0" -> "Inactive"; else -> "—"
            }

            // Plan card
            val planData = planD.await().getOrNull()
                ?.optJSONObject("response")?.optJSONObject("data")
            val expiryRaw = fundsD.await().getOrNull()
                ?.optJSONObject("response")?.optJSONObject("data")?.optString("expiryDate") ?: ""

            if (planData != null) {
                val name  = planData.optJSONObject("planName")?.optString("en") ?: "—"
                val price = planData.optInt("planPrice", 0) / 100
                val sb = StringBuilder("$name\n\$$price/year  •  ${formatRenewal(expiryRaw)}\n")
                val bundles = planData.optJSONArray("bundles")
                if (bundles != null) {
                    for (i in 0 until bundles.length()) {
                        val b = bundles.getJSONObject(i)
                        val used  = b.optString("personalUsed",    "0").toLongOrNull() ?: 0L
                        val limit = b.optString("personalLimit",   "0").toLongOrNull() ?: 0L
                        val left  = b.optString("personalBalance", "0").toLongOrNull() ?: 0L
                        sb.append("\n")
                        sb.append(when (b.optString("unitType")) {
                            "0"  -> "• ${used/60} / ${limit/60} min used  (${left/60} min left)"
                            "2"  -> "• $used / $limit texts used  ($left left)"
                            "3"  -> "• ${bytes(used)} / ${bytes(limit)} used  (${bytes(left)} left)"
                            else -> null
                        } ?: continue)
                    }
                }
                binding.planText.text = sb.toString().trim()
            } else {
                binding.planText.text = "—"
            }

            binding.swipeRefresh.isRefreshing = false
        }
    }

    private fun confirmLogout() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Log out")
            .setMessage("Clear your session and return to the login screen?")
            .setPositiveButton("Log out") { _, _ -> logout() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun logout() {
        KoodoApiClient.cookieJar.clear()
        goToLogin()
    }

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    private fun formatPhone(raw: String): String {
        val d = raw.filter { it.isDigit() }.removePrefix("1")
        return if (d.length == 10) "+1 (${d.substring(0,3)}) ${d.substring(3,6)}-${d.substring(6)}"
        else raw.ifBlank { "—" }
    }

    private fun formatRenewal(raw: String): String = try {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(raw.take(10))!!
        val days = ((date.time - System.currentTimeMillis()) / 86_400_000L).coerceAtLeast(0)
        "renews ${SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(date)} ($days days)"
    } catch (_: Exception) { "" }

    private fun bytes(b: Long) = when {
        b >= 1_073_741_824 -> "${"%.1f".format(b/1_073_741_824.0)} GB"
        b >= 1_048_576     -> "${"%.0f".format(b/1_048_576.0)} MB"
        else               -> "$b B"
    }
}
