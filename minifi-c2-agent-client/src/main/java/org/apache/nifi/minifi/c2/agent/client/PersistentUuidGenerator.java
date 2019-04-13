package org.apache.nifi.minifi.c2.agent.client;

import org.apache.nifi.util.file.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class PersistentUuidGenerator implements IdGenerator {

    private static final Logger logger = LoggerFactory.getLogger(PersistentUuidGenerator.class);

    private final File persistenceLocation;

    public PersistentUuidGenerator(final File persistenceLocation) {
        this.persistenceLocation = persistenceLocation;
    }

    @Override
    public String generate() {
        if (this.persistenceLocation.exists()) {
            return readFile();
        } else {
            return makeFile();
        }
    }

    private String readFile() {
        try {
            final List<String> fileLines = Files.readAllLines(persistenceLocation.toPath());
            if (fileLines.size() != 1) {
                throw new IllegalStateException(String.format("The file %s for the persisted identifier has the incorrect format.", persistenceLocation));
            }
            final String uuid = fileLines.get(0);
            return uuid;
        } catch (IOException e) {
            throw new IllegalStateException(String.format("Could not read file %s for persisted identifier.", persistenceLocation), e);

        }
    }

    private String makeFile() {
        try {
            final File parentDirectory = persistenceLocation.getParentFile();
            FileUtils.ensureDirectoryExistAndCanAccess(parentDirectory);
            final String uuid = UUID.randomUUID().toString();
            Files.write(persistenceLocation.toPath(), Arrays.asList(uuid));
            logger.info("Created identifier {} at {}.", uuid, persistenceLocation);
            return uuid;
        } catch (IOException e) {
            throw new IllegalStateException(String.format("Could not create file %s as persistence file.", persistenceLocation), e);
        }
    }
}
