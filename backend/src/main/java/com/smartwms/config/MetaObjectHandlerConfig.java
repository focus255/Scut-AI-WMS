/**
 * MyBatis-Plus 字段自动填充处理器，自动维护 created_at / updated_at 审计字段。
 *
 * @author Focus
 * @date 2026-06-03
 */
package com.smartwms.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class MetaObjectHandlerConfig implements MetaObjectHandler {

    private static final Logger log = LoggerFactory.getLogger(MetaObjectHandlerConfig.class);

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        // 对标记了 @TableField(fill = FieldFill.INSERT) 的字段自动赋值
        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
        // 审计字段
        this.strictInsertFill(metaObject, "createdBy", String.class, "system");
        this.strictInsertFill(metaObject, "updatedBy", String.class, "system");
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        // 对标记了 @TableField(fill = FieldFill.INSERT_UPDATE) 的字段自动赋值
        this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
        this.strictUpdateFill(metaObject, "updatedBy", String.class, "system");
    }
}
