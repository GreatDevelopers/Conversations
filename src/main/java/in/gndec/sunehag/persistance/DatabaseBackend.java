package in.gndec.sunehag.persistance;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteCantOpenDatabaseException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Environment;
import android.util.Base64;
import android.util.Log;
import android.util.Pair;

import org.json.JSONException;
import org.json.JSONObject;
import org.whispersystems.libaxolotl.AxolotlAddress;
import org.whispersystems.libaxolotl.IdentityKey;
import org.whispersystems.libaxolotl.IdentityKeyPair;
import org.whispersystems.libaxolotl.InvalidKeyException;
import org.whispersystems.libaxolotl.state.PreKeyRecord;
import org.whispersystems.libaxolotl.state.SessionRecord;
import org.whispersystems.libaxolotl.state.SignedPreKeyRecord;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import in.gndec.sunehag.Config;
import in.gndec.sunehag.crypto.axolotl.AxolotlService;
import in.gndec.sunehag.crypto.axolotl.FingerprintStatus;
import in.gndec.sunehag.crypto.axolotl.SQLiteAxolotlStore;
import in.gndec.sunehag.entities.Account;
import in.gndec.sunehag.entities.Contact;
import in.gndec.sunehag.entities.Conversation;
import in.gndec.sunehag.entities.Message;
import in.gndec.sunehag.entities.PresenceTemplate;
import in.gndec.sunehag.entities.Roster;
import in.gndec.sunehag.entities.ServiceDiscoveryResult;
import in.gndec.sunehag.xmpp.jid.InvalidJidException;
import in.gndec.sunehag.xmpp.jid.Jid;
import in.gndec.sunehag.utils.MimeUtils;


public class DatabaseBackend extends SQLiteOpenHelper {

	private static DatabaseBackend instance = null;

	private static final String DATABASE_NAME = "history";
	private static final int DATABASE_VERSION = 34;

	private static String CREATE_CONTATCS_STATEMENT = "create table "
			+ Contact.TABLENAME + "(" + Contact.ACCOUNT + " TEXT, "
			+ Contact.SERVERNAME + " TEXT, " + Contact.SYSTEMNAME + " TEXT,"
			+ Contact.JID + " TEXT," + Contact.KEYS + " TEXT,"
			+ Contact.PHOTOURI + " TEXT," + Contact.OPTIONS + " NUMBER,"
			+ Contact.SYSTEMACCOUNT + " NUMBER, " + Contact.AVATAR + " TEXT, "
			+ Contact.LAST_PRESENCE + " TEXT, " + Contact.LAST_TIME + " NUMBER, "
			+ Contact.GROUPS + " TEXT, FOREIGN KEY(" + Contact.ACCOUNT + ") REFERENCES "
			+ Account.TABLENAME + "(" + Account.UUID
			+ ") ON DELETE CASCADE, UNIQUE(" + Contact.ACCOUNT + ", "
			+ Contact.JID + ") ON CONFLICT REPLACE);";

	private static String CREATE_DISCOVERY_RESULTS_STATEMENT = "create table "
			+ ServiceDiscoveryResult.TABLENAME + "("
			+ ServiceDiscoveryResult.HASH + " TEXT, "
			+ ServiceDiscoveryResult.VER + " TEXT, "
			+ ServiceDiscoveryResult.RESULT + " TEXT, "
			+ "UNIQUE(" + ServiceDiscoveryResult.HASH + ", "
			+ ServiceDiscoveryResult.VER + ") ON CONFLICT REPLACE);";

	private static String CREATE_PRESENCE_TEMPLATES_STATEMENT = "CREATE TABLE "
			+ PresenceTemplate.TABELNAME + "("
			+ PresenceTemplate.UUID + " TEXT, "
			+ PresenceTemplate.LAST_USED + " NUMBER,"
			+ PresenceTemplate.MESSAGE + " TEXT,"
			+ PresenceTemplate.STATUS + " TEXT,"
			+ "UNIQUE("+PresenceTemplate.MESSAGE + "," +PresenceTemplate.STATUS+") ON CONFLICT REPLACE);";

	private static String CREATE_PREKEYS_STATEMENT = "CREATE TABLE "
			+ SQLiteAxolotlStore.PREKEY_TABLENAME + "("
			+ SQLiteAxolotlStore.ACCOUNT + " TEXT,  "
			+ SQLiteAxolotlStore.ID + " INTEGER, "
			+ SQLiteAxolotlStore.KEY + " TEXT, FOREIGN KEY("
			+ SQLiteAxolotlStore.ACCOUNT
			+ ") REFERENCES " + Account.TABLENAME + "(" + Account.UUID + ") ON DELETE CASCADE, "
			+ "UNIQUE( " + SQLiteAxolotlStore.ACCOUNT + ", "
			+ SQLiteAxolotlStore.ID
			+ ") ON CONFLICT REPLACE"
			+ ");";

	private static String CREATE_SIGNED_PREKEYS_STATEMENT = "CREATE TABLE "
			+ SQLiteAxolotlStore.SIGNED_PREKEY_TABLENAME + "("
			+ SQLiteAxolotlStore.ACCOUNT + " TEXT,  "
			+ SQLiteAxolotlStore.ID + " INTEGER, "
			+ SQLiteAxolotlStore.KEY + " TEXT, FOREIGN KEY("
			+ SQLiteAxolotlStore.ACCOUNT
			+ ") REFERENCES " + Account.TABLENAME + "(" + Account.UUID + ") ON DELETE CASCADE, "
			+ "UNIQUE( " + SQLiteAxolotlStore.ACCOUNT + ", "
			+ SQLiteAxolotlStore.ID
			+ ") ON CONFLICT REPLACE" +
			");";

	private static String CREATE_SESSIONS_STATEMENT = "CREATE TABLE "
			+ SQLiteAxolotlStore.SESSION_TABLENAME + "("
			+ SQLiteAxolotlStore.ACCOUNT + " TEXT,  "
			+ SQLiteAxolotlStore.NAME + " TEXT, "
			+ SQLiteAxolotlStore.DEVICE_ID + " INTEGER, "
			+ SQLiteAxolotlStore.KEY + " TEXT, FOREIGN KEY("
			+ SQLiteAxolotlStore.ACCOUNT
			+ ") REFERENCES " + Account.TABLENAME + "(" + Account.UUID + ") ON DELETE CASCADE, "
			+ "UNIQUE( " + SQLiteAxolotlStore.ACCOUNT + ", "
			+ SQLiteAxolotlStore.NAME + ", "
			+ SQLiteAxolotlStore.DEVICE_ID
			+ ") ON CONFLICT REPLACE"
			+ ");";

	private static String CREATE_IDENTITIES_STATEMENT = "CREATE TABLE "
			+ SQLiteAxolotlStore.IDENTITIES_TABLENAME + "("
			+ SQLiteAxolotlStore.ACCOUNT + " TEXT,  "
			+ SQLiteAxolotlStore.NAME + " TEXT, "
			+ SQLiteAxolotlStore.OWN + " INTEGER, "
			+ SQLiteAxolotlStore.FINGERPRINT + " TEXT, "
			+ SQLiteAxolotlStore.CERTIFICATE + " BLOB, "
			+ SQLiteAxolotlStore.TRUST + " TEXT, "
			+ SQLiteAxolotlStore.ACTIVE + " NUMBER, "
			+ SQLiteAxolotlStore.LAST_ACTIVATION + " NUMBER,"
			+ SQLiteAxolotlStore.KEY + " TEXT, FOREIGN KEY("
			+ SQLiteAxolotlStore.ACCOUNT
			+ ") REFERENCES " + Account.TABLENAME + "(" + Account.UUID + ") ON DELETE CASCADE, "
			+ "UNIQUE( " + SQLiteAxolotlStore.ACCOUNT + ", "
			+ SQLiteAxolotlStore.NAME + ", "
			+ SQLiteAxolotlStore.FINGERPRINT
			+ ") ON CONFLICT IGNORE"
			+ ");";

	private static String START_TIMES_TABLE = "start_times";

	private static String CREATE_START_TIMES_TABLE = "create table "+START_TIMES_TABLE+" (timestamp NUMBER);";

	private static String CREATE_MESSAGE_TIME_INDEX = "create INDEX message_time_index ON "+Message.TABLENAME+"("+Message.TIME_SENT+")";

