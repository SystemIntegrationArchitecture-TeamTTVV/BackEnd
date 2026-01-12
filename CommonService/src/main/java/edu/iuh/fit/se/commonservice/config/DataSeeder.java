package edu.iuh.fit.se.commonservice.config;

import edu.iuh.fit.se.commonservice.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.math.BigDecimal;
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
        if (mongoTemplate.getCollection("users").countDocuments() > 0) {
            System.out.println("✅ Data already exists. Skipping seed. Set 'app.data.seed.enabled=false' to disable this check.");
            return;
        }

        System.out.println("🌱 Starting data seeding...");

        // ==== Users ====
        List<User> users = seedUsers();
        mongoTemplate.insert(users, User.class);

        // ==== Friends & Friend Requests ====
        seedFriends(users);

        // ==== Groups & Members ====
        List<Group> groups = seedGroups(users);
        mongoTemplate.insert(groups, Group.class);
        seedGroupMembers(groups, users);

        // ==== Pages ====
        List<Page> pages = seedPages(users);
        mongoTemplate.insert(pages, Page.class);
        seedPageLikes(pages, users);

        // ==== Posts ====
        List<Post> posts = seedPosts(users, groups, pages);
        mongoTemplate.insert(posts, Post.class);

        // ==== Comments & Reactions ====
        seedCommentsAndReactions(posts, users);

        // ==== Events & Participants ====
        List<Event> events = seedEvents(users, groups);
        mongoTemplate.insert(events, Event.class);
        seedEventParticipants(events, users);

        // ==== Stories ====
        List<Story> stories = seedStories(users);
        mongoTemplate.insert(stories, Story.class);

        // ==== Albums & Photos ====
        seedAlbumsAndPhotos(users, posts);

        // ==== Saved items ====
        seedSavedItems(users, posts);

        // ==== Products (Marketplace) ====
        List<Product> products = seedProducts(users);
        mongoTemplate.insert(products, Product.class);

        // ==== Notifications ====
        seedNotifications(users, posts);

        // ==== Reports ====
        seedReports(users, posts);

        System.out.println("✅ Data seeding completed successfully!");
        System.out.println("💡 To disable seeding on next run, set 'app.data.seed.enabled=false' in application.properties");
    }

    private List<User> seedUsers() {
        List<User> list = new ArrayList<>();
        list.add(buildUser("sarah.johnson@example.com", "sarahj", "Sarah", "Johnson",
                "https://images.unsplash.com/photo-1544723795-3fb6469f5b39?w=400",
                "Kết nối & chia sẻ cảm hứng du lịch.", "Hà Nội"));
        list.add(buildUser("mike.chen@example.com", "mikechen", "Mike", "Chen",
                "https://images.unsplash.com/photo-1521572267360-ee0c2909d518?w=400",
                "Yêu công nghệ, mê cà phê.", "TP. HCM"));
        list.add(buildUser("emma.davis@example.com", "emmad", "Emma", "Davis",
                "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?w=400",
                "Foodie & travel blogger.", "Đà Nẵng"));
        list.add(buildUser("alex.park@example.com", "alexpark", "Alex", "Park",
                "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=400",
                "Sản phẩm số & startup.", "Hà Nội"));
        list.add(buildUser("lisa.nguyen@example.com", "lisanguyen", "Lisa", "Nguyễn",
                "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?w=400&sat=-50",
                "Designer thích tối giản.", "Cần Thơ"));
        list.add(buildUser("david.kim@example.com", "davidkim", "David", "Kim",
                "https://images.unsplash.com/photo-1521572163474-6864f9cf17ab?w=400",
                "Nhiếp ảnh & leo núi.", "Đà Lạt"));
        list.add(buildUser("maria.garcia@example.com", "mariag", "Maria", "Garcia",
                "https://images.unsplash.com/photo-1544723795-3fb6469f5b39?w=400&sat=-20",
                "Chạy bộ & thiền.", "Hải Phòng"));
        list.add(buildUser("john.doe@example.com", "johnd", "John", "Doe",
                "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?w=400&sat=10",
                "Kỹ sư phần mềm & đọc sách.", "Huế"));
        return list;
    }

    private User buildUser(String email, String username, String first, String last, String avatar, String bio, String city) {
        User u = new User();
        u.setEmail(email);
        u.setUsername(username);
        u.setPassword("{noop}123456"); // placeholder
        u.setFirstName(first);
        u.setLastName(last);
        u.setFullName(first + " " + last);
        u.setAvatar(avatar);
        u.setCoverPhoto("https://images.unsplash.com/photo-1503264116251-35a269479413?w=1200");
        u.setBio(bio);
        u.setCity(city);
        u.setCountry("Việt Nam");
        u.setGender("Khác");
        u.setInterests(List.of("du lịch", "ẩm thực", "công nghệ"));
        u.setCreatedAt(LocalDateTime.now().minusDays(random.nextInt(30)));
        u.setUpdatedAt(LocalDateTime.now());
        return u;
    }

    private void seedFriends(List<User> users) {
        List<Friend> friends = new ArrayList<>();
        // tạo cặp đôi vài người
        linkFriend(users.get(0), users.get(1), friends);
        linkFriend(users.get(0), users.get(2), friends);
        linkFriend(users.get(1), users.get(3), friends);
        linkFriend(users.get(4), users.get(5), friends);
        mongoTemplate.insert(friends, Friend.class);

        List<FriendRequest> requests = new ArrayList<>();
        requests.add(buildFriendRequest(users.get(6), users.get(0), "PENDING"));
        requests.add(buildFriendRequest(users.get(7), users.get(2), "ACCEPTED"));
        mongoTemplate.insert(requests, FriendRequest.class);
    }

    private void linkFriend(User a, User b, List<Friend> list) {
        Friend f1 = new Friend(null, a, a.getId(), b, b.getId(),
                LocalDateTime.now().minusDays(5), LocalDateTime.now());
        Friend f2 = new Friend(null, b, b.getId(), a, a.getId(),
                LocalDateTime.now().minusDays(5), LocalDateTime.now());
        list.add(f1);
        list.add(f2);
    }

    private FriendRequest buildFriendRequest(User sender, User receiver, String status) {
        FriendRequest fr = new FriendRequest();
        fr.setSender(sender);
        fr.setSenderId(sender.getId());
        fr.setReceiver(receiver);
        fr.setReceiverId(receiver.getId());
        fr.setStatus(status);
        fr.setCreatedAt(LocalDateTime.now().minusDays(2));
        fr.setUpdatedAt(LocalDateTime.now());
        return fr;
    }

    private List<Group> seedGroups(List<User> users) {
        List<Group> groups = new ArrayList<>();
        Group travel = new Group(null, "Hội mê du lịch", "Chia sẻ kinh nghiệm phượt & săn vé rẻ",
                "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=1200",
                "https://images.unsplash.com/photo-1503264116251-35a269479413?w=800",
                users.get(0), "PUBLIC", "VISIBLE", 0, 0,
                List.of("travel", "phuot", "review"), "Lifestyle",
                LocalDateTime.now().minusDays(10), LocalDateTime.now(), true);
        Group tech = new Group(null, "Dev & Product Việt", "Cộng đồng chia sẻ công nghệ, sản phẩm số",
                "https://images.unsplash.com/photo-1518770660439-4636190af475?w=1200",
                "https://images.unsplash.com/photo-1519389950473-47ba0277781c?w=800",
                users.get(3), "PRIVATE", "VISIBLE", 0, 0,
                List.of("tech", "dev", "product"), "Technology",
                LocalDateTime.now().minusDays(7), LocalDateTime.now(), true);
        groups.add(travel);
        groups.add(tech);
        return groups;
    }

    private void seedGroupMembers(List<Group> groups, List<User> users) {
        List<GroupMember> members = new ArrayList<>();
        members.add(buildGroupMember(users.get(0), groups.get(0), "ADMIN"));
        members.add(buildGroupMember(users.get(1), groups.get(0), "MEMBER"));
        members.add(buildGroupMember(users.get(2), groups.get(0), "MEMBER"));

        members.add(buildGroupMember(users.get(3), groups.get(1), "ADMIN"));
        members.add(buildGroupMember(users.get(4), groups.get(1), "MODERATOR"));
        members.add(buildGroupMember(users.get(5), groups.get(1), "MEMBER"));
        mongoTemplate.insert(members, GroupMember.class);
    }

    private GroupMember buildGroupMember(User user, Group group, String role) {
        GroupMember gm = new GroupMember();
        gm.setUser(user);
        gm.setUserId(user.getId());
        gm.setGroup(group);
        gm.setGroupId(group.getId());
        gm.setRole(role);
        gm.setStatus("ACTIVE");
        gm.setJoinedAt(LocalDateTime.now().minusDays(3));
        gm.setUpdatedAt(LocalDateTime.now());
        return gm;
    }

    private List<Page> seedPages(List<User> users) {
        List<Page> pages = new ArrayList<>();
        Page cafe = new Page(null, "The Coffee House VN", "thecoffeehouse",
                "Quán cà phê thân thiện, không gian chill.",
                "https://images.unsplash.com/photo-1504674900247-0877df9cc836?w=1200",
                "https://images.unsplash.com/photo-1521017432531-fbd92d768814?w=400",
                users.get(1), "Café & Đồ uống", "https://thecoffeehouse.vn",
                "+84 888 999 111", "contact@tch.vn",
                "86-88 Cao Thắng, Q3", "TP. HCM", "Việt Nam",
                12400, 19800, 560,
                List.of("coffee", "drink", "workspace"), true,
                LocalDateTime.now().minusDays(30), LocalDateTime.now(), true);

        Page gear = new Page(null, "GearHub VN", "gearhubvn",
                "Thiết bị công nghệ & phụ kiện chính hãng.",
                "https://images.unsplash.com/photo-1517336714731-489689fd1ca8?w=1200",
                "https://images.unsplash.com/photo-1526170375885-4d8ecf77b99f?w=400",
                users.get(3), "Shopping & Bán lẻ", "https://gearhub.vn",
                "+84 777 555 222", "hello@gearhub.vn",
                "12 Nguyễn Huệ, Q1", "TP. HCM", "Việt Nam",
                9200, 15300, 410,
                List.of("tech", "laptop", "camera"), false,
                LocalDateTime.now().minusDays(15), LocalDateTime.now(), true);
        pages.add(cafe);
        pages.add(gear);
        return pages;
    }

    private void seedPageLikes(List<Page> pages, List<User> users) {
        List<PageLike> likes = new ArrayList<>();
        // Nhiều user like các page
        for (int i = 0; i < users.size() && i < pages.size(); i++) {
            PageLike pl = new PageLike();
            pl.setUser(users.get(i));
            pl.setUserId(users.get(i).getId());
            pl.setPage(pages.get(i));
            pl.setPageId(pages.get(i).getId());
            pl.setFollowing(true);
            pl.setCreatedAt(LocalDateTime.now().minusDays(random.nextInt(20)));
            likes.add(pl);
        }
        // Thêm một số user khác like page
        if (users.size() > 2 && pages.size() > 0) {
            PageLike pl1 = new PageLike();
            pl1.setUser(users.get(2));
            pl1.setUserId(users.get(2).getId());
            pl1.setPage(pages.get(0));
            pl1.setPageId(pages.get(0).getId());
            pl1.setFollowing(true);
            pl1.setCreatedAt(LocalDateTime.now().minusDays(5));
            likes.add(pl1);
        }
        if (users.size() > 4 && pages.size() > 1) {
            PageLike pl2 = new PageLike();
            pl2.setUser(users.get(4));
            pl2.setUserId(users.get(4).getId());
            pl2.setPage(pages.get(1));
            pl2.setPageId(pages.get(1).getId());
            pl2.setFollowing(true);
            pl2.setCreatedAt(LocalDateTime.now().minusDays(3));
            likes.add(pl2);
        }
        mongoTemplate.insert(likes, PageLike.class);
    }

    private List<Post> seedPosts(List<User> users, List<Group> groups, List<Page> pages) {
        List<Post> posts = new ArrayList<>();
        posts.add(buildPost(users.get(0), "Vừa săn được vé đi Đà Lạt 299k/chiều, ai đi cùng không? 🌲",
                List.of("https://images.unsplash.com/photo-1501785888041-af3ef285b470?w=1200"),
                "Đà Lạt, Lâm Đồng", null, null, null));
        posts.add(buildPost(users.get(1), "Test MacBook Air M3 cả ngày pin vẫn trâu 😎", List.of(), "",
                null, null, pages.get(1)));
        posts.add(buildPost(users.get(2), "Món phở gà này ngon xuất sắc, quán ngay hồ Gươm!", List.of(
                "https://images.unsplash.com/photo-1504674900247-0877df9cc836?w=1200",
                "https://images.unsplash.com/photo-1467003909585-2f8a72700288?w=1200"),
                "Hà Nội", "Hạnh phúc", "Ăn uống", null));
        posts.add(buildPost(users.get(3), "Checklist launch MVP: user flow, tracking, support!", List.of(), "",
                null, "Làm việc", groups.get(1)));
        posts.add(buildPost(users.get(4), "Template poster mới, mọi người góp ý nhé 💙", List.of(
                "https://images.unsplash.com/photo-1521737604893-d14cc237f11d?w=1200"),
                "", "Hào hứng", "Thiết kế", null));
        posts.add(buildPost(users.get(5), "Chạy bộ sáng nay ở công viên, không khí trong lành quá! 🏃", List.of(
                "https://images.unsplash.com/photo-1544367567-0f2fcb009e0b?w=1200"),
                "Công viên Lê Văn Tám", "Năng động", "Tập thể dục", null));
        posts.add(buildPost(users.get(6), "Review quán cà phê mới mở, view đẹp lắm! ☕", List.of(
                "https://images.unsplash.com/photo-1504674900247-0877df9cc836?w=1200",
                "https://images.unsplash.com/photo-1521017432531-fbd92d768814?w=1200"),
                "Quận 1, TP.HCM", "Hạnh phúc", "Ăn uống", null));
        posts.add(buildPost(users.get(7), "Đọc sách cuối tuần, recommend cuốn này cho mọi người 📚", List.of(),
                "", "Bình yên", "Đọc sách", null));
        return posts;
    }

    private Post buildPost(User author, String content, List<String> images, String location,
                           String feeling, String activity, Object scope) {
        Post p = new Post();
        p.setAuthor(author);
        p.setContent(content);
        p.setImages(images);
        p.setLocation(location);
        p.setFeeling(feeling);
        p.setActivity(activity);
        if (scope instanceof Group g) {
            p.setGroup(g);
            p.setVisibility("GROUP");
        }
        if (scope instanceof Page pg) {
            p.setPage(pg);
            p.setVisibility("PUBLIC");
        }
        p.setCreatedAt(LocalDateTime.now().minusDays(random.nextInt(5)));
        p.setUpdatedAt(LocalDateTime.now());
        // Update post counts
        p.setLikeCount(random.nextInt(50) + 5);
        p.setCommentCount(random.nextInt(20) + 2);
        p.setShareCount(random.nextInt(10));
        return p;
    }

    private void seedCommentsAndReactions(List<Post> posts, List<User> users) {
        List<Comment> comments = new ArrayList<>();
        Comment c1 = new Comment(null, posts.get(0), users.get(1),
                "Cho xin lịch trình với bạn ơi!", List.of(), null,
                3, 1, LocalDateTime.now().minusHours(5), LocalDateTime.now(), null, false);
        Comment c2 = new Comment(null, posts.get(0), users.get(2),
                "Đi Đà Lạt nhớ mang áo ấm nha 🧥", List.of(), c1,
                1, 0, LocalDateTime.now().minusHours(3), LocalDateTime.now(), null, false);
        comments.add(c1);
        comments.add(c2);
        // Comments cho post 2
        comments.add(new Comment(null, posts.get(2), users.get(0),
                "Nhìn ngon quá, tối nay phải thử!", List.of(), null,
                2, 0, LocalDateTime.now().minusHours(4), LocalDateTime.now(), null, false));
        comments.add(new Comment(null, posts.get(2), users.get(3),
                "Quán này ở đâu vậy bạn?", List.of(), null,
                1, 0, LocalDateTime.now().minusHours(2), LocalDateTime.now(), null, false));
        // Comments cho post 3
        comments.add(new Comment(null, posts.get(3), users.get(1),
                "Checklist này hay đấy, mình sẽ áp dụng!", List.of(), null,
                0, 0, LocalDateTime.now().minusHours(1), LocalDateTime.now(), null, false));
        // Comments cho post 5
        if (posts.size() > 5) {
            comments.add(new Comment(null, posts.get(5), users.get(0),
                    "Chạy bộ buổi sáng là cách tốt nhất để bắt đầu ngày mới! 💪", List.of(), null,
                    0, 0, LocalDateTime.now().minusMinutes(30), LocalDateTime.now(), null, false));
        }
        mongoTemplate.insert(comments, Comment.class);

        List<Reaction> reactions = new ArrayList<>();
        // Reactions cho posts
        reactions.add(buildReaction(users.get(3), posts.get(0), null, "LOVE"));
        reactions.add(buildReaction(users.get(4), posts.get(0), null, "LIKE"));
        reactions.add(buildReaction(users.get(5), posts.get(0), null, "HAHA"));
        reactions.add(buildReaction(users.get(6), posts.get(0), null, "WOW"));
        reactions.add(buildReaction(users.get(5), posts.get(1), null, "LIKE"));
        reactions.add(buildReaction(users.get(0), posts.get(1), null, "LOVE"));
        reactions.add(buildReaction(users.get(2), posts.get(2), null, "LOVE"));
        reactions.add(buildReaction(users.get(4), posts.get(2), null, "LIKE"));
        reactions.add(buildReaction(users.get(1), posts.get(3), null, "LIKE"));
        reactions.add(buildReaction(users.get(2), posts.get(3), null, "LOVE"));
        reactions.add(buildReaction(users.get(0), posts.get(4), null, "LIKE"));
        if (posts.size() > 5) {
            reactions.add(buildReaction(users.get(1), posts.get(5), null, "LIKE"));
            reactions.add(buildReaction(users.get(3), posts.get(5), null, "LOVE"));
        }
        if (posts.size() > 6) {
            reactions.add(buildReaction(users.get(4), posts.get(6), null, "LIKE"));
        }
        if (posts.size() > 7) {
            reactions.add(buildReaction(users.get(5), posts.get(7), null, "LIKE"));
        }
        // Reactions cho comments
        reactions.add(buildReaction(users.get(2), null, comments.get(0), "HAHA"));
        if (comments.size() > 2) {
            reactions.add(buildReaction(users.get(0), null, comments.get(2), "LIKE"));
        }
        if (comments.size() > 3) {
            reactions.add(buildReaction(users.get(1), null, comments.get(3), "LIKE"));
        }
        mongoTemplate.insert(reactions, Reaction.class);
    }

    private Reaction buildReaction(User user, Post post, Comment comment, String type) {
        Reaction r = new Reaction();
        r.setUser(user);
        r.setUserId(user.getId());
        r.setType(type);
        r.setPost(post);
        r.setPostId(post != null ? post.getId() : null);
        r.setComment(comment);
        r.setCommentId(comment != null ? comment.getId() : null);
        r.setCreatedAt(LocalDateTime.now().minusHours(2));
        return r;
    }

    private List<Event> seedEvents(List<User> users, List<Group> groups) {
        List<Event> events = new ArrayList<>();
        events.add(buildEvent("Coffee meetup Hà Nội", users.get(0), groups.get(0),
                LocalDateTime.now().plusDays(3).withHour(9),
                LocalDateTime.now().plusDays(3).withHour(11),
                "Trill Rooftop, Hai Bà Trưng", "Hà Nội", "Việt Nam",
                "PUBLIC", List.of("meetup", "coffee"), "Social"));

        events.add(buildEvent("Tech sharing: Build MVP nhanh", users.get(3), groups.get(1),
                LocalDateTime.now().plusDays(5).withHour(19),
                LocalDateTime.now().plusDays(5).withHour(21),
                "Toong coworking, Q1", "TP. HCM", "Việt Nam",
                "PRIVATE", List.of("tech", "startup"), "Technology"));
        return events;
    }

    private Event buildEvent(String name, User host, Group group,
                             LocalDateTime start, LocalDateTime end,
                             String address, String city, String country,
                             String privacy, List<String> tags, String category) {
        Event e = new Event();
        e.setName(name);
        e.setDescription("Sự kiện kết nối & chia sẻ kinh nghiệm.");
        e.setCoverPhoto("https://images.unsplash.com/photo-1521737604893-d14cc237f11d?w=1200");
        e.setHost(host);
        e.setGroup(group);
        e.setStartTime(start);
        e.setEndTime(end);
        e.setAddress(address);
        e.setCity(city);
        e.setCountry(country);
        e.setPrivacy(privacy);
        e.setTags(tags);
        e.setCategory(category);
        e.setParticipantCount(random.nextInt(50) + 10);
        e.setInterestedCount(random.nextInt(80) + 20);
        e.setCreatedAt(LocalDateTime.now().minusDays(4));
        e.setUpdatedAt(LocalDateTime.now());
        return e;
    }

    private void seedEventParticipants(List<Event> events, List<User> users) {
        List<EventParticipant> parts = new ArrayList<>();
        parts.add(buildEventParticipant(users.get(0), events.get(0), "GOING"));
        parts.add(buildEventParticipant(users.get(1), events.get(0), "INTERESTED"));
        parts.add(buildEventParticipant(users.get(2), events.get(0), "GOING"));

        parts.add(buildEventParticipant(users.get(3), events.get(1), "GOING"));
        parts.add(buildEventParticipant(users.get(4), events.get(1), "INTERESTED"));
        parts.add(buildEventParticipant(users.get(5), events.get(1), "GOING"));
        mongoTemplate.insert(parts, EventParticipant.class);
    }

    private EventParticipant buildEventParticipant(User user, Event event, String status) {
        EventParticipant ep = new EventParticipant();
        ep.setUser(user);
        ep.setUserId(user.getId());
        ep.setEvent(event);
        ep.setEventId(event.getId());
        ep.setStatus(status);
        ep.setJoinedAt(LocalDateTime.now().minusDays(1));
        ep.setUpdatedAt(LocalDateTime.now());
        return ep;
    }

    private List<Story> seedStories(List<User> users) {
        List<Story> stories = new ArrayList<>();
        stories.add(buildStory(users.get(0), "IMAGE", "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?w=900", null));
        stories.add(buildStory(users.get(2), "IMAGE", "https://images.unsplash.com/photo-1504674900247-0877df9cc836?w=900", null));
        stories.add(buildStory(users.get(5), "VIDEO", "https://videos.pexels.com/video-files/856861/856861-hd_1920_1080_25fps.mp4",
                "https://images.unsplash.com/photo-1503264116251-35a269479413?w=600"));
        return stories;
    }

    private Story buildStory(User author, String type, String mediaUrl, String thumb) {
        Story s = new Story();
        s.setAuthor(author);
        s.setType(type);
        s.setMediaUrl(mediaUrl);
        s.setThumbnailUrl(thumb);
        s.setText("Ngày mới vui vẻ!");
        s.setBackgroundColor("#1877F2");
        s.setVisibility("FRIENDS");
        s.setViewCount(random.nextInt(300));
        s.setReactionCount(random.nextInt(50));
        s.setCreatedAt(LocalDateTime.now().minusHours(6));
        s.setExpiresAt(LocalDateTime.now().plusHours(18));
        s.setActive(true);
        return s;
    }

    private void seedAlbumsAndPhotos(List<User> users, List<Post> posts) {
        List<Album> albums = new ArrayList<>();
        Album a1 = new Album(null, "Ký ức Đà Lạt", "Album chuyến đi Đà Lạt 2026",
                users.get(0), null, 0, "FRIENDS",
                LocalDateTime.now().minusDays(12), LocalDateTime.now());
        Album a2 = new Album(null, "Food tour Hà Nội", "Những món ăn must-try",
                users.get(2), null, 0, "PUBLIC",
                LocalDateTime.now().minusDays(8), LocalDateTime.now());
        albums.add(a1);
        albums.add(a2);
        mongoTemplate.insert(albums, Album.class);

        List<Photo> photos = new ArrayList<>();
        photos.add(buildPhoto("https://images.unsplash.com/photo-1501785888041-af3ef285b470?w=1200", a1, posts.get(0), users.get(0)));
        photos.add(buildPhoto("https://images.unsplash.com/photo-1469474968028-56623f02e42e?w=1200", a1, null, users.get(0)));
        photos.add(buildPhoto("https://images.unsplash.com/photo-1504674900247-0877df9cc836?w=1200", a2, posts.get(2), users.get(2)));
        photos.add(buildPhoto("https://images.unsplash.com/photo-1504674900247-0877df9cc836?w=1200&sat=-30", a2, null, users.get(2)));
        mongoTemplate.insert(photos, Photo.class);
    }

    private Photo buildPhoto(String url, Album album, Post post, User owner) {
        Photo p = new Photo();
        p.setUrl(url);
        p.setThumbnailUrl(url + "&w=400");
        p.setCaption("Ảnh kỷ niệm");
        p.setOwner(owner);
        p.setAlbum(album);
        p.setPost(post);
        p.setWidth(1600);
        p.setHeight(900);
        p.setFileSize(350_000);
        p.setCreatedAt(LocalDateTime.now().minusDays(1));
        p.setUpdatedAt(LocalDateTime.now());
        return p;
    }

    private void seedSavedItems(List<User> users, List<Post> posts) {
        List<SavedItem> saved = new ArrayList<>();
        saved.add(buildSaved(users.get(0), posts.get(0).getId(), "POST", "Travel"));
        saved.add(buildSaved(users.get(1), posts.get(2).getId(), "POST", "Ẩm thực"));
        saved.add(buildSaved(users.get(2), posts.get(1).getId(), "POST", "Công nghệ"));
        saved.add(buildSaved(users.get(3), posts.get(3).getId(), "POST", "Startup"));
        saved.add(buildSaved(users.get(4), posts.get(5).getId(), "POST", "Sức khỏe"));
        if (posts.size() > 6) {
            saved.add(buildSaved(users.get(5), posts.get(6).getId(), "POST", "Ẩm thực"));
        }
        if (posts.size() > 7) {
            saved.add(buildSaved(users.get(6), posts.get(7).getId(), "POST", "Sách"));
        }
        mongoTemplate.insert(saved, SavedItem.class);
    }

    private SavedItem buildSaved(User user, String itemId, String type, String collection) {
        SavedItem s = new SavedItem();
        s.setUser(user);
        s.setUserId(user.getId());
        s.setItemId(itemId);
        s.setItemType(type);
        s.setCollection(collection);
        s.setSavedAt(LocalDateTime.now());
        return s;
    }

    private List<Product> seedProducts(List<User> users) {
        List<Product> products = new ArrayList<>();
        products.add(buildProduct(users.get(1), "MacBook Air M3 13 inch", new BigDecimal("24990000"),
                "Như mới 99%, full box.", "Electronics",
                List.of(
                        "https://images.unsplash.com/photo-1517336714731-489689fd1ca8?w=1200",
                        "https://images.unsplash.com/photo-1517336714731-489689fd1ca8?w=800"
                )));
        products.add(buildProduct(users.get(4), "iPhone 15 Pro 256GB", new BigDecimal("27990000"),
                "Hàng chính hãng VN/A, bảo hành 11 tháng.", "Electronics",
                List.of("https://images.unsplash.com/photo-1512499617640-c2f999098c01?w=1200")));
        products.add(buildProduct(users.get(5), "Xe đạp road Giant", new BigDecimal("8500000"),
                "Đạp mượt, mới bảo dưỡng.", "Vehicles",
                List.of("https://images.unsplash.com/photo-1502877828070-33c90eec2827?w=1200")));
        products.add(buildProduct(users.get(2), "Căn hộ studio Q7", new BigDecimal("820000000"),
                "35m2, full nội thất, view sông.", "Property",
                List.of("https://images.unsplash.com/photo-1505691938895-1758d7feb511?w=1200")));
        return products;
    }

    private Product buildProduct(User seller, String title, BigDecimal price, String desc,
                                 String category, List<String> images) {
        Product p = new Product();
        p.setSeller(seller);
        p.setTitle(title);
        p.setPrice(price);
        p.setDescription(desc);
        p.setCondition("Như mới");
        p.setLocation("TP. HCM");
        p.setAddress("Quận 1");
        p.setCategory(category);
        p.setImages(images);
        p.setTags(List.of("mua-ban", "marketplace"));
        p.setCurrency("VND");
        p.setCreatedAt(LocalDateTime.now().minusDays(2));
        p.setUpdatedAt(LocalDateTime.now());
        return p;
    }

    private void seedNotifications(List<User> users, List<Post> posts) {
        List<Notification> notis = new ArrayList<>();
        notis.add(buildNoti(users.get(0), users.get(1), "LIKE_POST",
                "Mike đã thích bài viết của bạn", posts.get(0).getId(), "POST"));
        notis.add(buildNoti(users.get(0), users.get(2), "COMMENT_POST",
                "Emma đã bình luận bài viết của bạn", posts.get(0).getId(), "POST"));
        notis.add(buildNoti(users.get(2), users.get(0), "COMMENT_POST",
                "Sarah đã bình luận bài của bạn", posts.get(2).getId(), "POST"));
        notis.add(buildNoti(users.get(2), users.get(3), "COMMENT_POST",
                "Alex đã bình luận bài của bạn", posts.get(2).getId(), "POST"));
        notis.add(buildNoti(users.get(3), users.get(4), "FRIEND_REQUEST",
                "Lisa đã gửi lời mời kết bạn", users.get(3).getId(), "USER"));
        notis.add(buildNoti(users.get(1), users.get(0), "LIKE_POST",
                "Sarah đã thích bài viết của bạn", posts.get(1).getId(), "POST"));
        if (posts.size() > 4) {
            notis.add(buildNoti(users.get(4), users.get(5), "LIKE_POST",
                    "David đã thích bài viết của bạn", posts.get(4).getId(), "POST"));
        }
        if (posts.size() > 5) {
            notis.add(buildNoti(users.get(5), users.get(1), "COMMENT_POST",
                    "Mike đã bình luận bài của bạn", posts.get(5).getId(), "POST"));
        }
        if (posts.size() > 6) {
            notis.add(buildNoti(users.get(6), users.get(2), "LIKE_POST",
                    "Emma đã thích bài viết của bạn", posts.get(6).getId(), "POST"));
        }
        mongoTemplate.insert(notis, Notification.class);
    }

    private Notification buildNoti(User recipient, User actor, String type, String title, String relatedId, String relatedType) {
        Notification n = new Notification();
        n.setRecipient(recipient);
        n.setRecipientId(recipient.getId());
        n.setActor(actor);
        n.setActorId(actor.getId());
        n.setType(type);
        n.setTitle(title);
        n.setContent(title);
        n.setRelatedId(relatedId);
        n.setRelatedType(relatedType);
        n.setImage(actor.getAvatar());
        n.setRead(false);
        n.setCreatedAt(LocalDateTime.now().minusHours(2));
        return n;
    }

    private void seedReports(List<User> users, List<Post> posts) {
        List<Report> reports = new ArrayList<>();
        reports.add(buildReport(users.get(6), posts.get(1).getId(), "POST", "Nội dung spam"));
        if (users.size() > 7) {
            reports.add(buildReport(users.get(5), users.get(7).getId(), "USER", "Tài khoản giả mạo"));
        }
        if (posts.size() > 3) {
            reports.add(buildReport(users.get(2), posts.get(3).getId(), "POST", "Nội dung không phù hợp"));
        }
        mongoTemplate.insert(reports, Report.class);
    }

    private Report buildReport(User reporter, String targetId, String targetType, String reason) {
        Report r = new Report();
        r.setReporter(reporter);
        r.setReporterId(reporter.getId());
        r.setTargetId(targetId);
        r.setTargetType(targetType);
        r.setReason(reason);
        r.setDetail("Vui lòng kiểm tra nội dung này.");
        r.setStatus("OPEN");
        r.setCreatedAt(LocalDateTime.now().minusDays(1));
        r.setUpdatedAt(LocalDateTime.now());
        return r;
    }
}

