package edu.cit.alvarado.supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Optional sanity check, not required by the lab: on startup, fetches
 * LegacySupply's catalog once and warns (does not fail startup) if any
 * configured SupplierSku in application.properties doesn't actually appear
 * in the partner's real catalog. Catches a typo'd SKU immediately at boot
 * instead of only discovering it the first time a reorder is attempted.
 */
@Component
@Order(100) // after the marketplace channel's startup (first heartbeat goes out first)
class CatalogStartupCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CatalogStartupCheck.class);

    private final LegacySupplyClient client;
    private final SupplierProperties properties;

    CatalogStartupCheck(LegacySupplyClient client, SupplierProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (properties.getCatalog().isEmpty()) {
            log.warn("No app.supplier.catalog.* mappings configured yet - "
                    + "auto-reorder will fail until they're filled in from GET /catalog");
            return;
        }

        LegacySupplyClient.Outcome outcome = client.getCatalog();
        if (!outcome.isSuccess()) {
            log.warn("Could not verify supplier catalog at startup (httpStatus={}) - "
                    + "skipping SKU validation, will only find out at reorder time", outcome.httpStatus);
            return;
        }

        String catalogXml = outcome.body;
        properties.getCatalog().forEach((productId, entry) -> {
            if (entry.getSku() != null && !catalogXml.contains(entry.getSku())) {
                log.warn("Configured SupplierSku '{}' for product {} was NOT found in LegacySupply's "
                        + "current catalog - double check app.supplier.catalog.{}.sku",
                        entry.getSku(), productId, productId);
            }
        });
    }
}
