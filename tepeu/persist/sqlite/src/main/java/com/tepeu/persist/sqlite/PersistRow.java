package com.tepeu.persist.sqlite;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 表行。MyBatis-Plus 实体，不是 {@link com.tepeu.persist.PersistRecord}。 */
@TableName("persist_record")
public class PersistRow {

    /** 写入序。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 调用方命名的集合。 */
    private String space;

    /** {@link com.tepeu.persist.PersistRecord#key()}。 */
    @TableField("rec_key")
    private String recKey;

    /** {@link com.tepeu.persist.PersistRecord#fields()} 的 JSON。 */
    private String fields;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSpace() {
        return space;
    }

    public void setSpace(String space) {
        this.space = space;
    }

    public String getRecKey() {
        return recKey;
    }

    public void setRecKey(String recKey) {
        this.recKey = recKey;
    }

    public String getFields() {
        return fields;
    }

    public void setFields(String fields) {
        this.fields = fields;
    }
}
