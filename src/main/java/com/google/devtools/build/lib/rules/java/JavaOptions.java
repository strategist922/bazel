// Copyright 2014 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.devtools.build.lib.rules.java;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.devtools.build.lib.analysis.config.BuildConfiguration;
import com.google.devtools.build.lib.analysis.config.BuildConfiguration.LabelConverter;
import com.google.devtools.build.lib.analysis.config.BuildConfiguration.LabelMapConverter;
import com.google.devtools.build.lib.analysis.config.BuildConfiguration.StrictDepsConverter;
import com.google.devtools.build.lib.analysis.config.BuildConfiguration.StrictDepsMode;
import com.google.devtools.build.lib.analysis.config.DefaultsPackage;
import com.google.devtools.build.lib.analysis.config.FragmentOptions;
import com.google.devtools.build.lib.cmdline.Label;
import com.google.devtools.build.lib.cmdline.LabelSyntaxException;
import com.google.devtools.build.lib.rules.java.JavaConfiguration.JavaClasspathMode;
import com.google.devtools.build.lib.rules.java.JavaConfiguration.JavaOptimizationMode;
import com.google.devtools.build.lib.rules.java.JavaConfiguration.OneVersionEnforcementLevel;
import com.google.devtools.common.options.EnumConverter;
import com.google.devtools.common.options.Option;
import com.google.devtools.common.options.OptionDocumentationCategory;
import com.google.devtools.common.options.OptionsParser.OptionUsageRestrictions;
import com.google.devtools.common.options.TriState;
import com.google.devtools.common.options.proto.OptionFilters.OptionEffectTag;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Command-line options for building Java targets
 */
public class JavaOptions extends FragmentOptions {

  /** Converter for the --java_classpath option. */
  public static class JavaClasspathModeConverter extends EnumConverter<JavaClasspathMode> {
    public JavaClasspathModeConverter() {
      super(JavaClasspathMode.class, "Java classpath reduction strategy");
    }
  }

  /**
   * Converter for the --java_optimization_mode option.
   */
  public static class JavaOptimizationModeConverter extends EnumConverter<JavaOptimizationMode> {
    public JavaOptimizationModeConverter() {
      super(JavaOptimizationMode.class, "Java optimization strategy");
    }
  }

  /** Converter for the --java_optimization_mode option. */
  public static class OneVersionEnforcementLevelConverter
      extends EnumConverter<OneVersionEnforcementLevel> {
    public OneVersionEnforcementLevelConverter() {
      super(OneVersionEnforcementLevel.class, "Enforcement level for Java One Version violations");
    }
  }

  @Option(
    name = "javabase",
    defaultValue = "@bazel_tools//tools/jdk:jdk",
    category = "version",
    documentationCategory = OptionDocumentationCategory.UNCATEGORIZED,
    effectTags = {OptionEffectTag.UNKNOWN},
    help =
        "JAVABASE used for the JDK invoked by Blaze. This is the "
            + "JAVABASE which will be used to execute external Java "
            + "commands."
  )
  public String javaBase;

  @Option(
    name = "java_toolchain",
    defaultValue = "@bazel_tools//tools/jdk:toolchain",
    category = "version",
    converter = LabelConverter.class,
    documentationCategory = OptionDocumentationCategory.UNCATEGORIZED,
    effectTags = {OptionEffectTag.UNKNOWN},
    help = "The name of the toolchain rule for Java."
  )
  public Label javaToolchain;

  @Option(
    name = "host_java_toolchain",
    defaultValue = "@bazel_tools//tools/jdk:toolchain",
    category = "version",
    converter = LabelConverter.class,
    documentationCategory = OptionDocumentationCategory.UNCATEGORIZED,
    effectTags = {OptionEffectTag.UNKNOWN},
    help = "The Java toolchain used to build tools that are executed during a build."
  )
  public Label hostJavaToolchain;

  @Option(
    name = "host_javabase",
    defaultValue = "@bazel_tools//tools/jdk:jdk",
    category = "version",
    documentationCategory = OptionDocumentationCategory.UNCATEGORIZED,
    effectTags = {OptionEffectTag.UNKNOWN},
    help =
        "JAVABASE used for the host JDK. This is the JAVABASE which is used to execute "
            + " tools during a build."
  )
  public String hostJavaBase;

