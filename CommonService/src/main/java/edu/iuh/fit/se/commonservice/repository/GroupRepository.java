package edu.iuh.fit.se.commonservice.repository;

import edu.iuh.fit.se.commonservice.model.Group;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GroupRepository extends MongoRepository<Group, String> {
    List<Group> findByIsActiveTrueOrderByCreatedAtDesc();
    List<Group> findByNameContainingIgnoreCase(String name);
    List<Group> findByAdminIdAndIsActiveTrueOrderByCreatedAtDesc(String adminId);
}

