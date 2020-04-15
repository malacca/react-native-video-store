# react-native-video-store

react-native 视频缓存组件，当前仅支持 android， 使用的是 [AndroidVideoCache](https://github.com/danikula/AndroidVideoCache)。支持视频播放后缓存，也支持视频预加载，但由于该组件不支持分片缓存，所以对于大视频边下载边播放并不是十分理想，比较适合短视频，后面会尝试进行优化。

# 安装

`yarn add react-native-video-store`


# 使用

```js
import vStore from 'react-native-video-store';

// 配置函数
vStore
  .setMaxSize(1024 * 200)  // 设置最大缓存空间(单位: KB)
  .setMaxFiles(500)   // 设置最大缓存文件数, 与缓存空间设置互斥, 二选一
  .withSystemUA(true) // 请求 video url 时是否使用 webivew ua

  // 设置其他自定义请求 header
  .setDefaultHeaders(
    {key:value,...},  //自定义 headers
    true   //若请求 url 发生301跳转, 是否仍然应用自定义的header
  )

  // 仅某个指定 host 的请求 header
  .setDomainHeaders('.xx.com', {}, true)  


// 获取 video 的 local url, 会返回 127.0.0.1 的本地址
// 若 video 已缓存, 也可返回 file:// 形式的本地文件地址
vStore.getProxyUrl(

  // 一般情况下, 缓存文件名是由 url 确定的, 但实际使用中, 
  // video url 可能包含 token, 会发生变动, 这也就造成重复缓存的问题
  // 所以这里可使用 videoId 作为缓存文件的唯一标识; 若可以保证 url 唯一, 
  // 也可以直接使用 url 作为标识, videoId 参数设置为 null 即可
  videoId,   
  
  // video 的 url
  url, 

  // 是否允许返回 file:// 格式的地址
  allowFileUri

).then(url => {})



// 预加载, videoId/url 参数参见 getProxyUrl 说明
// size: 设置预加载大小, 单位:KB，若不设置或为0, 则全量缓存
const prs = vStore.preload(videoId, url, size);

// 如有需要, 可取消预加载
psr.stop();

// 如有需要, 可获取预加载结果
psr.result().then(r => {
  // r = "preloaded" | "cached" | "canceled"
})



// 取消所有预加载
vStore.clearPreload();


// 获取当前缓存文件占用空间大小 (单位: KB)
vStore.getCacheSize().then(size => {})


// 移除指定 url 的缓存文件 (必须是加载时 videoId=null)
vStore.removeCache(url)


// 移除 videoId 的缓存文件
vStore.removeCacheById(videoId)


// 清空缓存
vStore.clearCache()
```