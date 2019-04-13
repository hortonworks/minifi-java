package org.apache.nifi.minifi.c2.protocol.rest;

import com.cloudera.cem.efm.client.C2Client;
import com.cloudera.cem.efm.client.impl.jersey.JerseyC2Client;
import org.apache.nifi.minifi.c2.agent.client.C2Payload;

public class C2ServerC2RestProtocol extends RestC2Protocol {

    private final C2Client c2Client;

    public C2ServerC2RestProtocol(String c2serverAddress, int c2ServerPort) {
        super(c2serverAddress, c2ServerPort);
        c2Client = new JerseyC2Client.Builder().config(null).build();
    }


    @Override
    protected String getHeartbeatEndpoint() {
        c2Client.getProtocolClient();
        return null;
    }

    @Override
    public C2Payload transmit(C2Payload payload) {
        return null;
    }
}
