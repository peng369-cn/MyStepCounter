package com.pengchangwei.stepcounter;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * OkHttp 拦截器，每次请求前从 SharedPreferences 取 JWT 拼到请求头。
 */
public class AuthInterceptor implements Interceptor {

    private final TokenProvider tokenProvider;

    public AuthInterceptor(TokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        String token = tokenProvider.getToken();
        Request request = chain.request();
        if (token != null && !token.isEmpty()) {
            request = request.newBuilder()
                    .header("Authorization", "Bearer " + token)
                    .build();
        }
        return chain.proceed(request);
    }

    public interface TokenProvider {
        String getToken();
    }
}
