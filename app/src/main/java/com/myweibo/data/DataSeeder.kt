package com.myweibo.data

import com.myweibo.data.local.entity.IdentityEntity
import com.myweibo.data.local.entity.PostEntity
import com.myweibo.data.repository.WeiboRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.random.Random

object DataSeeder {

    private val historicalFigures = listOf(
        IdentityData("孔子", 0xFF4054B2.toInt(), "儒家思想创始人，万世师表"),
        IdentityData("苏格拉底", 0xFFCE76AC.toInt(), "古希腊哲学家"),
        IdentityData("达芬奇", 0xFF4CAF50.toInt(), "文艺复兴时期艺术家、发明家"),
        IdentityData("李时珍", 0xFF2196F3.toInt(), "明代医药学家"),
        IdentityData("牛顿", 0xFFFF5722.toInt(), "英国物理学家、数学家"),
        IdentityData("苏轼", 0xFF9C27B0.toInt(), "北宋文学家、书画家"),
        IdentityData("居里夫人", 0xFF00BCD4.toInt(), "物理学家、化学家"),
        IdentityData("王阳明", 0xFF795548.toInt(), "明代心学大师"),
        IdentityData("莎士比亚", 0xFF607D8B.toInt(), "英国剧作家、诗人"),
        IdentityData("曹雪芹", 0xFFE91E63.toInt(), "清代小说家")
    )

    private val samplePosts = mapOf(
        "孔子" to listOf(
            "学而时习之，不亦说乎？有朋自远方来，不亦乐乎？",
            "己所不欲，勿施于人。",
            "知之为知之，不知为不知，是知也。",
            "三人行，必有我师焉。择其善者而从之，其不善者而改之。"
        ),
        "苏格拉底" to listOf(
            "我唯一知道的，就是我一无所知。",
            "未经审视的人生不值得过。",
            "认识你自己。",
            "教育不是灌输，而是点燃火焰。"
        ),
        "达芬奇" to listOf(
            "简单是终极的复杂。",
            "艺术家的眼睛是世界的镜子。",
            "学习永远不会晚。",
            "观察是一切理解的基础。"
        ),
        "李时珍" to listOf(
            "医者贵在格物，穷其理而后用药。",
            "药物用之于人，贵在对症。",
            "读万卷书，行万里路。",
            "本草之作，乃为救民疾苦。"
        ),
        "牛顿" to listOf(
            "如果我看得更远，那是因为我站在巨人的肩膀上。",
            "真理是沉默和冥想的果实。",
            "每一个作用力都有一个大小相等方向相反的反作用力。",
            "我并无特别之处，只是用正确的方式看待事物。"
        ),
        "苏轼" to listOf(
            "明月几时有，把酒问青天。",
            "但愿人长久，千里共婵娟。",
            "竹杖芒鞋轻胜马，谁怕？一蓑烟雨任平生。",
            "人生如逆旅，我亦是行人。"
        ),
        "居里夫人" to listOf(
            "生活中没有可怕的东西，只有需要理解的东西。",
            "我以为人在每一个阶段都可以过有趣的生活。",
            "科学的道路上没有平坦的大道。",
            "要坚强，要勇敢，不要让绝望和庸俗的忧愁压倒你。"
        ),
        "王阳明" to listOf(
            "知行合一，知是行的开始，行是知的完成。",
            "此心光明，亦复何言。",
            "致良知，存天理，去人欲。",
            "破山中贼易，破心中贼难。"
        ),
        "莎士比亚" to listOf(
            "生存还是毁灭，这是个问题。",
            "To be, or not to be, that is the question.",
            "人生如梦，一场戏罢了。",
            "黑夜无论怎样悠长，白昼总会到来。"
        ),
        "曹雪芹" to listOf(
            "满纸荒唐言，一把辛酸泪。",
            "都云作者痴，谁解其中味？",
            "假作真时真亦假，无为有处有还无。",
            "世事洞明皆学问，人情练达即文章。"
        )
    )

    fun seedIfEmpty(repository: WeiboRepository) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val existingIdentities = repository.allIdentities.first()
                
                if (existingIdentities.isEmpty()) {
                    val identityMap = mutableMapOf<String, Long>()
                    
                    for ((index, figure) in historicalFigures.withIndex()) {
                        val identity = IdentityEntity(
                            name = figure.name,
                            avatarColor = figure.color,
                            isActive = index == 5
                        )
                        val id = repository.insertIdentity(identity)
                        identityMap[figure.name] = id
                    }

                    for ((name, posts) in samplePosts) {
                        val identityId = identityMap[name]
                        if (identityId != null) {
                            for ((postIndex, content) in posts.withIndex()) {
                                val post = PostEntity(
                                    identityId = identityId,
                                    content = content,
                                    createdAt = System.currentTimeMillis() - (posts.size - postIndex) * 3600000L,
                                    likeCount = Random.nextInt(10, 1000),
                                    commentCount = Random.nextInt(0, 50)
                                )
                                repository.insertPost(post)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private data class IdentityData(
        val name: String,
        val color: Int,
        val description: String
    )
}
