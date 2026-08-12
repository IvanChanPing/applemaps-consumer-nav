import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.Remapper
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Isolates the legacy protobuf runtime embedded in Vela's OsmAnd fat JAR from the host app's
 * protobuf-javalite runtime. Both the embedded runtime and every OsmAnd reference to it move as
 * one closed namespace; the pinned input artifact and all Vela source remain untouched.
 */
abstract class RelocateOsmandProtobuf : DefaultTask() {
    @get:InputFile
    abstract val inputJar: RegularFileProperty

    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    @TaskAction
    fun relocate() {
        val input = inputJar.get().asFile
        val output = outputJar.get().asFile
        output.parentFile.mkdirs()
        val remapper = object : Remapper() {
            override fun map(internalName: String): String = when {
                internalName == OLD_PACKAGE -> NEW_PACKAGE
                internalName.startsWith("$OLD_PACKAGE/") -> NEW_PACKAGE + internalName.removePrefix(OLD_PACKAGE)
                else -> internalName
            }
        }
        ZipFile(input).use { zip ->
            ZipOutputStream(output.outputStream().buffered()).use { out ->
                zip.entries().asSequence()
                    .filterNot { it.isDirectory || isSignature(it.name) }
                    .forEach { entry ->
                        val source = zip.getInputStream(entry).readBytes()
                        val (name, bytes) = if (entry.name.endsWith(".class")) {
                            val reader = ClassReader(source)
                            val writer = ClassWriter(0)
                            reader.accept(ClassRemapper(writer, remapper), 0)
                            remapper.mapType(reader.className) + ".class" to writer.toByteArray()
                        } else {
                            entry.name to source
                        }
                        out.putNextEntry(ZipEntry(name))
                        out.write(bytes)
                        out.closeEntry()
                    }
            }
        }
    }

    private fun isSignature(name: String): Boolean =
        name.startsWith("META-INF/") && (name.endsWith(".SF") || name.endsWith(".RSA") || name.endsWith(".DSA"))

    private companion object {
        const val OLD_PACKAGE = "com/google/protobuf"
        const val NEW_PACKAGE = "app/vela/shaded/protobuf"
    }
}
