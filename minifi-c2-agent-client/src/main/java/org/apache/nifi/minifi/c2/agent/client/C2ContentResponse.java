package org.apache.nifi.minifi.c2.agent.client;

import java.util.Map;

public class C2ContentResponse {

    private final Operation operation;
    private boolean required;
    private String identifier;
    private long delay;
    private long ttl;
    private String name;
    private Map<String, String> operationArguments;

    public C2ContentResponse(final Operation operation) {
        this.operation = operation;
    }
}
