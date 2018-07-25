package com.xiyuan.ts.execution;

import com.google.gson.JsonParser;
import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.lang.javascript.TypeScriptFileType;
import com.intellij.lang.javascript.service.JSLanguageServiceResultContainer;
import com.intellij.lang.typescript.compiler.TypeScriptCompilerService;
import com.intellij.lang.typescript.compiler.TypeScriptCompilerSettings;
import com.intellij.lang.typescript.compiler.languageService.protocol.commands.TypeScriptServiceCommandClean;
import com.intellij.lang.typescript.compiler.ui.TypeScriptServerServiceSettings;
import com.intellij.lang.typescript.tsconfig.TypeScriptConfig;
import com.intellij.lang.typescript.tsconfig.TypeScriptConfigService;
import com.intellij.lang.typescript.tsconfig.TypeScriptConfigUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.PathUtil;
import com.jetbrains.nodejs.run.NodeJsRunConfiguration;
import com.jetbrains.nodejs.run.NodeJsRunConfigurationState;
import com.jetbrains.nodejs.run.NodeJsRunConfigurationType;
import org.jetbrains.annotations.SystemIndependent;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Created by xiyuan_fengyu on 2018/6/10 9:32.
 */
public class NodeJsExecution {

    private static final JsonParser jsonParser = new JsonParser();

    private static final Map<String, TypeScriptInfo> tsCaches = new HashMap<>();

    private static final Method getOptions;

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
        TypeScriptCompilerSettings settings = TypeScriptCompilerSettings.getSettings(project);
        if (settings.getDefaultServiceOptions() == null) settings.setDefaultServiceOptions("--sourceMap true");
        settings.setRecompileOnChanges(true);
        settings.setUseConfig(true);
        settings.setUseService(true);

        TypeScriptCompilerService.getAll(project).forEach(service -> {
            // Angular Langulage Service 启用后无法自动监视文件并自动编译，故禁用
            if (service.getClass().getSimpleName().equals("Angular2LanguageService")) {
                TypeScriptServerServiceSettings serviceSettings = service.getServiceSettings();
                if (serviceSettings != null) serviceSettings.setUseService(false);
            }
        });

