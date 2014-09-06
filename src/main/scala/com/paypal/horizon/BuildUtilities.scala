/**
 * Copyright 2013-2014 PayPal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.paypal.horizon

import sbt._
import sbt.Keys._
import sbtrelease._
import ReleaseStateTransformations._
import com.typesafe.sbt.SbtSite.site
import sbtunidoc.Plugin._
import com.typesafe.sbt.SbtGhPages.ghpages
import com.typesafe.sbt.SbtGhPages.GhPagesKeys._
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import com.typesafe.sbt.SbtGit.GitKeys._
import com.typesafe.sbt.SbtSite.SiteKeys._

/**
 * Settings and tasks for various build utilities. Override as needed.
 */
object BuildUtilitiesKeys {

  /**
   * Unique folder structure for local clone of ghpages branch (used by sbt-ghpages).
   */
  lazy val ghpagesDir = SettingKey[String]("build-utilities-ghpages-directory", "unique folder structure for the git project gh-pages branch")

  /**
   * Name of changelog file. Default is CHANGELOG.md.
   */
  lazy val changelog = SettingKey[String]("build-utilities-changelog-file", "Name of the changelog file, default is CHANGELOG.md")

  /**
   * Name of the readme file. Default is README.md.
   */
  lazy val readme = SettingKey[String]("build-utilities-readme-file", "Name of the readme file, default is README.md")

  /**
   * Name of the template file from which to generate the readme. Default is Readme-Template.md.
   */
  lazy val readmeTemplate = SettingKey[String]("build-utilities-readme-template-file",
    "Name of the readme template file from which the readme is created, default is Readme-Template.md")

  /**
   * keys/values to find and replace when generating the readme.
   */
  lazy val readmeTemplateMappings = SettingKey[Map[String, String]]("build-utilities-readme-template-mappings", "Mappings for generating readme file")

  /**
   * sbt task which generates the readme file according to the template and mappings.
   * Does not commit anything. Run manually via `sbt gen-readme`.
   */
  lazy val genReadme = TaskKey[Unit]("gen-readme", "Generates readme file from template")
}

/**
 * Primary plugin object used to access all major build utilities.
 *
 * Mixes in [[com.paypal.horizon.sbt.GitInfo]], a trait which can be used to access Git repository information for the current build.
 *
 * To access add the following import statement:
 *
 * {{{
 *   import com.paypal.horizon.sbt.BuildUtilities._
 * }}}
 *
 */
object BuildUtilities extends Plugin with GitInfo {
  import BuildUtilitiesKeys._

  /**
   * Default release process for projects,
   * uses sequence of release steps from [[https://github.com/sbt/sbt-release sbtrelease]]
   *
   * '''Caveats:'''
   *
   * Depends on adding `utilitySettings` to build settings in order to complete the `generateAndPushDocs` release step.
   * Readme release step depends on `Readme-Template.md` file, from which the README.md file is produced. Override setting if file is named differently.
   *
   * '''Process Order:'''
   *
   * 1. checkSnapshotDependencies - checks dependencies
   *
   * 2. inquireVersions - gets release version and figures out next version
   *
   * 3. checkForChangelog - checks that system properties
   *    changelog.msg and changelog.author have been set
   *
   * 4. runTest - run tests
   *
   * 5. setReleaseVersion - sets the release version (removes the SNAPSHOT part)
   *
   * 6. commitReleaseVersion - commits the release version
   *
   * 7. updateChangelog - updates the changelog entry for this
   *    release version and commits the change
   *
   * 8. generateReadme - generates README.md` file from `Readme-Template.md`, substituting
   *    `{{key}}` with associated value according to the `readmeTemplateMappings` settings.
   *
   * 9. tagRelease - tags the release
   *
   * 10. publishArtifacts - publishes artifacts to specified location
   *
   * 11. generateAndPushDocs - generates ScalaDocs and pushes to the gh-pages branch
   *
   * 12. setNextVersion - sets the next snapshot version
   *
   * 13. commitNextVersion - commits the next snapshot version
   *
   * 14. pushChanges - pushes all commits created by this process
   *
   * @example
   * {{{
   *   // in project build file
   *   releaseProcess := defaultReleaseProcess
   * }}}
   */
  lazy val defaultReleaseProcess = Seq[ReleaseStep](
    checkSnapshotDependencies,
    VersionReleaseSteps.checkForVersionDefinitions,
    ChangelogReleaseSteps.checkForChangelog,
    runTest,
    setReleaseVersion,
    commitReleaseVersion,
    ChangelogReleaseSteps.updateChangelog,
    ReadmeReleaseSteps.generateReadme,
    tagRelease,
    publishArtifacts,
    ScaladocReleaseSteps.generateAndPushDocs,
    setNextVersion,
    commitNextVersion,
    pushChanges
  )

