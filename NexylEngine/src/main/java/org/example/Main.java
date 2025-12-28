package org.example;

import imgui.*;
import imgui.flag.*;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

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
    // Application main components
    private Window window;           // GLFW Window
    private Renderer renderer;       // Scene renderer
    private InputHandler inputHandler; // Input handler
    private Scene scene;             // 3D scene with objects
    private UIManager uiManager;     // User interface manager
    
    // Timing and application state
    private boolean isRunning = true; // Main loop flag
    private float deltaTime = 0.0f;   // Time between frames
    private double lastFrameTime = 0.0; // Last frame time
    private FPSCounter fpsCounter = new FPSCounter(); // FPS counter

    // Main application launch method
    public void run() {
        try {
            initialize();   // Initialize all components
            mainLoop();     // Start main loop
        } finally {
            cleanup();      // Clean up resources on exit
        }
    }

    // Initialize all application components
    private void initialize() {
        System.out.println("=== APPLICATION INITIALIZATION ===");
        
        // Create window with 1920x1080 resolution
        window = new Window(1920, 1080, "3D Scene with Blender-like 3D Cursor");
        
        // Create main components
        renderer = new Renderer();       // Graphics renderer
        scene = new Scene();             // 3D scene with objects
        inputHandler = new InputHandler(window, scene, renderer); // Input handler
        uiManager = new UIManager(window, scene, inputHandler); // UI manager
        
        System.out.println("=== INITIALIZATION COMPLETE ===");
    }

    // Main application loop
    private void mainLoop() {
        System.out.println("=== STARTING MAIN LOOP ===");
        
        // Main loop runs while application is working and window is not closed
        while (isRunning && !window.shouldClose()) {
            updateTime();     // Update time
            handleInput();    // Handle user input
            update();         // Update game state
            render();         // Render frame
        }
    }

    // Calculate time between frames
    private void updateTime() {
        double currentTime = glfwGetTime();          // Current time
        deltaTime = (float) (currentTime - lastFrameTime); // Difference from previous frame
        lastFrameTime = currentTime;                // Save current frame time
        fpsCounter.update(deltaTime);               // Update FPS counter
    }

    // Handle user input
    private void handleInput() {
        inputHandler.pollEvents(); // Process GLFW events
    }

    // Update game state
    private void update() {
        scene.update(deltaTime);      // Update scene
        inputHandler.update(deltaTime); // Update input handler
    }

    // Render frame
    private void render() {
        renderer.beginFrame();                    // Start frame
        renderer.renderScene(scene, window);      // Render 3D scene
        uiManager.render(fpsCounter.getFPS());    // Render UI
        renderer.endFrame();                      // End frame
        window.swapBuffers();                     // Show frame on screen
    }

    // Clean up resources on exit
    private void cleanup() {
        System.out.println("=== CLEANING UP RESOURCES ===");
        
        // Clean all components in reverse order of creation
        if (uiManager != null) uiManager.cleanup();
        if (renderer != null) renderer.cleanup();
        if (scene != null) scene.cleanup();
        if (window != null) window.cleanup();
        
        System.out.println("=== CLEANUP COMPLETE ===");
    }

    // Application entry point
    public static void main(String[] args) {
        System.out.println("=== STARTING 3D APPLICATION ===");
        System.out.println("Platform: " + System.getProperty("os.name"));
        System.out.println("Java Version: " + System.getProperty("java.version"));

        try {
            new Main().run(); // Create and run application
        } catch (Exception e) {
            System.err.println("Application error:");
            e.printStackTrace();
            System.exit(-1);
        }
    }

    // ========== WINDOW CLASS (Window) ==========
    // Responsible for creating and managing GLFW window
    private static class Window {
        private long glfwWindow;   // GLFW window pointer
        private int width, height; // Window dimensions
        private String title;      // Window title

        public Window(int width, int height, String title) {
            this.width = width;
            this.height = height;
            this.title = title;
            initialize();
        }

        // Initialize window
        private void initialize() {
            setupGLFW();      // Configure GLFW
            createWindow();   // Create window
            setupOpenGL();    // Configure OpenGL
            showWindow();     // Show window
        }

        // Configure GLFW library
        private void setupGLFW() {
            // Set up GLFW error handler
            GLFWErrorCallback.createPrint(System.err).set();
            
            // Initialize GLFW
            if (!glfwInit()) {
                throw new IllegalStateException("Failed to initialize GLFW");
            }

            // Configure window parameters
            glfwDefaultWindowHints();
            glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);      // Hide window initially
            glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);     // Allow resizing
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3); // OpenGL 3.3 version
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
            glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE); // Core profile
            glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE); // Compatibility
        }

        // Create GLFW window
        private void createWindow() {
            glfwWindow = glfwCreateWindow(width, height, title, NULL, NULL);
            if (glfwWindow == NULL) {
                throw new RuntimeException("Failed to create GLFW window");
            }
        }

        // Configure OpenGL context
        private void setupOpenGL() {
            glfwMakeContextCurrent(glfwWindow); // Make context current
            GL.createCapabilities();            // Create OpenGL capabilities
            
            // Enable depth test for correct 3D display
            glEnable(GL_DEPTH_TEST);
            
            // Set screen clear color (dark gray)
            glClearColor(0.1f, 0.1f, 0.1f, 1.0f);
            
            // Enable vertical sync
            glfwSwapInterval(1);
        }

        // Show window to user
        private void showWindow() {
            updateFramebufferSize(); // Update framebuffer size
            glfwShowWindow(glfwWindow); // Show window
        }

        // Update render area size
        public void updateFramebufferSize() {
            IntBuffer widthBuffer = BufferUtils.createIntBuffer(1);
            IntBuffer heightBuffer = BufferUtils.createIntBuffer(1);
            
            // Get actual framebuffer size
            glfwGetFramebufferSize(glfwWindow, widthBuffer, heightBuffer);
            width = widthBuffer.get(0);
            height = heightBuffer.get(0);
            
            // Set render area
            glViewport(0, 0, width, height);
        }

        // Check if window close requested
        public boolean shouldClose() {
            return glfwWindowShouldClose(glfwWindow);
        }

        // Swap buffers (show frame)
        public void swapBuffers() {
            glfwSwapBuffers(glfwWindow);
        }

        // Clean window resources
        public void cleanup() {
            glfwFreeCallbacks(glfwWindow); // Free callbacks
            glfwDestroyWindow(glfwWindow); // Destroy window
            glfwTerminate();               // Terminate GLFW
            
            // Free error handler
            GLFWErrorCallback callback = glfwSetErrorCallback(null);
            if (callback != null) callback.free();
        }

        // Getters
        public long getGLFWWindow() { return glfwWindow; }
        public int getWidth() { return width; }
        public int getHeight() { return height; }
    }

    // ========== RENDERER CLASS (Renderer) ==========
    // Manages all graphics rendering
    private static class Renderer {
        private ShaderManager shaderManager;     // Shader manager
        private GeometryManager geometryManager; // Geometry manager
        private ObjectPicker objectPicker;       // Object selection system

        public Renderer() {
            shaderManager = new ShaderManager();
            geometryManager = new GeometryManager();
            objectPicker = new ObjectPicker();
        }

        // Start frame rendering
        public void beginFrame() {
            // Clear color and depth buffers
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        }

        // Render 3D scene
        public void renderScene(Scene scene, Window window) {
            scene.render(shaderManager, geometryManager, window);
        }

        // End frame rendering (place for post-processing)
        public void endFrame() {
            // Post-processing can go here
        }

        // Select object by mouse coordinates - ИСПРАВЛЕННЫЙ МЕТОД
        public GameObject performPicking(Scene scene, Window window, int mouseX, int mouseY) {
            return objectPicker.performPicking(scene, shaderManager, geometryManager, window, mouseX, mouseY);
        }
        
        // Get world position by mouse coordinates (for 3D cursor)
        public float[] performCursorRaycast(Scene scene, Window window, int mouseX, int mouseY) {
            return objectPicker.performRaycast(scene, window, mouseX, mouseY);
        }

        // Clean graphics resources
        public void cleanup() {
            if (shaderManager != null) shaderManager.cleanup();
            if (geometryManager != null) geometryManager.cleanup();
            if (objectPicker != null) objectPicker.cleanup();
        }
    }

    // ========== INPUT HANDLER CLASS (InputHandler) ==========
    // Handles all user actions (keyboard, mouse)
    private static class InputHandler {
        private Window window;      // Window for input handling
        private Scene scene;        // Scene for interaction
        private Renderer renderer;  // Renderer for object selection
        
        // Key states
        private boolean[] keyStates = new boolean[GLFW_KEY_LAST + 1];
        private boolean ctrlPressed = false;   // Ctrl pressed
        private boolean shiftPressed = false;  // Shift pressed
        private boolean altPressed = false;    // Alt pressed
        
        // Object creation menu
        private boolean showCreationMenu = false; // Show creation menu
        private double menuPosX, menuPosY;        // Menu position
        
        // Mouse state
        private double lastMouseX, lastMouseY;     // Last mouse position
        private boolean isMouseRightPressed = false; // Right mouse button
        private boolean isMouseLeftPressed = false;  // Left mouse button
        private boolean isFirstMouseMovement = true; // First mouse movement
        
        // Camera movement lock (during object transformations)
        private boolean cameraMovementLocked = false;

        public InputHandler(Window window, Scene scene, Renderer renderer) {
            this.window = window;
            this.scene = scene;
            this.renderer = renderer;
            setupCallbacks(); // Set up event handlers
        }

        // Set up GLFW event handlers
        private void setupCallbacks() {
            glfwSetFramebufferSizeCallback(window.getGLFWWindow(), this::onFramebufferSize);
            glfwSetCursorPosCallback(window.getGLFWWindow(), this::onCursorPos);
            glfwSetMouseButtonCallback(window.getGLFWWindow(), this::onMouseButton);
            glfwSetScrollCallback(window.getGLFWWindow(), this::onScroll);
            glfwSetKeyCallback(window.getGLFWWindow(), this::onKey);
        }

        // Window resize handler
        private void onFramebufferSize(long window, int width, int height) {
            if (width > 0 && height > 0) {
                this.window.updateFramebufferSize();
                
                // Update ImGui dimensions
                ImGuiIO io = ImGui.getIO();
                io.setDisplaySize(width, height);
                io.setDisplayFramebufferScale(1.0f, 1.0f);
            }
        }

        // Mouse movement handler
        private void onCursorPos(long window, double xpos, double ypos) {
            ImGuiIO io = ImGui.getIO();
            if (!io.getWantCaptureMouse()) {
                handleMouseMove(xpos, ypos);
            }
            
            lastMouseX = xpos;
            lastMouseY = ypos;
        }

        // Mouse button press handler - ИСПРАВЛЕННЫЙ ВЫЗОВ
        private void onMouseButton(long window, int button, int action, int mods) {
            ImGuiIO io = ImGui.getIO();
            if (!io.getWantCaptureMouse()) {
                handleMouseButton(button, action);
                
                // LMB: object selection or 3D cursor placement
                if (button == GLFW_MOUSE_BUTTON_LEFT && action == GLFW_PRESS) {
                    if (scene.getSelectionMode() == Scene.SelectionMode.STANDARD) {
                        // Standard selection mode: select object
                        GameObject selected = renderer.performPicking(scene, this.window, (int)lastMouseX, (int)lastMouseY);
                        scene.setSelectedObject(selected);
                    } else if (scene.getSelectionMode() == Scene.SelectionMode.CURSOR_3D) {
                        // 3D cursor mode: set cursor position
                        float[] cursorPos = renderer.performCursorRaycast(scene, this.window, (int)lastMouseX, (int)lastMouseY);
                        if (cursorPos != null) {
                            scene.setCursor3DPosition(cursorPos[0], cursorPos[1], cursorPos[2]);
                            System.out.println("3D Cursor set to: (" + cursorPos[0] + ", " + cursorPos[1] + ", " + cursorPos[2] + ")");
                        }
                    }
                }
            }
        }

        // Mouse wheel scroll handler
        private void onScroll(long window, double xoffset, double yoffset) {
            ImGuiIO io = ImGui.getIO();
            if (!io.getWantCaptureMouse()) {
                // Pass scroll to camera controller
                scene.getCameraController().handleScroll(yoffset);
            }
        }

        // Keyboard key press handler
        private void onKey(long window, int key, int scancode, int action, int mods) {
            // Update modifier states
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
            
            // Save all key states
            if (key >= 0 && key < keyStates.length) {
                keyStates[key] = (action == GLFW_PRESS || action == GLFW_REPEAT);
            }
            
            // Handle key presses
            if (action == GLFW_PRESS) {
                handleKeyPress(key, mods);
            }
            
            // Unlock camera movement when Ctrl is released
            if (action == GLFW_RELEASE && (key == GLFW_KEY_LEFT_CONTROL || key == GLFW_KEY_RIGHT_CONTROL)) {
                cameraMovementLocked = false;
            }
        }

        // Handle mouse movement
        private void handleMouseMove(double xpos, double ypos) {
            if (isMouseRightPressed) {
                // Camera rotation when right mouse button is held
                if (isFirstMouseMovement) {
                    lastMouseX = xpos;
                    lastMouseY = ypos;
                    isFirstMouseMovement = false;
                }

                double xoffset = xpos - lastMouseX;
                double yoffset = lastMouseY - ypos; // Reverse sign for natural rotation
                lastMouseX = xpos;
                lastMouseY = ypos;

                scene.getCameraController().handleMouseMovement(xoffset, yoffset);
            } else if (isMouseLeftPressed && scene.getSelectedObject() != null) {
                // Transform objects when left mouse button is held
                GameObject selected = scene.getSelectedObject();
                float xoffset = (float) (xpos - lastMouseX) * 0.01f;
                float yoffset = (float) (lastMouseY - ypos) * 0.01f;
                lastMouseX = xpos;
                lastMouseY = ypos;

                // Depending on active transformation mode
                if (scene.isTranslating()) {
                    // Move object
                    Camera camera = scene.getCameraController().getCamera();
                    
                    float[] right = camera.getRightVector();
                    float[] front = camera.getFrontVector();
                    
                    // Horizontal movement (X and Z axes)
                    selected.getPosition()[0] += right[0] * xoffset;
                    selected.getPosition()[2] += right[2] * xoffset;
                    
                    // Vertical movement (Y axis)
                    selected.getPosition()[1] -= yoffset;
                    
                    // Don't let object go below ground
                    if (selected.getPosition()[1] < 0.1f) {
                        selected.getPosition()[1] = 0.1f;
                    }
                } else if (scene.isRotating()) {
                    // Rotate object
                    selected.rotateY(xoffset * 50.0f); // Rotate around Y
                    selected.rotateX(yoffset * 50.0f); // Rotate around X
                } else if (scene.isScaling()) {
                    // Scale object
                    float scaleFactor = 1.0f + yoffset;
                    selected.scale(scaleFactor);
                }
            } else {
                isFirstMouseMovement = true;
            }
        }

        // Handle mouse button presses
        private void handleMouseButton(int button, int action) {
            if (button == GLFW_MOUSE_BUTTON_RIGHT) {
                isMouseRightPressed = (action == GLFW_PRESS);
                if (isMouseRightPressed) {
                    // Hide cursor during camera rotation
                    glfwSetInputMode(window.getGLFWWindow(), GLFW_CURSOR, GLFW_CURSOR_DISABLED);
                    isFirstMouseMovement = true;
                } else {
                    // Restore cursor
                    glfwSetInputMode(window.getGLFWWindow(), GLFW_CURSOR, GLFW_CURSOR_NORMAL);
                }
            } else if (button == GLFW_MOUSE_BUTTON_LEFT) {
                isMouseLeftPressed = (action == GLFW_PRESS);
            }
        }

        // Handle keyboard key presses
        private void handleKeyPress(int key, int mods) {
            // Close creation menu with Escape
            if (showCreationMenu && key == GLFW_KEY_ESCAPE) {
                showCreationMenu = false;
                System.out.println("Creation menu closed");
                return;
            }
            
            // Handle special keys
            switch (key) {
                case GLFW_KEY_ESCAPE:
                    // Escape: reset transformations or cancel selection
                    scene.handleEscape();
                    break;
                    
                case GLFW_KEY_T:
                    if (ctrlPressed) {
                        // Ctrl+T: toggle translation mode
                        scene.toggleTranslationMode();
                        cameraMovementLocked = true;
                    }
                    break;
                    
                case GLFW_KEY_R:
                    if (ctrlPressed) {
                        // Ctrl+R: toggle rotation mode
                        scene.toggleRotationMode();
                        cameraMovementLocked = true;
                    }
                    break;
                    
                case GLFW_KEY_S:
                    if (ctrlPressed && shiftPressed) {
                        // Ctrl+Shift+S: toggle scaling mode
                        scene.toggleScalingMode();
                        cameraMovementLocked = true;
                    }
                    break;
                    
                case GLFW_KEY_A:
                    if (shiftPressed) {
                        // Shift+A: open object creation menu
                        showCreationMenu = true;
                        menuPosX = lastMouseX;
                        menuPosY = lastMouseY;
                        System.out.println("Creation menu opened at (" + menuPosX + ", " + menuPosY + ")");
                    }
                    break;
                    
                case GLFW_KEY_SPACE:
                    // Space: attach 3D cursor to selected object
                    if (scene.getSelectedObject() != null && scene.getSelectionMode() == Scene.SelectionMode.STANDARD) {
                        float[] objPos = scene.getSelectedObject().getPosition();
                        scene.setCursor3DPosition(objPos[0], objPos[1], objPos[2]);
                        System.out.println("3D Cursor attached to selected object");
                    }
                    break;
            }
        }

        // Process GLFW events
        public void pollEvents() {
            glfwPollEvents();
        }

        // Update input handler state
        public void update(float deltaTime) {
            // Update camera movement by keys if not locked
            if (!cameraMovementLocked) {
                scene.getCameraController().updateMovement(keyStates, deltaTime);
            }
        }
        
        // Getters and setters
        public boolean isShowCreationMenu() { return showCreationMenu; }
        public double getMenuPosX() { return menuPosX; }
        public double getMenuPosY() { return menuPosY; }
        public void setShowCreationMenu(boolean show) { this.showCreationMenu = show; }
    }

    // ========== SCENE CLASS (Scene) ==========
    // Contains all 3D scene objects and manages them
    private static class Scene {
        // Selection modes (like in Blender)
        public enum SelectionMode {
            STANDARD,      // Standard object selection mode
            CURSOR_3D      // 3D cursor operation mode
        }
        
        private CameraController cameraController; // Camera controller
        private List<GameObject> gameObjects;      // List of all scene objects
        private GameObject selectedObject;         // Selected object
        
        // Object transformation modes
        private boolean isTranslating = false; // Translation mode
        private boolean isRotating = false;    // Rotation mode
        private boolean isScaling = false;     // Scaling mode
        
        private int nextObjectId = 6; // ID counter for new objects
        
        // 3D Cursor (Blender-like)
        private SelectionMode selectionMode = SelectionMode.STANDARD; // Current mode
        private float[] cursor3DPosition = {0.0f, 0.5f, 0.0f}; // Cursor position in world
        private boolean showCursor = true; // Cursor visibility
        private float cursorSize = 0.5f;   // Cursor size

        public Scene() {
            cameraController = new CameraController(); // Create camera controller
            gameObjects = new ArrayList<>();          // Initialize object list
            createDefaultScene();                     // Create starting scene
        }

        // Create starting scene with test objects
        private void createDefaultScene() {
            // Materials for different objects (like Blender)
            Material[] materials = {
                new Material(new float[]{0.19225f, 0.19225f, 0.19225f}, new float[]{0.50754f, 0.50754f, 0.50754f}, new float[]{0.508273f, 0.508273f, 0.508273f}, 0.4f),
                new Material(new float[]{0.25f, 0.25f, 0.25f}, new float[]{0.4f, 0.4f, 0.4f}, new float[]{0.774597f, 0.774597f, 0.774597f}, 0.6f),
                new Material(new float[]{0.05f, 0.05f, 0.05f}, new float[]{0.5f, 0.5f, 0.5f}, new float[]{0.7f, 0.7f, 0.7f}, 0.8f),
                new Material(new float[]{0.0f, 0.1f, 0.06f}, new float[]{0.0f, 0.50980392f, 0.50980392f}, new float[]{0.50196078f, 0.50196078f, 0.50196078f}, 0.25f)
            };

            // Create 4 standard cubes
            gameObjects.add(new GameObject(-2.0f, 0.5f, 0.0f, 1.0f, 1.0f, 1.0f, materials[0], 1, GameObject.Type.REGULAR, "Cube_1"));
            gameObjects.add(new GameObject(0.0f, 0.5f, 0.0f, 1.0f, 1.0f, 1.0f, materials[1], 2, GameObject.Type.REGULAR, "Cube_2"));
            gameObjects.add(new GameObject(2.0f, 0.5f, 0.0f, 1.0f, 1.0f, 1.0f, materials[2], 3, GameObject.Type.REGULAR, "Cube_3"));
            gameObjects.add(new GameObject(0.0f, 0.5f, -2.0f, 1.0f, 1.0f, 1.0f, materials[3], 4, GameObject.Type.REGULAR, "Cube_4"));
            
            // Create light source
            gameObjects.add(new GameObject(2.0f, 3.0f, 2.0f, 0.2f, 0.2f, 0.2f, null, 5, GameObject.Type.LIGHT, "Light_1"));
            
            // Initialize 3D cursor position at scene center
            cursor3DPosition[0] = 0.0f;
            cursor3DPosition[1] = 0.5f;
            cursor3DPosition[2] = 0.0f;
        }
        
        // Create new object in scene
        public void createObject(GameObject.Type type, String baseName) {
            // Objects are created at 3D cursor position (like in Blender)
            float x = cursor3DPosition[0];
            float y = cursor3DPosition[1];
            float z = cursor3DPosition[2];
            
            Material material = null;
            float scale = 1.0f;
            
            // Settings depending on object type
            if (type == GameObject.Type.REGULAR) {
                // Random material for cube
                material = new Material(
                    new float[]{(float)Math.random() * 0.5f, (float)Math.random() * 0.5f, (float)Math.random() * 0.5f},
                    new float[]{(float)Math.random() * 0.5f + 0.5f, (float)Math.random() * 0.5f + 0.5f, (float)Math.random() * 0.5f + 0.5f},
                    new float[]{(float)Math.random() * 0.5f + 0.5f, (float)Math.random() * 0.5f + 0.5f, (float)Math.random() * 0.5f + 0.5f},
                    (float)Math.random() * 0.5f + 0.25f
                );
                scale = 1.0f;
            } else if (type == GameObject.Type.LIGHT) {
                scale = 0.2f;
            }
            
            // Create unique name for object
            String name = baseName + "_" + nextObjectId;
            GameObject newObject = new GameObject(x, y, z, scale, scale, scale, material, nextObjectId, type, name);
            gameObjects.add(newObject);
            nextObjectId++;
            
            // In cursor mode, automatically select created object
            if (selectionMode == SelectionMode.CURSOR_3D) {
                selectedObject = newObject;
            }
            
            System.out.println("New object created at 3D Cursor: " + name + " at position (" + x + ", " + y + ", " + z + ")");
        }

        // Update scene state (called each frame)
        public void update(float deltaTime) {
            // Animations or other dynamic changes can be added here
        }

        // Render entire scene
        public void render(ShaderManager shaderManager, GeometryManager geometryManager, Window window) {
            shaderManager.useMainShader(); // Use main shader
            setupCameraUniforms(shaderManager, window); // Set camera parameters
            setupLightingUniforms(shaderManager);       // Set lighting parameters
            
            renderGrid(shaderManager, geometryManager);      // Render grid
            renderObjects(shaderManager, geometryManager);   // Render objects
            renderSelectionOutline(shaderManager, geometryManager); // Selection outline
            
            // Render 3D cursor if enabled
            if (showCursor) {
                render3DCursor(shaderManager, geometryManager, window);
            }
        }

        // Set camera uniform variables in shader
        private void setupCameraUniforms(ShaderManager shaderManager, Window window) {
            Camera camera = cameraController.getCamera();
            shaderManager.setViewMatrix(camera.getViewMatrix());
            shaderManager.setProjectionMatrix(camera.getProjectionMatrix(window.getWidth(), window.getHeight()));
            shaderManager.setViewPos(camera.getPosition());
        }

        // Set lighting uniform variables in shader
        private void setupLightingUniforms(ShaderManager shaderManager) {
            // Find first light source in scene
            GameObject light = gameObjects.stream()
                .filter(obj -> obj.getType() == GameObject.Type.LIGHT)
                .findFirst()
                .orElse(null);
                
            if (light != null) {
                shaderManager.setLightPosition(light.getPosition());
            }
            
            // Set light parameters
            shaderManager.setLightProperties(
                new float[]{0.2f, 0.2f, 0.2f},      // Ambient
                new float[]{0.8f, 0.8f, 0.8f},      // Diffuse
                new float[]{1.0f, 1.0f, 1.0f}       // Specular
            );
        }

        // Render coordinate grid
        private void renderGrid(ShaderManager shaderManager, GeometryManager geometryManager) {
            shaderManager.setUseLighting(false);    // Disable lighting for grid
            shaderManager.setObjectColor(0.3f, 0.3f, 0.3f); // Gray color
            geometryManager.renderGrid();           // Render grid
            shaderManager.setUseLighting(true);     // Enable lighting back
        }

        // Render all scene objects
        private void renderObjects(ShaderManager shaderManager, GeometryManager geometryManager) {
            for (GameObject obj : gameObjects) {
                if (obj.getType() == GameObject.Type.REGULAR) {
                    // Regular objects with materials and lighting
                    shaderManager.setMaterial(obj.getMaterial());
                    shaderManager.setObjectColor(1.0f, 1.0f, 1.0f);
                    geometryManager.renderCube(obj.getModelMatrix());
                } else {
                    // Light sources without lighting (they glow themselves)
                    shaderManager.setUseLighting(false);
                    shaderManager.setObjectColor(1.0f, 1.0f, 0.0f); // Yellow color
                    geometryManager.renderLight(obj.getModelMatrix());
                    shaderManager.setUseLighting(true);
                }
            }
        }

        // Render outline of selected object
        private void renderSelectionOutline(ShaderManager shaderManager, GeometryManager geometryManager) {
            if (selectedObject != null && selectionMode == SelectionMode.STANDARD) {
                geometryManager.renderOutline(selectedObject, shaderManager);
            }
        }
        
        // Render 3D cursor
        private void render3DCursor(ShaderManager shaderManager, GeometryManager geometryManager, Window window) {
            geometryManager.render3DCursor(cursor3DPosition, cursorSize, shaderManager, 
                                          cameraController.getCamera(), window);
        }

        // Toggle translation mode
        public void toggleTranslationMode() {
            if (selectedObject != null && selectionMode == SelectionMode.STANDARD) {
                isTranslating = !isTranslating;
                isRotating = false;
                isScaling = false;
                System.out.println("Translation mode: " + (isTranslating ? "ON" : "OFF"));
            } else {
                System.out.println("No object selected for translation");
            }
        }

        // Toggle rotation mode
        public void toggleRotationMode() {
            if (selectedObject != null && selectionMode == SelectionMode.STANDARD) {
                isRotating = !isRotating;
                isTranslating = false;
                isScaling = false;
                System.out.println("Rotation mode: " + (isRotating ? "ON" : "OFF"));
            } else {
                System.out.println("No object selected for rotation");
            }
        }

        // Toggle scaling mode
        public void toggleScalingMode() {
            if (selectedObject != null && selectionMode == SelectionMode.STANDARD) {
                isScaling = !isScaling;
                isTranslating = false;
                isRotating = false;
                System.out.println("Scaling mode: " + (isScaling ? "ON" : "OFF"));
            } else {
                System.out.println("No object selected for scaling");
            }
        }
        
        // Set selection mode
        public void setSelectionMode(SelectionMode mode) {
            this.selectionMode = mode;
            System.out.println("Selection mode changed to: " + mode);
        }
        
        // Toggle 3D cursor visibility
        public void toggle3DCursorVisibility() {
            showCursor = !showCursor;
            System.out.println("3D Cursor visibility: " + (showCursor ? "ON" : "OFF"));
        }
        
        // Set 3D cursor position
        public void setCursor3DPosition(float x, float y, float z) {
            cursor3DPosition[0] = x;
            cursor3DPosition[1] = y;
            cursor3DPosition[2] = z;
        }
        
        // Reset 3D cursor to origin
        public void resetCursorToOrigin() {
            cursor3DPosition[0] = 0.0f;
            cursor3DPosition[1] = 0.5f;
            cursor3DPosition[2] = 0.0f;
            System.out.println("3D Cursor reset to origin");
        }

        // Handle Escape key
        public void handleEscape() {
            if (isTranslating || isRotating || isScaling) {
                // Reset all transformation modes
                isTranslating = false;
                isRotating = false;
                isScaling = false;
                System.out.println("All transformation modes disabled");
            } else if (selectedObject != null && selectionMode == SelectionMode.STANDARD) {
                // Cancel object selection
                selectedObject = null;
                System.out.println("Object deselected");
            }
        }

        // Clean scene resources
        public void cleanup() {
            // Object resources can be freed here
        }

        // ========== GETTERS ==========
        public CameraController getCameraController() { return cameraController; }
        public List<GameObject> getGameObjects() { return gameObjects; }
        public GameObject getSelectedObject() { return selectedObject; }
        public SelectionMode getSelectionMode() { return selectionMode; }
        public boolean isTranslating() { return isTranslating; }
        public boolean isRotating() { return isRotating; }
        public boolean isScaling() { return isScaling; }
        public float[] getCursor3DPosition() { return cursor3DPosition; }
        public boolean isCursorVisible() { return showCursor; }
        
        // ========== SETTERS ==========
        public void setSelectedObject(GameObject selectedObject) { 
            this.selectedObject = selectedObject;
            if (selectedObject != null) {
                System.out.println("Object selected: " + selectedObject.getName());
            }
        }
    }

    // ========== UI MANAGER CLASS (UIManager) ==========
    // Manages user interface (ImGui)
    private static class UIManager {
        private final ImGuiImplGlfw imGuiGlfw = new ImGuiImplGlfw(); // ImGui integration with GLFW
        private final ImGuiImplGl3 imGuiGl3 = new ImGuiImplGl3();    // ImGui integration with OpenGL
        private Window window;    // Window for UI positioning
        private Scene scene;      // Scene for interaction
        private InputHandler inputHandler; // Input handler

        public UIManager(Window window, Scene scene, InputHandler inputHandler) {
            this.window = window;
            this.scene = scene;
            this.inputHandler = inputHandler;
            initialize(); // Initialize ImGui
        }

        // Initialize ImGui
        private void initialize() {
            ImGui.createContext(); // Create ImGui context
            ImGuiIO io = ImGui.getIO();
            io.setIniFilename(null); // Don't save settings to file
            io.addConfigFlags(ImGuiConfigFlags.NavEnableKeyboard); // Enable keyboard navigation

            // Set render area dimensions
            io.setDisplaySize(window.getWidth(), window.getHeight());
            io.setDisplayFramebufferScale(1.0f, 1.0f);

            // Initialize ImGui integrations
            imGuiGlfw.init(window.getGLFWWindow(), true);
            imGuiGl3.init(null);
            
            // Set dark theme
            ImGui.styleColorsDark();
        }

        // Render all UI
        public void render(float fps) {
            // Start new ImGui frame
            imGuiGlfw.newFrame();
            ImGui.newFrame();

            // Render UI components
            renderFPSOverlay(fps);      // FPS overlay
            renderModeSelector();       // Mode selection buttons (left)
            renderObjectTree();         // Object tree (right)
            renderCreationMenu();       // Object creation menu

            // Complete ImGui rendering
            ImGui.render();
            imGuiGl3.renderDrawData(ImGui.getDrawData());
        }

        // FPS overlay in top left corner
        private void renderFPSOverlay(float fps) {
            ImGui.setNextWindowPos(10, 10, ImGuiCond.Always);
            ImGui.setNextWindowSize(100, 30, ImGuiCond.Always);
            ImGui.begin("FPS Overlay", 
                ImGuiWindowFlags.NoTitleBar | 
                ImGuiWindowFlags.NoResize | 
                ImGuiWindowFlags.NoMove | 
                ImGuiWindowFlags.NoBackground);
            
            ImGui.text(String.format("FPS: %.1f", fps));
            ImGui.end();
        }
        
        // Mode selection buttons (left, without window)
        private void renderModeSelector() {
            // Position buttons in top left corner under FPS
            float xPos = 10;
            float yPos = 50;
            
            // Create transparent window without background and title
            ImGui.setNextWindowPos(xPos, yPos, ImGuiCond.Always);
            ImGui.setNextWindowSize(150, 90, ImGuiCond.Always);
            
            ImGui.begin("##ModeSelector", 
                ImGuiWindowFlags.NoTitleBar | 
                ImGuiWindowFlags.NoResize | 
                ImGuiWindowFlags.NoMove | 
                ImGuiWindowFlags.NoBackground |
                ImGuiWindowFlags.NoDecoration);
            
            // Standard selection mode button
            if (ImGui.button("Standard", 130, 30)) {
                scene.setSelectionMode(Scene.SelectionMode.STANDARD);
            }
            
            // 3D cursor mode button
            if (ImGui.button("3D Cursor", 130, 30)) {
                scene.setSelectionMode(Scene.SelectionMode.CURSOR_3D);
            }
            
            // Current mode indicator (text)
            String currentMode = scene.getSelectionMode() == Scene.SelectionMode.STANDARD ? 
                "Mode: Standard" : "Mode: 3D Cursor";
            ImGui.text(currentMode);
            
            ImGui.end();
        }

        // Object tree (right)
        private void renderObjectTree() {
            float xPos = window.getWidth() - 320 - 10; // 10px from right edge
            float yPos = 10;
            ImGui.setNextWindowPos(xPos, yPos, ImGuiCond.Always);
            ImGui.setNextWindowSize(320, 400, ImGuiCond.Always);
            
            ImGui.begin("Object Tree", ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoMove);

            ImGui.text("Scene Objects:");
            ImGui.separator();

            // Display all scene objects as buttons
            for (GameObject obj : scene.getGameObjects()) {
                String label = obj.getName();
                
                // Highlight selected object
                if (obj == scene.getSelectedObject()) {
                    label = "> " + label + " <";
                }

                // Button for object selection
                if (ImGui.button(label, ImGui.getContentRegionAvailX(), 0)) {
                    scene.setSelectedObject(obj);
                    System.out.println("Selected from UI: " + obj.getName());
                }
            }

            ImGui.end();
        }
        
        // Object creation menu (appears when Shift+A is pressed)
        private void renderCreationMenu() {
            if (!inputHandler.isShowCreationMenu()) {
                return;
            }
            
            // Menu position - where button was pressed
            double posX = inputHandler.getMenuPosX();
            double posY = inputHandler.getMenuPosY();
            
            float imGuiPosX = (float) posX;
            float imGuiPosY = (float) posY;
            
            ImGui.setNextWindowPos(imGuiPosX, imGuiPosY, ImGuiCond.Appearing);
            ImGui.setNextWindowSize(200, 150, ImGuiCond.Appearing);
            
            boolean[] keepMenuOpen = {true}; // Flag to close menu
            
            ImGui.begin("Create Object", 
                ImGuiWindowFlags.NoResize | 
                ImGuiWindowFlags.NoMove |
                ImGuiWindowFlags.NoCollapse);
            
            ImGui.text("Create new object:");
            ImGui.separator();
            
            // Button to create cube at 3D cursor position
            if (ImGui.button("Cube at Cursor", 180, 30)) {
                scene.createObject(GameObject.Type.REGULAR, "Cube");
                keepMenuOpen[0] = false;
                System.out.println("Cube created at 3D cursor");
            }
            
            // Button to create light source at 3D cursor position
            if (ImGui.button("Light Source", 180, 30)) {
                scene.createObject(GameObject.Type.LIGHT, "Light");
                keepMenuOpen[0] = false;
                System.out.println("Light source created at 3D cursor");
            }
            
            ImGui.separator();
            
            // Cancel button
            if (ImGui.button("Cancel", 180, 30)) {
                keepMenuOpen[0] = false;
                System.out.println("Creation menu cancelled");
            }
            
            ImGui.end();
            
            // Close menu if needed
            if (!keepMenuOpen[0]) {
                inputHandler.setShowCreationMenu(false);
            }
        }

        // Clean ImGui resources
        public void cleanup() {
            imGuiGl3.dispose();
            imGuiGlfw.dispose();
            ImGui.destroyContext();
        }
    }

    // ========== CAMERA CONTROLLER CLASS (CameraController) ==========
    // Manages camera movement and rotation
    private static class CameraController {
        private Camera camera;               // Camera
        private float speed = 3.0f;          // Movement speed
        private float mouseSensitivity = 0.1f; // Mouse sensitivity
        
        private float yaw = -90.0f;   // Horizontal rotation angle
        private float pitch = 0.0f;   // Vertical rotation angle

        public CameraController() {
            camera = new Camera();     // Create camera
            updateCameraVectors();     // Initialize camera vectors
        }

        // Handle mouse movement (camera rotation)
        public void handleMouseMovement(double xoffset, double yoffset) {
            xoffset *= mouseSensitivity;
            yoffset *= mouseSensitivity;

            yaw += (float) xoffset;
            pitch += (float) yoffset;

            // Limit vertical angle (to avoid camera flipping)
            if (pitch > 89.0f) pitch = 89.0f;
            if (pitch < -89.0f) pitch = -89.0f;

            updateCameraVectors();
        }

        // Handle mouse wheel scroll (speed change)
        public void handleScroll(double yoffset) {
            speed += (float) yoffset * 0.5f;
            
            // Limit speed
            if (speed < 0.1f) speed = 0.1f;
            if (speed > 10.0f) speed = 10.0f;
        }

        // Update camera movement with WASD keys
        public void updateMovement(boolean[] keyStates, float deltaTime) {
            float velocity = speed * deltaTime;
            float[] cameraPos = camera.getPosition();
            float[] front = camera.getFrontVector();

            // Forward movement (W)
            if (keyStates[GLFW_KEY_W]) {
                cameraPos[0] += front[0] * velocity;
                cameraPos[1] += front[1] * velocity;
                cameraPos[2] += front[2] * velocity;
            }
            
            // Backward movement (S)
            if (keyStates[GLFW_KEY_S]) {
                cameraPos[0] -= front[0] * velocity;
                cameraPos[1] -= front[1] * velocity;
                cameraPos[2] -= front[2] * velocity;
            }
            
            // Left movement (A)
            if (keyStates[GLFW_KEY_A]) {
                float[] right = camera.getRightVector();
                cameraPos[0] -= right[0] * velocity;
                cameraPos[1] -= right[1] * velocity;
                cameraPos[2] -= right[2] * velocity;
            }
            
            // Right movement (D)
            if (keyStates[GLFW_KEY_D]) {
                float[] right = camera.getRightVector();
                cameraPos[0] += right[0] * velocity;
                cameraPos[1] += right[1] * velocity;
                cameraPos[2] += right[2] * velocity;
            }
        }

        // Update camera direction vectors based on yaw and pitch angles
        private void updateCameraVectors() {
            float yawRad = (float) Math.toRadians(yaw);
            float pitchRad = (float) Math.toRadians(pitch);

            // Calculate camera direction vector
            float[] front = new float[3];
            front[0] = (float) (Math.cos(yawRad) * Math.cos(pitchRad));
            front[1] = (float) Math.sin(pitchRad);
            front[2] = (float) (Math.sin(yawRad) * Math.cos(pitchRad));
            
            camera.setFrontVector(normalize(front));
            camera.updateVectors();
        }

        // Normalize vector (bring to length 1)
        private float[] normalize(float[] v) {
            float length = (float) Math.sqrt(v[0]*v[0] + v[1]*v[1] + v[2]*v[2]);
            if (length > 0) {
                v[0] /= length;
                v[1] /= length;
                v[2] /= length;
            }
            return v;
        }

        // Getters
        public Camera getCamera() { return camera; }
        public float getSpeed() { return speed; }
    }

    // ========== CAMERA CLASS (Camera) ==========
    // Contains camera state and calculates view and projection matrices
    private static class Camera {
        private float[] position = {0.0f, 2.0f, 5.0f}; // Camera position
        private float[] front = {0.0f, 0.0f, -1.0f};   // Look direction
        private float[] up = {0.0f, 1.0f, 0.0f};       // Up vector
        private float[] right = {1.0f, 0.0f, 0.0f};    // Right vector
        private float[] worldUp = {0.0f, 1.0f, 0.0f};  // World up vector

        // Update right and up vectors based on front
        public void updateVectors() {
            right = normalize(crossProduct(front, worldUp));
            up = normalize(crossProduct(right, front));
        }

        // Create view matrix
        public float[] getViewMatrix() {
            float[] viewMatrix = new float[16];
            
            float[] cameraPos = position;
            float[] cameraFront = front;
            float[] cameraUp = up;

            // Calculate camera basis (LookAt matrix)
            float[] z = normalize(new float[]{-cameraFront[0], -cameraFront[1], -cameraFront[2]});
            float[] x = normalize(crossProduct(cameraUp, z));
            float[] y = crossProduct(z, x);

            // Fill view matrix
            viewMatrix[0] = x[0]; viewMatrix[1] = y[0]; viewMatrix[2] = z[0]; viewMatrix[3] = 0.0f;
            viewMatrix[4] = x[1]; viewMatrix[5] = y[1]; viewMatrix[6] = z[1]; viewMatrix[7] = 0.0f;
            viewMatrix[8] = x[2]; viewMatrix[9] = y[2]; viewMatrix[10] = z[2]; viewMatrix[11] = 0.0f;
            viewMatrix[12] = -dotProduct(x, cameraPos);
            viewMatrix[13] = -dotProduct(y, cameraPos);
            viewMatrix[14] = -dotProduct(z, cameraPos);
            viewMatrix[15] = 1.0f;

            return viewMatrix;
        }

        // Create projection matrix (perspective projection)
        public float[] getProjectionMatrix(int width, int height) {
            float[] projectionMatrix = new float[16];
            float aspect = (float) width / height;   // Aspect ratio
            float fov = (float) Math.toRadians(45.0f); // 45 degree field of view
            float near = 0.1f;  // Near clipping plane
            float far = 100.0f; // Far clipping plane

            // Calculate projection matrix components
            float f = (float) (1.0 / Math.tan(fov / 2.0));

            // Fill projection matrix (perspective projection)
            projectionMatrix[0] = f / aspect;
            projectionMatrix[5] = f;
            projectionMatrix[10] = (far + near) / (near - far);
            projectionMatrix[11] = -1.0f;
            projectionMatrix[14] = (2.0f * far * near) / (near - far);
            projectionMatrix[15] = 0.0f;

            return projectionMatrix;
        }
        
        // Convert screen coordinates to world space ray
        public float[] screenToWorldRay(int screenX, int screenY, int screenWidth, int screenHeight) {
            // Convert screen coordinates to normalized device coordinates
            float x = (2.0f * screenX) / screenWidth - 1.0f;
            float y = 1.0f - (2.0f * screenY) / screenHeight;
            
            // Coordinates in homogeneous clip space
            float[] rayClip = {x, y, -1.0f, 1.0f};
            
            // Convert to eye space
            float[] rayEye = multiplyMatrixVector(inverseProjectionMatrix(screenWidth, screenHeight), rayClip);
            rayEye[2] = -1.0f;
            rayEye[3] = 0.0f;
            
            // Convert to world space
            float[] rayWorld = multiplyMatrixVector(inverseViewMatrix(), rayEye);
            float[] rayDir = {rayWorld[0], rayWorld[1], rayWorld[2]};
            
            // Normalize ray direction
            float length = (float)Math.sqrt(rayDir[0]*rayDir[0] + rayDir[1]*rayDir[1] + rayDir[2]*rayDir[2]);
            if (length > 0) {
                rayDir[0] /= length;
                rayDir[1] /= length;
                rayDir[2] /= length;
            }
            
            return rayDir;
        }
        
        // Inverse projection matrix (for coordinate transformation)
        private float[] inverseProjectionMatrix(int width, int height) {
            float[] proj = getProjectionMatrix(width, height);
            float[] inv = new float[16];
            
            // Simplified inversion for perspective projection
            inv[0] = 1.0f / proj[0];
            inv[5] = 1.0f / proj[5];
            inv[10] = 0.0f;
            inv[11] = 1.0f / proj[14];
            inv[14] = 1.0f;
            inv[15] = -proj[10] / proj[14];
            
            return inv;
        }
        
        // Inverse view matrix
        private float[] inverseViewMatrix() {
            float[] view = getViewMatrix();
            float[] inv = new float[16];
            
            // Invert rotation (transposition)
            inv[0] = view[0]; inv[1] = view[4]; inv[2] = view[8];
            inv[4] = view[1]; inv[5] = view[5]; inv[6] = view[9];
            inv[8] = view[2]; inv[9] = view[6]; inv[10] = view[10];
            
            // Invert translation
            inv[12] = -(view[12] * view[0] + view[13] * view[1] + view[14] * view[2]);
            inv[13] = -(view[12] * view[4] + view[13] * view[5] + view[14] * view[6]);
            inv[14] = -(view[12] * view[8] + view[13] * view[9] + view[14] * view[10]);
            
            // Fill remaining elements
            inv[3] = 0.0f; inv[7] = 0.0f; inv[11] = 0.0f; inv[15] = 1.0f;
            
            return inv;
        }
        
        // Multiply matrix by vector
        private float[] multiplyMatrixVector(float[] m, float[] v) {
            float[] result = new float[4];
            result[0] = m[0] * v[0] + m[4] * v[1] + m[8] * v[2] + m[12] * v[3];
            result[1] = m[1] * v[0] + m[5] * v[1] + m[9] * v[2] + m[13] * v[3];
            result[2] = m[2] * v[0] + m[6] * v[1] + m[10] * v[2] + m[14] * v[3];
            result[3] = m[3] * v[0] + m[7] * v[1] + m[11] * v[2] + m[15] * v[3];
            return result;
        }

        // Cross product of two vectors
        private float[] crossProduct(float[] a, float[] b) {
            return new float[]{
                a[1] * b[2] - a[2] * b[1],
                a[2] * b[0] - a[0] * b[2],
                a[0] * b[1] - a[1] * b[0]
            };
        }

        // Dot product of two vectors
        private float dotProduct(float[] a, float[] b) {
            return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
        }

        // Normalize vector
        private float[] normalize(float[] v) {
            float length = (float) Math.sqrt(v[0]*v[0] + v[1]*v[1] + v[2]*v[2]);
            if (length > 0) {
                v[0] /= length;
                v[1] /= length;
                v[2] /= length;
            }
            return v;
        }

        // Getters and setters
        public float[] getPosition() { return position; }
        public float[] getFrontVector() { return front; }
        public float[] getUpVector() { return up; }
        public float[] getRightVector() { return right; }
        public void setFrontVector(float[] front) { this.front = front; }
    }

    // ========== GAME OBJECT CLASS (GameObject) ==========
    // Represents any object in 3D scene
    private static class GameObject {
        // Object types (regular objects and light sources)
        public enum Type { REGULAR, LIGHT }

        private float[] position = new float[3];  // Position in world
        private float[] scale = new float[3];     // Scale on axes
        private float[] rotation = new float[3];  // Rotation angles on axes
        private Material material;                // Object material
        private int id;                           // Unique ID
        private Type type;                        // Object type
        private String name;                      // Object name

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

        // Create model matrix (transformation from local to world coordinates)
        public float[] getModelMatrix() {
            float[] matrix = new float[16];
            
            // Initialize as identity matrix
            for (int i = 0; i < 16; i++) matrix[i] = 0.0f;
            matrix[0] = 1.0f; matrix[5] = 1.0f; matrix[10] = 1.0f; matrix[15] = 1.0f;

            // Apply scale
            matrix[0] *= scale[0];
            matrix[5] *= scale[1];
            matrix[10] *= scale[2];

            // Apply rotation if any
            if (rotation[0] != 0.0f || rotation[1] != 0.0f || rotation[2] != 0.0f) {
                float[] rotationMatrix = createRotationMatrix();
                matrix = multiplyMatrices(matrix, rotationMatrix);
            }

            // Apply translation
            matrix[12] = position[0];
            matrix[13] = position[1];
            matrix[14] = position[2];

            return matrix;
        }

        // Create rotation matrix from Euler angles
        private float[] createRotationMatrix() {
            float[] matrix = new float[16];
            
            // Initialize as identity matrix
            for (int i = 0; i < 16; i++) matrix[i] = 0.0f;
            matrix[0] = 1.0f; matrix[5] = 1.0f; matrix[10] = 1.0f; matrix[15] = 1.0f;

            // Convert angles from degrees to radians
            float rx = (float) Math.toRadians(rotation[0]);
            float ry = (float) Math.toRadians(rotation[1]);
            float rz = (float) Math.toRadians(rotation[2]);

            // Apply rotation around Z
            if (rz != 0.0f) {
                float[] rotZ = new float[16];
                for (int i = 0; i < 16; i++) rotZ[i] = 0.0f;
                rotZ[0] = (float) Math.cos(rz); rotZ[1] = (float) Math.sin(rz);
                rotZ[4] = (float) -Math.sin(rz); rotZ[5] = (float) Math.cos(rz);
                rotZ[10] = 1.0f;
                rotZ[15] = 1.0f;
                matrix = multiplyMatrices(matrix, rotZ);
            }

            // Apply rotation around Y
            if (ry != 0.0f) {
                float[] rotY = new float[16];
                for (int i = 0; i < 16; i++) rotY[i] = 0.0f;
                rotY[0] = (float) Math.cos(ry); rotY[2] = (float) -Math.sin(ry);
                rotY[5] = 1.0f;
                rotY[8] = (float) Math.sin(ry); rotY[10] = (float) Math.cos(ry);
                rotY[15] = 1.0f;
                matrix = multiplyMatrices(matrix, rotY);
            }

            // Apply rotation around X
            if (rx != 0.0f) {
                float[] rotX = new float[16];
                for (int i = 0; i < 16; i++) rotX[i] = 0.0f;
                rotX[0] = 1.0f;
                rotX[5] = (float) Math.cos(rx); rotX[6] = (float) Math.sin(rx);
                rotX[9] = (float) -Math.sin(rx); rotX[10] = (float) Math.cos(rx);
                rotX[15] = 1.0f;
                matrix = multiplyMatrices(matrix, rotX);
            }

            return matrix;
        }

        // Multiply two 4x4 matrices
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

        // Object transformation methods
        public void rotateX(float angle) {
            rotation[0] += angle;
            rotation[0] = rotation[0] % 360.0f; // Limit to 360 degrees
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
            
            // Minimum scale so object doesn't disappear
            if (scale[0] < 0.1f) scale[0] = 0.1f;
            if (scale[1] < 0.1f) scale[1] = 0.1f;
            if (scale[2] < 0.1f) scale[2] = 0.1f;
        }

        // Getters
        public float[] getPosition() { return position; }
        public Material getMaterial() { return material; }
        public Type getType() { return type; }
        public String getName() { return name; }
        public int getId() { return id; }
    }

    // ========== MATERIAL CLASS (Material) ==========
    // Contains material properties for lighting (Phong model)
    private static class Material {
        private float[] ambient;    // Ambient reflection
        private float[] diffuse;    // Diffuse reflection
        private float[] specular;   // Specular reflection
        private float shininess;    // Shininess (exponent in formula)

        public Material(float[] ambient, float[] diffuse, float[] specular, float shininess) {
            this.ambient = ambient;
            this.diffuse = diffuse;
            this.specular = specular;
            this.shininess = shininess;
        }

        // Getters
        public float[] getAmbient() { return ambient; }
        public float[] getDiffuse() { return diffuse; }
        public float[] getSpecular() { return specular; }
        public float getShininess() { return shininess; }
    }

    // ========== OBJECT PICKER CLASS (ObjectPicker) ==========
    // Implements object selection by pixels (picking) and raycasting
    private static class ObjectPicker {
        private ByteBuffer pixelBuffer = BufferUtils.createByteBuffer(4); // Buffer for reading pixels

        // Select object by mouse coordinates (color encoding)
        public GameObject performPicking(Scene scene, ShaderManager shaderManager, GeometryManager geometryManager, 
                                       Window window, int mouseX, int mouseY) {
            // Set black background for picking
            glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            shaderManager.usePickingShader(); // Use picking shader

            // Set camera matrices
            Camera camera = scene.getCameraController().getCamera();
            shaderManager.setViewMatrix(camera.getViewMatrix());
            shaderManager.setProjectionMatrix(camera.getProjectionMatrix(window.getWidth(), window.getHeight()));

            // Render all objects with unique colors (ID in RGB)
            for (GameObject obj : scene.getGameObjects()) {
                int objectID = obj.getId();
                
                // Encode ID into RGB color
                float r = ((objectID >> 16) & 0xFF) / 255.0f;
                float g = ((objectID >> 8) & 0xFF) / 255.0f;
                float b = (objectID & 0xFF) / 255.0f;

                shaderManager.setObjectColor(r, g, b);

                // Render object depending on type
                if (obj.getType() == GameObject.Type.REGULAR) {
                    geometryManager.renderCube(obj.getModelMatrix());
                } else {
                    geometryManager.renderLight(obj.getModelMatrix());
                }
            }

            // Read pixel color under mouse cursor
            glReadPixels(mouseX, window.getHeight() - mouseY, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixelBuffer);

            // Return to main shader
            shaderManager.useMainShader();
            glClearColor(0.1f, 0.1f, 0.1f, 1.0f);

            // Decode ID from pixel color
            int pickedID = -1;
            if (pixelBuffer != null) {
                int r = pixelBuffer.get(0) & 0xFF;
                int g = pixelBuffer.get(1) & 0xFF;
                int b = pixelBuffer.get(2) & 0xFF;
                pickedID = (r << 16) | (g << 8) | b;
            }

            // Find object with corresponding ID
            for (GameObject obj : scene.getGameObjects()) {
                if (obj.getId() == pickedID) {
                    return obj;
                }
            }

            return null; // Nothing selected
        }
        
        // Raycasting implementation for 3D cursor positioning
        public float[] performRaycast(Scene scene, Window window, int mouseX, int mouseY) {
            Camera camera = scene.getCameraController().getCamera();
            
            // Get ray direction from screen coordinates
            float[] rayDir = camera.screenToWorldRay(mouseX, mouseY, window.getWidth(), window.getHeight());
            float[] rayOrigin = camera.getPosition();
            
            // Find intersection with ground plane (y = 0)
            // Ray equation: P = O + tD
            // Plane equation: y = 0
            // Solve: O.y + t * D.y = 0
            // t = -O.y / D.y
            
            float t = -rayOrigin[1] / rayDir[1];
            
            if (t > 0) {
                // Found intersection with ground
                float[] intersection = new float[3];
                intersection[0] = rayOrigin[0] + rayDir[0] * t;
                intersection[1] = 0.0f; // Ground plane
                intersection[2] = rayOrigin[2] + rayDir[2] * t;
                return intersection;
            } else {
                // Ray directed upward, use fixed distance
                float defaultDistance = 10.0f;
                float[] intersection = new float[3];
                intersection[0] = rayOrigin[0] + rayDir[0] * defaultDistance;
                intersection[1] = rayOrigin[1] + rayDir[1] * defaultDistance;
                intersection[2] = rayOrigin[2] + rayDir[2] * defaultDistance;
                return intersection;
            }
        }

        public void cleanup() {
            // Clean resources if needed
        }
    }

    // ========== SHADER MANAGER CLASS (ShaderManager) ==========
    // Manages shader compilation and usage
    private static class ShaderManager {
        private int mainShaderProgram;      // Main shader (lighting)
        private int pickingShaderProgram;   // Object picking shader
        private int cursorShaderProgram;    // 3D cursor shader

        public ShaderManager() {
            mainShaderProgram = compileMainShader();
            pickingShaderProgram = compilePickingShader();
            cursorShaderProgram = compileCursorShader();
        }

        // Use main shader
        public void useMainShader() {
            glUseProgram(mainShaderProgram);
        }

        // Use object picking shader
        public void usePickingShader() {
            glUseProgram(pickingShaderProgram);
        }
        
        // Use 3D cursor shader
        public void useCursorShader() {
            glUseProgram(cursorShaderProgram);
        }

        // Set view matrix in current shader
        public void setViewMatrix(float[] viewMatrix) {
            int program = getCurrentProgram();
            int location = glGetUniformLocation(program, "view");
            if (location != -1) {
                glUniformMatrix4fv(location, false, viewMatrix);
            }
        }

        // Set projection matrix in current shader
        public void setProjectionMatrix(float[] projectionMatrix) {
            int program = getCurrentProgram();
            int location = glGetUniformLocation(program, "projection");
            if (location != -1) {
                glUniformMatrix4fv(location, false, projectionMatrix);
            }
        }

        // Set camera position (for lighting)
        public void setViewPos(float[] viewPos) {
            int location = glGetUniformLocation(mainShaderProgram, "viewPos");
            if (location != -1) {
                glUniform3f(location, viewPos[0], viewPos[1], viewPos[2]);
            }
        }

        // Set light source position
        public void setLightPosition(float[] lightPos) {
            int location = glGetUniformLocation(mainShaderProgram, "light.position");
            if (location != -1) {
                glUniform3f(location, lightPos[0], lightPos[1], lightPos[2]);
            }
        }

        // Set light source properties
        public void setLightProperties(float[] ambient, float[] diffuse, float[] specular) {
            int ambientLoc = glGetUniformLocation(mainShaderProgram, "light.ambient");
            int diffuseLoc = glGetUniformLocation(mainShaderProgram, "light.diffuse");
            int specularLoc = glGetUniformLocation(mainShaderProgram, "light.specular");
            
            if (ambientLoc != -1) glUniform3f(ambientLoc, ambient[0], ambient[1], ambient[2]);
            if (diffuseLoc != -1) glUniform3f(diffuseLoc, diffuse[0], diffuse[1], diffuse[2]);
            if (specularLoc != -1) glUniform3f(specularLoc, specular[0], specular[1], specular[2]);
        }

        // Set material properties
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

        // Set object color
        public void setObjectColor(float r, float g, float b) {
            int program = getCurrentProgram();
            int location = glGetUniformLocation(program, "objectColor");
            if (location != -1) {
                glUniform3f(location, r, g, b);
            }
        }

        // Enable/disable lighting
        public void setUseLighting(boolean useLighting) {
            int location = glGetUniformLocation(mainShaderProgram, "useLighting");
            if (location != -1) {
                glUniform1i(location, useLighting ? 1 : 0);
            }
        }

        // Get current shader program
        private int getCurrentProgram() {
            return glGetInteger(GL_CURRENT_PROGRAM);
        }

        // Compile main shader
        private int compileMainShader() {
            String vertexSource = loadShaderSource("vertex.glsl", VERTEX_SHADER_SOURCE);
            String fragmentSource = loadShaderSource("fragment.glsl", FRAGMENT_SHADER_SOURCE);
            System.out.println("Compiling main shader...");
            return createShaderProgram(vertexSource, fragmentSource);
        }

        // Compile object picking shader
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

            System.out.println("Compiling object picking shader...");
            return createShaderProgram(vertexShader, fragmentShader);
        }
        
        // Compile 3D cursor shader
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

            System.out.println("Compiling 3D cursor shader...");
            return createShaderProgram(vertexShader, fragmentShader);
        }

        // Create shader program from source code
        private int createShaderProgram(String vertexSource, String fragmentSource) {
            // Compile vertex shader
            int vertexShader = glCreateShader(GL_VERTEX_SHADER);
            glShaderSource(vertexShader, vertexSource);
            glCompileShader(vertexShader);
            checkShaderCompilation(vertexShader, "VERTEX");
            
            // Compile fragment shader
            int fragmentShader = glCreateShader(GL_FRAGMENT_SHADER);
            glShaderSource(fragmentShader, fragmentSource);
            glCompileShader(fragmentShader);
            checkShaderCompilation(fragmentShader, "FRAGMENT");
            
            // Create and link program
            int program = glCreateProgram();
            glAttachShader(program, vertexShader);
            glAttachShader(program, fragmentShader);
            glLinkProgram(program);
            checkProgramLinking(program);
            
            // Clean up compiled shaders
            glDeleteShader(vertexShader);
            glDeleteShader(fragmentShader);
            
            return program;
        }

        // Check shader compilation
        private void checkShaderCompilation(int shader, String type) {
            int success = glGetShaderi(shader, GL_COMPILE_STATUS);
            if (success == GL_FALSE) {
                int length = glGetShaderi(shader, GL_INFO_LOG_LENGTH);
                String infoLog = glGetShaderInfoLog(shader, length);
                System.err.println(type + " SHADER COMPILATION ERROR:\n" + infoLog);
            }
        }

        // Check shader program linking
        private void checkProgramLinking(int program) {
            int success = glGetProgrami(program, GL_LINK_STATUS);
            if (success == GL_FALSE) {
                int length = glGetProgrami(program, GL_INFO_LOG_LENGTH);
                String infoLog = glGetProgramInfoLog(program, length);
                System.err.println("PROGRAM LINKING ERROR:\n" + infoLog);
            }
        }

        // Load shader source code from file
        private String loadShaderSource(String filename, String fallback) {
            try {
                // Try to load from resources
                InputStream inputStream = getClass().getClassLoader().getResourceAsStream(filename);
                if (inputStream != null) {
                    System.out.println("Loading shader from resources: " + filename);
                    return new String(inputStream.readAllBytes());
                }
                
                // Try to load from file system
                java.nio.file.Path filePath = java.nio.file.Paths.get(filename);
                if (java.nio.file.Files.exists(filePath)) {
                    System.out.println("Loading shader from file: " + filename);
                    return new String(java.nio.file.Files.readAllBytes(filePath));
                }
                
                // Use fallback
                System.out.println("Using fallback shader for: " + filename);
                return fallback;
            } catch (IOException e) {
                System.out.println("Error loading shader, using fallback: " + e.getMessage());
                return fallback;
            }
        }

        // Clean shader programs
        public void cleanup() {
            if (mainShaderProgram != 0) glDeleteProgram(mainShaderProgram);
            if (pickingShaderProgram != 0) glDeleteProgram(pickingShaderProgram);
            if (cursorShaderProgram != 0) glDeleteProgram(cursorShaderProgram);
        }

        // Fallback vertex shader (if file not found)
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

        // Fallback fragment shader (if file not found)
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
            "    vec3 result = ambient + diffuse + specular;\n" +
            "    FragColor = vec4(result * objectColor, 1.0);\n" +
            "}";
    }

    // ========== GEOMETRY MANAGER CLASS (GeometryManager) ==========
    // Manages object geometry (VBO, VAO, EBO)
    private static class GeometryManager {
        private int cubeVAO, cubeVBO;        // Cube (regular objects)
        private int gridVAO, gridVBO;        // Grid
        private int lightVAO, lightVBO, lightEBO; // Sphere (light sources)
        private int cursorVAO, cursorVBO;    // 3D cursor
        private float[] sphereVertices;      // Sphere vertices
        private int[] sphereIndices;         // Sphere indices

        public GeometryManager() {
            setupGeometry(); // Initialize all geometry
        }

        // Initialize all geometry
        private void setupGeometry() {
            setupCube();      // Cube geometry
            setupGrid();      // Grid geometry
            setupLightSphere(); // Sphere geometry (light source)
            setup3DCursor();  // 3D cursor geometry
        }

        // Set up cube geometry
        private void setupCube() {
            // Cube vertices with normals (36 vertices, 6 faces with 2 triangles each)
            float[] cubeVertices = {
                // Back face
                -0.5f, -0.5f, -0.5f,  0.0f, 0.0f, -1.0f, 0.5f, -0.5f, -0.5f,  0.0f, 0.0f, -1.0f,
                0.5f,  0.5f, -0.5f,  0.0f, 0.0f, -1.0f, 0.5f,  0.5f, -0.5f,  0.0f, 0.0f, -1.0f,
                -0.5f,  0.5f, -0.5f,  0.0f, 0.0f, -1.0f, -0.5f, -0.5f, -0.5f,  0.0f, 0.0f, -1.0f,
                
                // Front face
                -0.5f, -0.5f,  0.5f,  0.0f, 0.0f, 1.0f, 0.5f, -0.5f,  0.5f,  0.0f, 0.0f, 1.0f,
                0.5f,  0.5f,  0.5f,  0.0f, 0.0f, 1.0f, 0.5f,  0.5f,  0.5f,  0.0f, 0.0f, 1.0f,
                -0.5f,  0.5f,  0.5f,  0.0f, 0.0f, 1.0f, -0.5f, -0.5f,  0.5f,  0.0f, 0.0f, 1.0f,
                
                // Left face
                -0.5f,  0.5f,  0.5f, -1.0f, 0.0f, 0.0f, -0.5f,  0.5f, -0.5f, -1.0f, 0.0f, 0.0f,
                -0.5f, -0.5f, -0.5f, -1.0f, 0.0f, 0.0f, -0.5f, -0.5f, -0.5f, -1.0f, 0.0f, 0.0f,
                -0.5f, -0.5f,  0.5f, -1.0f, 0.0f, 0.0f, -0.5f,  0.5f,  0.5f, -1.0f, 0.0f, 0.0f,
                
                // Right face
                0.5f,  0.5f,  0.5f,  1.0f, 0.0f, 0.0f, 0.5f,  0.5f, -0.5f,  1.0f, 0.0f, 0.0f,
                0.5f, -0.5f, -0.5f,  1.0f, 0.0f, 0.0f, 0.5f, -0.5f, -0.5f,  1.0f, 0.0f, 0.0f,
                0.5f, -0.5f,  0.5f,  1.0f, 0.0f, 0.0f, 0.5f,  0.5f,  0.5f,  1.0f, 0.0f, 0.0f,
                
                // Bottom face
                -0.5f, -0.5f, -0.5f,  0.0f, -1.0f, 0.0f, 0.5f, -0.5f, -0.5f,  0.0f, -1.0f, 0.0f,
                0.5f, -0.5f,  0.5f,  0.0f, -1.0f, 0.0f, 0.5f, -0.5f,  0.5f,  0.0f, -1.0f, 0.0f,
                -0.5f, -0.5f,  0.5f,  0.0f, -1.0f, 0.0f, -0.5f, -0.5f, -0.5f,  0.0f, -1.0f, 0.0f,
                
                // Top face
                -0.5f,  0.5f, -0.5f,  0.0f, 1.0f, 0.0f, 0.5f,  0.5f, -0.5f,  0.0f, 1.0f, 0.0f,
                0.5f,  0.5f,  0.5f,  0.0f, 1.0f, 0.0f, 0.5f,  0.5f,  0.5f,  0.0f, 1.0f, 0.0f,
                -0.5f,  0.5f,  0.5f,  0.0f, 1.0f, 0.0f, -0.5f,  0.5f, -0.5f,  0.0f, 1.0f, 0.0f
            };

            // Create VAO and VBO for cube
            cubeVAO = glGenVertexArrays();
            cubeVBO = glGenBuffers();

            glBindVertexArray(cubeVAO);
            glBindBuffer(GL_ARRAY_BUFFER, cubeVBO);
            glBufferData(GL_ARRAY_BUFFER, cubeVertices, GL_STATIC_DRAW);

            // Set vertex attributes (position and normal)
            glVertexAttribPointer(0, 3, GL_FLOAT, false, 6 * Float.BYTES, 0);
            glEnableVertexAttribArray(0);
            glVertexAttribPointer(1, 3, GL_FLOAT, false, 6 * Float.BYTES, 3 * Float.BYTES);
            glEnableVertexAttribArray(1);
        }

        // Set up grid geometry
        private void setupGrid() {
            float gridSize = 10.0f;   // Grid size
            int gridLines = 20;       // Number of lines
            List<Float> gridVertices = new ArrayList<>();

            // Vertical lines (Z axis)
            for (int i = 0; i <= gridLines; i++) {
                float z = -gridSize + (2 * gridSize * i / gridLines);
                gridVertices.add(-gridSize); gridVertices.add(0.0f); gridVertices.add(z);
                gridVertices.add(gridSize); gridVertices.add(0.0f); gridVertices.add(z);
            }

            // Horizontal lines (X axis)
            for (int i = 0; i <= gridLines; i++) {
                float x = -gridSize + (2 * gridSize * i / gridLines);
                gridVertices.add(x); gridVertices.add(0.0f); gridVertices.add(-gridSize);
                gridVertices.add(x); gridVertices.add(0.0f); gridVertices.add(gridSize);
            }

            // Convert list to array
            float[] gridArray = new float[gridVertices.size()];
            for (int i = 0; i < gridVertices.size(); i++) {
                gridArray[i] = gridVertices.get(i);
            }

            // Create VAO and VBO for grid
            gridVAO = glGenVertexArrays();
            gridVBO = glGenBuffers();

            glBindVertexArray(gridVAO);
            glBindBuffer(GL_ARRAY_BUFFER, gridVBO);
            glBufferData(GL_ARRAY_BUFFER, gridArray, GL_STATIC_DRAW);
            glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0);
            glEnableVertexAttribArray(0);
        }

        // Set up sphere geometry (for light sources)
        private void setupLightSphere() {
            createSphereGeometry(0.2f, 16, 16); // Create sphere with radius 0.2

            // Create VAO, VBO and EBO for sphere
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
        
        // Set up 3D cursor geometry (like Blender)
        private void setup3DCursor() {
            // Create cross-shaped cursor with three axes
            float size = 0.5f; // Cursor size
            float[] cursorVertices = {
                // X axis (red) - line along X
                -size, 0.0f, 0.0f,
                size, 0.0f, 0.0f,
                
                // Y axis (green) - line along Y
                0.0f, -size, 0.0f,
                0.0f, size, 0.0f,
                
                // Z axis (blue) - line along Z
                0.0f, 0.0f, -size,
                0.0f, 0.0f, size,
                
                // Circle in XY plane (white)
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

            // Create VAO and VBO for cursor
            cursorVAO = glGenVertexArrays();
            cursorVBO = glGenBuffers();

            glBindVertexArray(cursorVAO);
            glBindBuffer(GL_ARRAY_BUFFER, cursorVBO);
            glBufferData(GL_ARRAY_BUFFER, cursorVertices, GL_STATIC_DRAW);
            glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0);
            glEnableVertexAttribArray(0);
        }

        // Create sphere geometry (parametric)
        private void createSphereGeometry(float radius, int sectors, int stacks) {
            List<Float> vertices = new ArrayList<>();
            List<Integer> indices = new ArrayList<>();

            float sectorStep = (float) (2 * Math.PI / sectors);
            float stackStep = (float) (Math.PI / stacks);

            // Generate sphere vertices
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

            // Generate indices for triangles
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

            // Convert to arrays
            sphereVertices = new float[vertices.size()];
            for (int i = 0; i < vertices.size(); i++) sphereVertices[i] = vertices.get(i);

            sphereIndices = new int[indices.size()];
            for (int i = 0; i < indices.size(); i++) sphereIndices[i] = indices.get(i);
        }

        // Render cube with given model matrix
        public void renderCube(float[] modelMatrix) {
            int modelLoc = glGetUniformLocation(glGetInteger(GL_CURRENT_PROGRAM), "model");
            glUniformMatrix4fv(modelLoc, false, modelMatrix);
            glBindVertexArray(cubeVAO);
            glDrawArrays(GL_TRIANGLES, 0, 36);
        }

        // Render sphere (light source)
        public void renderLight(float[] modelMatrix) {
            int modelLoc = glGetUniformLocation(glGetInteger(GL_CURRENT_PROGRAM), "model");
            glUniformMatrix4fv(modelLoc, false, modelMatrix);
            glBindVertexArray(lightVAO);
            glDrawElements(GL_TRIANGLES, sphereIndices.length, GL_UNSIGNED_INT, 0);
        }

        // Render grid
        public void renderGrid() {
            float[] modelMatrix = new float[16];
            modelMatrix[0] = 1.0f; modelMatrix[5] = 1.0f; modelMatrix[10] = 1.0f; modelMatrix[15] = 1.0f;
            
            int modelLoc = glGetUniformLocation(glGetInteger(GL_CURRENT_PROGRAM), "model");
            glUniformMatrix4fv(modelLoc, false, modelMatrix);
            glBindVertexArray(gridVAO);
            glDrawArrays(GL_LINES, 0, 84); // 84 vertices in grid (21 lines * 2 ends * 2 directions)
        }
        
        // Render 3D cursor
        public void render3DCursor(float[] position, float size, ShaderManager shaderManager, 
                                  Camera camera, Window window) {
            shaderManager.useCursorShader(); // Use cursor shader
            
            // Set camera matrices for cursor
            shaderManager.setViewMatrix(camera.getViewMatrix());
            shaderManager.setProjectionMatrix(camera.getProjectionMatrix(window.getWidth(), window.getHeight()));
            
            // Create model matrix for cursor
            float[] modelMatrix = new float[16];
            modelMatrix[0] = size; modelMatrix[5] = size; modelMatrix[10] = size; modelMatrix[15] = 1.0f;
            modelMatrix[12] = position[0];
            modelMatrix[13] = position[1];
            modelMatrix[14] = position[2];
            
            // Set model matrix
            int modelLoc = glGetUniformLocation(glGetInteger(GL_CURRENT_PROGRAM), "model");
            glUniformMatrix4fv(modelLoc, false, modelMatrix);
            
            // Disable depth test so cursor is always on top
            glDisable(GL_DEPTH_TEST);
            
            // Set line width
            glLineWidth(2.0f);
            glBindVertexArray(cursorVAO);
            
            // Render axes with different colors
            shaderManager.setObjectColor(1.0f, 0.0f, 0.0f); // Red - X axis
            glDrawArrays(GL_LINES, 0, 2);
            
            shaderManager.setObjectColor(0.0f, 1.0f, 0.0f); // Green - Y axis
            glDrawArrays(GL_LINES, 2, 2);
            
            shaderManager.setObjectColor(0.0f, 0.0f, 1.0f); // Blue - Z axis
            glDrawArrays(GL_LINES, 4, 2);
            
            shaderManager.setObjectColor(1.0f, 1.0f, 1.0f); // White - circle
            glDrawArrays(GL_LINES, 6, 16);
            
            // Restore settings
            glLineWidth(1.0f);
            glEnable(GL_DEPTH_TEST);
            shaderManager.useMainShader(); // Return to main shader
        }

        // Render outline of selected object
        public void renderOutline(GameObject obj, ShaderManager shaderManager) {
            // Disable depth test and enable line mode
            glDisable(GL_DEPTH_TEST);
            glPolygonMode(GL_FRONT_AND_BACK, GL_LINE);
            glLineWidth(2.0f);

            // Disable lighting for outline
            shaderManager.setUseLighting(false);
            shaderManager.setObjectColor(1.0f, 0.0f, 0.0f); // Red outline

            // Get object's model matrix and slightly enlarge it
            float[] modelMatrix = obj.getModelMatrix();
            float outlineScale = 1.05f; // Small enlargement for outline
            modelMatrix[0] *= outlineScale; 
            modelMatrix[5] *= outlineScale; 
            modelMatrix[10] *= outlineScale;

            // Set model matrix
            int modelLoc = glGetUniformLocation(glGetInteger(GL_CURRENT_PROGRAM), "model");
            glUniformMatrix4fv(modelLoc, false, modelMatrix);

            // Render object depending on type
            if (obj.getType() == GameObject.Type.REGULAR) {
                glBindVertexArray(cubeVAO);
                glDrawArrays(GL_TRIANGLES, 0, 36);
            } else {
                glBindVertexArray(lightVAO);
                glDrawElements(GL_TRIANGLES, sphereIndices.length, GL_UNSIGNED_INT, 0);
            }

            // Restore settings
            glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
            glEnable(GL_DEPTH_TEST);
            shaderManager.setUseLighting(true);
        }

        // Clean graphics resources
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

    // ========== FPS COUNTER CLASS (FPSCounter) ==========
    // Calculates and averages FPS over the last second
    private static class FPSCounter {
        private float fps = 0.0f;           // Current FPS
        private float timeAccumulator = 0.0f; // Time accumulator
        private int frameCount = 0;         // Frame counter
        
        // Update counter (called each frame)
        public void update(float deltaTime) {
            timeAccumulator += deltaTime;
            frameCount++;
            
            // Recalculate FPS every second
            if (timeAccumulator >= 1.0f) {
                fps = frameCount / timeAccumulator;
                frameCount = 0;
                timeAccumulator = 0.0f;
            }
        }
        
        // Get current FPS
        public float getFPS() {
            return fps;
        }
    }
}
