package edu.iuh.fit.se.commonservice.dto.story;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateStoryRequestDTO {

    private String contentType; // text | image | video
    private String content;     // text (nếu text story)
    private String background;  // optional

}
