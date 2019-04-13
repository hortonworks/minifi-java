/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.nifi.minifi.bootstrap.status;

import org.apache.nifi.minifi.bootstrap.QueryableStatusAggregator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public abstract class PeriodicStatusReporter {

    private static Logger logger = LoggerFactory.getLogger(PeriodicStatusReporter.class);

    private final ScheduledThreadPoolExecutor scheduledExecutorService = new ScheduledThreadPoolExecutor(1);

    private volatile long period = -1;
    private volatile int termination_wait = 5000;

    public volatile Runnable reportRunner;

    /**
     * Provides an opportunity for the implementation to perform configuration and initialization based on properties received from the bootstrapping configuration.
     *
     * @param properties from the bootstrap configuration
     */
    public abstract void initialize(Properties properties, QueryableStatusAggregator queryableStatusAggregator);

    public abstract boolean isInitialized();

    /**
     * Begins the associated reporting service provided by the given implementation.  In most implementations, no action will occur until this method is invoked. The implementing class must have set
     * 'reportRunner' prior to this method being called.
     */
    public void start() {
        if (reportRunner == null) {
            logger.error("Report runner was null");
            throw new IllegalStateException("Programmatic error, the reportRunner is still NULL when 'start' was called.");
        }
        try {
            scheduledExecutorService.scheduleAtFixedRate(reportRunner, 0, 1000, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            logger.error("Could not start status reporter", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Stops the associated reporting service provided by the given implementation.
     */
    public void stop() {
        try {
            scheduledExecutorService.shutdown();
            scheduledExecutorService.awaitTermination(termination_wait, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignore) {
            // Shutting down anyway
        }
    }

    public long getPeriod() {
        return period;
    }

    public void setPeriod(long period) {
        this.period = period;
    }

    public int getTermination_wait() {
        return termination_wait;
    }

    public void setTermination_wait(int termination_wait) {
        this.termination_wait = termination_wait;
    }
}
