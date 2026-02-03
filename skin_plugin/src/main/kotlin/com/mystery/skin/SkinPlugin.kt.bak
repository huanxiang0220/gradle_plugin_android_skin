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
                    // 兼容性获取 buildDir
                    val buildDir = try {
                        skinProject.layout.buildDirectory.get().asFile
                    } catch (e: Throwable) {
                        // 回退到旧 API (Gradle < 8.0)
                        skinProject.buildDir
                    }
                    
                    val outputsDir = File(buildDir, "outputs")
                    val intermediatesApkDir = File(buildDir, "intermediates/apk") // 兼容某些 AGP 版本或构建变体
                    
                    val potentialDirs = listOf(
                        File(outputsDir, "apk"), // 优先级最高：标准输出
                        intermediatesApkDir,     // 优先级次之：中间产物（如果 outputs 没有）
                        outputsDir,              // 再次：outputs 根目录
                        buildDir                 // 最后兜底
                    ).filter { it.exists() }

                    var apkFile: File? = null
                    
                    for (dir in potentialDirs) {
                        apkFile = dir.walkTopDown()
                            .onEnter { d ->
                                // 性能优化
                                val name = d.name
                                name != "intermediates" && name != "generated" && name != "tmp" &&
                                // 避免在回退到父目录搜索时，重复搜索已经搜过的子目录（简单起见先不加复杂去重逻辑，依靠 maxByOrNull）
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
                        val assetsDir = File(project.projectDir, extension.assetsDir)
                        if (!assetsDir.exists()) {
                            assetsDir.mkdirs()
                        }
                        // 目标文件名
                        val destFile = File(assetsDir, extension.targetApkName)
                        apkFile.copyTo(destFile, overwrite = true)
                        println("SkinPlugin: Copied ${apkFile.name} to ${destFile.absolutePath}")
                    } else {
                        println("SkinPlugin: No apk found in ${potentialDirs.map { it.absolutePath }}")
                    }
                }
            }

            // 3. 挂载任务到 app 的构建流程
            // 根据需求：debug app 不要拷贝，只有 release app 或者手动执行 copySkinDebugApk 才拷贝
            
            // 尝试挂载到 Release 变体
            // 使用 named 而不是 findByName，以支持 Gradle 的 Lazy Task Creation
            val releaseTaskNames = listOf("mergeReleaseAssets", "generateReleaseAssets")
            
            var hooked = false
            for (taskName in releaseTaskNames) {
                try {
                    // 尝试配置任务
                    project.tasks.named(taskName) {
                        it.dependsOn(copySkinTask)
                        println("SkinPlugin: Hooked into $taskName")
                    }
                    hooked = true
                    // 通常只需要挂载到一个即可，mergeReleaseAssets 是最合适的
                    if (taskName == "mergeReleaseAssets") break
                } catch (e: Exception) {
                    // 忽略找不到任务的异常
                }
            }
            
            if (!hooked) {
                // 如果通过 named 没找到，尝试通过遍历所有任务（针对某些老版本 AGP 或特殊情况）
                project.tasks.configureEach { task ->
                    if (task.name == "mergeReleaseAssets") {
                        task.dependsOn(copySkinTask)
                        println("SkinPlugin: Hooked into ${task.name} (configureEach)")
                    }
                }
            }
        }
    }
}
