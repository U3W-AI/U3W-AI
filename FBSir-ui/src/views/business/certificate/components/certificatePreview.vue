<template>
  <div class="certificate-preview-container">
    <div class="certificate-wrapper" 
         :style="{
           width: displayWidth + 'px',
           height: displayHeight + 'px'
         }">
      <!-- 证书底版 -->
      <div v-if="certificateBgImage" class="certificate-background">
        <img :src="certificateBgImage" :alt="'证书底版'" class="bg-image" @load="onImageLoad"/>
      </div>
      
      <!-- 证书内容 -->
      <div class="certificate-content">
        <!-- 根据字段位置配置渲染字段 -->
        <div 
          v-for="position in fieldPositions" 
          :key="position.fieldName"
          class="preview-field"
          :style="{
            position: 'absolute',
            left: position.x + 'px',
            top: position.y + 'px',
            width: position.width + 'px',
            height: position.height + 'px',
            fontSize: (position.fontSize || 14) + 'px',
            color: position.color || '#000',
            fontWeight: position.fontWeight || 'normal',
            fontStyle: position.fontStyle || 'normal',
            fontFamily: position.fontFamily || 'Arial',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            textAlign: 'center',
            zIndex: position.zIndex || 1,
            boxSizing: 'border-box'
          }"
        >
          {{ position.fieldValue !== undefined && position.fieldValue !== null ? position.fieldValue : position.fieldName }}
        </div>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: "CertificatePreview",
  props: {
    certificateBgImage: {
      type: String,
      required: true
    },
    fieldPositions: {
      type: Array,
      default: () => []
    },
    previewWidth: {
      type: Number,
      default: null  // 如果未提供，则使用图片实际宽度
    },
    previewHeight: {
      type: Number,
      default: null  // 如果未提供，则使用图片实际高度
    }
  },
  data() {
    return {
      actualWidth: 800,  // 图片实际宽度
      actualHeight: 600   // 图片实际高度
    };
  },
  computed: {
    // 计算显示宽度，优先使用传入的尺寸，否则使用图片实际尺寸
    displayWidth() {
      return this.previewWidth || this.actualWidth;
    },
    // 计算显示高度，优先使用传入的尺寸，否则使用图片实际尺寸
    displayHeight() {
      return this.previewHeight || this.actualHeight;
    }
  },
  mounted() {
    // 初始化图片尺寸
    this.initImageSize();
  },
  watch: {
    // 监听背景图片变化
    certificateBgImage: {
      handler() {
        this.initImageSize();
      },
      immediate: true
    }
  },
  methods: {
    // 初始化图片尺寸
    initImageSize() {
      const img = new Image();
      img.onload = () => {
        this.actualWidth = img.width;
        this.actualHeight = img.height;
      };
      img.onerror = () => {
        // 如果加载失败，使用默认尺寸
        this.actualWidth = 800;
        this.actualHeight = 600;
      };
      img.src = this.certificateBgImage;
    },
    
    // 图片加载完成后更新尺寸
    onImageLoad(event) {
      this.actualWidth = event.target.naturalWidth;
      this.actualHeight = event.target.naturalHeight;
    }
  },
};
</script>

<style lang="scss" scoped>
.certificate-preview-container {
  display: flex;
  justify-content: center;
  align-items: flex-start;
  padding: 20px;
  background-color: #f5f5f5;
  min-height: 600px;
}

.certificate-wrapper {
  position: relative;
  border: 1px solid #ddd;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  overflow: hidden;
  background: white;
}

.certificate-background {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: 0;
  
  .bg-image {
    width: 100%;
    height: 100%;
    object-fit: contain; /* 使用contain确保图片完整显示，不裁剪 */
  }
}

.certificate-content {
  position: relative;
  z-index: 1;
  width: 100%;
  height: 100%;
  /* 不设置padding，确保坐标系统直接对应到底版图片 */
}

.preview-field {
  background-color: transparent;
  overflow: hidden;
  word-break: break-all;
  white-space: normal;
}
</style>