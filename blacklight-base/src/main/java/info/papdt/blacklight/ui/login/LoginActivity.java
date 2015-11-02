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

package info.papdt.blacklight.ui.login;

import android.support.v7.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import info.papdt.blacklight.R;
import info.papdt.blacklight.api.BaseApi;
import info.papdt.blacklight.api.PrivateKey;
import info.papdt.blacklight.cache.login.LoginApiCache;
import info.papdt.blacklight.support.AsyncTask;
import info.papdt.blacklight.support.Utility;
import info.papdt.blacklight.ui.common.AbsActivity;
import info.papdt.blacklight.ui.main.MainActivity;

import static info.papdt.blacklight.BuildConfig.DEBUG;
import static info.papdt.blacklight.support.Utility.hasSmartBar;

/* Login Activity */
public class LoginActivity extends AbsActivity {
	private static final String TAG = LoginActivity.class.getSimpleName();

	private WebView mWeb;

	private LoginApiCache mLogin;
	private boolean mIsMulti = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Log.d(TAG, TAG + "onCreate");
		mLayout = R.layout.web_login;
		super.onCreate(savedInstanceState);

		mIsMulti = getIntent().getBooleanExtra("multi", false);

		// Initialize views
		mWeb = Utility.findViewById(this, R.id.login_web);

		// Create login instance
        Log.d(TAG, "初始化登陆API缓存(LoginApiCache)");
		mLogin = new LoginApiCache(this);

		// Login page
        Log.d(TAG, "初始化 WebSettings");
		WebSettings settings = mWeb.getSettings();
		settings.setJavaScriptEnabled(true);
		settings.setSaveFormData(false);
		settings.setSavePassword(false);
		settings.setCacheMode(WebSettings.LOAD_NO_CACHE);

        Log.d(TAG, "将 MyWebViewClient 设为 mWeb 的 WebViewClient");
		mWeb.setWebViewClient(new MyWebViewClient());

