package com.example.easycoff.product;

public class Category {
    private String id;
    private String name;
    private String description;
    private Boolean is_active;

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public boolean isActive() { return is_active == null ? true : is_active; }

    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setActive(boolean active) { this.is_active = active; }
}
