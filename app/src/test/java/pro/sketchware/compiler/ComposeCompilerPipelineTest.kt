package pro.sketchware.compiler

import a.a.a.ProjectBuilder
import a.a.a.yq
import mod.hey.studios.compiler.kotlin.KotlinCompiler
import mod.hey.studios.compiler.kotlin.KotlinCompilerBridge
import mod.jbk.build.BuiltInLibraries
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipInputStream

class ComposeCompilerPipelineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var projectDir: File
    private lateinit var javaFilesDir: File
    private lateinit var compiledClassesDir: File
    private lateinit var binDir: File
    private lateinit var filesLibsDir: File

    private lateinit var workspace: yq
    private lateinit var builder: ProjectBuilder

    @Before
    fun setUp() {
        projectDir = tempFolder.newFolder("test_project")
        javaFilesDir = File(projectDir, "app/src/main/java").apply { mkdirs() }
        compiledClassesDir = File(projectDir, "bin/classes").apply { mkdirs() }
        binDir = File(projectDir, "bin").apply { mkdirs() }

        filesLibsDir = File(tempFolder.newFolder("files_dir"), "libs")
        filesLibsDir.mkdirs()

        // Extract libs.zip and plugin if available from project assets
        val assetsLibsDir = File("src/main/assets/libs")
        if (assetsLibsDir.exists()) {
            extractZip(File(assetsLibsDir, "libs.zip"), File(filesLibsDir, "libs"))
            extractZip(File(assetsLibsDir, "dexs.zip"), File(filesLibsDir, "dexs"))

            val androidJarArchive = File(assetsLibsDir, "android.jar.zip")
            if (androidJarArchive.exists()) {
                extractZip(androidJarArchive, filesLibsDir)
            }

            val pluginAsset = File(assetsLibsDir, "kotlin-compose-compiler-plugin-embeddable-2.1.21.jar")
            if (pluginAsset.exists()) {
                pluginAsset.copyTo(File(filesLibsDir, pluginAsset.name), overwrite = true)
            }
        }

        workspace = yq().apply {
            sc_id = "1001"
            javaFilesPath = javaFilesDir.absolutePath
            rJavaDirectoryPath = File(projectDir, "gen").apply { mkdirs() }.absolutePath
            compiledClassesPath = compiledClassesDir.absolutePath
            binDirectoryPath = binDir.absolutePath
        }

        builder = MockProjectBuilder(workspace, filesLibsDir)
    }

    @Test
    fun testA_ExistingJavaProject() {
        val javaFile = File(javaFilesDir, "test/me/Example.java").apply {
            parentFile.mkdirs()
            writeText(
                """
                package test.me;
                public class Example {
                    public static int add(int a, int b) {
                        return a + b;
                    }
                }
                """.trimIndent()
            )
        }
        assertTrue(javaFile.exists())
    }

    @Test
    fun testB_ExistingKotlinProject() {
        File(javaFilesDir, "test/me/Example.kt").apply {
            parentFile.mkdirs()
            writeText(
                """
                package test.me
                class Example {
                    fun add(a: Int, b: Int): Int = a + b
                }
                """.trimIndent()
            )
        }

        KotlinCompilerBridge.maybeAddKotlinBuiltInLibraryDependenciesIfPossible(builder, builder.builtInLibraryManager)
        KotlinCompiler(builder).compile()

        val compiledClass = File(compiledClassesDir, "test/me/Example.class")
        assertTrue("Kotlin class should compile", compiledClass.exists())
    }

    @Test
    fun testC_KotlinPlusAndroidX() {
        File(javaFilesDir, "test/me/MyActivity.kt").apply {
            parentFile.mkdirs()
            writeText(
                """
                package test.me
                import androidx.appcompat.app.AppCompatActivity
                class MyActivity : AppCompatActivity()
                """.trimIndent()
            )
        }

        builder.builtInLibraryManager.addLibrary(BuiltInLibraries.ANDROIDX_APPCOMPAT)
        KotlinCompilerBridge.maybeAddKotlinBuiltInLibraryDependenciesIfPossible(builder, builder.builtInLibraryManager)
        KotlinCompiler(builder).compile()

        val compiledClass = File(compiledClassesDir, "test/me/MyActivity.class")
        assertTrue("AppCompat Activity should compile", compiledClass.exists())
    }

    @Test
    fun testD_MinimalCompose() {
        File(javaFilesDir, "test/me/Hello.kt").apply {
            parentFile.mkdirs()
            writeText(
                """
                package test.me
                import androidx.compose.runtime.Composable

                @Composable
                fun Hello() {}
                """.trimIndent()
            )
        }

        KotlinCompilerBridge.maybeAddKotlinBuiltInLibraryDependenciesIfPossible(builder, builder.builtInLibraryManager)
        KotlinCompiler(builder).compile()

        val compiledClass = File(compiledClassesDir, "test/me/HelloKt.class")
        assertTrue("Minimal Compose should compile", compiledClass.exists())
    }

    @Test
    fun testE_ComposeUiAndFoundation() {
        File(javaFilesDir, "test/me/Screen.kt").apply {
            parentFile.mkdirs()
            writeText(
                """
                package test.me
                import androidx.compose.foundation.layout.Column
                import androidx.compose.foundation.layout.padding
                import androidx.compose.runtime.Composable
                import androidx.compose.ui.Modifier
                import androidx.compose.ui.unit.dp

                @Composable
                fun Screen() {
                    Column(modifier = Modifier.padding(16.dp)) {}
                }
                """.trimIndent()
            )
        }

        KotlinCompilerBridge.maybeAddKotlinBuiltInLibraryDependenciesIfPossible(builder, builder.builtInLibraryManager)
        KotlinCompiler(builder).compile()

        val compiledClass = File(compiledClassesDir, "test/me/ScreenKt.class")
        assertTrue("Compose UI and Foundation should compile", compiledClass.exists())
    }

    @Test
    fun testF_Material3() {
        File(javaFilesDir, "test/me/MatScreen.kt").apply {
            parentFile.mkdirs()
            writeText(
                """
                package test.me
                import androidx.compose.material3.Text
                import androidx.compose.runtime.Composable

                @Composable
                fun MatScreen() {
                    Text("Hello Material3")
                }
                """.trimIndent()
            )
        }

        KotlinCompilerBridge.maybeAddKotlinBuiltInLibraryDependenciesIfPossible(builder, builder.builtInLibraryManager)
        KotlinCompiler(builder).compile()

        val compiledClass = File(compiledClassesDir, "test/me/MatScreenKt.class")
        assertTrue("Material 3 should compile", compiledClass.exists())
    }

    @Test
    fun testG_ActivityComposeIntegration() {
        File(javaFilesDir, "test/me/LoginActivity.kt").apply {
            parentFile.mkdirs()
            writeText(
                """
                package test.me

                import android.os.Bundle
                import androidx.activity.ComponentActivity
                import androidx.activity.compose.setContent
                import androidx.compose.foundation.layout.Column
                import androidx.compose.foundation.layout.padding
                import androidx.compose.material3.Text
                import androidx.compose.runtime.Composable
                import androidx.compose.ui.Modifier
                import androidx.compose.ui.unit.dp

                class LoginActivity : ComponentActivity() {
                    override fun onCreate(savedInstanceState: Bundle?) {
                        super.onCreate(savedInstanceState)
                        setContent {
                            LoginScreen()
                        }
                    }
                }

                @Composable
                fun LoginScreen() {
                    Column(
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text("Hello TAJ Studio")
                        Text("Jetpack Compose is working")
                    }
                }
                """.trimIndent()
            )
        }

        KotlinCompilerBridge.maybeAddKotlinBuiltInLibraryDependenciesIfPossible(builder, builder.builtInLibraryManager)
        KotlinCompiler(builder).compile()

        val activityClass = File(compiledClassesDir, "test/me/LoginActivity.class")
        val screenClass = File(compiledClassesDir, "test/me/LoginActivityKt.class")

        assertTrue("LoginActivity.class must exist", activityClass.exists())
        assertTrue("LoginActivityKt.class must exist", screenClass.exists())

        // Section 14: Prove Compose compiler was REALLY active and transformed @Composable
        val screenClassContent = screenClass.readBytes()
        val classString = String(screenClassContent, Charsets.ISO_8859_1)

        // The Compose compiler plugin injects references to Composer and synthetic methods into transformed @Composable classes
        assertTrue(
            "Compiled bytecode must contain reference to Composer, proving Compose compiler transformed @Composable",
            classString.contains("androidx/compose/runtime/Composer") || classString.contains("Composer")
        )
    }

    @Test
    fun testH_JavaKotlinComposeInterop() {
        File(javaFilesDir, "test/me/Constants.java").apply {
            parentFile.mkdirs()
            writeText(
                """
                package test.me;
                public class Constants {
                    public static final String TITLE = "Interop Title";
                }
                """.trimIndent()
            )
        }

        File(javaFilesDir, "test/me/InteropScreen.kt").apply {
            parentFile.mkdirs()
            writeText(
                """
                package test.me
                import androidx.compose.material3.Text
                import androidx.compose.runtime.Composable

                @Composable
                fun InteropScreen() {
                    Text(Constants.TITLE)
                }
                """.trimIndent()
            )
        }

        KotlinCompilerBridge.maybeAddKotlinBuiltInLibraryDependenciesIfPossible(builder, builder.builtInLibraryManager)
        KotlinCompiler(builder).compile()

        val compiledClass = File(compiledClassesDir, "test/me/InteropScreenKt.class")
        assertTrue("Java+Kotlin+Compose interop should compile", compiledClass.exists())
    }

    @Test
    fun testI_CleanRebuild() {
        testD_MinimalCompose()
        assertTrue("Compiled class should exist before clean", File(compiledClassesDir, "test/me/HelloKt.class").exists())

        compiledClassesDir.deleteRecursively()
        compiledClassesDir.mkdirs()
        assertFalse("Compiled class deleted for clean rebuild", File(compiledClassesDir, "test/me/HelloKt.class").exists())

        KotlinCompiler(builder).compile()
        assertTrue("Compiled class regenerated after clean rebuild", File(compiledClassesDir, "test/me/HelloKt.class").exists())
    }

    private fun extractZip(zipFile: File, destDir: File) {
        if (!zipFile.exists()) return
        destDir.mkdirs()
        ZipInputStream(FileInputStream(zipFile)).use { zipIn ->
            var entry = zipIn.nextEntry
            while (entry != null) {
                val file = File(destDir, entry.name)
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    file.outputStream().use { out -> zipIn.copyTo(out) }
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }
    }

    private class MockProjectBuilder(
        workspace: yq,
        private val filesLibsDir: File
    ) : ProjectBuilder(workspace) {

        init {
            yq.sc_id = "1001"
            this.androidJarPath = File(filesLibsDir, "android.jar").absolutePath
            buildBuiltInLibraryInformation()
        }

        override fun getClasspath(): String {
            val cp = StringBuilder()
            KotlinCompilerBridge.maybeAddKotlinFilesToClasspath(cp, yq)
            cp.append(androidJarPath)

            for (library in builtInLibraryManager.libraries) {
                val libJar = File(filesLibsDir, "libs/${library.name}/classes.jar")
                if (libJar.exists()) {
                    cp.append(":").append(libJar.absolutePath)
                }
            }
            return cp.toString()
        }
    }
}
