package edu.cit.alvarado.channel;

import edu.cit.alvarado.inventory.InventoryItem;
import edu.cit.alvarado.inventory.InventoryService;
import edu.cit.alvarado.supplier.SupplierGateway;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Decides what we sell on Tiangge: every Inventory product that has a
 * LegacySupply mapping (Lab 3), up to Tiangge's limit of 10 listings. Our
 * Tiangge sellerSku is simply our own productId (P100, P200, ...), so
 * translating between the two needs no lookup table.
 */
@Component
class ListingCatalog {

    private static final int TIANGGE_MAX_LISTINGS = 10;

    private final InventoryService inventoryService;
    private final SupplierGateway supplierGateway;

    ListingCatalog(InventoryService inventoryService, SupplierGateway supplierGateway) {
        this.inventoryService = inventoryService;
        this.supplierGateway = supplierGateway;
    }

    List<TianggeJson.Listing> listings() {
        List<TianggeJson.Listing> listings = new ArrayList<>();
        for (InventoryItem item : inventoryService.listAll()) {
            Optional<String> supplierSku = supplierGateway.supplierSkuFor(item.getProductId());
            if (supplierSku.isPresent() && listings.size() < TIANGGE_MAX_LISTINGS) {
                listings.add(new TianggeJson.Listing(toSellerSku(item.getProductId()), item.getName(), supplierSku.get()));
            }
        }
        return listings;
    }

    boolean isListed(String productId) {
        return listings().stream().anyMatch(l -> l.sellerSku().equals(toSellerSku(productId)));
    }

    static String toSellerSku(String productId) {
        return productId;
    }

    static String toProductId(String sellerSku) {
        return sellerSku;
    }
}
