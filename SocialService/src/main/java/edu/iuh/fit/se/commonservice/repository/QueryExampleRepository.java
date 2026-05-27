package edu.iuh.fit.se.commonservice.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import edu.iuh.fit.se.commonservice.model.QueryExample;

public interface QueryExampleRepository extends MongoRepository<QueryExample, String> {

    List<QueryExample> findBySuccessTrueOrderByCreatedAtDesc();

    List<QueryExample> findTop50BySuccessTrueOrderByCreatedAtDesc();
}
