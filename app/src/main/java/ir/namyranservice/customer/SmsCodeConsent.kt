package ir.namyranservice.customer

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.google.android.gms.auth.api.phone.SmsRetriever
import ir.namyranservice.customer.data.normalizedDigits

@Composable
internal fun rememberSmsCodeConsent(onCode: (String) -> Unit): (() -> Unit) -> Unit {
    val context = LocalContext.current
    val callback = rememberUpdatedState(onCode)
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringExtra(SmsRetriever.EXTRA_SMS_MESSAGE)?.let(::extractSmsCode)?.let(callback.value)
        }
    }
    DisposableEffect(context, consent) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != SmsRetriever.SMS_RETRIEVED_ACTION) return
                val prompt = IntentCompat.getParcelableExtra(intent, SmsRetriever.EXTRA_CONSENT_INTENT, Intent::class.java)
                if (prompt != null) runCatching { consent.launch(prompt) }
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION),
            SmsRetriever.SEND_PERMISSION, null, ContextCompat.RECEIVER_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    return { send ->
        try {
            SmsRetriever.getClient(context).startSmsUserConsent(null).addOnCompleteListener { send() }
        } catch (_: RuntimeException) {
            send()
        }
    }
}

internal fun extractSmsCode(message: String): String? =
    Regex("(?<![0-9])[0-9]{4}(?![0-9])").findAll(normalizedDigits(message))
        .map { it.value }.distinct().toList().singleOrNull()
