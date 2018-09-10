package com.xiyuan.ts.activity;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.util.messages.MessageBusConnection;
import com.xiyuan.ts.execution.NodeJsExecution;
import org.jetbrains.annotations.NotNull;

/**
 * Created by xiyuan_fengyu on 2018/6/10 15:45.
 */
public class TsExecutorActivity implements StartupActivity {

    @Override
    public void runActivity(@NotNull Project project) {
        NodeJsExecution.resetTsCompilerSettings(project);
    }

}
