package com.example.networkio.model
import kotlinx.serialization.Serializable
data class Group(
    val id:String,
    val name:String,
    val description:String,
    val imagurUrl:String,
    val createdAt:Long=System.currentTimeMillis(),
    val ownwerId:String
)