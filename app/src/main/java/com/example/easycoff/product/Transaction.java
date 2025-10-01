package com.example.easycoff.product;

import java.util.ArrayList;
import java.util.List;

public class Transaction {
    private String id;
    private double total;
    private double amount_paid;
    private double change;
    private String timestamp;
    private String customer_name;

    // Local-only cart items (not returned by list endpoint)
    private transient List<EasyCoffProduct> items = new ArrayList<>();

    public String getId() { return id; }
    public double getTotal() { return total; }
    public double getAmountPaid() { return amount_paid; }
    public double getChange() { return change; }
    public String getTimestamp() { return timestamp; }
    public String getCustomerName() { return customer_name; }

    public void setId(String id) { this.id = id; }
    public void setTotal(double total) { this.total = total; }
    public void setAmountPaid(double amountPaid) { this.amount_paid = amountPaid; }
    public void setChange(double change) { this.change = change; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    public void setCustomerName(String customerName) { this.customer_name = customerName; }

    public List<EasyCoffProduct> getItems() { return items; }
    public void setItems(List<EasyCoffProduct> items) { this.items = items; }
    public void addItem(EasyCoffProduct p) { this.items.add(p); }
}
