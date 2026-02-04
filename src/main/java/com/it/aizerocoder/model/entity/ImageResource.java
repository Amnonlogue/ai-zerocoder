package com.it.aizerocoder.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 图片资源 实体类
 *
 * @author <a href="https://github.com/Amnonlogue">wanghf</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("image_resource")
public class ImageResource implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    /**
     * 关联应用ID
     */
    @Column("appId")
    private Long appId;

    /**
     * 图片类型：CONTENT/ILLUSTRATION/DIAGRAM/LOGO
     */
    private String category;

    /**
     * 图片描述
     */
    private String description;

    /**
     * 图片URL
     */
    private String url;

    /**
     * 创建时间
     */
    @Column("createTime")
    private LocalDateTime createTime;

    /**
     * 是否删除
     */
    @Column(value = "isDelete", isLogicDelete = true)
    private Integer isDelete;

}
