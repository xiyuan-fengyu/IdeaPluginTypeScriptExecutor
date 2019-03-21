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
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Created by xiyuan_fengyu on 2019/3/21 15:31.
 */
public class TypeScriptCompileCurrentAction extends com.intellij.lang.typescript.compiler.action.TypeScriptCompileCurrentAction {

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
                    TypeScriptConfig config = (TypeScriptConfig) DumbService.getInstance(project).runReadActionInSmartMode(() -> {
                        return TypeScriptConfigService.Provider.parseConfigFile(project, configFile);
                    });
                    if (config != null) {
                        TypeScriptCompilerService compilerService = this.getServiceForConfig(project, configFile);
                        if (compilerService != null) {
                            Collection<JSAnnotationError> infos = ContainerUtil.newLinkedHashSet();
                            this.runBeforeStartingCompile(project, indicator, compilerService);
                            TypeScriptServiceCommandClean command = new TypeScriptServiceCommandClean(TypeScriptCompilerConfigUtil.getConfigIdByConfig(config));
                            compilerService.sendCleanCommandToCompiler(command);
                            this.compileConfig(indicator, compilerService, config, infos);
                            this.logErrors(project, infos);
                            this.compileListener.onCompileResult(project, infos);
                        }
                    }
                };
            } else {
                VirtualFile[] files = this.getFiles(project, context);
                return (indicator) -> {
                    this.runCompile(project, indicator, files);
                };
            }
        }
    }

    public interface CompileListener {

        void onCompileResult(Project project, Collection<JSAnnotationError> infos);

    }

}
