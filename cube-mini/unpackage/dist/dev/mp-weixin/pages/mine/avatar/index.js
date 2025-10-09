(global["webpackJsonp"] = global["webpackJsonp"] || []).push([["pages/mine/avatar/index"],{

/***/ 100:
/*!*************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************!*\
  !*** ./node_modules/babel-loader/lib!./node_modules/@dcloudio/vue-cli-plugin-uni/packages/webpack-preprocess-loader??ref--13-1!./node_modules/@dcloudio/webpack-uni-mp-loader/lib/script.js!./node_modules/@dcloudio/vue-cli-plugin-uni/packages/vue-loader/lib??vue-loader-options!./node_modules/@dcloudio/webpack-uni-mp-loader/lib/style.js!D:/AAAi/U3W-AI-main/cube-mini/pages/mine/avatar/index.vue?vue&type=script&lang=js& ***!
  \*************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************/
/*! no static exports found */
/***/ (function(module, exports, __webpack_require__) {

"use strict";
/* WEBPACK VAR INJECTION */(function(uni) {

var _interopRequireDefault = __webpack_require__(/*! @babel/runtime/helpers/interopRequireDefault */ 4);
Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.default = void 0;
var _config = _interopRequireDefault(__webpack_require__(/*! @/config */ 36));
var _store = _interopRequireDefault(__webpack_require__(/*! @/store */ 37));
var _user = __webpack_require__(/*! @/api/system/user */ 101);
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//

var baseUrl = _config.default.baseUrl;
var sysInfo = uni.getSystemInfoSync();
var SCREEN_WIDTH = sysInfo.screenWidth;
var PAGE_X,
  // 手按下的x位置
  PAGE_Y,
  // 手按下y的位置 
  PR = sysInfo.pixelRatio,
  // dpi
  T_PAGE_X,
  // 手移动的时候x的位置
  T_PAGE_Y,
  // 手移动的时候Y的位置
  CUT_L,
  // 初始化拖拽元素的left值
  CUT_T,
  // 初始化拖拽元素的top值
  CUT_R,
  // 初始化拖拽元素的
  CUT_B,
  // 初始化拖拽元素的
  CUT_W,
  // 初始化拖拽元素的宽度
  CUT_H,
  //  初始化拖拽元素的高度
  IMG_RATIO,
  // 图片比例
  IMG_REAL_W,
  // 图片实际的宽度
  IMG_REAL_H,
  // 图片实际的高度
  DRAFG_MOVE_RATIO = 1,
  //移动时候的比例,
  INIT_DRAG_POSITION = 100,
  // 初始化屏幕宽度和裁剪区域的宽度之差，用于设置初始化裁剪的宽度
  DRAW_IMAGE_W = sysInfo.screenWidth; // 设置生成的图片宽度
var _default = {
  /**
   * 页面的初始数据
   */
  data: function data() {
    return {
      imageSrc: _store.default.getters.avatar,
      isShowImg: false,
      // 初始化的宽高
      cropperInitW: SCREEN_WIDTH,
      cropperInitH: SCREEN_WIDTH,
      // 动态的宽高
      cropperW: SCREEN_WIDTH,
      cropperH: SCREEN_WIDTH,
      // 动态的left top值
      cropperL: 0,
      cropperT: 0,
      transL: 0,
      transT: 0,
      // 图片缩放值
      scaleP: 0,
      imageW: 0,
      imageH: 0,
      // 裁剪框 宽高
      cutL: 0,
      cutT: 0,
      cutB: SCREEN_WIDTH,
      cutR: '100%',
      qualityWidth: DRAW_IMAGE_W,
      innerAspectRadio: DRAFG_MOVE_RATIO
    };
  },
  /**
   * 生命周期函数--监听页面初次渲染完成
   */
  onReady: function onReady() {
    this.loadImage();
  },
  methods: {
    setData: function setData(obj) {
      var that = this;
      Object.keys(obj).forEach(function (key) {
        that.$set(that.$data, key, obj[key]);
      });
    },
    getImage: function getImage() {
      var _this = this;
      uni.chooseImage({
        success: function success(res) {
          _this.setData({
            imageSrc: res.tempFilePaths[0]
          });
          _this.loadImage();
        }
      });
    },
    loadImage: function loadImage() {
      var _this = this;
      uni.getImageInfo({
        src: _this.imageSrc,
        success: function success(res) {
          IMG_RATIO = 1 / 1;
          if (IMG_RATIO >= 1) {
            IMG_REAL_W = SCREEN_WIDTH;
            IMG_REAL_H = SCREEN_WIDTH / IMG_RATIO;
          } else {
            IMG_REAL_W = SCREEN_WIDTH * IMG_RATIO;
            IMG_REAL_H = SCREEN_WIDTH;
          }
          var minRange = IMG_REAL_W > IMG_REAL_H ? IMG_REAL_W : IMG_REAL_H;
          INIT_DRAG_POSITION = minRange > INIT_DRAG_POSITION ? INIT_DRAG_POSITION : minRange;
          // 根据图片的宽高显示不同的效果   保证图片可以正常显示
          if (IMG_RATIO >= 1) {
            var cutT = Math.ceil((SCREEN_WIDTH / IMG_RATIO - (SCREEN_WIDTH / IMG_RATIO - INIT_DRAG_POSITION)) / 2);
            var cutB = cutT;
            var cutL = Math.ceil((SCREEN_WIDTH - SCREEN_WIDTH + INIT_DRAG_POSITION) / 2);
            var cutR = cutL;
            _this.setData({
              cropperW: SCREEN_WIDTH,
              cropperH: SCREEN_WIDTH / IMG_RATIO,
              // 初始化left right
              cropperL: Math.ceil((SCREEN_WIDTH - SCREEN_WIDTH) / 2),
              cropperT: Math.ceil((SCREEN_WIDTH - SCREEN_WIDTH / IMG_RATIO) / 2),
              cutL: cutL,
              cutT: cutT,
              cutR: cutR,
              cutB: cutB,
              // 图片缩放值
              imageW: IMG_REAL_W,
              imageH: IMG_REAL_H,
              scaleP: IMG_REAL_W / SCREEN_WIDTH,
              qualityWidth: DRAW_IMAGE_W,
              innerAspectRadio: IMG_RATIO
            });
          } else {
            var _cutL = Math.ceil((SCREEN_WIDTH * IMG_RATIO - SCREEN_WIDTH * IMG_RATIO) / 2);
            var _cutR = _cutL;
            var _cutT = Math.ceil((SCREEN_WIDTH - INIT_DRAG_POSITION) / 2);
            var _cutB = _cutT;
            _this.setData({
              cropperW: SCREEN_WIDTH * IMG_RATIO,
              cropperH: SCREEN_WIDTH,
              // 初始化left right
              cropperL: Math.ceil((SCREEN_WIDTH - SCREEN_WIDTH * IMG_RATIO) / 2),
              cropperT: Math.ceil((SCREEN_WIDTH - SCREEN_WIDTH) / 2),
              cutL: _cutL,
              cutT: _cutT,
              cutR: _cutR,
              cutB: _cutB,
              // 图片缩放值
              imageW: IMG_REAL_W,
              imageH: IMG_REAL_H,
              scaleP: IMG_REAL_W / SCREEN_WIDTH,
              qualityWidth: DRAW_IMAGE_W,
              innerAspectRadio: IMG_RATIO
            });
          }
          _this.setData({
            isShowImg: true
          });
          uni.hideLoading();
        }
      });
    },
    // 拖动时候触发的touchStart事件
    contentStartMove: function contentStartMove(e) {
      PAGE_X = e.touches[0].pageX;
      PAGE_Y = e.touches[0].pageY;
    },
    // 拖动时候触发的touchMove事件
    contentMoveing: function contentMoveing(e) {
      var _this = this;
      var dragLengthX = (PAGE_X - e.touches[0].pageX) * DRAFG_MOVE_RATIO;
      var dragLengthY = (PAGE_Y - e.touches[0].pageY) * DRAFG_MOVE_RATIO;
      // 左移
      if (dragLengthX > 0) {
        if (this.cutL - dragLengthX < 0) dragLengthX = this.cutL;
      } else {
        if (this.cutR + dragLengthX < 0) dragLengthX = -this.cutR;
      }
      if (dragLengthY > 0) {
        if (this.cutT - dragLengthY < 0) dragLengthY = this.cutT;
      } else {
        if (this.cutB + dragLengthY < 0) dragLengthY = -this.cutB;
      }
      this.setData({
        cutL: this.cutL - dragLengthX,
        cutT: this.cutT - dragLengthY,
        cutR: this.cutR + dragLengthX,
        cutB: this.cutB + dragLengthY
      });
      PAGE_X = e.touches[0].pageX;
      PAGE_Y = e.touches[0].pageY;
    },
    contentTouchEnd: function contentTouchEnd() {},
    // 获取图片
    getImageInfo: function getImageInfo() {
      var _this = this;
      uni.showLoading({
        title: '图片生成中...'
      });
      // 将图片写入画布
      var ctx = uni.createCanvasContext('myCanvas');
      ctx.drawImage(_this.imageSrc, 0, 0, IMG_REAL_W, IMG_REAL_H);
      ctx.draw(true, function () {
        // 获取画布要裁剪的位置和宽度   均为百分比 * 画布中图片的宽度    保证了在微信小程序中裁剪的图片模糊  位置不对的问题 canvasT = (_this.cutT / _this.cropperH) * (_this.imageH / pixelRatio)
        var canvasW = (_this.cropperW - _this.cutL - _this.cutR) / _this.cropperW * IMG_REAL_W;
        var canvasH = (_this.cropperH - _this.cutT - _this.cutB) / _this.cropperH * IMG_REAL_H;
        var canvasL = _this.cutL / _this.cropperW * IMG_REAL_W;
        var canvasT = _this.cutT / _this.cropperH * IMG_REAL_H;
        uni.canvasToTempFilePath({
          x: canvasL,
          y: canvasT,
          width: canvasW,
          height: canvasH,
          destWidth: canvasW,
          destHeight: canvasH,
          quality: 0.5,
          canvasId: 'myCanvas',
          success: function success(res) {
            uni.hideLoading();
            var data = {
              name: 'avatarfile',
              filePath: res.tempFilePath
            };
            (0, _user.uploadAvatar)(data).then(function (response) {
              _store.default.commit('SET_AVATAR', baseUrl + response.imgUrl);
              uni.showToast({
                title: "修改成功",
                icon: 'success'
              });
              uni.navigateBack();
            });
          }
        });
      });
    },
    // 设置大小的时候触发的touchStart事件
    dragStart: function dragStart(e) {
      T_PAGE_X = e.touches[0].pageX;
      T_PAGE_Y = e.touches[0].pageY;
      CUT_L = this.cutL;
      CUT_R = this.cutR;
      CUT_B = this.cutB;
      CUT_T = this.cutT;
    },
    // 设置大小的时候触发的touchMove事件
    dragMove: function dragMove(e) {
      var _this = this;
      var dragType = e.target.dataset.drag;
      switch (dragType) {
        case 'right':
          var dragLength = (T_PAGE_X - e.touches[0].pageX) * DRAFG_MOVE_RATIO;
          if (CUT_R + dragLength < 0) dragLength = -CUT_R;
          this.setData({
            cutR: CUT_R + dragLength
          });
          break;
        case 'left':
          var dragLength = (T_PAGE_X - e.touches[0].pageX) * DRAFG_MOVE_RATIO;
          if (CUT_L - dragLength < 0) dragLength = CUT_L;
          if (CUT_L - dragLength > this.cropperW - this.cutR) dragLength = CUT_L - (this.cropperW - this.cutR);
          this.setData({
            cutL: CUT_L - dragLength
          });
          break;
        case 'top':
          var dragLength = (T_PAGE_Y - e.touches[0].pageY) * DRAFG_MOVE_RATIO;
          if (CUT_T - dragLength < 0) dragLength = CUT_T;
          if (CUT_T - dragLength > this.cropperH - this.cutB) dragLength = CUT_T - (this.cropperH - this.cutB);
          this.setData({
            cutT: CUT_T - dragLength
          });
          break;
        case 'bottom':
          var dragLength = (T_PAGE_Y - e.touches[0].pageY) * DRAFG_MOVE_RATIO;
          if (CUT_B + dragLength < 0) dragLength = -CUT_B;
          this.setData({
            cutB: CUT_B + dragLength
          });
          break;
        case 'rightBottom':
          var dragLengthX = (T_PAGE_X - e.touches[0].pageX) * DRAFG_MOVE_RATIO;
          var dragLengthY = (T_PAGE_Y - e.touches[0].pageY) * DRAFG_MOVE_RATIO;
          if (CUT_B + dragLengthY < 0) dragLengthY = -CUT_B;
          if (CUT_R + dragLengthX < 0) dragLengthX = -CUT_R;
          var cutB = CUT_B + dragLengthY;
          var cutR = CUT_R + dragLengthX;
          this.setData({
            cutB: cutB,
            cutR: cutR
          });
          break;
        default:
          break;
      }
    }
  }
};
exports.default = _default;
/* WEBPACK VAR INJECTION */}.call(this, __webpack_require__(/*! ./node_modules/@dcloudio/uni-mp-weixin/dist/index.js */ 2)["default"]))

/***/ }),

/***/ 103:
/*!**************************************************************************************************!*\
  !*** D:/AAAi/U3W-AI-main/cube-mini/pages/mine/avatar/index.vue?vue&type=style&index=0&lang=css& ***!
  \**************************************************************************************************/
/*! no static exports found */
/***/ (function(module, __webpack_exports__, __webpack_require__) {

"use strict";
__webpack_require__.r(__webpack_exports__);
/* harmony import */ var _HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_mini_css_extract_plugin_dist_loader_js_ref_6_oneOf_1_0_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_css_loader_dist_cjs_js_ref_6_oneOf_1_1_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_vue_loader_lib_loaders_stylePostLoader_js_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_webpack_preprocess_loader_index_js_ref_6_oneOf_1_2_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_postcss_loader_src_index_js_ref_6_oneOf_1_3_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_vue_loader_lib_index_js_vue_loader_options_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_webpack_uni_mp_loader_lib_style_js_index_vue_vue_type_style_index_0_lang_css___WEBPACK_IMPORTED_MODULE_0__ = __webpack_require__(/*! -!../../../../../../小红帽/HBuilderX.4.76.2025082103/HBuilderX/plugins/uniapp-cli/node_modules/mini-css-extract-plugin/dist/loader.js??ref--6-oneOf-1-0!../../../../../../小红帽/HBuilderX.4.76.2025082103/HBuilderX/plugins/uniapp-cli/node_modules/css-loader/dist/cjs.js??ref--6-oneOf-1-1!../../../../../../小红帽/HBuilderX.4.76.2025082103/HBuilderX/plugins/uniapp-cli/node_modules/@dcloudio/vue-cli-plugin-uni/packages/vue-loader/lib/loaders/stylePostLoader.js!../../../../../../小红帽/HBuilderX.4.76.2025082103/HBuilderX/plugins/uniapp-cli/node_modules/@dcloudio/vue-cli-plugin-uni/packages/webpack-preprocess-loader??ref--6-oneOf-1-2!../../../../../../小红帽/HBuilderX.4.76.2025082103/HBuilderX/plugins/uniapp-cli/node_modules/postcss-loader/src??ref--6-oneOf-1-3!../../../../../../小红帽/HBuilderX.4.76.2025082103/HBuilderX/plugins/uniapp-cli/node_modules/@dcloudio/vue-cli-plugin-uni/packages/vue-loader/lib??vue-loader-options!../../../../../../小红帽/HBuilderX.4.76.2025082103/HBuilderX/plugins/uniapp-cli/node_modules/@dcloudio/webpack-uni-mp-loader/lib/style.js!./index.vue?vue&type=style&index=0&lang=css& */ 104);
/* harmony import */ var _HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_mini_css_extract_plugin_dist_loader_js_ref_6_oneOf_1_0_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_css_loader_dist_cjs_js_ref_6_oneOf_1_1_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_vue_loader_lib_loaders_stylePostLoader_js_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_webpack_preprocess_loader_index_js_ref_6_oneOf_1_2_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_postcss_loader_src_index_js_ref_6_oneOf_1_3_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_vue_loader_lib_index_js_vue_loader_options_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_webpack_uni_mp_loader_lib_style_js_index_vue_vue_type_style_index_0_lang_css___WEBPACK_IMPORTED_MODULE_0___default = /*#__PURE__*/__webpack_require__.n(_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_mini_css_extract_plugin_dist_loader_js_ref_6_oneOf_1_0_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_css_loader_dist_cjs_js_ref_6_oneOf_1_1_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_vue_loader_lib_loaders_stylePostLoader_js_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_webpack_preprocess_loader_index_js_ref_6_oneOf_1_2_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_postcss_loader_src_index_js_ref_6_oneOf_1_3_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_vue_loader_lib_index_js_vue_loader_options_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_webpack_uni_mp_loader_lib_style_js_index_vue_vue_type_style_index_0_lang_css___WEBPACK_IMPORTED_MODULE_0__);
/* harmony reexport (unknown) */ for(var __WEBPACK_IMPORT_KEY__ in _HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_mini_css_extract_plugin_dist_loader_js_ref_6_oneOf_1_0_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_css_loader_dist_cjs_js_ref_6_oneOf_1_1_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_vue_loader_lib_loaders_stylePostLoader_js_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_webpack_preprocess_loader_index_js_ref_6_oneOf_1_2_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_postcss_loader_src_index_js_ref_6_oneOf_1_3_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_vue_loader_lib_index_js_vue_loader_options_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_webpack_uni_mp_loader_lib_style_js_index_vue_vue_type_style_index_0_lang_css___WEBPACK_IMPORTED_MODULE_0__) if(["default"].indexOf(__WEBPACK_IMPORT_KEY__) < 0) (function(key) { __webpack_require__.d(__webpack_exports__, key, function() { return _HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_mini_css_extract_plugin_dist_loader_js_ref_6_oneOf_1_0_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_css_loader_dist_cjs_js_ref_6_oneOf_1_1_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_vue_loader_lib_loaders_stylePostLoader_js_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_webpack_preprocess_loader_index_js_ref_6_oneOf_1_2_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_postcss_loader_src_index_js_ref_6_oneOf_1_3_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_vue_loader_lib_index_js_vue_loader_options_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_webpack_uni_mp_loader_lib_style_js_index_vue_vue_type_style_index_0_lang_css___WEBPACK_IMPORTED_MODULE_0__[key]; }) }(__WEBPACK_IMPORT_KEY__));
 /* harmony default export */ __webpack_exports__["default"] = (_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_mini_css_extract_plugin_dist_loader_js_ref_6_oneOf_1_0_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_css_loader_dist_cjs_js_ref_6_oneOf_1_1_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_vue_loader_lib_loaders_stylePostLoader_js_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_webpack_preprocess_loader_index_js_ref_6_oneOf_1_2_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_postcss_loader_src_index_js_ref_6_oneOf_1_3_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_vue_loader_lib_index_js_vue_loader_options_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_webpack_uni_mp_loader_lib_style_js_index_vue_vue_type_style_index_0_lang_css___WEBPACK_IMPORTED_MODULE_0___default.a); 

/***/ }),

/***/ 104:
/*!******************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************!*\
  !*** ./node_modules/mini-css-extract-plugin/dist/loader.js??ref--6-oneOf-1-0!./node_modules/css-loader/dist/cjs.js??ref--6-oneOf-1-1!./node_modules/@dcloudio/vue-cli-plugin-uni/packages/vue-loader/lib/loaders/stylePostLoader.js!./node_modules/@dcloudio/vue-cli-plugin-uni/packages/webpack-preprocess-loader??ref--6-oneOf-1-2!./node_modules/postcss-loader/src??ref--6-oneOf-1-3!./node_modules/@dcloudio/vue-cli-plugin-uni/packages/vue-loader/lib??vue-loader-options!./node_modules/@dcloudio/webpack-uni-mp-loader/lib/style.js!D:/AAAi/U3W-AI-main/cube-mini/pages/mine/avatar/index.vue?vue&type=style&index=0&lang=css& ***!
  \******************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************/
/*! no static exports found */
/***/ (function(module, exports, __webpack_require__) {

// extracted by mini-css-extract-plugin
    if(false) { var cssReload; }
  

/***/ }),

/***/ 95:
/*!**************************************************************************************!*\
  !*** D:/AAAi/U3W-AI-main/cube-mini/main.js?{"page":"pages%2Fmine%2Favatar%2Findex"} ***!
  \**************************************************************************************/
/*! no static exports found */
/***/ (function(module, exports, __webpack_require__) {

"use strict";
/* WEBPACK VAR INJECTION */(function(wx, createPage) {

var _interopRequireDefault = __webpack_require__(/*! @babel/runtime/helpers/interopRequireDefault */ 4);
__webpack_require__(/*! uni-pages */ 26);
__webpack_require__(/*! @dcloudio/uni-stat/dist/uni-stat.es.js */ 27);
var _vue = _interopRequireDefault(__webpack_require__(/*! vue */ 25));
var _index = _interopRequireDefault(__webpack_require__(/*! ./pages/mine/avatar/index.vue */ 96));
// @ts-ignore
wx.__webpack_require_UNI_MP_PLUGIN__ = __webpack_require__;
createPage(_index.default);
/* WEBPACK VAR INJECTION */}.call(this, __webpack_require__(/*! ./node_modules/@dcloudio/uni-mp-weixin/dist/wx.js */ 1)["default"], __webpack_require__(/*! ./node_modules/@dcloudio/uni-mp-weixin/dist/index.js */ 2)["createPage"]))

/***/ }),

/***/ 96:
/*!*****************************************************************!*\
  !*** D:/AAAi/U3W-AI-main/cube-mini/pages/mine/avatar/index.vue ***!
  \*****************************************************************/
/*! no static exports found */
/***/ (function(module, __webpack_exports__, __webpack_require__) {

"use strict";
__webpack_require__.r(__webpack_exports__);
/* harmony import */ var _index_vue_vue_type_template_id_05deedaf___WEBPACK_IMPORTED_MODULE_0__ = __webpack_require__(/*! ./index.vue?vue&type=template&id=05deedaf& */ 97);
/* harmony import */ var _index_vue_vue_type_script_lang_js___WEBPACK_IMPORTED_MODULE_1__ = __webpack_require__(/*! ./index.vue?vue&type=script&lang=js& */ 99);
/* harmony reexport (unknown) */ for(var __WEBPACK_IMPORT_KEY__ in _index_vue_vue_type_script_lang_js___WEBPACK_IMPORTED_MODULE_1__) if(["default"].indexOf(__WEBPACK_IMPORT_KEY__) < 0) (function(key) { __webpack_require__.d(__webpack_exports__, key, function() { return _index_vue_vue_type_script_lang_js___WEBPACK_IMPORTED_MODULE_1__[key]; }) }(__WEBPACK_IMPORT_KEY__));
/* harmony import */ var _index_vue_vue_type_style_index_0_lang_css___WEBPACK_IMPORTED_MODULE_2__ = __webpack_require__(/*! ./index.vue?vue&type=style&index=0&lang=css& */ 103);
/* harmony import */ var _HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_vue_loader_lib_runtime_componentNormalizer_js__WEBPACK_IMPORTED_MODULE_3__ = __webpack_require__(/*! ../../../../../../小红帽/HBuilderX.4.76.2025082103/HBuilderX/plugins/uniapp-cli/node_modules/@dcloudio/vue-cli-plugin-uni/packages/vue-loader/lib/runtime/componentNormalizer.js */ 52);

var renderjs





/* normalize component */

var component = Object(_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_vue_loader_lib_runtime_componentNormalizer_js__WEBPACK_IMPORTED_MODULE_3__["default"])(
  _index_vue_vue_type_script_lang_js___WEBPACK_IMPORTED_MODULE_1__["default"],
  _index_vue_vue_type_template_id_05deedaf___WEBPACK_IMPORTED_MODULE_0__["render"],
  _index_vue_vue_type_template_id_05deedaf___WEBPACK_IMPORTED_MODULE_0__["staticRenderFns"],
  false,
  null,
  null,
  null,
  false,
  _index_vue_vue_type_template_id_05deedaf___WEBPACK_IMPORTED_MODULE_0__["components"],
  renderjs
)

component.options.__file = "pages/mine/avatar/index.vue"
/* harmony default export */ __webpack_exports__["default"] = (component.exports);

/***/ }),

/***/ 97:
/*!************************************************************************************************!*\
  !*** D:/AAAi/U3W-AI-main/cube-mini/pages/mine/avatar/index.vue?vue&type=template&id=05deedaf& ***!
  \************************************************************************************************/
/*! exports provided: render, staticRenderFns, recyclableRender, components */
/***/ (function(module, __webpack_exports__, __webpack_require__) {

"use strict";
__webpack_require__.r(__webpack_exports__);
/* harmony import */ var _HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_vue_loader_lib_loaders_templateLoader_js_vue_loader_options_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_webpack_preprocess_loader_index_js_ref_17_0_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_webpack_uni_mp_loader_lib_template_js_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_webpack_uni_app_loader_page_meta_js_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_vue_loader_lib_index_js_vue_loader_options_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_webpack_uni_mp_loader_lib_style_js_index_vue_vue_type_template_id_05deedaf___WEBPACK_IMPORTED_MODULE_0__ = __webpack_require__(/*! -!../../../../../../小红帽/HBuilderX.4.76.2025082103/HBuilderX/plugins/uniapp-cli/node_modules/@dcloudio/vue-cli-plugin-uni/packages/vue-loader/lib/loaders/templateLoader.js??vue-loader-options!../../../../../../小红帽/HBuilderX.4.76.2025082103/HBuilderX/plugins/uniapp-cli/node_modules/@dcloudio/vue-cli-plugin-uni/packages/webpack-preprocess-loader??ref--17-0!../../../../../../小红帽/HBuilderX.4.76.2025082103/HBuilderX/plugins/uniapp-cli/node_modules/@dcloudio/webpack-uni-mp-loader/lib/template.js!../../../../../../小红帽/HBuilderX.4.76.2025082103/HBuilderX/plugins/uniapp-cli/node_modules/@dcloudio/vue-cli-plugin-uni/packages/webpack-uni-app-loader/page-meta.js!../../../../../../小红帽/HBuilderX.4.76.2025082103/HBuilderX/plugins/uniapp-cli/node_modules/@dcloudio/vue-cli-plugin-uni/packages/vue-loader/lib??vue-loader-options!../../../../../../小红帽/HBuilderX.4.76.2025082103/HBuilderX/plugins/uniapp-cli/node_modules/@dcloudio/webpack-uni-mp-loader/lib/style.js!./index.vue?vue&type=template&id=05deedaf& */ 98);
/* harmony reexport (safe) */ __webpack_require__.d(__webpack_exports__, "render", function() { return _HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_vue_loader_lib_loaders_templateLoader_js_vue_loader_options_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_webpack_preprocess_loader_index_js_ref_17_0_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_webpack_uni_mp_loader_lib_template_js_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_webpack_uni_app_loader_page_meta_js_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_vue_loader_lib_index_js_vue_loader_options_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_webpack_uni_mp_loader_lib_style_js_index_vue_vue_type_template_id_05deedaf___WEBPACK_IMPORTED_MODULE_0__["render"]; });

/* harmony reexport (safe) */ __webpack_require__.d(__webpack_exports__, "staticRenderFns", function() { return _HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_vue_loader_lib_loaders_templateLoader_js_vue_loader_options_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_webpack_preprocess_loader_index_js_ref_17_0_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_webpack_uni_mp_loader_lib_template_js_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_webpack_uni_app_loader_page_meta_js_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_vue_loader_lib_index_js_vue_loader_options_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_webpack_uni_mp_loader_lib_style_js_index_vue_vue_type_template_id_05deedaf___WEBPACK_IMPORTED_MODULE_0__["staticRenderFns"]; });

/* harmony reexport (safe) */ __webpack_require__.d(__webpack_exports__, "recyclableRender", function() { return _HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_vue_loader_lib_loaders_templateLoader_js_vue_loader_options_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_webpack_preprocess_loader_index_js_ref_17_0_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_webpack_uni_mp_loader_lib_template_js_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_webpack_uni_app_loader_page_meta_js_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_vue_loader_lib_index_js_vue_loader_options_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_webpack_uni_mp_loader_lib_style_js_index_vue_vue_type_template_id_05deedaf___WEBPACK_IMPORTED_MODULE_0__["recyclableRender"]; });

/* harmony reexport (safe) */ __webpack_require__.d(__webpack_exports__, "components", function() { return _HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_vue_loader_lib_loaders_templateLoader_js_vue_loader_options_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_webpack_preprocess_loader_index_js_ref_17_0_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_webpack_uni_mp_loader_lib_template_js_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_webpack_uni_app_loader_page_meta_js_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_vue_loader_lib_index_js_vue_loader_options_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_webpack_uni_mp_loader_lib_style_js_index_vue_vue_type_template_id_05deedaf___WEBPACK_IMPORTED_MODULE_0__["components"]; });



/***/ }),

/***/ 98:
/*!************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************!*\
  !*** ./node_modules/@dcloudio/vue-cli-plugin-uni/packages/vue-loader/lib/loaders/templateLoader.js??vue-loader-options!./node_modules/@dcloudio/vue-cli-plugin-uni/packages/webpack-preprocess-loader??ref--17-0!./node_modules/@dcloudio/webpack-uni-mp-loader/lib/template.js!./node_modules/@dcloudio/vue-cli-plugin-uni/packages/webpack-uni-app-loader/page-meta.js!./node_modules/@dcloudio/vue-cli-plugin-uni/packages/vue-loader/lib??vue-loader-options!./node_modules/@dcloudio/webpack-uni-mp-loader/lib/style.js!D:/AAAi/U3W-AI-main/cube-mini/pages/mine/avatar/index.vue?vue&type=template&id=05deedaf& ***!
  \************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************/
/*! exports provided: render, staticRenderFns, recyclableRender, components */
/***/ (function(module, __webpack_exports__, __webpack_require__) {

"use strict";
__webpack_require__.r(__webpack_exports__);
/* harmony export (binding) */ __webpack_require__.d(__webpack_exports__, "render", function() { return render; });
/* harmony export (binding) */ __webpack_require__.d(__webpack_exports__, "staticRenderFns", function() { return staticRenderFns; });
/* harmony export (binding) */ __webpack_require__.d(__webpack_exports__, "recyclableRender", function() { return recyclableRender; });
/* harmony export (binding) */ __webpack_require__.d(__webpack_exports__, "components", function() { return components; });
var components
var render = function () {
  var _vm = this
  var _h = _vm.$createElement
  var _c = _vm._self._c || _h
}
var recyclableRender = false
var staticRenderFns = []
render._withStripped = true



/***/ }),

/***/ 99:
/*!******************************************************************************************!*\
  !*** D:/AAAi/U3W-AI-main/cube-mini/pages/mine/avatar/index.vue?vue&type=script&lang=js& ***!
  \******************************************************************************************/
/*! no static exports found */
/***/ (function(module, __webpack_exports__, __webpack_require__) {

"use strict";
__webpack_require__.r(__webpack_exports__);
/* harmony import */ var _HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_babel_loader_lib_index_js_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_webpack_preprocess_loader_index_js_ref_13_1_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_webpack_uni_mp_loader_lib_script_js_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_vue_loader_lib_index_js_vue_loader_options_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_webpack_uni_mp_loader_lib_style_js_index_vue_vue_type_script_lang_js___WEBPACK_IMPORTED_MODULE_0__ = __webpack_require__(/*! -!../../../../../../小红帽/HBuilderX.4.76.2025082103/HBuilderX/plugins/uniapp-cli/node_modules/babel-loader/lib!../../../../../../小红帽/HBuilderX.4.76.2025082103/HBuilderX/plugins/uniapp-cli/node_modules/@dcloudio/vue-cli-plugin-uni/packages/webpack-preprocess-loader??ref--13-1!../../../../../../小红帽/HBuilderX.4.76.2025082103/HBuilderX/plugins/uniapp-cli/node_modules/@dcloudio/webpack-uni-mp-loader/lib/script.js!../../../../../../小红帽/HBuilderX.4.76.2025082103/HBuilderX/plugins/uniapp-cli/node_modules/@dcloudio/vue-cli-plugin-uni/packages/vue-loader/lib??vue-loader-options!../../../../../../小红帽/HBuilderX.4.76.2025082103/HBuilderX/plugins/uniapp-cli/node_modules/@dcloudio/webpack-uni-mp-loader/lib/style.js!./index.vue?vue&type=script&lang=js& */ 100);
/* harmony import */ var _HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_babel_loader_lib_index_js_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_webpack_preprocess_loader_index_js_ref_13_1_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_webpack_uni_mp_loader_lib_script_js_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_vue_loader_lib_index_js_vue_loader_options_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_webpack_uni_mp_loader_lib_style_js_index_vue_vue_type_script_lang_js___WEBPACK_IMPORTED_MODULE_0___default = /*#__PURE__*/__webpack_require__.n(_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_babel_loader_lib_index_js_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_webpack_preprocess_loader_index_js_ref_13_1_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_webpack_uni_mp_loader_lib_script_js_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_vue_loader_lib_index_js_vue_loader_options_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_webpack_uni_mp_loader_lib_style_js_index_vue_vue_type_script_lang_js___WEBPACK_IMPORTED_MODULE_0__);
/* harmony reexport (unknown) */ for(var __WEBPACK_IMPORT_KEY__ in _HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_babel_loader_lib_index_js_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_webpack_preprocess_loader_index_js_ref_13_1_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_webpack_uni_mp_loader_lib_script_js_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_vue_loader_lib_index_js_vue_loader_options_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_webpack_uni_mp_loader_lib_style_js_index_vue_vue_type_script_lang_js___WEBPACK_IMPORTED_MODULE_0__) if(["default"].indexOf(__WEBPACK_IMPORT_KEY__) < 0) (function(key) { __webpack_require__.d(__webpack_exports__, key, function() { return _HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_babel_loader_lib_index_js_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_webpack_preprocess_loader_index_js_ref_13_1_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_webpack_uni_mp_loader_lib_script_js_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_vue_loader_lib_index_js_vue_loader_options_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_webpack_uni_mp_loader_lib_style_js_index_vue_vue_type_script_lang_js___WEBPACK_IMPORTED_MODULE_0__[key]; }) }(__WEBPACK_IMPORT_KEY__));
 /* harmony default export */ __webpack_exports__["default"] = (_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_babel_loader_lib_index_js_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_webpack_preprocess_loader_index_js_ref_13_1_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_webpack_uni_mp_loader_lib_script_js_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_vue_loader_lib_index_js_vue_loader_options_HBuilderX_4_76_2025082103_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_webpack_uni_mp_loader_lib_style_js_index_vue_vue_type_script_lang_js___WEBPACK_IMPORTED_MODULE_0___default.a); 

/***/ })

},[[95,"common/runtime","common/vendor"]]]);
//# sourceMappingURL=../../../../.sourcemap/mp-weixin/pages/mine/avatar/index.js.map