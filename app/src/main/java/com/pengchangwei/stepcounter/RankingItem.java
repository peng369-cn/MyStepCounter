package com.pengchangwei.stepcounter;

/**
 * 排行榜单项数据，对应后端排行榜接口返回的 data 列表中每个元素。
 */
public class RankingItem {

    private int rank;
    private long userId;
    private String nickname;
    private int stepCount;

    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }

    public long getUserId() { return userId; }
    public void setUserId(long userId) { this.userId = userId; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public int getStepCount() { return stepCount; }
    public void setStepCount(int stepCount) { this.stepCount = stepCount; }
}
