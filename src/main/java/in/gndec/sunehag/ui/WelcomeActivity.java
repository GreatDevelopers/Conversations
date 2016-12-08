package in.gndec.sunehag.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import in.gndec.sunehag.StartupManager;

public class WelcomeActivity extends Activity {

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		StartupManager manager = new StartupManager(this);
		if (manager.isFirstTimeLaunch()){
			startActivity(new Intent(WelcomeActivity.this, Intro.class));
			finish();
		} else {
			startActivity(new Intent(WelcomeActivity.this, ConversationActivity.class));
			finish();
		}
	}

}
