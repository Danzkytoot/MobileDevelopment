package com.example.fascan

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class FormsFragment : Fragment(R.layout.fragment_forms) {

    private val forms = mutableListOf<FormTemplate>()
    private lateinit var formAdapter: FormAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        formAdapter = FormAdapter(forms,
            onEdit = { openEditor(forms[it].name) },
            onDelete = { confirmDelete(it) })

        view.findViewById<RecyclerView>(R.id.rvForms).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = formAdapter
        }
        view.findViewById<Button>(R.id.btnAddForm).setOnClickListener { openEditor(null) }
    }

    // reload every time we come back from the editor
    override fun onResume() {
        super.onResume()
        forms.clear()
        forms.addAll(Store.getForms(requireContext()))
        formAdapter.notifyDataSetChanged()
    }

    private fun openEditor(name: String?) {
        val intent = Intent(requireContext(), FormEditorActivity::class.java)
        if (name != null) intent.putExtra(FormEditorActivity.EXTRA_FORM, name)
        startActivity(intent)
    }

    private fun confirmDelete(position: Int) {
        val name = forms[position].name
        AlertDialog.Builder(requireContext())
            .setTitle("Delete form?")
            .setMessage("\"$name\" will be removed.")
            .setPositiveButton("Delete") { _, _ ->
                Store.deleteForm(requireContext(), name)
                Store.addHistory(requireContext(), "Deleted form \"$name\"")
                forms.removeAt(position)
                formAdapter.notifyItemRemoved(position)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

class FormAdapter(
    private val items: List<FormTemplate>,
    private val onEdit: (Int) -> Unit,
    private val onDelete: (Int) -> Unit
) : RecyclerView.Adapter<FormAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.tvFormName)
        val info: TextView = v.findViewById(R.id.tvFormInfo)
        val delete: Button = v.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_form, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val f = items[position]
        holder.name.text = f.name
        holder.info.text = "${f.questions.size} question(s). Tap to edit"
        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onEdit(pos)
        }
        holder.delete.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onDelete(pos)
        }
    }

    override fun getItemCount() = items.size
}
