package com.osfans.trime.ui.main.settings

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.osfans.trime.R
import com.osfans.trime.data.packaging.PackageStateManager
import com.osfans.trime.data.packaging.SchemaPackage
import com.osfans.trime.data.packaging.SchemaPackageManager
import com.osfans.trime.ui.common.buildDialog
import kotlinx.coroutines.launch
import splitties.resources.styledColor
import splitties.views.backgroundColor
import splitties.views.dsl.recyclerview.recyclerView
import splitties.views.recyclerview.verticalLayoutManager

class PackageListFragment : ProgressFragment() {
    private lateinit var adapter: PackageAdapter
    private var currentDialog: AlertDialog? = null

    override fun onDestroyView() {
        super.onDestroyView()
        currentDialog?.dismiss()
        currentDialog = null
    }

    override suspend fun initialize(): View {
        val packages = SchemaPackageManager.discoverPackages()
        val enabledIds = SchemaPackageManager.getAllEnabledSchemaIds()

        return requireContext().recyclerView {
            layoutManager = verticalLayoutManager()
            backgroundColor = styledColor(android.R.attr.colorBackground)
            clipToPadding = false
            adapter = PackageAdapter(
                packages,
                enabledIds,
                onItemClick = { pkg ->
                    showSchemaMultiSelectDialog(pkg, enabledIds[pkg.id] ?: emptySet())
                },
                onRename = { pkg ->
                    showRenameDialog(pkg)
                },
                onDelete = { pkg ->
                    showDeleteDialog(pkg)
                },
            )
            this@recyclerView.adapter = adapter
        }
    }

    private fun refreshPackages() {
        if (!::adapter.isInitialized) return
        val packages = SchemaPackageManager.discoverPackages()
        val enabledIds = SchemaPackageManager.getAllEnabledSchemaIds()
        adapter.updateData(packages, enabledIds)
    }

    private fun showRenameDialog(pkg: com.osfans.trime.data.packaging.SchemaPackage) {
        val editText = EditText(requireContext())
        editText.setText(pkg.name)

        val layout = LinearLayout(requireContext())
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(48, 24, 48, 0)
        layout.addView(editText)

        var pendingNewName: String? = null

        currentDialog = requireContext().buildDialog(R.string.package_rename)
            .setView(layout)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty()) {
                    PackageStateManager.setCustomName(pkg.id, newName)
                    pendingNewName = newName
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
            .also {
                it.setOnDismissListener {
                    pendingNewName?.let { name ->
                        if (::adapter.isInitialized) {
                            adapter.renamePackage(pkg.id, name)
                        }
                    }
                }
            }
    }

    private fun showDeleteDialog(pkg: com.osfans.trime.data.packaging.SchemaPackage) {
        val message = getString(R.string.package_confirm_delete, pkg.name)
        currentDialog = requireContext().buildDialog(R.string.delete)
            .setMessage(message)
            .setPositiveButton(R.string.delete) { _, _ ->
                SchemaPackageManager.deletePackage(pkg.id)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
            .also { it.setOnDismissListener { refreshPackages() } }
    }

    private fun showSchemaMultiSelectDialog(
        pkg: com.osfans.trime.data.packaging.SchemaPackage,
        enabledIdsForPkg: Set<String>,
    ) {
        val schemas = pkg.schemas
        val names = schemas.map { it.name.ifEmpty { it.id } }.toTypedArray()
        val ids = schemas.map { it.id }
        val checked = BooleanArray(schemas.size) { enabledIdsForPkg.contains(ids[it]) }

        currentDialog = requireContext().buildDialog(R.string.select_schemata)
            .apply { setTitle("${pkg.name} — ${getString(R.string.select_schemata)}") }
            .setMultiChoiceItems(names, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val selectedIds = ids.filterIndexed { i, _ -> checked[i] }
                if (selectedIds.isEmpty()) return@setPositiveButton
                lifecycleScope.launch {
                    rime.runOnReady {
                        selectSchemaCrossPackage(selectedIds.first())
                        setEnabledSchemata(selectedIds.toTypedArray())
                        selectSchema(selectedIds.first())
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private class PackageAdapter(
        private var packages: List<com.osfans.trime.data.packaging.SchemaPackage>,
        private var enabledIds: Map<String, Set<String>>,
        private val onItemClick: (com.osfans.trime.data.packaging.SchemaPackage) -> Unit,
        private val onRename: (com.osfans.trime.data.packaging.SchemaPackage) -> Unit,
        private val onDelete: (com.osfans.trime.data.packaging.SchemaPackage) -> Unit,
    ) : RecyclerView.Adapter<PackageAdapter.ViewHolder>() {

        fun updateData(
            newPackages: List<com.osfans.trime.data.packaging.SchemaPackage>,
            newEnabledIds: Map<String, Set<String>>,
        ) {
            packages = newPackages
            enabledIds = newEnabledIds
            notifyDataSetChanged()
        }

        fun renamePackage(packageId: String, newName: String) {
            packages = packages.map {
                if (it.id == packageId) it.copy(name = newName) else it
            }.sortedBy { it.name }
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int,
        ): ViewHolder {
            val view =
                LayoutInflater
                    .from(parent.context)
                    .inflate(R.layout.item_package_entry, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(
            holder: ViewHolder,
            @SuppressLint("RecyclerView")
            position: Int,
        ) {
            val pkg = packages[position]
            val pkgEnabled = enabledIds[pkg.id] ?: emptySet()
            val selectedCount = pkg.schemas.count { it.id in pkgEnabled }
            holder.textView.text = "${pkg.name} ($selectedCount/${pkg.schemas.size})"
            holder.itemView.setOnClickListener { onItemClick(pkg) }
            holder.renameBtn.setOnClickListener { onRename(pkg) }
            holder.deleteBtn.setOnClickListener { onDelete(pkg) }
        }

        override fun getItemCount(): Int = packages.size

        class ViewHolder(
            itemView: View,
        ) : RecyclerView.ViewHolder(itemView) {
            val textView: TextView = itemView.findViewById(R.id.package_name)
            val renameBtn: Button = itemView.findViewById(R.id.rename_btn)
            val deleteBtn: Button = itemView.findViewById(R.id.delete_btn)
        }
    }
}
