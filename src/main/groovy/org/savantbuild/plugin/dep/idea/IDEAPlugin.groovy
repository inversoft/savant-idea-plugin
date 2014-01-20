/*
 * Copyright (c) 2014, Inversoft Inc., All Rights Reserved
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */
package org.savantbuild.plugin.dep.idea

import org.savantbuild.dep.DependencyService
import org.savantbuild.dep.domain.Artifact
import org.savantbuild.dep.domain.Publication
import org.savantbuild.dep.domain.Version
import org.savantbuild.domain.Project
import org.savantbuild.lang.Classpath
import org.savantbuild.output.Output
import org.savantbuild.plugin.dep.DependencyPlugin
import org.savantbuild.plugin.groovy.BaseGroovyPlugin

import java.nio.file.Files
import java.nio.file.Path

import static org.savantbuild.dep.DependencyService.ResolveConfiguration

/**
 * IntelliJ IDEA  plugin.
 *
 * @author Brian Pontarelli
 */
class IDEAPlugin extends BaseGroovyPlugin {
  DependencyPlugin dependencyPlugin

  IDEAPlugin(Project project, Output output) {
    super(project, output)
    dependencyPlugin = new DependencyPlugin(project, output)
  }

  /**
   * Builds a Classpath using the {@link DependencyService} with the given {@link ResolveConfiguration} (if specified).
   * The closure is optional and is invoked with the {@link Classpath} as the delegate. Any method on the {@link Classpath}
   * object can be called from the Closure.
   *
   * @param resolveConfiguration The dependency resolve configuration.
   * @param closure The closure.
   * @return The Classpath.
   */
  void iml() {
    Path imlFile = project.directory.resolve(project.name + ".iml")
    if (!Files.isRegularFile(imlFile) || !Files.isReadable(imlFile) || !Files.isWritable(imlFile)) {
      fail("IntelliJ IDEA module file [${imlFile}] doesn't exist or isn't readable and writable")
    }

    XmlParser root = new XmlParser().parse(imlFile.toFile())
    def component = root.component.find { it.@name == 'NewModuleRootManager' }
    if (!component) {
      fail("Invalid IntelliJ IDEA module file [${imlFile}]. It doesn't appear to be valid.")
    }

    // Remove the libraries
    component.findAll { it.@type == 'module-library' }.each { component.remove(it) }

    // Remove the modules
    component.findAll { it.@type == 'module' }.each { component.remove(it) }

    // Add the libraries
    dependencyPlugin.resolve()
  }

  /**
   * Integrates the project (using the project's defined publications and workflow). If there are no publications, this
   * does nothing. Otherwise, it builds the integration version from the project's version and then publishes the
   * publications using the project's workflow.
   */
  void integrate() {
    if (project.publications.size() == 0) {
      output.info("Project has no publications defined. Skipping integration")
    } else {
      output.info("Integrating project.")
    }

    for (Publication publication : project.publications.allPublications()) {
      // Change the version of the publication to an integration build
      Version integrationVersion = publication.artifact.version.toIntegrationVersion()
      Artifact artifact = new Artifact(publication.artifact.id, integrationVersion, project.license)
      Publication integrationPublication = new Publication(artifact, publication.metaData, publication.file, publication.sourceFile)
      dependencyService.publish(integrationPublication, project.workflow.publishWorkflow)
    }
  }
}
