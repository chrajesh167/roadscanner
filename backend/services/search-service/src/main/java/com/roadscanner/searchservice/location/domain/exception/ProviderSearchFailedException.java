package com.roadscanner.searchservice.location.domain.exception;

import com.roadscanner.searchservice.domain.exception.SearchServiceException;

/**
 * One provider could not be searched.
 *
 * <p>Never reaches a client as an error response. The federation catches this per provider, records
 * that provider as failed, and carries on with the rest — so a caller receives whatever the working
 * providers returned, plus an honest signal that the answer is partial.
 *
 * <p>Exists because "no trips" and "could not ask" are different answers that were previously
 * indistinguishable: the adapter returned an empty list for both, and a search where every provider
 * was down looked exactly like a route nobody serves.
 */
public final class ProviderSearchFailedException extends SearchServiceException {

    public ProviderSearchFailedException(String providerCode, Throwable cause) {
        super("Provider " + providerCode + " could not be searched", cause);
    }
}
