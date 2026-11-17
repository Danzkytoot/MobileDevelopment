package com.example.fascan

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment

class AnalyticsFragment : Fragment(R.layout.fragment_analytics) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val records = Store.getRecords(requireContext())
        val rows = records.mapIndexed { i, r ->
            "${i + 1}  |  ${r.formName}  |  ${r.status}  |  " +
                "${Store.format(r.time, "HH:mm")}  |  ${Store.format(r.time, "MM-dd-yy")}"
        }.ifEmpty { listOf("No scans yet. Tap the camera button to scan a form.") }

        val lv = view.findViewById<ListView>(R.id.lvRecords)
        lv.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, rows)
        lv.setOnItemClickListener { _, _, pos, _ ->
            if (pos < records.size) {
                val r = records[pos]
                AlertDialog.Builder(requireContext())
                    .setTitle(r.formName)
                    .setMessage("Status: ${r.status}\n${Store.format(r.time, "MMM dd, yyyy HH:mm")}\n\n${r.text}")
                    .setPositiveButton("Close", null)
                    .show()
            }
        }
    }
}
