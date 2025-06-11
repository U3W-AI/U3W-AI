module.exports = {
  purge: ['./src/**/*.{html,js,vue}'],  // 配置要扫描的文件
  content: [],
  theme: {
    spacing: Array.from({ length: 1000 }).reduce((map, _, index) => {
      map[index] = `${index}px`;
      return map;
    }, {}),
    extend: {},
  },
  plugins: [],
  corePlugins: {
    preflight: false,
  }
};
