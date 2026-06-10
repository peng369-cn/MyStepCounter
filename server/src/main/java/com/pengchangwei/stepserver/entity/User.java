package com.pengchangwei.stepserver.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user")
public class User {

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户名，登录用，数据库做了唯一约束 */
    private String username;

    /** BCrypt加密后的密码，永远不存明文 */
    private String password;

    /** 昵称，排行榜对外展示的名字 */
    private String nickname;

    /** token版本号，改密码时+1使旧token失效 */
    private Integer tokenVersion;

    /** 注册时间，插入时数据库自动填 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 最后修改时间，每次更新自动刷新 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** MyBatis-Plus逻辑删除标记，查询自动过滤deleted=1的记录 */
    @TableLogic
    private Integer deleted;
}
