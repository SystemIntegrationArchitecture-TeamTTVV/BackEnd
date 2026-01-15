package edu.iuh.fit.se.commonservice.controller;

import edu.iuh.fit.se.commonservice.dto.GroupDTO;
import edu.iuh.fit.se.commonservice.service.GroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    @GetMapping
    public ResponseEntity<List<GroupDTO>> getAllGroups() {
        return ResponseEntity.ok(groupService.getAllGroups());
    }

    @GetMapping("/{id}")
    public ResponseEntity<GroupDTO> getGroupById(@PathVariable String id) {
        return ResponseEntity.ok(groupService.getGroupById(id));
    }

    @GetMapping("/search")
    public ResponseEntity<List<GroupDTO>> searchGroups(@RequestParam String name) {
        return ResponseEntity.ok(groupService.searchGroups(name));
    }

    @GetMapping("/admin/{adminId}")
    public ResponseEntity<List<GroupDTO>> getGroupsByAdminId(@PathVariable String adminId) {
        return ResponseEntity.ok(groupService.getGroupsByAdminId(adminId));
    }

    @PostMapping
    public ResponseEntity<GroupDTO> createGroup(@RequestBody GroupDTO groupDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(groupService.createGroup(groupDTO));
    }

    @PutMapping("/{id}")
    public ResponseEntity<GroupDTO> updateGroup(@PathVariable String id, @RequestBody GroupDTO groupDTO) {
        return ResponseEntity.ok(groupService.updateGroup(id, groupDTO));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGroup(@PathVariable String id) {
        groupService.deleteGroup(id);
        return ResponseEntity.noContent().build();
    }
}

