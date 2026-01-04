package org.example;

import imgui.*;
import imgui.flag.*;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.system.MemoryUtil.*;

public class Main {
    private Window window;
    private Renderer renderer;
    private InputHandler inputHandler;
    private Scene scene;
    private UIManager uiManager;
    private ProjectFileManager projectFileManager;
    
    private boolean isRunning = true;
    private float deltaTime = 0.0f;
    private double lastFrameTime = 0.0;
    private FPSCounter fpsCounter = new FPSCounter();
    private String currentProjectName = "Untitled";

    public void run() {
        try {
            initialize();
            mainLoop();
        } finally {
            cleanup();
        }
    }

    private void initialize() {
        System.out.println("=== APPLICATION INITIALIZATION ===");
        
        window = new Window(1920, 1080, "3D Scene");
        renderer = new Renderer();
        scene = new Scene();
        inputHandler = new InputHandler(window, scene, renderer);
        projectFileManager = new ProjectFileManager();
        uiManager = new UIManager(window, scene, inputHandler, projectFileManager, this);
        
        System.out.println("=== INITIALIZATION COMPLETE ===");
    }

    private void mainLoop() {
        System.out.println("=== STARTING MAIN LOOP ===");
        
        while (isRunning && !window.shouldClose()) {
            updateTime();
            handleInput();
            update();
            render();
        }
    }

    private void updateTime() {
        double currentTime = glfwGetTime();
        deltaTime = (float) (currentTime - lastFrameTime);
        lastFrameTime = currentTime;
        fpsCounter.update(deltaTime);
    }

    private void handleInput() {
        inputHandler.pollEvents();
    }

    private void update() {
        scene.update(deltaTime);
        inputHandler.update(deltaTime);
    }

    private void render() {
        renderer.beginFrame();
        renderer.renderScene(scene, window);
        uiManager.render(fpsCounter.getFPS(), currentProjectName);
        renderer.endFrame();
        window.swapBuffers();
    }

    private void cleanup() {
        System.out.println("=== CLEANING UP RESOURCES ===");
        
        if (uiManager != null) uiManager.cleanup();
        if (renderer != null) renderer.cleanup();
        if (scene != null) scene.cleanup();
        if (window != null) window.cleanup();
        
        System.out.println("=== CLEANUP COMPLETE ===");
    }

    public void saveProject() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filterPatterns = stack.mallocPointer(1);
            filterPatterns.put(stack.UTF8("*.ne"));
            filterPatterns.flip();
            
            String filePath = TinyFileDialogs.tinyfd_saveFileDialog(
                "Save Project",
                currentProjectName + ".ne",
                filterPatterns,
                "Nexyl Engine Project Files (*.ne)"
            );
            
