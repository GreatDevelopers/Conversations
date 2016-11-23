package in.gndec.sunehag.ui;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.security.KeyChain;
import android.security.KeyChainAliasCallback;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import in.gndec.sunehag.Config;
import in.gndec.sunehag.OmemoActivity;
import in.gndec.sunehag.R;
import in.gndec.sunehag.crypto.axolotl.AxolotlService;
import in.gndec.sunehag.crypto.axolotl.XmppAxolotlSession;
import in.gndec.sunehag.entities.Account;
import in.gndec.sunehag.services.BarcodeProvider;
import in.gndec.sunehag.services.XmppConnectionService;
import in.gndec.sunehag.services.XmppConnectionService.OnAccountUpdate;
import in.gndec.sunehag.services.XmppConnectionService.OnCaptchaRequested;
import in.gndec.sunehag.ui.adapter.KnownHostsAdapter;
import in.gndec.sunehag.utils.CryptoHelper;
import in.gndec.sunehag.utils.UIHelper;
import in.gndec.sunehag.xml.Element;
import in.gndec.sunehag.xmpp.OnKeyStatusUpdated;
import in.gndec.sunehag.xmpp.XmppConnection;
import in.gndec.sunehag.xmpp.XmppConnection.Features;
import in.gndec.sunehag.xmpp.forms.Data;
import in.gndec.sunehag.xmpp.jid.InvalidJidException;
import in.gndec.sunehag.xmpp.jid.Jid;
import in.gndec.sunehag.xmpp.pep.Avatar;

