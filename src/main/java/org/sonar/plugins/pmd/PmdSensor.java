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

import java.util.Iterator;

import net.sourceforge.pmd.Report;
import net.sourceforge.pmd.RuleViolation;

import org.sonar.api.batch.Sensor;
import org.sonar.api.batch.SensorContext;
import org.sonar.api.profiles.RulesProfile;
import org.sonar.api.resources.Project;
import org.sonar.api.rules.Violation;
import org.sonar.api.utils.XmlParserException;
import org.sonar.plugins.java.Java;

public class PmdSensor implements Sensor {
  private final RulesProfile profile;
  private final PmdExecutor executor;
  private final PmdViolationToRuleViolation pmdViolationToRuleViolation;

  public PmdSensor(RulesProfile profile, PmdExecutor executor,
    PmdViolationToRuleViolation pmdViolationToRuleViolation) {
    this.profile = profile;
    this.executor = executor;
    this.pmdViolationToRuleViolation = pmdViolationToRuleViolation;
  }

  @Override
  public boolean shouldExecuteOnProject(Project project) {
    return !project.getFileSystem().mainFiles(Java.KEY).isEmpty()
      && !profile.getActiveRulesByRepository(
        PmdConstants.REPOSITORY_KEY).isEmpty()
        || !project.getFileSystem().testFiles(Java.KEY).isEmpty()
        && !profile.getActiveRulesByRepository(
          PmdConstants.TEST_REPOSITORY_KEY).isEmpty();
  }

  @Override
  public void analyse(Project project, SensorContext context) {
    try {
      Report report = executor.execute();
      reportViolations(report.iterator(), context);
    } catch (Exception e) {
      throw new XmlParserException(e);
    }
  }

  private void reportViolations(Iterator<RuleViolation> violations,
    SensorContext context) {
    while (violations.hasNext()) {
      RuleViolation pmdViolation = violations.next();

      Violation violation = pmdViolationToRuleViolation.toViolation(
        pmdViolation, context);
      if (null != violation) {
        context.saveViolation(violation);
      }
    }
  }

  @Override
  public String toString() {
    return getClass().getSimpleName();
  }
}
