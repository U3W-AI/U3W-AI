<template>
  <div class="certificate-preview-container">
    <div class="certificate-wrapper"
         :style="{
           width: displayWidth + 'px',
           height: displayHeight + 'px'
         }">
      <!-- 证书底版 -->
      <img v-if="certificateBgImage" :src="certificateBgImage" class="certificate-background" @load="onImageLoad" @error="onImageError" />
      <!-- 备用隐藏图片用于获取实际尺寸 -->
      <img v-if="certificateBgImage" :src="certificateBgImage" style="display: none;" @load="onImageLoad" @error="onImageError" />

      <!-- 证书内容 -->
      <div class="certificate-content">
        <!-- 根据字段位置配置渲染字段 -->
        <div
          v-for="position in fieldPositions"
          :key="position.fieldName"
          class="preview-field"
          :style="getFieldStyle(position)"
          v-if="imageLoaded"
        >
          <div class="preview-field-label">
            {{ getFieldDisplayValue(position) }}
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: "CommonCertificatePreview",
  props: {
    certificateBgImage: {
      type: String,
      required: true
    },
    fieldPositions: {
      type: Array,
      default: () => []
    },
    certificateData: {
      type: Object,
      default: () => ({})
    },
    previewWidth: {
      type: Number,
      default: 800
    },
    previewHeight: {
      type: Number,
      default: 600
    }
  },
  data() {
    return {
      actualWidth: 800,  // 设计稿原始宽度
      actualHeight: 600, // 设计稿原始高度
      imageActualWidth: 800,  // 图片实际宽度
      imageActualHeight: 600,  // 图片实际高度
      imageLoaded: false  // 图片是否已加载
    };
  },
  computed: {
    // 计算显示宽度，限制最大尺寸并保持宽高比
    displayWidth() {
      // 如果图片已加载，根据预设尺寸和宽高比进行适当缩放
      if (this.imageLoaded) {
        // 计算缩放比例，确保图片适合预设尺寸
        const scale = Math.min(
          this.previewWidth / this.imageActualWidth,
          this.previewHeight / this.imageActualHeight,
          1 // 不放大小于预设尺寸的图片
        );
        return this.imageActualWidth * scale;
      }
      // 否则使用预设宽度作为占位
      return this.previewWidth;
    },
    // 计算显示高度，限制最大尺寸并保持宽高比
    displayHeight() {
      // 如果图片已加载，根据预设尺寸和宽高比进行适当缩放
      if (this.imageLoaded) {
        // 计算缩放比例，确保图片适合预设尺寸
        const scale = Math.min(
          this.previewWidth / this.imageActualWidth,
          this.previewHeight / this.imageActualHeight,
          1 // 不放大小于预设尺寸的图片
        );
        return this.imageActualHeight * scale;
      }
      // 否则使用预设高度作为占位
      return this.previewHeight;
    },
    // 计算宽度缩放比例
    widthScale() {
      return this.imageActualWidth / this.actualWidth;
    },
    // 计算高度缩放比例
    heightScale() {
      return this.imageActualHeight / this.actualHeight;
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
        this.imageLoaded = false;  // 重置图片加载状态
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
        this.imageActualWidth = img.width;
        this.imageActualHeight = img.height;
        this.imageLoaded = true;  // 设置图片已加载状态
      };
      img.onerror = () => {
        // 如果加载失败，使用默认尺寸
        this.imageActualWidth = 800;
        this.imageActualHeight = 600;
        this.imageLoaded = true;  // 即使加载失败也设置为已加载状态，以免组件一直等待
      };
      img.src = this.certificateBgImage;
    },

    // 图片加载完成后更新尺寸
    onImageLoad(event) {
      this.imageActualWidth = event.target.naturalWidth;
      this.imageActualHeight = event.target.naturalHeight;
      this.imageLoaded = true;
    },
    
    onImageError(error) {
      console.error('Failed to load certificate background image:', error);
      // 如果图片加载失败，使用默认尺寸
      this.imageActualWidth = 800;
      this.imageActualHeight = 600;
      this.imageLoaded = true;
    },

    // 获取字段样式，与PDF生成逻辑保持一致
    getFieldStyle(position) {
      // 尝試從字段位置配置中獲取樣式
      const positionConfig = this.fieldPositions.find(pos => pos.fieldName === position.fieldName);
      if (positionConfig) {
        // 获取编辑器中的缩放比例（基于800x600画布）
        const editorCanvasWidth = 800;
        const editorCanvasHeight = 600;
        
        // 与编辑器中onImageLoad方法一致的计算方式
        const editorScale = Math.min(
          editorCanvasWidth / this.imageActualWidth,
          editorCanvasHeight / this.imageActualHeight,
          1 // 不放大小于预设尺寸的图片
        );

        // 获取当前预览的缩放比例，与PDF生成逻辑保持一致
        const currentScale = Math.min(
          this.displayWidth / this.imageActualWidth,
          this.displayHeight / this.imageActualHeight,
          1 // 不放大小于预设尺寸的图片
        );

        // 计算图片在编辑器画布中的偏移量（与PDF生成逻辑一致）
        const editorImageOffsetX = (editorCanvasWidth - this.imageActualWidth * editorScale) / 2;
        const editorImageOffsetY = (editorCanvasHeight - this.imageActualHeight * editorScale) / 2;

        // 计算图片在当前预览中的偏移量（与PDF生成逻辑一致）
        const currentImageOffsetX = (this.displayWidth - this.imageActualWidth * currentScale) / 2;
        const currentImageOffsetY = (this.displayHeight - this.imageActualHeight * currentScale) / 2;

        // 计算缩放比率，与PDF生成逻辑保持一致
        const scaleRatio = currentScale / editorScale;

        // 将编辑器中保存的坐标转换为相对于图片的坐标，再转换为当前预览坐标
        // 首先将编辑器坐标转换为相对于图片的坐标
        const imageBasedX = (positionConfig.x - editorImageOffsetX) * scaleRatio;
        const imageBasedY = (positionConfig.y - editorImageOffsetY) * scaleRatio;

        // 再加上当前预览中图片的偏移量
        const finalX = currentImageOffsetX + imageBasedX;
        let finalY = currentImageOffsetY + imageBasedY;

        // 对Y坐标进行微调以补偿可能的视觉偏移
        const yAdjustment = 0 * scaleRatio; // 使用统一缩放比例
        finalY += yAdjustment;

        // 计算缩放后的尺寸
        const scaledWidth = (positionConfig.width || 120) * scaleRatio;
        const scaledHeight = (positionConfig.height || 30) * scaleRatio;
        const scaledFontSize = (positionConfig.fontSize || 18) * scaleRatio;

        return {
          position: 'absolute',
          left: `${finalX}px`,
          top: `${finalY}px`,
          width: `${scaledWidth}px`,
          height: `${scaledHeight}px`,
          zIndex: positionConfig.zIndex || 1,
          fontSize: `${scaledFontSize}px`,
          color: positionConfig.color || '#000',
          fontWeight: positionConfig.fontWeight || 'normal',
          fontStyle: positionConfig.fontStyle || 'normal',
          fontFamily: positionConfig.fontFamily || 'SimSun, serif',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          textAlign: 'center',
          boxSizing: 'border-box',
          padding: 0,
          lineHeight: 1.2
        };
      }
      return {};
    },

    // 获取字段显示值
    getFieldDisplayValue(position) {
      // 优先使用position中的fieldValue（如果存在），这是从preparePreviewData方法中设置的
      if (position.fieldValue !== undefined && position.fieldValue !== null) {
        // 特殊处理有效期字段：如果为空字符串则显示'长期有效'
        if (position.fieldName === '有效期') {
          return position.fieldValue === '' || position.fieldValue === undefined || position.fieldValue === null
            ? '长期有效'
            : position.fieldValue;
        }
        return position.fieldValue;
      }

      if (!this.certificateData) {
        // 如果没有提供certificateData，且没有fieldValue，则返回字段名
        return position.fieldName;
      }

      // 从certificateData中获取字段值
      let fieldValue = this.getNestedValue(this.certificateData, position.fieldName);

      // 特殊处理有效期字段：如果为空字符串则显示'长期有效'
      if (position.fieldName === '有效期') {
        fieldValue = fieldValue === '' || fieldValue === undefined || fieldValue === null
          ? '长期有效'
          : fieldValue;
      } else {
        fieldValue = fieldValue || '';
      }

      return fieldValue;
    },

    // 获取嵌套对象的值（处理可能包含特殊字符的字段名）
    getNestedValue(obj, key) {
      if (!obj || typeof obj !== 'object') {
        return undefined;
      }

      // 直接匹配
      if (obj.hasOwnProperty(key)) {
        return obj[key];
      }

      // 清理键名中的特殊字符再匹配
      const cleanedKey = this.cleanKey(key);

      // 遍历对象的所有键
      for (const objKey in obj) {
        if (obj.hasOwnProperty(objKey)) {
          const cleanedObjKey = this.cleanKey(objKey);
          if (cleanedObjKey === cleanedKey) {
            return obj[objKey];
          }
        }
      }

      return undefined;
    },

    // 清理键名中的特殊字符
    cleanKey(key) {
      if (typeof key !== 'string') {
        return key;
      }
      // 处理可能存在的特殊字符，如\u0004等
      return key.replace(/[\ufeff\u0000-\u001f\u007f-\u009f\u0004]/g, '');
    }
  },
};
</script>

<style lang="scss" scoped>
.certificate-preview-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  padding: 20px;
  box-sizing: border-box;
}

.certificate-wrapper {
  position: relative;
  border: 1px solid #ddd;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  overflow: hidden;
  background: white;
  display: block;
}

.certificate-background {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: 0;
  object-fit: contain;
  object-position: center;
  pointer-events: none; /* 确保图片不会拦截鼠标事件 */
}

.certificate-content {
  position: relative;
  z-index: 1;
  width: 100%;
  height: 100%;
  color: #333;
  font-family: 'SimSun', serif;
  box-sizing: border-box;
}

.preview-field {
  font-size: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  text-align: center;
  line-height: 1.2;
  box-sizing: border-box;
}

.preview-field-label {
  font-size: inherit;
  color: inherit;
  text-align: center;
  word-break: break-all;
  padding: 2px;
}


</style>