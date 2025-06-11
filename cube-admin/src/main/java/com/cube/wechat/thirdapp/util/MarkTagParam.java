package com.cube.wechat.thirdapp.util;

import lombok.Data;

import java.util.List;

/**
 @author sjl
  * @Created date 2024/4/24 15:17
 */
@Data
public class MarkTagParam {
    private String userid;
    private String external_userid;
    private List<String> add_tag;
    private List<String> remove_tag;

    // Getters and Setters

    public String getUserid() {
        return userid;
    }

    public void setUserid(String userid) {
        this.userid = userid;
    }

    public String getExternal_userid() {
        return external_userid;
    }

    public void setExternal_userid(String external_userid) {
        this.external_userid = external_userid;
    }

    public List<String> getAdd_tag() {
        return add_tag;
    }

    public void setAdd_tag(List<String> add_tag) {
        this.add_tag = add_tag;
    }

    public List<String> getRemove_tag() {
        return remove_tag;
    }

    public void setRemove_tag(List<String> remove_tag) {
        this.remove_tag = remove_tag;
    }
}
