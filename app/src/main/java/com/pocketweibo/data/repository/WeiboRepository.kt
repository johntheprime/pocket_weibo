package com.pocketweibo.data.repository

import com.pocketweibo.data.local.dao.CommentDao
import com.pocketweibo.data.local.dao.CommentWithIdentity
import com.pocketweibo.data.local.dao.IdentityDao
import com.pocketweibo.data.local.dao.PostDao
import com.pocketweibo.data.local.dao.PostWithIdentity
import com.pocketweibo.data.local.entity.CommentEntity
import com.pocketweibo.data.local.entity.IdentityEntity
import com.pocketweibo.data.local.entity.PostEntity
import kotlinx.coroutines.flow.Flow

class WeiboRepository(
    private val identityDao: IdentityDao,
    private val postDao: PostDao,
    private val commentDao: CommentDao
) {
    val allIdentities: Flow<List<IdentityEntity>> = identityDao.getAllIdentities()
    val activeIdentity: Flow<IdentityEntity?> = identityDao.getActiveIdentity()
    val allPosts: Flow<List<PostWithIdentity>> = postDao.getAllPosts()

    suspend fun getIdentityById(id: Long): IdentityEntity? = identityDao.getIdentityById(id)

    suspend fun insertIdentity(identity: IdentityEntity): Long = identityDao.insert(identity)

    suspend fun updateIdentity(identity: IdentityEntity) = identityDao.update(identity)

    suspend fun deleteIdentity(identity: IdentityEntity) = identityDao.delete(identity)

    suspend fun setActiveIdentity(id: Long) {
        identityDao.deactivateAll()
        identityDao.activate(id)
    }

    suspend fun insertPost(post: PostEntity): Long = postDao.insert(post)

    suspend fun deletePost(post: PostEntity) = postDao.delete(post)

    suspend fun togglePostLike(postId: Long) = postDao.toggleLike(postId)

    fun getCommentsByPost(postId: Long): Flow<List<CommentWithIdentity>> =
        commentDao.getCommentsByPost(postId)

    suspend fun insertComment(comment: CommentEntity): Long {
        commentDao.insert(comment)
        postDao.incrementCommentCount(comment.postId)
        return comment.id
    }

    suspend fun deleteComment(comment: CommentEntity) = commentDao.delete(comment)

    fun getPostsByIdentity(identityId: Long): Flow<List<PostWithIdentity>> =
        postDao.getPostsByIdentity(identityId)
}
