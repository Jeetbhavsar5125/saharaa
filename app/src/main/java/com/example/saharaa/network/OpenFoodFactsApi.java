package com.example.saharaa.network;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

public interface OpenFoodFactsApi {
    @GET("api/v0/product/{barcode}.json")
    Call<ProductResponse> getProduct(@Path("barcode") String barcode);
}
