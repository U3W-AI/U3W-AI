(global["webpackJsonp"] = global["webpackJsonp"] || []).push([["pages/work/index"],{

/***/ 66:
/*!*****************************************************************************************!*\
  !*** /Users/workspace/AGI/u3w/U3W-AI/cube-mini/main.js?{"page":"pages%2Fwork%2Findex"} ***!
  \*****************************************************************************************/
/*! no static exports found */
/***/ (function(module, exports, __webpack_require__) {

"use strict";
/* WEBPACK VAR INJECTION */(function(wx, createPage) {

var _interopRequireDefault = __webpack_require__(/*! @babel/runtime/helpers/interopRequireDefault */ 4);
__webpack_require__(/*! uni-pages */ 26);
__webpack_require__(/*! @dcloudio/uni-stat/dist/uni-stat.es.js */ 27);
var _vue = _interopRequireDefault(__webpack_require__(/*! vue */ 25));
var _index = _interopRequireDefault(__webpack_require__(/*! ./pages/work/index.vue */ 67));
// @ts-ignore
wx.__webpack_require_UNI_MP_PLUGIN__ = __webpack_require__;
createPage(_index.default);
/* WEBPACK VAR INJECTION */}.call(this, __webpack_require__(/*! ./node_modules/@dcloudio/uni-mp-weixin/dist/wx.js */ 1)["default"], __webpack_require__(/*! ./node_modules/@dcloudio/uni-mp-weixin/dist/index.js */ 2)["createPage"]))

/***/ }),

/***/ 67:
/*!**********************************************************************!*\
  !*** /Users/workspace/AGI/u3w/U3W-AI/cube-mini/pages/work/index.vue ***!
  \**********************************************************************/
/*! no static exports found */
/***/ (function(module, __webpack_exports__, __webpack_require__) {

"use strict";
__webpack_require__.r(__webpack_exports__);
/* harmony import */ var _index_vue_vue_type_template_id_51b5538d_scoped_true___WEBPACK_IMPORTED_MODULE_0__ = __webpack_require__(/*! ./index.vue?vue&type=template&id=51b5538d&scoped=true& */ 68);
/* harmony import */ var _index_vue_vue_type_script_lang_js___WEBPACK_IMPORTED_MODULE_1__ = __webpack_require__(/*! ./index.vue?vue&type=script&lang=js& */ 70);
/* harmony reexport (unknown) */ for(var __WEBPACK_IMPORT_KEY__ in _index_vue_vue_type_script_lang_js___WEBPACK_IMPORTED_MODULE_1__) if(["default"].indexOf(__WEBPACK_IMPORT_KEY__) < 0) (function(key) { __webpack_require__.d(__webpack_exports__, key, function() { return _index_vue_vue_type_script_lang_js___WEBPACK_IMPORTED_MODULE_1__[key]; }) }(__WEBPACK_IMPORT_KEY__));
/* harmony import */ var _index_vue_vue_type_style_index_0_id_51b5538d_scoped_true_lang_css___WEBPACK_IMPORTED_MODULE_2__ = __webpack_require__(/*! ./index.vue?vue&type=style&index=0&id=51b5538d&scoped=true&lang=css& */ 83);
/* harmony import */ var _Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_vue_loader_lib_runtime_componentNormalizer_js__WEBPACK_IMPORTED_MODULE_3__ = __webpack_require__(/*! ../../../../../../../../Applications/HBuilderX.app/Contents/HBuilderX/plugins/uniapp-cli/node_modules/@dcloudio/vue-cli-plugin-uni/packages/vue-loader/lib/runtime/componentNormalizer.js */ 52);

var renderjs





/* normalize component */

var component = Object(_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_vue_loader_lib_runtime_componentNormalizer_js__WEBPACK_IMPORTED_MODULE_3__["default"])(
  _index_vue_vue_type_script_lang_js___WEBPACK_IMPORTED_MODULE_1__["default"],
  _index_vue_vue_type_template_id_51b5538d_scoped_true___WEBPACK_IMPORTED_MODULE_0__["render"],
  _index_vue_vue_type_template_id_51b5538d_scoped_true___WEBPACK_IMPORTED_MODULE_0__["staticRenderFns"],
  false,
  null,
  "51b5538d",
  null,
  false,
  _index_vue_vue_type_template_id_51b5538d_scoped_true___WEBPACK_IMPORTED_MODULE_0__["components"],
  renderjs
)

component.options.__file = "pages/work/index.vue"
/* harmony default export */ __webpack_exports__["default"] = (component.exports);

/***/ }),

/***/ 68:
/*!*****************************************************************************************************************!*\
  !*** /Users/workspace/AGI/u3w/U3W-AI/cube-mini/pages/work/index.vue?vue&type=template&id=51b5538d&scoped=true& ***!
  \*****************************************************************************************************************/
/*! exports provided: render, staticRenderFns, recyclableRender, components */
/***/ (function(module, __webpack_exports__, __webpack_require__) {

"use strict";
__webpack_require__.r(__webpack_exports__);
/* harmony import */ var _Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_vue_loader_lib_loaders_templateLoader_js_vue_loader_options_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_webpack_preprocess_loader_index_js_ref_17_0_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_webpack_uni_mp_loader_lib_template_js_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_webpack_uni_app_loader_page_meta_js_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_vue_loader_lib_index_js_vue_loader_options_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_webpack_uni_mp_loader_lib_style_js_index_vue_vue_type_template_id_51b5538d_scoped_true___WEBPACK_IMPORTED_MODULE_0__ = __webpack_require__(/*! -!../../../../../../../../Applications/HBuilderX.app/Contents/HBuilderX/plugins/uniapp-cli/node_modules/@dcloudio/vue-cli-plugin-uni/packages/vue-loader/lib/loaders/templateLoader.js??vue-loader-options!../../../../../../../../Applications/HBuilderX.app/Contents/HBuilderX/plugins/uniapp-cli/node_modules/@dcloudio/vue-cli-plugin-uni/packages/webpack-preprocess-loader??ref--17-0!../../../../../../../../Applications/HBuilderX.app/Contents/HBuilderX/plugins/uniapp-cli/node_modules/@dcloudio/webpack-uni-mp-loader/lib/template.js!../../../../../../../../Applications/HBuilderX.app/Contents/HBuilderX/plugins/uniapp-cli/node_modules/@dcloudio/vue-cli-plugin-uni/packages/webpack-uni-app-loader/page-meta.js!../../../../../../../../Applications/HBuilderX.app/Contents/HBuilderX/plugins/uniapp-cli/node_modules/@dcloudio/vue-cli-plugin-uni/packages/vue-loader/lib??vue-loader-options!../../../../../../../../Applications/HBuilderX.app/Contents/HBuilderX/plugins/uniapp-cli/node_modules/@dcloudio/webpack-uni-mp-loader/lib/style.js!./index.vue?vue&type=template&id=51b5538d&scoped=true& */ 69);
/* harmony reexport (safe) */ __webpack_require__.d(__webpack_exports__, "render", function() { return _Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_vue_loader_lib_loaders_templateLoader_js_vue_loader_options_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_webpack_preprocess_loader_index_js_ref_17_0_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_webpack_uni_mp_loader_lib_template_js_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_webpack_uni_app_loader_page_meta_js_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_vue_loader_lib_index_js_vue_loader_options_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_webpack_uni_mp_loader_lib_style_js_index_vue_vue_type_template_id_51b5538d_scoped_true___WEBPACK_IMPORTED_MODULE_0__["render"]; });

/* harmony reexport (safe) */ __webpack_require__.d(__webpack_exports__, "staticRenderFns", function() { return _Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_vue_loader_lib_loaders_templateLoader_js_vue_loader_options_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_webpack_preprocess_loader_index_js_ref_17_0_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_webpack_uni_mp_loader_lib_template_js_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_webpack_uni_app_loader_page_meta_js_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_vue_loader_lib_index_js_vue_loader_options_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_webpack_uni_mp_loader_lib_style_js_index_vue_vue_type_template_id_51b5538d_scoped_true___WEBPACK_IMPORTED_MODULE_0__["staticRenderFns"]; });

/* harmony reexport (safe) */ __webpack_require__.d(__webpack_exports__, "recyclableRender", function() { return _Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_vue_loader_lib_loaders_templateLoader_js_vue_loader_options_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_webpack_preprocess_loader_index_js_ref_17_0_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_webpack_uni_mp_loader_lib_template_js_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_webpack_uni_app_loader_page_meta_js_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_vue_loader_lib_index_js_vue_loader_options_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_webpack_uni_mp_loader_lib_style_js_index_vue_vue_type_template_id_51b5538d_scoped_true___WEBPACK_IMPORTED_MODULE_0__["recyclableRender"]; });

/* harmony reexport (safe) */ __webpack_require__.d(__webpack_exports__, "components", function() { return _Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_vue_loader_lib_loaders_templateLoader_js_vue_loader_options_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_webpack_preprocess_loader_index_js_ref_17_0_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_webpack_uni_mp_loader_lib_template_js_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_webpack_uni_app_loader_page_meta_js_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_vue_loader_lib_index_js_vue_loader_options_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_webpack_uni_mp_loader_lib_style_js_index_vue_vue_type_template_id_51b5538d_scoped_true___WEBPACK_IMPORTED_MODULE_0__["components"]; });



/***/ }),

/***/ 69:
/*!*****************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************!*\
  !*** ./node_modules/@dcloudio/vue-cli-plugin-uni/packages/vue-loader/lib/loaders/templateLoader.js??vue-loader-options!./node_modules/@dcloudio/vue-cli-plugin-uni/packages/webpack-preprocess-loader??ref--17-0!./node_modules/@dcloudio/webpack-uni-mp-loader/lib/template.js!./node_modules/@dcloudio/vue-cli-plugin-uni/packages/webpack-uni-app-loader/page-meta.js!./node_modules/@dcloudio/vue-cli-plugin-uni/packages/vue-loader/lib??vue-loader-options!./node_modules/@dcloudio/webpack-uni-mp-loader/lib/style.js!/Users/workspace/AGI/u3w/U3W-AI/cube-mini/pages/work/index.vue?vue&type=template&id=51b5538d&scoped=true& ***!
  \*****************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************/
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
  var l1 = _vm.sectionExpanded.aiConfig
    ? _vm.__map(_vm.aiList, function (ai, index) {
        var $orig = _vm.__get_orig(ai)
        var m0 = ai.enabled && _vm.isAiLoginEnabled(ai)
        var m1 = _vm.isAiLoginEnabled(ai)
        var m2 = _vm.isAiLoginEnabled(ai)
        var m3 =
          !_vm.isAiLoginEnabled(ai) &&
          !_vm.isLoading.yuanbao &&
          !_vm.isLoading.doubao &&
          !_vm.isLoading.agent
        var m4 = _vm.isAiInLoading(ai)
        var m5 = ai.enabled && _vm.isAiLoginEnabled(ai)
        var m6 = !_vm.isAiLoginEnabled(ai) || _vm.isAiInLoading(ai)
        var g0 = ai.capabilities.length
        var l0 =
          g0 > 0
            ? _vm.__map(ai.capabilities, function (capability, __i0__) {
                var $orig = _vm.__get_orig(capability)
                var g1 = ai.selectedCapabilities.includes(capability.value)
                var m7 = !ai.enabled || !_vm.isAiLoginEnabled(ai)
                return {
                  $orig: $orig,
                  g1: g1,
                  m7: m7,
                }
              })
            : null
        return {
          $orig: $orig,
          m0: m0,
          m1: m1,
          m2: m2,
          m3: m3,
          m4: m4,
          m5: m5,
          m6: m6,
          g0: g0,
          l0: l0,
        }
      })
    : null
  var g2 = _vm.sectionExpanded.promptInput ? _vm.promptInput.length : null
  var l3 =
    _vm.taskStarted && _vm.sectionExpanded.taskStatus
      ? _vm.__map(_vm.enabledAIs, function (ai, index) {
          var $orig = _vm.__get_orig(ai)
          var m8 = _vm.getStatusText(ai.status)
          var m9 = _vm.getStatusIconClass(ai.status)
          var m10 = _vm.getStatusEmoji(ai.status)
          var g3 = ai.isExpanded && ai.progressLogs.length > 0
          var l2 = g3
            ? _vm.__map(ai.progressLogs, function (log, logIndex) {
                var $orig = _vm.__get_orig(log)
                var m11 = _vm.formatTime(log.timestamp)
                return {
                  $orig: $orig,
                  m11: m11,
                }
              })
            : null
          return {
            $orig: $orig,
            m8: m8,
            m9: m9,
            m10: m10,
            g3: g3,
            l2: l2,
          }
        })
      : null
  var g4 = _vm.results.length
  var m12 =
    g4 > 0 && _vm.currentResult
      ? _vm.currentResult.shareImgUrl &&
        _vm.isImageFile(_vm.currentResult.shareImgUrl)
      : null
  var m13 =
    g4 > 0 && _vm.currentResult && !m12
      ? _vm.currentResult.shareImgUrl &&
        _vm.isPdfFile(_vm.currentResult.shareImgUrl)
      : null
  var m14 =
    g4 > 0 && _vm.currentResult && !m12 && !m13
      ? _vm.renderMarkdown(_vm.currentResult.content)
      : null
  var l5 = _vm.historyDrawerVisible
    ? _vm.__map(_vm.groupedHistory, function (group, date) {
        var $orig = _vm.__get_orig(group)
        var l4 = _vm.__map(group, function (item, index) {
          var $orig = _vm.__get_orig(item)
          var m15 = _vm.formatHistoryTime(item.createTime)
          return {
            $orig: $orig,
            m15: m15,
          }
        })
        return {
          $orig: $orig,
          l4: l4,
        }
      })
    : null
  var l6 = _vm.scoreModalVisible
    ? _vm.__map(_vm.results, function (result, index) {
        var $orig = _vm.__get_orig(result)
        var g5 = _vm.selectedResults.includes(result.aiName)
        return {
          $orig: $orig,
          g5: g5,
        }
      })
    : null
  var g6 = _vm.layoutModalVisible ? _vm.layoutPrompt.trim().length : null
  _vm.$mp.data = Object.assign(
    {},
    {
      $root: {
        l1: l1,
        g2: g2,
        l3: l3,
        g4: g4,
        m12: m12,
        m13: m13,
        m14: m14,
        l5: l5,
        l6: l6,
        g6: g6,
      },
    }
  )
}
var recyclableRender = false
var staticRenderFns = []
render._withStripped = true



/***/ }),

