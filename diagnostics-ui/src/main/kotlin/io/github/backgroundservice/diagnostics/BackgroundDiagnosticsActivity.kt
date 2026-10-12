package io.github.backgroundservice.diagnostics

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.widget.*
import io.github.backgroundservice.android.BackgroundCapabilities
import io.github.backgroundservice.android.BackgroundDiagnostics

class BackgroundDiagnosticsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val namespace = intent.getStringExtra("namespace") ?: "default"
        val permissions = io.github.backgroundservice.permissions.PermissionDiagnostics.report(
            this,
            intent.getStringExtra("permission_preferences") ?: "background.permissions.$namespace",
            intent.getStringExtra("oem_preferences") ?: "background.oem.$namespace",
        )
        val report = BackgroundCapabilities.read(this).toString() + "\n" + permissions + "\n" +
            BackgroundDiagnostics(this, namespace).report()
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        layout.addView(Button(this).apply { text = "Copy report"; setOnClickListener {
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Background diagnostics", report))
        } })
        layout.addView(ScrollView(this).apply { addView(TextView(this@BackgroundDiagnosticsActivity).apply {
            text = report; setTextIsSelectable(true); setPadding(24, 24, 24, 24)
        }) })
        setContentView(layout)
    }
}
