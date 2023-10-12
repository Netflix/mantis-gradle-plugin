/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.mantisrx.mantisplugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.Zip

class MantisPlugin implements Plugin<Project> {

    public static final String CREATE_ZIP_ARTIFACT_TASKNAME = 'mantisZipArtifact'

    /**
     * Creates a zip artifact via the {@code gradlew mantisZipArtifact} command.
     *
     * The zip and json files are created in the {@code build/distributions} sub directory.
     */
    @Override
    void apply(Project project) {
        project.apply plugin: 'application'

        try {
            project.mainClassName = new File('src/main/resources/META-INF/services/io.mantisrx.runtime.MantisJobProvider').getText('UTF-8')
        } catch (IOException ignored) {
            project.mainClassName = 'io.mantisrx.runtime.command.LoadValidateCreateZip'
        }

        project.task("copyMantisJobProvider") {
            def config = new File("src/main/resources/META-INF/services")
            outputs.dir config
        }

        project.applicationDistribution.from(project.tasks.copyMantisJobProvider) {
            into "config"
        }

        project.distZip.zip64 = true

        project.task([type: Jar], "pathingJar") {
            doFirst {
                appendix = "pathing"
                manifest {
                    attributes "Class-Path": project.startScripts.classpath.files.join(" ")
                }
            }
        }

        TaskProvider<JavaExec> createZipArtifactTask = project.tasks.register(CREATE_ZIP_ARTIFACT_TASKNAME, JavaExec)
        createZipArtifactTask.configure {
            mainClass.set( 'io.mantisrx.runtime.command.LoadValidateCreateZip')
            dependsOn(pathingJar)
            doFirst {
                classpath = project.files(project.tasks.getByName("pathingJar").archivePath) + project.sourceSets.main.output
                def readyForJobMaster = System.getProperty("READY_FOR_JOB_MASTER")

                if (readyForJobMaster == null) {
                    readyForJobMaster = true
                }
                def distZip = project.distZip as Zip
                def json_version = distZip.archiveVersion.get()
                def versionSuffix = System.getProperty("VERSION_SUFFIX")
                if (versionSuffix != null && !versionSuffix.isEmpty()) {
                    json_version = distZip.archiveVersion.get() + '-' + versionSuffix.trim()
                }
                def artifactProject = distZip.archiveBaseName.get()
                def destinationDirectory = distZip.destinationDirectory.get().getAsFile()
                args = [distZip.archiveFile.get().getAsFile(),
                        artifactProject,
                        json_version,
                        destinationDirectory,
                        readyForJobMaster]
            }
        }
    }
}

class MantisPluginExtension {
    List<String> jobClusters
}