/***/ 70:
/*!***********************************************************************************************!*\
  !*** /Users/workspace/AGI/u3w/U3W-AI/cube-mini/pages/work/index.vue?vue&type=script&lang=js& ***!
  \***********************************************************************************************/
/*! no static exports found */
/***/ (function(module, __webpack_exports__, __webpack_require__) {

"use strict";
__webpack_require__.r(__webpack_exports__);
/* harmony import */ var _Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_babel_loader_lib_index_js_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_webpack_preprocess_loader_index_js_ref_13_1_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_webpack_uni_mp_loader_lib_script_js_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_vue_loader_lib_index_js_vue_loader_options_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_webpack_uni_mp_loader_lib_style_js_index_vue_vue_type_script_lang_js___WEBPACK_IMPORTED_MODULE_0__ = __webpack_require__(/*! -!../../../../../../../../Applications/HBuilderX.app/Contents/HBuilderX/plugins/uniapp-cli/node_modules/babel-loader/lib!../../../../../../../../Applications/HBuilderX.app/Contents/HBuilderX/plugins/uniapp-cli/node_modules/@dcloudio/vue-cli-plugin-uni/packages/webpack-preprocess-loader??ref--13-1!../../../../../../../../Applications/HBuilderX.app/Contents/HBuilderX/plugins/uniapp-cli/node_modules/@dcloudio/webpack-uni-mp-loader/lib/script.js!../../../../../../../../Applications/HBuilderX.app/Contents/HBuilderX/plugins/uniapp-cli/node_modules/@dcloudio/vue-cli-plugin-uni/packages/vue-loader/lib??vue-loader-options!../../../../../../../../Applications/HBuilderX.app/Contents/HBuilderX/plugins/uniapp-cli/node_modules/@dcloudio/webpack-uni-mp-loader/lib/style.js!./index.vue?vue&type=script&lang=js& */ 71);
/* harmony import */ var _Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_babel_loader_lib_index_js_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_webpack_preprocess_loader_index_js_ref_13_1_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_webpack_uni_mp_loader_lib_script_js_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_vue_loader_lib_index_js_vue_loader_options_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_webpack_uni_mp_loader_lib_style_js_index_vue_vue_type_script_lang_js___WEBPACK_IMPORTED_MODULE_0___default = /*#__PURE__*/__webpack_require__.n(_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_babel_loader_lib_index_js_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_webpack_preprocess_loader_index_js_ref_13_1_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_webpack_uni_mp_loader_lib_script_js_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_vue_loader_lib_index_js_vue_loader_options_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_webpack_uni_mp_loader_lib_style_js_index_vue_vue_type_script_lang_js___WEBPACK_IMPORTED_MODULE_0__);
/* harmony reexport (unknown) */ for(var __WEBPACK_IMPORT_KEY__ in _Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_babel_loader_lib_index_js_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_webpack_preprocess_loader_index_js_ref_13_1_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_webpack_uni_mp_loader_lib_script_js_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_vue_loader_lib_index_js_vue_loader_options_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_webpack_uni_mp_loader_lib_style_js_index_vue_vue_type_script_lang_js___WEBPACK_IMPORTED_MODULE_0__) if(["default"].indexOf(__WEBPACK_IMPORT_KEY__) < 0) (function(key) { __webpack_require__.d(__webpack_exports__, key, function() { return _Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_babel_loader_lib_index_js_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_webpack_preprocess_loader_index_js_ref_13_1_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_webpack_uni_mp_loader_lib_script_js_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_vue_loader_lib_index_js_vue_loader_options_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_webpack_uni_mp_loader_lib_style_js_index_vue_vue_type_script_lang_js___WEBPACK_IMPORTED_MODULE_0__[key]; }) }(__WEBPACK_IMPORT_KEY__));
 /* harmony default export */ __webpack_exports__["default"] = (_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_babel_loader_lib_index_js_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_webpack_preprocess_loader_index_js_ref_13_1_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_webpack_uni_mp_loader_lib_script_js_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_vue_loader_lib_index_js_vue_loader_options_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_webpack_uni_mp_loader_lib_style_js_index_vue_vue_type_script_lang_js___WEBPACK_IMPORTED_MODULE_0___default.a); 

/***/ }),

/***/ 71:
/*!******************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************!*\
  !*** ./node_modules/babel-loader/lib!./node_modules/@dcloudio/vue-cli-plugin-uni/packages/webpack-preprocess-loader??ref--13-1!./node_modules/@dcloudio/webpack-uni-mp-loader/lib/script.js!./node_modules/@dcloudio/vue-cli-plugin-uni/packages/vue-loader/lib??vue-loader-options!./node_modules/@dcloudio/webpack-uni-mp-loader/lib/style.js!/Users/workspace/AGI/u3w/U3W-AI/cube-mini/pages/work/index.vue?vue&type=script&lang=js& ***!
  \******************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************/
