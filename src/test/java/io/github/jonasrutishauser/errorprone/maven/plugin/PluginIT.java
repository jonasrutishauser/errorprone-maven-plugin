package io.github.jonasrutishauser.errorprone.maven.plugin;

import java.io.File;

import org.junit.jupiter.api.extension.RegisterExtension;

import io.takari.maven.testing.TestResources5;
import io.takari.maven.testing.executor.MavenRuntime;
import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;
import io.takari.maven.testing.executor.MavenVersions;
import io.takari.maven.testing.executor.junit.MavenPluginTest;

@MavenVersions({"3.9.8", "3.9.12", "4.0.0-rc-5"})
public class PluginIT {

    @RegisterExtension
    final TestResources5 resources = new TestResources5();

    private final MavenRuntime maven;

    public PluginIT(MavenRuntimeBuilder mavenBuilder) throws Exception {
        this.maven = mavenBuilder.withCliOptions("-B", "-X").build();
    }

    @MavenPluginTest
    void simple() throws Exception {
        File basedir = resources.getBasedir("simple");

        maven.forProject(basedir).execute("clean", "verify").assertLogText(
                "Setting project property \"errorprone.compile.argument\" to \"-Xplugin:ErrorProne -Xep:NullAway:ERROR")
                .assertLogText("assigning @Nullable expression to @NonNull field").assertLogText("COMPILATION ERROR :")
                .assertLogText("BUILD FAILURE");
    }

    @MavenPluginTest
    void extension() throws Exception {
        File basedir = resources.getBasedir("extension");

        maven.forProject(basedir).execute("clean", "verify").assertLogText(
                "Setting project property \"errorprone.compile.argument\" to \"-Xplugin:ErrorProne -Xep:NullAway:ERROR")
                .assertLogText("assigning @Nullable expression to @NonNull field").assertLogText("COMPILATION ERROR :")
                .assertLogText("BUILD FAILURE");
    }

}
