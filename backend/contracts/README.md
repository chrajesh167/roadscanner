# Internal service contracts

Canonical, byte-level samples of every JSON payload that crosses a RoadScanner service boundary.

Each file here is the **single source of truth** for one wire shape. It is not a test fixture that
happens to be shared — it is the contract, and both sides are tested against it:

- the **producing** service has a test proving that what it serializes equals the file exactly,
  field for field, with no extra and no missing fields;
- every **consuming** service has a test that feeds the file verbatim through its real HTTP client
  and asserts every field lands in its domain model.

That pairing is what makes a rename impossible to land silently. Renaming a field on the producer
breaks the producer test; renaming it on a consumer leaves a null where the assertion expects a
value. Previously each side hand-wrote its own JSON literal in its own test, so both sides could
pass while disagreeing — which is exactly how the `busType` → `serviceClass` rename reached `main`
with a broken inventory-service binding and two green suites.

## Layout

`<producing-service>/<payload>.json`

| File | Produced by | Consumed by |
| --- | --- | --- |
| `provider-integration-service/search-trips-response.json` | `SearchTripsResponse` (both search routes) | search-service, inventory-service |
| `provider-integration-service/authenticate-provider-response.json` | `AuthenticateProviderResponse` | inventory-service |
| `provider-integration-service/seat-map-response.json` | `SeatMapResponse` | inventory-service |

## Changing a contract

A change here is a change to a published interface between services. Edit the file, then run the
producer suite and **every** consumer suite listed above. If a consumer needs to keep working
through the change, the field must be added rather than renamed — consumers bind with
`ignoreUnknown`, so additive changes are safe and renames are not.
