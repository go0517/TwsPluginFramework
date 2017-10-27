package com.tws.plugin.manager;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import qrom.component.log.QRomLog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.text.TextUtils;
import android.util.Base64;

import com.tws.plugin.content.PluginDescriptor;
import com.tws.plugin.core.PluginLoader;
import com.tws.plugin.util.ProcessUtil;

/**
 * 插件组件动态绑定到宿主的虚拟stub组件
 */
class PluginStubBinding {

	private static final String TAG = "rick_Print:PluginStubBinding";

	private static String KEY_SERVICE_MAP_PREFERENCES_NAME = "plugins.serviceMapping";
	private static String KEY_SERVICE_MAP_MAP_PREFERENCES_NAME = "plugins.serviceMapping.map";
	private static String KEY_MP_SERVICE_MAP_PREFERENCES_NAME = "plugins.mp.serviceMapping";
	private static String KEY_MP_SERVICE_MAP_MAP_PREFERENCES_NAME = "plugins.mp.serviceMapping.map";

	/**
	 * key:stub Activity Name value:plugin Activity Name
	 */
	private static HashMap<String, String> singleTaskActivityMapping = new HashMap<String, String>();
	private static HashMap<String, String> singleTopActivityMapping = new HashMap<String, String>();
	private static HashMap<String, String> singleInstanceActivityMapping = new HashMap<String, String>();
	private static String standardActivity = null;
	private static String receiver = null;
	/**
	 * key:stub Service Name value:plugin Service Name
	 */
	private static HashMap<String, String> serviceMapping = new HashMap<String, String>();
	private static HashMap<String, String> mpServiceMapping = new HashMap<String, String>();

	private static Set<String> mExcatStubSet;

	private static boolean isPoolInited = false;

	private static String buildDefaultAction() {
		return PluginLoader.getApplication().getPackageName() + ".STUB_DEFAULT";
	}

	private static String buildMpDefaultAction() {
		return PluginLoader.getApplication().getPackageName() + ".MP_STUB_DEFAULT";
	}

	private static String buildExactAction() {
		return PluginLoader.getApplication().getPackageName() + ".STUB_EXACT";
	}

	private static void initPool() {

		if (!ProcessUtil.isPluginProcess()) {
			throw new IllegalAccessError("此类只能在插件所在进程使用");
		}

		if (isPoolInited) {
			return;
		}

		loadStubActivity();

		loadStubService();

		loadMpStubService();

		loadStubExactly();

		loadStubReceiver();

		isPoolInited = true;
	}

	private static void loadStubActivity() {
		Intent launchModeIntent = new Intent();
		launchModeIntent.setAction(buildDefaultAction());
		launchModeIntent.setPackage(PluginLoader.getApplication().getPackageName());

		List<ResolveInfo> list = PluginLoader.getApplication().getPackageManager()
				.queryIntentActivities(launchModeIntent, PackageManager.MATCH_DEFAULT_ONLY);

		if (list != null && list.size() > 0) {
			for (ResolveInfo resolveInfo : list) {
				if (resolveInfo.activityInfo.launchMode == ActivityInfo.LAUNCH_SINGLE_TASK) {

					singleTaskActivityMapping.put(resolveInfo.activityInfo.name, null);

				} else if (resolveInfo.activityInfo.launchMode == ActivityInfo.LAUNCH_SINGLE_TOP) {

					singleTopActivityMapping.put(resolveInfo.activityInfo.name, null);

				} else if (resolveInfo.activityInfo.launchMode == ActivityInfo.LAUNCH_SINGLE_INSTANCE) {

					singleInstanceActivityMapping.put(resolveInfo.activityInfo.name, null);

				} else if (resolveInfo.activityInfo.launchMode == ActivityInfo.LAUNCH_MULTIPLE) {

					standardActivity = resolveInfo.activityInfo.name;

				}

			}
		}

	}

	private static synchronized void loadStubService() {
		Intent launchModeIntent = new Intent();
		launchModeIntent.setAction(buildDefaultAction());
		launchModeIntent.setPackage(PluginLoader.getApplication().getPackageName());

		List<ResolveInfo> list = PluginLoader.getApplication().getPackageManager()
				.queryIntentServices(launchModeIntent, PackageManager.MATCH_DEFAULT_ONLY);

		if (list != null && list.size() > 0) {
			for (ResolveInfo resolveInfo : list) {
				serviceMapping.put(resolveInfo.serviceInfo.name, null);
			}

			HashMap<String, String> mapping = restore(false);
			if (mapping != null) {
				Iterator<String> iter = mapping.keySet().iterator();
				String key;
				while (iter.hasNext()) {
					key = iter.next();
					if (serviceMapping.containsKey(key)) {
						serviceMapping.put(key, mapping.get(key));
					} else {
						// 我去这个还真的在当前版本被删除了
					}
				}
				// serviceMapping.putAll(mapping);
			}

			// 只有service需要固化
			save(serviceMapping, false);
		}
	}

