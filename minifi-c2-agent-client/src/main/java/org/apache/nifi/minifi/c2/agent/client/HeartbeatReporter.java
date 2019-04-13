package org.apache.nifi.minifi.c2.agent.client;

public interface HeartbeatReporter {

    boolean sendHeartbeat();

    boolean setInterval();

    boolean start();

    boolean stop();

    boolean isRunning();

}
