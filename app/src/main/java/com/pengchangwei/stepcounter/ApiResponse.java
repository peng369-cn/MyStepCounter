package com.pengchangwei.stepcounter;

/**
 * 后端统一响应格式 Result<T> 的映射，泛型 data 字段根据不同接口变化。
 */
public class ApiResponse<T> {

    private int code;
    private String message;
    private T data;

    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public T getData() { return data; }
    public void setData(T data) { this.data = data; }

    public boolean isSuccess() {
        return code == 200;
    }
}
