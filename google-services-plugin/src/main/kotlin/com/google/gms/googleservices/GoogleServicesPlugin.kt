/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.gms.googleservices

import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.DynamicFeatureAndroidComponentsExtension
import com.android.build.api.variant.GeneratesApk
import com.android.build.api.variant.Variant
import com.google.android.gms.dependencies.DependencyAnalyzer
import com.google.android.gms.dependencies.DependencyInspector
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.util.Locale

class GoogleServicesPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val config =
            project.extensions.create("googleServices", GoogleServicesPluginConfig::class.java)
        project.afterEvaluate {
            if (config.disableVersionCheck) {
                return@afterEvaluate
            }

            val globalDependencies = DependencyAnalyzer()
            val strictVersionDepInspector = DependencyInspector(
                globalDependencies, project.name,
                "This error message came from the google-services Gradle plugin, report" +
                        " issues at https://github.com/google/play-services-plugins and disable by " +
                        "adding \"googleServices { disableVersionCheck = true }\" to your build.gradle file."
            )
            project.configurations.all { projectConfig ->
                if (projectConfig.name.contains("ompile")) {
                    projectConfig.incoming.afterResolve(strictVersionDepInspector::afterResolve)
                }
            }
        }

        var pluginApplied = false

        project.pluginManager.withPlugin("com.android.application") {
            pluginApplied = true
            project.extensions.configure(ApplicationAndroidComponentsExtension::class.java) {
                it.registerSourceType(SOURCE_TYPE)
                it.onVariants { variant ->
                    handleVariant(variant, project)
                }
            }
        }
        project.pluginManager.withPlugin("com.android.dynamic-feature") {
            pluginApplied = true
            project.extensions.configure(DynamicFeatureAndroidComponentsExtension::class.java) {
                it.registerSourceType(SOURCE_TYPE)
                it.onVariants { variant ->
                    handleVariant(variant, project)
                }
            }
        }

        project.afterEvaluate {
            if (pluginApplied) {
                return@afterEvaluate
            }
            project.logger.error(
                "The google-services Gradle plugin needs to be applied on a project with " +
                        "com.android.application or com.android.dynamic-feature."
            )
        }
    }

    private fun <T> handleVariant(
        variant: T,
        project: Project
    ) where T : Variant, T : GeneratesApk {
        val jsonToXmlTask = project.tasks.register(
            "process${variant.name.capitalize()}GoogleServices",
            GoogleServicesTask::class.java
        ) {
            it.googleServicesJsonFiles.set(variant.sources.getByName(SOURCE_TYPE).all)
            it.applicationId.set(variant.applicationId)
        }

        // TODO: add an AGP version check to this block
        //  when https://issuetracker.google.com/issues/268192807 is fixed
        try {
            variant.sources.getByName(SOURCE_TYPE)
                .addStaticSourceDirectory(
                    project
                        .layout
                        .projectDirectory
                        .dir("src/${variant.name}/google-services").toString()
                )
        } catch (e: IllegalArgumentException) {
            // directory doesn't exist, ignore
        }
        variant.sources.res?.addGeneratedSourceDirectory(
            jsonToXmlTask,
            GoogleServicesTask::outputDirectory
        )
    }

    /* Recommended replacement for Kotlin's deprecated capitalize function */
    private fun String.capitalize(): String =
        replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }


    companion object {
        const val MODULE_GROUP = "com.google.android.gms"
        const val MODULE_GROUP_FIREBASE = "com.google.firebase"
        const val MODULE_CORE = "firebase-core"
        const val MODULE_VERSION = "11.4.2"
        const val MINIMUM_VERSION = "9.0.0"
        const val SOURCE_TYPE = "google-services"
    }

    open class GoogleServicesPluginConfig {
        /**
         * Disables checking of Google Play Services dependencies compatibility.
         */
        var disableVersionCheck = false
    }
}

