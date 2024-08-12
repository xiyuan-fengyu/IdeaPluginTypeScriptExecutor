package com.xiyuan.ts.execution;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.intellij.execution.*;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.lang.javascript.TypeScriptFileType;
import com.intellij.lang.javascript.buildTools.npm.beforeRun.NpmBeforeRunTask;
import com.intellij.lang.javascript.buildTools.npm.beforeRun.NpmBeforeRunTaskProvider;
import com.intellij.lang.javascript.buildTools.npm.rc.NpmCommand;
import com.intellij.lang.javascript.buildTools.npm.rc.NpmRunConfiguration;
import com.intellij.lang.javascript.buildTools.npm.rc.NpmRunConfigurationBuilder;
import com.intellij.lang.javascript.buildTools.npm.rc.NpmRunSettings;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileUtil;
import groovy.lang.Tuple2;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

/**
 * Created by xiyuan_fengyu on 2018/6/10 9:32.
 */
public class NodeJsExecution {

    private static final Field NpmRunSettings_myArguments;

    private static final Key<NpmBeforeRunTask> NpmBeforeRunTaskProvider_PROVIDER_ID;

    private static final Logger logger = Logger.getInstance(NodeJsExecution.class.toString());

    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    static {
        Field temp_myArguments = null;
        Key<NpmBeforeRunTask> temp_ProviderId = null;
        try {
            temp_myArguments = NpmRunSettings.class.getDeclaredField("myArguments");
            temp_myArguments.setAccessible(true);

            Field providerId = NpmBeforeRunTaskProvider.class.getDeclaredField("PROVIDER_ID");
            providerId.setAccessible(true);
            //noinspection unchecked
            temp_ProviderId = (Key<NpmBeforeRunTask>) providerId.get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            logger.error(e.getMessage());
        }
        NpmRunSettings_myArguments = temp_myArguments;
        NpmBeforeRunTaskProvider_PROVIDER_ID = temp_ProviderId;
    }

    private static String normalizePath(String path) {
        return path.replace('\\', '/').replaceFirst("/$", "");
    }

