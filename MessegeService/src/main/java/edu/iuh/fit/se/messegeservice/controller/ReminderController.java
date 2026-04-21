package edu.iuh.fit.se.messegeservice.controller;

import edu.iuh.fit.se.messegeservice.dto.ReminderDTO;
import edu.iuh.fit.se.messegeservice.service.ReminderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/reminders")
@RequiredArgsConstructor
public class ReminderController {

    private final ReminderService reminderService;

    @PostMapping
    public ResponseEntity<ReminderDTO> createReminder(@RequestBody ReminderDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reminderService.createReminder(dto));
    }

    @GetMapping("/conversation/{conversationId}")
    public ResponseEntity<List<ReminderDTO>> getReminders(@PathVariable String conversationId) {
        return ResponseEntity.ok(reminderService.getReminders(conversationId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReminder(@PathVariable String id) {
        reminderService.deleteReminder(id);
        return ResponseEntity.noContent().build();
    }
}
