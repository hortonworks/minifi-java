package org.apache.nifi.minifi.bootstrap.status.reporters;

import com.cloudera.cem.efm.model.AgentInfo;
import com.cloudera.cem.efm.model.C2Heartbeat;
import com.cloudera.cem.efm.model.C2OperationAck;
import com.cloudera.cem.efm.model.DeviceInfo;
import com.cloudera.cem.efm.model.FlowInfo;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.StringUtils;
import org.apache.nifi.minifi.bootstrap.C2Properties;
import org.apache.nifi.minifi.bootstrap.ConfigurationFileHolder;
import org.apache.nifi.minifi.bootstrap.QueryableStatusAggregator;
import org.apache.nifi.minifi.bootstrap.RunMiNiFi;
import org.apache.nifi.minifi.bootstrap.configuration.ConfigurationChangeNotifier;
import org.apache.nifi.minifi.bootstrap.configuration.differentiators.WholeConfigDifferentiator;
import org.apache.nifi.minifi.bootstrap.configuration.differentiators.interfaces.Differentiator;
import org.apache.nifi.minifi.bootstrap.configuration.ingestors.ConfigurableHttpClient;
import org.apache.nifi.minifi.bootstrap.configuration.ingestors.interfaces.ChangeIngestor;
import org.apache.nifi.minifi.bootstrap.util.ByteBufferInputStream;
import org.apache.nifi.minifi.commons.schema.ConfigSchema;
import org.apache.nifi.minifi.commons.schema.SecurityPropertiesSchema;
import org.apache.nifi.minifi.commons.schema.common.ConvertableSchema;
import org.apache.nifi.minifi.commons.schema.serialization.SchemaLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.nio.ByteBuffer;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.apache.nifi.minifi.bootstrap.configuration.ConfigurationChangeCoordinator.NOTIFIER_INGESTORS_KEY;
import static org.apache.nifi.minifi.bootstrap.configuration.differentiators.WholeConfigDifferentiator.WHOLE_CONFIG_KEY;

public class RestHeartbeatReporter extends HeartbeatReporter implements ConfigurableHttpClient, ChangeIngestor {

    private static final Logger logger = LoggerFactory.getLogger(RestHeartbeatReporter.class);

    private String c2ServerUrl;
    private QueryableStatusAggregator agentMonitor;

    private final AgentInfo agentInformation = new AgentInfo();

    private ObjectMapper objectMapper;
    private final AtomicLong pollingPeriodMS = new AtomicLong();

    private final AtomicReference<FlowUpdateInfo> updateInfo = new AtomicReference<>();

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicReference<String> flowIdReference = new AtomicReference<>("");

