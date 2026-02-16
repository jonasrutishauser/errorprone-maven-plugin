package io.github.jonasrutishauser.errorprone.maven.plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.commons.lang3.JavaVersion;
import org.apache.commons.lang3.SystemUtils;
import org.apache.maven.SessionScoped;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.PluginParameterExpressionEvaluator;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluationException;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Named
@SessionScoped
class CompilerConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(CompilerConfiguration.class);

    private static final List<String> JVM_ARGS_STRONG_ENCAPSULATION = List.of(
            "-J--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
            "-J--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
            "-J--add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",
            "-J--add-exports=jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED",
            "-J--add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
            "-J--add-exports=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED",
            "-J--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
            "-J--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
            "-J--add-opens=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
            "-J--add-opens=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED");

    private static final List<String> COMPILER_ARGS = List.of( //
            "-XDcompilePolicy=simple", //
            "--should-stop=ifError=FLOW", //
            "-XDaddTypeAnnotationsToSymbol=true");

    private final Map<String, List<MojoExecution>> compilerExecutions = new HashMap<>();
    private final MavenSession session;

    @Inject
    CompilerConfiguration(MavenSession session) {
        this.session = session;
    }

    public void setCompilerExecutions(MavenProject project, List<MojoExecution> executions) {
        compilerExecutions.put(project.getId(), executions);
    }

    public void clearCompilerExecutions(MavenProject project) {
        compilerExecutions.remove(project.getId());
    }

    void configure(MavenProject project, String propertyName) {
        for (MojoExecution compilerExecution : compilerExecutions.getOrDefault(project.getId(), List.of())) {
            configureCompilerPlugin(project, compilerExecution, propertyName);
        }
    }

    String getParameterValue(MojoExecution mojoExecution, Xpp3Dom value) {
        PluginParameterExpressionEvaluator evaluator = new PluginParameterExpressionEvaluator(session, mojoExecution);
        return getParameterValue(evaluator, value);
    }

    private String getParameterValue(PluginParameterExpressionEvaluator evaluator, Xpp3Dom value) {
        if (value == null) {
            return null;
        }
        try {
            Object evaluated = evaluator.evaluate(value.getValue());
            return evaluated == null ? value.getAttribute("default-value") : evaluated.toString();
        } catch (ExpressionEvaluationException e) {
            return null;
        }
    }

    private void configureCompilerPlugin(MavenProject project, MojoExecution compilerExecution, String propertyName) {
        LOGGER.debug("Configuring compiler plugin for execution {}", compilerExecution.getExecutionId());
        PluginParameterExpressionEvaluator evaluator = new PluginParameterExpressionEvaluator(session, compilerExecution);

        addAnnotationProcessorPaths(project.getDependencies(), compilerExecution.getConfiguration(), evaluator);
        if (project.getDependencyManagement() != null) {
            addAnnotationProcessorPaths(project.getDependencyManagement().getDependencies(),
                    compilerExecution.getConfiguration(), evaluator);
        }

        Xpp3Dom fork = setForkIfNeeded(compilerExecution.getConfiguration(), evaluator);

        Xpp3Dom compilerArgs = createOrGetCompilerArgs(compilerExecution.getConfiguration());
        addPluginArgument(propertyName, compilerArgs, evaluator);
        addCompilerArguments(compilerArgs, evaluator);
        if (isTrue(fork, evaluator)) {
            addJvmStrongEncapsulationArguments(compilerArgs, evaluator);
        }
    }

    private void addAnnotationProcessorPaths(List<Dependency> dependencies, Xpp3Dom configuration, PluginParameterExpressionEvaluator evaluator) {
        for (Dependency dependency : dependencies) {
            if ("errorprone".equals(dependency.getType())) {
                addAnnotationProcessorPath(configuration, dependency, evaluator);
            }
        }
    }

    private void addJvmStrongEncapsulationArguments(Xpp3Dom compilerArgs, PluginParameterExpressionEvaluator evaluator) {
        for (String jvmArg : JVM_ARGS_STRONG_ENCAPSULATION) {
            if (!hasCompilerArg(compilerArgs.getChildren(), jvmArg, evaluator)) {
                LOGGER.debug("Adding compiler argument \"{}\"", jvmArg);
                Xpp3Dom compilerArg = new Xpp3Dom("arg");
                compilerArg.setValue(jvmArg);
                compilerArgs.addChild(compilerArg);
            }
        }
    }

    private void addCompilerArguments(Xpp3Dom compilerArgs, PluginParameterExpressionEvaluator evaluator) {
        for (String arg : COMPILER_ARGS) {
            if (!hasCompilerArg(compilerArgs.getChildren(), arg, evaluator)) {
                LOGGER.debug("Adding compiler argument \"{}\"", arg);
                Xpp3Dom compilerArg = new Xpp3Dom("arg");
                compilerArg.setValue(arg);
                compilerArgs.addChild(compilerArg);
            }
        }
    }

    private void addPluginArgument(String propertyName, Xpp3Dom compilerArgs, PluginParameterExpressionEvaluator evaluator) {
        if (!hasCompilerArg(compilerArgs.getChildren(), "-Xplugin:ErrorProne", evaluator)) {
            LOGGER.debug("Adding compiler argument \"${{}}\"", propertyName);
            Xpp3Dom compilerArg = new Xpp3Dom("arg");
            compilerArg.setValue("${" + propertyName + "}");
            compilerArgs.addChild(compilerArg);
        }
    }

    private Xpp3Dom createOrGetCompilerArgs(Xpp3Dom configuration) {
        Xpp3Dom compilerArgs = configuration.getChild("compilerArgs");
        if (compilerArgs == null) {
            compilerArgs = new Xpp3Dom("compilerArgs");
            configuration.addChild(compilerArgs);
        }
        return compilerArgs;
    }

    private Xpp3Dom setForkIfNeeded(Xpp3Dom configuration, PluginParameterExpressionEvaluator evaluator) {
        Xpp3Dom fork = configuration.getChild("fork");
        if (StrongEncapsulationHelperJava.CURRENT_JVM_NEEDS_FORKING && !isTrue(fork, evaluator)) {
            if (fork == null) {
                fork = new Xpp3Dom("fork");
                configuration.addChild(fork);
            }
            fork.setValue("true");
            LOGGER.debug("Set fork to true");
        }
        return fork;
    }

    private void addAnnotationProcessorPath(Xpp3Dom configuration, Dependency dependency, PluginParameterExpressionEvaluator evaluator) {
        Xpp3Dom annotationProcessorPaths = configuration.getChild("annotationProcessorPaths");
        if (annotationProcessorPaths == null) {
            annotationProcessorPaths = new Xpp3Dom("annotationProcessorPaths");
            configuration.addChild(annotationProcessorPaths);
        }
        if (!hasDependency(annotationProcessorPaths.getChildren(), dependency, evaluator)) {
            LOGGER.debug("Adding annotation processor path for dependency {}:{}", dependency.getGroupId(),
                    dependency.getArtifactId());
            Xpp3Dom path = new Xpp3Dom("path");
            annotationProcessorPaths.addChild(path);
            Xpp3Dom groupId = new Xpp3Dom("groupId");
            groupId.setValue(dependency.getGroupId());
            path.addChild(groupId);
            Xpp3Dom artifactId = new Xpp3Dom("artifactId");
            artifactId.setValue(dependency.getArtifactId());
            path.addChild(artifactId);
            if (dependency.getVersion() != null) {
                Xpp3Dom version = new Xpp3Dom("version");
                version.setValue(dependency.getVersion());
                path.addChild(version);
            }
        }
    }

    private boolean isTrue(Xpp3Dom child, PluginParameterExpressionEvaluator evaluator) {
        return Boolean.parseBoolean(getParameterValue(evaluator, child));
    }

    private boolean hasDependency(Xpp3Dom[] paths, Dependency dependency, PluginParameterExpressionEvaluator evaluator) {
        for (Xpp3Dom path : paths) {
            String groupId = getParameterValue(evaluator, path.getChild("groupId"));
            String artifactId = getParameterValue(evaluator, path.getChild("artifactId"));
            if (dependency.getGroupId().equals(groupId) && dependency.getArtifactId().equals(artifactId)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasCompilerArg(Xpp3Dom[] compilerArgs, String arg, PluginParameterExpressionEvaluator evaluator) {
        for (Xpp3Dom compilerArg : compilerArgs) {
            String value = getParameterValue(evaluator, compilerArg);
            if (value != null && value.startsWith(arg)) {
                return true;
            }
        }
        return false;
    }

    private static class StrongEncapsulationHelperJava {
        static final boolean CURRENT_JVM_NEEDS_FORKING = currentJvmNeedsForking();

        private static boolean currentJvmNeedsForking() {
            if (SystemUtils.isJavaVersionAtMost(JavaVersion.JAVA_15)) {
                return false;
            }
            try {
                Module unnamedModule = StrongEncapsulationHelperJava.class.getClassLoader().getUnnamedModule();
                for (String className : new String[] { //
                        "com.sun.tools.javac.api.BasicJavacTask", //
                        "com.sun.tools.javac.api.JavacTrees", //
                        "com.sun.tools.javac.file.JavacFileManager", //
                        "com.sun.tools.javac.main.JavaCompiler", //
                        "com.sun.tools.javac.model.JavacElements", //
                        "com.sun.tools.javac.parser.JavacParser", //
                        "com.sun.tools.javac.processing.JavacProcessingEnvironment", //
                        "com.sun.tools.javac.tree.JCTree", //
                        "com.sun.tools.javac.util.JCDiagnostic", //
                }) {
                    Class<?> clazz = Class.forName(className);
                    if (!clazz.getModule().isExported(clazz.getPackageName(), unnamedModule)) {
                        return true;
                    }
                }
                for (String className : new String[] { //
                        "com.sun.tools.javac.code.Symbol", //
                        "com.sun.tools.javac.comp.Enter", //
                }) {
                    Class<?> clazz = Class.forName(className);
                    if (!clazz.getModule().isOpen(clazz.getPackageName(), unnamedModule)) {
                        return true;
                    }
                }
            } catch (ClassNotFoundException ignored) {
                return true;
            }
            return false;
        }
    }
}
