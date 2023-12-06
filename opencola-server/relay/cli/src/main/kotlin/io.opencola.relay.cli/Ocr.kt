package io.opencola.relay.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.long
import io.opencola.model.Id
import io.opencola.relay.common.message.v2.*
import io.opencola.relay.common.policy.Policy
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.io.path.deleteIfExists

// https://github.com/ajalt/clikt/blob/master/samples/repo/src/main/kotlin/com/github/ajalt/clikt/samples/repo/main.kt

inline fun <reified T> Json.tryDecodeFromString(jsonString: String): T? {
    return try {
        decodeFromString<T>(jsonString)
    } catch (e: Exception) {
        null
    }
}

fun AdminMessage.format(): String {
    return when (this) {
        is CommandResponse -> "${if (status != Status.SUCCESS) "$status: " else ""}$message"
        else -> this.toString()
    }
}

class ConfigCliktCommand(private val context: Context) : CliktCommand(name = "config") {
    override fun run() {
        println("storagePath: ${context.storagePath}")
    }
}

class IdentityCliktCommand(private val context: Context) : CliktCommand(name = "identity") {
    private val delete: Boolean by option("-r", "--reset", help = "Reset identity").flag()

    override fun run() {
        if (delete) {
            context.storagePath.resolve("keystore.pks").deleteIfExists()
        } else {
            println("Id: ${Id.ofPublicKey(context.keyPair.public)}")
        }
    }
}

// Example policy (with defaults left out):
// {"name":"admin","adminPolicy":{"isAdmin":true,"canEditPolicies":true,"canEditUserPolicies":true}}
// Escaped for command line use:
// {\"name\":\"admin\",\"adminPolicy\":{\"isAdmin\":true,\"canEditPolicies\":true,\"canEditUserPolicies\":true}}
class SetPolicyCliktCommand(val context: Context) : CliktCommand(name = "set") {
    private val json by option(help = "The policy in json format")
    private val file by option(help = "The policy in json format").file()
    override fun run() {
        if (json == null && file == null)
            throw CliktError("Must specify either --json or --file")

        if (json != null && file != null)
            throw CliktError("Must specify either --json or --file, not both")

        val jsonString = (if (json != null) json else file?.readText()) ?: throw CliktError("No policy specified")
        val policy =
            context.json.tryDecodeFromString<Policy>(jsonString) ?: throw CliktError("Invalid policy: $jsonString")
        val response = context.sendCommandMessage(SetPolicyCommand(policy))
        println(response.format())
    }
}

class GetPolicyCliktCommand(private val context: Context) : CliktCommand(name = "get") {
    private val name: String by argument(help = "The name of the policy")
    override fun run() {
        val response = context.sendCommandMessage(GetPolicyCommand(name))

        if (response is GetPolicyResponse) {
            if (response.policy == null)
                println("Policy \"$name\" does not exist")
            else
                println(context.json.encodeToString(response.policy))
        } else {
            println(response.format())
        }
    }
}

class GetAllPoliciesCliktCommand(private val context: Context) : CliktCommand(name = "ls") {
    override fun run() {
        val response = context.sendCommandMessage(GetPoliciesCommand())

        if (response is GetPoliciesResponse) {
            if (response.policies.isEmpty())
                println("No policies found")
            else
                response.policies.forEach { println(context.json.encodeToString(it)) }
        } else
            println(response.format())
    }
}

class RemovePolicyCliktCommand(val context: Context) : CliktCommand(name = "remove") {
    private val name: String by argument(help = "The name of the policy")

    override fun run() {
        val response = context.sendCommandMessage(RemovePolicyCommand(name))
        println(response.format())
    }
}

class PolicyCliktCommand() : CliktCommand(name = "policy") {
    override fun run() = Unit
}

class SetUserPolicyCliktCommand(val context: Context) : CliktCommand(name = "set") {
    private val userId: String by argument(help = "The id of the user")
    private val name: String by argument(help = "The name of the policy")
    override fun run() {
        val response = context.sendCommandMessage(SetUserPolicyCommand(Id.decode(userId), name))
        println(response.format())
    }
}

class GetUserPolicyCliktCommand(val context: Context) : CliktCommand(name = "get") {
    private val userId: String by argument(help = "The id of the user")
    override fun run() {
        val id = Id.tryDecode(userId) ?: throw CliktError("Invalid user id: $userId")
        val response = context.sendCommandMessage(GetUserPolicyCommand(id))

        if (response is GetUserPolicyResponse) {
            if (response.policy == null)
                println("User policy for \"$userId\" does not exist")
            else
                println(context.json.encodeToString(response.policy))
        } else {
            println(response.format())
        }
    }
}