            if (filePath != null) {
                if (!filePath.toLowerCase().endsWith(".ne")) {
                    filePath += ".ne";
                }
                
                try {
                    projectFileManager.saveProject(scene, filePath);
                    currentProjectName = extractFileName(filePath);
                    System.out.println("Project saved: " + filePath);
                } catch (IOException e) {
                    System.err.println("Failed to save project: " + e.getMessage());
                    TinyFileDialogs.tinyfd_messageBox(
                        "Error",
                        "Failed to save project: " + e.getMessage(),
                        "ok",
                        "error",
                        false
                    );
                }
            }
        }
    }

    public void saveProjectAs() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filterPatterns = stack.mallocPointer(1);
            filterPatterns.put(stack.UTF8("*.ne"));
            filterPatterns.flip();
            
            String filePath = TinyFileDialogs.tinyfd_saveFileDialog(
                "Save Project As",
                currentProjectName + ".ne",
                filterPatterns,
                "Nexyl Engine Project Files (*.ne)"
            );
            
            if (filePath != null) {
                if (!filePath.toLowerCase().endsWith(".ne")) {
                    filePath += ".ne";
                }
                
                try {
                    projectFileManager.saveProject(scene, filePath);
                    currentProjectName = extractFileName(filePath);
                    System.out.println("Project saved as: " + filePath);
                } catch (IOException e) {
                    System.err.println("Failed to save project: " + e.getMessage());
                    TinyFileDialogs.tinyfd_messageBox(
                        "Error",
                        "Failed to save project: " + e.getMessage(),
                        "ok",
                        "error",
                        false
                    );
                }
            }
        }
    }

    public void openProject() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filterPatterns = stack.mallocPointer(1);
            filterPatterns.put(stack.UTF8("*.ne"));
            filterPatterns.flip();
            
            String filePath = TinyFileDialogs.tinyfd_openFileDialog(
                "Open Project",
                null,
                filterPatterns,
                "Nexyl Engine Project Files (*.ne)",
                false
            );
            
            if (filePath != null) {
                try {
                    scene = projectFileManager.loadProject(filePath);
                    inputHandler.setScene(scene);
                    uiManager.setScene(scene);
                    currentProjectName = extractFileName(filePath);
                    System.out.println("Project loaded: " + filePath);
                } catch (Exception e) {
                    System.err.println("Failed to load project: " + e.getMessage());
                    TinyFileDialogs.tinyfd_messageBox(
                        "Error",
                        "Failed to load project: " + e.getMessage(),
                        "ok",
                        "error",
                        false
                    );
                }
            }
        }
    }

    public void newProject() {
        scene = new Scene();
        inputHandler.setScene(scene);
        uiManager.setScene(scene);
        currentProjectName = "Untitled";
        System.out.println("New project created");
    }

    private String extractFileName(String filePath) {
        String fileName = filePath.substring(
            Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\')) + 1
        );
        if (fileName.toLowerCase().endsWith(".ne")) {
            fileName = fileName.substring(0, fileName.length() - 3);
        }
        return fileName;
    }

    public static void main(String[] args) {
        System.out.println("=== STARTING 3D APPLICATION ===");
        
        try {
            new Main().run();
        } catch (Exception e) {
            System.err.println("Application error:");
            e.printStackTrace();
            System.exit(-1);
        }
    }

    public String getCurrentProjectName() {
        return currentProjectName;
    }

    private static class Window {
        private long glfwWindow;
        private int width, height;
        private String title;

        public Window(int width, int height, String title) {
            this.width = width;
            this.height = height;
            this.title = title;
            initialize();
        }

        private void initialize() {
            setupGLFW();
            createWindow();
            setupOpenGL();
            showWindow();
        }

        private void setupGLFW() {
            GLFWErrorCallback.createPrint(System.err).set();
            
            if (!glfwInit()) {
                throw new IllegalStateException("Failed to initialize GLFW");
            }

            glfwDefaultWindowHints();
            glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
            glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
            glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
            glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
        }

        private void createWindow() {
            glfwWindow = glfwCreateWindow(width, height, title, NULL, NULL);
            if (glfwWindow == NULL) {
                throw new RuntimeException("Failed to create GLFW window");
            }
        }

        private void setupOpenGL() {
            glfwMakeContextCurrent(glfwWindow);
            GL.createCapabilities();
            
            glEnable(GL_DEPTH_TEST);
            glClearColor(0.1f, 0.1f, 0.1f, 1.0f);
            glfwSwapInterval(1);
        }

        private void showWindow() {
            updateFramebufferSize();
            glfwShowWindow(glfwWindow);
        }

        public void updateFramebufferSize() {
            IntBuffer widthBuffer = BufferUtils.createIntBuffer(1);
            IntBuffer heightBuffer = BufferUtils.createIntBuffer(1);
            
            glfwGetFramebufferSize(glfwWindow, widthBuffer, heightBuffer);
            width = widthBuffer.get(0);
            height = heightBuffer.get(0);
            
            glViewport(0, 0, width, height);
        }

        public boolean shouldClose() {
            return glfwWindowShouldClose(glfwWindow);
        }

        public void swapBuffers() {
            glfwSwapBuffers(glfwWindow);
        }

        public void cleanup() {
            glfwFreeCallbacks(glfwWindow);
            glfwDestroyWindow(glfwWindow);
            glfwTerminate();
            
            GLFWErrorCallback callback = glfwSetErrorCallback(null);
            if (callback != null) callback.free();
        }

        public long getGLFWWindow() { return glfwWindow; }
        public int getWidth() { return width; }
        public int getHeight() { return height; }
    }

    private static class Renderer {
        private ShaderManager shaderManager;
        private GeometryManager geometryManager;
        private ObjectPicker objectPicker;

        public Renderer() {
            shaderManager = new ShaderManager();
            geometryManager = new GeometryManager();
            objectPicker = new ObjectPicker();
        }

        public void beginFrame() {
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        }

        public void renderScene(Scene scene, Window window) {
            scene.render(shaderManager, geometryManager, window);
        }

        public void endFrame() {
            // Пост-обработка
        }

        public GameObject performPicking(Scene scene, Window window, int mouseX, int mouseY) {
            return objectPicker.performPicking(scene, shaderManager, geometryManager, window, mouseX, mouseY);
        }
        
        public float[] performCursorRaycast(Scene scene, Window window, int mouseX, int mouseY) {
            return objectPicker.performRaycast(scene, window, mouseX, mouseY);
        }

        public void cleanup() {
            if (shaderManager != null) shaderManager.cleanup();
            if (geometryManager != null) geometryManager.cleanup();
            if (objectPicker != null) objectPicker.cleanup();
        }
    }

    private static class InputHandler {
        private Window window;
        private Scene scene;
        private Renderer renderer;
        
        private boolean[] keyStates = new boolean[GLFW_KEY_LAST + 1];
        private boolean ctrlPressed = false;
        private boolean shiftPressed = false;
        private boolean altPressed = false;
        
        private boolean showCreationMenu = false;
        private double menuPosX, menuPosY;
        
        private double lastMouseX, lastMouseY;
        private boolean isMouseRightPressed = false;
        private boolean isMouseLeftPressed = false;
        private boolean isFirstMouseMovement = true;
        
        private boolean cameraMovementLocked = false;

        public InputHandler(Window window, Scene scene, Renderer renderer) {
            this.window = window;
            this.scene = scene;
            this.renderer = renderer;
            setupCallbacks();
        }

        private void setupCallbacks() {
            glfwSetFramebufferSizeCallback(window.getGLFWWindow(), this::onFramebufferSize);
            glfwSetCursorPosCallback(window.getGLFWWindow(), this::onCursorPos);
            glfwSetMouseButtonCallback(window.getGLFWWindow(), this::onMouseButton);
            glfwSetScrollCallback(window.getGLFWWindow(), this::onScroll);
            glfwSetKeyCallback(window.getGLFWWindow(), this::onKey);
        }

        private void onFramebufferSize(long window, int width, int height) {
            if (width > 0 && height > 0) {
                this.window.updateFramebufferSize();
                
                ImGuiIO io = ImGui.getIO();
                io.setDisplaySize(width, height);
                io.setDisplayFramebufferScale(1.0f, 1.0f);
            }
        }

        private void onCursorPos(long window, double xpos, double ypos) {
            ImGuiIO io = ImGui.getIO();
            if (!io.getWantCaptureMouse()) {
                handleMouseMove(xpos, ypos);
            }
            
            lastMouseX = xpos;
            lastMouseY = ypos;
        }

        private void onMouseButton(long window, int button, int action, int mods) {
            ImGuiIO io = ImGui.getIO();
            if (!io.getWantCaptureMouse()) {
                handleMouseButton(button, action);
                
                if (button == GLFW_MOUSE_BUTTON_LEFT && action == GLFW_PRESS) {
                    // Если в режиме 3D курсора - перемещаем курсор
                    if (scene.getSelectionMode() == Scene.SelectionMode.CURSOR_3D) {
                        float[] intersection = renderer.performCursorRaycast(scene, this.window, (int)lastMouseX, (int)lastMouseY);
                        scene.setCursor3DPosition(intersection[0], intersection[1], intersection[2]);
                        System.out.println("Cursor moved to: (" + intersection[0] + ", " + intersection[1] + ", " + intersection[2] + ")");
                    } else {
                        // Стандартный режим - выбираем объект
                        GameObject selected = renderer.performPicking(scene, this.window, (int)lastMouseX, (int)lastMouseY);
                        scene.setSelectedObject(selected);
                        
                        if (selected != null) {
                            System.out.println("Selected: " + selected.getName());
                        }
                    }
                }
            }
        }

        private void onScroll(long window, double xoffset, double yoffset) {
            ImGuiIO io = ImGui.getIO();
            if (!io.getWantCaptureMouse()) {
                scene.getCameraController().handleScroll(yoffset);
            }
        }

        private void onKey(long window, int key, int scancode, int action, int mods) {
            if (key == GLFW_KEY_LEFT_CONTROL || key == GLFW_KEY_RIGHT_CONTROL) {
                ctrlPressed = (action == GLFW_PRESS || action == GLFW_REPEAT);
                cameraMovementLocked = ctrlPressed;
            }
            if (key == GLFW_KEY_LEFT_SHIFT || key == GLFW_KEY_RIGHT_SHIFT) {
                shiftPressed = (action == GLFW_PRESS || action == GLFW_REPEAT);
            }
            if (key == GLFW_KEY_LEFT_ALT || key == GLFW_KEY_RIGHT_ALT) {
                altPressed = (action == GLFW_PRESS || action == GLFW_REPEAT);
            }
            
            if (key >= 0 && key < keyStates.length) {
                keyStates[key] = (action == GLFW_PRESS || action == GLFW_REPEAT);
            }
            
            if (action == GLFW_PRESS) {
                handleKeyPress(key, mods);
            }
            
            if (action == GLFW_RELEASE && (key == GLFW_KEY_LEFT_CONTROL || key == GLFW_KEY_RIGHT_CONTROL)) {
                cameraMovementLocked = false;
            }
        }

        private void handleMouseMove(double xpos, double ypos) {
            if (isMouseRightPressed) {
                if (isFirstMouseMovement) {
                    lastMouseX = xpos;
                    lastMouseY = ypos;
                    isFirstMouseMovement = false;
                }

                double xoffset = xpos - lastMouseX;
                double yoffset = lastMouseY - ypos;
                lastMouseX = xpos;
                lastMouseY = ypos;

                scene.getCameraController().handleMouseMovement(xoffset, yoffset);
            } else if (isMouseLeftPressed && scene.getSelectedObject() != null && 
                      scene.getSelectionMode() == Scene.SelectionMode.STANDARD) {
                GameObject selected = scene.getSelectedObject();
                float xoffset = (float) (xpos - lastMouseX) * 0.01f;
                float yoffset = (float) (lastMouseY - ypos) * 0.01f;
                lastMouseX = xpos;
                lastMouseY = ypos;

                if (scene.isTranslating()) {
                    Camera camera = scene.getCameraController().getCamera();
                    float[] right = camera.getRightVector();
                    float[] up = camera.getUpVector();
                    
                    selected.translate(right[0] * xoffset, up[1] * yoffset, right[2] * xoffset);
                    
                    if (selected.getPosition()[1] < 0.1f) {
                        selected.getPosition()[1] = 0.1f;
                    }
                } else if (scene.isRotating()) {
                    selected.rotateY(xoffset * 100.0f);
                    selected.rotateX(yoffset * 100.0f);
                } else if (scene.isScaling()) {
                    float scaleFactor = 1.0f + yoffset;
                    selected.scale(scaleFactor);
                }
            } else {
                isFirstMouseMovement = true;
            }
        }

        private void handleMouseButton(int button, int action) {
            if (button == GLFW_MOUSE_BUTTON_RIGHT) {
                isMouseRightPressed = (action == GLFW_PRESS);
                if (isMouseRightPressed) {
                    glfwSetInputMode(window.getGLFWWindow(), GLFW_CURSOR, GLFW_CURSOR_DISABLED);
                    isFirstMouseMovement = true;
                } else {
                    glfwSetInputMode(window.getGLFWWindow(), GLFW_CURSOR, GLFW_CURSOR_NORMAL);
                }
            } else if (button == GLFW_MOUSE_BUTTON_LEFT) {
                isMouseLeftPressed = (action == GLFW_PRESS);
            }
        }

        private void handleKeyPress(int key, int mods) {
            if (showCreationMenu && key == GLFW_KEY_ESCAPE) {
                showCreationMenu = false;
                return;
            }
            
            switch (key) {
                case GLFW_KEY_ESCAPE:
                    scene.handleEscape();
                    break;
                    
                case GLFW_KEY_G:
                    if (ctrlPressed) {
                        scene.toggleTranslationMode();
                        cameraMovementLocked = true;
                    }
                    break;
                    
                case GLFW_KEY_R:
                    if (ctrlPressed) {
                        scene.toggleRotationMode();
                        cameraMovementLocked = true;
                    }
                    break;
                    
                case GLFW_KEY_S:
                    if (ctrlPressed) {
                        scene.toggleScalingMode();
                        cameraMovementLocked = true;
                    }
                    break;
                    
                case GLFW_KEY_A:
                    if (shiftPressed) {
                        showCreationMenu = true;
                        menuPosX = lastMouseX;
                        menuPosY = lastMouseY;
                    }
                    break;
                    
                case GLFW_KEY_C:
                    if (shiftPressed) {
                        scene.toggle3DCursorVisibility();
                    }
                    break;
                    
                case GLFW_KEY_DELETE:
                    scene.removeSelectedObject();
                    break;
            }
        }

        public void pollEvents() {
            glfwPollEvents();
        }

        public void update(float deltaTime) {
            if (!cameraMovementLocked) {
                scene.getCameraController().updateMovement(keyStates, deltaTime);
            }
        }
        
        public boolean isShowCreationMenu() { return showCreationMenu; }
        public double getMenuPosX() { return menuPosX; }
        public double getMenuPosY() { return menuPosY; }
        public void setShowCreationMenu(boolean show) { this.showCreationMenu = show; }
        public void setScene(Scene scene) { this.scene = scene; }
    }

    public static class Scene {
        public enum SelectionMode {
            STANDARD,
            CURSOR_3D
        }
        
        private CameraController cameraController;
        private List<GameObject> gameObjects;
        private GameObject selectedObject;
        
        private boolean isTranslating = false;
        private boolean isRotating = false;
        private boolean isScaling = false;
        
        private int nextObjectId = 6;
        
        private SelectionMode selectionMode = SelectionMode.STANDARD;
        private float[] cursor3DPosition = {0.0f, 0.5f, 0.0f};
        private boolean showCursor = true;
        private float cursorSize = 0.5f;

        public Scene() {
            cameraController = new CameraController();
            gameObjects = new ArrayList<>();
            createDefaultScene();
        }

        private void createDefaultScene() {
            Material[] materials = {
                new Material(new float[]{0.19225f, 0.19225f, 0.19225f}, new float[]{0.50754f, 0.50754f, 0.50754f}, new float[]{0.508273f, 0.508273f, 0.508273f}, 0.4f),
                new Material(new float[]{0.25f, 0.25f, 0.25f}, new float[]{0.4f, 0.4f, 0.4f}, new float[]{0.774597f, 0.774597f, 0.774597f}, 0.6f),
                new Material(new float[]{0.05f, 0.05f, 0.05f}, new float[]{0.5f, 0.5f, 0.5f}, new float[]{0.7f, 0.7f, 0.7f}, 0.8f),
                new Material(new float[]{0.0f, 0.1f, 0.06f}, new float[]{0.0f, 0.50980392f, 0.50980392f}, new float[]{0.50196078f, 0.50196078f, 0.50196078f}, 0.25f)
            };

            gameObjects.add(new GameObject(-2.0f, 0.5f, 0.0f, 1.0f, 1.0f, 1.0f, materials[0], 1, GameObject.Type.REGULAR, "Cube_1"));
            gameObjects.add(new GameObject(0.0f, 0.5f, 0.0f, 1.0f, 1.0f, 1.0f, materials[1], 2, GameObject.Type.REGULAR, "Cube_2"));
            gameObjects.add(new GameObject(2.0f, 0.5f, 0.0f, 1.0f, 1.0f, 1.0f, materials[2], 3, GameObject.Type.REGULAR, "Cube_3"));
            gameObjects.add(new GameObject(0.0f, 0.5f, -2.0f, 1.0f, 1.0f, 1.0f, materials[3], 4, GameObject.Type.REGULAR, "Cube_4"));
            
            gameObjects.add(new GameObject(2.0f, 3.0f, 2.0f, 0.5f, 0.5f, 0.5f, null, 5, GameObject.Type.LIGHT, "Light_1"));
            
            cursor3DPosition[0] = 0.0f;
            cursor3DPosition[1] = 0.5f;
            cursor3DPosition[2] = 0.0f;
        }
        
        public void createObject(GameObject.Type type, String baseName) {
            float x = cursor3DPosition[0];
            float y = cursor3DPosition[1];
            float z = cursor3DPosition[2];
            
            Material material = null;
            float scale = 1.0f;
            
            if (type == GameObject.Type.REGULAR) {
                material = new Material(
                    new float[]{(float)Math.random() * 0.5f, (float)Math.random() * 0.5f, (float)Math.random() * 0.5f},
                    new float[]{(float)Math.random() * 0.5f + 0.5f, (float)Math.random() * 0.5f + 0.5f, (float)Math.random() * 0.5f + 0.5f},
                    new float[]{(float)Math.random() * 0.5f + 0.5f, (float)Math.random() * 0.5f + 0.5f, (float)Math.random() * 0.5f + 0.5f},
                    (float)Math.random() * 0.5f + 0.25f
                );
                scale = 1.0f;
            } else if (type == GameObject.Type.LIGHT) {
                scale = 0.5f;
            }
            
            String name = baseName + "_" + nextObjectId;
            GameObject newObject = new GameObject(x, y, z, scale, scale, scale, material, nextObjectId, type, name);
            gameObjects.add(newObject);
            nextObjectId++;
            
            if (selectionMode == SelectionMode.CURSOR_3D) {
                selectedObject = newObject;
            }
            
            System.out.println("Created: " + name + " at (" + x + ", " + y + ", " + z + ")");
        }

        public void update(float deltaTime) {
            // Анимации
        }

        public void render(ShaderManager shaderManager, GeometryManager geometryManager, Window window) {
            shaderManager.useMainShader();
            setupCameraUniforms(shaderManager, window);
            setupLightingUniforms(shaderManager);
            
            renderGrid(shaderManager, geometryManager);
            renderObjects(shaderManager, geometryManager);
            renderSelectionOutline(shaderManager, geometryManager);
            
            if (showCursor) {
                render3DCursor(shaderManager, geometryManager, window);
            }
        }

        private void setupCameraUniforms(ShaderManager shaderManager, Window window) {
            Camera camera = cameraController.getCamera();
            shaderManager.setViewMatrix(camera.getViewMatrix());
            shaderManager.setProjectionMatrix(camera.getProjectionMatrix(window.getWidth(), window.getHeight()));
            shaderManager.setViewPos(camera.getPosition());
        }

        private void setupLightingUniforms(ShaderManager shaderManager) {
            GameObject light = gameObjects.stream()
                .filter(obj -> obj.getType() == GameObject.Type.LIGHT)
                .findFirst()
                .orElse(null);
                
            if (light != null) {
                shaderManager.setLightPosition(light.getPosition());
                shaderManager.setLightProperties(
                    new float[]{0.2f, 0.2f, 0.2f},
                    new float[]{0.8f, 0.8f, 0.8f},
                    new float[]{1.0f, 1.0f, 1.0f}
                );
            } else {
                // Если нет источника света, используем значения по умолчанию
                shaderManager.setLightPosition(new float[]{2.0f, 3.0f, 2.0f});
                shaderManager.setLightProperties(
                    new float[]{0.2f, 0.2f, 0.2f},
                    new float[]{0.8f, 0.8f, 0.8f},
                    new float[]{1.0f, 1.0f, 1.0f}
                );
            }
        }

        private void renderGrid(ShaderManager shaderManager, GeometryManager geometryManager) {
            shaderManager.setUseLighting(false);
            shaderManager.setObjectColor(0.3f, 0.3f, 0.3f);
            geometryManager.renderGrid();
            shaderManager.setUseLighting(true);
        }

        private void renderObjects(ShaderManager shaderManager, GeometryManager geometryManager) {
            for (GameObject obj : gameObjects) {
                if (obj.getType() == GameObject.Type.REGULAR) {
                    shaderManager.setMaterial(obj.getMaterial());
                    shaderManager.setObjectColor(1.0f, 1.0f, 1.0f);
                    geometryManager.renderCube(obj.getModelMatrix());
                } else {
                    shaderManager.setUseLighting(false);
                    shaderManager.setObjectColor(1.0f, 1.0f, 0.0f);
                    geometryManager.renderLight(obj.getModelMatrix());
                    shaderManager.setUseLighting(true);
                }
            }
        }

        private void renderSelectionOutline(ShaderManager shaderManager, GeometryManager geometryManager) {
            if (selectedObject != null && selectionMode == SelectionMode.STANDARD) {
                geometryManager.renderOutline(selectedObject, shaderManager);
            }
        }
        
        private void render3DCursor(ShaderManager shaderManager, GeometryManager geometryManager, Window window) {
            geometryManager.render3DCursor(cursor3DPosition, cursorSize, shaderManager, 
                                          cameraController.getCamera(), window);
        }

        public void toggleTranslationMode() {
            if (selectedObject != null && selectionMode == SelectionMode.STANDARD) {
                isTranslating = !isTranslating;
                isRotating = false;
                isScaling = false;
                System.out.println("Translation: " + (isTranslating ? "ON" : "OFF"));
            }
        }

        public void toggleRotationMode() {
            if (selectedObject != null && selectionMode == SelectionMode.STANDARD) {
                isRotating = !isRotating;
                isTranslating = false;
                isScaling = false;
                System.out.println("Rotation: " + (isRotating ? "ON" : "OFF"));
            }
        }

        public void toggleScalingMode() {
            if (selectedObject != null && selectionMode == SelectionMode.STANDARD) {
                isScaling = !isScaling;
                isTranslating = false;
                isRotating = false;
                System.out.println("Scaling: " + (isScaling ? "ON" : "OFF"));
            }
        }
        
        public void setSelectionMode(SelectionMode mode) {
            this.selectionMode = mode;
            System.out.println("Selection Mode: " + mode);
        }
        
        public void toggle3DCursorVisibility() {
            showCursor = !showCursor;
            System.out.println("3D Cursor: " + (showCursor ? "ON" : "OFF"));
        }
        
        public void setCursor3DPosition(float x, float y, float z) {
            cursor3DPosition[0] = x;
            cursor3DPosition[1] = Math.max(0.1f, y);
            cursor3DPosition[2] = z;
        }
        
        public void resetCursorToOrigin() {
            cursor3DPosition[0] = 0.0f;
            cursor3DPosition[1] = 0.5f;
            cursor3DPosition[2] = 0.0f;
            System.out.println("Cursor reset");
        }

        public void handleEscape() {
            if (isTranslating || isRotating || isScaling) {
                isTranslating = false;
                isRotating = false;
                isScaling = false;
                System.out.println("Transformations disabled");
            } else if (selectedObject != null && selectionMode == SelectionMode.STANDARD) {
                selectedObject = null;
                System.out.println("Deselected");
            }
        }
        
        public void removeSelectedObject() {
            if (selectedObject != null) {
                gameObjects.remove(selectedObject);
                System.out.println("Removed: " + selectedObject.getName());
                selectedObject = null;
            }
        }

        public void cleanup() {
            for (GameObject obj : gameObjects) {
                obj.cleanup();
            }
            gameObjects.clear();
        }

        public CameraController getCameraController() { return cameraController; }
        public List<GameObject> getGameObjects() { return gameObjects; }
        public GameObject getSelectedObject() { return selectedObject; }
        public SelectionMode getSelectionMode() { return selectionMode; }
        public boolean isTranslating() { return isTranslating; }
        public boolean isRotating() { return isRotating; }
        public boolean isScaling() { return isScaling; }
        public float[] getCursor3DPosition() { return cursor3DPosition; }
        public boolean isCursorVisible() { return showCursor; }
        public int getNextObjectId() { return nextObjectId; }
        public void setNextObjectId(int id) { this.nextObjectId = id; }
        
        public void setSelectedObject(GameObject selectedObject) { 
            this.selectedObject = selectedObject;
            if (selectedObject != null) {
                System.out.println("Selected: " + selectedObject.getName());
            }
        }
        
        public void updateSelectedObjectPosition(float x, float y, float z) {
            if (selectedObject != null) {
                selectedObject.setPosition(x, y, z);
            }
        }
        
        public void updateSelectedObjectRotation(float x, float y, float z) {
            if (selectedObject != null) {
                selectedObject.setRotation(x, y, z);
            }
        }
        
        public void updateSelectedObjectScale(float x, float y, float z) {
            if (selectedObject != null) {
                selectedObject.setScale(x, y, z);
            }
        }
        
        public void setTranslationMode(boolean translating) { this.isTranslating = translating; }
        public void setRotationMode(boolean rotating) { this.isRotating = rotating; }
        public void setScalingMode(boolean scaling) { this.isScaling = scaling; }
        public void setCursorVisible(boolean visible) { this.showCursor = visible; }
    }

    private static class UIManager {
        private final ImGuiImplGlfw imGuiGlfw = new ImGuiImplGlfw();
        private final ImGuiImplGl3 imGuiGl3 = new ImGuiImplGl3();
        private Window window;
        private Scene scene;
        private InputHandler inputHandler;
        private ProjectFileManager projectFileManager;
        private Main mainApp;
        
        private float[] tempPos = new float[3];
        private float[] tempRot = new float[3];
        private float[] tempScale = new float[3];

        public UIManager(Window window, Scene scene, InputHandler inputHandler, 
                        ProjectFileManager projectFileManager, Main mainApp) {
            this.window = window;
            this.scene = scene;
            this.inputHandler = inputHandler;
            this.projectFileManager = projectFileManager;
            this.mainApp = mainApp;
            initialize();
        }

        private void initialize() {
            ImGui.createContext();
            ImGuiIO io = ImGui.getIO();
            io.setIniFilename(null);
            io.addConfigFlags(ImGuiConfigFlags.NavEnableKeyboard);

            io.setDisplaySize(window.getWidth(), window.getHeight());
            io.setDisplayFramebufferScale(1.0f, 1.0f);

            imGuiGlfw.init(window.getGLFWWindow(), true);
            imGuiGl3.init(null);
            
            ImGui.styleColorsDark();
        }

        public void render(float fps, String projectName) {
            imGuiGlfw.newFrame();
            ImGui.newFrame();

            renderMainMenuBar(projectName);
            renderFPSOverlay(fps);
            renderModeSelector();
            renderObjectTree();
            renderObjectProperties();
            renderCreationMenu();

            ImGui.render();
            imGuiGl3.renderDrawData(ImGui.getDrawData());
        }
        
        private void renderMainMenuBar(String projectName) {
            if (ImGui.beginMainMenuBar()) {
                if (ImGui.beginMenu("File")) {
                    if (ImGui.menuItem("New", "Ctrl+N")) {
                        mainApp.newProject();
                    }
                    if (ImGui.menuItem("Open...", "Ctrl+O")) {
                        mainApp.openProject();
                    }
                    ImGui.separator();
                    if (ImGui.menuItem("Save", "Ctrl+S")) {
                        mainApp.saveProject();
                    }
                    if (ImGui.menuItem("Save As...", "Ctrl+Shift+S")) {
                        mainApp.saveProjectAs();
                    }
                    ImGui.separator();
                    if (ImGui.menuItem("Exit", "Alt+F4")) {
                        glfwSetWindowShouldClose(window.getGLFWWindow(), true);
                    }
                    ImGui.endMenu();
                }
                
                if (ImGui.beginMenu("Edit")) {
                    if (ImGui.menuItem("Undo", "Ctrl+Z")) {
                        // TODO: Implement undo
                    }
                    if (ImGui.menuItem("Redo", "Ctrl+Y")) {
                        // TODO: Implement redo
                    }
                    ImGui.separator();
                    if (ImGui.menuItem("Cut", "Ctrl+X")) {
                        // TODO: Implement cut
                    }
                    if (ImGui.menuItem("Copy", "Ctrl+C")) {
                        // TODO: Implement copy
                    }
                    if (ImGui.menuItem("Paste", "Ctrl+V")) {
                        // TODO: Implement paste
                    }
                    if (ImGui.menuItem("Delete", "Del")) {
                        scene.removeSelectedObject();
                    }
                    ImGui.endMenu();
                }
                
                if (ImGui.beginMenu("View")) {
                    if (ImGui.menuItem("Toggle 3D Cursor", "Shift+C")) {
                        scene.toggle3DCursorVisibility();
                    }
                    if (ImGui.menuItem("Reset Camera", "F1")) {
                        scene.getCameraController().resetCamera();
                    }
                    ImGui.endMenu();
                }
                
                ImGui.sameLine(ImGui.getWindowWidth() - 200);
                ImGui.text("Project: " + projectName);
                
                ImGui.endMainMenuBar();
            }
        }

        private void renderFPSOverlay(float fps) {
            ImGui.setNextWindowPos(10, 40, ImGuiCond.Always);
            ImGui.setNextWindowSize(100, 30, ImGuiCond.Always);
            ImGui.begin("FPS Overlay", 
                ImGuiWindowFlags.NoTitleBar | 
                ImGuiWindowFlags.NoResize | 
                ImGuiWindowFlags.NoMove | 
                ImGuiWindowFlags.NoBackground);
            
            ImGui.text(String.format("FPS: %.1f", fps));
            ImGui.end();
        }
        
        private void renderModeSelector() {
            float xPos = 10;
            float yPos = 80;
            
            ImGui.setNextWindowPos(xPos, yPos, ImGuiCond.Always);
            ImGui.setNextWindowSize(150, 180, ImGuiCond.Always);
            
            ImGui.begin("##ModeSelector", 
                ImGuiWindowFlags.NoTitleBar | 
                ImGuiWindowFlags.NoResize | 
                ImGuiWindowFlags.NoMove | 
                ImGuiWindowFlags.NoBackground |
                ImGuiWindowFlags.NoDecoration);
            
            ImGui.text("Selection Mode:");
            
            if (ImGui.button("Standard", 130, 25)) {
                scene.setSelectionMode(Scene.SelectionMode.STANDARD);
            }
            
            if (ImGui.button("3D Cursor", 130, 25)) {
                scene.setSelectionMode(Scene.SelectionMode.CURSOR_3D);
            }
            
            ImGui.separator();
            ImGui.text("Cursor:");
            
            if (ImGui.button("Toggle (C)", 130, 25)) {
                scene.toggle3DCursorVisibility();
            }
            
            if (ImGui.button("Reset", 130, 25)) {
                scene.resetCursorToOrigin();
            }
            
            ImGui.end();
        }

        private void renderObjectTree() {
            float xPos = window.getWidth() - 320 - 10;
            float yPos = 40;
            ImGui.setNextWindowPos(xPos, yPos, ImGuiCond.Always);
            ImGui.setNextWindowSize(320, 400, ImGuiCond.Always);
            
            ImGui.begin("Object Tree", ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoMove);

            ImGui.text("Objects:");
            ImGui.separator();

            for (GameObject obj : scene.getGameObjects()) {
                String label = obj.getName();
                
                if (obj == scene.getSelectedObject()) {
                    label = "> " + label + " <";
                }

                if (ImGui.button(label, ImGui.getContentRegionAvailX(), 0)) {
                    scene.setSelectedObject(obj);
                }
            }

            ImGui.end();
        }
        
        private void renderObjectProperties() {
            GameObject selected = scene.getSelectedObject();
            
            float xPos = window.getWidth() - 320 - 10;
            float yPos = 450;
            
            ImGui.setNextWindowPos(xPos, yPos, ImGuiCond.Always);
            ImGui.setNextWindowSize(320, 300, ImGuiCond.Always);
            
            ImGui.begin("Object Properties", ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoMove);
            
            if (selected != null) {
                ImGui.text("Name: " + selected.getName());
                ImGui.separator();
                
                float[] pos = selected.getPosition();
                float[] rot = selected.getRotation();
                float[] scale = selected.getScale();
                
                // Обновляем временные значения
                tempPos[0] = pos[0];
                tempPos[1] = pos[1];
                tempPos[2] = pos[2];
                
                tempRot[0] = rot[0];
                tempRot[1] = rot[1];
                tempRot[2] = rot[2];
                
                tempScale[0] = scale[0];
                tempScale[1] = scale[1];
                tempScale[2] = scale[2];
                
                ImGui.text("Position:");
                if (ImGui.inputFloat3("##Position", tempPos)) {
                    scene.updateSelectedObjectPosition(tempPos[0], tempPos[1], tempPos[2]);
                }
                
                ImGui.text("Rotation:");
                if (ImGui.inputFloat3("##Rotation", tempRot)) {
                    scene.updateSelectedObjectRotation(tempRot[0], tempRot[1], tempRot[2]);
                }
                
                ImGui.text("Scale:");
                if (ImGui.inputFloat3("##Scale", tempScale)) {
                    scene.updateSelectedObjectScale(tempScale[0], tempScale[1], tempScale[2]);
                }
                
                ImGui.separator();
                ImGui.text("Transformations:");
                
                if (ImGui.button("Move (Ctrl+G)", 95, 30)) {
                    scene.toggleTranslationMode();
                }
                ImGui.sameLine();
                if (ImGui.button("Rotate (Ctrl+R)", 95, 30)) {
                    scene.toggleRotationMode();
                }
                ImGui.sameLine();
                if (ImGui.button("Scale (Ctrl+S)", 95, 30)) {
                    scene.toggleScalingMode();
                }
            } else {
                ImGui.text("No object selected");
                ImGui.separator();
                ImGui.text("Select an object from the tree");
                ImGui.text("or create a new one with Shift+A");
            }
            
            ImGui.end();
        }
        
        private void renderCreationMenu() {
            if (!inputHandler.isShowCreationMenu()) {
                return;
            }
            
            double posX = inputHandler.getMenuPosX();
            double posY = inputHandler.getMenuPosY();
            
            float imGuiPosX = (float) posX;
            float imGuiPosY = (float) posY;
            
            ImGui.setNextWindowPos(imGuiPosX, imGuiPosY, ImGuiCond.Appearing);
            ImGui.setNextWindowSize(200, 150, ImGuiCond.Appearing);
            
            boolean[] keepMenuOpen = {true};
            
            ImGui.begin("Create Object", 
                ImGuiWindowFlags.NoResize | 
                ImGuiWindowFlags.NoMove |
                ImGuiWindowFlags.NoCollapse);
            
            ImGui.text("Create:");
            ImGui.separator();
            
            if (ImGui.button("Cube", 180, 30)) {
                scene.createObject(GameObject.Type.REGULAR, "Cube");
                keepMenuOpen[0] = false;
            }
            
            if (ImGui.button("Light", 180, 30)) {
                scene.createObject(GameObject.Type.LIGHT, "Light");
                keepMenuOpen[0] = false;
            }
            
            ImGui.separator();
            
            if (ImGui.button("Cancel", 180, 30)) {
                keepMenuOpen[0] = false;
            }
            
            ImGui.end();
            
            if (!keepMenuOpen[0]) {
                inputHandler.setShowCreationMenu(false);
            }
        }

        public void cleanup() {
            imGuiGl3.dispose();
            imGuiGlfw.dispose();
            ImGui.destroyContext();
        }
        
        public void setScene(Scene scene) {
            this.scene = scene;
        }
    }

    public static class CameraController {
        private Camera camera;
        private float speed = 3.0f;
        private float mouseSensitivity = 0.1f;
        
        private float yaw = -90.0f;
        private float pitch = 0.0f;
        private float[] initialPosition = {0.0f, 2.0f, 5.0f};
        private float initialYaw = -90.0f;
        private float initialPitch = 0.0f;

        public CameraController() {
            camera = new Camera();
            updateCameraVectors();
        }

        public void handleMouseMovement(double xoffset, double yoffset) {
            xoffset *= mouseSensitivity;
            yoffset *= mouseSensitivity;

            yaw += (float) xoffset;
            pitch += (float) yoffset;

            if (pitch > 89.0f) pitch = 89.0f;
            if (pitch < -89.0f) pitch = -89.0f;

            updateCameraVectors();
        }

        public void handleScroll(double yoffset) {
            speed += (float) yoffset * 0.5f;
            
            if (speed < 0.1f) speed = 0.1f;
            if (speed > 10.0f) speed = 10.0f;
        }

        public void updateMovement(boolean[] keyStates, float deltaTime) {
            float velocity = speed * deltaTime;
            float[] cameraPos = camera.getPosition();
            float[] front = camera.getFrontVector();

            if (keyStates[GLFW_KEY_W]) {
                cameraPos[0] += front[0] * velocity;
                cameraPos[1] += front[1] * velocity;
                cameraPos[2] += front[2] * velocity;
            }
            
            if (keyStates[GLFW_KEY_S]) {
                cameraPos[0] -= front[0] * velocity;
                cameraPos[1] -= front[1] * velocity;
                cameraPos[2] -= front[2] * velocity;
            }
            
            if (keyStates[GLFW_KEY_A]) {
                float[] right = camera.getRightVector();
                cameraPos[0] -= right[0] * velocity;
                cameraPos[1] -= right[1] * velocity;
                cameraPos[2] -= right[2] * velocity;
            }
            
            if (keyStates[GLFW_KEY_D]) {
                float[] right = camera.getRightVector();
                cameraPos[0] += right[0] * velocity;
                cameraPos[1] += right[1] * velocity;
                cameraPos[2] += right[2] * velocity;
            }
        }
        
        public void resetCamera() {
            float[] pos = camera.getPosition();
            pos[0] = initialPosition[0];
            pos[1] = initialPosition[1];
            pos[2] = initialPosition[2];
            yaw = initialYaw;
            pitch = initialPitch;
            updateCameraVectors();
            System.out.println("Camera reset to initial position");
        }

        private void updateCameraVectors() {
            float yawRad = (float) Math.toRadians(yaw);
            float pitchRad = (float) Math.toRadians(pitch);

            float[] front = new float[3];
            front[0] = (float) (Math.cos(yawRad) * Math.cos(pitchRad));
            front[1] = (float) Math.sin(pitchRad);
            front[2] = (float) (Math.sin(yawRad) * Math.cos(pitchRad));
            
            camera.setFrontVector(normalize(front));
            camera.updateVectors();
        }

        private float[] normalize(float[] v) {
            float length = (float) Math.sqrt(v[0]*v[0] + v[1]*v[1] + v[2]*v[2]);
            if (length > 0) {
                v[0] /= length;
                v[1] /= length;
                v[2] /= length;
            }
            return v;
        }

        public Camera getCamera() { return camera; }
        public float getSpeed() { return speed; }
        public float getYaw() { return yaw; }
        public float getPitch() { return pitch; }
        public float[] getCameraPosition() { return camera.getPosition(); }
        
        public void setSpeed(float speed) { this.speed = speed; }
        public void setYaw(float yaw) { this.yaw = yaw; }
        public void setPitch(float pitch) { this.pitch = pitch; }
        public void setCameraPosition(float x, float y, float z) {
            float[] pos = camera.getPosition();
            pos[0] = x;
            pos[1] = y;
            pos[2] = z;
        }
    }

    public static class Camera {
        private float[] position = {0.0f, 2.0f, 5.0f};
        private float[] front = {0.0f, 0.0f, -1.0f};
        private float[] up = {0.0f, 1.0f, 0.0f};
        private float[] right = {1.0f, 0.0f, 0.0f};
        private float[] worldUp = {0.0f, 1.0f, 0.0f};

        public void updateVectors() {
            right = normalize(crossProduct(front, worldUp));
            up = normalize(crossProduct(right, front));
        }

        public float[] getViewMatrix() {
            float[] viewMatrix = new float[16];
            
            float[] cameraPos = position;
            float[] cameraFront = front;
            float[] cameraUp = up;

            float[] z = normalize(new float[]{-cameraFront[0], -cameraFront[1], -cameraFront[2]});
            float[] x = normalize(crossProduct(cameraUp, z));
            float[] y = crossProduct(z, x);

            viewMatrix[0] = x[0]; viewMatrix[1] = y[0]; viewMatrix[2] = z[0]; viewMatrix[3] = 0.0f;
            viewMatrix[4] = x[1]; viewMatrix[5] = y[1]; viewMatrix[6] = z[1]; viewMatrix[7] = 0.0f;
            viewMatrix[8] = x[2]; viewMatrix[9] = y[2]; viewMatrix[10] = z[2]; viewMatrix[11] = 0.0f;
            viewMatrix[12] = -dotProduct(x, cameraPos);
            viewMatrix[13] = -dotProduct(y, cameraPos);
            viewMatrix[14] = -dotProduct(z, cameraPos);
            viewMatrix[15] = 1.0f;

            return viewMatrix;
        }

        public float[] getProjectionMatrix(int width, int height) {
            float[] projectionMatrix = new float[16];
            float aspect = (float) width / height;
            float fov = (float) Math.toRadians(45.0f);
            float near = 0.1f;
            float far = 100.0f;

            float f = (float) (1.0 / Math.tan(fov / 2.0));

            projectionMatrix[0] = f / aspect;
            projectionMatrix[5] = f;
            projectionMatrix[10] = (far + near) / (near - far);
            projectionMatrix[11] = -1.0f;
            projectionMatrix[14] = (2.0f * far * near) / (near - far);
            projectionMatrix[15] = 0.0f;

            return projectionMatrix;
        }
        
        public float[] screenToWorldRay(int screenX, int screenY, int screenWidth, int screenHeight) {
            float x = (2.0f * screenX) / screenWidth - 1.0f;
            float y = 1.0f - (2.0f * screenY) / screenHeight;
            
            float[] rayClip = {x, y, -1.0f, 1.0f};
            
            float[] rayEye = multiplyMatrixVector(inverseProjectionMatrix(screenWidth, screenHeight), rayClip);
            rayEye[2] = -1.0f;
            rayEye[3] = 0.0f;
            
            float[] rayWorld = multiplyMatrixVector(inverseViewMatrix(), rayEye);
            float[] rayDir = {rayWorld[0], rayWorld[1], rayWorld[2]};
            
            float length = (float)Math.sqrt(rayDir[0]*rayDir[0] + rayDir[1]*rayDir[1] + rayDir[2]*rayDir[2]);
            if (length > 0) {
                rayDir[0] /= length;
                rayDir[1] /= length;
                rayDir[2] /= length;
            }
            
            return rayDir;
        }
        
        private float[] inverseProjectionMatrix(int width, int height) {
            float[] proj = getProjectionMatrix(width, height);
            float[] inv = new float[16];
            
            inv[0] = 1.0f / proj[0];
            inv[5] = 1.0f / proj[5];
            inv[10] = 0.0f;
            inv[11] = 1.0f / proj[14];
            inv[14] = 1.0f;
            inv[15] = -proj[10] / proj[14];
            
            return inv;
        }
        
        private float[] inverseViewMatrix() {
            float[] view = getViewMatrix();
            float[] inv = new float[16];
            
            inv[0] = view[0]; inv[1] = view[4]; inv[2] = view[8];
            inv[4] = view[1]; inv[5] = view[5]; inv[6] = view[9];
            inv[8] = view[2]; inv[9] = view[6]; inv[10] = view[10];
            
            inv[12] = -(view[12] * view[0] + view[13] * view[1] + view[14] * view[2]);
            inv[13] = -(view[12] * view[4] + view[13] * view[5] + view[14] * view[6]);
            inv[14] = -(view[12] * view[8] + view[13] * view[9] + view[14] * view[10]);
            
            inv[3] = 0.0f; inv[7] = 0.0f; inv[11] = 0.0f; inv[15] = 1.0f;
            
            return inv;
        }
        
        private float[] multiplyMatrixVector(float[] m, float[] v) {
            float[] result = new float[4];
            result[0] = m[0] * v[0] + m[4] * v[1] + m[8] * v[2] + m[12] * v[3];
            result[1] = m[1] * v[0] + m[5] * v[1] + m[9] * v[2] + m[13] * v[3];
            result[2] = m[2] * v[0] + m[6] * v[1] + m[10] * v[2] + m[14] * v[3];
            result[3] = m[3] * v[0] + m[7] * v[1] + m[11] * v[2] + m[15] * v[3];
            return result;
        }

        private float[] crossProduct(float[] a, float[] b) {
            return new float[]{
                a[1] * b[2] - a[2] * b[1],
                a[2] * b[0] - a[0] * b[2],
                a[0] * b[1] - a[1] * b[0]
            };
        }

        private float dotProduct(float[] a, float[] b) {
            return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
        }

        private float[] normalize(float[] v) {
            float length = (float) Math.sqrt(v[0]*v[0] + v[1]*v[1] + v[2]*v[2]);
            if (length > 0) {
                v[0] /= length;
                v[1] /= length;
                v[2] /= length;
            }
            return v;
        }

        public float[] getPosition() { return position; }
        public float[] getFrontVector() { return front; }
        public float[] getUpVector() { return up; }
        public float[] getRightVector() { return right; }
        public void setFrontVector(float[] front) { this.front = front; }
        public void setPosition(float[] position) { this.position = position; }
    }

    public static class GameObject {
        public enum Type { REGULAR, LIGHT }

        private float[] position = new float[3];
        private float[] scale = new float[3];
        private float[] rotation = new float[3];
        private Material material;
        private int id;
        private Type type;
        private String name;

        public GameObject(float x, float y, float z, float sx, float sy, float sz, 
                         Material mat, int id, Type type, String name) {
            this.position[0] = x;
            this.position[1] = y;
            this.position[2] = z;
            this.scale[0] = sx;
            this.scale[1] = sy;
            this.scale[2] = sz;
            this.rotation[0] = 0.0f;
            this.rotation[1] = 0.0f;
            this.rotation[2] = 0.0f;
            this.material = mat;
            this.id = id;
            this.type = type;
            this.name = name;
        }

        public float[] getModelMatrix() {
            float[] matrix = new float[16];
            
            for (int i = 0; i < 16; i++) matrix[i] = 0.0f;
            matrix[0] = 1.0f; matrix[5] = 1.0f; matrix[10] = 1.0f; matrix[15] = 1.0f;

            matrix[0] *= scale[0];
            matrix[5] *= scale[1];
            matrix[10] *= scale[2];

            if (rotation[0] != 0.0f || rotation[1] != 0.0f || rotation[2] != 0.0f) {
                float[] rotationMatrix = createRotationMatrix();
                matrix = multiplyMatrices(matrix, rotationMatrix);
            }

            matrix[12] = position[0];
            matrix[13] = position[1];
            matrix[14] = position[2];

            return matrix;
        }

        private float[] createRotationMatrix() {
            float[] matrix = new float[16];
            
            for (int i = 0; i < 16; i++) matrix[i] = 0.0f;
            matrix[0] = 1.0f; matrix[5] = 1.0f; matrix[10] = 1.0f; matrix[15] = 1.0f;

            float rx = (float) Math.toRadians(rotation[0]);
            float ry = (float) Math.toRadians(rotation[1]);
            float rz = (float) Math.toRadians(rotation[2]);

            if (rx != 0.0f) {
                float[] rotX = new float[16];
                for (int i = 0; i < 16; i++) rotX[i] = 0.0f;
                rotX[0] = 1.0f;
                rotX[5] = (float) Math.cos(rx); rotX[6] = (float) Math.sin(rx);
                rotX[9] = (float) -Math.sin(rx); rotX[10] = (float) Math.cos(rx);
                rotX[15] = 1.0f;
                matrix = multiplyMatrices(matrix, rotX);
            }

            if (ry != 0.0f) {
                float[] rotY = new float[16];
                for (int i = 0; i < 16; i++) rotY[i] = 0.0f;
                rotY[0] = (float) Math.cos(ry); rotY[2] = (float) -Math.sin(ry);
                rotY[5] = 1.0f;
                rotY[8] = (float) Math.sin(ry); rotY[10] = (float) Math.cos(ry);
                rotY[15] = 1.0f;
                matrix = multiplyMatrices(matrix, rotY);
            }

            if (rz != 0.0f) {
                float[] rotZ = new float[16];
                for (int i = 0; i < 16; i++) rotZ[i] = 0.0f;
                rotZ[0] = (float) Math.cos(rz); rotZ[1] = (float) Math.sin(rz);
                rotZ[4] = (float) -Math.sin(rz); rotZ[5] = (float) Math.cos(rz);
                rotZ[10] = 1.0f;
                rotZ[15] = 1.0f;
                matrix = multiplyMatrices(matrix, rotZ);
            }

            return matrix;
        }

        private float[] multiplyMatrices(float[] a, float[] b) {
            float[] result = new float[16];
            for (int i = 0; i < 4; i++) {
                for (int j = 0; j < 4; j++) {
                    result[i*4+j] = 0.0f;
                    for (int k = 0; k < 4; k++) {
                        result[i*4+j] += a[i*4+k] * b[k*4+j];
                    }
                }
            }
            return result;
        }

        public void translate(float dx, float dy, float dz) {
            position[0] += dx;
            position[1] += dy;
            position[2] += dz;
        }

        public void rotateX(float angle) {
            rotation[0] += angle;
            rotation[0] = rotation[0] % 360.0f;
        }

        public void rotateY(float angle) {
            rotation[1] += angle;
            rotation[1] = rotation[1] % 360.0f;
        }

        public void rotateZ(float angle) {
            rotation[2] += angle;
            rotation[2] = rotation[2] % 360.0f;
        }

        public void scale(float factor) {
            scale[0] *= factor;
            scale[1] *= factor;
            scale[2] *= factor;
            
            if (scale[0] < 0.1f) scale[0] = 0.1f;
            if (scale[1] < 0.1f) scale[1] = 0.1f;
            if (scale[2] < 0.1f) scale[2] = 0.1f;
        }

        public float[] getPosition() { return position.clone(); }
        public float[] getScale() { return scale.clone(); }
        public float[] getRotation() { return rotation.clone(); }
        public Material getMaterial() { return material; }
        public Type getType() { return type; }
        public String getName() { return name; }
        public int getId() { return id; }
        
        public void setPosition(float x, float y, float z) {
            position[0] = x;
            position[1] = Math.max(0.1f, y);
            position[2] = z;
        }
        
        public void setRotation(float x, float y, float z) {
            rotation[0] = x % 360.0f;
            rotation[1] = y % 360.0f;
            rotation[2] = z % 360.0f;
        }
        
        public void setScale(float x, float y, float z) {
            scale[0] = Math.max(0.1f, x);
            scale[1] = Math.max(0.1f, y);
            scale[2] = Math.max(0.1f, z);
        }
        
        public void cleanup() {
            // Cleanup resources if needed
        }
    }

    public static class Material {
        private float[] ambient;
        private float[] diffuse;
        private float[] specular;
        private float shininess;

        public Material(float[] ambient, float[] diffuse, float[] specular, float shininess) {
            this.ambient = ambient;
            this.diffuse = diffuse;
            this.specular = specular;
            this.shininess = shininess;
        }

        public float[] getAmbient() { return ambient; }
        public float[] getDiffuse() { return diffuse; }
        public float[] getSpecular() { return specular; }
        public float getShininess() { return shininess; }
    }

    private static class ObjectPicker {
        private ByteBuffer pixelBuffer = BufferUtils.createByteBuffer(4);

        public GameObject performPicking(Scene scene, ShaderManager shaderManager, GeometryManager geometryManager, 
                                       Window window, int mouseX, int mouseY) {
            glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            shaderManager.usePickingShader();

            Camera camera = scene.getCameraController().getCamera();
            shaderManager.setViewMatrix(camera.getViewMatrix());
            shaderManager.setProjectionMatrix(camera.getProjectionMatrix(window.getWidth(), window.getHeight()));

            for (GameObject obj : scene.getGameObjects()) {
                int objectID = obj.getId();
                
                float r = ((objectID >> 16) & 0xFF) / 255.0f;
                float g = ((objectID >> 8) & 0xFF) / 255.0f;
                float b = (objectID & 0xFF) / 255.0f;

                shaderManager.setObjectColor(r, g, b);

                if (obj.getType() == GameObject.Type.REGULAR) {
                    geometryManager.renderCube(obj.getModelMatrix());
                } else {
                    geometryManager.renderLight(obj.getModelMatrix());
                }
            }

            glReadPixels(mouseX, window.getHeight() - mouseY, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixelBuffer);

            shaderManager.useMainShader();
            glClearColor(0.1f, 0.1f, 0.1f, 1.0f);

            int pickedID = -1;
            if (pixelBuffer != null) {
                pixelBuffer.rewind();
                int r = pixelBuffer.get(0) & 0xFF;
                int g = pixelBuffer.get(1) & 0xFF;
                int b = pixelBuffer.get(2) & 0xFF;
                pickedID = (r << 16) | (g << 8) | b;
            }

            for (GameObject obj : scene.getGameObjects()) {
                if (obj.getId() == pickedID) {
                    return obj;
                }
            }

            return null;
        }
        
        public float[] performRaycast(Scene scene, Window window, int mouseX, int mouseY) {
            Camera camera = scene.getCameraController().getCamera();
            
            float[] rayDir = camera.screenToWorldRay(mouseX, mouseY, window.getWidth(), window.getHeight());
            float[] rayOrigin = camera.getPosition();
            
            float t = -rayOrigin[1] / rayDir[1];
            
            if (t > 0) {
                float[] intersection = new float[3];
                intersection[0] = rayOrigin[0] + rayDir[0] * t;
                intersection[1] = 0.0f;
                intersection[2] = rayOrigin[2] + rayDir[2] * t;
                return intersection;
            } else {
                float defaultDistance = 10.0f;
                float[] intersection = new float[3];
                intersection[0] = rayOrigin[0] + rayDir[0] * defaultDistance;
                intersection[1] = rayOrigin[1] + rayDir[1] * defaultDistance;
                intersection[2] = rayOrigin[2] + rayDir[2] * defaultDistance;
                return intersection;
            }
        }

        public void cleanup() {
            // Cleanup
        }
    }

    private static class ShaderManager {
        private int mainShaderProgram;
        private int pickingShaderProgram;
        private int cursorShaderProgram;

        public ShaderManager() {
            mainShaderProgram = compileMainShader();
            pickingShaderProgram = compilePickingShader();
            cursorShaderProgram = compileCursorShader();
        }

        public void useMainShader() {
            glUseProgram(mainShaderProgram);
        }

        public void usePickingShader() {
            glUseProgram(pickingShaderProgram);
        }
        
        public void useCursorShader() {
            glUseProgram(cursorShaderProgram);
        }

        public void setViewMatrix(float[] viewMatrix) {
            int program = getCurrentProgram();
            int location = glGetUniformLocation(program, "view");
            if (location != -1) {
                glUniformMatrix4fv(location, false, viewMatrix);
            }
        }

        public void setProjectionMatrix(float[] projectionMatrix) {
            int program = getCurrentProgram();
            int location = glGetUniformLocation(program, "projection");
            if (location != -1) {
                glUniformMatrix4fv(location, false, projectionMatrix);
            }
        }

        public void setViewPos(float[] viewPos) {
            int location = glGetUniformLocation(mainShaderProgram, "viewPos");
            if (location != -1) {
                glUniform3f(location, viewPos[0], viewPos[1], viewPos[2]);
            }
        }

        public void setLightPosition(float[] lightPos) {
            int location = glGetUniformLocation(mainShaderProgram, "light.position");
            if (location != -1) {
                glUniform3f(location, lightPos[0], lightPos[1], lightPos[2]);
            }
        }

        public void setLightProperties(float[] ambient, float[] diffuse, float[] specular) {
            int ambientLoc = glGetUniformLocation(mainShaderProgram, "light.ambient");
            int diffuseLoc = glGetUniformLocation(mainShaderProgram, "light.diffuse");
            int specularLoc = glGetUniformLocation(mainShaderProgram, "light.specular");
            
            if (ambientLoc != -1) glUniform3f(ambientLoc, ambient[0], ambient[1], ambient[2]);
            if (diffuseLoc != -1) glUniform3f(diffuseLoc, diffuse[0], diffuse[1], diffuse[2]);
            if (specularLoc != -1) glUniform3f(specularLoc, specular[0], specular[1], specular[2]);
        }

        public void setMaterial(Material material) {
            if (material != null) {
                int ambientLoc = glGetUniformLocation(mainShaderProgram, "material.ambient");
                int diffuseLoc = glGetUniformLocation(mainShaderProgram, "material.diffuse");
                int specularLoc = glGetUniformLocation(mainShaderProgram, "material.specular");
                int shininessLoc = glGetUniformLocation(mainShaderProgram, "material.shininess");
                
                if (ambientLoc != -1) glUniform3f(ambientLoc, material.getAmbient()[0], material.getAmbient()[1], material.getAmbient()[2]);
                if (diffuseLoc != -1) glUniform3f(diffuseLoc, material.getDiffuse()[0], material.getDiffuse()[1], material.getDiffuse()[2]);
                if (specularLoc != -1) glUniform3f(specularLoc, material.getSpecular()[0], material.getSpecular()[1], material.getSpecular()[2]);
                if (shininessLoc != -1) glUniform1f(shininessLoc, material.getShininess());
            }
        }

        public void setObjectColor(float r, float g, float b) {
            int program = getCurrentProgram();
            int location = glGetUniformLocation(program, "objectColor");
            if (location != -1) {
                glUniform3f(location, r, g, b);
            }
        }

        public void setUseLighting(boolean useLighting) {
            int location = glGetUniformLocation(mainShaderProgram, "useLighting");
            if (location != -1) {
                glUniform1i(location, useLighting ? 1 : 0);
            }
        }

        private int getCurrentProgram() {
            return glGetInteger(GL_CURRENT_PROGRAM);
        }

        private int compileMainShader() {
            String vertexSource = loadShaderSource("vertex.glsl");
            String fragmentSource = loadShaderSource("fragment.glsl");
            
            if (vertexSource == null || fragmentSource == null) {
                System.out.println("Shader files not found, using built-in shaders");
                vertexSource = VERTEX_SHADER_SOURCE;
                fragmentSource = FRAGMENT_SHADER_SOURCE;
            } else {
                System.out.println("Shader files loaded successfully");
            }
            
            return createShaderProgram(vertexSource, fragmentSource);
        }

        private int compilePickingShader() {
            String vertexShader = "#version 330 core\n" +
                "layout (location = 0) in vec3 aPos;\n" +
                "uniform mat4 model;\n" +
                "uniform mat4 view;\n" +
                "uniform mat4 projection;\n" +
                "void main() {\n" +
                "    gl_Position = projection * view * model * vec4(aPos, 1.0);\n" +
                "}";

            String fragmentShader = "#version 330 core\n" +
                "out vec4 FragColor;\n" +
                "uniform vec3 objectColor;\n" +
                "void main() {\n" +
                "    FragColor = vec4(objectColor, 1.0);\n" +
                "}";

            return createShaderProgram(vertexShader, fragmentShader);
        }
        
        private int compileCursorShader() {
            String vertexShader = "#version 330 core\n" +
                "layout (location = 0) in vec3 aPos;\n" +
                "uniform mat4 model;\n" +
                "uniform mat4 view;\n" +
                "uniform mat4 projection;\n" +
                "void main() {\n" +
                "    gl_Position = projection * view * model * vec4(aPos, 1.0);\n" +
                "}";

            String fragmentShader = "#version 330 core\n" +
                "out vec4 FragColor;\n" +
                "uniform vec3 objectColor;\n" +
                "void main() {\n" +
                "    FragColor = vec4(objectColor, 1.0);\n" +
                "}";

            return createShaderProgram(vertexShader, fragmentShader);
        }

        private int createShaderProgram(String vertexSource, String fragmentSource) {
            int vertexShader = glCreateShader(GL_VERTEX_SHADER);
            glShaderSource(vertexShader, vertexSource);
            glCompileShader(vertexShader);
            checkShaderCompilation(vertexShader, "VERTEX");
            
            int fragmentShader = glCreateShader(GL_FRAGMENT_SHADER);
            glShaderSource(fragmentShader, fragmentSource);
            glCompileShader(fragmentShader);
            checkShaderCompilation(fragmentShader, "FRAGMENT");
            
            int program = glCreateProgram();
            glAttachShader(program, vertexShader);
            glAttachShader(program, fragmentShader);
            glLinkProgram(program);
            checkProgramLinking(program);
            
            glDeleteShader(vertexShader);
            glDeleteShader(fragmentShader);
            
            return program;
        }

        private void checkShaderCompilation(int shader, String type) {
            int success = glGetShaderi(shader, GL_COMPILE_STATUS);
            if (success == GL_FALSE) {
                int length = glGetShaderi(shader, GL_INFO_LOG_LENGTH);
                String infoLog = glGetShaderInfoLog(shader, length);
                System.err.println(type + " SHADER COMPILATION ERROR:\n" + infoLog);
            }
        }

        private void checkProgramLinking(int program) {
            int success = glGetProgrami(program, GL_LINK_STATUS);
            if (success == GL_FALSE) {
                int length = glGetProgrami(program, GL_INFO_LOG_LENGTH);
                String infoLog = glGetProgramInfoLog(program, length);
                System.err.println("PROGRAM LINKING ERROR:\n" + infoLog);
            }
        }

        private String loadShaderSource(String filename) {
            try {
                // Пробуем загрузить из src/main/resources/shaders/
                java.nio.file.Path filePath = java.nio.file.Paths.get("src/main/resources/shaders/" + filename);
                if (java.nio.file.Files.exists(filePath)) {
                    return new String(java.nio.file.Files.readAllBytes(filePath));
                }
                
                // Пробуем загрузить из classpath
                InputStream inputStream = getClass().getClassLoader().getResourceAsStream("shaders/" + filename);
                if (inputStream != null) {
                    return new String(inputStream.readAllBytes());
                }
                
                System.err.println("Shader file not found: " + filename);
                return null;
            } catch (IOException e) {
                System.err.println("Failed to load shader: " + filename);
                return null;
            }
        }

        public void cleanup() {
            if (mainShaderProgram != 0) glDeleteProgram(mainShaderProgram);
            if (pickingShaderProgram != 0) glDeleteProgram(pickingShaderProgram);
            if (cursorShaderProgram != 0) glDeleteProgram(cursorShaderProgram);
        }

        private static final String VERTEX_SHADER_SOURCE = "#version 330 core\n" +
            "layout (location = 0) in vec3 aPos;\n" +
            "layout (location = 1) in vec3 aNormal;\n" +
            "out vec3 FragPos;\n" +
            "out vec3 Normal;\n" +
            "uniform mat4 model;\n" +
            "uniform mat4 view;\n" +
            "uniform mat4 projection;\n" +
            "void main() {\n" +
            "    FragPos = vec3(model * vec4(aPos, 1.0));\n" +
            "    Normal = mat3(transpose(inverse(model))) * aNormal;\n" +
            "    gl_Position = projection * view * vec4(FragPos, 1.0);\n" +
            "}";

        private static final String FRAGMENT_SHADER_SOURCE = "#version 330 core\n" +
            "out vec4 FragColor;\n" +
            "in vec3 FragPos;\n" +
            "in vec3 Normal;\n" +
            "uniform vec3 objectColor;\n" +
            "uniform vec3 viewPos;\n" +
            "struct Material {\n" +
            "    vec3 ambient;\n" +
            "    vec3 diffuse;\n" +
            "    vec3 specular;\n" +
            "    float shininess;\n" +
            "};\n" +
            "struct Light {\n" +
            "    vec3 position;\n" +
            "    vec3 ambient;\n" +
            "    vec3 diffuse;\n" +
            "    vec3 specular;\n" +
            "};\n" +
            "uniform Material material;\n" +
            "uniform Light light;\n" +
            "uniform bool useLighting;\n" +
            "void main() {\n" +
            "    if (!useLighting) {\n" +
            "        FragColor = vec4(objectColor, 1.0);\n" +
            "        return;\n" +
            "    }\n" +
            "    vec3 ambient = light.ambient * material.ambient;\n" +
            "    vec3 norm = normalize(Normal);\n" +
            "    vec3 lightDir = normalize(light.position - FragPos);\n" +
            "    float diff = max(dot(norm, lightDir), 0.0);\n" +
            "    vec3 diffuse = light.diffuse * (diff * material.diffuse);\n" +
            "    vec3 viewDir = normalize(viewPos - FragPos);\n" +
            "    vec3 reflectDir = reflect(-lightDir, norm);\n" +
            "    float spec = pow(max(dot(viewDir, reflectDir), 0.0), material.shininess);\n" +
            "    vec3 specular = light.specular * (spec * material.specular);\n" +
            "    vec3 result = (ambient + diffuse + specular) * objectColor;\n" +
            "    FragColor = vec4(result, 1.0);\n" +
            "}";
    }

    private static class GeometryManager {
        private int cubeVAO, cubeVBO;
        private int gridVAO, gridVBO;
        private int lightVAO, lightVBO, lightEBO;
        private int cursorVAO, cursorVBO;
        private float[] sphereVertices;
        private int[] sphereIndices;

        public GeometryManager() {
            setupGeometry();
        }

        private void setupGeometry() {
            setupCube();
            setupGrid();
            setupLightSphere();
            setup3DCursor();
        }

        private void setupCube() {
            float[] cubeVertices = {
                -0.5f, -0.5f, -0.5f,  0.0f, 0.0f, -1.0f, 0.5f, -0.5f, -0.5f,  0.0f, 0.0f, -1.0f,
                0.5f,  0.5f, -0.5f,  0.0f, 0.0f, -1.0f, 0.5f,  0.5f, -0.5f,  0.0f, 0.0f, -1.0f,
                -0.5f,  0.5f, -0.5f,  0.0f, 0.0f, -1.0f, -0.5f, -0.5f, -0.5f,  0.0f, 0.0f, -1.0f,
                
                -0.5f, -0.5f,  0.5f,  0.0f, 0.0f, 1.0f, 0.5f, -0.5f,  0.5f,  0.0f, 0.0f, 1.0f,
                0.5f,  0.5f,  0.5f,  0.0f, 0.0f, 1.0f, 0.5f,  0.5f,  0.5f,  0.0f, 0.0f, 1.0f,
                -0.5f,  0.5f,  0.5f,  0.0f, 0.0f, 1.0f, -0.5f, -0.5f,  0.5f,  0.0f, 0.0f, 1.0f,
                
                -0.5f,  0.5f,  0.5f, -1.0f, 0.0f, 0.0f, -0.5f,  0.5f, -0.5f, -1.0f, 0.0f, 0.0f,
                -0.5f, -0.5f, -0.5f, -1.0f, 0.0f, 0.0f, -0.5f, -0.5f, -0.5f, -1.0f, 0.0f, 0.0f,
                -0.5f, -0.5f,  0.5f, -1.0f, 0.0f, 0.0f, -0.5f,  0.5f,  0.5f, -1.0f, 0.0f, 0.0f,
                
                0.5f,  0.5f,  0.5f,  1.0f, 0.0f, 0.0f, 0.5f,  0.5f, -0.5f,  1.0f, 0.0f, 0.0f,
                0.5f, -0.5f, -0.5f,  1.0f, 0.0f, 0.0f, 0.5f, -0.5f, -0.5f,  1.0f, 0.0f, 0.0f,
                0.5f, -0.5f,  0.5f,  1.0f, 0.0f, 0.0f, 0.5f,  0.5f,  0.5f,  1.0f, 0.0f, 0.0f,
                
                -0.5f, -0.5f, -0.5f,  0.0f, -1.0f, 0.0f, 0.5f, -0.5f, -0.5f,  0.0f, -1.0f, 0.0f,
                0.5f, -0.5f,  0.5f,  0.0f, -1.0f, 0.0f, 0.5f, -0.5f,  0.5f,  0.0f, -1.0f, 0.0f,
                -0.5f, -0.5f,  0.5f,  0.0f, -1.0f, 0.0f, -0.5f, -0.5f, -0.5f,  0.0f, -1.0f, 0.0f,
                
                -0.5f,  0.5f, -0.5f,  0.0f, 1.0f, 0.0f, 0.5f,  0.5f, -0.5f,  0.0f, 1.0f, 0.0f,
                0.5f,  0.5f,  0.5f,  0.0f, 1.0f, 0.0f, 0.5f,  0.5f,  0.5f,  0.0f, 1.0f, 0.0f,
                -0.5f,  0.5f,  0.5f,  0.0f, 1.0f, 0.0f, -0.5f,  0.5f, -0.5f,  0.0f, 1.0f, 0.0f
            };

            cubeVAO = glGenVertexArrays();
            cubeVBO = glGenBuffers();

            glBindVertexArray(cubeVAO);
            glBindBuffer(GL_ARRAY_BUFFER, cubeVBO);
            glBufferData(GL_ARRAY_BUFFER, cubeVertices, GL_STATIC_DRAW);

            glVertexAttribPointer(0, 3, GL_FLOAT, false, 6 * Float.BYTES, 0);
            glEnableVertexAttribArray(0);
            glVertexAttribPointer(1, 3, GL_FLOAT, false, 6 * Float.BYTES, 3 * Float.BYTES);
            glEnableVertexAttribArray(1);
        }

        private void setupGrid() {
            float gridSize = 10.0f;
            int gridLines = 20;
            List<Float> gridVertices = new ArrayList<>();

            for (int i = 0; i <= gridLines; i++) {
                float z = -gridSize + (2 * gridSize * i / gridLines);
                gridVertices.add(-gridSize); gridVertices.add(0.0f); gridVertices.add(z);
                gridVertices.add(gridSize); gridVertices.add(0.0f); gridVertices.add(z);
            }

            for (int i = 0; i <= gridLines; i++) {
                float x = -gridSize + (2 * gridSize * i / gridLines);
                gridVertices.add(x); gridVertices.add(0.0f); gridVertices.add(-gridSize);
                gridVertices.add(x); gridVertices.add(0.0f); gridVertices.add(gridSize);
            }

            float[] gridArray = new float[gridVertices.size()];
            for (int i = 0; i < gridVertices.size(); i++) {
                gridArray[i] = gridVertices.get(i);
            }

            gridVAO = glGenVertexArrays();
            gridVBO = glGenBuffers();

            glBindVertexArray(gridVAO);
            glBindBuffer(GL_ARRAY_BUFFER, gridVBO);
            glBufferData(GL_ARRAY_BUFFER, gridArray, GL_STATIC_DRAW);
            glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0);
            glEnableVertexAttribArray(0);
        }

        private void setupLightSphere() {
            createSphereGeometry(0.5f, 16, 16);

            lightVAO = glGenVertexArrays();
            lightVBO = glGenBuffers();
            lightEBO = glGenBuffers();

            glBindVertexArray(lightVAO);
            glBindBuffer(GL_ARRAY_BUFFER, lightVBO);
            glBufferData(GL_ARRAY_BUFFER, sphereVertices, GL_STATIC_DRAW);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, lightEBO);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, sphereIndices, GL_STATIC_DRAW);
            glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0);
            glEnableVertexAttribArray(0);
        }
        
        private void setup3DCursor() {
            float size = 0.5f;
            float[] cursorVertices = {
                -size, 0.0f, 0.0f,
                size, 0.0f, 0.0f,
                
                0.0f, -size, 0.0f,
                0.0f, size, 0.0f,
                
                0.0f, 0.0f, -size,
                0.0f, 0.0f, size,
                
                0.0f, size, 0.0f,
                size * 0.7f, size * 0.7f, 0.0f,
                size * 0.7f, size * 0.7f, 0.0f,
                size, 0.0f, 0.0f,
                size, 0.0f, 0.0f,
                size * 0.7f, -size * 0.7f, 0.0f,
                size * 0.7f, -size * 0.7f, 0.0f,
                0.0f, -size, 0.0f,
                0.0f, -size, 0.0f,
                -size * 0.7f, -size * 0.7f, 0.0f,
                -size * 0.7f, -size * 0.7f, 0.0f,
                -size, 0.0f, 0.0f,
                -size, 0.0f, 0.0f,
                -size * 0.7f, size * 0.7f, 0.0f,
                -size * 0.7f, size * 0.7f, 0.0f,
                0.0f, size, 0.0f,
            };

            cursorVAO = glGenVertexArrays();
            cursorVBO = glGenBuffers();

            glBindVertexArray(cursorVAO);
            glBindBuffer(GL_ARRAY_BUFFER, cursorVBO);
            glBufferData(GL_ARRAY_BUFFER, cursorVertices, GL_STATIC_DRAW);
            glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0);
            glEnableVertexAttribArray(0);
        }

        private void createSphereGeometry(float radius, int sectors, int stacks) {
            List<Float> vertices = new ArrayList<>();
            List<Integer> indices = new ArrayList<>();

            float sectorStep = (float) (2 * Math.PI / sectors);
            float stackStep = (float) (Math.PI / stacks);

            for (int i = 0; i <= stacks; ++i) {
                float stackAngle = (float) (Math.PI / 2 - i * stackStep);
                float xy = radius * (float) Math.cos(stackAngle);
                float z = radius * (float) Math.sin(stackAngle);

                for (int j = 0; j <= sectors; ++j) {
                    float sectorAngle = j * sectorStep;
                    float x = xy * (float) Math.cos(sectorAngle);
                    float y = xy * (float) Math.sin(sectorAngle);
                    vertices.add(x); vertices.add(y); vertices.add(z);
                }
            }

            for (int i = 0; i < stacks; ++i) {
                int k1 = i * (sectors + 1);
                int k2 = k1 + sectors + 1;

                for (int j = 0; j < sectors; ++j, ++k1, ++k2) {
                    if (i != 0) {
                        indices.add(k1); indices.add(k2); indices.add(k1 + 1);
                    }
                    if (i != (stacks - 1)) {
                        indices.add(k1 + 1); indices.add(k2); indices.add(k2 + 1);
                    }
                }
            }

            sphereVertices = new float[vertices.size()];
            for (int i = 0; i < vertices.size(); i++) sphereVertices[i] = vertices.get(i);

            sphereIndices = new int[indices.size()];
            for (int i = 0; i < indices.size(); i++) sphereIndices[i] = indices.get(i);
        }

        public void renderCube(float[] modelMatrix) {
            int modelLoc = glGetUniformLocation(glGetInteger(GL_CURRENT_PROGRAM), "model");
            glUniformMatrix4fv(modelLoc, false, modelMatrix);
            glBindVertexArray(cubeVAO);
            glDrawArrays(GL_TRIANGLES, 0, 36);
        }

        public void renderLight(float[] modelMatrix) {
            int modelLoc = glGetUniformLocation(glGetInteger(GL_CURRENT_PROGRAM), "model");
            glUniformMatrix4fv(modelLoc, false, modelMatrix);
            glBindVertexArray(lightVAO);
            glDrawElements(GL_TRIANGLES, sphereIndices.length, GL_UNSIGNED_INT, 0);
        }

        public void renderGrid() {
            float[] modelMatrix = new float[16];
            modelMatrix[0] = 1.0f; modelMatrix[5] = 1.0f; modelMatrix[10] = 1.0f; modelMatrix[15] = 1.0f;
            
            int modelLoc = glGetUniformLocation(glGetInteger(GL_CURRENT_PROGRAM), "model");
            glUniformMatrix4fv(modelLoc, false, modelMatrix);
            glBindVertexArray(gridVAO);
            glDrawArrays(GL_LINES, 0, 84);
        }
        
        public void render3DCursor(float[] position, float size, ShaderManager shaderManager, 
                                  Camera camera, Window window) {
            shaderManager.useCursorShader();
            
            shaderManager.setViewMatrix(camera.getViewMatrix());
            shaderManager.setProjectionMatrix(camera.getProjectionMatrix(window.getWidth(), window.getHeight()));
            
            float[] modelMatrix = new float[16];
            modelMatrix[0] = size; modelMatrix[5] = size; modelMatrix[10] = size; modelMatrix[15] = 1.0f;
            modelMatrix[12] = position[0];
            modelMatrix[13] = position[1];
            modelMatrix[14] = position[2];
            
            int modelLoc = glGetUniformLocation(glGetInteger(GL_CURRENT_PROGRAM), "model");
            glUniformMatrix4fv(modelLoc, false, modelMatrix);
            
            glDisable(GL_DEPTH_TEST);
            glLineWidth(2.0f);
            glBindVertexArray(cursorVAO);
            
            shaderManager.setObjectColor(1.0f, 0.0f, 0.0f);
            glDrawArrays(GL_LINES, 0, 2);
            
            shaderManager.setObjectColor(0.0f, 1.0f, 0.0f);
            glDrawArrays(GL_LINES, 2, 2);
            
            shaderManager.setObjectColor(0.0f, 0.0f, 1.0f);
            glDrawArrays(GL_LINES, 4, 2);
            
            shaderManager.setObjectColor(1.0f, 1.0f, 1.0f);
            glDrawArrays(GL_LINES, 6, 16);
            
            glLineWidth(1.0f);
            glEnable(GL_DEPTH_TEST);
            shaderManager.useMainShader();
        }

        public void renderOutline(GameObject obj, ShaderManager shaderManager) {
            glDisable(GL_DEPTH_TEST);
            glPolygonMode(GL_FRONT_AND_BACK, GL_LINE);
            glLineWidth(2.0f);

            shaderManager.setUseLighting(false);
            shaderManager.setObjectColor(1.0f, 0.0f, 0.0f);

            float[] modelMatrix = obj.getModelMatrix();
            float outlineScale = 1.05f;
            modelMatrix[0] *= outlineScale; 
            modelMatrix[5] *= outlineScale; 
            modelMatrix[10] *= outlineScale;

            int modelLoc = glGetUniformLocation(glGetInteger(GL_CURRENT_PROGRAM), "model");
            glUniformMatrix4fv(modelLoc, false, modelMatrix);

            if (obj.getType() == GameObject.Type.REGULAR) {
                glBindVertexArray(cubeVAO);
                glDrawArrays(GL_TRIANGLES, 0, 36);
            } else {
                glBindVertexArray(lightVAO);
                glDrawElements(GL_TRIANGLES, sphereIndices.length, GL_UNSIGNED_INT, 0);
            }

            glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
            glEnable(GL_DEPTH_TEST);
            shaderManager.setUseLighting(true);
        }

        public void cleanup() {
            if (cubeVAO != 0) glDeleteVertexArrays(cubeVAO);
            if (cubeVBO != 0) glDeleteBuffers(cubeVBO);
            if (gridVAO != 0) glDeleteVertexArrays(gridVAO);
            if (gridVBO != 0) glDeleteBuffers(gridVBO);
            if (lightVAO != 0) glDeleteVertexArrays(lightVAO);
            if (lightVBO != 0) glDeleteBuffers(lightVBO);
            if (lightEBO != 0) glDeleteBuffers(lightEBO);
            if (cursorVAO != 0) glDeleteVertexArrays(cursorVAO);
            if (cursorVBO != 0) glDeleteBuffers(cursorVBO);
        }
    }

    private static class FPSCounter {
        private float fps = 0.0f;
        private float timeAccumulator = 0.0f;
        private int frameCount = 0;
        
        public void update(float deltaTime) {
            timeAccumulator += deltaTime;
            frameCount++;
            
            if (timeAccumulator >= 1.0f) {
                fps = frameCount / timeAccumulator;
                frameCount = 0;
                timeAccumulator = 0.0f;
            }
        }
        
        public float getFPS() {
            return fps;
        }
    }
}