/*! no static exports found */
/***/ (function(module, exports, __webpack_require__) {

"use strict";
/* WEBPACK VAR INJECTION */(function(uni) {

var _interopRequireDefault = __webpack_require__(/*! @babel/runtime/helpers/interopRequireDefault */ 4);
Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.default = void 0;
var _regenerator = _interopRequireDefault(__webpack_require__(/*! @babel/runtime/regenerator */ 72));
var _slicedToArray2 = _interopRequireDefault(__webpack_require__(/*! @babel/runtime/helpers/slicedToArray */ 5));
var _typeof2 = _interopRequireDefault(__webpack_require__(/*! @babel/runtime/helpers/typeof */ 13));
var _asyncToGenerator2 = _interopRequireDefault(__webpack_require__(/*! @babel/runtime/helpers/asyncToGenerator */ 74));
var _defineProperty2 = _interopRequireDefault(__webpack_require__(/*! @babel/runtime/helpers/defineProperty */ 11));
var _marked = __webpack_require__(/*! marked */ 75);
var _aigc = __webpack_require__(/*! @/api/wechat/aigc */ 77);
var _uuid = __webpack_require__(/*! uuid */ 78);
var _storage = _interopRequireDefault(__webpack_require__(/*! @/utils/storage */ 40));
var _constant = _interopRequireDefault(__webpack_require__(/*! @/utils/constant */ 41));
function ownKeys(object, enumerableOnly) { var keys = Object.keys(object); if (Object.getOwnPropertySymbols) { var symbols = Object.getOwnPropertySymbols(object); enumerableOnly && (symbols = symbols.filter(function (sym) { return Object.getOwnPropertyDescriptor(object, sym).enumerable; })), keys.push.apply(keys, symbols); } return keys; }
function _objectSpread(target) { for (var i = 1; i < arguments.length; i++) { var source = null != arguments[i] ? arguments[i] : {}; i % 2 ? ownKeys(Object(source), !0).forEach(function (key) { (0, _defineProperty2.default)(target, key, source[key]); }) : Object.getOwnPropertyDescriptors ? Object.defineProperties(target, Object.getOwnPropertyDescriptors(source)) : ownKeys(Object(source)).forEach(function (key) { Object.defineProperty(target, key, Object.getOwnPropertyDescriptor(source, key)); }); } return target; }
var _default = {
  name: 'MiniConsole',
  data: function data() {
    return {
      // 用户信息
      userId: '',
      corpId: '',
      chatId: '',
      expandedHistoryItems: {},
      userInfoReq: {
        userPrompt: '',
        userId: '',
        corpId: '',
        taskId: '',
        roles: '',
        toneChatId: '',
        ybDsChatId: '',
        dbChatId: '',
        isNewChat: true
      },
      jsonRpcReqest: {
        jsonrpc: '2.0',
        id: '',
        method: '',
        params: {}
      },
      // 区域展开状态
      sectionExpanded: {
        aiConfig: true,
        promptInput: true,
        taskStatus: true
      },
      // AI配置（参考PC端完整配置）
      aiList: [{
        name: 'TurboS@元器',
        avatar: 'https://u3w.com/chatfile/yuanbao.png',
        capabilities: [],
        selectedCapabilities: [],
        enabled: true,
        status: 'idle',
        progressLogs: [],
        isExpanded: true
      }, {
        name: 'TurboS长文版@元器',
        avatar: 'https://u3w.com/chatfile/yuanbao.png',
        capabilities: [],
        selectedCapabilities: [],
        enabled: true,
        status: 'idle',
        progressLogs: [],
        isExpanded: true
      }, {
        name: '腾讯元宝T1',
        avatar: 'https://u3w.com/chatfile/yuanbao.png',
        capabilities: [{
          label: '深度思考',
          value: 'deep_thinking'
        }, {
          label: '联网搜索',
          value: 'web_search'
        }],
        selectedCapabilities: ['deep_thinking', 'web_search'],
        enabled: true,
        status: 'idle',
        progressLogs: [],
        isExpanded: true
      }, {
        name: '腾讯元宝DS',
        avatar: 'https://u3w.com/chatfile/yuanbao.png',
        capabilities: [{
          label: '深度思考',
          value: 'deep_thinking'
        }, {
          label: '联网搜索',
          value: 'web_search'
        }],
        selectedCapabilities: ['deep_thinking', 'web_search'],
        enabled: true,
        status: 'idle',
        progressLogs: [],
        isExpanded: true
      }, {
        name: '豆包',
        avatar: 'https://u3w.com/chatfile/%E8%B1%86%E5%8C%85.png',
        capabilities: [{
          label: '深度思考',
          value: 'deep_thinking'
        }],
        selectedCapabilities: ['deep_thinking'],
        enabled: true,
        status: 'idle',
        progressLogs: [],
        isExpanded: true
      }],
      // 输入和任务状态
      promptInput: '',
      taskStarted: false,
      enabledAIs: [],
      // 可视化
      screenshots: [],
      autoPlay: false,
      // 结果
      results: [],
      activeResultIndex: 0,
      // 历史记录
      chatHistory: [],
      // 评分
      selectedResults: [],
      scorePrompt: '请你深度阅读以下几篇公众号文章，从多个维度进行逐项打分，输出评分结果。并在以下各篇文章的基础上博采众长，综合整理一篇更全面的文章。',
      // 收录计数器
      collectNum: 0,
      // 智能排版
      layoutPrompt: '',
      // WebSocket
      socketTask: null,
      reconnectTimer: null,
      heartbeatTimer: null,
      reconnectCount: 0,
      maxReconnectCount: 5,
      isConnecting: false,
      scrollIntoView: '',
      // 弹窗状态
      historyDrawerVisible: false,
      scoreModalVisible: false,
      layoutModalVisible: false,
      currentLayoutResult: null,
      // 当前要排版的结果

      // AI登录状态
      aiLoginStatus: {
        yuanbao: false,
        doubao: false,
        agent: false
      },
      accounts: {
        yuanbao: '',
        doubao: '',
        agent: ''
      },
      isLoading: {
        yuanbao: true,
        doubao: true,
        agent: true
      }
    };
  },
  computed: {
    canSend: function canSend() {
      var _this = this;
      // 检查是否有输入内容
      var hasInput = this.promptInput.trim().length > 0;

      // 检查是否有可用的AI（既启用又已登录）
      var hasAvailableAI = this.aiList.some(function (ai) {
        return ai.enabled && _this.isAiLoginEnabled(ai);
      });

      // 检查是否正在加载AI状态（如果正在加载，禁用发送按钮）
      var isCheckingStatus = this.isLoading.yuanbao || this.isLoading.doubao || this.isLoading.agent;
      return hasInput && hasAvailableAI && !isCheckingStatus;
    },
    canScore: function canScore() {
      var hasSelected = this.selectedResults.length > 0;
      var hasPrompt = this.scorePrompt.trim().length > 0;
      console.log('canScore - selectedResults:', this.selectedResults);
      console.log('canScore - scorePrompt length:', this.scorePrompt.trim().length);
      console.log('canScore - hasSelected:', hasSelected, 'hasPrompt:', hasPrompt);
      return hasSelected && hasPrompt;
    },
    currentResult: function currentResult() {
      return this.results[this.activeResultIndex] || null;
    },
    groupedHistory: function groupedHistory() {
      var _this2 = this;
      var groups = {};
      var chatGroups = {};

      // 首先按chatId分组
      this.chatHistory.forEach(function (item) {
        if (!chatGroups[item.chatId]) {
          chatGroups[item.chatId] = [];
        }
        chatGroups[item.chatId].push(item);
      });

      // 然后按日期分组，并处理父子关系
      Object.values(chatGroups).forEach(function (chatGroup) {
        // 按时间排序
        chatGroup.sort(function (a, b) {
          return new Date(a.createTime) - new Date(b.createTime);
        });

        // 获取最早的记录作为父级
        var parentItem = chatGroup[0];
        var date = _this2.getHistoryDate(parentItem.createTime);
        if (!groups[date]) {
          groups[date] = [];
        }

        // 添加父级记录
        groups[date].push(_objectSpread(_objectSpread({}, parentItem), {}, {
          isParent: true,
          isExpanded: _this2.expandedHistoryItems[parentItem.chatId] || false,
          children: chatGroup.slice(1).map(function (child) {
            return _objectSpread(_objectSpread({}, child), {}, {
              isParent: false
            });
          })
        }));
      });
      return groups;
    }
  },
  onLoad: function onLoad() {
    this.initUserInfo();

    // 检查用户信息是否完整
    if (!this.userId || !this.corpId) {
      console.log('用户信息不完整，跳转到登录页面');
      uni.showModal({
        title: '提示',
        content: '请先登录后再使用',
        showCancel: false,
        confirmText: '去登录',
        success: function success() {
          uni.navigateTo({
            url: '/pages/login/index'
          });
        }
      });
      return;
    }
    this.initWebSocket();
    this.loadChatHistory(0); // 加载历史记录
    this.loadLastChat(); // 加载上次会话
    this.checkAiLoginStatus(); // 检查AI登录状态
  },
  onUnload: function onUnload() {
    this.closeWebSocket();
  },
  methods: {
    // 初始化用户信息
    initUserInfo: function initUserInfo() {
      // 从store获取用户信息，兼容缓存方式
      this.userId = _storage.default.get(_constant.default.userId);
      this.corpId = _storage.default.get(_constant.default.corpId);
      this.chatId = this.generateUUID();

      // 初始化请求参数
      this.userInfoReq.userId = this.userId;
      this.userInfoReq.corpId = this.corpId;
      console.log('初始化用户信息:', {
        userId: this.userId,
        corpId: this.corpId
      });
    },
    // 生成UUID
    generateUUID: function generateUUID() {
      return (0, _uuid.v4)();
    },
    // 切换区域展开状态
    toggleSection: function toggleSection(section) {
      this.sectionExpanded[section] = !this.sectionExpanded[section];
    },
    // 切换AI启用状态
    toggleAI: function toggleAI(ai, event) {
      // 检查AI是否已登录
      if (!this.isAiLoginEnabled(ai)) {
        uni.showModal({
          title: '提示',
          content: "".concat(ai.name, "\u9700\u8981\u5148\u767B\u5F55\uFF0C\u8BF7\u524D\u5F80PC\u7AEF\u8FDB\u884C\u767B\u5F55\u540E\u518D\u4F7F\u7528"),
          showCancel: false,
          confirmText: '知道了'
        });
        return;
      }
      ai.enabled = event.detail.value;
    },
    // 切换AI能力
    toggleCapability: function toggleCapability(ai, capabilityValue) {
      // 检查AI是否已登录和启用
      if (!this.isAiLoginEnabled(ai)) {
        uni.showModal({
          title: '提示',
          content: "".concat(ai.name, "\u9700\u8981\u5148\u767B\u5F55\uFF0C\u8BF7\u524D\u5F80PC\u7AEF\u8FDB\u884C\u767B\u5F55\u540E\u518D\u4F7F\u7528"),
          showCancel: false,
          confirmText: '知道了'
        });
        return;
      }
      if (!ai.enabled) return;
      var index = ai.selectedCapabilities.indexOf(capabilityValue);
      if (index === -1) {
        ai.selectedCapabilities.push(capabilityValue);
      } else {
        ai.selectedCapabilities.splice(index, 1);
      }
    },
    // 发送提示词
    sendPrompt: function sendPrompt() {
      var _this3 = this;
      if (!this.canSend) return;
      this.screenshots = [];
      // 折叠所有区域
      this.sectionExpanded.aiConfig = false;
      this.sectionExpanded.promptInput = false;
      // this.sectionExpanded.taskStatus = false;

      this.taskStarted = true;
      this.results = []; // 清空之前的结果

      this.userInfoReq.roles = '';
      this.userInfoReq.taskId = this.generateUUID();
      this.userInfoReq.userId = this.userId;
      this.userInfoReq.corpId = this.corpId;
      this.userInfoReq.userPrompt = this.promptInput;

      // 获取启用的AI列表及其状态
      this.enabledAIs = this.aiList.filter(function (ai) {
        return ai.enabled;
      });

      // 将所有启用的AI状态设置为运行中
      this.enabledAIs.forEach(function (ai) {
        ai.status = 'running';
      });

      // 构建角色参数
      this.enabledAIs.forEach(function (ai) {
        if (ai.name === '腾讯元宝T1') {
          _this3.userInfoReq.roles = _this3.userInfoReq.roles + 'yb-hunyuan-pt,';
          if (ai.selectedCapabilities.includes("deep_thinking")) {
            _this3.userInfoReq.roles = _this3.userInfoReq.roles + 'yb-hunyuan-sdsk,';
          }
          if (ai.selectedCapabilities.includes("web_search")) {
            _this3.userInfoReq.roles = _this3.userInfoReq.roles + 'yb-hunyuan-lwss,';
          }
        }
        if (ai.name === '腾讯元宝DS') {
          _this3.userInfoReq.roles = _this3.userInfoReq.roles + 'yb-deepseek-pt,';
          if (ai.selectedCapabilities.includes("deep_thinking")) {
            _this3.userInfoReq.roles = _this3.userInfoReq.roles + 'yb-deepseek-sdsk,';
          }
          if (ai.selectedCapabilities.includes("web_search")) {
            _this3.userInfoReq.roles = _this3.userInfoReq.roles + 'yb-deepseek-lwss,';
          }
        }
        if (ai.name === 'TurboS@元器') {
          _this3.userInfoReq.roles = _this3.userInfoReq.roles + 'cube-trubos-agent,';
        }
        if (ai.name === 'TurboS长文版@元器') {
          _this3.userInfoReq.roles = _this3.userInfoReq.roles + 'cube-turbos-large-agent,';
        }
        if (ai.name === '豆包') {
          _this3.userInfoReq.roles = _this3.userInfoReq.roles + 'zj-db,';
          if (ai.selectedCapabilities.includes("deep_thinking")) {
            _this3.userInfoReq.roles = _this3.userInfoReq.roles + 'zj-db-sdsk,';
          }
        }
      });
      console.log("参数：", this.userInfoReq);

      // 滚动到任务状态区域
      this.scrollIntoView = 'task-status';

      //调用后端接口
      this.jsonRpcReqest.id = this.generateUUID();
      this.jsonRpcReqest.method = "使用F8S";
      this.jsonRpcReqest.params = this.userInfoReq;
      this.message(this.jsonRpcReqest);
      this.userInfoReq.isNewChat = false;
      uni.showToast({
        title: '任务已提交',
        icon: 'success'
      });
    },
    // WebSocket相关方法
    initWebSocket: function initWebSocket() {
      var _this4 = this;
      // 检查用户信息是否完整
      if (!this.userId || !this.corpId) {
        console.log('用户信息不完整，跳转到登录页面');
        uni.showModal({
          title: '提示',
          content: '请先登录后再使用',
          showCancel: false,
          confirmText: '去登录',
          success: function success() {
            uni.navigateTo({
              url: '/pages/login/index'
            });
          }
        });
        return;
      }
      if (this.isConnecting) {
        console.log('WebSocket正在连接中，跳过重复连接');
        return;
      }
      this.isConnecting = true;

      // 使用PC端的WebSocket连接方式
      // const wsUrl = `${process.env.VUE_APP_WS_API || 'wss://u3w.com/cubeServer/websocket?clientId='}mypc-${this.userId}`;
      var wsUrl = "".concat(Object({"NODE_ENV":"development","VUE_APP_DARK_MODE":"false","VUE_APP_NAME":"U3W-AI","VUE_APP_PLATFORM":"mp-weixin","BASE_URL":"/"}).VUE_APP_WS_API || 'ws://127.0.0.1:8081/websocket?clientId=', "mypc-").concat(this.userId);
      console.log('WebSocket URL:', wsUrl);
      this.socketTask = uni.connectSocket({
        url: wsUrl,
        success: function success() {
          console.log('WebSocket连接成功');
        },
        fail: function fail(err) {
          console.error('WebSocket连接失败', err);
          _this4.isConnecting = false;
          _this4.handleReconnect();
        }
      });
      this.socketTask.onOpen(function () {
        console.log('WebSocket连接已打开');
        _this4.isConnecting = false;
        _this4.reconnectCount = 0; // 重置重连次数

        uni.showToast({
          title: '连接成功',
          icon: 'success',
          duration: 1000
        });

        // 开始心跳检测
        _this4.startHeartbeat();
      });
      this.socketTask.onMessage(function (res) {
        _this4.handleWebSocketMessage(res.data);
      });
      this.socketTask.onError(function (err) {
        console.error('WebSocket连接错误', err);
        _this4.isConnecting = false;
        uni.showToast({
          title: 'WebSocket连接错误',
          icon: 'none'
        });
        _this4.handleReconnect();
      });
      this.socketTask.onClose(function () {
        console.log('WebSocket连接已关闭');
        _this4.isConnecting = false;
        _this4.stopHeartbeat(); // 停止心跳

        uni.showToast({
          title: 'WebSocket连接已关闭',
          icon: 'none'
        });

        // 尝试重连
        _this4.handleReconnect();
      });
    },
    // 处理重连
    handleReconnect: function handleReconnect() {
      var _this5 = this;
      if (this.reconnectCount >= this.maxReconnectCount) {
        console.log('WebSocket重连次数已达上限');
        uni.showModal({
          title: '连接失败',
          content: '网络连接不稳定，请检查网络后手动刷新页面',
          showCancel: false,
          confirmText: '知道了'
        });
        return;
      }
      this.reconnectCount++;
      var delay = Math.min(1000 * Math.pow(2, this.reconnectCount), 30000); // 指数退避，最大30秒

      console.log("WebSocket\u5C06\u5728".concat(delay, "ms\u540E\u8FDB\u884C\u7B2C").concat(this.reconnectCount, "\u6B21\u91CD\u8FDE"));
      this.reconnectTimer = setTimeout(function () {
        console.log("\u5F00\u59CB\u7B2C".concat(_this5.reconnectCount, "\u6B21\u91CD\u8FDE"));
        _this5.initWebSocket();
      }, delay);
    },
    // 开始心跳检测
    startHeartbeat: function startHeartbeat() {
      var _this6 = this;
      this.stopHeartbeat(); // 先停止之前的心跳

      this.heartbeatTimer = setInterval(function () {
        if (_this6.socketTask) {
          _this6.sendWebSocketMessage({
            type: 'HEARTBEAT',
            timestamp: Date.now()
          });
        }
      }, 30000); // 每30秒发送一次心跳
    },
    // 停止心跳检测
    stopHeartbeat: function stopHeartbeat() {
      if (this.heartbeatTimer) {
        clearInterval(this.heartbeatTimer);
        this.heartbeatTimer = null;
      }
    },
    sendWebSocketMessage: function sendWebSocketMessage(data) {
      if (this.socketTask) {
        this.socketTask.send({
          data: JSON.stringify(data)
        });
      } else {
        console.warn('WebSocket未连接，无法发送消息');
      }
    },
    // 调用后端message接口
    message: function message(data) {
      (0, _aigc.message)(data).then(function (res) {
        if (res.code == 201) {
          uni.showToast({
            title: res.messages,
            icon: 'none',
            duration: 1500
          });
        }
      });
    },
    closeWebSocket: function closeWebSocket() {
      // 清理重连定时器
      if (this.reconnectTimer) {
        clearTimeout(this.reconnectTimer);
        this.reconnectTimer = null;
      }

      // 停止心跳检测
      this.stopHeartbeat();

      // 关闭WebSocket连接
      if (this.socketTask) {
        this.socketTask.close();
        this.socketTask = null;
      }

      // 重置状态
      this.isConnecting = false;
      this.reconnectCount = 0;
    },
    // 处理WebSocket消息
    handleWebSocketMessage: function handleWebSocketMessage(data) {
      try {
        var datastr = data;
        var dataObj = JSON.parse(datastr);

        // 忽略心跳响应
        if (dataObj.type === 'HEARTBEAT_RESPONSE' || dataObj.type === 'HEARTBEAT') {
          return;
        }

        // 处理chatId消息
        if (dataObj.type === 'RETURN_YBT1_CHATID' && dataObj.chatId) {
          this.userInfoReq.toneChatId = dataObj.chatId;
        } else if (dataObj.type === 'RETURN_YBDS_CHATID' && dataObj.chatId) {
          this.userInfoReq.ybDsChatId = dataObj.chatId;
        } else if (dataObj.type === 'RETURN_DB_CHATID' && dataObj.chatId) {
          this.userInfoReq.dbChatId = dataObj.chatId;
        }

        // 处理进度日志消息
        if (dataObj.type === 'RETURN_PC_TASK_LOG' && dataObj.aiName) {
          var targetAI = this.enabledAIs.find(function (ai) {
            return ai.name === dataObj.aiName;
          });
          if (targetAI) {
            // 将新进度添加到数组开头
            targetAI.progressLogs.unshift({
              content: dataObj.content,
              timestamp: new Date(),
              isCompleted: false
            });
          }
          return;
        }

        // 处理截图消息
        if (dataObj.type === 'RETURN_PC_TASK_IMG' && dataObj.url) {
          // 将新的截图添加到数组开头
          this.screenshots.unshift(dataObj.url);
          return;
        }

        // 处理智能评分结果
        if (dataObj.type === 'RETURN_WKPF_RES') {
          var wkpfAI = this.enabledAIs.find(function (ai) {
            return ai.name === '智能评分';
          });
          if (wkpfAI) {
            wkpfAI.status = 'completed';
            if (wkpfAI.progressLogs.length > 0) {
              wkpfAI.progressLogs[0].isCompleted = true;
            }
            // 添加评分结果到results最前面
            this.results.unshift({
              aiName: '智能评分',
              content: dataObj.draftContent,
              shareUrl: dataObj.shareUrl || '',
              shareImgUrl: dataObj.shareImgUrl || '',
              timestamp: new Date()
            });
            this.activeResultIndex = 0;

            // 折叠所有区域当智能评分完成时
            this.sectionExpanded.aiConfig = false;
            this.sectionExpanded.promptInput = false;
            this.sectionExpanded.taskStatus = false;

            // 智能评分完成时，再次保存历史记录
            this.saveHistory();
          }
          return;
        }

        // 处理智能排版结果
        if (dataObj.type === 'RETURN_ZNPB_RES') {
          console.log("收到智能排版结果", dataObj);
          console.log("当前 currentLayoutResult:", this.currentLayoutResult);
          var znpbAI = this.enabledAIs.find(function (ai) {
            return ai.name === '智能排版';
          });
          if (znpbAI) {
            znpbAI.status = 'completed';
            if (znpbAI.progressLogs.length > 0) {
              znpbAI.progressLogs[0].isCompleted = true;
            }

            // 不添加到结果展示，直接调用推送方法
            this.handlePushToWechat(dataObj.draftContent);

            // 智能排版完成时，保存历史记录
            this.saveHistory();
          }
          return;
        }

        // 处理AI登录状态消息
        this.handleAiStatusMessage(datastr, dataObj);

        // 处理AI结果
        this.handleAIResult(dataObj);
      } catch (error) {
        console.error('WebSocket消息处理错误', error);
      }
    },
    handleAiStatusMessage: function handleAiStatusMessage(datastr, dataObj) {
      // 处理腾讯元宝登录状态
      if (datastr.includes("RETURN_YB_STATUS") && dataObj.status != '') {
        this.isLoading.yuanbao = false;
        if (!datastr.includes("false")) {
          this.aiLoginStatus.yuanbao = true;
          this.accounts.yuanbao = dataObj.status;
        } else {
          this.aiLoginStatus.yuanbao = false;
          // 禁用相关AI
          this.disableAIsByLoginStatus('yuanbao');
        }
        // 更新AI启用状态
        this.updateAiEnabledStatus();
      }
      // 处理豆包登录状态
      else if (datastr.includes("RETURN_DB_STATUS") && dataObj.status != '') {
        this.isLoading.doubao = false;
        if (!datastr.includes("false")) {
          this.aiLoginStatus.doubao = true;
          this.accounts.doubao = dataObj.status;
        } else {
          this.aiLoginStatus.doubao = false;
          // 禁用相关AI
          this.disableAIsByLoginStatus('doubao');
        }
        // 更新AI启用状态
        this.updateAiEnabledStatus();
      }
      // 处理智能体登录状态
      else if (datastr.includes("RETURN_AGENT_STATUS") && dataObj.status != '') {
        this.isLoading.agent = false;
        if (!datastr.includes("false")) {
          this.aiLoginStatus.agent = true;
          this.accounts.agent = dataObj.status;
        } else {
          this.aiLoginStatus.agent = false;
          // 禁用相关AI
          this.disableAIsByLoginStatus('agent');
        }
        // 更新AI启用状态
        this.updateAiEnabledStatus();
      }
    },
    handleAIResult: function handleAIResult(dataObj) {
      var targetAI = null;

      // 根据消息类型匹配AI
      switch (dataObj.type) {
        case 'RETURN_YBT1_RES':
          console.log('收到消息:', dataObj);
          targetAI = this.enabledAIs.find(function (ai) {
            return ai.name === '腾讯元宝T1';
          });
          break;
        case 'RETURN_YBDS_RES':
          console.log('收到消息:', dataObj);
          targetAI = this.enabledAIs.find(function (ai) {
            return ai.name === '腾讯元宝DS';
          });
          break;
        case 'RETURN_DB_RES':
          console.log('收到消息:', dataObj);
          targetAI = this.enabledAIs.find(function (ai) {
            return ai.name === '豆包';
          });
          break;
        case 'RETURN_TURBOS_RES':
          console.log('收到消息:', dataObj);
          targetAI = this.enabledAIs.find(function (ai) {
            return ai.name === 'TurboS@元器';
          });
          break;
        case 'RETURN_TURBOS_LARGE_RES':
          console.log('收到消息:', dataObj);
          targetAI = this.enabledAIs.find(function (ai) {
            return ai.name === 'TurboS长文版@元器';
          });
          break;
      }
      if (targetAI) {
        // 更新AI状态为已完成
        targetAI.status = 'completed';

        // 将最后一条进度消息标记为已完成
        if (targetAI.progressLogs.length > 0) {
          targetAI.progressLogs[0].isCompleted = true;
        }

        // 添加结果到数组开头
        var resultIndex = this.results.findIndex(function (r) {
          return r.aiName === targetAI.name;
        });
        if (resultIndex === -1) {
          this.results.unshift({
            aiName: targetAI.name,
            content: dataObj.draftContent,
            shareUrl: dataObj.shareUrl || '',
            shareImgUrl: dataObj.shareImgUrl || '',
            timestamp: new Date()
          });
          this.activeResultIndex = 0;
        } else {
          this.results.splice(resultIndex, 1);
          this.results.unshift({
            aiName: targetAI.name,
            content: dataObj.draftContent,
            shareUrl: dataObj.shareUrl || '',
            shareImgUrl: dataObj.shareImgUrl || '',
            timestamp: new Date()
          });
          this.activeResultIndex = 0;
        }

        // 折叠所有区域当有结果返回时
        this.sectionExpanded.aiConfig = false;
        this.sectionExpanded.promptInput = false;
        this.sectionExpanded.taskStatus = false;

        // 滚动到结果区域
        this.scrollIntoView = 'results';

        // 保存历史记录
        this.saveHistory();
      }
    },
    // 状态相关方法
    getStatusText: function getStatusText(status) {
      var statusMap = {
        'idle': '等待中',
        'running': '正在执行',
        'completed': '已完成',
        'failed': '执行失败'
      };
      return statusMap[status] || '未知状态';
    },
    getStatusIconClass: function getStatusIconClass(status) {
      var classMap = {
        'idle': 'status-idle',
        'running': 'status-running',
        'completed': 'status-completed',
        'failed': 'status-failed'
      };
      return classMap[status] || 'status-unknown';
    },
    getStatusEmoji: function getStatusEmoji(status) {
      var emojiMap = {
        'idle': '⏳',
        'running': '🔄',
        'completed': '✅',
        'failed': '❌'
      };
      return emojiMap[status] || '❓';
    },
    // 切换任务展开状态
    toggleTaskExpansion: function toggleTaskExpansion(ai) {
      ai.isExpanded = !ai.isExpanded;
    },
    // 切换自动播放
    toggleAutoPlay: function toggleAutoPlay(event) {
      this.autoPlay = event.detail.value;
    },
    // 预览图片
    previewImage: function previewImage(url) {
      uni.previewImage({
        current: url,
        urls: [url]
      });
    },
    // 结果相关方法
    switchResultTab: function switchResultTab(index) {
      this.activeResultIndex = index;
    },
    renderMarkdown: function renderMarkdown(text) {
      try {
        return (0, _marked.marked)(text);
      } catch (error) {
        return text;
      }
    },
    isImageFile: function isImageFile(url) {
      if (!url) return false;
      var imageExtensions = ['.jpg', '.jpeg', '.png', '.gif', '.bmp', '.webp', '.svg'];
      var urlLower = url.toLowerCase();
      return imageExtensions.some(function (ext) {
        return urlLower.includes(ext);
      });
    },
    // 判断是否为PDF文件
    isPdfFile: function isPdfFile(url) {
      if (!url) return false;
      return url.toLowerCase().includes('.pdf');
    },
    copyResult: function copyResult(content) {
      uni.setClipboardData({
        data: content,
        success: function success() {
          uni.showToast({
            title: '已复制到剪贴板',
            icon: 'success'
          });
        }
      });
    },
    // shareResult(result) {
    // 	uni.share({
    // 		provider: 'weixin',
    // 		scene: 'WXSceneSession',
    // 		type: 0,
    // 		title: `${result.aiName}的执行结果`,
    // 		summary: result.content.substring(0, 100),
    // 		success: () => {
    // 			uni.showToast({
    // 				title: '分享成功',
    // 				icon: 'success'
    // 			});
    // 		}
    // 	});
    // },
    exportResult: function exportResult(result) {
      // 小程序环境下的导出功能可以通过分享或复制实现
      this.copyResult(result.content);
    },
    openShareUrl: function openShareUrl(url) {
      uni.setClipboardData({
        data: url,
        success: function success() {
          uni.showToast({
            title: '原链接已复制',
            icon: 'success'
          });
        },
        fail: function fail() {
          uni.showToast({
            title: '复制失败',
            icon: 'none'
          });
        }
      });
    },
    // 复制PDF链接
    copyPdfUrl: function copyPdfUrl(url) {
      uni.setClipboardData({
        data: url,
        success: function success() {
          uni.showToast({
            title: 'PDF链接已复制',
            icon: 'success'
          });
        },
        fail: function fail() {
          uni.showToast({
            title: '复制失败',
            icon: 'none'
          });
        }
      });
    },
    // 打开PDF文件
    openPdfFile: function openPdfFile(url) {
      uni.showLoading({
        title: '正在下载PDF...'
      });

      // 尝试下载并打开文件
      uni.downloadFile({
        url: url,
        success: function success(res) {
          uni.hideLoading();
          if (res.statusCode === 200) {
            // 打开文件
            uni.openDocument({
              filePath: res.tempFilePath,
              success: function success() {
                uni.showToast({
                  title: 'PDF已打开',
                  icon: 'success'
                });
              },
              fail: function fail() {
                // 如果无法打开，提示并复制链接
                uni.showModal({
                  title: '提示',
                  content: '无法在当前环境打开PDF文件，已复制链接到剪贴板，请在浏览器中打开',
                  showCancel: false,
                  success: function success() {
                    uni.setClipboardData({
                      data: url
                    });
                  }
                });
              }
            });
          } else {
            uni.showToast({
              title: '下载失败',
              icon: 'none'
            });
          }
        },
        fail: function fail() {
          uni.hideLoading();
          // 下载失败，提示并复制链接
          uni.showModal({
            title: '提示',
            content: '下载失败，已复制PDF链接到剪贴板，请在浏览器中打开',
            showCancel: false,
            success: function success() {
              uni.setClipboardData({
                data: url
              });
            }
          });
        }
      });
    },
    // 历史记录相关方法
    showHistoryDrawer: function showHistoryDrawer() {
      this.historyDrawerVisible = true;
      this.loadChatHistory(1);
    },
    closeHistoryDrawer: function closeHistoryDrawer() {
      this.historyDrawerVisible = false;
    },
    loadChatHistory: function loadChatHistory(isAll) {
      var _this7 = this;
      return (0, _asyncToGenerator2.default)( /*#__PURE__*/_regenerator.default.mark(function _callee() {
        var res;
        return _regenerator.default.wrap(function _callee$(_context) {
          while (1) {
            switch (_context.prev = _context.next) {
              case 0:
                _context.prev = 0;
                _context.next = 3;
                return (0, _aigc.getChatHistory)(_this7.userId, isAll);
              case 3:
                res = _context.sent;
                if (res.code === 200) {
                  _this7.chatHistory = res.data || [];
                }
                _context.next = 11;
                break;
              case 7:
                _context.prev = 7;
                _context.t0 = _context["catch"](0);
                console.error('加载历史记录失败:', _context.t0);
                uni.showToast({
                  title: '加载历史记录失败',
                  icon: 'none'
                });
              case 11:
              case "end":
                return _context.stop();
            }
          }
        }, _callee, null, [[0, 7]]);
      }))();
    },
    loadHistoryItem: function loadHistoryItem(item) {
      try {
        var historyData = JSON.parse(item.data);
        // 恢复AI选择配置
        this.aiList = historyData.aiList || this.aiList;
        // 恢复提示词输入
        this.promptInput = historyData.promptInput || item.userPrompt;
        // 恢复任务流程
        this.enabledAIs = historyData.enabledAIs || [];
        // 恢复主机可视化
        this.screenshots = historyData.screenshots || [];
        // 恢复执行结果
        this.results = historyData.results || [];
        // 恢复chatId
        this.chatId = item.chatId || this.chatId;
        this.userInfoReq.toneChatId = item.toneChatId || '';
        this.userInfoReq.ybDsChatId = item.ybDsChatId || '';
        this.userInfoReq.dbChatId = item.dbChatId || '';
        this.userInfoReq.isNewChat = false;

        // 不再根据AI登录状态更新AI启用状态，保持原有选择

        // 展开相关区域
        this.sectionExpanded.aiConfig = true;
        this.sectionExpanded.promptInput = true;
        this.sectionExpanded.taskStatus = true;
        this.taskStarted = true;
        this.closeHistoryDrawer();
        uni.showToast({
          title: '历史记录加载成功',
          icon: 'success'
        });
      } catch (error) {
        console.error('加载历史记录失败:', error);
        uni.showToast({
          title: '加载失败',
          icon: 'none'
        });
      }
    },
    // 加载上次会话
    loadLastChat: function loadLastChat() {
      var _this8 = this;
      return (0, _asyncToGenerator2.default)( /*#__PURE__*/_regenerator.default.mark(function _callee2() {
        var res, lastChat;
        return _regenerator.default.wrap(function _callee2$(_context2) {
          while (1) {
            switch (_context2.prev = _context2.next) {
              case 0:
                _context2.prev = 0;
                _context2.next = 3;
                return (0, _aigc.getChatHistory)(_this8.userId, 0);
              case 3:
                res = _context2.sent;
                if (res.code === 200 && res.data && res.data.length > 0) {
                  // 获取最新的会话记录
                  lastChat = res.data[0];
                  _this8.loadHistoryItem(lastChat);
                }
                _context2.next = 10;
                break;
              case 7:
                _context2.prev = 7;
                _context2.t0 = _context2["catch"](0);
                console.error('加载上次会话失败:', _context2.t0);
              case 10:
              case "end":
                return _context2.stop();
            }
          }
        }, _callee2, null, [[0, 7]]);
      }))();
    },
    saveHistory: function saveHistory() {
      var _this9 = this;
      return (0, _asyncToGenerator2.default)( /*#__PURE__*/_regenerator.default.mark(function _callee3() {
        var historyData;
        return _regenerator.default.wrap(function _callee3$(_context3) {
          while (1) {
            switch (_context3.prev = _context3.next) {
              case 0:
                historyData = {
                  aiList: _this9.aiList,
                  promptInput: _this9.promptInput,
                  enabledAIs: _this9.enabledAIs,
                  screenshots: _this9.screenshots,
                  results: _this9.results,
                  chatId: _this9.chatId,
                  toneChatId: _this9.userInfoReq.toneChatId,
                  ybDsChatId: _this9.userInfoReq.ybDsChatId,
                  dbChatId: _this9.userInfoReq.dbChatId
                };
                _context3.prev = 1;
                _context3.next = 4;
                return (0, _aigc.saveUserChatData)({
                  userId: _this9.userId,
                  userPrompt: _this9.promptInput,
                  data: JSON.stringify(historyData),
                  chatId: _this9.chatId,
                  toneChatId: _this9.userInfoReq.toneChatId,
                  ybDsChatId: _this9.userInfoReq.ybDsChatId,
                  dbChatId: _this9.userInfoReq.dbChatId
                });
              case 4:
                _context3.next = 10;
                break;
              case 6:
                _context3.prev = 6;
                _context3.t0 = _context3["catch"](1);
                console.error('保存历史记录失败:', _context3.t0);
                uni.showToast({
                  title: '保存历史记录失败',
                  icon: 'none'
                });
              case 10:
              case "end":
                return _context3.stop();
            }
          }
        }, _callee3, null, [[1, 6]]);
      }))();
    },
    getHistoryDate: function getHistoryDate(timestamp) {
      try {
        console.log('getHistoryDate 输入:', timestamp, (0, _typeof2.default)(timestamp));
        if (!timestamp) {
          return '未知日期';
        }
        var date;
        if (typeof timestamp === 'number') {
          date = new Date(timestamp);
        } else if (typeof timestamp === 'string') {
          // 处理 "2025-6-23 14:53:12" 这种格式
          var match = timestamp.match(/(\d{4})-(\d{1,2})-(\d{1,2})\s+(\d{1,2}):(\d{1,2}):(\d{1,2})/);
          if (match) {
            var _match = (0, _slicedToArray2.default)(match, 7),
              year = _match[1],
              month = _match[2],
              day = _match[3],
              hour = _match[4],
              minute = _match[5],
              second = _match[6];
            date = new Date(parseInt(year), parseInt(month) - 1, parseInt(day), parseInt(hour), parseInt(minute), parseInt(second));
          } else {
            // 如果正则不匹配，尝试其他方式
            var fixedTimestamp = timestamp.replace(/\s/g, 'T');
            date = new Date(fixedTimestamp);
            if (isNaN(date.getTime())) {
              date = new Date(timestamp);
            }
          }
        } else {
          date = new Date(timestamp);
        }
        console.log('getHistoryDate 解析结果:', date, date.getTime());
        if (isNaN(date.getTime())) {
          return '未知日期';
        }
        var today = new Date();
        var yesterday = new Date(today);
        yesterday.setDate(yesterday.getDate() - 1);
        if (date.toDateString() === today.toDateString()) {
          return '今天';
        } else if (date.toDateString() === yesterday.toDateString()) {
          return '昨天';
        } else {
          return date.toLocaleDateString('zh-CN');
        }
      } catch (error) {
        console.error('格式化日期错误:', error, timestamp);
        return '未知日期';
      }
    },
    // 格式化历史记录时间
    formatHistoryTime: function formatHistoryTime(timestamp) {
      try {
        console.log('formatHistoryTime 输入:', timestamp, (0, _typeof2.default)(timestamp));
        var date;
        if (!timestamp) {
          return '时间未知';
        }

        // 如果是数字，直接创建Date对象
        if (typeof timestamp === 'number') {
          date = new Date(timestamp);
        } else if (typeof timestamp === 'string') {
          // 处理ISO 8601格式：2025-06-25T07:18:54.110Z
          if (timestamp.includes('T') && (timestamp.includes('Z') || timestamp.includes('+'))) {
            date = new Date(timestamp);
          }
          // 处理 "2025-6-26 08:46:26" 这种格式
          else {
            var match = timestamp.match(/(\d{4})-(\d{1,2})-(\d{1,2})\s+(\d{1,2}):(\d{1,2}):(\d{1,2})/);
            if (match) {
              var _match2 = (0, _slicedToArray2.default)(match, 7),
                year = _match2[1],
                month = _match2[2],
                day = _match2[3],
                _hour = _match2[4],
                _minute = _match2[5],
                second = _match2[6];
              // 注意：Date构造函数的month参数是0-11，所以要减1
              date = new Date(parseInt(year), parseInt(month) - 1, parseInt(day), parseInt(_hour), parseInt(_minute), parseInt(second));
            } else {
              // 如果正则不匹配，尝试其他方式
              var fixedTimestamp = timestamp.replace(/\s/g, 'T');
              date = new Date(fixedTimestamp);
              if (isNaN(date.getTime())) {
                date = new Date(timestamp);
              }
            }
          }
        } else if (timestamp instanceof Date) {
          date = timestamp;
        } else {
          date = new Date(timestamp);
        }
        console.log('formatHistoryTime 解析结果:', date, date.getTime());
        if (isNaN(date.getTime())) {
          return '时间未知';
        }

        // 使用更简洁的时间格式，避免显示时区信息
        var hour = date.getHours().toString().padStart(2, '0');
        var minute = date.getMinutes().toString().padStart(2, '0');
        var timeString = "".concat(hour, ":").concat(minute);
        console.log('formatHistoryTime 输出:', timeString);
        return timeString;
      } catch (error) {
        console.error('格式化时间错误:', error, timestamp);
        return '时间未知';
      }
    },
    // 修改折叠切换方法
    toggleHistoryExpansion: function toggleHistoryExpansion(item) {
      this.expandedHistoryItems[item.chatId] = !this.expandedHistoryItems[item.chatId];
      this.$forceUpdate(); // 强制更新视图
    },
    // 智能评分相关方法
    showScoreModal: function showScoreModal() {
      this.selectedResults = [];
      this.scoreModalVisible = true;
    },
    closeScoreModal: function closeScoreModal() {
      this.scoreModalVisible = false;
    },
    // 智能排版相关方法
    showLayoutModal: function showLayoutModal() {
      if (!this.currentResult) {
        uni.showToast({
          title: '没有可排版的内容',
          icon: 'none'
        });
        return;
      }
      console.log("showLayoutModal", this.currentResult);
      // 深度拷贝当前结果，避免引用被修改
      this.currentLayoutResult = {
        aiName: this.currentResult.aiName,
        content: this.currentResult.content,
        shareUrl: this.currentResult.shareUrl,
        shareImgUrl: this.currentResult.shareImgUrl,
        timestamp: this.currentResult.timestamp
      };
      console.log("showLayoutModal", this.currentLayoutResult);

      // 设置默认提示词
      this.layoutPrompt = "\u8BF7\u4F60\u5BF9\u4EE5\u4E0B HTML \u5185\u5BB9\u8FDB\u884C\u6392\u7248\u4F18\u5316\uFF0C\u76EE\u6807\u662F\u7528\u4E8E\u5FAE\u4FE1\u516C\u4F17\u53F7\u201C\u8349\u7A3F\u7BB1\u63A5\u53E3\u201D\u7684 content \u5B57\u6BB5\uFF0C\u8981\u6C42\u5982\u4E0B\uFF1A\n\n1. \u4EC5\u8FD4\u56DE <body> \u5185\u90E8\u53EF\u7528\u7684 HTML \u5185\u5BB9\u7247\u6BB5\uFF08\u4E0D\u8981\u5305\u542B <!DOCTYPE>\u3001<html>\u3001<head>\u3001<meta>\u3001<title> \u7B49\u6807\u7B7E\uFF09\u3002\n2. \u6240\u6709\u6837\u5F0F\u5FC5\u987B\u4EE5\u201C\u5185\u8054 style\u201D\u65B9\u5F0F\u5199\u5165\u3002\n3. \u4FDD\u6301\u7ED3\u6784\u6E05\u6670\u3001\u89C6\u89C9\u53CB\u597D\uFF0C\u9002\u914D\u516C\u4F17\u53F7\u56FE\u6587\u6392\u7248\u3002\n4. \u8BF7\u76F4\u63A5\u8F93\u51FA\u4EE3\u7801\uFF0C\u4E0D\u8981\u6DFB\u52A0\u4EFB\u4F55\u6CE8\u91CA\u6216\u989D\u5916\u8BF4\u660E\u3002\n5. \u4E0D\u5F97\u4F7F\u7528 emoji \u8868\u60C5\u7B26\u53F7\u6216\u5C0F\u56FE\u6807\u5B57\u7B26\u3002\n\n" + this.currentResult.content;
      this.layoutModalVisible = true;
    },
    closeLayoutModal: function closeLayoutModal() {
      this.layoutModalVisible = false;
    },
    handleLayout: function handleLayout() {
      if (this.layoutPrompt.trim().length === 0) return;

      // 构建智能排版请求
      var layoutRequest = {
        jsonrpc: '2.0',
        id: this.generateUUID(),
        method: 'AI排版',
        params: {
          taskId: this.generateUUID(),
          userId: this.userId,
          corpId: this.corpId,
          userPrompt: this.layoutPrompt,
          roles: 'zj-db-sdsk' // 默认使用豆包进行排版
        }
      };

      // 发送排版请求
      console.log("智能排版参数", layoutRequest);
      this.message(layoutRequest);
      this.closeLayoutModal();

      // 创建智能排版AI节点
      var znpbAI = {
        name: '智能排版',
        avatar: 'https://u3w.com/chatfile/%E8%B1%86%E5%8C%85.png',
        capabilities: [],
        selectedCapabilities: [],
        enabled: true,
        status: 'running',
        progressLogs: [{
          content: '智能排版任务已提交，正在排版...',
          timestamp: new Date(),
          isCompleted: false,
          type: '智能排版'
        }],
        isExpanded: true
      };

      // 检查是否已存在智能排版
      var existIndex = this.enabledAIs.findIndex(function (ai) {
        return ai.name === '智能排版';
      });
      if (existIndex === -1) {
        // 如果不存在，添加到数组开头
        this.enabledAIs.unshift(znpbAI);
      } else {
        // 如果已存在，更新状态和日志
        this.enabledAIs[existIndex] = znpbAI;
        // 将智能排版移到数组开头
        var znpb = this.enabledAIs.splice(existIndex, 1)[0];
        this.enabledAIs.unshift(znpb);
      }
      uni.showToast({
        title: '排版请求已发送，请等待结果',
        icon: 'success'
      });
    },
    // 推送到公众号
    handlePushToWechat: function handlePushToWechat(contentText) {
      var _this10 = this;
      return (0, _asyncToGenerator2.default)( /*#__PURE__*/_regenerator.default.mark(function _callee4() {
        var params, res;
        return _regenerator.default.wrap(function _callee4$(_context4) {
          while (1) {
            switch (_context4.prev = _context4.next) {
              case 0:
                _context4.prev = 0;
                console.log("handlePushToWechat 开始执行", _this10.currentLayoutResult);
                if (_this10.currentLayoutResult) {
                  _context4.next = 6;
                  break;
                }
                console.error("currentLayoutResult 为空，无法投递");
                uni.showToast({
                  title: '投递失败：缺少原始结果信息',
                  icon: 'none'
                });
                return _context4.abrupt("return");
              case 6:
                uni.showLoading({
                  title: '正在投递...'
                });

                // 自增计数器
                _this10.collectNum++;
                params = {
                  contentText: contentText,
                  userId: _this10.userId,
                  shareUrl: _this10.currentLayoutResult.shareUrl || '',
                  aiName: _this10.currentLayoutResult.aiName || '',
                  num: _this10.collectNum
                };
                console.log("投递参数", params);
                _context4.next = 12;
                return (0, _aigc.pushAutoOffice)(params);
              case 12:
                res = _context4.sent;
                uni.hideLoading();
                if (res.code === 200) {
                  uni.showToast({
                    title: "\u6295\u9012\u6210\u529F(".concat(_this10.collectNum, ")"),
                    icon: 'success'
                  });
                } else {
                  uni.showToast({
                    title: res.message || '投递失败',
                    icon: 'none'
                  });
                }
                _context4.next = 22;
                break;
              case 17:
                _context4.prev = 17;
                _context4.t0 = _context4["catch"](0);
                uni.hideLoading();
                console.error('投递到公众号失败:', _context4.t0);
                uni.showToast({
                  title: '投递失败',
                  icon: 'none'
                });
              case 22:
              case "end":
                return _context4.stop();
            }
          }
        }, _callee4, null, [[0, 17]]);
      }))();
    },
    toggleResultSelection: function toggleResultSelection(event) {
      var values = event.detail.value;
      console.log('toggleResultSelection - 选中的values:', values);
      console.log('toggleResultSelection - 当前scorePrompt:', this.scorePrompt.trim());
      this.selectedResults = values;
      console.log('toggleResultSelection - 更新后的selectedResults:', this.selectedResults);
      console.log('toggleResultSelection - canScore状态:', this.canScore);
    },
    handleScore: function handleScore() {
      var _this11 = this;
      if (!this.canScore) return;

      // 获取选中的结果内容并按照指定格式拼接
      var selectedContents = this.results.filter(function (result) {
        return _this11.selectedResults.includes(result.aiName);
      }).map(function (result) {
        // 将HTML内容转换为纯文本（小程序版本简化处理）
        var plainContent = result.content.replace(/<[^>]*>/g, '');
        return "".concat(result.aiName, "\u521D\u7A3F\uFF1A\n").concat(plainContent, "\n");
      }).join('\n');

      // 构建完整的评分提示内容
      var fullPrompt = "".concat(this.scorePrompt, "\n").concat(selectedContents);

      // 构建评分请求
      var scoreRequest = {
        jsonrpc: '2.0',
        id: this.generateUUID(),
        method: 'AI评分',
        params: {
          taskId: this.generateUUID(),
          userId: this.userId,
          corpId: this.corpId,
          userPrompt: fullPrompt,
          roles: 'zj-db-sdsk' // 默认使用豆包进行评分
        }
      };

      // 发送评分请求
      console.log("参数", scoreRequest);
      this.message(scoreRequest);
      this.closeScoreModal();

      // 创建智能评分AI节点
      var wkpfAI = {
        name: '智能评分',
        avatar: 'https://u3w.com/chatfile/%E8%B1%86%E5%8C%85.png',
        capabilities: [],
        selectedCapabilities: [],
        enabled: true,
        status: 'running',
        progressLogs: [{
          content: '智能评分任务已提交，正在评分...',
          timestamp: new Date(),
          isCompleted: false,
          type: '智能评分'
        }],
        isExpanded: true
      };

      // 检查是否已存在智能评分
      var existIndex = this.enabledAIs.findIndex(function (ai) {
        return ai.name === '智能评分';
      });
      if (existIndex === -1) {
        // 如果不存在，添加到数组开头
        this.enabledAIs.unshift(wkpfAI);
      } else {
        // 如果已存在，更新状态和日志
        this.enabledAIs[existIndex] = wkpfAI;
        // 将智能评分移到数组开头
        var wkpf = this.enabledAIs.splice(existIndex, 1)[0];
        this.enabledAIs.unshift(wkpf);
      }
      uni.showToast({
        title: '评分请求已发送，请等待结果',
        icon: 'success'
      });
    },
    // 创建新对话
    createNewChat: function createNewChat() {
      // 重置所有数据
      this.chatId = this.generateUUID();
      this.promptInput = '';
      this.taskStarted = false;
      this.screenshots = [];
      this.results = [];
      this.enabledAIs = [];
      this.userInfoReq = {
        userPrompt: '',
        userId: this.userId,
        corpId: this.corpId,
        taskId: '',
        roles: '',
        toneChatId: '',
        ybDsChatId: '',
        dbChatId: '',
        isNewChat: true
      };
      // 重置AI列表为初始状态
      this.aiList = [{
        name: 'TurboS@元器',
        avatar: 'https://u3w.com/chatfile/yuanbao.png',
        capabilities: [],
        selectedCapabilities: [],
        enabled: true,
        status: 'idle',
        progressLogs: [],
        isExpanded: true
      }, {
        name: 'TurboS长文版@元器',
        avatar: 'https://u3w.com/chatfile/yuanbao.png',
        capabilities: [],
        selectedCapabilities: [],
        enabled: true,
        status: 'idle',
        progressLogs: [],
        isExpanded: true
      }, {
        name: '腾讯元宝T1',
        avatar: 'https://u3w.com/chatfile/yuanbao.png',
        capabilities: [{
          label: '深度思考',
          value: 'deep_thinking'
        }, {
          label: '联网搜索',
          value: 'web_search'
        }],
        selectedCapabilities: ['deep_thinking', 'web_search'],
        enabled: true,
        status: 'idle',
        progressLogs: [],
        isExpanded: true
      }, {
        name: '腾讯元宝DS',
        avatar: 'https://u3w.com/chatfile/yuanbao.png',
        capabilities: [{
          label: '深度思考',
          value: 'deep_thinking'
        }, {
          label: '联网搜索',
          value: 'web_search'
        }],
        selectedCapabilities: ['deep_thinking', 'web_search'],
        enabled: true,
        status: 'idle',
        progressLogs: [],
        isExpanded: true
      }, {
        name: '豆包',
        avatar: 'https://u3w.com/chatfile/%E8%B1%86%E5%8C%85.png',
        capabilities: [{
          label: '深度思考',
          value: 'deep_thinking'
        }],
        selectedCapabilities: ['deep_thinking'],
        enabled: true,
        status: 'idle',
        progressLogs: [],
        isExpanded: true
      }];
      // 不再根据AI登录状态更新AI启用状态，保持原有选择

      // 展开相关区域
      this.sectionExpanded.aiConfig = true;
      this.sectionExpanded.promptInput = true;
      this.sectionExpanded.taskStatus = true;
      uni.showToast({
        title: '已创建新对话',
        icon: 'success'
      });
    },
    // AI状态相关方法
    checkAiLoginStatus: function checkAiLoginStatus() {
      var _this12 = this;
      // 延迟检查，确保WebSocket连接已建立
      setTimeout(function () {
        _this12.sendAiStatusCheck();
        // 不再更新AI启用状态，保持原有选择
      }, 2000);
    },
    sendAiStatusCheck: function sendAiStatusCheck() {
      // 检查腾讯元宝登录状态
      this.sendWebSocketMessage({
        type: 'PLAY_CHECK_YB_LOGIN',
        userId: this.userId,
        corpId: this.corpId
      });

      // 检查豆包登录状态
      this.sendWebSocketMessage({
        type: 'PLAY_CHECK_DB_LOGIN',
        userId: this.userId,
        corpId: this.corpId
      });

      // 检查智能体登录状态
      this.sendWebSocketMessage({
        type: 'PLAY_CHECK_AGENT_LOGIN',
        userId: this.userId,
        corpId: this.corpId
      });
    },
    getPlatformIcon: function getPlatformIcon(type) {
      var icons = {
        yuanbao: 'https://u3w.com/chatfile/yuanbao.png',
        doubao: 'https://u3w.com/chatfile/%E8%B1%86%E5%8C%85.png',
        agent: 'https://u3w.com/chatfile/yuanbao.png'
      };
      return icons[type] || '';
    },
    getPlatformName: function getPlatformName(type) {
      var names = {
        yuanbao: '腾讯元宝',
        doubao: '豆包',
        agent: '智能体'
      };
      return names[type] || '';
    },
    refreshAiStatus: function refreshAiStatus() {
      var _this13 = this;
      // 重置所有AI状态为加载中
      this.isLoading = {
        yuanbao: true,
        doubao: true,
        agent: true
      };

      // 重置登录状态
      this.aiLoginStatus = {
        yuanbao: false,
        doubao: false,
        agent: false
      };

      // 重置账户信息
      this.accounts = {
        yuanbao: '',
        doubao: '',
        agent: ''
      };

      // 显示刷新提示
      uni.showToast({
        title: '正在刷新连接状态...',
        icon: 'loading',
        duration: 1500
      });

      // 重新建立WebSocket连接
      this.closeWebSocket();
      setTimeout(function () {
        _this13.initWebSocket();
        // 延迟检查AI状态，确保WebSocket重新连接
        setTimeout(function () {
          _this13.sendAiStatusCheck();
        }, 2000);
      }, 500);
    },
    // 判断AI是否已登录可用
    isAiLoginEnabled: function isAiLoginEnabled(ai) {
      switch (ai.name) {
        case 'TurboS@元器':
        case 'TurboS长文版@元器':
          return this.aiLoginStatus.agent;
        // 智能体登录状态
        case '腾讯元宝T1':
        case '腾讯元宝DS':
          return this.aiLoginStatus.yuanbao;
        // 腾讯元宝登录状态
        case '豆包':
          return this.aiLoginStatus.doubao;
        // 豆包登录状态
        default:
          return false;
      }
    },
    // 判断AI是否在加载状态
    isAiInLoading: function isAiInLoading(ai) {
      switch (ai.name) {
        case 'TurboS@元器':
        case 'TurboS长文版@元器':
          return this.isLoading.agent;
        case '腾讯元宝T1':
        case '腾讯元宝DS':
          return this.isLoading.yuanbao;
        case '豆包':
          return this.isLoading.doubao;
        default:
          return false;
      }
    },
    // 根据登录状态禁用相关AI（已废弃，不再修改enabled状态）
    disableAIsByLoginStatus: function disableAIsByLoginStatus(loginType) {
      // 不再修改enabled状态，只通过UI控制操作权限
      console.log("AI ".concat(loginType, " \u767B\u5F55\u72B6\u6001\u5DF2\u66F4\u65B0\uFF0C\u4F46\u4FDD\u6301\u539F\u6709\u9009\u62E9"));
    },
    // 根据当前AI登录状态更新AI启用状态（已废弃，不再修改enabled状态）
    updateAiEnabledStatus: function updateAiEnabledStatus() {
      // 不再修改enabled状态，只通过UI控制操作权限
      console.log('AI登录状态已更新，但保持原有选择');
    },
    // 格式化时间
    formatTime: function formatTime(timestamp) {
      try {
        console.log('formatTime 输入:', timestamp, (0, _typeof2.default)(timestamp));
        if (!timestamp) {
          return '时间未知';
        }
        var date;
        if (typeof timestamp === 'number') {
          date = new Date(timestamp);
        } else if (typeof timestamp === 'string') {
          // 处理ISO 8601格式：2025-06-25T07:18:54.110Z
          if (timestamp.includes('T') && (timestamp.includes('Z') || timestamp.includes('+'))) {
            date = new Date(timestamp);
          }
          // 处理 "2025-6-23 14:53:12" 这种格式
          else {
            var match = timestamp.match(/(\d{4})-(\d{1,2})-(\d{1,2})\s+(\d{1,2}):(\d{1,2}):(\d{1,2})/);
            if (match) {
              var _match3 = (0, _slicedToArray2.default)(match, 7),
                year = _match3[1],
                month = _match3[2],
                day = _match3[3],
                _hour2 = _match3[4],
                _minute2 = _match3[5],
                _second = _match3[6];
              date = new Date(parseInt(year), parseInt(month) - 1, parseInt(day), parseInt(_hour2), parseInt(_minute2), parseInt(_second));
            } else {
              // 如果正则不匹配，尝试其他方式
              var fixedTimestamp = timestamp.replace(/\s/g, 'T');
              date = new Date(fixedTimestamp);
              if (isNaN(date.getTime())) {
                date = new Date(timestamp);
              }
            }
          }
        } else if (timestamp instanceof Date) {
          date = timestamp;
        } else {
          date = new Date(timestamp);
        }
        console.log('formatTime 解析结果:', date, date.getTime());
        if (isNaN(date.getTime())) {
          return '时间未知';
        }

        // 使用更简洁的时间格式，避免显示时区信息
        var hour = date.getHours().toString().padStart(2, '0');
        var minute = date.getMinutes().toString().padStart(2, '0');
        var second = date.getSeconds().toString().padStart(2, '0');
        var timeString = "".concat(hour, ":").concat(minute, ":").concat(second);
        console.log('formatTime 输出:', timeString);
        return timeString;
      } catch (error) {
        console.error('格式化时间错误:', error, timestamp);
        return '时间未知';
      }
    }
  }
};
exports.default = _default;
/* WEBPACK VAR INJECTION */}.call(this, __webpack_require__(/*! ./node_modules/@dcloudio/uni-mp-weixin/dist/index.js */ 2)["default"]))

/***/ }),