class GetAllUserPoliciesCliktCommand(private val context: Context) : CliktCommand(name = "ls") {
    override fun run() {
        val response = context.sendCommandMessage(GetUserPoliciesCommand())

        if (response is GetUserPoliciesResponse) {
            if (response.policies.isEmpty())
                println("No user policies found")
            else
                response.policies.forEach {
                    println("${it.first}\t${it.second}")
                }
        } else
            println(response.format())
    }
}

class RemoveUserPolicyCliktCommand(val context: Context) : CliktCommand(name = "remove") {
    private val userId: String by argument(help = "The id of the user")

    override fun run() {
        val id = Id.tryDecode(userId) ?: throw CliktError("Invalid user id: $userId")
        val response = context.sendCommandMessage(RemoveUserPolicyCommand(id))
        println(response.format())
    }
}

class UserPolicyCliktCommand() : CliktCommand(name = "user-policy") {
    override fun run() = Unit
}

class RemoveUserMessagesCliktCommand(private val context: Context) : CliktCommand(name = "remove") {
    private val userId: String? by option(help = "The id of the user")
    private val age: Long? by option(help = "Remove messages older than age in milliseconds").long()

    override fun run() {
        if (userId == null && age == null)
            throw CliktError("Must specify either --user-id or --age")

        if (userId != null && age != null)
            throw CliktError("Must specify either --user-id or --age, not both")

        val response = if (age != null) {
            context.sendCommandMessage(RemoveMessagesByAgeCommand(age!!))
        } else {
            val id = Id.tryDecode(userId!!) ?: throw CliktError("Invalid user id: $userId")
            context.sendCommandMessage(RemoveUserMessagesCommand(id))
        }

        println(response.format())
    }
}

class GetMessageUsageCliktCommand(private val context: Context) : CliktCommand(name = "usage") {
    override fun run() {
        val response = context.sendCommandMessage(GetMessageUsageCommand())

        if (response is GetMessageUsageResponse) {
            if (response.usages.isEmpty())
                println("No stored messages")
            response.usages.forEach {
                println("${it.to}\t${it.numMessages}\t${it.numBytes}")
            }
        } else {
            println(response.format())
        }
    }
}

class MessagesCliktCommand() : CliktCommand(name = "messages") {
    override fun run() = Unit
}

class StorageCliktCommand() : CliktCommand(name = "storage") {
    override fun run() = Unit
}

class ExecCliktCommand(private val context: Context) : CliktCommand(name = "exec") {
    private val args by argument(help = "The command to execute").multiple()

    override fun run() {
        val cmd = args.joinToString(" ") { if (it.contains(" ")) "\"$it\"" else it }
    }
}

class OcrCliktCommand : CliktCommand(name = "ocr") {
    override fun run() = Unit
}

fun main(args: Array<String>) {
    val storagePath = initStorage()
    val config = loadConfig(storagePath.resolve(configFileName))
    val keyPair = getKeyPair(initStorage(), getPasswordHash(config))
        ?: error("KeyPair not found. You may need to delete your identity and try again.")

    runBlocking {
        val context = Context(storagePath, config, keyPair)

        context.use {
            OcrCliktCommand()
                .subcommands(
                    ConfigCliktCommand(context),
                    IdentityCliktCommand(context),
                    PolicyCliktCommand().subcommands(
                        SetPolicyCliktCommand(context),
                        GetPolicyCliktCommand(context),
                        GetAllPoliciesCliktCommand(context),
                        RemovePolicyCliktCommand(context)
                    ),
                    UserPolicyCliktCommand().subcommands(
                        SetUserPolicyCliktCommand(context),
                        GetUserPolicyCliktCommand(context),
                        GetAllUserPoliciesCliktCommand(context),
                        RemoveUserPolicyCliktCommand(context),
                    ),
                    MessagesCliktCommand().subcommands(
                        RemoveUserMessagesCliktCommand(context),
                        GetMessageUsageCliktCommand(context)
                    ),
//                    StorageCliktCommand().subcommands(
//                        GetMessageUsageCliktCommand(context)
//                    ),
//                    ExecCliktCommand(context),
                )
                .main(args)
        }
    }
}
