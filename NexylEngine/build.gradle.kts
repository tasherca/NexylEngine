plugins {
    id("java")
    id("application")
}

application {
    mainClass.set("org.example.Main")
}

group = "org.example"
version = "1.0-SNAPSHOT"

val lwjglVersion = "3.3.5"

val lwjglNatives = Pair(
    System.getProperty("os.name")!!,
    System.getProperty("os.arch")!!
).let { (name, arch) ->
    when {
        arrayOf("Linux", "SunOS", "Unit").any { name.startsWith(it) } ->
            if (arrayOf("arm", "aarch64").any { arch.startsWith(it) })
                "natives-linux${if (arch.contains("64") || arch.startsWith("armv8")) "-arm64" else "-arm32"}"
            else if (arch.startsWith("ppc"))
                "natives-linux-ppc64le"
            else if (arch.startsWith("riscv"))
                "natives-linux-riscv64"
            else
                "natives-linux"
        arrayOf("Mac OS X", "Darwin").any { name.startsWith(it) } ->
            "natives-macos${if (arch.startsWith("aarch64")) "-arm64" else ""}"
        arrayOf("Windows").any { name.startsWith(it) } ->
            if (arch.contains("64")) "natives-windows" else "natives-windows-x86"
        else ->
            throw Error("Unrecognized or unsupported platform. Please set \"lwjglNatives\" manually")
    }
}

repositories {
    mavenCentral()
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
}

dependencies {
    implementation(platform("org.lwjgl:lwjgl-bom:$lwjglVersion"))

    // ImGui зависимости
    implementation("io.github.spair:imgui-java-binding:1.86.11")
    implementation("io.github.spair:imgui-java-lwjgl3:1.86.11")

    // LWJGL зависимости
    implementation("org.lwjgl", "lwjgl")
    implementation("org.lwjgl", "lwjgl-glfw")
    implementation("org.lwjgl", "lwjgl-opengl")
    implementation("org.lwjgl", "lwjgl-stb")
    implementation("org.lwjgl", "lwjgl-tinyfd") // Добавлено для нативных диалогов

    // Нативные зависимости
    runtimeOnly("org.lwjgl", "lwjgl", classifier = lwjglNatives)
    runtimeOnly("org.lwjgl", "lwjgl-glfw", classifier = lwjglNatives)
    runtimeOnly("org.lwjgl", "lwjgl-opengl", classifier = lwjglNatives)
    runtimeOnly("org.lwjgl", "lwjgl-stb", classifier = lwjglNatives)
    runtimeOnly("org.lwjgl", "lwjgl-tinyfd", classifier = lwjglNatives) // Добавлено

    // Добавляем нативные зависимости для всех платформ
    runtimeOnly("io.github.spair:imgui-java-natives-windows:1.86.11")
    runtimeOnly("io.github.spair:imgui-java-natives-linux:1.86.11")
    runtimeOnly("io.github.spair:imgui-java-natives-macos:1.86.11")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaExec> {
    jvmArgs = listOf("-Xmx2g", "--enable-preview")
    
    // Force X11 backend on Linux
    if (System.getProperty("os.name").lowercase().contains("linux")) {
        environment("GDK_BACKEND", "x11")
        environment("XDG_SESSION_TYPE", "x11")
        environment("QT_QPA_PLATFORM", "xcb")
        environment("SDL_VIDEODRIVER", "x11")
        environment("JAVA_TOOL_OPTIONS", "-Djdk.gtk.version=2")
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("--enable-preview", "-Xlint:preview"))
}

tasks.named<JavaExec>("run") {
    jvmArgs = listOf("--enable-preview", "-Xmx2g")
    
    // Force X11 backend on Linux
    if (System.getProperty("os.name").lowercase().contains("linux")) {
        environment("GDK_BACKEND", "x11")
        environment("XDG_SESSION_TYPE", "x11")
        environment("QT_QPA_PLATFORM", "xcb")
        environment("SDL_VIDEODRIVER", "x11")
        environment("JAVA_TOOL_OPTIONS", "-Djdk.gtk.version=2")
    }
}

// Создаем FAT JAR
tasks.named<Jar>("jar") {
    manifest {
        attributes(
            "Main-Class" to "org.example.Main",
            "Implementation-Version" to project.version
        )
    }
    
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().output)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith("jar") }
            .map { zipTree(it) }
    })
}

// ========== НАТИВНАЯ СБОРКА ==========