/***/ 83:
/*!*******************************************************************************************************************************!*\
  !*** /Users/workspace/AGI/u3w/U3W-AI/cube-mini/pages/work/index.vue?vue&type=style&index=0&id=51b5538d&scoped=true&lang=css& ***!
  \*******************************************************************************************************************************/
/*! no static exports found */
/***/ (function(module, __webpack_exports__, __webpack_require__) {

"use strict";
__webpack_require__.r(__webpack_exports__);
/* harmony import */ var _Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_mini_css_extract_plugin_dist_loader_js_ref_6_oneOf_1_0_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_css_loader_dist_cjs_js_ref_6_oneOf_1_1_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_vue_loader_lib_loaders_stylePostLoader_js_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_webpack_preprocess_loader_index_js_ref_6_oneOf_1_2_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_postcss_loader_src_index_js_ref_6_oneOf_1_3_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_vue_loader_lib_index_js_vue_loader_options_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_webpack_uni_mp_loader_lib_style_js_index_vue_vue_type_style_index_0_id_51b5538d_scoped_true_lang_css___WEBPACK_IMPORTED_MODULE_0__ = __webpack_require__(/*! -!../../../../../../../../Applications/HBuilderX.app/Contents/HBuilderX/plugins/uniapp-cli/node_modules/mini-css-extract-plugin/dist/loader.js??ref--6-oneOf-1-0!../../../../../../../../Applications/HBuilderX.app/Contents/HBuilderX/plugins/uniapp-cli/node_modules/css-loader/dist/cjs.js??ref--6-oneOf-1-1!../../../../../../../../Applications/HBuilderX.app/Contents/HBuilderX/plugins/uniapp-cli/node_modules/@dcloudio/vue-cli-plugin-uni/packages/vue-loader/lib/loaders/stylePostLoader.js!../../../../../../../../Applications/HBuilderX.app/Contents/HBuilderX/plugins/uniapp-cli/node_modules/@dcloudio/vue-cli-plugin-uni/packages/webpack-preprocess-loader??ref--6-oneOf-1-2!../../../../../../../../Applications/HBuilderX.app/Contents/HBuilderX/plugins/uniapp-cli/node_modules/postcss-loader/src??ref--6-oneOf-1-3!../../../../../../../../Applications/HBuilderX.app/Contents/HBuilderX/plugins/uniapp-cli/node_modules/@dcloudio/vue-cli-plugin-uni/packages/vue-loader/lib??vue-loader-options!../../../../../../../../Applications/HBuilderX.app/Contents/HBuilderX/plugins/uniapp-cli/node_modules/@dcloudio/webpack-uni-mp-loader/lib/style.js!./index.vue?vue&type=style&index=0&id=51b5538d&scoped=true&lang=css& */ 84);
/* harmony import */ var _Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_mini_css_extract_plugin_dist_loader_js_ref_6_oneOf_1_0_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_css_loader_dist_cjs_js_ref_6_oneOf_1_1_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_vue_loader_lib_loaders_stylePostLoader_js_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_webpack_preprocess_loader_index_js_ref_6_oneOf_1_2_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_postcss_loader_src_index_js_ref_6_oneOf_1_3_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_vue_loader_lib_index_js_vue_loader_options_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_webpack_uni_mp_loader_lib_style_js_index_vue_vue_type_style_index_0_id_51b5538d_scoped_true_lang_css___WEBPACK_IMPORTED_MODULE_0___default = /*#__PURE__*/__webpack_require__.n(_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_mini_css_extract_plugin_dist_loader_js_ref_6_oneOf_1_0_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_css_loader_dist_cjs_js_ref_6_oneOf_1_1_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_vue_loader_lib_loaders_stylePostLoader_js_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_webpack_preprocess_loader_index_js_ref_6_oneOf_1_2_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_postcss_loader_src_index_js_ref_6_oneOf_1_3_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_vue_loader_lib_index_js_vue_loader_options_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_webpack_uni_mp_loader_lib_style_js_index_vue_vue_type_style_index_0_id_51b5538d_scoped_true_lang_css___WEBPACK_IMPORTED_MODULE_0__);
/* harmony reexport (unknown) */ for(var __WEBPACK_IMPORT_KEY__ in _Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_mini_css_extract_plugin_dist_loader_js_ref_6_oneOf_1_0_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_css_loader_dist_cjs_js_ref_6_oneOf_1_1_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_vue_loader_lib_loaders_stylePostLoader_js_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_webpack_preprocess_loader_index_js_ref_6_oneOf_1_2_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_postcss_loader_src_index_js_ref_6_oneOf_1_3_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_vue_loader_lib_index_js_vue_loader_options_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_webpack_uni_mp_loader_lib_style_js_index_vue_vue_type_style_index_0_id_51b5538d_scoped_true_lang_css___WEBPACK_IMPORTED_MODULE_0__) if(["default"].indexOf(__WEBPACK_IMPORT_KEY__) < 0) (function(key) { __webpack_require__.d(__webpack_exports__, key, function() { return _Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_mini_css_extract_plugin_dist_loader_js_ref_6_oneOf_1_0_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_css_loader_dist_cjs_js_ref_6_oneOf_1_1_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_vue_loader_lib_loaders_stylePostLoader_js_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_webpack_preprocess_loader_index_js_ref_6_oneOf_1_2_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_postcss_loader_src_index_js_ref_6_oneOf_1_3_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_vue_loader_lib_index_js_vue_loader_options_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_webpack_uni_mp_loader_lib_style_js_index_vue_vue_type_style_index_0_id_51b5538d_scoped_true_lang_css___WEBPACK_IMPORTED_MODULE_0__[key]; }) }(__WEBPACK_IMPORT_KEY__));
 /* harmony default export */ __webpack_exports__["default"] = (_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_mini_css_extract_plugin_dist_loader_js_ref_6_oneOf_1_0_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_css_loader_dist_cjs_js_ref_6_oneOf_1_1_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_vue_loader_lib_loaders_stylePostLoader_js_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_webpack_preprocess_loader_index_js_ref_6_oneOf_1_2_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_postcss_loader_src_index_js_ref_6_oneOf_1_3_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_vue_cli_plugin_uni_packages_vue_loader_lib_index_js_vue_loader_options_Applications_HBuilderX_app_Contents_HBuilderX_plugins_uniapp_cli_node_modules_dcloudio_webpack_uni_mp_loader_lib_style_js_index_vue_vue_type_style_index_0_id_51b5538d_scoped_true_lang_css___WEBPACK_IMPORTED_MODULE_0___default.a); 

/***/ }),

