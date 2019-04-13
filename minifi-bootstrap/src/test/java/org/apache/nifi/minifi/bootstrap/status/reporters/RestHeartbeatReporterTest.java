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

package org.apache.nifi.minifi.bootstrap.status.reporters;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

public class RestHeartbeatReporterTest {

    private static final Logger logger = LoggerFactory.getLogger(RestHeartbeatReporterTest.class);

    @Test
    public void testGetFlowUuid() throws Exception {
        final String sampleUri = "http://proxy/efm/api/flows/27601cce-9a79-4b64-92af-c0e6295474d9/content";
        URI flowUri = new URI(sampleUri);

        final String flowUriPath = flowUri.getPath();
        String[] split = flowUriPath.split("/");
        String flowId = split[4];
        logger.info("Values are {} for {}", split, flowUriPath);
        logger.info("Flow Id is {}", flowId);
    }

}