public class EditAccountActivity extends OmemoActivity implements OnAccountUpdate,
		OnKeyStatusUpdated, OnCaptchaRequested, KeyChainAliasCallback, XmppConnectionService.OnShowErrorToast, XmppConnectionService.OnMamPreferencesFetched {

	private static final int REQUEST_DATA_SAVER = 0x37af244;
	private AutoCompleteTextView mAccountJid;
	private EditText mPassword;
	private EditText mPasswordConfirm;
	private CheckBox mRegisterNew;
	private Button mCancelButton;
	private Button mSaveButton;
	private Button mDisableOsOptimizationsButton;
	private TextView mDisableOsOptimizationsHeadline;
	private TextView getmDisableOsOptimizationsBody;
	private TableLayout mMoreTable;

	private LinearLayout mStats;
	private RelativeLayout mOsOptimizations;
	private TextView mServerInfoSm;
	private TextView mServerInfoRosterVersion;
	private TextView mServerInfoCarbons;
	private TextView mServerInfoMam;
	private TextView mServerInfoCSI;
	private TextView mServerInfoBlocking;
	private TextView mServerInfoPep;
	private TextView mServerInfoHttpUpload;
	private TextView mServerInfoPush;
	private TextView mSessionEst;
	private TextView mOtrFingerprint;
	private TextView mAxolotlFingerprint;
	private TextView mOwnFingerprintDesc;
	private TextView mAccountJidLabel;
	private TextView forgotPassword;
	private ImageView mAvatar;
	private RelativeLayout mOtrFingerprintBox;
	private RelativeLayout mAxolotlFingerprintBox;
	private ImageButton mOtrFingerprintToClipboardButton;
	private ImageButton mAxolotlFingerprintToClipboardButton;
	private ImageButton mRegenerateAxolotlKeyButton;
	private LinearLayout keys;
	private LinearLayout keysCard;
	private LinearLayout mNamePort;
	private EditText mHostname;
	private EditText mPort;
	private AlertDialog mCaptchaDialog = null;

	private Jid jidToEdit;
	private boolean mInitMode = false;
	private boolean mUsernameMode = Config.DOMAIN_LOCK != null;
	private boolean mShowOptions = false;
	private Account mAccount;
	private String messageFingerprint;

	private boolean mFetchingAvatar = false;

	private final OnClickListener mSaveButtonClickListener = new OnClickListener() {

		@Override
		public void onClick(final View v) {
			final String password = mPassword.getText().toString();
			final String passwordConfirm = mPasswordConfirm.getText().toString();

			if (!mInitMode && passwordChangedInMagicCreateMode()) {
				//	gotoChangePassword(password);
				return;
			}
			if (mInitMode && mAccount != null) {
				mAccount.setOption(Account.OPTION_DISABLED, false);
			}
			if (mAccount != null && mAccount.getStatus() == Account.State.DISABLED && !accountInfoEdited()) {
				mAccount.setOption(Account.OPTION_DISABLED, false);
				if (!xmppConnectionService.updateAccount(mAccount)) {
					Toast.makeText(EditAccountActivity.this,R.string.unable_to_update_account,Toast.LENGTH_SHORT).show();
				}
				return;
			}
			final boolean registerNewAccount = mRegisterNew.isChecked() && !Config.DISALLOW_REGISTRATION_IN_UI;
			if (mUsernameMode && mAccountJid.getText().toString().contains("@")) {
				mAccountJid.setError(getString(R.string.invalid_username));
				mAccountJid.requestFocus();
				return;
			}
			final Jid jid;
			try {
				if (mUsernameMode) {
					jid = Jid.fromParts(mAccountJid.getText().toString(), getUserModeDomain(), null);
				} else {
					jid = Jid.fromString(mAccountJid.getText().toString());
				}
			} catch (final InvalidJidException e) {
				if (mUsernameMode) {
					mAccountJid.setError(getString(R.string.invalid_username));
				} else {
					mAccountJid.setError(getString(R.string.invalid_jid));
				}
				mAccountJid.requestFocus();
				return;
			}
			String hostname = null;
			int numericPort = 5222;
			if (mShowOptions) {
				hostname = mHostname.getText().toString().replaceAll("\\s","");
				final String port = mPort.getText().toString().replaceAll("\\s","");
				if (hostname.contains(" ")) {
					mHostname.setError(getString(R.string.not_valid_hostname));
					mHostname.requestFocus();
					return;
				}
				try {
					numericPort = Integer.parseInt(port);
					if (numericPort < 0 || numericPort > 65535) {
						mPort.setError(getString(R.string.not_a_valid_port));
						mPort.requestFocus();
						return;
					}

				} catch (NumberFormatException e) {
					mPort.setError(getString(R.string.not_a_valid_port));
					mPort.requestFocus();
					return;
				}
			}

			if (jid.isDomainJid()) {
				if (mUsernameMode) {
					mAccountJid.setError(getString(R.string.invalid_username));
				} else {
					mAccountJid.setError(getString(R.string.invalid_jid));
				}
				mAccountJid.requestFocus();
				return;
			}
			if (password.isEmpty()) {
				mPassword.setError(getString(R.string.empty_password_error_message));
				mPassword.requestFocus();
				return;
			}
			if (registerNewAccount) {
				if (!password.equals(passwordConfirm)) {
					mPasswordConfirm.setError(getString(R.string.passwords_do_not_match));
					mPasswordConfirm.requestFocus();
					return;
				}
			}
			if (mAccount != null) {
				if (mInitMode && mAccount.isOptionSet(Account.OPTION_MAGIC_CREATE)) {
					mAccount.setOption(Account.OPTION_MAGIC_CREATE, mAccount.getPassword().contains(password));
				}
				mAccount.setJid(jid);
				mAccount.setPort(numericPort);
				mAccount.setHostname(hostname);
				mAccountJid.setError(null);
				mPasswordConfirm.setError(null);
				mAccount.setPassword(password);
				mAccount.setOption(Account.OPTION_REGISTER, registerNewAccount);
				if (!xmppConnectionService.updateAccount(mAccount)) {
					Toast.makeText(EditAccountActivity.this,R.string.unable_to_update_account,Toast.LENGTH_SHORT).show();
					return;
				}
			} else {
				if (xmppConnectionService.findAccountByJid(jid) != null) {
					mAccountJid.setError(getString(R.string.account_already_exists));
					mAccountJid.requestFocus();
					return;
				}
				mAccount = new Account(jid.toBareJid(), password);
				mAccount.setPort(numericPort);
				mAccount.setHostname(hostname);
				mAccount.setOption(Account.OPTION_USETLS, true);
				mAccount.setOption(Account.OPTION_USECOMPRESSION, true);
				mAccount.setOption(Account.OPTION_REGISTER, registerNewAccount);
				xmppConnectionService.createAccount(mAccount);
			}
			mHostname.setError(null);
			mPort.setError(null);
			if (!mAccount.isOptionSet(Account.OPTION_DISABLED)
					&& !registerNewAccount
					&& !mInitMode) {
				finish();
			} else {
				updateSaveButton();
				updateAccountInformation(true);
			}

		}
	};
	private final OnClickListener mCancelButtonClickListener = new OnClickListener() {

		@Override
		public void onClick(final View v) {
			deleteMagicCreatedAccountAndReturnIfNecessary();
			finish();
		}
	};
	private Toast mFetchingMamPrefsToast;
	private TableRow mPushRow;
	private String mSavedInstanceAccount;
	private boolean mSavedInstanceInit = false;
	private Button mClearDevicesButton;

	public void refreshUiReal() {
		invalidateOptionsMenu();
		if (mAccount != null
				&& mAccount.getStatus() != Account.State.ONLINE
				&& mFetchingAvatar) {
			startActivity(new Intent(getApplicationContext(),
					ManageAccountActivity.class));
			finish();
		} else if (mInitMode && mAccount != null && mAccount.getStatus() == Account.State.ONLINE) {
			if (!mFetchingAvatar) {
				mFetchingAvatar = true;
				xmppConnectionService.checkForAvatar(mAccount, mAvatarFetchCallback);
			}
		}
		if (mAccount != null) {
			updateAccountInformation(false);
		}
		updateSaveButton();
	}

	@Override
	public boolean onNavigateUp() {
		deleteMagicCreatedAccountAndReturnIfNecessary();
		return super.onNavigateUp();
	}

	@Override
	public void onBackPressed() {
		deleteMagicCreatedAccountAndReturnIfNecessary();
		super.onBackPressed();
	}

	private void deleteMagicCreatedAccountAndReturnIfNecessary() {
		if (Config.MAGIC_CREATE_DOMAIN != null
				&& mAccount != null
				&& mAccount.isOptionSet(Account.OPTION_MAGIC_CREATE)
				&& mAccount.isOptionSet(Account.OPTION_REGISTER)
				&& xmppConnectionService.getAccounts().size() == 1) {
			xmppConnectionService.deleteAccount(mAccount);
			startActivity(new Intent(EditAccountActivity.this, WelcomeActivity.class));
		}
	}

	@Override
	public void onAccountUpdate() {
		refreshUi();
	}

	private final UiCallback<Avatar> mAvatarFetchCallback = new UiCallback<Avatar>() {

		@Override
		public void userInputRequried(final PendingIntent pi, final Avatar avatar) {
			finishInitialSetup(avatar);
		}

		@Override
		public void success(final Avatar avatar) {
			finishInitialSetup(avatar);
		}

		@Override
		public void error(final int errorCode, final Avatar avatar) {
			finishInitialSetup(avatar);
		}
	};
	private final TextWatcher mTextWatcher = new TextWatcher() {

		@Override
		public void onTextChanged(final CharSequence s, final int start, final int before, final int count) {
			updateSaveButton();
		}

		@Override
		public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after) {
		}

		@Override
		public void afterTextChanged(final Editable s) {

		}
	};

	private final OnClickListener mAvatarClickListener = new OnClickListener() {
		@Override
		public void onClick(final View view) {
			if (mAccount != null) {
				final Intent intent = new Intent(getApplicationContext(), PublishProfilePictureActivity.class);
				intent.putExtra(EXTRA_ACCOUNT, mAccount.getJid().toBareJid().toString());
				startActivity(intent);
			}
		}
	};

	protected void finishInitialSetup(final Avatar avatar) {
		runOnUiThread(new Runnable() {

			@Override
			public void run() {
				final Intent intent;
				final XmppConnection connection = mAccount.getXmppConnection();
				final boolean wasFirstAccount = xmppConnectionService != null && xmppConnectionService.getAccounts().size() == 1;
				if (avatar != null || (connection != null && !connection.getFeatures().pep())) {
					intent = new Intent(getApplicationContext(), StartConversationActivity.class);
					if (wasFirstAccount) {
						intent.putExtra("init", true);
					}
				} else {
					intent = new Intent(getApplicationContext(), PublishProfilePictureActivity.class);
					intent.putExtra(EXTRA_ACCOUNT, mAccount.getJid().toBareJid().toString());
					intent.putExtra("setup", true);
				}
				if (wasFirstAccount) {
					intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
				}
				startActivity(intent);
				finish();
			}
		});
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == REQUEST_BATTERY_OP || requestCode == REQUEST_DATA_SAVER) {
			updateAccountInformation(mAccount == null);
		}
	}

	protected void updateSaveButton() {
		boolean accountInfoEdited = accountInfoEdited();

		if (!mInitMode && passwordChangedInMagicCreateMode()) {
			this.mSaveButton.setText(R.string.change_password);
			this.mSaveButton.setEnabled(true);
			this.mSaveButton.setTextColor(getPrimaryTextColor());
		} else if (accountInfoEdited && !mInitMode) {
			this.mSaveButton.setText(R.string.save);
			this.mSaveButton.setEnabled(true);
			this.mSaveButton.setTextColor(getPrimaryTextColor());
		} else if (mAccount != null
				&& (mAccount.getStatus() == Account.State.CONNECTING || mAccount.getStatus() == Account.State.REGISTRATION_SUCCESSFUL|| mFetchingAvatar)) {
			this.mSaveButton.setEnabled(false);
			this.mSaveButton.setTextColor(getSecondaryTextColor());
			this.mSaveButton.setText(R.string.account_status_connecting);
		} else if (mAccount != null && mAccount.getStatus() == Account.State.DISABLED && !mInitMode) {
			this.mSaveButton.setEnabled(true);
			this.mSaveButton.setTextColor(getPrimaryTextColor());
			this.mSaveButton.setText(R.string.enable);
		} else {
			this.mSaveButton.setEnabled(true);
			this.mSaveButton.setTextColor(getPrimaryTextColor());
			if (!mInitMode) {
				if (mAccount != null && mAccount.isOnlineAndConnected()) {
					this.mSaveButton.setText(R.string.save);
					if (!accountInfoEdited) {
						this.mSaveButton.setEnabled(false);
						this.mSaveButton.setTextColor(getSecondaryTextColor());
					}
				} else {
					this.mSaveButton.setText(R.string.connect);
				}
			} else {
				this.mSaveButton.setText(R.string.next);
			}
		}
	}

	protected boolean accountInfoEdited() {
		if (this.mAccount == null) {
			return false;
		}
		return jidEdited() ||
				!this.mAccount.getPassword().equals(this.mPassword.getText().toString()) ||
				!this.mAccount.getHostname().equals(this.mHostname.getText().toString()) ||
				!String.valueOf(this.mAccount.getPort()).equals(this.mPort.getText().toString());
	}

	protected boolean jidEdited() {
		final String unmodified;
		if (mUsernameMode) {
			unmodified = this.mAccount.getJid().getLocalpart();
		} else {
			unmodified = this.mAccount.getJid().toBareJid().toString();
		}
		return !unmodified.equals(this.mAccountJid.getText().toString());
	}

	protected boolean passwordChangedInMagicCreateMode() {
		return mAccount != null
				&& mAccount.isOptionSet(Account.OPTION_MAGIC_CREATE)
				&& !this.mAccount.getPassword().equals(this.mPassword.getText().toString())
				&& !this.jidEdited()
				&& mAccount.isOnlineAndConnected();
	}

	@Override
	protected String getShareableUri() {
		if (mAccount != null) {
			return mAccount.getShareableUri();
		} else {
			return "";
		}
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (savedInstanceState != null) {
			this.mSavedInstanceAccount = savedInstanceState.getString("account");
			this.mSavedInstanceInit = savedInstanceState.getBoolean("initMode", false);
		}
		setContentView(R.layout.activity_edit_account);
		this.forgotPassword = (TextView) findViewById(R.id.edit_account_forgot_password);
		forgotPassword.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent browserIntent = new Intent(Intent.ACTION_VIEW);
				browserIntent.setData(Uri.parse(Config.FORGOT_PASSWORD_URL));
				startActivity(browserIntent);
			}
		});
		this.mAccountJid = (AutoCompleteTextView) findViewById(R.id.account_jid);
		this.mAccountJid.addTextChangedListener(this.mTextWatcher);
		this.mAccountJidLabel = (TextView) findViewById(R.id.account_jid_label);
		this.mPassword = (EditText) findViewById(R.id.account_password);
		this.mPassword.addTextChangedListener(this.mTextWatcher);
		this.mPasswordConfirm = (EditText) findViewById(R.id.account_password_confirm);
		this.mAvatar = (ImageView) findViewById(R.id.avatar);
		this.mAvatar.setVisibility(View.VISIBLE);
		this.mAvatar.setImageResource(R.drawable.gne);
		this.mAvatar.setOnClickListener(this.mAvatarClickListener);
		this.mRegisterNew = (CheckBox) findViewById(R.id.account_register_new);
		this.mStats = (LinearLayout) findViewById(R.id.stats);
		this.mOsOptimizations = (RelativeLayout) findViewById(R.id.os_optimization);
		this.mDisableOsOptimizationsButton = (Button) findViewById(R.id.os_optimization_disable);
		this.mDisableOsOptimizationsHeadline = (TextView) findViewById(R.id.os_optimization_headline);
		this.getmDisableOsOptimizationsBody = (TextView) findViewById(R.id.os_optimization_body);
		this.mSessionEst = (TextView) findViewById(R.id.session_est);
		this.mServerInfoRosterVersion = (TextView) findViewById(R.id.server_info_roster_version);
		this.mServerInfoCarbons = (TextView) findViewById(R.id.server_info_carbons);
		this.mServerInfoMam = (TextView) findViewById(R.id.server_info_mam);
		this.mServerInfoCSI = (TextView) findViewById(R.id.server_info_csi);
		this.mServerInfoBlocking = (TextView) findViewById(R.id.server_info_blocking);
		this.mServerInfoSm = (TextView) findViewById(R.id.server_info_sm);
		this.mServerInfoPep = (TextView) findViewById(R.id.server_info_pep);
		this.mServerInfoHttpUpload = (TextView) findViewById(R.id.server_info_http_upload);
		this.mPushRow = (TableRow) findViewById(R.id.push_row);
		this.mServerInfoPush = (TextView) findViewById(R.id.server_info_push);
		this.mOtrFingerprint = (TextView) findViewById(R.id.otr_fingerprint);
		this.mOtrFingerprintBox = (RelativeLayout) findViewById(R.id.otr_fingerprint_box);
		this.mOtrFingerprintToClipboardButton = (ImageButton) findViewById(R.id.action_copy_to_clipboard);
		this.mAxolotlFingerprint = (TextView) findViewById(R.id.axolotl_fingerprint);
		this.mAxolotlFingerprintBox = (RelativeLayout) findViewById(R.id.axolotl_fingerprint_box);
		this.mAxolotlFingerprintToClipboardButton = (ImageButton) findViewById(R.id.action_copy_axolotl_to_clipboard);
		this.mRegenerateAxolotlKeyButton = (ImageButton) findViewById(R.id.action_regenerate_axolotl_key);
		this.mOwnFingerprintDesc = (TextView) findViewById(R.id.own_fingerprint_desc);
		this.keysCard = (LinearLayout) findViewById(R.id.other_device_keys_card);
		this.keys = (LinearLayout) findViewById(R.id.other_device_keys);
		this.mNamePort = (LinearLayout) findViewById(R.id.name_port);
		this.mHostname = (EditText) findViewById(R.id.hostname);
		this.mHostname.addTextChangedListener(mTextWatcher);
		this.mClearDevicesButton = (Button) findViewById(R.id.clear_devices);
		this.mClearDevicesButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				showWipePepDialog();
			}
		});
		this.mPort = (EditText) findViewById(R.id.port);
		this.mPort.setText("5222");
		this.mPort.addTextChangedListener(mTextWatcher);
		this.mSaveButton = (Button) findViewById(R.id.save_button);
		this.mCancelButton = (Button) findViewById(R.id.cancel_button);
		this.mSaveButton.setOnClickListener(this.mSaveButtonClickListener);
		this.mCancelButton.setOnClickListener(this.mCancelButtonClickListener);
		this.mMoreTable = (TableLayout) findViewById(R.id.server_info_more);
		if (savedInstanceState != null && savedInstanceState.getBoolean("showMoreTable")) {
			changeMoreTableVisibility(true);
		}
		final OnCheckedChangeListener OnCheckedShowConfirmPassword = new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(final CompoundButton buttonView,
										 final boolean isChecked) {
				if (isChecked) {
					mPasswordConfirm.setVisibility(View.VISIBLE);
				} else {
					mPasswordConfirm.setVisibility(View.GONE);
				}
				updateSaveButton();
			}
		};
		this.mRegisterNew.setOnCheckedChangeListener(OnCheckedShowConfirmPassword);
		if (Config.DISALLOW_REGISTRATION_IN_UI) {
			this.mRegisterNew.setVisibility(View.GONE);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {
		super.onCreateOptionsMenu(menu);
		getMenuInflater().inflate(R.menu.editaccount, menu);
		final MenuItem showQrCode = menu.findItem(R.id.action_show_qr_code);
		final MenuItem showBlocklist = menu.findItem(R.id.action_show_block_list);
		final MenuItem showMoreInfo = menu.findItem(R.id.action_server_info_show_more);
		final MenuItem changePassword = menu.findItem(R.id.action_change_password_on_server);
		final MenuItem showPassword = menu.findItem(R.id.action_show_password);
		final MenuItem renewCertificate = menu.findItem(R.id.action_renew_certificate);
		final MenuItem mamPrefs = menu.findItem(R.id.action_mam_prefs);
		final MenuItem changePresence = menu.findItem(R.id.action_change_presence);

		renewCertificate.setVisible(mAccount != null && mAccount.getPrivateKeyAlias() != null);

		if (mAccount != null && mAccount.isOnlineAndConnected()) {
			if (!mAccount.getXmppConnection().getFeatures().blocking()) {
				showBlocklist.setVisible(false);
			}
			//if (!mAccount.getXmppConnection().getFeatures().register()) {
			//	changePassword.setVisible(false);
			//}
			mamPrefs.setVisible(mAccount.getXmppConnection().getFeatures().mam());
			changePresence.setVisible(manuallyChangePresence());
		} else {
			showQrCode.setVisible(false);
			showBlocklist.setVisible(false);
			showMoreInfo.setVisible(false);
			changePassword.setVisible(false);
			mamPrefs.setVisible(false);
			changePresence.setVisible(false);
		}

		if (mAccount != null) {
			showPassword.setVisible(mAccount.isOptionSet(Account.OPTION_MAGIC_CREATE)
					&& !mAccount.isOptionSet(Account.OPTION_REGISTER));
		} else {
			showPassword.setVisible(false);
		}
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		final MenuItem showMoreInfo = menu.findItem(R.id.action_server_info_show_more);
		if (showMoreInfo.isVisible()) {
			showMoreInfo.setChecked(mMoreTable.getVisibility() == View.VISIBLE);
		}
		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	protected void onStart() {
		super.onStart();
		final int theme = findTheme();
		if (this.mTheme != theme) {
			recreate();
		} else if (getIntent() != null) {
			try {
				this.jidToEdit = Jid.fromString(getIntent().getStringExtra("jid"));
			} catch (final InvalidJidException | NullPointerException ignored) {
				this.jidToEdit = null;
			}
			boolean init = getIntent().getBooleanExtra("init", false);
			this.mInitMode = init || this.jidToEdit == null;
			this.messageFingerprint = getIntent().getStringExtra("fingerprint");
			if (!mInitMode) {
				this.mRegisterNew.setVisibility(View.GONE);
				if (getActionBar() != null) {
					getActionBar().setTitle(getString(R.string.account_details));
				}
			} else {
				this.mAvatar.setVisibility(View.VISIBLE);
				ActionBar ab = getActionBar();
				if (ab != null) {
					if (init && Config.MAGIC_CREATE_DOMAIN == null) {
						ab.setDisplayShowHomeEnabled(false);
						ab.setDisplayHomeAsUpEnabled(false);
					}
					ab.setTitle(R.string.action_add_account);
				}
			}
		}
		SharedPreferences preferences = getPreferences();
		//boolean useTor = Config.FORCE_ORBOT || preferences.getBoolean("use_tor", false);
		//this.mShowOptions = useTor || preferences.getBoolean("show_connection_options", false);
		//mHostname.setHint(useTor ? R.string.hostname_or_onion : R.string.hostname_example);
		this.mNamePort.setVisibility(mShowOptions ? View.VISIBLE : View.GONE);
	}

	@Override
	public void onSaveInstanceState(final Bundle savedInstanceState) {
		if (mAccount != null) {
			savedInstanceState.putString("account", mAccount.getJid().toBareJid().toString());
			savedInstanceState.putBoolean("initMode", mInitMode);
			savedInstanceState.putBoolean("showMoreTable", mMoreTable.getVisibility() == View.VISIBLE);
		}
		super.onSaveInstanceState(savedInstanceState);
	}

	protected void onBackendConnected() {
		boolean init = true;
		if (mSavedInstanceAccount != null) {
			try {
				this.mAccount = xmppConnectionService.findAccountByJid(Jid.fromString(mSavedInstanceAccount));
				this.mInitMode = mSavedInstanceInit;
				init = false;
			} catch (InvalidJidException e) {
				this.mAccount = null;
			}

		} else if (this.jidToEdit != null) {
			this.mAccount = xmppConnectionService.findAccountByJid(jidToEdit);
		}

		if (mAccount != null) {
			this.mInitMode |= this.mAccount.isOptionSet(Account.OPTION_REGISTER);
			this.mUsernameMode |= mAccount.isOptionSet(Account.OPTION_MAGIC_CREATE) && mAccount.isOptionSet(Account.OPTION_REGISTER);
			if (this.mAccount.getPrivateKeyAlias() != null) {
				this.mPassword.setHint(R.string.authenticate_with_certificate);
				if (this.mInitMode) {
					this.mPassword.requestFocus();
				}
			}
			updateAccountInformation(init);
		}


		if (Config.MAGIC_CREATE_DOMAIN == null && this.xmppConnectionService.getAccounts().size() == 0) {
			this.mCancelButton.setEnabled(false);
			this.mCancelButton.setTextColor(getSecondaryTextColor());
		}
		if (mUsernameMode) {
			this.mAccountJidLabel.setText(R.string.username);
			this.mAccountJid.setHint(R.string.username_hint);
		} else {
			final KnownHostsAdapter mKnownHostsAdapter = new KnownHostsAdapter(this,
					R.layout.simple_list_item,
					xmppConnectionService.getKnownHosts());
			this.mAccountJid.setAdapter(mKnownHostsAdapter);
		}
		updateSaveButton();
		invalidateOptionsMenu();
	}

	private String getUserModeDomain() {
		if (mAccount != null) {
			return mAccount.getJid().getDomainpart();
		} else {
			return Config.DOMAIN_LOCK;
		}
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		switch (item.getItemId()) {
			case R.id.action_show_block_list:
				final Intent showBlocklistIntent = new Intent(this, BlocklistActivity.class);
				showBlocklistIntent.putExtra(EXTRA_ACCOUNT, mAccount.getJid().toString());
				startActivity(showBlocklistIntent);
				break;
			case R.id.action_server_info_show_more:
				changeMoreTableVisibility(!item.isChecked());
				break;
			case R.id.action_share:
				shareLink(false);
				break;
			case R.id.action_change_password_on_server:
				gotoLink(Config.CHANGE_PASSWORD_URL);
				break;
			case R.id.action_mam_prefs:
				editMamPrefs();
				break;
			case R.id.action_renew_certificate:
				renewCertificate();
				break;
			case R.id.action_change_presence:
				changePresence();
				break;
			case R.id.action_show_password:
				showPassword();
				break;
		}
		return super.onOptionsItemSelected(item);
	}

	private void shareLink(boolean http) {
		Intent intent = new Intent(Intent.ACTION_SEND);
		intent.setType("text/plain");
		String text;
		if (http) {
			text = "https://conversations.im/i/"+mAccount.getJid().toBareJid().toString();
		} else {
			text = mAccount.getShareableUri();
		}
		intent.putExtra(Intent.EXTRA_TEXT,text);
		startActivity(Intent.createChooser(intent, getText(R.string.share_with)));
	}

	private void changeMoreTableVisibility(boolean visible) {
		mMoreTable.setVisibility(visible ? View.VISIBLE : View.GONE);
	}

	private void gotoLink (String newPassword) {
		Intent changePasswordIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(newPassword));
		/*final Intent changePasswordIntent = new Intent(this, ChangePasswordActivity.class);
		changePasswordIntent.putExtra(EXTRA_ACCOUNT, mAccount.getJid().toString());
		if (newPassword != null) {
			changePasswordIntent.putExtra("password", newPassword);
		}*/
		startActivity(changePasswordIntent);
	}

	private void renewCertificate() {
		KeyChain.choosePrivateKeyAlias(this, this, null, null, null, -1, null);
	}

	private void changePresence() {
		Intent intent = new Intent(this, SetPresenceActivity.class);
		intent.putExtra(SetPresenceActivity.EXTRA_ACCOUNT,mAccount.getJid().toBareJid().toString());
		startActivity(intent);
	}

	@Override
	public void alias(String alias) {
		if (alias != null) {
			xmppConnectionService.updateKeyInAccount(mAccount, alias);
		}
	}

	private void updateAccountInformation(boolean init) {
		if (init) {
			this.mAccountJid.getEditableText().clear();
			if (mUsernameMode) {
				this.mAccountJid.getEditableText().append(this.mAccount.getJid().getLocalpart());
			} else {
				this.mAccountJid.getEditableText().append(this.mAccount.getJid().toBareJid().toString());
			}
			this.mPassword.setText(this.mAccount.getPassword());
			this.mHostname.setText("");
			this.mHostname.getEditableText().append(this.mAccount.getHostname());
			this.mPort.setText("");
			this.mPort.getEditableText().append(String.valueOf(this.mAccount.getPort()));
			this.mNamePort.setVisibility(mShowOptions ? View.VISIBLE : View.GONE);

		}

		if (!mInitMode) {
			this.mAvatar.setVisibility(View.VISIBLE);
			this.mAvatar.setImageBitmap(avatarService().get(this.mAccount, getPixel(72)));
		} else {
			this.mAvatar.setVisibility(View.VISIBLE);
			this.mAvatar.setImageResource(R.drawable.gne);
			//this.mAvatar.setVisibility(View.GONE);
		}
		if (this.mAccount.isOptionSet(Account.OPTION_REGISTER)) {
			this.mRegisterNew.setVisibility(View.VISIBLE);
			this.mRegisterNew.setChecked(true);
			this.mPasswordConfirm.setText(this.mAccount.getPassword());
		} else {
			this.mRegisterNew.setVisibility(View.GONE);
			this.mRegisterNew.setChecked(false);
		}
		if (this.mAccount.isOnlineAndConnected() && !this.mFetchingAvatar) {
			Features features = this.mAccount.getXmppConnection().getFeatures();
			this.mStats.setVisibility(View.VISIBLE);
			boolean showBatteryWarning = !xmppConnectionService.getPushManagementService().available(mAccount) && isOptimizingBattery();
			boolean showDataSaverWarning = isAffectedByDataSaver();
			showOsOptimizationWarning(showBatteryWarning,showDataSaverWarning);
			this.mSessionEst.setText(UIHelper.readableTimeDifferenceFull(this, this.mAccount.getXmppConnection()
					.getLastSessionEstablished()));
			if (features.rosterVersioning()) {
				this.mServerInfoRosterVersion.setText(R.string.server_info_available);
			} else {
				this.mServerInfoRosterVersion.setText(R.string.server_info_unavailable);
			}
			if (features.carbons()) {
				this.mServerInfoCarbons.setText(R.string.server_info_available);
			} else {
				this.mServerInfoCarbons
						.setText(R.string.server_info_unavailable);
			}
			if (features.mam()) {
				this.mServerInfoMam.setText(R.string.server_info_available);
			} else {
				this.mServerInfoMam.setText(R.string.server_info_unavailable);
			}
			if (features.csi()) {
				this.mServerInfoCSI.setText(R.string.server_info_available);
			} else {
				this.mServerInfoCSI.setText(R.string.server_info_unavailable);
			}
			if (features.blocking()) {
				this.mServerInfoBlocking.setText(R.string.server_info_available);
			} else {
				this.mServerInfoBlocking.setText(R.string.server_info_unavailable);
			}
			if (features.sm()) {
				this.mServerInfoSm.setText(R.string.server_info_available);
			} else {
				this.mServerInfoSm.setText(R.string.server_info_unavailable);
			}
			if (features.pep()) {
				AxolotlService axolotlService = this.mAccount.getAxolotlService();
				if (axolotlService != null && axolotlService.isPepBroken()) {
					this.mServerInfoPep.setText(R.string.server_info_broken);
				} else {
					this.mServerInfoPep.setText(R.string.server_info_available);
				}
			} else {
				this.mServerInfoPep.setText(R.string.server_info_unavailable);
			}
			if (features.httpUpload(0)) {
				this.mServerInfoHttpUpload.setText(R.string.server_info_available);
			} else {
				this.mServerInfoHttpUpload.setText(R.string.server_info_unavailable);
			}

			this.mPushRow.setVisibility(xmppConnectionService.getPushManagementService().isStub() ? View.GONE : View.VISIBLE);

			if (xmppConnectionService.getPushManagementService().available(mAccount)) {
				this.mServerInfoPush.setText(R.string.server_info_available);
			} else {
				this.mServerInfoPush.setText(R.string.server_info_unavailable);
			}
			final String otrFingerprint = this.mAccount.getOtrFingerprint();
			if (otrFingerprint != null && Config.supportOtr()) {
				this.mOtrFingerprintBox.setVisibility(View.VISIBLE);
				this.mOtrFingerprint.setText(CryptoHelper.prettifyFingerprint(otrFingerprint));
				this.mOtrFingerprintToClipboardButton
						.setVisibility(View.VISIBLE);
				this.mOtrFingerprintToClipboardButton
						.setOnClickListener(new View.OnClickListener() {

							@Override
							public void onClick(final View v) {

								if (copyTextToClipboard(otrFingerprint, R.string.otr_fingerprint)) {
									Toast.makeText(
											EditAccountActivity.this,
											R.string.toast_message_otr_fingerprint,
											Toast.LENGTH_SHORT).show();
								}
							}
						});
			} else {
				this.mOtrFingerprintBox.setVisibility(View.GONE);
			}
			final String ownAxolotlFingerprint = this.mAccount.getAxolotlService().getOwnFingerprint();
			if (ownAxolotlFingerprint != null && Config.supportOmemo()) {
				this.mAxolotlFingerprintBox.setVisibility(View.VISIBLE);
				if (ownAxolotlFingerprint.equals(messageFingerprint)) {
					this.mOwnFingerprintDesc.setTextColor(getResources().getColor(R.color.accent));
				} else {
					this.mOwnFingerprintDesc.setTextColor(getSecondaryTextColor());
				}
				this.mAxolotlFingerprint.setText(CryptoHelper.prettifyFingerprint(ownAxolotlFingerprint.substring(2)));
				this.mAxolotlFingerprintToClipboardButton
						.setVisibility(View.VISIBLE);
				this.mAxolotlFingerprintToClipboardButton
						.setOnClickListener(new View.OnClickListener() {

							@Override
							public void onClick(final View v) {
								copyOmemoFingerprint(ownAxolotlFingerprint);
							}
						});
				if (Config.SHOW_REGENERATE_AXOLOTL_KEYS_BUTTON) {
					this.mRegenerateAxolotlKeyButton
							.setVisibility(View.VISIBLE);
					this.mRegenerateAxolotlKeyButton
							.setOnClickListener(new View.OnClickListener() {

								@Override
								public void onClick(final View v) {
									showRegenerateAxolotlKeyDialog();
								}
							});
				}
			} else {
				this.mAxolotlFingerprintBox.setVisibility(View.GONE);
			}
			boolean hasKeys = false;
			keys.removeAllViews();
			for(XmppAxolotlSession session : mAccount.getAxolotlService().findOwnSessions()) {
				if (!session.getTrust().isCompromised()) {
					boolean highlight = session.getFingerprint().equals(messageFingerprint);
					addFingerprintRow(keys,session,highlight);
					hasKeys = true;
				}
			}
			if (hasKeys && Config.supportOmemo()) {
				keysCard.setVisibility(View.VISIBLE);
				Set<Integer> otherDevices = mAccount.getAxolotlService().getOwnDeviceIds();
				if (otherDevices == null || otherDevices.isEmpty()) {
					mClearDevicesButton.setVisibility(View.GONE);
				} else {
					mClearDevicesButton.setVisibility(View.VISIBLE);
				}
			} else {
				keysCard.setVisibility(View.GONE);
			}
		} else {
			if (this.mAccount.errorStatus()) {
				final EditText errorTextField;
				if (this.mAccount.getStatus() == Account.State.UNAUTHORIZED) {
					errorTextField = this.mPassword;
				} else if (mShowOptions
						&& this.mAccount.getStatus() == Account.State.SERVER_NOT_FOUND
						&& this.mHostname.getText().length() > 0) {
					errorTextField = this.mHostname;
				} else {
					errorTextField = this.mAccountJid;
				}
				errorTextField.setError(getString(this.mAccount.getStatus().getReadableId()));
				if (init || !accountInfoEdited()) {
					errorTextField.requestFocus();
				}
			} else {
				this.mAccountJid.setError(null);
				this.mPassword.setError(null);
				this.mHostname.setError(null);
			}
			this.mStats.setVisibility(View.GONE);
		}
	}

	private void showOsOptimizationWarning(boolean showBatteryWarning, boolean showDataSaverWarning) {
		this.mOsOptimizations.setVisibility(showBatteryWarning || showDataSaverWarning ? View.VISIBLE : View.GONE);
		if (showDataSaverWarning) {
			this.mDisableOsOptimizationsHeadline.setText(R.string.data_saver_enabled);
			this.getmDisableOsOptimizationsBody.setText(R.string.data_saver_enabled_explained);
			this.mDisableOsOptimizationsButton.setText(R.string.allow);
			this.mDisableOsOptimizationsButton.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					Intent intent = new Intent(Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS);
					Uri uri = Uri.parse("package:"+getPackageName());
					intent.setData(uri);
					try {
						startActivityForResult(intent, REQUEST_DATA_SAVER);
					} catch (ActivityNotFoundException e) {
						Toast.makeText(EditAccountActivity.this, R.string.device_does_not_support_data_saver, Toast.LENGTH_SHORT).show();
					}
				}
			});
		} else if (showBatteryWarning) {
			this.mDisableOsOptimizationsButton.setText(R.string.disable);
			this.mDisableOsOptimizationsHeadline.setText(R.string.battery_optimizations_enabled);
			this.getmDisableOsOptimizationsBody.setText(R.string.battery_optimizations_enabled_explained);
			this.mDisableOsOptimizationsButton.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
					Uri uri = Uri.parse("package:"+getPackageName());
					intent.setData(uri);
					try {
						startActivityForResult(intent, REQUEST_BATTERY_OP);
					} catch (ActivityNotFoundException e) {
						Toast.makeText(EditAccountActivity.this, R.string.device_does_not_support_battery_op, Toast.LENGTH_SHORT).show();
					}
				}
			});
		}
	}

	public void showRegenerateAxolotlKeyDialog() {
		Builder builder = new Builder(this);
		builder.setTitle("Regenerate Key");
		builder.setIconAttribute(android.R.attr.alertDialogIcon);
		builder.setMessage("Are you sure you want to regenerate your Identity Key? (This will also wipe all established sessions and contact Identity Keys)");
		builder.setNegativeButton(getString(R.string.cancel), null);
		builder.setPositiveButton("Yes",
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						mAccount.getAxolotlService().regenerateKeys(false);
					}
				});
		builder.create().show();
	}

	public void showWipePepDialog() {
		Builder builder = new Builder(this);
		builder.setTitle(getString(R.string.clear_other_devices));
		builder.setIconAttribute(android.R.attr.alertDialogIcon);
		builder.setMessage(getString(R.string.clear_other_devices_desc));
		builder.setNegativeButton(getString(R.string.cancel), null);
		builder.setPositiveButton(getString(R.string.accept),
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						mAccount.getAxolotlService().wipeOtherPepDevices();
					}
				});
		builder.create().show();
	}

	private void editMamPrefs() {
		this.mFetchingMamPrefsToast = Toast.makeText(this, R.string.fetching_mam_prefs, Toast.LENGTH_LONG);
		this.mFetchingMamPrefsToast.show();
		xmppConnectionService.fetchMamPreferences(mAccount, this);
	}

	private void showPassword() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		View view = getLayoutInflater().inflate(R.layout.dialog_show_password, null);
		TextView password = (TextView) view.findViewById(R.id.password);
		password.setText(mAccount.getPassword());
		builder.setTitle(R.string.password);
		builder.setView(view);
		builder.setPositiveButton(R.string.cancel, null);
		builder.create().show();
	}

	@Override
	public void onKeyStatusUpdated(AxolotlService.FetchStatus report) {
		refreshUi();
	}

	@Override
	public void onCaptchaRequested(final Account account, final String id, final Data data, final Bitmap captcha) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if ((mCaptchaDialog != null) && mCaptchaDialog.isShowing()) {
					mCaptchaDialog.dismiss();
				}
				final AlertDialog.Builder builder = new AlertDialog.Builder(EditAccountActivity.this);
				final View view = getLayoutInflater().inflate(R.layout.captcha, null);
				final ImageView imageView = (ImageView) view.findViewById(R.id.captcha);
				final EditText input = (EditText) view.findViewById(R.id.input);
				imageView.setImageBitmap(captcha);

				builder.setTitle(getString(R.string.captcha_required));
				builder.setView(view);

				builder.setPositiveButton(getString(R.string.ok),
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								String rc = input.getText().toString();
								data.put("username", account.getUsername());
								data.put("password", account.getPassword());
								data.put("ocr", rc);
								data.submit();

								if (xmppConnectionServiceBound) {
									xmppConnectionService.sendCreateAccountWithCaptchaPacket(
											account, id, data);
								}
							}
						});
				builder.setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						if (xmppConnectionService != null) {
							xmppConnectionService.sendCreateAccountWithCaptchaPacket(account, null, null);
						}
					}
				});

				builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
					@Override
					public void onCancel(DialogInterface dialog) {
						if (xmppConnectionService != null) {
							xmppConnectionService.sendCreateAccountWithCaptchaPacket(account, null, null);
						}
					}
				});
				mCaptchaDialog = builder.create();
				mCaptchaDialog.show();
			}
		});
	}

	public void onShowErrorToast(final int resId) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Toast.makeText(EditAccountActivity.this, resId, Toast.LENGTH_SHORT).show();
			}
		});
	}

	@Override
	public void onPreferencesFetched(final Element prefs) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (mFetchingMamPrefsToast != null) {
					mFetchingMamPrefsToast.cancel();
				}
				AlertDialog.Builder builder = new Builder(EditAccountActivity.this);
				builder.setTitle(R.string.server_side_mam_prefs);
				String defaultAttr = prefs.getAttribute("default");
				final List<String> defaults = Arrays.asList("never", "roster", "always");
				final AtomicInteger choice = new AtomicInteger(Math.max(0,defaults.indexOf(defaultAttr)));
				builder.setSingleChoiceItems(R.array.mam_prefs, choice.get(), new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						choice.set(which);
					}
				});
				builder.setNegativeButton(R.string.cancel, null);
				builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						prefs.setAttribute("default",defaults.get(choice.get()));
						xmppConnectionService.pushMamPreferences(mAccount, prefs);
					}
				});
				builder.create().show();
			}
		});
	}

	@Override
	public void onPreferencesFetchFailed() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (mFetchingMamPrefsToast != null) {
					mFetchingMamPrefsToast.cancel();
				}
				Toast.makeText(EditAccountActivity.this,R.string.unable_to_fetch_mam_prefs,Toast.LENGTH_LONG).show();
			}
		});
	}
}