/***/ 84:
/*!***********************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************!*\
  !*** ./node_modules/mini-css-extract-plugin/dist/loader.js??ref--6-oneOf-1-0!./node_modules/css-loader/dist/cjs.js??ref--6-oneOf-1-1!./node_modules/@dcloudio/vue-cli-plugin-uni/packages/vue-loader/lib/loaders/stylePostLoader.js!./node_modules/@dcloudio/vue-cli-plugin-uni/packages/webpack-preprocess-loader??ref--6-oneOf-1-2!./node_modules/postcss-loader/src??ref--6-oneOf-1-3!./node_modules/@dcloudio/vue-cli-plugin-uni/packages/vue-loader/lib??vue-loader-options!./node_modules/@dcloudio/webpack-uni-mp-loader/lib/style.js!/Users/workspace/AGI/u3w/U3W-AI/cube-mini/pages/work/index.vue?vue&type=style&index=0&id=51b5538d&scoped=true&lang=css& ***!
  \***********************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************/
/*! no static exports found */
/***/ (function(module, exports, __webpack_require__) {

// extracted by mini-css-extract-plugin
    if(false) { var cssReload; }
  

/***/ })

},[[66,"common/runtime","common/vendor"]]]);
//# sourceMappingURL=../../../.sourcemap/mp-weixin/pages/work/index.js.map