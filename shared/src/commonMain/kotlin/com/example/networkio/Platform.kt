package com.example.networkio

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform