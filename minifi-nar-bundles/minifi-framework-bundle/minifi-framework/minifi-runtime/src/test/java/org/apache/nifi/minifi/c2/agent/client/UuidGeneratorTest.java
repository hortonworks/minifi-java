package org.apache.nifi.minifi.c2.agent.client;

import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.*;

public class UuidGeneratorTest {

    private static final String TEST_SEED = "my-test-seed";
    private static final String EXPECTED_GENERATED_ID = "61ebb199-9804-313c-911b-9a81eb92f11b";


    @Test
    public void testGenerate() {
        final String generatedId = new UuidGenerator(TEST_SEED).generate();

        Assert.assertNotNull(generatedId);
        Assert.assertEquals(EXPECTED_GENERATED_ID, generatedId);
    }

    @Test
    public void testGeneratedIdMulitpleInvocations() {
        for (int i = 0; i < 10; i++) {
            final String generatedId = new UuidGenerator(TEST_SEED).generate();
            Assert.assertNotNull(generatedId);
            Assert.assertEquals(EXPECTED_GENERATED_ID, generatedId);
        }
    }
}