# INTEGRATION.md

## 1. Product mapping

| Our Product ID | Our Name | LegacySupply SupplierSku | PackSize |
|---|---|---|---|
| P100 | Wireless Mouse | BTK-1241 | 6 |
| P200 | Mechanical Keyboard | BTK-7342 | 10 |
| P300 | USB-C Hub | BTK-7074 | 12 |

Matched by product similarity against LegacySupply's `GET /catalog` response
(`WIRELESS MOUSE 2.4GHZ`, `KEYBOARD MECH TKL`, `USB HUB 4-PORT`) since each
partner's catalog and item numbers are their own — there's no shared ID to
join on, only description matching by judgment. Worth flagging: P300's
match ("USB HUB 4-PORT") isn't an exact name match to "USB-C Hub" — it was
the closest reasonable candidate in the catalog, not a guaranteed-correct
pairing, which is a limitation of description-based matching.

## 2. Session behavior

Authentication is `POST /auth/token` with `ClientId` + `ApiKey`, returning
a `SessionToken` sent as `X-LS-Session` on every subsequent request. A
session obtained at 03:09:28 UTC was confirmed still valid at least through
t+61 seconds in one direct test, and separately, by the time a later
request was made (several minutes afterward, in a later terminal session),
the same token had expired and returned `401 E-AUTH-07 Session not
valid.` So the observed lifetime is at least roughly a minute, and
somewhere under several minutes — the manual doesn't state an exact
figure, and testing bore that out: it isn't a fixed, documented TTL you
can rely on, only a "short-lived, expect to need a new one" guarantee.

Our adapter (`LegacySupplyClient`) never hardcodes or waits on a specific
duration — it treats any `401` with `E-AUTH-02`/`E-AUTH-03`/`E-AUTH-07` as
"get a new session and retry once," which sidesteps needing to know the
exact lifetime at all.

## 3. Error codes observed

| Trigger | Status Code | Code | Message |
|---|---|---|---|
| `GET /catalog` during a real service disruption | 503 | E-SYS-50 | Processing error. |
| No `X-LS-Session` header sent | 401 | E-AUTH-02 | Session header missing. |
| Garbage/fabricated session token | 401 | E-AUTH-03 | Session not recognized. |
| A real session token, after it had expired | 401 | E-AUTH-07 | Session not valid. |
| `SupplierSku` not in our catalog | 422 | E-SKU-02 | Item not recognized. |
| `Qty` sent as 0 | 422 | E-QTY-11 | Quantity invalid. |
| `BuyerRef` omitted from the request | 400 | E-REF-05 | BuyerRef invalid. |
| `Content-Type: application/json` instead of `application/xml` | 415 | E-FMT-01 | Unsupported media. |
| Same `X-Request-Id` sent twice with different request bodies | 409 | E-IDEM-04 | Request id reused with different content. |
| A network/read timeout talking to LegacySupply (live, unprompted) | — (transport failure, no HTTP response) | — | `Could not refresh status for RO-2: httpStatus=-1 error=transport failure` (our own log line) |

The last row wasn't deliberately triggered — it happened on its own while
`SupplierOrderPollingJob` was polling a real order, consistent with the
manual's warning that "response times vary, and the service may be
unavailable... without notice." Our client treated it as retryable rather
than a business failure, per its `isRetryableServerError()` check.

## 4. Qty and Uom, in your own words

`Qty` in a purchase order is a whole number of **packs** (LegacySupply's
own unit of measure for that item — e.g. `Uom: "CS"` for a case), not
individual units. Our own inventory tracks stock in individual units, so
`LegacySupplyGatewayImpl` converts: it takes how many units are needed to
get back up to our target stock level, then divides by that product's
`PackSize`, rounding **up** to the next whole pack (since you can't order
a fraction of a case).

**Worked example, from an actual delivery in this run:** P100 dropped to
4 units in stock against a target level of 20, so 16 units were needed.
P100's `PackSize` is 6 (`BTK-1241`), so `Qty` sent was `ceil(16 / 6) = 3`
packs. LegacySupply confirmed the order, and once delivered, our system's
own log recorded exactly `18 units of P100` restocked (3 packs × 6 units
per pack) — matching the math exactly.
