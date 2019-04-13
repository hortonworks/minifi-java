package org.apache.nifi.minifi.c2.core.service;

import org.apache.nifi.minifi.c2.model.C2Heartbeat;
import org.apache.nifi.minifi.c2.model.C2HeartbeatResponse;
import org.apache.nifi.minifi.c2.model.C2OperationAck;

public interface C2ProtocolService {

    C2HeartbeatResponse processHeartbeat(C2Heartbeat heartbeat);

    void processOperationAck(C2OperationAck operationAck);

}