	private DatabaseBackend(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL("PRAGMA foreign_keys=ON;");
		db.execSQL("create table " + Account.TABLENAME + "(" + Account.UUID+ " TEXT PRIMARY KEY,"
				+ Account.USERNAME + " TEXT,"
				+ Account.SERVER + " TEXT,"
				+ Account.PASSWORD + " TEXT,"
				+ Account.DISPLAY_NAME + " TEXT, "
				+ Account.STATUS + " TEXT,"
				+ Account.STATUS_MESSAGE + " TEXT,"
				+ Account.ROSTERVERSION + " TEXT,"
				+ Account.OPTIONS + " NUMBER, "
				+ Account.AVATAR + " TEXT, "
				+ Account.KEYS + " TEXT, "
				+ Account.HOSTNAME + " TEXT, "
				+ Account.PORT + " NUMBER DEFAULT 5222)");
		db.execSQL("create table " + Conversation.TABLENAME + " ("
				+ Conversation.UUID + " TEXT PRIMARY KEY, " + Conversation.NAME
				+ " TEXT, " + Conversation.CONTACT + " TEXT, "
				+ Conversation.ACCOUNT + " TEXT, " + Conversation.CONTACTJID
				+ " TEXT, " + Conversation.CREATED + " NUMBER, "
				+ Conversation.STATUS + " NUMBER, " + Conversation.MODE
				+ " NUMBER, " + Conversation.ATTRIBUTES + " TEXT, FOREIGN KEY("
				+ Conversation.ACCOUNT + ") REFERENCES " + Account.TABLENAME
				+ "(" + Account.UUID + ") ON DELETE CASCADE);");
		db.execSQL("create table " + Message.TABLENAME + "( " + Message.UUID
				+ " TEXT PRIMARY KEY, " + Message.CONVERSATION + " TEXT, "
				+ Message.TIME_SENT + " NUMBER, " + Message.COUNTERPART
				+ " TEXT, " + Message.TRUE_COUNTERPART + " TEXT,"
				+ Message.BODY + " TEXT, " + Message.ENCRYPTION + " NUMBER, "
				+ Message.STATUS + " NUMBER," + Message.TYPE + " NUMBER, "
				+ Message.RELATIVE_FILE_PATH + " TEXT, "
				+ Message.SERVER_MSG_ID + " TEXT, "
				+ Message.FINGERPRINT + " TEXT, "
				+ Message.CARBON + " INTEGER, "
				+ Message.EDITED + " TEXT, "
				+ Message.READ + " NUMBER DEFAULT 1, "
				+ Message.OOB + " INTEGER, "
				+ Message.ERROR_MESSAGE + " TEXT,"
				+ Message.REMOTE_MSG_ID + " TEXT, FOREIGN KEY("
				+ Message.CONVERSATION + ") REFERENCES "
				+ Conversation.TABLENAME + "(" + Conversation.UUID
				+ ") ON DELETE CASCADE);");
		db.execSQL(CREATE_MESSAGE_TIME_INDEX);
		db.execSQL(CREATE_CONTATCS_STATEMENT);
		db.execSQL(CREATE_DISCOVERY_RESULTS_STATEMENT);
		db.execSQL(CREATE_SESSIONS_STATEMENT);
		db.execSQL(CREATE_PREKEYS_STATEMENT);
		db.execSQL(CREATE_SIGNED_PREKEYS_STATEMENT);
		db.execSQL(CREATE_IDENTITIES_STATEMENT);
		db.execSQL(CREATE_PRESENCE_TEMPLATES_STATEMENT);
		db.execSQL(CREATE_START_TIMES_TABLE);
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		if (oldVersion < 2 && newVersion >= 2) {
			db.execSQL("update " + Account.TABLENAME + " set "
					+ Account.OPTIONS + " = " + Account.OPTIONS + " | 8");
		}
		if (oldVersion < 3 && newVersion >= 3) {
			db.execSQL("ALTER TABLE " + Message.TABLENAME + " ADD COLUMN "
					+ Message.TYPE + " NUMBER");
		}
		if (oldVersion < 5 && newVersion >= 5) {
			db.execSQL("DROP TABLE " + Contact.TABLENAME);
			db.execSQL(CREATE_CONTATCS_STATEMENT);
			db.execSQL("UPDATE " + Account.TABLENAME + " SET "
					+ Account.ROSTERVERSION + " = NULL");
		}
		if (oldVersion < 6 && newVersion >= 6) {
			db.execSQL("ALTER TABLE " + Message.TABLENAME + " ADD COLUMN "
					+ Message.TRUE_COUNTERPART + " TEXT");
		}
		if (oldVersion < 7 && newVersion >= 7) {
			db.execSQL("ALTER TABLE " + Message.TABLENAME + " ADD COLUMN "
					+ Message.REMOTE_MSG_ID + " TEXT");
			db.execSQL("ALTER TABLE " + Contact.TABLENAME + " ADD COLUMN "
					+ Contact.AVATAR + " TEXT");
			db.execSQL("ALTER TABLE " + Account.TABLENAME + " ADD COLUMN "
					+ Account.AVATAR + " TEXT");
		}
		if (oldVersion < 8 && newVersion >= 8) {
			db.execSQL("ALTER TABLE " + Conversation.TABLENAME + " ADD COLUMN "
					+ Conversation.ATTRIBUTES + " TEXT");
		}
		if (oldVersion < 9 && newVersion >= 9) {
			db.execSQL("ALTER TABLE " + Contact.TABLENAME + " ADD COLUMN "
					+ Contact.LAST_TIME + " NUMBER");
			db.execSQL("ALTER TABLE " + Contact.TABLENAME + " ADD COLUMN "
					+ Contact.LAST_PRESENCE + " TEXT");
		}
		if (oldVersion < 10 && newVersion >= 10) {
			db.execSQL("ALTER TABLE " + Message.TABLENAME + " ADD COLUMN "
					+ Message.RELATIVE_FILE_PATH + " TEXT");
		}
		if (oldVersion < 11 && newVersion >= 11) {
			db.execSQL("ALTER TABLE " + Contact.TABLENAME + " ADD COLUMN "
					+ Contact.GROUPS + " TEXT");
			db.execSQL("delete from " + Contact.TABLENAME);
			db.execSQL("update " + Account.TABLENAME + " set " + Account.ROSTERVERSION + " = NULL");
		}
		if (oldVersion < 12 && newVersion >= 12) {
			db.execSQL("ALTER TABLE " + Message.TABLENAME + " ADD COLUMN "
					+ Message.SERVER_MSG_ID + " TEXT");
		}
		if (oldVersion < 13 && newVersion >= 13) {
			db.execSQL("delete from " + Contact.TABLENAME);
			db.execSQL("update " + Account.TABLENAME + " set " + Account.ROSTERVERSION + " = NULL");
		}
		if (oldVersion < 14 && newVersion >= 14) {
			canonicalizeJids(db);
		}
		if (oldVersion < 15 && newVersion >= 15) {
			recreateAxolotlDb(db);
			db.execSQL("ALTER TABLE " + Message.TABLENAME + " ADD COLUMN "
					+ Message.FINGERPRINT + " TEXT");
		} else if (oldVersion < 22 && newVersion >= 22) {
			db.execSQL("ALTER TABLE " + SQLiteAxolotlStore.IDENTITIES_TABLENAME + " ADD COLUMN " + SQLiteAxolotlStore.CERTIFICATE);
		}
		if (oldVersion < 16 && newVersion >= 16) {
			db.execSQL("ALTER TABLE " + Message.TABLENAME + " ADD COLUMN "
					+ Message.CARBON + " INTEGER");
		}
		if (oldVersion < 19 && newVersion >= 19) {
			db.execSQL("ALTER TABLE " + Account.TABLENAME + " ADD COLUMN " + Account.DISPLAY_NAME + " TEXT");
		}
		if (oldVersion < 20 && newVersion >= 20) {
			db.execSQL("ALTER TABLE " + Account.TABLENAME + " ADD COLUMN " + Account.HOSTNAME + " TEXT");
			db.execSQL("ALTER TABLE " + Account.TABLENAME + " ADD COLUMN " + Account.PORT + " NUMBER DEFAULT 5222");
		}
		if (oldVersion < 26 && newVersion >= 26) {
			db.execSQL("ALTER TABLE " + Account.TABLENAME + " ADD COLUMN " + Account.STATUS + " TEXT");
			db.execSQL("ALTER TABLE " + Account.TABLENAME + " ADD COLUMN " + Account.STATUS_MESSAGE + " TEXT");
		}
		/* Any migrations that alter the Account table need to happen BEFORE this migration, as it
		 * depends on account de-serialization.
		 */
		if (oldVersion < 17 && newVersion >= 17) {
			List<Account> accounts = getAccounts(db);
			for (Account account : accounts) {
				String ownDeviceIdString = account.getKey(SQLiteAxolotlStore.JSONKEY_REGISTRATION_ID);
				if (ownDeviceIdString == null) {
					continue;
				}
				int ownDeviceId = Integer.valueOf(ownDeviceIdString);
				AxolotlAddress ownAddress = new AxolotlAddress(account.getJid().toBareJid().toPreppedString(), ownDeviceId);
				deleteSession(db, account, ownAddress);
				IdentityKeyPair identityKeyPair = loadOwnIdentityKeyPair(db, account);
				if (identityKeyPair != null) {
					String[] selectionArgs = {
							account.getUuid(),
							identityKeyPair.getPublicKey().getFingerprint().replaceAll("\\s", "")
					};
					ContentValues values = new ContentValues();
					values.put(SQLiteAxolotlStore.TRUSTED, 2);
					db.update(SQLiteAxolotlStore.IDENTITIES_TABLENAME, values,
							SQLiteAxolotlStore.ACCOUNT + " = ? AND "
									+ SQLiteAxolotlStore.FINGERPRINT + " = ? ",
							selectionArgs);
				} else {
					Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": could not load own identity key pair");
				}
			}
		}
		if (oldVersion < 18 && newVersion >= 18) {
			db.execSQL("ALTER TABLE " + Message.TABLENAME + " ADD COLUMN " + Message.READ + " NUMBER DEFAULT 1");
		}

