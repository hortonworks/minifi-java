package org.apache.nifi.minifi.c2.client.impl.jersey;


import com.cloudera.cem.efm.client.C2Client;
import com.cloudera.cem.efm.client.C2ClientConfig;

import com.cloudera.cem.efm.client.C2Exception;
import com.cloudera.cem.efm.client.impl.jersey.JerseyC2Client;
import com.cloudera.cem.efm.model.AgentClass;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

public class JerseyAgentClassClientIT {

    private C2Client c2Client;

    @Before
    public void setUp() throws Exception {
        final C2ClientConfig.Builder clientCfgBuilder = new C2ClientConfig.Builder();
        clientCfgBuilder.baseUrl("http://localhost:10080");

        C2ClientConfig clientConfig = clientCfgBuilder.build();
        c2Client = new JerseyC2Client.Builder().config(clientConfig).build();
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void createAgentClass() throws C2Exception, IOException {

        AgentClass agentClass = new AgentClass();
        agentClass.setName("Secret Agent Class");
        agentClass.setDescription("This class is super duper secret.");

        AgentClass returnedAgentClass = c2Client.getAgentClassClient().createAgentClass(agentClass);
        Assert.assertNotNull(returnedAgentClass);
    }

    @Test
    public void getAgentClasses() throws C2Exception, IOException {
        List<AgentClass> agentClasses = c2Client.getAgentClassClient().getAgentClasses();
        Assert.assertTrue(agentClasses.size() > 0);
    }

    @Test
    public void getAgentClass() throws C2Exception, IOException {
        AgentClass retrievedClass = c2Client.getAgentClassClient().getAgentClass("Secret Agent Class");
        Assert.assertNotNull(retrievedClass);
        Assert.assertEquals("Secret Agent Class", retrievedClass.getName());
    }

    @Test
    public void replaceAgentClass() throws C2Exception, IOException {

        final String updatedDescription = "This class is super duper secreter.";

        AgentClass agentClass = new AgentClass();
        agentClass.setName("Secret Agent Class");
        agentClass.setDescription(updatedDescription);

        final AgentClass updatedAgentClass = c2Client.getAgentClassClient().replaceAgentClass(agentClass.getName(), agentClass);

        Assert.assertEquals(updatedDescription, updatedAgentClass.getDescription());

    }

    @Test
    public void deleteAgentClassExistent() throws C2Exception, IOException {
        final AgentClass validClassDeletion = c2Client.getAgentClassClient().deleteAgentClass("Secret Agent Class");
        Assert.assertNotNull(validClassDeletion);
    }

    @Test(expected = C2Exception.class)
    public void deleteAgentClassNonexistent() throws C2Exception, IOException {
        final AgentClass nonExistentClassDeletion = c2Client.getAgentClassClient().deleteAgentClass("No Such Class");
        Assert.fail("Delete action should not have successfully occurred.");
    }
}