    private static Tuple2<String, Boolean> initPackageJson(Project project, VirtualFile tsFile) throws IOException {
        String projectPath = project.getBasePath();
        if (projectPath == null) {
            throw new RuntimeException("project path is null");
        }

        projectPath = normalizePath(projectPath);
        VirtualFile visitDir = tsFile.getParent();
        String packageJsonF = null;
        while (true) {
            String visitDirPath = normalizePath(visitDir.getPath());
            String tempPackageJsonF = visitDirPath + "/package.json";
            if (Files.exists(new File(tempPackageJsonF).toPath())) {
                packageJsonF = tempPackageJsonF;
                break;
            }

            if (visitDirPath.equals(projectPath)) {
                break;
            }

            visitDir = visitDir.getParent();
        }

        if (packageJsonF == null) {
            packageJsonF = projectPath + "/package.json";
        }

        File packageJsonFile = new File(packageJsonF);

        boolean packageJsonUpdated = !packageJsonFile.exists();
        JsonObject packageJson = packageJsonFile.exists() ?
                gson.fromJson(Files.readString(packageJsonFile.toPath(), StandardCharsets.UTF_8), JsonObject.class) : new JsonObject();

        if (!packageJson.has("name")) {
            packageJson.addProperty("name", project.getName());
            packageJsonUpdated = true;
        }

        if (!packageJson.has("scripts")) {
            packageJson.add("scripts", new JsonObject());
            packageJsonUpdated = true;
        }
        JsonObject scripts = packageJson.getAsJsonObject("scripts");
        if (!scripts.has("ts-node")) {
            scripts.addProperty("ts-node", "ts-node");
            packageJsonUpdated = true;
        }

        if (!packageJson.has("devDependencies")) {
            packageJson.add("devDependencies", new JsonObject());
            packageJsonUpdated = true;
        }
        JsonObject devDependencies = packageJson.getAsJsonObject("devDependencies");
        if (!devDependencies.has("@types/node")) {
            devDependencies.addProperty("@types/node", "^18.19.43");
            packageJsonUpdated = true;
        }
        if (!devDependencies.has("typescript")) {
            devDependencies.addProperty("typescript", "^4.9.5");
            packageJsonUpdated = true;
        }
        if (!devDependencies.has("ts-node")) {
            devDependencies.addProperty("ts-node", "^10.9.2");
            packageJsonUpdated = true;
        }

        if (packageJsonUpdated) {
            VirtualFile packageJsonDir = VirtualFileManager.getInstance().findFileByUrl(
                    "file://" + packageJsonF.replace("/package.json", ""));
            assert packageJsonDir != null;

            Application application = ApplicationManager.getApplication();
            application.runWriteAction(() -> {
                try {
                    VirtualFile packageJsonVF = packageJsonDir.createChildData(application, "package.json");
                    VirtualFileUtil.writeBytes(packageJsonVF, gson.toJson(packageJson).getBytes(StandardCharsets.UTF_8));
//                    InstallNodeLocalDependenciesAction.runAndShowConsole(project, packageJsonVF);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        return new Tuple2<>(packageJsonF, packageJsonUpdated);
    }

    public static void execute(AnActionEvent event, boolean debug) throws IOException, IllegalAccessException, ExecutionException {
        logger.debug(String.format("%s %s", debug ? "debug" : "run", event.getData(CommonDataKeys.VIRTUAL_FILE)));

        Project project = event.getProject();
        VirtualFile virtualFile = event.getData(CommonDataKeys.VIRTUAL_FILE);

        assert project != null;
        assert project.getBasePath() != null;
        assert virtualFile != null;

        String projectDir = normalizePath(project.getBasePath());
        Tuple2<String, Boolean> initPackageJsonRes = initPackageJson(project, virtualFile);
        String packageJsonF = initPackageJsonRes.getV1();
        boolean packageJsonUpdated = initPackageJsonRes.getV2();

        String packageJsonDir = normalizePath(new File(packageJsonF).getParent());
        String scriptF = normalizePath(virtualFile.getPath());
        String scriptRelativeToProject = scriptF.startsWith(projectDir) ? scriptF.substring(projectDir.length() + 1) : scriptF;
        String scriptRelativeToPackageJson = scriptF.startsWith(packageJsonDir) ? scriptF.substring(packageJsonDir.length() + 1) : scriptF;

        RunManager runManager = RunManager.getInstance(project);
        RunnerAndConfigurationSettings npmRunConfiguration = runManager.findConfigurationByTypeAndName("js.build_tools.npm", "ts-node " + scriptRelativeToProject);
        if (npmRunConfiguration == null) {
            HashMap<String, Object> options = new HashMap<>();
            options.put(NpmRunConfigurationBuilder.RUN_SCRIPT, "ts-node");

            RunnerAndConfigurationSettings temp = new NpmRunConfigurationBuilder(project)
                    .createRunConfiguration(
                            "ts-node " + scriptRelativeToProject,
                            VirtualFileManager.getInstance().findFileByUrl("file://" + packageJsonDir),
                            packageJsonF,
                            options
                    );
            NpmRunConfiguration configuration = (NpmRunConfiguration) temp.getConfiguration();
            NpmRunSettings runSettings = configuration.getRunSettings();
            NpmRunSettings_myArguments.set(runSettings, "-- " + scriptRelativeToPackageJson);

            npmRunConfiguration = temp;
        }
        if (runManager.getSelectedConfiguration() != npmRunConfiguration) {
            runManager.setSelectedConfiguration(npmRunConfiguration);
        }

        List<BeforeRunTask<?>> beforeRunTasks = new ArrayList<>(npmRunConfiguration.getConfiguration().getBeforeRunTasks());
        int found = -1;
        for (int i = 0; i < beforeRunTasks.size(); i++) {
            BeforeRunTask<?> beforeRunTask = beforeRunTasks.get(i);
            if (beforeRunTask instanceof NpmBeforeRunTask && ((NpmBeforeRunTask) beforeRunTask).getSettings().getCommand() == NpmCommand.INSTALL) {
                found = i;
                break;
            }
        }
        if (packageJsonUpdated && found == -1) {
            BeforeRunTaskProvider<NpmBeforeRunTask> provider = NpmBeforeRunTaskProvider.getProvider(project, NpmBeforeRunTaskProvider_PROVIDER_ID);
            assert provider != null;
            NpmRunConfiguration configuration = new NpmRunConfiguration(project, npmRunConfiguration.getFactory(), "install");
            beforeRunTasks = new ArrayList<>(beforeRunTasks);
            beforeRunTasks.add(provider.createTask(configuration));
            npmRunConfiguration.getConfiguration().setBeforeRunTasks(beforeRunTasks);

            try {
                List<BeforeRunTask<?>> runTasks = npmRunConfiguration.getConfiguration().getBeforeRunTasks();
                NpmBeforeRunTask task = (NpmBeforeRunTask) runTasks.get(runTasks.size() - 1);

                Constructor<NpmRunSettings.Builder> constructor = NpmRunSettings.Builder.class.getDeclaredConstructor(NpmRunSettings.class);
                constructor.setAccessible(true);
                NpmRunSettings.Builder builder = constructor.newInstance(task.getSettings());
                builder.setCommand(NpmCommand.INSTALL);
                builder.setPackageJsonPath(packageJsonF);

                Field mySettings = NpmBeforeRunTask.class.getDeclaredField("mySettings");
                mySettings.setAccessible(true);
                mySettings.set(task, builder.build());
            } catch (Exception e) {
                logger.error(e.getMessage());
            }
        }
        else if (!packageJsonUpdated && found != -1) {
            beforeRunTasks.remove(found);
            npmRunConfiguration.getConfiguration().setBeforeRunTasks(beforeRunTasks);
        }

        Executor executor = debug ? DefaultDebugExecutor.getDebugExecutorInstance() : DefaultRunExecutor.getRunExecutorInstance();
        ExecutionEnvironment environment = ExecutionEnvironmentBuilder.create(executor, npmRunConfiguration)
                .contentToReuse(null).dataContext(null).activeTarget().build();
        final RunnerAndConfigurationSettings finalRunConfiguration = npmRunConfiguration;
        ProgramRunnerUtil.executeConfigurationAsync(environment, true, true, descriptor -> {
            ApplicationManager.getApplication().runWriteAction(() -> {
                // Run/Debug 后，移除 npm install
                List<BeforeRunTask<?>> runTasks = finalRunConfiguration.getConfiguration().getBeforeRunTasks();
                Iterator<BeforeRunTask<?>> iterator = runTasks.iterator();
                while (iterator.hasNext()) {
                    BeforeRunTask<?> beforeRunTask = iterator.next();
                    if (beforeRunTask instanceof NpmBeforeRunTask && ((NpmBeforeRunTask) beforeRunTask).getSettings().getCommand() == NpmCommand.INSTALL) {
                        iterator.remove();
                        break;
                    }
                }
                finalRunConfiguration.getConfiguration().setBeforeRunTasks(runTasks);
            });
        });
    }

    public static boolean executable(VirtualFile virtualFile) {
        return virtualFile.getFileType() instanceof TypeScriptFileType;
    }

}
