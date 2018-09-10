package com.xiyuan.ts.setting.autoCompile;

import com.intellij.lang.typescript.tsconfig.TypeScriptConfig;
import com.intellij.lang.typescript.tsconfig.TypeScriptConfigService;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.util.PathUtil;
import com.xiyuan.ts.model.tuple.Tuple2;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by xiyuan_fengyu on 2018/9/10 9:54.
 */
public class AutoCompileConfigurable implements SearchableConfigurable {

    private Project project;

    private AutoCompileConfig autoCompileConfig;

    private AutoCompileForm gui;

    private Map<String, Boolean> autoCompileEnablesModify = new HashMap<>();

    public AutoCompileConfigurable(@NotNull Project project) {
        this.project = project;
        this.autoCompileConfig = AutoCompileConfig.getInstance(project);
    }

    @NotNull
    @Override
    public String getId() {
        return "com.xiyuan.ts.setting.moduleCompile.ModuleCompileConfigurable";
    }

    @Nls
    @Override
    public String getDisplayName() {
        return "TypeScript Auto Compile";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        gui = new AutoCompileForm();
        gui.setAutoCompileToggleListener((configFileRelativePath, selected) -> {
            if (autoCompileConfig.isAutoCompileEnable(configFileRelativePath) == selected) {
                autoCompileEnablesModify.remove(configFileRelativePath);
            }
            else {
                autoCompileEnablesModify.put(configFileRelativePath, selected);
            }
        });
        reloadComponent();
        return gui.getRootPanel();
    }

    private void reloadComponent() {
        String projectDir = PathUtil.toSystemIndependentName(project.getBaseDir().getCanonicalPath()) + "/";
        Collection<TypeScriptConfig> configFiles = TypeScriptConfigService.Provider.getConfigFiles(project);
        List<Tuple2<String, Boolean>> moduleCompileEnables = configFiles.stream().map(configFile -> {
            String configFilePath = PathUtil.toSystemIndependentName(configFile.getConfigFile().getCanonicalPath());
            if (configFilePath != null) {
                if (configFilePath.indexOf(projectDir) == 0) {
                    configFilePath = configFilePath.substring(projectDir.length());
                }
                return new Tuple2<>(configFilePath, autoCompileConfig.isAutoCompileEnable(configFilePath));
            }
            return null;
        }).filter(Objects::nonNull).collect(Collectors.toList());
        gui.loadAutoCompileEnables(moduleCompileEnables);
    }

    @Override
    public boolean isModified() {
        return !autoCompileEnablesModify.isEmpty();
    }

    @Override
    public void apply() throws ConfigurationException {
        autoCompileEnablesModify.forEach((key, value) -> this.autoCompileConfig.setAutoCompileEnable(key, value));
        this.autoCompileEnablesModify.clear();
    }

    @Override
    public void reset() {
        this.autoCompileEnablesModify.clear();
        this.reloadComponent();
    }

}
