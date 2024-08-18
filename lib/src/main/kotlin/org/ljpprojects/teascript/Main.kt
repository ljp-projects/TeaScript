package org.ljpprojects.teascript

import com.google.gson.Gson
import com.moandjiezana.toml.Toml
import org.ljpprojects.teascript.frontend.AbstrDeclParser
import org.ljpprojects.teascript.frontend.Parser
import org.ljpprojects.teascript.frontend.tokenise
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.kohsuke.args4j.Argument
import org.kohsuke.args4j.CmdLineParser
import org.kohsuke.args4j.Option
import org.kohsuke.args4j.spi.StringArrayOptionHandler
import org.ljpprojects.teascript.runtime.evaluate
import org.ljpprojects.teascript.runtime.makeGlobalEnv
import org.ljpprojects.teascript.runtime.types.makeString
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import kotlin.io.path.*
import kotlin.system.exitProcess
import kotlin.time.ExperimentalTime

/**
 * This is the global coroutine scope.
 * It is used to execute tasks in parallel.
 * */
val globalCoroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)

/**
 * These are global variables created by the makeGlobalEnv family of functions.
 * */
val globalVars: HashSet<String> =
    hashSetOf(
        "io",
        "math",
        "net",
        "data",
        "false",
        "null",
        "true",
        "time",
        "argv",
        "std",
        "web",
        "reflection"
    )

var serverRunning = false

/**
 * The user's home directory (used for the build system primarily)
 * */
val home: String = System.getProperty("user.home")

/**
 * Will store command-line arguments parsed by Args4j.
 */
val argsParsed: Main = Main()

/**
 * A data class containing every parameter that may be in a TeaScript package.
 * This class is generated after the package json file is received (via GSON)
 * ```json
 * {
 *  name: "Test-Package",
 *  files: [ "https://example.com/example.tea" ],
 *  dependencies: [ "https://example.com/dependency.tea" ],
 *  bin: null
 * }
 * ```
 *
 * @property name The name of the package
 * @property files The files included with the package
 * @property dependencies Packages this package needs to run properly
 * @property bin The (optional) binary to include with the package (also TeaScript code)
 */
data class Package(
    val name: String,
    val files: List<String>,
    val dependencies: List<String>,
    val bin: String?
)

/**
 * Run the TeaScript Build System with the arguments parsed by Args4j.
 */
