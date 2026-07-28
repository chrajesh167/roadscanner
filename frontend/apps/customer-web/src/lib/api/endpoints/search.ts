import { searchApi } from '../client';
import type {
  SearchResultResponse,
  SearchSuggestionsResponse,
  SearchTripsParams,
  TripResponse,
} from '../types';

/** search-service — `adapter/in/rest/{search,detail,suggestion}`. Public, no auth required. */
export const searchEndpoints = {
  /** GET /api/v1/search/trips — origin, destination and date are required by the controller. */
  async searchTrips(params: SearchTripsParams): Promise<SearchResultResponse> {
    const { data } = await searchApi.get<SearchResultResponse>('/api/v1/search/trips', {
      // Undefined values are dropped by axios, so optional filters simply stay off the query.
      params,
    });
    return data;
  },

  /** GET /api/v1/search/trips/{tripId} — indexed trip plus its live availability overlay. */
  async getTrip(tripId: string): Promise<TripResponse> {
    const { data } = await searchApi.get<TripResponse>(`/api/v1/search/trips/${tripId}`);
    return data;
  },

  /** GET /api/v1/search/suggestions — origin/destination autocomplete. */
  async suggestions(query: string, maxResults?: number): Promise<string[]> {
    const { data } = await searchApi.get<SearchSuggestionsResponse>('/api/v1/search/suggestions', {
      params: { query, maxResults },
    });
    return data.suggestions;
  },
};
