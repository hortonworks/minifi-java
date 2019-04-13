package org.apache.nifi.minifi.c2.agent.client;

import java.util.UUID;

public class UuidGenerator implements IdGenerator {

    private final String seed;

    public UuidGenerator(final String seed) {
        this.seed = seed;
    }

    @Override
    public String generate() {
        return UUID.nameUUIDFromBytes(seed.getBytes()).toString();
    }
}
