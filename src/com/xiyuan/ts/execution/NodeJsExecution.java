package com.xiyuan.ts.execution;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunManager;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.lang.javascript.service.JSLanguageServiceResultContainer;
import com.intellij.lang.typescript.compiler.TypeScriptCompilerService;
import com.intellij.lang.typescript.compiler.TypeScriptCompilerSettings;
import com.intellij.lang.typescript.compiler.action.before.TypeScriptCompileBeforeRunTaskProvider;
import com.intellij.lang.typescript.tsconfig.TypeScriptConfig;
import com.intellij.lang.typescript.tsconfig.TypeScriptConfigService;
import com.intellij.lang.typescript.tsconfig.TypeScriptConfigUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.jetbrains.nodejs.run.NodeJsRunConfiguration;
import com.jetbrains.nodejs.run.NodeJsRunConfigurationState;
import com.jetbrains.nodejs.run.NodeJsRunConfigurationType;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

/**
 * Created by xiyuan_fengyu on 2018/6/10 9:32.
 */
public class NodeJsExecution {

    private static final String TypeScriptFileType = "com.intellij.lang.javascript.TypeScriptFileType";

    private static final String tsconfig_json = "tsconfig.json";

    private static final JsonParser jsonParser = new JsonParser();

    private static final Map<String, RunnerAndConfigurationSettingsImpl> configurations = new HashMap<>();

    private static final Method getOptions;

    private static final TypeScriptCompileBeforeRunTaskProvider provider = new TypeScriptCompileBeforeRunTaskProvider();

    private static final Logger logger = Logger.getInstance(NodeJsExecution.class.toString());

    static {
        Method temp = null;
        try {
            temp = NodeJsRunConfiguration.class.getDeclaredMethod("getOptions");
            temp.setAccessible(true);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        getOptions = temp;
    }

    public static void resetTsCompilerSettings(Project project) {
        try {
            Iterator<TypeScriptConfig> it = TypeScriptConfigService.Provider.getConfigFiles(project).iterator();
            if (it.hasNext()) {
                TypeScriptCompilerSettings settings = TypeScriptCompilerSettings.getSettings(project);
                settings.setRecompileOnChanges(true);
                settings.setUseConfig(true);
                settings.setUseService(true);
                Future<JSLanguageServiceResultContainer> future = TypeScriptCompilerService.getDefaultService(project).compileConfigProjectAndGetErrors(it.next());
                ApplicationManager.getApplication().invokeLater(() -> {
                    try {
                        future.get();
                        VirtualFileManager.getInstance().syncRefresh();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void execute(Project project, VirtualFile virtualFile, boolean debug) {
        RunManager runManager = RunManager.getInstance(project);
        RunnerAndConfigurationSettingsImpl configuration = getConfiguration(project, virtualFile);
        if (configuration != null) {
            List<RunConfiguration> configurationsList = runManager.getConfigurationsList(NodeJsRunConfigurationType.getInstance());
            if (!configurationsList.contains(configuration.getConfiguration())) {
                runManager.addConfiguration(configuration);
            }
            if (runManager.getSelectedConfiguration() != configuration) {
                runManager.setSelectedConfiguration(configuration);
            }
            ProgramRunnerUtil.executeConfiguration(configuration,
                    debug ? DefaultDebugExecutor.getDebugExecutorInstance() : DefaultRunExecutor.getRunExecutorInstance());
        }
    }

    public static boolean executable(Project project, VirtualFile virtualFile) {
        return TypeScriptFileType.equals(virtualFile.getFileType().getClass().getName())
                && getConfiguration(project, virtualFile) != null;
    }

    private static RunnerAndConfigurationSettingsImpl getConfiguration(Project project, VirtualFile virtualFile) {
        String tsPath = virtualFile.getCanonicalPath();
        RunnerAndConfigurationSettingsImpl configuration = configurations.get(tsPath);

        if (configuration == null) {
            if (tsPath == null) return null;

            File complieJs = null;
            TypeScriptConfig tsconfig = null;

            String rootDir = null;
            String outDir = null;

            try {
                tsconfig = TypeScriptConfigUtil.getConfigForFile(project, virtualFile);
                if (tsconfig != null) {
                    String tscofigStr = new String(tsconfig.getConfigFile().contentsToByteArray(), StandardCharsets.UTF_8);
                    JsonObject tsconfigJson = jsonParser.parse(tscofigStr).getAsJsonObject();
                    JsonObject compilerOptions = tsconfigJson.getAsJsonObject("compilerOptions");

                    String tsconfigDir = tsconfig.getConfigDirectory().getCanonicalPath();

                    rootDir = compilerOptions.get("rootDir").getAsString();
                    outDir = compilerOptions.get("outDir").getAsString();

                    rootDir = getPath(tsconfigDir, rootDir);
                    outDir = getPath(tsconfigDir, outDir);

                    if ((rootDir.equals(outDir)) || tsPath.replaceAll("\\\\", "/")
                            .indexOf((rootDir + File.separator).replaceAll("\\\\", "/")) == 0) {
                        if (!rootDir.equals(outDir)) {
                            String relatedTsPath = tsPath
                                    .substring(rootDir.length() + 1)
                                    .replaceAll("\\.ts$", ".js");

                            complieJs = new File(outDir + File.separator + relatedTsPath);
                        } else {
                            complieJs = new File(tsPath.replaceAll("\\.ts$", ".js"));
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                //logger.error(e);

                if (rootDir == null && outDir == null || rootDir == outDir)
                {
                    complieJs = new File(tsPath.replaceAll("\\.ts$", ".js"));
                }
            }

            try {
                if (complieJs != null && complieJs.exists()) {
                    String complieJsName = complieJs.getName();
                    RunManager runManager = RunManager.getInstance(project);
                    configuration = (RunnerAndConfigurationSettingsImpl) runManager.createConfiguration(virtualFile.getName(),
                            NodeJsRunConfigurationType.getInstance().getFactory());
                    RunConfiguration con = configuration.getConfiguration();
                    NodeJsRunConfigurationState state = (NodeJsRunConfigurationState) getOptions.invoke(con);
                    state.setWorkingDir(complieJs.getParent());
                    state.setPathToJsFile(complieJsName);
                    runManager.addConfiguration(configuration);
                    configurations.put(tsPath, configuration);
                }
            } catch (Exception e) {
                e.printStackTrace();
                //logger.error(e);
            }
        }
        return configuration;
    }

    private static String getPath(String curDirPath, String path) {
        if (path.startsWith("/")) return path;
        try {
            return new File(curDirPath + "/" + path).getCanonicalPath();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return path;
    }

}
