package org.apache.nifi.minifi.bootstrap.configuration.ingestors;

import okhttp3.OkHttpClient;

import java.util.concurrent.atomic.AtomicReference;

public interface ConfigurableHttpClient {

    AtomicReference<OkHttpClient> httpClientReference = new AtomicReference<>();

}