	private static synchronized void loadMpStubService() {
		Intent launchModeIntent = new Intent();
		launchModeIntent.setAction(buildMpDefaultAction());
		launchModeIntent.setPackage(PluginLoader.getApplication().getPackageName());

		List<ResolveInfo> list = PluginLoader.getApplication().getPackageManager()
				.queryIntentServices(launchModeIntent, PackageManager.MATCH_DEFAULT_ONLY);

		if (list != null && list.size() > 0) {
			for (ResolveInfo resolveInfo : list) {
				mpServiceMapping.put(resolveInfo.serviceInfo.name, null);
			}

			HashMap<String, String> mapping = restore(true);
			if (mapping != null) {
				Iterator<String> iter = mapping.keySet().iterator();
				String key;
				while (iter.hasNext()) {
					key = iter.next();
					if (mpServiceMapping.containsKey(key)) {
						mpServiceMapping.put(key, mapping.get(key));
					} else {
						// 我去这个还真的在当前版本被删除了
					}
				}
				// mpServiceMapping.putAll(mapping);
			}

			// 只有service需要固化
			save(mpServiceMapping, true);
		}
	}

	private static void loadStubExactly() {
		Intent exactStub = new Intent();
		exactStub.setAction(buildExactAction());
		exactStub.setPackage(PluginLoader.getApplication().getPackageName());

		// 精确匹配的activity
		List<ResolveInfo> resolveInfos = PluginLoader.getApplication().getPackageManager()
				.queryIntentActivities(exactStub, PackageManager.MATCH_DEFAULT_ONLY);

		if (resolveInfos != null && resolveInfos.size() > 0) {
			if (mExcatStubSet == null) {
				mExcatStubSet = new HashSet<String>();
			}
			for (ResolveInfo info : resolveInfos) {
				mExcatStubSet.add(info.activityInfo.name);
			}
		}

		// 精确匹配的service
		resolveInfos = PluginLoader.getApplication().getPackageManager()
				.queryIntentServices(exactStub, PackageManager.MATCH_DEFAULT_ONLY);

		if (resolveInfos != null && resolveInfos.size() > 0) {
			if (mExcatStubSet == null) {
				mExcatStubSet = new HashSet<String>();
			}
			for (ResolveInfo info : resolveInfos) {
				mExcatStubSet.add(info.serviceInfo.name);
			}
		}

	}

	private static void loadStubReceiver() {
		Intent exactStub = new Intent();
		exactStub.setAction(buildDefaultAction());
		exactStub.setPackage(PluginLoader.getApplication().getPackageName());

		List<ResolveInfo> resolveInfos = PluginLoader.getApplication().getPackageManager()
				.queryBroadcastReceivers(exactStub, PackageManager.MATCH_DEFAULT_ONLY);

		if (resolveInfos != null && resolveInfos.size() > 0) {
			receiver = resolveInfos.get(0).activityInfo.name;
		}

	}

	public static String bindStubReceiver() {
		initPool();
		return receiver;
	}

	public static synchronized String bindStubActivity(String pluginActivityClassName, int launchMode) {

		initPool();

		if (isExact(pluginActivityClassName, PluginDescriptor.ACTIVITY)) {
			return pluginActivityClassName;
		}

		HashMap<String, String> bindingMapping = null;

		if (launchMode == ActivityInfo.LAUNCH_MULTIPLE) {

			return standardActivity;

		} else if (launchMode == ActivityInfo.LAUNCH_SINGLE_TASK) {

			bindingMapping = singleTaskActivityMapping;

		} else if (launchMode == ActivityInfo.LAUNCH_SINGLE_TOP) {

			bindingMapping = singleTopActivityMapping;

		} else if (launchMode == ActivityInfo.LAUNCH_SINGLE_INSTANCE) {

			bindingMapping = singleInstanceActivityMapping;

		}

		if (bindingMapping != null) {

			Iterator<Map.Entry<String, String>> itr = bindingMapping.entrySet().iterator();
			String idleStubActivityName = null;

			while (itr.hasNext()) {
				Map.Entry<String, String> entry = itr.next();
				if (entry.getValue() == null) {
					if (idleStubActivityName == null) {
						idleStubActivityName = entry.getKey();
						// 这里找到空闲的stubactivity以后，还需继续遍历，用来检查是否pluginActivityClassName已经绑定过了
					}
				} else if (pluginActivityClassName.equals(entry.getValue())) {
					// 已绑定过，直接返回
					return entry.getKey();
				}
			}

			// 没有绑定到StubActivity，而且还有空余的stubActivity，进行绑定
			if (idleStubActivityName != null) {
				bindingMapping.put(idleStubActivityName, pluginActivityClassName);
				return idleStubActivityName;
			}

		}

		return standardActivity;
	}