    @Override
    public void initialize(Properties properties, QueryableStatusAggregator queryableStatusAggregator) {
        final C2Properties c2Properties = new C2Properties(properties);
        this.properties.set(properties);
        objectMapper = new ObjectMapper();
        this.agentMonitor = queryableStatusAggregator;
        this.configurationChangeNotifier = queryableStatusAggregator.getConfigChangeNotifier();


        if (!c2Properties.isEnabled()) {
            throw new IllegalArgumentException("Cannot initialize the REST HeartbeatReporter when C2 is not enabled");
        }

        agentInformation.setAgentClass(c2Properties.getAgentClass());
        agentInformation.setIdentifier(c2Properties.getAgentIdentifier());
        this.c2ServerUrl = c2Properties.getRestUrl();

        pollingPeriodMS.set(c2Properties.getAgentHeartbeatPeriod());
        if (pollingPeriodMS.get() < 1) {
            throw new IllegalArgumentException("Property, " + C2Properties.C2_AGENT_HEARTBEAT_PERIOD_KEY + ", for the polling period ms must be set with a positive integer.");
        }
        this.setPeriod(pollingPeriodMS.get());


        if (StringUtils.isBlank(c2ServerUrl)) {
            throw new IllegalArgumentException("Property, " + c2ServerUrl + ", for the hostname to pull configurations from must be specified.");
        }

        httpClientReference.set(null);

        final OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient.Builder();

        // Set whether to follow redirects
        okHttpClientBuilder.followRedirects(true);

        // check if the ssl path is set and add the factory if so
        if (StringUtils.isNotBlank(c2Properties.getKeystore())) {
            try {
                setSslSocketFactory(okHttpClientBuilder, c2Properties);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        httpClientReference.set(okHttpClientBuilder.build());
        reportRunner = new RestHeartbeatReporter.HeartbeatReporter();
        differentiator = WholeConfigDifferentiator.getByteBufferDifferentiator();

        differentiator.initialize(properties, queryableStatusAggregator);

        initialized.set(true);
    }

    @Override
    public boolean isInitialized() {
        return initialized.get();
    }


    // Handle change ingestor

    private static final Map<String, Supplier<Differentiator<ByteBuffer>>> DIFFERENTIATOR_CONSTRUCTOR_MAP;

    static {
        HashMap<String, Supplier<Differentiator<ByteBuffer>>> tempMap = new HashMap<>();
        tempMap.put(WHOLE_CONFIG_KEY, WholeConfigDifferentiator::getByteBufferDifferentiator);
        DIFFERENTIATOR_CONSTRUCTOR_MAP = Collections.unmodifiableMap(tempMap);
    }

    private static final String DEFAULT_CONNECT_TIMEOUT_MS = "5000";
    private static final String DEFAULT_READ_TIMEOUT_MS = "15000";

    private static final String PULL_HTTP_BASE_KEY = NOTIFIER_INGESTORS_KEY + ".pull.http";
    public static final String PULL_HTTP_POLLING_PERIOD_KEY = PULL_HTTP_BASE_KEY + ".period.ms";
    public static final String PORT_KEY = PULL_HTTP_BASE_KEY + ".port";
    public static final String HOST_KEY = PULL_HTTP_BASE_KEY + ".hostname";
    public static final String PATH_KEY = PULL_HTTP_BASE_KEY + ".path";
    public static final String QUERY_KEY = PULL_HTTP_BASE_KEY + ".query";
    public static final String PROXY_HOST_KEY = PULL_HTTP_BASE_KEY + ".proxy.hostname";
    public static final String PROXY_PORT_KEY = PULL_HTTP_BASE_KEY + ".proxy.port";
    public static final String PROXY_USERNAME = PULL_HTTP_BASE_KEY + ".proxy.username";
    public static final String PROXY_PASSWORD = PULL_HTTP_BASE_KEY + ".proxy.password";
    public static final String TRUSTSTORE_LOCATION_KEY = PULL_HTTP_BASE_KEY + ".truststore.location";
    public static final String TRUSTSTORE_PASSWORD_KEY = PULL_HTTP_BASE_KEY + ".truststore.password";
    public static final String TRUSTSTORE_TYPE_KEY = PULL_HTTP_BASE_KEY + ".truststore.type";
    public static final String KEYSTORE_LOCATION_KEY = PULL_HTTP_BASE_KEY + ".keystore.location";
    public static final String KEYSTORE_PASSWORD_KEY = PULL_HTTP_BASE_KEY + ".keystore.password";
    public static final String KEYSTORE_TYPE_KEY = PULL_HTTP_BASE_KEY + ".keystore.type";
    public static final String CONNECT_TIMEOUT_KEY = PULL_HTTP_BASE_KEY + ".connect.timeout.ms";
    public static final String READ_TIMEOUT_KEY = PULL_HTTP_BASE_KEY + ".read.timeout.ms";
    public static final String DIFFERENTIATOR_KEY = PULL_HTTP_BASE_KEY + ".differentiator";
    public static final String USE_ETAG_KEY = PULL_HTTP_BASE_KEY + ".use.etag";
    public static final String OVERRIDE_SECURITY = PULL_HTTP_BASE_KEY + ".override.security";

    private final AtomicReference<OkHttpClient> ingestorHttpClientReference = new AtomicReference<>();
    private final AtomicReference<Integer> portReference = new AtomicReference<>();
    private final AtomicReference<String> hostReference = new AtomicReference<>();
    private final AtomicReference<String> pathReference = new AtomicReference<>();
    private final AtomicReference<String> queryReference = new AtomicReference<>();
    private volatile Differentiator<ByteBuffer> differentiator;
    private volatile String connectionScheme;
    private volatile String lastEtag = "";
    private volatile boolean useEtag = false;
    private volatile boolean overrideSecurity = false;

    // 5 minute default pulling period
    protected static final String DEFAULT_POLLING_PERIOD = "300000";

    //    private final ScheduledThreadPoolExecutor scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(1);
    protected volatile ConfigurationChangeNotifier configurationChangeNotifier;
    protected final AtomicReference<Properties> properties = new AtomicReference<>();

    @Override
    public void initialize(Properties properties, ConfigurationFileHolder configurationFileHolder, ConfigurationChangeNotifier configurationChangeNotifier) {

        this.properties.set(properties);

        pollingPeriodMS.set(Integer.parseInt(properties.getProperty(PULL_HTTP_POLLING_PERIOD_KEY, DEFAULT_POLLING_PERIOD)));
        if (pollingPeriodMS.get() < 1) {
            throw new IllegalArgumentException("Property, " + PULL_HTTP_POLLING_PERIOD_KEY + ", for the polling period ms must be set with a positive integer.");
        }

        final String host = properties.getProperty(HOST_KEY);
        if (host == null || host.isEmpty()) {
            throw new IllegalArgumentException("Property, " + HOST_KEY + ", for the hostname to pull configurations from must be specified.");
        }

        final String path = properties.getProperty(PATH_KEY, "/");
        final String query = properties.getProperty(QUERY_KEY, "");

        final String portString = (String) properties.get(PORT_KEY);
        final Integer port;
        if (portString == null) {
            throw new IllegalArgumentException("Property, " + PORT_KEY + ", for the hostname to pull configurations from must be specified.");
        } else {
            port = Integer.parseInt(portString);
        }

        portReference.set(port);
        hostReference.set(host);
        pathReference.set(path);
        queryReference.set(query);

        final String useEtagString = (String) properties.getOrDefault(USE_ETAG_KEY, "false");
        if ("true".equalsIgnoreCase(useEtagString) || "false".equalsIgnoreCase(useEtagString)) {
            useEtag = Boolean.parseBoolean(useEtagString);
        } else {
            throw new IllegalArgumentException("Property, " + USE_ETAG_KEY + ", to specify whether to use the ETag header, must either be a value boolean value (\"true\" or \"false\") or left to " +
                    "the default value of \"false\". It is set to \"" + useEtagString + "\".");
        }

        final String overrideSecurityProperties = (String) properties.getOrDefault(OVERRIDE_SECURITY, "false");
        if ("true".equalsIgnoreCase(overrideSecurityProperties) || "false".equalsIgnoreCase(overrideSecurityProperties)) {
            overrideSecurity = Boolean.parseBoolean(overrideSecurityProperties);
        } else {
            throw new IllegalArgumentException("Property, " + OVERRIDE_SECURITY + ", to specify whether to override security properties must either be a value boolean value (\"true\" or \"false\")" +
                    " or left to the default value of \"false\". It is set to \"" + overrideSecurityProperties + "\".");
        }

        ingestorHttpClientReference.set(null);

        final OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient.Builder();

        // Set timeouts
        okHttpClientBuilder.connectTimeout(Long.parseLong(properties.getProperty(CONNECT_TIMEOUT_KEY, DEFAULT_CONNECT_TIMEOUT_MS)), TimeUnit.MILLISECONDS);
        okHttpClientBuilder.readTimeout(Long.parseLong(properties.getProperty(READ_TIMEOUT_KEY, DEFAULT_READ_TIMEOUT_MS)), TimeUnit.MILLISECONDS);

        // Set whether to follow redirects
        okHttpClientBuilder.followRedirects(true);

        String proxyHost = properties.getProperty(PROXY_HOST_KEY, "");
        if (!proxyHost.isEmpty()) {
            String proxyPort = properties.getProperty(PROXY_PORT_KEY);
            if (proxyPort == null || proxyPort.isEmpty()) {
                throw new IllegalArgumentException("Proxy port required if proxy specified.");
            }
            okHttpClientBuilder.proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, Integer.parseInt(proxyPort))));
            String proxyUsername = properties.getProperty(PROXY_USERNAME);
            if (proxyUsername != null) {
                String proxyPassword = properties.getProperty(PROXY_PASSWORD);
                if (proxyPassword == null) {
                    throw new IllegalArgumentException("Must specify proxy password with proxy username.");
                }
                okHttpClientBuilder.proxyAuthenticator((route, response) -> response.request().newBuilder().addHeader("Proxy-Authorization", Credentials.basic(proxyUsername, proxyPassword)).build());
            }
        }

