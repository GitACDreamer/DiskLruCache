package cn.net.sunet.DiskLruCache;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Environment;
import android.support.annotation.LayoutRes;
import android.support.annotation.Nullable;
import android.support.v4.util.LruCache;
import android.widget.ImageView;

import com.blankj.utilcode.util.AppUtils;
import com.blankj.utilcode.util.EncryptUtils;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.jakewharton.disklrucache.DiskLruCache;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Author:  Leland
 * Email:   AC_Dreamer@163.com
 * Website: www.sunet.net.cn
 * Date:    2017/12/11
 * Function:
 */

class ImageListAdapter extends BaseQuickAdapter<String, BaseViewHolder> {

	/**
	 * 记录所有正在下载或者等待的任务
	 */
	private Set<BitmapWorkerTask> collections;

	/**
	 * 图片缓存技术核心类，用于缓存所有下载好的图片，在程序内存达到设定值时会将最近最少使用的图片移除
	 */
	private LruCache<String, Bitmap> mMemoryCache;

	/**
	 * 图片硬盘缓存核心类
	 *
	 */
	private DiskLruCache mDiskLruCache;

	private ImageView mImageView;

	ImageListAdapter(Context context, @LayoutRes int layoutResId, @Nullable List<String> data) {
		super(layoutResId, data);

		collections = new HashSet<>();

		//获取应用程序最大可用内存
		int maxMemory = (int) Runtime.getRuntime().maxMemory();
		int cacheSize = maxMemory / 8;
		//设置图片缓存大小为程序最大可用内存的1/8
		mMemoryCache = new LruCache<String, Bitmap>(cacheSize) {
			@Override
			protected int sizeOf(String key, Bitmap value) {
				return value.getByteCount();
			}
		};
		try {
			//设置图片缓存路径
			File cacheDir = getDiskCacheDir(context, "thumb");
			if (!cacheDir.exists()) cacheDir.mkdirs();
			//创建DiskLruCache实例，初始化缓存数据
			mDiskLruCache = DiskLruCache.open(cacheDir, AppUtils.getAppVersionCode(), 1, 50 * 1024 * 1024);
		} catch (final IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	protected void convert(BaseViewHolder helper, String item) {
		mImageView = helper.getView(R.id.iv_picture);
		loadBitmap(mImageView, item);
	}

	private class BitmapWorkerTask extends AsyncTask<String, Void, Bitmap> {

		//图片的URL地址
		private String imageUrl;

		@Override
		protected Bitmap doInBackground(String... params) {
			imageUrl = params[0];
			FileDescriptor fileDescriptor = null;
			FileInputStream in = null;
			DiskLruCache.Snapshot snapshot;
			try {
				//生成图片URL对应的key
				final String key = EncryptUtils.encryptMD5ToString(imageUrl).toLowerCase();
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
					//缓存被写入后，再次查找key对应的缓存
					snapshot = mDiskLruCache.get(key);
				}
				if (snapshot != null) {
					in = (FileInputStream) snapshot.getInputStream(0);
					fileDescriptor = in.getFD();
				}
				//将缓存数据解析成Bitmap对象
				Bitmap bitmap = null;
				if (fileDescriptor != null) {
					bitmap = BitmapFactory.decodeFileDescriptor(fileDescriptor);
				}
				if (bitmap != null) {
					//将Bitmap对象添加到内存缓存当中
					addBitmapToMemoryCache(params[0], bitmap);
				}
				return bitmap;

			} catch (final IOException e) {
				e.printStackTrace();
			} finally {
				if (fileDescriptor == null && in != null) {
					try {
						in.close();
					} catch (final IOException e) {
						e.printStackTrace();
					}
				}
			}
			return null;
		}

		@Override
		protected void onPostExecute(Bitmap bitmap) {
			super.onPostExecute(bitmap);
			//根据Tag找到相应的ImageView控件，将下载好的图片显示出来
			if (mImageView != null && bitmap != null) {
				mImageView.setImageBitmap(bitmap);
			}
			if (bitmap == null) mImageView.setImageResource(android.R.mipmap.sym_def_app_icon);
			collections.remove(this);
		}
	}

	/**
	 * 从LruCache中获取一张图片，如果不存在就返回null
	 *
	 * @param key LruCache的键，传入图片的url地址
	 * @return 对应传入键的Bitmap对象或者null
	 */
	private Bitmap getBitmapFromMemoryCache(String key) {
		return mMemoryCache.get(key);
	}

	/**
	 * 将一张图片存储到LruCache中
	 *
	 * @param key    LruCache的键，这里传入图片的URL地址
	 * @param bitmap LruCache的值，这里传入从网络上下载的Bitmap对象
	 */
	private void addBitmapToMemoryCache(String key, Bitmap bitmap) {
		if (getBitmapFromMemoryCache(key) == null) mMemoryCache.put(key, bitmap);
	}

	private void loadBitmap(ImageView imageView, String imageUrl) {
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
	}

	/**
	 * 取消所有正在下载或者等待下载的任务
	 */
	public void cancelAllTask() {
		if (collections != null) {
			for (BitmapWorkerTask task : collections) {
				task.cancel(false);
			}
		}
	}

	/**
	 * 将缓存记录同步到journal中
	 */
	public void flushCache() {
		if (mDiskLruCache != null) {
			try {
				mDiskLruCache.flush();
			} catch (final IOException e) {
				e.printStackTrace();
			}
		}
	}

	private File getDiskCacheDir(Context context, String directory) {
		String cachePath;
		if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()) || !Environment
			.isExternalStorageRemovable()) {
			cachePath = context.getExternalCacheDir().getPath();
		} else {
			cachePath = context.getCacheDir().getPath();
		}
		return new File(cachePath + File.separator + directory);
	}

	/**
	 * 从网络上获取图片
	 *
	 * @param urlString    图片url
	 * @param outputStream 输出stream
	 * @return 是否返回成功
	 */
	private boolean downloadUrlToString(String urlString, OutputStream outputStream) {
		HttpURLConnection connection = null;
		BufferedOutputStream out = null;
		BufferedInputStream in = null;
		try {
			final URL url = new URL(urlString);
			connection = (HttpURLConnection) url.openConnection();
			in = new BufferedInputStream(connection.getInputStream(), 8 * 1024);
			out = new BufferedOutputStream(outputStream, 8 * 1024);
			int b;
			while ((b = in.read()) != -1) {
				out.write(b);
			}
			return true;
		} catch (final IOException e) {
			e.printStackTrace();
		} finally {
			if (connection != null) connection.disconnect();
			try {
				if (out != null) out.close();
				if (in != null) in.close();
			} catch (final IOException e) {
				e.printStackTrace();
			}
		}
		return false;
	}
}


