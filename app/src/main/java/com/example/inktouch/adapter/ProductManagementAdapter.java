package com.example.inktouch.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import com.bumptech.glide.Glide;
import com.example.inktouch.R;
import com.example.inktouch.product.IceCreamProduct;
import com.google.android.material.card.MaterialCardView;
import androidx.core.content.ContextCompat;

public class ProductManagementAdapter extends ArrayAdapter<IceCreamProduct> {
    private Context context;
    private List<IceCreamProduct> products;
    private boolean isDeletingMode;
    private List<IceCreamProduct> selectedProducts;

    public ProductManagementAdapter(Context context, List<IceCreamProduct> products) {
        super(context, 0, products);
        this.context = context;
        this.products = products;
        this.isDeletingMode = false;
        this.selectedProducts = new ArrayList<>();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.product_management_item, parent, false);
        }

        IceCreamProduct product = products.get(position);

        TextView tvName = convertView.findViewById(R.id.tv_product_name);
        TextView tvPrice = convertView.findViewById(R.id.tv_product_price);
        TextView tvStock = convertView.findViewById(R.id.tv_product_stock);
        ImageView ivProduct = convertView.findViewById(R.id.iv_product);
        CheckBox cbSelect = convertView.findViewById(R.id.cb_select);
        MaterialCardView card = convertView.findViewById(R.id.card_root);

        tvName.setText(product.getName());
        tvPrice.setText(String.format("Rp %.0f", product.getPrice()));
        tvStock.setText(String.format("Stok: %d", product.getStock()));

        String url = product.getImageUrl();
        if (url != null && !url.isEmpty()) {
            Glide.with(context)
                    .load(url)
                    .placeholder(R.drawable.inktouch)
                    .error(R.drawable.inktouch)
                    .into(ivProduct);
        } else {
            try {
                if (product.getImageResId() > 0) {
                    ivProduct.setImageResource(product.getImageResId());
                } else {
                    ivProduct.setImageResource(R.drawable.inktouch);
                }
            } catch (Exception e) {
                ivProduct.setImageResource(R.drawable.inktouch);
            }
        }

        // Tampilkan indikator untuk mode hapus (checkbox + card stroke)
        cbSelect.setVisibility(isDeletingMode ? View.VISIBLE : View.GONE);
        boolean isChecked = selectedProducts.contains(product);
        cbSelect.setOnCheckedChangeListener(null);
        cbSelect.setChecked(isChecked);

        if (card != null) {
            int stroke = (int) (2 * context.getResources().getDisplayMetrics().density);
            int strokeColor = ContextCompat.getColor(getContext(), R.color.pink_deep);
            card.setStrokeWidth(isChecked ? stroke : 0);
            card.setStrokeColor(strokeColor);
        }

        cbSelect.setOnCheckedChangeListener((buttonView, checked) -> {
            if (checked) {
                if (!selectedProducts.contains(product)) selectedProducts.add(product);
            } else {
                selectedProducts.remove(product);
            }
            notifyDataSetChanged();
        });

        return convertView;
    }

    public void setDeletingMode(boolean deletingMode) {
        this.isDeletingMode = deletingMode;
        notifyDataSetChanged();
    }

    public void toggleSelection(IceCreamProduct product) {
        if (selectedProducts.contains(product)) {
            selectedProducts.remove(product);
        } else {
            selectedProducts.add(product);
        }
        notifyDataSetChanged();
    }

    public List<IceCreamProduct> getSelectedProducts() {
        return selectedProducts;
    }
}