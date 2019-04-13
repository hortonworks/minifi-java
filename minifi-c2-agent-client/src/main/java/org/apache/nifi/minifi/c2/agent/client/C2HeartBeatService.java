package org.apache.nifi.minifi.c2.agent.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ScheduledThreadPoolExecutor;

public class C2HeartBeatService extends ScheduledThreadPoolExecutor implements HeartbeatReporter {

    private static final Logger log = LoggerFactory.getLogger(C2HeartBeatService.class);

    private final long heartbeatInterval;

    private C2Agent agent;

    public C2HeartBeatService(long interval) {
        super(1);
        this.heartbeatInterval = interval;
    }


    @Override
    public boolean sendHeartbeat() {
        return false;
    }

    @Override
    public boolean setInterval() {
        return false;
    }

    @Override
    public boolean start() {
        return false;
    }

    @Override
    public boolean stop() {
        return false;
    }

    @Override
    public boolean isRunning() {
        return false;
    }
}
