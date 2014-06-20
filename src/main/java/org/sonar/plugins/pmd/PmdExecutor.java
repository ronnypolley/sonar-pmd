/*
 * SonarQube PMD Plugin
 * Copyright (C) 2012 SonarSource
 * dev@sonar.codehaus.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.plugins.pmd;

import java.io.File;
import java.nio.charset.Charset;

import net.sourceforge.pmd.Report;
import net.sourceforge.pmd.RuleContext;
import net.sourceforge.pmd.RuleSet;
import net.sourceforge.pmd.RuleSetFactory;
import net.sourceforge.pmd.RuleSetNotFoundException;
import net.sourceforge.pmd.RuleSets;

import org.sonar.api.BatchExtension;
import org.sonar.api.batch.ProjectClasspath;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.config.Settings;
import org.sonar.api.profiles.RulesProfile;
import org.sonar.api.resources.Project;
import org.sonar.api.utils.SonarException;
import org.sonar.api.utils.TimeProfiler;

import com.google.common.annotations.VisibleForTesting;

public class PmdExecutor implements BatchExtension {
  private final Project project;
  private final FileSystem projectFileSystem;
  private final RulesProfile rulesProfile;
  private final PmdProfileExporter pmdProfileExporter;
  private final PmdConfiguration pmdConfiguration;
  private final ClassLoader projectClassloader;
  private final Settings settings;

  public PmdExecutor(Project project, FileSystem projectFileSystem, RulesProfile rulesProfile,
    PmdProfileExporter pmdProfileExporter, PmdConfiguration pmdConfiguration, ProjectClasspath classpath, Settings settings) {
    this.project = project;
    this.projectFileSystem = projectFileSystem;
    this.rulesProfile = rulesProfile;
    this.pmdProfileExporter = pmdProfileExporter;
    this.pmdConfiguration = pmdConfiguration;
    this.settings = settings;
    this.projectClassloader = classpath.getClassloader();
  }

  public Report execute() {
    TimeProfiler profiler = new TimeProfiler().start("Execute PMD " + PmdVersion.getVersion());

    ClassLoader initialClassLoader = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

      return executePmd();
    } finally {
      Thread.currentThread().setContextClassLoader(initialClassLoader);
      profiler.stop();
    }
  }

  private Report executePmd() {
    Report report = new Report();

    RuleContext context = new RuleContext();
    context.setReport(report);

    PmdTemplate pmdFactory = createPmdTemplate();
    executeRules(pmdFactory, context, projectFileSystem.inputFiles(new JavaFilePredicate()), PmdConstants.REPOSITORY_KEY);
    executeRules(pmdFactory, context, projectFileSystem.inputFiles(new JavaTestFilePredicate()), PmdConstants.TEST_REPOSITORY_KEY);

    pmdConfiguration.dumpXmlReport(report);

    return report;
  }

  public void executeRules(PmdTemplate pmdFactory, RuleContext ruleContext, Iterable<InputFile> files, String repositoryKey) {
    if (files == null || !files.iterator().hasNext()) {
      // Nothing to analyze
      return;
    }

    RuleSets rulesets = createRulesets(repositoryKey);
    if (rulesets.getAllRules().isEmpty()) {
      // No rule
      return;
    }

    rulesets.start(ruleContext);

    for (InputFile file : files) {
      pmdFactory.process(file, rulesets, ruleContext);
    }

    rulesets.end(ruleContext);
  }

  private RuleSets createRulesets(String repositoryKey) {
    String rulesXml = pmdProfileExporter.exportProfile(repositoryKey, rulesProfile);
    File ruleSetFile = pmdConfiguration.dumpXmlRuleSet(repositoryKey, rulesXml);
    String ruleSetFilePath = ruleSetFile.getAbsolutePath();
    RuleSetFactory ruleSetFactory = new RuleSetFactory();
    try {
      RuleSet ruleSet = ruleSetFactory.createRuleSet(ruleSetFilePath);
      return new RuleSets(ruleSet);
    } catch (RuleSetNotFoundException e) {
      throw new SonarException(e);
    }
  }

  @VisibleForTesting
  PmdTemplate createPmdTemplate() {
    Charset encoding = projectFileSystem.encoding();
    return PmdTemplate.create(settings.getString("sonar.java.source"), projectClassloader, encoding);
  }

}
