package com.osfans.trime.ui.main.settings

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.osfans.trime.R
import com.osfans.trime.data.packaging.SchemaPackageManager
import com.osfans.trime.ui.common.buildDialog
import kotlinx.coroutines.launch
import splitties.resources.styledColor
import splitties.views.backgroundColor
import splitties.views.dsl.recyclerview.recyclerView
import splitties.views.recyclerview.verticalLayoutManager

class PackageListFragment : ProgressFragment() {
    override suspend fun initialize(): View {
        val packages = SchemaPackageManager.discoverPackages()
        val enabledIds = SchemaPackageManager.getAllEnabledSchemaIds()

        return requireContext().recyclerView {
            layoutManager = verticalLayoutManager()
            backgroundColor = styledColor(android.R.attr.colorBackground)
            clipToPadding = false
            adapter = PackageAdapter(packages, enabledIds) { pkg ->
                showSchemaMultiSelectDialog(pkg, enabledIds[pkg.id] ?: emptySet())
            }
        }
    }

    private fun showSchemaMultiSelectDialog(
        pkg: com.osfans.trime.data.packaging.SchemaPackage,
        enabledIdsForPkg: Set<String>,
    ) {
        val schemas = pkg.schemas
        val names = schemas.map { it.name.ifEmpty { it.id } }.toTypedArray()
        val ids = schemas.map { it.id }
        val checked = BooleanArray(schemas.size) { enabledIdsForPkg.contains(ids[it]) }

        requireContext().buildDialog(R.string.select_schemata)
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
        private val packages: List<com.osfans.trime.data.packaging.SchemaPackage>,
        private val enabledIds: Map<String, Set<String>>,
        private val onItemClick: (com.osfans.trime.data.packaging.SchemaPackage) -> Unit,
    ) : RecyclerView.Adapter<PackageAdapter.ViewHolder>() {

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int,
        ): ViewHolder {
            val view =
                LayoutInflater
                    .from(parent.context)
                    .inflate(android.R.layout.simple_list_item_1, parent, false)
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
        }

        override fun getItemCount(): Int = packages.size

        class ViewHolder(
            itemView: View,
        ) : RecyclerView.ViewHolder(itemView) {
            val textView: TextView = itemView as TextView
        }
    }
}
