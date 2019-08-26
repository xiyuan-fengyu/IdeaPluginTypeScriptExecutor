package com.xiyuan.ts.execution;

import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.lang.javascript.TypeScriptFileType;
import com.intellij.lang.typescript.compiler.TypeScriptCompilerService;
import com.intellij.lang.typescript.compiler.TypeScriptCompilerSettings;
import com.intellij.lang.typescript.compiler.ui.TypeScriptServerServiceSettings;
import com.intellij.lang.typescript.tsconfig.TypeScriptConfig;
import com.intellij.lang.typescript.tsconfig.TypeScriptConfigUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtil;
import com.jetbrains.nodejs.run.NodeJsRunConfiguration;
import com.jetbrains.nodejs.run.NodeJsRunConfigurationType;
import com.xiyuan.ts.action.TypeScriptCompileCurrentAction;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;

import java.io.File;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by xiyuan_fengyu on 2018/6/10 9:32.
 */
public class NodeJsExecution {

    private static final Map<String, TypeScriptInfo> tsCaches = new HashMap<>();

    private static final Method getOptions;

    private static final Logger logger = Logger.getInstance(NodeJsExecution.class.toString());

    private static final Map<VirtualFile, Boolean> changedTsFiles = new ConcurrentHashMap<>();

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
        if (settings.getDefaultServiceOptions() == null) {
            settings.setDefaultServiceOptions("--sourceMap true");
        }
        settings.setRecompileOnChanges(true);
        settings.setUseConfig(true);
        settings.setUseService(true);
        TypeScriptCompilerService.getAll(project).forEach(service -> {

            // Angular Langulage Service 启用后编译会卡主一段时间然后失败，故禁用
            if (service.getClass().getSimpleName().equals("Angular2LanguageService")) {
                TypeScriptServerServiceSettings serviceSettings = service.getServiceSettings();
                if (serviceSettings != null) serviceSettings.setUseService(false);
            }
        });
    }

    public static void addChangedTsFiles(VirtualFile file) {
        changedTsFiles.put(file, true);
    }

    private static boolean isTsChanged(Project project, TypeScriptConfig config) {
        for (Map.Entry<VirtualFile, Boolean>  entry: changedTsFiles.entrySet()) {
            VirtualFile virtualFile = entry.getKey();
            String relativePath = ProjectUtil.calcRelativeToProjectPath(virtualFile, project);
            if (relativePath.startsWith("...")) {
                TypeScriptConfig configForFile = TypeScriptConfigUtil.getConfigForFile(project, virtualFile);
                if (Objects.equals(config, configForFile)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void removeTsChanged(Project project, TypeScriptConfig config) {
        Iterator<Map.Entry<VirtualFile, Boolean>> it = changedTsFiles.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<VirtualFile, Boolean> entry = it.next();
            VirtualFile virtualFile = entry.getKey();
            String relativePath = ProjectUtil.calcRelativeToProjectPath(virtualFile, project);
            if (relativePath.startsWith("...")) {
                TypeScriptConfig configForFile = TypeScriptConfigUtil.getConfigForFile(project, entry.getKey());
                if (Objects.equals(config, configForFile)) {
                    it.remove();
                }
            }
        }
    }

    private static void printInfoMessage(String msg) {
        Notification notification = new Notification("TypeScriptExecutor", "TypeScriptExecutor", msg, NotificationType.INFORMATION);
        Notifications.Bus.notify(notification);
        if (notification.getBalloon() != null) {
            notification.getBalloon().hide();
        }
    }

    public static void execute(AnActionEvent event, boolean debug) {
        Project project = event.getProject();
        VirtualFile virtualFile = event.getData(LangDataKeys.VIRTUAL_FILE);
        Module module = event.getData(LangDataKeys.MODULE);

        if (project == null || virtualFile == null || module == null) return;

        String tsPath = virtualFile.getCanonicalPath();
        printInfoMessage("prepare to execute " + tsPath);

        TypeScriptInfo typeScriptInfo = tsCaches.get(tsPath);
        if (typeScriptInfo == null) {
            printInfoMessage("init typescript file info ...");
            typeScriptInfo = new TypeScriptInfo(project, virtualFile);
            tsCaches.put(tsPath, typeScriptInfo);
            printInfoMessage("init typescript file info successfully");
        }
        typeScriptInfo.checkTypeScriptConfig(project, module, virtualFile);

        printInfoMessage("check whether js existed or typescript changed");
        final File compiledJs = typeScriptInfo.compiledJs;
        if (!compiledJs.exists() || isTsChanged(project, typeScriptInfo.typeScriptConfig)) {
            printInfoMessage("start to compile typescript");
            // 如果ts对应的编译后的js文件不存在，或者 typeScriptConfig 管理的ts文件有变动，则通过 typeScriptConfig 进行一次编译
            AnActionEvent actionEvent = new AnActionEvent(null,
                    new MyDataContext(event.getDataContext(), typeScriptInfo.typeScriptConfig == null ? null : typeScriptInfo.typeScriptConfig.getConfigFile()),
                    ActionPlaces.UNKNOWN, new Presentation(), ActionManager.getInstance(), 0);
            TypeScriptInfo finalTypeScriptInfo = typeScriptInfo;
            new TypeScriptCompileCurrentAction((project1, infos) -> {
                if (infos.isEmpty()) {
                    printInfoMessage("compiled typescript successfully");
                    finalTypeScriptInfo.execute(project, debug);
                }
                else {
                    printInfoMessage("compiled typescript with " + infos.size() + (infos.size() > 1 ? " errors" : "error"));
                }
            }).actionPerformed(actionEvent);
        }
        else {
            typeScriptInfo.execute(project, debug);
        }
        removeTsChanged(project, typeScriptInfo.typeScriptConfig);
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

        private File compiledJs;

        private TypeScriptConfig typeScriptConfig;

        private String tsconfigDir;

        private String rootDir;

        private String outDir;

        private String runConfName;

        private RunnerAndConfigurationSettings runConf;

        private TypeScriptInfo(Project project, VirtualFile virtualFile) {
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
        }

        private void checkTypeScriptConfig(Project project, Module module, VirtualFile virtualFile) {
            try {
                String tsPath = virtualFile.getCanonicalPath();
                typeScriptConfig = TypeScriptConfigUtil.getConfigForFile(project, virtualFile);
                if (typeScriptConfig != null) {
                    tsconfigDir = typeScriptConfig.getConfigDirectory().getCanonicalPath();

                    String rootDir = getPath(tsconfigDir, typeScriptConfig.getRawCompilerOption("rootDir"));
                    if (rootDir == null) {
                        @SystemIndependent String moduleFilePath = module.getModuleFilePath();
                        rootDir = PathUtil.toSystemIndependentName(PathUtil.getParentPath(moduleFilePath));
                        if (tsPath.indexOf(rootDir + "/") != 0) {
                            rootDir = tsconfigDir;
                        }
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
                else {
                    tsconfigDir = null;
                    @SystemIndependent String moduleFilePath = module.getModuleFilePath();
                    rootDir = PathUtil.toSystemIndependentName(PathUtil.getParentPath(moduleFilePath));
                    outDir = rootDir;
                    compiledJs = new File(tsPath.replaceAll("\\.ts$", ".js"));
                }
            } catch (Exception e) {
                logger.error(e);
            }
        }

        private void execute(Project project, boolean debug) {
            printInfoMessage("check settings");
            RunManager runManager = RunManager.getInstance(project);
            while (true) {
                List<RunConfiguration> configurationsList = runManager.getConfigurationsList(NodeJsRunConfigurationType.getInstance());
                if (runConf == null) {
                    try {
                        printInfoMessage("init runner and configuration settings");

                        String complieJsName = compiledJs.getName();
                        RunnerAndConfigurationSettings settings = runManager.createConfiguration(runConfName,
                                NodeJsRunConfigurationType.class);
                        RunConfiguration configuration = settings.getConfiguration();
                        Object state = getOptions.invoke(configuration);
                        Method setWorkingDir = state.getClass().getDeclaredMethod("setWorkingDir", String.class);
                        setWorkingDir.setAccessible(true);
                        setWorkingDir.invoke(state, compiledJs.getParent());
                        Method setPathToJsFile = state.getClass().getDeclaredMethod("setPathToJsFile", String.class);
                        setPathToJsFile.setAccessible(true);
                        setPathToJsFile.invoke(state, complieJsName);
                        runManager.addConfiguration(settings);
                        runConf = settings;

                        printInfoMessage("add settings to RunManager");
                        break;
                    } catch (Exception e) {
                        logger.error(e);
                    }
                }
                else if (!runConfExisted(configurationsList, runConf.getConfiguration())) {
                    runConf = null;
                }
                else break;
            }

            if (runManager.getSelectedConfiguration() != runConf) {
                printInfoMessage("select settings");
                runManager.setSelectedConfiguration(runConf);
            }

            printInfoMessage("start to execute");
            ProgramRunnerUtil.executeConfiguration(runConf,
                    debug ? DefaultDebugExecutor.getDebugExecutorInstance() : DefaultRunExecutor.getRunExecutorInstance());
        }

        private boolean runConfExisted( List<RunConfiguration> configurationsList, RunConfiguration runConfiguration) {
            try {
                Method getWorkingDirectory = runConfiguration.getClass().getDeclaredMethod("getWorkingDirectory");
                getWorkingDirectory.setAccessible(true);
                Method getExePath = runConfiguration.getClass().getDeclaredMethod("getExePath");
                getExePath.setAccessible(true);
                for (RunConfiguration configuration : configurationsList) {
                    if (configuration == runConfiguration &&
                            Objects.equals(getWorkingDirectory.invoke(configuration), compiledJs.getParent())) {
                        return true;
                    }
                }
            }
            catch (Exception e) {
            }
            return false;
        }

    }

    private static class MyDataContext implements DataContext {

        private final DataContext parent;

        private final VirtualFile virtualFile;

        public MyDataContext(DataContext parent, VirtualFile virtualFile) {
            this.parent = parent;
            this.virtualFile = virtualFile;
        }

        @Nullable
        @Override
        public Object getData(String s) {
            if (CommonDataKeys.VIRTUAL_FILE.getName().equals(s)) {
                return virtualFile;
            }
            return parent.getData(s);
        }

    }

}