  @Option(
    name = "javacopt",
    allowMultiple = true,
    defaultValue = "",
    category = "flags",
    documentationCategory = OptionDocumentationCategory.UNCATEGORIZED,
    effectTags = {OptionEffectTag.UNKNOWN},
    help = "Additional options to pass to javac."
  )
  public List<String> javacOpts;

  @Option(
    name = "jvmopt",
    allowMultiple = true,
    defaultValue = "",
    category = "flags",
    documentationCategory = OptionDocumentationCategory.UNCATEGORIZED,
    effectTags = {OptionEffectTag.UNKNOWN},
    help =
        "Additional options to pass to the Java VM. These options will get added to the "
            + "VM startup options of each java_binary target."
  )
  public List<String> jvmOpts;

  @Option(
    name = "use_ijars",
    defaultValue = "true",
    category = "strategy",
    documentationCategory = OptionDocumentationCategory.UNCATEGORIZED,
    effectTags = {OptionEffectTag.UNKNOWN},
    help =
        "If enabled, this option causes Java compilation to use interface jars. "
            + "This will result in faster incremental compilation, "
            + "but error messages can be different."
  )
  public boolean useIjars;

  @Deprecated
  @Option(
    name = "use_src_ijars",
    defaultValue = "false",
    optionUsageRestrictions = OptionUsageRestrictions.UNDOCUMENTED,
    documentationCategory = OptionDocumentationCategory.UNCATEGORIZED,
    effectTags = {OptionEffectTag.UNKNOWN},
    help = "No-op. Kept here for backwards compatibility."
  )
  public boolean useSourceIjars;

  @Option(
    name = "java_header_compilation",
    defaultValue = "true",
    category = "semantics",
    documentationCategory = OptionDocumentationCategory.UNCATEGORIZED,
    effectTags = {OptionEffectTag.UNKNOWN},
    help = "Compile ijars directly from source.",
    oldName = "experimental_java_header_compilation"
  )
  public boolean headerCompilation;

  // TODO(cushon): delete flag after removing from global .blazerc
  @Deprecated
  @Option(
    name = "experimental_optimize_header_compilation_annotation_processing",
    defaultValue = "false",
    optionUsageRestrictions = OptionUsageRestrictions.UNDOCUMENTED,
    documentationCategory = OptionDocumentationCategory.UNCATEGORIZED,
    effectTags = {OptionEffectTag.UNKNOWN},
    help = "This flag is a noop and scheduled for removal."
  )
  public boolean optimizeHeaderCompilationAnnotationProcessing;

  @Option(
    name = "java_deps",
    defaultValue = "true",
    category = "strategy",
    documentationCategory = OptionDocumentationCategory.UNCATEGORIZED,
    effectTags = {OptionEffectTag.UNKNOWN},
    help = "Generate dependency information (for now, compile-time classpath) per Java target."
  )
  public boolean javaDeps;

  @Option(
    name = "java_classpath",
    allowMultiple = false,
    defaultValue = "javabuilder",
    converter = JavaClasspathModeConverter.class,
    category = "semantics",
    documentationCategory = OptionDocumentationCategory.UNCATEGORIZED,
    effectTags = {OptionEffectTag.UNKNOWN},
    help = "Enables reduced classpaths for Java compilations.",
    oldName = "experimental_java_classpath"
  )
  public JavaClasspathMode javaClasspath;

  @Option(
    name = "java_debug",
    defaultValue = "null",
    category = "testing",
    expansion = {
      "--test_arg=--wrapper_script_flag=--debug",
      "--test_output=streamed",
      "--test_strategy=exclusive",
      "--test_timeout=9999",
      "--nocache_test_results"
    },
    documentationCategory = OptionDocumentationCategory.UNCATEGORIZED,
    effectTags = {OptionEffectTag.UNKNOWN},
    help =
        "Causes the Java virtual machine of a java test to wait for a connection from a "
            + "JDWP-compliant debugger (such as jdb) before starting the test. Implies "
            + "-test_output=streamed."
  )
  public Void javaTestDebug;

  @Option(
    name = "strict_java_deps",
    allowMultiple = false,
    defaultValue = "default",
    converter = StrictDepsConverter.class,
    category = "semantics",
    documentationCategory = OptionDocumentationCategory.UNCATEGORIZED,
    effectTags = {OptionEffectTag.UNKNOWN},
    help =
        "If true, checks that a Java target explicitly declares all directly used "
            + "targets as dependencies.",
    oldName = "strict_android_deps"
  )
  public StrictDepsMode strictJavaDeps;

