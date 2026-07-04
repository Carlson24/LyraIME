package com.osfans.trime.data.packaging

import com.osfans.trime.core.SchemaItem
import com.osfans.trime.data.base.DataManager
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.util.yaml.Yaml
import com.osfans.trime.util.yaml.mapping
import com.osfans.trime.util.yaml.sequence
import com.osfans.trime.util.yaml.string
import timber.log.Timber
import java.io.File

data class SchemaPackage(
    val id: String,
    val name: String,
    val schemas: List<SchemaItem>,
    val path: File,
)

object SchemaPackageManager {
    private val prefs by lazy { AppPrefs.defaultInstance() }

    private val excludedDirs = setOf("themes", "textmate", "voice", "build", ".cache", "lib")

    val activePackageId: String
        get() = prefs.profile.activePackageId.getValue()

    fun setActivePackageId(id: String) {
        prefs.profile.activePackageId.setValue(id)
    }

    private fun isPackageDir(dir: File): Boolean {
        if (!dir.isDirectory) return false
        if (dir.name in excludedDirs) return false
        if (dir.name.startsWith(".")) return false
        val hasDefault = File(dir, "default.yaml").exists()
        val hasSchema = dir.listFiles()?.any { it.name.endsWith(".schema.yaml") } ?: false
        if (!hasDefault) {
            Timber.d("discoverPackages: ${dir.absolutePath} skipped, no default.yaml")
        }
        if (!hasSchema) {
            Timber.d("discoverPackages: ${dir.absolutePath} skipped, no .schema.yaml files")
        }
        return hasDefault && hasSchema
    }

    private fun buildPackage(dir: File): SchemaPackage {
        val id = dir.name
        val schemas = discoverSchemasInDir(dir)
        return SchemaPackage(id = id, name = id, schemas = schemas, path = dir)
    }

    fun discoverPackages(): List<SchemaPackage> {
        val candidates = mutableMapOf<String, File>()

        // 扫描共享目录（APK 同步的包）
        val sharedDir = DataManager.externalFilesDir
        Timber.d("discoverPackages: scanning sharedDir=$sharedDir, exists=${sharedDir.exists()}")
        if (sharedDir.exists() && sharedDir.isDirectory) {
            val sharedChildren = sharedDir.listFiles()?.filter { it.isDirectory }
            Timber.d("discoverPackages: sharedDir children: ${sharedChildren?.map { it.name }}")
            sharedChildren?.forEach { dir ->
                if (isPackageDir(dir)) {
                    Timber.d("discoverPackages: found package in shared: ${dir.name}")
                    candidates.putIfAbsent(dir.name, dir)
                }
            }
        }

        // 扫描用户目录（用户安装的包，优先级高于共享目录中同 ID 的包）
        val userDir = DataManager.userDataBaseDir
        Timber.d("discoverPackages: scanning userDir=$userDir, exists=${userDir.exists()}")
        if (userDir.exists() && userDir.isDirectory) {
            val userChildren = userDir.listFiles()?.filter { it.isDirectory }
            Timber.d("discoverPackages: userDir children: ${userChildren?.map { it.name }}")
            userChildren?.forEach { dir ->
                if (isPackageDir(dir)) {
                    Timber.d("discoverPackages: found package in userDir: ${dir.name}")
                    candidates[dir.name] = dir
                }
            }
        }

        val result = candidates.values.map { buildPackage(it) }.sortedBy { it.name }
        Timber.d("discoverPackages: result size=${result.size}, packages=${result.map { it.id }}")
        result.forEach { pkg ->
            Timber.d("discoverPackages: pkg id=${pkg.id} name=${pkg.name} schemas=${pkg.schemas.map { it.id }}")
        }
        return result
    }

    private fun discoverSchemasInDir(dir: File): List<SchemaItem> = dir.listFiles()
        ?.filter { it.name.endsWith(".schema.yaml") }
        ?.map { file ->
            val id = file.nameWithoutExtension.removeSuffix(".schema")
            val name = runCatching {
                Yaml.parseToYamlNode(file.readText()).mapping
                    ?.get("schema")?.mapping
                    ?.get("name")?.string ?: ""
            }.getOrDefault("")
            SchemaItem(id = id, name = name)
        }
        ?.sortedBy { it.name }
        ?: emptyList()

    fun getActivePackage(): SchemaPackage? = discoverPackages().find { it.id == activePackageId }

    fun getEnabledSchemaIds(packageId: String): Set<String> {
        val customFile = File(DataManager.userDataBaseDir, "$packageId/default.custom.yaml")
        if (!customFile.exists()) {
            Timber.d("getEnabledSchemaIds: $customFile not found")
            return emptySet()
        }
        return try {
            val root = Yaml.parseToYamlNode(customFile.readText()).mapping ?: return emptySet()
            val patch = root["patch"]?.mapping ?: return emptySet()
            val schemaList = patch["schema_list"]?.sequence ?: return emptySet()
            schemaList.mapNotNull { it.mapping?.get("schema")?.string }.toSet()
        } catch (e: Exception) {
            Timber.w(e, "getEnabledSchemaIds: failed to parse $customFile")
            emptySet()
        }
    }

    fun getAllEnabledSchemaIds(): Map<String, Set<String>> {
        return discoverPackages().associate { pkg ->
            pkg.id to getEnabledSchemaIds(pkg.id)
        }
    }

    fun getAllSchemas(): List<Pair<SchemaItem, SchemaPackage>> {
        val result = discoverPackages().flatMap { pkg ->
            pkg.schemas.map { it to pkg }
        }.sortedBy { (schema, _) -> schema.name.ifEmpty { schema.id } }
        Timber.d("getAllSchemas: size=${result.size}")
        result.forEach { (schema, pkg) ->
            Timber.d("getAllSchemas: schema=${schema.id} name=${schema.name} pkg=${pkg.id}")
        }
        return result
    }

    fun findPackageForSchema(schemaId: String): String? {
        Timber.d("findPackageForSchema: looking for schemaId=$schemaId")
        return discoverPackages().find { pkg ->
            pkg.schemas.any { it.id == schemaId }
        }?.also {
            Timber.d("findPackageForSchema: found in pkg=${it.id}")
        }?.id
    }

    fun deletePackage(packageId: String): Boolean {
        if (packageId == activePackageId) {
            Timber.w("Cannot delete active package")
            return false
        }
        val pkgDir = File(DataManager.externalFilesDir, packageId)
        return if (pkgDir.exists()) {
            pkgDir.deleteRecursively()
        } else {
            false
        }
    }

    fun installPackage(source: File): Boolean {
        if (!source.isDirectory) return false
        val id = source.name
        val dest = File(DataManager.externalFilesDir, id)
        if (dest.exists()) {
            Timber.w("Package '$id' already installed")
            return false
        }
        return source.copyRecursively(dest, overwrite = false)
    }
}