        doCompile(project, TypeScriptConfigService.Provider.getConfigFiles(project), () -> {
            // System.out.println("compile finish");
        });
    }

    private static void doCompile(Project project, Collection<TypeScriptConfig> configFiles, Runnable callback) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            List<Future<JSLanguageServiceResultContainer>> futures = configFiles.stream().filter(Objects::nonNull)
                    .map(configFile -> TypeScriptCompilerService.getDefaultService(project).compileConfigProjectAndGetErrors(configFile))
                    .collect(Collectors.toList());
            try {
                for (Future<JSLanguageServiceResultContainer> future : futures) {
                    if (future != null) future.get(60, TimeUnit.SECONDS);
                }
                VirtualFileManager.getInstance().asyncRefresh(null);
                if (callback != null) callback.run();
            } catch (Exception e) {
                logger.error(e);
            }
        });
    }

    public static void execute(AnActionEvent event, boolean debug) {
        Project project = event.getProject();
        VirtualFile virtualFile = event.getData(DataKeys.VIRTUAL_FILE);
        Module module = event.getData(DataKeys.MODULE);

        if (project == null || virtualFile == null || module == null) return;

        String tsPath = virtualFile.getCanonicalPath();
        TypeScriptInfo tempTypeScriptInfo = tsCaches.get(tsPath);
        if (tempTypeScriptInfo == null) {
            tempTypeScriptInfo = new TypeScriptInfo(project, module, virtualFile);
            tsCaches.put(tsPath, tempTypeScriptInfo);
        }
        TypeScriptInfo typeScriptInfo = tempTypeScriptInfo;

        if (!typeScriptInfo.compiledJs.exists()) {
            TypeScriptCompilerService.getDefaultService(project).sendCleanCommandToCompiler(
                    new TypeScriptServiceCommandClean(true));
        }

        doCompile(typeScriptInfo.project, Collections.singletonList(typeScriptInfo.typeScriptConfig), () -> {
            typeScriptInfo.execute(debug);
        });
    }

    public static boolean executable(VirtualFile virtualFile) {
        return virtualFile.getFileType() instanceof TypeScriptFileType;
    }

    private static String getPath(String curDirPath, String path) {
        if (path == null) return null;
        if (!path.startsWith("/")) {
            try {
                path = new File(curDirPath + "/" + path).getCanonicalPath();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        return PathUtil.toSystemIndependentName(path);
    }

    private static class TypeScriptInfo {

        private static final Map<String, Boolean> existedRunConfNames = new HashMap<>();

        private Project project;

        private File compiledJs;

        private TypeScriptConfig typeScriptConfig;

        private String tsconfigDir;

        private String rootDir;

        private String outDir;

        private String runConfName;

        private RunnerAndConfigurationSettings runConf;

        private TypeScriptInfo(Project project, Module module, VirtualFile virtualFile) {
            this.project = project;

            String tsPath = virtualFile.getCanonicalPath();
            if (tsPath == null) return;

            String projectPath = PathUtil.toSystemIndependentName(project.getBasePath());
            String tsName = virtualFile.getName();
            if (existedRunConfNames.containsKey(tsName)) {
                if (projectPath != null && tsPath.indexOf(projectPath + "/") == 0) {
                    tsName = tsPath.substring(projectPath.length() + 1);
                }
            }
            this.runConfName = tsName;
            existedRunConfNames.put(tsName, true);

            try {
                typeScriptConfig = TypeScriptConfigUtil.getConfigForFile(project, virtualFile);
                if (typeScriptConfig != null) {
                    tsconfigDir = typeScriptConfig.getConfigDirectory().getCanonicalPath();

                    String rootDir = getPath(tsconfigDir, typeScriptConfig.getRawCompilerOption("rootDir"));
                    if (rootDir == null) {
                        @SystemIndependent String moduleFilePath = module.getModuleFilePath();
                        rootDir = PathUtil.toSystemIndependentName(PathUtil.getParentPath(moduleFilePath));
                    }

                    String outDir = getPath(tsconfigDir, typeScriptConfig.getRawCompilerOption("outDir"));

                    if (tsPath.indexOf(rootDir + "/") == 0) {
                        if (!rootDir.equals(outDir)) {
                            String relatedTsPath = tsPath.substring(rootDir.length() + 1)
                                    .replaceAll("\\.ts$", ".js");
                            compiledJs = new File(outDir + "/" + relatedTsPath);
                        }
                    }
                    this.rootDir = rootDir;
                    this.outDir = outDir;
                }
            } catch (Exception e) {
                logger.error(e);
            }

            if (compiledJs == null)
            {
                compiledJs = new File(tsPath.replaceAll("\\.ts$", ".js"));
            }
        }

        private void execute(boolean debug) {
            RunManager runManager = RunManager.getInstance(project);
            while (true) {
                List<RunConfiguration> configurationsList = runManager.getConfigurationsList(NodeJsRunConfigurationType.getInstance());
                if (runConf == null) {
                    try {
                        String complieJsName = compiledJs.getName();
                        RunnerAndConfigurationSettings settings = runManager.createConfiguration(runConfName,
                                NodeJsRunConfigurationType.getInstance().getFactory());
                        RunConfiguration configuration = settings.getConfiguration();
                        NodeJsRunConfigurationState state = (NodeJsRunConfigurationState) getOptions.invoke(configuration);
                        state.setWorkingDir(compiledJs.getParent());
                        state.setPathToJsFile(complieJsName);
                        runManager.addConfiguration(settings);
                        runConf = settings;
                        break;
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        logger.error(e);
                    }
                }
                else if (!configurationsList.contains(runConf.getConfiguration())) {
                    runConf = null;
                }
            }

            if (runManager.getSelectedConfiguration() != runConf) {
                runManager.setSelectedConfiguration(runConf);
            }
            ProgramRunnerUtil.executeConfiguration(runConf,
                    debug ? DefaultDebugExecutor.getDebugExecutorInstance() : DefaultRunExecutor.getRunExecutorInstance());
        }

    }

}
