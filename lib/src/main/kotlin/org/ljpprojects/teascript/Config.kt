package org.ljpprojects.teascript

data class ConfigCommand(
    val name: String,
    val args: List<String>?,
    val type: String
)

data class ConfigAlias(
    val name: String,
    val commands: List<ConfigCommand>?
)

data class ConfigActionMeta(
    val trigger: String,
    val args: List<String>?,
)

data class ConfigAction(
    val name: String,
    val desc: String,
    val commands: List<ConfigCommand>,
    val meta: ConfigActionMeta
)

data class ConfigImport(
    val file: String,
    val items: List<String>?
)

data class ConfigServerPrivilege(
    val hostname: String,
    val port: UInt
)

data class ConfigNetPrivileges(
    val get: List<String>?,
    val write: List<String>?,
    val serve: List<ConfigServerPrivilege>?
)

data class ConfigFilePrivileges(
    val read: List<String>?,
    val write: List<String>?
)

data class ConfigPrintPrivileges(
    val numOfDocuments: UInt
)

data class ConfigShellPrivileges(
    val name: String,
    val sandboxed: Boolean,
    val timeout: ULong,
)

data class ConfigPrivileges(
    val net: ConfigNetPrivileges?,
    val file: ConfigFilePrivileges?,
    val print: ConfigPrintPrivileges?,
    val shell: List<ConfigShellPrivileges>?,
)

data class Config(
    val aliases: List<ConfigAlias>?,
    val actions: List<ConfigAction>?,
    val imports: List<ConfigImport>?,
    val privileges: ConfigPrivileges?,
)