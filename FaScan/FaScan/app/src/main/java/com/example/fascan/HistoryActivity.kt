package com.example.fascan

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class HistoryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)
        findViewById<Button>(R.id.btnClear).setOnClickListener {
            Store.clearHistory(this); refresh()
        }
        refresh()
    }

    private fun refresh() {
        // newest first
        val items = Store.getHistory(this).reversed().map {
            "${Store.format(it.time, "MMM dd, HH:mm")}  -  ${it.action}"
        }
        findViewById<ListView>(R.id.lvHistory).adapter =
            ArrayAdapter(this, android.R.layout.simple_list_item_1, items)
        findViewById<TextView>(R.id.tvEmpty).visibility =
            if (items.isEmpty()) View.VISIBLE else View.GONE
    }
}
