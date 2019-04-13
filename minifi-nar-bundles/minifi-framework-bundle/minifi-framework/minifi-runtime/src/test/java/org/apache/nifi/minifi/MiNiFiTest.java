package org.apache.nifi.minifi;

import com.cloudera.cem.efm.model.AgentInfo;
import com.cloudera.cem.efm.model.AgentStatus;
import com.cloudera.cem.efm.model.Device;
import com.cloudera.cem.efm.model.DeviceInfo;
import com.cloudera.cem.efm.model.NetworkInfo;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

public class MiNiFiTest {
    private static final Logger logger = LoggerFactory.getLogger(MiNiFiTest.class);

    @Test
    public void generateAgentInfo() throws Exception {
        NetworkInfo networkInfo = new NetworkInfo();


        final InetAddress localHost = InetAddress.getLocalHost();
        Assert.assertNotNull(localHost);
        final Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();

        Assert.assertNotNull(networkInterfaces);

        while (networkInterfaces.hasMoreElements()) {
            NetworkInterface networkInterface = networkInterfaces.nextElement();

            if (!networkInterface.isLoopback() && networkInterface.isUp()) {
                StringBuilder sb = new StringBuilder();
                byte[] hardwareAddress = networkInterface.getHardwareAddress();
                if (hardwareAddress != null) {
                    for (int i = 0; i < hardwareAddress.length; i++) {
                        sb.append(String.format("%02X", hardwareAddress[i]));
                    }
                }
                String macString = sb.toString();
                logger.trace("Have interface with address {} and name {} with hashcode {}", macString, networkInterface.getName(), macString.hashCode());
                Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
                while (inetAddresses.hasMoreElements()) {
                    InetAddress inetAddress = inetAddresses.nextElement();
                    String hostAddress = inetAddress.getHostAddress();
                    String hostName = inetAddress.getHostName();
                    byte[] address = inetAddress.getAddress();
                    String canonicalHostName = inetAddress.getCanonicalHostName();

                    logger.warn("Have host with hostaddress={} hostname={} address={} canonicalhostname={}", hostAddress, hostName, address, canonicalHostName);
                }
            } else {
                logger.info("Not considering itnerface {}", networkInterface.getName());
            }
        }
    }
}