        ingestorHttpClientReference.set(okHttpClientBuilder.build());
        final String differentiatorName = properties.getProperty(DIFFERENTIATOR_KEY);

        if (differentiatorName != null && !differentiatorName.isEmpty()) {
            Supplier<Differentiator<ByteBuffer>> differentiatorSupplier = DIFFERENTIATOR_CONSTRUCTOR_MAP.get(differentiatorName);
            if (differentiatorSupplier == null) {
                throw new IllegalArgumentException("Property, " + DIFFERENTIATOR_KEY + ", has value " + differentiatorName + " which does not " +
                        "correspond to any in the PullHttpChangeIngestor Map:" + DIFFERENTIATOR_CONSTRUCTOR_MAP.keySet());
            }
            differentiator = differentiatorSupplier.get();
        } else {
            differentiator = WholeConfigDifferentiator.getByteBufferDifferentiator();
        }
        differentiator.initialize(properties, configurationFileHolder);
    }

    @Override
    public void close() throws IOException {

    }

    private class HeartbeatReporter implements Runnable {

        @Override
        public void run() {
            try {
                final FlowUpdateInfo originalFlowUpdateInfo = updateInfo.get();

                try {
                    String heartbeatString;
                    try {
                        heartbeatString = generateHeartbeat();
                        if (StringUtils.isBlank(heartbeatString)) {
                            // If the process is still initializing, and we cannot generate a heartbeat, do not proceed with the execution
                            return;
                        }
                    } catch (IOException e) {
                        logger.info("Instance is currently restarting and cannot heartbeat.");
                        return;
                    }

                    // Update flow identifier
                    final Payload payload = objectMapper.readValue(heartbeatString, Payload.class);

                    FlowUpdateInfo flowUpdateInfo = updateInfo.get();
                    if (flowUpdateInfo != null) {
                        final String flowId = flowUpdateInfo.getFlowId();
                        logger.trace("Determined that current flow id is {}.", flowId);
                        payload.getFlowInfo().setFlowId(flowId);
                        heartbeatString = objectMapper.writeValueAsString(payload);
                    }

                    final RequestBody requestBody = RequestBody.create(MediaType.parse(javax.ws.rs.core.MediaType.APPLICATION_JSON), heartbeatString);
                    final String c2Url = properties.get().getProperty("nifi.c2.rest.url");
                    logger.info("Performing request to {}", c2Url);
                    final Request.Builder requestBuilder = new Request.Builder()
                            .post(requestBody)
                            .url(c2Url);


                    try {
                        final Response heartbeatResponse = httpClientReference.get().newCall(requestBuilder.build()).execute();
                        int statusCode = heartbeatResponse.code();
                        final String responseBody = heartbeatResponse.body().string();
                        logger.debug("Received heartbeat response (Status={}) {}", statusCode, responseBody);
                        ObjectMapper objMapper = new ObjectMapper();
                        final JsonNode responseJsonNode = objMapper.readTree(responseBody);

                        final JsonNode requestedOperations = responseJsonNode.get("requestedOperations");
                        if (requestedOperations != null && requestedOperations.size() > 0) {
                            logger.info("Received {} operations from the C2 server", requestedOperations.size());
                            JsonNode operation = requestedOperations.get(0);
                            if (operation.get("operation").asText().equals("UPDATE")) {
                                final String opIdentifier = operation.get("identifier").asText();
                                final JsonNode args = operation.get("args");
                                final String updateLocation = args.get("location").asText();

                                final FlowUpdateInfo fui = new FlowUpdateInfo(updateLocation, opIdentifier);
                                final FlowUpdateInfo currentFui = updateInfo.get();
                                if (currentFui == null || !currentFui.getFlowId().equals(fui.getFlowId())) {
                                    logger.info("Will perform flow update from {} for command #{}.  Previous flow id was {} with new id {}", updateLocation, opIdentifier, currentFui == null ? "not set" : currentFui.getFlowId(), fui.getFlowId());
                                    updateInfo.set(fui);
                                } else {
                                    logger.info("Flow is current...");
                                }
                                updateInfo.set(fui);
                            }
                        }
                    } catch (IOException e) {
                        logger.error("Could not transmit", e);
                        throw new RuntimeException(e);

                    }
                } catch (IOException e) {
                    logger.error("Could not transmit", e);
                    throw new RuntimeException(e);
                }

                // Attempt to pull config
                if (updateInfo.get() == null) {
                    logger.debug("Not performing flow update.");
                    return;
                }

                if (originalFlowUpdateInfo != null) {
                    if (originalFlowUpdateInfo.getFlowId().equals(updateInfo.get().getFlowId())) {
                        logger.debug("Not performing flow update as flow has not changed");
                        return;
                    }
                }

                final FlowUpdateInfo newFlowInfo = updateInfo.get();
                logger.info("Attempting to pull new config from {}", newFlowInfo.getFlowUpdateUrl());

                final Request.Builder requestBuilder = new Request.Builder()
                        .get()
                        .url(newFlowInfo.getFlowUpdateUrl());
                final Request request = requestBuilder.build();

                ResponseBody body = null;
                try (final Response response = httpClientReference.get().newCall(request).execute()) {
                    logger.debug("Response received: {}", response.toString());

                    logger.info("Performing acknowledgement as we have received a response.");
                    if (response.isSuccessful()) {
                        acknowledgeRequest(newFlowInfo);
                    }

                    int code = response.code();

                    if (code >= 400) {
                        throw new IOException("Got response code " + code + " while trying to pull configuration: " + response.body().string());
                    }

                    body = response.body();

                    if (body == null) {
                        logger.warn("No body returned when pulling a new configuration");
                        return;
                    }

                    final ByteBuffer bodyByteBuffer = ByteBuffer.wrap(body.bytes());
                    ByteBuffer readOnlyNewConfig = null;

                    // checking if some parts of the configuration must be preserved
                    if (overrideSecurity) {
                        readOnlyNewConfig = bodyByteBuffer.asReadOnlyBuffer();
                    } else {
                        logger.debug("Preserving previous security properties...");

                        // get the current security properties from the current configuration file
                        final File configFile = new File(properties.get().getProperty(RunMiNiFi.MINIFI_CONFIG_FILE_KEY));
                        ConvertableSchema<ConfigSchema> configSchema = SchemaLoader.loadConvertableSchemaFromYaml(new FileInputStream(configFile));
                        ConfigSchema currentSchema = configSchema.convert();
                        SecurityPropertiesSchema secProps = currentSchema.getSecurityProperties();

                        // override the security properties in the pulled configuration with the previous properties
                        configSchema = SchemaLoader.loadConvertableSchemaFromYaml(new ByteBufferInputStream(bodyByteBuffer.duplicate()));
                        ConfigSchema newSchema = configSchema.convert();
                        newSchema.setSecurityProperties(secProps);

                        // return the updated configuration preserving the previous security configuration
                        readOnlyNewConfig = ByteBuffer.wrap(new Yaml().dump(newSchema.toMap()).getBytes()).asReadOnlyBuffer();
                    }

                    if (differentiator.isNew(readOnlyNewConfig)) {
                        logger.debug("New change received, notifying listener");
                        agentMonitor.getConfigChangeNotifier().notifyListeners(readOnlyNewConfig);
                        logger.debug("Listeners notified");
                    } else {
                        logger.debug("Pulled config same as currently running.");
                    }

                    if (useEtag) {
                        lastEtag = (new StringBuilder("\""))
                                .append(response.header("ETag").trim())
                                .append("\"").toString();
                    }
                } catch (Exception e) {
                    logger.warn("Hit an exception while trying to pull", e);
                }

                //  Clear flowupdate after successful change
                if (updateInfo.compareAndSet(originalFlowUpdateInfo, null)) {
                    logger.info("Finished for flow at {}", originalFlowUpdateInfo.getFlowUpdateUrl());
                    logger.info("Value was nullified as there was no change");
                } else {
                    logger.info("Value was changed while performing update");
                }

            } catch (Exception e) {
                logger.error("Could not run the heartbeat reporter runnable for this execution.");
            }
        }
    }

    private void acknowledgeRequest(FlowUpdateInfo flowUpdateInfo) {
        final String c2Url = properties.get().getProperty("nifi.c2.rest.url.ack");
        logger.warn("Performing acknowledgement request to {} for flow {}", c2Url, flowUpdateInfo.getFlowId());
        flowIdReference.set(flowUpdateInfo.getFlowId());
        final ObjectMapper jacksonObjectMapper = new ObjectMapper();
        jacksonObjectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        final C2OperationAck operationAck = new C2OperationAck();
        operationAck.setOperationId(flowUpdateInfo.getRequestId());
        try {
            final String operationAckBody = jacksonObjectMapper.writeValueAsString(operationAck);

            final RequestBody requestBody = RequestBody.create(MediaType.parse(javax.ws.rs.core.MediaType.APPLICATION_JSON), operationAckBody);
            final Request.Builder requestBuilder = new Request.Builder()
                    .post(requestBody)
                    .url(c2Url);
            final Response heartbeatResponse = httpClientReference.get().newCall(requestBuilder.build()).execute();
            if (!heartbeatResponse.isSuccessful()) {
                logger.warn("Acknowledgement was not successful.");
            }
            logger.trace("Status on acknowledgement was {}", heartbeatResponse.code());
        } catch (Exception e) {
            logger.error("Could not transmit ack to c2 server", e);
        }

    }

    private class FlowUpdateInfo {
        private final String flowUpdateUrl;
        private final String requestId;

        public FlowUpdateInfo(final String flowUpdateUrl, final String requestId) {
            this.flowUpdateUrl = flowUpdateUrl;
            this.requestId = requestId;
        }

        public String getFlowUpdateUrl() {
            return flowUpdateUrl;
        }

        public String getRequestId() {
            return requestId;
        }

        public String getFlowId() {
            try {
                final URI flowUri = new URI(flowUpdateUrl);
                final String flowUriPath = flowUri.getPath();
                final String[] split = flowUriPath.split("/");
                final String flowId = split[4];
                return flowId;
            } catch (Exception e) {
                throw new IllegalStateException("Could not get flow id from the provided URL");
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FlowUpdateInfo that = (FlowUpdateInfo) o;
            return Objects.equals(flowUpdateUrl, that.flowUpdateUrl) &&
                    Objects.equals(requestId, that.requestId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(flowUpdateUrl, requestId);
        }
    }

    private static class Payload {

        private String operation;
        private AgentInfo agentInfo;
        private DeviceInfo deviceInfo;
        private FlowInfo flowInfo;

        public Payload() {
        }

        public Payload(C2Heartbeat heartbeat) {
            this.operation = "heartbeat";
            this.agentInfo = heartbeat.getAgentInfo();
            this.deviceInfo = heartbeat.getDeviceInfo();
            this.flowInfo = heartbeat.getFlowInfo();
        }

        public String getOperation() {
            return operation;
        }

        public void setOperation(String operation) {
            this.operation = operation;
        }

        public AgentInfo getAgentInfo() {
            return agentInfo;
        }

        public void setAgentInfo(AgentInfo agentInfo) {
            this.agentInfo = agentInfo;
        }

        public DeviceInfo getDeviceInfo() {
            return deviceInfo;
        }

        public void setDeviceInfo(DeviceInfo deviceInfo) {
            this.deviceInfo = deviceInfo;
        }

        public FlowInfo getFlowInfo() {
            return flowInfo;
        }

        public void setFlowInfo(FlowInfo flowInfo) {
            this.flowInfo = flowInfo;
        }
    }

    private String generateHeartbeat() throws IOException {
        return this.agentMonitor.getBundles();
    }

    private void setSslSocketFactory(OkHttpClient.Builder okHttpClientBuilder, C2Properties properties) throws Exception {
        final String keystoreLocation = properties.getKeystore();
        final String keystoreType = properties.getKeystoreType();
        final String keystorePass = properties.getKeystorePassword();

        assertKeystorePropertiesSet(keystoreLocation, keystorePass, keystoreType);

        // prepare the keystore
        final KeyStore keyStore = KeyStore.getInstance(keystoreType);

        try (FileInputStream keyStoreStream = new FileInputStream(keystoreLocation)) {
            keyStore.load(keyStoreStream, keystorePass.toCharArray());
        }

        final KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, keystorePass.toCharArray());

        // load truststore
        final String truststoreLocation = properties.getTruststore();
        final String truststorePass = properties.getTruststorePassword();
        final String truststoreType = properties.getTruststoreType();
        assertTruststorePropertiesSet(truststoreLocation, truststorePass, truststoreType);

        KeyStore truststore = KeyStore.getInstance(truststoreType);
        final TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("X509");
        truststore.load(new FileInputStream(truststoreLocation), truststorePass.toCharArray());
        trustManagerFactory.init(truststore);

        final X509TrustManager x509TrustManager;
        TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
        if (trustManagers[0] != null) {
            x509TrustManager = (X509TrustManager) trustManagers[0];
        } else {
            throw new IllegalStateException("List of trust managers is null");
        }

        SSLContext tempSslContext;
        try {
            tempSslContext = SSLContext.getInstance("TLS");
        } catch (NoSuchAlgorithmException e) {
            logger.warn("Unable to use 'TLS' for the PullHttpChangeIngestor due to NoSuchAlgorithmException. Will attempt to use the default algorithm.", e);
            tempSslContext = SSLContext.getDefault();
        }

        final SSLContext sslContext = tempSslContext;
        sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);

        final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
        okHttpClientBuilder.sslSocketFactory(sslSocketFactory, x509TrustManager);
    }

    private void assertKeystorePropertiesSet(String location, String password, String type) {
        if (location == null || location.isEmpty()) {
            throw new IllegalArgumentException(KEYSTORE_LOCATION_KEY + " is null or is empty");
        }

        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException(KEYSTORE_LOCATION_KEY + " is set but " + KEYSTORE_PASSWORD_KEY + " is not (or is empty). If the location is set, the password must also be.");
        }

        if (type == null || type.isEmpty()) {
            throw new IllegalArgumentException(KEYSTORE_LOCATION_KEY + " is set but " + KEYSTORE_TYPE_KEY + " is not (or is empty). If the location is set, the type must also be.");
        }
    }

    private void assertTruststorePropertiesSet(String location, String password, String type) {
        if (location == null || location.isEmpty()) {
            throw new IllegalArgumentException(TRUSTSTORE_LOCATION_KEY + " is not set or is empty");
        }

        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException(TRUSTSTORE_LOCATION_KEY + " is set but " + TRUSTSTORE_PASSWORD_KEY + " is not (or is empty). If the location is set, the password must also be.");
        }

        if (type == null || type.isEmpty()) {
            throw new IllegalArgumentException(TRUSTSTORE_LOCATION_KEY + " is set but " + TRUSTSTORE_TYPE_KEY + " is not (or is empty). If the location is set, the type must also be.");
        }
    }

}
