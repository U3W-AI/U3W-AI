package com.wx.fbsir.business.certificate.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 证书模板配置对象
 * 
 * @author wxfbsir
 * @date 2026-01-10
 */
public class FormFieldConfig {
    /**
     * 申请必填字段配置
     */
    private List<FormFieldItem> formFields;

    /**
     * 所需材料配置
     */
    private List<MaterialItem> requiredMaterials;

    public List<FormFieldItem> getFormFields() {
        return formFields;
    }

    public void setFormFields(List<FormFieldItem> formFields) {
        this.formFields = formFields;
    }

    public List<MaterialItem> getRequiredMaterials() {
        return requiredMaterials;
    }

    public void setRequiredMaterials(List<MaterialItem> requiredMaterials) {
        this.requiredMaterials = requiredMaterials;
    }

    /**
     * 表单字段项
     */
    public static class FormFieldItem {
        /** 字段序号 */
        @JsonProperty("index")
        private Integer index;

        /** 字段名称 */
        @JsonProperty("fieldName")
        private String fieldName;

        /** 字段类型 */
        @JsonProperty("fieldType")
        private String fieldType;

        /** 是否必填 */
        @JsonProperty("required")
        private Boolean required;

        public Integer getIndex() {
            return index;
        }

        public void setIndex(Integer index) {
            this.index = index;
        }

        public String getFieldName() {
            return fieldName;
        }

        public void setFieldName(String fieldName) {
            this.fieldName = fieldName;
        }

        public String getFieldType() {
            return fieldType;
        }

        public void setFieldType(String fieldType) {
            this.fieldType = fieldType;
        }

        public Boolean getRequired() {
            return required;
        }

        public void setRequired(Boolean required) {
            this.required = required;
        }
    }

    /**
     * 材料项
     */
    public static class MaterialItem {
        /** 材料序号 */
        @JsonProperty("index")
        private Integer index;

        /** 材料名称 */
        @JsonProperty("materialName")
        private String materialName;

        /** 是否必填 */
        @JsonProperty("required")
        private Boolean required;

        public Integer getIndex() {
            return index;
        }

        public void setIndex(Integer index) {
            this.index = index;
        }

        public String getMaterialName() {
            return materialName;
        }

        public void setMaterialName(String materialName) {
            this.materialName = materialName;
        }

        public Boolean getRequired() {
            return required;
        }

        public void setRequired(Boolean required) {
            this.required = required;
        }
    }
}