		if (oldVersion < 21 && newVersion >= 21) {
			List<Account> accounts = getAccounts(db);
			for (Account account : accounts) {
				account.unsetPgpSignature();
				db.update(Account.TABLENAME, account.getContentValues(), Account.UUID
						+ "=?", new String[]{account.getUuid()});
			}
		}

		if (oldVersion < 23 && newVersion >= 23) {
			db.execSQL(CREATE_DISCOVERY_RESULTS_STATEMENT);
		}

		if (oldVersion < 24 && newVersion >= 24) {
			db.execSQL("ALTER TABLE " + Message.TABLENAME + " ADD COLUMN " + Message.EDITED + " TEXT");
		}

		if (oldVersion < 25 && newVersion >= 25) {
			db.execSQL("ALTER TABLE " + Message.TABLENAME + " ADD COLUMN " + Message.OOB + " INTEGER");
		}

		if (oldVersion <  26 && newVersion >= 26) {
			db.execSQL(CREATE_PRESENCE_TEMPLATES_STATEMENT);
		}

		if (oldVersion < 27 && newVersion >= 27) {
			db.execSQL("DELETE FROM "+ServiceDiscoveryResult.TABLENAME);
		}

		if (oldVersion < 28 && newVersion >= 28) {
			canonicalizeJids(db);
		}

		if (oldVersion < 29 && newVersion >= 29) {
			db.execSQL("ALTER TABLE " + Message.TABLENAME + " ADD COLUMN " + Message.ERROR_MESSAGE + " TEXT");
		}
		if (oldVersion < 30 && newVersion >= 30) {
			db.execSQL(CREATE_START_TIMES_TABLE);
		}
		if (oldVersion < 31 && newVersion >= 31) {
			db.execSQL("ALTER TABLE "+ SQLiteAxolotlStore.IDENTITIES_TABLENAME + " ADD COLUMN "+SQLiteAxolotlStore.TRUST + " TEXT");
			db.execSQL("ALTER TABLE "+ SQLiteAxolotlStore.IDENTITIES_TABLENAME + " ADD COLUMN "+SQLiteAxolotlStore.ACTIVE + " NUMBER");
			HashMap<Integer,ContentValues> migration = new HashMap<>();
			migration.put(0,createFingerprintStatusContentValues(FingerprintStatus.Trust.TRUSTED,true));
			migration.put(1,createFingerprintStatusContentValues(FingerprintStatus.Trust.TRUSTED, true));
			migration.put(2,createFingerprintStatusContentValues(FingerprintStatus.Trust.UNTRUSTED, true));
			migration.put(3,createFingerprintStatusContentValues(FingerprintStatus.Trust.COMPROMISED, false));
			migration.put(4,createFingerprintStatusContentValues(FingerprintStatus.Trust.TRUSTED, false));
			migration.put(5,createFingerprintStatusContentValues(FingerprintStatus.Trust.TRUSTED, false));
			migration.put(6,createFingerprintStatusContentValues(FingerprintStatus.Trust.UNTRUSTED, false));
			migration.put(7,createFingerprintStatusContentValues(FingerprintStatus.Trust.VERIFIED_X509, true));
			migration.put(8,createFingerprintStatusContentValues(FingerprintStatus.Trust.VERIFIED_X509, false));
			for(Map.Entry<Integer,ContentValues> entry : migration.entrySet()) {
				String whereClause = SQLiteAxolotlStore.TRUSTED+"=?";
				String[] where = {String.valueOf(entry.getKey())};
				db.update(SQLiteAxolotlStore.IDENTITIES_TABLENAME,entry.getValue(),whereClause,where);
			}

		}
		if (oldVersion < 32 && newVersion >= 32) {
			db.execSQL("ALTER TABLE "+ SQLiteAxolotlStore.IDENTITIES_TABLENAME + " ADD COLUMN "+SQLiteAxolotlStore.LAST_ACTIVATION + " NUMBER");
			ContentValues defaults = new ContentValues();
			defaults.put(SQLiteAxolotlStore.LAST_ACTIVATION,System.currentTimeMillis());
			db.update(SQLiteAxolotlStore.IDENTITIES_TABLENAME,defaults,null,null);
		}
		if (oldVersion < 33 && newVersion >= 33) {
			String whereClause = SQLiteAxolotlStore.OWN+"=1";
			db.update(SQLiteAxolotlStore.IDENTITIES_TABLENAME,createFingerprintStatusContentValues(FingerprintStatus.Trust.VERIFIED,true),whereClause,null);
		}