	public static boolean isExact(String name, int type) {
		initPool();

		if (mExcatStubSet != null && mExcatStubSet.size() > 0) {
			return mExcatStubSet.contains(name);
		}

		return false;
	}

	public static synchronized void unBindLaunchModeStubActivity(String stubActivityName, String pluginActivityName) {

		QRomLog.d(TAG, "call unBindLaunchModeStubActivity:" + stubActivityName + " pluginActivityName is "
				+ pluginActivityName);

		if (pluginActivityName.equals(singleTaskActivityMapping.get(stubActivityName))) {
			QRomLog.d(TAG, "equals singleTaskActivityMapping");
			singleTaskActivityMapping.put(stubActivityName, null);

		} else if (pluginActivityName.equals(singleInstanceActivityMapping.get(stubActivityName))) {
			QRomLog.d(TAG, "equals singleInstanceActivityMapping");
			singleInstanceActivityMapping.put(stubActivityName, null);
		} else {
			QRomLog.d(TAG, "对于standard和singleTop的launchmode，不做处理。");
		}
	}

	public static synchronized String getBindedPluginServiceName(String stubServiceName) {

		initPool();

		if (isExact(stubServiceName, PluginDescriptor.SERVICE)) {
			return stubServiceName;
		}

		Iterator<Map.Entry<String, String>> itr = serviceMapping.entrySet().iterator();
		while (itr.hasNext()) {
			Map.Entry<String, String> entry = itr.next();

			if (entry.getKey().equals(stubServiceName)) {
				return entry.getValue();
			}
		}

		// 没找到尝试MP里面
		Iterator<Map.Entry<String, String>> mpItr = mpServiceMapping.entrySet().iterator();
		while (mpItr.hasNext()) {
			Map.Entry<String, String> entry = mpItr.next();

			if (entry.getKey().equals(stubServiceName)) {
				return entry.getValue();
			}
		}

		return null;
	}

	public static synchronized String bindStubService(String pluginServiceClassName, String process) {

		initPool();

		if (isExact(pluginServiceClassName, PluginDescriptor.SERVICE)) {
			return pluginServiceClassName;
		}

		final boolean isMp = !TextUtils.isEmpty(process);
		Iterator<Map.Entry<String, String>> itr = isMp ? mpServiceMapping.entrySet().iterator() : serviceMapping
				.entrySet().iterator();

		String idleStubServiceName = null;

		while (itr.hasNext()) {
			Map.Entry<String, String> entry = itr.next();
			if (entry.getValue() == null) {
				if (idleStubServiceName == null) {
					idleStubServiceName = entry.getKey();
					// 这里找到空闲的idleStubServiceName以后，还需继续遍历，用来检查是否pluginServiceClassName已经绑定过了
				}
			} else if (pluginServiceClassName.equals(entry.getValue())) {
				// 已经绑定过，直接返回
				QRomLog.d(TAG, "已经绑定过:" + entry.getKey() + " pluginServiceClassName is " + pluginServiceClassName);
				return entry.getKey();
			}
		}

		// 没有绑定到StubService，而且还有空余的StubService，进行绑定
		if (idleStubServiceName != null) {
			QRomLog.d(TAG, "添加绑定:" + idleStubServiceName + " pluginServiceClassName is " + pluginServiceClassName);
			if (isMp) {
				mpServiceMapping.put(idleStubServiceName, pluginServiceClassName);
				// 对serviceMapping持久化是因为如果service处于运行状态时app发生了crash，系统会自动恢复之前的service，此时插件映射信息查不到的话会再次crash
				save(mpServiceMapping, true);
			} else {
				serviceMapping.put(idleStubServiceName, pluginServiceClassName);
				// 对serviceMapping持久化是因为如果service处于运行状态时app发生了crash，系统会自动恢复之前的service，此时插件映射信息查不到的话会再次crash
				save(serviceMapping, false);
			}
			return idleStubServiceName;
		}

		// 绑定失败
		return null;
	}

