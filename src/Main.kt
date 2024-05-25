import com.google.gson.Gson
import frontend.Parser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import runtime.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import javax.swing.JFrame
import kotlin.io.path.*
import kotlin.system.exitProcess

val globalCoroutineScope = CoroutineScope(Dispatchers.Default)
val globalVars = hashSetOf("io", "math", "net", "data", "false", "null", "true", "time", "argv", "std", "ui")
val windows: MutableMap<Double, JFrame> = mutableMapOf()
val home: String = System.getProperty("user.home")

/*
Example package JSON
{
    name: "Test-Package",
    files: [ "https://example.com/example.tea" ],
    dependencies: [ "https://example.com/dependency.tea" ],
    bin: null
}
*/

data class Package(val name: String, val files: List<String>, val dependencies: List<String>, val bin: String?)

fun run(argv: Array<String>) = runBlocking {
    val parser = Parser()
    val cmd = argv[0]
    val args = argv.slice(2..<argv.size).map { makeString(it) }
    val env = makeGlobalEnv(args.toTypedArray())

    when (cmd) {
        "run" -> {
            val buf = File(argv[1]).readBytes()

            val ast = parser.produceAST(String(buf))

            evaluate(
                ast,
                env
            )
        }
        "compile" -> {
            val buf = File(argv[1]).readBytes()

            val ast = parser.produceAST(String(buf))

            val stdlibDir = Paths.get("src/tea")
            val mainClass = File(argv[1]).nameWithoutExtension

            val manifest = Manifest().apply {
                mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
                mainAttributes[Attributes.Name.MAIN_CLASS] = mainClass
            }

            val jar = JarOutputStream(FileOutputStream(argv[2]), manifest)

            stdlibDir.forEachDirectoryEntry {
                if (it.extension == "class") {
                    val entry = JarEntry("tea/" + it.name)

                    jar.putNextEntry(entry)
                    jar.write(it.readBytes())
                    jar.closeEntry()
                }
            }

            compile(ast, env, cw, mainClass, jar)

            jar.close()
        }
        "transpile" -> {
            val buf = File(argv[1]).readBytes()

            val ast = parser.produceAST(String(buf))

            val js = """
// Start standard library for non-native js functions
const argv = []
const __std = Object.freeze({
    arr: (...els) => els,
    pusharr: (arr, el) => arr.push(el),
    poparr: (arr) => arr.pop(el),
    assignArrIdx: (arr, idx, el) => arr[idx] = el,
    exit: (code) => {
        throw `Program exited with code ${'$'}{code}`
    },
    not_supported_js: () => {
        throw 'Some function wasn't able to be transpiled to JavaScript :('
    }
});
// End standard library for non-native js functions
               
                ${transpile(ast, env)}
            """.trimIndent()
            Path(argv[2]).writeText(js)
        }
        // e.g. tea install https://example.com/pkg.json
        "install" -> {
            if (!Paths.get(home, ".tea", "scripts").exists()) {
                Files.createDirectory(Paths.get(home, ".tea", "scripts"))
            }

            val client = OkHttpClient()
            val req = Request.Builder().url(argv[1]).build()

            val body = client.newCall(req).execute().body?.string()
            val gson = Gson()
            val pkg = gson.fromJson(body, Package::class.java)

            for (dependencyURL in pkg.dependencies) {
                val dependency = client.newCall(
                    Request.Builder().url(dependencyURL).build()
                ).execute().body?.string()

                val dependencyJSON = gson.fromJson(dependency, Package::class.java)
                val dependencyPath = Paths.get(home, ".tea/scripts/${pkg.name}")

                if (!dependencyPath.exists()) {
                    for (dependencyFile in dependencyJSON.files) {
                        val fileReq = Request.Builder().url(dependencyFile).build()
                        val fileFuture = CompletableFuture<String>()
                        val filePath = Paths.get(home, ".tea/scripts/${pkg.name}/${dependencyFile.toHttpUrl().toUrl().path.split("/").last()}")

                        println("------ Downloading dependency of package ${pkg.name}'s file $dependencyFile to file $filePath ------")

                        client.newCall(fileReq).execute().use {
                            Files.createDirectory(Paths.get(home, ".tea/scripts/${pkg.name}"))

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
            val path = Paths.get(home, ".tea/scripts/${argv[1]}")

            if (!path.exists()) {
                throw IOException("Cannot uninstall package ${argv[1]} since it was never installed.")
            }

            val file = File(path.toUri())

            if (!file.deleteRecursively()) {
                throw Exception("Package ${argv[1]} could not be deleted.")
            }
        }
        "version" -> {
            println("TeaScript Build System ${Path("/usr/local/bin/tea-version").readText()}")
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

    while ((globalCoroutineScope.coroutineContext[Job]?.children?.count() ?: 0) > 0) {
        if (globalCoroutineScope.coroutineContext[Job]?.children?.count() == 0) {
            break
        }
    }

    while (windows.any { it.value.isVisible }) {
        // Do nothing because we are waiting for windows to be closed
    }

    println("All windows have been closed and coroutines finished.")

    return@runBlocking
}

fun main(args: Array<String>) {
    run(args)

    exitProcess(0)
}