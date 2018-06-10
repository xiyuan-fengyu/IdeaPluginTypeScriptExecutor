package com.xiyuan.ts.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.xiyuan.ts.execution.NodeJsExecution;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;


/**
 * Created by xiyuan_fengyu on 2018/6/9 22:49.
 */
public class NewTsconfigAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) return;

        try (InputStream in = NewTsconfigAction.class.getClassLoader().getResourceAsStream("tsconfig.json");
             FileOutputStream out = new FileOutputStream(new File(project.getBasePath() + File.separator + "tsconfig.json"))) {
            byte[] buffer = new byte[2048];
            int read;
            while ((read = in.read(buffer)) > -1) {
                out.write(buffer, 0, read);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        VirtualFileManager.getInstance().syncRefresh();
        NodeJsExecution.resetTsCompilerSettings(project);
    }

}
