<template>
  <div class="field-editor-container">
    <div class="editor-header">
      <h3>证书字段位置编辑器</h3>
      <p>在证书底版上拖拽字段框以调整位置</p>
    </div>

    <div class="editor-content">
      <div class="certificate-preview-area">
        <div
          class="certificate-canvas"
          :style="{ width: canvasWidth + 'px', height: canvasHeight + 'px' }"
          @click="handleCanvasClick"
        >
          <!-- 使用img标签显示背景图片 -->
          <img
            :src="certificateBgImage + '?v=' + timestamp"
            style="position: absolute; top: 0; left: 0; width: 100%; height: 100%; object-fit: contain; object-position: center; z-index: 0;"
            @load="onImageLoad"
            @error="onImageError"
          />
          <!-- 字段拖拽区域 -->
          <div
            v-for="(field, index) in fieldPositions"
            :key="index"
            class="field-box"
            :style="{
              left: field.x + 'px',
              top: field.y + 'px',
              width: field.width + 'px',
              height: field.height + 'px',
              zIndex: field.zIndex || 1
            }"
            @mousedown="startDrag($event, index)"
            @click.stop="selectField(index)"
          >
            <div class="field-label" :style="{
              fontSize: field.fontSize + 'px',
              color: field.color || '#1890ff',
              fontWeight: field.fontWeight || 'normal',
              fontStyle: field.fontStyle || 'normal',
              fontFamily: field.fontFamily
            }">{{ field.fieldName }}</div>
            <div class="field-resize-handle br" @mousedown.stop="startResize($event, index, 'br')"></div>
            <div class="field-resize-handle bl" @mousedown.stop="startResize($event, index, 'bl')"></div>
            <div class="field-resize-handle tr" @mousedown.stop="startResize($event, index, 'tr')"></div>
            <div class="field-resize-handle tl" @mousedown.stop="startResize($event, index, 'tl')"></div>
            <div class="field-resize-handle r" @mousedown.stop="startResize($event, index, 'r')"></div>
            <div class="field-resize-handle l" @mousedown.stop="startResize($event, index, 'l')"></div>
            <div class="field-resize-handle t" @mousedown.stop="startResize($event, index, 't')"></div>
            <div class="field-resize-handle b" @mousedown.stop="startResize($event, index, 'b')"></div>
          </div>

          <!-- 图片边界指示器（仅用于可视化参考，不影响实际功能） -->
          <div
            v-if="imageLoaded"
            class="image-boundary"
            :style="{
              left: imageOffsetX + 'px',
              top: imageOffsetY + 'px',
              width: adjustedCanvasWidth + 'px',
              height: adjustedCanvasHeight + 'px',
              position: 'absolute',
              pointerEvents: 'none',
              zIndex: 2,
              boxSizing: 'border-box',
              background: 'transparent',
              border: '2px dashed rgba(255, 0, 0, 0.8)',
              transform: 'none',
              margin: 0,
              padding: 0
            }"
          ></div>

          <!-- 选中框 -->
          <div
            v-if="selectedFieldIndex !== -1"
            class="selection-outline"
            :style="{
              left: fieldPositions[selectedFieldIndex].x + 'px',
              top: fieldPositions[selectedFieldIndex].y + 'px',
              width: fieldPositions[selectedFieldIndex].width + 'px',
              height: fieldPositions[selectedFieldIndex].height + 'px'
            }"
          ></div>
        </div>
      </div>

      <div class="editor-panel">
        <div class="field-properties" v-if="selectedFieldIndex !== -1">
          <h4>字段属性</h4>
          <div class="property-item">
            <label>字段名称:</label>
            <span>{{ fieldPositions[selectedFieldIndex].fieldName }}</span>
          </div>
          <div class="property-item">
            <label>X坐标:</label>
            <input
              type="number"
              :value="canvasToImageCoords(fieldPositions[selectedFieldIndex].x, 0).x"
              @input="updateFieldX($event.target.value, selectedFieldIndex)"
              @change="validatePosition(selectedFieldIndex)"
            />
          </div>
          <div class="property-item">
            <label>Y坐标:</label>
            <input
              type="number"
              :value="canvasToImageCoords(0, fieldPositions[selectedFieldIndex].y).y"
              @input="updateFieldY($event.target.value, selectedFieldIndex)"
              @change="validatePosition(selectedFieldIndex)"
            />
          </div>
          <div class="property-item">
            <label>宽度:</label>
            <input
              type="number"
              v-model.number="fieldPositions[selectedFieldIndex].width"
              @change="validatePosition(selectedFieldIndex)"
            />
          </div>
          <div class="property-item">
            <label>高度:</label>
            <input
              type="number"
              v-model.number="fieldPositions[selectedFieldIndex].height"
              @change="validatePosition(selectedFieldIndex)"
            />
          </div>
          <div class="property-item">
            <label>字体大小:</label>
            <div style="display: flex; gap: 5px;">
              <input
                type="number"
                v-model.number="fieldPositions[selectedFieldIndex].fontSize"
                @change="validatePosition(selectedFieldIndex)"
                min="8" max="72"
                style="flex: 1;"
              />
              <select v-model="fieldPositions[selectedFieldIndex].fontSize" style="flex: 1;">
                <option :value="72">初号</option>
                <option :value="63">小初</option>
                <option :value="56">一号</option>
                <option :value="49">小一</option>
                <option :value="42">二号</option>
                <option :value="36">小二</option>
                <option :value="28">三号</option>
                <option :value="24">小三</option>
                <option :value="21">四号</option>
                <option :value="18">小四</option>
                <option :value="16">五号</option>
                <option :value="14">小五</option>
                <option :value="12">六号</option>
                <option :value="11">小六</option>
                <option :value="10">七号</option>
                <option :value="9">八号</option>
              </select>
            </div>
          </div>
          <div class="property-item">
            <label>字体颜色:</label>
            <input
              type="color"
              v-model="fieldPositions[selectedFieldIndex].color"
            />
          </div>
          <div class="property-item">
            <label>字体粗细:</label>
            <select v-model="fieldPositions[selectedFieldIndex].fontWeight">
              <option value="normal">普通</option>
              <option value="bold">粗体</option>
            </select>
          </div>
          <div class="property-item">
            <label>字体样式:</label>
            <select v-model="fieldPositions[selectedFieldIndex].fontStyle">
              <option value="normal">标准</option>
              <option value="italic">斜体</option>
            </select>
          </div>
          <div class="property-item">
            <label>字体族:</label>
            <select v-model="fieldPositions[selectedFieldIndex].fontFamily">
              <option value="SimSun">宋体</option>
              <option value="SimHei">黑体</option>
              <option value="KaiTi">楷体</option>
              <option value="Microsoft YaHei">微软雅黑</option>
              <option value="Arial">Arial</option>
              <option value="Times New Roman">Times New Roman</option>
            </select>
          </div>
        </div>

        <div class="available-fields" v-if="availableFields.length > 0">
          <h4>可用字段</h4>
          <div class="fields-list">
            <div
              v-for="field in availableFields"
              :key="field.fieldName"
              class="field-item"
              @click="addFieldToCanvas(field)"
            >
              {{ field.fieldName }}
            </div>
          </div>
        </div>

        <div class="current-fields" v-if="fieldPositions.length > 0">
          <h4>当前字段</h4>
          <div class="fields-list">
            <div
              v-for="(field, index) in fieldPositions"
              :key="index"
              class="field-item"
              :class="{ 'selected': selectedFieldIndex === index }"
              @click="selectField(index)"
            >
              {{ field.fieldName }}
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="editor-actions">
      <el-button @click="cancel">取消</el-button>
      <el-button type="primary" @click="save">保存</el-button>
    </div>
  </div>
