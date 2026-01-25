//package edu.iuh.fit.se.commonservice.controller;
//
//import edu.iuh.fit.se.commonservice.dto.StoryDTO;
//import edu.iuh.fit.se.commonservice.service.StoryService;
//import lombok.RequiredArgsConstructor;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//
//import java.util.List;
//
//@RestController
//@RequestMapping("/api/stories")
//@RequiredArgsConstructor
//public class StoryController {
//
//    private final StoryService storyService;
//
//    @GetMapping
//    public ResponseEntity<List<StoryDTO>> getAllActiveStories() {
//        return ResponseEntity.ok(storyService.getAllActiveStories());
//    }
//
//    @GetMapping("/author/{authorId}")
//    public ResponseEntity<List<StoryDTO>> getStoriesByAuthorId(@PathVariable String authorId) {
//        return ResponseEntity.ok(storyService.getStoriesByAuthorId(authorId));
//    }
//
//    @GetMapping("/{id}")
//    public ResponseEntity<StoryDTO> getStoryById(@PathVariable String id) {
//        return ResponseEntity.ok(storyService.getStoryById(id));
//    }
//
//    @PostMapping
//    public ResponseEntity<StoryDTO> createStory(@RequestBody StoryDTO storyDTO) {
//        return ResponseEntity.status(HttpStatus.CREATED).body(storyService.createStory(storyDTO));
//    }
//
//    @PutMapping("/{id}")
//    public ResponseEntity<StoryDTO> updateStory(@PathVariable String id, @RequestBody StoryDTO storyDTO) {
//        return ResponseEntity.ok(storyService.updateStory(id, storyDTO));
//    }
//
//    @PutMapping("/{id}/view")
//    public ResponseEntity<StoryDTO> incrementViewCount(@PathVariable String id) {
//        return ResponseEntity.ok(storyService.incrementViewCount(id));
//    }
//
//    @DeleteMapping("/{id}")
//    public ResponseEntity<Void> deleteStory(@PathVariable String id) {
//        storyService.deleteStory(id);
//        return ResponseEntity.noContent().build();
//    }
//
//    @DeleteMapping("/expired")
//    public ResponseEntity<Void> deleteExpiredStories() {
//        storyService.deleteExpiredStories();
//        return ResponseEntity.noContent().build();
//    }
//}
//
