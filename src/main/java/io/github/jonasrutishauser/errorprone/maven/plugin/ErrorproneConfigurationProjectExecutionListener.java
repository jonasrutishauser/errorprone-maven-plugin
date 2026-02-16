package io.github.jonasrutishauser.errorprone.maven.plugin;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;

import org.apache.maven.execution.ProjectExecutionEvent;
import org.apache.maven.execution.ProjectExecutionListener;
import org.apache.maven.lifecycle.LifecycleExecutionException;
import org.apache.maven.plugin.MojoExecution;

@Named
public class ErrorproneConfigurationProjectExecutionListener implements ProjectExecutionListener {

    private final Provider<CompilerConfiguration> compilerConfiguration;

    @Inject
    ErrorproneConfigurationProjectExecutionListener(Provider<CompilerConfiguration> compilerConfiguration) {
        this.compilerConfiguration = compilerConfiguration;
    }

    @Override
    public void beforeProjectExecution(ProjectExecutionEvent event) throws LifecycleExecutionException {
        // nothing to do
    }

    @Override
    public void beforeProjectLifecycleExecution(ProjectExecutionEvent event) throws LifecycleExecutionException {
        CompilerConfiguration configuration = compilerConfiguration.get();
        configuration.setCompilerExecutions(event.getProject(), event.getExecutionPlan().stream()
                .filter(execution -> isJavacCompilerExecution(configuration, execution)).toList());
    }

    private boolean isJavacCompilerExecution(CompilerConfiguration configuration, MojoExecution mojoExecution) {
        if ("org.apache.maven.plugins".equals(mojoExecution.getGroupId())
                && "maven-compiler-plugin".equals(mojoExecution.getArtifactId())
                && ("compile".equals(mojoExecution.getGoal()) || "testCompile".equals(mojoExecution.getGoal()))) {
            String value = configuration.getParameterValue(mojoExecution, mojoExecution.getConfiguration().getChild("compilerId"));
            return value == null || "javac".equals(value);
        }
        return false;
    }

    @Override
    public void afterProjectExecutionSuccess(ProjectExecutionEvent event) throws LifecycleExecutionException {
        compilerConfiguration.get().clearCompilerExecutions(event.getProject());
    }

    @Override
    public void afterProjectExecutionFailure(ProjectExecutionEvent event) {
        compilerConfiguration.get().clearCompilerExecutions(event.getProject());
    }

}
