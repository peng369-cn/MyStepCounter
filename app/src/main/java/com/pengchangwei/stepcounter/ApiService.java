package com.pengchangwei.stepcounter;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

/**
 * 后端所有接口的 Retrofit 声明。
 */
public interface ApiService {

    @POST("/api/user/register")
    Call<ApiResponse<LoginData>> register(@Body Map<String, String> body);

    @POST("/api/user/login")
    Call<ApiResponse<LoginData>> login(@Body Map<String, String> body);

    @POST("/api/user/refresh")
    Call<ApiResponse<LoginData>> refresh(@Body Map<String, String> body);

    @POST("/api/step/upload")
    Call<ApiResponse<String>> uploadSteps(@Body Map<String, Object> body);

    @GET("/api/step/daily")
    Call<ApiResponse<StepServiceData>> getDailySteps(@Query("date") String date);

    @GET("/api/step/weekly")
    Call<ApiResponse<Map<String, StepServiceData>>> getWeeklySteps(
            @Query("startDate") String startDate,
            @Query("endDate") String endDate);

    @GET("/api/step/monthly")
    Call<ApiResponse<Map<String, StepServiceData>>> getMonthlySteps(
            @Query("startDate") String startDate,
            @Query("endDate") String endDate);

    @GET("/api/ranking/daily")
    Call<ApiResponse<Map<String, Object>>> getDailyRanking();

    @GET("/api/user/profile")
    Call<ApiResponse<Map<String, Object>>> getProfile();
}
