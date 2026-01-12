package edu.iuh.fit.se.messegeservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageAttachment {
    private String type; // IMAGE, VIDEO, FILE, AUDIO
    private String url;
    private String fileName;
    private long fileSize; // bytes
    private int width;
    private int height;
}

