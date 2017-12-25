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
4. 效果图
![cache.gif](http://upload-images.jianshu.io/upload_images/4073499-cfd0cca6d06ec51a.gif?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)
录制了半天老是有花脸 
![cry.jpg](http://upload-images.jianshu.io/upload_images/4073499-9f06245b6445a0a9.jpg?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)

5. 参考链接
[Demo中用到的图片源，采用的郭霖大神的图片][1]
[用到了快速开发工具库][2]
[用到了RecyclerView的万能适配Adapter][3]
6. 源码传送门
DiskLruCache:<https://www.jianshu.com/p/35f939874960>
7. 题外
> 使用模拟器出现的意料错误，真机没有出现，通过Google搜索发现是`依赖库版本不一致导致的异常`
```
java.lang.ClassNotFoundException: Didn't find class "cn.net.sunet.disklrucache.Mainactivity" on path: DexPathList[[zip file "/data/app/cn.net.sunet.disklrucache-2/base.apk", zip file "/data/app/cn.net.sunet.disklrucache-2/split_lib_slice_9_apk.apk"],nativeLibraryDirectories=[/vendor/lib64, /system/lib64]]。
```
**如果大家觉得还行请给个赞，谢谢**

[1]:http://blog.csdn.net/guolin_blog/article/details/34093441
[2]:https://github.com/Blankj/AndroidUtilCode
[3]:https://github.com/CymChad/BaseRecyclerViewAdapterHelper