  /**
   * Finds the managed dependency for use with sbt api mappings.
   *
   * @param organization the module organization
   * @param name the module name
   * @return the managed dependency
   *
   * @example
   * {{{
   *   apiMappings ++= {
   *     import BuildUtilities._
   *     val links = Seq(
   *       findManagedDependency("org.scala-lang", "scala-library").value.map(d => d -> url(s"http://www.scala-lang.org/api/$scalaVsn/")),
   *       findManagedDependency("com.typesafe.akka", "akka-actor").value.map(d => d -> url(s"http://doc.akka.io/api/akka/$akkaVersion/")),
   *       findManagedDependency("com.typesafe", "config").value.map(d => d -> url("http://typesafehub.github.io/config/latest/api/")),
   *       findManagedDependency("org.slf4j", "slf4j-api").value.map(d => d -> url("http://www.slf4j.org/api/")),
   *       findManagedDependency("com.typesafe.akka", "akka-testkit").value.map(d => d -> url(s"http://doc.akka.io/api/akka/$akkaVersion/")),
   *       findManagedDependency("org.specs2", "specs2").value.map(d => d -> url(s"http://etorreborre.github.io/specs2/api/SPECS2-$specs2Version/"))
   *     )
   *     links.collect { case Some(d) => d }.toMap
   *   }
   * }}}
   */
  def findManagedDependency(organization: String, name: String): Def.Initialize[Task[Option[File]]] = {
    Def.task {
      val artifacts = for {
        entry <- (fullClasspath in Runtime).value ++ (fullClasspath in Test).value
        module <- entry.get(moduleID.key) if module.organization == organization && module.name.startsWith(name)
      } yield entry.data
      artifacts.headOption
    }
  }

  private val gitDir = new File(".", ".git")
  private val repo = FileRepositoryBuilder.create(gitDir)
  private val originUrl = repo.getConfig.getString("remote", "origin", "url")

  /**
   * Format of str will either be
   *
   * https://github.paypal.com/$name/$repo.git, or
   * git@github.paypal.com:$name/$repo.git,
   *
   * where $name is the individual or organization.
   *
   * To get the directory structure, we:
   *
   * 1. Remove the .git
   * 2. Replace all colons with forward slashes
   * 3. Split the str by the / character
   * 4. Concat the last two elements of the resulting array with a forward slash and return $name/$repo
   */
  private def extractDirStructure(str: String): String = {
    val gitRemoved = str.replace(".git", "")
    val colonsReplaced = gitRemoved.replace(":", "/")
    val splitStr = colonsReplaced.split('/')
    val repo = splitStr(splitStr.length - 1)
    val name = splitStr(splitStr.length - 2)
    s"$name/$repo"
  }

  /**
   * Settings val which provides all utility settings.
   *
   * Current settings:
   *
   * Combines sbt-unidoc, sbt-site, and ghpages settings. Also sets the following additional settings:
   *
   * - gitRemoteRepo (sbt-git) is set to current remote origin, extracted using sbt-git.
   *
   * - ghpagesNoJekyll (sbt-ghpages) is set to false
   *
   * - siteMappings (sbt-site) is overridden to create a folder structure like /api/$version
   *
   * - synchLocal (sbt-ghpages) is overridden so older docs are not deleted
   *
   * - repository (sbt-ghpages) is overridden to clone the repo's gh-pages branch into a directory structure of the form:
   *   ~/.sbt/ghpages/$name/$repo
   *
   * - changelog is set to CHANGELOG.md
   *
   * - readme is set to README.md
   *
   * - readmeTemplate is set to Readme-Template.md
   *
   * Customize settings in your project's build file as needed. For example, look at unidoc settings to exclude aggregate projects from the docs.
   *
   * sbt-site provides commands to create a project site for publishing,
   * and includes commands for auto doc generation. [[https://github.com/sbt/sbt-site]]
   *
   * sbt-unidoc works with sbt-site to combine multiple sub-project documentation into one "site".
   * See docs for how to exclude projects if needed. [[https://github.com/sbt/sbt-unidoc]]
   *
   * ghpages provides commands to easily push a created "site" to the gh-pages branch of the repository. [[https://github.com/sbt/sbt-ghpages]]
   *
   * To use:
   *
   * Add `utilitySettings` to the root project's settings. For example:
   *
   * {{{
   *   lazy val parent = Project("parent", file("."),
   *    settings = standardSettings ++ utilitySettings ++ Seq(
   *     name := "parent",
   *    ),
   *    aggregate = Seq(project1, project2)
   *   )
   * }}}
   *
   * Provided you have included `utilitySettings` in your root project's build settings, the `defaultReleaseProcess` using [[sbtrelease]]
   * includes a step to generate and push docs.
   *
   * To manually create, run the following sbt command:
   *
   * {{{
   *   sbt unidoc make-site ghpages-push-site
   * }}}
   *
   */
  lazy val utilitySettings: Seq[Setting[_]] =
    unidocSettings ++
    ghpages.settings ++
    site.settings ++ Seq(
      gitRemoteRepo := originUrl,
      ghpagesNoJekyll := false,
      ghpagesDir := extractDirStructure(originUrl),
      repository <<= ghpagesDir.apply (dir => file(sys.props.getOrElse("user.home", null)) / ".sbt" / "ghpages" / dir),
      siteMappings <++= (mappings in (ScalaUnidoc, packageDoc), version).map { (mapping, ver) =>
        for((file, path) <- mapping) yield (file, (s"api/$ver/$path"))
      },
      synchLocal <<= (privateMappings, updatedRepository, gitRunner, streams).map { (mappings, repo, git, s) =>
        val betterMappings = mappings.map { case (file, target) => (file, repo / target) }
        IO.copy(betterMappings)
        repo
      },
      changelog := "CHANGELOG.md",
      readme := "README.md",
      readmeTemplate := "Readme-Template.md",
      readmeTemplateMappings <<= (version in ThisBuild) {
        ver => Map("version" -> ver)
      },
      readmeTemplateMappings ++= Map("auto-gen" -> "THIS FILE WAS AUTO GENERATED BY THE README TEMPLATE. DO NOT EDIT DIRECTLY."),
      genReadme <<= ReadmeReleaseSteps.genReadmeTask
    )
}
