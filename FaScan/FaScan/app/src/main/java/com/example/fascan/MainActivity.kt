package com.example.fascan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* result not needed */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        NotificationHelper.createChannel(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.inflateMenu(R.menu.menu_top)
        toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.action_history -> {
                    startActivity(Intent(this, HistoryActivity::class.java)); true
                }
                R.id.action_logout -> {
                    Store.addHistory(this, "Logged out")
                    Store.logout(this)
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish(); true
                }
                else -> false
            }
        }

        val nav = findViewById<BottomNavigationView>(R.id.bottomNav)
        nav.setOnItemSelectedListener { item ->
            val (fragment, title) = when (item.itemId) {
                R.id.nav_forms -> FormsFragment() to "Forms"
                R.id.nav_analytics -> AnalyticsFragment() to "Data"
                else -> DashboardFragment() to "Dashboard"
            }
            toolbar.title = title
            supportFragmentManager.beginTransaction().replace(R.id.container, fragment).commit()
            true
        }
        if (savedInstanceState == null) nav.selectedItemId = R.id.nav_dashboard

        findViewById<FloatingActionButton>(R.id.fabScan).setOnClickListener {
            startActivity(Intent(this, ScannerActivity::class.java))
        }
    }
}
