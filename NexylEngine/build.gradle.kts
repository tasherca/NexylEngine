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

    // Нативные зависимости
    runtimeOnly("org.lwjgl", "lwjgl", classifier = lwjglNatives)
    runtimeOnly("org.lwjgl", "lwjgl-glfw", classifier = lwjglNatives)
    runtimeOnly("org.lwjgl", "lwjgl-opengl", classifier = lwjglNatives)
    runtimeOnly("org.lwjgl", "lwjgl-stb", classifier = lwjglNatives)

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
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("--enable-preview", "-Xlint:preview"))
}

tasks.named<JavaExec>("run") {
    jvmArgs = listOf("--enable-preview", "-Xmx2g")
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

// ========== НАТИВНАЯ СБОРКА С ПОИСКОМ LIBERICA NIK ==========

// Определяем путь к Liberica NIK
val libericaHome = System.getenv("LIBERICA_HOME") ?: "C:\\Program Files\\BellSoft\\LibericaJDK-21"
val libericaNikDir = file(libericaHome)
val nativeImageCmd = file("$libericaHome/bin/native-image.cmd")

// Задача для проверки Liberica NIK
tasks.register("checkLiberica") {
    group = "native"
    description = "Check if Liberica NIK is installed"
    
    doLast {
        println("=== ПРОВЕРКА LIBERICA NIK ===")
        println("Ищем по пути: $libericaHome")
        
        if (libericaNikDir.exists()) {
            println("✓ Директория Liberica NIK найдена")
        } else {
            println("✗ Директория Liberica NIK не найдена")
        }
        
        if (nativeImageCmd.exists()) {
            println("✓ Native Image найден: ${nativeImageCmd.absolutePath}")
        } else {
            println("✗ Native Image не найден")
        }
        
        // Проверяем альтернативные пути
        val altPaths = listOf(
            "C:\\Program Files\\LibericaJDK-21",
            "C:\\BellSoft\\LibericaJDK-21",
            System.getenv("JAVA_HOME") ?: ""
        )
        
        for (path in altPaths) {
            if (path.isNotEmpty()) {
                val altNativeImage = file("$path/bin/native-image.cmd")
                if (altNativeImage.exists()) {
                    println("✓ Найден альтернативный Native Image: ${altNativeImage.absolutePath}")
                    return@doLast
                }
            }
        }
        
        if (!nativeImageCmd.exists()) {
            println("\n=== ИНСТРУКЦИЯ ===")
            println("1. Установите Liberica Native Image Kit:")
            println("   https://bell-sw.com/pages/downloads/native-image-kit/")
            println("2. Установите в: C:\\Program Files\\BellSoft\\LibericaJDK-21")
            println("3. Или установите переменную окружения:")
            println("   setx LIBERICA_HOME \"путь\\к\\liberica\"")
        }
    }
}

// Задача для нативной сборки
tasks.register<Exec>("buildNative") {
    group = "native"
    description = "Build native executable"
    
    // Если native-image существует, используем его
    val cmd = if (nativeImageCmd.exists()) {
        nativeImageCmd.absolutePath
    } else {
        // Ищем в альтернативных путях
        val altPaths = listOf(
            "C:\\Program Files\\LibericaJDK-21\\bin\\native-image.cmd",
            "C:\\BellSoft\\LibericaJDK-21\\bin\\native-image.cmd",
            "${System.getenv("JAVA_HOME")}\\bin\\native-image.cmd"
        ).firstOrNull { file(it).exists() }
        
        if (altPaths != null) {
            altPaths
        } else {
            // Если не нашли, используем команду из PATH
            "native-image.cmd"
        }
    }
    
    val jarFile = tasks.jar.get().archiveFile.get().asFile
    
    commandLine(
        cmd,
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
        println("Команда: $cmd")
        println("JAR файл: $jarFile")
        
        // Проверяем существование команды
        if (cmd == "native-image.cmd") {
            println("Используем native-image.cmd из PATH")
        } else if (!file(cmd).exists()) {
            throw GradleException("Native Image не найден. Установите Liberica NIK.")
        }
    }
    
    doLast {
        println("✓ Нативный образ собран")
        println("Файл: nexyl-engine.exe")
    }
    
    dependsOn("jar")
}

// Альтернативная задача с ручным указанием пути
tasks.register<Exec>("buildNativeManual") {
    group = "native"
    description = "Build native executable with manual path"
    
    val jarFile = tasks.jar.get().archiveFile.get().asFile
    
    // Ручной ввод пути - пользователь должен указать
    val manualPath = providers.gradleProperty("liberica.path")
        .orElse(System.getenv("LIBERICA_HOME"))
        .orElse("C:\\Program Files\\BellSoft\\LibericaJDK-21")
        .get() + "\\bin\\native-image.cmd"
    
    commandLine(
        manualPath,
        "-jar", jarFile.absolutePath,
        "nexyl-engine",
        "--no-fallback",
        "--enable-url-protocols=http,https",
        "-H:+JNI",
        "-H:+AllowIncompleteClasspath",
        "--verbose"
    )
    
    doFirst {
        println("Используем путь: $manualPath")
        if (!file(manualPath).exists()) {
            throw GradleException("Native Image не найден по пути: $manualPath")
        }
    }
    
    dependsOn("jar")
}

// Простая команда для пользователя - собирает JAR и показывает инструкцию
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
        
        1. Убедитесь, что установлен Liberica Native Image Kit:
           https://bell-sw.com/pages/downloads/native-image-kit/
           
        2. Откройте CMD или PowerShell в папке с проектом
        
        3. Выполните одну из команд:
        
        Вариант A (если Liberica в PATH):
        native-image.cmd -jar ${jarFile.absolutePath} nexyl-engine --no-fallback
        
        Вариант B (если Liberica установлен в стандартном месте):
        "C:\Program Files\BellSoft\LibericaJDK-21\bin\native-image.cmd" -jar ${jarFile.absolutePath} nexyl-engine --no-fallback
        
        Вариант C (ручное выполнение через gradle):
        ./gradlew buildNative
        
        ДОПОЛНИТЕЛЬНЫЕ ПАРАМЕТРЫ:
        --enable-url-protocols=http,https
        -H:+JNI
        -H:+AllowIncompleteClasspath
        --verbose
        
        === УСТРАНЕНИЕ ПРОБЛЕМ ===
        
        1. Если native-image.cmd не найден:
           - Установите Liberica Native Image Kit
           - Или установите переменную окружения LIBERICA_HOME
           
        2. Если используется GraalVM из Scoop:
           - Временно удалите из PATH: C:\Users\iv4no\scoop\apps\graalvm22\current\bin
           - Или используйте полный путь к Liberica
        """.trimIndent())
    }
}

// Задача для сборки без зависимостей (ручной режим)
tasks.register("prepareForNativeBuild") {
    group = "native"
    description = "Prepare everything for native build"
    
    dependsOn("jar")
    
    doLast {
        val jarFile = tasks.jar.get().archiveFile.get().asFile
        
        println("=== ВСЕ ГОТОВО ДЛЯ РУЧНОЙ СБОРКИ ===")
        println()
        println("1. JAR файл создан: build/libs/${jarFile.name}")
        println()
        println("2. Откройте CMD и перейдите в папку проекта:")
        println("   cd \"C:\\Users\\iv4no\\Documents\\NexylEngine\"")
        println()
        println("3. Выполните команду сборки:")
        println("   \"C:\\Program Files\\BellSoft\\LibericaJDK-21\\bin\\native-image.cmd\" -jar build\\libs\\${jarFile.name} nexyl-engine --no-fallback --enable-url-protocols=http,https -H:+JNI -H:+AllowIncompleteClasspath")
        println()
        println("4. Если Liberica установлен в другом месте, укажите правильный путь")
    }
}