fun run(argv: Main) = runBlocking {
    val cmd = argv["CMD"] ?: "help"
    val args by lazy { argv.args.map { makeString(it) } }
    val env by lazy { makeGlobalEnv(args.toTypedArray()) }
    // uncomment when needed val cEnv by lazy { makeGlobalCompilationEnv() }

    when (cmd) {
        "run" -> {
            val buf = File(argv["SRC"].toString()).readBytes()

            val ast = Parser(String(buf)).apply { Parser.tokens = tokenise(String(buf)) }.produceAST()

            evaluate(
                ast,
                env,
                argv["SRC"].toString()
            )
        }

        "test" -> {
            val buf = File(argv["SRC"].toString()).readBytes()

            val ast = AbstrDeclParser(String(buf)).produceDecl()

            println(ast)
        }

        // e.g. tea install https://example.com/pkg.json
        "install" -> {
            if (!Paths.get(home, ".tea", "scripts").exists()) {
                Files.createDirectory(Paths.get(home, ".tea", "scripts"))
            }

            val client = OkHttpClient()
            val req = Request.Builder().url(argv["SRC"].toString()).build()

            val body = client.newCall(req).execute().body?.string()
            val gson = Gson()
            val pkg = gson.fromJson(body, Package::class.java)

            for (dependencyURL in pkg.dependencies) {
                val dependency = client.newCall(
                    Request.Builder().url(dependencyURL).build()
                ).execute().body?.string()

                val dependencyJSON = gson.fromJson(dependency, Package::class.java)
                val dependencyPath = Paths.get(home, ".tea", "scripts", pkg.name)

                if (!dependencyPath.exists()) {
                    for (dependencyFile in dependencyJSON.files) {
                        val fileReq = Request.Builder().url(dependencyFile).build()
                        val fileFuture = CompletableFuture<String>()
                        val filePath = Paths.get(home, ".tea/scripts/${pkg.name}/${dependencyFile.toHttpUrl().toUrl().path.split("/").last()}")

                        println("------ Downloading dependency of package ${pkg.name}'s file $dependencyFile to file $filePath ------")

                        client.newCall(fileReq).execute().use {
                            Files.createDirectory(Paths.get(home, ".tea", "scripts", pkg.name))

                            fileFuture.complete(it.body?.string() ?: "")
                        }

                        val data = fileFuture.get()

                        if (!filePath.exists()) {
                            Files.createFile(filePath)
                        }

                        File(filePath.toUri()).writeText(data)
                    }
                }
            }
        }
        // e.g. tea uninstall pkg
        "uninstall" -> {
            val path = Paths.get(home, ".tea", "scripts", argv["SRC"].toString())

            if (!path.exists()) {
                throw IOException("Cannot uninstall package ${argv["SRC"]} since it was never installed.")
            }

            val file = File(path.toUri())

            if (!file.deleteRecursively()) {
                throw Exception("Package ${argv["SRC"].toString()} could not be deleted.")
            }
        }

        "version" -> {
            println("TeaScript Build System ${Path("/usr/local/bin/tea-version").readText()}")
        }

        "config" -> {
            if (argsParsed.json) {
                val gson = Gson()
                val configParsed = gson.fromJson(File(argsParsed.source).readText(), Config::class.java)

                println(configParsed)

                return@runBlocking
            }

            val tomlParser = Toml().read(File(argsParsed.source))
            val configParsed = tomlParser.to(Config::class.java)

            println(configParsed)
        }

        "help" -> {
            println(
                """
                |usage: tea [command] [arguments]
                
                |commands:
                |    tea run [source file] - Run a .tea file
                |    tea transpile [source file] [destination file] - Transpile a .tea file and write the result to [destination file]
                |    tea compile [source file] [destination jar] - Compile a .tea file to a .jar file
                |    tea install [package json url] - Install a package from a JSON file
                |    tea uninstall [package name] - Uninstall and delete a package from the system
                |    tea version - Display the current version of the TeaScript Build System installed
            """.trimMargin()
            )
        }

        else -> Unit
    }

    while ((globalCoroutineScope.coroutineContext[Job]?.children?.count() ?: 0) > 0 || serverRunning) {
        if (globalCoroutineScope.coroutineContext[Job]?.children?.count() == 0) {
            break
        }
    }

    return@runBlocking
}

/**
 * The class to use add arguments parsed by Args4j.
 */
class Main {

    /**
     * When passing -J or --json, JSON will be used instead of TOML.
     */
    @Option(
        name = "-J",
        aliases = ["--json"],
        required = false,
        usage = "Prefer JSON over TOML.",
        metaVar = "JSON"
    )
    var json = false

    /**
     * The command to run.
     * It is required.
     * @see run
     */
    @Argument(index = 0, usage = "The command to run.", required = true, metaVar = "CMD")
    lateinit var command: String

    /**
     * The source file to use for certain commands.
     */
    @Argument(index = 1, usage = "The source file to use.", required = false, metaVar = "SRC")
    var source: String = ""

    /**
     * The output file to use for certain commands.
     */
    @Argument(index = 2, usage = "The file to output a result to.", required = false, metaVar = "OUT")
    var output: String = ""

    /**
     * Arguments to the program
     */
    @Argument(index = 3, usage = "Arguments to the program.", handler = StringArrayOptionHandler::class)
    var args: Array<String> = arrayOf()

    /**
     * Return the arguments parsed as a HashMap.
     * */
    private val map: HashMap<String, Any>
        get() =
            hashMapOf(
                "CMD" to this.command,
                "SRC" to this.source,
                "OUT" to this.output,
                "JSON" to this.json
            )

    /**
     * Use the HashMap made by the .map value to get a key
     */
    operator fun get(key: String): Any? = this.map[key]
}

fun main(args: Array<String>) {
    val parser = CmdLineParser(argsParsed)

    try {
        parser.parseArgument(*args)
    } catch (e: Exception) {
        println(e.message)

        parser.printUsage(System.err)

        exitProcess(1)
    }

    run(argsParsed)
}