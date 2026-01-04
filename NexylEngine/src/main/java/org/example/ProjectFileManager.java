package org.example;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class ProjectFileManager {
    private static final String FILE_SIGNATURE = "NEXYLENGINE";
    private static final int FILE_VERSION = 1;
    
    public void saveProject(Main.Scene scene, String filePath) throws IOException {
        try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(filePath))) {
            // Write file signature and version
            dos.writeUTF(FILE_SIGNATURE);
            dos.writeInt(FILE_VERSION);
            
            // Write scene data
            writeSceneData(scene, dos);
            
            // Write camera data
            writeCameraData(scene.getCameraController(), dos);
            
            // Write cursor data
            writeCursorData(scene, dos);
            
            // Write selection data
            writeSelectionData(scene, dos);
            
            // Write transformation modes
            dos.writeBoolean(scene.isTranslating());
            dos.writeBoolean(scene.isRotating());
            dos.writeBoolean(scene.isScaling());
            
            // Write next object ID
            dos.writeInt(scene.getNextObjectId());
            
            System.out.println("Project saved successfully: " + filePath);
        }
    }
    
    public Main.Scene loadProject(String filePath) throws IOException, ClassNotFoundException {
        try (DataInputStream dis = new DataInputStream(new FileInputStream(filePath))) {
            // Verify file signature and version
            String signature = dis.readUTF();
            if (!signature.equals(FILE_SIGNATURE)) {
                throw new IOException("Invalid file format: not a Nexyl Engine project");
            }
            
            int version = dis.readInt();
            if (version != FILE_VERSION) {
                throw new IOException("Unsupported file version: " + version);
            }
            
            // Create new scene
            Main.Scene scene = new Main.Scene();
            
            // Read scene data
            readSceneData(scene, dis);
            
            // Read camera data
            readCameraData(scene.getCameraController(), dis);
            
            // Read cursor data
            readCursorData(scene, dis);
            
            // Read selection data
            readSelectionData(scene, dis);
            
            // Read transformation modes
            scene.setTranslationMode(dis.readBoolean());
            scene.setRotationMode(dis.readBoolean());
            scene.setScalingMode(dis.readBoolean());
            
            // Read next object ID
            scene.setNextObjectId(dis.readInt());
            
            System.out.println("Project loaded successfully: " + filePath);
            return scene;
        }
    }
    
    private void writeSceneData(Main.Scene scene, DataOutputStream dos) throws IOException {
        List<Main.GameObject> objects = scene.getGameObjects();
        dos.writeInt(objects.size());
        
        for (Main.GameObject obj : objects) {
            // Write object type
            dos.writeInt(obj.getType().ordinal());
            
            // Write object ID
            dos.writeInt(obj.getId());
            
            // Write object name
            dos.writeUTF(obj.getName());
            
            // Write position
            float[] pos = obj.getPosition();
            dos.writeFloat(pos[0]);
            dos.writeFloat(pos[1]);
            dos.writeFloat(pos[2]);
            
            // Write rotation
            float[] rot = obj.getRotation();
            dos.writeFloat(rot[0]);
            dos.writeFloat(rot[1]);
            dos.writeFloat(rot[2]);
            
            // Write scale
            float[] scale = obj.getScale();
            dos.writeFloat(scale[0]);
            dos.writeFloat(scale[1]);
            dos.writeFloat(scale[2]);
            
            // Write material if object is REGULAR
            if (obj.getType() == Main.GameObject.Type.REGULAR) {
                Main.Material mat = obj.getMaterial();
                if (mat != null) {
                    dos.writeBoolean(true);
                    float[] ambient = mat.getAmbient();
                    float[] diffuse = mat.getDiffuse();
                    float[] specular = mat.getSpecular();
                    
                    dos.writeFloat(ambient[0]);
                    dos.writeFloat(ambient[1]);
                    dos.writeFloat(ambient[2]);
                    
                    dos.writeFloat(diffuse[0]);
                    dos.writeFloat(diffuse[1]);
                    dos.writeFloat(diffuse[2]);
                    
                    dos.writeFloat(specular[0]);
                    dos.writeFloat(specular[1]);
                    dos.writeFloat(specular[2]);
                    
                    dos.writeFloat(mat.getShininess());
                } else {
                    dos.writeBoolean(false);
                }
            } else {
                dos.writeBoolean(false);
            }
        }
    }
    
    private void readSceneData(Main.Scene scene, DataInputStream dis) throws IOException {
        int objectCount = dis.readInt();
        List<Main.GameObject> objects = new ArrayList<>();
        
        for (int i = 0; i < objectCount; i++) {
            // Read object type
            Main.GameObject.Type type = Main.GameObject.Type.values()[dis.readInt()];
            
            // Read object ID
            int id = dis.readInt();
            
            // Read object name
            String name = dis.readUTF();
            
            // Read position
            float posX = dis.readFloat();
            float posY = dis.readFloat();
            float posZ = dis.readFloat();
            
            // Read rotation
            float rotX = dis.readFloat();
            float rotY = dis.readFloat();
            float rotZ = dis.readFloat();
            
            // Read scale
            float scaleX = dis.readFloat();
            float scaleY = dis.readFloat();
            float scaleZ = dis.readFloat();
            
            // Read material
            Main.Material material = null;
            boolean hasMaterial = dis.readBoolean();
            
            if (hasMaterial) {
                float ambientR = dis.readFloat();
                float ambientG = dis.readFloat();
                float ambientB = dis.readFloat();
                
                float diffuseR = dis.readFloat();
                float diffuseG = dis.readFloat();
                float diffuseB = dis.readFloat();
                
                float specularR = dis.readFloat();
                float specularG = dis.readFloat();
                float specularB = dis.readFloat();
                
                float shininess = dis.readFloat();
                
                material = new Main.Material(
                    new float[]{ambientR, ambientG, ambientB},
                    new float[]{diffuseR, diffuseG, diffuseB},
                    new float[]{specularR, specularG, specularB},
                    shininess
                );
            }
            
            // Create game object
            Main.GameObject obj = new Main.GameObject(
                posX, posY, posZ,
                scaleX, scaleY, scaleZ,
                material, id, type, name
            );
            
            obj.setRotation(rotX, rotY, rotZ);
            objects.add(obj);
        }
        
        // Clear existing objects and add loaded ones
        scene.getGameObjects().clear();
        scene.getGameObjects().addAll(objects);
    }
    
    private void writeCameraData(Main.CameraController cameraController, DataOutputStream dos) throws IOException {
        // Write camera position
        float[] camPos = cameraController.getCameraPosition();
        dos.writeFloat(camPos[0]);
        dos.writeFloat(camPos[1]);
        dos.writeFloat(camPos[2]);
        
        // Write camera yaw and pitch
        dos.writeFloat(cameraController.getYaw());
        dos.writeFloat(cameraController.getPitch());
        
        // Write camera speed
        dos.writeFloat(cameraController.getSpeed());
    }
    
    private void readCameraData(Main.CameraController cameraController, DataInputStream dis) throws IOException {
        // Read camera position
        float camX = dis.readFloat();
        float camY = dis.readFloat();
        float camZ = dis.readFloat();
        cameraController.setCameraPosition(camX, camY, camZ);
        
        // Read camera yaw and pitch
        cameraController.setYaw(dis.readFloat());
        cameraController.setPitch(dis.readFloat());
        
        // Read camera speed
        cameraController.setSpeed(dis.readFloat());
        
        // Update camera vectors
        cameraController.getCamera().updateVectors();
    }
    
    private void writeCursorData(Main.Scene scene, DataOutputStream dos) throws IOException {
        float[] cursorPos = scene.getCursor3DPosition();
        dos.writeFloat(cursorPos[0]);
        dos.writeFloat(cursorPos[1]);
        dos.writeFloat(cursorPos[2]);
        
        dos.writeBoolean(scene.isCursorVisible());
        dos.writeInt(scene.getSelectionMode().ordinal());
    }
    
    private void readCursorData(Main.Scene scene, DataInputStream dis) throws IOException {
        float cursorX = dis.readFloat();
        float cursorY = dis.readFloat();
        float cursorZ = dis.readFloat();
        scene.setCursor3DPosition(cursorX, cursorY, cursorZ);
        
        scene.setCursorVisible(dis.readBoolean());
        scene.setSelectionMode(Main.Scene.SelectionMode.values()[dis.readInt()]);
    }
    
    private void writeSelectionData(Main.Scene scene, DataOutputStream dos) throws IOException {
        Main.GameObject selected = scene.getSelectedObject();
        if (selected != null) {
            dos.writeBoolean(true);
            dos.writeInt(selected.getId());
        } else {
            dos.writeBoolean(false);
        }
    }
    
    private void readSelectionData(Main.Scene scene, DataInputStream dis) throws IOException {
        boolean hasSelection = dis.readBoolean();
        if (hasSelection) {
            int selectedId = dis.readInt();
            for (Main.GameObject obj : scene.getGameObjects()) {
                if (obj.getId() == selectedId) {
                    scene.setSelectedObject(obj);
                    break;
                }
            }
        }
    }
}
