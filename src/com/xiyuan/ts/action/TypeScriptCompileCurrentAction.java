package com.xiyuan.ts.action;

import com.intellij.lang.javascript.integration.JSAnnotationError;
import com.intellij.lang.typescript.compiler.TypeScriptCompilerConfigUtil;
import com.intellij.lang.typescript.compiler.TypeScriptCompilerService;
import com.intellij.lang.typescript.compiler.languageService.protocol.commands.TypeScriptServiceCommandClean;
import com.intellij.lang.typescript.tsconfig.TypeScriptConfig;
import com.intellij.lang.typescript.tsconfig.TypeScriptConfigService;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashSet;

/**
 * Created by xiyuan_fengyu on 2019/3/21 15:31.
 */
public class TypeScriptCompileCurrentAction extends com.intellij.lang.typescript.compiler.action.TypeScriptCompileWithConfigAction {

    private CompileListener compileListener;

    public TypeScriptCompileCurrentAction(CompileListener compileListener) {
        this.compileListener = compileListener;
    }

    protected Consumer<ProgressIndicator> getBackgroundProcess(@Nullable Project project, @Nullable DataContext context) {
        if (project == null) {
            return null;
        } else {
            VirtualFile configFile = this.getConfigFile(context);
            if (configFile != null) {
                return (indicator) -> {
                    TypeScriptConfig config = DumbService.getInstance(project).runReadActionInSmartMode(() ->
                            TypeScriptConfigService.Provider.parseConfigFile(project, configFile));
                    if (config != null) {
                        TypeScriptCompilerService compilerService = this.getServiceForConfig(project, configFile);
                        if (compilerService != null) {
                            Collection<JSAnnotationError> infos = new LinkedHashSet<>();
                            this.runBeforeStartingCompile(project, indicator, compilerService);
                            TypeScriptServiceCommandClean command = new TypeScriptServiceCommandClean(TypeScriptCompilerConfigUtil.getConfigIdByConfig(config));
                            compilerService.sendCleanCommandToCompiler(command);
                            this.compileConfig(indicator, compilerService, configFile, infos);
                            this.logErrors(project, infos);
                            this.compileListener.onCompileResult(project, infos);
                        }
                    }
                };
            } else {
                VirtualFile[] files = this.getFiles(project, context);
                return (indicator) -> {
                    TypeScriptCompilerService compilerService = TypeScriptCompilerService.getDefaultService(project);
                    this.runBeforeStartingCompile(project, indicator, compilerService);
                    TypeScriptServiceCommandClean command = new TypeScriptServiceCommandClean(false);
                    compilerService.sendCleanCommandToCompiler(command);

                    Collection<JSAnnotationError> infos = new LinkedHashSet<>();
                    Collection<String> processed = new LinkedHashSet<>();
                    if (files != null) {
                        for (VirtualFile file : files) {
                            TypeScriptCompilerService tempCompilerService = TypeScriptCompilerService.getServiceForFile(project, file);
                            this.compileFile(file, indicator, tempCompilerService, infos, processed);
                        }
                    }
                    indicator.checkCanceled();
                    this.logErrors(project, infos);
                    this.compileListener.onCompileResult(project, infos);
                };
            }
        }
    }

    public interface CompileListener {

        void onCompileResult(Project project, Collection<JSAnnotationError> infos);

    }

}
