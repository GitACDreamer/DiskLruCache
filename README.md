## DiskLruCache 本地缓存使用

### DiskLruCache使用的步骤
1. 创建缓存路径
```
//设置图片缓存路径
File cacheDir = getDiskCacheDir(context, "thumb");
if (!cacheDir.exists()) cacheDir.mkdirs();
```
2. 创建DiskLruCache实例
```
//创建DiskLruCache实例，初始化缓存数据
mDiskLruCache = DiskLruCache.open(cacheDir, AppUtils.getAppVersionCode(), 1, 50 * 1024 * 1024);
```
3. 加载缓存
*`先判断本地是否有缓存，如果没有则从网络中加载缓存，否则直接加载本地缓存`*
* 将图片链接转换成MD5编码
```
  //生成图片URL对应的key
  final String key = EncryptUtils.encryptMD5ToString(imageUrl).toLowerCase();
```
* 下载网络或者直接获取本地缓存图片（如果图片较多，可采取线程池）
```
//查找key对应的缓存
snapshot = mDiskLruCache.get(key);
//如果没有找到对应的本地缓存，则准备从网络上请求数据，并且写入缓存
if (snapshot == null) {
    DiskLruCache.Editor editor = mDiskLruCache.edit(key);
    if (editor != null) {
        OutputStream out = editor.newOutputStream(0);
        if (downloadUrlToString(imageUrl, out)) {
            editor.commit();
        } else {
             editor.abort();
        }
    }
}
//缓存被写入后，再次查找key对应的缓存
snapshot = mDiskLruCache.get(key);
try {
    //从缓存中获取图片，如果有，则直接设置到页面上，如果没有，则从网络上下载
    Bitmap bitmap = getBitmapFromMemoryCache(imageUrl);
    if (bitmap == null) {
        //创建下载图片任务
        BitmapWorkerTask task = new BitmapWorkerTask();
        //添加下载任务到集合中
        collections.add(task);
        //将任务队列使用线程池处理
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, imageUrl);
    } else {
        if (imageView != null) imageView.setImageBitmap(bitmap);
    }
} catch (final Exception e) {
    e.printStackTrace();
}
```
* 加载图片到指定的控件上
```
if (imageView != null) imageView.setImageBitmap(bitmap);
```