/**
 * MyBatis-Plus 字段自动填充处理器，自动维护 created_at / updated_at 审计字段。
 *
 * @author Focus
 * @date 2026-06-03
 */
package com.smartwms.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.smartwms.common.BaseContext;
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
        String username = BaseContext.getCurrentUsername();
        String operator = username == null || username.isBlank() ? "system" : username;
        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "createdBy", String.class, operator);
        this.strictInsertFill(metaObject, "updatedBy", String.class, operator);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        String username = BaseContext.getCurrentUsername();
        String operator = username == null || username.isBlank() ? "system" : username;
        this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
        this.strictUpdateFill(metaObject, "updatedBy", String.class, operator);
    }
}
