#ifndef SCENE_HPP
#define SCENE_HPP

#include <glm/glm.hpp>
#include <vector>
#include <string>
#include <unordered_map>

enum ObjectType {
    CUBE,
    AMBIENT_LIGHT,
    POINT_LIGHT,
    DIRECTIONAL_LIGHT
};

struct SceneObject {
    int id;
    std::string name;
    ObjectType type;
    glm::vec3 position;
    glm::vec2 rotation;
    float scale;
    glm::vec3 lightColor;
    float lightIntensity;
    glm::vec3 lightDirection;
};

class Scene {
private:
    std::vector<SceneObject> objects;
    std::unordered_map<int, size_t> objectIndexMap;
    int nextId;

public:
    Scene() : nextId(0) {}

    void addObject(const std::string& name, const glm::vec3& position, const glm::vec2& rotation, float scale) {
        SceneObject obj;
        obj.id = nextId++;
        obj.name = name;
        obj.type = CUBE;
        obj.position = position;
        obj.rotation = rotation;
        obj.scale = scale;
        obj.lightColor = glm::vec3(1.0f);
        obj.lightIntensity = 0.0f;
        obj.lightDirection = glm::vec3(0.0f, -1.0f, 0.0f);
        objectIndexMap[obj.id] = objects.size();
        objects.push_back(obj);
    }

    void addLight(const SceneObject& light) {
        SceneObject obj = light;
        obj.id = nextId++;
        objectIndexMap[obj.id] = objects.size();
        objects.push_back(obj);
    }

    void updateObjectPosition(int id, const glm::vec3& position) {
        auto it = objectIndexMap.find(id);
        if (it != objectIndexMap.end()) {
            objects[it->second].position = position;
        }
    }

    void updateObjectRotation(int id, const glm::vec2& rotation) {
        auto it = objectIndexMap.find(id);
        if (it != objectIndexMap.end()) {
            objects[it->second].rotation = rotation;
        }
    }

    void updateObjectScale(int id, float scale) {
        auto it = objectIndexMap.find(id);
        if (it != objectIndexMap.end()) {
            objects[it->second].scale = scale;
        }
    }

    bool removeObject(int id) {
        auto it = objectIndexMap.find(id);
        if (it != objectIndexMap.end()) {
            size_t index = it->second;
            objects.erase(objects.begin() + index);
            objectIndexMap.erase(id);
            
            // Rebuild index map for remaining objects
            for (size_t i = index; i < objects.size(); i++) {
                objectIndexMap[objects[i].id] = i;
            }
            return true;
        }
        return false;
    }

    SceneObject* getObject(int id) {
        auto it = objectIndexMap.find(id);
        if (it != objectIndexMap.end()) {
            return &objects[it->second];
        }
        return nullptr;
    }

    const std::vector<SceneObject>& getObjects() const {
        return objects;
    }
};

#endif
