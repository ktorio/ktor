package you.kube

import java.io.*


data class Video(val id: Long, val title: String, val authorId: String, val videoFileName: String) : Serializable