		if (oldVersion < 34 && newVersion >= 34) {
			db.execSQL(CREATE_MESSAGE_TIME_INDEX);

			final File oldPicturesDirectory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)+"/Conversations/");
			final File oldFilesDirectory = new File(Environment.getExternalStorageDirectory() + "/Conversations/");
			final File newFilesDirectory = new File(Environment.getExternalStorageDirectory() + "/Conversations/Media/Conversations Files/");
			final File newVideosDirectory = new File(Environment.getExternalStorageDirectory() + "/Conversations/Media/Conversations Videos/");
			if (oldPicturesDirectory.exists() && oldPicturesDirectory.isDirectory()) {
				final File newPicturesDirectory = new File(Environment.getExternalStorageDirectory() + "/Conversations/Media/Conversations Images/");
				newPicturesDirectory.getParentFile().mkdirs();
				if (oldPicturesDirectory.renameTo(newPicturesDirectory)) {
					Log.d(Config.LOGTAG,"moved "+oldPicturesDirectory.getAbsolutePath()+" to "+newPicturesDirectory.getAbsolutePath());
				}
			}
			if (oldFilesDirectory.exists() && oldFilesDirectory.isDirectory()) {
				newFilesDirectory.mkdirs();
				newVideosDirectory.mkdirs();
				for(File file : oldFilesDirectory.listFiles()) {
					if (file.getName().equals(".nomedia")) {
						if (file.delete()) {
							Log.d(Config.LOGTAG,"deleted nomedia file in "+oldFilesDirectory.getAbsolutePath());
						}
					} else if (file.isFile()) {
						final String name = file.getName();
						boolean isVideo = false;
						int start = name.lastIndexOf('.') + 1;
						if (start < name.length()) {
							String mime=  MimeUtils.guessMimeTypeFromExtension(name.substring(start));
							isVideo = mime != null && mime.startsWith("video/");
						}
						File dst = new File((isVideo ? newVideosDirectory : newFilesDirectory).getAbsolutePath()+"/"+file.getName());
						if (file.renameTo(dst)) {
							Log.d(Config.LOGTAG, "moved " + file + " to " + dst);
						}
					}
				}
			}
		}
	}

	private static ContentValues createFingerprintStatusContentValues(FingerprintStatus.Trust trust, boolean active) {
		ContentValues values = new ContentValues();
		values.put(SQLiteAxolotlStore.TRUST,trust.toString());
		values.put(SQLiteAxolotlStore.ACTIVE,active ? 1 : 0);
		return values;
	}

	private void canonicalizeJids(SQLiteDatabase db) {
		// migrate db to new, canonicalized JID domainpart representation

		// Conversation table
		Cursor cursor = db.rawQuery("select * from " + Conversation.TABLENAME, new String[0]);
		while (cursor.moveToNext()) {
			String newJid;
			try {
				newJid = Jid.fromString(
						cursor.getString(cursor.getColumnIndex(Conversation.CONTACTJID))
				).toPreppedString();
			} catch (InvalidJidException ignored) {
				Log.e(Config.LOGTAG, "Failed to migrate Conversation CONTACTJID "
						+ cursor.getString(cursor.getColumnIndex(Conversation.CONTACTJID))
						+ ": " + ignored + ". Skipping...");
				continue;
			}

			String updateArgs[] = {
					newJid,
					cursor.getString(cursor.getColumnIndex(Conversation.UUID)),
			};
			db.execSQL("update " + Conversation.TABLENAME
					+ " set " + Conversation.CONTACTJID + " = ? "
					+ " where " + Conversation.UUID + " = ?", updateArgs);
		}
		cursor.close();

		// Contact table
		cursor = db.rawQuery("select * from " + Contact.TABLENAME, new String[0]);
		while (cursor.moveToNext()) {
			String newJid;
			try {
				newJid = Jid.fromString(
						cursor.getString(cursor.getColumnIndex(Contact.JID))
				).toPreppedString();
			} catch (InvalidJidException ignored) {
				Log.e(Config.LOGTAG, "Failed to migrate Contact JID "
						+ cursor.getString(cursor.getColumnIndex(Contact.JID))
						+ ": " + ignored + ". Skipping...");
				continue;
			}

			String updateArgs[] = {
					newJid,
					cursor.getString(cursor.getColumnIndex(Contact.ACCOUNT)),
					cursor.getString(cursor.getColumnIndex(Contact.JID)),
			};
			db.execSQL("update " + Contact.TABLENAME
					+ " set " + Contact.JID + " = ? "
					+ " where " + Contact.ACCOUNT + " = ? "
					+ " AND " + Contact.JID + " = ?", updateArgs);
		}
		cursor.close();

		// Account table
		cursor = db.rawQuery("select * from " + Account.TABLENAME, new String[0]);
		while (cursor.moveToNext()) {
			String newServer;
			try {
				newServer = Jid.fromParts(
						cursor.getString(cursor.getColumnIndex(Account.USERNAME)),
						cursor.getString(cursor.getColumnIndex(Account.SERVER)),
						"mobile"
				).getDomainpart();
			} catch (InvalidJidException ignored) {
				Log.e(Config.LOGTAG, "Failed to migrate Account SERVER "
						+ cursor.getString(cursor.getColumnIndex(Account.SERVER))
						+ ": " + ignored + ". Skipping...");
				continue;
			}

			String updateArgs[] = {
					newServer,
					cursor.getString(cursor.getColumnIndex(Account.UUID)),
			};
			db.execSQL("update " + Account.TABLENAME
					+ " set " + Account.SERVER + " = ? "
					+ " where " + Account.UUID + " = ?", updateArgs);
		}
		cursor.close();
	}

	public static synchronized DatabaseBackend getInstance(Context context) {
		if (instance == null) {
			instance = new DatabaseBackend(context);
		}
		return instance;
	}

	public void createConversation(Conversation conversation) {
		SQLiteDatabase db = this.getWritableDatabase();
		db.insert(Conversation.TABLENAME, null, conversation.getContentValues());
	}

	public void createMessage(Message message) {
		SQLiteDatabase db = this.getWritableDatabase();
		db.insert(Message.TABLENAME, null, message.getContentValues());
	}

	public void createAccount(Account account) {
		SQLiteDatabase db = this.getWritableDatabase();
		db.insert(Account.TABLENAME, null, account.getContentValues());
	}

	public void insertDiscoveryResult(ServiceDiscoveryResult result) {
		SQLiteDatabase db = this.getWritableDatabase();
		db.insert(ServiceDiscoveryResult.TABLENAME, null, result.getContentValues());
	}

	public ServiceDiscoveryResult findDiscoveryResult(final String hash, final String ver) {
		SQLiteDatabase db = this.getReadableDatabase();
		String[] selectionArgs = {hash, ver};
		Cursor cursor = db.query(ServiceDiscoveryResult.TABLENAME, null,
				ServiceDiscoveryResult.HASH + "=? AND " + ServiceDiscoveryResult.VER + "=?",
				selectionArgs, null, null, null);
		if (cursor.getCount() == 0) {
			cursor.close();
			return null;
		}
		cursor.moveToFirst();

		ServiceDiscoveryResult result = null;
		try {
			result = new ServiceDiscoveryResult(cursor);
		} catch (JSONException e) { /* result is still null */ }

		cursor.close();
		return result;
	}

	public void insertPresenceTemplate(PresenceTemplate template) {
		SQLiteDatabase db = this.getWritableDatabase();
		db.insert(PresenceTemplate.TABELNAME, null, template.getContentValues());
	}

	public List<PresenceTemplate> getPresenceTemplates() {
		ArrayList<PresenceTemplate> templates = new ArrayList<>();
		SQLiteDatabase db = this.getReadableDatabase();
		Cursor cursor = db.query(PresenceTemplate.TABELNAME,null,null,null,null,null,PresenceTemplate.LAST_USED+" desc");
		while (cursor.moveToNext()) {
			templates.add(PresenceTemplate.fromCursor(cursor));
		}
		cursor.close();
		return templates;
	}

	public void deletePresenceTemplate(PresenceTemplate template) {
		Log.d(Config.LOGTAG,"deleting presence template with uuid "+template.getUuid());
		SQLiteDatabase db = this.getWritableDatabase();
		String where = PresenceTemplate.UUID+"=?";
		String[] whereArgs = {template.getUuid()};
		db.delete(PresenceTemplate.TABELNAME,where,whereArgs);
	}

	public CopyOnWriteArrayList<Conversation> getConversations(int status) {
		CopyOnWriteArrayList<Conversation> list = new CopyOnWriteArrayList<>();
		SQLiteDatabase db = this.getReadableDatabase();
		String[] selectionArgs = {Integer.toString(status)};
		Cursor cursor = db.rawQuery("select * from " + Conversation.TABLENAME
				+ " where " + Conversation.STATUS + " = ? order by "
				+ Conversation.CREATED + " desc", selectionArgs);
		while (cursor.moveToNext()) {
			list.add(Conversation.fromCursor(cursor));
		}
		cursor.close();
		return list;
	}

	public ArrayList<Message> getMessages(Conversation conversations, int limit) {
		return getMessages(conversations, limit, -1);
	}

	public ArrayList<Message> getMessages(Conversation conversation, int limit,
										  long timestamp) {
		ArrayList<Message> list = new ArrayList<>();
		SQLiteDatabase db = this.getReadableDatabase();
		Cursor cursor;
		if (timestamp == -1) {
			String[] selectionArgs = {conversation.getUuid()};
			cursor = db.query(Message.TABLENAME, null, Message.CONVERSATION
					+ "=?", selectionArgs, null, null, Message.TIME_SENT
					+ " DESC", String.valueOf(limit));
		} else {
			String[] selectionArgs = {conversation.getUuid(),
					Long.toString(timestamp)};
			cursor = db.query(Message.TABLENAME, null, Message.CONVERSATION
							+ "=? and " + Message.TIME_SENT + "<?", selectionArgs,
					null, null, Message.TIME_SENT + " DESC",
					String.valueOf(limit));
		}
		if (cursor.getCount() > 0) {
			cursor.moveToLast();
			do {
				Message message = Message.fromCursor(cursor);
				message.setConversation(conversation);
				list.add(message);
			} while (cursor.moveToPrevious());
		}
		cursor.close();
		return list;
	}

	public Iterable<Message> getMessagesIterable(final Conversation conversation) {
		return new Iterable<Message>() {
			@Override
			public Iterator<Message> iterator() {
				class MessageIterator implements Iterator<Message> {
					SQLiteDatabase db = getReadableDatabase();
					String[] selectionArgs = {conversation.getUuid()};
					Cursor cursor = db.query(Message.TABLENAME, null, Message.CONVERSATION
							+ "=?", selectionArgs, null, null, Message.TIME_SENT
							+ " ASC", null);

					public MessageIterator() {
						cursor.moveToFirst();
					}

					@Override
					public boolean hasNext() {
						return !cursor.isAfterLast();
					}

					@Override
					public Message next() {
						Message message = Message.fromCursor(cursor);
						cursor.moveToNext();
						return message;
					}

					@Override
					public void remove() {
						throw new UnsupportedOperationException();
					}
				}
				return new MessageIterator();
			}
		};
	}

	public Conversation findConversation(final Account account, final Jid contactJid) {
		SQLiteDatabase db = this.getReadableDatabase();
		String[] selectionArgs = {account.getUuid(),
				contactJid.toBareJid().toPreppedString() + "/%",
				contactJid.toBareJid().toPreppedString()
		};
		Cursor cursor = db.query(Conversation.TABLENAME, null,
				Conversation.ACCOUNT + "=? AND (" + Conversation.CONTACTJID
						+ " like ? OR " + Conversation.CONTACTJID + "=?)", selectionArgs, null, null, null);
		if (cursor.getCount() == 0) {
			cursor.close();
			return null;
		}
		cursor.moveToFirst();
		Conversation conversation = Conversation.fromCursor(cursor);
		cursor.close();
		return conversation;
	}

	public void updateConversation(final Conversation conversation) {
		final SQLiteDatabase db = this.getWritableDatabase();
		final String[] args = {conversation.getUuid()};
		db.update(Conversation.TABLENAME, conversation.getContentValues(),
				Conversation.UUID + "=?", args);
	}

	public List<Account> getAccounts() {
		SQLiteDatabase db = this.getReadableDatabase();
		return getAccounts(db);
	}

	private List<Account> getAccounts(SQLiteDatabase db) {
		List<Account> list = new ArrayList<>();
		Cursor cursor = db.query(Account.TABLENAME, null, null, null, null,
				null, null);
		while (cursor.moveToNext()) {
			list.add(Account.fromCursor(cursor));
		}
		cursor.close();
		return list;
	}

	public boolean updateAccount(Account account) {
		SQLiteDatabase db = this.getWritableDatabase();
		String[] args = {account.getUuid()};
		final int rows = db.update(Account.TABLENAME, account.getContentValues(), Account.UUID + "=?", args);
		return rows == 1;
	}

	public boolean deleteAccount(Account account) {
		SQLiteDatabase db = this.getWritableDatabase();
		String[] args = {account.getUuid()};
		final int rows = db.delete(Account.TABLENAME, Account.UUID + "=?", args);
		return rows == 1;
	}

	public boolean hasEnabledAccounts() {
		SQLiteDatabase db = this.getReadableDatabase();
		Cursor cursor = db.rawQuery("select count(" + Account.UUID + ")  from "
				+ Account.TABLENAME + " where not options & (1 <<1)", null);
		try {
			cursor.moveToFirst();
			int count = cursor.getInt(0);
			return (count > 0);
		} catch (SQLiteCantOpenDatabaseException e) {
			return true; // better safe than sorry
		} catch (RuntimeException e) {
			return true; // better safe than sorry
		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}
	}

	@Override
	public SQLiteDatabase getWritableDatabase() {
		SQLiteDatabase db = super.getWritableDatabase();
		db.execSQL("PRAGMA foreign_keys=ON;");
		return db;
	}

	public void updateMessage(Message message) {
		SQLiteDatabase db = this.getWritableDatabase();
		String[] args = {message.getUuid()};
		db.update(Message.TABLENAME, message.getContentValues(), Message.UUID
				+ "=?", args);
	}

	public void updateMessage(Message message, String uuid) {
		SQLiteDatabase db = this.getWritableDatabase();
		String[] args = {uuid};
		db.update(Message.TABLENAME, message.getContentValues(), Message.UUID
				+ "=?", args);
	}

	public void readRoster(Roster roster) {
		SQLiteDatabase db = this.getReadableDatabase();
		Cursor cursor;
		String args[] = {roster.getAccount().getUuid()};
		cursor = db.query(Contact.TABLENAME, null, Contact.ACCOUNT + "=?", args, null, null, null);
		while (cursor.moveToNext()) {
			roster.initContact(Contact.fromCursor(cursor));
		}
		cursor.close();
	}

	public void writeRoster(final Roster roster) {
		final Account account = roster.getAccount();
		final SQLiteDatabase db = this.getWritableDatabase();
		db.beginTransaction();
		for (Contact contact : roster.getContacts()) {
			if (contact.getOption(Contact.Options.IN_ROSTER)) {
				db.insert(Contact.TABLENAME, null, contact.getContentValues());
			} else {
				String where = Contact.ACCOUNT + "=? AND " + Contact.JID + "=?";
				String[] whereArgs = {account.getUuid(), contact.getJid().toPreppedString()};
				db.delete(Contact.TABLENAME, where, whereArgs);
			}
		}
		db.setTransactionSuccessful();
		db.endTransaction();
		account.setRosterVersion(roster.getVersion());
		updateAccount(account);
	}

	public void deleteMessagesInConversation(Conversation conversation) {
		SQLiteDatabase db = this.getWritableDatabase();
		String[] args = {conversation.getUuid()};
		db.delete(Message.TABLENAME, Message.CONVERSATION + "=?", args);
	}

	public boolean expireOldMessages(long timestamp) {
		String where = Message.TIME_SENT+"<?";
		String[] whereArgs = {String.valueOf(timestamp)};
		SQLiteDatabase db = this.getReadableDatabase();
		return db.delete(Message.TABLENAME,where,whereArgs) > 0;
	}

	public Pair<Long, String> getLastMessageReceived(Account account) {
		Cursor cursor = null;
		try {
			SQLiteDatabase db = this.getReadableDatabase();
			String sql = "select messages.timeSent,messages.serverMsgId from accounts join conversations on accounts.uuid=conversations.accountUuid join messages on conversations.uuid=messages.conversationUuid where accounts.uuid=? and (messages.status=0 or messages.carbon=1 or messages.serverMsgId not null) order by messages.timesent desc limit 1";
			String[] args = {account.getUuid()};
			cursor = db.rawQuery(sql, args);
			if (cursor.getCount() == 0) {
				return null;
			} else {
				cursor.moveToFirst();
				return new Pair<>(cursor.getLong(0), cursor.getString(1));
			}
		} catch (Exception e) {
			return null;
		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}
	}

	public long getLastTimeFingerprintUsed(Account account, String fingerprint) {
		String SQL = "select messages.timeSent from accounts join conversations on accounts.uuid=conversations.accountUuid join messages on conversations.uuid=messages.conversationUuid where accounts.uuid=? and messages.axolotl_fingerprint=? order by messages.timesent desc limit 1";
		String[] args = {account.getUuid(), fingerprint};
		Cursor cursor = getReadableDatabase().rawQuery(SQL,args);
		long time;
		if (cursor.moveToFirst()) {
			time = cursor.getLong(0);
		} else {
			time = 0;
		}
		cursor.close();
		return time;
	}

	public Pair<Long,String> getLastClearDate(Account account) {
		SQLiteDatabase db = this.getReadableDatabase();
		String[] columns = {Conversation.ATTRIBUTES};
		String selection = Conversation.ACCOUNT + "=?";
		String[] args = {account.getUuid()};
		Cursor cursor = db.query(Conversation.TABLENAME,columns,selection,args,null,null,null);
		long maxClearDate = 0;
		while (cursor.moveToNext()) {
			try {
				final JSONObject jsonObject = new JSONObject(cursor.getString(0));
				maxClearDate = Math.max(maxClearDate, jsonObject.getLong(Conversation.ATTRIBUTE_LAST_CLEAR_HISTORY));
			} catch (Exception e) {
				//ignored
			}
		}
		cursor.close();
		return new Pair<>(maxClearDate,null);
	}

	private Cursor getCursorForSession(Account account, AxolotlAddress contact) {
		final SQLiteDatabase db = this.getReadableDatabase();
		String[] selectionArgs = {account.getUuid(),
				contact.getName(),
				Integer.toString(contact.getDeviceId())};
		return db.query(SQLiteAxolotlStore.SESSION_TABLENAME,
				null,
				SQLiteAxolotlStore.ACCOUNT + " = ? AND "
						+ SQLiteAxolotlStore.NAME + " = ? AND "
						+ SQLiteAxolotlStore.DEVICE_ID + " = ? ",
				selectionArgs,
				null, null, null);
	}

	public SessionRecord loadSession(Account account, AxolotlAddress contact) {
		SessionRecord session = null;
		Cursor cursor = getCursorForSession(account, contact);
		if (cursor.getCount() != 0) {
			cursor.moveToFirst();
			try {
				session = new SessionRecord(Base64.decode(cursor.getString(cursor.getColumnIndex(SQLiteAxolotlStore.KEY)), Base64.DEFAULT));
			} catch (IOException e) {
				cursor.close();
				throw new AssertionError(e);
			}
		}
		cursor.close();
		return session;
	}

	public List<Integer> getSubDeviceSessions(Account account, AxolotlAddress contact) {
		final SQLiteDatabase db = this.getReadableDatabase();
		return getSubDeviceSessions(db, account, contact);
	}

	private List<Integer> getSubDeviceSessions(SQLiteDatabase db, Account account, AxolotlAddress contact) {
		List<Integer> devices = new ArrayList<>();
		String[] columns = {SQLiteAxolotlStore.DEVICE_ID};
		String[] selectionArgs = {account.getUuid(),
				contact.getName()};
		Cursor cursor = db.query(SQLiteAxolotlStore.SESSION_TABLENAME,
				columns,
				SQLiteAxolotlStore.ACCOUNT + " = ? AND "
						+ SQLiteAxolotlStore.NAME + " = ?",
				selectionArgs,
				null, null, null);

		while (cursor.moveToNext()) {
			devices.add(cursor.getInt(
					cursor.getColumnIndex(SQLiteAxolotlStore.DEVICE_ID)));
		}

		cursor.close();
		return devices;
	}

	public boolean containsSession(Account account, AxolotlAddress contact) {
		Cursor cursor = getCursorForSession(account, contact);
		int count = cursor.getCount();
		cursor.close();
		return count != 0;
	}

	public void storeSession(Account account, AxolotlAddress contact, SessionRecord session) {
		SQLiteDatabase db = this.getWritableDatabase();
		ContentValues values = new ContentValues();
		values.put(SQLiteAxolotlStore.NAME, contact.getName());
		values.put(SQLiteAxolotlStore.DEVICE_ID, contact.getDeviceId());
		values.put(SQLiteAxolotlStore.KEY, Base64.encodeToString(session.serialize(), Base64.DEFAULT));
		values.put(SQLiteAxolotlStore.ACCOUNT, account.getUuid());
		db.insert(SQLiteAxolotlStore.SESSION_TABLENAME, null, values);
	}

	public void deleteSession(Account account, AxolotlAddress contact) {
		SQLiteDatabase db = this.getWritableDatabase();
		deleteSession(db, account, contact);
	}

	private void deleteSession(SQLiteDatabase db, Account account, AxolotlAddress contact) {
		String[] args = {account.getUuid(),
				contact.getName(),
				Integer.toString(contact.getDeviceId())};
		db.delete(SQLiteAxolotlStore.SESSION_TABLENAME,
				SQLiteAxolotlStore.ACCOUNT + " = ? AND "
						+ SQLiteAxolotlStore.NAME + " = ? AND "
						+ SQLiteAxolotlStore.DEVICE_ID + " = ? ",
				args);
	}

	public void deleteAllSessions(Account account, AxolotlAddress contact) {
		SQLiteDatabase db = this.getWritableDatabase();
		String[] args = {account.getUuid(), contact.getName()};
		db.delete(SQLiteAxolotlStore.SESSION_TABLENAME,
				SQLiteAxolotlStore.ACCOUNT + "=? AND "
						+ SQLiteAxolotlStore.NAME + " = ?",
				args);
	}

	private Cursor getCursorForPreKey(Account account, int preKeyId) {
		SQLiteDatabase db = this.getReadableDatabase();
		String[] columns = {SQLiteAxolotlStore.KEY};
		String[] selectionArgs = {account.getUuid(), Integer.toString(preKeyId)};
		Cursor cursor = db.query(SQLiteAxolotlStore.PREKEY_TABLENAME,
				columns,
				SQLiteAxolotlStore.ACCOUNT + "=? AND "
						+ SQLiteAxolotlStore.ID + "=?",
				selectionArgs,
				null, null, null);

		return cursor;
	}

	public PreKeyRecord loadPreKey(Account account, int preKeyId) {
		PreKeyRecord record = null;
		Cursor cursor = getCursorForPreKey(account, preKeyId);
		if (cursor.getCount() != 0) {
			cursor.moveToFirst();
			try {
				record = new PreKeyRecord(Base64.decode(cursor.getString(cursor.getColumnIndex(SQLiteAxolotlStore.KEY)), Base64.DEFAULT));
			} catch (IOException e) {
				throw new AssertionError(e);
			}
		}
		cursor.close();
		return record;
	}

	public boolean containsPreKey(Account account, int preKeyId) {
		Cursor cursor = getCursorForPreKey(account, preKeyId);
		int count = cursor.getCount();
		cursor.close();
		return count != 0;
	}

	public void storePreKey(Account account, PreKeyRecord record) {
		SQLiteDatabase db = this.getWritableDatabase();
		ContentValues values = new ContentValues();
		values.put(SQLiteAxolotlStore.ID, record.getId());
		values.put(SQLiteAxolotlStore.KEY, Base64.encodeToString(record.serialize(), Base64.DEFAULT));
		values.put(SQLiteAxolotlStore.ACCOUNT, account.getUuid());
		db.insert(SQLiteAxolotlStore.PREKEY_TABLENAME, null, values);
	}

	public void deletePreKey(Account account, int preKeyId) {
		SQLiteDatabase db = this.getWritableDatabase();
		String[] args = {account.getUuid(), Integer.toString(preKeyId)};
		db.delete(SQLiteAxolotlStore.PREKEY_TABLENAME,
				SQLiteAxolotlStore.ACCOUNT + "=? AND "
						+ SQLiteAxolotlStore.ID + "=?",
				args);
	}

	private Cursor getCursorForSignedPreKey(Account account, int signedPreKeyId) {
		SQLiteDatabase db = this.getReadableDatabase();
		String[] columns = {SQLiteAxolotlStore.KEY};
		String[] selectionArgs = {account.getUuid(), Integer.toString(signedPreKeyId)};
		Cursor cursor = db.query(SQLiteAxolotlStore.SIGNED_PREKEY_TABLENAME,
				columns,
				SQLiteAxolotlStore.ACCOUNT + "=? AND " + SQLiteAxolotlStore.ID + "=?",
				selectionArgs,
				null, null, null);

		return cursor;
	}

	public SignedPreKeyRecord loadSignedPreKey(Account account, int signedPreKeyId) {
		SignedPreKeyRecord record = null;
		Cursor cursor = getCursorForSignedPreKey(account, signedPreKeyId);
		if (cursor.getCount() != 0) {
			cursor.moveToFirst();
			try {
				record = new SignedPreKeyRecord(Base64.decode(cursor.getString(cursor.getColumnIndex(SQLiteAxolotlStore.KEY)), Base64.DEFAULT));
			} catch (IOException e) {
				throw new AssertionError(e);
			}
		}
		cursor.close();
		return record;
	}

	public List<SignedPreKeyRecord> loadSignedPreKeys(Account account) {
		List<SignedPreKeyRecord> prekeys = new ArrayList<>();
		SQLiteDatabase db = this.getReadableDatabase();
		String[] columns = {SQLiteAxolotlStore.KEY};
		String[] selectionArgs = {account.getUuid()};
		Cursor cursor = db.query(SQLiteAxolotlStore.SIGNED_PREKEY_TABLENAME,
				columns,
				SQLiteAxolotlStore.ACCOUNT + "=?",
				selectionArgs,
				null, null, null);

		while (cursor.moveToNext()) {
			try {
				prekeys.add(new SignedPreKeyRecord(Base64.decode(cursor.getString(cursor.getColumnIndex(SQLiteAxolotlStore.KEY)), Base64.DEFAULT)));
			} catch (IOException ignored) {
			}
		}
		cursor.close();
		return prekeys;
	}

	public boolean containsSignedPreKey(Account account, int signedPreKeyId) {
		Cursor cursor = getCursorForPreKey(account, signedPreKeyId);
		int count = cursor.getCount();
		cursor.close();
		return count != 0;
	}

	public void storeSignedPreKey(Account account, SignedPreKeyRecord record) {
		SQLiteDatabase db = this.getWritableDatabase();
		ContentValues values = new ContentValues();
		values.put(SQLiteAxolotlStore.ID, record.getId());
		values.put(SQLiteAxolotlStore.KEY, Base64.encodeToString(record.serialize(), Base64.DEFAULT));
		values.put(SQLiteAxolotlStore.ACCOUNT, account.getUuid());
		db.insert(SQLiteAxolotlStore.SIGNED_PREKEY_TABLENAME, null, values);
	}

	public void deleteSignedPreKey(Account account, int signedPreKeyId) {
		SQLiteDatabase db = this.getWritableDatabase();
		String[] args = {account.getUuid(), Integer.toString(signedPreKeyId)};
		db.delete(SQLiteAxolotlStore.SIGNED_PREKEY_TABLENAME,
				SQLiteAxolotlStore.ACCOUNT + "=? AND "
						+ SQLiteAxolotlStore.ID + "=?",
				args);
	}

	private Cursor getIdentityKeyCursor(Account account, String name, boolean own) {
		final SQLiteDatabase db = this.getReadableDatabase();
		return getIdentityKeyCursor(db, account, name, own);
	}

	private Cursor getIdentityKeyCursor(SQLiteDatabase db, Account account, String name, boolean own) {
		return getIdentityKeyCursor(db, account, name, own, null);
	}

	private Cursor getIdentityKeyCursor(Account account, String fingerprint) {
		final SQLiteDatabase db = this.getReadableDatabase();
		return getIdentityKeyCursor(db, account, fingerprint);
	}

	private Cursor getIdentityKeyCursor(SQLiteDatabase db, Account account, String fingerprint) {
		return getIdentityKeyCursor(db, account, null, null, fingerprint);
	}

	private Cursor getIdentityKeyCursor(SQLiteDatabase db, Account account, String name, Boolean own, String fingerprint) {
		String[] columns = {SQLiteAxolotlStore.TRUST,
				SQLiteAxolotlStore.ACTIVE,
				SQLiteAxolotlStore.LAST_ACTIVATION,
				SQLiteAxolotlStore.KEY};
		ArrayList<String> selectionArgs = new ArrayList<>(4);
		selectionArgs.add(account.getUuid());
		String selectionString = SQLiteAxolotlStore.ACCOUNT + " = ?";
		if (name != null) {
			selectionArgs.add(name);
			selectionString += " AND " + SQLiteAxolotlStore.NAME + " = ?";
		}
		if (fingerprint != null) {
			selectionArgs.add(fingerprint);
			selectionString += " AND " + SQLiteAxolotlStore.FINGERPRINT + " = ?";
		}
		if (own != null) {
			selectionArgs.add(own ? "1" : "0");
			selectionString += " AND " + SQLiteAxolotlStore.OWN + " = ?";
		}
		Cursor cursor = db.query(SQLiteAxolotlStore.IDENTITIES_TABLENAME,
				columns,
				selectionString,
				selectionArgs.toArray(new String[selectionArgs.size()]),
				null, null, null);

		return cursor;
	}

	public IdentityKeyPair loadOwnIdentityKeyPair(Account account) {
		SQLiteDatabase db = getReadableDatabase();
		return loadOwnIdentityKeyPair(db, account);
	}

	private IdentityKeyPair loadOwnIdentityKeyPair(SQLiteDatabase db, Account account) {
		String name = account.getJid().toBareJid().toPreppedString();
		IdentityKeyPair identityKeyPair = null;
		Cursor cursor = getIdentityKeyCursor(db, account, name, true);
		if (cursor.getCount() != 0) {
			cursor.moveToFirst();
			try {
				identityKeyPair = new IdentityKeyPair(Base64.decode(cursor.getString(cursor.getColumnIndex(SQLiteAxolotlStore.KEY)), Base64.DEFAULT));
			} catch (InvalidKeyException e) {
				Log.d(Config.LOGTAG, AxolotlService.getLogprefix(account) + "Encountered invalid IdentityKey in database for account" + account.getJid().toBareJid() + ", address: " + name);
			}
		}
		cursor.close();

		return identityKeyPair;
	}

	public Set<IdentityKey> loadIdentityKeys(Account account, String name) {
		return loadIdentityKeys(account, name, null);
	}

	public Set<IdentityKey> loadIdentityKeys(Account account, String name, FingerprintStatus status) {
		Set<IdentityKey> identityKeys = new HashSet<>();
		Cursor cursor = getIdentityKeyCursor(account, name, false);

		while (cursor.moveToNext()) {
			if (status != null && !FingerprintStatus.fromCursor(cursor).equals(status)) {
				continue;
			}
			try {
				String key = cursor.getString(cursor.getColumnIndex(SQLiteAxolotlStore.KEY));
				if (key != null) {
					identityKeys.add(new IdentityKey(Base64.decode(key, Base64.DEFAULT), 0));
				} else {
					Log.d(Config.LOGTAG, AxolotlService.getLogprefix(account) + "Missing key (possibly preverified) in database for account" + account.getJid().toBareJid() + ", address: " + name);
				}
			} catch (InvalidKeyException e) {
				Log.d(Config.LOGTAG, AxolotlService.getLogprefix(account) + "Encountered invalid IdentityKey in database for account" + account.getJid().toBareJid() + ", address: " + name);
			}
		}
		cursor.close();

		return identityKeys;
	}

	public long numTrustedKeys(Account account, String name) {
		SQLiteDatabase db = getReadableDatabase();
		String[] args = {
				account.getUuid(),
				name,
				FingerprintStatus.Trust.TRUSTED.toString(),
				FingerprintStatus.Trust.VERIFIED.toString(),
				FingerprintStatus.Trust.VERIFIED_X509.toString()
		};
		return DatabaseUtils.queryNumEntries(db, SQLiteAxolotlStore.IDENTITIES_TABLENAME,
				SQLiteAxolotlStore.ACCOUNT + " = ?"
						+ " AND " + SQLiteAxolotlStore.NAME + " = ?"
						+ " AND (" + SQLiteAxolotlStore.TRUST + " = ? OR " + SQLiteAxolotlStore.TRUST + " = ? OR " +SQLiteAxolotlStore.TRUST +" = ?)"
						+ " AND " +SQLiteAxolotlStore.ACTIVE + " > 0",
				args
		);
	}

	private void storeIdentityKey(Account account, String name, boolean own, String fingerprint, String base64Serialized, FingerprintStatus status) {
		SQLiteDatabase db = this.getWritableDatabase();
		ContentValues values = new ContentValues();
		values.put(SQLiteAxolotlStore.ACCOUNT, account.getUuid());
		values.put(SQLiteAxolotlStore.NAME, name);
		values.put(SQLiteAxolotlStore.OWN, own ? 1 : 0);
		values.put(SQLiteAxolotlStore.FINGERPRINT, fingerprint);
		values.put(SQLiteAxolotlStore.KEY, base64Serialized);
		values.putAll(status.toContentValues());
		String where = SQLiteAxolotlStore.ACCOUNT+"=? AND "+SQLiteAxolotlStore.NAME+"=? AND "+SQLiteAxolotlStore.FINGERPRINT+" =?";
		String[] whereArgs = {account.getUuid(),name,fingerprint};
		int rows = db.update(SQLiteAxolotlStore.IDENTITIES_TABLENAME,values,where,whereArgs);
		if (rows == 0) {
			db.insert(SQLiteAxolotlStore.IDENTITIES_TABLENAME, null, values);
		}
	}

	public void storePreVerification(Account account, String name, String fingerprint, FingerprintStatus status) {
		SQLiteDatabase db = this.getWritableDatabase();
		ContentValues values = new ContentValues();
		values.put(SQLiteAxolotlStore.ACCOUNT, account.getUuid());
		values.put(SQLiteAxolotlStore.NAME, name);
		values.put(SQLiteAxolotlStore.OWN, 0);
		values.put(SQLiteAxolotlStore.FINGERPRINT, fingerprint);
		values.putAll(status.toContentValues());
		db.insert(SQLiteAxolotlStore.IDENTITIES_TABLENAME, null, values);
	}

	public FingerprintStatus getFingerprintStatus(Account account, String fingerprint) {
		Cursor cursor = getIdentityKeyCursor(account, fingerprint);
		final FingerprintStatus status;
		if (cursor.getCount() > 0) {
			cursor.moveToFirst();
			status = FingerprintStatus.fromCursor(cursor);
		} else {
			status = null;
		}
		cursor.close();
		return status;
	}

	public boolean setIdentityKeyTrust(Account account, String fingerprint, FingerprintStatus fingerprintStatus) {
		SQLiteDatabase db = this.getWritableDatabase();
		return setIdentityKeyTrust(db, account, fingerprint, fingerprintStatus);
	}

	private boolean setIdentityKeyTrust(SQLiteDatabase db, Account account, String fingerprint, FingerprintStatus status) {
		String[] selectionArgs = {
				account.getUuid(),
				fingerprint
		};
		int rows = db.update(SQLiteAxolotlStore.IDENTITIES_TABLENAME, status.toContentValues(),
				SQLiteAxolotlStore.ACCOUNT + " = ? AND "
						+ SQLiteAxolotlStore.FINGERPRINT + " = ? ",
				selectionArgs);
		return rows == 1;
	}

	public boolean setIdentityKeyCertificate(Account account, String fingerprint, X509Certificate x509Certificate) {
		SQLiteDatabase db = this.getWritableDatabase();
		String[] selectionArgs = {
				account.getUuid(),
				fingerprint
		};
		try {
			ContentValues values = new ContentValues();
			values.put(SQLiteAxolotlStore.CERTIFICATE, x509Certificate.getEncoded());
			return db.update(SQLiteAxolotlStore.IDENTITIES_TABLENAME, values,
					SQLiteAxolotlStore.ACCOUNT + " = ? AND "
							+ SQLiteAxolotlStore.FINGERPRINT + " = ? ",
					selectionArgs) == 1;
		} catch (CertificateEncodingException e) {
			Log.d(Config.LOGTAG, "could not encode certificate");
			return false;
		}
	}

	public X509Certificate getIdentityKeyCertifcate(Account account, String fingerprint) {
		SQLiteDatabase db = this.getReadableDatabase();
		String[] selectionArgs = {
				account.getUuid(),
				fingerprint
		};
		String[] colums = {SQLiteAxolotlStore.CERTIFICATE};
		String selection = SQLiteAxolotlStore.ACCOUNT + " = ? AND " + SQLiteAxolotlStore.FINGERPRINT + " = ? ";
		Cursor cursor = db.query(SQLiteAxolotlStore.IDENTITIES_TABLENAME, colums, selection, selectionArgs, null, null, null);
		if (cursor.getCount() < 1) {
			return null;
		} else {
			cursor.moveToFirst();
			byte[] certificate = cursor.getBlob(cursor.getColumnIndex(SQLiteAxolotlStore.CERTIFICATE));
			cursor.close();
			if (certificate == null || certificate.length == 0) {
				return null;
			}
			try {
				CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
				return (X509Certificate) certificateFactory.generateCertificate(new ByteArrayInputStream(certificate));
			} catch (CertificateException e) {
				Log.d(Config.LOGTAG,"certificate exception "+e.getMessage());
				return null;
			}
		}
	}

	public void storeIdentityKey(Account account, String name, IdentityKey identityKey, FingerprintStatus status) {
		storeIdentityKey(account, name, false, identityKey.getFingerprint().replaceAll("\\s", ""), Base64.encodeToString(identityKey.serialize(), Base64.DEFAULT), status);
	}

	public void storeOwnIdentityKeyPair(Account account, IdentityKeyPair identityKeyPair) {
		storeIdentityKey(account, account.getJid().toBareJid().toPreppedString(), true, identityKeyPair.getPublicKey().getFingerprint().replaceAll("\\s", ""), Base64.encodeToString(identityKeyPair.serialize(), Base64.DEFAULT), FingerprintStatus.createActiveVerified(false));
	}


	public void recreateAxolotlDb(SQLiteDatabase db) {
		Log.d(Config.LOGTAG, AxolotlService.LOGPREFIX + " : " + ">>> (RE)CREATING AXOLOTL DATABASE <<<");
		db.execSQL("DROP TABLE IF EXISTS " + SQLiteAxolotlStore.SESSION_TABLENAME);
		db.execSQL(CREATE_SESSIONS_STATEMENT);
		db.execSQL("DROP TABLE IF EXISTS " + SQLiteAxolotlStore.PREKEY_TABLENAME);
		db.execSQL(CREATE_PREKEYS_STATEMENT);
		db.execSQL("DROP TABLE IF EXISTS " + SQLiteAxolotlStore.SIGNED_PREKEY_TABLENAME);
		db.execSQL(CREATE_SIGNED_PREKEYS_STATEMENT);
		db.execSQL("DROP TABLE IF EXISTS " + SQLiteAxolotlStore.IDENTITIES_TABLENAME);
		db.execSQL(CREATE_IDENTITIES_STATEMENT);
	}

	public void wipeAxolotlDb(Account account) {
		String accountName = account.getUuid();
		Log.d(Config.LOGTAG, AxolotlService.getLogprefix(account) + ">>> WIPING AXOLOTL DATABASE FOR ACCOUNT " + accountName + " <<<");
		SQLiteDatabase db = this.getWritableDatabase();
		String[] deleteArgs = {
				accountName
		};
		db.delete(SQLiteAxolotlStore.SESSION_TABLENAME,
				SQLiteAxolotlStore.ACCOUNT + " = ?",
				deleteArgs);
		db.delete(SQLiteAxolotlStore.PREKEY_TABLENAME,
				SQLiteAxolotlStore.ACCOUNT + " = ?",
				deleteArgs);
		db.delete(SQLiteAxolotlStore.SIGNED_PREKEY_TABLENAME,
				SQLiteAxolotlStore.ACCOUNT + " = ?",
				deleteArgs);
		db.delete(SQLiteAxolotlStore.IDENTITIES_TABLENAME,
				SQLiteAxolotlStore.ACCOUNT + " = ?",
				deleteArgs);
	}

	public boolean startTimeCountExceedsThreshold() {
		SQLiteDatabase db = this.getWritableDatabase();
		long cleanBeforeTimestamp = System.currentTimeMillis() - Config.FREQUENT_RESTARTS_DETECTION_WINDOW;
		db.execSQL("delete from "+START_TIMES_TABLE+" where timestamp < "+cleanBeforeTimestamp);
		ContentValues values = new ContentValues();
		values.put("timestamp",System.currentTimeMillis());
		db.insert(START_TIMES_TABLE,null,values);
		String[] columns = new String[]{"count(timestamp)"};
		Cursor cursor = db.query(START_TIMES_TABLE,columns,null,null,null,null,null);
		int count;
		if (cursor.moveToFirst()) {
			count = cursor.getInt(0);
		} else {
			count = 0;
		}
		cursor.close();
		Log.d(Config.LOGTAG,"start time counter reached "+count);
		return count >= Config.FREQUENT_RESTARTS_THRESHOLD;
	}

	public void clearStartTimeCounter(boolean justOne) {
		SQLiteDatabase db = this.getWritableDatabase();
		if (justOne) {
			db.execSQL("delete from "+START_TIMES_TABLE+" where timestamp in (select timestamp from "+START_TIMES_TABLE+" order by timestamp desc limit 1)");
			Log.d(Config.LOGTAG,"do not count start up after being swiped away");
		} else {
			Log.d(Config.LOGTAG,"resetting start time counter");
			db.execSQL("delete from " + START_TIMES_TABLE);
		}
	}
}
