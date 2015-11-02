/* 
 * Copyright (C) 2015 Peter Cai
 *
 * This file is part of BlackLight
 *
 * BlackLight is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BlackLight is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BlackLight.  If not, see <http://www.gnu.org/licenses/>.
 */

package info.papdt.blacklight.ui.entry;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import info.papdt.blacklight.cache.file.FileCacheManager;
import info.papdt.blacklight.cache.login.LoginApiCache;
import info.papdt.blacklight.receiver.ConnectivityReceiver;
import info.papdt.blacklight.support.CrashHandler;
import info.papdt.blacklight.support.Emoticons;
import info.papdt.blacklight.support.FilterUtility;
import info.papdt.blacklight.support.Utility;
import info.papdt.blacklight.support.feedback.SubmitLogTask;
import info.papdt.blacklight.support.http.FeedbackUtility;
import info.papdt.blacklight.ui.login.LoginActivity;
import info.papdt.blacklight.ui.main.MainActivity;

public class EntryActivity extends Activity
{
    public static final String TAG = EntryActivity.class.getSimpleName();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, TAG + "onCreate");
        CrashHandler.init(this);
		CrashHandler.register();
		
		super.onCreate(savedInstanceState);

		// Clear
        Log.d(TAG, "清理文件缓存");
		FileCacheManager.instance(this).clearUnavailable();
		
		// Init
        Log.d(TAG, "查询网络状态");
		ConnectivityReceiver.readNetworkState(this);
        Log.d(TAG, "初始化表情");
		Emoticons.init(this);
        Log.d(TAG, "初始化关键词屏蔽");
		FilterUtility.init(this);

		// Crash Log
		if (FeedbackUtility.shouldSendLog(this)) {
			new SubmitLogTask(this).execute();
		}

        Log.d(TAG, "初始化登陆API缓存(LoginApiCache)");
		LoginApiCache login = new LoginApiCache(this);
		if (needsLogin(login)) {
            Log.d(TAG, "需要新登陆");
			login.logout();
            Log.d(TAG, "进入 LoginActivity 并关闭本界面");
			Intent i = new Intent();
			i.setAction(Intent.ACTION_MAIN);
			i.setClass(this, LoginActivity.class);
			startActivity(i);
			finish();
		} else {
            Log.d(TAG, "毋需新登陆");
            Log.d(TAG, "进入 MainActivity 并关闭本界面");
			Intent i = new Intent();
			i.setAction(Intent.ACTION_MAIN);
			i.setClass(this, MainActivity.class);
			i.putExtra(Intent.EXTRA_INTENT,getIntent().getIntExtra(Intent.EXTRA_INTENT,0));
			startActivity(i);
			finish();
		}
		
	}
	
	private boolean needsLogin(LoginApiCache login) {
		return login.getAccessToken() == null || Utility.isTokenExpired(login.getExpireDate());
	}
}
