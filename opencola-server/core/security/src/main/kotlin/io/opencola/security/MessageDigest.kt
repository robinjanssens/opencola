package io.opencola.security

import java.security.MessageDigest

// TODO: Just change to hash, and configure algorithm
fun sha256(input: ByteArray) : ByteArray{
    // Can use update() method to add in length
    return MessageDigest
        .getInstance("SHA-256") // TODO: SHA 3?
        .digest(input)
}

fun sha256(input: String): ByteArray {
    return sha256(input.toByteArray())
}

fun getMd5Digest(str: String): ByteArray = MessageDigest.getInstance("MD5").digest(str.toByteArray(Charsets.UTF_8))