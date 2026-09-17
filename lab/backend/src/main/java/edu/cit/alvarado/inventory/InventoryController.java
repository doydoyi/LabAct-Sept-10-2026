package edu.cit.alvarado.inventory;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Live inventory read endpoint, used by the frontend's auto-refreshing table. */
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    public List<InventoryView> listInventory() {
        return inventoryService.listAll().stream()
                .map(item -> new InventoryView(item.getProductId(), item.getName(), item.getStock()))
                .toList();
    }
}
