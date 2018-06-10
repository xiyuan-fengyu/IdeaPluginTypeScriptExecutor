package com.xiyuan.ts.activity;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
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
