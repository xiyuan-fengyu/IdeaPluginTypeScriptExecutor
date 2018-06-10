package com.xiyuan.ts.action;

import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunManager;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.nodejs.run.NodeJsRunConfiguration;
import com.jetbrains.nodejs.run.NodeJsRunConfigurationState;
import com.jetbrains.nodejs.run.NodeJsRunConfigurationType;
import com.xiyuan.ts.execution.NodeJsExecution;

import java.lang.reflect.Method;


/**
 * Created by xiyuan_fengyu on 2018/6/9 22:49.
 */
public class RunAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent event) {
        Project project = event.getProject();
        VirtualFile virtualFile = event.getData(DataKeys.VIRTUAL_FILE);
        if (project == null || virtualFile == null) return;
        NodeJsExecution.execute(project, virtualFile, false);
    }

    @Override
    public void update(AnActionEvent event) {
        Project project = event.getProject();
        VirtualFile virtualFile = event.getData(DataKeys.VIRTUAL_FILE);
        if (project != null && virtualFile != null && NodeJsExecution.executable(project, virtualFile)) {
            event.getPresentation().setEnabledAndVisible(true);
            event.getPresentation().setText("Run '" + virtualFile.getName() + "'");
        }
        else event.getPresentation().setEnabledAndVisible(false);
    }

}
