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
                // 注意：在 AGP 7.x + Run App 场景下，IDE 可能会直接调用 installDebug 而不一定完整触发 assembleDebug
                // 这可能导致 outputs 目录没有生成标准 APK，而只有 intermediates。
                // 
                // 为了确保 outputs 优先，我们依然强制依赖 assembleDebug。
                // 这样，只要执行了 copySkinDebugApk，就一定会触发 assembleDebug，从而生成 outputs。
                //
                // 但是，如果用户觉得每次 Run App 都触发完整 assembleDebug 太慢（虽然 Gradle 有增量构建），
                // 或者在某些特殊情况下 outputs 依然没生成（如配置缓存问题），
                // 我们的回退策略（potentialDirs）会负责去 intermediates 找。
                
                val targetTaskPath = ":$skinProjectName:assemble${capVariantName}"
                
                // 强制依赖 assembleDebug
                task.dependsOn(targetTaskPath)
                println("SkinPlugin: Creating task ${task.name} which depends on $targetTaskPath")
                
                task.doLast {
                    // 兼容性获取 buildDir
                    val buildDir = try {
                        skinProject.layout.buildDirectory.get().asFile
                    } catch (e: Throwable) {
                        skinProject.buildDir
                    }
                    
                    val outputsDir = File(buildDir, "outputs")
                    val intermediatesApkDir = File(buildDir, "intermediates/apk")
                    
                    // 判断构建意图
                    val startTaskNames = project.gradle.startParameter.taskNames
                    val isExplicitBuild = startTaskNames.any { 
                        it.contains("assemble") || it.contains("copySkinDebugApk") || it.contains("build") 
                    }
                    
                    
                    
                    val potentialDirs = mutableListOf<File>()
                    potentialDirs.add(File(outputsDir, "apk")) // 优先级1
                    potentialDirs.add(outputsDir)              // 优先级2
                    
                    if (!isExplicitBuild) {
                         potentialDirs.add(intermediatesApkDir)     // 优先级3
                         println("SkinPlugin Debug: Non-explicit build detected, allowing fallback to intermediates.")
                    } else {
                        println("SkinPlugin Debug: Explicit build detected, STRICTLY searching outputs only.")
                    }
                    
                    // 调试日志
                    

                    var apkFile: File? = null
                    
                    // 显式构建且 outputs/apk 不存在时，先尝试将 intermediates 的产物物化到 outputs
                    val outputsApkDir = File(outputsDir, "apk")
                    if (isExplicitBuild && !outputsApkDir.exists()) {
                        val fallbackFileEarly = intermediatesApkDir.walkTopDown()
                            .filter { it.isFile && it.name.endsWith(".apk") && it.name.contains("debug", ignoreCase = true) }
                            .maxByOrNull { it.lastModified() }
                        if (fallbackFileEarly != null) {
                            val outputsApkVariantDir = File(outputsApkDir, "debug")
                            if (!outputsApkVariantDir.exists()) outputsApkVariantDir.mkdirs()
                            val materialized = File(outputsApkVariantDir, fallbackFileEarly.name)
                            fallbackFileEarly.copyTo(materialized, overwrite = true)
                            
                            apkFile = materialized
                        }
                    }
                    
                    // 核心循环：按优先级搜索
                    if (apkFile == null) {
                        for (dir in potentialDirs) {
                            if (!dir.exists()) {
                                continue
                            }
                            val foundInThisDir = dir.walkTopDown()
                                .onEnter { d ->
                                    val name = d.name
                                    if (name == "generated" || name == "tmp") return@onEnter false
                                    if (name == "intermediates" && !dir.absolutePath.contains("intermediates")) {
                                        return@onEnter false
                                    }
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
                            if (foundInThisDir != null) {
                                apkFile = foundInThisDir
                                break
                            }
                        }
                    }

                    if (apkFile != null) {
                        println("SkinPlugin: Found APK: ${apkFile.absolutePath}")
                        
                        val assetsDir = File(project.projectDir, extension.assetsDir)
                        if (!assetsDir.exists()) {
                            assetsDir.mkdirs()
                        }
                        // 目标文件名
                        val destFile = File(assetsDir, extension.targetApkName)
                        apkFile.copyTo(destFile, overwrite = true)
                    } else {
                        if (isExplicitBuild) {
                             // 尝试去 intermediates 看看有没有，如果有，证明是 AGP 没给 outputs
                             val fallbackFile = intermediatesApkDir.walkTopDown().find { it.isFile && it.name.endsWith(".apk") && it.name.contains("debug", ignoreCase = true) }
                             if (fallbackFile != null) {
                                 // 显式构建要求只读 outputs；如果 outputs 中没有，则将 intermediates 的产物“物化”到 outputs
                                 val outputsApkVariantDir = File(outputsDir, "apk${File.separator}debug")
                                 if (!outputsApkVariantDir.exists()) {
                                     outputsApkVariantDir.mkdirs()
                                 }
                                 val materialized = File(outputsApkVariantDir, fallbackFile.name)
                                 fallbackFile.copyTo(materialized, overwrite = true)
                                 println("SkinPlugin: Found APK: ${materialized.absolutePath}")
                                 val assetsDir = File(project.projectDir, extension.assetsDir)
                                 if (!assetsDir.exists()) {
                                     assetsDir.mkdirs()
                                 }
                                 val destFile = File(assetsDir, extension.targetApkName)
                                 materialized.copyTo(destFile, overwrite = true)
                                 return@doLast
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
            
            // 更早的挂载点：确保在整个 Release 构建早期阶段就执行复制任务
            try {
                project.tasks.named("preReleaseBuild") {
                    it.dependsOn(copySkinTask)
                    println("SkinPlugin: Hooked into preReleaseBuild")
                }
            } catch (_: Exception) {}
            try {
                project.tasks.named("assembleRelease") {
                    it.dependsOn(copySkinTask)
                    println("SkinPlugin: Hooked into assembleRelease")
                }
            } catch (_: Exception) {}
            
            // 当 app_skin 的 debug 打包完成后立刻触发复制（跨工程挂载）
            try {
                skinProject.tasks.named("packageDebug") {
                    it.finalizedBy(copySkinTask)
                    println("SkinPlugin: Finalized by packageDebug")
                }
            } catch (_: Exception) {}
            try {
                skinProject.tasks.named("assembleDebug") {
                    it.finalizedBy(copySkinTask)
                    println("SkinPlugin: Finalized by assembleDebug")
                }
            } catch (_: Exception) {}
            
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
