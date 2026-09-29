package edu.cit.alvarado.supplier;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface SupplierOrderRepository extends JpaRepository<SupplierOrder, Long> {

    List<SupplierOrder> findByStatus(SupplierOrderStatus status);

    Optional<SupplierOrder> findByRequestId(String requestId);

    /** Used before creating a new reorder, to avoid piling up duplicate
     *  in-flight orders for the same product while one is already open. */
    List<SupplierOrder> findByProductIdAndStatusIn(String productId, List<SupplierOrderStatus> statuses);
}
