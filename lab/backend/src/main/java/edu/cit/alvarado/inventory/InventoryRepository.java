package edu.cit.alvarado.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Package-private on purpose: persistence details of the Inventory module
 * are an implementation detail. Nothing outside this package (including the
 * Order module) should talk to the repository directly.
 */
interface InventoryRepository extends JpaRepository<InventoryItem, String> {
}
