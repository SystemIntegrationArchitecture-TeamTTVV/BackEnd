package edu.iuh.fit.se.messegeservice.config;

import edu.iuh.fit.se.messegeservice.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Seed dữ liệu mẫu khi app khởi động (bật bằng app.data.seed.enabled=true).
 * Dùng Component thay vì Configuration để tránh lỗi CGLIB với record phụ (SeedUserRef).
 */
@Component
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
        var lan = user("u1", "Nguyễn Thị Lan", "https://picsum.photos/400/400?random=70");
        var minh  = user("u2", "Trần Văn Minh", "https://picsum.photos/400/400?random=71");
        var hong  = user("u3", "Lê Thị Hồng", "https://picsum.photos/400/400?random=72");

        // Conversation 1: Lan - Minh
        Conversation c1 = new Conversation();
        c1.setParticipantIds(List.of(lan.id(), minh.id()));
        c1.setParticipantNames(List.of(lan.name(), minh.name()));
        c1.setParticipantAvatars(List.of(lan.avatar(), minh.avatar()));
        c1.setGroup(false);
        c1.setLastMessagePreview("Đi biển nhé! Bạn nghĩ sao? 🌊");
        c1.setLastMessageAt(LocalDateTime.now().minusMinutes(5));
        c1.setCreatedAt(LocalDateTime.now().minusDays(2));
        c1.setUpdatedAt(LocalDateTime.now());

        // Conversation 2: Nhóm 3 người
        Conversation c2 = new Conversation();
        c2.setParticipantIds(List.of(lan.id(), minh.id(), hong.id()));
        c2.setParticipantNames(List.of(lan.name(), minh.name(), hong.name()));
        c2.setParticipantAvatars(List.of(lan.avatar(), minh.avatar(), hong.avatar()));
        c2.setGroup(true);
        c2.setGroupName("Nhóm bạn thân 🎉");
        c2.setGroupAvatar("https://picsum.photos/400/400?random=73");
        c2.setLastMessagePreview("Tối nay 7h meetup nhé!");
        c2.setLastMessageAt(LocalDateTime.now().minusHours(2));
        c2.setCreatedAt(LocalDateTime.now().minusDays(3));
        c2.setUpdatedAt(LocalDateTime.now());

        mongoTemplate.insertAll(List.of(c1, c2));

        // Messages for c1
        List<Message> messages = new ArrayList<>();
        messages.add(buildMessage(c1, lan, "Chào bạn! Hôm nay thế nào?\n😊", null));
        messages.add(buildMessage(c1, minh, "Mình rất tốt, cảm ơn bạn!\nCòn bạn thì sao?", null));
        messages.add(buildMessage(c1, lan, "Tuyệt vời! Cuối tuần này có kế hoạch gì chưa? 🎉", null));
        messages.add(buildMessage(c1, minh, "Chưa có kế hoạch cụ thể!\nMình đang nghĩ đi chơi đâu đó thư giãn 😎",
                null));
        messages.add(buildMessage(c1, lan, "Đi biển nhé! Bạn nghĩ sao? 🌊",
                List.of(new MessageAttachment("IMAGE",
                        "https://picsum.photos/1200/900?random=80",
                        "bien-nha-trang.jpg", 350000L, 1600, 900))));
        messages.add(buildMessage(c1, minh, "Đồng ý luôn! Đi đâu vậy bạn?", null));
        messages.add(buildMessage(c1, lan, "Nha Trang nhé, mình đã đặt khách sạn rồi 🏖️", null));
        messages.add(buildMessage(c1, minh, "Tuyệt vời! Cảm ơn bạn nhiều 😊", null));

        // Messages for c2 (group)
        messages.add(buildMessage(c2, hong, "Tối nay 7h meetup nhé!", null));
        messages.add(buildMessage(c2, lan, "Ok nha, quán cà phê cũ nhé?", null));
        messages.add(buildMessage(c2, minh, "Mang theo laptop để demo luôn 💻", null));
        messages.add(buildMessage(c2, hong, "Được rồi, mình sẽ chuẩn bị slide!", null));
        messages.add(buildMessage(c2, lan, "Có ai muốn đi ăn sau meetup không?", null));
        messages.add(buildMessage(c2, minh, "Mình đồng ý! 🍕", null));

        mongoTemplate.insert(messages, Message.class);

        // Reactions
        List<MessageReaction> reactions = new ArrayList<>();
        reactions.add(buildReaction(messages.get(4), minh, "❤️"));
        reactions.add(buildReaction(messages.get(4), hong, "👍"));
        reactions.add(buildReaction(messages.get(1), lan, "😂"));
        if (messages.size() > 6) {
            reactions.add(buildReaction(messages.get(6), minh, "😊"));
        }
        if (messages.size() > 8) {
            reactions.add(buildReaction(messages.get(8), lan, "👍"));
        }
        if (messages.size() > 9) {
            reactions.add(buildReaction(messages.get(9), hong, "❤️"));
        }
        if (messages.size() > 11) {
            reactions.add(buildReaction(messages.get(11), minh, "👍"));
        }
        mongoTemplate.insert(reactions, MessageReaction.class);

        // Calls
        List<Call> calls = new ArrayList<>();
        calls.add(buildCall(c1, lan, List.of(minh.id()), "VOICE", "COMPLETED", 420));
        calls.add(buildCall(c1, minh, List.of(lan.id()), "VIDEO", "COMPLETED", 1200));
        calls.add(buildCall(c2, minh, List.of(lan.id(), hong.id()), "VIDEO", "MISSED", 0));
        calls.add(buildCall(c2, hong, List.of(lan.id(), minh.id()), "VOICE", "COMPLETED", 600));
        mongoTemplate.insert(calls, Call.class);

        System.out.println("✅ Data seeding completed successfully!");
        System.out.println("💡 To disable seeding on next run, set 'app.data.seed.enabled=false' in application.properties");
    }

    private SeedUserRef user(String id, String name, String avatar) {
        return new SeedUserRef(id, name, avatar);
    }

    private Message buildMessage(Conversation c, SeedUserRef sender, String content, List<MessageAttachment> attachments) {
        Message m = new Message();
        m.setConversation(c);
        m.setConversationId(c.getId());
        m.setSenderId(sender.id());
        m.setSenderName(sender.name());
        m.setSenderAvatar(sender.avatar());
        m.setContent(content);
        m.setAttachments(attachments);
        m.setEmojis(List.of());
        m.setCreatedAt(LocalDateTime.now().minusMinutes(random.nextInt(120)));
        m.setUpdatedAt(m.getCreatedAt());
        return m;
    }

    private MessageReaction buildReaction(Message msg, SeedUserRef user, String emoji) {
        MessageReaction r = new MessageReaction();
        r.setMessageId(msg.getId());
        r.setConversationId(msg.getConversationId());
        r.setUserId(user.id());
        r.setEmoji(emoji);
        r.setCreatedAt(LocalDateTime.now().minusMinutes(10));
        return r;
    }

    private Call buildCall(Conversation c, SeedUserRef caller, List<String> callees, String type, String status, int duration) {
        Call call = new Call();
        call.setConversationId(c.getId());
        call.setCallerId(caller.id());
        call.setCalleeIds(callees);
        call.setType(type);
        call.setStatus(status);
        call.setDurationSeconds(duration);
        call.setStartedAt(LocalDateTime.now().minusHours(6));
        call.setEndedAt(call.getStartedAt().plusSeconds(duration));
        return call;
    }
}