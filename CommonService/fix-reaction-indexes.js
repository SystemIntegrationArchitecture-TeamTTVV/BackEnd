// Connect to your database
const db = db.getSiblingDB('ttvv_common');

print('Dropping existing reaction indexes...');
try {
    db.reactions.dropIndex('user_post_idx');
    print('✓ Dropped user_post_idx');
} catch(e) {
    print('✗ Could not drop user_post_idx:', e.message);
}

try {
    db.reactions.dropIndex('user_comment_idx');
    print('✓ Dropped user_comment_idx');
} catch(e) {
    print('✗ Could not drop user_comment_idx:', e.message);
}

print('\nCreating partial indexes...');
// Create partial index for post reactions (only where postId exists and is not null)
db.reactions.createIndex(
    { userId: 1, postId: 1 },
    { 
        name: "user_post_idx",
        unique: true,
        partialFilterExpression: { postId: { $exists: true, $ne: null } }
    }
);
print('✓ Created user_post_idx with partial filter');

// Create partial index for comment reactions (only where commentId exists and is not null)
db.reactions.createIndex(
    { userId: 1, commentId: 1 },
    { 
        name: "user_comment_idx",
        unique: true,
        partialFilterExpression: { commentId: { $exists: true, $ne: null } }
    }
);
print('✓ Created user_comment_idx with partial filter');

print('\nDone! You can now start your application.');
