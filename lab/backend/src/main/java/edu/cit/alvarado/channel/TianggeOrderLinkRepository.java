package edu.cit.alvarado.channel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface TianggeOrderLinkRepository extends JpaRepository<TianggeOrderLink, String> {

    List<TianggeOrderLink> findByDecisionReportedFalse();

    /** Backorders still waiting for stock (not resolved, not cancelled by the buyer). */
    List<TianggeOrderLink> findByDecisionAndResolutionIsNullAndCancelRequestedFalse(String decision);

    List<TianggeOrderLink> findByResolutionIsNotNullAndResolutionReportedFalse();

    List<TianggeOrderLink> findByCancelRequestedTrueAndCancelConfirmedFalse();

    long countByDecision(String decision);
}