// Задача для нативной сборки
tasks.register<Exec>("buildNative") {
    group = "native"
    description = "Build native executable"
    
    val jarFile = tasks.jar.get().archiveFile.get().asFile
    
    // Try to find native-image command
    val nativeImageCmd = findNativeImageCommand()
    
    commandLine(
        nativeImageCmd,
        "-jar", jarFile.absolutePath,
        "nexyl-engine",
        "--no-fallback",
        "--enable-url-protocols=http,https",
        "-H:+JNI",
        "-H:+AllowIncompleteClasspath",
        "--verbose"
    )
    
    doFirst {
        println("=== НАЧИНАЕМ СБОРКУ НАТИВНОГО ОБРАЗА ===")
        println("Команда: $nativeImageCmd")
        println("JAR файл: $jarFile")
        
        if (!file(nativeImageCmd).exists() && !isInPath(nativeImageCmd)) {
            throw GradleException("Native Image не найден. Установите GraalVM или Liberica NIK.")
        }
    }
    
    doLast {
        println("✓ Нативный образ собран")
        println("Файл: nexyl-engine")
    }
    
    dependsOn("jar")
}

fun findNativeImageCommand(): String {
    val os = System.getProperty("os.name").lowercase()
    
    // Try common paths
    val paths = mutableListOf<String>()
    
    if (os.contains("linux") || os.contains("mac")) {
        paths.add("native-image")
        paths.add("/usr/bin/native-image")
        paths.add("/usr/local/bin/native-image")
        paths.add("${System.getenv("HOME")}/.sdkman/candidates/java/current/bin/native-image")
        paths.add("/opt/graalvm/bin/native-image")
        paths.add("/opt/graalvm-ce-java21/bin/native-image")
    } else if (os.contains("win")) {
        paths.add("native-image.cmd")
        paths.add("C:\\Program Files\\GraalVM\\graalvm-ce-java21\\bin\\native-image.cmd")
        paths.add("C:\\Program Files\\BellSoft\\LibericaJDK-21\\bin\\native-image.cmd")
    }
    
    // Check environment variables
    val javaHome = System.getenv("JAVA_HOME")
    if (javaHome != null) {
        if (os.contains("win")) {
            paths.add("$javaHome\\bin\\native-image.cmd")
        } else {
            paths.add("$javaHome/bin/native-image")
        }
    }
    
    val graalvmHome = System.getenv("GRAALVM_HOME")
    if (graalvmHome != null) {
        if (os.contains("win")) {
            paths.add("$graalvmHome\\bin\\native-image.cmd")
        } else {
            paths.add("$graalvmHome/bin/native-image")
        }
    }
    
    // Find first existing command
    for (path in paths) {
        if (file(path).exists() || isInPath(path)) {
            return path
        }
    }
    
    return "native-image" // Fallback
}

fun isInPath(command: String): Boolean {
    return try {
        ProcessBuilder("which", command).start().waitFor() == 0
    } catch (e: Exception) {
        false
    }
}

// Задача для проверки доступности Native Image
tasks.register("checkNativeImage") {
    group = "native"
    description = "Check if Native Image is available"
    
    doLast {
        println("=== ПРОВЕРКА NATIVE IMAGE ===")
        val cmd = findNativeImageCommand()
        
        if (file(cmd).exists() || isInPath(cmd)) {
            println("✓ Native Image найден: $cmd")
        } else {
            println("✗ Native Image не найден")
            println("\n=== ИНСТРУКЦИЯ ===")
            println("1. Установите GraalVM или Liberica Native Image Kit:")
            println("   - GraalVM: https://www.graalvm.org/downloads/")
            println("   - Liberica NIK: https://bell-sw.com/pages/downloads/native-image-kit/")
            println("2. Добавьте в PATH: /path/to/graalvm/bin")
            println("3. Или установите переменную окружения JAVA_HOME")
        }
    }
}

// Простая команда для пользователя
tasks.register("nativeBuildHelp") {
    group = "native"
    description = "Show native build instructions"
    
    dependsOn("jar")
    
    doLast {
        val jarFile = tasks.jar.get().archiveFile.get().asFile
        
        println("""
        === ИНСТРУКЦИЯ ДЛЯ НАТИВНОЙ СБОРКИ ===
        
        JAR файл создан: ${jarFile.name}
        
        ДАЛЬНЕЙШИЕ ДЕЙСТВИЯ:
        
        1. Убедитесь, что установлен GraalVM Native Image или Liberica NIK
        
        2. Для Linux/Mac:
           native-image -jar ${jarFile.absolutePath} nexyl-engine --no-fallback
        
        3. Для Windows:
           native-image.cmd -jar ${jarFile.absolutePath} nexyl-engine --no-fallback
        
        4. Или используйте Gradle:
           ./gradlew buildNative
        
        ДОПОЛНИТЕЛЬНЫЕ ПАРАМЕТРЫ:
        --enable-url-protocols=http,https
        -H:+JNI
        -H:+AllowIncompleteClasspath
        --verbose
        """.trimIndent())
    }
}
