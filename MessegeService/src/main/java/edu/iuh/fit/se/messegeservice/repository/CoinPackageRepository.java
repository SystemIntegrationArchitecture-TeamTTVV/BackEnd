package edu.iuh.fit.se.messegeservice.repository;

import edu.iuh.fit.se.messegeservice.model.CoinPackage;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CoinPackageRepository extends MongoRepository<CoinPackage, String> {
    List<CoinPackage> findByActiveTrueOrderBySortOrderAsc();
    List<CoinPackage> findAllByOrderBySortOrderAsc();
}
