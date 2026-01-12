package edu.iuh.fit.se.messegeservice.config;

import edu.iuh.fit.se.messegeservice.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Configuration
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final MongoTemplate mongoTemplate;
    private final Random random = new Random();

    @Value("${app.data.seed.enabled:false}")
    private boolean seedEnabled;

    @Override
    public void run(String... args) {
        // Kiểm tra property để quyết định có seed hay không
        if (!seedEnabled) {
            System.out.println("⚠️  Data seeding is DISABLED. Set 'app.data.seed.enabled=true' to enable.");
            return;
        }

        // Kiểm tra xem đã có data chưa để tránh seed lại
        if (mongoTemplate.getCollection("conversations").countDocuments() > 0) {
            System.out.println("✅ Data already exists. Skipping seed. Set 'app.data.seed.enabled=false' to disable this check.");
            return;
        }

        System.out.println("🌱 Starting data seeding...");

        // participants mock (khớp với CommonService user ids nếu đồng bộ, còn không thì dùng mock)
        var sarah = user("u1", "Sarah Johnson", "https://images.unsplash.com/photo-1544723795-3fb6469f5b39?w=400");
        var mike  = user("u2", "Mike Chen", "https://images.unsplash.com/photo-1521572267360-ee0c2909d518?w=400");
        var emma  = user("u3", "Emma Davis", "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?w=400");

        // Conversation 1: Sarah - Mike
        Conversation c1 = new Conversation();
        c1.setParticipantIds(List.of(sarah.id, mike.id));
        c1.setParticipantNames(List.of(sarah.name, mike.name));
        c1.setParticipantAvatars(List.of(sarah.avatar, mike.avatar));
        c1.setGroup(false);
        c1.setLastMessagePreview("Đi biển nhé! Bạn nghĩ sao? 🌊");
        c1.setLastMessageAt(LocalDateTime.now().minusMinutes(5));
        c1.setCreatedAt(LocalDateTime.now().minusDays(2));
        c1.setUpdatedAt(LocalDateTime.now());

        // Conversation 2: Nhóm 3 người
        Conversation c2 = new Conversation();
        c2.setParticipantIds(List.of(sarah.id, mike.id, emma.id));
        c2.setParticipantNames(List.of(sarah.name, mike.name, emma.name));
        c2.setParticipantAvatars(List.of(sarah.avatar, mike.avatar, emma.avatar));
        c2.setGroup(true);
        c2.setGroupName("Nhóm bạn thân 🎉");
        c2.setGroupAvatar("https://images.unsplash.com/photo-1521737604893-d14cc237f11d?w=400");
        c2.setLastMessagePreview("Tối nay 7h meetup nhé!");
        c2.setLastMessageAt(LocalDateTime.now().minusHours(2));
        c2.setCreatedAt(LocalDateTime.now().minusDays(3));
        c2.setUpdatedAt(LocalDateTime.now());

        mongoTemplate.insertAll(List.of(c1, c2));

        // Messages for c1
        List<Message> messages = new ArrayList<>();
        messages.add(buildMessage(c1, sarah, "Chào bạn! Hôm nay thế nào?\n😊", null));
        messages.add(buildMessage(c1, mike, "Mình rất tốt, cảm ơn bạn!\nCòn bạn thì sao?", null));
        messages.add(buildMessage(c1, sarah, "Tuyệt vời! Cuối tuần này có kế hoạch gì chưa? 🎉", null));
        messages.add(buildMessage(c1, mike, "Chưa có kế hoạch cụ thể!\nMình đang nghĩ đi chơi đâu đó thư giãn 😎",
                null));
        messages.add(buildMessage(c1, sarah, "Đi biển nhé! Bạn nghĩ sao? 🌊",
                List.of(new MessageAttachment("IMAGE",
                        "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=1200",
                        "bien-nha-trang.jpg", 350000, 1600, 900))));
        messages.add(buildMessage(c1, mike, "Đồng ý luôn! Đi đâu vậy bạn?", null));
        messages.add(buildMessage(c1, sarah, "Nha Trang nhé, mình đã book khách sạn rồi 🏖️", null));
        messages.add(buildMessage(c1, mike, "Perfect! Cảm ơn bạn nhiều 😊", null));

        // Messages for c2 (group)
        messages.add(buildMessage(c2, emma, "Tối nay 7h meetup nhé!", null));
        messages.add(buildMessage(c2, sarah, "Ok nha, quán cà phê cũ nhé?", null));
        messages.add(buildMessage(c2, mike, "Mang theo laptop để demo luôn 💻", null));
        messages.add(buildMessage(c2, emma, "Được rồi, mình sẽ chuẩn bị slide!", null));
        messages.add(buildMessage(c2, sarah, "Có ai muốn đi ăn sau meetup không?", null));
        messages.add(buildMessage(c2, mike, "Mình đồng ý! 🍕", null));

        mongoTemplate.insert(messages, Message.class);

        // Reactions
        List<MessageReaction> reactions = new ArrayList<>();
        reactions.add(buildReaction(messages.get(4), mike, "❤️"));
        reactions.add(buildReaction(messages.get(4), emma, "👍"));
        reactions.add(buildReaction(messages.get(1), sarah, "😂"));
        if (messages.size() > 6) {
            reactions.add(buildReaction(messages.get(6), mike, "😊"));
        }
        if (messages.size() > 8) {
            reactions.add(buildReaction(messages.get(8), sarah, "👍"));
        }
        if (messages.size() > 9) {
            reactions.add(buildReaction(messages.get(9), emma, "❤️"));
        }
        if (messages.size() > 11) {
            reactions.add(buildReaction(messages.get(11), mike, "👍"));
        }
        mongoTemplate.insert(reactions, MessageReaction.class);

        // Calls
        List<Call> calls = new ArrayList<>();
        calls.add(buildCall(c1, sarah, List.of(mike.id), "VOICE", "COMPLETED", 420));
        calls.add(buildCall(c1, mike, List.of(sarah.id), "VIDEO", "COMPLETED", 1200));
        calls.add(buildCall(c2, mike, List.of(sarah.id, emma.id), "VIDEO", "MISSED", 0));
        calls.add(buildCall(c2, emma, List.of(sarah.id, mike.id), "VOICE", "COMPLETED", 600));
        mongoTemplate.insert(calls, Call.class);

        System.out.println("✅ Data seeding completed successfully!");
        System.out.println("💡 To disable seeding on next run, set 'app.data.seed.enabled=false' in application.properties");
    }

    private record UserLite(String id, String name, String avatar) {}

    private UserLite user(String id, String name, String avatar) {
        return new UserLite(id, name, avatar);
    }

    private Message buildMessage(Conversation c, UserLite sender, String content, List<MessageAttachment> attachments) {
        Message m = new Message();
        m.setConversation(c);
        m.setConversationId(c.getId());
        m.setSenderId(sender.id);
        m.setSenderName(sender.name);
        m.setSenderAvatar(sender.avatar);
        m.setContent(content);
        m.setAttachments(attachments);
        m.setEmojis(List.of());
        m.setCreatedAt(LocalDateTime.now().minusMinutes(random.nextInt(120)));
        m.setUpdatedAt(m.getCreatedAt());
        return m;
    }

    private MessageReaction buildReaction(Message msg, UserLite user, String emoji) {
        MessageReaction r = new MessageReaction();
        r.setMessageId(msg.getId());
        r.setConversationId(msg.getConversationId());
        r.setUserId(user.id);
        r.setEmoji(emoji);
        r.setCreatedAt(LocalDateTime.now().minusMinutes(10));
        return r;
    }

    private Call buildCall(Conversation c, UserLite caller, List<String> callees, String type, String status, int duration) {
        Call call = new Call();
        call.setConversationId(c.getId());
        call.setCallerId(caller.id);
        call.setCalleeIds(callees);
        call.setType(type);
        call.setStatus(status);
        call.setDurationSeconds(duration);
        call.setStartedAt(LocalDateTime.now().minusHours(6));
        call.setEndedAt(call.getStartedAt().plusSeconds(duration));
        return call;
    }
}

