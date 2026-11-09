# CodeBook R8 规则
# kotlinx.serialization 自带 consumer rules，这里只保留显式需要的部分。

# 保留 @Serializable 生成的序列化器（R8 全模式下可能需要）
-keepclassmembers class **$$serializer {
    *** descriptor;
}
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
}