  // TODO(bazel-team): This flag should ideally default to true (and eventually removed). We have
  // been accidentally supplying JUnit and Hamcrest deps to java_test targets indirectly via the
  // BazelTestRunner, and setting this flag to true fixes that behaviour.
  @Option(
    name = "explicit_java_test_deps",
    defaultValue = "false",
    category = "semantics",
    documentationCategory = OptionDocumentationCategory.UNCATEGORIZED,
    effectTags = {OptionEffectTag.UNKNOWN},
    help =
        "Explicitly specify a dependency to JUnit or Hamcrest in a java_test instead of "
            + " accidentally obtaining from the TestRunner's deps. Only works for bazel right now."
  )
  public boolean explicitJavaTestDeps;

  @Option(
    name = "experimental_testrunner",
    defaultValue = "false",
    optionUsageRestrictions = OptionUsageRestrictions.UNDOCUMENTED,
    documentationCategory = OptionDocumentationCategory.UNCATEGORIZED,
    effectTags = {OptionEffectTag.UNKNOWN},
    help =
        "Use the experimental test runner in bazel which runs the tests under a separate "
            + "classloader. We must set the --explicit_java_test_deps flag with this to ensure "
            + "the test targets have their dependencies right."
  )
  public boolean experimentalTestRunner;

  @Option(
    name = "javabuilder_top",
    defaultValue = "null",
    optionUsageRestrictions = OptionUsageRestrictions.UNDOCUMENTED,
    documentationCategory = OptionDocumentationCategory.UNCATEGORIZED,
    effectTags = {OptionEffectTag.UNKNOWN},
    help = "No-op. Kept here for backwards compatibility."
  )
  public String javaBuilderTop;

  @Option(
    name = "singlejar_top",
    defaultValue = "null",
    optionUsageRestrictions = OptionUsageRestrictions.UNDOCUMENTED,
    documentationCategory = OptionDocumentationCategory.UNCATEGORIZED,
    effectTags = {OptionEffectTag.UNKNOWN},
    help = "No-op. Kept here for backwards compatibility."
  )
  public String singleJarTop;

  @Option(
    name = "genclass_top",
    defaultValue = "null",
    optionUsageRestrictions = OptionUsageRestrictions.UNDOCUMENTED,
    documentationCategory = OptionDocumentationCategory.UNCATEGORIZED,
    effectTags = {OptionEffectTag.UNKNOWN},
    help = "No-op. Kept here for backwards compatibility."
  )
  public String genClassTop;

  @Option(
    name = "ijar_top",
    defaultValue = "null",
    optionUsageRestrictions = OptionUsageRestrictions.UNDOCUMENTED,
    documentationCategory = OptionDocumentationCategory.UNCATEGORIZED,
    effectTags = {OptionEffectTag.UNKNOWN},
    help = "No-op. Kept here for backwards compatibility."
  )
  public String iJarTop;

  @Option(
    name = "java_langtools",
    defaultValue = "null",
    optionUsageRestrictions = OptionUsageRestrictions.UNDOCUMENTED,
    documentationCategory = OptionDocumentationCategory.UNCATEGORIZED,
    effectTags = {OptionEffectTag.UNKNOWN},
    help = "No-op. Kept here for backwards compatibility."
  )
  public String javaLangtoolsJar;

  @Option(
    name = "javac_bootclasspath",
    defaultValue = "null",
    optionUsageRestrictions = OptionUsageRestrictions.UNDOCUMENTED,
    documentationCategory = OptionDocumentationCategory.UNCATEGORIZED,
    effectTags = {OptionEffectTag.UNKNOWN},
    help = "No-op. Kept here for backwards compatibility."
  )
  public String javacBootclasspath;

  @Option(
    name = "javac_extdir",
    defaultValue = "null",
    optionUsageRestrictions = OptionUsageRestrictions.UNDOCUMENTED,
    documentationCategory = OptionDocumentationCategory.UNCATEGORIZED,
    effectTags = {OptionEffectTag.UNKNOWN},
    help = "No-op. Kept here for backwards compatibility."
  )
  public String javacExtdir;

  @Option(
    name = "host_java_launcher",
    defaultValue = "null",
    converter = LabelConverter.class,
    category = "semantics",
    documentationCategory = OptionDocumentationCategory.UNCATEGORIZED,
    effectTags = {OptionEffectTag.UNKNOWN},
    help = "The Java launcher used by tools that are executed during a build."
  )
  public Label hostJavaLauncher;

