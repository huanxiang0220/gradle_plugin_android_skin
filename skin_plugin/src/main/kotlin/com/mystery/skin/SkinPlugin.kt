package com.mystery.skin

import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File

/**
 * Skin plugin
 * 用于自动构建 app_skin 模块的 apk 并复制到 app 模块的 assets 目录
 */
class SkinPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        println("SkinPlugin applying to ${project.path}...")

        // 创建扩展配置
        val extension = project.extensions.create("skin", SkinExtension::class.java)
        
        project.afterEvaluate {
            // 仅在 Android Application 模块生效
            if (!project.plugins.hasPlugin("com.android.application")) {
                return@afterEvaluate
            }

            // 1. 找到 app_skin project
            var skinProjectName = extension.skinProjectName
            if (skinProjectName.startsWith(":")) {
                skinProjectName = skinProjectName.substring(1)
            }
            val skinProject = project.rootProject.findProject(":$skinProjectName")
            if (skinProject == null) {
                println("SkinPlugin: Project :$skinProjectName not found.")
                return@afterEvaluate
            }

            // 2. 针对 debug 变体创建复制任务
            val variantName = "debug"
            val capVariantName = variantName.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

            val copySkinTask = project.tasks.create("copySkin${capVariantName}Apk") { task ->
                task.group = "skin"
                task.description = "Builds and copies $skinProjectName $variantName apk to assets."

                // 依赖 skinProject 的 assembleDebug
                val targetTaskPath = ":$skinProjectName:assemble${capVariantName}"
                task.dependsOn(targetTaskPath)
                println("SkinPlugin: Creating task ${task.name} which depends on $targetTaskPath")

                    task.doLast {
                    // 获取 buildDir (AGP 8.x 标准 API)
                    val buildDir = skinProject.layout.buildDirectory.get().asFile
                    
                    val outputsDir = File(buildDir, "outputs")
                    val intermediatesApkDir = File(buildDir, "intermediates/apk")
                    
                    // 判断当前是否是由 IDE 触发的 "Run App" (通常不包含 assemble 完整任务，或者为了加速直接部署)
                    // 或者我们可以简单地通过检查 outputs 目录是否有 APK 来决定策略：
                    // 如果 outputs 下有 APK，说明刚执行过完整构建，优先用 outputs。
                    // 如果 outputs 下没有 APK，但 intermediates 下有，说明可能是一次增量构建或 Run App，此时降级使用 intermediates。
                    // 
                    // 但用户的需求是：
                    // 1. "仅仅 run app 模式" -> 优先 intermediates
                    // 2. "执行 copySkinDebugApk" (手动或 Release 依赖) -> 必须从 outputs 获取
                    //
                    // 如何区分 "Run App" 和 "Execute copySkinDebugApk"？
                    // 其实 Run App 最终也会触发 copySkinDebugApk (如果挂载了)。
                    // 区别可能在于：Run App 时，skinProject 可能并没有执行完整的 assembleDebug，只执行了部分任务生成了 intermediates。
                    // 而当我们显式依赖 assembleDebug 时，outputs 应该会被生成。
                    //
                    // 策略调整：
                    // 始终先找 outputs。如果 outputs 有，那最好。
                    // 如果 outputs 没有（说明 assembleDebug 没生成 outputs，可能是一次快速的增量构建），再去找 intermediates。
                    // 这样既满足了 "完整构建用 outputs"，也满足了 "Run App (如果没有生成 outputs) 用 intermediates"。
                    
                    val potentialDirs = mutableListOf<File>()
                    potentialDirs.add(File(outputsDir, "apk")) // 优先级1：标准输出
                    potentialDirs.add(outputsDir)              // 优先级2：outputs 根目录
                    potentialDirs.add(intermediatesApkDir)     // 优先级3：中间产物 (仅当 outputs 缺失时使用)
                    
                    // 调试日志：打印 potentialDirs 状态
                    // println("SkinPlugin Debug: Checking potential dirs: ${potentialDirs.map { "${it.absolutePath} (exists=${it.exists()})" }}")

                    // 过滤出存在的目录
                    val existingDirs = potentialDirs.filter { it.exists() }

                    var apkFile: File? = null
                    
                    for (dir in existingDirs) {
                        apkFile = dir.walkTopDown()
                            .onEnter { d ->
                                // 严格过滤：不进入 generated, tmp
                                // 对于 intermediates，只有当我们正在搜索 intermediatesApkDir 时才允许进入
                                val name = d.name
                                if (name == "generated" || name == "tmp") return@onEnter false
                                
                                // 如果我们在搜索 outputs，遇到 intermediates 应该跳过
                                if (name == "intermediates" && !dir.absolutePath.contains("intermediates")) return@onEnter false
                                
                                true
                            }
                            .filter { file -> 
                                file.isFile && 
                                file.name.endsWith(".apk") && 
                                !file.name.contains("-unaligned") &&
                                (file.name.contains("debug", ignoreCase = true) || 
                                 file.absolutePath.contains("${File.separator}debug${File.separator}", ignoreCase = true))
                            }
                            .maxByOrNull { it.lastModified() }
                        
                        if (apkFile != null) {
                            break
                        }
                    }

                    if (apkFile != null) {
                        println("SkinPlugin: Found APK: ${apkFile.absolutePath}")
                        println("SkinPlugin: APK Last Modified: ${java.util.Date(apkFile.lastModified())}")
                        
                        val assetsDir = File(project.projectDir, extension.assetsDir)
                        if (!assetsDir.exists()) {
                            assetsDir.mkdirs()
                        }
                        // 目标文件名
                        val destFile = File(assetsDir, extension.targetApkName)
                        apkFile.copyTo(destFile, overwrite = true)
                        println("SkinPlugin: Copied ${apkFile.name} to ${destFile.absolutePath}")
                    } else {
                        println("SkinPlugin: No apk found in ${existingDirs.map { it.absolutePath }}")
                        // 如果 existingDirs 为空，说明 outputs 目录可能根本没生成，打印一下 buildDir 状态
                        if (existingDirs.isEmpty()) {
                            println("SkinPlugin: Warning - No output directories found. Build dir exists: ${buildDir.exists()}")
                            if (buildDir.exists()) {
                                println("SkinPlugin: Build dir content: ${buildDir.list()?.joinToString()}")
                            }
                        }
                    }
                }
            }

            // 3. 挂载任务到 app 的构建流程
            // 根据需求：debug app 不要拷贝，只有 release app 或者手动执行 copySkinDebugApk 才拷贝
            
            // 尝试挂载到 Release 变体
            // 使用 named 而不是 findByName，以支持 Gradle 的 Lazy Task Creation
            val releaseTaskNames = listOf("mergeReleaseAssets", "generateReleaseAssets")
            
            for (taskName in releaseTaskNames) {
                try {
                    // 尝试配置任务
                    project.tasks.named(taskName) {
                        it.dependsOn(copySkinTask)
                        println("SkinPlugin: Hooked into $taskName")
                    }
                    // 通常只需要挂载到一个即可，mergeReleaseAssets 是最合适的
                    if (taskName == "mergeReleaseAssets") break
                } catch (e: Exception) {
                    // 忽略找不到任务的异常
                }
            }
            
            // 【新增】尝试挂载到 Debug 变体（可选）
            // 如果用户希望在 Run App (Debug) 时也自动更新皮肤包，可以解开下面的逻辑
            // 但根据之前的需求描述 "debug app 不要拷贝"，这里默认是不挂载的。
            // 
            // 如果您的意图是：在 Release 模式下 Run App，需要执行 copySkinDebugApk。
            // 上面的 releaseTaskNames 逻辑已经覆盖了 assembleRelease。
            // 
            // 如果您指的是在 IDE 中点击 "Run 'app'" (Release 变体)，它会执行 assembleRelease 吗？
            // 是的，通常会执行 assembleRelease 或者 installRelease。
            // 
            // 问题可能出在：IDE 的 "Run" 操作可能只执行了部分任务，或者变体切换导致的上下文问题。
            // 让我们确保 copySkinDebugApk 被正确挂载到了 release 的 assets 合并任务前。
            
            // 另外，为了确保 copySkinDebugApk 能找到 APK，我们必须确保它依赖的 assembleDebug 执行了。
            // 代码里已经有了: task.dependsOn(targetTaskPath) // :app_skin:assembleDebug
            // 所以只要 copySkinDebugApk 跑了，app_skin 的 assembleDebug 就会跑。
            
            // 现在的核心问题是：为什么在 Release 模式下，copySkinDebugApk 跑了，却找不到 APK？
            // 难道是因为在 Release 构建流程中，skinProject 的 buildDir 发生了变化？或者被清理了？
            
            // 让我们加一个强力的 check：在找不到 APK 时，打印一下当时所有的 task graph，或者 buildDir 的内容。
        }
    }
}
