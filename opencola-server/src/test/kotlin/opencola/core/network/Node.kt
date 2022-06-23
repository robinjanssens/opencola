package opencola.core.network

import opencola.server.handlers.Peer
import opencola.server.handlers.PeersResult

interface Node {
    fun make()
    fun start() : Node
    fun stop()
    fun setNetworkToken(token: String)
    fun getInviteToken() : String
    fun postInviteToken(token: String) : Peer
    fun getPeers() : PeersResult
    fun updatePeer(peer: Peer)
}