package com.example.fascan

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val STATUS_SUCCESS = "Success"
const val STATUS_REVIEW = "Needs Review"
const val STATUS_UNREADABLE = "Unreadable"

/** One question printed on the form. Each option becomes a circle to shade. */
data class Question(val text: String, val options: List<String>)
data class FormTemplate(val name: String, val questions: List<Question>)
data class ScanRecord(val formName: String, val text: String, val status: String, val time: Long)
data class HistoryItem(val action: String, val time: Long)

/** Simple local persistence using SharedPreferences + JSON. */
object Store {
    private const val PREFS = "fascan"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun format(time: Long, pattern: String): String =
        SimpleDateFormat(pattern, Locale.getDefault()).format(Date(time))

    // ----- session -----
    fun currentUser(c: Context): String? = prefs(c).getString("user", null)
    fun login(c: Context, id: String) = prefs(c).edit().putString("user", id).apply()
    fun logout(c: Context) = prefs(c).edit().remove("user").apply()

    // ----- form templates -----
    private fun defaultForms() = mutableListOf(
        FormTemplate(
            "Registration Form", listOf(
                Question("Sex", listOf("Male", "Female")),
                Question("Age group", listOf("18-25", "26-35", "36-45", "46+")),
                Question("Employment status", listOf("Employed", "Student", "Unemployed"))
            )
        )
    )

    fun getForms(c: Context): MutableList<FormTemplate> {
        val raw = prefs(c).getString("form_templates", null) ?: return defaultForms()
        val arr = JSONArray(raw)
        return MutableList(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            val qa = o.getJSONArray("questions")
            FormTemplate(o.getString("name"), List(qa.length()) { j ->
                val q = qa.getJSONObject(j)
                val oa = q.getJSONArray("options")
                Question(q.getString("text"), List(oa.length()) { k -> oa.getString(k) })
            })
        }
    }

    private fun persistForms(c: Context, forms: List<FormTemplate>) {
        val arr = JSONArray()
        for (f in forms) {
            val qa = JSONArray()
            for (q in f.questions) {
                qa.put(JSONObject().put("text", q.text).put("options", JSONArray(q.options)))
            }
            arr.put(JSONObject().put("name", f.name).put("questions", qa))
        }
        prefs(c).edit().putString("form_templates", arr.toString()).apply()
    }

    /** Adds a new form, or replaces the one called [original]. Returns false if the name is taken. */
    fun saveForm(c: Context, original: String?, template: FormTemplate): Boolean {
        val list = getForms(c)
        if (list.any { it.name.equals(template.name, true) && !it.name.equals(original, true) }) return false
        val idx = list.indexOfFirst { it.name == original }
        if (idx >= 0) list[idx] = template else list.add(template)
        persistForms(c, list)
        return true
    }

    fun deleteForm(c: Context, name: String) {
        val list = getForms(c)
        list.removeAll { it.name == name }
        persistForms(c, list)
    }

    // ----- scan records -----
    fun getRecords(c: Context): List<ScanRecord> {
        val arr = JSONArray(prefs(c).getString("records", "[]"))
        return List(arr.length()) {
            val o = arr.getJSONObject(it)
            ScanRecord(o.getString("form"), o.getString("text"), o.getString("status"), o.getLong("time"))
        }
    }

    fun addRecord(c: Context, r: ScanRecord) {
        val arr = JSONArray(prefs(c).getString("records", "[]"))
        arr.put(JSONObject().put("form", r.formName).put("text", r.text)
            .put("status", r.status).put("time", r.time))
        prefs(c).edit().putString("records", arr.toString()).apply()
    }

    // ----- activity history -----
    fun getHistory(c: Context): List<HistoryItem> {
        val arr = JSONArray(prefs(c).getString("history", "[]"))
        return List(arr.length()) {
            val o = arr.getJSONObject(it)
            HistoryItem(o.getString("action"), o.getLong("time"))
        }
    }

    fun addHistory(c: Context, action: String) {
        val old = JSONArray(prefs(c).getString("history", "[]"))
        val arr = JSONArray()
        for (i in maxOf(0, old.length() - 99) until old.length()) arr.put(old.get(i))
        arr.put(JSONObject().put("action", action).put("time", System.currentTimeMillis()))
        prefs(c).edit().putString("history", arr.toString()).apply()
    }

    fun clearHistory(c: Context) = prefs(c).edit().remove("history").apply()
}
