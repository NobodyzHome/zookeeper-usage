package com.mzq.zookeeper.launcher.zookeeper;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties("spring.zookeeper")
public class ZookeeperProperties {

    private String connectString;
    private String defaultData;
    private Duration sessionTimeout;
    private Duration connectionTimeout;
    private int retryTimes;
    private Duration retrySleepTime;

    public String getConnectString() {
        return connectString;
    }

    public void setConnectString(String connectString) {
        this.connectString = connectString;
    }

    public String getDefaultData() {
        return defaultData;
    }

    public void setDefaultData(String defaultData) {
        this.defaultData = defaultData;
    }

    public Duration getSessionTimeout() {
        return sessionTimeout;
    }

    public void setSessionTimeout(Duration sessionTimeout) {
        this.sessionTimeout = sessionTimeout;
    }

    public Duration getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(Duration connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public int getRetryTimes() {
        return retryTimes;
    }

    public void setRetryTimes(int retryTimes) {
        this.retryTimes = retryTimes;
    }

    public Duration getRetrySleepTime() {
        return retrySleepTime;
    }

    public void setRetrySleepTime(Duration retrySleepTime) {
        this.retrySleepTime = retrySleepTime;
    }
}
