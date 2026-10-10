package me.rerere.rikkahub.data.files

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.datastore.SettingsStore

class SkillManager(
    private val context: Context,
    private val settingsStore: SettingsStore,
) {
    companion object {
        private const val TAG = "SkillManager"

        // 内置技能：以散文件形式打包在 assets/skills/<技能名>/... 中，
        // 首次启动或版本升级时复制到用户的 skills 运行时目录，实现开箱即用。
        private const val BUNDLED_SKILLS_ASSET_DIR = "skills"
        private const val BUNDLED_PREFS = "bundled_skills"
        private const val BUNDLED_VERSION_KEY = "installed_version"

        // 每次更新 assets/skills/ 里的内置技能后，把这个版本号 +1，
        // 即可让老用户在下次启动时补齐新增/更新的内置技能。
        // v2: 新增 reverse-skills 7 个技能(rev-frida/idapython/ios-dump/struct/symbol/u3d-dump/unicorn-debug) + 更新 mt-mcp-apk-analyzer。
        // v3: 新增 ad-removal(去广告) / noroot-hook(免root hook) / ai-reverse-workflow(AI逆向工作流)。
        // v4: 新增 6 个高阶逆向技能（内核Hook/SVC追踪/内存完整性绕过/反OLLVM/符号执行/VMP去虚化）
        // v5: mt-mcp SKILL 加"省磁盘铁律"（分析用 temporary=true + 用完 mt_apk_close，防工作区堆积）
        private const val BUNDLED_SKILLS_VERSION = 5
    }

    fun getSkillsDir(): File {
        val dir = context.filesDir.resolve(FileFolders.SKILLS)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * 首次启动（或内置技能版本升级）时，将 assets/skills/ 下预置的技能散文件复制到用户的 skills
     * 运行时目录，让用户打开 App 即可在技能列表看到并直接开启，省去手动导入。
     *
     * 策略：
     * - 用 SharedPreferences 记录已安装的内置版本号，只有版本号落后于 [BUNDLED_SKILLS_VERSION] 时才复制。
     * - 仅当目标技能目录不存在时才写入，绝不覆盖用户已有的同名技能（避免抹掉用户的本地修改）。
     * - 复用与运行时相同的 [SkillPaths] 路径校验，防止目录穿越。
     */
    suspend fun installBundledSkillsIfNeeded() = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(BUNDLED_PREFS, Context.MODE_PRIVATE)
        val installedVersion = prefs.getInt(BUNDLED_VERSION_KEY, 0)
        if (installedVersion >= BUNDLED_SKILLS_VERSION) return@withContext

        runCatching {
            val skillsDir = getSkillsDir()
            val assets = context.assets

            // assets/skills 下的每个一级子目录即一个内置技能
            val bundledSkillNames = assets.list(BUNDLED_SKILLS_ASSET_DIR)?.toList() ?: emptyList()
            var installedSkills = 0
            var skippedSkills = 0

            for (skillName in bundledSkillNames) {
                val skillDir = SkillPaths.resolveSkillDir(skillsDir, skillName)
                if (skillDir == null) {
                    Log.w(TAG, "installBundledSkills: illegal skill name '$skillName', skipped")
                    continue
                }
                // 已存在则跳过，保护用户的本地修改
                if (skillDir.exists()) {
                    skippedSkills++
                    continue
                }
                val assetSkillPath = "$BUNDLED_SKILLS_ASSET_DIR/$skillName"
                if (copyAssetDir(assets, assetSkillPath, skillDir)) {
                    installedSkills++
                }
            }

            prefs.edit().putInt(BUNDLED_VERSION_KEY, BUNDLED_SKILLS_VERSION).apply()
            Log.i(TAG, "installBundledSkills: installed $installedSkills skills, skipped $skippedSkills existing")
        }.onFailure {
            Log.e(TAG, "installBundledSkills failed", it)
        }
    }

    /**
     * 递归复制 assets 目录到目标文件夹。返回是否至少写入了一个文件。
     *
     * AssetManager 无法直接区分文件与目录：list() 返回非空视为目录，为空则尝试当作文件打开。
     */
    private fun copyAssetDir(assets: AssetManager, assetPath: String, targetDir: File): Boolean {
        val children = runCatching { assets.list(assetPath) }.getOrNull() ?: emptyArray()
        var wroteAny = false

        if (children.isEmpty()) {
            // 叶子节点：当作文件复制
            runCatching {
                assets.open(assetPath).use { input ->
                    targetDir.parentFile?.mkdirs()
                    targetDir.outputStream().use { output -> input.copyTo(output) }
                }
                wroteAny = true
            }.onFailure {
                Log.w(TAG, "copyAssetDir: failed to copy file '$assetPath'", it)
            }
            return wroteAny
        }

        // 目录：递归处理每个子项
        targetDir.mkdirs()
        for (child in children) {
            val childTarget = File(targetDir, child)
            if (copyAssetDir(assets, "$assetPath/$child", childTarget)) {
                wroteAny = true
            }
        }
        return wroteAny
    }

    fun listSkills(): List<SkillMetadata> {
        val skillsDir = getSkillsDir()
        return skillsDir.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { dir ->
                val skillFile = dir.resolve("SKILL.md")
                if (!skillFile.exists()) return@mapNotNull null
                parseSkillFile(skillFile, dir)
            }
            ?: emptyList()
    }

    fun readSkillBody(skillName: String): String? {
        val skillFile = resolveSkillDir(skillName)?.resolve("SKILL.md") ?: return null
        if (!skillFile.exists()) return null
        return SkillFrontmatterParser.extractBody(skillFile.readText())
    }

    fun readSkillContent(skillName: String): String? {
        val skillFile = resolveSkillDir(skillName)?.resolve("SKILL.md") ?: return null
        if (!skillFile.exists()) return null
        return skillFile.readText()
    }

    fun saveSkill(name: String, content: String): SkillMetadata? {
        // 通过原子写入(staging + rename)落盘，避免直接 mkdirs 失败时
        // writeText 抛出 FileNotFoundException 导致崩溃
        if (!saveSkillFileBytesAtomically(name, mapOf("SKILL.md" to content.toByteArray()))) {
            return null
        }
        val skillDir = resolveSkillDir(name) ?: return null
        return parseSkillFile(skillDir.resolve("SKILL.md"), skillDir)
    }

    suspend fun deleteSkill(name: String): Boolean = withContext(Dispatchers.IO) {
        val skillDir = resolveSkillDir(name) ?: return@withContext false
        val deleted = skillDir.deleteRecursively()
        if (deleted) {
            settingsStore.update { settings ->
                settings.copy(
                    assistants = settings.assistants.map { assistant ->
                        if (assistant.enabledSkills.contains(name)) {
                            assistant.copy(enabledSkills = assistant.enabledSkills - name)
                        } else {
                            assistant
                        }
                    }
                )
            }
        }
        deleted
    }

    /**
     * 清理所有助手 enabledSkills 中已不存在于磁盘的技能名。
     *
     * 当用户在 App 外直接删除 /skills/ 目录下的技能时，不会走 [deleteSkill] 的清理逻辑，
     * 导致 enabledSkills 残留"幽灵"技能名，使扩展入口角标计数偏大。
     */
    suspend fun pruneOrphanedEnabledSkills(): List<SkillMetadata> = withContext(Dispatchers.IO) {
        val skills = listSkills()
        val existing = skills.mapTo(HashSet()) { it.name }
        settingsStore.update { settings ->
            var changed = false
            val newAssistants = settings.assistants.map { assistant ->
                val pruned = assistant.enabledSkills.filterTo(LinkedHashSet()) { it in existing }
                if (pruned.size != assistant.enabledSkills.size) {
                    changed = true
                    assistant.copy(enabledSkills = pruned)
                } else {
                    assistant
                }
            }
            if (changed) settings.copy(assistants = newAssistants) else settings
        }
        skills
    }

    fun getSkillDir(skillName: String): File? = resolveSkillDir(skillName)

    fun saveSkillFile(skillName: String, relativePath: String, content: String): Boolean {
        val skillDir = resolveSkillDir(skillName) ?: return false
        val target = SkillPaths.resolveSkillFile(skillDir, relativePath) ?: return false
        target.parentFile?.mkdirs()
        target.writeText(content)
        return true
    }

    fun saveSkillFilesAtomically(skillName: String, files: Map<String, String>): Boolean {
        return saveSkillFileBytesAtomically(
            skillName = skillName,
            files = files.mapValues { it.value.toByteArray() },
        )
    }

    fun saveSkillFileBytesAtomically(skillName: String, files: Map<String, ByteArray>): Boolean {
        val skillsDir = getSkillsDir()
        val targetDir = resolveSkillDir(skillName) ?: return false
        val stagingDir = createTempSkillDir(skillsDir, skillName, "staging") ?: return false
        var backupDir: File? = null

        try {
            for ((relativePath, content) in files) {
                val target = SkillPaths.resolveSkillFile(stagingDir, relativePath) ?: return false
                target.parentFile?.mkdirs()
                target.writeBytes(content)
            }

            if (!stagingDir.resolve("SKILL.md").exists()) return false

            if (targetDir.exists()) {
                backupDir = createTempSkillDir(skillsDir, skillName, "backup") ?: return false
                if (!targetDir.renameTo(backupDir)) return false
            }

            if (!stagingDir.renameTo(targetDir)) {
                if (backupDir != null && !targetDir.exists()) {
                    backupDir.renameTo(targetDir)
                }
                return false
            }

            backupDir?.deleteRecursively()
            return true
        } catch (e: Exception) {
            Log.w(TAG, "saveSkillFilesAtomically: Failed to save $skillName", e)
            if (backupDir != null && !targetDir.exists()) {
                backupDir.renameTo(targetDir)
            }
            return false
        } finally {
            if (stagingDir.exists()) {
                stagingDir.deleteRecursively()
            }
            if (backupDir?.exists() == true && targetDir.exists()) {
                backupDir.deleteRecursively()
            }
        }
    }

    fun deleteSkillFile(skillName: String, relativePath: String): Boolean {
        val skillDir = resolveSkillDir(skillName) ?: return false
        val target = SkillPaths.resolveSkillFile(skillDir, relativePath) ?: return false
        return target.delete()
    }

    fun resolveSkillFile(skillName: String, relativePath: String): File? {
        val skillDir = resolveSkillDir(skillName) ?: return null
        return SkillPaths.resolveSkillFile(skillDir, relativePath)
    }

    private fun resolveSkillDir(skillName: String): File? {
        return SkillPaths.resolveSkillDir(getSkillsDir(), skillName)
    }

    private fun createTempSkillDir(skillsRoot: File, skillName: String, suffix: String): File? {
        repeat(100) { attempt ->
            val candidate = skillsRoot.resolve(".$skillName.$suffix.$attempt.tmp")
            if (!candidate.exists() && candidate.mkdirs()) {
                return candidate
            }
        }
        return null
    }

    private fun parseSkillFile(skillFile: File, skillDir: File): SkillMetadata? {
        return runCatching {
            val content = skillFile.readText()
            val frontmatter = SkillFrontmatterParser.parse(content)
            val name = frontmatter["name"]?.takeIf { it.isNotBlank() } ?: return null
            val description = frontmatter["description"]?.takeIf { it.isNotBlank() } ?: return null
            SkillMetadata(
                name = name,
                description = description,
                compatibility = frontmatter["compatibility"],
                allowedTools = frontmatter["allowed-tools"]?.split(" ")?.filter { it.isNotBlank() } ?: emptyList(),
                skillDir = skillDir,
            )
        }.getOrElse {
            Log.w(TAG, "parseSkillFile: Failed to parse ${skillFile.absolutePath}", it)
            null
        }
    }
}

data class SkillMetadata(
    val name: String,
    val description: String,
    val compatibility: String? = null,
    val allowedTools: List<String> = emptyList(),
    val skillDir: File,
) {
    val skillFile: File get() = skillDir.resolve("SKILL.md")
}
