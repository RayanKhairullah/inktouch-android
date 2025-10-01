package com.example.easycoff.product;

public class CartItem {
    private EasyCoffProduct product;
    private int quantity;

    public CartItem(EasyCoffProduct product, int quantity) {
        this.product = product;
        this.quantity = quantity;
    }

    public EasyCoffProduct getProduct() { return product; }
    public int getQuantity() { return quantity; }

    public void setProduct(EasyCoffProduct product) { this.product = product; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public double getLineTotal() { return (product != null ? product.getPrice() : 0) * quantity; }
}