</template>

<script>
export default {
  name: "CertificateFieldEditor",
  props: {
    certificateBgImage: {
      type: String,
      required: true
    },
    templateFields: {
      type: Array,
      default: () => []
    },
    initialFieldPositions: {
      type: Array,
      default: () => []
    }
  },
  emits: ['save', 'cancel'],
  data() {
    return {
      canvasWidth: 800,
      canvasHeight: 600,
      imageActualWidth: 800,
      imageActualHeight: 600,
      imageLoaded: false,
      adjustedCanvasWidth: 800,  // 图片实际显示宽度
      adjustedCanvasHeight: 600, // 图片实际显示高度
      imageOffsetX: 0,           // 图片在画布中的X偏移
      imageOffsetY: 0,           // 图片在画布中的Y偏移
      fieldPositions: [],
      selectedFieldIndex: -1,
      draggingFieldIndex: -1,
      resizingFieldIndex: -1,
      resizeDirection: '',
      startX: 0,
      startY: 0,
      startWidth: 0,
      startHeight: 0,
      startLeft: 0,
      startTop: 0,
      timestamp: Date.now()  // 添加时间戳用于强制刷新图片
    };
  },

  computed: {
    availableFields() {
      // 返回尚未添加到画布上的字段
      return this.templateFields.filter(templateField => {
        return !this.fieldPositions.some(positionField =>
          positionField.fieldName === templateField.fieldName
        );
      });
    },

    // 计算有效拖拽区域（图片显示区域）
    draggableArea() {
      if (this.imageLoaded) {
        return {
          x: this.imageOffsetX,
          y: this.imageOffsetY,
          width: this.adjustedCanvasWidth,
          height: this.adjustedCanvasHeight
        };
      } else {
        return {
          x: 0,
          y: 0,
          width: this.canvasWidth,
          height: this.canvasHeight
        };
      }
    }
  },
  mounted() {
    // 初始化字段位置
    this.initializeFieldPositions();
    // 更新时间戳以确保图片加载
    this.updateTimestamp();
  },
  

  
  watch: {
    // 监听证书背景图片的变化
    certificateBgImage: {
      handler() {
        this.updateTimestamp();
      },
      immediate: true
    }
  },
  methods: {
    initializeFieldPositions() {
      // 如果提供了初始字段位置，则使用它们，否则使用默认位置
      if (this.initialFieldPositions && this.initialFieldPositions.length > 0) {
        this.fieldPositions = this.initialFieldPositions.map(field => ({
          ...field,
          x: field.x || 100,
          y: field.y || 100,
          width: field.width || 120,
          height: field.height || 30,
          fontSize: field.fontSize || 14,
          color: field.color || '#1890ff',
          fontWeight: field.fontWeight || 'normal',
          fontStyle: field.fontStyle || 'normal',
          fontFamily: field.fontFamily || 'Arial'
        }));
      } else {
        // 默认初始化，将所有模板字段放置在画布上
        this.fieldPositions = this.templateFields.map((field, index) => ({
          fieldName: field.fieldName,
          x: 100 + (index % 3) * 150,
          y: 150 + Math.floor(index / 3) * 50,
          width: 120,
          height: 30,
          fontSize: 14,
          color: '#1890ff',
          fontWeight: 'normal',
          fontStyle: 'normal'
        }));
      }
    },
    
    updateTimestamp() {
      // 更新时间戳以强制重新加载图片
      this.timestamp = Date.now();
    },

    onImageLoad(event) {
      this.imageActualWidth = event.target.naturalWidth;
      this.imageActualHeight = event.target.naturalHeight;
      this.imageLoaded = true;

      // 立即计算图片位置
      this.calculateImagePosition();
      
      // 再次确认计算（以防初次计算时机过早）
      this.$nextTick(() => {
        this.calculateImagePosition();
      });
    },

    onImageLoad(event) {
      this.imageActualWidth = event.target.naturalWidth;
      this.imageActualHeight = event.target.naturalHeight;
      this.imageLoaded = true;

      // 计算缩放比例，确保图片适合画布尺寸
      const scaleX = this.canvasWidth / this.imageActualWidth;
      const scaleY = this.canvasHeight / this.imageActualHeight;
      const scale = Math.min(scaleX, scaleY, 1); // 不放大小于预设尺寸的图片

      // 计算居中后图片在画布上的实际显示尺寸
      const displayWidth = this.imageActualWidth * scale;
      const displayHeight = this.imageActualHeight * scale;

      // 计算居中后图片在画布上的位置（相对于画布左上角）
      const offsetX = (this.canvasWidth - displayWidth) / 2;
      const offsetY = (this.canvasHeight - displayHeight) / 2;

      // 更新画布尺寸为实际图片显示尺寸，这样可以将坐标系统原点设置为图片左上角
      this.adjustedCanvasWidth = displayWidth;
      this.adjustedCanvasHeight = displayHeight;
      this.imageOffsetX = offsetX;
      this.imageOffsetY = offsetY;
    },

    onImageError(error) {
      console.error('Failed to load certificate background image:', error);
      // 如果图片加载失败，使用默认尺寸
      this.imageLoaded = false;
      this.imageActualWidth = 800;
      this.imageActualHeight = 600;
    },

    startDrag(event, index) {
      event.preventDefault();
      this.draggingFieldIndex = index;
      this.selectedFieldIndex = index;
      this.startX = event.clientX;
      this.startY = event.clientY;
      this.startLeft = this.fieldPositions[index].x;
      this.startTop = this.fieldPositions[index].y;

      document.addEventListener('mousemove', this.onDrag);
      document.addEventListener('mouseup', this.stopDrag);
    },

    onDrag(event) {
      if (this.draggingFieldIndex === -1) return;

      const dx = event.clientX - this.startX;
      const dy = event.clientY - this.startY;

      let newX = this.startLeft + dx;
      let newY = this.startTop + dy;

      // 边界检查：限制在图片范围内而不是整个画布
      if (this.imageLoaded) {
        // 计算图片在画布上的实际显示区域
        const offsetX = this.imageOffsetX || 0;
        const offsetY = this.imageOffsetY || 0;
        const displayWidth = this.adjustedCanvasWidth || this.canvasWidth;
        const displayHeight = this.adjustedCanvasHeight || this.canvasHeight;

        // 将坐标系统原点移到图片左上角，所以减去图片的偏移量
        const relativeX = newX - offsetX;
        const relativeY = newY - offsetY;

        // 确保字段框完全在图片区域内（相对于图片坐标系）
        const clampedRelativeX = Math.max(0, Math.min(relativeX, displayWidth - this.fieldPositions[this.draggingFieldIndex].width));
        const clampedRelativeY = Math.max(0, Math.min(relativeY, displayHeight - this.fieldPositions[this.draggingFieldIndex].height));

        // 转换回画布坐标系
        newX = clampedRelativeX + offsetX;
        newY = clampedRelativeY + offsetY;
      } else {
        // 如果图片未加载，仍然限制在整个画布内
        newX = Math.max(0, Math.min(newX, this.canvasWidth - this.fieldPositions[this.draggingFieldIndex].width));
        newY = Math.max(0, Math.min(newY, this.canvasHeight - this.fieldPositions[this.draggingFieldIndex].height));
      }

      // 使用直接赋值替代 $set
      this.fieldPositions[this.draggingFieldIndex].x = newX;
      this.fieldPositions[this.draggingFieldIndex].y = newY;
    },

    stopDrag() {
      this.draggingFieldIndex = -1;
      document.removeEventListener('mousemove', this.onDrag);
      document.removeEventListener('mouseup', this.stopDrag);
    },

    startResize(event, index, direction) {
      event.preventDefault();
      this.resizingFieldIndex = index;
      this.resizeDirection = direction;
      this.selectedFieldIndex = index;
      this.startX = event.clientX;
      this.startY = event.clientY;
      this.startWidth = this.fieldPositions[index].width;
      this.startHeight = this.fieldPositions[index].height;
      this.startLeft = this.fieldPositions[index].x;
      this.startTop = this.fieldPositions[index].y;

      document.addEventListener('mousemove', this.onResize);
      document.addEventListener('mouseup', this.stopResize);
    },

    onResize(event) {
      if (this.resizingFieldIndex === -1) return;

      const dx = event.clientX - this.startX;
      const dy = event.clientY - this.startY;

      const field = this.fieldPositions[this.resizingFieldIndex];
      let newWidth = this.startWidth;
      let newHeight = this.startHeight;
      let newX = this.startLeft;
      let newY = this.startTop;

      // 根据方向调整尺寸和位置
      switch (this.resizeDirection) {
        case 'br': // 右下
          newWidth = Math.max(30, this.startWidth + dx);
          newHeight = Math.max(20, this.startHeight + dy);
          break;
        case 'bl': // 左下
          newWidth = Math.max(30, this.startWidth - dx);
          newHeight = Math.max(20, this.startHeight + dy);
          newX = Math.min(this.startLeft + dx, this.startLeft + this.startWidth - 30);
          break;
        case 'tr': // 右上
          newWidth = Math.max(30, this.startWidth + dx);
          newHeight = Math.max(20, this.startHeight - dy);
          newY = Math.min(this.startTop + dy, this.startTop + this.startHeight - 20);
          break;
        case 'tl': // 左上
          newWidth = Math.max(30, this.startWidth - dx);
          newHeight = Math.max(20, this.startHeight - dy);
          newX = Math.min(this.startLeft + dx, this.startLeft + this.startWidth - 30);
          newY = Math.min(this.startTop + dy, this.startTop + this.startHeight - 20);
          break;
        case 'r': // 右
          newWidth = Math.max(30, this.startWidth + dx);
          break;
        case 'l': // 左
          newWidth = Math.max(30, this.startWidth - dx);
          newX = Math.min(this.startLeft + dx, this.startLeft + this.startWidth - 30);
          break;
        case 't': // 上
          newHeight = Math.max(20, this.startHeight - dy);
          newY = Math.min(this.startTop + dy, this.startTop + this.startHeight - 20);
          break;
        case 'b': // 下
          newHeight = Math.max(20, this.startHeight + dy);
          break;
      }

      // 边界检查：限制在图片范围内而不是整个画布
      if (this.imageLoaded) {
        // 计算图片在画布上的实际显示区域
        const offsetX = this.imageOffsetX || 0;
        const offsetY = this.imageOffsetY || 0;
        const displayWidth = this.adjustedCanvasWidth || this.canvasWidth;
        const displayHeight = this.adjustedCanvasHeight || this.canvasHeight;

        // 将坐标系统原点移到图片左上角，所以减去图片的偏移量
        const relativeX = newX - offsetX;
        const relativeY = newY - offsetY;
        const relativeRight = relativeX + newWidth;
        const relativeBottom = relativeY + newHeight;

        // 确保字段框完全在图片区域内（相对于图片坐标系）
        let clampedRelativeX = Math.max(0, relativeX);
        let clampedRelativeY = Math.max(0, relativeY);
        let clampedRelativeRight = Math.min(displayWidth, relativeRight);
        let clampedRelativeBottom = Math.min(displayHeight, relativeBottom);

        // 计算调整后的尺寸
        newWidth = clampedRelativeRight - clampedRelativeX;
        newHeight = clampedRelativeBottom - clampedRelativeY;

        // 转换回画布坐标系
        newX = clampedRelativeX + offsetX;
        newY = clampedRelativeY + offsetY;
      } else {
        // 如果图片未加载，仍然限制在整个画布内
        if (newX < 0) {
          newWidth -= Math.abs(newX);
          newX = 0;
        }
        if (newY < 0) {
          newHeight -= Math.abs(newY);
          newY = 0;
        }
        if (newX + newWidth > this.canvasWidth) {
          newWidth = this.canvasWidth - newX;
        }
        if (newY + newHeight > this.canvasHeight) {
          newHeight = this.canvasHeight - newY;
        }
      }

      // 最小尺寸限制
      newWidth = Math.max(30, newWidth);
      newHeight = Math.max(20, newHeight);

      // 使用直接赋值替代 $set
      this.fieldPositions[this.resizingFieldIndex].x = newX;
      this.fieldPositions[this.resizingFieldIndex].y = newY;
      this.fieldPositions[this.resizingFieldIndex].width = newWidth;
      this.fieldPositions[this.resizingFieldIndex].height = newHeight;
    },

    stopResize() {
      this.resizingFieldIndex = -1;
      this.resizeDirection = '';
      document.removeEventListener('mousemove', this.onResize);
      document.removeEventListener('mouseup', this.stopResize);
    },

    validatePosition(index) {
      const field = this.fieldPositions[index];

      // 确保字段在图片范围内而不是整个画布
      if (this.imageLoaded) {
        // 计算图片在画布上的实际显示区域
        const offsetX = this.imageOffsetX || 0;
        const offsetY = this.imageOffsetY || 0;
        const displayWidth = this.adjustedCanvasWidth || this.canvasWidth;
        const displayHeight = this.adjustedCanvasHeight || this.canvasHeight;

        // 将坐标系统原点移到图片左上角，所以减去图片的偏移量
        const relativeX = field.x - offsetX;
        const relativeY = field.y - offsetY;

        // 确保字段框完全在图片区域内（相对于图片坐标系）
        const clampedRelativeX = Math.max(0, Math.min(relativeX, displayWidth - field.width));
        const clampedRelativeY = Math.max(0, Math.min(relativeY, displayHeight - field.height));

        // 转换回画布坐标系
        this.fieldPositions[index].x = clampedRelativeX + offsetX;
        this.fieldPositions[index].y = clampedRelativeY + offsetY;
      } else {
        // 如果图片未加载，仍然限制在整个画布内
        this.fieldPositions[index].x = Math.max(0, Math.min(field.x, this.canvasWidth - field.width));
        this.fieldPositions[index].y = Math.max(0, Math.min(field.y, this.canvasHeight - field.height));
      }

      // 最小尺寸限制
      this.fieldPositions[index].width = Math.max(30, field.width);
      this.fieldPositions[index].height = Math.max(20, field.height);

      // 验证字体大小
      this.fieldPositions[index].fontSize = Math.max(8, Math.min(field.fontSize, 72));
    },
    

    


    // 将画布坐标转换为相对于图片的坐标
    canvasToImageCoords(canvasX, canvasY) {
      if (!this.imageLoaded) {
        return { x: canvasX, y: canvasY };
      }

      const offsetX = this.imageOffsetX || 0;
      const offsetY = this.imageOffsetY || 0;

      return {
        x: canvasX - offsetX,
        y: canvasY - offsetY
      };
    },

    // 将相对于图片的坐标转换为画布坐标
    imageToCanvasCoords(imageX, imageY) {
      if (!this.imageLoaded) {
        return { x: imageX, y: imageY };
      }

      const offsetX = this.imageOffsetX || 0;
      const offsetY = this.imageOffsetY || 0;

      return {
        x: imageX + offsetX,
        y: imageY + offsetY
      };
    },

    selectField(index) {
      this.selectedFieldIndex = index;
    },

    handleCanvasClick(event) {
      // 点击空白区域取消选择
      if (event.target.classList.contains('certificate-canvas')) {
        this.selectedFieldIndex = -1;
      }
    },

    addFieldToCanvas(field) {
      // 添加字段到画布
      const newFieldPosition = {
        fieldName: field.fieldName,
        x: 100,
        y: 150,
        width: 120,
        height: 30,
        fontSize: 14,
        color: '#1890ff',
        fontWeight: 'normal',
        fontStyle: 'normal',
        fontFamily: 'Arial'
      };
      this.fieldPositions.push(newFieldPosition);
    },



    save() {
      // 过滤掉无效的字段位置，确保每个字段都有有效的坐标和尺寸
      const validFieldPositions = this.fieldPositions
        .filter(field => field && field.fieldName) // 确保字段存在且有名称
        .map(field => ({
          ...field,
          x: field.x !== undefined && field.x !== null ? field.x : 0,
          y: field.y !== undefined && field.y !== null ? field.y : 0,
          width: field.width || 120,
          height: field.height || 30,
          fontSize: field.fontSize || 14,
          color: field.color || '#000',
          fontWeight: field.fontWeight || 'normal',
          fontStyle: field.fontStyle || 'normal',
          fontFamily: field.fontFamily || 'Arial'
        }));
      
      this.$emit('save', validFieldPositions);
    },

    cancel() {
      this.$emit('cancel');
    },

    // 将相对于图片的坐标转换为画布坐标并更新字段X坐标
    updateFieldX(newValue, index) {
      const imageX = parseFloat(newValue) || 0;
      const canvasCoords = this.imageToCanvasCoords(imageX, 0);
      this.fieldPositions[index].x = canvasCoords.x;
    },

    // 将相对于图片的坐标转换为画布坐标并更新字段Y坐标
    updateFieldY(newValue, index) {
      const imageY = parseFloat(newValue) || 0;
      const canvasCoords = this.imageToCanvasCoords(0, imageY);
      this.fieldPositions[index].y = canvasCoords.y;
    }
  }
};
</script>

