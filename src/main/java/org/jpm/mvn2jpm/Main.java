package org.jpm.mvn2jpm;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.module.ModuleFinder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Pattern;

import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.jboss.shrinkwrap.resolver.api.maven.MavenResolvedArtifact;

public class Main {
    public static class Cmd {
        public static String run(String cmd) {
            var processBuilder = new ProcessBuilder();
            System.out.println("$ " + cmd);
            processBuilder.command("sh", "-c", cmd);
            try {
                var process = processBuilder.start();
                var output = new StringBuilder();
                var reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line + "\n");
                }
                int exitValue = process.waitFor();
                if (exitValue == 0) {
                    return output.toString();
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } return null;
        }
    }


    public static String getJpmVersion(String mvnVersion) {
        int major = 0;
        int minor = 0;
        int patch = 0;
        var pattern = Pattern.compile("(\\d+)(\\.(\\d+))?(\\.(\\d+))?(-.*)?");
        var matcher = pattern.matcher(mvnVersion);
        if (!matcher.matches()) {
            throw new RuntimeException("Bad mvn version: " + mvnVersion);
        }
        try {
            major = Integer.parseInt(matcher.group(1));
        } catch (Exception e) {}
        try {
            minor = Integer.parseInt(matcher.group(3));
        } catch (Exception e) {}
        try {
            patch = Integer.parseInt(matcher.group(5));
        } catch (Exception e) {}
        return "" + major + "." + minor + "." + patch;
    }

    public static String getJpmModuleName(File jarFile) {
        var finder = ModuleFinder.of(jarFile.toPath());
        var module = finder.findAll().iterator().next();
        return module.descriptor().name();
    }

    public static MavenResolvedArtifact getArtifactFor(String group, String artifactName, String version, MavenResolvedArtifact[] artifacts) {
        for (var artifact: artifacts) {
          var coordinate = artifact.getCoordinate();
          if (!coordinate.getGroupId().equals(group)) {
              continue;
          }
          if (!coordinate.getArtifactId().equals(artifactName)) {
              continue;
          }
          if (coordinate.getVersion().equals(version)) {
              return artifact;
          }
        }
        return null;
    }

    public static String createJpmFile(MavenResolvedArtifact artifact, MavenResolvedArtifact[] artifacts) {
        StringBuilder str = new StringBuilder();
        str.append("{\n");
        var coordinate = artifact.getCoordinate();
        String group = coordinate.getGroupId();
        String name = coordinate.getArtifactId();
        String version = coordinate.getVersion();
        str.append("    module: \"" + getJpmModuleName(artifact.asFile()) + "-" + getJpmVersion(version) + "\"\n");
        str.append("    dependencies: [");
        int i = 0;
        for (var dependency : artifact.getDependencies()) {
            var dependencyCoordinate = dependency.getCoordinate();
            String dependencyGroup = dependencyCoordinate.getGroupId();
            String dependencyName = dependencyCoordinate.getArtifactId();
            String dependencyVersion = dependencyCoordinate.getVersion();
            var dependencyArtifact = getArtifactFor(dependencyGroup, dependencyName, dependencyVersion, artifacts);
            if (i++ != 0) {
              str.append(",");
            }
            str.append("\n        \"" + getJpmModuleName(dependencyArtifact.asFile()) + "-" + getJpmVersion(dependencyVersion) + "\"");
        }
        str.append("\n    ]\n");
        str.append("}\n");
        return str.toString();
    }

    public static void main(String[] args) throws IOException {
        for (var arg: args) {
            var artifacts = Maven.resolver().resolve(arg).withTransitivity().as(MavenResolvedArtifact.class);
            for (var artifact: artifacts) {
                var tempDir = Files.createTempDirectory("mvn2jpm-");
                var jpmDir = tempDir.resolve("META-INF").resolve("jpm");
                jpmDir.toFile().mkdirs();
                Path newJar = tempDir.resolve(getJpmModuleName(artifact.asFile()) + "-" + getJpmVersion(artifact.getResolvedVersion()) + ".jar");
                Path originalJar = artifact.asFile().toPath();
                Path jpmPath = jpmDir.resolve("main.jpm");
                String jpmFile = createJpmFile(artifact, artifacts);
                Files.copy(originalJar, newJar, StandardCopyOption.REPLACE_EXISTING);
                Files.write(jpmPath, jpmFile.getBytes());
                Cmd.run("jar --update --file " + newJar.toString() + " -C " + tempDir + " META-INF/jpm/main.jpm");
                Cmd.run("jar --update --file " + newJar.toString() + " --module-version " + getJpmVersion(artifact.getResolvedVersion()));
                Cmd.run("jpm publish " + newJar.toString());
            }
        }
    }
}

