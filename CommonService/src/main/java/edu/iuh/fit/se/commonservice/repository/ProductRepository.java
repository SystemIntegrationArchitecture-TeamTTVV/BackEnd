// ...existing code...
package edu.iuh.fit.se.commonservice.repository;

import edu.iuh.fit.se.commonservice.model.Product;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends MongoRepository<Product, String> {
    List<Product> findBySellerId(String sellerId);
}
// ...existing code...
