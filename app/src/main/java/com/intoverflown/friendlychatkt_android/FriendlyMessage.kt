package com.intoverflown.friendlychatkt_android

class FriendlyMessage {
    var text: String? = null
    var name: String? = null
    var photoUrl: String? = null

    constructor(text: String?, name: String?, photoUrl: String?) {
        this.text = text
        this.name = name
        this.photoUrl = photoUrl
    }
}