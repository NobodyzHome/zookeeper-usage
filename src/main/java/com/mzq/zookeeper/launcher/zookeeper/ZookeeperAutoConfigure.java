package com.mzq.zookeeper.launcher.zookeeper;


import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryNTimes;
import org.apache.curator.retry.RetryOneTime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

@Component
public class ZookeeperAutoConfigure {

    @Bean(initMethod = "start", destroyMethod = "close")
    @ConditionalOnClass(CuratorFramework.class)
    @ConditionalOnMissingBean(CuratorFramework.class)
    public CuratorFramework curatorFramework(ZookeeperProperties zookeeperProperties) {
        CuratorFrameworkFactory.Builder builder = CuratorFrameworkFactory.builder();
        builder.connectString(zookeeperProperties.getConnectString());
        Optional.ofNullable(zookeeperProperties.getDefaultData()).ifPresent(defaultData -> builder.defaultData(defaultData.getBytes()));
        Optional.ofNullable(zookeeperProperties.getSessionTimeout()).ifPresent(sessionTimeout -> builder.sessionTimeoutMs(Long.valueOf(sessionTimeout.toMillis()).intValue()));
        Optional.ofNullable(zookeeperProperties.getConnectionTimeout()).ifPresent(connectionTimeout -> builder.connectionTimeoutMs(Long.valueOf(connectionTimeout.toMillis()).intValue()));
        RetryPolicy retryPolicy;
        if (zookeeperProperties.getRetryTimes() > 0 && Objects.nonNull(zookeeperProperties.getRetrySleepTime())) {
            retryPolicy = new RetryNTimes(zookeeperProperties.getRetryTimes(), Long.valueOf(zookeeperProperties.getRetrySleepTime().toMillis()).intValue());
        } else {
            retryPolicy = new RetryOneTime(1000);
        }
        builder.retryPolicy(retryPolicy);

        return builder.build();
    }
}
