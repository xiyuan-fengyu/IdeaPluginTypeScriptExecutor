package com.xiyuan.ts.setting.autoCompile;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by xiyuan_fengyu on 2018/9/10 10:32.
 */
@State(
        name="TypeScriptAutoCompileConfig",
        storages = {
                @Storage("TypeScriptAutoCompileConfig.xml")
        }
)
public class AutoCompileConfig implements PersistentStateComponent<AutoCompileConfig> {

    private Map<String, Boolean> autoCompileEnables = new HashMap<>();

    public Map<String, Boolean> getAutoCompileEnables() {
        return autoCompileEnables;
    }

    public void setAutoCompileEnables(Map<String, Boolean> autoCompileEnables) {
        this.autoCompileEnables = autoCompileEnables;
    }

    @Nullable
    @Override
    public AutoCompileConfig getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull AutoCompileConfig autoCompileConfig) {
        XmlSerializerUtil.copyBean(autoCompileConfig, this);
    }

    public static AutoCompileConfig getInstance(Project project) {
        return ServiceManager.getService(project, AutoCompileConfig.class);
    }

    public boolean isAutoCompileEnable(String configFileRelativePath) {
        // 所有模块默认开启自动编译
        return !Boolean.FALSE.equals(autoCompileEnables.get(configFileRelativePath));
    }

    void setAutoCompileEnable(String configFileRelativePath, boolean enable) {
        autoCompileEnables.put(configFileRelativePath, enable);
    }

}
