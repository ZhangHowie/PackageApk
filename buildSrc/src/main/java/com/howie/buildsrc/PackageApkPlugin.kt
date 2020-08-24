package com.howie.buildsrc

import com.android.build.gradle.AppExtension
import com.android.build.gradle.internal.api.ApkVariantOutputImpl
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PackageApkPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val packageApkExtension =
            project.extensions.create("packageApk", PackageExtension::class.java)

        val appExtension = project.property("android")

        //当前的拓展是App拓展我们才去支持
        if (appExtension is AppExtension) {
            renameApk(appExtension, packageApkExtension)

            packageApkToZip(project, appExtension, packageApkExtension)
            project.afterEvaluate {
                println("project apk rename to ${packageApkExtension.apkName}")
            }
        } else {
            throw IllegalAccessException("This plugin is design for application module, please check it.")
        }


    }

    /**
     * 重命名Apk
     */
    fun renameApk(android: AppExtension, packageApkExtension: PackageExtension) {
        println("renameApk$packageApkExtension")
        //遍历所有的变体
        android.applicationVariants.all { applicationVariant ->
            //遍历所有的输出
            applicationVariant.outputs.all { output ->
                //如果设置了自定义Name，才去重命名
                if (!packageApkExtension.apkName.isNullOrBlank()) {
                    var apkNameResult = packageApkExtension.apkName!!

                    //如果显示构建类型，则显示添加_release、_debug
                    if (apkNameResult.contains("\$buildType\$")) {
                        apkNameResult = apkNameResult.replace("\$buildType\$", applicationVariant.buildType.name, true)
                    }
                    if (output is ApkVariantOutputImpl) {
                        output.outputFileName = apkNameResult
                    }
                }
            }
        }
    }


    /**
     * Hook Android打包流程
     * 在执行assembleXXRelease的时候将其Apk，制作成Zip包，
     * 放入项目的output目录
     */
    fun packageApkToZip(
        project: Project,
        android: AppExtension,
        packageApkExtension: PackageExtension
    ) {

        project.tasks.whenTaskAdded { task: Task ->
            if (!packageApkExtension.zipName.isNullOrBlank()) {
                android.applicationVariants.all { variant ->
                    //记录哪个task是我们需要Hook的
                    val hookTaskName = "assemble" + (variant.flavorName ?: "") + variant.buildType.name
                    if (task.name.equals(hookTaskName, true)) {
                        println("task expect name is:$hookTaskName, truly is:${task.name}")
                        //记录我们加载出来的Apk路径
                        var apkPath = ""
                        variant.outputs.all { output ->
                            apkPath = output.outputFile.path
                        }

                        //真正的去另存为我们的Zip文件
                        task.doLast {
                            val apkFile = File(apkPath)
                            val outputFilePath = File(project.rootDir.path + File.separator + "output")
                            println("output zip:${outputFilePath.path}")
                            outputFilePath.listFiles()?.forEach {
                                it.delete()
                            }

                            if (!outputFilePath.exists()) outputFilePath.mkdir()
                            val zipFile =
                                outputFilePath.path + File.separator + packageApkExtension.zipName

                            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { out ->
                                BufferedInputStream(FileInputStream(apkFile)).use { apkInput ->
                                    val entry = ZipEntry(apkFile.name)
                                    out.putNextEntry(entry)
                                    apkInput.copyTo(out, 1024)
                                }
                            }
                        }
                    }
                }
            }

        }
    }
}