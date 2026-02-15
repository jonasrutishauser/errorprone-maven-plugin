package io.github.jonasrutishauser.errorprone.maven.plugin;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "configuration", defaultPhase = LifecyclePhase.PROCESS_RESOURCES, threadSafe = true)
public class ConfigurationMojo extends AbstractConfigurationMojo {

    /**
     * Tells Error Prone that the compilation contains only test code. Maps to
     * {@code -XepCompilingTestOnlyCode}.
     */
    @Parameter(defaultValue = "false")
    private boolean compilingTestOnlyCode;

    @Override
    protected boolean isCompilingTestOnlyCode() {
        return compilingTestOnlyCode;
    }

}
