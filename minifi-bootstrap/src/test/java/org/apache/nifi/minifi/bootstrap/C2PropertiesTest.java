package org.apache.nifi.minifi.bootstrap;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Properties;

import static org.junit.Assert.*;

public class C2PropertiesTest {

    private static final String TEST_AGENT_CLASS = "my-test-agent";
    private C2Properties c2props;

    @Test
    public void testGetProperty() {
        final Properties properties = new Properties();
        properties.put(C2Properties.C2_AGENT_CLASS_KEY, TEST_AGENT_CLASS);
        c2props = new C2Properties(properties);

        Assert.assertEquals(TEST_AGENT_CLASS, c2props.getAgentClass());
    }

    @Test
    public void testGetPropertyDeprecatedKey() {
        // Deprecated keys started with c2.*
        final String deprecatedAgentClassKey = C2Properties.C2_AGENT_CLASS_KEY.replaceFirst("nifi.", "");

        final Properties properties = new Properties();
        properties.put(deprecatedAgentClassKey, TEST_AGENT_CLASS);
        c2props = new C2Properties(properties);

        Assert.assertEquals(TEST_AGENT_CLASS, c2props.getAgentClass());
    }

    @Test
    public void testSetAgentHeartbeatPeriod() {
        final String validPeriodString = "3000";

        final Properties properties = new Properties();
        properties.put(C2Properties.C2_AGENT_HEARTBEAT_PERIOD_KEY, validPeriodString);
        c2props = new C2Properties(properties);

        Assert.assertEquals(3000, c2props.getAgentHeartbeatPeriod());
    }

    @Test
    public void testSetAgentInvalidHeartbeatPeriod() {
        final String invalidPeriodString = "abc";

        final Properties properties = new Properties();
        properties.put(C2Properties.C2_AGENT_HEARTBEAT_PERIOD_KEY, invalidPeriodString);
        c2props = new C2Properties(properties);

        // An invalid entry should resort to using the default heartbeat period
        Assert.assertEquals(C2Properties.C2_AGENT_DEFAULT_HEARTBEAT_PERIOD, c2props.getAgentHeartbeatPeriod());
    }
}