        Log.d(TAG, "从 SP 中获取私钥");
		if (PrivateKey.readFromPref(this)) {
			mWeb.loadUrl(PrivateKey.getOauthLoginPage());
		} else {
            Log.d(TAG, "无私钥, 显示私钥填写页面");
			mWeb.loadUrl("about:blank");
			showAppKeyDialog();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.login, menu);
		return true;
	}


	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			setResult(RESULT_CANCELED);
			finish();
			return true;
		} else if (item.getItemId() == R.id.custom) {
			showAppKeyDialog();
			return true;
		} else {
			return super.onOptionsItemSelected(item);
		}
	}

	private void handleRedirectedUrl(String url) {
        Log.d(TAG, "判断url中是否有出错信息");
		if (!url.contains("error")) {
            Log.d(TAG, "获取 token 与 expiresIndex");
			int tokenIndex = url.indexOf("access_token=");
			int expiresIndex = url.indexOf("expires_in=");
			String token = url.substring(tokenIndex + 13, url.indexOf("&", tokenIndex));
			String expiresIn = url.substring(expiresIndex + 11, url.indexOf("&", expiresIndex));

            Log.d(TAG, "url = " + url);
            Log.d(TAG, "token = " + token);
            Log.d(TAG, "expires_in = " + expiresIn);

            Log.d(TAG, "执行 LoginTask()");
            new LoginTask().execute(token, expiresIn);
		} else {
			showLoginFail();
		}
	}

	private void showLoginFail() {
		// Wrong username or password
		new AlertDialog.Builder(LoginActivity.this)
								.setMessage(R.string.login_fail)
								.setCancelable(true)
								.create()
								.show();
	}

	private void showAppKeyDialog() {
		// Inflate dialog layout
		LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View v = inflater.inflate(R.layout.app_key, null);
		final EditText tvId = Utility.findViewById(v, R.id.app_id);
		final EditText tvSecret = Utility.findViewById(v, R.id.app_secret);
		final EditText tvRedirect = Utility.findViewById(v, R.id.redirect_uri);
		final EditText tvScope = Utility.findViewById(v, R.id.scope);
		final EditText tvPkg = Utility.findViewById(v, R.id.app_pkg);

		// Build the dialog
		final AlertDialog dialog = new AlertDialog.Builder(this)
				.setTitle(R.string.custom)
				.setView(v)
				.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface p1, int p2) {

					}
				})
				.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface p1, int p2) {

					}
				})
				.setNeutralButton(R.string.app_copy, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface p1, int p2) {

					}
				})
				.create();

		dialog.show();
        // 按钮回调在外面设置
		dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
                Log.d(TAG, "对话框点了确定");
				String id = tvId.getText().toString().trim();
				String sec = tvSecret.getText().toString().trim();
				String uri = tvRedirect.getText().toString().trim();
				String scope = tvScope.getText().toString().trim();
				String pkg = tvPkg.getText().toString().trim();
                // 只做了判空检查
				if (!TextUtils.isEmpty(id) && !TextUtils.isEmpty(sec)
					&& !TextUtils.isEmpty(uri) && !TextUtils.isEmpty(scope)) {

                    Log.d(TAG, "写入私钥");
					PrivateKey.setPrivateKey(id, sec, uri, pkg, scope);
					PrivateKey.writeToPref(LoginActivity.this);
					dialog.dismiss();

                    Log.d(TAG, "加载登陆页");
					mWeb.loadUrl(PrivateKey.getOauthLoginPage());
				}
			}
		});

		dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setTag(true);
		dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				boolean isEmpty = (Boolean) v.getTag();

				if (!isEmpty) {
                    Log.d(TAG, "已有内容的话 Base64 加密并复制");
					Utility.copyToClipboard(LoginActivity.this, encodeLoginData(
						tvId.getText().toString(), tvSecret.getText().toString(),
						tvRedirect.getText().toString(), tvScope.getText().toString(),
						tvPkg.getText().toString()));
				} else {
					Intent i = new Intent(Intent.ACTION_VIEW);
					i.setData(Uri.parse(getString(R.string.key_gist)));
					startActivity(i);
				}
			}
		});

		// Text listener
		TextWatcher watcher = new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence p1, int p2, int p3, int p4) {

			}

			@Override
			public void onTextChanged(CharSequence p1, int p2, int p3, int p4) {

			}

			@Override
			public void afterTextChanged(Editable text) {
                Log.d(TAG, "检测到输入框有内容加入");
				String str = text.toString().trim();
                Log.d(TAG, "判断是否是预置内容");
				if (isLoginData(str)) {
                    Log.d(TAG, "是的话解密并设置");
					String[] data = decodeLoginData(text.toString());

					if (data == null || data.length < 5) return;

					tvId.setText(data[0].trim());
					tvSecret.setText(data[1].trim());
					tvRedirect.setText(data[2].trim());
					tvScope.setText(data[3].trim());
					tvPkg.setText(data[4].trim());
				} else {
					if (!str.equals("")) {
						dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setText(R.string.app_copy);
					} else {
						dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setText(R.string.app_hint);
					}

					dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setTag(str.equals(""));
				}
			}
		};
        Log.d(TAG, "为 EditText 添加 watcher");
		tvId.addTextChangedListener(watcher);
		tvSecret.addTextChangedListener(watcher);
		tvRedirect.addTextChangedListener(watcher);
		tvScope.addTextChangedListener(watcher);
		tvPkg.addTextChangedListener(watcher);

		// Initialize values
		if (DEBUG) {
			tvId.setText("SSMjExMTYwNjc5OjoxZTZlMzNkYjA4ZjkxOTIzMDZjNGFmYTBhNjFhZDU2Yzo6aHR0cDovL29hdXRoLndlaWNvLmNjOjplbWFpbCxkaXJlY3RfbWVzc2FnZXNfcmVhZCxkaXJlY3RfbWVzc2FnZXNfd3JpdGUsZnJpZW5kc2hpcHNfZ3JvdXBzX3JlYWQsZnJpZW5kc2hpcHNfZ3JvdXBzX3dyaXRlLHN0YXR1c2VzX3RvX21lX3JlYWQsZm9sbG93X2FwcF9vZmZpY2lhbF9taWNyb2Jsb2csaW52aXRhdGlvbl93cml0ZTo6Y29tLmVpY28ud2VpY286OkVFEE");
		} else {
			String[] val = PrivateKey.getAll();
			tvId.setText(val[0]);
			tvSecret.setText(val[1]);
			tvRedirect.setText(val[2]);
			tvPkg.setText(val[3]);
			tvScope.setText(val[4]);
		}

	}

	private static final String SEPERATOR = "::";
	private static final String START = "SS",
								END = "EE";
	private String encodeLoginData(String id, String secret, String uri, String scope, String pkg) {
		return START + Base64.encodeToString(
			(id + SEPERATOR + secret + SEPERATOR + uri + SEPERATOR +
			scope + SEPERATOR + pkg + SEPERATOR + END).getBytes(), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING | Base64.NO_CLOSE) + END;
	}

	private String[] decodeLoginData(String str) {
		if (!isLoginData(str))
			return null;

		String data = str.substring(START.length(), str.length() - END.length() - 1);

		if (DEBUG) {
			Log.d(TAG, data);
		}

		try {
			return new String(Base64.decode(data, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING | Base64.NO_CLOSE)).split(SEPERATOR);
		} catch (Exception e) {
			return null;
		}
	}

	private boolean isLoginData(String str) {
		return str.startsWith(START) && str.length() > START.length() + END.length() && str.endsWith(END);
	}

	private class MyWebViewClient extends WebViewClient {

        /**
         * Give the host application a chance to take over the control when a new
         * url is about to be loaded in the current WebView. If WebViewClient is not
         * provided, by default WebView will ask Activity Manager to choose the
         * proper handler for the url. If WebViewClient is provided, return true
         * means the host application handles the url, while return false means the
         * current WebView handles the url.
         * This method is not called for requests using the POST "method".
         *
         * 给主程序一个机会当一个新的 url 快要在当前 WebView 中加载出来的时候,是否掌控
         * 如果没有提供 WebViewClient,默认 WebView 会问 Activity Manager 选择合适的
         * 处理器打开,如提供,返回 true 表示已有 WebViewClient 掌控
         *
         * @param view The WebView that is initiating the callback.
         * @param url The url to be loaded.
         * @return True if the host application wants to leave the current WebView
         *         and handle the url itself, otherwise return false.
         */
		@Override
		public boolean shouldOverrideUrlLoading(WebView view, String url) {
            Log.d(TAG, "检查是否是跳转 url");
			if (PrivateKey.isUrlRedirected(url)) {
                Log.d(TAG, "这是点击确定,之后发生跳转");
				view.stopLoading();
                Log.d(TAG, "交由 handleRedirectedUrl 处理");
				handleRedirectedUrl(url);
			} else {
                Log.d(TAG, "这是填写登陆密码的那个url");
				view.loadUrl(url);
			}
			return true;
		}

		@Override
		public void onPageStarted(WebView view, String url, Bitmap favicon) {
			if (!url.equals("about:blank") && PrivateKey.isUrlRedirected(url)) {
				view.stopLoading();
				handleRedirectedUrl(url);
				return;
			}
			super.onPageStarted(view, url, favicon);
		}
	}

	private class LoginTask extends AsyncTask<String, Void, Long>
	{
        public final String TAG = LoginTask.class.getSimpleName();
		private ProgressDialog progDialog;

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			progDialog = new ProgressDialog(LoginActivity.this);
			progDialog.setMessage(getResources().getString(R.string.plz_wait));
			progDialog.setCancelable(false);
			progDialog.show();
		}

		@Override
		protected Long doInBackground(String... params) {
			if (DEBUG) {
				Log.d(TAG, "doInBackground...");
			}

			if (!mIsMulti) {
                Log.d(TAG, "调用 LoginApiCache 的 login 方法");
				mLogin.login(params[0], params[1]);
				return mLogin.getExpireDate();
			} else {
				return mLogin.addUser(params[0], params[1]);
			}
		}

		@Override
		protected void onPostExecute(Long result) {
			super.onPostExecute(result);
			progDialog.dismiss();

			if (!mIsMulti && mLogin.getAccessToken() != null) {
                Log.d(TAG, "Access Token:" + mLogin.getAccessToken());
                Log.d(TAG, "Expires in:" + mLogin.getExpireDate());
                Log.d(TAG, "调用 LoginApiCache 的 cache 方法");
                mLogin.cache();
				BaseApi.setAccessToken(mLogin.getAccessToken());
			} else if (!mIsMulti && mLogin.getAccessToken() == null) {
				showLoginFail();
				return;
			}


			// Expire date
			String msg = String.format(getResources().getString(R.string.expires_in), Utility.expireTimeInDays(result));
			new AlertDialog.Builder(LoginActivity.this)
				.setMessage(msg)
				.setCancelable(false)
				.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int id) {
						dialog.dismiss();
						if (!mIsMulti) {
                            Log.d(TAG, "点击确定进入主界面");
							Intent i = new Intent();
							i.setAction(Intent.ACTION_MAIN);
							i.setClass(LoginActivity.this, MainActivity.class);
							startActivity(i);
							finish();
						} else {
							setResult(RESULT_OK);
							finish();
						}
					}
				})
				.create()
				.show();
		}

	}
}
