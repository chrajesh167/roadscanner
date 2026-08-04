package com.roadscanner.providerintegrationservice.testsupport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Reads a canonical inter-service payload from {@code backend/contracts} — see that directory's
 * README for why the sample lives outside any one service.
 *
 * <p>The point is that producer and consumer read the <em>same bytes</em>. A test that inlines its
 * own JSON literal proves only that a service agrees with itself, which is how a field rename can
 * leave both sides green and the integration broken.
 *
 * <p>Resolved by walking up from the working directory rather than from the classpath: the file is
 * deliberately not packaged into any service's resources, because copying it per service would
 * reintroduce exactly the divergence it exists to prevent.
 */
public final class ServiceContract {

    private ServiceContract() {
    }

    public static String json(String relativePath) {
        Path contract = contractsRoot().resolve(relativePath);
        if (!Files.isRegularFile(contract)) {
            throw new IllegalStateException("No such contract: " + contract);
        }
        try {
            return Files.readString(contract);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read contract " + contract, e);
        }
    }

    private static Path contractsRoot() {
        for (Path candidate = Paths.get("").toAbsolutePath(); candidate != null; candidate = candidate.getParent()) {
            Path contracts = candidate.resolve("backend").resolve("contracts");
            if (Files.isDirectory(contracts)) {
                return contracts;
            }
        }
        throw new IllegalStateException("Could not locate backend/contracts above " + Paths.get("").toAbsolutePath());
    }
}
