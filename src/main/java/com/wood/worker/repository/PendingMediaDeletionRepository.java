package com.wood.worker.repository;

import com.wood.worker.model.PendingMediaDeletion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PendingMediaDeletionRepository extends JpaRepository<PendingMediaDeletion, String> {
}
