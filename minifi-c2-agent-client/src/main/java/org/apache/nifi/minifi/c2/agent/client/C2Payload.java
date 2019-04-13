package org.apache.nifi.minifi.c2.agent.client;

import java.util.List;

public class C2Payload {

    private final Operation operation;

    private final String identifier;
    private String label;

    private List<C2Payload> payloads;
    private List<C2ContentResponse> content;

    private final boolean response;
    private final boolean raw;
    private byte[] rawData;

    public C2Payload(final Operation operation, final String identifier, final boolean response, final boolean raw) {
        this.operation = operation;
        this.identifier = identifier;
        this.response = response;
        this.raw = raw;
    }

    public Operation getOperation() {
        return operation;
    }

    public String getIdentifier() {
        return identifier;
    }

    public String getLabel() {
        return label;
    }

    public List<C2Payload> getPayloads() {
        return payloads;
    }

    public List<C2ContentResponse> getContent() {
        return content;
    }

    public boolean isRaw() {
        return raw;
    }

    public boolean isResponse() {
        return response;
    }

    public void setRawData(byte[] rawData) {
        this.rawData = rawData;
    }

    public byte[] getRawData() {
        return rawData;
    }
}
