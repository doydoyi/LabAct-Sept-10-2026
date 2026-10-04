package edu.cit.alvarado.channel;

import org.springframework.data.jpa.repository.JpaRepository;

interface ProcessedFeedEventRepository extends JpaRepository<ProcessedFeedEvent, String> {
}