<style lang="scss" scoped>
.field-editor-container {
  height: 100%;
  display: flex;
  flex-direction: column;

  .editor-header {
    padding: 15px;
    border-bottom: 1px solid #eee;
    background-color: #fafafa;

    h3 {
      margin: 0;
      color: #333;
    }

    p {
      margin: 5px 0 0;
      color: #666;
      font-size: 14px;
    }
  }

  .editor-content {
    flex: 1;
    display: flex;
    overflow: auto;

    .certificate-preview-area {
      flex: 1;
      display: flex;
      justify-content: center;
      align-items: flex-start;
      padding: 20px;
      background-color: #f5f5f5;

      .certificate-canvas {
        position: relative;
        border: 1px solid #ddd;
        cursor: default;
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
        overflow: hidden;
      }

      .field-box {
        position: absolute;
        border: 1px dashed #1890ff;
        background-color: rgba(255, 255, 255, 0.7); /* 轻微透明背景，确保在图片上清晰可见 */
        display: flex;
        align-items: center;
        justify-content: center;
        cursor: move;
        user-select: none;
        box-sizing: border-box;
        z-index: 10; /* 确保字段框在图片上方 */

        .field-label {
          font-size: 12px;
          color: #1890ff;
          text-align: center;
          word-break: break-all;
          padding: 2px;
        }

        &:hover {
          border-color: #409EFF;
          background-color: rgba(64, 158, 255, 0.5);
        }
      }

      .field-resize-handle {
        position: absolute;
        width: 6px;
        height: 6px;
        background-color: #409EFF;
        border: 1px solid #fff;
        z-index: 11;

        &.br {
          bottom: -3px;
          right: -3px;
          cursor: se-resize;
        }

        &.bl {
          bottom: -3px;
          left: -3px;
          cursor: sw-resize;
        }

        &.tr {
          top: -3px;
          right: -3px;
          cursor: ne-resize;
        }

        &.tl {
          top: -3px;
          left: -3px;
          cursor: nw-resize;
        }

        &.r {
          top: 50%;
          right: -3px;
          transform: translateY(-50%);
          cursor: e-resize;
        }

        &.l {
          top: 50%;
          left: -3px;
          transform: translateY(-50%);
          cursor: w-resize;
        }

        &.t {
          top: -3px;
          left: 50%;
          transform: translateX(-50%);
          cursor: n-resize;
        }

        &.b {
          bottom: -3px;
          left: 50%;
          transform: translateX(-50%);
          cursor: s-resize;
        }
      }

      .selection-outline {
        position: absolute;
        border: 2px solid #409EFF;
        pointer-events: none;
        z-index: 12;
      }

      .image-boundary {
        position: absolute;
        pointer-events: none;
        z-index: 3;
        border: 2px dashed rgba(255, 0, 0, 0.8);
        box-sizing: border-box;
        background: transparent;
        margin: 0;
        padding: 0;
        /* 边界框不应影响溢出 */
        overflow: visible;
        clip-path: none;
        outline: none;
      }
    }

    .editor-panel {
      width: 300px;
      padding: 15px;
      border-left: 1px solid #eee;
      background-color: #fff;
      overflow-y: auto;

      .property-item {
        margin-bottom: 15px;

        label {
          display: block;
          margin-bottom: 5px;
          font-weight: bold;
          color: #666;
        }

        input[type="number"],
        input[type="text"] {
          width: 100%;
          padding: 5px;
          border: 1px solid #ddd;
          border-radius: 4px;
          box-sizing: border-box;
        }

        input[type="color"] {
          width: 100%;
          height: 30px;
          border: 1px solid #ddd;
          border-radius: 4px;
          cursor: pointer;
        }

        select {
          width: 100%;
          padding: 5px;
          border: 1px solid #ddd;
          border-radius: 4px;
          box-sizing: border-box;
          background-color: white;
        }
      }

      .fields-list {
        margin-top: 10px;

        .field-item {
          padding: 8px 12px;
          margin-bottom: 8px;
          background-color: #f8f9fa;
          border: 1px solid #e9ecef;
          border-radius: 4px;
          cursor: pointer;
          transition: all 0.2s;

          &.selected {
            background-color: #e6f7ff;
            border-color: #1890ff;
          }

          &:hover {
            background-color: #e9ecef;
          }


        }
      }
    }
  }

  .editor-actions {
    padding: 15px;
    border-top: 1px solid #eee;
    text-align: right;
    background-color: #fff;
  }
}
</style>