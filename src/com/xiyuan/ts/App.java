package com.xiyuan.ts;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.messages.MessageBusConnection;
import com.xiyuan.ts.execution.NodeJsExecution;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Created by xiyuan_fengyu on 2018/9/10 17:38.
 */
public class App implements BaseComponent, BulkFileListener {

    private MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect();

    @Override
    public void initComponent() {
        connection.subscribe(VirtualFileManager.VFS_CHANGES, this);
    }

    @Override
    public void disposeComponent() {
        connection.disconnect();
    }

    @NotNull
    @Override
    public String getComponentName() {
        return "TypeScriptExecutor";
    }

    @Override
    public void after(@NotNull List<? extends VFileEvent> list) {
        for (VFileEvent event: list) {
            VirtualFile file = event.getFile();
            if (file != null) {
                String fileType = file.getFileType().getName();
                if ("TypeScript".equals(fileType) && event instanceof VFileContentChangeEvent || event instanceof VFileCreateEvent) {
                    NodeJsExecution.addChangedTsFiles(file);
                }
            }
        }
    }

}