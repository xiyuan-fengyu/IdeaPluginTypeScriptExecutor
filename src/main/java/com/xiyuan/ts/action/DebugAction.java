package com.xiyuan.ts.action;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.xiyuan.ts.execution.NodeJsExecution;
import org.jetbrains.annotations.NotNull;


/**
 * Created by xiyuan_fengyu on 2018/6/9 22:49.
 */
public class DebugAction extends AnAction {

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        try {
            NodeJsExecution.execute(event, true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void update(AnActionEvent event) {
        Project project = event.getProject();
        VirtualFile virtualFile = event.getData(CommonDataKeys.VIRTUAL_FILE);
        if (project != null && virtualFile != null && NodeJsExecution.executable(virtualFile)) {
            event.getPresentation().setEnabledAndVisible(true);
            event.getPresentation().setText("Debug '" + virtualFile.getName() + "'");
        }
        else event.getPresentation().setEnabledAndVisible(false);
    }

}
