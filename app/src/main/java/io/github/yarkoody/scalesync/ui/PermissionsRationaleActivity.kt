package io.github.yarkoody.scalesync.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Shown by Health Connect when you tap the privacy policy link on the permissions screen. */
class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Column(Modifier.padding(24.dp)) {
                    Text("My Health privacy", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "My Health reads your Xiaomi scale over Bluetooth and writes the weigh-ins to " +
                            "Health Connect. It reads sleep, heart rate, HRV, steps, oxygen, breathing rate and " +
                            "weight from Health Connect to show them in one place. Your data and medical documents " +
                            "stay on this phone. They only leave it when you choose to send something to an AI app " +
                            "such as Claude, ChatGPT or Gemini, or, if you signed in with ChatGPT, when you tap " +
                            "Ask here, which sends that summary to OpenAI on your ChatGPT plan.",
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
    }
}
