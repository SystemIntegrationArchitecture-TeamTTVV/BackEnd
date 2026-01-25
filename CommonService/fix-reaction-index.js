// Fix reaction indexes to support both post and comment reactions
use('ttvv_common');

// Drop old index
try {
    db.reactions.dropIndex('user_post_idx');
    print('✅ Dropped old user_post_idx');
} catch (e) {
    print('⚠️ Index user_post_idx not found or already dropped');
}

// Create sparse indexes for post reactions
db.reactions.createIndex(
    { userId: 1, postId: 1 }, 
    { unique: true, sparse: true, name: 'user_post_idx' }
);
print('✅ Created user_post_idx with sparse option');

// Create sparse indexes for comment reactions
db.reactions.createIndex(
    { userId: 1, commentId: 1 }, 
    { unique: true, sparse: true, name: 'user_comment_idx' }
);
print('✅ Created user_comment_idx with sparse option');

// Show all indexes
print('\n📋 Current indexes:');
db.reactions.getIndexes().forEach(idx => {
    print(`  - ${idx.name}: ${JSON.stringify(idx.key)}, unique: ${idx.unique || false}, sparse: ${idx.sparse || false}`);
});
