package cn.structured.cloud.gateway.listener;

import cn.structured.cloud.gateway.service.DynamicRouteRefreshService;
import com.alibaba.cloud.nacos.NacosConfigManager;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.concurrent.Executor;

/**
 * Nacos配置刷新监听器
 * <p>
 * 职责：监听Nacos配置变化，自动刷新网关路由
 * <p>
 * 工作原理：
 * 1. 应用启动后注册Nacos配置监听器
 * 2. 当Nacos配置发生变化时，收到回调通知
 * 3. 调用DynamicRouteRefreshService刷新路由
 * <p>
 * 使用 @EventListener 注解监听 ApplicationReadyEvent 事件
 * <p>
 * 支持无Nacos环境运行：当NacosConfigManager不存在时，监听器会静默跳过
 */
@Slf4j
@Component
public class NacosConfigRefreshListener {

    private final DynamicRouteRefreshService dynamicRouteRefreshService;
    private NacosConfigManager nacosConfigManager;

    @Value("${spring.application.name:structure-gateway}")
    private String applicationName;

    @Value("${spring.cloud.nacos.config.file-extension:yaml}")
    private String fileExtension;

    @Value("${spring.cloud.nacos.config.group:DEFAULT_GROUP}")
    private String group;

    @Value("${spring.profiles.active:dev}")
    private String profile;

    /**
     * 配置监听器
     */
    private Listener configListener;

    /**
     * 构造函数 - required = false 使 NacosConfigManager 变为可选依赖
     */
    public NacosConfigRefreshListener(DynamicRouteRefreshService dynamicRouteRefreshService) {
        this.dynamicRouteRefreshService = dynamicRouteRefreshService;
    }

    /**
     * 可选注入 NacosConfigManager
     * 如果 Nacos 相关依赖不存在，此方法不会被调用
     */
    @Autowired(required = false)
    public void setNacosConfigManager(NacosConfigManager nacosConfigManager) {
        this.nacosConfigManager = nacosConfigManager;
    }

    /**
     * 应用启动完成后注册Nacos配置监听器
     * 如果Nacos不可用，则静默跳过
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (nacosConfigManager == null) {
            log.info("NacosConfigManager not found, skipping Nacos config listener registration");
            log.info("Dynamic routes will not be automatically refreshed from Nacos");
            return;
        }

        log.info("Registering Nacos config listener for dynamic route refresh");

        String dataId = buildDataId();
        log.info("Listening to Nacos config: dataId={}, group={}", dataId, group);

        configListener = new Listener() {
            @Override
            public void receiveConfigInfo(String configInfo) {
                log.info("Received config change from Nacos, refreshing routes...");
                try {
                    dynamicRouteRefreshService.refreshRoutes();
                } catch (Exception e) {
                    log.error("Failed to refresh routes after config change", e);
                }
            }

            @Override
            public Executor getExecutor() {
                return null;
            }
        };

        try {
            nacosConfigManager.getConfigService().addListener(dataId, group, configListener);
            log.info("Nacos config listener registered successfully");
        } catch (NacosException e) {
            log.error("Failed to register Nacos config listener", e);
        }
    }

    /**
     * 构建Nacos配置dataId
     * 格式：{applicationName}-{profile}.{fileExtension}
     */
    private String buildDataId() {
        if (profile != null && !profile.isEmpty()) {
            return applicationName + "-" + profile + "." + fileExtension;
        }
        return applicationName + "." + fileExtension;
    }

    /**
     * 应用关闭前移除监听器
     */
    @PreDestroy
    public void destroy() {
        if (configListener != null && nacosConfigManager != null) {
            try {
                String dataId = buildDataId();
                nacosConfigManager.getConfigService().removeListener(dataId, group, configListener);
                log.info("Nacos config listener removed");
            } catch (Exception e) {
                log.warn("Failed to remove Nacos config listener", e);
            }
        }
    }
}