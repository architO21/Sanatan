package com.example.networkio.model

import kotlinx.serialization.Serializable

@Serializable
data class GroupMember(
    val id:String,
    val groupId:String,
    val userId:String,
    val role:String
)