  @Option(
    name = "java_launcher",
    defaultValue = "null",
    converter = LabelConverter.class,
    category = "semantics",
    documentationCategory = OptionDocumentationCategory.UNCATEGORIZED,
    effectTags = {OptionEffectTag.UNKNOWN},
    help =
        "The Java launcher to use when building Java binaries. "
            + "The \"launcher\" attribute overrides this flag. "
  )
  public Label javaLauncher;

  @Option(
    name = "proguard_top",
    defaultValue = "null",
    category = "version",
    converter = LabelConverter.class,
    documentationCategory = OptionDocumentationCategory.UNCATEGORIZED,
    effectTags = {OptionEffectTag.UNKNOWN},
    help =
        "Specifies which version of ProGuard to use for code removal when building a Java "
            + "binary."
  )
  public Label proguard;

  @Option(
    name = "extra_proguard_specs",
    allowMultiple = true,
    defaultValue = "", // Ignored
    converter = LabelConverter.class,
    optionUsageRestrictions = OptionUsageRestrictions.UNDOCUMENTED,
    documentationCategory = OptionDocumentationCategory.UNCATEGORIZED,
    effectTags = {OptionEffectTag.UNKNOWN},
    help =
        "Additional Proguard specs that will be used for all Proguard invocations.  Note that "
            + "using this option only has an effect when Proguard is used anyway."
  )
  public List<Label> extraProguardSpecs;

  /**
   * Comma-separated list of Mnemonic=label pairs of optimizers to run in the given order, treating
   * {@code Proguard} specially by substituting in the relevant Proguard binary automatically. All
   * optimizers must understand the same flags as Proguard.
   */
  @Option(
    name = "experimental_bytecode_optimizers",
    defaultValue = "Proguard",
    converter = LabelMapConverter.class,
    optionUsageRestrictions = OptionUsageRestrictions.UNDOCUMENTED,
    documentationCategory = OptionDocumentationCategory.UNCATEGORIZED,
    effectTags = {OptionEffectTag.UNKNOWN},
    help = "Do not use."
  )
  public Map<String, Label> bytecodeOptimizers;

  @Option(
    name = "translations",
    defaultValue = "auto",
    category = "semantics",
    documentationCategory = OptionDocumentationCategory.UNCATEGORIZED,
    effectTags = {OptionEffectTag.UNKNOWN},
    help =
        "Translate Java messages; bundle all translations into the jar " + "for each affected rule."
  )
  public TriState bundleTranslations;

  @Option(
    name = "message_translations",
    defaultValue = "",
    category = "semantics",
    allowMultiple = true,
    documentationCategory = OptionDocumentationCategory.UNCATEGORIZED,
    effectTags = {OptionEffectTag.UNKNOWN},
    help = "The message translations used for translating messages in Java targets."
  )
  public List<String> translationTargets;

  @Option(
    name = "check_constraint",
    allowMultiple = true,
    defaultValue = "",
    category = "checking",
    documentationCategory = OptionDocumentationCategory.UNCATEGORIZED,
    effectTags = {OptionEffectTag.UNKNOWN},
    help = "Check the listed constraint."
  )
  public List<String> checkedConstraints;

  @Option(
    name = "experimental_disable_jvm",
    defaultValue = "false",
    optionUsageRestrictions = OptionUsageRestrictions.UNDOCUMENTED,
    documentationCategory = OptionDocumentationCategory.UNCATEGORIZED,
    effectTags = {OptionEffectTag.UNKNOWN},
    help = "Disables the Jvm configuration entirely."
  )
  public boolean disableJvm;

  @Option(
    name = "java_optimization_mode",
    defaultValue = "legacy",
    converter = JavaOptimizationModeConverter.class,
    optionUsageRestrictions = OptionUsageRestrictions.UNDOCUMENTED,
    documentationCategory = OptionDocumentationCategory.UNCATEGORIZED,
    effectTags = {OptionEffectTag.UNKNOWN},
    help = "Applies desired link-time optimizations to Java binaries and tests."
  )
  public JavaOptimizationMode javaOptimizationMode;

