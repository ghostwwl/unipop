package org.unipop.query.controller;

import org.apache.commons.configuration.Configuration;
import org.json.JSONObject;
import org.unipop.schema.property.PropertySchema;
import org.unipop.structure.traversalfilter.TraversalFilter;
import org.unipop.structure.UniGraph;
import org.unipop.util.DirectoryWatcher;
import org.unipop.util.GitWatcher;
import org.unipop.util.PropertySchemaFactory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ConfigurationControllerManager implements ControllerManager {

    protected Set<SourceProvider> sourceProviders = new HashSet<>();
    protected Set<UniQueryController> controllers = new HashSet<>();
    protected DirectoryWatcher watcher;
    protected GitWatcher gitWatcher;
    protected Path path;
    protected UniGraph graph;
    protected List<PropertySchema.PropertySchemaBuilder> thirdPartyPropertySchemas;
    protected TraversalFilter filter;

    public ConfigurationControllerManager(UniGraph graph, Configuration configuration, List<PropertySchema.PropertySchemaBuilder> thirdPartyPropertySchemas, TraversalFilter filter) throws Exception {
        // path = Paths.get(configuration.getString("providers"));

        // need to check where the properties coming from
        path = Paths.get("../unipop-core/resources-git");
        String gitRemote = "https://github.com/edeneliel/unipop-resources.git";

        this.graph = graph;
        this.thirdPartyPropertySchemas = thirdPartyPropertySchemas;
        this.filter = filter;
        this.gitWatcher = new GitWatcher(gitRemote, path, 10000);
        this.watcher = new DirectoryWatcher(path, configuration.getInt("controllerManager.interval", 10000),
                (newPath) -> loadControllers(), () -> gitWatcher.isNeedUpdateAndReset());
        loadControllers();
        this.watcher.start();
        this.gitWatcher.start();
    }

    private void loadControllers() throws IOException {
        controllers.clear();
        sourceProviders.forEach(SourceProvider::close);
        sourceProviders.clear();
        Files.walk(path).filter((file) -> file.toString().endsWith(".json")).forEach(filePath -> {
            if (Files.isRegularFile(filePath)){
                String providerJson = readFile(filePath.toAbsolutePath().toString());
                JSONObject providerConfig = new JSONObject(providerJson);
                String providerClass = providerConfig.getString("class");
                SourceProvider sourceProvider = null;
                try {
                    sourceProvider = Class.forName(providerClass).asSubclass(SourceProvider.class).newInstance();
                    PropertySchemaFactory.build(sourceProvider.providerBuilders(), thirdPartyPropertySchemas);
                    Set<UniQueryController> controllers = sourceProvider.init(graph, providerConfig, filter);
                    this.controllers.addAll(controllers);
                    this.sourceProviders.add(sourceProvider);
                } catch (Exception e) {
                    throw new RuntimeException("class: " + providerClass + " not found");
                }
            }
        });
    }

    private static String readFile(String filename) {
        String result = "";
        try {
            BufferedReader br = new BufferedReader(new FileReader(filename));
            StringBuilder sb = new StringBuilder();
            String line = br.readLine();
            while (line != null) {
                sb.append(line);
                line = br.readLine();
            }
            result = sb.toString();
            br.close();
        } catch(Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    @Override
    public Set<UniQueryController> getControllers() {
        return controllers;
    }

    @Override
    public void close() {
        sourceProviders.forEach(SourceProvider::close);

        try {
            watcher.stop();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String toString() {
        return "ConfigurationControllerManager{" +
                "controllers=" + controllers +
                ", sourceProviders=" + sourceProviders +
                '}';
    }
}
