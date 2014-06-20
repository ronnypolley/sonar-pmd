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

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import net.sourceforge.pmd.Report;
import net.sourceforge.pmd.RuleContext;
import net.sourceforge.pmd.RuleSets;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mockito;
import org.sonar.api.batch.ProjectClasspath;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.config.Settings;
import org.sonar.api.profiles.RulesProfile;
import org.sonar.api.resources.Project;
import org.sonar.test.TestUtils;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

public class PmdExecutorTest {
  PmdExecutor pmdExecutor;

  Project project = mock(Project.class);
  FileSystem projectFileSystem = mock(FileSystem.class);
  RulesProfile rulesProfile = mock(RulesProfile.class);
  PmdProfileExporter pmdProfileExporter = mock(PmdProfileExporter.class);
  PmdConfiguration pmdConfiguration = mock(PmdConfiguration.class);
  PmdTemplate pmdTemplate = mock(PmdTemplate.class);
  ProjectClasspath projectClasspath = mock(ProjectClasspath.class);
  Settings settings = mock(Settings.class);

  @Before
  public void setUpPmdExecutor() {
    pmdExecutor = Mockito.spy(new PmdExecutor(project, projectFileSystem, rulesProfile, pmdProfileExporter, pmdConfiguration, projectClasspath, settings));

    doReturn(pmdTemplate).when(pmdExecutor).createPmdTemplate();
  }

  @Test
  public void should_execute_pmd_on_source_files_and_test_files() throws Exception {
    InputFile srcFile = file("src/Class.java");
    InputFile tstFile = file("test/ClassTest.java");
    setupPmdRuleSet(PmdConstants.REPOSITORY_KEY, "simple.xml");
    setupPmdRuleSet(PmdConstants.TEST_REPOSITORY_KEY, "junit.xml");

    when(projectFileSystem.encoding()).thenReturn(Charsets.UTF_8);
    when(projectFileSystem.inputFiles(Matchers.isA(JavaFilePredicate.class))).thenReturn(Arrays.asList(srcFile));
    when(projectFileSystem.inputFiles(Matchers.isA(JavaTestFilePredicate.class))).thenReturn(Arrays.asList(tstFile));

    Report report = pmdExecutor.execute();

    verify(pmdTemplate).process(eq(srcFile), any(RuleSets.class), any(RuleContext.class));
    verify(pmdTemplate).process(eq(tstFile), any(RuleSets.class), any(RuleContext.class));
    assertThat(report).isNotNull();

  }

  @Test
  public void should_dump_configuration_as_xml() {
    when(pmdProfileExporter.exportProfile(PmdConstants.REPOSITORY_KEY, rulesProfile)).thenReturn(TestUtils.getResourceContent("/org/sonar/plugins/pmd/simple.xml"));
    when(pmdProfileExporter.exportProfile(PmdConstants.TEST_REPOSITORY_KEY, rulesProfile)).thenReturn(TestUtils.getResourceContent("/org/sonar/plugins/pmd/junit.xml"));

    Report report = pmdExecutor.execute();

    verify(pmdConfiguration).dumpXmlReport(report);
  }

  @Test
  public void should_dump_ruleset_as_xml() throws Exception {
    InputFile srcFile = file("src/Class.java");
    InputFile tstFile = file("test/ClassTest.java");
    setupPmdRuleSet(PmdConstants.REPOSITORY_KEY, "simple.xml");
    setupPmdRuleSet(PmdConstants.TEST_REPOSITORY_KEY, "junit.xml");
    when(projectFileSystem.inputFiles(Matchers.isA(JavaFilePredicate.class))).thenReturn(Arrays.asList(srcFile));
    when(projectFileSystem.inputFiles(Matchers.isA(JavaTestFilePredicate.class))).thenReturn(Arrays.asList(tstFile));

    pmdExecutor.execute();

    verify(pmdConfiguration).dumpXmlRuleSet(PmdConstants.REPOSITORY_KEY, TestUtils.getResourceContent("/org/sonar/plugins/pmd/simple.xml"));
    verify(pmdConfiguration).dumpXmlRuleSet(PmdConstants.TEST_REPOSITORY_KEY, TestUtils.getResourceContent("/org/sonar/plugins/pmd/junit.xml"));
  }

  @Test
  public void should_ignore_empty_test_dir() throws Exception {
    InputFile srcFile = file("src/Class.java");
    doReturn(pmdTemplate).when(pmdExecutor).createPmdTemplate();
    setupPmdRuleSet(PmdConstants.REPOSITORY_KEY, "simple.xml");
    when(projectFileSystem.encoding()).thenReturn(Charsets.UTF_8);
    when(projectFileSystem.inputFiles(Matchers.isA(JavaFilePredicate.class))).thenReturn(Arrays.asList(srcFile));
    when(projectFileSystem.inputFiles(Matchers.isA(JavaTestFilePredicate.class))).thenReturn(Collections.<InputFile>emptyList());

    pmdExecutor.execute();

    verify(pmdTemplate).process(eq(srcFile), any(RuleSets.class), any(RuleContext.class));
    verifyNoMoreInteractions(pmdTemplate);
  }

  static InputFile file(String path) {
    InputFile inputFile = mock(InputFile.class);
    when(inputFile.file()).thenReturn(new File(path));
    return inputFile;
  }

  private void setupPmdRuleSet(String repositoryKey, String profileFileName) throws IOException {
    File ruleSetDirectory = new File("src/test/resources/org/sonar/plugins/pmd/");
    File file = new File(ruleSetDirectory, profileFileName);
    String profileContent = Files.toString(file, Charsets.UTF_8);
    when(pmdProfileExporter.exportProfile(repositoryKey, rulesProfile)).thenReturn(profileContent);
    when(pmdConfiguration.dumpXmlRuleSet(repositoryKey, profileContent)).thenReturn(file);
  }
}
