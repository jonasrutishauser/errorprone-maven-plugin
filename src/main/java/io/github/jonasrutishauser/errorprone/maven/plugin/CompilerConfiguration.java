package io.github.jonasrutishauser.errorprone.maven.plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Named;

import org.apache.commons.lang3.JavaVersion;
import org.apache.commons.lang3.SystemUtils;
import org.apache.maven.SessionScoped;
import org.apache.maven.execution.ProjectExecutionEvent;
import org.apache.maven.execution.ProjectExecutionListener;
import org.apache.maven.lifecycle.LifecycleExecutionException;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Named
@SessionScoped
public class CompilerConfiguration implements ProjectExecutionListener {

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

    @Override
    public void beforeProjectExecution(ProjectExecutionEvent event) throws LifecycleExecutionException {
        // nothing to do
    }

    @Override
    public void beforeProjectLifecycleExecution(ProjectExecutionEvent event) throws LifecycleExecutionException {
        compilerExecutions.put(event.getProject().getId(), event.getExecutionPlan().stream() //
                .filter(mojoExecution -> "org.apache.maven.plugins".equals(mojoExecution.getGroupId())
                        && "maven-compiler-plugin".equals(mojoExecution.getArtifactId())
                        && ("compile".equals(mojoExecution.getGoal()) || "testCompile".equals(mojoExecution.getGoal()))) //
                .toList());
    }

    @Override
    public void afterProjectExecutionSuccess(ProjectExecutionEvent event) throws LifecycleExecutionException {
        compilerExecutions.remove(event.getProject().getId());
    }

    @Override
    public void afterProjectExecutionFailure(ProjectExecutionEvent event) {
        compilerExecutions.remove(event.getProject().getId());
    }

    void configure(MavenProject project, String propertyName) {
        for (MojoExecution compilerExecution : compilerExecutions.getOrDefault(project.getId(), List.of())) {
            configureCompilerPlugin(project, compilerExecution, propertyName);
        }
    }

    private void configureCompilerPlugin(MavenProject project, MojoExecution compilerExecution, String propertyName) {
        LOGGER.debug("Configuring compiler plugin for execution {}", compilerExecution.getExecutionId());
        project.getDependencies().stream().filter(dependency -> "errorprone".equals(dependency.getType()))
                .forEach(dependency -> addAnnotationProcessorPath(compilerExecution.getConfiguration(), dependency));

        Xpp3Dom fork = compilerExecution.getConfiguration().getChild("fork");
        if (StrongEncapsulationHelperJava.CURRENT_JVM_NEEDS_FORKING && !isTrue(fork)) {
            if (fork == null) {
                fork = new Xpp3Dom("fork");
                compilerExecution.getConfiguration().addChild(fork);
            }
            fork.setValue("true");
            LOGGER.debug("Set fork to true");
        }

        Xpp3Dom compilerArgs = compilerExecution.getConfiguration().getChild("compilerArgs");
        if (compilerArgs == null) {
            compilerArgs = new Xpp3Dom("compilerArgs");
            compilerExecution.getConfiguration().addChild(compilerArgs);
        }
        if (!hasCompilerArg(compilerArgs.getChildren(), "-Xplugin:ErrorProne")
                && !hasCompilerArg(compilerArgs.getChildren(), "${" + propertyName + "}")) {
            LOGGER.debug("Adding compiler argument \"${" + propertyName + "}\"");
            Xpp3Dom compilerArg = new Xpp3Dom("arg");
            compilerArg.setValue("${" + propertyName + "}");
            compilerArgs.addChild(compilerArg);
        }
        for (String arg : COMPILER_ARGS) {
            if (!hasCompilerArg(compilerArgs.getChildren(), arg)) {
                LOGGER.debug("Adding compiler argument \"{}\"", arg);
                Xpp3Dom compilerArg = new Xpp3Dom("arg");
                compilerArg.setValue(arg);
                compilerArgs.addChild(compilerArg);
            }
        }
        if (isTrue(fork)) {
            for (String jvmArg : JVM_ARGS_STRONG_ENCAPSULATION) {
                if (!hasCompilerArg(compilerArgs.getChildren(), jvmArg)) {
                    LOGGER.debug("Adding compiler argument \"{}\"", jvmArg);
                    Xpp3Dom compilerArg = new Xpp3Dom("arg");
                    compilerArg.setValue(jvmArg);
                    compilerArgs.addChild(compilerArg);
                }
            }
        }
    }

    private void addAnnotationProcessorPath(Xpp3Dom configuration, Dependency dependency) {
        Xpp3Dom annotationProcessorPaths = configuration.getChild("annotationProcessorPaths");
        if (annotationProcessorPaths == null) {
            annotationProcessorPaths = new Xpp3Dom("annotationProcessorPaths");
            configuration.addChild(annotationProcessorPaths);
        }
        if (!hasDependency(annotationProcessorPaths.getChildren(), dependency)) {
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

    private boolean isTrue(Xpp3Dom child) {
        return child != null && Boolean.parseBoolean(child.getValue());
    }

    private boolean hasDependency(Xpp3Dom[] paths, Dependency dependency) {
        for (Xpp3Dom path : paths) {
            String groupId = path.getChild("groupId").getValue();
            String artifactId = path.getChild("artifactId").getValue();
            if (dependency.getGroupId().equals(groupId) && dependency.getArtifactId().equals(artifactId)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasCompilerArg(Xpp3Dom[] compilerArgs, String arg) {
        for (Xpp3Dom compilerArg : compilerArgs) {
            if (compilerArg.getValue().startsWith(arg)) {
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