  @Option(
    name = "legacy_bazel_java_test",
    defaultValue = "false",
    optionUsageRestrictions = OptionUsageRestrictions.UNDOCUMENTED,
    documentationCategory = OptionDocumentationCategory.UNCATEGORIZED,
    effectTags = {OptionEffectTag.UNKNOWN},
    help = "Use the legacy mode of Bazel for java_test."
  )
  public boolean legacyBazelJavaTest;

  @Option(
    name = "strict_deps_java_protos",
    defaultValue = "false",
    optionUsageRestrictions = OptionUsageRestrictions.UNDOCUMENTED,
    documentationCategory = OptionDocumentationCategory.UNCATEGORIZED,
    effectTags = {OptionEffectTag.UNKNOWN},
    help =
        "When 'strict-deps' is on, .java files that depend on classes not declared in their rule's "
            + "'deps' fail to build. In other words, it's forbidden to depend on classes obtained "
            + "transitively. When true, Java protos are strict regardless of their 'strict_deps' "
            + "attribute."
  )
  public boolean strictDepsJavaProtos;

  @Option(
    name = "experimental_java_header_compilation_disable_javac_fallback",
    defaultValue = "false",
    optionUsageRestrictions = OptionUsageRestrictions.UNDOCUMENTED,
    documentationCategory = OptionDocumentationCategory.UNCATEGORIZED,
    effectTags = {OptionEffectTag.UNKNOWN},
    help =
        "If --java_header_compilation is set, report diagnostics from turbine instead of falling "
            + " back to javac. Diagnostics will be produced more quickly, but may be less helpful."
  )
  public boolean headerCompilationDisableJavacFallback;

  @Option(
    name = "experimental_one_version_enforcement",
    defaultValue = "OFF",
    converter = OneVersionEnforcementLevelConverter.class,
    optionUsageRestrictions = OptionUsageRestrictions.UNDOCUMENTED,
    documentationCategory = OptionDocumentationCategory.UNCATEGORIZED,
    effectTags = {OptionEffectTag.UNKNOWN},
    help =
        "When enabled, enforce that a java_binary rule can't contain more than one version "
            + "of the same class file on the classpath. This enforcement can break the build, or "
            + "can just result in warnings."
  )
  public OneVersionEnforcementLevel enforceOneVersion;

  @Override
  public FragmentOptions getHost(boolean fallback) {
    JavaOptions host = (JavaOptions) getDefault();

    host.javaBase = hostJavaBase;
    host.jvmOpts = ImmutableList.of("-XX:ErrorFile=/dev/stderr");

    host.javacOpts = javacOpts;
    host.javaToolchain = hostJavaToolchain;

    host.javaLauncher = hostJavaLauncher;

    // Java builds often contain complicated code generators for which
    // incremental build performance is important.
    host.useIjars = useIjars;
    host.headerCompilation = headerCompilation;
    host.headerCompilationDisableJavacFallback = headerCompilationDisableJavacFallback;

    host.javaDeps = javaDeps;
    host.javaClasspath = javaClasspath;

    host.strictJavaDeps = strictJavaDeps;

    host.enforceOneVersion = enforceOneVersion;

    return host;
  }

  @Override
  public void addAllLabels(Multimap<String, Label> labelMap) {
    addOptionalLabel(labelMap, "jdk", javaBase);
    addOptionalLabel(labelMap, "jdk", hostJavaBase);
    if (javaLauncher != null) {
      labelMap.put("java_launcher", javaLauncher);
    }
    labelMap.put("java_toolchain", javaToolchain);
    labelMap.putAll("translation", getTranslationLabels());
  }

  @Override
  public Map<String, Set<Label>> getDefaultsLabels(BuildConfiguration.Options commonOptions) {
    Set<Label> jdkLabels = new LinkedHashSet<>();
    DefaultsPackage.parseAndAdd(jdkLabels, javaBase);
    DefaultsPackage.parseAndAdd(jdkLabels, hostJavaBase);
    Map<String, Set<Label>> result = new HashMap<>();
    result.put("JDK", jdkLabels);
    result.put("JAVA_TOOLCHAIN", ImmutableSet.of(javaToolchain));

    return result;
  }

  private Set<Label> getTranslationLabels() {
    Set<Label> result = new LinkedHashSet<>();
    for (String s : translationTargets) {
      try {
        Label label = Label.parseAbsolute(s);
        result.add(label);
      } catch (LabelSyntaxException e) {
        // We ignore this exception here - it will cause an error message at a later time.
      }
    }
    return result;
  }
}
