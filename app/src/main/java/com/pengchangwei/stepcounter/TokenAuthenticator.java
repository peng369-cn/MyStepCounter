package com.pengchangwei.stepcounter;

import java.io.IOException;
import android.util.Log;

import okhttp3.Authenticator;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;
import retrofit2.Call;

/**
 * OkHttp Authenticator，收到 401 时自动用 refreshToken 换新的 accessToken，
 * 刷新成功后使用新 Token 重试原请求，刷新失败返回 null 让上层处理。
 */
public class TokenAuthenticator implements Authenticator {

    private static final String TAG = "TokenAuth";

    private final RetrofitClient retrofitClient;

    public TokenAuthenticator(RetrofitClient retrofitClient) {
        this.retrofitClient = retrofitClient;
    }

    @Override
    public Request authenticate(Route route, Response response) throws IOException {
        String refreshToken = retrofitClient.getRefreshToken();
        String requestUrl = response.request().url().toString();
        Log.d(TAG, "收到401, URL=" + requestUrl
                + ", 响应码=" + response.code()
                + ", refreshToken存在=" + (refreshToken != null && !refreshToken.isEmpty()));

        if (refreshToken == null || refreshToken.isEmpty()) {
            Log.e(TAG, "refreshToken为空，无法刷新 → 返回null，401将穿透到上层");
            return null;
        }

        synchronized (retrofitClient) {
            String currentToken = retrofitClient.getToken();
            String reqAuthHeader = response.request().header("Authorization");
            String reqTokenPart = (reqAuthHeader != null && reqAuthHeader.startsWith("Bearer "))
                    ? reqAuthHeader.substring(7) : "null";
            String reqTokenPreview = reqTokenPart.length() > 8
                    ? reqTokenPart.substring(0, 8) : reqTokenPart;

            if (currentToken != null && !currentToken.isEmpty()) {
                if (reqAuthHeader == null || !reqAuthHeader.endsWith(currentToken)) {
                    Log.d(TAG, "其他线程已刷新，用新Token重试, 新Token前8位="
                            + currentToken.substring(0, Math.min(8, currentToken.length())));
                    return response.request().newBuilder()
                            .header("Authorization", "Bearer " + currentToken)
                            .build();
                }
            }

            String rtPreview = refreshToken.length() > 8
                    ? refreshToken.substring(0, 8) : refreshToken;
            Log.d(TAG, "开始刷新Token... accessToken前8位=" + reqTokenPreview
                    + ", refreshToken前8位=" + rtPreview);

            Call<ApiResponse<LoginData>> call = retrofitClient.getApiService()
                    .refresh(java.util.Collections.singletonMap("refreshToken", refreshToken));

            try {
                retrofit2.Response<ApiResponse<LoginData>> refreshResponse = call.execute();
                Log.d(TAG, "刷新请求完成: HTTPcode=" + refreshResponse.code()
                        + ", isSuccessful=" + refreshResponse.isSuccessful()
                        + ", bodyNull=" + (refreshResponse.body() == null));

                if (refreshResponse.isSuccessful() && refreshResponse.body() != null
                        && refreshResponse.body().isSuccess()) {
                    LoginData data = refreshResponse.body().getData();
                    retrofitClient.saveTokens(data.getAccessToken(), data.getRefreshToken());
                    String newAtPreview = data.getAccessToken().length() > 8
                            ? data.getAccessToken().substring(0, 8) : data.getAccessToken();
                    Log.d(TAG, "刷新成功, 新accessToken前8位=" + newAtPreview);
                    return response.request().newBuilder()
                            .header("Authorization", "Bearer " + data.getAccessToken())
                            .build();
                } else {
                    int bodyCode = refreshResponse.body() != null
                            ? refreshResponse.body().getCode() : -1;
                    String bodyMsg = refreshResponse.body() != null
                            ? refreshResponse.body().getMessage() : "null";
                    Log.e(TAG, "刷新失败 → 返回null: HTTPcode=" + refreshResponse.code()
                            + ", bodyCode=" + bodyCode + ", bodyMsg=" + bodyMsg);
                }
            } catch (IOException e) {
                Log.e(TAG, "刷新网络异常 → 返回null: " + e.getClass().getSimpleName()
                        + " - " + e.getMessage());
                return null;
            }
            Log.w(TAG, "刷新未成功，返回null，401将穿透到上层");
            return null;
        }
    }
}
