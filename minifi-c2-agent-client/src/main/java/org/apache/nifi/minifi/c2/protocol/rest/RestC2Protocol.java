package org.apache.nifi.minifi.c2.protocol.rest;

import okhttp3.OkHttpClient;
import org.apache.nifi.minifi.c2.agent.client.C2Protocol;

public abstract class RestC2Protocol implements C2Protocol {

    protected final OkHttpClient httpClient = new OkHttpClient();

    private final String c2ServerAddress;
    private final int c2ServerPort;

    public RestC2Protocol(final String c2serverAddress,
                          final int c2ServerPort) {
        this.c2ServerAddress = c2serverAddress;
        this.c2ServerPort = c2ServerPort;
    }

    protected abstract String getHeartbeatEndpoint();

    protected int getC2ServerPort() {
        return c2ServerPort;
    }

    protected String getC2ServerAddress() {
        return c2ServerAddress;
    }
}
