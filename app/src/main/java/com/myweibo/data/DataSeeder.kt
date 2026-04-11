package com.myweibo.data

import com.myweibo.data.local.entity.Gender
import com.myweibo.data.local.entity.IdentityEntity
import com.myweibo.data.local.entity.PostEntity
import com.myweibo.data.repository.WeiboRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object DataSeeder {

    private val historicalFigures = listOf(
        IdentityFullData(
            name = "孔子",
            avatarResName = "avatar_chinese_scholar",
            nationality = "中国·鲁国",
            gender = Gender.MALE,
            birthYear = -551,
            deathYear = -479,
            occupation = "思想家、教育家",
            motto = "己所不欲，勿施于人。",
            famousWork = "《论语》",
            bio = "儒家学派创始人，万世师表，开创私学之先河。"
        ),
        IdentityFullData(
            name = "苏格拉底",
            avatarResName = "avatar_western",
            nationality = "古希腊·雅典",
            gender = Gender.MALE,
            birthYear = -470,
            deathYear = -399,
            occupation = "哲学家",
            motto = "我唯一知道的，就是我一无所知。",
            famousWork = "苏格拉底式问答法",
            bio = "古希腊哲学家，西方哲学的奠基人之一。"
        ),
        IdentityFullData(
            name = "达芬奇",
            avatarResName = "avatar_artist",
            nationality = "意大利·芬奇镇",
            gender = Gender.MALE,
            birthYear = 1452,
            deathYear = 1519,
            occupation = "艺术家、发明家、科学家",
            motto = "简单是终极的复杂。",
            famousWork = "《蒙娜丽莎》《最后的晚餐》",
            bio = "文艺复兴时期最完美的代表，人类历史上绝无仅有的全才。"
        ),
        IdentityFullData(
            name = "李时珍",
            avatarResName = "avatar_chinese_scholar",
            nationality = "中国·明朝",
            gender = Gender.MALE,
            birthYear = 1518,
            deathYear = 1593,
            occupation = "医药学家",
            motto = "医者贵在格物，穷其理而后用药。",
            famousWork = "《本草纲目》",
            bio = "明代医药学家，著有《本草纲目》等传世之作。"
        ),
        IdentityFullData(
            name = "牛顿",
            avatarResName = "avatar_scientist",
            nationality = "英国·伍尔斯索普",
            gender = Gender.MALE,
            birthYear = 1643,
            deathYear = 1727,
            occupation = "物理学家、数学家",
            motto = "如果我看得更远，那是因为我站在巨人的肩膀上。",
            famousWork = "《自然哲学的数学原理》",
            bio = "经典力学体系的创立者，万有引力定律的发现者。"
        ),
        IdentityFullData(
            name = "苏轼",
            avatarResName = "avatar_chinese_scholar",
            nationality = "中国·北宋",
            gender = Gender.MALE,
            birthYear = 1037,
            deathYear = 1101,
            occupation = "文学家、书画家",
            motto = "竹杖芒鞋轻胜马，谁怕？一蓑烟雨任平生。",
            famousWork = "《赤壁赋》《水调歌头》",
            bio = "北宋文学家，唐宋八大家之一，诗词书画俱佳。"
        ),
        IdentityFullData(
            name = "居里夫人",
            avatarResName = "avatar_female_scholar",
            nationality = "波兰·法国",
            gender = Gender.FEMALE,
            birthYear = 1867,
            deathYear = 1934,
            occupation = "物理学家、化学家",
            motto = "生活中没有可怕的东西，只有需要理解的东西。",
            famousWork = "发现镭和钋元素",
            bio = "两次获得诺贝尔奖的女性科学家。"
        ),
        IdentityFullData(
            name = "王阳明",
            avatarResName = "avatar_chinese_scholar",
            nationality = "中国·明朝",
            gender = Gender.MALE,
            birthYear = 1472,
            deathYear = 1529,
            occupation = "思想家、军事家",
            motto = "知行合一，致良知。",
            famousWork = "《传习录》",
            bio = "明代心学大师，提出知行合一、致良知等思想。"
        ),
        IdentityFullData(
            name = "莎士比亚",
            avatarResName = "avatar_writer",
            nationality = "英国·斯特拉福",
            gender = Gender.MALE,
            birthYear = 1564,
            deathYear = 1616,
            occupation = "剧作家、诗人",
            motto = "生存还是毁灭，这是个问题。",
            famousWork = "《哈姆雷特》《罗密欧与朱丽叶》",
            bio = "英国文学史上最杰出的戏剧家，西方文学的典范。"
        ),
        IdentityFullData(
            name = "曹雪芹",
            avatarResName = "avatar_chinese_scholar",
            nationality = "中国·清朝",
            gender = Gender.MALE,
            birthYear = 1715,
            deathYear = 1763,
            occupation = "小说家",
            motto = "满纸荒唐言，一把辛酸泪。",
            famousWork = "《红楼梦》",
            bio = "清代小说家，《红楼梦》被公认为中国古典小说巅峰之作。"
        )
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
                            avatarResName = figure.avatarResName,
                            nationality = figure.nationality,
                            gender = figure.gender,
                            birthYear = figure.birthYear,
                            deathYear = figure.deathYear,
                            occupation = figure.occupation,
                            motto = figure.motto,
                            famousWork = figure.famousWork,
                            bio = figure.bio,
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
                                    likeCount = 0,
                                    commentCount = 0
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

    private data class IdentityFullData(
        val name: String,
        val avatarResName: String,
        val nationality: String,
        val gender: Gender,
        val birthYear: Int?,
        val deathYear: Int?,
        val occupation: String,
        val motto: String,
        val famousWork: String,
        val bio: String
    )
}
