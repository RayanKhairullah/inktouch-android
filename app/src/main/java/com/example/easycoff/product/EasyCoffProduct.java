package com.example.easycoff.product;

public class EasyCoffProduct {
    private String id;
    private String name;
    private double price;
    private int stock;
    private String image_url; // JSON field name
    private String category;
    private String description;
    private Boolean is_active;
    private String sku;

    // Getters for Gson
    public String getId() { return id; }
    public String getName() { return name; }
    public double getPrice() { return price; }
    public int getStock() { return stock; }
    public String getImageUrl() { return image_url; }
    public String getCategory() { return category; }
    public String getDescription() { return description; }
    public boolean isActive() { return is_active == null ? true : is_active; }
    public String getSku() { return sku; }

    // Setters (optional for local operations)
    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setPrice(double price) { this.price = price; }
    public void setStock(int stock) { this.stock = stock; }
    public void setImageUrl(String imageUrl) { this.image_url = imageUrl; }
    public void setCategory(String category) { this.category = category; }
    public void setDescription(String description) { this.description = description; }
    public void setActive(boolean active) { this.is_active = active; }
    public void setSku(String sku) { this.sku = sku; }
}
