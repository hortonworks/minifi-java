package org.apache.nifi.minifi.c2.agent.client;

import org.apache.nifi.minifi.c2.protocol.rest.C2ServerC2RestProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class C2Agent extends ScheduledThreadPoolExecutor implements HeartbeatReporter {
    private final Logger logger = LoggerFactory.getLogger(C2Agent.class);

    private final AtomicReference<ScheduledFuture> execScheduledFutureRef = new AtomicReference<>();
    public static final int DEFAULT_AGENT_PORT = 10080;


    protected static final int DEFAULT_THREADPOOL_SIZE = 2;

    protected final C2Protocol c2Protocol;
    protected final C2Serializer serializer;
    protected final C2Deserializer deserializer;

    private final List<ScheduledFuture> scheduledTasks = new ArrayList<>();

    /* Prefer the handling of responses first before initiating new heartbeats */
    protected final Queue<C2Payload> payloads = new PriorityQueue<>((obj, other) -> obj.isResponse() == other.isResponse() ? 0 : obj.isResponse() ? 1 : -1);

    protected final Queue<C2Payload> requests = new ConcurrentLinkedQueue<>();
    protected final Queue<C2Payload> responses = new ConcurrentLinkedQueue<>();

    public C2Agent() {
        this(DEFAULT_THREADPOOL_SIZE,
                new C2ServerC2RestProtocol("localhost", DEFAULT_AGENT_PORT),
                new C2Serializer() {
                },
                new C2Deserializer() {
                }
        );
    }

    public C2Agent(final int threadpoolSize, final C2Protocol protocol, final C2Serializer serializer, final C2Deserializer deserializer) {
        super(threadpoolSize);
        this.c2Protocol = protocol;
        this.serializer = serializer;
        this.deserializer = deserializer;
    }


    @Override
    public boolean sendHeartbeat() {
        logger.debug("Enqueuing heartbeat");
        // serialize request
        final String testHeartBeat = "{\n" +
                "   \"Components\" : {\n" +
                "      \"FlowController\" : \"enabled\",\n" +
                "      \"GetFile\" : \"enabled\",\n" +
                "      \"LogAttribute\" : \"enabled\"\n" +
                "   },\n" +
                "   \"DeviceInfo\" : {\n" +
                "      \"NetworkInfo\" : {\n" +
                "         \"deviceid\" : \"" + new Random().nextLong() + "\",\n" +
                "         \"hostname\" : \"" + UUID.randomUUID().toString() + "\",\n" +
                "         \"ip\" : \"\"\n" +
                "      },\n" +
                "      \"SystemInformation\" : {\n" +
                "         \"machinearch\" : \"x86_64\",\n" +
                "         \"physicalmem\" : \"17179869184\",\n" +
                "         \"vcores\" : \"8\"\n" +
                "      }\n" +
                "   },\n" +
                "   \"metrics\" : {\n" +
                "      \"ProcessMetrics\" : {\n" +
                "         \"CpuMetrics\" : {\n" +
                "            \"involcs\" : \"2183\"\n" +
                "         },\n" +
                "         \"MemoryMetrics\" : {\n" +
                "            \"maxrss\" : \"17866752\"\n" +
                "         }\n" +
                "      },\n" +
                "      \"QueueMetrics\" : {\n" +
                "         \"GetFile/success/LogAttribute\" : {\n" +
                "            \"datasize\" : \"0\",\n" +
                "            \"datasizemax\" : \"1073741824\",\n" +
                "            \"queued\" : \"0\",\n" +
                "            \"queuedmax\" : \"10000\"\n" +
                "         }\n" +
                "      },\n" +
                "      \"RepositoryMetrics\" : {\n" +
                "         \"flowfilerepository\" : {\n" +
                "            \"full\" : \"0\",\n" +
                "            \"running\" : \"1\",\n" +
                "            \"size\" : \"0\"\n" +
                "         },\n" +
                "         \"provenancerepository\" : {\n" +
                "            \"full\" : \"0\",\n" +
                "            \"running\" : \"1\",\n" +
                "            \"size\" : \"0\"\n" +
                "         }\n" +
                "      }\n" +
                "   },\n" +
                "   \"operation\" : \"heartbeat\",\n" +
                "   \"state\" : {\n" +
                "      \"running\" : \"true\",\n" +
                "      \"uptime\" : \"2919\"\n" +
                "   }\n" +
                "}";

        C2Payload payload = new C2Payload(Operation.HEARTBEAT, UUID.randomUUID().toString(), false, true);
        payload.setRawData(testHeartBeat.getBytes(StandardCharsets.UTF_8));

        payloads.offer(payload);
        return true;
    }

    @Override
    public boolean setInterval() {
        return false;
    }


    public void processQueues() {
        logger.debug("Processing queues.  Running? = ");
        logger.debug("Task count is " + getFutures().size());
        // Make preference for queued responses before additional requests
    }

    public void consumeC2() {
        logger.debug("Consuming C2 messages");
        logger.debug("enqueuing response, total number of responses is {}", responses.size());
    }

    public void produceC2() {
        logger.debug("Producing C2 messages");
        logger.debug("Current # of messages enqueued: " + this.requests.size());
        C2Payload payload = this.payloads.poll();
        if (payload == null) {
            return;
        }

        if (payload.isResponse()) {
            logger.debug("handling response");
        } else {
            final C2Payload responsePayload = c2Protocol.transmit(payload);
            payloads.offer(responsePayload);
            logger.debug("enqueuing response, total number of responses is {}", responses.size());
        }
    }

    @Override
    public boolean start() {
        addFuture(this.scheduleAtFixedRate(() -> sendHeartbeat(), 0, 15, TimeUnit.SECONDS));
        for (int i = 0; i < 3; i++) {
            addFuture(this.scheduleAtFixedRate(
                    () -> this.processQueues(),
                    0, 3, TimeUnit.SECONDS));
        }
        return false;
    }

    protected synchronized void addFuture(ScheduledFuture future) {
        this.scheduledTasks.add(future);
    }

    protected synchronized List<Future> getFutures() {
        return Collections.unmodifiableList(this.scheduledTasks);
    }

    @Override
    public boolean stop() {
        this.shutdown();
        return true;
    }

    @Override
    public boolean isRunning() {
        return !(execScheduledFutureRef.get().isDone() && execScheduledFutureRef.get().isCancelled());
    }

    public static void main(String[] args) {
        C2Agent c2Agent = new C2Agent();
        c2Agent.start();
    }

}
