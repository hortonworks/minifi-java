package org.apache.nifi.minifi.c2.agent.client;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class PersistentUuidGeneratorTest {

    private static final Logger logger = LoggerFactory.getLogger(PersistentUuidGeneratorTest.class);

    private static final String TEST_ID_FILENAME = "test-identifier";

    private PersistentUuidGenerator uuidGenerator;
    private File testFile;

    @Rule
    public TemporaryFolder testDirectory = new TemporaryFolder();

    @Before
    public void setup() throws Exception {
        testFile = new File(testDirectory.getRoot(), TEST_ID_FILENAME);
        uuidGenerator = new PersistentUuidGenerator(testFile);
    }

    @Test
    public void testGenerate() {
        final String generatedId = uuidGenerator.generate();
        Assert.assertNotNull(generatedId);
        Assert.assertEquals("Incorrect length", 36, generatedId.length());
    }

    @Test
    public void testReadExisting() {
        // Generate the file
        final String generatedId = uuidGenerator.generate();

        assertNotNull(generatedId);

        // Create another instance of the generator
        PersistentUuidGenerator anotherInstance = new PersistentUuidGenerator(testFile);
        final String readId = anotherInstance.generate();

        Assert.assertNotNull(readId);
        assertEquals(generatedId, readId);
    }
}