	public static synchronized void unBindStubService(String pluginServiceName) {
		Iterator<Map.Entry<String, String>> itr = serviceMapping.entrySet().iterator();
		while (itr.hasNext()) {
			Map.Entry<String, String> entry = itr.next();
			if (pluginServiceName.equals(entry.getValue())) {
				// 如果存在绑定关系，解绑
				QRomLog.d(TAG, "回收绑定 Key:" + entry.getKey() + " Value:" + entry.getValue());
				serviceMapping.put(entry.getKey(), null);
				save(serviceMapping, false);
				break;
			}
		}

		Iterator<Map.Entry<String, String>> mpItr = mpServiceMapping.entrySet().iterator();
		while (mpItr.hasNext()) {
			Map.Entry<String, String> entry = mpItr.next();
			if (pluginServiceName.equals(entry.getValue())) {
				// 如果存在绑定关系，解绑
				QRomLog.d(TAG, "回收绑定 Key:" + entry.getKey() + " Value:" + entry.getValue());
				mpServiceMapping.put(entry.getKey(), null);
				save(mpServiceMapping, false);
				break;
			}
		}
	}

	public static String dumpServieInfo() {
		return serviceMapping.toString();
	}

	private static boolean save(HashMap<String, String> mapping, boolean isMp) {

		ObjectOutputStream objectOutputStream = null;
		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		try {
			objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
			objectOutputStream.writeObject(mapping);
			objectOutputStream.flush();

			byte[] data = byteArrayOutputStream.toByteArray();
			String list = Base64.encodeToString(data, Base64.DEFAULT);

			if (isMp) {
				PluginLoader.getApplication()
						.getSharedPreferences(KEY_MP_SERVICE_MAP_PREFERENCES_NAME, Context.MODE_PRIVATE).edit()
						.putString(KEY_MP_SERVICE_MAP_MAP_PREFERENCES_NAME, list).commit();
			} else {
				PluginLoader.getApplication()
						.getSharedPreferences(KEY_SERVICE_MAP_PREFERENCES_NAME, Context.MODE_PRIVATE).edit()
						.putString(KEY_SERVICE_MAP_MAP_PREFERENCES_NAME, list).commit();
			}

			return true;
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (objectOutputStream != null) {
				try {
					objectOutputStream.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			if (byteArrayOutputStream != null) {
				try {
					byteArrayOutputStream.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return false;
	}

	private static HashMap<String, String> restore(boolean isMp) {
		String list = isMp ? PluginLoader.getApplication()
				.getSharedPreferences(KEY_MP_SERVICE_MAP_PREFERENCES_NAME, Context.MODE_PRIVATE)
				.getString(KEY_MP_SERVICE_MAP_MAP_PREFERENCES_NAME, "") : PluginLoader.getApplication()
				.getSharedPreferences(KEY_SERVICE_MAP_PREFERENCES_NAME, Context.MODE_PRIVATE)
				.getString(KEY_SERVICE_MAP_MAP_PREFERENCES_NAME, "");
		Serializable object = null;
		if (!TextUtils.isEmpty(list)) {
			ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(Base64.decode(list, Base64.DEFAULT));
			ObjectInputStream objectInputStream = null;
			try {
				objectInputStream = new ObjectInputStream(byteArrayInputStream);
				object = (Serializable) objectInputStream.readObject();
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				if (objectInputStream != null) {
					try {
						objectInputStream.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				if (byteArrayInputStream != null) {
					try {
						byteArrayInputStream.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}
		if (object != null) {
			HashMap<String, String> mapping = (HashMap<String, String>) object;
			return mapping;
		}
		return null;
	}

	public static boolean isStub(String className) {
		initPool();

		return isExact(className, PluginDescriptor.ACTIVITY) || className.equals(standardActivity)
				|| singleTaskActivityMapping.containsKey(className) || singleTopActivityMapping.containsKey(className)
				|| singleInstanceActivityMapping.containsKey(className) || serviceMapping.containsKey(className)
				|| mpServiceMapping.containsKey(className) || className.equals(receiver);
	}
}
