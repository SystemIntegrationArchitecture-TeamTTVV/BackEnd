package edu.iuh.fit.se.messegeservice.repository;

import edu.iuh.fit.se.messegeservice.model.LiveStream;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LiveStreamRepository extends MongoRepository<LiveStream, String> {

    List<LiveStream> findByStatus(String status);

    List<LiveStream> findByStatusOrderByStartedAtDesc(String status);

    List<LiveStream> findByStreamerIdAndStatusIn(String streamerId, List<String> statuses);

    Optional<LiveStream> findByStreamKey(String streamKey);

    List<LiveStream> findByStreamerIdOrderByCreatedAtDesc(String streamerId);
}
