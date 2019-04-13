package org.apache.nifi.minifi.c2.agent.client;

public enum Operation {
    ACKNOWLEDGE,
    START,
    STOP,
    RESTART,
    DESCRIBE,
    HEARTBEAT,
    UPDATE,
    VALIDATE,
    CLEAR
};
