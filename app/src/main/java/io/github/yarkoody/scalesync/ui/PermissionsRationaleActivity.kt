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
                    Text("Scale Sync privacy", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Scale Sync reads your Xiaomi scale over Bluetooth and writes the weigh-ins to " +
                            "Health Connect on this phone. It only writes, never reads, your health data, " +
                            "and it has no internet access, so nothing leaves your phone.",
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
    }
}
