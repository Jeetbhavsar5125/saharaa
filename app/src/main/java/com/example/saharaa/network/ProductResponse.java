package com.example.saharaa.network;

import com.google.gson.annotations.SerializedName;

public class ProductResponse {
    @SerializedName("status")
    public int status; // 1 = found, 0 = not found

    @SerializedName("product")
    public Product product;

    public static class Product {
        @SerializedName("product_name")
        public String productName;

        @SerializedName("brands")
        public String brands;

        @SerializedName("ingredients_text")
        public String ingredientsText;

        @SerializedName("nutriments")
        public Nutriments nutriments;
    }

    public static class Nutriments {
        @SerializedName("energy-kcal_100g")
        public Double energyKcal;
    }
}
