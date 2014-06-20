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

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import net.sourceforge.pmd.lang.Language;

import org.jdom.CDATA;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.sonar.api.profiles.ProfileExporter;
import org.sonar.api.profiles.RulesProfile;
import org.sonar.api.rules.ActiveRule;
import org.sonar.api.rules.ActiveRuleParam;
import org.sonar.api.utils.SonarException;
import org.sonar.plugins.java.Java;
import org.sonar.plugins.pmd.xml.PmdProperty;
import org.sonar.plugins.pmd.xml.PmdRule;
import org.sonar.plugins.pmd.xml.PmdRuleset;

import com.google.common.annotations.VisibleForTesting;

public class PmdProfileExporter extends ProfileExporter {
  public PmdProfileExporter() {
    super(PmdConstants.REPOSITORY_KEY, PmdConstants.PLUGIN_NAME);
    setSupportedLanguages(Java.KEY);
    setMimeType("application/xml");
  }

  @Override
  public void exportProfile(RulesProfile profile, Writer writer) {
    try {
      String xmlModules = exportProfile(PmdConstants.REPOSITORY_KEY, profile);
      writer.append(xmlModules);
    } catch (IOException e) {
      throw new SonarException("Fail to export the profile " + profile, e);
    }
  }

  public String exportProfile(String repositoryKey, RulesProfile profile) {
    PmdRuleset tree = createPmdRuleset(repositoryKey, profile.getActiveRulesByRepository(repositoryKey), profile.getName());
    return exportPmdRulesetToXml(tree);
  }

  private PmdRuleset createPmdRuleset(String repositoryKey, List<ActiveRule> activeRules, String profileName) {
    PmdRuleset ruleset = new PmdRuleset(profileName);
    for (ActiveRule activeRule : activeRules) {
      if (activeRule.getRule().getRepositoryKey().equals(repositoryKey)) {
        String configKey = activeRule.getRule().getConfigKey();
        PmdRule rule = new PmdRule(configKey, PmdLevelUtils.toLevel(activeRule.getSeverity()));
        if (activeRule.getActiveRuleParams() != null && !activeRule.getActiveRuleParams().isEmpty()) {
          List<PmdProperty> properties = new ArrayList<PmdProperty>();
          for (ActiveRuleParam activeRuleParam : activeRule.getActiveRuleParams()) {
            properties.add(new PmdProperty(activeRuleParam.getRuleParam().getKey(), activeRuleParam.getValue()));
          }
          rule.setProperties(properties);
        }
        ruleset.addRule(rule);
        processXPathRule(activeRule.getRuleKey(), rule);
      }
    }
    return ruleset;
  }

  @VisibleForTesting
  void processXPathRule(String sonarRuleKey, PmdRule rule) {
    if (PmdConstants.XPATH_CLASS.equals(rule.getRef())) {
      rule.setRef(null);
      PmdProperty xpathMessage = rule.getProperty(PmdConstants.XPATH_MESSAGE_PARAM);
      if (xpathMessage == null) {
        throw new SonarException("Property '" + PmdConstants.XPATH_MESSAGE_PARAM + "' should be set for PMD rule " + sonarRuleKey);
      }
      rule.setMessage(xpathMessage.getValue());
      rule.removeProperty(PmdConstants.XPATH_MESSAGE_PARAM);
      PmdProperty xpathExp = rule.getProperty(PmdConstants.XPATH_EXPRESSION_PARAM);
      if (xpathExp == null) {
        throw new SonarException("Property '" + PmdConstants.XPATH_EXPRESSION_PARAM + "' should be set for PMD rule " + sonarRuleKey);
      }
      xpathExp.setCdataValue(xpathExp.getValue());
      rule.setClazz(PmdConstants.XPATH_CLASS);
      rule.setLanguage(Language.JAVA.getTerseName());
      rule.setName(sonarRuleKey);
    }
  }

  private String exportPmdRulesetToXml(PmdRuleset pmdRuleset) {
    Element eltRuleset = new Element("ruleset");
    for (PmdRule pmdRule : pmdRuleset.getPmdRules()) {
      Element eltRule = new Element("rule");
      addAttribute(eltRule, "ref", pmdRule.getRef());
      addAttribute(eltRule, "class", pmdRule.getClazz());
      addAttribute(eltRule, "message", pmdRule.getMessage());
      addAttribute(eltRule, "name", pmdRule.getName());
      addAttribute(eltRule, "language", pmdRule.getLanguage());
      addChild(eltRule, "priority", pmdRule.getPriority());
      if (pmdRule.hasProperties()) {
        Element eltProperties = new Element("properties");
        eltRule.addContent(eltProperties);
        for (PmdProperty prop : pmdRule.getProperties()) {
          Element eltProperty = new Element("property");
          eltProperty.setAttribute("name", prop.getName());
          if (prop.isCdataValue()) {
            Element eltValue = new Element("value");
            eltValue.addContent(new CDATA(prop.getCdataValue()));
            eltProperty.addContent(eltValue);
          } else {
            eltProperty.setAttribute("value", prop.getValue());
          }
          eltProperties.addContent(eltProperty);
        }
      }
      eltRuleset.addContent(eltRule);
    }
    XMLOutputter serializer = new XMLOutputter(Format.getPrettyFormat());
    StringWriter xml = new StringWriter();
    try {
      serializer.output(new Document(eltRuleset), xml);
    } catch (IOException e) {
      throw new SonarException("A exception occured while generating the PMD configuration file.", e);
    }
    return xml.toString();
  }

  private void addChild(Element elt, String name, String text) {
    if (text != null) {
      elt.addContent(new Element(name).setText(text));
    }
  }

  private void addAttribute(Element elt, String name, String value) {
    if (value != null) {
      elt.setAttribute(name, value);
    }
  }
}
