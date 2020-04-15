import { NativeModules } from 'react-native';
const { RNVideoStore } = NativeModules;

let _PRELOAD_ID = 0;
class PreloadResult {
    constructor(id, preload) {
        this._id = id;
        this._preload = preload.then(r => {
            return {ok:true, rs:r}
        }).catch(err => {
            return {ok:false, rs:err}
        });
    }
    stop() {
        RNVideoStore.stopPreload(this._id);
    }
    result(){
        return this._preload.then(({ok,rs}) => {
            if (!ok) {
                throw rs;
            }
            return rs;
        })
    }
}
function urlWithId(url, id) {
    id = id === undefined ? "" : String(id);
    id = id === "" ? "" : (url.indexOf("?") > -1 ? "&" : "?") + "_l=" + encodeURIComponent(id);
    return url + id;
}
const VideoStore = {};

VideoStore.setMaxSize = (size) => {
    size = parseInt(size);
    if (!isNaN(size)) {
        RNVideoStore.setMaxSize(size)
    }
    return VideoStore;
}
VideoStore.setMaxFiles = (size) => {
    size = parseInt(size);
    if (!isNaN(size)) {
        RNVideoStore.setMaxFiles(size)
    }
    return VideoStore;
}
VideoStore.withSystemUA = (enable) => {
    RNVideoStore.disableSystemUA(!Boolean(enable))
    return VideoStore;
}
VideoStore.setDefaultHeaders = (headers, applyRedirect) => {
    RNVideoStore.setDefaultHeaders(
        headers||{}, 
        applyRedirect===undefined ? true : Boolean(applyRedirect)
    )
    return VideoStore;
}
VideoStore.setDomainHeaders = (host, headers, applyRedirect) => {
    RNVideoStore.setDomainHeaders(
        host, 
        headers||{}, 
        applyRedirect===undefined ? true : Boolean(applyRedirect)
    )
    return VideoStore;
}

// 一般情况下, 缓存文件是由 url 确定的, 但实际使用中, 
// video url 会因为认证等原因无法保证唯一性, 这也就造成重复缓存的问题
// 所以这里使用 videoId 作为缓存文件的唯一标识, 若可以保证 url 唯一, 
// 也可以直接使用 url 作为标识, videoId 参数设置为 null 即可
VideoStore.getProxyUrl = (videoId, url, allowFileUri) => {
    return RNVideoStore.getProxyUrl(
        urlWithId(url, videoId),
        allowFileUri===undefined ? true : Boolean(allowFileUri)
    );
}
VideoStore.preload = (videoId, url, size) => {
    const id = ++_PRELOAD_ID;
    return new PreloadResult(id, RNVideoStore.startPreload(
        id,
        urlWithId(url, videoId),
        size === undefined ? 0 : size
    ))
}

VideoStore.clearPreload = () => {
    RNVideoStore.stopAllPreload();
    return VideoStore;
}

// 获取所有缓存 size
VideoStore.getCacheSize = () => {
    return RNVideoStore.getCacheSize();
}

// 通过 url 移除缓存
// 移除 getProxyUrl/preload 调用时 videoId=null 的缓存
VideoStore.removeCache = (url) => {
    RNVideoStore.removeCache(url);
    return VideoStore;
}

// 通过 videoId 移除缓存 
VideoStore.removeCacheById = (videoId) => {
    RNVideoStore.removeCache(urlWithId('', videoId));
    return VideoStore;
}

// 移除所有缓存
VideoStore.clearCache = () => {
    RNVideoStore.clearCache();
    return VideoStore;
}

export default VideoStore;