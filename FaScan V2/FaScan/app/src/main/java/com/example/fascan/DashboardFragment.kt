package com.example.fascan

import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import java.util.Calendar

class DashboardFragment : Fragment(R.layout.fragment_dashboard) {

    // onResume so the numbers refresh after returning from the scanner
    override fun onResume() {
        super.onResume()
        val v = view ?: return
        val ctx = requireContext()
        val records = Store.getRecords(ctx)
        val total = records.size

        val greeting = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 0..11 -> "Good Morning"
            in 12..17 -> "Good Afternoon"
            else -> "Good Evening"
        }
        v.findViewById<TextView>(R.id.tvGreeting).text = "$greeting, ${Store.currentUser(ctx) ?: "User"}!"
        v.findViewById<TextView>(R.id.tvTotal).text = "$total total scans"

        fun bind(barId: Int, textId: Int, status: String) {
            val n = records.count { it.status == status }
            val pct = if (total == 0) 0 else n * 100 / total
            v.findViewById<ProgressBar>(barId).progress = pct
            v.findViewById<TextView>(textId).text = "$n ($pct%)"
        }
        bind(R.id.pbSuccess, R.id.tvSuccess, STATUS_SUCCESS)
        bind(R.id.pbReview, R.id.tvReview, STATUS_REVIEW)
        bind(R.id.pbUnreadable, R.id.tvUnreadable, STATUS_UNREADABLE)
    }
}
