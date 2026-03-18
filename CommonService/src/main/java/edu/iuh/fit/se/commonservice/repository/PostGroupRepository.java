//package edu.iuh.fit.se.commonservice.repository;
//
//import java.util.List;
//
//import edu.iuh.fit.se.commonservice.model.Post;
//import org.springframework.data.mongodb.repository.MongoRepository;
//import org.springframework.data.mongodb.repository.Query;
//
//import edu.iuh.fit.se.commonservice.model.PostGroup;
//
//public interface PostGroupRepository extends MongoRepository<Post, String> {
//
//    //  Lấy bài viết trong group (chưa bị xóa)
//    List<Post> findByGroupIdAndDeletedFalseOrderByCreatedAtDesc(String groupId);
//
///
//    //  Custom query (nếu cần tối ưu sau này)
//    @Query("{ 'group.$id': ?0, 'isDeleted': false }")
//    List<Post> findPostsByGroupCustom(String groupId);
//}
