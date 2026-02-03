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
                    // 获取 apk 输出路径
                    val buildDir = skinProject.layout.buildDirectory.get().asFile
                    val apkDir = File(buildDir, "outputs/apk/$variantName")
                    
                    // 查找 apk (支持子目录递归查找，以应对 Flavor 等情况)
                    val apkFile = if (apkDir.exists()) {
                        apkDir.walkTopDown()
                            .filter { it.isFile && it.name.endsWith(".apk") && !it.name.contains("-unaligned") }
                            .firstOrNull()
                    } else {
                        null
                    }
                    
                    if (apkFile != null) {
                        val assetsDir = File(project.projectDir, extension.assetsDir)
                        if (!assetsDir.exists()) {
                            assetsDir.mkdirs()
                        }
                        // 目标文件名
                        val destFile = File(assetsDir, extension.targetApkName)
                        apkFile.copyTo(destFile, overwrite = true)
                        println("SkinPlugin: Copied ${apkFile.name} to ${destFile.absolutePath}")
                    } else {
                        println("SkinPlugin: No apk found in ${apkDir.absolutePath} or its subdirectories.")
                    }
                }
            }

            // 3. 挂载任务到 app 的构建流程
            // 根据需求：debug app 不要拷贝，只有 release app 或者手动执行 copySkinDebugApk 才拷贝
            
            // 尝试挂载到 Release 变体
            val releaseVariantName = "Release"
            val mergeReleaseAssetsTask = project.tasks.findByName("merge${releaseVariantName}Assets")
            if (mergeReleaseAssetsTask != null) {
                mergeReleaseAssetsTask.dependsOn(copySkinTask)
                println("SkinPlugin: Hooked into merge${releaseVariantName}Assets")
            }
        }
    }
}
